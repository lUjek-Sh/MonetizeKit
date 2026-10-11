package ua.lujek.monetizekit.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import ua.lujek.monetizekit.core.MonetizeKit

class MainActivity : ComponentActivity() {
    private val adsViewModel: SampleAdsViewModel by lazy {
        ViewModelProvider(this)[SampleAdsViewModel::class]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        MonetizeKit.bindColdStartSplashScreen(splashScreen)
        super.onCreate(savedInstanceState)
        val systemBarColor = getColor(R.color.sample_canvas)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = systemBarColor,
                darkScrim = systemBarColor,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = systemBarColor,
                darkScrim = systemBarColor,
            ),
        )
        setContent {
            val uiState by adsViewModel.uiState.collectAsState()
            MonetizeKitSampleTheme {
                SampleScreen(
                    state = uiState,
                    onAction = { action -> adsViewModel.onAction(this, action) },
                )
            }
        }
    }
}
