package ua.lujek.monetizekit.sample

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class SampleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersDebugControlsWithoutStartingRealAds() {
        composeRule.setContent {
            MonetizeKitSampleTheme {
                SampleScreen(
                    state = SampleAdsUiState(
                        adsEnabled = true,
                        isSdkReady = true,
                        sdkStatus = "Ready: ADMOB",
                        lastAction = "Preload: ready",
                        lastEvent = "debug_interstitial preloaded by ADMOB",
                    ),
                    onAction = {},
                    showInlineAds = false,
                )
            }
        }

        composeRule.onNodeWithTag("sample_title").assertExists()
    }
}
