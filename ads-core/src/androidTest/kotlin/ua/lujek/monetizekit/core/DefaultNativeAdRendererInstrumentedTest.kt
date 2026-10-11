package ua.lujek.monetizekit.core.test

import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ua.lujek.monetizekit.core.AdsNetwork
import ua.lujek.monetizekit.core.DefaultNativeAdRenderer
import ua.lujek.monetizekit.core.NativeAdContent
import ua.lujek.monetizekit.core.NativeAdAppearance

@RunWith(AndroidJUnit4::class)
class DefaultNativeAdRendererInstrumentedTest {
    @Test
    fun createsVisibleRequiredAssetsAndNonZeroMediaArea() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val appearance = NativeAdAppearance(
            mediaHeightDp = 180,
            headlineMaxLines = 2,
            showAdvertiser = true,
            showCallToAction = true,
        )
        val binding = DefaultNativeAdRenderer.render(
            context = context,
            content = NativeAdContent(
                network = AdsNetwork.ADMOB,
                headline = "Example headline",
                body = "Example body",
                callToAction = "Install",
                advertiser = "Example advertiser",
                price = null,
                store = null,
                icon = ColorDrawable(android.graphics.Color.MAGENTA),
            ),
            appearance = appearance,
        )

        val width = context.dp(320)
        val height = context.dp(appearance.minimumReservedHeightDp)
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        binding.root.layout(0, 0, width, height)

        assertNotNull(binding.headlineView)
        assertNotNull(binding.bodyView)
        assertNotNull(binding.callToActionView)
        assertNotNull(binding.advertiserView)
        assertNotNull(binding.iconView)
        val mediaContainer = requireNotNull(binding.mediaContainer)
        assertEquals(View.VISIBLE, binding.headlineView?.visibility)
        assertTrue(mediaContainer.height > 0)
        val card = binding.root
        assertTrue(card.background is GradientDrawable)
        val attributionLayoutParams = (card as android.widget.LinearLayout)
            .getChildAt(0)
            .layoutParams as android.widget.LinearLayout.LayoutParams
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, attributionLayoutParams.width)
        assertEquals(android.view.Gravity.CENTER_HORIZONTAL, attributionLayoutParams.gravity)
        assertEquals(context.dp(180), mediaContainer.height)
        assertTrue(mediaContainer.bottom <= card.height)
        assertTrue(binding.bodyView!!.bottom <= card.height)
        assertTrue(binding.callToActionView!!.bottom <= card.height)
    }

    @Test
    fun omitsUnavailableOptionalAssetsInsteadOfDisplayingEmptyViews() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val binding = DefaultNativeAdRenderer.render(
            context = context,
            content = NativeAdContent(
                network = AdsNetwork.ADMOB,
                headline = "Example headline",
                body = null,
                callToAction = null,
                advertiser = null,
                price = null,
                store = null,
            ),
        )

        assertEquals(null, binding.bodyView)
        assertEquals(null, binding.callToActionView)
        assertEquals(null, binding.advertiserView)
        assertEquals(null, binding.iconView)
        assertTrue(binding.root is ViewGroup)
    }

    @Test
    fun rendersCompactCardWithConfiguredMediaAndIconBackground() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val iconColor = 0xFF123456.toInt()
        val appearance = NativeAdAppearance(
            iconContainerColor = iconColor,
            mediaHeightDp = 120,
            headlineMaxLines = 1,
            showAdvertiser = false,
            showCallToAction = false,
        )
        val binding = DefaultNativeAdRenderer.render(
            context = context,
            content = NativeAdContent(
                network = AdsNetwork.ADMOB,
                headline = "Example headline",
                body = "Example body",
                callToAction = "Install",
                advertiser = "Example advertiser",
                price = null,
                store = null,
                icon = ColorDrawable(android.graphics.Color.MAGENTA),
            ),
            appearance = appearance,
        )

        val width = context.dp(320)
        val height = context.dp(appearance.minimumReservedHeightDp)
        binding.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        binding.root.layout(0, 0, width, height)

        assertEquals(1, (binding.headlineView as TextView).maxLines)
        assertNull(binding.advertiserView)
        assertNotNull(binding.bodyView)
        assertNull(binding.callToActionView)
        val mediaContainer = requireNotNull(binding.mediaContainer)
        assertEquals(context.dp(120), mediaContainer.height)
        val iconBackground = requireNotNull(binding.iconView).background as GradientDrawable
        assertEquals(iconColor, iconBackground.color?.defaultColor)
        assertTrue(mediaContainer.bottom <= binding.root.height)
    }

    private fun android.content.Context.dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
