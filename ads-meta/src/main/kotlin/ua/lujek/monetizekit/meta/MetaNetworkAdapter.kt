package ua.lujek.monetizekit.meta

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import ua.lujek.monetizekit.core.NativeAdContent
import ua.lujek.monetizekit.core.NativeAdRenderBinding
import ua.lujek.monetizekit.core.NativePlacement
import ua.lujek.monetizekit.core.NetworkInitializationResult
import ua.lujek.monetizekit.core.RewardedPlacement
import ua.lujek.monetizekit.core.isFullscreenAdExpired
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
        eventReporter: AdsEventReporter,
    ): AdLoadResult = when (placement) {
        is InterstitialPlacement -> loadInterstitial(adUnitId, placement, eventReporter)
        is RewardedPlacement -> loadRewarded(adUnitId, placement, eventReporter)
    }

    override suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
        eventReporter: AdsEventReporter,
    ): AdShowResult = when (placement) {
        is InterstitialPlacement -> showInterstitial(adUnitId)
        is RewardedPlacement -> showRewarded(adUnitId, onReward)
    }

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): BannerAdController = MetaBannerController(context, placement, adUnitId, eventReporter)

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): NativeAdController = MetaNativeController(context, placement, adUnitId, eventReporter)

    override fun clearFullscreenCache() {
        interstitials.values.forEach(MetaInterstitialEntry::destroy)
        rewardedVideos.values.forEach(MetaRewardedEntry::destroy)
        interstitials.clear()
        rewardedVideos.clear()
    }

    private suspend fun loadInterstitial(
        adUnitId: String,
        placement: InterstitialPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        interstitials.removeExpiredInterstitial(adUnitId)
        return interstitials.getOrPut(adUnitId) {
            MetaInterstitialEntry(requireNotNull(application), adUnitId, placement, eventReporter)
        }.load()
    }

    private suspend fun loadRewarded(
        adUnitId: String,
        placement: RewardedPlacement,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        rewardedVideos.removeExpiredRewarded(adUnitId)
        return rewardedVideos.getOrPut(adUnitId) {
            MetaRewardedEntry(requireNotNull(application), adUnitId, placement, eventReporter)
        }.load()
    }

    private suspend fun showInterstitial(adUnitId: String): AdShowResult {
        val entry = interstitials.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Meta interstitial is not loaded."))
        if (entry.isExpired()) {
            entry.destroy()
            return AdShowResult.Failed(error("Meta interstitial expired before it could be shown."))
        }
        return entry.show()
    }

    private suspend fun showRewarded(
        adUnitId: String,
        onReward: (AdReward) -> Unit,
    ): AdShowResult {
        val entry = rewardedVideos.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Meta rewarded ad is not loaded."))
        if (entry.isExpired()) {
            entry.destroy()
            return AdShowResult.Failed(error("Meta rewarded ad expired before it could be shown."))
        }
        return entry.show(onReward)
    }

    private fun error(message: String): AdsError = AdsError(network = network, message = message)

}

