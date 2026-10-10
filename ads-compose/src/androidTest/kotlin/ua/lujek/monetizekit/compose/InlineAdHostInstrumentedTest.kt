package ua.lujek.monetizekit.compose

import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ua.lujek.monetizekit.core.AdsAccess

@RunWith(AndroidJUnit4::class)
class InlineAdHostInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun suppressed_access_never_creates_an_sdk_view() {
        val factoryCalls = AtomicInteger(0)

        composeRule.setContent {
            InlineAdHost(
                adsAccess = AdsAccess.Suppressed,
                modifier = Modifier,
                factory = { context ->
                    factoryCalls.incrementAndGet()
                    View(context)
                },
                update = {},
                onRelease = {},
            )
        }

        composeRule.runOnIdle {
            assertEquals(0, factoryCalls.get())
        }
    }

    @Test
    fun allowed_access_releases_the_sdk_view_when_removed_from_composition() {
        val factoryCalls = AtomicInteger(0)
        val releaseCalls = AtomicInteger(0)
        val isVisible = mutableStateOf(true)

        composeRule.setContent {
            if (isVisible.value) {
                InlineAdHost(
                    adsAccess = AdsAccess.Allowed,
                    modifier = Modifier,
                    factory = { context ->
                        factoryCalls.incrementAndGet()
                        View(context)
                    },
                    update = {},
                    onRelease = { releaseCalls.incrementAndGet() },
                )
            }
        }

        composeRule.runOnIdle {
            assertEquals(1, factoryCalls.get())
            isVisible.value = false
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(1, releaseCalls.get())
        }
    }
}
