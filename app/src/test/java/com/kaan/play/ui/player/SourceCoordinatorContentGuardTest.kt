package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StartPolicy
import com.kaan.play.data.stream.StreamOwnership
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamVerdicts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** İçerik güvencesi: süresi içerikle bağdaşmayan kaynak elenir; başka içeriğin kaynağı yedek listeye karışsa bile oynatılmaz. */
@OptIn(ExperimentalCoroutinesApi::class)
class SourceCoordinatorContentGuardTest {
    private val bogard = StreamSource(1, "Bogard")
    private val other = StreamSource(2, "Diger")
    private val movie = StreamRequest(120, "tt0120737", MediaType.Movie)
    private val key = StreamOwnership.keyOf(movie)
    private val min = 60_000L

    private fun stream(source: StreamSource, url: String, forKey: String? = key, dub: Boolean = false) =
        PlayableStream(source, url, 1080, null, emptyMap(), emptyList(), dubbed = dub, forKey = forKey)

    private class Rig(
        scope: TestScope,
        request: StreamRequest,
        streams: List<PlayableStream>,
        runtime: Int?,
        val verdicts: StreamVerdicts = StreamVerdicts(),
    ) {
        val loaded = mutableListOf<String>()
        val coordinator = SourceCoordinator(
            scope = scope,
            request = request,
            streamsFor = {
                flow {
                    streams.groupBy { it.source }.forEach { (source, list) -> emit(StreamResult.Found(source, list)) }
                    delay(60_000)
                }
            },
            runtimeFor = { runtime },
            verdicts = verdicts,
            load = { s, _ -> loaded += s.url },
            clock = { scope.currentTime },
            rankingDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun wrongLengthVideoIsRejected_nextSourceIsTried_andRememberedAsRejected() = runTest {
        val bad = stream(bogard, "https://bad/1", dub = true)
        val good = stream(other, "https://good/2")
        val rig = Rig(this, movie, listOf(bad, good), runtime = 178)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(1_000)
        assertEquals(listOf("https://bad/1"), rig.loaded) // dublajli onde
        rig.coordinator.onFirstFrame()
        rig.coordinator.onDuration(33 * min + 25_000) // 33:25
        advanceTimeBy(100)
        rig.coordinator.onFirstFrame() // eski kaynaktan kalan olay: yok sayilir
        assertEquals(SourcePhase.Starting, rig.coordinator.phase)
        advanceTimeBy(300)
        assertEquals(listOf("https://bad/1", "https://good/2"), rig.loaded)
        assertEquals(SourceNotice.Mismatch, rig.coordinator.notice)
        assertTrue(rig.verdicts.isRejected(key, "https://bad/1"))
        assertEquals(listOf("https://good/2"), rig.coordinator.streams.map { it.url })

        // Dogru sure: kabul, ek gecis yok.
        rig.coordinator.onFirstFrame()
        rig.coordinator.onDuration(179 * min)
        advanceTimeBy(100)
        assertEquals(2, rig.loaded.size)
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
        advanceUntilIdle()
    }

    @Test
    fun afterMismatch_nextSourceFailingToOpenFallsThroughToTheFollowingOne() = runTest {
        val bad = stream(bogard, "https://bad/1", dub = true)
        val broken = stream(other, "https://broken/2", dub = true)
        val good = stream(StreamSource(3, "Iyi"), "https://good/3", dub = true)
        val rig = Rig(this, movie, listOf(bad, broken, good), runtime = 178)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(1_000)
        rig.coordinator.onFirstFrame()
        rig.coordinator.onDuration(33 * min)
        advanceTimeBy(100)
        rig.coordinator.onFirstFrame() // bayat olay: faz Starting kalmali ki hata yedege dussun
        assertEquals(2, rig.loaded.size) // yanlis icerik elendi, siradaki denendi
        // Siradaki kaynak acilmiyor (format hatasi): hata ekrani degil, bir sonraki kaynak.
        assertTrue(rig.coordinator.onFailure("format"))
        advanceTimeBy(100)
        assertEquals(3, rig.loaded.size)
        assertEquals("https://good/3", rig.loaded.last())
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun allSourcesRejected_endsInFailedWithEmptyList() = runTest {
        val only = stream(bogard, "https://bad/1")
        val rig = Rig(this, movie, listOf(only), runtime = 178)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 1_000)
        rig.coordinator.onFirstFrame()
        rig.coordinator.onDuration(33 * min)
        runCurrent()
        // Kaynaklar akisi bitmedi (60 sn bekliyor): yeni kaynak gelebilir diye Resolving'de bekler; akis bitince Failed.
        advanceTimeBy(61_000)
        advanceUntilIdle()
        assertEquals(SourcePhase.Failed, rig.coordinator.phase)
        assertTrue(rig.coordinator.streams.isEmpty()) // arayuz "kaynak bulunamadi" gosterir
    }

    @Test
    fun unknownRuntimeOrDurationNeverRejects() = runTest {
        val s = stream(bogard, "https://x/1")
        val rig = Rig(this, movie, listOf(s), runtime = null)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 1_000)
        rig.coordinator.onFirstFrame()
        rig.coordinator.onDuration(5 * min)
        advanceUntilIdle()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
        assertFalse(rig.verdicts.isRejected(key, "https://x/1"))
    }

    @Test
    fun fallbacksOfAnotherContentAreNeverPlayed() = runTest {
        val foreign = stream(other, "https://foreign/1", forKey = "movie:999:0:0")
        val mine = stream(bogard, "https://mine/1")
        val rig = Rig(this, movie, listOf(mine), runtime = 178)
        rig.coordinator.start(null, listOf(foreign), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 1_000)
        assertEquals(listOf("https://mine/1"), rig.loaded)
        // Kendi kaynagi bozulsa bile yabanci yedege dusulmez.
        rig.coordinator.onFailure("http 500")
        advanceTimeBy(1_000)
        assertFalse(rig.loaded.contains("https://foreign/1"))
        advanceUntilIdle()
    }

    @Test
    fun foreignInitialStreamIsIgnored_sourcesAreSearchedAgain() = runTest {
        val foreign = stream(other, "https://foreign/1", forKey = "movie:999:0:0")
        val mine = stream(bogard, "https://mine/1")
        val rig = Rig(this, movie, listOf(mine), runtime = 178)
        rig.coordinator.start(foreign, emptyList(), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 1_000)
        assertEquals(listOf("https://mine/1"), rig.loaded)
        advanceUntilIdle()
    }

    @Test
    fun episodeNeedsClearMismatchOnly() = runTest {
        val ep = StreamRequest(1399, "tt0944947", MediaType.Tv, season = 1, episode = 2)
        val epKey = StreamOwnership.keyOf(ep)
        val s = stream(bogard, "https://x/ep", forKey = epKey)
        val rig = Rig(this, ep, listOf(s), runtime = 45)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 1_000)
        rig.coordinator.onFirstFrame()
        rig.coordinator.onDuration(70 * min) // 1,5 kat: eleme yok
        advanceUntilIdle()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
    }
}
