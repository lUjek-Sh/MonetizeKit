package ua.lujek.monetizekit.billing.googleplay

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails as PlayProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import ua.lujek.monetizekit.billing.core.BillingCatalog
import ua.lujek.monetizekit.billing.core.BillingFailure
import ua.lujek.monetizekit.billing.core.BillingOffer
import ua.lujek.monetizekit.billing.core.BillingProduct
import ua.lujek.monetizekit.billing.core.BillingProductDetails
import ua.lujek.monetizekit.billing.core.BillingProductId
import ua.lujek.monetizekit.billing.core.BillingProductType
import ua.lujek.monetizekit.billing.core.BillingPurchase
import ua.lujek.monetizekit.billing.core.BillingPurchaseEvent
import ua.lujek.monetizekit.billing.core.BillingStore
import ua.lujek.monetizekit.billing.core.EntitlementState
import ua.lujek.monetizekit.billing.core.PurchaseVerification
import ua.lujek.monetizekit.billing.core.PurchaseVerifier

sealed interface GooglePlayBillingState {
    data object Idle : GooglePlayBillingState
    data object Connecting : GooglePlayBillingState
    data object Ready : GooglePlayBillingState
    data class Unavailable(val failure: BillingFailure) : GooglePlayBillingState
    data object Closed : GooglePlayBillingState
}

sealed interface BillingRefreshResult {
    data object Refreshed : BillingRefreshResult
    data class Failed(val failure: BillingFailure) : BillingRefreshResult
}

sealed interface PurchaseLaunchResult {
    data object Launched : PurchaseLaunchResult
    data class Failed(val failure: BillingFailure) : PurchaseLaunchResult
}

/**
 * Google Play Billing implementation that owns one [BillingClient] and exposes arbitrary product
 * IDs as application entitlements. A purchase is never granted or acknowledged until
 * [purchaseVerifier] has approved it; use a secure backend for that verifier in production.
 */
