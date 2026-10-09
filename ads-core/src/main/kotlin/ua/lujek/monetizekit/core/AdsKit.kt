package ua.lujek.monetizekit.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.util.concurrent.ConcurrentHashMap

sealed interface MonetizeKitState {
    data object Uninitialized : MonetizeKitState

    data class Initializing(
        val networks: Set<AdsNetwork>,
    ) : MonetizeKitState

    data class AwaitingGdprConsent(
        val networks: Set<AdsNetwork>,
    ) : MonetizeKitState

    data object GdprConsentRequired : MonetizeKitState

    data class Ready(
        val availableNetworks: Set<AdsNetwork>,
        val unavailableNetworks: Map<AdsNetwork, AdsError>,
    ) : MonetizeKitState

    data class Failed(
        val errors: Map<AdsNetwork, AdsError>,
    ) : MonetizeKitState
}

sealed interface AdsEvent {
    data class NetworkInitialized(val network: AdsNetwork) : AdsEvent
    data class NetworkInitializationFailed(val network: AdsNetwork, val error: AdsError) : AdsEvent
    data class AdPreloaded(val placement: FullscreenPlacement, val network: AdsNetwork) : AdsEvent
    data class AdPreloadFailed(val placement: FullscreenPlacement, val error: AdsError) : AdsEvent
    data class AdShown(val placement: AdPlacement, val network: AdsNetwork) : AdsEvent
    data class AdShowFailed(val placement: AdPlacement, val error: AdsError) : AdsEvent
}

/**
 * Process-wide ad facade. Configure it once from [Application.onCreate].
 * Network modules self-register before Application.onCreate through a manifest provider.
 */
object MonetizeKit {
    private val lock = Any()
    private val adapterFactories = ConcurrentHashMap<AdsNetwork, AdsNetworkAdapterFactory>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<MonetizeKitState>(MonetizeKitState.Uninitialized)
    private val mutableEvents = MutableSharedFlow<AdsEvent>(extraBufferCapacity = 32)
    private val mutableGdprConsent = MutableStateFlow<GdprConsentState>(GdprConsentState.NotRequested)
    private val consentMutex = Mutex()
    private val fullscreenShowMutex = Mutex()
    private val fullscreenPlacementMutexes = ConcurrentHashMap<PlacementId, Mutex>()

    private var container: KoinApplication? = null
    private var initializationJob: Job? = null

    val state: StateFlow<MonetizeKitState> = mutableState.asStateFlow()
    val events: SharedFlow<AdsEvent> = mutableEvents.asSharedFlow()
    val gdprConsent: StateFlow<GdprConsentState> = mutableGdprConsent.asStateFlow()

    /** Called automatically by an included network module. Host apps should not need this method. */
    @JvmStatic
    fun registerNetworkAdapter(factory: AdsNetworkAdapterFactory) {
        synchronized(lock) {
            check(mutableState.value is MonetizeKitState.Uninitialized) {
                "Network adapters must be registered before MonetizeKit.initialize()."
            }
            val adapter = factory.create()
            check(adapterFactories.putIfAbsent(adapter.network, factory) == null) {
                "An adapter for ${adapter.network} is already registered."
            }
        }
    }

    @JvmStatic
    fun initialize(
        application: Application,
        block: AdsConfigurationBuilder.() -> Unit,
    ) {
        initialize(application, monetizeConfiguration(block))
    }

    @JvmStatic
    fun initialize(
        application: Application,
        configuration: AdsConfiguration,
    ) {
        synchronized(lock) {
            check(mutableState.value is MonetizeKitState.Uninitialized) {
                "MonetizeKit has already been initialized. Configure it once in Application.onCreate()."
            }

            val configuredNetworks = configuration.configuredNetworks()
            val adapters = configuredNetworks.associateWith { network ->
                adapterFactories[network]?.create()
            }.filterValues { it != null }.mapValues { (_, adapter) -> requireNotNull(adapter) }
            val runtime = AdsRuntime(
                configuration = configuration,
                adapters = adapters,
                consentManager = GdprConsentManager(application, configuration.gdpr),
            )

            container = koinApplication {
                modules(
                    module {
                        single { runtime }
                        single { configuration }
                    },
                )
            }
            mutableState.value = MonetizeKitState.AwaitingGdprConsent(configuredNetworks)
            registerFirstActivityConsentFlow(application)
        }
    }

