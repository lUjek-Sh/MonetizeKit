package ua.lujek.monetizekit.compose

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import ua.lujek.monetizekit.core.AdsAccess

/** Shared lifecycle bridge for SDK-owned inline ad Views. */
@Composable
internal fun <T : View> InlineAdHost(
    adsAccess: AdsAccess,
    modifier: Modifier,
    factory: (Context) -> T,
    update: (T) -> Unit,
    onRelease: (T) -> Unit,
) {
    if (adsAccess != AdsAccess.Allowed) return

    AndroidView(
        modifier = modifier,
        factory = factory,
        update = update,
        onRelease = onRelease,
    )
}
