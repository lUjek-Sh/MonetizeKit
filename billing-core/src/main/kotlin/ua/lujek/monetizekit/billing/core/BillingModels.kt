package ua.lujek.monetizekit.billing.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@JvmInline
value class BillingProductId private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        fun of(value: String): BillingProductId = BillingProductId(value.requireIdentifier("Billing product id"))
    }
}

@JvmInline
value class BillingEntitlementId private constructor(
    val value: String,
) {
    override fun toString(): String = value

    companion object {
        fun of(value: String): BillingEntitlementId =
            BillingEntitlementId(value.requireIdentifier("Billing entitlement id"))
    }
}

/** The Google Play product category and its post-purchase handling semantics. */
enum class BillingProductType {
    ONE_TIME_CONSUMABLE,
    ONE_TIME_NON_CONSUMABLE,
    SUBSCRIPTION,
}

/**
 * Maps a Play Console product to zero or more application-owned entitlements.
 * IDs are deliberately arbitrary: neither a product nor an entitlement is required to be named
 * `remove_ads`.
 */
data class BillingProduct(
    val id: BillingProductId,
    val type: BillingProductType,
    val grants: Set<BillingEntitlementId> = emptySet(),
) {
    init {
        require(type != BillingProductType.ONE_TIME_CONSUMABLE || grants.isEmpty()) {
            "Consumable product '$id' cannot grant a durable entitlement. Process it through purchase events instead."
        }
    }
}

/** Immutable catalogue declared by the application. */
class BillingCatalog(products: Collection<BillingProduct>) {
    val products: List<BillingProduct> = products.toList()
    private val productsById = this.products.associateBy(BillingProduct::id)

    init {
        require(this.products.isNotEmpty()) { "Configure at least one billing product." }
        require(productsById.size == this.products.size) { "Every billing product id must be unique." }
    }

    operator fun get(id: BillingProductId): BillingProduct? = productsById[id]
}

/** Localized information ready for an application-owned purchase UI. */
data class BillingProductDetails(
    val id: BillingProductId,
    val title: String,
    val description: String,
    val offers: List<BillingOffer>,
)

/** A currently eligible Google Play offer. Pass [token] back to the billing flow unchanged. */
data class BillingOffer(
    val token: String,
    val formattedPrice: String,
    val billingPeriod: String?,
)

data class BillingFailure(
    val message: String,
    val cause: Throwable? = null,
)

/**
 * The entitlement source is intentionally independent from Google Play. It can also be backed by
 * an application's server, a different storefront, or a test implementation.
 */
interface EntitlementProvider {
    val entitlements: StateFlow<EntitlementState>
}

sealed interface EntitlementState {
    /** The source has not yet checked the account. Consumers must not infer that ads are allowed. */
    data object Loading : EntitlementState

    /** The account was resolved successfully; the set may be empty. */
    data class Available(
        val active: Set<BillingEntitlementId>,
    ) : EntitlementState

    /** The account could not be resolved. Consumers should choose a conservative policy. */
    data class Unavailable(
        val failure: BillingFailure,
    ) : EntitlementState
}

/**
 * A small application-owned entitlement source for custom storefronts or server-backed accounts.
 * It is also useful when an app has billing but no Google Play dependency.
 */
class MutableEntitlementProvider(
    initial: EntitlementState = EntitlementState.Loading,
) : EntitlementProvider {
    private val mutableEntitlements = MutableStateFlow(initial)

    override val entitlements: StateFlow<EntitlementState> = mutableEntitlements.asStateFlow()

    fun setAvailable(active: Set<BillingEntitlementId>) {
        mutableEntitlements.value = EntitlementState.Available(active.toSet())
    }

    fun setUnavailable(failure: BillingFailure) {
        mutableEntitlements.value = EntitlementState.Unavailable(failure)
    }

    fun setLoading() {
        mutableEntitlements.value = EntitlementState.Loading
    }
}

/** A normalized, verification-ready purchase. Never log [token], [originalJson], or [signature]. */
data class BillingPurchase(
    val token: String,
    val productIds: Set<BillingProductId>,
    val purchaseTimeMillis: Long,
    val originalJson: String,
    val signature: String,
    val acknowledged: Boolean,
)

/**
 * Verifies a completed purchase before it grants entitlements or is acknowledged/consumed.
 * A production implementation normally delegates to the application's secure backend.
 */
fun interface PurchaseVerifier {
    suspend fun verify(purchase: BillingPurchase): PurchaseVerification
}

sealed interface PurchaseVerification {
    /** The backend has verified and granted the purchase; client-side finalization may continue. */
    data object Verified : PurchaseVerification

    /** The backend conclusively rejected the purchase. No entitlement is granted. */
    data class Rejected(
        val reason: String,
    ) : PurchaseVerification

    /** A temporary backend failure. The SDK leaves the purchase unacknowledged for a later retry. */
    data class RetryLater(
        val failure: BillingFailure,
    ) : PurchaseVerification
}

sealed interface BillingPurchaseEvent {
    data class Pending(
        val productIds: Set<BillingProductId>,
    ) : BillingPurchaseEvent

    /**
     * A verified purchase was reconciled and finalized with Google Play.
     *
     * This is not an exactly-once delivery callback: it can be emitted again when [BillingStore]
     * refreshes an already-owned non-consumable or subscription. Grant one-time content only in
     * the verifier/backend, where the purchase token is deduplicated.
     */
    data class Reconciled(
        val purchase: BillingPurchase,
    ) : BillingPurchaseEvent

    data object Cancelled : BillingPurchaseEvent

    data class Failed(
        val failure: BillingFailure,
    ) : BillingPurchaseEvent
}

/** Common observable output shared by billing implementations. */
interface BillingStore : EntitlementProvider {
    val products: StateFlow<Map<BillingProductId, BillingProductDetails>>
    val purchaseEvents: SharedFlow<BillingPurchaseEvent>
}

private fun String.requireIdentifier(label: String): String {
    require(isNotBlank()) { "$label must not be blank." }
    require(this == trim()) { "$label must not have leading or trailing whitespace." }
    return this
}
