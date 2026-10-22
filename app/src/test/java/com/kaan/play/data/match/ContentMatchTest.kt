package com.kaan.play.data.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Eşleştirme ve doğrulama kuralları: tek bir filme değil, genel durumlara (aynı adlı filmler, devam filmleri, TR/EN başlık…). */
class ContentMatchTest {

    private fun movie(vararg titles: String, year: Int?, tmdb: Int? = null, imdb: String? = null, runtime: Int? = null) =
        ContentIdentity(ContentKind.Movie, titles.toList(), year, tmdb, imdb, runtime)

    private fun tv(vararg titles: String, year: Int?) = ContentIdentity(ContentKind.Tv, titles.toList(), year)

    private fun c(name: String, year: Int? = null, url: String = "u:$name", kind: ContentKind? = null, other: List<String> = emptyList()) =
        MatchCandidate(name, year, url, kind, other)

    private fun pickUrl(candidates: List<MatchCandidate>, identity: ContentIdentity): String? =
        (CsMatchRules.pick(candidates, identity) as? MatchOutcome.Match)?.best?.candidate?.url

    // ---- Aynı adlı farklı yıllar ----

    @Test
    fun dune_1984_and_2021_pick_by_year() {
        val results = listOf(c("Dune", 1984, "old"), c("Dune", 2021, "new"))
        assertEquals("new", pickUrl(results, movie("Dune", year = 2021)))
        assertEquals("old", pickUrl(results, movie("Dune", year = 1984)))
    }

    @Test
    fun joker_1975_never_matches_2019() {
        val only1975 = listOf(c("Joker", 1975, "j75"))
        assertNull(pickUrl(only1975, movie("Joker", year = 2019)))
        val both = listOf(c("Joker", 1975, "j75"), c("Joker", 2019, "j19"))
        assertEquals("j19", pickUrl(both, movie("Joker", year = 2019)))
    }

    @Test
    fun yearWithinOneIsAccepted_twoApartIsRejected() {
        assertEquals("a", pickUrl(listOf(c("Inception", 2011, "a")), movie("Inception", year = 2010)))
        assertNull(pickUrl(listOf(c("Inception", 2012, "a")), movie("Inception", year = 2010)))
    }

    @Test
    fun yearIsReadFromNameWhenResultHasNone() {
        val results = listOf(c("Joker (1975)", null, "j75"), c("Joker (2019)", null, "j19"))
        assertEquals("j19", pickUrl(results, movie("Joker", year = 2019)))
    }

    // ---- Devam filmleri ----

