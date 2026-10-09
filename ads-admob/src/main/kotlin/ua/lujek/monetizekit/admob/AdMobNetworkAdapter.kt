package ua.lujek.monetizekit.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.nativead.MediaView
import com.google.ads.mediation.admob.AdMobAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ua.lujek.monetizekit.core.AdLoadResult
import ua.lujek.monetizekit.core.AdPlacement
import ua.lujek.monetizekit.core.AdFormat
import ua.lujek.monetizekit.core.AdReward
import ua.lujek.monetizekit.core.AdShowResult
import ua.lujek.monetizekit.core.AdsConfiguration
import ua.lujek.monetizekit.core.AdsEvent
import ua.lujek.monetizekit.core.AdsEventReporter
import ua.lujek.monetizekit.core.AdsError
import ua.lujek.monetizekit.core.AdsNetwork
import ua.lujek.monetizekit.core.AdsNetworkAdapter
import ua.lujek.monetizekit.core.BannerAdController
import ua.lujek.monetizekit.core.AdViewState
import ua.lujek.monetizekit.core.AdDiagnostics
import ua.lujek.monetizekit.core.AdRevenue
import ua.lujek.monetizekit.core.AdRevenuePrecision
import ua.lujek.monetizekit.core.BannerPlacement
import ua.lujek.monetizekit.core.BannerSize
import ua.lujek.monetizekit.core.AppOpenPlacement
import ua.lujek.monetizekit.core.CollapsibleBannerAnchor
import ua.lujek.monetizekit.core.DefaultNativeAdRenderer
import ua.lujek.monetizekit.core.FullscreenPlacement
import ua.lujek.monetizekit.core.InterstitialPlacement
import ua.lujek.monetizekit.core.NetworkInitializationResult
import ua.lujek.monetizekit.core.NativeAdController
import ua.lujek.monetizekit.core.NativeAdContent
import ua.lujek.monetizekit.core.NativeAdAppearance
import ua.lujek.monetizekit.core.NativePlacement
import ua.lujek.monetizekit.core.RewardedPlacement
import ua.lujek.monetizekit.core.RewardedInterstitialPlacement
import ua.lujek.monetizekit.core.UsStatePrivacyMode
import ua.lujek.monetizekit.core.isFullscreenAdExpired
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class AdMobNetworkAdapter : AdsNetworkAdapter {
    override val network: AdsNetwork = AdsNetwork.ADMOB
    override val supportedFormats: Set<AdFormat> = setOf(
        AdFormat.BANNER,
        AdFormat.INTERSTITIAL,
        AdFormat.REWARDED,
        AdFormat.REWARDED_INTERSTITIAL,
        AdFormat.NATIVE,
        AdFormat.APP_OPEN,
    )

    private var application: Application? = null
    private var nativeAdAppearance: NativeAdAppearance = NativeAdAppearance()
    private val interstitials = ConcurrentHashMap<String, CachedFullscreenAd<InterstitialAd>>()
    private val rewardedAds = ConcurrentHashMap<String, CachedFullscreenAd<RewardedAd>>()
    private val rewardedInterstitialAds = ConcurrentHashMap<String, CachedFullscreenAd<RewardedInterstitialAd>>()
    private val appOpenAds = ConcurrentHashMap<String, CachedFullscreenAd<AppOpenAd>>()

    override suspend fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ): NetworkInitializationResult {
        val adMob = configuration.adMob
            ?: return NetworkInitializationResult.Failed(error("AdMob configuration is missing."))
        val manifestApplicationId = application.adMobApplicationId()
        if (manifestApplicationId != adMob.applicationId) {
            return NetworkInitializationResult.Failed(
                error(
                    "AdMob application id in MonetizeKit configuration must match " +
                        "com.google.android.gms.ads.APPLICATION_ID in AndroidManifest.xml.",
                ),
            )
        }
        this.application = application
        nativeAdAppearance = configuration.nativeAdAppearance
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(adMob.testDeviceIds.toList())
                .applyAgeRestrictedTreatment(configuration)
                .build(),
        )
        application.applyUsStatePrivacy(configuration)
        return suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(application) {
                if (continuation.isActive) continuation.resume(NetworkInitializationResult.Ready)
            }
        }
    }

    override suspend fun loadFullscreen(
        placement: FullscreenPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdLoadResult = when (placement) {
        is InterstitialPlacement -> loadInterstitial(adUnitId, placement, eventReporter)
        is RewardedPlacement -> loadRewarded(adUnitId, placement, eventReporter)
        is RewardedInterstitialPlacement -> loadRewardedInterstitial(adUnitId, placement, eventReporter)
        is AppOpenPlacement -> loadAppOpen(adUnitId, placement, eventReporter)
    }

    override suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
        eventReporter: AdsEventReporter,
    ): AdShowResult = when (placement) {
        is InterstitialPlacement -> showInterstitial(activity, placement, adUnitId, eventReporter)
        is RewardedPlacement -> showRewarded(activity, placement, adUnitId, onReward, eventReporter)
        is RewardedInterstitialPlacement -> {
            showRewardedInterstitial(activity, placement, adUnitId, onReward, eventReporter)
        }
        is AppOpenPlacement -> showAppOpen(activity, placement, adUnitId, eventReporter)
    }

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): BannerAdController = AdMobBannerController(context, placement, adUnitId, eventReporter)

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): NativeAdController = AdMobNativeController(
        context = context,
        placement = placement,
        adUnitId = adUnitId,
        eventReporter = eventReporter,
        appearance = nativeAdAppearance,
    )

    override fun clearFullscreenCache() {
        interstitials.clear()
        rewardedAds.clear()
        rewardedInterstitialAds.clear()
        appOpenAds.clear()
    }

    private suspend fun loadInterstitial(
        adUnitId: String,
        placement: InterstitialPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        if (interstitials.fresh(adUnitId) != null) return AdLoadResult.Loaded
        val context = application ?: return AdLoadResult.Failed(error("AdMob is not initialized."))
        return suspendCancellableCoroutine { continuation ->
            InterstitialAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        ad.onPaidEventListener = paidEventListener(placement, eventReporter)
                        interstitials[adUnitId] = CachedFullscreenAd(ad, SystemClock.elapsedRealtime())
                        eventReporter.report(AdsEvent.AdLoaded(placement, network, ad.responseInfo.toDiagnostics()))
                        if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        eventReporter.report(AdsEvent.AdLoadFailed(placement, error(loadAdError)))
                        if (continuation.isActive) {
                            continuation.resume(AdLoadResult.Failed(error(loadAdError)))
                        }
                    }
                },
            )
        }
    }

    private suspend fun loadRewarded(
        adUnitId: String,
        placement: RewardedPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        if (rewardedAds.fresh(adUnitId) != null) return AdLoadResult.Loaded
        val context = application ?: return AdLoadResult.Failed(error("AdMob is not initialized."))
        return suspendCancellableCoroutine { continuation ->
            RewardedAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        ad.onPaidEventListener = paidEventListener(placement, eventReporter)
                        rewardedAds[adUnitId] = CachedFullscreenAd(ad, SystemClock.elapsedRealtime())
                        eventReporter.report(AdsEvent.AdLoaded(placement, network, ad.responseInfo.toDiagnostics()))
                        if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        eventReporter.report(AdsEvent.AdLoadFailed(placement, error(loadAdError)))
                        if (continuation.isActive) {
                            continuation.resume(AdLoadResult.Failed(error(loadAdError)))
                        }
                    }
                },
            )
        }
    }

    private suspend fun loadRewardedInterstitial(
        adUnitId: String,
        placement: RewardedInterstitialPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        if (rewardedInterstitialAds.fresh(adUnitId) != null) return AdLoadResult.Loaded
        val context = application ?: return AdLoadResult.Failed(error("AdMob is not initialized."))
        return suspendCancellableCoroutine { continuation ->
            RewardedInterstitialAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        ad.onPaidEventListener = paidEventListener(placement, eventReporter)
                        rewardedInterstitialAds[adUnitId] = CachedFullscreenAd(ad, SystemClock.elapsedRealtime())
                        eventReporter.report(AdsEvent.AdLoaded(placement, network, ad.responseInfo.toDiagnostics()))
                        if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        val failure = error(loadAdError)
                        eventReporter.report(AdsEvent.AdLoadFailed(placement, failure))
                        if (continuation.isActive) continuation.resume(AdLoadResult.Failed(failure))
                    }
                },
            )
        }
    }

    private suspend fun loadAppOpen(
        adUnitId: String,
        placement: AppOpenPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        if (appOpenAds.fresh(adUnitId) != null) return AdLoadResult.Loaded
        val context = application ?: return AdLoadResult.Failed(error("AdMob is not initialized."))
        return suspendCancellableCoroutine { continuation ->
            AppOpenAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        ad.onPaidEventListener = paidEventListener(placement, eventReporter)
                        appOpenAds[adUnitId] = CachedFullscreenAd(ad, SystemClock.elapsedRealtime())
                        eventReporter.report(AdsEvent.AdLoaded(placement, network, ad.responseInfo.toDiagnostics()))
                        if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        val failure = error(loadAdError)
                        eventReporter.report(AdsEvent.AdLoadFailed(placement, failure))
                        if (continuation.isActive) continuation.resume(AdLoadResult.Failed(failure))
                    }
                },
            )
        }
    }

    private suspend fun showInterstitial(
        activity: Activity,
        placement: InterstitialPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdShowResult {
        val cachedAd = interstitials.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Interstitial ad is not loaded."))
        if (cachedAd.isExpired()) return AdShowResult.Failed(error("Interstitial ad expired before it could be shown."))
        val ad = cachedAd.ad
        return suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdImpression() {
                    eventReporter.report(AdsEvent.AdImpression(placement, network))
                }

                override fun onAdClicked() {
                    eventReporter.report(AdsEvent.AdClicked(placement, network))
                }

                override fun onAdDismissedFullScreenContent() {
                    if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    if (continuation.isActive) continuation.resume(AdShowResult.Failed(error(adError)))
                }
            }
            ad.show(activity)
        }
    }

    private suspend fun showRewarded(
        activity: Activity,
        placement: RewardedPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
        eventReporter: AdsEventReporter,
    ): AdShowResult {
        val cachedAd = rewardedAds.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Rewarded ad is not loaded."))
        if (cachedAd.isExpired()) return AdShowResult.Failed(error("Rewarded ad expired before it could be shown."))
        val ad = cachedAd.ad
        return suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdImpression() {
                    eventReporter.report(AdsEvent.AdImpression(placement, network))
                }

                override fun onAdClicked() {
                    eventReporter.report(AdsEvent.AdClicked(placement, network))
                }

                override fun onAdDismissedFullScreenContent() {
                    if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    if (continuation.isActive) continuation.resume(AdShowResult.Failed(error(adError)))
                }
            }
            ad.show(activity) { rewardItem ->
                val reward = AdReward(amount = rewardItem.amount, type = rewardItem.type)
                eventReporter.report(AdsEvent.AdRewardEarned(placement, network, reward))
                onReward(reward)
            }
        }
    }

    private suspend fun showRewardedInterstitial(
        activity: Activity,
        placement: RewardedInterstitialPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
        eventReporter: AdsEventReporter,
    ): AdShowResult {
        val cachedAd = rewardedInterstitialAds.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Rewarded interstitial ad is not loaded."))
        if (cachedAd.isExpired()) {
            return AdShowResult.Failed(error("Rewarded interstitial ad expired before it could be shown."))
        }
        val ad = cachedAd.ad
        return suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = fullscreenContentCallback(placement, eventReporter, continuation)
            ad.show(activity) { rewardItem ->
                val reward = AdReward(amount = rewardItem.amount, type = rewardItem.type)
                eventReporter.report(AdsEvent.AdRewardEarned(placement, network, reward))
                onReward(reward)
            }
        }
    }

    private suspend fun showAppOpen(
        activity: Activity,
        placement: AppOpenPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdShowResult {
        val cachedAd = appOpenAds.remove(adUnitId)
            ?: return AdShowResult.Failed(error("App Open ad is not loaded."))
        if (cachedAd.isExpired()) return AdShowResult.Failed(error("App Open ad expired before it could be shown."))
        val ad = cachedAd.ad
        return suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = fullscreenContentCallback(placement, eventReporter, continuation)
            ad.show(activity)
        }
    }

    private fun fullscreenContentCallback(
        placement: AdPlacement,
        eventReporter: AdsEventReporter,
        continuation: kotlinx.coroutines.CancellableContinuation<AdShowResult>,
    ): FullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdImpression() {
            eventReporter.report(AdsEvent.AdImpression(placement, network))
        }

        override fun onAdClicked() {
            eventReporter.report(AdsEvent.AdClicked(placement, network))
        }

        override fun onAdDismissedFullScreenContent() {
            if (continuation.isActive) continuation.resume(AdShowResult.Shown)
        }

        override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
            if (continuation.isActive) continuation.resume(AdShowResult.Failed(error(adError)))
        }
    }

    private fun error(message: String): AdsError = AdsError(network = network, message = message)

    private fun error(loadAdError: LoadAdError): AdsError = AdsError(
        network = network,
        message = loadAdError.message,
        code = loadAdError.code.toString(),
        diagnostics = loadAdError.responseInfo.toDiagnostics(),
    )

    private fun error(adError: com.google.android.gms.ads.AdError): AdsError = AdsError(
        network = network,
        message = adError.message,
        code = adError.code.toString(),
    )

    private fun paidEventListener(
        placement: AdPlacement,
        eventReporter: AdsEventReporter,
    ): OnPaidEventListener = OnPaidEventListener { adValue ->
        eventReporter.report(
            AdsEvent.AdPaid(
                placement = placement,
                network = network,
                revenue = adValue.toRevenue(),
            ),
        )
    }
}

