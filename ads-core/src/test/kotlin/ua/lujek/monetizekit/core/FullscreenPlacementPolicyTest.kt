package ua.lujek.monetizekit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenPlacementPolicyTest {
    @Test
    fun `cooldown blocks a second fullscreen until its exact expiry`() {
        val cap = FullscreenFrequencyCap(cooldownMillis = 60_000L)

        val capped = cap.evaluate(shownAtEpochMillis = listOf(10_000L), nowEpochMillis = 69_999L)
        val allowed = cap.evaluate(shownAtEpochMillis = listOf(10_000L), nowEpochMillis = 70_000L)

        assertEquals(FrequencyCapDecision.Capped(70_000L), capped)
        assertEquals(FrequencyCapDecision.Allowed, allowed)
    }

    @Test
    fun `rolling cap releases when the oldest counted impression leaves the window`() {
        val cap = FullscreenFrequencyCap(maxImpressions = 2, windowMillis = 100L)

        val capped = cap.evaluate(shownAtEpochMillis = listOf(0L, 50L), nowEpochMillis = 75L)
        val allowed = cap.evaluate(shownAtEpochMillis = listOf(0L, 50L), nowEpochMillis = 100L)

        assertEquals(FrequencyCapDecision.Capped(100L), capped)
        assertEquals(FrequencyCapDecision.Allowed, allowed)
    }

    @Test
    fun `stored history retains only data that can affect a future cap`() {
        val cap = FullscreenFrequencyCap(
            cooldownMillis = 50L,
            maxImpressions = 2,
            windowMillis = 100L,
        )

        val history = cap.historyAfterShow(
            history = listOf(0L, 25L, 90L),
            shownAtEpochMillis = 100L,
        )

        assertEquals(listOf(90L, 100L), history)
    }

    @Test
    fun `retry delay grows exponentially and remains bounded`() {
        val policy = FullscreenRetryPolicy(
            maxAttempts = 4,
            initialDelayMillis = 1_000L,
            maxDelayMillis = 2_500L,
            backoffMultiplier = 2.0,
        )

        assertEquals(1_000L, policy.delayAfterFailedAttempt(1))
        assertEquals(2_000L, policy.delayAfterFailedAttempt(2))
        assertEquals(2_500L, policy.delayAfterFailedAttempt(3))
    }

    @Test
    fun `fullscreen policy is retained by the placement configuration`() {
        val placement = AdsPlacements.interstitial("level_complete")
        val policy = FullscreenPlacementPolicy(
            frequencyCap = FullscreenFrequencyCap(cooldownMillis = 30_000L),
            retryPolicy = FullscreenRetryPolicy(maxAttempts = 3),
        )

        val configuration = monetizeConfiguration {
            direct {
                admob { applicationId = "ca-app-pub-test" }
            }
            interstitial(placement, policy) {
                admob("ca-app-pub-unit")
            }
        }

        assertEquals(policy, configuration.placements.getValue(placement.id).fullscreenPolicy)
    }

    @Test
    fun `frequency cap rejects an incomplete rolling-window rule`() {
        val exception = runCatching {
            FullscreenFrequencyCap(maxImpressions = 2)
        }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }
}
