package com.kaan.play.data.provider

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCatalogRulesTest {
    @Test
    fun keepsExplicitTvAndChannelItemsEvenWhenSectionTitleIsGeneric() {
        val row = CatalogRow(
            id = "homepage_0",
            title = "Ana Sayfa",
            providerId = "cs:4",
            providerName = "Live plugin",
            items = listOf(
                item("live-1", "Spor TV", "TV"),
                item("live-2", "Haber", "channel"),
                item("series-1", "Örnek Dizi", "series", MediaType.Tv),
                item("movie-1", "Film", "movie"),
            ),
        )

        val filtered = LiveCatalogRules.onlyLiveRows(listOf(row))

        assertEquals(1, filtered.size)
        assertEquals(listOf("live-1", "live-2"), filtered.single().items.map { it.id })
    }

    @Test
    fun dropsLiveNamedSectionsWhenItemsAreNotExplicitlyLive() {
        val row = CatalogRow(
            id = "live_sports_0",
            title = "Canlı Spor",
            providerId = "nuvio_addon:8",
            providerName = "Sports addon",
            items = listOf(item("series", "Dizi", "series", MediaType.Tv)),
        )

        assertTrue(LiveCatalogRules.onlyLiveRows(listOf(row)).isEmpty())
        assertTrue(LiveCatalogRules.isLiveType("TV"))
        assertTrue(LiveCatalogRules.isLiveType(" Channel "))
        assertFalse(LiveCatalogRules.isLiveType("series"))
        assertTrue(LiveCatalogRules.isLiveCatalog("movie", "Live Channels", "channels"))
        assertTrue(LiveCatalogRules.isLiveCatalog("tv", null, "top"))
        assertFalse(LiveCatalogRules.isLiveType("series"))
    }

    @Test
    fun recognizesUserConfirmedChannelNamesEvenWhenPluginReportsMovieType() {
        val names = listOf(
            "0 WioSpor", "WioSpor", "Anthology - Canlı TV", "AlbaniaLive", "ArdaSpor", "AslanTV", "AtomSpor",
            "BasketballReplays", "BasketballVideo", "BCSports", "CanliTv", "CanliTV", "Dexter", "DomatesTV",
            "DominoTV", "GolgeTV", "InterSporTV", "JestYayin", "KacakSpor", "KoalaTV", "Kool TV", "KralSporHD",
            "LeoTV", "MacKeyfi", "MahsunSports", "Mien TV", "MRC-Live", "myway TV - Latte",
        )

        names.forEach { assertTrue("Expected known live channel: $it", LiveCatalogRules.isKnownLiveChannel(it)) }
        assertFalse(LiveCatalogRules.isKnownLiveChannel("Popular Movies"))
        listOf("BronzeCloud", "TurkSpor", "WioSpor", "BTVault", "Latte - Sinetech. TR")
            .forEach { assertTrue("Expected known live source: $it", LiveCatalogRules.isKnownLiveProvider(it)) }
        assertFalse(LiveCatalogRules.isKnownLiveChannel("BronzeCloud"))

        val row = CatalogRow(
            id = "mislabelled",
            title = "Ana Sayfa",
            providerId = "cs:4",
            providerName = "Test plugin",
            items = listOf(
                item("live", "ArdaSpor", "movie"),
                item("movie", "Unrelated film", "movie"),
            ),
        )
        assertEquals(listOf("live"), LiveCatalogRules.onlyLiveRows(listOf(row)).single().items.map { it.id })
    }

    @Test
    fun channelGroupKeyNormalizesAndMatchesCanonicalPrefixes() {
        assertEquals(LiveCatalogRules.channelGroupKey("TRT 1"), LiveCatalogRules.channelGroupKey("TRT 1 HD"))
        assertEquals(LiveCatalogRules.channelGroupKey("kanal d"), LiveCatalogRules.channelGroupKey("Kanal D FHD"))
        assertEquals(LiveCatalogRules.channelGroupKey("bein sports 1"), LiveCatalogRules.channelGroupKey("beIN Sports 1 HD"))
        assertNotEquals(LiveCatalogRules.channelGroupKey("bein sports 1"), LiveCatalogRules.channelGroupKey("bein sports 2"))
        assertNotEquals(LiveCatalogRules.channelGroupKey("TRT 1"), LiveCatalogRules.channelGroupKey("TRT 2"))

        // Uzun önek önceliği ("trt spor yildiz" > "trt spor") ve Türkçe karakterler
        assertEquals("trtsporyildiz", LiveCatalogRules.channelGroupKey("TRT Spor Yıldız 1080p"))
        assertEquals("trtspor", LiveCatalogRules.channelGroupKey("TRT Spor 4K"))
        assertEquals("beinsports1", LiveCatalogRules.channelGroupKey("beIN Sports 1 UHD"))
        assertEquals("powerturk", LiveCatalogRules.channelGroupKey("Power Türk 720p"))
        assertEquals("trtmuzik", LiveCatalogRules.channelGroupKey("TRT Müzik HD"))
        assertEquals("customsporttv", LiveCatalogRules.channelGroupKey("Custom Sport TV 576p"))
    }

    @Test
    fun routeForEnforcesProviderAndFieldRules() {
        val nuvio = CatalogItem(
            id = " ch_1 ",
            title = " TRT 1 ",
            mediaType = MediaType.Tv,
            providerId = " nuvio_addon:5 ",
            rawUrl = "https://ignored.example/raw",
            addonType = "CHANNEL",
        )
        assertEquals(
            LiveSourceRoute(pluginId = "nuvio_addon:5", addonType = "channel", contentId = "ch_1", dataUrl = null),
            LiveCatalogRules.routeFor(nuvio),
        )

        val nuvioInvalidTypeFallsBackToTv = nuvio.copy(addonType = "movie")
        assertEquals("tv", LiveCatalogRules.routeFor(nuvioInvalidTypeFallsBackToTv)?.addonType)

        val cs = CatalogItem(
            id = "ignored_id",
            title = "Kanal D",
            mediaType = MediaType.Tv,
            providerId = "cs:12",
            rawUrl = " https://cs.example/kanald ",
            addonType = null,
        )
        assertEquals(
            LiveSourceRoute(
                pluginId = "cs:12",
                addonType = "tv",
                contentId = "https://cs.example/kanald",
                dataUrl = "https://cs.example/kanald",
            ),
            LiveCatalogRules.routeFor(cs),
        )

        assertNull(LiveCatalogRules.routeFor(nuvio.copy(providerId = "nuvio_addon:0")))
        assertNull(LiveCatalogRules.routeFor(nuvio.copy(providerId = "nuvio_addon:-3")))
        assertNull(LiveCatalogRules.routeFor(nuvio.copy(id = "  ")))
        assertNull(LiveCatalogRules.routeFor(cs.copy(rawUrl = "  ")))
        assertNull(LiveCatalogRules.routeFor(cs.copy(rawUrl = null)))
        assertNull(LiveCatalogRules.routeFor(cs.copy(title = "  ")))
        assertNull(LiveCatalogRules.routeFor(cs.copy(providerId = "unknown:1")))
    }

    @Test
    fun isLiveRequestValidatesAllFields() {
        val valid = StreamRequest(
            tmdbId = 0,
            imdbId = null,
            type = MediaType.Tv,
            pluginId = "nuvio_addon:1",
            addonType = "TV",
            addonContentId = "ch1",
        )
        assertTrue(LiveCatalogRules.isLiveRequest(valid))
        assertTrue(LiveCatalogRules.isLiveRequest(valid.copy(tmdbId = -5, addonType = "Channel")))
        assertFalse(LiveCatalogRules.isLiveRequest(valid.copy(tmdbId = 1)))
        assertFalse(LiveCatalogRules.isLiveRequest(valid.copy(type = MediaType.Movie)))
        assertFalse(LiveCatalogRules.isLiveRequest(valid.copy(addonType = "series")))
        assertFalse(LiveCatalogRules.isLiveRequest(valid.copy(addonType = null)))
        assertFalse(LiveCatalogRules.isLiveRequest(valid.copy(pluginId = "  ")))
        assertFalse(LiveCatalogRules.isLiveRequest(valid.copy(pluginId = null)))
    }

    @Test
    fun mergeChannelsCombinesSameGroupKeyAndPicksFirstTitleAndNonBlankLogo() {
        val fromNuvio = CatalogItem(
            id = "n_trt1",
            title = "TRT 1 HD",
            posterUrl = "  ",
            mediaType = MediaType.Tv,
            providerId = "nuvio_addon:1",
            addonType = "tv",
        )
        val fromCs = CatalogItem(
            id = "cs_trt1",
            title = "TRT 1 FHD",
            posterUrl = "https://img.example/trt1.png",
            mediaType = MediaType.Tv,
            providerId = "cs:2",
            rawUrl = "https://cs.example/trt1",
            addonType = "channel",
        )
        val trt2 = CatalogItem(
            id = "n_trt2",
            title = "TRT 2",
            posterUrl = null,
            mediaType = MediaType.Tv,
            providerId = "nuvio_addon:1",
            addonType = "tv",
        )

        val merged = LiveCatalogRules.mergeChannels(listOf(fromNuvio, fromCs, trt2))

        assertEquals(2, merged.size)
        val ch1 = merged[0]
        assertEquals("trt1", ch1.channelKey)
        assertEquals("TRT 1 HD", ch1.title)
        assertEquals("https://img.example/trt1.png", ch1.logoUrl)
        assertEquals(2, ch1.routes.size)
        assertEquals("nuvio_addon:1", ch1.routes[0].pluginId)
        assertEquals("cs:2", ch1.routes[1].pluginId)
        assertEquals("trt2", merged[1].channelKey)
    }

    private fun item(id: String, title: String, type: String, mediaType: MediaType = MediaType.Movie) =
        CatalogItem(
            id = id,
            title = title,
            mediaType = mediaType,
            providerId = "test",
            addonType = type,
        )
}
