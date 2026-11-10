package com.kaan.play.data.match

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeSourceLabelRulesTest {

    private fun seriesReq(season: Int, episode: Int, addonType: String? = null) = StreamRequest(
        tmdbId = 4057,
        imdbId = "tt0452046",
        type = MediaType.Tv,
        season = season,
        episode = episode,
        title = "Criminal Minds",
        addonType = addonType,
    )

    private fun movieReq(year: Int?, title: String = "Joker") = StreamRequest(
        tmdbId = 475557,
        imdbId = "tt7286456",
        type = MediaType.Movie,
        title = title,
        year = year,
    )

    @Test
    fun acceptsMatchingAndAmbiguousSeriesLabels() {
        val req1x2 = seriesReq(1, 2)
        val req2x5 = seriesReq(2, 5)
        val accepted = listOf(
            "Criminal.Minds.S01E02.1080p.WEB-DL.x265",
            "criminal.minds.s1e2.720p",
            "Criminal Minds 1x02 TR Dublaj",
            "Criminal Minds 1x2 Altyazı",
            "1. Sezon 2. Bölüm 1080p",
            "Season 1 Episode 2",
            "2. Bölüm (Tek Parça)",
            "1080p FHD H264",
            "x265 10bit HEVC",
            "TR Dublaj",
            "S01 Complete",
            "Season 1 Complete Pack",
            "Tüm Bölümler 1080p",
            "S01E01-E05 Paket",
            "1920x1080 5.1 AAC",
            "16x9 720p WebRip",
            "4x3 Remastered",
            null,
            "   ",
        )
        for (label in accepted) {
            val decision = EpisodeSourceLabelRules.evaluate(label, req1x2)
            assertTrue("Kabul edilmeliydi: '$label'", decision.accept)
        }

        // Mutlak numaralandırma olma ihtimali nedeniyle 2. sezonda sezonsuz "25. Bölüm" elenmez:
        assertTrue(EpisodeSourceLabelRules.evaluate("25. Bölüm 1080p", req2x5).accept)
        // S02E05 tam eşleşmesi kabul edilir:
        assertTrue(EpisodeSourceLabelRules.evaluate("2. Sezon 5. Bölüm 1080p", req2x5).accept)
        // Canlı TV ASLA elenmez:
        assertTrue(EpisodeSourceLabelRules.evaluate("S03E09 Haber", seriesReq(1, 1, addonType = "tv")).accept)
    }

    @Test
    fun rejectsExplicitlyMismatchedEpisodeMarkers() {
        val req1x2 = seriesReq(1, 2)
        val rejected = listOf(
            "Criminal.Minds.S01E03.1080p.WEB-DL",
            "criminal minds s2e2 720p",
            "1x05.720p.BluRay",
            "02x02 TR Dublaj",
            "1. Sezon 4. Bölüm",
            "2. Sezon 2. Bölüm",
            "Season 2 Episode 2",
            "5. Bölüm 1080p",
            "Episode 7 WEB-DL",
        )
        for (label in rejected) {
            val decision = EpisodeSourceLabelRules.evaluate(label, req1x2)
            assertFalse("Elenmeliydi: '$label'", decision.accept)
        }
    }

    @Test
    fun movieYearChecksWithinToleranceAndPreservesTitleYears() {
        val jokerReq = movieReq(2019, "Joker")
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker.2019.1080p.WEB-DL", jokerReq).accept)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker.2020.BluRay", jokerReq).accept)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker 2160p x265 TR Dublaj", jokerReq).accept)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker 1080p", jokerReq).accept)

        assertFalse(EpisodeSourceLabelRules.evaluate("Joker (1985) 1080p", jokerReq).accept)
        assertFalse(EpisodeSourceLabelRules.evaluate("Joker.2024.WEB-DL.x264", jokerReq).accept)

        // F1: Çözünürlük, boyut ve birim sayıları yıl SAYILMAZ (yanlış eleme önlenir)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker 1920x1080 WEB-DL", jokerReq).accept)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker · 1350×720 · TR Dublaj", jokerReq).accept)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker 1998 MB", jokerReq).accept)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker 2000kbps", jokerReq).accept)
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker 2160p 2019", jokerReq).accept)

        // Yanlış yıl içeren ama yanında boyut/çözünürlük olan kaynak elenmeli
        assertFalse(EpisodeSourceLabelRules.evaluate("Joker (1985) 1920x1080", jokerReq).accept)

        // Çoklu aday yıldan HERHANGİ biri ±1 içindeyse kabul
        assertTrue(EpisodeSourceLabelRules.evaluate("Joker 1989 2019 Remastered", jokerReq).accept)

        // Film adının kendisinde yıl varsa (Wonder Woman 1984 / Blade Runner 2049) yanlışlıkla elenmez!
        val wwReq = movieReq(2020, "Wonder Woman 1984")
        assertTrue(EpisodeSourceLabelRules.evaluate("Wonder Woman 1984 1080p WEB-DL", wwReq).accept)
    }
}
