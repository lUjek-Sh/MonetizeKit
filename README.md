# MonetizeKit

**MonetizeKit** is a modern Android advertising library in the package
`ua.lujek.monetizekit`. It keeps screens independent from ad-network identifiers:
the application declares stable placements once, then renders a banner/native slot or asks to
show a fullscreen placement.

It supports two mutually exclusive delivery modes:

1. **Direct** — AdMob, Meta Audience Network, and Unity Ads are loaded by their native SDKs.
   The host application explicitly selects the fallback priority per placement.
2. **AdMob Mediation** — the application loads one AdMob ad unit; bidding and waterfall decisions
   belong to the AdMob console.

`direct { ... }` and `admobMediation { ... }` cannot be used in one configuration. This prevents
the same placement from requesting both a direct network and its mediated copy.

## Modules

| Module | Purpose |
| --- | --- |
| `:ads-core` | Typed placements, configuration DSL, direct arbitration, UMP consent, fullscreen API. |
| `:ads-admob` | Direct Google Mobile Ads adapter. |
| `:ads-meta` | Direct Meta Audience Network adapter. |
| `:ads-unity` | Direct Unity Ads adapter, using Unity Ads 4.20 instance APIs. |
| `:ads-admob-meta-mediation` | Optional official Meta adapter for AdMob Mediation only. |
| `:ads-admob-unity-mediation` | Optional official Unity adapter for AdMob Mediation only. |
| `:ads-compose` | Compose `AdsBanner` and `AdsNative` wrappers. |

Include only the adapters required by the selected mode and the formats used by the application.

## Install

The library currently builds as a multi-module project. Add project dependencies in the host
application's `build.gradle.kts`.

### Direct mode

```kotlin
dependencies {
    implementation(project(":ads-core"))
    implementation(project(":ads-admob"))       // only if direct AdMob is used
    implementation(project(":ads-meta"))        // only if direct Meta is used
    implementation(project(":ads-unity"))       // only if direct Unity is used
    implementation(project(":ads-compose"))     // only for Compose UI
}
```

### AdMob Mediation mode

```kotlin
dependencies {
    implementation(project(":ads-core"))
    implementation(project(":ads-admob"))
    implementation(project(":ads-admob-meta-mediation"))  // only if Meta is mediated
    implementation(project(":ads-admob-unity-mediation")) // only if Unity is mediated
    implementation(project(":ads-compose"))
}
```

Do **not** include `:ads-meta` or `:ads-unity` for a mediated network. The mediation adapter is
not a direct adapter and is initialized by Google Mobile Ads.

## Manifest

Google Mobile Ads and UMP require an AdMob application ID, including projects that use UMP only
for the unified consent message.

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

The same value must be assigned to `applicationId` when direct AdMob or AdMob Mediation is
enabled. `MonetizeKit` validates the match at runtime.

## Define placements

Declare placements as application-owned constants. A network ID never appears in UI code.

```kotlin
object AppAds {
    val homeBanner = AdsPlacements.banner("home_banner", reservedHeightDp = 50)
    val articleNative = AdsPlacements.native("article_native", reservedHeightDp = 280)
    val levelComplete = AdsPlacements.interstitial("level_complete")
    val rewardCoins = AdsPlacements.rewarded("reward_coins")
}
```

`reservedHeightDp` is mandatory for banner and native placements. The container retains this height
while an ad loads, fails, or is unavailable, so content does not jump.

## Initialize: direct mode

