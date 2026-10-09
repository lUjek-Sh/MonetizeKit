package ua.lujek.monetizekit.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenAdExpiryTest {
    @Test
    fun `creative remains valid before one hour`() {
        assertFalse(
            isFullscreenAdExpired(
                loadedAtElapsedRealtimeMillis = 10_000L,
                nowElapsedRealtimeMillis = 10_000L + FULLSCREEN_AD_MAX_AGE_MILLIS - 1L,
            ),
        )
    }

    @Test
    fun `creative expires exactly after one hour`() {
        assertTrue(
            isFullscreenAdExpired(
                loadedAtElapsedRealtimeMillis = 10_000L,
                nowElapsedRealtimeMillis = 10_000L + FULLSCREEN_AD_MAX_AGE_MILLIS,
            ),
        )
    }
}
