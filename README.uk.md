# MonetizeKit

**MonetizeKit** — сучасна Android-бібліотека реклами з пакетом
`ua.lujek.monetizekit`. Екран працює зі стабільним application placement-ом, а не
з ідентифікаторами рекламних мереж: placement оголошується один раз, після чого його можна
намалювати як banner/native або явно показати як fullscreen-рекламу.

Бібліотека має два взаємовиключні режими доставки реклами:

1. **Direct** — нативні Android SDK AdMob, Meta Audience Network і Unity Ads. Застосунок сам
   задає порядок fallback для кожного placement-а.
2. **AdMob Mediation** — код запитує один AdMob ad unit, а bidding і waterfall налаштовуються в
   консолі AdMob.

В одному `MonetizeKit` конфігураційному блоці не можна поєднати `direct { ... }` та
`admobMediation { ... }`. Це не дозволяє запитати одну й ту саму рекламу напряму та через її
mediation-версію одночасно.

## Модулі

| Модуль | Призначення |
| --- | --- |
| `:ads-core` | Типізовані placement-и, DSL-конфігурація, direct arbitration, UMP consent, fullscreen API. |
| `:ads-admob` | Прямий адаптер Google Mobile Ads. |
| `:ads-meta` | Прямий адаптер Meta Audience Network. |
| `:ads-unity` | Прямий Unity Ads adapter на instance API Unity Ads 4.20. |
| `:ads-admob-meta-mediation` | Необов'язковий офіційний Meta adapter тільки для AdMob Mediation. |
| `:ads-admob-unity-mediation` | Необов'язковий офіційний Unity adapter тільки для AdMob Mediation. |
| `:ads-compose` | Compose-обгортки `AdsBanner` і `AdsNative`. |
| `:billing-core` | Каталог продуктів, довільні application entitlement-и, нормалізовані purchase-події та контракт верифікації. |
| `:billing-google-play` | Необов'язкова реалізація Google Play Billing 9. |
| `:billing-ads` | Необов'язковий міст, який вимикає рекламу за вибраними billing entitlement-ами. |

Підключайте лише адаптери обраного режиму та лише ті формати, які справді потрібні застосунку.

## Підключення

Для використання MonetizeKit не потрібні `maven-publish`, локальний Maven-репозиторій або
завантажений artifact. Оберіть один із локальних способів підключення нижче.

### Рекомендовано: composite build із checkout вихідного коду

Розмістіть checkout MonetizeKit поруч із host-проєктом, наприклад:

```text
workspace/
├── MonetizeKit/
└── MyApplication/
```

У `settings.gradle.kts` host-проєкту зареєструйте checkout і явно підставте лише модулі
MonetizeKit, які використовує застосунок:

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

Після цього використовуйте звичайні module coordinates у `app/build.gradle.kts` host-застосунку.
Gradle візьме їх із локального checkout і не звертатиметься до Maven-репозиторію MonetizeKit:

```kotlin
dependencies {
    implementation("ua.lujek.monetizekit.local:ads-core:local")
    implementation("ua.lujek.monetizekit.local:ads-admob:local")
    implementation("ua.lujek.monetizekit.local:ads-compose:local")
}
```

Composite build — найкращий спосіб для розробки: Gradle автоматично бачить project dependencies,
consumer ProGuard rules та транзитивні third-party залежності.

### Локальні release AAR-файли

Використовуйте цей варіант, коли host-застосунок має отримати бінарні файли замість вихідного коду
MonetizeKit. Зберіть лише обрані застосунком модулі. Наприклад, direct AdMob + Compose у Windows:

```powershell
.\gradlew.bat :ads-core:assembleRelease :ads-admob:assembleRelease :ads-compose:assembleRelease
```

У macOS/Linux використайте той самий список task із `./gradlew`. Кожен artifact з'явиться у
`build/outputs/aar/` свого модуля, наприклад:

```text
ads-core/build/outputs/aar/ads-core-release.aar
ads-admob/build/outputs/aar/ads-admob-release.aar
ads-compose/build/outputs/aar/ads-compose-release.aar
```