    @Test
    fun sequelsDoNotMatchEachOther_lotr() {
        val sites = listOf(
            c("Yüzüklerin Efendisi: Yüzük Kardeşliği", 2001, "ff"),
            c("Yüzüklerin Efendisi: İki Kule", 2002, "tt"),
            c("Yüzüklerin Efendisi: Kralın Dönüşü", 2003, "rotk"),
        )
        assertEquals("ff", pickUrl(sites, movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = 2001)))
        assertEquals("tt", pickUrl(sites, movie("Yüzüklerin Efendisi: İki Kule", year = 2002)))
        assertEquals("rotk", pickUrl(sites, movie("Yüzüklerin Efendisi: Kralın Dönüşü", year = 2003)))
    }

    @Test
    fun sequelRequestedButOnlyFirstExists_noMatch_evenWithoutYear() {
        val onlyFirst = listOf(c("Yüzüklerin Efendisi: Yüzük Kardeşliği", null, "ff"))
        assertNull(pickUrl(onlyFirst, movie("Yüzüklerin Efendisi: İki Kule", year = 2002)))
        assertNull(pickUrl(onlyFirst, movie("Yüzüklerin Efendisi: Kralın Dönüşü", year = 2003)))
    }

    @Test
    fun containsRuleRejectsExtraWordsWhenYearUnknown() {
        // "Matrix" istenirken "Matrix Reloaded" yılsız eşleşmez; yılı da farklıysa hiç eşleşmez.
        assertNull(pickUrl(listOf(c("The Matrix Reloaded", null)), movie("The Matrix", year = 1999)))
        assertNull(pickUrl(listOf(c("The Matrix Reloaded", 2003)), movie("The Matrix", year = 1999)))
        assertNull(pickUrl(listOf(c("Joker: Folie à Deux", null)), movie("Joker", year = 2019)))
    }

    @Test
    fun partTwoVariantsAreTheSameTitle() {
        val id = movie("Dune: Part Two", year = 2024)
        assertNotNull(pickUrl(listOf(c("Dune Part II", 2024)), id))
        assertNotNull(pickUrl(listOf(c("Dune: Part 2", 2024)), id))
        assertNotNull(pickUrl(listOf(c("Dune Part Two", 2024)), id))
        assertNotNull(pickUrl(listOf(c("Dune 2", 2024)), id))
        // Part Two istenirken Part One (2021) gelirse eşleşmez.
        assertNull(pickUrl(listOf(c("Dune: Part One", 2021)), id))
        assertNull(pickUrl(listOf(c("Dune", 2021)), id))
    }

    @Test
    fun numberedFirstFilmMatchesUnnumbered() {
        assertEquals("a", pickUrl(listOf(c("Yüzüklerin Efendisi 1 - Yüzük Kardeşliği Extended", null, "a")), movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = 2001)))
    }

    // ---- Alt başlıklı / alt başlıksız ----

    @Test
    fun subtitleOnlyCandidateAcceptedOnlyWithExactYear() {
        val id = movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = 2001)
        assertEquals("a", pickUrl(listOf(c("Yüzük Kardeşliği", 2001, "a")), id))
        assertNull(pickUrl(listOf(c("Yüzük Kardeşliği", null, "a")), id))
        assertNull(pickUrl(listOf(c("Yüzük Kardeşliği", 2002, "a")), id)) // yıl ±1 içinde ama birebir değil: kısmi eşleşme yok
    }

    @Test
    fun franchiseOnlyCandidateIsNotASequelMatch() {
        // Sitede yalnızca seri adı ("Yüzüklerin Efendisi", 2001) var: İki Kule (2002) için yanlış film olurdu.
        val id = movie("Yüzüklerin Efendisi: İki Kule", year = 2002)
        assertNull(pickUrl(listOf(c("Yüzüklerin Efendisi", 2001, "a")), id))
    }

    // ---- Türkçe / İngilizce başlık farkı ----

    @Test
    fun matchesOnOriginalTitleWhenLocalTitleDiffers() {
        val id = movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", "The Lord of the Rings: The Fellowship of the Ring", year = 2001)
        val results = listOf(c("The Lord of the Rings: The Fellowship of the Ring", 2001, "en"))
        assertEquals("en", pickUrl(results, id))
    }

    @Test
    fun bilingualSiteTitleIsSplitIntoSegments() {
        val id = movie("The Lord of the Rings: The Fellowship of the Ring", year = 2001)
        val results = listOf(c("Yüzüklerin Efendisi Yüzük Kardeşliği - The Lord of the Rings: The Fellowship of the Ring", null, "bi"))
        assertEquals("bi", pickUrl(results, id))
    }

    @Test
    fun siteNoiseWordsAndAccentsAreIgnored() {
        val id = movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = 2001)
        assertNotNull(pickUrl(listOf(c("yuzuklerin efendisi yuzuk kardesligi Türkçe Dublaj izle Full HD", 2001)), id))
        assertNotNull(pickUrl(listOf(c("YÜZÜKLERİN EFENDİSİ: YÜZÜK KARDEŞLİĞİ", 2001)), id))
    }

    @Test
    fun inflectionTypoIsTolerated() {
        val id = movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = 2001)
        assertNotNull(pickUrl(listOf(c("Yüzüklerin Efendsi: Yüzük Kardeşliği", 2001)), id)) // tek harf eksik
        assertNull(pickUrl(listOf(c("Yüzüklerin Efendisi: Yüzük Kardeşlik", 2001)), id)) // iki harf fark: eşleşme yok
    }

    // ---- Çok aday / eşik / eşleşme yok ----

    @Test
    fun picksHighestScoreNotFirst() {
        val id = movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = null)
        val results = listOf(
            c("Yüzük Kardeşliği Belgeseli: Perde Arkası", null, "doc"),
            c("Yüzüklerin Efendisi: Yüzük Kardeşliği", null, "exact"),
        )
        assertEquals("exact", pickUrl(results, id))
    }

    @Test
    fun tieOnScorePrefersExactYearThenKind() {
        val id = movie("Dune", year = 2021)
        val results = listOf(c("Dune", 2020, "y20"), c("Dune", 2021, "y21"))
        assertEquals("y21", pickUrl(results, id))
        val kinds = listOf(c("Dune", null, "tvkind", ContentKind.Tv), c("Dune", null, "moviekind", ContentKind.Movie))
        assertEquals("moviekind", pickUrl(kinds, movie("Dune", year = null)))
    }

    @Test
    fun noMatchWhenNothingResembles() {
        val results = listOf(c("Buddy"), c("Moana"), c("Hazine Avcısının Maceraları"), c("Kötü Elmalar"), c("Kayıp Balık Dori"))
        val outcome = CsMatchRules.pick(results, movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = 2001))
        assertTrue(outcome is MatchOutcome.NoMatch)
        assertEquals(3, (outcome as MatchOutcome.NoMatch).rejections.size) // günlük için ilk 3
    }

    @Test
    fun emptyResultsNoMatch() {
        assertTrue(CsMatchRules.pick(emptyList(), movie("Dune", year = 2021)) is MatchOutcome.NoMatch)
    }

    @Test
    fun unknownYearOnBothSidesStillRequiresFullTitle() {
        val id = movie("Inception", year = null)
        assertNotNull(pickUrl(listOf(c("Inception", null)), id))
        assertNull(pickUrl(listOf(c("Inception: The Cobol Job", null)), id))
    }

    @Test
    fun yearLikeTitlesAreNotStripped() {
        val id = movie("1917", year = 2019)
        assertNotNull(pickUrl(listOf(c("1917", 2019)), id))
        val bladeRunner = movie("Blade Runner 2049", year = 2017)
        assertNotNull(pickUrl(listOf(c("Blade Runner 2049", 2017)), bladeRunner))
        assertNull(pickUrl(listOf(c("Blade Runner", 1982)), bladeRunner))
    }

    // ---- Dizi ----

    @Test
    fun seriesMatchByNameAndFirstAirYear() {
        val id = tv("Kurtlar Vadisi", year = 2003)
        val results = listOf(c("Kurtlar Vadisi Pusu", 2007, "pusu"), c("Kurtlar Vadisi", 2003, "kv"))
        assertEquals("kv", pickUrl(results, id))
    }

    @Test
    fun searchQueriesAreDistinctAndCapped() {
        val id = ContentIdentity.of(
            ContentKind.Movie, "Yüzüklerin Efendisi: Yüzük Kardeşliği", "The Lord of the Rings: The Fellowship of the Ring",
            listOf("YÜZÜKLERİN EFENDİSİ: YÜZÜK KARDEŞLİĞİ", "LOTR 1", "Lord of the Rings 1", "x"), year = 2001,
        )
        val q = id.searchQueries()
        assertEquals(3, q.size)
        assertEquals("Yüzüklerin Efendisi: Yüzük Kardeşliği", q[0])
        assertEquals("The Lord of the Rings: The Fellowship of the Ring", q[1])
    }

    // ---- Yükleme sonrası doğrulama ----

    @Test
    fun loadedWithMatchingTmdbIdIsOk_differentIsRejected() {
        val id = movie("Dune", year = 2021, tmdb = 438631)
        assertEquals(Verdict.Ok, CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("???", 1999, null, "438631", null), id))
        assertTrue(CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("Dune", 2021, null, "841", null), id) is Verdict.Reject)
    }

    @Test
    fun loadedImdbIdDecidesWhenNoTmdb() {
        val id = movie("Dune", year = 2021, imdb = "tt1160419")
        assertEquals(Verdict.Ok, CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo(null, null, "tt1160419", null, null), id))
        assertTrue(CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("Dune", 2021, "tt0087182", null, null), id) is Verdict.Reject)
    }

    @Test
    fun loadedNameAndYearAreCheckedWithoutIds() {
        val id = movie("Joker", year = 2019)
        assertEquals(Verdict.Ok, CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("Joker", 2019, null, null, null), id))
        assertTrue(CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("Joker", 1975, null, null, null), id) is Verdict.Reject)
        assertTrue(CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("Moana", 2019, null, null, null), id) is Verdict.Reject)
        // Hiçbir şey bildirmeyen yanıt arama aşamasındaki eşleşmeye bırakılır.
        assertEquals(Verdict.Ok, CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo(null, null, null, null, null), id))
    }

    @Test
    fun loadedRuntimeFarOffIsRejectedForMovies() {
        val id = movie("Yüzüklerin Efendisi: Yüzük Kardeşliği", year = 2001, runtime = 178)
        assertTrue(CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("Yüzüklerin Efendisi: Yüzük Kardeşliği", 2001, null, null, 33), id) is Verdict.Reject)
        assertEquals(Verdict.Ok, CsMatchRules.verifyLoaded(CsMatchRules.LoadedInfo("Yüzüklerin Efendisi: Yüzük Kardeşliği", 2001, null, null, 228), id)) // extended
    }

    // ---- Süre (nesnel doğrulama) ----

    @Test
    fun durationRules_movie() {
        val min = 60_000L
        assertEquals(ContentDurationRules.Result.Mismatch, ContentDurationRules.check(33 * min + 25_000, 178, ContentKind.Movie))
        assertEquals(ContentDurationRules.Result.Ok, ContentDurationRules.check(178 * min, 178, ContentKind.Movie))
        assertEquals(ContentDurationRules.Result.Ok, ContentDurationRules.check(228 * min, 178, ContentKind.Movie)) // extended edition
        assertEquals(ContentDurationRules.Result.Mismatch, ContentDurationRules.check(600 * min, 100, ContentKind.Movie))
        assertEquals(ContentDurationRules.Result.Unknown, ContentDurationRules.check(0, 178, ContentKind.Movie))
        assertEquals(ContentDurationRules.Result.Unknown, ContentDurationRules.check(10 * min, null, ContentKind.Movie))
        assertEquals(ContentDurationRules.Result.Unknown, ContentDurationRules.check(2 * min, 12, ContentKind.Movie)) // kısa film: oran güvenilmez
    }

    @Test
    fun durationRules_episode() {
        val min = 60_000L
        assertEquals(ContentDurationRules.Result.Ok, ContentDurationRules.check(42 * min, 45, ContentKind.Tv))
        assertEquals(ContentDurationRules.Result.Mismatch, ContentDurationRules.check(3 * min, 45, ContentKind.Tv)) // fragman
        assertEquals(ContentDurationRules.Result.Mismatch, ContentDurationRules.check(240 * min, 45, ContentKind.Tv))
    }

    // ---- Bölüm seçimi ----

    private fun ep(data: String, s: Int?, e: Int?, name: String? = null) = EpisodeRef(data, name, s, e)

    @Test
    fun episodePicker_exactSeasonEpisode() {
        val list = listOf(ep("s1e1", 1, 1), ep("s1e2", 1, 2), ep("s2e1", 2, 1), ep("s2e2", 2, 2))
        assertEquals("s2e2", EpisodePicker.pick(list, 2, 2))
        assertEquals("s1e2", EpisodePicker.pick(list, 1, 2))
    }

    @Test
    fun episodePicker_neverReturnsAnotherSeasonsEpisode() {
        val onlySeason1 = listOf(ep("s1e1", 1, 1), ep("s1e2", 1, 2), ep("s1e3", 1, 3))
        assertNull(EpisodePicker.pick(onlySeason1, 2, 3)) // eskiden S1B3 dönerdi
        assertNull(EpisodePicker.pick(onlySeason1, 1, 9))
    }

    @Test
    fun episodePicker_flatListOnlyForFirstSeason() {
        val flat = listOf(ep("a", null, 1), ep("b", null, 2), ep("c", null, 3))
        assertEquals("b", EpisodePicker.pick(flat, 1, 2))
        assertEquals("c", EpisodePicker.pick(flat, null, 3))
        assertNull(EpisodePicker.pick(flat, 2, 2)) // sezon 2 için sezonsuz düz liste: hangi sezon olduğu bilinemez
    }

    @Test
    fun episodePicker_extractsNumberFromNameOrData() {
        val named = listOf(ep("x1", null, null, "3. Bölüm"), ep("x2", null, null, "4. Bölüm"))
        assertEquals("x2", EpisodePicker.pick(named, 1, 4))
        val byData = listOf(ep("/dizi/bolum-1", null, null), ep("/dizi/bolum-2", null, null))
        assertEquals("/dizi/bolum-2", EpisodePicker.pick(byData, 1, 2))
    }

    @Test
    fun episodePicker_indexFallbackOnlyForPureOrderedLists() {
        val unnumbered = listOf(ep("first", null, null), ep("second", null, null))
        assertEquals("second", EpisodePicker.pick(unnumbered, 1, 2))
        val partlyNumbered = listOf(ep("first", null, 1), ep("second", null, 2))
        assertNull(EpisodePicker.pick(partlyNumbered, 1, 5))
    }

    @Test
    fun episodePicker_noEpisodeRequestedReturnsFirst_emptyReturnsNull() {
        assertEquals("only", EpisodePicker.pick(listOf(ep("only", 1, 1)), 1, null))
        assertNull(EpisodePicker.pick(emptyList(), 1, 1))
    }

    @Test
    fun titleText_basics() {
        assertEquals("yuzuklerin efendisi", TitleText.fold("Yüzüklerin Efendisi"))
        assertEquals("istanbul", TitleText.fold("İSTANBUL"))
        assertEquals(listOf("dune", "2"), TitleText.tokens("Dune: Part Two"))
        assertEquals(listOf("dune", "2"), TitleText.tokens("Dune Part II (2024)"))
        assertEquals(2021, TitleText.yearIn("Dune (2021)"))
        assertEquals(2021, TitleText.yearIn("Dune 2021"))
        assertNull(TitleText.yearIn("1917"))
        assertNull(TitleText.yearIn("Blade Runner 2049"))
        assertFalse(TitleText.segments("A - B").isEmpty())
    }
}
