package ua.lujek.monetizekit.core

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Network-neutral data that may be safely rendered by an application-owned native ad layout. */
data class NativeAdContent(
    val network: AdsNetwork,
    val headline: String,
    val body: String?,
    val callToAction: String?,
    val advertiser: String?,
    val price: String?,
    val store: String?,
)

/**
 * A native layout created by an application renderer.
 *
 * [root] must be a new unattached view. Asset views let MonetizeKit register the network-native
 * creative correctly. Use [mediaContainer] when the layout reserves a media area; the adapter adds
 * its SDK-owned media view to that container.
 */
data class NativeAdRenderBinding(
    val root: View,
    val headlineView: View? = null,
    val bodyView: View? = null,
    val callToActionView: View? = null,
    val mediaContainer: ViewGroup? = null,
)

/** Creates application-owned native ad layouts while MonetizeKit retains ad SDK registration. */
fun interface NativeAdRenderer {
    fun render(context: Context, content: NativeAdContent): NativeAdRenderBinding
}
