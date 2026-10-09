package ua.lujek.monetizekit.sample

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
                        privacyStatus = "completed",
                        privacyProfile = SamplePrivacyProfile.UMP_DEFAULT,
                        umpTestDeviceHash = "",
                        networkTestSetup = "AdMob demo",
                        lastAction = "Preload: ready",
                        lastEvent = "debug_interstitial preloaded by ADMOB",
                        inlineReloadKey = 0,
                    ),
                    onAction = {},
                    showInlineAds = false,
                )
            }
        }

        composeRule.onNodeWithTag("sample_title").assertExists()
        composeRule.onNodeWithText("Open Ad Inspector").assertExists()
        composeRule.onNodeWithText("Run configured consent flow").assertExists()
        composeRule.onNodeWithTag("ump_test_device_hash").assertExists()
    }
}
