package ua.lujek.monetizekit.core

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.ceil
import kotlin.math.max

/** Network-neutral data that may be safely rendered by an application-owned native ad layout. */
data class NativeAdContent(
    val network: AdsNetwork,
    val headline: String,
    val body: String?,
    val callToAction: String?,
    val advertiser: String?,
    val price: String?,
    val store: String?,
    val icon: Drawable? = null,
)

/**
 * A native layout created by an application renderer.
 *
 * [root] must be a new unattached view. Asset views let MonetizeKit register the network-native
 * creative correctly. Use [mediaContainer] when the layout reserves a media area; the adapter adds
 * its SDK-owned media view to that container. A renderer must expose [headlineView] because a
 * native ad headline is required by Google Mobile Ads native-ad policy.
 */
data class NativeAdRenderBinding(
    val root: View,
    val headlineView: View? = null,
    val bodyView: View? = null,
    val callToActionView: View? = null,
    val advertiserView: View? = null,
    val iconView: ImageView? = null,
    val mediaContainer: ViewGroup? = null,
)

/** Creates application-owned native ad layouts while MonetizeKit retains ad SDK registration. */
fun interface NativeAdRenderer {
    fun render(context: Context, content: NativeAdContent): NativeAdRenderBinding
}

/**
 * Visual properties of the built-in native card. Values are Android ARGB colors and density-
 * independent pixels except for the three text sizes, which are in sp.
 *
 * This affects only [DefaultNativeAdRenderer]. A placement with a custom [NativeAdRenderer]
 * owns its entire layout and does not read this appearance.
 */
