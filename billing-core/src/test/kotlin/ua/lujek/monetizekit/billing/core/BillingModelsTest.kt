package ua.lujek.monetizekit.billing.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingModelsTest {
    @Test
    fun `one product can map to several arbitrary entitlement ids`() {
        val adsFree = BillingEntitlementId.of("ads_free")
        val proSections = BillingEntitlementId.of("pro_sections")
        val product = BillingProduct(
            id = BillingProductId.of("pro_app"),
            type = BillingProductType.ONE_TIME_NON_CONSUMABLE,
            grants = setOf(adsFree, proSections),
        )

        val catalog = BillingCatalog(listOf(product))

        assertEquals(product, catalog[BillingProductId.of("pro_app")])
        assertTrue(catalog.products.single().grants.containsAll(setOf(adsFree, proSections)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `consumables cannot grant durable entitlements`() {
        BillingProduct(
            id = BillingProductId.of("coin_pack"),
            type = BillingProductType.ONE_TIME_CONSUMABLE,
            grants = setOf(BillingEntitlementId.of("coins")),
        )
    }
}
