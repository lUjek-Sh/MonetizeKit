package ua.lujek.monetizekit.billing.googleplay

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ua.lujek.monetizekit.billing.core.BillingCatalog
import ua.lujek.monetizekit.billing.core.BillingEntitlementId
import ua.lujek.monetizekit.billing.core.BillingProduct
import ua.lujek.monetizekit.billing.core.BillingProductId
import ua.lujek.monetizekit.billing.core.BillingProductType
import ua.lujek.monetizekit.billing.core.EntitlementState
import ua.lujek.monetizekit.billing.core.PurchaseVerification

class BillingTestActivity : Activity()

@RunWith(AndroidJUnit4::class)
class GooglePlayBillingInstrumentedTest {
    @Test
    fun unknown_product_fails_before_connecting_to_google_play() {
        ActivityScenario.launch(BillingTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val billing = billing(activity)
                val result = runBlocking {
                    billing.launchPurchase(activity, BillingProductId.of("not_configured"))
                }

                assertTrue(result is PurchaseLaunchResult.Failed)
                assertEquals(
                    "Product 'not_configured' is not configured in BillingCatalog.",
                    (result as PurchaseLaunchResult.Failed).failure.message,
                )
                billing.close()
            }
        }
    }

    @Test
    fun close_is_idempotent_and_moves_billing_to_a_safe_terminal_state() {
        ActivityScenario.launch(BillingTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val billing = billing(activity)

                billing.close()
                billing.close()

                assertEquals(GooglePlayBillingState.Closed, billing.state.value)
                assertTrue(billing.entitlements.value is EntitlementState.Unavailable)
            }
        }
    }

    private fun billing(activity: Activity): GooglePlayBilling = GooglePlayBilling(
        context = activity,
        catalog = BillingCatalog(
            listOf(
                BillingProduct(
                    id = BillingProductId.of("pro_app"),
                    type = BillingProductType.ONE_TIME_NON_CONSUMABLE,
                    grants = setOf(BillingEntitlementId.of("ads_free")),
                ),
            ),
        ),
        purchaseVerifier = { PurchaseVerification.Verified },
    )
}
