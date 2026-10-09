package ua.lujek.monetizekit.core

@DslMarker
annotation class MonetizeKitDsl

enum class AdsNetwork {
    ADMOB,
    META,
    UNITY,
}

/**
 * Selects the delivery owner for the whole SDK configuration. Direct SDK calls and AdMob
 * Mediation must never coexist because that would duplicate requests for the same inventory.
 */
enum class AdsDeliveryMode {
    DIRECT,
    ADMOB_MEDIATION,
}

internal enum class AdRequestRoute {
    DIRECT,
    ADMOB_MEDIATION,
}

@ConsistentCopyVisibility
data class AdMobConfiguration internal constructor(
    val applicationId: String,
    val testDeviceIds: Set<String>,
)

@ConsistentCopyVisibility
data class MetaConfiguration internal constructor(
    val testDeviceHashes: Set<String>,
)

@ConsistentCopyVisibility
data class UnityConfiguration internal constructor(
    val gameId: String,
    val isTestMode: Boolean,
)

enum class GdprDebugGeography {
    DISABLED,
    EEA,
    REGULATED_US_STATE,
    OTHER,
}

/** How the Google Mobile Ads SDK should receive a United States privacy signal. */
enum class UsStatePrivacyMode {
    /** The app or its consent provider does not set a US-specific signal through MonetizeKit. */
    NONE,

    /** Enables Google restricted data processing by writing the documented `gad_rdp` signal. */
    RESTRICTED_DATA_PROCESSING,

    /** A UMP or external CMP writes IAB GPP values before ad SDK initialization. */
    GLOBAL_PRIVACY_PLATFORM,
}

@ConsistentCopyVisibility
data class GdprDebugConfiguration internal constructor(
    val deviceHashedIds: Set<String>,
    val geography: GdprDebugGeography,
)

@ConsistentCopyVisibility
data class GdprConfiguration internal constructor(
    val debug: GdprDebugConfiguration?,
    val childDirectedTreatment: Boolean?,
    val underAgeOfConsent: Boolean?,
    val usStatePrivacyMode: UsStatePrivacyMode,
    val externalConsentProvider: PrivacyConsentProvider?,
)

@ConsistentCopyVisibility
data class NetworkPlacement internal constructor(
    val network: AdsNetwork,
    val adUnitId: String,
    internal val route: AdRequestRoute,
)

@ConsistentCopyVisibility
data class PlacementDefinition internal constructor(
    val placement: AdPlacement,
    val candidates: List<NetworkPlacement>,
    val fullscreenPolicy: FullscreenPlacementPolicy?,
)

class AdsConfiguration internal constructor(
    val deliveryMode: AdsDeliveryMode,
    val adMob: AdMobConfiguration?,
    val meta: MetaConfiguration?,
    val unity: UnityConfiguration?,
    val gdpr: GdprConfiguration,
    val adsAccessProvider: AdsAccessProvider,
    internal val placements: Map<PlacementId, PlacementDefinition>,
) {
    internal fun networkConfiguration(network: AdsNetwork): Any? = when (network) {
        AdsNetwork.ADMOB -> adMob
        AdsNetwork.META -> meta
        AdsNetwork.UNITY -> unity
    }
}

fun monetizeConfiguration(block: AdsConfigurationBuilder.() -> Unit): AdsConfiguration =
    AdsConfigurationBuilder().apply(block).build()

@MonetizeKitDsl
class AdsConfigurationBuilder {
    private var deliveryMode: AdsDeliveryMode? = null
    private var adMobConfiguration: AdMobConfiguration? = null
    private var metaConfiguration: MetaConfiguration? = null
    private var unityConfiguration: UnityConfiguration? = null
    private var gdprConfiguration: GdprConfiguration = GdprConfiguration(
        debug = null,
        childDirectedTreatment = null,
        underAgeOfConsent = null,
        usStatePrivacyMode = UsStatePrivacyMode.NONE,
        externalConsentProvider = null,
    )
    private var adsAccessProvider: AdsAccessProvider = AdsAccessProviders.alwaysAllowed
    private var isGdprConfigured = false
    private var isAdsAccessConfigured = false
    private val placementDefinitions = linkedMapOf<PlacementId, PlacementDefinition>()

    /**
     * Uses the networks' native Android SDKs. The placement order is an application-defined
     * fallback order; MonetizeKit arbitrates one winner and never lets late callbacks replace it.
     */
    fun direct(block: DirectNetworksBuilder.() -> Unit) {
        selectMode(AdsDeliveryMode.DIRECT)
        val networks = DirectNetworksBuilder().apply(block).build()
        adMobConfiguration = networks.adMob
        metaConfiguration = networks.meta
        unityConfiguration = networks.unity
    }

