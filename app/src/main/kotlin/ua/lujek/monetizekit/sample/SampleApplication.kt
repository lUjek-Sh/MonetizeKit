package ua.lujek.monetizekit.sample

import android.app.Application
import ua.lujek.monetizekit.core.AppOpenPlacementPolicy
import ua.lujek.monetizekit.core.AdsPlacements
import ua.lujek.monetizekit.core.GdprDebugGeography
import ua.lujek.monetizekit.core.MonetizeKit
import ua.lujek.monetizekit.core.UsStatePrivacyMode

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) return

        val privacyProfile = SamplePrivacyProfileStore.read(this)
        val meta = SampleDebugConfiguration.meta
        val unity = SampleDebugConfiguration.unity
        MonetizeKit.initialize(this) {
            privacy {
                when (privacyProfile) {
                    SamplePrivacyProfile.UMP_DEFAULT -> Unit
                    SamplePrivacyProfile.UMP_EEA -> configureUmpTestGeography(GdprDebugGeography.EEA)
                    SamplePrivacyProfile.COPPA -> ageRestrictedTreatment(childDirected = true)
                    SamplePrivacyProfile.TFUA -> ageRestrictedTreatment(underAgeOfConsent = true)
                    SamplePrivacyProfile.US_RESTRICTED_DATA_PROCESSING -> {
                        usStatePrivacy(UsStatePrivacyMode.RESTRICTED_DATA_PROCESSING)
                    }
                    SamplePrivacyProfile.US_GLOBAL_PRIVACY_PLATFORM -> {
                        usStatePrivacy(UsStatePrivacyMode.GLOBAL_PRIVACY_PLATFORM)
                        configureUmpTestGeography(GdprDebugGeography.REGULATED_US_STATE)
                    }
                    SamplePrivacyProfile.EXTERNAL_CMP -> {
                        externalConsent(SampleExternalCmpConsentProvider(this@SampleApplication))
                    }
                }
            }
            direct {
                admob {
                    applicationId = DebugAdMobIds.APPLICATION_ID
                }
                meta?.let { metaConfiguration ->
                    meta {
                        metaConfiguration.testDeviceHash?.let { testDeviceHash ->
                            testing(BuildConfig.DEBUG) {
                                testDevice(testDeviceHash)
                            }
                        }
                    }
                }
                unity?.let { unityConfiguration ->
                    unity {
                        gameId = requireNotNull(unityConfiguration.gameId)
                        testing(BuildConfig.DEBUG)
                    }
                }
            }
            banner(DebugSamplePlacements.adMobBanner) {
                admob(DebugAdMobIds.BANNER)
            }
            native(DebugSamplePlacements.adMobNative) {
                admob(DebugAdMobIds.NATIVE)
            }
            interstitial(DebugSamplePlacements.adMobInterstitial) {
                admob(DebugAdMobIds.INTERSTITIAL)
            }
            rewarded(DebugSamplePlacements.adMobRewarded) {
                admob(DebugAdMobIds.REWARDED)
            }
            rewardedInterstitial(DebugSamplePlacements.adMobRewardedInterstitial) {
                admob(DebugAdMobIds.REWARDED_INTERSTITIAL)
            }
            appOpen(
                placement = DebugSamplePlacements.adMobAppOpen,
                policy = AppOpenPlacementPolicy(showOnAppForeground = true),
            ) {
                admob(DebugAdMobIds.APP_OPEN)
            }
            meta?.banner?.let { placementId ->
                banner(DebugSamplePlacements.metaBanner) {
                    meta(placementId)
                }
            }
            meta?.native?.let { placementId ->
                native(DebugSamplePlacements.metaNative) {
                    meta(placementId)
                }
            }
            meta?.interstitial?.let { placementId ->
                interstitial(DebugSamplePlacements.metaInterstitial) {
                    meta(placementId)
                }
            }
            meta?.rewarded?.let { placementId ->
                rewarded(DebugSamplePlacements.metaRewarded) {
                    meta(placementId)
                }
            }
            unity?.banner?.let { adUnitId ->
                banner(DebugSamplePlacements.unityBanner) {
                    unity(adUnitId)
                }
            }
            unity?.interstitial?.let { adUnitId ->
                interstitial(DebugSamplePlacements.unityInterstitial) {
                    unity(adUnitId)
                }
            }
            unity?.rewarded?.let { adUnitId ->
                rewarded(DebugSamplePlacements.unityRewarded) {
                    unity(adUnitId)
                }
            }
        }
    }

    private fun ua.lujek.monetizekit.core.GdprConfigurationBuilder.configureUmpTestGeography(
        geography: GdprDebugGeography,
    ) {
        debug(enabled = BuildConfig.DEBUG) {
            this.geography = geography
            SampleDebugConfiguration.umpTestDeviceHash?.let(::testDeviceHashedId)
        }
    }
}

object DebugSamplePlacements {
    val adMobBanner = AdsPlacements.banner(id = "debug_admob_banner", reservedHeightDp = 50)
    val adMobNative = AdsPlacements.native(id = "debug_admob_native", reservedHeightDp = 280)
    val adMobInterstitial = AdsPlacements.interstitial(id = "debug_admob_interstitial")
    val adMobRewarded = AdsPlacements.rewarded(id = "debug_admob_rewarded")
    val adMobRewardedInterstitial = AdsPlacements.rewardedInterstitial(id = "debug_admob_rewarded_interstitial")
    val adMobAppOpen = AdsPlacements.appOpen(id = "debug_admob_app_open")
    val metaBanner = AdsPlacements.banner(id = "debug_meta_banner", reservedHeightDp = 50)
    val metaNative = AdsPlacements.native(id = "debug_meta_native", reservedHeightDp = 280)
    val metaInterstitial = AdsPlacements.interstitial(id = "debug_meta_interstitial")
    val metaRewarded = AdsPlacements.rewarded(id = "debug_meta_rewarded")
    val unityBanner = AdsPlacements.banner(id = "debug_unity_banner", reservedHeightDp = 50)
    val unityInterstitial = AdsPlacements.interstitial(id = "debug_unity_interstitial")
    val unityRewarded = AdsPlacements.rewarded(id = "debug_unity_rewarded")
}

private object DebugAdMobIds {
    const val APPLICATION_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}

/** Fill only the Meta IDs you need; empty values keep their direct-network controls disabled. */
object DebugMetaIds {
    const val BANNER = ""
    const val NATIVE = ""
    const val INTERSTITIAL = ""
    const val REWARDED = ""
    const val TEST_DEVICE_HASH = ""
}

/** Fill GAME_ID and only the Unity ad-unit IDs you need. Debug builds enable Unity test mode. */
object DebugUnityIds {
    const val GAME_ID = ""
    const val BANNER = ""
    const val INTERSTITIAL = ""
    const val REWARDED = ""
}

/** UMP logs this hashed test-device ID after the first consent-info request. */
object DebugUmpIds {
    const val TEST_DEVICE_HASH = ""
}