private data class CachedFullscreenAd<T>(
    val ad: T,
    val loadedAtElapsedRealtimeMillis: Long,
) {
    fun isExpired(): Boolean = isFullscreenAdExpired(
        loadedAtElapsedRealtimeMillis = loadedAtElapsedRealtimeMillis,
        nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
    )
}

private fun <T> ConcurrentHashMap<String, CachedFullscreenAd<T>>.fresh(adUnitId: String): CachedFullscreenAd<T>? {
    val cachedAd = this[adUnitId] ?: return null
    return cachedAd.takeUnless { it.isExpired() }
        ?: run {
            remove(adUnitId, cachedAd)
            null
        }
}

private class AdMobNativeController(
    private val context: Context,
    private val placement: NativePlacement,
    private val adUnitId: String,
    private val eventReporter: AdsEventReporter,
    private val appearance: NativeAdAppearance,
) : NativeAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private var nativeAd: NativeAd? = null
    private val nativeAdView = NativeAdView(context)

    override val view: View = nativeAdView
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        AdLoader.Builder(context, adUnitId)
            .forNativeAd { loadedAd ->
                nativeAd?.destroy()
                nativeAd = loadedAd
                val binding = placement.renderer?.render(context, loadedAd.toContent())
                    ?: DefaultNativeAdRenderer.render(context, loadedAd.toContent(), appearance)
                require(binding.root.parent == null) {
                    "NativeAdRenderer must return a new unattached root view."
                }
                requireNotNull(binding.headlineView) {
                    "NativeAdRenderer must provide a headlineView for a native ad."
                }
                nativeAdView.removeAllViews()
                nativeAdView.addView(
                    binding.root,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                nativeAdView.headlineView = binding.headlineView
                nativeAdView.bodyView = binding.bodyView
                nativeAdView.callToActionView = binding.callToActionView
                nativeAdView.advertiserView = binding.advertiserView
                nativeAdView.iconView = binding.iconView
                nativeAdView.mediaView = null
                binding.mediaContainer?.let { container ->
                    val mediaView = MediaView(context)
                    container.addView(
                        mediaView,
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                    nativeAdView.mediaView = mediaView
                }
                nativeAdView.setNativeAd(loadedAd)
                mutableState.value = AdViewState.Loaded
                eventReporter.report(
                    AdsEvent.AdLoaded(placement, AdsNetwork.ADMOB, loadedAd.responseInfo.toDiagnostics()),
                )
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdImpression() {
                        eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.ADMOB))
                    }

                    override fun onAdClicked() {
                        eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.ADMOB))
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        mutableState.value = AdViewState.Failed(
                            AdsError(
                                network = AdsNetwork.ADMOB,
                                message = loadAdError.message,
                                code = loadAdError.code.toString(),
                                diagnostics = loadAdError.responseInfo.toDiagnostics(),
                            ),
                        )
                        eventReporter.report(
                            AdsEvent.AdLoadFailed(
                                placement,
                                AdsError(
                                    network = AdsNetwork.ADMOB,
                                    message = loadAdError.message,
                                    code = loadAdError.code.toString(),
                                    diagnostics = loadAdError.responseInfo.toDiagnostics(),
                                ),
                            ),
                        )
                    }
                },
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    override fun destroy() {
        nativeAd?.destroy()
        nativeAd = null
    }

}

