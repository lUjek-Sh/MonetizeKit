package ua.lujek.monetizekit.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Serializes fullscreen operations without delaying independent inline ad work.
 *
 * The global lock guarantees that two fullscreens cannot be visible at once; the per-placement
 * lock also protects preload and cache replacement for a single placement.
 */
internal class FullscreenOperationLocks {
    private val showMutex = Mutex()
    private val placementMutexes = ConcurrentHashMap<PlacementId, Mutex>()

    suspend fun <T> show(placementId: PlacementId, block: suspend () -> T): T = showMutex.withLock {
        forPlacement(placementId, block)
    }

    suspend fun <T> forPlacement(placementId: PlacementId, block: suspend () -> T): T =
        placementMutexes.getOrPut(placementId) { Mutex() }.withLock { block() }
}
