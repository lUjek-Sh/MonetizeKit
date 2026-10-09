package ua.lujek.monetizekit.core

import android.app.Activity

/**
 * A single application-owned privacy flow, typically backed by a certified CMP.
 *
 * Return [ConsentSnapshot.canRequestAds] only after the provider has stored every consent signal
 * required by enabled direct networks. MonetizeKit uses this provider instead of UMP; it never
 * combines two consent dialogs.
 */
interface PrivacyConsentProvider {
    suspend fun gatherConsent(activity: Activity): ConsentSnapshot

    suspend fun showPrivacyOptions(activity: Activity): ConsentSnapshot
}
