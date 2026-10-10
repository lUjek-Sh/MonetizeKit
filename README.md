# MonetizeKit

<p align="center">
  <strong>One Android toolkit for ads, consent, billing, and entitlement-aware ad removal.</strong>
</p>

<p align="center">
  <a href="https://github.com/lUjek-Sh/MonetizeKit/actions/workflows/verify.yml"><img src="https://github.com/lUjek-Sh/MonetizeKit/actions/workflows/verify.yml/badge.svg" alt="Verification workflow"></a>
  <a href="https://jitpack.io/#lUjek-Sh/MonetizeKit"><img src="https://jitpack.io/v/lUjek-Sh/MonetizeKit.svg" alt="JitPack"></a>
  <img src="https://img.shields.io/badge/Android-API%2024%2B-3DDC84?logo=android&logoColor=white" alt="Android API 24 and above">
  <img src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-supported-4285F4" alt="Jetpack Compose supported">
</p>

<p align="center">
  <a href="README.uk.md">Українська версія</a> ·
  <a href="#quick-start">Quick start</a> ·
  <a href="#ai-integration-brief">AI integration brief</a> ·
  <a href="#install">Install</a>
</p>

MonetizeKit is a modular Android library in the `ua.lujek.monetizekit` package. It keeps UI code
free of ad-network identifiers: define stable application placements once, then render an inline
slot or explicitly show a fullscreen placement.

> [!IMPORTANT]
> Choose **one** delivery mode for the application. `direct { ... }` and `admobMediation { ... }`
> are mutually exclusive. This prevents an ad source from being requested directly and through
> AdMob Mediation for the same placement.

## At a glance

| Need | Choose | Who decides which network serves |
| --- | --- | --- |
| Explicit per-placement priority such as `meta → unity → admob` | **Direct** | Your app configuration. MonetizeKit uses ordered fallback and selects one winner. |
| AdMob bidding, waterfall, reporting, and console-side optimisation | **AdMob Mediation** | The AdMob console. The app requests one real AdMob unit per placement. |
| Remove ads after a purchase, while keeping other purchases independent | **Optional Billing** | Your entitlement mapping through `billing-ads`. |

### Core guarantees

- **No surprise double ads.** Direct mode honours your priority and does not let a late callback
  replace an inline creative or open a second fullscreen ad.
- **No inline layout jump.** Banner and native placements reserve their declared height until ads
  load, fail, or are suppressed by an ads-removal entitlement.
- **One privacy gate.** UMP consent is collected before ad requests; an external CMP can replace it
  when your organisation provides one.
- **Only what you include.** Add the adapters and formats that the application actually uses.

## Quick start

