package ua.lujek.monetizekit.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/** Debug-only host for asserting the sample's rendered Compose UI through accessibility. */
class SampleComposeTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
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
    }
}