private class MetaNativeController(
    private val context: Context,
    private val placement: NativePlacement,
    adUnitId: String,
    private val eventReporter: AdsEventReporter,
) : NativeAdListener, NativeAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val nativeAd = NativeAd(context, adUnitId)
    private val nativeAdLayout = NativeAdLayout(context)

    override val view: View = nativeAdLayout
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        nativeAd.loadAd(nativeAd.buildLoadAdConfig().withAdListener(this).build())
    }

    override fun onAdLoaded(ad: Ad) {
        val binding = placement.renderer?.render(context, nativeAd.toContent())
            ?: defaultBinding(nativeAd.toContent())
        require(binding.root.parent == null) {
            "NativeAdRenderer must return a new unattached root view."
        }
        val mediaContainer = requireNotNull(binding.mediaContainer) {
            "Meta native rendering requires NativeAdRenderBinding.mediaContainer."
        }
        val mediaView = MediaView(context)
        mediaContainer.addView(
            mediaView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        nativeAdLayout.removeAllViews()
        nativeAdLayout.addView(
            binding.root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        nativeAd.registerViewForInteraction(
            nativeAdLayout,
            mediaView,
            listOf(binding.callToActionView ?: binding.root),
        )
        mutableState.value = AdViewState.Loaded
        eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.META))
    }

    override fun onError(ad: Ad?, adError: AdError) {
        val error = AdsError(
            network = AdsNetwork.META,
            message = adError.errorMessage,
            code = adError.errorCode.toString(),
        )
        mutableState.value = AdViewState.Failed(error)
        eventReporter.report(AdsEvent.AdLoadFailed(placement, error))
    }

    override fun onAdClicked(ad: Ad) {
        eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.META))
    }

    override fun onLoggingImpression(ad: Ad) {
        eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.META))
    }

    override fun onMediaDownloaded(ad: Ad) = Unit

    override fun destroy() {
        nativeAd.unregisterView()
        nativeAd.destroy()
    }

    private fun defaultBinding(content: NativeAdContent): NativeAdRenderBinding {
        val headlineView = TextView(context).apply { text = content.headline }
        val bodyView = TextView(context).apply { text = content.body.orEmpty() }
        val callToActionView = TextView(context).apply { text = content.callToAction.orEmpty() }
        val mediaContainer = FrameLayout(context)
        return NativeAdRenderBinding(
            root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(mediaContainer)
                addView(headlineView)
                addView(bodyView)
                addView(callToActionView)
            },
            headlineView = headlineView,
            bodyView = bodyView,
            callToActionView = callToActionView,
            mediaContainer = mediaContainer,
        )
    }
}

private fun NativeAd.toContent(): NativeAdContent = NativeAdContent(
    network = AdsNetwork.META,
    headline = advertiserName.orEmpty(),
    body = adBodyText,
    callToAction = adCallToAction,
    advertiser = advertiserName,
    price = null,
    store = null,
)

private class MetaInterstitialEntry(
    context: Context,
    adUnitId: String,
    private val placement: InterstitialPlacement,
    private val eventReporter: AdsEventReporter,
) : InterstitialAdListener {
    private val ad = InterstitialAd(context, adUnitId)
    private var loadContinuation: CancellableContinuation<AdLoadResult>? = null
    private var showContinuation: CancellableContinuation<AdShowResult>? = null
    private var loadedAtElapsedRealtimeMillis: Long? = null

    suspend fun load(): AdLoadResult {
        if (ad.isAdLoaded && !isExpired()) return AdLoadResult.Loaded
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
        loadedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.META))
        loadContinuation?.takeIf { it.isActive }?.resume(AdLoadResult.Loaded)
        loadContinuation = null
    }

    override fun onError(ad: Ad?, adError: AdError) = fail(adError.errorMessage)

    override fun onInterstitialDisplayed(ad: Ad) {
        eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.META))
    }

    override fun onInterstitialDismissed(ad: Ad) {
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Shown)
        showContinuation = null
        this.ad.destroy()
    }

    override fun onAdClicked(ad: Ad) {
        eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.META))
    }

    override fun onLoggingImpression(ad: Ad) = Unit

    private fun fail(message: String) {
        val failure = error(message)
        loadContinuation?.takeIf { it.isActive }?.let { continuation ->
            eventReporter.report(AdsEvent.AdLoadFailed(placement, failure))
            continuation.resume(AdLoadResult.Failed(failure))
        }
        loadContinuation = null
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Failed(failure))
        showContinuation = null
    }

    private fun error(message: String): AdsError = AdsError(network = AdsNetwork.META, message = message)

    fun destroy() {
        loadContinuation?.cancel()
        showContinuation?.cancel()
        ad.destroy()
    }

    fun isExpired(): Boolean = loadedAtElapsedRealtimeMillis?.let { loadedAt ->
        isFullscreenAdExpired(loadedAt, SystemClock.elapsedRealtime())
    } ?: false
}

