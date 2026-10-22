package com.kaan.play.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class DubDetectorTest {

    @Test
    fun detectsTheListedMarkers() {
        listOf(
            "Film TR Dublaj 1080p",
            "Türkçe Dublaj",
            "TÜRKÇE DUBLAJLI",
            "film.2020.dublajli.mkv",
            "Dublaj",
            "Dublajlı",
            "TR Dub",
            "tr.dub.720p",
            "Turkish Dubbed",
            "Dual Audio TR",
            "Dual-Audio TR-EN",
            "[TR-EN] 1080p",
            "tr.dublaj",
            "İngilizce Türkçe Dublaj",
            "TR-DUBLAJ",
        ).forEach { assertTrue("dublaj sayılmalı: $it", DubDetector.isDubbed(it)) }
    }

    @Test
    fun ignoresCaseAndTurkishLetters() {
        assertTrue(DubDetector.isDubbed("TURKCE DUBLAJ"))
        assertTrue(DubDetector.isDubbed("türkçe dublajlı"))
        assertTrue(DubDetector.isDubbed("DUBLAJLI"))
        assertTrue(DubDetector.isDubbed("dublaj"))
        assertTrue(DubDetector.isDubbed("DUBLAJLI".lowercase(Locale("tr"))))
    }

    @Test
    fun uncertainOrOtherLanguagesAreSubtitled() {
        listOf(
            "Altyazılı",
            "TR Sub",
            "Turkish subtitles",
            "English 1080p",
            "Dublajsız",
            "dublajsiz",
            "Dub",
            "Dubai 2020",
            "Dual Audio",
            "str-en",
            "",
            "   ",
        ).forEach { assertFalse("altyazılı sayılmalı: '$it'", DubDetector.isDubbed(it)) }
        assertFalse(DubDetector.isDubbed(null))
    }

    @Test
    fun dublajsizDoesNotCancelADubMarkerElsewhere() {
        assertTrue(DubDetector.isDubbed("Dublajsız sürüm değil: TR Dublaj"))
    }
}

class SizeTextTest {
    private val tr = Locale("tr", "TR")
    private val en = Locale.US

    @Test
    fun gigabytesHaveOneDecimalAndLocaleSeparator() {
        assertEquals("7,7 GB", SizeText.format(7_700_000_000L, tr))
        assertEquals("7.7 GB", SizeText.format(7_700_000_000L, en))
        assertEquals("22,0 GB", SizeText.format(22_000_000_000L, tr))
        assertEquals("1,0 GB", SizeText.format(1_000_000_000L, tr))
    }

    @Test
    fun belowOneGigabyteIsMegabytes() {
        assertEquals("700 MB", SizeText.format(700_000_000L, tr))
        assertEquals("999 MB", SizeText.format(999_000_000L, en))
        assertEquals("1 MB", SizeText.format(100_000L, en)) // en az 1 MB
    }

    @Test
    fun unknownOrZeroSizeShowsNothing() {
        assertNull(SizeText.format(null, tr))
        assertNull(SizeText.format(0L, tr))
        assertNull(SizeText.format(-5L, tr))
    }

    @Test
    fun dimensions() {
        assertEquals("1920×872", SizeText.dimensions(1920, 872))
        assertNull(SizeText.dimensions(0, 872))
        assertNull(SizeText.dimensions(1920, 0))
    }
}

class SourceRankingTest {

    private fun s(
        addon: Long,
        url: String,
        dub: Boolean = false,
        res: Int? = 1080,
        size: Long? = 2_000_000_000L,
    ) = PlayableStream(StreamSource(addon, "A$addon"), url, res, size, emptyMap(), emptyList(), dubbed = dub)

    private val magnet = "magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val ctx = RankContext(maxHeight = 1080, preferDub = true)

    @Test
    fun dubbedComesBeforeSubtitledWhenPreferred() {
        val sub = s(1, "https://a/1")
        val dub = s(2, "https://b/1", dub = true, res = 720)
        assertEquals(listOf(dub, sub), SourceRanking.recommended(listOf(sub, dub), ctx))
        // Öncelik kapalıyken tür ayrımı yok: kaliteye göre.
        assertEquals(listOf(sub, dub), SourceRanking.recommended(listOf(dub, sub), ctx.copy(preferDub = false)))
    }

    @Test
    fun directLinksComeBeforeTorrentsInTheSameGroup() {
        val torrent = s(1, magnet, res = 1080)
        val direct = s(2, "https://a/1", res = 720)
        assertEquals(listOf(direct, torrent), SourceRanking.recommended(listOf(torrent, direct), ctx))
        // Dublajlı torrent yine altyazılı doğrudan bağlantının önünde (grup önce gelir).
        val dubTorrent = s(3, "magnet:?xt=urn:btih:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", dub = true)
        assertEquals(listOf(dubTorrent, direct), SourceRanking.recommended(listOf(direct, dubTorrent), ctx))
    }

    @Test
    fun overDeviceResolutionAndHugeFilesGoLast() {
        val fourK = s(1, "https://a/4k", dub = true, res = 2160)
        val huge = s(2, "https://a/huge", dub = true, res = 1080, size = 22_000_000_000L)
        val normalSub = s(3, "https://a/sub", res = 720)
        val ordered = SourceRanking.recommended(listOf(fourK, huge, normalSub), ctx)
        assertEquals(normalSub, ordered.first())
        assertEquals(setOf(fourK, huge), ordered.drop(1).toSet())
        // Cihaz 4K çözebiliyorsa 4K sona gitmez (yine 1080p'den sonra gelir).
        val on4k = SourceRanking.recommended(listOf(normalSub, fourK), ctx.copy(maxHeight = 2160))
        assertEquals(listOf(fourK, normalSub), on4k) // dublajlı önce
    }