Скопіюйте потрібні AAR-файли в host-застосунок, наприклад у `app/libs/monetizekit/`, і підключіть
їх напряму — `flatDir` та Maven-репозиторій не потрібні:

```kotlin
dependencies {
    implementation(files("libs/monetizekit/ads-core-release.aar"))
    implementation(files("libs/monetizekit/ads-admob-release.aar"))
    implementation(files("libs/monetizekit/ads-compose-release.aar"))
}
```

AAR не є fat AAR: він навмисно не містить усі сторонні залежності. Залиште звичайні репозиторії
`google()` та `mavenCentral()` для публічних SDK, після чого додайте runtime-залежності для
кожного обраного локального AAR. Версії нижче відповідають цьому checkout:

```kotlin
dependencies {
    // Потрібно для ads-core.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("io.insert-koin:koin-core:4.2.2")
    implementation("androidx.lifecycle:lifecycle-runtime:2.11.0")
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")

    // Додайте для direct AdMob або будь-якої конфігурації AdMob Mediation.
    implementation("com.google.android.gms:play-services-ads:25.5.0")

    // Додайте лише для відповідного direct adapter-а.
    implementation("com.facebook.android:audience-network-sdk:6.22.0") // ads-meta
    implementation("com.unity3d.ads:unity-ads:4.21.0")                 // ads-unity

    // Додайте лише для відповідного AAR AdMob Mediation.
    implementation("com.google.ads.mediation:facebook:6.22.0.1")      // Meta mediation
    implementation("com.google.ads.mediation:unity:4.21.0.0")         // Unity mediation
}
```

Для `ads-compose` також додайте той самий Compose BOM і залежності `foundation`, `runtime` та
`ui`, які використовує host-застосунок. Для billing AAR додайте
`com.android.billingclient:billing-ktx:9.1.0`; підключайте `billing-core-release.aar` перед
`billing-google-play-release.aar` або `billing-ads-release.aar`.

Не поєднуйте direct Meta/Unity AAR із відповідним AdMob-mediation AAR в одній конфігурації:
звичайне правило direct проти mediation залишається чинним. Consumer R8 rules уже є всередині
локальних AAR — вручну копіювати їх у host не треба.

### Коли модулі MonetizeKit є subproject-ами того самого build

Наведені нижче декларації `project(":…")` працюють лише тоді, коли модулі MonetizeKit скопійовані
у власний `settings.gradle.kts` host-проєкту або включені ним:

### Direct-режим

```kotlin
dependencies {
    implementation(project(":ads-core"))
    implementation(project(":ads-admob"))       // лише якщо потрібен direct AdMob
    implementation(project(":ads-meta"))        // лише якщо потрібен direct Meta
    implementation(project(":ads-unity"))       // лише якщо потрібен direct Unity
    implementation(project(":ads-compose"))     // лише для Compose UI
}
```

### AdMob Mediation

```kotlin
dependencies {
    implementation(project(":ads-core"))
    implementation(project(":ads-admob"))
    implementation(project(":ads-admob-meta-mediation"))  // лише якщо Meta у mediation
    implementation(project(":ads-admob-unity-mediation")) // лише якщо Unity у mediation
    implementation(project(":ads-compose"))
}
```

Для mediated мережі не додавайте `:ads-meta` або `:ads-unity`: mediation adapter не є direct
adapter-ом і його ініціалізує Google Mobile Ads SDK.

### Необов'язковий billing

```kotlin
dependencies {
    implementation(project(":billing-core"))
    implementation(project(":billing-google-play")) // лише для Google Play storefront
    implementation(project(":billing-ads"))         // лише коли entitlement може вимкнути рекламу
}
```

Billing повністю необов'язковий. Застосунок без покупок не додає ці модулі. Якщо застосунок продає
лише покупки, не пов'язані з рекламою, додайте `:billing-core` і `:billing-google-play`, але не
додавайте `:billing-ads` та не викликайте `adsAccess(...)`.

## AndroidManifest

Google Mobile Ads та UMP потребують AdMob Application ID, навіть якщо AdMob використовується
лише для єдиного UMP consent-діалогу.

```xml
<application>
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy" />
</application>
```

