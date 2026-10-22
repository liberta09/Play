package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.SourcePref
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceCoordinatorTest {

    private val source1 = StreamSource(1, "Sahte A")
    private val source2 = StreamSource(2, "Sahte B")
    private val request = StreamRequest(1, "tt1", MediaType.Movie)

    private fun stream(
        source: StreamSource,
        url: String,
        dub: Boolean = false,
        res: Int? = 1080,
        size: Long? = 2_000_000_000L,
        mime: String? = null,
    ) = PlayableStream(source, url, res, size, emptyMap(), emptyList(), mimeType = mime, dubbed = dub)

    private class Rig(
        val scope: TestScope,
        results: Flow<StreamResult>,
        preferDub: Boolean = true,
        autoSource: Boolean = true,
        preference: SourcePref? = null,
        probe: suspend (PlayableStream) -> Long? = { null },
        request: StreamRequest,
    ) {
        val loaded = mutableListOf<Pair<PlayableStream, Long>>()
        val remembered = mutableListOf<PlayableStream>()
        var positionMs = 0L
        val coordinator = SourceCoordinator(
            scope = scope,
            request = request,
            streamsFor = { results },
            preferenceFor = { preference },
            remember = { _, s -> remembered += s },
            sizeProbe = probe,
            preferDub = preferDub,
            autoSource = autoSource,
            load = { s, p -> loaded += s to p },
            position = { positionMs },
            clock = { scope.currentTime },
        )
        val lastUrl get() = loaded.lastOrNull()?.first?.url
    }

    private fun found(source: StreamSource, vararg streams: PlayableStream) = StreamResult.Found(source, streams.toList())

    @Test
    fun startsWithTheDubbedSourceAsSoonAsItArrives() = runTest {
        val sub = stream(source1, "https://a/sub")
        val dub = stream(source2, "https://b/dub", dub = true, res = 720)
        val rig = Rig(this, flow {
            emit(found(source1, sub))
            delay(1_000)
            emit(found(source2, dub))
            delay(60_000)
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertEquals(SourcePhase.Resolving, rig.coordinator.phase)
        assertTrue("dublaj beklenirken altyazılıyla başlanmaz", rig.loaded.isEmpty())

        advanceTimeBy(1_100)
        assertEquals(listOf("https://b/dub"), rig.loaded.map { it.first.url })
        assertEquals(SourcePhase.Starting, rig.coordinator.phase)
        rig.coordinator.onFirstFrame()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
        advanceUntilIdle()
    }

    @Test
    fun startsWithSubtitledAfterThreeSecondsWhenNoDubArrives() = runTest {
        val sub = stream(source1, "https://a/sub")
        val rig = Rig(this, flow {
            emit(found(source1, sub))
            delay(60_000)
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(2_900)
        assertTrue(rig.loaded.isEmpty())
        advanceTimeBy(200)
        assertEquals(listOf("https://a/sub"), rig.loaded.map { it.first.url })
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun doesNotWaitWhenAllSourcesAnsweredWithoutDub() = runTest {
        val sub = stream(source1, "https://a/sub")
        val rig = Rig(this, flow { emit(found(source1, sub)) }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertEquals(listOf("https://a/sub"), rig.loaded.map { it.first.url })
        assertEquals(0L, currentTime) // sanal zaman ilerlemedi: beklenmedi
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun startsAtOnceWhenDubPreferenceIsOff() = runTest {
        val sub = stream(source1, "https://a/sub")
        val rig = Rig(this, flow { emit(found(source1, sub)); delay(60_000) }, preferDub = false, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertEquals(listOf("https://a/sub"), rig.loaded.map { it.first.url })
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun noSourcesAtAllEndsInFailedState() = runTest {
        val rig = Rig(this, flow { emit(StreamResult.Failed(source1, FailureReason.Timeout)) }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceUntilIdle()
        assertEquals(SourcePhase.Failed, rig.coordinator.phase)
        assertTrue(rig.loaded.isEmpty())
        assertEquals(listOf(source1), rig.coordinator.failedSources)
    }

    @Test
    fun aSourceThatNeverRendersAFrameIsSkippedAfterTenSeconds() = runTest {
        val a = stream(source1, "https://a/1", size = 1_000_000_000L)
        val b = stream(source1, "https://a/2", size = 2_000_000_000L)
        val rig = Rig(this, flow { emit(found(source1, a, b)) }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertEquals("https://a/1", rig.lastUrl)
        advanceTimeBy(9_900)
        assertEquals(1, rig.loaded.size)
        advanceTimeBy(200)
        assertEquals("https://a/2", rig.lastUrl)
        assertTrue(rig.coordinator.notice is SourceNotice.Skipped)
        // Hepsi bozuk: siyah ekranda bırakılmaz, "Başka kaynak seç".
        advanceTimeBy(10_100)
        assertEquals(SourcePhase.Failed, rig.coordinator.phase)
        assertEquals(2, rig.loaded.size)
    }

    @Test
    fun aPlayerErrorBeforeTheFirstFrameMovesToTheNextSource() = runTest {
        val a = stream(source1, "https://a/1", size = 1_000_000_000L)
        val b = stream(source1, "https://a/2", size = 2_000_000_000L)
        val rig = Rig(this, flow { emit(found(source1, a, b)) }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertTrue(rig.coordinator.onFailure("ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=404"))
        assertEquals("https://a/2", rig.lastUrl)
        val notice = rig.coordinator.notice as SourceNotice.Skipped
        assertEquals("ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=404", notice.detail)
        rig.coordinator.onFirstFrame()
        // Oynarken gelen hata arayüze bırakılır.
        assertFalse(rig.coordinator.onFailure("later"))
        advanceUntilIdle()
    }

    @Test
    fun failedSourceIsNotRetriedAndLateSourcesAreUsedWhenAllFailedSoFar() = runTest {
        val a = stream(source1, "https://a/1")
        val late = stream(source2, "https://b/late")
        val rig = Rig(this, flow {
            emit(found(source1, a))
            delay(30_000)
            emit(found(source2, late))
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(3_100) // 3 sn bekleme sonrası a başlar
        assertEquals("https://a/1", rig.lastUrl)
        assertTrue(rig.coordinator.onFailure("x"))
        // Başka aday yok ama kaynaklar hâlâ geliyor: bekler (siyah ekran değil, yükleniyor).
        assertEquals(SourcePhase.Resolving, rig.coordinator.phase)
        advanceTimeBy(30_000)
        assertEquals("https://b/late", rig.lastUrl)
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun manualPickResumesAtTheSameSecondAndFallsBackWhenItFails() = runTest {
        val a = stream(source1, "https://a/1")
        val b = stream(source2, "https://b/1")
        val rig = Rig(this, flow { emit(found(source1, a)); emit(found(source2, b)) }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        rig.coordinator.onFirstFrame()
        val first = rig.lastUrl
        rig.positionMs = 754_000

        val target = if (first == a.url) b else a
        rig.coordinator.pick(target)
        assertEquals(target.url, rig.lastUrl)
        assertEquals(754_000L, rig.loaded.last().second)
        assertTrue(rig.coordinator.switching)
        runCurrent()
        assertEquals(listOf(target), rig.remembered)

        // Yeni kaynak açılmadı: önceki kaynağa aynı saniyeden dönülür, bildirim ayrıntıyla.
        assertTrue(rig.coordinator.onFailure("ERROR_CODE_TIMEOUT (1003)"))
        assertEquals(first, rig.lastUrl)
        assertEquals(754_000L, rig.loaded.last().second)
        assertFalse(rig.coordinator.switching)
        val notice = rig.coordinator.notice as SourceNotice.SwitchFailed
        assertEquals("ERROR_CODE_TIMEOUT (1003)", notice.detail)
        advanceTimeBy(400) // kaynak değişiminden hemen sonra gelen ilk kare olayı eski kaynağa aittir (yok sayılır)
        rig.coordinator.onFirstFrame()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
        advanceUntilIdle()
    }

    @Test
    fun manualPickThatOpensClearsTheSwitchingState() = runTest {
        val a = stream(source1, "https://a/1")
        val b = stream(source2, "https://b/1")
        val rig = Rig(this, flow { emit(found(source1, a, b)) }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        rig.coordinator.onFirstFrame()
        rig.coordinator.pick(rig.coordinator.streams.first { it.url != rig.lastUrl })
        assertTrue(rig.coordinator.switching)
        rig.coordinator.onFirstFrame() // eski kaynaktan kalan olay: yok sayılır
        assertTrue(rig.coordinator.switching)
        advanceTimeBy(400)
        rig.coordinator.onFirstFrame()
        assertFalse(rig.coordinator.switching)
        assertNull(rig.coordinator.notice)
        advanceUntilIdle()
    }

    @Test
    fun aLateDubbedSourceIsOfferedWithoutStoppingPlayback() = runTest {
        val sub = stream(source1, "https://a/sub")
        val dub = stream(source2, "https://b/dub", dub = true)
        val rig = Rig(this, flow {
            emit(found(source1, sub))
            delay(6_000)
            emit(found(source2, dub))
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(3_100)
        assertEquals("https://a/sub", rig.lastUrl)
        rig.coordinator.onFirstFrame()
        assertNull(rig.coordinator.dubSuggestion)
        advanceTimeBy(3_500)
        assertEquals(dub, rig.coordinator.dubSuggestion)
        assertEquals("oynatma kesilmedi", 1, rig.loaded.size)

        // Geç: kaldığı saniyeden devam.
        rig.positionMs = 61_000
        rig.coordinator.acceptDubSuggestion()
        assertEquals(dub.url, rig.lastUrl)
        assertEquals(61_000L, rig.loaded.last().second)
        assertNull(rig.coordinator.dubSuggestion)
        runCurrent()
        assertEquals(listOf(dub), rig.remembered)
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun dismissingTheDubOfferKeepsItClosed() = runTest {
        val sub = stream(source1, "https://a/sub")
        val dub1 = stream(source2, "https://b/dub", dub = true)
        val dub2 = stream(StreamSource(3, "C"), "https://c/dub", dub = true)
        val rig = Rig(this, flow {
            emit(found(source1, sub))
            delay(5_000)
            emit(found(source2, dub1))
            delay(5_000)
            emit(found(StreamSource(3, "C"), dub2))
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(3_100)
        rig.coordinator.onFirstFrame()
        advanceTimeBy(2_000)
        assertEquals(dub1, rig.coordinator.dubSuggestion)
        rig.coordinator.dismissDubSuggestion()
        advanceUntilIdle()
        assertNull(rig.coordinator.dubSuggestion)
        assertEquals(1, rig.loaded.size)
    }

    @Test
    fun noOfferWhenAlreadyDubbedOrUserPickedASource() = runTest {
        val dub = stream(source1, "https://a/dub", dub = true)
        val sub = stream(source2, "https://b/sub")
        val dub2 = stream(StreamSource(3, "C"), "https://c/dub", dub = true)
        val rig = Rig(this, flow {
            emit(found(source1, dub))
            emit(found(source2, sub))
            delay(5_000)
            emit(found(StreamSource(3, "C"), dub2))
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertEquals("https://a/dub", rig.lastUrl)
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
        assertNull("dublajlı oynarken öneri yok", rig.coordinator.dubSuggestion)

        // Elle (altyazılı) seçildiyse yine öneri yok.
        rig.coordinator.pick(sub)
        rig.coordinator.onFirstFrame()
        assertNull(rig.coordinator.dubSuggestion)
    }

    @Test
    fun givenInitialStreamPlaysFirstWithFallbacksAfter() = runTest {
        val initial = stream(source1, "https://a/initial")
        val fallback = stream(source1, "https://a/fallback")
        val rig = Rig(this, flow { delay(60_000) }, request = request)
        rig.coordinator.start(initial, listOf(fallback), 42_000)
        runCurrent()
        assertEquals(listOf(initial to 42_000L), rig.loaded)
        assertTrue(rig.coordinator.onFailure("x"))
        assertEquals("https://a/fallback", rig.lastUrl)
        assertEquals(42_000L, rig.loaded.last().second)
        rig.coordinator.onFirstFrame()
        advanceUntilIdle()
    }

    @Test
    fun withAutoSourceOffTheListLoadsOnlyWhenAsked() = runTest {
        var asked = 0
        val a = stream(source1, "https://a/1")
        val results = flow { asked++; emit(found(source1, a)) }
        val rig = Rig(this, results, autoSource = false, request = request)
        rig.coordinator.start(a, emptyList(), 0)
        advanceUntilIdle()
        assertEquals(0, asked)
        rig.coordinator.ensureSources()
        advanceUntilIdle()
        assertEquals(1, asked)
        assertEquals(listOf(a), rig.coordinator.streams)
    }

    @Test
    fun foundSizesAreUsedAndHlsAndTorrentAreNotProbed() = runTest {
        val direct = stream(source1, "https://a/file.mkv", size = null)
        val hls = stream(source1, "https://a/list.m3u8", size = null)
        val torrent = stream(source1, "magnet:?xt=urn:btih:cccccccccccccccccccccccccccccccccccccccc", size = null)
        val known = stream(source2, "https://b/known.mp4", size = 3_000_000_000L)
        val probed = mutableListOf<String>()
        val rig = Rig(
            this,
            flow { emit(found(source1, direct, hls, torrent)); emit(found(source2, known)) },
            probe = { probed += it.url; 7_700_000_000L },
            request = request,
        )
        rig.coordinator.start(null, emptyList(), 0)
        advanceUntilIdle()
        assertEquals(listOf("https://a/file.mkv"), probed)
        assertEquals(7_700_000_000L, rig.coordinator.sizeOf(direct))
        assertNull(rig.coordinator.sizeOf(hls))
        assertNull(rig.coordinator.sizeOf(torrent))
        assertEquals(3_000_000_000L, rig.coordinator.sizeOf(known))

        // Torrent üst bilgisinden gelen boyut da kaydedilir.
        rig.coordinator.reportSize(torrent.url, 22_000_000_000L)
        assertEquals(22_000_000_000L, rig.coordinator.sizeOf(torrent))
        rig.coordinator.onFirstFrame()
    }

    @Test
    fun aFailingSizeProbeIsSilent() = runTest {
        val a = stream(source1, "https://a/file.mkv", size = null)
        val rig = Rig(this, flow { emit(found(source1, a)) }, probe = { null }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceUntilIdle()
        assertNull(rig.coordinator.sizeOf(a))
        assertEquals(listOf(a.url), rig.loaded.map { it.first.url })
        rig.coordinator.onFirstFrame()
    }

    @Test
    fun theUsersPreviousChoiceStartsFirstInItsGroup() = runTest {
        val a = stream(source1, "https://a/1", size = 1_000_000_000L)
        val b = stream(source2, "https://b/1", size = 2_000_000_000L)
        val rig = Rig(this, flow { emit(found(source1, a, b.copy(source = source2))) }, preference = SourcePref(2, "Sahte B"), request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceUntilIdle()
        assertEquals("https://b/1", rig.loaded.first().first.url)
        rig.coordinator.onFirstFrame()
    }

    @Test
    fun skippingWorksForTorrentWithALongerTimeout() = runTest {
        val torrent = stream(source1, "magnet:?xt=urn:btih:dddddddddddddddddddddddddddddddddddddddd")
        val direct = stream(source2, "https://b/1", res = 480)
        val rig = Rig(this, flow { emit(found(source1, torrent)); emit(found(source2, direct)) }, request = request)
        // Doğrudan bağlantı torrentten önce gelir.
        rig.coordinator.start(null, emptyList(), 0)
        advanceUntilIdle()
        assertEquals("https://b/1", rig.loaded.first().first.url)
        rig.coordinator.onFirstFrame()
    }
}
