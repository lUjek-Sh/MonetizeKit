package ua.lujek.monetizekit.unity

import android.app.Activity
import android.app.Application
import android.content.Context
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class UnityNetworkAdapter : AdsNetworkAdapter {
    override val network: AdsNetwork = AdsNetwork.UNITY
    override val supportedFormats: Set<AdFormat> = setOf(
        AdFormat.BANNER,
        AdFormat.INTERSTITIAL,
        AdFormat.REWARDED,
    )

    private val interstitials = ConcurrentHashMap<String, InterstitialAd>()
    private val rewardedAds = ConcurrentHashMap<String, RewardedAd>()

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
    ): AdLoadResult = when (placement) {
        is InterstitialPlacement -> loadInterstitial(adUnitId)
        is RewardedPlacement -> loadRewarded(adUnitId)
    }

    override suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
    ): AdShowResult = when (placement) {
        is InterstitialPlacement -> showInterstitial(activity, adUnitId)
        is RewardedPlacement -> showRewarded(activity, adUnitId, onReward)
    }

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
    ): BannerAdController = UnityBannerController(context, adUnitId)

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
    ): NativeAdController? = null

    override fun clearFullscreenCache() {
        interstitials.clear()
        rewardedAds.clear()
    }

    private suspend fun loadInterstitial(adUnitId: String): AdLoadResult {
        if (interstitials.containsKey(adUnitId)) return AdLoadResult.Loaded
        return suspendCancellableCoroutine { continuation ->
            InterstitialAd.load(
                LoadConfiguration.Builder(adUnitId).build(),
            ) { ad, loadError ->
                if (ad != null) {
                    ad.onAdExpired = AdExpiredListener { expiredAd ->
                        interstitials.remove(adUnitId, expiredAd)
                    }
                    interstitials[adUnitId] = ad
                    if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                } else if (continuation.isActive) {
                    continuation.resume(AdLoadResult.Failed(error(loadError?.message ?: "Unity interstitial failed to load.")))
                }
            }
        }
    }

    private suspend fun loadRewarded(adUnitId: String): AdLoadResult {
        if (rewardedAds.containsKey(adUnitId)) return AdLoadResult.Loaded
        return suspendCancellableCoroutine { continuation ->
            RewardedAd.load(
                LoadConfiguration.Builder(adUnitId).build(),
            ) { ad, loadError ->
                if (ad != null) {
                    ad.onAdExpired = AdExpiredListener { expiredAd ->
                        rewardedAds.remove(adUnitId, expiredAd)
                    }
                    rewardedAds[adUnitId] = ad
                    if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                } else if (continuation.isActive) {
                    continuation.resume(AdLoadResult.Failed(error(loadError?.message ?: "Unity rewarded ad failed to load.")))
                }
            }
        }
    }

    private suspend fun showInterstitial(activity: Activity, adUnitId: String): AdShowResult {
        val ad = interstitials.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Unity interstitial is not loaded."))
        return suspendCancellableCoroutine { continuation ->
            ad.show(
                activity,
                ShowConfiguration.Builder().build(),
                object : InterstitialShowListener {
                    override fun onStarted(unityAd: InterstitialAd) = Unit

                    override fun onClicked(unityAd: InterstitialAd) = Unit

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
        adUnitId: String,
        onReward: (AdReward) -> Unit,
    ): AdShowResult {
        val ad = rewardedAds.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Unity rewarded ad is not loaded."))
        return suspendCancellableCoroutine { continuation ->
            ad.show(
                activity,
                ShowConfiguration.Builder().build(),
                object : RewardedShowListener {
                    override fun onStarted(unityAd: RewardedAd) = Unit

                    override fun onClicked(unityAd: RewardedAd) = Unit

                    override fun onCompleted(unityAd: RewardedAd, state: ShowFinishState) {
                        if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                    }

                    override fun onFailed(unityAd: RewardedAd, error: UnityAdsError) {
                        if (continuation.isActive) {
                            continuation.resume(AdShowResult.Failed(this@UnityNetworkAdapter.error(error.message)))
                        }
                    }

                    override fun onRewarded(rewardedAd: RewardedAd) {
                        onReward(AdReward())
                    }
                },
            )
        }
    }

    private fun error(message: String): AdsError = AdsError(network = network, message = message)
}

private class UnityBannerController(
    context: Context,
    private val adUnitId: String,
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
            override fun onImpression(banner: BannerAd) = Unit

            override fun onClicked(banner: BannerAd) = Unit

            override fun onFailedToShow(banner: BannerAd, error: UnityAdsError) {
                mutableState.value = AdViewState.Failed(
                    AdsError(network = AdsNetwork.UNITY, message = error.message),
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
            } else {
                mutableState.value = AdViewState.Failed(
                    AdsError(network = AdsNetwork.UNITY, message = loadError?.message ?: "Unity banner failed to load."),
                )
            }
        }
    }

    override fun destroy() {
        bannerAd = null
        container.removeAllViews()
    }
}