Коли увімкнено direct AdMob або AdMob Mediation, це саме значення має бути у
`applicationId`. MonetizeKit перевіряє збіг під час виконання.

## Оголосіть placement-и

Placement-и належать застосунку. Network ID не повинен потрапляти у UI-код.

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

`reservedHeightDp` є обов'язковим для banner і native. Контейнер зберігає висоту, поки реклама
завантажується, не заповнюється або недоступна, тому контент екрана не стрибає. Висота стає нульовою
лише тоді, коли налаштована access policy явно вимикає рекламу, наприклад після активного paid
entitlement-а.

### Підтримувані формати

| Placement MonetizeKit | AdMob direct | Meta direct | Unity direct | Примітка |
| --- | --- | --- | --- | --- |
| `banner` | Так | Так | Так | Inline; резервуйте висоту. |
| `native` | Так | Так | Ні | Inline; резервуйте висоту. |
| `interstitial` | Так | Так | Так | Явний fullscreen-перехід. |
| `rewarded` | Так | Так | Так | Користувач сам погоджується на перегляд. |
| `rewardedInterstitial` | Так | Ні | Ні | Потрібен екран пояснення винагороди з можливістю пропустити. |
| `appOpen` | Так | Ні | Ні | Для foreground/loading моментів застосунку. |

В AdMob Mediation кожен формат запитується через один `admobMediation(...)` unit. Наявність
конкретного джерела залежить від підтримки формату, налаштованої в консолі AdMob. У direct-режимі
MonetizeKit пропускає кандидата, адаптер якого не підтримує формат, і переходить до наступного;
не додавайте Meta або Unity до `appOpen` чи `rewardedInterstitial`.

## Ініціалізація: direct

Викликайте `initialize` один раз у `Application.onCreate`. Послідовність мереж усередині
placement-а — це **обраний користувачем пріоритет**. Переможе перший доступний кандидат; пізній
callback не підмінить уже відмальований banner/native і не відкриє другу fullscreen-рекламу.

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
                meta("META_HOME_BANNER_PLACEMENT")       // перший пріоритет
                unity("UNITY_HOME_BANNER_PLACEMENT")     // fallback
                admob("ca-app-pub-xxxxxxxxxxxxxxxx/111") // останній fallback
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

Unity не надає native-рекламу. Не додавайте `unity(...)` до native placement-а: конфігурація
пройде, але адаптер не зможе його намалювати. Для native використовуйте Meta та/або AdMob.

### Direct arbitration і безпечний показ

Direct-режим — це priority fallback, а не ціновий аукціон. Порядок списку гарантується:

1. Завантажується перший підтримуваний та ініціалізований кандидат.
2. У разі no-fill або помилки він знищується, після чого завантажується наступний.
3. Після успішного завантаження banner/native кандидат фіксується для цього slot-а. Пізні callback-и
   не можуть його замінити.
4. Fullscreen кандидат стає лише `Ready`; він показується лише після явного `show()`, окрім
   `appOpen`, для якого увімкнено автоматичний показ при foreground.
5. Process-wide mutex дозволяє показувати одночасно лише один fullscreen creative.

Отже, бібліотека навмисно не використовує небезпечний підхід «показати кожну мережу, callback
якої прийшов». Кілька незалежних slot-ів можливі, бо вони є різними місцями на екрані.

## Ініціалізація: AdMob Mediation

Налаштуйте Meta/Unity bidding або waterfall, відповідності placement-ів та ціни у консолі AdMob.
У коді залишається лише AdMob ad unit.

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

Для кожного placement-а mediation приймає рівно один `admobMediation(...)`. Якщо додати `meta`,
`unity` або direct `admob`, конфігурація завершиться помилкою: fallback та bidding належать тільки
консолі AdMob.

## GDPR і privacy options

За замовчуванням MonetizeKit показує єдиний UMP consent flow при першому resumed activity та не
ініціалізує рекламні адаптери, доки UMP не дозволить запитувати рекламу. Окремих Meta або Unity
діалогів немає. Явно налаштований зовнішній CMP замінює UMP і ніколи не комбінується з ним.

