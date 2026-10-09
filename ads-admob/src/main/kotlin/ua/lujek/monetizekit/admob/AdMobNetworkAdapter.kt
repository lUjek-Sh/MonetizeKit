package ua.lujek.monetizekit.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

internal class AdMobNetworkAdapter : AdsNetworkAdapter {
    override val network: AdsNetwork = AdsNetwork.ADMOB
    override val supportedFormats: Set<AdFormat> = setOf(
        AdFormat.BANNER,
        AdFormat.INTERSTITIAL,
        AdFormat.REWARDED,
        AdFormat.NATIVE,
    )

    private var application: Application? = null
    private val interstitials = ConcurrentHashMap<String, InterstitialAd>()
    private val rewardedAds = ConcurrentHashMap<String, RewardedAd>()

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
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder().setTestDeviceIds(adMob.testDeviceIds.toList()).build(),
        )
        return suspendCancellableCoroutine { continuation ->
            MobileAds.initialize(application) {
                if (continuation.isActive) continuation.resume(NetworkInitializationResult.Ready)
            }
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
    ): BannerAdController = AdMobBannerController(context, adUnitId)

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
    ): NativeAdController = AdMobNativeController(context, adUnitId)

    override fun clearFullscreenCache() {
        interstitials.clear()
        rewardedAds.clear()
    }

    private suspend fun loadInterstitial(adUnitId: String): AdLoadResult {
        if (interstitials.containsKey(adUnitId)) return AdLoadResult.Loaded
        val context = application ?: return AdLoadResult.Failed(error("AdMob is not initialized."))
        return suspendCancellableCoroutine { continuation ->
            InterstitialAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitials[adUnitId] = ad
                        if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        if (continuation.isActive) {
                            continuation.resume(AdLoadResult.Failed(error(loadAdError.message)))
                        }
                    }
                },
            )
        }
    }

    private suspend fun loadRewarded(adUnitId: String): AdLoadResult {
        if (rewardedAds.containsKey(adUnitId)) return AdLoadResult.Loaded
        val context = application ?: return AdLoadResult.Failed(error("AdMob is not initialized."))
        return suspendCancellableCoroutine { continuation ->
            RewardedAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAds[adUnitId] = ad
                        if (continuation.isActive) continuation.resume(AdLoadResult.Loaded)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        if (continuation.isActive) {
                            continuation.resume(AdLoadResult.Failed(error(loadAdError.message)))
                        }
                    }
                },
            )
        }
    }

    private suspend fun showInterstitial(activity: Activity, adUnitId: String): AdShowResult {
        val ad = interstitials.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Interstitial ad is not loaded."))
        return suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    if (continuation.isActive) continuation.resume(AdShowResult.Failed(error(adError.message)))
                }
            }
            ad.show(activity)
        }
    }

    private suspend fun showRewarded(
        activity: Activity,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
    ): AdShowResult {
        val ad = rewardedAds.remove(adUnitId)
            ?: return AdShowResult.Failed(error("Rewarded ad is not loaded."))
        return suspendCancellableCoroutine { continuation ->
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (continuation.isActive) continuation.resume(AdShowResult.Shown)
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    if (continuation.isActive) continuation.resume(AdShowResult.Failed(error(adError.message)))
                }
            }
            ad.show(activity) { rewardItem ->
                onReward(AdReward(amount = rewardItem.amount, type = rewardItem.type))
            }
        }
    }

    private fun error(message: String): AdsError = AdsError(network = network, message = message)
}

private class AdMobNativeController(
    private val context: Context,
    private val adUnitId: String,
) : NativeAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private var nativeAd: NativeAd? = null
    private val nativeAdView = NativeAdView(context)
    private val headlineView = TextView(context)
    private val bodyView = TextView(context)
    private val callToActionView = TextView(context)

    init {
        nativeAdView.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(headlineView)
                addView(bodyView)
                addView(callToActionView)
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        nativeAdView.headlineView = headlineView
        nativeAdView.bodyView = bodyView
        nativeAdView.callToActionView = callToActionView
    }

    override val view: View = nativeAdView
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        AdLoader.Builder(context, adUnitId)
            .forNativeAd { loadedAd ->
                nativeAd?.destroy()
                nativeAd = loadedAd
                headlineView.text = loadedAd.headline
                bodyView.text = loadedAd.body.orEmpty()
                callToActionView.text = loadedAd.callToAction.orEmpty()
                nativeAdView.setNativeAd(loadedAd)
                mutableState.value = AdViewState.Loaded
            }
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        mutableState.value = AdViewState.Failed(
                            AdsError(network = AdsNetwork.ADMOB, message = loadAdError.message),
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

@Suppress("DEPRECATION")
private fun Application.adMobApplicationId(): String? = packageManager
    .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
    .metaData
    ?.getString("com.google.android.gms.ads.APPLICATION_ID")

private class AdMobBannerController(
    context: Context,
    adUnitId: String,
) : BannerAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private val adView = AdView(context).apply {
        setAdSize(AdSize.BANNER)
        this.adUnitId = adUnitId
        adListener = object : AdListener() {
            override fun onAdLoaded() {
                mutableState.value = AdViewState.Loaded
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                mutableState.value = AdViewState.Failed(
                    AdsError(network = AdsNetwork.ADMOB, message = loadAdError.message),
                )
            }
        }
    }

    override val view: View = adView
    override val state: StateFlow<AdViewState> = mutableState

    override fun load() {
        if (mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        mutableState.value = AdViewState.Loading
        adView.loadAd(AdRequest.Builder().build())
    }

    override fun destroy() {
        adView.destroy()
    }
}
