package ua.lujek.monetizekit.core

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Visual properties of MonetizeKit's container around a network-owned banner creative.
 *
 * Colors are Android ARGB values and dimensions are dp. The creative itself remains owned by the
 * ad network: this class decorates only the outer frame and never changes its assets or click
 * handling. The attribution label, [attributionToCreativeSpacingDp], and the top and bottom
 * [contentPaddingDp] are part of a placement's [BannerPlacement.reservedHeightDp].
 */
data class BannerAppearance(
    val frameBackgroundColor: Int = 0xFF1B1B21.toInt(),
    val frameStrokeColor: Int = 0xFF4C4C57.toInt(),
    val attributionBackgroundColor: Int = 0xFF3A3A44.toInt(),
    val attributionTextColor: Int = 0xFFEEEBF2.toInt(),
    val contentPaddingDp: Int = 0,
    val attributionHeightDp: Int = 20,
    val attributionToCreativeSpacingDp: Int = 4,
    val attributionHorizontalPaddingDp: Int = 6,
    val attributionCornerRadiusDp: Int = 7,
    val cornerRadiusDp: Int = 16,
    val frameStrokeWidthDp: Int = 1,
) {
    init {
        require(contentPaddingDp >= 0) { "Banner frame contentPaddingDp must not be negative." }
        require(attributionHeightDp > 0) { "Banner attributionHeightDp must be greater than zero." }
        require(attributionToCreativeSpacingDp >= 0) {
            "Banner attributionToCreativeSpacingDp must not be negative."
        }
        require(attributionHorizontalPaddingDp >= 0) {
            "Banner attributionHorizontalPaddingDp must not be negative."
        }
        require(attributionCornerRadiusDp >= 0) {
            "Banner attributionCornerRadiusDp must not be negative."
        }
        require(cornerRadiusDp >= 0) { "Banner frame cornerRadiusDp must not be negative." }
        require(frameStrokeWidthDp >= 0) { "Banner frame frameStrokeWidthDp must not be negative." }
    }
}

/** Shared wrapper used by every banner adapter, including direct and mediated inventory. */
internal class BannerFrame(
    context: Context,
    appearance: BannerAppearance,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val contentPadding = appearance.contentPaddingDp.dp
    private val attributionHeight = appearance.attributionHeightDp.dp
    private val attributionSpacing = appearance.attributionToCreativeSpacingDp.dp

    init {
        background = GradientDrawable().apply {
            setColor(appearance.frameBackgroundColor)
            setStroke(
                appearance.frameStrokeWidthDp.dp,
                appearance.frameStrokeColor,
            )
            cornerRadius = appearance.cornerRadiusDp.dp.toFloat()
        }
        clipToOutline = true
        addView(
            TextView(context).apply {
                text = context.getString(R.string.monetizekit_banner_ad_attribution)
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(appearance.attributionTextColor)
                setTextSize(11f)
                setPadding(
                    appearance.attributionHorizontalPaddingDp.dp,
                    0,
                    appearance.attributionHorizontalPaddingDp.dp,
                    0,
                )
                background = GradientDrawable().apply {
                    setColor(appearance.attributionBackgroundColor)
                    cornerRadius = appearance.attributionCornerRadiusDp.dp.toFloat()
                }
            },
            LayoutParams(WRAP_CONTENT, attributionHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = contentPadding
            },
        )
    }

    fun attach(creative: View) {
        addView(
            creative,
            LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                leftMargin = contentPadding
                topMargin = contentPadding + attributionHeight + attributionSpacing
                rightMargin = contentPadding
                bottomMargin = contentPadding
            },
        )
    }

    private val Int.dp: Int
        get() = (this * density).toInt()
}
