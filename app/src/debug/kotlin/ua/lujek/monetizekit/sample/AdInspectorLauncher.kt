package ua.lujek.monetizekit.sample

import android.app.Activity
import com.google.android.gms.ads.MobileAds

internal object AdInspectorLauncher {
    fun open(
        activity: Activity,
        onClosed: (message: String) -> Unit,
    ) {
        MobileAds.openAdInspector(activity) { error ->
            onClosed(error?.message ?: "Ad Inspector closed")
        }
    }
}
