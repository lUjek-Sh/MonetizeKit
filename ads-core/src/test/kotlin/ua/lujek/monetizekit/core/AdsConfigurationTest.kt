package ua.lujek.monetizekit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsConfigurationTest {
    private val homeBanner = AdsPlacements.banner("home_banner", reservedHeightDp = 50)
    private val levelComplete = AdsPlacements.interstitial("level_complete")
    private val feedNative = AdsPlacements.native("feed_native", reservedHeightDp = 280)

    @Test
    fun `banner-only configuration does not require fullscreen placements or networks`() {
        val configuration = monetizeConfiguration {
            admob {
                applicationId = "ca-app-pub-123456789~123456789"
            }
            banner(homeBanner) {
                admob("ca-app-pub-123456789/123456789")
            }
        }

        assertEquals(1, configuration.placements.size)
        assertTrue(configuration.placements.containsKey(homeBanner.id))
        assertEquals(AdsNetwork.ADMOB, configuration.placements.getValue(homeBanner.id).waterfall.single().network)
    }

    @Test
    fun `each placement keeps its requested waterfall order`() {
        val configuration = monetizeConfiguration {
            admob {
                applicationId = "ca-app-pub-123456789~123456789"
            }
            meta()
            unity {
                gameId = "1234567"
                isTestMode = true
            }
            interstitial(levelComplete) {
                admob("ca-app-pub-123456789/111111111")
                meta("META_PLACEMENT")
                unity("Unity_Interstitial")
            }
        }

        assertEquals(
            listOf(AdsNetwork.ADMOB, AdsNetwork.META, AdsNetwork.UNITY),
            configuration.placements.getValue(levelComplete.id).waterfall.map(NetworkPlacement::network),
        )
    }

    @Test
    fun `native-only configuration does not require banner or fullscreen formats`() {
        val configuration = monetizeConfiguration {
            meta()
            native(feedNative) {
                meta("META_NATIVE_PLACEMENT")
            }
        }

        assertEquals(1, configuration.placements.size)
        assertEquals(AdFormat.NATIVE, configuration.placements.getValue(feedNative.id).placement.format)
        assertEquals(280, feedNative.reservedHeightDp)
    }
}
