package ua.lujek.monetizekit.core

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
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
import java.lang.ref.WeakReference
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

    data object AdsSuppressed : MonetizeKitState

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
    data class AdPreloadRetryScheduled(
        val placement: FullscreenPlacement,
        val nextAttempt: Int,
        val delayMillis: Long,
        val error: AdsError,
    ) : AdsEvent
    data class AdShown(val placement: AdPlacement, val network: AdsNetwork) : AdsEvent
    data class AdShowFailed(val placement: AdPlacement, val error: AdsError) : AdsEvent
    data class AdShowCapped(
        val placement: FullscreenPlacement,
        val nextAllowedAtEpochMillis: Long,
    ) : AdsEvent
    data class AdsAccessChanged(val access: AdsAccess) : AdsEvent
    data class AdLoaded(
        val placement: AdPlacement,
        val network: AdsNetwork,
        val diagnostics: AdDiagnostics? = null,
    ) : AdsEvent

    data class AdLoadFailed(val placement: AdPlacement, val error: AdsError) : AdsEvent
    data class AdImpression(val placement: AdPlacement, val network: AdsNetwork) : AdsEvent
    data class AdClicked(val placement: AdPlacement, val network: AdsNetwork) : AdsEvent
    data class AdRewardEarned(
        val placement: RewardedFullscreenPlacement,
        val network: AdsNetwork,
        val reward: AdReward,
    ) : AdsEvent

    data class AdPaid(
        val placement: AdPlacement,
        val network: AdsNetwork,
        val revenue: AdRevenue,
    ) : AdsEvent
}

/**
 * Process-wide ad facade. Configure it once from [Application.onCreate].
 * Network modules self-register before Application.onCreate through a manifest provider.
 */
object MonetizeKit {
    private val lock = Any()
    private val adapterFactories = ConcurrentHashMap<AdsNetwork, AdsNetworkAdapterFactory>()
    private val mediationPartnerConfigurators = ConcurrentHashMap<AdsNetwork, AdMobMediationPartnerConfigurator>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow<MonetizeKitState>(MonetizeKitState.Uninitialized)
    private val mutableEvents = MutableSharedFlow<AdsEvent>(extraBufferCapacity = 32)
    private val mutableGdprConsent = MutableStateFlow<GdprConsentState>(GdprConsentState.NotRequested)
    private val mutableAdsAccess = MutableStateFlow<AdsAccess>(AdsAccess.Allowed)
    private val consentMutex = Mutex()
    private val fullscreenOperations = FullscreenOperationLocks()

    private var container: KoinApplication? = null
    private var initializationJob: Job? = null
    private var lastResumedActivity: WeakReference<Activity>? = null
    private var consentLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private var appOpenActivityLifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null
    private var appOpenLifecycleObserver: DefaultLifecycleObserver? = null
    private var automaticConsentRequestStarted = false
    private var hasEnteredProcessForeground = false
    private var automaticAppOpenPending = false
    private var automaticAppOpenShowRunning = false
    private val adapterEventReporter = AdsEventReporter { event -> mutableEvents.tryEmit(event) }

    val state: StateFlow<MonetizeKitState> = mutableState.asStateFlow()
    val events: SharedFlow<AdsEvent> = mutableEvents.asSharedFlow()
    val gdprConsent: StateFlow<GdprConsentState> = mutableGdprConsent.asStateFlow()
    val adsAccess: StateFlow<AdsAccess> = mutableAdsAccess.asStateFlow()

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

