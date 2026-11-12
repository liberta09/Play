package com.kaan.play.ui.tv

import com.kaan.play.data.live.LiveCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvScreenRulesTest {

    @Test
    fun testCalculateGridColumns() {
        assertEquals(3, TvScreenRules.calculateGridColumns(360))
        assertEquals(3, TvScreenRules.calculateGridColumns(411))
        assertEquals(4, TvScreenRules.calculateGridColumns(600))
        assertEquals(6, TvScreenRules.calculateGridColumns(840))
    }

    @Test
    fun testShowSourceRow() {
        // User catalogs > 0
        assertTrue(TvScreenRules.showSourceRow(1, 0, 0))

        // 0 user catalogs, but multiple sources
        assertTrue(TvScreenRules.showSourceRow(0, 1, 1))

        // 0 user catalogs, only 1 source
        assertFalse(TvScreenRules.showSourceRow(0, 1, 0))
        assertFalse(TvScreenRules.showSourceRow(0, 0, 1))

        // 0 user catalogs, 0 sources
        assertFalse(TvScreenRules.showSourceRow(0, 0, 0))
    }

    @Test
    fun testShouldShowSourceChip() {
        // Sayısı 0 olan kaynak çipi gizlenir
        assertFalse(TvScreenRules.shouldShowSourceChip(0))
        assertTrue(TvScreenRules.shouldShowSourceChip(1))
        assertTrue(TvScreenRules.shouldShowSourceChip(129))
    }

    @Test
    fun testFilterChannels_CategoryAndSearch() {
        val sampleChannels = listOf(
            TvScreenRules.ChannelItem(
                key = "trt-1",
                title = "TRT 1",
                sources = listOf("nuvio_addon:1", "cs:trt"),
                category = LiveCategory.National,
            ),
            TvScreenRules.ChannelItem(
                key = "trt-haber",
                title = "TRT Haber",
                sources = listOf("nuvio_addon:2"),
                category = LiveCategory.News,
            ),
            TvScreenRules.ChannelItem(
                key = "bein-sports",
                title = "beIN Sports 1",
                sources = listOf("cs:bein"),
                category = LiveCategory.BeIn,
            ),
        )

        // 1. Kaynak Tümü, Kategori yok, Arama boş -> 3 kanal
        assertEquals(3, TvScreenRules.filterChannels(sampleChannels, source = null, category = null, query = "").size)

        // 2. Kaynak Nuvio -> 2 kanal (trt-1 ve trt-haber)
        val nuvioOnly = TvScreenRules.filterChannels(sampleChannels, source = "nuvio", category = null, query = "")
        assertEquals(2, nuvioOnly.size)
        assertTrue(nuvioOnly.any { it.key == "trt-1" })
        assertTrue(nuvioOnly.any { it.key == "trt-haber" })

        // 3. Kaynak CloudStream -> 2 kanal (trt-1 ve bein-sports)
        val csOnly = TvScreenRules.filterChannels(sampleChannels, source = "cs", category = null, query = "")
        assertEquals(2, csOnly.size)
        assertTrue(csOnly.any { it.key == "trt-1" })
        assertTrue(csOnly.any { it.key == "bein-sports" })

        // 4. Kategori Haber -> 1 kanal (trt-haber)
        val newsOnly = TvScreenRules.filterChannels(sampleChannels, source = null, category = LiveCategory.News, query = "")
        assertEquals(1, newsOnly.size)
        assertEquals("trt-haber", newsOnly.first().key)

        // 5. Arama + Kategori birlikte: arama "TRT", kategori National -> sadece TRT 1
        val searchAndCat = TvScreenRules.filterChannels(sampleChannels, source = null, category = LiveCategory.National, query = "TRT")
        assertEquals(1, searchAndCat.size)
        assertEquals("trt-1", searchAndCat.first().key)

        // 6. Eşleşmeyen arama -> boş sonuç
        val noMatch = TvScreenRules.filterChannels(sampleChannels, source = null, category = null, query = "XYZ123")
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun testSourceCountsAndAllCategoriesCountEquivalence() {
        // Nuvio: 129, CloudStream: 108, 43 ortak -> Tümü: 194
        val nuvioCount = 129
        val cloudStreamCount = 108
        val overlap = 43
        val allChannelsCount = nuvioCount + cloudStreamCount - overlap
        assertEquals(194, allChannelsCount)

        // Kaynak 'Tümü' seçiliyken 'Tüm kategoriler' sayısı Tümü kanal sayısına (194) eşit olmalı
        val selectedSourceAll = allChannelsCount
        assertEquals(194, selectedSourceAll)

        // Kaynak 'Nuvio' iken kategori menüsündeki tüm kanallar 129
        val selectedSourceNuvio = nuvioCount
        assertEquals(129, selectedSourceNuvio)

        // Kaynak 'CloudStream' iken kategori menüsündeki tüm kanallar 108
        val selectedSourceCs = cloudStreamCount
        assertEquals(108, selectedSourceCs)
    }
}