    /**
     * Requests consent information and presents a UMP form when required. Normally this runs
     * automatically for the first resumed activity; call it explicitly to control launch timing.
     */
    suspend fun gatherGdprConsent(activity: Activity): ConsentSnapshot = consentMutex.withLock {
        val runtime = currentRuntime()
            ?: return@withLock ConsentSnapshot(
                canRequestAds = false,
                isPrivacyOptionsRequired = false,
                error = AdsError(message = "MonetizeKit is not initialized."),
            )
        mutableGdprConsent.value = GdprConsentState.Gathering
        val snapshot = runCatching { runtime.consentManager.gather(activity) }
            .getOrElse { throwable ->
                ConsentSnapshot(
                    canRequestAds = false,
                    isPrivacyOptionsRequired = false,
                    error = AdsError(message = throwable.message ?: "GDPR consent gathering failed.", cause = throwable),
                )
            }
        mutableGdprConsent.value = GdprConsentState.Resolved(snapshot)
        if (snapshot.canRequestAds) {
            startAdaptersIfAllowed(activity.application, runtime)
        } else {
            mutableState.value = MonetizeKitState.GdprConsentRequired
        }
        snapshot
    }

    /** Call only from a user action, for example an in-app Privacy settings row. */
    suspend fun showPrivacyOptions(activity: Activity): ConsentSnapshot = consentMutex.withLock {
        val runtime = currentRuntime()
            ?: return@withLock ConsentSnapshot(
                canRequestAds = false,
                isPrivacyOptionsRequired = false,
                error = AdsError(message = "MonetizeKit is not initialized."),
            )
        val snapshot = runCatching { runtime.consentManager.showPrivacyOptions(activity) }
            .getOrElse { throwable ->
                ConsentSnapshot(
                    canRequestAds = false,
                    isPrivacyOptionsRequired = false,
                    error = AdsError(message = throwable.message ?: "Privacy options could not be shown.", cause = throwable),
                )
            }
        mutableGdprConsent.value = GdprConsentState.Resolved(snapshot)
        if (snapshot.canRequestAds) startAdaptersIfAllowed(activity.application, runtime)
        return@withLock snapshot
    }

    suspend fun show(
        activity: Activity,
        placement: InterstitialPlacement,
    ): AdShowResult = fullscreenShowMutex.withLock {
        withFullscreenPlacementLock(placement.id) {
            showInternal(activity, placement) {}
        }
    }

    suspend fun show(
        activity: Activity,
        placement: RewardedPlacement,
        onReward: (AdReward) -> Unit,
    ): AdShowResult = fullscreenShowMutex.withLock {
        withFullscreenPlacementLock(placement.id) {
            showInternal(activity, placement, onReward)
        }
    }

    /**
     * Prepares an interstitial without showing it. Calls for the same placement are serialized,
     * and direct-mode candidates are tried in their configured priority order.
     */
    suspend fun preload(placement: InterstitialPlacement): AdLoadResult =
        withFullscreenPlacementLock(placement.id) { preloadInternal(placement) }

    /**
     * Prepares a rewarded ad without showing it. Calls for the same placement are serialized,
     * and direct-mode candidates are tried in their configured priority order.
     */
    suspend fun preload(placement: RewardedPlacement): AdLoadResult =
        withFullscreenPlacementLock(placement.id) { preloadInternal(placement) }

    /**
     * Creates a traditional Android View container for use by Views or the ads-compose module.
     * The caller owns the returned view's lifecycle.
     */
    fun createBannerSlot(
        context: Context,
        placement: BannerPlacement,
    ): AdsBannerSlot {
        val slot = AdsBannerSlot(context, placement.reservedHeightDp)
        val runtime = currentRuntime()
        if (runtime == null) {
            slot.setError(AdsError(message = "MonetizeKit is not initialized."))
            return slot
        }
        if (runtime.readyNetworks.isEmpty()) {
            slot.setError(AdsError(message = "Ads are unavailable until GDPR consent is resolved."))
            return slot
        }
        val definition = runtime.configuration.placements[placement.id]
        if (definition == null || definition.placement.format != AdFormat.BANNER) {
            slot.setError(AdsError(message = "Banner placement '${placement.id}' is not configured."))
            return slot
        }
        val attempts = definition.candidates.mapNotNull { candidate ->
            runtime.adapters[candidate.network]?.takeIf { AdFormat.BANNER in it.supportedFormats }?.let { adapter ->
                BannerAttempt(adapter = adapter, placement = placement, adUnitId = candidate.adUnitId)
            }
        }
        if (attempts.isEmpty()) {
            slot.setError(AdsError(message = "No initialized network can render '${placement.id}'."))
        } else {
            slot.setCandidates(attempts)
        }
        return slot
    }

