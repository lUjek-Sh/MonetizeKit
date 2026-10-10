package ua.lujek.monetizekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ua.lujek.monetizekit.core.MonetizeKit
import ua.lujek.monetizekit.core.AdsAccess
import ua.lujek.monetizekit.core.BannerPlacement

/**
 * Renders a configured banner placement and retries the next configured network when a load fails.
 * No Material components or visual chrome are added around the network-owned creative.
 */
@Composable
fun AdsBanner(
    placement: BannerPlacement,
    modifier: Modifier = Modifier,
) {
    val kitState by MonetizeKit.state.collectAsState()
    val adsAccess by MonetizeKit.adsAccess.collectAsState()
    val context = LocalContext.current

    if (adsAccess != AdsAccess.Allowed) return

    // Recreate a previously unavailable slot after asynchronous SDK initialization completes.
    key(placement.id.value, kitState) {
        InlineAdHost(
            adsAccess = adsAccess,
            modifier = modifier,
            factory = { MonetizeKit.createBannerSlot(context, placement).also { it.load() } },
            update = { it.load() },
            onRelease = { it.release() },
        )
    }
}