    /** Called by optional mediation artifacts before [initialize]. */
    @JvmStatic
    fun registerAdMobMediationPartnerConfigurator(configurator: AdMobMediationPartnerConfigurator) {
        synchronized(lock) {
            check(mutableState.value is MonetizeKitState.Uninitialized) {
                "AdMob mediation partners must be registered before MonetizeKit.initialize()."
            }
            check(mediationPartnerConfigurators.putIfAbsent(configurator.network, configurator) == null) {
                "An AdMob mediation partner configurator for ${configurator.network} is already registered."
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
                application = application,
                configuration = configuration,
                adapters = adapters,
                consentManager = GdprConsentManager(application, configuration.gdpr),
            )

            mutableAdsAccess.value = configuration.adsAccessProvider.access.value

            container = koinApplication {
                modules(
                    module {
                        single { runtime }
                        single { configuration }
                    },
                )
            }
            mutableState.value = if (isAdsAllowed()) {
                MonetizeKitState.AwaitingGdprConsent(configuredNetworks)
            } else {
                MonetizeKitState.AdsSuppressed
            }
            observeAdsAccess(runtime)
            registerFirstActivityConsentFlow(application)
            registerAppOpenLifecycle(application, configuration)
        }
    }

    /**
     * Requests consent information and presents a UMP form when required. Normally this runs
     * automatically for the first resumed activity; call it explicitly to control launch timing.
     */
    suspend fun gatherGdprConsent(activity: Activity): ConsentSnapshot = consentMutex.withLock {
        if (!isAdsAllowed()) {
            mutableState.value = MonetizeKitState.AdsSuppressed
            return@withLock suppressedConsentSnapshot()
        }
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
        if (!isAdsAllowed()) return@withLock suppressedConsentSnapshot()
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

    /**
     * Clears UMP's persisted consent state for development only. Call this in a debug-only path
     * and restart the process before requesting ads again; it does not reconfigure live adapters.
     */
    @JvmStatic
    fun resetUmpConsentForTesting() {
        val runtime = currentRuntime() ?: return
        runtime.consentManager.resetUmpConsentForTesting()
        mutableGdprConsent.value = GdprConsentState.NotRequested
    }

    suspend fun show(
        activity: Activity,
        placement: InterstitialPlacement,
    ): AdShowResult = fullscreenOperations.show(placement.id) {
            showInternal(activity, placement) {}
    }

    suspend fun show(
        activity: Activity,
        placement: RewardedPlacement,
        onReward: (AdReward) -> Unit,
    ): AdShowResult = fullscreenOperations.show(placement.id) {
            showInternal(activity, placement, onReward)
    }

    suspend fun show(
        activity: Activity,
        placement: RewardedInterstitialPlacement,
        onReward: (AdReward) -> Unit,
    ): AdShowResult = fullscreenOperations.show(placement.id) {
        showInternal(activity, placement, onReward)
    }

    /** Shows an App Open creative explicitly. Automatic foreground display is configured by appOpen(). */
    suspend fun show(
        activity: Activity,
        placement: AppOpenPlacement,
    ): AdShowResult = fullscreenOperations.show(placement.id) {
        showInternal(activity, placement) {}
    }

    /**
     * Prepares an interstitial without showing it. Load attempts for the same placement are
     * serialized, and direct-mode candidates are tried in their configured priority order.
     */
    suspend fun preload(placement: InterstitialPlacement): AdLoadResult =
        preloadWithRetryInternal(placement)

    /**
     * Prepares a rewarded ad without showing it. Load attempts for the same placement are
     * serialized, and direct-mode candidates are tried in their configured priority order.
     */
    suspend fun preload(placement: RewardedPlacement): AdLoadResult =
        preloadWithRetryInternal(placement)

    /** Prepares a rewarded interstitial without showing it. */
    suspend fun preload(placement: RewardedInterstitialPlacement): AdLoadResult =
        preloadWithRetryInternal(placement)

    /** Prepares an App Open creative without showing it. */
    suspend fun preload(placement: AppOpenPlacement): AdLoadResult =
        preloadWithRetryInternal(placement)

    /**
     * Creates a traditional Android View container for use by Views or the ads-compose module.
     * The caller owns the returned view's lifecycle.
     */
    fun createBannerSlot(
        context: Context,
        placement: BannerPlacement,
    ): AdsBannerSlot {
        val runtime = currentRuntime()
        val slot = AdsBannerSlot(
            context = context,
            reservedHeightDp = placement.reservedHeightDp,
            appearance = runtime?.configuration?.bannerAppearance ?: BannerAppearance(),
        )
        if (runtime == null) {
            slot.setError(AdsError(message = "MonetizeKit is not initialized."))
            return slot
        }
        val definition = runtime.configuration.placements[placement.id]
        if (definition == null || definition.placement.format != AdFormat.BANNER) {
            slot.setError(AdsError(message = "Banner placement '${placement.id}' is not configured."))
            return slot
        }
        slot.setCandidateProvider {
            definition.candidates.mapNotNull { candidate ->
                runtime.adapters[candidate.network]
                    ?.takeIf { candidate.network in runtime.readyNetworks }
                    ?.takeIf { AdFormat.BANNER in it.supportedFormats }
                    ?.let { adapter ->
                        BannerAttempt(adapter = adapter, placement = placement, adUnitId = candidate.adUnitId)
                    }
            }
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
        val definition = runtime.configuration.placements[placement.id]
        if (definition == null || definition.placement.format != AdFormat.NATIVE) {
            slot.setError(AdsError(message = "Native placement '${placement.id}' is not configured."))
            return slot
        }
        slot.setCandidateProvider {
            definition.candidates.mapNotNull { candidate ->
                runtime.adapters[candidate.network]
                    ?.takeIf { candidate.network in runtime.readyNetworks }
                    ?.takeIf { AdFormat.NATIVE in it.supportedFormats }
                    ?.let { adapter ->
                        NativeAttempt(adapter = adapter, placement = placement, adUnitId = candidate.adUnitId)
                    }
            }
        }
        return slot
    }

    private suspend fun initializeAdapters(
        application: Application,
        runtime: AdsRuntime,
        configuredNetworks: Set<AdsNetwork>,
    ) {
        val errors = linkedMapOf<AdsNetwork, AdsError>()
        configureAdMobMediationPartners(runtime.configuration)?.let { error ->
            errors[AdsNetwork.ADMOB] = error
            mutableEvents.emit(AdsEvent.NetworkInitializationFailed(AdsNetwork.ADMOB, error))
            mutableState.value = MonetizeKitState.Failed(errors)
            return
        }
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
        if (!isAdsAllowed()) {
            runtime.adapters.values.forEach(AdsNetworkAdapter::clearFullscreenCache)
            mutableState.value = MonetizeKitState.AdsSuppressed
        } else if (runtime.readyNetworks.isEmpty()) {
            mutableState.value = MonetizeKitState.Failed(errors)
        } else {
            mutableState.value = MonetizeKitState.Ready(runtime.readyNetworks.toSet(), errors)
            preloadConfiguredAppOpenPlacements(runtime)
            maybeShowAutomaticAppOpen()
        }
    }

    private fun configureAdMobMediationPartners(configuration: AdsConfiguration): AdsError? {
        if (configuration.deliveryMode != AdsDeliveryMode.ADMOB_MEDIATION) return null

        val requiredPartners = buildSet {
            if (configuration.adMob?.metaMediationTestDeviceHashes.orEmpty().isNotEmpty()) {
                add(AdsNetwork.META)
            }
        }
        val missingPartners = requiredPartners - mediationPartnerConfigurators.keys
        if (missingPartners.isNotEmpty()) {
            return AdsError(
                network = AdsNetwork.ADMOB,
                message = "Mediation test settings for $missingPartners require the matching mediation artifact on the classpath.",
            )
        }
        return runCatching {
            requiredPartners.forEach { network ->
                requireNotNull(mediationPartnerConfigurators[network])
                    .configureBeforeAdMobInitialization(configuration)
            }
        }.exceptionOrNull()?.let { throwable ->
            AdsError(
                network = AdsNetwork.ADMOB,
                message = throwable.message ?: "Could not configure an AdMob mediation partner.",
                cause = throwable,
            )
        }
    }

    private fun startAdaptersIfAllowed(application: Application, runtime: AdsRuntime) {
        if (!isAdsAllowed()) return
        synchronized(lock) {
            if (initializationJob?.isActive == true) return
            if (runtime.readyNetworks.isNotEmpty()) {
                mutableState.value = MonetizeKitState.Ready(
                    availableNetworks = runtime.readyNetworks.toSet(),
                    unavailableNetworks = emptyMap(),
                )
                preloadConfiguredAppOpenPlacements(runtime)
                maybeShowAutomaticAppOpen()
                return
            }
            val configuredNetworks = runtime.configuration.configuredNetworks()
            mutableState.value = MonetizeKitState.Initializing(configuredNetworks)
            initializationJob = scope.launch {
                initializeAdapters(application, runtime, configuredNetworks)
            }
        }
    }

    private fun registerFirstActivityConsentFlow(application: Application) {
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                lastResumedActivity = WeakReference(activity)
                startAutomaticConsentFlow(activity)
                maybeShowAutomaticAppOpen()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        consentLifecycleCallbacks = callbacks
        application.registerActivityLifecycleCallbacks(callbacks)
    }

    private fun registerAppOpenLifecycle(
        application: Application,
        configuration: AdsConfiguration,
    ) {
        if (configuration.automaticAppOpenPlacement() == null) return
        val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                lastResumedActivity = WeakReference(activity)
                maybeShowAutomaticAppOpen()
            }

            override fun onActivityPaused(activity: Activity) {
                if (lastResumedActivity?.get() === activity) lastResumedActivity = null
            }

            override fun onActivityDestroyed(activity: Activity) {
                if (lastResumedActivity?.get() === activity) lastResumedActivity = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        }
        appOpenActivityLifecycleCallbacks = activityCallbacks
        application.registerActivityLifecycleCallbacks(activityCallbacks)
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                val isFirstProcessForeground = !hasEnteredProcessForeground
                hasEnteredProcessForeground = true
                val policy = configuration.automaticAppOpenPlacement()?.appOpenPolicy ?: return
                if (!isFirstProcessForeground || policy.showOnColdStart) {
                    automaticAppOpenPending = true
                    maybeShowAutomaticAppOpen()
                }
            }
        }
        appOpenLifecycleObserver = observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
    }

    private fun preloadConfiguredAppOpenPlacements(runtime: AdsRuntime) {
        runtime.configuration.placements.values
            .mapNotNull { definition -> definition.placement as? AppOpenPlacement }
            .forEach { placement ->
                scope.launch { preloadWithRetryInternal(placement) }
            }
    }

    private fun maybeShowAutomaticAppOpen() {
        if (!automaticAppOpenPending || automaticAppOpenShowRunning || !isAdsAllowed()) return
        val runtime = currentRuntime() ?: return
        if (runtime.readyNetworks.isEmpty()) return
        val placement = runtime.configuration.automaticAppOpenPlacement()?.placement as? AppOpenPlacement ?: return
        val activity = lastResumedActivity?.get() ?: return
        automaticAppOpenPending = false
        automaticAppOpenShowRunning = true
        scope.launch {
            try {
                show(activity, placement)
            } finally {
                automaticAppOpenShowRunning = false
            }
        }
    }

    private suspend fun showInternal(
        activity: Activity,
        placement: FullscreenPlacement,
        onReward: (AdReward) -> Unit,
    ): AdShowResult = withContext(Dispatchers.Main.immediate) {
        if (!isAdsAllowed()) return@withContext AdShowResult.Suppressed
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

        definition.fullscreenPolicy?.frequencyCap?.let { frequencyCap ->
            val decision = try {
                frequencyCap.evaluate(
                    shownAtEpochMillis = runtime.frequencyCapStore.shownAt(placement.id),
                    nowEpochMillis = System.currentTimeMillis(),
                )
            } catch (_: Throwable) {
                // A local storage failure must never turn an already available ad into a failed show.
                FrequencyCapDecision.Allowed
            }
            if (decision is FrequencyCapDecision.Capped) {
                mutableEvents.emit(AdsEvent.AdShowCapped(placement, decision.nextAllowedAtEpochMillis))
                return@withContext AdShowResult.Capped(decision.nextAllowedAtEpochMillis)
            }
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
            when (
                val loadResult = adapter.loadFullscreen(
                    placement = placement,
                    adUnitId = candidate.adUnitId,
                    eventReporter = adapterEventReporter,
                )
            ) {
                AdLoadResult.Loaded -> when (
                    val showResult = adapter.showFullscreen(
                        activity = activity,
                        placement = placement,
                        adUnitId = candidate.adUnitId,
                        onReward = onReward,
                        eventReporter = adapterEventReporter,
                    )
                ) {
                    AdShowResult.Shown -> {
                        definition.fullscreenPolicy?.frequencyCap?.let { frequencyCap ->
                            try {
                                runtime.frequencyCapStore.recordShow(
                                    placementId = placement.id,
                                    frequencyCap = frequencyCap,
                                    shownAtEpochMillis = System.currentTimeMillis(),
                                )
                            } catch (_: Throwable) {
                                // Showing an ad succeeded; storage only controls future local caps.
                            }
                        }
                        mutableEvents.emit(AdsEvent.AdShown(placement, candidate.network))
                        scope.launch {
                            preloadWithRetryInternal(placement)
                        }
                        return@withContext AdShowResult.Shown
                    }

                    is AdShowResult.Failed -> lastError = showResult.error
                    AdShowResult.Suppressed -> return@withContext AdShowResult.Suppressed
                    is AdShowResult.Capped -> return@withContext showResult
                }

                is AdLoadResult.Failed -> lastError = loadResult.error
                AdLoadResult.Suppressed -> return@withContext AdShowResult.Suppressed
            }
        }
        failShow(placement, lastError)
    }

    private suspend fun preloadWithRetryInternal(placement: FullscreenPlacement): AdLoadResult {
        return when (
            val result = retryFullscreenPreload(
                loadAttempt = {
                    fullscreenOperations.forPlacement(placement.id) {
                        preloadOnceInternal(placement)
                    }
                },
                onRetryScheduled = { error, nextAttempt, delayMillis ->
                    mutableEvents.emit(
                        AdsEvent.AdPreloadRetryScheduled(
                            placement = placement,
                            nextAttempt = nextAttempt,
                            delayMillis = delayMillis,
                            error = error,
                        ),
                    )
                },
            )
        ) {
            is FullscreenPreloadAttempt.Loaded -> {
                mutableEvents.emit(AdsEvent.AdPreloaded(placement, result.network))
                AdLoadResult.Loaded
            }

            FullscreenPreloadAttempt.Suppressed -> AdLoadResult.Suppressed

            is FullscreenPreloadAttempt.Failed -> failPreload(placement, result.error)
        }
    }

    private suspend fun preloadOnceInternal(placement: FullscreenPlacement): FullscreenPreloadAttempt =
        withContext(Dispatchers.Main.immediate) {
            if (!isAdsAllowed()) return@withContext FullscreenPreloadAttempt.Suppressed
            val runtime = currentRuntime()
                ?: return@withContext FullscreenPreloadAttempt.Failed(
                    error = AdsError(message = "MonetizeKit is not initialized."),
                    isRetryable = false,
                    retryPolicy = FullscreenRetryPolicy(),
                )
            val definition = runtime.configuration.placements[placement.id]
                ?: return@withContext FullscreenPreloadAttempt.Failed(
                    error = AdsError(message = "Fullscreen placement '${placement.id}' is not configured."),
                    isRetryable = false,
                    retryPolicy = FullscreenRetryPolicy(),
                )
            if (definition.placement.format != placement.format) {
                return@withContext FullscreenPreloadAttempt.Failed(
                    error = AdsError(message = "Placement '${placement.id}' has a different ad format."),
                    isRetryable = false,
                    retryPolicy = FullscreenRetryPolicy(),
                )
            }

            when (
                val result = preloadFirstFullscreenCandidate(
                    placement = placement,
                    candidates = definition.candidates,
                    adapters = runtime.adapters,
                    readyNetworks = runtime.readyNetworks,
                    eventReporter = adapterEventReporter,
                )
            ) {
                is FullscreenPreloadResult.Loaded -> {
                    FullscreenPreloadAttempt.Loaded(result.network)
                }

                is FullscreenPreloadResult.Failed -> FullscreenPreloadAttempt.Failed(
                    error = result.error,
                    isRetryable = result.isRetryable,
                    retryPolicy = requireNotNull(definition.fullscreenPolicy).retryPolicy,
                )
            }
        }

    private fun failPreload(placement: FullscreenPlacement, error: AdsError): AdLoadResult.Failed {
        mutableEvents.tryEmit(AdsEvent.AdPreloadFailed(placement, error))
        return AdLoadResult.Failed(error)
    }

    private fun failShow(placement: AdPlacement, error: AdsError): AdShowResult.Failed {
        mutableEvents.tryEmit(AdsEvent.AdShowFailed(placement, error))
        return AdShowResult.Failed(error)
    }

    private fun currentRuntime(): AdsRuntime? = container?.koin?.get()

    internal fun adapterEventReporter(): AdsEventReporter = adapterEventReporter

    private fun observeAdsAccess(runtime: AdsRuntime) {
        scope.launch {
            runtime.configuration.adsAccessProvider.access.collect { access ->
                mutableAdsAccess.value = access
                mutableEvents.emit(AdsEvent.AdsAccessChanged(access))
                if (access == AdsAccess.Suppressed) {
                    runtime.adapters.values.forEach(AdsNetworkAdapter::clearFullscreenCache)
                    automaticAppOpenPending = false
                    mutableState.value = MonetizeKitState.AdsSuppressed
                } else {
                    lastResumedActivity?.get()?.let(::startAutomaticConsentFlow)
                }
            }
        }
    }

    private fun startAutomaticConsentFlow(activity: Activity) {
        if (!isAdsAllowed()) return
        val callbacks = synchronized(lock) {
            if (automaticConsentRequestStarted) return
            automaticConsentRequestStarted = true
            consentLifecycleCallbacks.also { consentLifecycleCallbacks = null }
        }
        callbacks?.let(activity.application::unregisterActivityLifecycleCallbacks)
        scope.launch { gatherGdprConsent(activity) }
    }

    private fun isAdsAllowed(): Boolean = mutableAdsAccess.value == AdsAccess.Allowed

    private fun suppressedConsentSnapshot(): ConsentSnapshot = ConsentSnapshot(
        canRequestAds = false,
        isPrivacyOptionsRequired = false,
        error = AdsError(message = "Ads are disabled by the application's access policy."),
    )

    private fun AdsConfiguration.configuredNetworks(): Set<AdsNetwork> = buildSet {
        if (adMob != null) add(AdsNetwork.ADMOB)
        if (meta != null) add(AdsNetwork.META)
        if (unity != null) add(AdsNetwork.UNITY)
    }

    private fun AdsConfiguration.automaticAppOpenPlacement(): PlacementDefinition? =
        placements.values.singleOrNull { definition ->
            definition.placement is AppOpenPlacement && definition.appOpenPolicy?.showOnAppForeground == true
        }
}

private class AdsRuntime(
    application: Application,
    val configuration: AdsConfiguration,
    val adapters: Map<AdsNetwork, AdsNetworkAdapter>,
    val consentManager: GdprConsentManager,
) {
    val readyNetworks = ConcurrentHashMap.newKeySet<AdsNetwork>()
    val frequencyCapStore = FullscreenFrequencyCapStore(application)
}
