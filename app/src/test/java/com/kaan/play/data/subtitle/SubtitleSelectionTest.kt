package com.kaan.play.data.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSelectionTest {

    private fun ext(url: String, lang: String?, origin: String = "Addon") =
        SubtitleItem.External(SubtitleTrack(url, SubtitleLanguages.normalize(lang), origin))

    private fun name(code: String) = SubtitleLanguages.displayName(code, "tr")

    // ---- diller ----

    @Test
    fun normalizesIsoCodesNamesAndRegions() {
        assertEquals("tr", SubtitleLanguages.normalize("tur"))
        assertEquals("tr", SubtitleLanguages.normalize("tr"))
        assertEquals("tr", SubtitleLanguages.normalize("TR-tr"))
        assertEquals("en", SubtitleLanguages.normalize("eng"))
        assertEquals("pt", SubtitleLanguages.normalize("pt-BR"))
        assertEquals("pt", SubtitleLanguages.normalize("pob"))
        assertEquals("fr", SubtitleLanguages.normalize("fre")) // 639-2/B
        assertEquals("fr", SubtitleLanguages.normalize("fra")) // 639-2/T
        assertEquals("de", SubtitleLanguages.normalize("ger"))
        assertEquals("zh", SubtitleLanguages.normalize("chi"))
        assertEquals("tr", SubtitleLanguages.normalize("Turkish"))
        assertEquals("es", SubtitleLanguages.normalize("spa"))
        assertNull(SubtitleLanguages.normalize(null))
        assertNull(SubtitleLanguages.normalize(""))
        assertNull(SubtitleLanguages.normalize("und"))
        assertNull(SubtitleLanguages.normalize("zzz"))
        assertNull(SubtitleLanguages.normalize("not a language"))
    }

    @Test
    fun displayNamesFollowTheUiLanguage() {
        assertEquals("Türkçe", SubtitleLanguages.displayName("tr", "tr"))
        assertEquals("İngilizce", SubtitleLanguages.displayName("en", "tr"))
        assertEquals("Turkish", SubtitleLanguages.displayName("tr", "en"))
        assertEquals("English", SubtitleLanguages.displayName("en", "en"))
        assertEquals("Almanca", SubtitleLanguages.displayName("de", "tr"))
    }

    // ---- gruplama ----

    @Test
    fun dedupeKeepsTheFirstOfEachUrlAndNeverDropsEmbedded() {
        val a = ext("https://s/a.srt", "tur", "Akış")
        val dup = ext("https://s/a.srt", "tur", "Addon")
        val items = listOf(SubtitleItem.Embedded(0, "en"), SubtitleItem.Embedded(1, "en"), a, dup, ext("https://s/b.srt", "eng"))
        val result = SubtitleSelection.dedupe(items)
        assertEquals(4, result.size)
        assertEquals("Akış", (result[2] as SubtitleItem.External).track.origin)
    }

    @Test
    fun groupsOrderPreferredLanguagesFirstThenAlphabeticalThenUnknown() {
        val items = listOf(
            ext("https://s/1", "ger"), ext("https://s/2", "eng", "A"), ext("https://s/3", null),
            ext("https://s/4", "tur", "B"), ext("https://s/5", "fre"), ext("https://s/6", "eng", "C"), ext("https://s/7", "tur", "D"),
            ext("https://s/8", "ara"),
        )
        val groups = SubtitleSelection.groups(items, preferred = listOf("tr", "en"), nameOf = ::name, unknownName = "Diğer")
        assertEquals(listOf("tr", "en", "de", "ar", "fr", null), groups.map { it.language })
        assertEquals(listOf("Türkçe", "İngilizce", "Almanca", "Arapça", "Fransızca", "Diğer"), groups.map { it.name })
        // Grup içi sıra giriş sırasıdır.
        assertEquals(listOf("B", "D"), groups[0].items.map { (it as SubtitleItem.External).track.origin })
        assertEquals(listOf("A", "C"), groups[1].items.map { (it as SubtitleItem.External).track.origin })
    }

    @Test
    fun groupsCollapseDuplicateUrlsAcrossSources() {
        val items = listOf(ext("https://s/x.srt", "tur", "Akış"), ext("https://s/x.srt", "tur", "Addon"))
        val groups = SubtitleSelection.groups(items, listOf("tr"), ::name, "Diğer")
        assertEquals(1, groups.single().items.size)
    }

    // ---- varsayılan seçim ----

    @Test
    fun preferredLanguagesAreRememberedThenAppThenEnglish() {
        assertEquals(listOf("tr", "en"), SubtitleSelection.preferredLanguages(null, "tr"))
        assertEquals(listOf("en"), SubtitleSelection.preferredLanguages(null, "en"))
        assertEquals(listOf("de", "tr", "en"), SubtitleSelection.preferredLanguages("de", "tr"))
        assertEquals(listOf("en", "tr"), SubtitleSelection.preferredLanguages("en", "tr"))
        assertTrue(SubtitleSelection.preferredLanguages(SubtitleSelection.REMEMBERED_OFF, "tr").isEmpty())
    }

    @Test
    fun defaultPicksAppLanguageThenEnglishThenNothing() {
        val en = ext("https://s/en", "eng")
        val tr = ext("https://s/tr", "tur")
        val fr = ext("https://s/fr", "fre")
        assertEquals(tr, SubtitleSelection.pickDefault(listOf(en, fr, tr), listOf("tr", "en")))
        assertEquals(en, SubtitleSelection.pickDefault(listOf(fr, en), listOf("tr", "en")))
        assertNull(SubtitleSelection.pickDefault(listOf(fr), listOf("tr", "en"))) // uygun dil yok: kapalı kalır
        assertNull(SubtitleSelection.pickDefault(listOf(tr, en), emptyList())) // kullanıcı kapatmıştı
        assertNull(SubtitleSelection.pickDefault(listOf(ext("https://s/?", null)), listOf("tr", "en")))
    }

    @Test
    fun rememberedSeriesLanguageBeatsAppLanguageAndEmbeddedBeatsAddonsInTheSameLanguage() {
        val de = ext("https://s/de", "ger")
        val tr = ext("https://s/tr", "tur")
        val embeddedTr = SubtitleItem.Embedded(0, "tr")
        val prefs = SubtitleSelection.preferredLanguages("de", "tr")
        assertEquals(de, SubtitleSelection.pickDefault(listOf(tr, de), prefs))
        // Hatırlanan dil bu bölümde yoksa uygulama diline düşer.
        assertEquals(tr, SubtitleSelection.pickDefault(listOf(tr), prefs))
        // Aynı dilde liste sırası kazanır: videonun içindeki, addon'dan önce gelir.
        assertEquals(embeddedTr, SubtitleSelection.pickDefault(listOf(embeddedTr, tr), listOf("tr", "en")))
    }

    @Test
    fun rankOrdersByPreference() {
        val prefs = listOf("tr", "en")
        assertEquals(0, SubtitleSelection.rank("tr", prefs))
        assertEquals(1, SubtitleSelection.rank("en", prefs))
        assertNull(SubtitleSelection.rank("de", prefs))
        assertNull(SubtitleSelection.rank(null, prefs))
    }

    // ---- zaman çizgisi ve senkron ----

    private val timeline = SubtitleTimeline(
        listOf(
            SubtitleCue(1_000, 3_000, "A"),
            SubtitleCue(2_500, 4_000, "B"), // A ile kısa süre üst üste biner
            SubtitleCue(10_000, 12_000, "C"),
        ),
    )

    @Test
    fun activeCuesFollowThePositionIncludingOverlaps() {
        assertTrue(timeline.activeAt(500).isEmpty())
        assertEquals(listOf("A"), timeline.activeAt(1_000).map { it.text }) // başlangıç dahil
        assertEquals(listOf("A", "B"), timeline.activeAt(2_700).map { it.text })
        assertEquals(listOf("B"), timeline.activeAt(3_000).map { it.text }) // bitiş hariç
        assertTrue(timeline.activeAt(5_000).isEmpty())
        assertEquals(listOf("C"), timeline.activeAt(11_999).map { it.text })
        assertTrue(timeline.activeAt(12_000).isEmpty())
        assertTrue(SubtitleTimeline(emptyList()).activeAt(1_000).isEmpty())
    }

    @Test
    fun positiveOffsetDelaysAndNegativeOffsetAdvancesTheSubtitles() {
        // +500 ms: A, oynatma 1500'de görünür; 1000'de henüz görünmez.
        assertTrue(timeline.activeAt(1_000, 500).isEmpty())
        assertEquals(listOf("A"), timeline.activeAt(1_500, 500).map { it.text })
        assertEquals(listOf("A", "B"), timeline.activeAt(3_499, 500).map { it.text }) // t=2999: B de başlamış
        assertTrue(timeline.activeAt(3_500, 500).filter { it.text == "A" }.isEmpty())
        // -500 ms: A, oynatma 500'de görünür.
        assertEquals(listOf("A"), timeline.activeAt(500, -500).map { it.text })
        assertTrue(timeline.activeAt(2_500, -500).none { it.text == "A" }) // A artık 2500'de bitmiş
        // Satır süreleri değişmez, yalnızca kayar.
        assertEquals(timeline.activeAt(11_000).map { it.text }, timeline.activeAt(11_100, 100).map { it.text })
    }

    @Test
    fun offsetStepsAreClampedAndAdditive() {
        assertEquals(500L, SubtitleOffset.step(0, SubtitleOffset.COARSE_MS))
        assertEquals(400L, SubtitleOffset.step(500, -SubtitleOffset.FINE_MS))
        assertEquals(-600L, SubtitleOffset.step(-100, -500))
        assertEquals(SubtitleOffset.LIMIT_MS, SubtitleOffset.step(SubtitleOffset.LIMIT_MS - 100, 500))
        assertEquals(-SubtitleOffset.LIMIT_MS, SubtitleOffset.step(-SubtitleOffset.LIMIT_MS + 100, -500))
        // Ondalık hata birikmez: tamsayı milisaniye.
        var v = 0L
        repeat(10) { v = SubtitleOffset.step(v, SubtitleOffset.FINE_MS) }
        assertEquals(1_000L, v)
    }

    @Test
    fun cleanLabelCleansExtensionAndLimitsLength() {
        assertEquals("Release", SubtitleSelection.cleanLabel("Release.srt"))
        assertEquals("Release", SubtitleSelection.cleanLabel("Release.vtt"))
        assertEquals("Release", SubtitleSelection.cleanLabel("Release.sub"))
        assertEquals("Release", SubtitleSelection.cleanLabel("Release.ass"))
        assertEquals(null, SubtitleSelection.cleanLabel(null))
        assertEquals(null, SubtitleSelection.cleanLabel("   "))
        assertEquals("A".repeat(24) + "…", SubtitleSelection.cleanLabel("A".repeat(30)))
    }

    @Test
    fun dedupeAndDisambiguateCapsAtMaxPerAddonLanguage() {
        val tracks = (1..10).map { i ->
            SubtitleItem.External(SubtitleTrack("https://s/tr$i.srt", "tr", "AddonX", label = "Sub$i"))
        }
        val result = SubtitleSelection.dedupeAndDisambiguate(tracks, maxPerAddonLang = 5)
        assertEquals(5, result.size)
        assertEquals(listOf("Sub1", "Sub2", "Sub3", "Sub4", "Sub5"), result.map { (it as SubtitleItem.External).track.label })
    }

    @Test
    fun dedupeAndDisambiguatePreservesLabelsWhenDistinct() {
        val tracks = listOf(
            SubtitleItem.External(SubtitleTrack("https://s/tr1.srt", "tr", "AddonX", label = "BluRay")),
            SubtitleItem.External(SubtitleTrack("https://s/tr2.srt", "tr", "AddonX", label = "WEB-DL")),
        )
        val result = SubtitleSelection.dedupeAndDisambiguate(tracks)
        assertEquals(2, result.size)
        assertEquals(listOf("BluRay", "WEB-DL"), result.map { (it as SubtitleItem.External).track.label })
    }

    @Test
    fun dedupeAndDisambiguateAssignsSequentialNumbersWhenLabelsAbsentOrNotDistinct() {
        val tracks = listOf(
            SubtitleItem.External(SubtitleTrack("https://s/tr1.srt", "tr", "AddonX", label = null)),
            SubtitleItem.External(SubtitleTrack("https://s/tr2.srt", "tr", "AddonX", label = null)),
            SubtitleItem.External(SubtitleTrack("https://s/tr3.srt", "tr", "AddonX", label = null)),
        )
        val result = SubtitleSelection.dedupeAndDisambiguate(tracks)
        assertEquals(3, result.size)
        assertEquals(listOf("1", "2", "3"), result.map { (it as SubtitleItem.External).track.label })
    }

    @Test
    fun dedupeAndDisambiguatePreservesPriorityOrder() {
        val embedded = SubtitleItem.Embedded(0, "tr")
        val stream = SubtitleItem.External(SubtitleTrack("https://s/stream.srt", "tr", "Akış"))
        val addon1 = SubtitleItem.External(SubtitleTrack("https://s/ad1.srt", "tr", "Addon1"))
        val addon2 = SubtitleItem.External(SubtitleTrack("https://s/ad2.srt", "tr", "Addon2"))
        val items = listOf(embedded, stream, addon1, addon2)
        val result = SubtitleSelection.dedupeAndDisambiguate(items)
        assertEquals(4, result.size)
        assertEquals(embedded, result[0])
        assertEquals(stream, result[1])
        assertEquals(addon1, result[2])
        assertEquals(addon2, result[3])
    }
}
