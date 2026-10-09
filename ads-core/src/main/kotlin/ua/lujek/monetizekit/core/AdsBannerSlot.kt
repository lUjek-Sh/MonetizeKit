package ua.lujek.monetizekit.core

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
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

/** An Android View host with ordered fallback. Prefer AdsBanner from ads-compose in Compose UI. */
class AdsBannerSlot(
    context: Context,
    private val reservedHeightDp: Int,
) : FrameLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private var candidates: List<BannerAttempt> = emptyList()
    private var activeController: BannerAdController? = null
    private var observationJob: Job? = null
    private var released = false
    private var adsAllowed = MonetizeKit.adsAccess.value == AdsAccess.Allowed

    val state: StateFlow<AdViewState> = mutableState

    init {
        scope.launch {
            MonetizeKit.adsAccess.collect { access ->
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
    }

    internal fun setCandidates(candidates: List<BannerAttempt>) {
        check(!released) { "This AdsBannerSlot has already been released." }
        this.candidates = candidates
    }

    internal fun setError(error: AdsError) {
        mutableState.value = AdViewState.Failed(error)
    }

    fun load() {
        if (!adsAllowed || released || mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        if (candidates.isEmpty()) {
            if (mutableState.value !is AdViewState.Failed) {
                setError(AdsError(message = "No banner network is available."))
            }
            return
        }
        loadCandidate(index = 0)
    }

    fun release() {
        if (released) return
        released = true
        observationJob?.cancel()
        activeController?.destroy()
        activeController = null
        removeAllViews()
        scope.coroutineContext[Job]?.cancel()
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
            attempt.adapter.createBannerController(context, attempt.placement, attempt.adUnitId)
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
                AdViewState.Loaded -> mutableState.value = AdViewState.Loaded
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
}

/** An Android View host with ordered fallback for native placements. */
class AdsNativeSlot(
    context: Context,
    private val reservedHeightDp: Int,
) : FrameLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<AdViewState>(AdViewState.Idle)
    private var candidates: List<NativeAttempt> = emptyList()
    private var activeController: NativeAdController? = null
    private var observationJob: Job? = null
    private var released = false
    private var adsAllowed = MonetizeKit.adsAccess.value == AdsAccess.Allowed

    val state: StateFlow<AdViewState> = mutableState

    init {
        scope.launch {
            MonetizeKit.adsAccess.collect { access ->
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
    }

    internal fun setCandidates(candidates: List<NativeAttempt>) {
        check(!released) { "This AdsNativeSlot has already been released." }
        this.candidates = candidates
    }

    internal fun setError(error: AdsError) {
        mutableState.value = AdViewState.Failed(error)
    }

    fun load() {
        if (!adsAllowed || released || mutableState.value is AdViewState.Loading || mutableState.value is AdViewState.Loaded) return
        if (candidates.isEmpty()) {
            if (mutableState.value !is AdViewState.Failed) setError(AdsError(message = "No native network is available."))
            return
        }
        loadCandidate(index = 0)
    }

    fun release() {
        if (released) return
        released = true
        observationJob?.cancel()
        activeController?.destroy()
        activeController = null
        removeAllViews()
        scope.coroutineContext[Job]?.cancel()
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
            attempt.adapter.createNativeController(context, attempt.placement, attempt.adUnitId)
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
            when (controller.state.filter { it is AdViewState.Loaded || it is AdViewState.Failed }.first()) {
                AdViewState.Loaded -> mutableState.value = AdViewState.Loaded
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
}