data class NativeAdAppearance(
    val cardBackgroundColor: Int = 0xFF1B1B21.toInt(),
    val cardStrokeColor: Int = 0xFF4C4C57.toInt(),
    val primaryTextColor: Int = 0xFFF6F4F9.toInt(),
    val secondaryTextColor: Int = 0xFFCFCAD6.toInt(),
    val attributionBackgroundColor: Int = 0xFF3A3A44.toInt(),
    val attributionTextColor: Int = 0xFFEEEBF2.toInt(),
    val iconContainerColor: Int = 0xFF5B3DA9.toInt(),
    val mediaPlaceholderColor: Int = 0xFF121218.toInt(),
    val callToActionColor: Int = 0xFF714ECF.toInt(),
    val contentPaddingDp: Int = 14,
    val itemSpacingDp: Int = 12,
    val headerTopMarginDp: Int = 18,
    val mediaTopMarginDp: Int = 14,
    val identitySpacingDp: Int = 6,
    val iconSizeDp: Int = 44,
    val iconInnerPaddingDp: Int = 5,
    val iconToTextSpacingDp: Int = 12,
    val iconCornerRadiusDp: Int = 10,
    val cardCornerRadiusDp: Int = 20,
    val cardStrokeWidthDp: Int = 1,
    val attributionMinimumSizeDp: Int = 16,
    val attributionHorizontalPaddingDp: Int = 6,
    val attributionVerticalPaddingDp: Int = 2,
    val attributionCornerRadiusDp: Int = 7,
    /** Fixed height of the SDK-owned MediaView. */
    val mediaHeightDp: Int = 180,
    val callToActionMinHeightDp: Int = 40,
    val callToActionHorizontalPaddingDp: Int = 12,
    val callToActionVerticalPaddingDp: Int = 8,
    val callToActionCornerRadiusDp: Int = 8,
    val headlineTextSizeSp: Float = 18f,
    val secondaryTextSizeSp: Float = 14f,
    val callToActionTextSizeSp: Float = 14f,
) {
    init {
        require(contentPaddingDp >= 0) { "Native card contentPaddingDp must not be negative." }
        require(itemSpacingDp >= 0) { "Native card itemSpacingDp must not be negative." }
        require(headerTopMarginDp >= 0) { "Native card headerTopMarginDp must not be negative." }
        require(mediaTopMarginDp >= 0) { "Native card mediaTopMarginDp must not be negative." }
        require(identitySpacingDp >= 0) { "Native card identitySpacingDp must not be negative." }
        require(iconSizeDp > 0) { "Native card iconSizeDp must be greater than zero." }
        require(iconInnerPaddingDp >= 0) { "Native card iconInnerPaddingDp must not be negative." }
        require(iconToTextSpacingDp >= 0) { "Native card iconToTextSpacingDp must not be negative." }
        require(iconCornerRadiusDp >= 0) { "Native card iconCornerRadiusDp must not be negative." }
        require(cardCornerRadiusDp >= 0) { "Native card cardCornerRadiusDp must not be negative." }
        require(cardStrokeWidthDp >= 0) { "Native card cardStrokeWidthDp must not be negative." }
        require(attributionMinimumSizeDp > 0) { "Native card attributionMinimumSizeDp must be greater than zero." }
        require(attributionHorizontalPaddingDp >= 0 && attributionVerticalPaddingDp >= 0) {
            "Native card attribution padding must not be negative."
        }
        require(attributionCornerRadiusDp >= 0) { "Native card attributionCornerRadiusDp must not be negative." }
        require(mediaHeightDp > 0) { "Native card media height must be greater than zero." }
        require(callToActionMinHeightDp > 0) { "Native card callToActionMinHeightDp must be greater than zero." }
        require(callToActionHorizontalPaddingDp >= 0 && callToActionVerticalPaddingDp >= 0) {
            "Native card call-to-action padding must not be negative."
        }
        require(callToActionCornerRadiusDp >= 0) { "Native card callToActionCornerRadiusDp must not be negative." }
        require(headlineTextSizeSp > 0f && secondaryTextSizeSp > 0f && callToActionTextSizeSp > 0f) {
            "Native card text sizes must be greater than zero."
        }
    }

    /**
     * Minimum native placement reservation for the built-in renderer when every supported asset
     * is present: two-line headline, advertiser, three-line body, CTA, and [mediaHeightDp].
     *
     * The value includes a small rounding allowance for Android text measurement at the default
     * font scale. Use [minimumReservedHeightDp] with the current font scale when creating a
     * placement after a [Context] is available.
     */
    val minimumReservedHeightDp: Int
        get() = minimumReservedHeightDp(fontScale = 1f)

    /**
     * Minimum native placement reservation for the built-in renderer at [fontScale].
     *
     * Pass `context.resources.configuration.fontScale` when placements are created during
     * application startup. This keeps all supported text assets inside the reserved native slot
     * when the user uses a larger system font.
     */
    fun minimumReservedHeightDp(fontScale: Float): Int {
        require(fontScale > 0f) { "Native card fontScale must be greater than zero." }
        val attributionHeight = max(
            attributionMinimumSizeDp,
            lineHeightDp(
                textSizeSp = 11f,
                maxLines = 1,
                fontScale = fontScale,
            ) + attributionVerticalPaddingDp * 2,
        )
        val headlineHeight = lineHeightDp(headlineTextSizeSp, maxLines = 2, fontScale = fontScale)
        val advertiserHeight = lineHeightDp(secondaryTextSizeSp, maxLines = 1, fontScale = fontScale)
        val identityHeight = max(iconSizeDp, headlineHeight + identitySpacingDp + advertiserHeight)
        val bodyHeight = lineHeightDp(secondaryTextSizeSp, maxLines = 3, fontScale = fontScale)
        val callToActionHeight = max(
            callToActionMinHeightDp,
            lineHeightDp(
                textSizeSp = callToActionTextSizeSp,
                maxLines = 1,
                fontScale = fontScale,
            ) + callToActionVerticalPaddingDp * 2,
        )
        return contentPaddingDp * 2 +
            attributionHeight +
            headerTopMarginDp +
            identityHeight +
            mediaTopMarginDp +
            mediaHeightDp +
            itemSpacingDp + bodyHeight +
            itemSpacingDp + callToActionHeight +
            TEXT_MEASUREMENT_ALLOWANCE_DP
    }

    private fun lineHeightDp(textSizeSp: Float, maxLines: Int, fontScale: Float): Int =
        ceil(textSizeSp * fontScale * DEFAULT_LINE_HEIGHT_MULTIPLIER * maxLines).toInt()

    private companion object {
        const val DEFAULT_LINE_HEIGHT_MULTIPLIER = 1.25f
        const val TEXT_MEASUREMENT_ALLOWANCE_DP = 4
    }
}

