package ua.lujek.monetizekit.core

import kotlin.math.pow

/**
 * Limits fullscreen impressions for one placement. The history survives process restarts.
 *
 * Use [cooldownMillis] to require a minimum time between impressions. To additionally limit
 * impressions in a rolling window, provide [maxImpressions] and [windowMillis] together.
 */
data class FullscreenFrequencyCap(
    val cooldownMillis: Long = 0L,
    val maxImpressions: Int? = null,
    val windowMillis: Long? = null,
) {
    init {
        require(cooldownMillis >= 0L) { "Fullscreen frequency-cap cooldownMillis must not be negative." }
        require((maxImpressions == null) == (windowMillis == null)) {
            "Fullscreen frequency-cap maxImpressions and windowMillis must be set together."
        }
        if (maxImpressions != null) {
            require(maxImpressions > 0) { "Fullscreen frequency-cap maxImpressions must be greater than zero." }
            require(requireNotNull(windowMillis) > 0L) {
                "Fullscreen frequency-cap windowMillis must be greater than zero."
            }
        }
        require(cooldownMillis > 0L || maxImpressions != null) {
            "Fullscreen frequency cap needs a cooldown and/or a rolling impression limit."
        }
    }
}

/**
 * Retries a failed fullscreen preload with exponential backoff.
 *
 * [maxAttempts] includes the first request. The default value of one preserves the previous
 * behavior: one request and no automatic retry. Explicit `show()` calls never wait for retries.
 */
data class FullscreenRetryPolicy(
    val maxAttempts: Int = 1,
    val initialDelayMillis: Long = 1_000L,
    val maxDelayMillis: Long = 30_000L,
    val backoffMultiplier: Double = 2.0,
) {
    init {
        require(maxAttempts > 0) { "Fullscreen retry maxAttempts must be greater than zero." }
        require(initialDelayMillis >= 0L) { "Fullscreen retry initialDelayMillis must not be negative." }
        require(maxDelayMillis >= initialDelayMillis) {
            "Fullscreen retry maxDelayMillis must be at least initialDelayMillis."
        }
        require(backoffMultiplier.isFinite() && backoffMultiplier >= 1.0) {
            "Fullscreen retry backoffMultiplier must be finite and at least 1.0."
        }
        require(maxAttempts == 1 || initialDelayMillis > 0L) {
            "Fullscreen retry initialDelayMillis must be greater than zero when retries are enabled."
        }
    }

    /** Returns the delay after a failed [attempt], where the first request is attempt one. */
    fun delayAfterFailedAttempt(attempt: Int): Long {
        require(attempt in 1 until maxAttempts) {
            "Retry delay is only defined for a failed attempt before the final attempt."
        }
        val exponentialDelay = initialDelayMillis.toDouble() * backoffMultiplier.pow(attempt - 1)
        return exponentialDelay.coerceAtMost(maxDelayMillis.toDouble()).toLong()
    }
}

/** Per-placement rules for fullscreen loading and presentation. */
data class FullscreenPlacementPolicy(
    val frequencyCap: FullscreenFrequencyCap? = null,
    val retryPolicy: FullscreenRetryPolicy = FullscreenRetryPolicy(),
)

internal sealed interface FrequencyCapDecision {
    data object Allowed : FrequencyCapDecision

    data class Capped(val nextAllowedAtEpochMillis: Long) : FrequencyCapDecision
}

internal fun FullscreenFrequencyCap.evaluate(
    shownAtEpochMillis: List<Long>,
    nowEpochMillis: Long,
): FrequencyCapDecision {
    val timestamps = shownAtEpochMillis.filter { it >= 0L }.sorted()
    val blockedUntil = buildList {
        timestamps.lastOrNull()?.let { lastShownAt ->
            if (cooldownMillis > 0L && nowEpochMillis < lastShownAt.saturatedAdd(cooldownMillis)) {
                add(lastShownAt.saturatedAdd(cooldownMillis))
            }
        }

        val rollingWindowMillis = windowMillis
        val rollingMaximum = maxImpressions
        if (rollingWindowMillis != null && rollingMaximum != null) {
            val timestampsInWindow = timestamps.filter { timestamp ->
                timestamp > nowEpochMillis - rollingWindowMillis
            }
            if (timestampsInWindow.size >= rollingMaximum) {
                val nextAllowedAt = timestampsInWindow[timestampsInWindow.size - rollingMaximum]
                    .saturatedAdd(rollingWindowMillis)
                if (nowEpochMillis < nextAllowedAt) add(nextAllowedAt)
            }
        }
    }
    return blockedUntil.maxOrNull()
        ?.let(FrequencyCapDecision::Capped)
        ?: FrequencyCapDecision.Allowed
}

internal fun FullscreenFrequencyCap.historyAfterShow(
    history: List<Long>,
    shownAtEpochMillis: Long,
): List<Long> {
    val retentionMillis = maxOf(cooldownMillis, windowMillis ?: 0L)
    val cutoff = shownAtEpochMillis - retentionMillis
    val maximumEntries = maxImpressions ?: 1
    return (history + shownAtEpochMillis)
        .filter { timestamp -> timestamp >= cutoff }
        .sorted()
        .takeLast(maximumEntries)
}

private fun Long.saturatedAdd(other: Long): Long =
    if (this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other
