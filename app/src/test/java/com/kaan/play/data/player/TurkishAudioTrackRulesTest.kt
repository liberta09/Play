package com.kaan.play.data.player

import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurkishAudioTrackRulesTest {

    private fun stream(url: String, dubbed: Boolean = false, res: Int? = 1080, size: Long? = 1_000_000L) =
        PlayableStream(
            source = StreamSource(1L, "TestAddon"),
            url = url,
            resolution = res,
            sizeBytes = size,
            headers = emptyMap(),
            subtitles = emptyList(),
            dubbed = dubbed,
        )

    @Test
    fun recognizesTurkishByLanguageCodesAndLabelsWithoutFalsePositives() {
        assertTrue(TurkishAudioTrackRules.isTurkishTrack("tr", null))
        assertTrue(TurkishAudioTrackRules.isTurkishTrack("tur", null))
        assertTrue(TurkishAudioTrackRules.isTurkishTrack("tr-TR", null))
        assertTrue(TurkishAudioTrackRules.isTurkishTrack("TR_tr", null))
        assertTrue(TurkishAudioTrackRules.isTurkishTrack(null, "Türkçe"))
        assertTrue(TurkishAudioTrackRules.isTurkishTrack("und", "Turkish Audio"))
        assertTrue(TurkishAudioTrackRules.isTurkishTrack(null, "Ses [TR]"))
        assertTrue(TurkishAudioTrackRules.isTurkishTrack(null, "Dublaj AAC"))

        assertFalse(TurkishAudioTrackRules.isTurkishTrack("en", "English"))
        assertFalse(TurkishAudioTrackRules.isTurkishTrack("und", "Track 1"))
        assertFalse(TurkishAudioTrackRules.isTurkishTrack(null, "Extra Stereo"))
        assertFalse(TurkishAudioTrackRules.isTurkishTrack(null, "Matrix"))
        assertFalse(TurkishAudioTrackRules.isTurkishTrack("und", "Dublajsız Türkçe Altyazılı"))
    }

    @Test
    fun selectsTurkishTrackWhenFoundOrSwitchesToNextDubAfterTimeout() {
        val dub1 = stream("https://a/dub1", dubbed = true)
        val dub2 = stream("https://a/dub2", dubbed = true)
        val fastAlt = stream("https://a/sub", dubbed = false, size = 500_000L)

        val verifier = TurkishAudioVerifier(scanTimeoutMs = 7_000L)
        verifier.begin(dub1, shouldVerify = true)
        assertTrue(verifier.scanning)

        val beforeStart = verifier.evaluate(
            stream = dub1,
            tracks = listOf(AudioTrackInfo(0, "en", "English", isSelected = true)),
            playbackStarted = false,
            nowMs = 10_000L,
            remainingDubbedCandidates = listOf(dub2),
            remainingAlternativeCandidates = listOf(fastAlt),
            lastWorking = null,
        )
        assertEquals(TurkishVerifyDecision.KeepWaiting, beforeStart)

        // İlk kare 10_000ms'de gelir; 5 sn sonra (15_000ms) hâlâ bekliyor.
        val startCheck = verifier.evaluate(
            stream = dub1,
            tracks = listOf(AudioTrackInfo(0, "en", "English", isSelected = true)),
            playbackStarted = true,
            nowMs = 10_000L,
            remainingDubbedCandidates = listOf(dub2),
            remainingAlternativeCandidates = listOf(fastAlt),
            lastWorking = null,
        )
        assertEquals(TurkishVerifyDecision.KeepWaiting, startCheck)

        val at5s = verifier.evaluate(
            stream = dub1,
            tracks = listOf(AudioTrackInfo(0, "en", "English", isSelected = true)),
            playbackStarted = true,
            nowMs = 15_000L,
            remainingDubbedCandidates = listOf(dub2),
            remainingAlternativeCandidates = listOf(fastAlt),
            lastWorking = null,
        )
        assertEquals(TurkishVerifyDecision.KeepWaiting, at5s)

        // 7 sn doldu (17_000ms): sıradaki dublajlı adaya geçilir.
        val at7s = verifier.evaluate(
            stream = dub1,
            tracks = listOf(AudioTrackInfo(0, "en", "English", isSelected = true)),
            playbackStarted = true,
            nowMs = 17_000L,
            remainingDubbedCandidates = listOf(dub2),
            remainingAlternativeCandidates = listOf(fastAlt),
            lastWorking = null,
        )
        assertEquals(
            TurkishVerifyDecision.TryNextDubbed(dub2, TurkishAudioNotice.UnverifiedTryingNextDub),
            at7s,
        )

        verifier.begin(dub2, shouldVerify = true)
        verifier.evaluate(dub2, emptyList(), playbackStarted = true, nowMs = 30_000L, emptyList(), listOf(fastAlt), null)
        val fallback = verifier.evaluate(
            stream = dub2,
            tracks = listOf(AudioTrackInfo(0, "en", null, isSelected = true)),
            playbackStarted = true,
            nowMs = 37_000L,
            remainingDubbedCandidates = emptyList(),
            remainingAlternativeCandidates = listOf(fastAlt),
            lastWorking = null,
        )
        assertEquals(
            TurkishVerifyDecision.FallbackToAlternative(fastAlt, TurkishAudioNotice.NotFoundOpeningFastAlternative),
            fallback,
        )
    }

    @Test
    fun selectsSupportedTurkishTrackAndStopsWhenUserChoosesManually() {
        val dub = stream("https://a/dub", dubbed = true)
        val verifier = TurkishAudioVerifier(scanTimeoutMs = 5_000L)
        verifier.begin(dub, shouldVerify = true)

        val select = verifier.evaluate(
            stream = dub,
            tracks = listOf(
                AudioTrackInfo(0, "en", "English", isSelected = true),
                AudioTrackInfo(1, "tr", "Türkçe", isSelected = false),
            ),
            playbackStarted = true,
            nowMs = 1_000L,
            remainingDubbedCandidates = emptyList(),
            remainingAlternativeCandidates = emptyList(),
            lastWorking = null,
        )
        assertEquals(TurkishVerifyDecision.SelectTrack(1), select)

        val verified = verifier.evaluate(
            stream = dub,
            tracks = listOf(
                AudioTrackInfo(0, "en", "English", isSelected = false),
                AudioTrackInfo(1, "tr", "Türkçe", isSelected = true),
            ),
            playbackStarted = true,
            nowMs = 1_200L,
            remainingDubbedCandidates = emptyList(),
            remainingAlternativeCandidates = emptyList(),
            lastWorking = null,
        )
        assertEquals(TurkishVerifyDecision.Verified, verified)
        assertFalse(verifier.scanning)

        val dub2 = stream("https://a/dub2", dubbed = true)
        verifier.begin(dub2, shouldVerify = true)
        assertTrue(verifier.scanning)
        verifier.stopByUser()
        assertFalse(verifier.scanning)
        verifier.begin(dub2, shouldVerify = true)
        assertFalse(verifier.scanning)
    }

    @Test
    fun unsupportedOrUnselectableTurkishTrackFallsBackAppropriately() {
        val dub1 = stream("https://a/dub1", dubbed = true)
        val dub2 = stream("https://a/dub2", dubbed = true)
        val verifier = TurkishAudioVerifier(scanTimeoutMs = 7_000L)
        verifier.begin(dub1, shouldVerify = true)

        val unsupported = verifier.evaluate(
            stream = dub1,
            tracks = listOf(AudioTrackInfo(-1, "tr", "DTS Turkish", isSelected = false, isSupported = false)),
            playbackStarted = true,
            nowMs = 500L,
            remainingDubbedCandidates = listOf(dub2),
            remainingAlternativeCandidates = emptyList(),
            lastWorking = null,
        )
        assertEquals(
            TurkishVerifyDecision.TryNextDubbed(dub2, TurkishAudioNotice.UnsupportedTryingAnother),
            unsupported,
        )

        // Desteklenen parça seçilemezse (timeout sonrası hâlâ seçili değilse) ve başka kaynak yoksa mevcut kaynakta kalır.
        verifier.begin(dub2, shouldVerify = true)
        val tracks = listOf(
            AudioTrackInfo(0, "en", "English", isSelected = true),
            AudioTrackInfo(1, "tr", "Türkçe", isSelected = false),
        )
        assertEquals(
            TurkishVerifyDecision.SelectTrack(1),
            verifier.evaluate(dub2, tracks, playbackStarted = true, nowMs = 1_000L, emptyList(), emptyList(), null),
        )
        assertEquals(
            TurkishVerifyDecision.ContinueCurrent(TurkishAudioNotice.FoundButSelectionFailed),
            verifier.evaluate(
                dub2,
                tracks,
                playbackStarted = true,
                nowMs = 1_000L + TurkishAudioVerifier.SELECT_SETTLE_MS + 100L,
                emptyList(),
                emptyList(),
                null,
            ),
        )
    }
}