    /**
     * Delegates bidding and fallback to the AdMob console. Meta and Unity must be linked there,
     * not configured as direct networks in this application.
     */
    fun admobMediation(block: AdMobConfigurationBuilder.() -> Unit) {
        selectMode(AdsDeliveryMode.ADMOB_MEDIATION)
        adMobConfiguration = AdMobConfigurationBuilder().apply(block).build()
    }

    /**
     * Configures the one UMP consent flow. Add every enabled ad partner (Meta and Unity included)
     * to the published UMP message in AdMob Privacy & messaging.
     */
    fun gdpr(block: GdprConfigurationBuilder.() -> Unit) {
        check(!isGdprConfigured) { "GDPR is configured more than once." }
        isGdprConfigured = true
        gdprConfiguration = GdprConfigurationBuilder().apply(block).build()
    }

    /**
     * Alias for [gdpr]. It configures every privacy option, including COPPA, TFUA, and US states
     * signals; it does not create a second consent dialog.
     */
    fun privacy(block: GdprConfigurationBuilder.() -> Unit) = gdpr(block)

    /**
     * Observes whether ads may be requested. Use this only when an application has an ad-removal
     * entitlement; omit it when billing sells unrelated products or when billing is absent.
     */
    fun adsAccess(provider: AdsAccessProvider) {
        check(!isAdsAccessConfigured) { "Ads access is configured more than once." }
        isAdsAccessConfigured = true
        adsAccessProvider = provider
    }

    fun banner(
        placement: BannerPlacement,
        block: PlacementCandidatesBuilder.() -> Unit,
    ) = addPlacement(placement, fullscreenPolicy = null, block = block)

    fun native(
        placement: NativePlacement,
        block: PlacementCandidatesBuilder.() -> Unit,
    ) = addPlacement(placement, fullscreenPolicy = null, block = block)

    fun interstitial(
        placement: InterstitialPlacement,
        policy: FullscreenPlacementPolicy = FullscreenPlacementPolicy(),
        block: PlacementCandidatesBuilder.() -> Unit,
    ) = addPlacement(placement, fullscreenPolicy = policy, block = block)

    fun rewarded(
        placement: RewardedPlacement,
        policy: FullscreenPlacementPolicy = FullscreenPlacementPolicy(),
        block: PlacementCandidatesBuilder.() -> Unit,
    ) = addPlacement(placement, fullscreenPolicy = policy, block = block)

    private fun addPlacement(
        placement: AdPlacement,
        fullscreenPolicy: FullscreenPlacementPolicy?,
        block: PlacementCandidatesBuilder.() -> Unit,
    ) {
        check(placementDefinitions[placement.id] == null) {
            "Placement id '${placement.id}' is configured more than once."
        }
        placementDefinitions[placement.id] = PlacementDefinition(
            placement = placement,
            candidates = PlacementCandidatesBuilder().apply(block).build(),
            fullscreenPolicy = fullscreenPolicy,
        )
    }

    internal fun build(): AdsConfiguration {
        val selectedMode = requireNotNull(deliveryMode) {
            "Select exactly one delivery mode: direct { ... } or admobMediation { ... }."
        }
        val configuration = AdsConfiguration(
            deliveryMode = selectedMode,
            adMob = adMobConfiguration,
            meta = metaConfiguration,
            unity = unityConfiguration,
            gdpr = gdprConfiguration,
            adsAccessProvider = adsAccessProvider,
            placements = placementDefinitions.toMap(),
        )
        require(configuration.placements.isNotEmpty()) { "Configure at least one ad placement." }
        configuration.placements.values.forEach { definition ->
            definition.candidates.forEach { candidate ->
                require(candidate.route.matches(selectedMode)) {
                    "Placement '${definition.placement.id}' uses ${candidate.route}; " +
                        "the selected delivery mode is $selectedMode."
                }
                requireNotNull(configuration.networkConfiguration(candidate.network)) {
                    "Placement '${definition.placement.id}' references ${candidate.network}, but the network is not configured."
                }
            }
            if (selectedMode == AdsDeliveryMode.ADMOB_MEDIATION) {
                require(definition.candidates.size == 1) {
                    "AdMob Mediation owns fallback and bidding. Configure exactly one ad unit for '${definition.placement.id}'."
                }
            }
        }
        return configuration
    }

    private fun selectMode(mode: AdsDeliveryMode) {
        check(deliveryMode == null) {
            "Choose either direct { ... } or admobMediation { ... }; they cannot be enabled together."
        }
        deliveryMode = mode
    }
}

@MonetizeKitDsl
class DirectNetworksBuilder {
    private var adMobConfiguration: AdMobConfiguration? = null
    private var metaConfiguration: MetaConfiguration? = null
    private var unityConfiguration: UnityConfiguration? = null