private fun NativeAd.toContent(): NativeAdContent = NativeAdContent(
    network = AdsNetwork.ADMOB,
    headline = headline.orEmpty(),
    body = body,
    callToAction = callToAction,
    advertiser = advertiser,
    price = price,
    store = store,
    icon = icon?.drawable,
)

@Suppress("DEPRECATION")
private fun Application.adMobApplicationId(): String? = packageManager
    .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    .metaData
    ?.getString("com.google.android.gms.ads.APPLICATION_ID")

@Suppress("DEPRECATION")
private fun RequestConfiguration.Builder.applyAgeRestrictedTreatment(
    configuration: AdsConfiguration,
): RequestConfiguration.Builder = apply {
    configuration.gdpr.childDirectedTreatment?.let { childDirected ->
        setTagForChildDirectedTreatment(
            if (childDirected) {
                RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
            } else {
                RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE
            },
        )
    }
    configuration.gdpr.underAgeOfConsent?.let { underAgeOfConsent ->
        setTagForUnderAgeOfConsent(
            if (underAgeOfConsent) {
                RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
            } else {
                RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_FALSE
            },
        )
    }
}

private fun Application.applyUsStatePrivacy(configuration: AdsConfiguration) {
    if (configuration.gdpr.usStatePrivacyMode != UsStatePrivacyMode.RESTRICTED_DATA_PROCESSING) return
    getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
        .edit()
        .putInt("gad_rdp", 1)
        .apply()
}

