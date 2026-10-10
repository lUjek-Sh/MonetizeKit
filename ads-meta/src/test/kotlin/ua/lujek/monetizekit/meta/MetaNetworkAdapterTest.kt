package ua.lujek.monetizekit.meta

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.lujek.monetizekit.core.AdFormat
import ua.lujek.monetizekit.core.AdsNetwork

class MetaNetworkAdapterTest {
    @Test
    fun declares_only_formats_supported_by_meta_direct() {
        val adapter = MetaNetworkAdapter()

        assertEquals(AdsNetwork.META, adapter.network)
        assertEquals(
            setOf(
                AdFormat.BANNER,
                AdFormat.INTERSTITIAL,
                AdFormat.REWARDED,
                AdFormat.NATIVE,
            ),
            adapter.supportedFormats,
        )
    }
}