class GooglePlayBilling(
    context: Context,
    private val catalog: BillingCatalog,
    private val purchaseVerifier: PurchaseVerifier,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : BillingStore, AutoCloseable {
    private val applicationContext = context.applicationContext
    private val connectionMutex = Mutex()
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow<GooglePlayBillingState>(GooglePlayBillingState.Idle)
    private val mutableProducts = MutableStateFlow<Map<BillingProductId, BillingProductDetails>>(emptyMap())
    private val mutableEntitlements = MutableStateFlow<EntitlementState>(EntitlementState.Loading)
    private val mutablePurchaseEvents = MutableSharedFlow<BillingPurchaseEvent>(extraBufferCapacity = 32)

    private var closed = false
    private val billingClient = BillingClient.newBuilder(applicationContext)
        .setListener(PurchasesUpdatedListener(::onPurchasesUpdated))
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .enableAutoServiceReconnection()
        .build()

    val state: StateFlow<GooglePlayBillingState> = mutableState.asStateFlow()
    override val products: StateFlow<Map<BillingProductId, BillingProductDetails>> = mutableProducts.asStateFlow()
    override val entitlements: StateFlow<EntitlementState> = mutableEntitlements.asStateFlow()
    override val purchaseEvents: SharedFlow<BillingPurchaseEvent> = mutablePurchaseEvents.asSharedFlow()

    /** Connects, refreshes account purchases, and obtains current product information. */
    suspend fun start(): BillingRefreshResult = refresh()

    /** Requeries owned purchases and product data. Call when the app returns to the foreground. */
    suspend fun refresh(): BillingRefreshResult = refreshMutex.withLock {
        val connectionFailure = ensureConnected()
        if (connectionFailure != null) return@withLock BillingRefreshResult.Failed(connectionFailure)

        val entitlementResult = refreshEntitlements()
        val productsFailure = refreshProducts()
        when {
            entitlementResult != null -> BillingRefreshResult.Failed(entitlementResult)
            productsFailure != null -> BillingRefreshResult.Failed(productsFailure)
            else -> BillingRefreshResult.Refreshed
        }
    }

    /**
     * Queries fresh ProductDetails immediately before opening Google Play, avoiding stale cached
     * ProductDetails objects. The asynchronous completion is delivered through [purchaseEvents].
     */
    suspend fun launchPurchase(
        activity: Activity,
        productId: BillingProductId,
        offerToken: String? = null,
        isOfferPersonalized: Boolean = false,
        obfuscatedAccountId: String? = null,
        obfuscatedProfileId: String? = null,
    ): PurchaseLaunchResult = withContext(Dispatchers.Main.immediate) {
        val product = catalog[productId]
            ?: return@withContext PurchaseLaunchResult.Failed(
                BillingFailure("Product '$productId' is not configured in BillingCatalog."),
            )
        val connectionFailure = ensureConnected()
        if (connectionFailure != null) return@withContext PurchaseLaunchResult.Failed(connectionFailure)

        val detailResult = queryProductDetails(listOf(product))
        if (detailResult.failure != null) return@withContext PurchaseLaunchResult.Failed(detailResult.failure)
        val details = detailResult.details.singleOrNull()
            ?: return@withContext PurchaseLaunchResult.Failed(
                BillingFailure("Google Play did not return product details for '$productId'."),
            )
        val availableOffers = details.offers()
        val selectedOffer = offerToken?.let { requested -> availableOffers.firstOrNull { it.token == requested } }
            ?: availableOffers.firstOrNull()
            ?: return@withContext PurchaseLaunchResult.Failed(
                BillingFailure("Google Play returned no eligible offers for '$productId'."),
            )
        if (offerToken != null && selectedOffer.token != offerToken) {
            return@withContext PurchaseLaunchResult.Failed(
                BillingFailure("Offer '$offerToken' is not currently eligible for '$productId'."),
            )
        }

        val detailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(selectedOffer.token)
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(detailsParams))
            .setIsOfferPersonalized(isOfferPersonalized)
            .apply {
                obfuscatedAccountId?.let(::setObfuscatedAccountId)
                obfuscatedProfileId?.let(::setObfuscatedProfileId)
            }
            .build()
        val result = billingClient.launchBillingFlow(activity, params)
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            PurchaseLaunchResult.Launched
        } else {
            PurchaseLaunchResult.Failed(result.toFailure("Google Play could not open the purchase flow."))
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        billingClient.endConnection()
        mutableState.value = GooglePlayBillingState.Closed
        mutableEntitlements.value = EntitlementState.Unavailable(
            BillingFailure("Google Play Billing has been closed."),
        )
    }

    private suspend fun ensureConnected(): BillingFailure? = connectionMutex.withLock {
        if (closed) {
            return@withLock BillingFailure("Google Play Billing has been closed.")
        }
        if (billingClient.isReady) {
            mutableState.value = GooglePlayBillingState.Ready
            return@withLock null
        }
        mutableState.value = GooglePlayBillingState.Connecting
        suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (!continuation.isActive) return
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        mutableState.value = GooglePlayBillingState.Ready
                        continuation.resume(null)
                    } else {
                        val failure = result.toFailure("Google Play Billing connection failed.")
                        mutableState.value = GooglePlayBillingState.Unavailable(failure)
                        mutableEntitlements.value = EntitlementState.Unavailable(failure)
                        continuation.resume(failure)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    if (closed) return
                    val failure = BillingFailure("Google Play Billing service disconnected.")
                    mutableState.value = GooglePlayBillingState.Unavailable(failure)
                    mutableEntitlements.value = EntitlementState.Unavailable(failure)
                }
            })
        }
    }

    private suspend fun refreshProducts(): BillingFailure? {
        val mappedProducts = linkedMapOf<BillingProductId, BillingProductDetails>()
        catalog.products.groupBy(BillingProduct::type).values.forEach { productsOfType ->
            val result = queryProductDetails(productsOfType)
            if (result.failure != null) return result.failure
            result.details.forEach { details ->
                mappedProducts[BillingProductId.of(details.productId)] = details.toBillingProductDetails()
            }
        }
        mutableProducts.value = mappedProducts
        return null
    }

    private suspend fun refreshEntitlements(): BillingFailure? {
        val purchases = mutableListOf<Purchase>()
        catalog.products.groupBy(BillingProduct::type).keys.forEach { type ->
            when (val result = queryPurchases(type)) {
                is PurchasesQueryResult.Failed -> {
                    mutableEntitlements.value = EntitlementState.Unavailable(result.failure)
                    mutablePurchaseEvents.emit(BillingPurchaseEvent.Failed(result.failure))
                    return result.failure
                }

                is PurchasesQueryResult.Success -> purchases += result.purchases
            }
        }

        val activeEntitlements = linkedSetOf<ua.lujek.monetizekit.billing.core.BillingEntitlementId>()
        purchases.forEach { purchase ->
            val productIds = purchase.products.map(BillingProductId::of).toSet()
            val configuredProducts = productIds.mapNotNull(catalog::get)
            if (configuredProducts.isEmpty()) return@forEach
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                mutablePurchaseEvents.emit(BillingPurchaseEvent.Pending(productIds))
                return@forEach
            }
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach

            val normalizedPurchase = purchase.toBillingPurchase(productIds)
            when (val verification = purchaseVerifier.verify(normalizedPurchase)) {
                PurchaseVerification.Verified -> {
                    activeEntitlements += configuredProducts.flatMap(BillingProduct::grants)
                    finalizePurchase(purchase, configuredProducts)?.let { failure ->
                        mutablePurchaseEvents.emit(BillingPurchaseEvent.Failed(failure))
                        return@forEach
                    }
                    mutablePurchaseEvents.emit(BillingPurchaseEvent.Processed(normalizedPurchase))
                }

                is PurchaseVerification.Rejected -> {
                    mutablePurchaseEvents.emit(
                        BillingPurchaseEvent.Failed(BillingFailure(verification.reason)),
                    )
                }

                is PurchaseVerification.RetryLater -> {
                    mutableEntitlements.value = EntitlementState.Unavailable(verification.failure)
                    mutablePurchaseEvents.emit(BillingPurchaseEvent.Failed(verification.failure))
                    return verification.failure
                }
            }
        }
        mutableEntitlements.value = EntitlementState.Available(activeEntitlements)
        return null
    }

    private suspend fun finalizePurchase(
        purchase: Purchase,
        configuredProducts: List<BillingProduct>,
    ): BillingFailure? {
        return if (configuredProducts.any { it.type == BillingProductType.ONE_TIME_CONSUMABLE }) {
            consumePurchase(purchase.purchaseToken)
        } else if (purchase.isAcknowledged) {
            null
        } else {
            acknowledgePurchase(purchase.purchaseToken)
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                if (purchases.isNullOrEmpty()) return@launch
                refresh()
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                mutablePurchaseEvents.tryEmit(BillingPurchaseEvent.Cancelled)
            }

            else -> {
                val failure = result.toFailure("Google Play purchase flow failed.")
                mutablePurchaseEvents.tryEmit(BillingPurchaseEvent.Failed(failure))
            }
        }
    }

    private suspend fun queryProductDetails(products: List<BillingProduct>): ProductDetailsQueryResult {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                products.map { product ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(product.id.value)
                        .setProductType(product.type.playProductType)
                        .build()
                },
            )
            .build()
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
                if (!continuation.isActive) return@queryProductDetailsAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(ProductDetailsQueryResult(detailsResult.productDetailsList, null))
                } else {
                    continuation.resume(
                        ProductDetailsQueryResult(
                            details = emptyList(),
                            failure = result.toFailure("Google Play could not query product details."),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun queryPurchases(type: BillingProductType): PurchasesQueryResult {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(type.playProductType)
            .build()
        return suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (!continuation.isActive) return@queryPurchasesAsync
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(PurchasesQueryResult.Success(purchases))
                } else {
                    continuation.resume(
                        PurchasesQueryResult.Failed(
                            result.toFailure("Google Play could not query purchases."),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun acknowledgePurchase(token: String): BillingFailure? =
        suspendCancellableCoroutine { continuation ->
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
            billingClient.acknowledgePurchase(params) { result ->
                if (!continuation.isActive) return@acknowledgePurchase
                continuation.resume(
                    result.takeUnless { it.responseCode != BillingClient.BillingResponseCode.OK }
                        ?.let { null }
                        ?: result.toFailure("Google Play could not acknowledge the purchase."),
                )
            }
        }

    private suspend fun consumePurchase(token: String): BillingFailure? =
        suspendCancellableCoroutine { continuation ->
            val params = ConsumeParams.newBuilder().setPurchaseToken(token).build()
            billingClient.consumeAsync(params) { result, _ ->
                if (!continuation.isActive) return@consumeAsync
                continuation.resume(
                    result.takeUnless { it.responseCode != BillingClient.BillingResponseCode.OK }
                        ?.let { null }
                        ?: result.toFailure("Google Play could not consume the purchase."),
                )
            }
        }

    private val BillingProductType.playProductType: String
        get() = when (this) {
            BillingProductType.ONE_TIME_CONSUMABLE,
            BillingProductType.ONE_TIME_NON_CONSUMABLE,
            -> BillingClient.ProductType.INAPP

            BillingProductType.SUBSCRIPTION -> BillingClient.ProductType.SUBS
        }

    private fun PlayProductDetails.toBillingProductDetails(): BillingProductDetails = BillingProductDetails(
        id = BillingProductId.of(productId),
        title = title,
        description = description,
        offers = offers(),
    )

    private fun PlayProductDetails.offers(): List<BillingOffer> = when (productType) {
        BillingClient.ProductType.SUBS -> subscriptionOfferDetails.orEmpty().map { offer ->
            val phase = offer.pricingPhases.pricingPhaseList.firstOrNull()
            BillingOffer(
                token = offer.offerToken,
                formattedPrice = phase?.formattedPrice.orEmpty(),
                billingPeriod = phase?.billingPeriod,
            )
        }

        else -> oneTimePurchaseOfferDetailsList.orEmpty().mapNotNull { offer ->
            val token = offer.offerToken ?: return@mapNotNull null
            BillingOffer(
                token = token,
                formattedPrice = offer.formattedPrice,
                billingPeriod = null,
            )
        }
    }

    private fun Purchase.toBillingPurchase(productIds: Set<BillingProductId>): BillingPurchase = BillingPurchase(
        token = purchaseToken,
        productIds = productIds,
        purchaseTimeMillis = purchaseTime,
        originalJson = originalJson,
        signature = signature,
        acknowledged = isAcknowledged,
    )

    private fun BillingResult.toFailure(prefix: String): BillingFailure = BillingFailure(
        message = "$prefix responseCode=$responseCode, debugMessage=$debugMessage",
    )

    private data class ProductDetailsQueryResult(
        val details: List<PlayProductDetails>,
        val failure: BillingFailure?,
    )

    private sealed interface PurchasesQueryResult {
        data class Success(val purchases: List<Purchase>) : PurchasesQueryResult
        data class Failed(val failure: BillingFailure) : PurchasesQueryResult
    }
}
