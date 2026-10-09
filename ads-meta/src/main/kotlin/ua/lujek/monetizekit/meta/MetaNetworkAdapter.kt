package ua.lujek.monetizekit.meta

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSettings
import com.facebook.ads.AdSize
import com.facebook.ads.AdView
import com.facebook.ads.AudienceNetworkAds
import com.facebook.ads.InterstitialAd
import com.facebook.ads.InterstitialAdListener
import com.facebook.ads.MediaView
import com.facebook.ads.NativeAd
import com.facebook.ads.NativeAdListener
import com.facebook.ads.NativeAdLayout
import com.facebook.ads.RewardedVideoAd
import com.facebook.ads.RewardedVideoAdListener
import kotlinx.coroutines.CancellableContinuation
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

internal class MetaNetworkAdapter : AdsNetworkAdapter {
    override val network: AdsNetwork = AdsNetwork.META
    override val supportedFormats: Set<AdFormat> = setOf(
        AdFormat.BANNER,
        AdFormat.INTERSTITIAL,
        AdFormat.REWARDED,
        AdFormat.NATIVE,
    )

    private var application: Application? = null
    private val interstitials = ConcurrentHashMap<String, MetaInterstitialEntry>()
    private val rewardedVideos = ConcurrentHashMap<String, MetaRewardedEntry>()

    override suspend fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ): NetworkInitializationResult {
        val meta = configuration.meta
            ?: return NetworkInitializationResult.Failed(error("Meta configuration is missing."))
        this.application = application
        meta.testDeviceHashes.forEach(AdSettings::addTestDevice)
        AudienceNetworkAds.initialize(application)
        return NetworkInitializationResult.Ready
    }

    override suspend fun loadFullscreen(
        placement: FullscreenPlacement,
        adUnitId: String,
    ): AdLoadResult = when (placement) {
        is InterstitialPlacement -> interstitials.getOrPut(adUnitId) {
            MetaInterstitialEntry(requireNotNull(application), adUnitId)
        }.load()

        is RewardedPlacement -> rewardedVideos.getOrPut(adUnitId) {
            MetaRewardedEntry(requireNotNull(application), adUnitId)
        }.load()
    }

    override suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
    ): AdShowResult = when (placement) {
        is InterstitialPlacement -> interstitials.remove(adUnitId)?.show()
            ?: AdShowResult.Failed(error("Meta interstitial is not loaded."))

        is RewardedPlacement -> rewardedVideos.remove(adUnitId)?.show(onReward)
            ?: AdShowResult.Failed(error("Meta rewarded ad is not loaded."))
    }

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
    ): BannerAdController = MetaBannerController(context, adUnitId)

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
    ): NativeAdController = MetaNativeController(context, adUnitId)

    private fun error(message: String): AdsError = AdsError(network = network, message = message)
}

private class MetaNativeController(
    context: Context,
    adUnitId: String,
) : NativeAdListener, NativeAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val nativeAd = NativeAd(context, adUnitId)
    private val nativeAdLayout = NativeAdLayout(context)
    private val headlineView = TextView(context)
    private val bodyView = TextView(context)
    private val callToActionView = TextView(context)
    private val mediaView = MediaView(context)

    init {
        nativeAdLayout.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(mediaView)
                addView(headlineView)
                addView(bodyView)
                addView(callToActionView)
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    override val view: View = nativeAdLayout
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        nativeAd.loadAd(nativeAd.buildLoadAdConfig().withAdListener(this).build())
    }

    override fun onAdLoaded(ad: Ad) {
        headlineView.text = nativeAd.advertiserName
        bodyView.text = nativeAd.adBodyText.orEmpty()
        callToActionView.text = nativeAd.adCallToAction.orEmpty()
        nativeAd.registerViewForInteraction(nativeAdLayout, mediaView, listOf(callToActionView))
        mutableState.value = AdViewState.Loaded
    }

    override fun onError(ad: Ad?, adError: AdError) {
        mutableState.value = AdViewState.Failed(AdsError(network = AdsNetwork.META, message = adError.errorMessage))
    }

    override fun onAdClicked(ad: Ad) = Unit

    override fun onLoggingImpression(ad: Ad) = Unit

    override fun onMediaDownloaded(ad: Ad) = Unit

    override fun destroy() {
        nativeAd.unregisterView()
        nativeAd.destroy()
    }
}