    /** Creates an ordered-fallback host for a configured native placement. */
    fun createNativeSlot(
        context: Context,
        placement: NativePlacement,
    ): AdsNativeSlot {
        val slot = AdsNativeSlot(context, placement.reservedHeightDp)
        val runtime = currentRuntime()
        if (runtime == null) {
            slot.setError(AdsError(message = "MonetizeKit is not initialized."))
            return slot
        }
        if (runtime.readyNetworks.isEmpty()) {
            slot.setError(AdsError(message = "Ads are unavailable until GDPR consent is resolved."))
            return slot
        }
        val definition = runtime.configuration.placements[placement.id]
        if (definition == null || definition.placement.format != AdFormat.NATIVE) {
            slot.setError(AdsError(message = "Native placement '${placement.id}' is not configured."))
            return slot
        }
        val attempts = definition.candidates.mapNotNull { candidate ->
            runtime.adapters[candidate.network]?.takeIf { AdFormat.NATIVE in it.supportedFormats }?.let { adapter ->
                NativeAttempt(adapter = adapter, placement = placement, adUnitId = candidate.adUnitId)
            }
        }
        if (attempts.isEmpty()) {
            slot.setError(AdsError(message = "No initialized network can render native placement '${placement.id}'."))
        } else {
            slot.setCandidates(attempts)
        }
        return slot
    }

    private suspend fun initializeAdapters(
        application: Application,
        runtime: AdsRuntime,
        configuredNetworks: Set<AdsNetwork>,
    ) {
        val errors = linkedMapOf<AdsNetwork, AdsError>()
        configuredNetworks.forEach { network ->
            val adapter = runtime.adapters[network]
            if (adapter == null) {
                val error = AdsError(
                    network = network,
                    message = "$network is configured but its library module is not on the classpath.",
                )
                errors[network] = error
                mutableEvents.emit(AdsEvent.NetworkInitializationFailed(network, error))
                return@forEach
            }
            when (
                val result = runCatching {
                    withContext(Dispatchers.Main.immediate) {
                        adapter.initialize(application, runtime.configuration)
                    }
                }.getOrElse { throwable ->
                    NetworkInitializationResult.Failed(
                        AdsError(network = network, message = throwable.message ?: "Initialization failed.", cause = throwable),
                    )
                }
            ) {
                NetworkInitializationResult.Ready -> {
                    runtime.readyNetworks += network
                    mutableEvents.emit(AdsEvent.NetworkInitialized(network))
                }

                is NetworkInitializationResult.Failed -> {
                    errors[network] = result.error
                    mutableEvents.emit(AdsEvent.NetworkInitializationFailed(network, result.error))
                }
            }
        }
        mutableState.value = if (runtime.readyNetworks.isEmpty()) {
            MonetizeKitState.Failed(errors)
        } else {
            MonetizeKitState.Ready(runtime.readyNetworks.toSet(), errors)
        }
    }

    private fun startAdaptersIfAllowed(application: Application, runtime: AdsRuntime) {
        synchronized(lock) {
            if (initializationJob != null) return
            val configuredNetworks = runtime.configuration.configuredNetworks()
            mutableState.value = MonetizeKitState.Initializing(configuredNetworks)
            initializationJob = scope.launch {
                initializeAdapters(application, runtime, configuredNetworks)
            }
        }
    }