Перед релізом опублікуйте GDPR-повідомлення у **AdMob → Privacy & messaging** і додайте усіх
активних партнерів, зокрема Meta та Unity. Це також стосується direct-режиму: UMP створює TCF та
Additional Consent сигнали для ad partners. Перевірте точне production налаштування consent для
вашого Meta account із представником Meta.

Покажіть privacy options у налаштуваннях застосунку, якщо цього вимагає `MonetizeKit.gdpr`:

```kotlin
lifecycleScope.launch {
    MonetizeKit.showPrivacyOptions(activity)
}
```

Щоб зібрати consent у конкретний момент onboarding-а, а не чекати першої resumed activity:

```kotlin
lifecycleScope.launch {
    val snapshot = MonetizeKit.gatherGdprConsent(activity)
    if (snapshot.canRequestAds) {
        // Тепер SDK може ініціалізуватися та завантажувати рекламу.
    }
}
```

### COPPA, TFUA, US states і зовнішній CMP

`privacy { ... }` — це alias для `gdpr { ... }`; він так само запускає рівно один consent flow.
Використовуйте його, щоб оголосити age treatment і US privacy transport до ініціалізації рекламних
мереж:

```kotlin
privacy {
    ageRestrictedTreatment(
        childDirected = true,       // COPPA-декларація для Google Mobile Ads
        underAgeOfConsent = true,   // TFUA для UMP і Google Mobile Ads request-ів
    )
    usStatePrivacy(UsStatePrivacyMode.RESTRICTED_DATA_PROCESSING)
}
```

`RESTRICTED_DATA_PROCESSING` записує документований Google сигнал `gad_rdp`.
`GLOBAL_PRIVACY_PLATFORM` використовуйте, коли UMP або CMP замість цього записує IAB GPP. Жоден з
режимів не визначає за publisher-а юридичну застосовність.

Для сертифікованого зовнішнього CMP реалізуйте `PrivacyConsentProvider` і налаштуйте його один раз.
Provider повністю замінює UMP; до повернення `canRequestAds = true` він має зберегти всі TCF/GPP
сигнали, потрібні увімкненим direct-мережам.

```kotlin
privacy {
    externalConsent(appCertifiedCmp)
    usStatePrivacy(UsStatePrivacyMode.GLOBAL_PRIVACY_PLATFORM)
}
```

## Необов'язковий billing та entitlement-и

MonetizeKit не прирівнює product ID до функції. Застосунок сам зіставляє довільний Play Console
product ID з одним або кількома довільними application entitlement ID. Тому одна покупка `pro_app`
може одразу надати `ads_free`, `pro_sections` і будь-які наступні права без зміни product ID.

У прикладі нижче `ads_free` керує лише рекламою. `pro_sections` доступний решті застосунку через
`billing.entitlements`; для реклами це право не має значення.

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
            // direct { ... } або admobMediation { ... }, GDPR та placement-и
        }

        applicationScope.launch { billing.start() }
    }
}
```

`appPurchaseVerifier` — реалізація `PurchaseVerifier` на боці застосунку: вона надсилає purchase
token, підписаний purchase JSON і signature на захищений backend застосунку. Вона має повернути
`PurchaseVerification.Verified` тільки після того, як backend **перевірив і надав** покупку.
Після цього SDK acknowledge-ить non-consumable/subscription або consume-ить consumable. Якщо
verifier повертає `RetryLater`, покупка лишається необробленою, тому Google Play доставить її знову.

Ця послідовність відповідає вимозі Google: verify, grant, потім acknowledge (або consume для
consumable). Не використовуйте client-only verifier для платних production-прав. [Посібник з
інтеграції Google Play Billing](https://developer.android.com/google/play/billing/integrate)

Коли entitlement-и мають стан `Loading` або `Unavailable`, ads bridge fail-closed: не резервує
місце під banner/native і не робить ad request. Після `Available(emptySet())` MonetizeKit запускає
звичайний GDPR flow. Якщо `ads_free` стане активним пізніше, завантажені fullscreen-оголошення
видаляються, `show()`/`preload()` повертають `Suppressed`, а inline slot-и зникають одразу.

Той самий entitlement stream керує функціями, не пов'язаними з рекламою:

```kotlin
val canOpenProSections = (billing.entitlements.value as? EntitlementState.Available)
    ?.active
    ?.contains(proSections)
    ?: false