private class MetaInterstitialEntry(
    context: Context,
    adUnitId: String,
) : InterstitialAdListener {
    private val ad = InterstitialAd(context, adUnitId)
    private var loadContinuation: CancellableContinuation<AdLoadResult>? = null
    private var showContinuation: CancellableContinuation<AdShowResult>? = null

    suspend fun load(): AdLoadResult {
        if (ad.isAdLoaded) return AdLoadResult.Loaded
        return suspendCancellableCoroutine { continuation ->
            loadContinuation = continuation
            ad.loadAd(ad.buildLoadAdConfig().withAdListener(this).build())
        }
    }

    suspend fun show(): AdShowResult = suspendCancellableCoroutine { continuation ->
        showContinuation = continuation
        if (!ad.show()) {
            showContinuation = null
            continuation.resume(AdShowResult.Failed(error("Meta interstitial could not be shown.")))
        }
    }

    override fun onAdLoaded(ad: Ad) {
        loadContinuation?.takeIf { it.isActive }?.resume(AdLoadResult.Loaded)
        loadContinuation = null
    }

    override fun onError(ad: Ad?, adError: AdError) = fail(adError.errorMessage)

    override fun onInterstitialDisplayed(ad: Ad) = Unit

    override fun onInterstitialDismissed(ad: Ad) {
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Shown)
        showContinuation = null
        this.ad.destroy()
    }

    override fun onAdClicked(ad: Ad) = Unit

    override fun onLoggingImpression(ad: Ad) = Unit

    private fun fail(message: String) {
        val failure = error(message)
        loadContinuation?.takeIf { it.isActive }?.resume(AdLoadResult.Failed(failure))
        loadContinuation = null
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Failed(failure))
        showContinuation = null
    }

    private fun error(message: String): AdsError = AdsError(network = AdsNetwork.META, message = message)
}

private class MetaRewardedEntry(
    context: Context,
    adUnitId: String,
) : RewardedVideoAdListener {
    private val ad = RewardedVideoAd(context, adUnitId)
    private var loadContinuation: CancellableContinuation<AdLoadResult>? = null
    private var showContinuation: CancellableContinuation<AdShowResult>? = null
    private var rewardHandler: ((AdReward) -> Unit)? = null

    suspend fun load(): AdLoadResult {
        if (ad.isAdLoaded) return AdLoadResult.Loaded
        return suspendCancellableCoroutine { continuation ->
            loadContinuation = continuation
            ad.loadAd(ad.buildLoadAdConfig().withAdListener(this).build())
        }
    }

    suspend fun show(onReward: (AdReward) -> Unit): AdShowResult = suspendCancellableCoroutine { continuation ->
        showContinuation = continuation
        rewardHandler = onReward
        if (!ad.show()) {
            showContinuation = null
            rewardHandler = null
            continuation.resume(AdShowResult.Failed(error("Meta rewarded ad could not be shown.")))
        }
    }

    override fun onAdLoaded(ad: Ad) {
        loadContinuation?.takeIf { it.isActive }?.resume(AdLoadResult.Loaded)
        loadContinuation = null
    }

    override fun onError(ad: Ad?, adError: AdError) = fail(adError.errorMessage)

    override fun onRewardedVideoCompleted() {
        rewardHandler?.invoke(AdReward())
    }

    override fun onRewardedVideoClosed() {
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Shown)
        showContinuation = null
        rewardHandler = null
        ad.destroy()
    }

    override fun onAdClicked(ad: Ad) = Unit

    override fun onLoggingImpression(ad: Ad) = Unit

    private fun fail(message: String) {
        val failure = error(message)
        loadContinuation?.takeIf { it.isActive }?.resume(AdLoadResult.Failed(failure))
        loadContinuation = null
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Failed(failure))
        showContinuation = null
        rewardHandler = null
    }

    private fun error(message: String): AdsError = AdsError(network = AdsNetwork.META, message = message)
}

private class MetaBannerController(
    context: Context,
    adUnitId: String,
) : BannerAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val listener = object : AdListener {
        override fun onError(ad: Ad?, adError: AdError) {
            mutableState.value = AdViewState.Failed(AdsError(network = AdsNetwork.META, message = adError.errorMessage))
        }

        override fun onAdLoaded(ad: Ad) {
            mutableState.value = AdViewState.Loaded
        }

        override fun onAdClicked(ad: Ad) = Unit

        override fun onLoggingImpression(ad: Ad) = Unit
    }
    private val adView = AdView(context, adUnitId, AdSize.BANNER_HEIGHT_50)

    override val view: View = adView
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        adView.loadAd(adView.buildLoadAdConfig().withAdListener(listener).build())
    }

    override fun destroy() {
        adView.destroy()
    }
}
