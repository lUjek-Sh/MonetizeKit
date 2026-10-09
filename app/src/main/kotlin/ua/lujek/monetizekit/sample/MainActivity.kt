package ua.lujek.monetizekit.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private val adsViewModel: SampleAdsViewModel by lazy {
        ViewModelProvider(this)[SampleAdsViewModel::class]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