private class MetaRewardedEntry(
    context: Context,
    adUnitId: String,
    private val placement: RewardedPlacement,
    private val eventReporter: AdsEventReporter,
) : RewardedVideoAdListener {
    private val ad = RewardedVideoAd(context, adUnitId)
    private var loadContinuation: CancellableContinuation<AdLoadResult>? = null
    private var showContinuation: CancellableContinuation<AdShowResult>? = null
    private var rewardHandler: ((AdReward) -> Unit)? = null
    private var loadedAtElapsedRealtimeMillis: Long? = null

    suspend fun load(): AdLoadResult {
        if (ad.isAdLoaded && !isExpired()) return AdLoadResult.Loaded
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
        loadedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.META))
        loadContinuation?.takeIf { it.isActive }?.resume(AdLoadResult.Loaded)
        loadContinuation = null
    }

    override fun onError(ad: Ad?, adError: AdError) = fail(adError.errorMessage)

    override fun onRewardedVideoCompleted() {
        val reward = AdReward()
        eventReporter.report(AdsEvent.AdRewardEarned(placement, AdsNetwork.META, reward))
        rewardHandler?.invoke(reward)
    }

    override fun onRewardedVideoClosed() {
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Shown)
        showContinuation = null
        rewardHandler = null
        ad.destroy()
    }

    override fun onAdClicked(ad: Ad) {
        eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.META))
    }

    override fun onLoggingImpression(ad: Ad) {
        eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.META))
    }

    private fun fail(message: String) {
        val failure = error(message)
        loadContinuation?.takeIf { it.isActive }?.let { continuation ->
            eventReporter.report(AdsEvent.AdLoadFailed(placement, failure))
            continuation.resume(AdLoadResult.Failed(failure))
        }
        loadContinuation = null
        showContinuation?.takeIf { it.isActive }?.resume(AdShowResult.Failed(failure))
        showContinuation = null
        rewardHandler = null
    }

    private fun error(message: String): AdsError = AdsError(network = AdsNetwork.META, message = message)

    fun destroy() {
        loadContinuation?.cancel()
        showContinuation?.cancel()
        rewardHandler = null
        ad.destroy()
    }

    fun isExpired(): Boolean = loadedAtElapsedRealtimeMillis?.let { loadedAt ->
        isFullscreenAdExpired(loadedAt, SystemClock.elapsedRealtime())
    } ?: false
}

private fun ConcurrentHashMap<String, MetaInterstitialEntry>.removeExpiredInterstitial(adUnitId: String) {
    val entry = this[adUnitId] ?: return
    if (entry.isExpired() && remove(adUnitId, entry)) entry.destroy()
}

private fun ConcurrentHashMap<String, MetaRewardedEntry>.removeExpiredRewarded(adUnitId: String) {
    val entry = this[adUnitId] ?: return
    if (entry.isExpired() && remove(adUnitId, entry)) entry.destroy()
}

private class MetaBannerController(
    context: Context,
    private val placement: BannerPlacement,
    adUnitId: String,
    private val eventReporter: AdsEventReporter,
) : BannerAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val listener = object : AdListener {
        override fun onError(ad: Ad?, adError: AdError) {
            val error = AdsError(
                network = AdsNetwork.META,
                message = adError.errorMessage,
                code = adError.errorCode.toString(),
            )
            mutableState.value = AdViewState.Failed(error)
            eventReporter.report(AdsEvent.AdLoadFailed(placement, error))
        }

        override fun onAdLoaded(ad: Ad) {
            mutableState.value = AdViewState.Loaded
            eventReporter.report(AdsEvent.AdLoaded(placement, AdsNetwork.META))
        }

        override fun onAdClicked(ad: Ad) {
            eventReporter.report(AdsEvent.AdClicked(placement, AdsNetwork.META))
        }

        override fun onLoggingImpression(ad: Ad) {
            eventReporter.report(AdsEvent.AdImpression(placement, AdsNetwork.META))
        }
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
