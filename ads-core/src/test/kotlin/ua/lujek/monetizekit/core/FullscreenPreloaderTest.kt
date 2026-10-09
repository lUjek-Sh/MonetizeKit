package ua.lujek.monetizekit.core

import android.app.Activity
import android.app.Application
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenPreloaderTest {
    private val placement = AdsPlacements.interstitial("level_complete")

    @Test
    fun `preload preserves priority and stops after the first loaded candidate`() = runBlocking {
        val calls = mutableListOf<String>()
        val meta = FakeAdapter(AdsNetwork.META, calls, AdLoadResult.Failed(AdsError(message = "No fill")))
        val adMob = FakeAdapter(AdsNetwork.ADMOB, calls, AdLoadResult.Loaded)
        val unity = FakeAdapter(AdsNetwork.UNITY, calls, AdLoadResult.Loaded)

        val result = preloadFirstFullscreenCandidate(
            placement = placement,
            candidates = listOf(
                candidate(AdsNetwork.META, "meta"),
                candidate(AdsNetwork.ADMOB, "admob"),
                candidate(AdsNetwork.UNITY, "unity"),
            ),
            adapters = mapOf(
                AdsNetwork.META to meta,
                AdsNetwork.ADMOB to adMob,
                AdsNetwork.UNITY to unity,
            ),
            readyNetworks = setOf(AdsNetwork.META, AdsNetwork.ADMOB, AdsNetwork.UNITY),
            eventReporter = AdsEventReporter { },
        )

        assertTrue(result is FullscreenPreloadResult.Loaded)
        assertEquals(AdsNetwork.ADMOB, (result as FullscreenPreloadResult.Loaded).network)
        assertEquals(listOf("meta", "admob"), calls)
    }

    @Test
    fun `preload skips an unavailable network without changing the configured order`() = runBlocking {
        val calls = mutableListOf<String>()
        val meta = FakeAdapter(AdsNetwork.META, calls, AdLoadResult.Loaded)
        val adMob = FakeAdapter(AdsNetwork.ADMOB, calls, AdLoadResult.Loaded)

        val result = preloadFirstFullscreenCandidate(
            placement = placement,
            candidates = listOf(
                candidate(AdsNetwork.META, "meta"),
                candidate(AdsNetwork.ADMOB, "admob"),
            ),
            adapters = mapOf(AdsNetwork.META to meta, AdsNetwork.ADMOB to adMob),
            readyNetworks = setOf(AdsNetwork.ADMOB),
            eventReporter = AdsEventReporter { },
        )

        assertTrue(result is FullscreenPreloadResult.Loaded)
        assertEquals(AdsNetwork.ADMOB, (result as FullscreenPreloadResult.Loaded).network)
        assertEquals(listOf("admob"), calls)
    }

    @Test
    fun `App Open can be preloaded by a supporting adapter`() = runBlocking {
        val appOpen = AdsPlacements.appOpen("app_return")
        val calls = mutableListOf<String>()
        val adMob = FakeAdapter(AdsNetwork.ADMOB, calls, AdLoadResult.Loaded, AdFormat.APP_OPEN)

        val result = preloadFirstFullscreenCandidate(
            placement = appOpen,
            candidates = listOf(candidate(AdsNetwork.ADMOB, "admob_app_open")),
            adapters = mapOf(AdsNetwork.ADMOB to adMob),
            readyNetworks = setOf(AdsNetwork.ADMOB),
            eventReporter = AdsEventReporter { },
        )

        assertTrue(result is FullscreenPreloadResult.Loaded)
        assertEquals(listOf("admob_app_open"), calls)
    }

    private fun candidate(network: AdsNetwork, id: String): NetworkPlacement = NetworkPlacement(
        network = network,
        adUnitId = id,
        route = AdRequestRoute.DIRECT,
    )
}

private class FakeAdapter(
    override val network: AdsNetwork,
    private val calls: MutableList<String>,
    private val result: AdLoadResult,
    format: AdFormat = AdFormat.INTERSTITIAL,
) : AdsNetworkAdapter {
    override val supportedFormats: Set<AdFormat> = setOf(format)

    override suspend fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ): NetworkInitializationResult = NetworkInitializationResult.Ready

    override suspend fun loadFullscreen(
        placement: FullscreenPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdLoadResult {
        calls += adUnitId
        return result
    }

    override suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
        eventReporter: AdsEventReporter,
    ): AdShowResult = error("Not used by preload tests")

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): BannerAdController? = null

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): NativeAdController? = null

    override fun clearFullscreenCache() = Unit
}
