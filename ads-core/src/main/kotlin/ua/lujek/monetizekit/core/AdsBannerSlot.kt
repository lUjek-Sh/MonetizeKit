package ua.lujek.monetizekit.core

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class BannerAttempt(
    val adapter: AdsNetworkAdapter,
    val placement: BannerPlacement,
    val adUnitId: String,
)

internal data class NativeAttempt(
    val adapter: AdsNetworkAdapter,
    val placement: NativePlacement,
    val adUnitId: String,
)

/** Dependencies for an inline slot. The public constructor always uses the process facade. */
internal data class InlineSlotEnvironment(
    val kitState: StateFlow<MonetizeKitState>,
    val adsAccess: StateFlow<AdsAccess>,
    val eventReporter: AdsEventReporter,
)

private fun monetizeKitInlineEnvironment(): InlineSlotEnvironment = InlineSlotEnvironment(
    kitState = MonetizeKit.state,
    adsAccess = MonetizeKit.adsAccess,
    eventReporter = MonetizeKit.adapterEventReporter(),
)

/**
 * An Android View host with ordered fallback. It may be created before consent and network
 * initialization finish; calling [load] then waits for a ready SDK instead of permanently failing.
 * Prefer AdsBanner from ads-compose in Compose UI.
 */
class AdsBannerSlot internal constructor(
    context: Context,
    private val reservedHeightDp: Int,
    private val environment: InlineSlotEnvironment,
) : FrameLayout(context) {
    constructor(context: Context, reservedHeightDp: Int) : this(
        context = context,
        reservedHeightDp = reservedHeightDp,
        environment = monetizeKitInlineEnvironment(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private var candidateProvider: (() -> List<BannerAttempt>)? = null
    private var candidates: List<BannerAttempt> = emptyList()
    private var activeController: BannerAdController? = null
    private var observationJob: Job? = null
    private var released = false
    private var waitingForSdk = false
    private var adsAllowed = environment.adsAccess.value == AdsAccess.Allowed
    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            activeController?.onResume()
        }

        override fun onPause(owner: LifecycleOwner) {
            activeController?.onPause()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            release()
        }
    }

    val state: StateFlow<AdViewState> = mutableState

    init {
        scope.launch {
            environment.adsAccess.collect { access ->
                val nowAllowed = access == AdsAccess.Allowed
                if (adsAllowed == nowAllowed) return@collect
                adsAllowed = nowAllowed
                if (nowAllowed) {
                    mutableState.value = AdViewState.Idle
                    requestLayout()
                    load()
                } else {
                    suppress()
                }
            }
        }
        scope.launch {
            environment.kitState.collect { kitState ->
                when (kitState) {
                    is MonetizeKitState.Ready -> {
                        if (waitingForSdk && adsAllowed && !released) {
                            waitingForSdk = false
                            mutableState.value = AdViewState.Idle
                            load()
                        }
                    }

                    is MonetizeKitState.Failed -> failWhileWaitingForSdk(
                        "No configured banner network could be initialized.",
                    )

                    MonetizeKitState.GdprConsentRequired -> failWhileWaitingForSdk(
                        "Ads cannot be requested until privacy consent allows them.",
                    )

                    else -> Unit
                }
            }
        }
    }

    internal fun setCandidateProvider(candidateProvider: () -> List<BannerAttempt>) {
        check(!released) { "This AdsBannerSlot has already been released." }
        this.candidateProvider = candidateProvider
    }

    internal fun setError(error: AdsError) {
        mutableState.value = AdViewState.Failed(error)
    }

    /**
     * Binds the slot to an explicit lifecycle, for example a Fragment's viewLifecycleOwner.
     * If omitted, the slot binds to the nearest ViewTreeLifecycleOwner after attachment.
     */
    fun bindTo(lifecycleOwner: LifecycleOwner) {
        check(!released) { "This AdsBannerSlot has already been released." }
        if (this.lifecycleOwner === lifecycleOwner) return
        this.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        this.lifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            activeController?.onResume()
        }
    }

    fun load() {
        if (!adsAllowed || released || mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        if (environment.kitState.value !is MonetizeKitState.Ready) {
            waitingForSdk = true
            return
        }
        waitingForSdk = false
        candidates = candidateProvider?.invoke().orEmpty()
        if (candidates.isEmpty()) {
            setError(AdsError(message = "No initialized network can render this banner placement."))
            return
        }
        loadCandidate(index = 0)
    }

    fun release() {
        if (released) return
        released = true
        observationJob?.cancel()
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        activeController?.destroy()
        activeController = null
        removeAllViews()
        scope.coroutineContext[Job]?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (lifecycleOwner == null) findViewTreeLifecycleOwner()?.let(::bindTo)
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun loadCandidate(index: Int) {
        if (released || !adsAllowed) return
        if (index >= candidates.size) {
            setError(AdsError(message = "All configured banner networks failed to load."))
            return
        }
        val attempt = candidates[index]
        val controller = runCatching {
            attempt.adapter.createBannerController(
                context = context,
                placement = attempt.placement,
                adUnitId = attempt.adUnitId,
                eventReporter = environment.eventReporter,
            )
        }.getOrNull()
        if (controller == null) {
            loadCandidate(index + 1)
            return
        }

        activeController?.destroy()
        observationJob?.cancel()
        activeController = controller
        removeAllViews()
        addView(controller.view, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        mutableState.value = AdViewState.Loading
        controller.load()
        observationJob = scope.launch {
            when (val result = controller.state.filter { it is AdViewState.Loaded || it is AdViewState.Failed }.first()) {
                AdViewState.Loaded -> {
                    mutableState.value = AdViewState.Loaded
                    lifecycleOwner?.takeIf { it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
                        ?.let { controller.onResume() }
                }

                is AdViewState.Failed -> {
                    controller.destroy()
                    if (activeController === controller) activeController = null
                    loadCandidate(index + 1)
                }

                else -> Unit
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val height = if (adsAllowed) View.resolveSize(reservedHeightPx, heightMeasureSpec) else 0
        setMeasuredDimension(measuredWidth, height)
    }

    private val reservedHeightPx: Int
        get() = (reservedHeightDp * resources.displayMetrics.density).toInt()

    private fun suppress() {
        observationJob?.cancel()
        activeController?.destroy()
        activeController = null
        removeAllViews()
        mutableState.value = AdViewState.Suppressed
        requestLayout()
    }

    private fun failWhileWaitingForSdk(message: String) {
        if (waitingForSdk && !released) {
            waitingForSdk = false
            setError(AdsError(message = message))
        }
    }
}

/**
 * An Android View host with ordered fallback for native placements. It supports the same delayed
 * creation and lifecycle behavior as [AdsBannerSlot].
 */
class AdsNativeSlot internal constructor(
    context: Context,
    private val reservedHeightDp: Int,
    private val environment: InlineSlotEnvironment,
) : FrameLayout(context) {
    constructor(context: Context, reservedHeightDp: Int) : this(
        context = context,
        reservedHeightDp = reservedHeightDp,
        environment = monetizeKitInlineEnvironment(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private var candidateProvider: (() -> List<NativeAttempt>)? = null
    private var candidates: List<NativeAttempt> = emptyList()
    private var activeController: NativeAdController? = null
    private var observationJob: Job? = null
    private var released = false
    private var waitingForSdk = false
    private var adsAllowed = environment.adsAccess.value == AdsAccess.Allowed
    private var lifecycleOwner: LifecycleOwner? = null
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) {
            activeController?.onResume()
        }

        override fun onPause(owner: LifecycleOwner) {
            activeController?.onPause()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            release()
        }
    }

    val state: StateFlow<AdViewState> = mutableState

    init {
        scope.launch {
            environment.adsAccess.collect { access ->
                val nowAllowed = access == AdsAccess.Allowed
                if (adsAllowed == nowAllowed) return@collect
                adsAllowed = nowAllowed
                if (nowAllowed) {
                    mutableState.value = AdViewState.Idle
                    requestLayout()
                    load()
                } else {
                    suppress()
                }
            }
        }
        scope.launch {
            environment.kitState.collect { kitState ->
                when (kitState) {
                    is MonetizeKitState.Ready -> {
                        if (waitingForSdk && adsAllowed && !released) {
                            waitingForSdk = false
                            mutableState.value = AdViewState.Idle
                            load()
                        }
                    }

                    is MonetizeKitState.Failed -> failWhileWaitingForSdk(
                        "No configured native network could be initialized.",
                    )

                    MonetizeKitState.GdprConsentRequired -> failWhileWaitingForSdk(
                        "Ads cannot be requested until privacy consent allows them.",
                    )

                    else -> Unit
                }
            }
        }
    }

    internal fun setCandidateProvider(candidateProvider: () -> List<NativeAttempt>) {
        check(!released) { "This AdsNativeSlot has already been released." }
        this.candidateProvider = candidateProvider
    }

    internal fun setError(error: AdsError) {
        mutableState.value = AdViewState.Failed(error)
    }

    /** See [AdsBannerSlot.bindTo]. */
    fun bindTo(lifecycleOwner: LifecycleOwner) {
        check(!released) { "This AdsNativeSlot has already been released." }
        if (this.lifecycleOwner === lifecycleOwner) return
        this.lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        this.lifecycleOwner = lifecycleOwner
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            activeController?.onResume()
        }
    }

    fun load() {
        if (!adsAllowed || released || mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        if (environment.kitState.value !is MonetizeKitState.Ready) {
            waitingForSdk = true
            return
        }
        waitingForSdk = false
        candidates = candidateProvider?.invoke().orEmpty()
        if (candidates.isEmpty()) {
            setError(AdsError(message = "No initialized network can render this native placement."))
            return
        }
        loadCandidate(index = 0)
    }

    fun release() {
        if (released) return
        released = true
        observationJob?.cancel()
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        activeController?.destroy()
        activeController = null
        removeAllViews()
        scope.coroutineContext[Job]?.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (lifecycleOwner == null) findViewTreeLifecycleOwner()?.let(::bindTo)
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    private fun loadCandidate(index: Int) {
        if (released || !adsAllowed) return
        if (index >= candidates.size) {
            setError(AdsError(message = "All configured native networks failed to load."))
            return
        }
        val attempt = candidates[index]
        val controller = runCatching {
            attempt.adapter.createNativeController(
                context = context,
                placement = attempt.placement,
                adUnitId = attempt.adUnitId,
                eventReporter = environment.eventReporter,
            )
        }.getOrNull()
        if (controller == null) {
            loadCandidate(index + 1)
            return
        }
        activeController?.destroy()
        observationJob?.cancel()
        activeController = controller
        removeAllViews()
        addView(controller.view, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        mutableState.value = AdViewState.Loading
        controller.load()
        observationJob = scope.launch {
            when (val result = controller.state.filter { it is AdViewState.Loaded || it is AdViewState.Failed }.first()) {
                AdViewState.Loaded -> {
                    mutableState.value = AdViewState.Loaded
                    lifecycleOwner?.takeIf { it.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
                        ?.let { controller.onResume() }
                }

                is AdViewState.Failed -> {
                    controller.destroy()
                    if (activeController === controller) activeController = null
                    loadCandidate(index + 1)
                }

                else -> Unit
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val height = if (adsAllowed) View.resolveSize(reservedHeightPx, heightMeasureSpec) else 0
        setMeasuredDimension(measuredWidth, height)
    }

    private val reservedHeightPx: Int
        get() = (reservedHeightDp * resources.displayMetrics.density).toInt()

    private fun suppress() {
        observationJob?.cancel()
        activeController?.destroy()
        activeController = null
        removeAllViews()
        mutableState.value = AdViewState.Suppressed
        requestLayout()
    }

    private fun failWhileWaitingForSdk(message: String) {
        if (waitingForSdk && !released) {
            waitingForSdk = false
            setError(AdsError(message = message))
        }
    }
}
