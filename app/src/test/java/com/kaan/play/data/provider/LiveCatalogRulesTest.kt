package com.kaan.play.data.provider

import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun item(id: String, title: String, type: String, mediaType: MediaType = MediaType.Movie) =
        CatalogItem(
            id = id,
            title = title,
            mediaType = mediaType,
            providerId = "test",
            addonType = type,
        )
}
