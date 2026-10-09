package ua.lujek.monetizekit.unity

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ua.lujek.monetizekit.core.AdLoadResult
import ua.lujek.monetizekit.core.AdFormat
import ua.lujek.monetizekit.core.AdReward
import ua.lujek.monetizekit.core.AdShowResult
import ua.lujek.monetizekit.core.AdsConfiguration
import ua.lujek.monetizekit.core.AdsError
import ua.lujek.monetizekit.core.AdsNetwork
import ua.lujek.monetizekit.core.AdsNetworkAdapter
import ua.lujek.monetizekit.core.BannerAdController
import ua.lujek.monetizekit.core.AdViewState
import ua.lujek.monetizekit.core.BannerPlacement
import ua.lujek.monetizekit.core.FullscreenPlacement
import ua.lujek.monetizekit.core.InterstitialPlacement
import ua.lujek.monetizekit.core.NetworkInitializationResult
import ua.lujek.monetizekit.core.NativeAdController
import ua.lujek.monetizekit.core.NativePlacement
import ua.lujek.monetizekit.core.RewardedPlacement
import kotlin.coroutines.resume

internal class UnityNetworkAdapter : AdsNetworkAdapter {
    override val network: AdsNetwork = AdsNetwork.UNITY
    override val supportedFormats: Set<AdFormat> = setOf(
        AdFormat.BANNER,
        AdFormat.INTERSTITIAL,
        AdFormat.REWARDED,
    )

    private val loadedUnitIds = mutableSetOf<String>()

    override suspend fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ): NetworkInitializationResult {
        val unity = configuration.unity
            ?: return NetworkInitializationResult.Failed(error("Unity configuration is missing."))
        if (UnityAds.isInitialized()) return NetworkInitializationResult.Ready
        return suspendCancellableCoroutine { continuation ->
            UnityAds.initialize(
                application,
                unity.gameId,
                unity.isTestMode,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        if (continuation.isActive) continuation.resume(NetworkInitializationResult.Ready)
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError,
                        message: String,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(NetworkInitializationResult.Failed(error("$error: $message")))
                        }
                    }
                },
            )
        }
    }

    override suspend fun loadFullscreen(
        placement: FullscreenPlacement,
        adUnitId: String,
    ): AdLoadResult {
        if (adUnitId in loadedUnitIds) return AdLoadResult.Loaded
        return suspendCancellableCoroutine { continuation ->
            UnityAds.load(
                adUnitId,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String) {
                        loadedUnitIds += placementId
                        if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String,
                        error: UnityAds.UnityAdsLoadError,
                        message: String,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(AdLoadResult.Failed(error("$error: $message")))
                        }
                    }
                },
            )
        }
    }

    override suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
    ): AdShowResult {
        if (!loadedUnitIds.remove(adUnitId)) {
            return AdShowResult.Failed(error("Unity ad is not loaded."))
        }
        return suspendCancellableCoroutine { continuation ->
            UnityAds.show(
                activity,
                adUnitId,
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String,
                        error: UnityAds.UnityAdsShowError,
                        message: String,
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(AdShowResult.Failed(error("$error: $message")))
                        }
                    }

                    override fun onUnityAdsShowStart(placementId: String) = Unit

                    override fun onUnityAdsShowClick(placementId: String) = Unit

                    override fun onUnityAdsShowComplete(
                        placementId: String,
                        state: UnityAds.UnityAdsShowCompletionState,
                    ) {
                        if (placement is RewardedPlacement && state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                            onReward(AdReward())
                        }
                        if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                    }
                },
            )
        }
    }

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
    ): BannerAdController? {
        val activity = context.findActivity() ?: return null
        return UnityBannerController(activity, adUnitId)
    }

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
    ): NativeAdController? = null

    private fun error(message: String): AdsError = AdsError(network = network, message = message)
}

private class UnityBannerController(
    activity: Activity,
    adUnitId: String,
) : BannerAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val bannerView = BannerView(activity, adUnitId, UnityBannerSize.getDynamicSize(activity)).apply {
        listener = object : BannerView.Listener() {
            override fun onBannerLoaded(bannerAdView: BannerView) {
                mutableState.value = AdViewState.Loaded
            }

            override fun onBannerFailedToLoad(bannerAdView: BannerView, errorInfo: BannerErrorInfo) {
                mutableState.value = AdViewState.Failed(
                    AdsError(network = AdsNetwork.UNITY, message = errorInfo.errorMessage),
                )
            }
        }
    }

    override val view: View = bannerView
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        bannerView.load()
    }

    override fun destroy() {
        bannerView.destroy()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
