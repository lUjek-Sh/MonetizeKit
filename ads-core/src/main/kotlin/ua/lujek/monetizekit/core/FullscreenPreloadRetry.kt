package ua.lujek.monetizekit.core

import kotlinx.coroutines.delay

/**
 * Outcome of one fullscreen preload request before retry policy is applied.
 *
 * This is separate from [AdLoadResult] so the retry implementation can retain the selected
 * placement policy without exposing internal network details through the public API.
 */
internal sealed interface FullscreenPreloadAttempt {
    data class Loaded(val network: AdsNetwork) : FullscreenPreloadAttempt
    data object Suppressed : FullscreenPreloadAttempt

    data class Failed(
        val error: AdsError,
        val isRetryable: Boolean,
        val retryPolicy: FullscreenRetryPolicy,
    ) : FullscreenPreloadAttempt
}

/**
 * Applies the configured retry policy to a sequence of actual preload attempts.
 *
 * [loadAttempt] intentionally owns only one request. Callers can wrap that request in a
 * placement lock, while the waiting interval remains unlocked so an explicit show or another
 * preload is never stalled by backoff.
 */
internal suspend fun retryFullscreenPreload(
    loadAttempt: suspend () -> FullscreenPreloadAttempt,
    onRetryScheduled: suspend (
        error: AdsError,
        nextAttempt: Int,
        delayMillis: Long,
    ) -> Unit,
    waitForRetry: suspend (Long) -> Unit = { delay(it) },
): FullscreenPreloadAttempt {
    var attempt = 1
    while (true) {
        when (val result = loadAttempt()) {
            is FullscreenPreloadAttempt.Loaded,
            FullscreenPreloadAttempt.Suppressed,
            -> return result

            is FullscreenPreloadAttempt.Failed -> {
                if (!result.isRetryable || attempt >= result.retryPolicy.maxAttempts) {
                    return result
                }
                val delayMillis = result.retryPolicy.delayAfterFailedAttempt(attempt)
                onRetryScheduled(result.error, attempt + 1, delayMillis)
                waitForRetry(delayMillis)
                attempt += 1
            }
        }
    }
}
