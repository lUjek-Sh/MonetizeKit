package ua.lujek.monetizekit.admob

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.lujek.monetizekit.core.AdFormat
import ua.lujek.monetizekit.core.AdsNetwork

class AdMobNetworkAdapterTest {
    @Test
    fun declares_every_format_supported_by_the_direct_adapter() {
        val adapter = AdMobNetworkAdapter()

        assertEquals(AdsNetwork.ADMOB, adapter.network)
        assertEquals(
            setOf(
                AdFormat.BANNER,
                AdFormat.INTERSTITIAL,
                AdFormat.REWARDED,
                AdFormat.REWARDED_INTERSTITIAL,
                AdFormat.NATIVE,
                AdFormat.APP_OPEN,
            ),
            adapter.supportedFormats,
        )
    }
}
