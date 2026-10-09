package ua.lujek.monetizekit.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow

interface AdsNetworkAdapter {
    val network: AdsNetwork
    val supportedFormats: Set<AdFormat>

    suspend fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ): NetworkInitializationResult

    suspend fun loadFullscreen(
        placement: FullscreenPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdLoadResult

    suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
        eventReporter: AdsEventReporter,
    ): AdShowResult

    fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): BannerAdController?

    fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): NativeAdController?

    /** Removes a not-yet-shown fullscreen creative after ad access has been revoked. */
    fun clearFullscreenCache()
}

fun interface AdsNetworkAdapterFactory {
    fun create(): AdsNetworkAdapter
}

interface ViewAdController {
    val view: View
    val state: StateFlow<AdViewState>

    fun load()

    /** Propagates the host lifecycle pause to SDK views that need it. */
    fun onPause() = Unit

    /** Propagates the host lifecycle resume to SDK views that need it. */
    fun onResume() = Unit

    fun destroy()
}

interface BannerAdController : ViewAdController

interface NativeAdController : ViewAdController

sealed interface AdViewState {
    data object Idle : AdViewState
    data object Loading : AdViewState
    data object Loaded : AdViewState
    data object Suppressed : AdViewState
    data class Failed(val error: AdsError) : AdViewState
}


sealed interface NetworkInitializationResult {
    data object Ready : NetworkInitializationResult
    data class Failed(val error: AdsError) : NetworkInitializationResult
}

sealed interface AdLoadResult {
    data object Loaded : AdLoadResult
    data object Suppressed : AdLoadResult
    data class Failed(val error: AdsError) : AdLoadResult
}

sealed interface AdShowResult {
    data object Shown : AdShowResult
    data object Suppressed : AdShowResult
    data class Failed(val error: AdsError) : AdShowResult
}

data class AdsError(
    val network: AdsNetwork? = null,
    val message: String,
    val code: String? = null,
    val diagnostics: AdDiagnostics? = null,
    val cause: Throwable? = null,
)

data class AdReward(
    val amount: Int? = null,
    val type: String? = null,
)

/** Safe response metadata for logging and diagnostics. It intentionally excludes creative data. */
data class AdDiagnostics(
    val responseId: String? = null,
    val mediationAdapterClassName: String? = null,
    val adSourceName: String? = null,
    val adSourceInstanceName: String? = null,
)

data class AdRevenue(
    val valueMicros: Long,
    val currencyCode: String,
    val precision: AdRevenuePrecision,
)

enum class AdRevenuePrecision {
    UNKNOWN,
    ESTIMATED,
    PUBLISHER_PROVIDED,
    PRECISE,
}

/** Receives normalized events from direct network adapters. */
fun interface AdsEventReporter {
    fun report(event: AdsEvent)
}
