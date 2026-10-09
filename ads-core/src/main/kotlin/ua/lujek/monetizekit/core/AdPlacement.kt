package ua.lujek.monetizekit.core

/** A stable identifier owned by the host application, not by an ad network. */
@JvmInline
value class PlacementId private constructor(val value: String) {
    override fun toString(): String = value

    companion object {
        fun of(value: String): PlacementId {
            require(value.isNotBlank()) { "Placement id must not be blank." }
            require(value == value.trim()) { "Placement id must not contain leading or trailing whitespace." }
            return PlacementId(value)
        }
    }
}

enum class AdFormat {
    BANNER,
    INTERSTITIAL,
    REWARDED,
    NATIVE,
}

sealed class AdPlacement protected constructor(
    val id: PlacementId,
    val format: AdFormat,
) {
    override fun toString(): String = "${format.name.lowercase()}:${id.value}"
}

sealed class InlinePlacement protected constructor(
    id: PlacementId,
    format: AdFormat,
    val reservedHeightDp: Int,
) : AdPlacement(id, format) {
    init {
        require(reservedHeightDp > 0) { "Inline ad reservedHeightDp must be greater than zero." }
    }
}

sealed interface BannerSize {
    /** The network's standard banner size, typically 320x50dp. */
    data object Standard : BannerSize

    /** A width-adaptive anchored banner. Reserve a stable height in [AdsPlacements.banner]. */
    data object AnchoredAdaptive : BannerSize

    /** A width-adaptive in-feed banner whose height does not exceed [maxHeightDp]. */
    data class InlineAdaptive(val maxHeightDp: Int) : BannerSize {
        init {
            require(maxHeightDp > 0) { "Inline adaptive banner maxHeightDp must be greater than zero." }
        }
    }
}

enum class CollapsibleBannerAnchor {
    TOP,
    BOTTOM,
}

class BannerPlacement internal constructor(
    id: PlacementId,
    reservedHeightDp: Int,
    val size: BannerSize,
    val collapsibleAnchor: CollapsibleBannerAnchor?,
) : InlinePlacement(id, AdFormat.BANNER, reservedHeightDp) {
    init {
        require(size !is BannerSize.InlineAdaptive || collapsibleAnchor == null) {
            "Collapsible banners cannot use inline adaptive sizing."
        }
    }
}

class NativePlacement internal constructor(
    id: PlacementId,
    reservedHeightDp: Int,
    val renderer: NativeAdRenderer?,
) : InlinePlacement(id, AdFormat.NATIVE, reservedHeightDp)

sealed class FullscreenPlacement protected constructor(
    id: PlacementId,
    format: AdFormat,
) : AdPlacement(id, format)

class InterstitialPlacement internal constructor(id: PlacementId) :
    FullscreenPlacement(id, AdFormat.INTERSTITIAL)

class RewardedPlacement internal constructor(id: PlacementId) :
    FullscreenPlacement(id, AdFormat.REWARDED)

/** Factory for the strongly typed placements used by the host application. */
object AdsPlacements {
    /**
     * [reservedHeightDp] is part of the screen contract: the space stays occupied while the banner
     * loads, fails, or is unavailable, so nearby content never jumps.
     */
    fun banner(
        id: String,
        reservedHeightDp: Int,
        size: BannerSize = BannerSize.Standard,
        collapsibleAnchor: CollapsibleBannerAnchor? = null,
    ): BannerPlacement = BannerPlacement(PlacementId.of(id), reservedHeightDp, size, collapsibleAnchor)

    /**
     * See [banner]; native placements use the same stable layout reservation. When [renderer] is
     * supplied, it owns visual layout while MonetizeKit still handles network asset registration.
     */
    fun native(
        id: String,
        reservedHeightDp: Int,
        renderer: NativeAdRenderer? = null,
    ): NativePlacement = NativePlacement(PlacementId.of(id), reservedHeightDp, renderer)

    fun interstitial(id: String): InterstitialPlacement = InterstitialPlacement(PlacementId.of(id))

    fun rewarded(id: String): RewardedPlacement = RewardedPlacement(PlacementId.of(id))
}