    fun admob(block: AdMobConfigurationBuilder.() -> Unit) {
        check(adMobConfiguration == null) { "AdMob is configured more than once." }
        adMobConfiguration = AdMobConfigurationBuilder().apply(block).build()
    }

    fun meta(block: MetaConfigurationBuilder.() -> Unit = {}) {
        check(metaConfiguration == null) { "Meta is configured more than once." }
        metaConfiguration = MetaConfigurationBuilder().apply(block).build()
    }

    fun unity(block: UnityConfigurationBuilder.() -> Unit) {
        check(unityConfiguration == null) { "Unity is configured more than once." }
        unityConfiguration = UnityConfigurationBuilder().apply(block).build()
    }

    internal fun build(): DirectNetworks = DirectNetworks(
        adMob = adMobConfiguration,
        meta = metaConfiguration,
        unity = unityConfiguration,
    ).also { networks ->
        require(networks.configured.isNotEmpty()) { "Configure at least one direct ad network." }
    }
}

internal data class DirectNetworks(
    val adMob: AdMobConfiguration?,
    val meta: MetaConfiguration?,
    val unity: UnityConfiguration?,
) {
    val configured: List<Any> = listOfNotNull(adMob, meta, unity)
}

@MonetizeKitDsl
class AdMobConfigurationBuilder {
    var applicationId: String? = null
    private val mutableTestDeviceIds = linkedSetOf<String>()

    /** Safely enables Google Mobile Ads test devices only for a debug build. */
    fun testing(
        enabled: Boolean,
        block: AdMobTestingBuilder.() -> Unit,
    ) {
        if (!enabled) return
        mutableTestDeviceIds += AdMobTestingBuilder().apply(block).build()
    }

    internal fun build(): AdMobConfiguration = AdMobConfiguration(
        applicationId = requireNotNull(applicationId) {
            "AdMob application id must be provided."
        }.requireValue("AdMob application id"),
        testDeviceIds = mutableTestDeviceIds.toSet(),
    )
}

@MonetizeKitDsl
class AdMobTestingBuilder {
    private val mutableDeviceIds = linkedSetOf<String>()

    fun testDevice(vararg deviceIds: String) {
        mutableDeviceIds += deviceIds.map { it.requireValue("AdMob test device id") }
    }

    internal fun build(): Set<String> = mutableDeviceIds.toSet()
}

@MonetizeKitDsl
class MetaConfigurationBuilder {
    private val mutableTestDeviceHashes = linkedSetOf<String>()

    /** Safely enables Meta test devices only for a debug build. */
    fun testing(
        enabled: Boolean,
        block: MetaTestingBuilder.() -> Unit,
    ) {
        if (!enabled) return
        mutableTestDeviceHashes += MetaTestingBuilder().apply(block).build()
    }

    internal fun build(): MetaConfiguration = MetaConfiguration(
        testDeviceHashes = mutableTestDeviceHashes.toSet(),
    )
}

@MonetizeKitDsl
class MetaTestingBuilder {
    private val mutableDeviceHashes = linkedSetOf<String>()

    fun testDevice(vararg deviceHashes: String) {
        mutableDeviceHashes += deviceHashes.map { it.requireValue("Meta test device hash") }
    }

    internal fun build(): Set<String> = mutableDeviceHashes.toSet()
}

@MonetizeKitDsl
class UnityConfigurationBuilder {
    var gameId: String? = null
    private var isTestMode: Boolean = false

    /** Pass `BuildConfig.DEBUG`; Unity test mode is then structurally disabled in release builds. */
    fun testing(enabled: Boolean) {
        isTestMode = enabled
    }

    internal fun build(): UnityConfiguration = UnityConfiguration(
        gameId = requireNotNull(gameId) {
            "Unity Game ID must be provided."
        }.requireValue("Unity Game ID"),
        isTestMode = isTestMode,
    )
}

@MonetizeKitDsl
class GdprConfigurationBuilder {
    private var debugConfiguration: GdprDebugConfiguration? = null
    private var childDirectedTreatment: Boolean? = null
    private var underAgeOfConsent: Boolean? = null
    private var usStatePrivacyMode: UsStatePrivacyMode = UsStatePrivacyMode.NONE
    private var externalConsentProvider: PrivacyConsentProvider? = null
    private var isAgeTreatmentConfigured = false
    private var isUsStatePrivacyConfigured = false
    private var isExternalConsentConfigured = false

    /**
     * Registers UMP debug parameters only when [enabled] is true. The UMP device hash is printed
     * by the UMP SDK after the first consent-info request and is not an AdMob or Meta test ID.
     */
    fun debug(
        enabled: Boolean,
        block: GdprDebugSettingsBuilder.() -> Unit,
    ) {
        if (!enabled) return
        check(debugConfiguration == null) { "GDPR debug settings are configured more than once." }
        debugConfiguration = GdprDebugSettingsBuilder().apply(block).build()
    }

