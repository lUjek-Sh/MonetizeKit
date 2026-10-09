package ua.lujek.monetizekit.sample

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.lujek.monetizekit.core.AdLoadResult
import ua.lujek.monetizekit.core.AdShowResult
import ua.lujek.monetizekit.core.AdsEvent
import ua.lujek.monetizekit.core.ConsentSnapshot
import ua.lujek.monetizekit.core.GdprConsentState
import ua.lujek.monetizekit.core.MonetizeKit
import ua.lujek.monetizekit.core.MonetizeKitState

data class SampleAdsUiState(
    val adsEnabled: Boolean,
    val isSdkReady: Boolean,
    val sdkStatus: String,
    val privacyStatus: String,
    val privacyProfile: SamplePrivacyProfile,
    val networkTestSetup: String,
    val lastAction: String,
    val lastEvent: String,
    val inlineReloadKey: Int,
)

sealed interface SampleAdsAction {
    data object OpenAdInspector : SampleAdsAction
    data object PreloadAllFullscreen : SampleAdsAction
    data object ReloadInlineAds : SampleAdsAction
    data object ShowAdMobInterstitial : SampleAdsAction
    data object ShowAdMobRewarded : SampleAdsAction
    data object ShowAdMobRewardedInterstitial : SampleAdsAction
    data object ShowAdMobAppOpen : SampleAdsAction
    data object ShowMetaInterstitial : SampleAdsAction
    data object ShowMetaRewarded : SampleAdsAction
    data object ShowUnityInterstitial : SampleAdsAction
    data object ShowUnityRewarded : SampleAdsAction
    data object GatherConsent : SampleAdsAction
    data object OpenPrivacyOptions : SampleAdsAction
    data class SelectPrivacyProfile(val profile: SamplePrivacyProfile) : SampleAdsAction
    data object RestartWithPrivacyProfile : SampleAdsAction
}

class SampleAdsViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        SampleAdsUiState(
            adsEnabled = BuildConfig.DEBUG,
            isSdkReady = false,
            sdkStatus = if (BuildConfig.DEBUG) "Waiting for UMP consent" else "Ads are disabled in release",
            privacyStatus = "Consent has not been requested",
            privacyProfile = SamplePrivacyProfileStore.current(),
            networkTestSetup = SampleDebugConfiguration.networkSummary,
            lastAction = "No fullscreen request yet",
            lastEvent = "No SDK event yet",
            inlineReloadKey = 0,
        ),
    )

    private var fullscreenPreloaded = false

    val uiState: StateFlow<SampleAdsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            MonetizeKit.state.collect { state ->
                mutableUiState.update {
                    it.copy(
                        isSdkReady = state is MonetizeKitState.Ready,
                        sdkStatus = state.label(),
                    )
                }
                if (state is MonetizeKitState.Ready && !fullscreenPreloaded) {
                    fullscreenPreloaded = true
                    preloadFullscreen()
                }
            }
        }
        viewModelScope.launch {
            MonetizeKit.events.collect { event ->
                mutableUiState.update { it.copy(lastEvent = event.label()) }
            }
        }
        viewModelScope.launch {
            MonetizeKit.gdprConsent.collect { consent ->
                mutableUiState.update { it.copy(privacyStatus = consent.label()) }
            }
        }
    }

    fun onAction(activity: Activity, action: SampleAdsAction) {
        if (!BuildConfig.DEBUG) {
            mutableUiState.update { it.copy(lastAction = "Debug ads are unavailable in release") }
            return
        }
        if (action.requiresReadySdk() && !uiState.value.isSdkReady) {
            mutableUiState.update { it.copy(lastAction = "Wait until the SDK is ready before requesting ads or Ad Inspector") }
            return
        }
        viewModelScope.launch {
            when (action) {
                SampleAdsAction.OpenAdInspector -> openAdInspector(activity)
                SampleAdsAction.PreloadAllFullscreen -> preloadFullscreen()
                SampleAdsAction.ReloadInlineAds -> reloadInlineAds()
                SampleAdsAction.ShowAdMobInterstitial -> showAdMobInterstitial(activity)
                SampleAdsAction.ShowAdMobRewarded -> showAdMobRewarded(activity)
                SampleAdsAction.ShowAdMobRewardedInterstitial -> showAdMobRewardedInterstitial(activity)
                SampleAdsAction.ShowAdMobAppOpen -> showAdMobAppOpen(activity)
                SampleAdsAction.ShowMetaInterstitial -> showMetaInterstitial(activity)
                SampleAdsAction.ShowMetaRewarded -> showMetaRewarded(activity)
                SampleAdsAction.ShowUnityInterstitial -> showUnityInterstitial(activity)
                SampleAdsAction.ShowUnityRewarded -> showUnityRewarded(activity)
                SampleAdsAction.GatherConsent -> gatherConsent(activity)
                SampleAdsAction.OpenPrivacyOptions -> showPrivacyOptions(activity)
                is SampleAdsAction.SelectPrivacyProfile -> selectPrivacyProfile(activity, action.profile)
                SampleAdsAction.RestartWithPrivacyProfile -> DebugPrivacyProfileRestart.restart(activity)
            }
        }
    }

    private suspend fun preloadFullscreen() {
        val results = mutableListOf<String>()
        suspend fun record(name: String, load: suspend () -> AdLoadResult) {
            results += "$name ${load().label()}"
        }

        record("AdMob interstitial") { MonetizeKit.preload(DebugSamplePlacements.adMobInterstitial) }
        record("AdMob rewarded") { MonetizeKit.preload(DebugSamplePlacements.adMobRewarded) }
        record("AdMob rewarded interstitial") {
            MonetizeKit.preload(DebugSamplePlacements.adMobRewardedInterstitial)
        }
        record("AdMob app open") { MonetizeKit.preload(DebugSamplePlacements.adMobAppOpen) }
        SampleDebugConfiguration.meta?.interstitial?.let {
            record("Meta interstitial") { MonetizeKit.preload(DebugSamplePlacements.metaInterstitial) }
        }
        SampleDebugConfiguration.meta?.rewarded?.let {
            record("Meta rewarded") { MonetizeKit.preload(DebugSamplePlacements.metaRewarded) }
        }
        SampleDebugConfiguration.unity?.interstitial?.let {
            record("Unity interstitial") { MonetizeKit.preload(DebugSamplePlacements.unityInterstitial) }
        }
        SampleDebugConfiguration.unity?.rewarded?.let {
            record("Unity rewarded") { MonetizeKit.preload(DebugSamplePlacements.unityRewarded) }
        }
        mutableUiState.update {
            it.copy(lastAction = "Preload: ${results.joinToString()}")
        }
    }

    private fun openAdInspector(activity: Activity) {
        mutableUiState.update { it.copy(lastAction = "Opening Ad Inspector") }
        AdInspectorLauncher.open(activity) { message ->
            mutableUiState.update { it.copy(lastAction = message) }
        }
    }

    private fun reloadInlineAds() {
        mutableUiState.update {
            it.copy(
                inlineReloadKey = it.inlineReloadKey + 1,
                lastAction = "Reloading configured inline ad slots",
            )
        }
    }

    private suspend fun showAdMobInterstitial(activity: Activity) {
        val result = MonetizeKit.show(activity, DebugSamplePlacements.adMobInterstitial)
        mutableUiState.update { it.copy(lastAction = "AdMob interstitial ${result.label()}") }
    }

    private suspend fun showAdMobRewarded(activity: Activity) {
        var receivedReward: Pair<Int, String?>? = null
        val result = MonetizeKit.show(activity, DebugSamplePlacements.adMobRewarded) { reward ->
            receivedReward = (reward.amount ?: 1) to reward.type
        }
        mutableUiState.update {
            val reward = receivedReward
            it.copy(
                lastAction = if (reward == null) {
                    "AdMob rewarded ${result.label()}"
                } else {
                    "AdMob reward: ${reward.first} ${reward.second.orEmpty()}"
                },
            )
        }
    }

    private suspend fun showAdMobRewardedInterstitial(activity: Activity) {
        var receivedReward: Pair<Int, String?>? = null
        val result = MonetizeKit.show(activity, DebugSamplePlacements.adMobRewardedInterstitial) { reward ->
            receivedReward = (reward.amount ?: 1) to reward.type
        }
        mutableUiState.update {
            val reward = receivedReward
            it.copy(
                lastAction = if (reward == null) {
                    "AdMob rewarded interstitial ${result.label()}"
                } else {
                    "AdMob rewarded interstitial reward: ${reward.first} ${reward.second.orEmpty()}"
                },
            )
        }
    }

    private suspend fun showAdMobAppOpen(activity: Activity) {
        val result = MonetizeKit.show(activity, DebugSamplePlacements.adMobAppOpen)
        mutableUiState.update { it.copy(lastAction = "AdMob App Open ${result.label()}") }
    }

    private suspend fun showMetaInterstitial(activity: Activity) {
        if (SampleDebugConfiguration.meta?.interstitial == null) return notConfigured("Meta interstitial")
        val result = MonetizeKit.show(activity, DebugSamplePlacements.metaInterstitial)
        mutableUiState.update { it.copy(lastAction = "Meta interstitial ${result.label()}") }
    }

    private suspend fun showMetaRewarded(activity: Activity) {
        if (SampleDebugConfiguration.meta?.rewarded == null) return notConfigured("Meta rewarded")
        showRewarded(
            activity = activity,
            placement = DebugSamplePlacements.metaRewarded,
            networkName = "Meta",
        )
    }

    private suspend fun showUnityInterstitial(activity: Activity) {
        if (SampleDebugConfiguration.unity?.interstitial == null) return notConfigured("Unity interstitial")
        val result = MonetizeKit.show(activity, DebugSamplePlacements.unityInterstitial)
        mutableUiState.update { it.copy(lastAction = "Unity interstitial ${result.label()}") }
    }

    private suspend fun showUnityRewarded(activity: Activity) {
        if (SampleDebugConfiguration.unity?.rewarded == null) return notConfigured("Unity rewarded")
        showRewarded(
            activity = activity,
            placement = DebugSamplePlacements.unityRewarded,
            networkName = "Unity",
        )
    }

    private suspend fun showRewarded(
        activity: Activity,
        placement: ua.lujek.monetizekit.core.RewardedPlacement,
        networkName: String,
    ) {
        var receivedReward: Pair<Int, String?>? = null
        val result = MonetizeKit.show(activity, placement) { reward ->
            receivedReward = (reward.amount ?: 1) to reward.type
        }
        mutableUiState.update {
            val reward = receivedReward
            it.copy(
                lastAction = if (reward == null) {
                    "$networkName rewarded ${result.label()}"
                } else {
                    "$networkName reward: ${reward.first} ${reward.second.orEmpty()}"
                },
            )
        }
    }

    private suspend fun gatherConsent(activity: Activity) {
        val result = MonetizeKit.gatherGdprConsent(activity)
        mutableUiState.update { it.copy(lastAction = "Consent flow ${result.label()}") }
    }

    private suspend fun showPrivacyOptions(activity: Activity) {
        val result = MonetizeKit.showPrivacyOptions(activity)
        mutableUiState.update {
            it.copy(lastAction = "Privacy options ${result.label()}")
        }
    }

    private fun selectPrivacyProfile(activity: Activity, profile: SamplePrivacyProfile) {
        SamplePrivacyProfileStore.save(activity.applicationContext, profile)
        mutableUiState.update {
            it.copy(
                privacyProfile = profile,
                lastAction = "${profile.label} selected. Restart the debug sample to apply it.",
            )
        }
    }

    private fun notConfigured(networkFormat: String) {
        mutableUiState.update {
            it.copy(lastAction = "$networkFormat is not configured in local.properties")
        }
    }
}

