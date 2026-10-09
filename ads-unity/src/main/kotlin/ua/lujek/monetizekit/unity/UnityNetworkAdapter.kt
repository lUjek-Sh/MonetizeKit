package ua.lujek.monetizekit.unity

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.unity3d.ads.AdExpiredListener
import com.unity3d.ads.BannerAd
import com.unity3d.ads.BannerConfiguration
import com.unity3d.ads.BannerShowListener
import com.unity3d.ads.BannerSize
import com.unity3d.ads.InitializationConfiguration
import com.unity3d.ads.InitializationListener
import com.unity3d.ads.InterstitialAd
import com.unity3d.ads.InterstitialShowListener
import com.unity3d.ads.LoadConfiguration
import com.unity3d.ads.RewardedAd
import com.unity3d.ads.RewardedShowListener
import com.unity3d.ads.ShowConfiguration
import com.unity3d.ads.ShowFinishState
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsError
import com.unity3d.ads.UnityAdsExperimental
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ua.lujek.monetizekit.core.AdFormat
import ua.lujek.monetizekit.core.AdLoadResult
import ua.lujek.monetizekit.core.AdReward
import ua.lujek.monetizekit.core.AdShowResult
import ua.lujek.monetizekit.core.AdViewState
import ua.lujek.monetizekit.core.AdsConfiguration
import ua.lujek.monetizekit.core.AdsEvent
import ua.lujek.monetizekit.core.AdsEventReporter
import ua.lujek.monetizekit.core.AdsError
import ua.lujek.monetizekit.core.AdsNetwork
import ua.lujek.monetizekit.core.AdsNetworkAdapter
import ua.lujek.monetizekit.core.BannerAdController
import ua.lujek.monetizekit.core.BannerPlacement
import ua.lujek.monetizekit.core.FullscreenPlacement
import ua.lujek.monetizekit.core.InterstitialPlacement
import ua.lujek.monetizekit.core.NativeAdController
import ua.lujek.monetizekit.core.NativePlacement
import ua.lujek.monetizekit.core.NetworkInitializationResult
import ua.lujek.monetizekit.core.RewardedPlacement
import ua.lujek.monetizekit.core.isFullscreenAdExpired
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class UnityNetworkAdapter : AdsNetworkAdapter {
    override val network: AdsNetwork = AdsNetwork.UNITY
    override val supportedFormats: Set<AdFormat> = setOf(
        AdFormat.BANNER,
        AdFormat.INTERSTITIAL,
        AdFormat.REWARDED,
    )

    private val interstitials = ConcurrentHashMap<String, CachedFullscreenAd<InterstitialAd>>()
    private val rewardedAds = ConcurrentHashMap<String, CachedFullscreenAd<RewardedAd>>()

    @OptIn(UnityAdsExperimental::class)
    override suspend fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ): NetworkInitializationResult {
        val unity = configuration.unity
            ?: return NetworkInitializationResult.Failed(error("Unity configuration is missing."))
        if (UnityAds.isInitialized) return NetworkInitializationResult.Ready
        return suspendCancellableCoroutine { continuation ->
            val initializationConfiguration = InitializationConfiguration.Builder(unity.gameId)
                .withTestMode(unity.isTestMode)
                .build()
            UnityAds.initialize(
                initializationConfiguration,
                InitializationListener { initializationError ->
                    if (!continuation.isActive) return@InitializationListener
                    if (initializationError == null) {
                        continuation.resume(NetworkInitializationResult.Ready)
                    } else {
                        continuation.resume(NetworkInitializationResult.Failed(error(initializationError.message)))
                    }
                },
            )
        }
    }

    override suspend fun loadFullscreen(
        placement: FullscreenPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdLoadResult = when (placement) {
        is InterstitialPlacement -> loadInterstitial(adUnitId, placement, eventReporter)
        is RewardedPlacement -> loadRewarded(adUnitId, placement, eventReporter)
        else -> AdLoadResult.Failed(error("Unity does not support ${placement.format}."))
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
        else -> AdShowResult.Failed(error("Unity does not support ${placement.format}."))
    }

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): BannerAdController = UnityBannerController(context, placement, adUnitId, eventReporter)

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): NativeAdController? = null

    override fun clearFullscreenCache() {
        interstitials.clear()
        rewardedAds.clear()
    }

    private suspend fun loadInterstitial(
        adUnitId: String,
        placement: InterstitialPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        if (interstitials.fresh(adUnitId) != null) return AdLoadResult.Loaded
        return suspendCancellableCoroutine { continuation ->
            InterstitialAd.load(
                LoadConfiguration.Builder(adUnitId).build(),
            ) { ad, loadError ->
                if (ad != null) {
                    val cachedAd = CachedFullscreenAd(ad, SystemClock.elapsedRealtime())
                    ad.onAdExpired = AdExpiredListener { expiredAd ->
                        if (cachedAd.ad === expiredAd) interstitials.remove(adUnitId, cachedAd)
                    }
                    interstitials[adUnitId] = cachedAd
                    eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.UNITY))
                    if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                } else if (continuation.isActive) {
                    val failure = error(loadError?.message ?: "Unity interstitial failed to load.")
                    eventReporter.report(AdsEvent.AdLoadFailed(placement, failure))
                    continuation.resume(AdLoadResult.Failed(failure))
                }
            }
        }
    }

    private suspend fun loadRewarded(
        adUnitId: String,
        placement: RewardedPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        if (rewardedAds.fresh(adUnitId) != null) return AdLoadResult.Loaded
        return suspendCancellableCoroutine { continuation ->
            RewardedAd.load(
                LoadConfiguration.Builder(adUnitId).build(),
            ) { ad, loadError ->
                if (ad != null) {
                    val cachedAd = CachedFullscreenAd(ad, SystemClock.elapsedRealtime())
                    ad.onAdExpired = AdExpiredListener { expiredAd ->
                        if (cachedAd.ad === expiredAd) rewardedAds.remove(adUnitId, cachedAd)
                    }
                    rewardedAds[adUnitId] = cachedAd
                    eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.UNITY))
                    if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                } else if (continuation.isActive) {
                    val failure = error(loadError?.message ?: "Unity rewarded ad failed to load.")
                    eventReporter.report(AdsEvent.AdLoadFailed(placement, failure))
                    continuation.resume(AdLoadResult.Failed(failure))
                }
            }
        }
    }

    private suspend fun showInterstitial(
        activity: Activity,
        placement: InterstitialPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdShowResult {
        val cachedAd = interstitials.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Unity interstitial is not loaded."))
        if (cachedAd.isExpired()) return AdShowResult.Failed(error("Unity interstitial expired before it could be shown."))
        val ad = cachedAd.ad
        return suspendCancellableCoroutine { continuation ->
            ad.show(
                activity,
                ShowConfiguration.Builder().build(),
                object : InterstitialShowListener {
                    override fun onStarted(unityAd: InterstitialAd) {
                        eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.UNITY))
                    }

                    override fun onClicked(unityAd: InterstitialAd) {
                        eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.UNITY))
                    }

                    override fun onCompleted(unityAd: InterstitialAd, state: ShowFinishState) {
                        if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                    }

                    override fun onFailed(unityAd: InterstitialAd, error: UnityAdsError) {
                        if (continuation.isActive) {
                            continuation.resume(AdShowResult.Failed(this@UnityNetworkAdapter.error(error.message)))
                        }
                    }
                },
            )
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
            ?: return AdShowResult.Failed(error("Unity rewarded ad is not loaded."))
        if (cachedAd.isExpired()) return AdShowResult.Failed(error("Unity rewarded ad expired before it could be shown."))
        val ad = cachedAd.ad
        return suspendCancellableCoroutine { continuation ->
            ad.show(
                activity,
                ShowConfiguration.Builder().build(),
                object : RewardedShowListener {
                    override fun onStarted(unityAd: RewardedAd) {
                        eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.UNITY))
                    }

                    override fun onClicked(unityAd: RewardedAd) {
                        eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.UNITY))
                    }

                    override fun onCompleted(unityAd: RewardedAd, state: ShowFinishState) {
                        if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                    }

                    override fun onFailed(unityAd: RewardedAd, error: UnityAdsError) {
                        if (continuation.isActive) {
                            continuation.resume(AdShowResult.Failed(this@UnityNetworkAdapter.error(error.message)))
                        }
                    }

                    override fun onRewarded(rewardedAd: RewardedAd) {
                        val reward = AdReward()
                        eventReporter.report(AdsEvent.AdRewardEarned(placement, AdsNetwork.UNITY, reward))
                        onReward(reward)
                    }
                },
            )
        }
    }

    private fun error(message: String): AdsError = AdsError(network = network, message = message)
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

