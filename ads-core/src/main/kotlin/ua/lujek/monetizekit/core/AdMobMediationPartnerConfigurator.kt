package ua.lujek.monetizekit.core

/**
 * Implemented by an optional AdMob-mediation artifact to apply its SDK's process-wide settings
 * before the Google Mobile Ads SDK initializes its mediation adapters.
 */
interface AdMobMediationPartnerConfigurator {
    val network: AdsNetwork

    fun configureBeforeAdMobInitialization(configuration: AdsConfiguration)
}