Call `initialize` exactly once in `Application.onCreate`. The order of networks inside a placement
is the **user-selected priority**. The first available candidate wins; a later callback never
replaces an already rendered banner/native ad and never triggers a second fullscreen ad.

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        MonetizeKit.initialize(this) {
            direct {
                admob {
                    applicationId = "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
                    testing(enabled = BuildConfig.DEBUG) {
                        testDevice("ADMOB_TEST_DEVICE_ID")
                    }
                }
                meta {
                    testing(enabled = BuildConfig.DEBUG) {
                        testDevice("META_TEST_DEVICE_HASH")
                    }
                }
                unity {
                    gameId = "UNITY_ANDROID_GAME_ID"
                    testing(enabled = BuildConfig.DEBUG)
                }
            }

            gdpr {
                debug(enabled = BuildConfig.DEBUG) {
                    testDeviceHashedId("UMP_TEST_DEVICE_HASH")
                    geography = GdprDebugGeography.EEA
                }
            }

            banner(AppAds.homeBanner) {
                meta("META_HOME_BANNER_PLACEMENT")       // first choice
                unity("UNITY_HOME_BANNER_PLACEMENT")     // fallback
                admob("ca-app-pub-xxxxxxxxxxxxxxxx/111") // final fallback
            }
            native(AppAds.articleNative) {
                meta("META_ARTICLE_NATIVE_PLACEMENT")
                admob("ca-app-pub-xxxxxxxxxxxxxxxx/222")
            }
            interstitial(AppAds.levelComplete) {
                unity("UNITY_LEVEL_COMPLETE_PLACEMENT")
                meta("META_LEVEL_COMPLETE_PLACEMENT")
                admob("ca-app-pub-xxxxxxxxxxxxxxxx/333")
            }
            rewarded(AppAds.rewardCoins) {
                admob("ca-app-pub-xxxxxxxxxxxxxxxx/444")
                unity("UNITY_REWARD_COINS_PLACEMENT")
                meta("META_REWARD_COINS_PLACEMENT")
            }
        }
    }
}
```

Unity does not provide native ads. Do not put `unity(...)` in a native placement: configuration
would succeed but the adapter cannot render it, so omit it and use Meta and/or AdMob instead.

### Direct arbitration and display safety

Direct mode is a priority fallback, not a price auction. The list order is honored:

1. Load the first supported, initialized candidate.
2. On no-fill or load failure, destroy it and continue with the next candidate.
3. Once a banner/native candidate loads, it is locked for that slot. Late callbacks cannot replace it.
4. A fullscreen candidate becomes ready only. It is shown only after an explicit `show()` call.
5. A process-wide mutex permits only one interstitial or rewarded display at a time.

This deliberately avoids the unsafe "show every network that calls back" pattern. If an application
creates several slots, each slot is independent because it represents a separate screen location.

## Initialize: AdMob Mediation

Configure Meta/Unity bidding or waterfall, placement mappings, and price settings in AdMob. Code
contains only the AdMob ad unit.

```kotlin
MonetizeKit.initialize(this) {
    admobMediation {
        applicationId = "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
        testing(enabled = BuildConfig.DEBUG) {
            testDevice("ADMOB_TEST_DEVICE_ID")
        }
    }

    gdpr {
        debug(enabled = BuildConfig.DEBUG) {
            testDeviceHashedId("UMP_TEST_DEVICE_HASH")
            geography = GdprDebugGeography.EEA
        }
    }

    banner(AppAds.homeBanner) {
        admobMediation("ca-app-pub-xxxxxxxxxxxxxxxx/111")
    }
    interstitial(AppAds.levelComplete) {
        admobMediation("ca-app-pub-xxxxxxxxxxxxxxxx/333")
    }
}
```

Mediation accepts exactly one `admobMediation(...)` source for every placement. Adding `meta`,
`unity`, or direct `admob` to it fails fast because the AdMob console is the sole owner of fallback
and bidding.

## GDPR and privacy options

MonetizeKit presents one Google UMP consent flow on the first resumed activity and blocks adapter
initialization until UMP reports that ads may be requested. It does not show a separate Meta or
Unity dialog.

Before release, publish a GDPR message in **AdMob → Privacy & messaging** and add every active ad
partner, including Meta and Unity. This applies to direct mode as well: UMP creates the TCF and
Additional Consent signals consumed by ad partners. Confirm the exact production consent setup for
your Meta account with your Meta representative.

Expose privacy options in app settings when `MonetizeKit.gdpr` says they are required:

```kotlin
lifecycleScope.launch {
    MonetizeKit.showPrivacyOptions(activity)
}
```

To collect consent at a deliberate point in onboarding instead of waiting for the first resumed
activity:

```kotlin
lifecycleScope.launch {
    val snapshot = MonetizeKit.gatherGdprConsent(activity)
    if (snapshot.canRequestAds) {
        // Ads may now initialize and load.
    }
}
```

## Render inline ads

### Jetpack Compose

```kotlin
@Composable
fun HomeScreen() {
    AdsBanner(placement = AppAds.homeBanner)
    AdsNative(placement = AppAds.articleNative)
}
```

### Views

```kotlin
val bannerSlot = MonetizeKit.createBannerSlot(this, AppAds.homeBanner)
container.addView(bannerSlot)
bannerSlot.load()

// Call release if the View will not be detached automatically.
bannerSlot.release()
```

## Show fullscreen ads

The API is suspending. It performs direct fallback (or asks the mediation ad unit) and returns a
result instead of automatically presenting an ad.

```kotlin
lifecycleScope.launch {
    when (val result = MonetizeKit.show(this@GameActivity, AppAds.levelComplete)) {
        AdShowResult.Shown -> continueGame()
        is AdShowResult.Failed -> continueGameWithoutAd(result.error)
    }
}

lifecycleScope.launch {
    MonetizeKit.show(this@GameActivity, AppAds.rewardCoins) { reward ->
        grantCoins(reward.amount ?: 1)
    }
}
```

## Test-device safety

Never put a testing ID or Unity test mode behind a hard-coded `true`. Use
`enabled = BuildConfig.DEBUG` exactly as in the examples. In a release build the DSL discards:

- `AdMobTestingBuilder.testDevice(...)` IDs;
- `MetaTestingBuilder.testDevice(...)` hashes;
- Unity test mode;
- UMP test-device hashes and forced EEA geography.

The UMP test-device hash is not the same as an AdMob or Meta test device ID. UMP prints it after a
consent-info request. Verify release variants with `BuildConfig.DEBUG == false` before publishing.

## Observe state and events

```kotlin
lifecycleScope.launch {
    MonetizeKit.state.collect { state ->
        // AwaitingGdprConsent, Ready, or Failed.
    }
}

lifecycleScope.launch {
    MonetizeKit.events.collect { event ->
        // NetworkInitialized, AdShown, or AdShowFailed.
    }
}
```

## Verification checklist

- Configure and publish the UMP privacy message and partner list.
- Use either `direct { ... }` or `admobMediation { ... }`, never both.
- Add only the Gradle adapters that belong to the selected mode.
- Test every configured placement and direct fallback order on real devices.
- Confirm no debug test IDs or Unity test mode are included in the release variant.
- Provide an in-app privacy-options entry point when UMP requires it.