private class UnityBannerController(
    context: Context,
    private val placement: BannerPlacement,
    private val adUnitId: String,
    private val eventReporter: AdsEventReporter,
) : BannerAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val container = FrameLayout(context)
    private var bannerAd: BannerAd? = null

    override val view: View = container
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        val bannerListener = object : BannerShowListener {
            override fun onImpression(banner: BannerAd) {
                eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.UNITY))
            }

            override fun onClicked(banner: BannerAd) {
                eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.UNITY))
            }

            override fun onFailedToShow(banner: BannerAd, error: UnityAdsError) {
                mutableState.value = AdViewState.Failed(
                    AdsError(network = AdsNetwork.UNITY, message = error.message),
                )
                eventReporter.report(
                    AdsEvent.AdLoadFailed(
                        placement,
                        AdsError(network = AdsNetwork.UNITY, message = error.message),
                    ),
                )
            }
        }
        BannerAd.load(
            BannerConfiguration.Builder(adUnitId, BannerSize.standard, bannerListener).build(),
        ) { loadedAd, loadError ->
            if (loadedAd != null) {
                bannerAd = loadedAd
                loadedAd.onAdExpired = AdExpiredListener { expiredAd ->
                    if (bannerAd === expiredAd) {
                        bannerAd = null
                        container.removeAllViews()
                        mutableState.value = AdViewState.Failed(
                            AdsError(network = AdsNetwork.UNITY, message = "Unity banner expired."),
                        )
                    }
                }
                container.removeAllViews()
                container.addView(loadedAd.view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                mutableState.value = AdViewState.Loaded
                eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.UNITY))
            } else {
                val failure = AdsError(
                    network = AdsNetwork.UNITY,
                    message = loadError?.message ?: "Unity banner failed to load.",
                )
                mutableState.value = AdViewState.Failed(failure)
                eventReporter.report(AdsEvent.AdLoadFailed(placement, failure))
            }
        }
    }

    override fun destroy() {
        bannerAd = null
        container.removeAllViews()
    }
}