private fun SampleAdsAction.requiresReadySdk(): Boolean = when (this) {
    SampleAdsAction.OpenAdInspector,
    SampleAdsAction.PreloadAllFullscreen,
    SampleAdsAction.ReloadInlineAds,
    SampleAdsAction.ShowAdMobInterstitial,
    SampleAdsAction.ShowAdMobRewarded,
    SampleAdsAction.ShowAdMobRewardedInterstitial,
    SampleAdsAction.ShowAdMobAppOpen,
    SampleAdsAction.ShowMetaInterstitial,
    SampleAdsAction.ShowMetaRewarded,
    SampleAdsAction.ShowUnityInterstitial,
    SampleAdsAction.ShowUnityRewarded,
    -> true

    SampleAdsAction.GatherConsent,
    SampleAdsAction.OpenPrivacyOptions,
    is SampleAdsAction.SelectPrivacyProfile,
    SampleAdsAction.RestartWithPrivacyProfile,
    -> false
}

private fun MonetizeKitState.label(): String = when (this) {
    MonetizeKitState.Uninitialized -> "Not initialized"
    is MonetizeKitState.AwaitingGdprConsent -> "Waiting for UMP consent"
    is MonetizeKitState.Initializing -> "Initializing ${networks.joinToString()}"
    MonetizeKitState.GdprConsentRequired -> "Consent is required before ads can load"
    MonetizeKitState.AdsSuppressed -> "Ads are disabled by the access policy"
    is MonetizeKitState.Ready -> "Ready: ${availableNetworks.joinToString()}"
    is MonetizeKitState.Failed -> "Initialization failed"
}