```

`BillingPurchaseEvent.Reconciled` надходить лише після верифікації та Google Play finalization.
Це сигнал синхронізації, а не callback рівно-одного доставлення: він може повторитися після refresh.
Спостерігайте за ним для UI чи analytics. Саме сервер, а не event collector, має idempotent-но
надавати consumable та інший paid content за purchase token. Викликайте `billing.refresh()`, коли
застосунок повертається на foreground після тривалої роботи, а `billing.close()` — лише під час
завершення процесу застосунку.

## Відмальовуйте inline-рекламу

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
bannerSlot.load() // Безпечно до UMP/network initialization: slot дочекається Ready.

// Для Fragment View явно передайте його view lifecycle.
bannerSlot.bindTo(viewLifecycleOwner)
```

Після attachment slot також знаходить найближчий `ViewTreeLifecycleOwner`. Він передає
pause/resume сумісним ad view і звільняє creative при знищенні lifecycle або від'єднанні View.
Викликайте `release()` самостійно лише тоді, коли жоден із цих випадків не може настати.

### Налаштування вбудованої native-картки

Якщо не передати власний `NativeAdRenderer`, MonetizeKit показує темну заокруглену native-картку:
видима позначка реклами, справжня іконка рекламодавця, заголовок, рекламне медіа, опис і CTA.
Її вигляд можна задати один раз під час ініціалізації:

```kotlin
MonetizeKit.initialize(this) {
    nativeAppearance(
        NativeAdAppearance(
            cardBackgroundColor = 0xFF17171F.toInt(),
            cardStrokeColor = 0xFF4C4C57.toInt(),
            primaryTextColor = 0xFFF6F4F9.toInt(),
            secondaryTextColor = 0xFFCFCAD6.toInt(),
            attributionBackgroundColor = 0xFF3A3A44.toInt(),
            attributionTextColor = 0xFFEEEBF2.toInt(),
            iconContainerColor = 0xFF5B3DA9.toInt(),
            mediaPlaceholderColor = 0xFF121218.toInt(),
            callToActionColor = 0xFF714ECF.toInt(),
            contentPaddingDp = 14,
            cardCornerRadiusDp = 20,
            headlineTextSizeSp = 18f,
        ),
    )

    // direct { ... } або admobMediation { ... }
}
```

Кольори — це Android ARGB `Int`; усі розміри, крім `*TextSizeSp`, вказуються в dp. Передавайте
лише потрібні властивості: решта зберігають policy-safe значення за замовчуванням. Налаштування
впливає на всі native-картки, створені вбудованим renderer-ом, зокрема на native creative,
отриманий через AdMob Mediation. Placement із власним `NativeAdRenderer` повністю керує своєю
розміткою і не читає `NativeAdAppearance`.

Звичайний `Banner` не можна стилізувати як цю картку: creative, розмір і склад банера належать
рекламній мережі. Для нього використовуйте `BannerSize` і зарезервовану висоту; для такого дизайну
використовуйте native placement.

### Власна native-розмітка

Передайте `NativeAdRenderer` під час оголошення native placement. Renderer створює лише візуальну
розмітку; MonetizeKit володіє network-native wrapper, media view, реєстрацією кліків і очищенням.
Кожен виклик `render` має повертати новий View, який ще не прикріплений до parent. Якщо renderer
не передано, використовується описана вище вбудована картка. Власний renderer має показати чітку
локалізовану позначку `Ad`/`Sponsored`, надати `headlineView`, зарезервувати реальний простір для
`mediaContainer` і не показувати optional asset View, коли для нього немає значення. Якщо renderer
показує іконку рекламодавця, поверніть її через `iconView`.

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
                addView(TextView(context).apply { text = "Ad" }) // Локалізуйте у production-застосунку.
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

