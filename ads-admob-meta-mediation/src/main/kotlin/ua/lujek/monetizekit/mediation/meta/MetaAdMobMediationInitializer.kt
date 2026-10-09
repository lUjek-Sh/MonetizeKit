package ua.lujek.monetizekit.mediation.meta

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.facebook.ads.AdSettings
import ua.lujek.monetizekit.core.AdMobMediationPartnerConfigurator
import ua.lujek.monetizekit.core.AdsConfiguration
import ua.lujek.monetizekit.core.AdsNetwork
import ua.lujek.monetizekit.core.MonetizeKit

/** Registers Meta's mediation-only test configuration before Application.onCreate. */
class MetaAdMobMediationInitializer : ContentProvider() {
    override fun onCreate(): Boolean {
        MonetizeKit.registerAdMobMediationPartnerConfigurator(MetaMediationTestConfigurator)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

private object MetaMediationTestConfigurator : AdMobMediationPartnerConfigurator {
    override val network: AdsNetwork = AdsNetwork.META

    override fun configureBeforeAdMobInitialization(configuration: AdsConfiguration) {
        configuration.adMob?.metaMediationTestDeviceHashes.orEmpty().forEach(AdSettings::addTestDevice)
    }
}
