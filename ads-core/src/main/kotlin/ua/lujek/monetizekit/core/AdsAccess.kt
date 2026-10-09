package ua.lujek.monetizekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Whether this app session may request or display advertising. */
sealed interface AdsAccess {
    /** Ads may follow the normal consent, initialization, and placement rules. */
    data object Allowed : AdsAccess

    /** Ads are intentionally disabled, for example by an active paid entitlement. */
    data object Suppressed : AdsAccess
}

/**
 * Application-owned source of ad access. The ads module deliberately has no dependency on a
 * billing provider, so the source may be backed by Google Play Billing, a server, or any SDK.
 */
interface AdsAccessProvider {
    val access: StateFlow<AdsAccess>
}

/** Factories for applications that do not use a paid ad-removal entitlement. */
object AdsAccessProviders {
    val alwaysAllowed: AdsAccessProvider = object : AdsAccessProvider {
        override val access: StateFlow<AdsAccess> = MutableStateFlow(AdsAccess.Allowed)
    }
}
