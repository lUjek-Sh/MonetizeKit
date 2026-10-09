package ua.lujek.monetizekit.core

/**
 * Maximum age for a manually preloaded fullscreen creative.
 *
 * Google Mobile Ads requires a fresh load after one hour. MonetizeKit applies the same upper
 * bound to every direct network so a placement never serves a stale cached creative merely
 * because its fallback network changed.
 */
const val FULLSCREEN_AD_MAX_AGE_MILLIS: Long = 60L * 60L * 1_000L

/** Uses elapsed-realtime values, so wall-clock changes cannot extend a creative's lifetime. */
fun isFullscreenAdExpired(
    loadedAtElapsedRealtimeMillis: Long,
    nowElapsedRealtimeMillis: Long,
): Boolean = nowElapsedRealtimeMillis - loadedAtElapsedRealtimeMillis >= FULLSCREEN_AD_MAX_AGE_MILLIS
