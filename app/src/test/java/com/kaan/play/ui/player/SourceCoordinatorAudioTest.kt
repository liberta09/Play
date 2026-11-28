package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamOwnership
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamVerdicts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sesi bu cihazda çalınamayan kaynak: otomatik modda sıradakine geçilir, elle seçildiyse yalnızca ileti çıkar. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceCoordinatorAudioTest {
    private val hub = StreamSource(1, "4KHDHub")
    private val other = StreamSource(2, "Jinbe")
    private val movie = StreamRequest(1368337, "tt33764258", MediaType.Movie)
    private val key = StreamOwnership.keyOf(movie)

    private fun stream(source: StreamSource, url: String) =
        PlayableStream(source, url, 1080, null, emptyMap(), emptyList(), dubbed = true, forKey = key)

    private class Rig(
        scope: TestScope,
        request: StreamRequest,
        streams: List<PlayableStream>,
        autoSource: Boolean = true,
        val verdicts: StreamVerdicts = StreamVerdicts(),
    ) {
        val loaded = mutableListOf<Pair<String, Long>>()
        var positionMs = 0L
        val coordinator = SourceCoordinator(
            scope = scope,
            request = request,
            streamsFor = {
                flow {
                    streams.groupBy { it.source }.forEach { (source, list) -> emit(StreamResult.Found(source, list)) }
                    delay(60_000)
                }
            },
            verdicts = verdicts,
            autoSource = autoSource,
            load = { s, start -> loaded += s.url to start },
            position = { positionMs },
            clock = { scope.currentTime },
            rankingDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun autoMode_unplayableAudio_skipsToNextSourceFromSamePosition() = runTest {
        val silent = stream(hub, "https://silent/1")
        val good = stream(other, "https://good/2")
        val rig = Rig(this, movie, listOf(silent, good))
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(1_000)
        assertEquals("https://silent/1", rig.loaded.single().first)
        rig.coordinator.onFirstFrame()
        rig.positionMs = 12_000

        rig.coordinator.onAudioUnplayable()
        advanceTimeBy(300)

        assertEquals("https://good/2" to 12_000L, rig.loaded.last())
        assertTrue(rig.verdicts.isRejected(key, "https://silent/1"))
        assertEquals(listOf("https://good/2"), rig.coordinator.streams.map { it.url })
        assertEquals(SourceNotice.Skipped("audio-unsupported"), rig.coordinator.notice)
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun sameSourceIsReportedOnlyOnce() = runTest {
        val picked = stream(hub, "https://silent/1")
        val rig = Rig(this, movie, listOf(picked), autoSource = false)
        rig.coordinator.start(picked, emptyList(), 0)
        advanceTimeBy(1_000)
        rig.coordinator.onFirstFrame()
        rig.coordinator.onAudioUnplayable()
        assertEquals(SourceNotice.AudioUnsupported, rig.coordinator.notice)
        rig.coordinator.clearNotice()
        // Parça listesi yeniden bildirildi (ör. altyazı seçimi): aynı kaynak için ileti tekrar çıkmaz.
        rig.coordinator.onAudioUnplayable()
        assertEquals(null, rig.coordinator.notice)
        advanceUntilIdle()
    }

    @Test
    fun manualSelection_unplayableAudio_showsNoticeAndKeepsSource() = runTest {
        val picked = stream(hub, "https://silent/1")
        val good = stream(other, "https://good/2")
        val rig = Rig(this, movie, listOf(picked, good), autoSource = false)
        rig.coordinator.start(picked, emptyList(), 0)
        advanceTimeBy(1_000)
        rig.coordinator.onFirstFrame()

        rig.coordinator.onAudioUnplayable()
        advanceTimeBy(300)

        assertEquals(listOf("https://silent/1"), rig.loaded.map { it.first })
        assertEquals(SourceNotice.AudioUnsupported, rig.coordinator.notice)
        assertFalse(rig.verdicts.isRejected(key, "https://silent/1"))
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
        advanceUntilIdle()
    }
}