    @Test
    fun foundSizeCanMakeASourceTooBig() {
        val a = s(1, "https://a/1", size = null)
        val b = s(2, "https://b/1", size = null, res = 720)
        assertEquals(listOf(a, b), SourceRanking.recommended(listOf(b, a), ctx))
        val withSizes = ctx.copy(sizes = mapOf(a.url to 30_000_000_000L))
        assertEquals(listOf(b, a), SourceRanking.recommended(listOf(a, b), withSizes))
    }

    @Test
    fun previousChoiceLeadsItsOwnGroup() {
        val dub1 = s(1, "https://a/dub", dub = true)
        val dub2 = s(2, "https://b/dub", dub = true)
        val sub1 = s(1, "https://a/sub")
        val sub2 = s(2, "https://b/sub")
        val pref = SourcePref(addonId = 2, addonName = "A2")
        val ordered = SourceRanking.recommended(listOf(dub1, dub2, sub1, sub2), ctx.copy(preference = pref))
        assertEquals(listOf(dub2, dub1, sub2, sub1), ordered)
    }

    @Test
    fun withinSameQualityTheSmallerFileStartsFirst() {
        val big = s(1, "https://a/big", size = 9_000_000_000L)
        val small = s(2, "https://b/small", size = 1_500_000_000L)
        val unknown = s(3, "https://c/unknown", size = null)
        assertEquals(listOf(small, big, unknown), SourceRanking.recommended(listOf(unknown, big, small), ctx))
    }

    @Test
    fun highestResolutionUpTo1080ComesFirst() {
        val p720 = s(1, "https://a/720", res = 720)
        val p1080 = s(2, "https://a/1080", res = 1080)
        val unknown = s(3, "https://a/u", res = null)
        assertEquals(listOf(p1080, p720, unknown), SourceRanking.recommended(listOf(unknown, p720, p1080), ctx))
    }

    @Test
    fun sortsBySizeAndResolution() {
        val a = s(1, "https://a/1", res = 720, size = 3_000_000_000L)
        val b = s(2, "https://a/2", res = 1080, size = 1_000_000_000L)
        val c = s(3, "https://a/3", res = 2160, size = null)
        assertEquals(listOf(a, b, c), SourceRanking.sorted(listOf(c, b, a), SourceSort.SizeDesc, ctx))
        assertEquals(listOf(c, b, a), SourceRanking.sorted(listOf(a, b, c), SourceSort.ResolutionDesc, ctx))
    }

    @Test
    fun filtersByAudioKind() {
        val dub = s(1, "https://a/1", dub = true)
        val sub = s(2, "https://a/2")
        assertEquals(listOf(dub, sub), SourceRanking.filtered(listOf(dub, sub), AudioFilter.All))
        assertEquals(listOf(dub), SourceRanking.filtered(listOf(dub, sub), AudioFilter.Dubbed))
        assertEquals(listOf(sub), SourceRanking.filtered(listOf(dub, sub), AudioFilter.Subbed))
    }

    @Test
    fun duplicateUrlsAreListedOnce() {
        val a = s(1, "https://a/1")
        assertEquals(1, SourceRanking.recommended(listOf(a, a.copy(source = StreamSource(9, "X"))), ctx).size)
    }
}

class StartPolicyTest {
    private fun s(url: String, dub: Boolean, res: Int? = 1080) =
        PlayableStream(StreamSource(1, "A"), url, res, 1_000_000_000L, emptyMap(), emptyList(), dubbed = dub)

    private val ctx = RankContext()

    @Test
    fun startsAtOnceWithADubbedSource() {
        val d = StartPolicy(preferDub = true).decide(0, listOf(s("https://a/d", true), s("https://a/s", false)), ctx, finished = false)
        assertEquals(StartDecision.Start(s("https://a/d", true)), d)
    }

    @Test
    fun waitsUpToEightSecondsForADubbedSource() {
        val policy = StartPolicy(preferDub = true)
        val sub = listOf(s("https://a/s", false))
        assertEquals(StartDecision.Wait(StartPolicy.DUB_WAIT_MS), policy.decide(0, sub, ctx, finished = false))
        assertEquals(StartDecision.Wait(StartPolicy.DUB_WAIT_MS), policy.decide(StartPolicy.DUB_WAIT_MS - 1, sub, ctx, finished = false))
        assertEquals(StartDecision.Start(sub[0]), policy.decide(StartPolicy.DUB_WAIT_MS, sub, ctx, finished = false))
    }

    @Test
    fun doesNotWaitWhenEverySourceAlreadyAnswered() {
        val sub = listOf(s("https://a/s", false))
        assertEquals(StartDecision.Start(sub[0]), StartPolicy(true).decide(100, sub, ctx, finished = true))
    }

    @Test
    fun startsWithTheFirstSourceWhenDubPreferenceIsOff() {
        val sub = listOf(s("https://a/s", false))
        assertEquals(StartDecision.Start(sub[0]), StartPolicy(false).decide(0, sub, ctx, finished = false))
    }

    @Test
    fun heavyDubbedSourceDoesNotSkipTheWait() {
        val heavy = s("https://a/4k", true, res = 2160)
        val d = StartPolicy(true).decide(500, listOf(heavy), RankContext(maxHeight = 1080), finished = false)
        assertEquals(StartDecision.Wait(StartPolicy.DUB_WAIT_MS), d)
    }

    @Test
    fun noSourcesWaitsThenReportsNone() {
        assertEquals(StartDecision.Wait(null), StartPolicy(true).decide(5_000, emptyList(), ctx, finished = false))
        assertEquals(StartDecision.NoSources, StartPolicy(true).decide(5_000, emptyList(), ctx, finished = true))
    }
}
