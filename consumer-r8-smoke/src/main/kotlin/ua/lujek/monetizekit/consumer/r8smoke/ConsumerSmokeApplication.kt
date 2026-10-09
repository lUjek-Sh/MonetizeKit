package ua.lujek.monetizekit.consumer.r8smoke

import android.app.Application
import ua.lujek.monetizekit.core.AdsPlacements
import ua.lujek.monetizekit.core.MonetizeKit

/**
 * A deliberately small consumer application compiled with R8. It represents a host that ships
 * every direct adapter; CI only assembles it and never installs or requests live advertising.
 */
/**
 * Deliberately contains both direct and mediation adapter dependencies. Runtime configuration
 * selects direct mode only, because MonetizeKit rejects mixing direct and mediation routes in one
 * placement; the release build verifies each adapter's consumer rules survive R8 together.
 */
class ConsumerSmokeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        MonetizeKit.initialize(this) {
            direct {
                admob {
                    applicationId = ADMOB_APPLICATION_ID
                }
                meta()
                unity {
                    gameId = UNITY_GAME_ID
                }
            }

            banner(ConsumerSmokePlacements.banner) {
                admob(ADMOB_BANNER_AD_UNIT)
                meta(META_BANNER_PLACEMENT)
                unity(UNITY_BANNER_PLACEMENT)
            }
            native(ConsumerSmokePlacements.native) {
                admob(ADMOB_NATIVE_AD_UNIT)
                meta(META_NATIVE_PLACEMENT)
            }
            interstitial(ConsumerSmokePlacements.interstitial) {
                admob(ADMOB_INTERSTITIAL_AD_UNIT)
                meta(META_INTERSTITIAL_PLACEMENT)
                unity(UNITY_INTERSTITIAL_PLACEMENT)
            }
            rewarded(ConsumerSmokePlacements.rewarded) {
                admob(ADMOB_REWARDED_AD_UNIT)
                meta(META_REWARDED_PLACEMENT)
                unity(UNITY_REWARDED_PLACEMENT)
            }
            rewardedInterstitial(ConsumerSmokePlacements.rewardedInterstitial) {
                admob(ADMOB_REWARDED_INTERSTITIAL_AD_UNIT)
            }
            appOpen(ConsumerSmokePlacements.appOpen) {
                admob(ADMOB_APP_OPEN_AD_UNIT)
            }
        }
    }
}

private object ConsumerSmokePlacements {
    val banner = AdsPlacements.banner(id = "consumer_banner", reservedHeightDp = 50)
    val native = AdsPlacements.native(id = "consumer_native", reservedHeightDp = 280)
    val interstitial = AdsPlacements.interstitial(id = "consumer_interstitial")
    val rewarded = AdsPlacements.rewarded(id = "consumer_rewarded")
    val rewardedInterstitial = AdsPlacements.rewardedInterstitial(id = "consumer_rewarded_interstitial")
    val appOpen = AdsPlacements.appOpen(id = "consumer_app_open")
}

private const val ADMOB_APPLICATION_ID = "ca-app-pub-3940256099942544~3347511713"
private const val ADMOB_BANNER_AD_UNIT = "ca-app-pub-3940256099942544/6300978111"
private const val ADMOB_NATIVE_AD_UNIT = "ca-app-pub-3940256099942544/2247696110"
private const val ADMOB_INTERSTITIAL_AD_UNIT = "ca-app-pub-3940256099942544/1033173712"
private const val ADMOB_REWARDED_AD_UNIT = "ca-app-pub-3940256099942544/5224354917"
private const val ADMOB_REWARDED_INTERSTITIAL_AD_UNIT = "ca-app-pub-3940256099942544/5354046379"
private const val ADMOB_APP_OPEN_AD_UNIT = "ca-app-pub-3940256099942544/9257395921"
private const val META_BANNER_PLACEMENT = "META_CONSUMER_BANNER"
private const val META_NATIVE_PLACEMENT = "META_CONSUMER_NATIVE"
private const val META_INTERSTITIAL_PLACEMENT = "META_CONSUMER_INTERSTITIAL"
private const val META_REWARDED_PLACEMENT = "META_CONSUMER_REWARDED"
private const val UNITY_GAME_ID = "1234567"
private const val UNITY_BANNER_PLACEMENT = "UNITY_CONSUMER_BANNER"
private const val UNITY_INTERSTITIAL_PLACEMENT = "UNITY_CONSUMER_INTERSTITIAL"
private const val UNITY_REWARDED_PLACEMENT = "UNITY_CONSUMER_REWARDED"
