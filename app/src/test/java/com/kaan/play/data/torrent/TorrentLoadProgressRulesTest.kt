package com.kaan.play.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentLoadProgressRulesTest {

    @Test
    fun initialStateIsZeroAndMetadataPhase() {
        val state = TorrentLoadProgressRules.initialState()
        assertEquals(0f, state.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Metadata, state.phase)
        assertFalse(state.isFirstFrame)
        assertFalse(state.fallbackToStandard)
    }

    @Test
    fun metadataPhaseStaysConstantWhenMetadataDoesNotAdvance() {
        val s1 = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = false,
            metadataFraction = 0f,
            previousProgress = 0f
        )
        assertEquals(0f, s1.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Metadata, s1.phase)

        // Metadata gelmiyorsa yüzde sabit kalır
        val s2 = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = false,
            metadataFraction = 0f,
            previousProgress = s1.progress
        )
        assertEquals(s1.progress, s2.progress, 0.001f)
    }

    @Test
    fun metadataPhaseAdvancesUpTo30Percent() {
        // Yarım metadata (%50) -> %15 ilerleme
        val half = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = false,
            metadataFraction = 0.5f,
            previousProgress = 0f
        )
        assertEquals(15f, half.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Metadata, half.phase)

        // Tam metadata (%100) -> %30 ilerleme
        val full = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = false,
            metadataFraction = 1.0f,
            previousProgress = half.progress
        )
        assertEquals(30f, full.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Metadata, full.phase)
    }

    @Test
    fun metadataPhaseNeverExceeds30Percent() {
        val overflow = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = false,
            metadataFraction = 1.8f,
            previousProgress = 0f
        )
        assertEquals(30f, overflow.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Metadata, overflow.phase)
    }

    @Test
    fun transitionToBufferingPhaseStartsAt30Percent() {
        val s = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = true,
            bufferedBytes = 0L,
            requiredBytes = 2_000_000L,
            previousProgress = 0f
        )
        assertEquals(30f, s.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Buffering, s.phase)
        assertFalse(s.isFirstFrame)
    }

    @Test
    fun bufferingPhaseCalculatesProportionalProgress() {
        // Tampon yarım (1 MB / 2 MB) -> %30 + 0.5 * %70 = %65
        val half = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = true,
            bufferedBytes = 1_000_000L,
            requiredBytes = 2_000_000L,
            previousProgress = 30f
        )
        assertEquals(65f, half.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Buffering, half.phase)
    }

    @Test
    fun bufferingPhaseFullDoesNotReach100BeforeFirstFrame() {
        // Tampon tamamen dolu ama henüz ilk kare gelmedi -> %99 ile sınırlanır, %100 olamaz
        val full = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = true,
            bufferedBytes = 2_000_000L,
            requiredBytes = 2_000_000L,
            firstFrameRendered = false,
            previousProgress = 65f
        )
        assertEquals(99f, full.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Buffering, full.phase)
        assertFalse(full.isFirstFrame)
    }

    @Test
    fun hundredPercentOnlyOnFirstFrame() {
        val frame = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = true,
            bufferedBytes = 2_000_000L,
            requiredBytes = 2_000_000L,
            firstFrameRendered = true,
            previousProgress = 99f
        )
        assertEquals(100f, frame.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Buffering, frame.phase)
        assertTrue(frame.isFirstFrame)
    }

    @Test
    fun monotonicityGuaranteedAcrossFluctuations() {
        // İlerleme %65'e ulaştı
        val s1 = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = true,
            bufferedBytes = 1_000_000L,
            requiredBytes = 2_000_000L,
            previousProgress = 60f
        )
        assertEquals(65f, s1.progress, 0.001f)

        // Ağ dalgalanması ile tampon geçici olarak 0'a düşerse ilerleme geri gitmemeli
        val s2 = TorrentLoadProgressRules.calculateProgress(
            hasMetadata = true,
            bufferedBytes = 0L,
            requiredBytes = 2_000_000L,
            previousProgress = s1.progress
        )
        assertEquals(65f, s2.progress, 0.001f)
    }

    @Test
    fun fallbackOnErrorReturnsFallbackState() {
        val fallback = TorrentLoadProgressRules.fallbackState()
        assertTrue(fallback.fallbackToStandard)

        // Hata durumunda sessizce standart görünüme düşüş kanıtı
        val errorState = TorrentLoadProgressRules.safeCalculateProgress(
            hasMetadata = true,
            bufferedBytes = 1_000_000L,
            requiredBytes = 2_000_000L,
            hasError = true
        )
        assertTrue(errorState.fallbackToStandard)

        // Hata yoksa normal ilerleme dönmeli
        val normalState = TorrentLoadProgressRules.safeCalculateProgress(
            hasMetadata = true,
            bufferedBytes = 0L,
            requiredBytes = 2_000_000L,
            hasError = false
        )
        assertFalse(normalState.fallbackToStandard)
        assertEquals(30f, normalState.progress, 0.001f)
    }

    @Test
    fun safeCalculateProgressWithSnapshot() {
        val snapshot = TorrentLoadProgressRules.TorrentLoadSnapshot(
            hasMetadata = true,
            bufferedBytes = 1_000_000L,
            requiredBytes = 2_000_000L
        )
        val state = TorrentLoadProgressRules.safeCalculateProgress(snapshot, previousProgress = 30f)
        assertEquals(65f, state.progress, 0.001f)
        assertEquals(TorrentLoadProgressRules.Phase.Buffering, state.phase)
        assertFalse(state.fallbackToStandard)

        val errorState = TorrentLoadProgressRules.safeCalculateProgress(snapshot, hasError = true)
        assertTrue(errorState.fallbackToStandard)
    }
}
