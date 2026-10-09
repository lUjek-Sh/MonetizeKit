package ua.lujek.monetizekit.sample

import android.app.Activity

internal object AdInspectorLauncher {
    fun open(
        activity: Activity,
        onClosed: (message: String) -> Unit,
    ) {
        onClosed("Ad Inspector is available only in the debug variant")
    }
}
