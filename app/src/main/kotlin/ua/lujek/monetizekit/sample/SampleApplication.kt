package ua.lujek.monetizekit.sample

import android.app.Application
import ua.lujek.monetizekit.core.AdsPlacements
import ua.lujek.monetizekit.core.MonetizeKit

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!BuildConfig.DEBUG) return

        MonetizeKit.initialize(this) {
            direct {
                admob {
                    applicationId = DebugAdMobIds.APPLICATION_ID
                }
            }
            banner(DebugSamplePlacements.banner) {
                admob(DebugAdMobIds.BANNER)
            }
            native(DebugSamplePlacements.native) {
                admob(DebugAdMobIds.NATIVE)
            }
            interstitial(DebugSamplePlacements.interstitial) {
                admob(DebugAdMobIds.INTERSTITIAL)
            }
            rewarded(DebugSamplePlacements.rewarded) {
                admob(DebugAdMobIds.REWARDED)
            }
        }
    }
}

object DebugSamplePlacements {
    val banner = AdsPlacements.banner(id = "debug_banner", reservedHeightDp = 50)
    val native = AdsPlacements.native(id = "debug_native", reservedHeightDp = 280)
    val interstitial = AdsPlacements.interstitial(id = "debug_interstitial")
    val rewarded = AdsPlacements.rewarded(id = "debug_rewarded")
}

private object DebugAdMobIds {
    const val APPLICATION_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}