`mediaContainer` обов'язковий для Meta native ads. Той самий placement працює в Compose `AdsNative`,
бо він показує зареєстрований native View через `AndroidView`. Перед релізом перевірте test ads у
AdMob Native Ad Validator: він повідомляє про policy та layout-проблеми в debug-збірці.

### Adaptive і collapsible banners

Banner presentation задається явно й зберігає зарезервовану висоту під час завантаження:

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

Adaptive і collapsible request-и застосовує AdMob adapter. Meta та Unity зберігають standard banner
size, коли є fallback-кандидатами, тому резервуйте висоту для найбільшого creative цього placement-а.

## Показуйте fullscreen-рекламу

API є suspending. Коли SDK перейшов у `Ready`, завчасно викличте preload fullscreen-placement:
буде завантажено лише першого доступного кандидата у заданому порядку, без показу реклами. Мережеві
спроби завантаження для одного placement виконуються послідовно. Після успішного `show()` creative витрачається, а бібліотека
автоматично починає готувати наступний після закриття реклами.

Manual direct cache має максимальний строк життя одну годину. MonetizeKit відкидає прострочений
creative перед наступним `preload()` або `show()` і запитує свіжий; це відповідає рекомендації
Google Mobile Ads щодо кешування interstitial і rewarded inventory.

### Frequency cap і retry для preload

Налаштуйте policy для конкретного fullscreen placement-а, якщо застосунок не повинен показувати
рекламу надто часто. Cap перевіряється безпосередньо перед `show()` і записується лише після
фактичного показу реклами. Невелика історія timestamp-ів, що належить placement-у, зберігається в
DataStore між перезапусками процесу; вона не містить ані ad, ані device, ані user identifier-ів.

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
            maxAttempts = 3,          // включає перший preload-запит
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

`FullscreenFrequencyCap` може використовувати cooldown, ковзне правило
`maxImpressions`/`windowMillis` або обидва варіанти. Якщо спрацьовує cap, `show()` повертає
`AdShowResult.Capped(nextAllowedAtEpochMillis)` без рекламного запиту. `FullscreenRetryPolicy`
застосовується тільки до явного `preload()` та автоматичного preload після успішного показу;
явний `show()` ніколи не чекає retry delay. Повторюються лише retryable network-load помилки з
експоненційним backoff. Policy за замовчуванням має одну спробу та зберігає попередню поведінку без retry.

```kotlin
lifecycleScope.launch {
    when (val result = MonetizeKit.preload(AppAds.levelComplete)) {
        AdLoadResult.Loaded -> enableLevelCompleteTransition()
        AdLoadResult.Suppressed -> continueWithoutInterstitial()
        is AdLoadResult.Failed -> continueWithoutInterstitial(result.error)
    }
}
```

`show()` для interstitial, rewarded і rewarded-interstitial викликається явно. Він використає
підготовлений creative, якщо він є; інакше виконає той самий ordered direct fallback або звернеться
до mediation ad unit:

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

Показуйте rewarded interstitial лише після екрана, який належить застосунку, чітко пояснює
винагороду й дає можливість пропустити рекламу. Це не opt-in rewarded ad, тому SDK навмисно не
створює такий policy-critical UI самостійно.

```kotlin
// Викликайте лише після того, як гравець погодився у reward prompt.
lifecycleScope.launch {
    MonetizeKit.show(this@GameActivity, AppAds.levelBonus) { reward ->
        grantLevelBonus(reward.amount ?: 1)
    }
}

// Кнопка Skip у prompt продовжує сценарій без show().
```

### App Open

`appOpen(...)` автоматично preload-иться після consent і за замовчуванням показує один creative,
коли застосунок наступного разу повертається у foreground. Він навмисно пропускає перший cold
start, не перетинається з іншим fullscreen-форматом, використовує той самий frequency cap/retry
policy та reload-иться після показу. Автоматичним може бути лише один App Open placement.

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

Вкажіть `showOnAppForeground = false`, щоб самостійно керувати моментом показу: тоді викликайте
`MonetizeKit.preload(AppAds.appReturn)` і `MonetizeKit.show(activity, AppAds.appReturn)` зі свого
loading/foreground flow. Не показуйте App Open, якщо користувач уже дістався інтерактивного
контенту під час cold start.

