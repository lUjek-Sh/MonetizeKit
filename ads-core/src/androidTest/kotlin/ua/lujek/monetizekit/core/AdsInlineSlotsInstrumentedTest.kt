package ua.lujek.monetizekit.core.test

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ua.lujek.monetizekit.core.*

@RunWith(AndroidJUnit4::class)
class AdsInlineSlotsInstrumentedTest {
    @Test
    fun banner_created_before_consent_keeps_space_then_loads_when_sdk_becomes_ready() {
        val kitState = MutableStateFlow<MonetizeKitState>(
            MonetizeKitState.AwaitingGdprConsent(setOf(AdsNetwork.ADMOB)),
        )
        val access = MutableStateFlow<AdsAccess>(AdsAccess.Allowed)
        val controller = RecordingInlineController()
        val placement = AdsPlacements.banner("before_consent_banner", reservedHeightDp = 64)
        lateinit var slot: AdsBannerSlot

        ActivityScenario.launch(InstrumentedTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                slot = AdsBannerSlot(
                    context = activity,
                    reservedHeightDp = placement.reservedHeightDp,
                    environment = environment(kitState, access),
                ).apply {
                    setCandidateProvider {
                        listOf(BannerAttempt(FakeInlineAdapter(controller), placement, "banner-unit"))
                    }
                    load()
                    measure(atMost(1_000), atMost(1_000))
                }
                assertEquals(reservedHeight(activity, placement.reservedHeightDp), slot.measuredHeight)
                assertEquals(0, controller.loadCalls.get())
            }

            kitState.value = MonetizeKitState.Ready(
                availableNetworks = setOf(AdsNetwork.ADMOB),
                unavailableNetworks = emptyMap(),
            )
            awaitLoaded(slot.state)

            assertEquals(1, controller.loadCalls.get())
            access.value = AdsAccess.Suppressed
            awaitSuppressed(slot.state)
            scenario.onActivity {
                slot.measure(atMost(1_000), atMost(1_000))
                assertEquals(0, slot.measuredHeight)
                assertEquals(1, controller.destroyCalls.get())
                slot.release()
            }
        }
    }

    @Test
    fun native_slot_created_after_consent_propagates_pause_resume_and_destroy() {
        val kitState = MutableStateFlow<MonetizeKitState>(
            MonetizeKitState.Ready(setOf(AdsNetwork.ADMOB), emptyMap()),
        )
        val access = MutableStateFlow<AdsAccess>(AdsAccess.Allowed)
        val controller = RecordingInlineController()
        val placement = AdsPlacements.native("ready_native", reservedHeightDp = 180)
        lateinit var slot: AdsNativeSlot
        lateinit var lifecycleOwner: TestLifecycleOwner

        ActivityScenario.launch(InstrumentedTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                lifecycleOwner = TestLifecycleOwner().apply {
                    moveTo(Lifecycle.Event.ON_CREATE)
                    moveTo(Lifecycle.Event.ON_START)
                    moveTo(Lifecycle.Event.ON_RESUME)
                }
                slot = AdsNativeSlot(
                    context = activity,
                    reservedHeightDp = placement.reservedHeightDp,
                    environment = environment(kitState, access),
                ).apply {
                    setCandidateProvider {
                        listOf(NativeAttempt(FakeInlineAdapter(controller), placement, "native-unit"))
                    }
                    bindTo(lifecycleOwner)
                    load()
                }
            }
            awaitLoaded(slot.state)

            scenario.onActivity {
                lifecycleOwner.moveTo(Lifecycle.Event.ON_PAUSE)
                assertEquals(1, controller.pauseCalls.get())

                lifecycleOwner.moveTo(Lifecycle.Event.ON_RESUME)
                assertEquals(2, controller.resumeCalls.get())

                lifecycleOwner.moveTo(Lifecycle.Event.ON_DESTROY)
                assertEquals(1, controller.destroyCalls.get())
            }
            assertTrue(slot.state.value is AdViewState.Loaded)
        }
    }

    private fun environment(
        kitState: StateFlow<MonetizeKitState>,
        access: StateFlow<AdsAccess>,
    ): InlineSlotEnvironment = InlineSlotEnvironment(
        kitState = kitState,
        adsAccess = access,
        eventReporter = AdsEventReporter { },
    )

    private fun awaitLoaded(state: StateFlow<AdViewState>) = runBlocking {
        withTimeout(3_000L) { state.filter { it is AdViewState.Loaded }.first() }
    }

    private fun awaitSuppressed(state: StateFlow<AdViewState>) = runBlocking {
        withTimeout(3_000L) { state.filter { it is AdViewState.Suppressed }.first() }
    }

    private fun atMost(size: Int): Int = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private fun reservedHeight(context: Context, heightDp: Int): Int =
        (heightDp * context.resources.displayMetrics.density).toInt()
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    fun moveTo(event: Lifecycle.Event) {
        registry.handleLifecycleEvent(event)
    }
}

private class RecordingInlineController : BannerAdController, NativeAdController {
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)

    override val view: View
        get() = View(requireNotNull(context) { "The test controller is not attached to an adapter yet." })
    override val state: StateFlow<AdViewState> = mutableState
    val loadCalls = AtomicInteger(0)
    val pauseCalls = AtomicInteger(0)
    val resumeCalls = AtomicInteger(0)
    val destroyCalls = AtomicInteger(0)
    private var context: Context? = null

    fun attach(context: Context): RecordingInlineController = apply {
        this.context = context
    }

    override fun load() {
        loadCalls.incrementAndGet()
        mutableState.value = AdViewState.Loaded
    }

    override fun onPause() {
        pauseCalls.incrementAndGet()
    }

    override fun onResume() {
        resumeCalls.incrementAndGet()
    }

    override fun destroy() {
        destroyCalls.incrementAndGet()
    }
}

private class FakeInlineAdapter(
    private val controller: RecordingInlineController,
) : AdsNetworkAdapter {
    override val network: AdsNetwork = AdsNetwork.ADMOB
    override val supportedFormats: Set<AdFormat> = setOf(AdFormat.BANNER, AdFormat.NATIVE)

    override suspend fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ): NetworkInitializationResult = NetworkInitializationResult.Ready

    override suspend fun loadFullscreen(
        placement: FullscreenPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): AdLoadResult = error("Not used by inline-slot tests.")

    override suspend fun showFullscreen(
        activity: Activity,
        placement: FullscreenPlacement,
        adUnitId: String,
        onReward: (AdReward) -> Unit,
        eventReporter: AdsEventReporter,
    ): AdShowResult = error("Not used by inline-slot tests.")

    override fun createBannerController(
        context: Context,
        placement: BannerPlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): BannerAdController = controller.attach(context)

    override fun createNativeController(
        context: Context,
        placement: NativePlacement,
        adUnitId: String,
        eventReporter: AdsEventReporter,
    ): NativeAdController = controller.attach(context)

    override fun clearFullscreenCache() = Unit
}
