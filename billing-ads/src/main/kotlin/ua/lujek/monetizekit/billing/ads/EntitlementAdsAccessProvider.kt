package ua.lujek.monetizekit.billing.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ua.lujek.monetizekit.billing.core.BillingEntitlementId
import ua.lujek.monetizekit.billing.core.EntitlementProvider
import ua.lujek.monetizekit.billing.core.EntitlementState
import ua.lujek.monetizekit.core.AdsAccess
import ua.lujek.monetizekit.core.AdsAccessProvider

/**
 * Converts application entitlements into the ads policy without coupling ads-core to billing.
 * Unknown billing state is intentionally fail-closed: no inventory is requested or reserved until
 * the active account is known. [AdsAccess.Pending] remains distinct from [AdsAccess.Suppressed],
 * so a cold-start App Open splash can wait for the entitlement result up to its configured timeout.
 */
class EntitlementAdsAccessProvider(
    entitlementProvider: EntitlementProvider,
    suppressAdsWhenAnyOf: Set<BillingEntitlementId>,
    scope: CoroutineScope,
) : AdsAccessProvider {
    init {
        require(suppressAdsWhenAnyOf.isNotEmpty()) {
            "Provide at least one entitlement that suppresses ads. Omit adsAccess entirely when none do."
        }
    }

    override val access: StateFlow<AdsAccess> = entitlementProvider.entitlements
        .map { state -> state.toAdsAccess(suppressAdsWhenAnyOf) }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = entitlementProvider.entitlements.value.toAdsAccess(suppressAdsWhenAnyOf),
        )
}

/**
 * Convenience factory for applications that keep a single process-wide coroutine scope.
 *
 * A product may grant several entitlements. For example `pro_app` can grant both `ads_free` and
 * `pro_sections`; configure only `ads_free` here and use the entitlement provider directly to
 * gate the premium sections.
 */
fun EntitlementProvider.adsAccessProvider(
    scope: CoroutineScope,
    suppressAdsWhenAnyOf: Set<BillingEntitlementId>,
): AdsAccessProvider = EntitlementAdsAccessProvider(
    entitlementProvider = this,
    suppressAdsWhenAnyOf = suppressAdsWhenAnyOf,
    scope = scope,
)

private fun EntitlementState.toAdsAccess(
    suppressAdsWhenAnyOf: Set<BillingEntitlementId>,
): AdsAccess = when (this) {
    is EntitlementState.Available -> {
        if (active.any(suppressAdsWhenAnyOf::contains)) AdsAccess.Suppressed else AdsAccess.Allowed
    }

    EntitlementState.Loading -> AdsAccess.Pending

    is EntitlementState.Unavailable,
    -> AdsAccess.Suppressed
}