private class AdMobBannerController(
    context: Context,
    private val placement: BannerPlacement,
    adUnitId: String,
    private val eventReporter: AdsEventReporter,
) : BannerAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val adView = AdView(context).apply {
        setAdSize(placement.size.toAdSize(context))
        this.adUnitId = adUnitId
        onPaidEventListener = OnPaidEventListener { adValue ->
            eventReporter.report(
                AdsEvent.AdPaid(
                    placement = placement,
                    network = AdsNetwork.ADMOB,
                    revenue = adValue.toRevenue(),
                ),
            )
        }
        adListener = object : AdListener() {
            override fun onAdLoaded() {
                mutableState.value = AdViewState.Loaded
                eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.ADMOB, responseInfo.toDiagnostics()))
            }

            override fun onAdImpression() {
                eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.ADMOB))
            }

            override fun onAdClicked() {
                eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.ADMOB))
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                val error = AdsError(
                    network = AdsNetwork.ADMOB,
                    message = loadAdError.message,
                    code = loadAdError.code.toString(),
                    diagnostics = loadAdError.responseInfo.toDiagnostics(),
                )
                mutableState.value = AdViewState.Failed(error)
                eventReporter.report(AdsEvent.AdLoadFailed(placement, error))
            }
        }
    }

    override val view: View = adView
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        adView.loadAd(placement.toAdRequest())
    }

    override fun onPause() {
        adView.pause()
    }

    override fun onResume() {
        adView.resume()
    }

    override fun destroy() {
        adView.destroy()
    }
}