/**
 * Policy-safe baseline renderer for native ads.
 *
 * It displays a dark rounded card with a clear ad-attribution badge, advertiser icon, required
 * headline, every available text asset, and non-zero SDK-owned media area. Applications may
 * provide their own [NativeAdRenderer], but then own visual attribution and native-ad policy
 * compliance.
 */
object DefaultNativeAdRenderer : NativeAdRenderer {
    override fun render(context: Context, content: NativeAdContent): NativeAdRenderBinding =
        render(context, content, NativeAdAppearance())

    fun render(
        context: Context,
        content: NativeAdContent,
        appearance: NativeAdAppearance,
    ): NativeAdRenderBinding {
        val dimensions = DefaultNativeAdDimensions(context, appearance)
        val headlineView = TextView(context).apply {
            text = content.headline
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(appearance.primaryTextColor)
            setTextSize(appearance.headlineTextSizeSp)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        }
        val advertiserView = content.advertiser
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { it == content.headline }
            ?.let { advertiser ->
                TextView(context).apply {
                    text = advertiser
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(appearance.secondaryTextColor)
                    setTextSize(appearance.secondaryTextSizeSp)
                }
            }
        val bodyView = content.body?.takeIf(String::isNotBlank)?.let { body ->
            TextView(context).apply {
                text = body
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(appearance.secondaryTextColor)
                setTextSize(appearance.secondaryTextSizeSp)
            }
        }
        val callToActionView = content.callToAction?.takeIf(String::isNotBlank)?.let { callToAction ->
            TextView(context).apply {
                text = callToAction
                gravity = android.view.Gravity.CENTER
                minHeight = dimensions.callToActionMinHeight
                setPadding(
                    dimensions.callToActionHorizontalPadding,
                    dimensions.callToActionVerticalPadding,
                    dimensions.callToActionHorizontalPadding,
                    dimensions.callToActionVerticalPadding,
                )
                setTextColor(0xFFFFFFFF.toInt())
                setTextSize(appearance.callToActionTextSizeSp)
                background = GradientDrawable().apply {
                    setColor(appearance.callToActionColor)
                    cornerRadius = dimensions.callToActionCornerRadius.toFloat()
                }
            }
        }
        val iconView = content.icon?.let { icon ->
            ImageView(context).apply {
                setImageDrawable(icon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(
                    dimensions.iconInnerPadding,
                    dimensions.iconInnerPadding,
                    dimensions.iconInnerPadding,
                    dimensions.iconInnerPadding,
                )
                background = GradientDrawable().apply {
                    setColor(appearance.iconContainerColor)
                    cornerRadius = dimensions.iconCornerRadius.toFloat()
                }
            }
        }
        val mediaContainer = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                setColor(appearance.mediaPlaceholderColor)
                cornerRadius = dimensions.mediaCornerRadius.toFloat()
            }
            clipToOutline = true
        }
        val attributionView = TextView(context).apply {
            text = context.getString(R.string.monetizekit_native_ad_attribution)
            minWidth = dimensions.attributionMinimumSize
            minHeight = dimensions.attributionMinimumSize
            gravity = android.view.Gravity.CENTER
            setPadding(
                dimensions.attributionHorizontalPadding,
                dimensions.attributionVerticalPadding,
                dimensions.attributionHorizontalPadding,
                dimensions.attributionVerticalPadding,
            )
            setTextColor(appearance.attributionTextColor)
            setTextSize(11f)
            background = GradientDrawable().apply {
                setColor(appearance.attributionBackgroundColor)
                cornerRadius = dimensions.attributionCornerRadius.toFloat()
            }
        }