1. Pick **Direct** or **AdMob Mediation** using the table above.
2. [Install only the needed modules](#install), then add the AdMob App ID to the manifest.
3. [Define stable placements](#define-placements) outside UI code; banner/native always declare
   `reservedHeightDp`.
4. Initialise once in `Application.onCreate` using either the
   [direct example](#initialize-direct-mode) or the [mediation example](#initialize-admob-mediation).
5. Render inline inventory with [Compose or Views](#render-inline-ads) and call
   [`show()` for fullscreen inventory](#show-fullscreen-ads) only at natural transitions.

<details>
<summary><strong>Documentation map</strong></summary>

| Topic | Start here |
| --- | --- |
| Packages, composite builds, local AARs, JitPack | [Install](#install) |
| Placement types and network support | [Define placements](#define-placements) |
| Direct priority / fallback | [Initialize: direct mode](#initialize-direct-mode) |
| AdMob bidding / waterfall | [Initialize: AdMob Mediation](#initialize-admob-mediation) |
| UMP, COPPA, TFUA, US privacy, external CMP | [GDPR and privacy options](#gdpr-and-privacy-options) |
| Compose, Views, native renderer, banner sizes | [Render inline ads](#render-inline-ads) |
| Preload, caps, retry, interstitial, rewarded, App Open | [Show fullscreen ads](#show-fullscreen-ads) |
| Purchases and ad removal | [Optional billing and entitlements](#optional-billing-and-entitlements) |
| Analytics and ad revenue | [Observe state and events](#observe-state-and-events) |
| Test devices, UMP test geography, debug sample | [Test-device safety](#test-device-safety) |
</details>

## AI integration brief

<details>
<summary><strong>Copy this into ChatGPT, Claude, Cursor, Copilot, or another coding assistant</strong></summary>

```text
You are integrating the Android library MonetizeKit (package ua.lujek.monetizekit) into an existing
Kotlin Android app. Make small, compiling changes that preserve the app's architecture.

First choose exactly one delivery mode:
- Direct mode: configure direct AdMob, Meta Audience Network, and/or Unity Ads. Each placement
  lists network candidates in explicit priority order. MonetizeKit falls back sequentially and one
  loaded banner/native ad is final for that slot. Never request all networks in parallel and never
  show every callback that succeeds.
- AdMob Mediation mode: configure only admobMediation(...) for each placement, with a real AdMob
  ad unit mapped in the AdMob console. Do not add direct meta(...), unity(...), or admob(...) to a
  mediation placement. Bidding and waterfall belong to AdMob.

Rules that must always hold:
1. Initialise MonetizeKit exactly once in Application.onCreate.
2. Define typed AdsPlacements as app-owned constants; never place network IDs in Compose/UI code.
3. Banner and native placements require reservedHeightDp. Render them with AdsBanner/AdsNative in
   Compose or createBannerSlot/createNativeSlot in Views. Do not remove the reserved space while an
   ad is loading or fails. If an ads-removal entitlement is active, MonetizeKit collapses it to zero.
4. Add the AdMob App ID to AndroidManifest.xml. Use the same ID in direct AdMob or mediation
   configuration when those modes are enabled.
5. Use MonetizeKit's single UMP consent flow before requesting ads. Surface Privacy Options only
   through MonetizeKit.showPrivacyOptions(activity). An external certified CMP replaces UMP; it is
   not a second consent dialog.
6. Fullscreen ads are explicit: preload when useful and call MonetizeKit.show(activity, placement)
   only at a natural transition. Rewarded interstitial requires a reward explanation and a skip path.
7. Test IDs and privacy debug geography belong only to debug builds. Never use production IDs as
   test IDs or ship test-device configuration.
8. Billing is optional. Use billing-ads only when a verified entitlement should suppress ads;
   unrelated purchases must not hide ads.

Before proposing code, list the required Gradle modules and identify the selected delivery mode.
After code, state how to test consent, inline ads, and fullscreen ads. Refer to the MonetizeKit
README for exact DSL and network-format support.
```

</details>

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
| `:billing-core` | Product catalogue, arbitrary application entitlements, normalized purchase events, and verification contract. |
| `:billing-google-play` | Optional Google Play Billing 9 implementation. |
| `:billing-ads` | Optional bridge that uses selected billing entitlements to suppress ads. |

Include only the adapters required by the selected mode and the formats used by the application.

## Install

Use GitHub Packages when a released binary is needed. For active MonetizeKit development, choose
one of the local integration methods below instead.

### GitHub Packages

Published library modules use the coordinates `ua.lujek.monetizekit:<module>:<version>` and are
hosted in `https://maven.pkg.github.com/lujek-sh/MonetizeKit`. A private GitHub Packages registry
requires authentication for Gradle dependency resolution.

Create a GitHub **classic** personal access token with `read:packages` and permission to read the
private `lUjek-Sh/MonetizeKit` repository. Store it outside the project, never in Git. Create or
edit the global Gradle properties file:

```text
# Windows: %USERPROFILE%\\.gradle\\gradle.properties
# macOS/Linux: ~/.gradle/gradle.properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=github_pat_your_token
```

In the consuming project's `settings.gradle.kts`, add the registry alongside `google()` and
`mavenCentral()`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/lujek-sh/MonetizeKit")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Then include only the required modules. For direct AdMob with Compose, for example:

```kotlin
val monetizeKitVersion = "0.1.0"

dependencies {
    implementation("ua.lujek.monetizekit:ads-core:$monetizeKitVersion")
    implementation("ua.lujek.monetizekit:ads-admob:$monetizeKitVersion")
    implementation("ua.lujek.monetizekit:ads-compose:$monetizeKitVersion")
}
```

For AdMob Mediation with Meta, use `ads-core`, `ads-admob`, and
`ads-admob-meta-mediation`; do not also add direct `ads-meta`. Add `billing-core`,
`billing-google-play`, or `billing-ads` only when billing is needed. The published Gradle metadata
and POM resolve MonetizeKit module dependencies and public SDK dependencies transitively.

#### Publishing a release

`Publish GitHub Packages` is deliberately not triggered by normal branch pushes. It verifies core
tests and the minified consumer app, then publishes all library modules when either:

1. a `v*` tag is pushed, for example `v0.1.0`; or
2. the workflow is started manually in the Actions tab with a version such as `0.1.0`.

The workflow uses the repository `GITHUB_TOKEN` with `packages: write`; no publishing token needs
to be stored as a repository secret. It rejects `SNAPSHOT` versions. The default local version is
in `gradle.properties` as `monetizekit.version=0.1.0-SNAPSHOT`; keep it as the next development
version after a release.

### JitPack for a future public repository

JitPack is configured but inactive while the repository is private. If `lUjek-Sh/MonetizeKit` is
made public, JitPack builds a tagged revision on demand; it does not receive an upload from the
GitHub Packages workflow. `jitpack.yml` selects JDK 17 and runs `build publishToMavenLocal`, which
publishes every library module from this multi-module project.

After a public tag such as `v0.1.0`, a consumer can use JitPack without credentials:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.lUjek-Sh.MonetizeKit") }
        }
    }
}
```

```kotlin
dependencies {
    implementation("com.github.lUjek-Sh.MonetizeKit:ads-core:v0.1.0")
    implementation("com.github.lUjek-Sh.MonetizeKit:ads-admob:v0.1.0")
    implementation("com.github.lUjek-Sh.MonetizeKit:ads-compose:v0.1.0")
}
```

Use either GitHub Packages or JitPack for a given MonetizeKit version, not both. Keep using a
release tag for public consumption; branch-SNAPSHOT coordinates are intentionally not documented
as a production installation method.

### Recommended: composite build from the source checkout

Keep the MonetizeKit checkout next to the host project, for example:

```text
workspace/
├── MonetizeKit/
└── MyApplication/
```

In the host project's `settings.gradle.kts`, register the checkout and explicitly substitute only
the MonetizeKit modules the application will use:

```kotlin
includeBuild("../MonetizeKit") {
    dependencySubstitution {
        substitute(module("ua.lujek.monetizekit.local:ads-core"))
            .using(project(":ads-core"))
        substitute(module("ua.lujek.monetizekit.local:ads-admob"))
            .using(project(":ads-admob"))
        substitute(module("ua.lujek.monetizekit.local:ads-meta"))
            .using(project(":ads-meta"))
        substitute(module("ua.lujek.monetizekit.local:ads-unity"))
            .using(project(":ads-unity"))
        substitute(module("ua.lujek.monetizekit.local:ads-compose"))
            .using(project(":ads-compose"))
        substitute(module("ua.lujek.monetizekit.local:ads-admob-meta-mediation"))
            .using(project(":ads-admob-meta-mediation"))
        substitute(module("ua.lujek.monetizekit.local:ads-admob-unity-mediation"))
            .using(project(":ads-admob-unity-mediation"))
        substitute(module("ua.lujek.monetizekit.local:billing-core"))
            .using(project(":billing-core"))
        substitute(module("ua.lujek.monetizekit.local:billing-google-play"))
            .using(project(":billing-google-play"))
        substitute(module("ua.lujek.monetizekit.local:billing-ads"))
            .using(project(":billing-ads"))
    }
}
```

Then use ordinary module coordinates in the host app's `app/build.gradle.kts`. Gradle resolves
them from the local checkout; it does not contact a MonetizeKit Maven repository:

```kotlin
dependencies {
    implementation("ua.lujek.monetizekit.local:ads-core:local")
    implementation("ua.lujek.monetizekit.local:ads-admob:local")
    implementation("ua.lujek.monetizekit.local:ads-compose:local")
}
```

Composite build is the preferred development workflow: project dependencies, consumer ProGuard
rules, and third-party transitive dependencies are all resolved by Gradle automatically.

### Local release AAR files

Use this when the host application must receive binaries rather than the MonetizeKit source tree.
Build only the modules selected by the app. For example, direct AdMob + Compose on Windows:

```powershell
.\gradlew.bat :ads-core:assembleRelease :ads-admob:assembleRelease :ads-compose:assembleRelease
```

On macOS/Linux, use the same task list with `./gradlew`. Every artifact is written to its module's
`build/outputs/aar/` directory, for example:

```text
ads-core/build/outputs/aar/ads-core-release.aar
ads-admob/build/outputs/aar/ads-admob-release.aar
ads-compose/build/outputs/aar/ads-compose-release.aar
```

Copy the selected AAR files to the host app, for example `app/libs/monetizekit/`, and reference
them directly — no `flatDir` or Maven repository is required:

```kotlin
dependencies {
    implementation(files("libs/monetizekit/ads-core-release.aar"))
    implementation(files("libs/monetizekit/ads-admob-release.aar"))
    implementation(files("libs/monetizekit/ads-compose-release.aar"))
}
```

An AAR is not a fat AAR: it deliberately does not embed its third-party dependencies. Keep the
normal `google()` and `mavenCentral()` repositories for those public SDKs, then add the runtime
dependencies for each selected local AAR. These versions match this checkout:

```kotlin
dependencies {
    // Required by ads-core.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.insert-koin:koin-core:4.2.2")
    implementation("androidx.lifecycle:lifecycle-runtime:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // Add for direct AdMob or any AdMob Mediation setup.
    implementation("com.google.android.gms:play-services-ads:25.5.0")

    // Add only for the corresponding direct adapter.
    implementation("com.facebook.android:audience-network-sdk:6.22.0") // ads-meta
    implementation("com.unity3d.ads:unity-ads:4.21.0")                 // ads-unity

    // Add only for the corresponding AdMob Mediation AAR.
    implementation("com.google.ads.mediation:facebook:6.22.0.1")      // Meta mediation
    implementation("com.google.ads.mediation:unity:4.21.0.0")         // Unity mediation
}
```

For `ads-compose`, also add the same Compose BOM and `foundation`, `runtime`, and `ui`
dependencies used by the host application. For billing AARs add
`com.android.billingclient:billing-ktx:9.1.0`; include `billing-core-release.aar` before
`billing-google-play-release.aar` or `billing-ads-release.aar`.

Do not combine direct Meta/Unity AARs with their AdMob-mediation AAR for the same configuration;
the normal direct-versus-mediation rule still applies. Consumer R8 rules are already packaged in
the local AARs — the host must not copy them manually.

### When MonetizeKit modules are subprojects of the same build

The following `project(":…")` declarations work only when the MonetizeKit modules have been
copied into, or included by, the host's own `settings.gradle.kts`:

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

### Optional billing

```kotlin
dependencies {
    implementation(project(":billing-core"))
    implementation(project(":billing-google-play")) // Google Play storefront only
    implementation(project(":billing-ads"))         // only when a right can disable ads
}
```

Billing is entirely optional. An app without purchases includes none of these modules. An app that
sells purchases unrelated to advertising can include `:billing-core` and
`:billing-google-play`, but omit `:billing-ads` and `adsAccess(...)`.

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

Declare placements as application-owned values. A network ID never appears in UI code. Create the
native placement with the current system font scale, not with a fixed 1.0-scale value.

```kotlin
object AppAds {
    val nativeAppearance = NativeAdAppearance()
    val homeBanner = AdsPlacements.banner("home_banner", reservedHeightDp = 74)
    lateinit var articleNative: NativePlacement
        private set
    val levelComplete = AdsPlacements.interstitial("level_complete")
    val rewardCoins = AdsPlacements.rewarded("reward_coins")
    val levelBonus = AdsPlacements.rewardedInterstitial("level_bonus")
    val appReturn = AdsPlacements.appOpen("app_return")

    fun configure(fontScale: Float) {
        articleNative = AdsPlacements.native(
            "article_native",
            reservedHeightDp = nativeAppearance.minimumReservedHeightDp(fontScale),
        )
    }
}
```

`reservedHeightDp` is mandatory for banner and native placements. The container retains this height
while an ad loads, fails, or is unavailable, so content does not jump. It becomes zero only when
the configured access policy explicitly suppresses ads, such as after an active paid entitlement.

The built-in native card requires `NativeAdAppearance.minimumReservedHeightDp` (448dp with the
default appearance at font scale 1.0), not an arbitrary 280dp. When placements are created from
`Application`, pass the actual system font scale and the **same appearance** to `initialize`:

```kotlin
val nativeAppearance = NativeAdAppearance(mediaHeightDp = 180)
val articleNative = AdsPlacements.native(
    id = "article_native",
    reservedHeightDp = nativeAppearance.minimumReservedHeightDp(
        resources.configuration.fontScale,
    ),
)

MonetizeKit.initialize(this) {
    nativeAppearance(nativeAppearance)
    // Configure articleNative here.
}
```

Call `AppAds.configure(resources.configuration.fontScale)` in `Application.onCreate`, before using
`AppAds.articleNative` in either initialization mode. Configure the same appearance there:

```kotlin
AppAds.configure(resources.configuration.fontScale)
MonetizeKit.initialize(this) {
    nativeAppearance(AppAds.nativeAppearance)
    // direct { ... } or admobMediation { ... }
}
```

MonetizeKit rejects an undersized placement that uses the built-in renderer during initialization,
instead of clipping the creative. A placement with a custom `NativeAdRenderer` owns its own height.

### Supported formats

| MonetizeKit placement | AdMob direct | Meta direct | Unity direct | Notes |
| --- | --- | --- | --- | --- |
| `banner` | Yes | Yes | Yes | Inline; reserve layout height. |
| `native` | Yes | Yes | No | Inline; reserve layout height. |
| `interstitial` | Yes | Yes | Yes | Explicit fullscreen transition. |
| `rewarded` | Yes | Yes | Yes | User explicitly opts in. |
| `rewardedInterstitial` | Yes | No | No | Requires a reward-intro screen with a skip option. |
| `appOpen` | Yes | No | No | Designed for app foreground/loading moments. |

In AdMob Mediation every row is requested through one `admobMediation(...)` unit. The actual
availability of an ad source then depends on the format support configured in the AdMob console.
In direct mode MonetizeKit skips a candidate whose adapter does not support that format and moves
to the next candidate; do not list Meta or Unity for `appOpen` or `rewardedInterstitial`.

## Initialize: direct mode

Call `initialize` exactly once in `Application.onCreate`. The order of networks inside a placement
is the **user-selected priority**. The first available candidate wins; a later callback never
replaces an already rendered banner/native ad and never triggers a second fullscreen ad.

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppAds.configure(resources.configuration.fontScale)

        MonetizeKit.initialize(this) {
            nativeAppearance(AppAds.nativeAppearance)
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
            rewardedInterstitial(AppAds.levelBonus) {
                admob("ca-app-pub-xxxxxxxxxxxxxxxx/555")
            }
            appOpen(AppAds.appReturn) {
                admob("ca-app-pub-xxxxxxxxxxxxxxxx/666")
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
4. A fullscreen candidate becomes ready only. It is shown only after an explicit `show()` call,
   except an `appOpen` placement configured for automatic foreground presentation.
5. A process-wide mutex permits only one fullscreen creative at a time.

This deliberately avoids the unsafe "show every network that calls back" pattern. If an application
creates several slots, each slot is independent because it represents a separate screen location.

## Initialize: AdMob Mediation

Configure Meta/Unity bidding or waterfall, placement mappings, and price settings in AdMob. Code
contains the **real AdMob ad unit ID** that is attached to that mediation group; Google sample ad
units can serve Google ads only and can never reach the Meta or Unity mapping.

```kotlin
AppAds.configure(resources.configuration.fontScale)
MonetizeKit.initialize(this) {
    nativeAppearance(AppAds.nativeAppearance)
    admobMediation {
        applicationId = "ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy"
        testing(enabled = BuildConfig.DEBUG) {
            testDevice("ADMOB_TEST_DEVICE_ID")
        }
        mediationTesting(enabled = BuildConfig.DEBUG) {
            meta {
                // Meta hashed test-device ID, not the AdMob test-device ID.
                testDevice("META_TEST_DEVICE_HASH")
            }
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
and bidding. `mediationTesting { meta { ... } }` only calls `AdSettings.addTestDevice(...)` before
the Meta mediation adapter is initialized; it does not enable direct Meta requests.

To test mediated Meta safely, use the real AdMob units that are mapped to your Meta placements,
configure the device in both `testing { ... }` and `mediationTesting { meta { ... } }`, and enable
the app's test mode in the Meta dashboard. Install Facebook and log in on the test device, then use
Ad Inspector's **single ad source** test for Meta. For Unity mediation, configure the device in
`testing { ... }` and enable the test device (or forced test mode) in the Unity Ads dashboard; no
extra Unity SDK call is required. Disable testing in the AdMob, Meta, and Unity dashboards before
release.

## GDPR and privacy options

By default, MonetizeKit presents one Google UMP consent flow on the first resumed activity and
blocks adapter initialization until UMP reports that ads may be requested. It does not show a
separate Meta or Unity dialog. An explicitly configured external CMP replaces UMP; it is never
combined with it.

Before release, publish a GDPR message in **AdMob → Privacy & messaging** and add every active ad
partner, including Meta and Unity. This applies to direct mode as well: UMP creates the TCF and
Additional Consent signals consumed by ad partners. Confirm the exact production consent setup for
your Meta account with your Meta representative.

The automated suite verifies MonetizeKit's UMP gateway configuration, reset, and Privacy Options
routing with a deterministic fake. It cannot download or render your account's remote UMP message.
Before release, manually validate a published message with an actual UMP test-device hash and forced
EEA geography, then open Privacy Options and reset the consent state.

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

### COPPA, TFUA, US states, and an external CMP

`privacy { ... }` is an alias of `gdpr { ... }`; it still runs exactly one consent flow. Use it to
declare age treatment and the US privacy transport before any ad network initializes:

```kotlin
privacy {
    ageRestrictedTreatment(
        childDirected = true,       // COPPA declaration for Google Mobile Ads
        underAgeOfConsent = true,   // TFUA for UMP and Google Mobile Ads requests
    )
    usStatePrivacy(UsStatePrivacyMode.RESTRICTED_DATA_PROCESSING)
}
```

`RESTRICTED_DATA_PROCESSING` writes Google's documented `gad_rdp` signal. Use
`GLOBAL_PRIVACY_PLATFORM` when UMP or a CMP writes IAB GPP data instead. Neither option decides
legal applicability for the publisher.

To use a certified external CMP, implement `PrivacyConsentProvider` and configure it once. The
provider replaces UMP completely; before returning `canRequestAds = true`, it must persist all
TCF/GPP signals required by the enabled direct networks.

```kotlin
privacy {
    externalConsent(appCertifiedCmp)
    usStatePrivacy(UsStatePrivacyMode.GLOBAL_PRIVACY_PLATFORM)
}
```

## Optional billing and entitlements

MonetizeKit does not equate a product ID with a feature. The app maps each arbitrary Play Console
product ID to one or more arbitrary application entitlement IDs. This means a single `pro_app`
purchase can simultaneously grant `ads_free`, `pro_sections`, and any later entitlement without
changing the product's ID.

The example below uses `ads_free` only to control advertising. `pro_sections` remains visible to
the rest of the app through `billing.entitlements`; it is not an advertising concern.

```kotlin
class App : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val adsFree = BillingEntitlementId.of("ads_free")
        val proSections = BillingEntitlementId.of("pro_sections")
        val billing = GooglePlayBilling(
            context = this,
            catalog = BillingCatalog(
                listOf(
                    BillingProduct(
                        id = BillingProductId.of("pro_app"),
                        type = BillingProductType.ONE_TIME_NON_CONSUMABLE,
                        grants = setOf(adsFree, proSections),
                    ),
                    BillingProduct(
                        id = BillingProductId.of("coins_500"),
                        type = BillingProductType.ONE_TIME_CONSUMABLE,
                    ),
                ),
            ),
            purchaseVerifier = appPurchaseVerifier,
        )

        MonetizeKit.initialize(this) {
            adsAccess(
                billing.adsAccessProvider(
                    scope = applicationScope,
                    suppressAdsWhenAnyOf = setOf(adsFree),
                ),
            )
            // direct { ... } or admobMediation { ... }, GDPR, and placements
        }

        applicationScope.launch { billing.start() }
    }
}
```

`appPurchaseVerifier` is an application-owned `PurchaseVerifier` that sends the purchase token,
signed purchase JSON, and signature to the app's secure backend. It must return
`PurchaseVerification.Verified` only after the backend has verified **and granted** the purchase.
The SDK then acknowledges a non-consumable/subscription or consumes a consumable. It leaves a
purchase pending when the verifier returns `RetryLater`, so Google Play can deliver it again.

This sequence follows Google's required lifecycle: verify, grant, then acknowledge (or consume
consumables). Do not use a client-only verifier for paid production benefits. [Google Play Billing
integration guide](https://developer.android.com/google/play/billing/integrate)

While entitlements are `Loading` or `Unavailable`, the ads bridge fails closed: it reserves no
banner/native space and makes no ad request. Once the provider reports `Available(emptySet())`,
MonetizeKit starts its normal GDPR flow. If `ads_free` later becomes active, loaded fullscreen ads
are discarded, `show()`/`preload()` return `Suppressed`, and inline slots disappear immediately.

Use the same entitlement stream for non-ad features:

```kotlin
val canOpenProSections = (billing.entitlements.value as? EntitlementState.Available)
    ?.active
    ?.contains(proSections)
    ?: false
```

`BillingPurchaseEvent.Reconciled` is emitted only after verification and Google Play finalization.
It is a reconciliation signal, not an exactly-once delivery callback: it can repeat after a
refresh. Observe it for UI or analytics. The server, not the event collector, must grant consumables
and other paid content idempotently by purchase token. Call `billing.refresh()` when the app returns to
the foreground if it stays alive for a long time, and call `billing.close()` only when the owning
application process is shutting down.

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
bannerSlot.load() // Safe before UMP/network initialization; it waits for Ready.

// Fragment views should use their view lifecycle explicitly.
bannerSlot.bindTo(viewLifecycleOwner)
```

After attachment, a slot also discovers the nearest `ViewTreeLifecycleOwner`. It forwards
pause/resume to compatible ad views and releases its creative at lifecycle destruction or when the
view is detached. Call `release()` yourself only when neither condition can happen.

### Custom native layout

Pass a `NativeAdRenderer` when declaring a native placement. The renderer creates only the visual
layout; MonetizeKit owns the network-native wrapper, media view, click registration, and cleanup.
Every `render` call must return a new, unattached view. The built-in
`DefaultNativeAdRenderer` is used when no renderer is supplied. It renders the dark rounded card
with advertiser icon, visible ad attribution, headline, advertiser, media, body, and optional CTA.

### Configure the default native card

Set `nativeAppearance(...)` once inside `initialize` to override the built-in card without writing
a renderer. All colors are Android ARGB integers; all dimensions are dp except text sizes, which
are sp. This setting affects every native card created by a built-in adapter, including native
creative returned by AdMob Mediation. It has no effect on a placement that supplies a custom
`NativeAdRenderer`. Calculate the native placement reservation from this same instance as shown
above, especially after changing `mediaHeightDp` or supporting larger font scales.

```kotlin
import android.graphics.Color

MonetizeKit.initialize(this) {
    nativeAppearance(
        NativeAdAppearance(
            cardBackgroundColor = Color.rgb(20, 20, 26),
            cardStrokeColor = Color.rgb(75, 75, 88),
            primaryTextColor = Color.WHITE,
            secondaryTextColor = Color.rgb(208, 203, 216),
            attributionBackgroundColor = Color.rgb(60, 60, 72),
            iconContainerColor = Color.rgb(91, 61, 169),
            callToActionColor = Color.rgb(113, 78, 207),
            cardCornerRadiusDp = 20,
            contentPaddingDp = 14,
            iconSizeDp = 44,
            headlineTextSizeSp = 18f,
        ),
    )

    // direct { ... } or admobMediation { ... }, GDPR, and placements
}
```

### Configure the banner frame

Banner creative remains owned by its ad network, but MonetizeKit can render a rounded frame around
it without changing its assets, click handling, or attribution. The frame applies consistently to
direct AdMob/Meta/Unity inventory and to AdMob Mediation.

```kotlin
MonetizeKit.initialize(this) {
    bannerAppearance(
        BannerAppearance(
            frameBackgroundColor = 0xFF1B1B21.toInt(),
            frameStrokeColor = 0xFF4C4C57.toInt(),
            attributionBackgroundColor = 0xFF3A3A44.toInt(),
            attributionTextColor = 0xFFEEEBF2.toInt(),
            contentPaddingDp = 6,
            cornerRadiusDp = 16,
            frameStrokeWidthDp = 1,
        ),
    )

    // direct { ... } or admobMediation { ... }, GDPR, and placements
}
```

The frame always draws a localized `Ad` / `Реклама` attribution label above the network creative.
It never covers or changes the creative itself. Its 20dp height and 4dp spacing are part of the
outer height: a 50dp banner needs at least `reservedHeightDp = 74`. With `contentPaddingDp = 6`,
reserve at least `86dp`; reserve more for adaptive or network-specific banner heights.

A custom renderer must display a clear localized `Ad`/`Sponsored` label, provide `headlineView`,
reserve real space for `mediaContainer`, and omit optional asset views when their values are absent.
If it renders an advertiser icon, it must return that view as `iconView`.

```kotlin
val articleNative = AdsPlacements.native(
    id = "article_native",
    reservedHeightDp = 280,
    renderer = NativeAdRenderer { context, ad ->
        val headline = TextView(context).apply { text = ad.headline }
        val body = ad.body?.takeIf(String::isNotBlank)?.let { TextView(context).apply { text = it } }
        val cta = ad.callToAction?.takeIf(String::isNotBlank)?.let { Button(context).apply { text = it } }
        val media = FrameLayout(context)

        NativeAdRenderBinding(
            root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply { text = "Ad" }) // Localize in a production app.
                addView(headline)
                addView(media, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
                body?.let { addView(it) }
                cta?.let { addView(it) }
            },
            headlineView = headline,
            bodyView = body,
            callToActionView = cta,
            mediaContainer = media,
        )
    },
)
```

`mediaContainer` is required for Meta native ads. The same placement still works in `AdsNative` in
Compose because it hosts the registered native View through `AndroidView`. Check test ads with the
AdMob Native Ad Validator before release; it reports policy and layout issues in the debug build.

### Adaptive and collapsible banners

Banner presentation is explicit and preserves its reserved height while loading:

```kotlin
val footerBanner = AdsPlacements.banner(
    id = "footer_banner",
    reservedHeightDp = 150,
    size = BannerSize.AnchoredAdaptive,
    collapsibleAnchor = CollapsibleBannerAnchor.BOTTOM,
)

val feedBanner = AdsPlacements.banner(
    id = "feed_banner",
    reservedHeightDp = 250,
    size = BannerSize.InlineAdaptive(maxHeightDp = 250),
)
```

Adaptive and collapsible requests are applied by the AdMob adapter. Meta and Unity retain their
standard banner sizes when they are fallback candidates, so reserve enough height for the largest
creative the placement may render.

## Show fullscreen ads

The API is suspending. Preload a fullscreen placement when the SDK becomes `Ready`; this loads only
the first eligible candidate in the configured priority order and does not show anything. Network
load attempts for the same placement are serialized. A successful `show()` consumes that creative and automatically
starts preparing its replacement after dismissal.

Manual direct caches have a one-hour maximum lifetime. MonetizeKit drops an expired creative before
the next `preload()` or `show()` and obtains a fresh one; this follows the Google Mobile Ads caching
guidance for interstitial and rewarded inventory.

### Frequency caps and preload retry

Configure this per fullscreen placement when the app must not show ads too often. A cap is checked
immediately before `show()` and is recorded only after an ad was actually shown. Its small
placement-owned timestamp history is kept in DataStore across process restarts; it contains no ad,
device, or user identifiers.

```kotlin
interstitial(
    placement = AppAds.levelComplete,
    policy = FullscreenPlacementPolicy(
        frequencyCap = FullscreenFrequencyCap(
            cooldownMillis = 90_000L,
            maxImpressions = 3,
            windowMillis = 10 * 60_000L,
        ),
        retryPolicy = FullscreenRetryPolicy(
            maxAttempts = 3,          // includes the first preload request
            initialDelayMillis = 2_000L,
            maxDelayMillis = 15_000L,
        ),
    ),
) {
    unity("UNITY_LEVEL_COMPLETE_PLACEMENT")
    meta("META_LEVEL_COMPLETE_PLACEMENT")
    admob("ca-app-pub-xxxxxxxxxxxxxxxx/333")
}
```

`FullscreenFrequencyCap` may use a cooldown, a rolling `maxImpressions`/`windowMillis` rule, or
both. When capped, `show()` returns `AdShowResult.Capped(nextAllowedAtEpochMillis)` without making
an ad request. `FullscreenRetryPolicy` applies only to explicit `preload()` and to the automatic
replacement preload after a successful show; an explicit `show()` never waits for retry delays.
Only retryable network-load failures are retried, with exponential backoff. The default policy has
one attempt and therefore preserves the previous no-retry behavior.

```kotlin
lifecycleScope.launch {
    when (val result = MonetizeKit.preload(AppAds.levelComplete)) {
        AdLoadResult.Loaded -> enableLevelCompleteTransition()
        AdLoadResult.Suppressed -> continueWithoutInterstitial()
        is AdLoadResult.Failed -> continueWithoutInterstitial(result.error)
    }
}
```

Interstitial, rewarded, and rewarded-interstitial `show()` calls are explicit. They use the cached
creative when available, otherwise perform the same ordered direct fallback (or ask the mediation
ad unit) and return a result:

```kotlin
lifecycleScope.launch {
    when (val result = MonetizeKit.show(this@GameActivity, AppAds.levelComplete)) {
        AdShowResult.Shown -> continueGame()
        AdShowResult.Suppressed -> continueGameWithoutAd()
        is AdShowResult.Capped -> continueGameWithoutAd()
        is AdShowResult.Failed -> continueGameWithoutAd(result.error)
    }
}

lifecycleScope.launch {
    MonetizeKit.show(this@GameActivity, AppAds.rewardCoins) { reward ->
        grantCoins(reward.amount ?: 1)
    }
}
```

### Rewarded interstitial

Show a rewarded interstitial only after an application-owned intro screen clearly states the
reward and gives the user a skip action. It is not an opt-in rewarded ad, so the SDK deliberately
does not manufacture that policy-critical UI.

```kotlin
// Invoked only after the player accepted the reward prompt.
lifecycleScope.launch {
    MonetizeKit.show(this@GameActivity, AppAds.levelBonus) { reward ->
        grantLevelBonus(reward.amount ?: 1)
    }
}

// The prompt's Skip button proceeds without calling show().
```

### App Open

`appOpen(...)` automatically preloads after consent and, by default, shows one creative when the
app next returns to the foreground. It intentionally skips the first cold start, never overlaps
another fullscreen format, uses the same frequency-cap/retry policy, and reloads after a show.
Only one automatic App Open placement may be configured.

```kotlin
appOpen(
    placement = AppAds.appReturn,
    policy = AppOpenPlacementPolicy(
        showOnAppForeground = true,
        showOnColdStart = false,
        fullscreenPolicy = FullscreenPlacementPolicy(
            frequencyCap = FullscreenFrequencyCap(cooldownMillis = 5 * 60_000L),
        ),
    ),
) {
    admob("ca-app-pub-xxxxxxxxxxxxxxxx/666")
}
```

Set `showOnAppForeground = false` to manage timing yourself, then call
`MonetizeKit.preload(AppAds.appReturn)` and `MonetizeKit.show(activity, AppAds.appReturn)` from a
loading/foreground flow. Do not show an App Open ad after the user has reached interactive content
on a cold start.

## Test-device safety

Never put a testing ID or Unity test mode behind a hard-coded `true`. Use
`enabled = BuildConfig.DEBUG` exactly as in the examples. In a release build the DSL discards:

- `AdMobTestingBuilder.testDevice(...)` IDs;
- `MetaTestingBuilder.testDevice(...)` hashes;
- `AdMobMediationTestingBuilder.meta { testDevice(...) }` hashes;
- Unity test mode;
- UMP test-device hashes and forced EEA geography.

The UMP test-device hash is not the same as an AdMob or Meta test device ID. UMP prints it after a
consent-info request. Verify release variants with `BuildConfig.DEBUG == false` before publishing.

## Observe state and events

```kotlin
lifecycleScope.launch {
    MonetizeKit.state.collect { state ->
        // AwaitingGdprConsent, Ready, AdsSuppressed, or Failed.
    }
}

lifecycleScope.launch {
    MonetizeKit.events.collect { event ->
        // NetworkInitialized, AdLoaded, AdImpression, AdClicked, AdPaid,
        // AdRewardEarned, AdPreloadRetryScheduled, AdShowCapped, AdsAccessChanged,
        // AdPreloaded, AdShown, or failure events.
    }
}
```

`AdsEvent.AdPaid` exposes currency, micros, and SDK precision for formats whose SDK reports an
impression-level paid value. `AdsEvent.AdLoaded` and `AdsError` can include safe response
diagnostics such as the AdMob response ID and mediation adapter class. Never use these values to
reconstruct or log an ad creative.

### Firebase / Google Analytics ad revenue

Keep the Firebase integration in the **host application**, not in MonetizeKit. Firebase is optional,
the host owns the Firebase project and Analytics-consent policy, and the same normalized event can
also be sent to an MMP or a backend. MonetizeKit deliberately has no Firebase dependency.

Choose exactly one reporting path per AdMob impression:

| Delivery path | Firebase revenue setup |
| --- | --- |
| Direct AdMob or AdMob Mediation (including Meta/Unity mediated by AdMob) | Link the AdMob app to Firebase/Google Analytics. Firebase records `ad_impression` automatically, including mediation revenue. **Do not** manually forward `AdsEvent.AdPaid`, or revenue will be duplicated. |
| Direct Meta or direct Unity in this version | Do not manufacture revenue from an eCPM or from `AdsEvent.AdImpression`. Their direct adapters currently do not emit `AdsEvent.AdPaid`, so an exact client-side Firebase revenue event is unavailable. Use the network's reporting, mediation with AdMob, or an integration that exposes impression-level revenue. |
| An adapter that emits a genuine `AdsEvent.AdPaid` and is not auto-reported by Firebase | Forward that event from the application, as shown below. |

For automatic AdMob reporting, follow the [Firebase AdMob linking instructions](https://firebase.google.com/docs/analytics/measure-ad-revenue#automatically-track-ad-revenue).
For a manual reporter, add Firebase Analytics to the application using the current Firebase BoM:

```kotlin
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:<current-version>"))
    implementation("com.google.firebase:firebase-analytics")
}
```

Install the collector once in an application-owned scope only when the third row of the table
applies. It converts micros to the currency-unit `Double` required by Firebase. Call
`FirebaseAnalytics.setConsent(...)` first when your Analytics-consent policy requires it; UMP
consent managed by MonetizeKit is not a substitute for configuring Firebase Analytics consent.

```kotlin
import android.app.Application
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import ua.lujek.monetizekit.core.AdsEvent
import ua.lujek.monetizekit.core.MonetizeKit

class App : Application() {
    private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        val analytics = FirebaseAnalytics.getInstance(this)
        analyticsScope.launch {
            MonetizeKit.events
                .filterIsInstance<AdsEvent.AdPaid>()
                .collect { event -> analytics.logManualAdRevenue(event) }
        }
    }
}

private fun FirebaseAnalytics.logManualAdRevenue(event: AdsEvent.AdPaid) {
    logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, Bundle().apply {
        putString(FirebaseAnalytics.Param.AD_PLATFORM, event.network.name.lowercase())
        putString(FirebaseAnalytics.Param.AD_SOURCE, event.network.name)
        putString(FirebaseAnalytics.Param.AD_UNIT_NAME, event.placement.id.value)
        putString(FirebaseAnalytics.Param.AD_FORMAT, event.placement.format.name.lowercase())
        putDouble(FirebaseAnalytics.Param.VALUE, event.revenue.valueMicros / 1_000_000.0)
        putString(FirebaseAnalytics.Param.CURRENCY, event.revenue.currencyCode)
    })
}
```

Never use the manual collector together with the AdMob-to-Firebase automatic integration. Validate
manual events in DebugView; Firebase requires a numeric `value` and an ISO-4217 `currency` for
ad-revenue reporting.

## Debug sample

The `:app` module is a runnable debug playground. It uses Google's official demo App ID and ad
unit IDs for banner, native, interstitial, and rewarded ads; the values live only in the `debug`
source set. The release variant neither initializes MonetizeKit nor contains the AdMob App ID.

```shell
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

The sample exposes SDK/UMP state, reloadable height-reserved Compose banner/native slots, explicit
fullscreen controls, and **Open Ad Inspector**. Ad Inspector diagnoses Google Mobile Ads and AdMob
Mediation; it does not inspect requests issued directly by Meta or Unity. The sample has separate
direct Meta/Unity placements and buttons for those networks, so a test request is never confused
with an AdMob-Mediation request.

For direct Meta or Unity verification, fill only the formats you use in `DebugMetaIds` and
`DebugUnityIds` in [`SampleApplication.kt`](app/src/main/kotlin/ua/lujek/monetizekit/sample/SampleApplication.kt).
An empty ID leaves its control disabled. For direct Meta, copy the **hashed device ID** from Meta's
[test-device flow](https://developers.facebook.com/documentation/audience-network/setting-up/test/test-device)
into `DebugMetaIds.TEST_DEVICE_HASH`; it is neither the Android advertising ID nor the AdMob
test-device ID. The sample passes it to `AdSettings.addTestDevice(...)` before Meta can load an ad,
and only in a debug build. The debug sample enables Unity SDK test mode, but test mode must also be
enabled for the Android app in the Unity dashboard.

For Meta through AdMob Mediation use `admobMediation { mediationTesting(...) { meta { ... } } }`,
not `DebugMetaIds`: it invokes `AdSettings.addTestDevice(...)` without enabling direct Meta. The
mediated request must use your real AdMob unit ID mapped to that Meta placement — Google's demo
units cannot test a mediation chain. These adapters and the inspector exist only in the sample's
debug variant.

To test a real UMP form in the sample, replace the default Google demo App ID in
[`sample_debug_ids.xml`](app/src/debug/res/values/sample_debug_ids.xml) with the App ID from your
AdMob account, and publish a Privacy & messaging message for that app. The App ID must be a manifest
resource because UMP reads it before Kotlin code runs. This does not require replacing the sample's
Google demo **ad-unit** IDs.

The **Privacy test profile** controls persist the selected profile and restart the debug process so
the policy is applied before any network initialization. They cover standard UMP, forced UMP EEA,
COPPA, TFUA, US restricted data processing, US GPP, and the external-CMP contract. First run
**Standard UMP** and copy the separate UMP hash from the Logcat line containing
`addTestDeviceHashedId(...)` — an AdMob `setTestDeviceIds(...)` ID is not valid here. Paste it in
the sample's **UMP test-device hash** field, select UMP EEA or US GPP, then use **Reset UMP, apply
profile, and restart debug app**. This calls UMP's test-only `reset()` before restart, so the form
can be shown again. `DebugUmpIds` remains an optional source-code fallback. The external-CMP profile
is an interactive contract test only: it validates the `PrivacyConsentProvider` lifecycle but is not
a certified CMP and must never be copied into production.

## Verification checklist

### R8 consumer smoke test

`:consumer-r8-smoke` is an internal application that links the direct AdMob, Meta, Unity and the
official Meta/Unity AdMob-mediation adapters exactly as a host app would. It uses a test-only
activity, is installed by CI after R8 shrinking, and uses public demo/placeholder IDs solely to
keep the consumer configuration reachable by R8. CI also inspects the shrinked APK and fails unless
`FacebookMediationAdapter`, `UnityMediationAdapter`, and MonetizeKit's Meta-mediation initializer
are retained.

```shell
./gradlew testDebugUnitTest :consumer-r8-smoke:assembleRelease :consumer-r8-smoke:analyzeReleaseR8Config
```

### Android behavior test matrix

With an Android device or emulator connected, run the same verified runner used by GitHub Actions:

```shell
bash scripts/ci/run-instrumented-tests.sh
```

The script builds and installs every test APK, then accepts only a complete successful
`AndroidJUnitRunner` result with the expected test count. The device suite uses deterministic fake adapters and consent provider. It covers the UMP gateway
configuration (including EEA debug geography), Privacy Options routing and reset, external consent,
banner creation before consent versus native creation after the SDK is ready, Compose inline-view
creation/release, inline pause/resume/destroy forwarding, Google Play Billing callback contracts,
and persisted DataStore frequency-cap history. Core unit tests additionally verify same-placement
concurrent `show()` serialization and that a retry waits then performs the next actual preload
attempt.

`assembleRelease` produces a shrinked APK and verifies merged adapter providers plus every
library's consumer ProGuard rules. The script and CI also install and start that minified APK. The R8 analyzer
checks code shrinking rules; resource shrinking is intentionally outside this smoke test. The same
matrix runs in GitHub Actions on push and pull request.

- Configure and publish the UMP privacy message and partner list.
- Use either `direct { ... }` or `admobMediation { ... }`, never both.
- Add only the Gradle adapters that belong to the selected mode.
- Test every configured placement and direct fallback order on real devices.
- Check that preloading produces one `AdPreloaded` event and that `show()` never presents more than one fullscreen ad.
- Verify `AdShowResult.Capped` and `AdPreloadRetryScheduled` with the chosen production policy.
- Confirm no debug test IDs or Unity test mode are included in the release variant.
- Provide an in-app privacy-options entry point when UMP requires it.
- If billing controls ad access, verify and grant purchases on a secure backend before returning `Verified`.
- Confirm a `pro_app` purchase grants every expected entitlement and only the configured ad-free
  entitlement suppresses advertising.

## License

Copyright 2026 lUjek-Sh.

MonetizeKit is licensed under the [Apache License 2.0](LICENSE). See [NOTICE](NOTICE) for the original author attribution. When redistributing the library or a derivative work, retain both files and their notices.
