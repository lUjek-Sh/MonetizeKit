package ua.lujek.monetizekit.sample

import android.app.Application
import ua.lujek.monetizekit.core.AppOpenPlacementPolicy
import ua.lujek.monetizekit.core.AdsPlacements
import ua.lujek.monetizekit.core.BannerAppearance
import ua.lujek.monetizekit.core.GdprDebugGeography
import ua.lujek.monetizekit.core.MonetizeKit
import ua.lujek.monetizekit.core.NativeAdAppearance
import ua.lujek.monetizekit.core.UsStatePrivacyMode

private val DebugSampleNativeAppearance = NativeAdAppearance()

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) return

        val privacyProfile = SamplePrivacyProfileStore.read(this)
        val umpTestDeviceHash = SamplePrivacyTestSettingsStore.readUmpTestDeviceHash(this)
        val meta = SampleDebugConfiguration.meta
        val unity = SampleDebugConfiguration.unity
        DebugSamplePlacements.configureNativePlacements(
            appearance = DebugSampleNativeAppearance,
            fontScale = resources.configuration.fontScale,
        )
        MonetizeKit.initialize(this) {
            privacy {
                when (privacyProfile) {
                    SamplePrivacyProfile.UMP_DEFAULT -> Unit
                    SamplePrivacyProfile.UMP_EEA -> {
                        configureUmpTestGeography(GdprDebugGeography.EEA, umpTestDeviceHash)
                    }
                    SamplePrivacyProfile.COPPA -> ageRestrictedTreatment(childDirected = true)
                    SamplePrivacyProfile.TFUA -> ageRestrictedTreatment(underAgeOfConsent = true)
                    SamplePrivacyProfile.US_RESTRICTED_DATA_PROCESSING -> {
                        usStatePrivacy(UsStatePrivacyMode.RESTRICTED_DATA_PROCESSING)
                    }
                    SamplePrivacyProfile.US_GLOBAL_PRIVACY_PLATFORM -> {
                        usStatePrivacy(UsStatePrivacyMode.GLOBAL_PRIVACY_PLATFORM)
                        configureUmpTestGeography(
                            geography = GdprDebugGeography.REGULATED_US_STATE,
                            testDeviceHash = umpTestDeviceHash,
                        )
                    }
                    SamplePrivacyProfile.EXTERNAL_CMP -> {
                        externalConsent(SampleExternalCmpConsentProvider(this@SampleApplication))
                    }
                }
            }
            bannerAppearance(
                BannerAppearance(
                    contentPaddingDp = 6,
                    cornerRadiusDp = 16,
                ),
            )
            nativeAppearance(DebugSampleNativeAppearance)
            direct {
                admob {
                    applicationId = this@SampleApplication.getString(
                        R.string.monetizekit_sample_admob_application_id,
                    )
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
        testDeviceHash: String?,
    ) {
        debug(enabled = BuildConfig.DEBUG) {
            this.geography = geography
            testDeviceHash?.let(::testDeviceHashedId)
        }
    }
}

object DebugSamplePlacements {
    val adMobBanner = AdsPlacements.banner(id = "debug_admob_banner", reservedHeightDp = 96)
    var adMobNative = AdsPlacements.native(
        id = "debug_admob_native",
        reservedHeightDp = DebugSampleNativeAppearance.minimumReservedHeightDp,
    )
        private set
    val adMobInterstitial = AdsPlacements.interstitial(id = "debug_admob_interstitial")
    val adMobRewarded = AdsPlacements.rewarded(id = "debug_admob_rewarded")
    val adMobRewardedInterstitial = AdsPlacements.rewardedInterstitial(id = "debug_admob_rewarded_interstitial")
    val adMobAppOpen = AdsPlacements.appOpen(id = "debug_admob_app_open")
    val metaBanner = AdsPlacements.banner(id = "debug_meta_banner", reservedHeightDp = 96)
    var metaNative = AdsPlacements.native(
        id = "debug_meta_native",
        reservedHeightDp = DebugSampleNativeAppearance.minimumReservedHeightDp,
    )
        private set
    val metaInterstitial = AdsPlacements.interstitial(id = "debug_meta_interstitial")
    val metaRewarded = AdsPlacements.rewarded(id = "debug_meta_rewarded")
    val unityBanner = AdsPlacements.banner(id = "debug_unity_banner", reservedHeightDp = 96)
    val unityInterstitial = AdsPlacements.interstitial(id = "debug_unity_interstitial")
    val unityRewarded = AdsPlacements.rewarded(id = "debug_unity_rewarded")

    fun configureNativePlacements(appearance: NativeAdAppearance, fontScale: Float) {
        val reservedHeightDp = appearance.minimumReservedHeightDp(fontScale)
        adMobNative = AdsPlacements.native(
            id = "debug_admob_native",
            reservedHeightDp = reservedHeightDp,
        )
        metaNative = AdsPlacements.native(
            id = "debug_meta_native",
            reservedHeightDp = reservedHeightDp,
        )
    }
}

private object DebugAdMobIds {
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
}

/**
 * Fill only the direct Meta IDs you need; empty values keep their controls disabled.
 *
 * [TEST_DEVICE_HASH] is the hashed device ID from Meta's test-device flow (or its Logcat hint),
 * not the Android advertising ID and not an AdMob test-device ID.
 */
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

/** Fallback UMP hash. The debug sample can also persist it from its Privacy controls. */
object DebugUmpIds {
    const val TEST_DEVICE_HASH = ""
}