private fun ResponseInfo?.toDiagnostics(): AdDiagnostics? = this?.let { responseInfo ->
    AdDiagnostics(
        responseId = responseInfo.responseId,
        mediationAdapterClassName = responseInfo.mediationAdapterClassName,
    )
}

private fun AdValue.toRevenue(): AdRevenue = AdRevenue(
    valueMicros = valueMicros,
    currencyCode = currencyCode,
    precision = when (precisionType) {
        AdValue.PrecisionType.PRECISE -> AdRevenuePrecision.PRECISE
        AdValue.PrecisionType.PUBLISHER_PROVIDED -> AdRevenuePrecision.PUBLISHER_PROVIDED
        AdValue.PrecisionType.ESTIMATED -> AdRevenuePrecision.ESTIMATED
        else -> AdRevenuePrecision.UNKNOWN
    },
)

private fun BannerSize.toAdSize(context: Context): AdSize {
    val widthDp = context.resources.configuration.screenWidthDp
        .takeIf { it > 0 }
        ?: (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    return when (this) {
        BannerSize.Standard -> AdSize.BANNER
        BannerSize.AnchoredAdaptive -> AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
        is BannerSize.InlineAdaptive -> {
            AdSize.getInlineAdaptiveBannerAdSize(widthDp, maxHeightDp)
        }
    }
}

private fun BannerPlacement.toAdRequest(): AdRequest = AdRequest.Builder().apply {
    collapsibleAnchor?.let { anchor ->
        addNetworkExtrasBundle(
            AdMobAdapter::class.java,
            Bundle().apply {
                putString(
                    "collapsible",
                    when (anchor) {
                        CollapsibleBannerAnchor.TOP -> "top"
                        CollapsibleBannerAnchor.BOTTOM -> "bottom"
                    },
                )
            },
        )
    }
}.build()