        val identityColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(headlineView)
            advertiserView?.let { addView(it, verticalLayoutParams(topMargin = dimensions.identitySpacing)) }
        }
        val identityRow = LinearLayout(context).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            iconView?.let {
                addView(it, LinearLayout.LayoutParams(dimensions.iconSize, dimensions.iconSize))
            }
            addView(
                identityColumn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = if (iconView == null) 0 else dimensions.iconToTextSpacing
                },
            )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dimensions.contentPadding,
                dimensions.contentPadding,
                dimensions.contentPadding,
                dimensions.contentPadding,
            )
            background = GradientDrawable().apply {
                setColor(appearance.cardBackgroundColor)
                setStroke(dimensions.cardStrokeWidth, appearance.cardStrokeColor)
                cornerRadius = dimensions.cardCornerRadius.toFloat()
            }
            // MediaView is supplied by the ad SDK and can fill the remaining card area. Keep it
            // inside the rounded card outline instead of letting its rectangular surface cover
            // the bottom corners.
            clipToOutline = true

            addView(
                attributionView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                },
            )
            addView(identityRow, verticalLayoutParams(topMargin = dimensions.headerTopMargin))
            addView(
                mediaContainer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dimensions.mediaHeight,
                ).apply {
                    topMargin = dimensions.mediaTopMargin
                },
            )
            bodyView?.let { addView(it, verticalLayoutParams(topMargin = dimensions.itemSpacing)) }
            callToActionView?.let { addView(it, verticalLayoutParams(topMargin = dimensions.itemSpacing)) }
        }
        return NativeAdRenderBinding(
            root = card,
            headlineView = headlineView,
            bodyView = bodyView,
            callToActionView = callToActionView,
            advertiserView = advertiserView,
            iconView = iconView,
            mediaContainer = mediaContainer,
        )
    }

    private fun verticalLayoutParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            this.topMargin = topMargin
        }
}

private class DefaultNativeAdDimensions(
    context: Context,
    appearance: NativeAdAppearance,
) {
    private val density = context.resources.displayMetrics.density

    val contentPadding: Int = appearance.contentPaddingDp.dp
    val mediaHeight: Int = appearance.mediaHeightDp.dp
    val itemSpacing: Int = appearance.itemSpacingDp.dp
    val headerTopMargin: Int = appearance.headerTopMarginDp.dp
    val mediaTopMargin: Int = appearance.mediaTopMarginDp.dp
    val identitySpacing: Int = appearance.identitySpacingDp.dp
    val iconSize: Int = appearance.iconSizeDp.dp
    val iconInnerPadding: Int = appearance.iconInnerPaddingDp.dp
    val iconToTextSpacing: Int = appearance.iconToTextSpacingDp.dp
    val iconCornerRadius: Int = appearance.iconCornerRadiusDp.dp
    val mediaCornerRadius: Int = 0.dp
    val cardCornerRadius: Int = appearance.cardCornerRadiusDp.dp
    val cardStrokeWidth: Int = appearance.cardStrokeWidthDp.dp
    val attributionMinimumSize: Int = appearance.attributionMinimumSizeDp.dp
    val attributionHorizontalPadding: Int = appearance.attributionHorizontalPaddingDp.dp
    val attributionVerticalPadding: Int = appearance.attributionVerticalPaddingDp.dp
    val attributionCornerRadius: Int = appearance.attributionCornerRadiusDp.dp
    val callToActionMinHeight: Int = appearance.callToActionMinHeightDp.dp
    val callToActionHorizontalPadding: Int = appearance.callToActionHorizontalPaddingDp.dp
    val callToActionVerticalPadding: Int = appearance.callToActionVerticalPaddingDp.dp
    val callToActionCornerRadius: Int = appearance.callToActionCornerRadiusDp.dp

    private val Int.dp: Int
        get() = (this * density).toInt()
}
