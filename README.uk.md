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
}
```

`reservedHeightDp` є обов'язковим для banner і native. Контейнер зберігає висоту, поки реклама
завантажується, не заповнюється або недоступна, тому контент екрана не стрибає. Висота стає нульовою
лише тоді, коли налаштована access policy явно вимикає рекламу, наприклад після активного paid
entitlement-а.

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
4. Fullscreen кандидат стає лише `Ready`; він показується виключно після явного `show()`.
5. Process-wide mutex дозволяє показувати лише один interstitial або rewarded одночасно.

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

### Власна native-розмітка

Передайте `NativeAdRenderer` під час оголошення native placement. Renderer створює лише візуальну
розмітку; MonetizeKit володіє network-native wrapper, media view, реєстрацією кліків і очищенням.
Кожен виклик `render` має повертати новий View, який ще не прикріплений до parent.

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

`mediaContainer` обов'язковий для Meta native ads. Той самий placement працює в Compose `AdsNative`,
бо він показує зареєстрований native View через `AndroidView`.

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

`show()` завжди викликається явно. Він використає підготовлений creative, якщо він є; інакше
виконає той самий ordered direct fallback або звернеться до mediation ad unit:

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

`AdsEvent.AdPaid` надає currency, micros і SDK precision для підтримуваних network format-ів.
`AdsEvent.AdLoaded` та `AdsError` можуть містити безпечні response diagnostics: наприклад AdMob
response ID і mediation adapter class. Не використовуйте ці значення для відтворення чи логування
ad creative.

## Debug sample

Модуль `:app` — це runnable playground для debug. Він використовує офіційні Google demo App ID та
ad unit ID для banner, native, interstitial і rewarded; ці значення існують лише у source set
`debug`. Release-variant не ініціалізує MonetizeKit і не містить AdMob App ID.

```shell
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

Sample показує SDK/UMP state, preload, явний показ fullscreen-реклами, privacy options та Compose
banner/native slots із зарезервованою висотою. Це інструмент перевірки, а не production app code.

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
