package ua.lujek.monetizekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ua.lujek.monetizekit.core.MonetizeKit
import ua.lujek.monetizekit.core.AdsAccess
import ua.lujek.monetizekit.core.NativePlacement

/**
 * Renders a native placement in a container whose height is reserved by [NativePlacement].
 * The placeholder stays in the layout on loading, no-fill, and failure, avoiding content movement.
 *
 * This is the Compose-facing API, but it intentionally uses an Android View host internally:
 * Google Mobile Ads and Meta native creatives require SDK-owned asset and media Views for click
 * registration and media rendering. Hiding that bridge preserves a Compose-only call site without
 * exposing an unsafe pure-Compose renderer that cannot register the native assets correctly.
 */
@Composable
fun AdsNative(
    placement: NativePlacement,
    modifier: Modifier = Modifier,
) {
    val kitState by MonetizeKit.state.collectAsState()
    val adsAccess by MonetizeKit.adsAccess.collectAsState()
    val context = LocalContext.current

    if (adsAccess != AdsAccess.Allowed) return

    key(placement.id.value, kitState) {
        AndroidView(
            modifier = modifier,
            factory = { MonetizeKit.createNativeSlot(context, placement).also { it.load() } },
            update = { it.load() },
            onRelease = { it.release() },
        )
    }
}
