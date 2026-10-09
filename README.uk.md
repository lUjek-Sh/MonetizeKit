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

Підключайте лише адаптери обраного режиму та лише ті формати, які справді потрібні застосунку.

## Підключення

Поки що проєкт є multi-module бібліотекою. Додайте залежності проєкту до
`build.gradle.kts` застосунку.

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
завантажується, не заповнюється або недоступна, тому контент екрана не стрибає.

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

MonetizeKit показує єдиний UMP consent flow при першому resumed activity та не ініціалізує рекламні
адаптери, доки UMP не дозволить запитувати рекламу. Окремих Meta або Unity діалогів немає.

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
bannerSlot.load()

// Викличте release, якщо View не буде автоматично від'єднано.
bannerSlot.release()
```

## Показуйте fullscreen-рекламу

API є suspending. Коли SDK перейшов у `Ready`, завчасно викличте preload fullscreen-placement:
буде завантажено лише першого доступного кандидата у заданому порядку, без показу реклами. Виклики
для одного placement виконуються послідовно. Після успішного `show()` creative витрачається, а бібліотека
автоматично починає готувати наступний після закриття реклами.

```kotlin
lifecycleScope.launch {
    when (val result = MonetizeKit.preload(AppAds.levelComplete)) {
        AdLoadResult.Loaded -> enableLevelCompleteTransition()
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
        // AwaitingGdprConsent, Ready або Failed.
    }
}

lifecycleScope.launch {
    MonetizeKit.events.collect { event ->
        // NetworkInitialized, AdPreloaded, AdShown, AdPreloadFailed або AdShowFailed.
    }
}
```

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

- Опублікуйте UMP privacy message та список ad partners.
- Використовуйте або `direct { ... }`, або `admobMediation { ... }`, але не обидва режими.
- Додавайте лише Gradle adapters обраного режиму.
- Перевірте кожен placement і порядок direct fallback на реальних пристроях.
- Переконайтеся, що preload генерує одну подію `AdPreloaded`, а `show()` не показує дві fullscreen-реклами підряд.
- Переконайтеся, що у release немає test ID та Unity test mode.
- Додайте in-app точку входу для privacy options, якщо цього вимагає UMP.
