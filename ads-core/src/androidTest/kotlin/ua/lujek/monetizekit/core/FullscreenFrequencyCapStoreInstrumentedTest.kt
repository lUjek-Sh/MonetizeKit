package ua.lujek.monetizekit.core.test

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import ua.lujek.monetizekit.core.*

@RunWith(AndroidJUnit4::class)
class FullscreenFrequencyCapStoreInstrumentedTest {
    @Test
    fun frequency_cap_history_survives_a_new_store_instance() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<android.content.Context>()
        val placementId = PlacementId.of("instrumented_cap_${UUID.randomUUID()}")
        val cap = FullscreenFrequencyCap(cooldownMillis = 60_000L)
        val firstStore = FullscreenFrequencyCapStore(application)

        firstStore.recordShow(
            placementId = placementId,
            frequencyCap = cap,
            shownAtEpochMillis = 1_000L,
        )

        val restoredHistory = FullscreenFrequencyCapStore(application).shownAt(placementId)

        assertEquals(listOf(1_000L), restoredHistory)
        assertEquals(
            FrequencyCapDecision.Capped(61_000L),
            cap.evaluate(restoredHistory, nowEpochMillis = 60_999L),
        )
    }
}