private fun AdsEvent.label(): String = when (this) {
    is AdsEvent.NetworkInitialized -> "$network initialized"
    is AdsEvent.NetworkInitializationFailed -> "$network initialization failed: ${error.message}"
    is AdsEvent.AdPreloaded -> "${placement.id} preloaded by $network"
    is AdsEvent.AdPreloadFailed -> "${placement.id} preload failed: ${error.message}"
    is AdsEvent.AdPreloadRetryScheduled -> {
        "${placement.id} retry ${nextAttempt} in ${delayMillis}ms: ${error.message}"
    }
    is AdsEvent.AdShown -> "${placement.id} shown by $network"
    is AdsEvent.AdShowFailed -> "${placement.id} show failed: ${error.message}"
    is AdsEvent.AdShowCapped -> "${placement.id} capped until $nextAllowedAtEpochMillis"
    is AdsEvent.AdsAccessChanged -> "Ads access changed to $access"
    is AdsEvent.AdLoaded -> "${placement.id} loaded by $network"
    is AdsEvent.AdLoadFailed -> "${placement.id} load failed: ${error.message}"
    is AdsEvent.AdImpression -> "${placement.id} impression by $network"
    is AdsEvent.AdClicked -> "${placement.id} clicked in $network"
    is AdsEvent.AdRewardEarned -> {
        "${placement.id} reward from $network: ${reward.amount ?: 1} ${reward.type.orEmpty()}"
    }
    is AdsEvent.AdPaid -> {
        "${placement.id} revenue: ${revenue.valueMicros} ${revenue.currencyCode} by $network"
    }
}

private fun AdLoadResult.label(): String = when (this) {
    AdLoadResult.Loaded -> "ready"
    AdLoadResult.Suppressed -> "suppressed"
    is AdLoadResult.Failed -> "failed (${error.message})"
}

private fun AdShowResult.label(): String = when (this) {
    AdShowResult.Shown -> "shown"
    AdShowResult.Suppressed -> "suppressed"
    is AdShowResult.Capped -> "capped until $nextAllowedAtEpochMillis"
    is AdShowResult.Failed -> "failed (${error.message})"
}

private fun ConsentSnapshot.label(): String = buildString {
    append(if (canRequestAds) "completed" else "blocked")
    append(", privacy options ")
    append(if (isPrivacyOptionsRequired) "required" else "not required")
    error?.let { append(": ${it.message}") }
}

private fun GdprConsentState.label(): String = when (this) {
    GdprConsentState.NotRequested -> "Consent has not been requested"
    GdprConsentState.Gathering -> "Consent is being gathered"
    is GdprConsentState.Resolved -> snapshot.label()
}
