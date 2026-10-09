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
}
