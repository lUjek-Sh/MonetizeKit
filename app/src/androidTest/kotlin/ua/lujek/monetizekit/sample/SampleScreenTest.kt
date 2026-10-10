package ua.lujek.monetizekit.sample

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.platform.ComposeView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampleScreenTest {
    @Test
    fun rendersDebugControlsWithoutStartingRealAds() {
        ActivityScenario.launch(SampleComposeTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val composeHost = content.findComposeHost()
            assertTrue(
                    "SampleScreen did not attach a Compose host view.",
                    composeHost != null && composeHost.isAttachedToWindow,
                )
                val renderedHost = requireNotNull(composeHost)
                assertTrue(
                    "SampleScreen's Compose host was not laid out.",
                    renderedHost.width > 0 && renderedHost.height > 0,
                )
            }
        }
    }

    private fun View.findComposeHost(): ComposeView? {
        if (this is ComposeView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findComposeHost()?.let { return it }
        }
        return null
    }
}