## Безпечне тестування

Не пишіть test ID або Unity test mode з hard-coded `true`. Використовуйте
`enabled = BuildConfig.DEBUG`, як у прикладах. У release-збірці DSL відкидає:

- ID з `AdMobTestingBuilder.testDevice(...)`;
- хеші з `MetaTestingBuilder.testDevice(...)`;
- Unity test mode;
- UMP test-device hash і примусову EEA geography.

UMP test-device hash не дорівнює AdMob або Meta test device ID. UMP виводить його після
consent-info request. Перед публікацією переконайтеся, що у release variant
`BuildConfig.DEBUG == false`.

## Стан та події

```kotlin
lifecycleScope.launch {
    MonetizeKit.state.collect { state ->
        // AwaitingGdprConsent, Ready, AdsSuppressed або Failed.
    }
}

lifecycleScope.launch {
    MonetizeKit.events.collect { event ->
        // NetworkInitialized, AdLoaded, AdImpression, AdClicked, AdPaid,
        // AdRewardEarned, AdPreloadRetryScheduled, AdShowCapped, AdsAccessChanged,
        // AdPreloaded, AdShown або failure-події.
    }
}
```

`AdsEvent.AdPaid` надає currency, micros і SDK precision для format-ів, SDK яких повідомляє
impression-level paid value. `AdsEvent.AdLoaded` та `AdsError` можуть містити безпечні response
diagnostics: наприклад AdMob response ID і mediation adapter class. Не використовуйте ці значення
для відтворення чи логування ad creative.

### Дохід від реклами у Firebase / Google Analytics

Залишайте Firebase-інтеграцію у **host-застосунку**, а не в MonetizeKit. Firebase є необов'язковим,
саме host володіє Firebase project і політикою Analytics consent, а ту саму нормалізовану подію
можна також надіслати в MMP чи backend. MonetizeKit навмисно не має Firebase dependency.

Для кожного AdMob impression оберіть рівно один шлях звітування:

| Шлях доставки | Налаштування Firebase revenue |
| --- | --- |
| Direct AdMob або AdMob Mediation (зокрема Meta/Unity через AdMob) | Зв'яжіть AdMob app із Firebase/Google Analytics. Firebase автоматично записує `ad_impression`, зокрема mediation revenue. **Не** пересилайте `AdsEvent.AdPaid` вручну — дохід буде продубльований. |
| Direct Meta або direct Unity у цій версії | Не створюйте дохід зі значення eCPM або з `AdsEvent.AdImpression`. Їх direct adapters зараз не емітять `AdsEvent.AdPaid`, тому точний client-side Firebase revenue event недоступний. Використовуйте звіти мережі, mediation через AdMob або інтеграцію, що надає impression-level revenue. |
| Adapter, що емітить справжній `AdsEvent.AdPaid` і не звітується Firebase автоматично | Пересилайте цю подію із застосунку, як показано нижче. |

