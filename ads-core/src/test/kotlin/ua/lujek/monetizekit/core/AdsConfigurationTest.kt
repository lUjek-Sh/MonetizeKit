package ua.lujek.monetizekit.core

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsConfigurationTest {
    private val homeBanner = AdsPlacements.banner("home_banner", reservedHeightDp = 50)
    private val levelComplete = AdsPlacements.interstitial("level_complete")
    private val feedNative = AdsPlacements.native("feed_native", reservedHeightDp = 280)
    private val levelBonus = AdsPlacements.rewardedInterstitial("level_bonus")
    private val appReturn = AdsPlacements.appOpen("app_return")

    @Test
    fun `direct configuration supports only the networks and formats used by an app`() {
        val configuration = monetizeConfiguration {
            direct {
                admob {
                    applicationId = "ca-app-pub-123456789~123456789"
                }
                meta()
            }
            banner(homeBanner) {
                meta("META_HOME_BANNER")
                admob("ca-app-pub-123456789/123456789")
            }
        }

        assertEquals(AdsDeliveryMode.DIRECT, configuration.deliveryMode)
        assertEquals(2, configuration.placements.getValue(homeBanner.id).candidates.size)
        assertEquals(AdsNetwork.META, configuration.placements.getValue(homeBanner.id).candidates.first().network)
        assertEquals(AdsNetwork.ADMOB, configuration.placements.getValue(homeBanner.id).candidates.last().network)
        assertNull(configuration.unity)
    }

    @Test
    fun `mediation configuration has one AdMob source per placement`() {
        val configuration = monetizeConfiguration {
            admobMediation {
                applicationId = "ca-app-pub-123456789~123456789"
            }
            native(feedNative) {
                admobMediation("ca-app-pub-123456789/987654321")
            }
        }

        assertEquals(AdsDeliveryMode.ADMOB_MEDIATION, configuration.deliveryMode)
        assertEquals(1, configuration.placements.size)
        assertEquals(AdFormat.NATIVE, configuration.placements.getValue(feedNative.id).placement.format)
        assertEquals(280, feedNative.reservedHeightDp)
    }

    @Test
    fun `mediation Meta test device stays in the mediation configuration`() {
        val configuration = monetizeConfiguration {
            admobMediation {
                applicationId = "ca-app-pub-123456789~123456789"
                mediationTesting(enabled = true) {
                    meta {
                        testDevice("c277a773-197d-42d0-af7d-c0a4ceedf1f0")
                    }
                }
            }
            native(feedNative) {
                admobMediation("ca-app-pub-123456789/987654321")
            }
        }

        assertEquals(
            setOf("c277a773-197d-42d0-af7d-c0a4ceedf1f0"),
            configuration.adMob?.metaMediationTestDeviceHashes,
        )
        assertNull(configuration.meta)
    }

    @Test(expected = IllegalStateException::class)
    fun `direct and mediation cannot be enabled together`() {
        monetizeConfiguration {
            direct {
                meta()
            }
            admobMediation {
                applicationId = "ca-app-pub-123456789~123456789"
            }
            interstitial(levelComplete) {
                meta("META_INTERSTITIAL")
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mediation placement cannot contain direct source`() {
        monetizeConfiguration {
            admobMediation {
                applicationId = "ca-app-pub-123456789~123456789"
            }
            banner(homeBanner) {
                admob("ca-app-pub-123456789/123456789")
            }
        }
    }

    @Test
    fun `test settings stay empty when disabled`() {
        val configuration = monetizeConfiguration {
            direct {
                admob {
                    applicationId = "ca-app-pub-123456789~123456789"
                    testing(enabled = false) {
                        testDevice("AD_MOB_TEST_DEVICE")
                    }
                }
            }
            interstitial(levelComplete) {
                admob("ca-app-pub-123456789/123456789")
            }
        }

        assertTrue(configuration.adMob?.testDeviceIds.orEmpty().isEmpty())
        assertFalse(configuration.gdpr.debug != null)
    }

    @Test
    fun `mediation partner test settings stay empty when disabled`() {
        val configuration = monetizeConfiguration {
            admobMediation {
                applicationId = "ca-app-pub-123456789~123456789"
                mediationTesting(enabled = false) {
                    meta {
                        testDevice("c277a773-197d-42d0-af7d-c0a4ceedf1f0")
                    }
                }
            }
            banner(homeBanner) {
                admobMediation("ca-app-pub-123456789/987654321")
            }
        }

        assertTrue(configuration.adMob?.metaMediationTestDeviceHashes.orEmpty().isEmpty())
    }

    @Test
    fun `native appearance is retained by configuration`() {
        val appearance = NativeAdAppearance(
            cardBackgroundColor = 0xFF112233.toInt(),
            callToActionColor = 0xFF445566.toInt(),
            cardCornerRadiusDp = 24,
        )

        val configuration = monetizeConfiguration {
            nativeAppearance(appearance)
            direct {
                meta()
            }
            native(feedNative) {
                meta("META_FEED_NATIVE")
            }
        }

        assertSame(appearance, configuration.nativeAdAppearance)
    }

    @Test
    fun `native appearance reserves enough vertical space for selected built-in assets`() {
        val appearance = NativeAdAppearance(
            mediaHeightDp = 180,
            headlineMaxLines = 2,
            showAdvertiser = true,
            showCallToAction = true,
        )

        assertEquals(448, appearance.minimumReservedHeightDp)
        assertEquals(
            468,
            appearance.copy(mediaHeightDp = 200).minimumReservedHeightDp,
        )
        assertEquals(487, appearance.minimumReservedHeightDp(fontScale = 1.3f))
        assertEquals(311, NativeAdAppearance().minimumReservedHeightDp)
        assertEquals(331, NativeAdAppearance().minimumReservedHeightDp(fontScale = 1.3f))
    }

    @Test
    fun `compact native appearance reserves space only for visible assets`() {
        val fullCard = NativeAdAppearance(
            mediaHeightDp = 180,
            headlineMaxLines = 2,
            showAdvertiser = true,
            showCallToAction = true,
        )
        val compactCard = NativeAdAppearance()

        assertTrue(compactCard.minimumReservedHeightDp < fullCard.minimumReservedHeightDp)
        assertEquals(311, compactCard.minimumReservedHeightDp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `native minimum reservation rejects invalid font scale`() {
        NativeAdAppearance().minimumReservedHeightDp(fontScale = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `native appearance rejects an empty headline`() {
        NativeAdAppearance(headlineMaxLines = 0)
    }

    @Test
    fun `banner appearance is retained by configuration`() {
        val appearance = BannerAppearance(
            frameBackgroundColor = 0xFF112233.toInt(),
            frameStrokeColor = 0xFF445566.toInt(),
            contentPaddingDp = 6,
            cornerRadiusDp = 18,
        )

        val configuration = monetizeConfiguration {
            bannerAppearance(appearance)
            direct {
                meta()
            }
            banner(homeBanner) {
                meta("META_HOME_BANNER")
            }
        }

        assertSame(appearance, configuration.bannerAppearance)
    }

    @Test
    fun `ads access is application owned and optional`() {
        val accessProvider = object : AdsAccessProvider {
            override val access = MutableStateFlow<AdsAccess>(AdsAccess.Suppressed)
        }

        val configuration = monetizeConfiguration {
            adsAccess(accessProvider)
            direct {
                meta()
            }
            interstitial(levelComplete) {
                meta("META_INTERSTITIAL")
            }
        }

        assertSame(accessProvider, configuration.adsAccessProvider)
        assertEquals(AdsAccess.Suppressed, configuration.adsAccessProvider.access.value)
        assertEquals(AdsAccess.Allowed, AdsAccessProviders.alwaysAllowed.access.value)
    }

    @Test
    fun `privacy configuration keeps age and US settings with one external consent provider`() {
        val externalProvider = TestConsentProvider()
        val fallbackUmpProvider = TestConsentProvider()

        val configuration = monetizeConfiguration {
            privacy {
                ageRestrictedTreatment(
                    childDirected = true,
                    underAgeOfConsent = true,
                )
                usStatePrivacy(UsStatePrivacyMode.GLOBAL_PRIVACY_PLATFORM)
                externalConsent(externalProvider)
            }
            direct {
                meta()
            }
            interstitial(levelComplete) {
                meta("META_INTERSTITIAL")
            }
        }

        assertEquals(true, configuration.gdpr.childDirectedTreatment)
        assertEquals(true, configuration.gdpr.underAgeOfConsent)
        assertEquals(UsStatePrivacyMode.GLOBAL_PRIVACY_PLATFORM, configuration.gdpr.usStatePrivacyMode)
        assertSame(externalProvider, selectPrivacyConsentProvider(configuration.gdpr, fallbackUmpProvider))
    }

    @Test
    fun `banner placement keeps adaptive and collapsible presentation requirements`() {
        val anchored = AdsPlacements.banner(
            id = "anchored_banner",
            reservedHeightDp = 90,
            size = BannerSize.AnchoredAdaptive,
            collapsibleAnchor = CollapsibleBannerAnchor.BOTTOM,
        )
        val inline = AdsPlacements.banner(
            id = "inline_banner",
            reservedHeightDp = 250,
            size = BannerSize.InlineAdaptive(maxHeightDp = 250),
        )

        assertEquals(BannerSize.AnchoredAdaptive, anchored.size)
        assertEquals(CollapsibleBannerAnchor.BOTTOM, anchored.collapsibleAnchor)
        assertEquals(BannerSize.InlineAdaptive(maxHeightDp = 250), inline.size)
    }

    @Test
    fun `rewarded interstitial and App Open keep their typed configuration`() {
        val policy = AppOpenPlacementPolicy(
            showOnAppForeground = false,
            showOnColdStart = true,
            fullscreenPolicy = FullscreenPlacementPolicy(
                frequencyCap = FullscreenFrequencyCap(cooldownMillis = 60_000L),
            ),
        )
        val configuration = monetizeConfiguration {
            direct {
                admob {
                    applicationId = "ca-app-pub-123456789~123456789"
                }
            }
            rewardedInterstitial(levelBonus) {
                admob("ca-app-pub-123456789/111111111")
            }
            appOpen(appReturn, policy) {
                admob("ca-app-pub-123456789/222222222")
            }
        }

        assertEquals(AdFormat.REWARDED_INTERSTITIAL, levelBonus.format)
        assertEquals(AdFormat.APP_OPEN, appReturn.format)
        assertSame(policy, configuration.placements.getValue(appReturn.id).appOpenPolicy)
        assertEquals(
            policy.fullscreenPolicy,
            configuration.placements.getValue(appReturn.id).fullscreenPolicy,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `only one App Open placement can present automatically`() {
        val secondAppOpen = AdsPlacements.appOpen("second_app_return")
        monetizeConfiguration {
            direct {
                admob {
                    applicationId = "ca-app-pub-123456789~123456789"
                }
            }
            appOpen(appReturn) {
                admob("ca-app-pub-123456789/111111111")
            }
            appOpen(secondAppOpen) {
                admob("ca-app-pub-123456789/222222222")
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inline adaptive banners cannot be collapsible`() {
        AdsPlacements.banner(
            id = "invalid_banner",
            reservedHeightDp = 250,
            size = BannerSize.InlineAdaptive(maxHeightDp = 250),
            collapsibleAnchor = CollapsibleBannerAnchor.TOP,
        )
    }

    private class TestConsentProvider : PrivacyConsentProvider {
        override suspend fun gatherConsent(activity: android.app.Activity): ConsentSnapshot =
            error("Not used in configuration tests.")

        override suspend fun showPrivacyOptions(activity: android.app.Activity): ConsentSnapshot =
            error("Not used in configuration tests.")
    }
}
