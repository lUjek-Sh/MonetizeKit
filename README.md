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
}
```

`reservedHeightDp` is mandatory for banner and native placements. The container retains this height
while an ad loads, fails, or is unavailable, so content does not jump. It becomes zero only when
the configured access policy explicitly suppresses ads, such as after an active paid entitlement.

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
Every `render` call must return a new, unattached view.

```kotlin
val articleNative = AdsPlacements.native(
    id = "article_native",
    reservedHeightDp = 280,
    renderer = NativeAdRenderer { context, ad ->
        val headline = TextView(context).apply { text = ad.headline }
        val body = TextView(context).apply { text = ad.body.orEmpty() }
        val cta = Button(context).apply { text = ad.callToAction.orEmpty() }
        val media = FrameLayout(context)

        NativeAdRenderBinding(
            root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(media)
                addView(headline)
                addView(body)
                addView(cta)
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
Compose because it hosts the registered native View through `AndroidView`.

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

`show()` is always explicit. It uses the cached creative when available, otherwise performs the
same ordered direct fallback (or asks the mediation ad unit) and returns a result:

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

`AdsEvent.AdPaid` exposes currency, micros, and SDK precision for supported network formats.
`AdsEvent.AdLoaded` and `AdsError` can include safe response diagnostics such as the AdMob response
ID and mediation adapter class. Never use these values to reconstruct or log an ad creative.

## Debug sample

The `:app` module is a runnable debug playground. It uses Google's official demo App ID and ad
unit IDs for banner, native, interstitial, and rewarded ads; the values live only in the `debug`
source set. The release variant neither initializes MonetizeKit nor contains the AdMob App ID.

```shell
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

The sample exposes SDK/UMP state, preload, explicit fullscreen show controls, privacy options, and
the height-reserved Compose banner/native slots. It is a verification tool, not production app code.

## Verification checklist

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