    /**
     * Sets the COPPA and TFUA flags for Google Mobile Ads. Leave a value null when the app must
     * not make a declaration. TFUA is also passed to UMP so child users are not asked for consent.
     */
    fun ageRestrictedTreatment(
        childDirected: Boolean? = null,
        underAgeOfConsent: Boolean? = null,
    ) {
        check(!isAgeTreatmentConfigured) { "Age-restricted treatment is configured more than once." }
        require(childDirected != null || underAgeOfConsent != null) {
            "Provide childDirected and/or underAgeOfConsent when configuring age-restricted treatment."
        }
        isAgeTreatmentConfigured = true
        childDirectedTreatment = childDirected
        this.underAgeOfConsent = underAgeOfConsent
    }

    /**
     * Selects the US privacy transport. For [UsStatePrivacyMode.GLOBAL_PRIVACY_PLATFORM], UMP or
     * the supplied external provider must write GPP consent values before ads initialize.
     */
    fun usStatePrivacy(mode: UsStatePrivacyMode) {
        check(!isUsStatePrivacyConfigured) { "US state privacy is configured more than once." }
        isUsStatePrivacyConfigured = true
        usStatePrivacyMode = mode
    }

    /**
     * Replaces UMP's form with one application-owned certified CMP. The provider must persist any
     * TCF/GPP values required by enabled direct networks before returning a requestable snapshot.
     */
    fun externalConsent(provider: PrivacyConsentProvider) {
        check(!isExternalConsentConfigured) { "An external consent provider is configured more than once." }
        isExternalConsentConfigured = true
        externalConsentProvider = provider
    }

    internal fun build(): GdprConfiguration = GdprConfiguration(
        debug = debugConfiguration,
        childDirectedTreatment = childDirectedTreatment,
        underAgeOfConsent = underAgeOfConsent,
        usStatePrivacyMode = usStatePrivacyMode,
        externalConsentProvider = externalConsentProvider,
    )
}

@MonetizeKitDsl
class GdprDebugSettingsBuilder {
    var geography: GdprDebugGeography = GdprDebugGeography.DISABLED
    private val mutableDeviceHashedIds = linkedSetOf<String>()

    fun testDeviceHashedId(vararg deviceHashedIds: String) {
        mutableDeviceHashedIds += deviceHashedIds.map { it.requireValue("UMP test device hashed id") }
    }

    internal fun build(): GdprDebugConfiguration {
        require(mutableDeviceHashedIds.isNotEmpty()) {
            "Provide at least one UMP test device hashed id when GDPR debug settings are enabled."
        }
        return GdprDebugConfiguration(
            deviceHashedIds = mutableDeviceHashedIds.toSet(),
            geography = geography,
        )
    }
}

@MonetizeKitDsl
class PlacementCandidatesBuilder {
    private val candidates = mutableListOf<NetworkPlacement>()

    /** Adds a direct Google Mobile Ads request to this placement's fallback chain. */
    fun admob(adUnitId: String) = add(AdsNetwork.ADMOB, adUnitId, AdRequestRoute.DIRECT)

    /** Adds a direct Meta Audience Network request to this placement's fallback chain. */
    fun meta(placementId: String) = add(AdsNetwork.META, placementId, AdRequestRoute.DIRECT)

    /** Adds a direct Unity Ads request to this placement's fallback chain. */
    fun unity(placementId: String) = add(AdsNetwork.UNITY, placementId, AdRequestRoute.DIRECT)

    /** Lets Google Mobile Ads SDK select AdMob and all mediated networks for this ad unit. */
    fun admobMediation(adUnitId: String) = add(AdsNetwork.ADMOB, adUnitId, AdRequestRoute.ADMOB_MEDIATION)

    private fun add(network: AdsNetwork, adUnitId: String, route: AdRequestRoute) {
        check(candidates.none { it.network == network }) {
            "$network is configured more than once in the same placement."
        }
        candidates += NetworkPlacement(
            network = network,
            adUnitId = adUnitId.requireValue("$network ad unit id"),
            route = route,
        )
    }

    internal fun build(): List<NetworkPlacement> {
        require(candidates.isNotEmpty()) { "Every placement needs at least one network ad unit id." }
        return candidates.toList()
    }
}

private fun AdRequestRoute.matches(mode: AdsDeliveryMode): Boolean = when (mode) {
    AdsDeliveryMode.DIRECT -> this == AdRequestRoute.DIRECT
    AdsDeliveryMode.ADMOB_MEDIATION -> this == AdRequestRoute.ADMOB_MEDIATION
}

private fun String.requireValue(label: String): String {
    require(isNotBlank()) { "$label must not be blank." }
    require(this == trim()) { "$label must not contain leading or trailing whitespace." }
    return this
}