    private fun registerFirstActivityConsentFlow(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                private var consumed = false

                override fun onActivityResumed(activity: Activity) {
                    if (consumed) return
                    consumed = true
                    application.unregisterActivityLifecycleCallbacks(this)
                    scope.launch { gatherGdprConsent(activity) }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    private suspend fun showInternal(
        activity: Activity,
        placement: FullscreenPlacement,
        onReward: (AdReward) -> Unit,
    ): AdShowResult = withContext(Dispatchers.Main.immediate) {
        val runtime = currentRuntime()
            ?: return@withContext failShow(placement, AdsError(message = "MonetizeKit is not initialized."))
        val definition = runtime.configuration.placements[placement.id]
            ?: return@withContext failShow(
                placement,
                AdsError(message = "Fullscreen placement '${placement.id}' is not configured."),
            )
        if (definition.placement.format != placement.format) {
            return@withContext failShow(
                placement,
                AdsError(message = "Placement '${placement.id}' has a different ad format."),
            )
        }

        var lastError = AdsError(message = "No configured network could show '${placement.id}'.")
        definition.candidates.forEach { candidate ->
            val adapter = runtime.adapters[candidate.network] ?: return@forEach
            if (candidate.network !in runtime.readyNetworks) return@forEach
            if (placement.format !in adapter.supportedFormats) {
                lastError = AdsError(
                    network = candidate.network,
                    message = "${candidate.network} does not support ${placement.format}.",
                )
                return@forEach
            }
            when (val loadResult = adapter.loadFullscreen(placement, candidate.adUnitId)) {
                AdLoadResult.Loaded -> when (
                    val showResult = adapter.showFullscreen(
                        activity = activity,
                        placement = placement,
                        adUnitId = candidate.adUnitId,
                        onReward = onReward,
                    )
                ) {
                    AdShowResult.Shown -> {
                        mutableEvents.emit(AdsEvent.AdShown(placement, candidate.network))
                        scope.launch {
                            withFullscreenPlacementLock(placement.id) {
                                preloadInternal(placement)
                            }
                        }
                        return@withContext AdShowResult.Shown
                    }

                    is AdShowResult.Failed -> lastError = showResult.error
                }

                is AdLoadResult.Failed -> lastError = loadResult.error
            }
        }
        failShow(placement, lastError)
    }

    private suspend fun preloadInternal(placement: FullscreenPlacement): AdLoadResult =
        withContext(Dispatchers.Main.immediate) {
            val runtime = currentRuntime()
                ?: return@withContext failPreload(
                    placement,
                    AdsError(message = "MonetizeKit is not initialized."),
                )
            val definition = runtime.configuration.placements[placement.id]
                ?: return@withContext failPreload(
                    placement,
                    AdsError(message = "Fullscreen placement '${placement.id}' is not configured."),
                )
            if (definition.placement.format != placement.format) {
                return@withContext failPreload(
                    placement,
                    AdsError(message = "Placement '${placement.id}' has a different ad format."),
                )
            }

            when (
                val result = preloadFirstFullscreenCandidate(
                    placement = placement,
                    candidates = definition.candidates,
                    adapters = runtime.adapters,
                    readyNetworks = runtime.readyNetworks,
                )
            ) {
                is FullscreenPreloadResult.Loaded -> {
                    mutableEvents.emit(AdsEvent.AdPreloaded(placement, result.network))
                    AdLoadResult.Loaded
                }

                is FullscreenPreloadResult.Failed -> failPreload(placement, result.error)
            }
        }

    private suspend fun <T> withFullscreenPlacementLock(
        placementId: PlacementId,
        block: suspend () -> T,
    ): T = fullscreenPlacementMutexes.getOrPut(placementId) { Mutex() }.withLock { block() }

    private fun failPreload(placement: FullscreenPlacement, error: AdsError): AdLoadResult.Failed {
        mutableEvents.tryEmit(AdsEvent.AdPreloadFailed(placement, error))
        return AdLoadResult.Failed(error)
    }

    private fun failShow(placement: AdPlacement, error: AdsError): AdShowResult.Failed {
        mutableEvents.tryEmit(AdsEvent.AdShowFailed(placement, error))
        return AdShowResult.Failed(error)
    }

    private fun currentRuntime(): AdsRuntime? = container?.koin?.get()

    private fun AdsConfiguration.configuredNetworks(): Set<AdsNetwork> = buildSet {
        if (adMob != null) add(AdsNetwork.ADMOB)
        if (meta != null) add(AdsNetwork.META)
        if (unity != null) add(AdsNetwork.UNITY)
    }
}

private class AdsRuntime(
    val configuration: AdsConfiguration,
    val adapters: Map<AdsNetwork, AdsNetworkAdapter>,
    val consentManager: GdprConsentManager,
) {
    val readyNetworks = ConcurrentHashMap.newKeySet<AdsNetwork>()
}
