package ua.lujek.monetizekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ua.lujek.monetizekit.core.MonetizeKit
import ua.lujek.monetizekit.core.NativePlacement

/**
 * Renders a native placement in a container whose height is reserved by [NativePlacement].
 * The placeholder stays in the layout on loading, no-fill, and failure, avoiding content movement.
 */
@Composable
fun AdsNative(
    placement: NativePlacement,
    modifier: Modifier = Modifier,
) {
    val kitState by MonetizeKit.state.collectAsState()
    val context = LocalContext.current

    key(placement.id.value, kitState) {
        AndroidView(
            modifier = modifier,
            factory = { MonetizeKit.createNativeSlot(context, placement).also { it.load() } },
            update = { it.load() },
            onRelease = { it.release() },
        )
    }
}
