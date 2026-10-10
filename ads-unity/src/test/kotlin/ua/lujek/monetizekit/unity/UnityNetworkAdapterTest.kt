package ua.lujek.monetizekit.unity

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.lujek.monetizekit.core.AdFormat
import ua.lujek.monetizekit.core.AdsNetwork

class UnityNetworkAdapterTest {
    @Test
    fun declares_only_formats_supported_by_unity_direct() {
        val adapter = UnityNetworkAdapter()

        assertEquals(AdsNetwork.UNITY, adapter.network)
        assertEquals(
            setOf(
                AdFormat.BANNER,
                AdFormat.INTERSTITIAL,
                AdFormat.REWARDED,
            ),
            adapter.supportedFormats,
        )
    }
}
