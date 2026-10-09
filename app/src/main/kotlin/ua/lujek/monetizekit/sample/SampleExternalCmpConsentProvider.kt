package ua.lujek.monetizekit.sample

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ua.lujek.monetizekit.core.ConsentSnapshot
import ua.lujek.monetizekit.core.PrivacyConsentProvider
import kotlin.coroutines.resume

/**
 * A fully interactive debug provider for validating MonetizeKit's external-CMP contract.
 * It deliberately does not claim to be a certified CMP and must never be used in production.
 */
internal class SampleExternalCmpConsentProvider(context: Context) : PrivacyConsentProvider {
    private val preferences = context.applicationContext.getSharedPreferences(
        "monetizekit_sample_external_cmp",
        Context.MODE_PRIVATE,
    )

    override suspend fun gatherConsent(activity: Activity): ConsentSnapshot {
        if (preferences.contains(consentGrantedKey)) return currentSnapshot()
        return SampleExternalCmpDialogController.request(SampleExternalCmpRequest.CONSENT, ::storeDecision)
    }

    override suspend fun showPrivacyOptions(activity: Activity): ConsentSnapshot =
        SampleExternalCmpDialogController.request(SampleExternalCmpRequest.PRIVACY_OPTIONS, ::storeDecision)

    private fun storeDecision(allowed: Boolean): ConsentSnapshot {
        preferences.edit().putBoolean(consentGrantedKey, allowed).apply()
        return currentSnapshot()
    }

    private fun currentSnapshot(): ConsentSnapshot = ConsentSnapshot(
        canRequestAds = preferences.getBoolean(consentGrantedKey, false),
        isPrivacyOptionsRequired = true,
    )

    private companion object {
        const val consentGrantedKey = "consent_granted"
    }
}

internal enum class SampleExternalCmpRequest {
    CONSENT,
    PRIVACY_OPTIONS,
}

internal data class SampleExternalCmpUiState(
    val request: SampleExternalCmpRequest,
)

internal object SampleExternalCmpDialogController {
    private val mutableUiState = MutableStateFlow<SampleExternalCmpUiState?>(null)
    val uiState: StateFlow<SampleExternalCmpUiState?> = mutableUiState.asStateFlow()
    private var pendingContinuation: CancellableContinuation<ConsentSnapshot>? = null
    private var decisionHandler: ((Boolean) -> ConsentSnapshot)? = null

    suspend fun request(
        request: SampleExternalCmpRequest,
        onDecision: (Boolean) -> ConsentSnapshot,
    ): ConsentSnapshot = suspendCancellableCoroutine { continuation ->
        check(pendingContinuation == null) { "Only one sample external CMP request may be active." }
        pendingContinuation = continuation
        decisionHandler = onDecision
        mutableUiState.value = SampleExternalCmpUiState(request)
        continuation.invokeOnCancellation { clear(continuation) }
    }

    fun complete(allowed: Boolean) {
        val continuation = pendingContinuation ?: return
        val snapshot = requireNotNull(decisionHandler).invoke(allowed)
        clear(continuation)
        if (continuation.isActive) continuation.resume(snapshot)
    }

    private fun clear(continuation: CancellableContinuation<ConsentSnapshot>) {
        if (pendingContinuation !== continuation) return
        pendingContinuation = null
        decisionHandler = null
        mutableUiState.value = null
    }
}
