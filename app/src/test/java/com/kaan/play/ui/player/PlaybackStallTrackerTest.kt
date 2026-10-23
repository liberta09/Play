package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStallTrackerTest {

    private var currentTime = 1_000_000L
    private val tracker = PlaybackStallTracker(clock = { currentTime })

    @Test
    fun initialState_defaultsToZeroAndUnknownSource() {
        assertEquals("bilinmeyen", tracker.sourceName)
        assertEquals(0, tracker.stallCount)
        assertEquals(0L, tracker.totalStallMs)
        assertEquals(0L, tracker.totalPlayMs)
        assertEquals("takılma kaynak=bilinmeyen sayı=0 toplam=0.0 sn oynama=0 dk", tracker.finish())
    }

    @Test
    fun bufferingBeforeFirstFrame_isNotCountedAsStall() {
        tracker.reset("FilmModu")
        // Başlangıç arabelleğe alması (henüz ilk kare gelmedi)
        tracker.onPlaybackStateChanged(buffering = true, isReady = false)
        currentTime += 4_000L // 4 sn arabelleğe alma
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)

        assertEquals(0, tracker.stallCount)
        assertEquals(0L, tracker.totalStallMs)

        // İlk kare şimdi geldi
        tracker.onFirstFrameRendered()
        assertTrue(tracker.firstFrameRendered)
        assertEquals(0, tracker.stallCount)
        assertEquals(0L, tracker.totalStallMs)
    }

    @Test
    fun stallAfterFirstFrame_incrementsCountAndAccumulatesDuration() {
        tracker.reset("FilmModu")
        tracker.onFirstFrameRendered()
        tracker.onIsPlayingChanged(true)
        currentTime += 10_000L // 10 sn oynatıldı

        // Takılma 1: 2.5 saniye
        tracker.onPlaybackStateChanged(buffering = true, isReady = false)
        currentTime += 2_500L
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)

        assertEquals(1, tracker.stallCount)
        assertEquals(2500L, tracker.totalStallMs)

        currentTime += 30_000L // 30 sn oynatıldı

        // Takılma 2: 1.2 saniye
        tracker.onPlaybackStateChanged(buffering = true, isReady = false)
        currentTime += 1_200L
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)

        assertEquals(2, tracker.stallCount)
        assertEquals(3700L, tracker.totalStallMs)

        // Oynatma süresini 3 dakikaya tamamla (toplam oynama = 10 + 30 + 140 = 180 sn = 3 dk)
        currentTime += 140_000L
        val log = tracker.finish()

        assertEquals("takılma kaynak=FilmModu sayı=2 toplam=3.7 sn oynama=3 dk", log)
    }

    @Test
    fun seekingBuffering_isNotCountedAsStall() {
        tracker.reset("CS3")
        tracker.onFirstFrameRendered()
        tracker.onIsPlayingChanged(true)
        currentTime += 15_000L

        // Kullanıcı ileri sardı
        tracker.onSeekStarted()
        assertTrue(tracker.isSeeking)

        // Sarma nedeniyle arabelleğe alma başladı (takılma sayılmamalı)
        tracker.onPlaybackStateChanged(buffering = true, isReady = false)
        currentTime += 3_000L
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)

        assertFalse(tracker.isSeeking)
        assertEquals(0, tracker.stallCount)
        assertEquals(0L, tracker.totalStallMs)
    }

    @Test
    fun seekDuringActiveStall_endsStallAtSeekTime() {
        tracker.reset("Nuvio")
        tracker.onFirstFrameRendered()
        tracker.onIsPlayingChanged(true)
        currentTime += 20_000L

        // Takılma başladı
        tracker.onPlaybackStateChanged(buffering = true, isReady = false)
        currentTime += 1_500L // 1.5 sn takıldı
        assertEquals(1, tracker.stallCount)

        // Kullanıcı takılma sürerken ileri sardı
        tracker.onSeekStarted()
        assertEquals(1500L, tracker.totalStallMs)
        assertFalse(tracker.isBuffering)
        assertTrue(tracker.isSeeking)

        // Sarma arabelleğe alması 2 sn sürdü
        currentTime += 2_000L
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)

        // Sarma sonrası takılma süresi artmamalı
        assertEquals(1, tracker.stallCount)
        assertEquals(1500L, tracker.totalStallMs)
    }

    @Test
    fun finishWhileBuffering_flushesActiveStallDuration() {
        tracker.reset("Dizipal")
        tracker.onFirstFrameRendered()
        tracker.onIsPlayingChanged(true)
        currentTime += 5_000L

        // Takılma başladı
        tracker.onPlaybackStateChanged(buffering = true, isReady = false)
        currentTime += 2_100L
        // Oynatıcı takılma devam ederken kapandı
        val log = tracker.finish()

        assertEquals(1, tracker.stallCount)
        assertEquals(2100L, tracker.totalStallMs)
        assertEquals("takılma kaynak=Dizipal sayı=1 toplam=2.1 sn oynama=0 dk", log)
    }

    @Test
    fun reset_clearsAllMetricsForNewSource() {
        tracker.reset("Kaynak1")
        tracker.onFirstFrameRendered()
        tracker.onPlaybackStateChanged(buffering = true, isReady = false)
        currentTime += 1_000L
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)

        assertEquals(1, tracker.stallCount)

        // Yeni kaynak
        tracker.reset("Kaynak2")
        assertEquals("Kaynak2", tracker.sourceName)
        assertEquals(0, tracker.stallCount)
        assertEquals(0L, tracker.totalStallMs)
        assertFalse(tracker.firstFrameRendered)
    }

    @Test
    fun onPlaybackStateChanged_returnsTrueOnlyWhenStallStarts() {
        tracker.reset("Enel")
        // İlk kareden önce buffering true olsa da takılma başlamaz
        assertFalse(tracker.onPlaybackStateChanged(buffering = true, isReady = false))
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)

        tracker.onFirstFrameRendered()

        // İlk kareden sonra ilk buffering: takılma başlar -> true
        assertTrue(tracker.onPlaybackStateChanged(buffering = true, isReady = false))

        // Halen buffering iken tekrar buffering gelirse yeni takılma başlamaz -> false
        assertFalse(tracker.onPlaybackStateChanged(buffering = true, isReady = false))

        // Ready olunca takılma biter -> false
        assertFalse(tracker.onPlaybackStateChanged(buffering = false, isReady = true))

        // Kullanıcı seek yaparken buffering -> false
        tracker.onSeekStarted()
        assertFalse(tracker.onPlaybackStateChanged(buffering = true, isReady = false))
        tracker.onPlaybackStateChanged(buffering = false, isReady = true)
        tracker.onSeekEnded()

        // Seek bittikten sonra yeni buffering -> true
        assertTrue(tracker.onPlaybackStateChanged(buffering = true, isReady = false))
    }

    @Test
    fun formatStallDetail_formatsBitrateAndBandwidthAndBufferCorrectly() {
        val detail = PlaybackStallTracker.formatStallDetail(
            sourceName = "Enel",
            videoBitrate = 2_450_000,
            bandwidthEstimate = 8_200_000L,
            bufferedPositionMs = 25_000L,
            currentPositionMs = 23_000L,
        )
        assertEquals("takılma-ayrıntı kaynak=Enel bitHızı=2.45 Mbps bantTahmini=8.20 Mbps tampon=2.0 sn", detail)
    }

    @Test
    fun formatStallDetail_handlesUnknownBitrateAndBandwidth() {
        val detail = PlaybackStallTracker.formatStallDetail(
            sourceName = "Dragon",
            videoBitrate = null,
            bandwidthEstimate = null,
            bufferedPositionMs = 10_000L,
            currentPositionMs = 10_000L,
        )
        assertEquals("takılma-ayrıntı kaynak=Dragon bitHızı=bilinmiyor bantTahmini=bilinmiyor tampon=0.0 sn", detail)

        val detailNegative = PlaybackStallTracker.formatStallDetail(
            sourceName = "Ace",
            videoBitrate = -1,
            bandwidthEstimate = 0L,
            bufferedPositionMs = 5_000L,
            currentPositionMs = 6_000L, // current > buffer
        )
        assertEquals("takılma-ayrıntı kaynak=Ace bitHızı=bilinmiyor bantTahmini=bilinmiyor tampon=0.0 sn", detailNegative)
    }
}