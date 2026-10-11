package ua.lujek.monetizekit.billing.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ua.lujek.monetizekit.billing.core.BillingEntitlementId
import ua.lujek.monetizekit.billing.core.BillingFailure
import ua.lujek.monetizekit.billing.core.MutableEntitlementProvider
import ua.lujek.monetizekit.core.AdsAccess
import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementAdsAccessProviderTest {
    @Test
    fun `pro purchase can disable ads while retaining a separate section entitlement`() {
        val adsFree = BillingEntitlementId.of("ads_free")
        val proSections = BillingEntitlementId.of("pro_sections")
        val source = MutableEntitlementProvider()
        val provider = source.adsAccessProvider(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            suppressAdsWhenAnyOf = setOf(adsFree),
        )

        assertEquals(AdsAccess.Pending, provider.access.value)

        source.setAvailable(setOf(proSections))
        assertEquals(AdsAccess.Allowed, provider.access.value)

        source.setAvailable(setOf(adsFree, proSections))
        assertEquals(AdsAccess.Suppressed, provider.access.value)

        source.setUnavailable(BillingFailure(message = "Billing service unavailable"))
        assertEquals(AdsAccess.Suppressed, provider.access.value)
    }
}
