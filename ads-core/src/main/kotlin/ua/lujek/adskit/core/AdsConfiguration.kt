package ua.lujek.monetizekit.core

@DslMarker
annotation class MonetizeKitDsl

enum class AdsNetwork {
    ADMOB,
    META,
    UNITY,
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

@ConsistentCopyVisibility
data class NetworkPlacement internal constructor(
    val network: AdsNetwork,
    val adUnitId: String,
)

@ConsistentCopyVisibility
data class PlacementDefinition internal constructor(
    val placement: AdPlacement,
    val waterfall: List<NetworkPlacement>,
)

class AdsConfiguration internal constructor(
    val adMob: AdMobConfiguration?,
    val meta: MetaConfiguration?,
    val unity: UnityConfiguration?,
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
    private var adMobConfiguration: AdMobConfiguration? = null
    private var metaConfiguration: MetaConfiguration? = null
    private var unityConfiguration: UnityConfiguration? = null
    private val placementDefinitions = linkedMapOf<PlacementId, PlacementDefinition>()

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

    fun banner(
        placement: BannerPlacement,
        block: PlacementWaterfallBuilder.() -> Unit,
    ) = addPlacement(placement, block)

    fun native(
        placement: NativePlacement,
        block: PlacementWaterfallBuilder.() -> Unit,
    ) = addPlacement(placement, block)

    fun interstitial(
        placement: InterstitialPlacement,
        block: PlacementWaterfallBuilder.() -> Unit,
    ) = addPlacement(placement, block)

    fun rewarded(
        placement: RewardedPlacement,
        block: PlacementWaterfallBuilder.() -> Unit,
    ) = addPlacement(placement, block)

    private fun addPlacement(
        placement: AdPlacement,
        block: PlacementWaterfallBuilder.() -> Unit,
    ) {
        check(placementDefinitions[placement.id] == null) {
            "Placement id '${placement.id}' is configured more than once."
        }
        placementDefinitions[placement.id] = PlacementDefinition(
            placement = placement,
            waterfall = PlacementWaterfallBuilder().apply(block).build(),
        )
    }

    internal fun build(): AdsConfiguration {
        val configuration = AdsConfiguration(
            adMob = adMobConfiguration,
            meta = metaConfiguration,
            unity = unityConfiguration,
            placements = placementDefinitions.toMap(),
        )
        require(configuration.placements.isNotEmpty()) { "Configure at least one ad placement." }
        // A configuration deliberately contains only the placements that this app uses.
        // For example, a banner-only app never needs to declare interstitial or rewarded placements.
        configuration.placements.values.forEach { definition ->
            definition.waterfall.forEach { candidate ->
                requireNotNull(configuration.networkConfiguration(candidate.network)) {
                    "Placement '${definition.placement.id}' references ${candidate.network}, but the network is not configured."
                }
            }
        }
        return configuration
    }
}

@MonetizeKitDsl
class AdMobConfigurationBuilder {
    lateinit var applicationId: String
    private val mutableTestDeviceIds = linkedSetOf<String>()

    fun testDevice(vararg deviceIds: String) {
        mutableTestDeviceIds += deviceIds.map { it.requireValue("AdMob test device id") }
    }

    internal fun build(): AdMobConfiguration = AdMobConfiguration(
        applicationId = applicationId.requireValue("AdMob application id"),
        testDeviceIds = mutableTestDeviceIds.toSet(),
    )
}

@MonetizeKitDsl
class MetaConfigurationBuilder {
    private val mutableTestDeviceHashes = linkedSetOf<String>()

    fun testDevice(vararg deviceHashes: String) {
        mutableTestDeviceHashes += deviceHashes.map { it.requireValue("Meta test device hash") }
    }

    internal fun build(): MetaConfiguration = MetaConfiguration(
        testDeviceHashes = mutableTestDeviceHashes.toSet(),
    )
}

@MonetizeKitDsl
class UnityConfigurationBuilder {
    lateinit var gameId: String
    var isTestMode: Boolean = false

    internal fun build(): UnityConfiguration = UnityConfiguration(
        gameId = gameId.requireValue("Unity Game ID"),
        isTestMode = isTestMode,
    )
}

@MonetizeKitDsl
class PlacementWaterfallBuilder {
    private val candidates = mutableListOf<NetworkPlacement>()

    fun admob(adUnitId: String) = add(AdsNetwork.ADMOB, adUnitId)

    fun meta(adUnitId: String) = add(AdsNetwork.META, adUnitId)

    fun unity(adUnitId: String) = add(AdsNetwork.UNITY, adUnitId)

    private fun add(network: AdsNetwork, adUnitId: String) {
        check(candidates.none { it.network == network }) {
            "$network is configured more than once in the same waterfall."
        }
        candidates += NetworkPlacement(network, adUnitId.requireValue("$network ad unit id"))
    }

    internal fun build(): List<NetworkPlacement> {
        require(candidates.isNotEmpty()) { "Every placement needs at least one network ad unit id." }
        return candidates.toList()
    }
}

private fun String.requireValue(label: String): String {
    require(isNotBlank()) { "$label must not be blank." }
    require(this == trim()) { "$label must not contain leading or trailing whitespace." }
    return this
}
