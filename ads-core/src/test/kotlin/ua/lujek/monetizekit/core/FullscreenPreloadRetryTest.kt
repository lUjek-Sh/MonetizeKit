package ua.lujek.monetizekit.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FullscreenPreloadRetryTest {
    @Test
    fun `retry waits then performs another real preload attempt`() = runBlocking {
        val error = AdsError(network = AdsNetwork.ADMOB, message = "Temporary network failure")
        val attempts = ArrayDeque<FullscreenPreloadAttempt>(
            listOf(
                FullscreenPreloadAttempt.Failed(
                    error = error,
                    isRetryable = true,
                    retryPolicy = FullscreenRetryPolicy(
                        maxAttempts = 2,
                        initialDelayMillis = 250L,
                    ),
                ),
                FullscreenPreloadAttempt.Loaded(AdsNetwork.ADMOB),
            ),
        )
        val scheduledRetries = mutableListOf<Triple<AdsError, Int, Long>>()
        val waits = mutableListOf<Long>()

        val result = retryFullscreenPreload(
            loadAttempt = { attempts.removeFirst() },
            onRetryScheduled = { retryError, nextAttempt, delayMillis ->
                scheduledRetries += Triple(retryError, nextAttempt, delayMillis)
            },
            waitForRetry = waits::add,
        )

        assertEquals(0, attempts.size)
        assertEquals(AdsNetwork.ADMOB, (result as FullscreenPreloadAttempt.Loaded).network)
        assertEquals(listOf(Triple(error, 2, 250L)), scheduledRetries)
        assertEquals(listOf(250L), waits)
    }

    @Test
    fun `non retryable failure returns without waiting`() = runBlocking {
        val failure = FullscreenPreloadAttempt.Failed(
            error = AdsError(message = "Placement is not configured"),
            isRetryable = false,
            retryPolicy = FullscreenRetryPolicy(maxAttempts = 3),
        )
        var attemptCount = 0
        var retryScheduled = false
        var waited = false

        val result = retryFullscreenPreload(
            loadAttempt = {
                attemptCount += 1
                failure
            },
            onRetryScheduled = { _, _, _ -> retryScheduled = true },
            waitForRetry = { waited = true },
        )

        assertEquals(failure, result)
        assertEquals(1, attemptCount)
        assertEquals(false, retryScheduled)
        assertEquals(false, waited)
    }
}
