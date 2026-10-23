package com.kaan.play.data.stream

import com.kaan.play.data.match.ContentDurationCheck
import com.kaan.play.ui.player.PlaybackController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LiveStreamBlackScreenFixTest {

    @Test
    fun isFakeLiveStreamIdentifiesShortFiniteDurationInLiveStream() {
        // Canlı TV yayınında 9:59 (599 saniye / ~10 dk) sonlu süre sahte kaynaktır
        val duration10Min = 599_000L
        assertTrue(ContentDurationCheck.isFakeLiveStream(duration10Min, isLive = true))

        // 14 MB / 10 dk dosya
        val duration14Min = 14 * 60 * 1000L
        assertTrue(ContentDurationCheck.isFakeLiveStream(duration14Min, isLive = true))

        // 30 dk altındaki sonlu süreler canlıda sahte kaynaktır
        val duration25Min = 25 * 60 * 1000L
        assertTrue(ContentDurationCheck.isFakeLiveStream(duration25Min, isLive = true))

        // Normal film veya dizide sahte canlı sayılmaz
        assertFalse(ContentDurationCheck.isFakeLiveStream(duration10Min, isLive = false))

        // Canlıda süre bilinmiyorsa (0 veya negatif) sahte sayılmaz
        assertFalse(ContentDurationCheck.isFakeLiveStream(0L, isLive = true))
        assertFalse(ContentDurationCheck.isFakeLiveStream(-1L, isLive = true))
    }

    @Test
    fun sourceRankingPreservesManifestOrderForLiveStreamInsteadOfForcingResolutionTier() {
        val source = StreamSource(1L, "WioSpor")
        // Stream 1: PatronHD HLS (başlıkta çözünürlük yok, "Auto")
        val streamHlsAuto = PlayableStream(
            source = source,
            url = "https://patron.example.com/live.m3u8",
            resolution = null, // Auto
            sizeBytes = null,
            headers = mapOf("User-Agent" to "CustomUA"),
            subtitles = emptyList(),
            language = "TR",
            torrentFileIdx = null,
            dubbed = false,
        )

        // Stream 2: IPTV link (başlıkta 1080p var)
        val streamIptv1080p = PlayableStream(
            source = source,
            url = "http://iptv.example.com:80/live.php",
            resolution = 1080,
            sizeBytes = null,
            headers = emptyMap(),
            subtitles = emptyList(),
            language = "TR",
            torrentFileIdx = null,
            dubbed = false,
        )

        val rawList = listOf(streamHlsAuto, streamIptv1080p)

        // Film modu (isLive = false): 1080p olan öne geçer
        val movieCtx = RankContext(maxHeight = 1080, preferDub = false, isLive = false)
        val movieRanked = SourceRanking.recommended(rawList, movieCtx)
        assertEquals("http://iptv.example.com:80/live.php", movieRanked.first().url)

        // Canlı TV modu (isLive = true): Eklentinin özgün manifest sırası korunur, Auto HLS önde kalır!
        val liveCtx = RankContext(maxHeight = 1080, preferDub = false, isLive = true)
        val liveRanked = SourceRanking.recommended(rawList, liveCtx)
        assertEquals("https://patron.example.com/live.m3u8", liveRanked.first().url)
    }

    @Test
    fun defaultUserAgentIsBrowserLikeAndNotEmpty() {
        val ua = PlaybackController.DEFAULT_USER_AGENT
        assertTrue(ua.isNotBlank())
        assertTrue(ua.startsWith("Mozilla/5.0"))
    }
}
