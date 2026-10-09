package ua.lujek.monetizekit.core

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FullscreenOperationLocksTest {
    @Test
    fun `concurrent shows from different placements are globally serialized`() = runBlocking {
        val locks = FullscreenOperationLocks()
        val firstStarted = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val activeShows = AtomicInteger(0)
        val maxActiveShows = AtomicInteger(0)

        val first = async {
            locks.show(PlacementId.of("first")) {
                maxActiveShows.set(maxOf(maxActiveShows.get(), activeShows.incrementAndGet()))
                firstStarted.complete(Unit)
                allowFirstToFinish.await()
                activeShows.decrementAndGet()
            }
        }
        firstStarted.await()

        val second = async {
            locks.show(PlacementId.of("second")) {
                maxActiveShows.set(maxOf(maxActiveShows.get(), activeShows.incrementAndGet()))
                secondStarted.complete(Unit)
                activeShows.decrementAndGet()
            }
        }
        yield()

        assertFalse(secondStarted.isCompleted)
        allowFirstToFinish.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, maxActiveShows.get())
    }
}
