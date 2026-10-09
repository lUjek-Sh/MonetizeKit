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
import ua.lujek.monetizekit.core.MonetizeKit
import ua.lujek.monetizekit.core.MonetizeKitState

data class SampleAdsUiState(
    val adsEnabled: Boolean,
    val isSdkReady: Boolean,
    val sdkStatus: String,
    val lastAction: String,
    val lastEvent: String,
)

enum class SampleAdsAction {
    PRELOAD_FULLSCREEN,
    SHOW_INTERSTITIAL,
    SHOW_REWARDED,
    OPEN_PRIVACY_OPTIONS,
}

class SampleAdsViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        SampleAdsUiState(
            adsEnabled = BuildConfig.DEBUG,
            isSdkReady = false,
            sdkStatus = if (BuildConfig.DEBUG) "Waiting for UMP consent" else "Ads are disabled in release",
            lastAction = "No fullscreen request yet",
            lastEvent = "No SDK event yet",
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
    }

    fun onAction(activity: Activity, action: SampleAdsAction) {
        if (!BuildConfig.DEBUG) {
            mutableUiState.update { it.copy(lastAction = "Debug ads are unavailable in release") }
            return
        }
        if (action != SampleAdsAction.OPEN_PRIVACY_OPTIONS && !uiState.value.isSdkReady) {
            mutableUiState.update { it.copy(lastAction = "Wait until the SDK is ready before requesting fullscreen ads") }
            return
        }
        viewModelScope.launch {
            when (action) {
                SampleAdsAction.PRELOAD_FULLSCREEN -> preloadFullscreen()
                SampleAdsAction.SHOW_INTERSTITIAL -> showInterstitial(activity)
                SampleAdsAction.SHOW_REWARDED -> showRewarded(activity)
                SampleAdsAction.OPEN_PRIVACY_OPTIONS -> showPrivacyOptions(activity)
            }
        }
    }

    private suspend fun preloadFullscreen() {
        val interstitial = MonetizeKit.preload(DebugSamplePlacements.interstitial)
        val rewarded = MonetizeKit.preload(DebugSamplePlacements.rewarded)
        mutableUiState.update {
            it.copy(
                lastAction = "Preload: interstitial ${interstitial.label()}, rewarded ${rewarded.label()}",
            )
        }
    }

    private suspend fun showInterstitial(activity: Activity) {
        val result = MonetizeKit.show(activity, DebugSamplePlacements.interstitial)
        mutableUiState.update { it.copy(lastAction = "Interstitial ${result.label()}") }
    }

    private suspend fun showRewarded(activity: Activity) {
        var receivedReward: Pair<Int, String?>? = null
        val result = MonetizeKit.show(activity, DebugSamplePlacements.rewarded) { reward ->
            receivedReward = (reward.amount ?: 1) to reward.type
        }
        mutableUiState.update {
            val reward = receivedReward
            it.copy(
                lastAction = if (reward == null) {
                    "Rewarded ${result.label()}"
                } else {
                    "Reward received: ${reward.first} ${reward.second.orEmpty()}"
                },
            )
        }
    }

    private suspend fun showPrivacyOptions(activity: Activity) {
        val result = MonetizeKit.showPrivacyOptions(activity)
        mutableUiState.update {
            it.copy(lastAction = if (result.canRequestAds) "Privacy options completed" else "Privacy options unavailable")
        }
    }
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
