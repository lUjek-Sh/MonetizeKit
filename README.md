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
| `:billing-core` | Product catalogue, arbitrary application entitlements, normalized purchase events, and verification contract. |
| `:billing-google-play` | Optional Google Play Billing 9 implementation. |
| `:billing-ads` | Optional bridge that uses selected billing entitlements to suppress ads. |

Include only the adapters required by the selected mode and the formats used by the application.

## Install

You do not need `maven-publish`, a local Maven repository, or an uploaded artifact to use
MonetizeKit. Choose one of the following local integration methods.

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

Declare placements as application-owned constants. A network ID never appears in UI code.

```kotlin
object AppAds {
    val homeBanner = AdsPlacements.banner("home_banner", reservedHeightDp = 50)
    val articleNative = AdsPlacements.native("article_native", reservedHeightDp = 280)
    val levelComplete = AdsPlacements.interstitial("level_complete")
    val rewardCoins = AdsPlacements.rewarded("reward_coins")
    val levelBonus = AdsPlacements.rewardedInterstitial("level_bonus")
    val appReturn = AdsPlacements.appOpen("app_return")
}
```

`reservedHeightDp` is mandatory for banner and native placements. The container retains this height
while an ad loads, fails, or is unavailable, so content does not jump. It becomes zero only when
the configured access policy explicitly suppresses ads, such as after an active paid entitlement.

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

By default, MonetizeKit presents one Google UMP consent flow on the first resumed activity and
blocks adapter initialization until UMP reports that ads may be requested. It does not show a
separate Meta or Unity dialog. An explicitly configured external CMP replaces UMP; it is never
combined with it.

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
`NativeAdRenderer`.

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

A standard banner is network-owned creative, so MonetizeKit does not place this native-card chrome
around it or restyle its content. Use `BannerSize` to choose its format and reserve the necessary
space; use `nativeAppearance` when the layout needs this card design.

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
An empty ID leaves its control disabled. Add the Meta test-device hash before requesting Meta ads;
the debug sample enables Unity SDK test mode, but test mode must also be enabled for the Android app
in the Unity dashboard. These adapters and the inspector exist only in the sample's debug variant.

The **Privacy test profile** controls persist the selected profile and restart the debug process so
the policy is applied before any network initialization. They cover standard UMP, forced UMP EEA,
COPPA, TFUA, US restricted data processing, US GPP, and the external-CMP contract. Put the UMP
test-device hash in `DebugUmpIds` before using either forced UMP geography. The external-CMP profile
is an interactive contract test only: it validates the `PrivacyConsentProvider` lifecycle but is not
a certified CMP and must never be copied into production.

## Verification checklist

### R8 consumer smoke test

`:consumer-r8-smoke` is an internal application that links the direct AdMob, Meta, Unity and the
official Meta/Unity AdMob-mediation adapters exactly as a host app would. It has no launcher
activity, is never installed by CI, and uses public demo/placeholder IDs solely to keep the
consumer configuration reachable by R8. CI also inspects the shrinked APK and fails unless
`FacebookMediationAdapter` and `UnityMediationAdapter` are retained.

```shell
./gradlew :ads-core:testDebugUnitTest :consumer-r8-smoke:assembleRelease
```

### Android behavior test matrix

With an Android device or emulator connected, run:

```shell
./gradlew :ads-core:connectedDebugAndroidTest
```

The device suite uses deterministic fake adapters and consent provider. It covers the external
consent and Privacy Options flow, banner creation before consent versus native creation after the
SDK is ready, inline pause/resume/destroy forwarding, and persisted DataStore frequency-cap
history. Core unit tests additionally verify same-placement concurrent `show()` serialization and
that a retry waits then performs the next actual preload attempt.

`assembleRelease` produces a shrinked APK and verifies merged adapter providers plus every
library's consumer ProGuard rules. The same command runs in GitHub Actions on push and pull request.

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