Для автоматичного AdMob-звітування виконайте [інструкцію Firebase зі зв'язування AdMob](https://firebase.google.com/docs/analytics/measure-ad-revenue#automatically-track-ad-revenue).
Для manual reporter додайте Firebase Analytics до застосунку через актуальний Firebase BoM:

```kotlin
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:<current-version>"))
    implementation("com.google.firebase:firebase-analytics")
}
```

Встановлюйте collector один раз в application-owned scope лише тоді, коли підходить третій рядок
таблиці. Він перетворює micros на `Double` в одиницях валюти, яких потребує Firebase. Спершу
викличте `FirebaseAnalytics.setConsent(...)`, якщо цього потребує ваша Analytics-consent policy:
UMP consent, яким керує MonetizeKit, не замінює налаштування Firebase Analytics consent.

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

Ніколи не використовуйте manual collector разом з автоматичною інтеграцією AdMob-to-Firebase.
Перевіряйте manual events у DebugView; для ad-revenue Firebase потребує числовий `value` та
`currency` за ISO-4217.

## Debug sample

Модуль `:app` — це runnable playground для debug. Він використовує офіційні Google demo App ID та
ad unit ID для banner, native, interstitial і rewarded; ці значення існують лише у source set
`debug`. Release-variant не ініціалізує MonetizeKit і не містить AdMob App ID.

```shell
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

Sample показує SDK/UMP state, Compose banner/native slots із зарезервованою висотою та можливістю
reload, явний показ fullscreen-реклами й кнопку **Open Ad Inspector**. Ad Inspector діагностує
Google Mobile Ads і AdMob Mediation, але не перевіряє запити, які напряму виконує Meta або Unity.
Для них у sample є окремі direct placements і кнопки, тому їхні test-запити не змішуються з
AdMob Mediation.

Щоб перевірити direct Meta або Unity, заповніть лише потрібні формати в `DebugMetaIds` і
`DebugUnityIds` у [`SampleApplication.kt`](app/src/main/kotlin/ua/lujek/monetizekit/sample/SampleApplication.kt).
Порожній ID залишає відповідну кнопку вимкненою. Додайте Meta test-device hash перед запитами Meta;
debug sample вмикає Unity SDK test mode, але test mode також слід увімкнути для Android-застосунку
в Unity dashboard. Ці адаптери та inspector існують лише у debug variant sample.

Кнопки **Privacy test profile** зберігають обраний профіль і перезапускають debug process, щоб policy
було застосовано до ініціалізації мереж. Є стандартний UMP, примусовий UMP EEA, COPPA, TFUA, US
restricted data processing, US GPP та external-CMP contract. Перед forced UMP geography додайте UMP
test-device hash у `DebugUmpIds`. External-CMP profile — лише інтерактивна перевірка lifecycle
`PrivacyConsentProvider`: він не є сертифікованим CMP і його не можна переносити у production.

## Перевірка перед релізом

### R8 consumer smoke test

`:consumer-r8-smoke` — внутрішній застосунок, який підключає direct adapters AdMob, Meta, Unity
та офіційні Meta/Unity adapters для AdMob Mediation так само, як звичайний host app. Він не має
launcher activity, CI його не встановлює, а public demo/placeholder ID існують лише для того, щоб
consumer-конфігурація залишалася досяжною для R8. CI також перевіряє shrinked APK і зупиняється,
якщо `FacebookMediationAdapter` або `UnityMediationAdapter` не збережено.

```shell
./gradlew :ads-core:testDebugUnitTest :consumer-r8-smoke:assembleRelease
```

### Матриця Android behavior-тестів

Підключіть Android-пристрій або емулятор і виконайте:

```shell
./gradlew :ads-core:connectedDebugAndroidTest
```

Device-suite використовує детерміновані fake adapter-и та consent provider. Він покриває external
consent і Privacy Options flow, створення banner до consent та native після готовності SDK,
передавання inline pause/resume/destroy і збереження історії frequency cap у DataStore. Core
unit-тести додатково перевіряють серіалізацію конкурентних `show()` для одного placement та те,
що retry очікує і виконує наступну фактичну preload-спробу.

`assembleRelease` створює shrinked APK і перевіряє merged adapter provider-и та consumer ProGuard
rules усіх бібліотек. Та сама команда запускається у GitHub Actions для push і pull request.

- Опублікуйте UMP privacy message та список ad partners.
- Використовуйте або `direct { ... }`, або `admobMediation { ... }`, але не обидва режими.
- Додавайте лише Gradle adapters обраного режиму.
- Перевірте кожен placement і порядок direct fallback на реальних пристроях.
- Переконайтеся, що preload генерує одну подію `AdPreloaded`, а `show()` не показує дві fullscreen-реклами підряд.
- Перевірте `AdShowResult.Capped` та `AdPreloadRetryScheduled` для обраної production policy.
- Переконайтеся, що у release немає test ID та Unity test mode.
- Додайте in-app точку входу для privacy options, якщо цього вимагає UMP.
- Якщо billing керує доступом до реклами, перевіряйте та надавайте покупки на захищеному backend
  перед поверненням `Verified`.
- Переконайтеся, що покупка `pro_app` надає всі потрібні entitlement-и, а рекламу вимикає лише
  явно налаштований ad-free entitlement.
