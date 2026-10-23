package com.kaan.play.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderRouteCodecTest {

    @Test
    fun roundtripStandardIds() {
        assertEquals("cs:12", ProviderRouteCodec.decode(ProviderRouteCodec.encode("cs:12")))
        assertEquals("nuvio_addon:5", ProviderRouteCodec.decode(ProviderRouteCodec.encode("nuvio_addon:5")))
    }

    @Test
    fun roundtripWithUnderscoresAndColons() {
        val id1 = "my_custom_provider:123"
        assertEquals(id1, ProviderRouteCodec.decode(ProviderRouteCodec.encode(id1)))

        val id2 = "nuvio_addon:wio_spor_tv:1"
        assertEquals(id2, ProviderRouteCodec.decode(ProviderRouteCodec.encode(id2)))

        val id3 = "cs:repo_name_with_underscores:99"
        assertEquals(id3, ProviderRouteCodec.decode(ProviderRouteCodec.encode(id3)))
    }

    @Test
    fun roundtripEmptyAndPlainIds() {
        assertEquals("", ProviderRouteCodec.decode(ProviderRouteCodec.encode("")))
        assertEquals("simple_id", ProviderRouteCodec.decode(ProviderRouteCodec.encode("simple_id")))
        assertEquals("plain", ProviderRouteCodec.decode(ProviderRouteCodec.encode("plain")))
    }

    @Test
    fun roundtripTildeEscaping() {
        val withTildes = "addon~name:1"
        assertEquals(withTildes, ProviderRouteCodec.decode(ProviderRouteCodec.encode(withTildes)))
    }

    @Test
    fun legacyCompatibility() {
        assertEquals("cs:12", ProviderRouteCodec.decode("cs_12"))
        assertEquals("nuvio_addon:5", ProviderRouteCodec.decode("nuvio_addon_5"))
        assertEquals("unknown_id", ProviderRouteCodec.decode("unknown_id"))
        assertEquals("cs:already_colon:1", ProviderRouteCodec.decode("cs:already_colon:1"))
    }
}
