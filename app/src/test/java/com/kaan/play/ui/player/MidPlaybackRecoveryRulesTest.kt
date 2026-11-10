package com.kaan.play.ui.player

import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MidPlaybackRecoveryRulesTest {

    private val source = StreamSource(1L, "TestProvider")

    private fun stream(url: String, dubbed: Boolean = false) = PlayableStream(
        source = source,
        url = url,
        resolution = 1080,
        sizeBytes = 1_000_000_000L,
        headers = emptyMap(),
        subtitles = emptyList(),
        dubbed = dubbed,
    )

    private val stream1 = stream("https://stream1", dubbed = true)
    private val stream2 = stream("https://stream2", dubbed = false)
    private val stream3 = stream("https://stream3", dubbed = true)

    @Test
    fun whenPlaying_firstFailure_retriesSameStreamOnce() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = false,
            switchCount = 0,
            candidates = listOf(stream2),
            isManual = false,
        )
        assertEquals(MidPlaybackRecoveryRules.Decision.RetrySame(stream1), decision)
    }

    @Test
    fun whenPlaying_secondFailure_switchesToNextCandidate() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = true,
            switchCount = 0,
            candidates = listOf(stream2, stream3),
            isManual = false,
        )
        assertEquals(
            MidPlaybackRecoveryRules.Decision.Switch(
                candidate = stream2,
                notice = MidPlaybackRecoveryRules.NoticeType.STREAM_DROPPED_TRYING_NEXT,
            ),
            decision,
        )
    }

    @Test
    fun whenPlaying_manualSource_notifiesSourceSwitched() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = true,
            switchCount = 1,
            candidates = listOf(stream2),
            isManual = true,
        )
        assertEquals(
            MidPlaybackRecoveryRules.Decision.Switch(
                candidate = stream2,
                notice = MidPlaybackRecoveryRules.NoticeType.SOURCE_SWITCHED,
            ),
            decision,
        )
    }

    @Test
    fun whenSwitchesReachMax_fails() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = true,
            switchCount = 3,
            candidates = listOf(stream2),
        )
        assertEquals(MidPlaybackRecoveryRules.Decision.Fail, decision)
    }

    @Test
    fun whenNoCandidatesLeft_fails() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = true,
            switchCount = 0,
            candidates = emptyList(),
        )
        assertEquals(MidPlaybackRecoveryRules.Decision.Fail, decision)
    }

    @Test
    fun whenPhaseIsNotPlaying_fails() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Starting,
            currentStream = stream1,
            hasRetriedCurrentStream = false,
            switchCount = 0,
            candidates = listOf(stream2),
        )
        assertEquals(MidPlaybackRecoveryRules.Decision.Fail, decision)
    }

    @Test
    fun whenOffline_doesNotSwitch() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = false,
            switchCount = 0,
            candidates = listOf(stream2),
            isOffline = true,
        )
        assertEquals(MidPlaybackRecoveryRules.Decision.Fail, decision)
    }

    @Test
    fun whenLeaving_doesNotSwitch() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = false,
            switchCount = 0,
            candidates = listOf(stream2),
            isLeaving = true,
        )
        assertEquals(MidPlaybackRecoveryRules.Decision.Fail, decision)
    }

    @Test
    fun whenLive_skipsSameSourceRetryAndSwitchesDirectly() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = false,
            switchCount = 0,
            candidates = listOf(stream2),
            isLive = true,
            isNetworkError = true,
        )
        assertEquals(
            MidPlaybackRecoveryRules.Decision.Switch(
                candidate = stream2,
                notice = MidPlaybackRecoveryRules.NoticeType.STREAM_DROPPED_TRYING_NEXT,
            ),
            decision,
        )
    }

    @Test
    fun whenNonNetworkError_skipsSameSourceRetryAndSwitchesDirectly() {
        val decision = MidPlaybackRecoveryRules.evaluate(
            phase = SourcePhase.Playing,
            currentStream = stream1,
            hasRetriedCurrentStream = false,
            switchCount = 0,
            candidates = listOf(stream2),
            isLive = false,
            isNetworkError = false,
        )
        assertEquals(
            MidPlaybackRecoveryRules.Decision.Switch(
                candidate = stream2,
                notice = MidPlaybackRecoveryRules.NoticeType.STREAM_DROPPED_TRYING_NEXT,
            ),
            decision,
        )
    }

    @Test
    fun isNetworkErrorRecognizesIoAndExcludesDecoderOrParsing() {
        assertTrue(MidPlaybackRecoveryRules.isNetworkError("ERROR_CODE_IO_NETWORK_CONNECTION_CLOSED (-1004)"))
        assertTrue(MidPlaybackRecoveryRules.isNetworkError("ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=503"))
        assertFalse(MidPlaybackRecoveryRules.isNetworkError("ERROR_CODE_DECODER_INIT_FAILED (4001)"))
        assertFalse(MidPlaybackRecoveryRules.isNetworkError("ERROR_CODE_PARSING_CONTAINER_MALFORMED (3001)"))
    }
}
