package ua.lujek.monetizekit.core

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ConsentSnapshot(
    val canRequestAds: Boolean,
    val isPrivacyOptionsRequired: Boolean,
    val error: AdsError? = null,
)

sealed interface GdprConsentState {
    data object NotRequested : GdprConsentState
    data object Gathering : GdprConsentState
    data class Resolved(val snapshot: ConsentSnapshot) : GdprConsentState
}

/** Google UMP-backed GDPR consent gathering. A valid UMP message must be configured in AdMob. */
internal class GdprConsentManager(context: Context) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

    suspend fun gather(activity: Activity): ConsentSnapshot {
        val updateError = suspendCancellableCoroutine<FormError?> { continuation ->
            consentInformation.requestConsentInfoUpdate(
                activity,
                ConsentRequestParameters.Builder().build(),
                { if (continuation.isActive) continuation.resume(null) },
                { error -> if (continuation.isActive) continuation.resume(error) },
            )
        }

        val formError = if (updateError == null) {
            suspendCancellableCoroutine<FormError?> { continuation ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (continuation.isActive) continuation.resume(error)
                }
            }
        } else {
            null
        }
        return consentSnapshot(updateError ?: formError)
    }

    suspend fun showPrivacyOptions(activity: Activity): ConsentSnapshot {
        val formError = suspendCancellableCoroutine<FormError?> { continuation ->
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                if (continuation.isActive) continuation.resume(error)
            }
        }
        return consentSnapshot(formError)
    }

    private fun consentSnapshot(formError: FormError?): ConsentSnapshot = ConsentSnapshot(
        canRequestAds = consentInformation.canRequestAds(),
        isPrivacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
        error = formError?.let { error -> AdsError(message = error.message) },
    )
}
