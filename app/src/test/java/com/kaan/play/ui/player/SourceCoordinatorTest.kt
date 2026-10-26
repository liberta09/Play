package com.kaan.play.ui.player

import com.kaan.play.data.download.Probe
import com.kaan.play.data.download.SourceKind
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.SlowHostMemory
import com.kaan.play.data.stream.StartPolicy
import com.kaan.play.data.stream.SourcePref
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.asCoroutineDispatcher
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
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceCoordinatorTest {

    @Before
    @After
    fun resetSlowHosts() {
        SlowHostMemory.clear()
    }

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
        sourceProbe: (suspend (PlayableStream) -> Probe)? = null,
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
            clock = { scope.testScheduler.currentTime },
            sourceProbe = sourceProbe,
            rankingDispatcher = Dispatchers.Unconfined,
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
    fun openingDubWaitPrefersDubbedArrivingAtFiveSeconds() = runTest {
        val sub = stream(source1, "https://a/sub")
        val dub = stream(source2, "https://b/dub", dub = true, res = 720)
        val rig = Rig(this, flow {
            emit(found(source1, sub))
            delay(5_000)
            emit(found(source2, dub))
            delay(60_000)
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertEquals(SourcePhase.Resolving, rig.coordinator.phase)
        assertTrue(rig.loaded.isEmpty())

        // 3.5 sn'de (eski 3 sn beklemesi aşılmışken) henüz altyazılı başlamamalı, 8 sn beklenmeli
        advanceTimeBy(3_500)
        assertTrue("3.5 sn'de altyazılı başlamamalı, dublaj beklenmeli", rig.loaded.isEmpty())

        // 5 sn'de dublajlı kaynak gelince dublajlı başlamalı
        advanceTimeBy(1_600)
        assertEquals(listOf("https://b/dub"), rig.loaded.map { it.first.url })
        assertEquals(SourcePhase.Starting, rig.coordinator.phase)
    }

    @Test
    fun startsWithSubtitledAfterEightSecondsWhenNoDubArrives() = runTest {
        val sub = stream(source1, "https://a/sub")
        val rig = Rig(this, flow {
            emit(found(source1, sub))
            delay(60_000)
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS - 100)
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
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 100) // dublaj bekleme süresi sonrası a başlar
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
            delay(StartPolicy.DUB_WAIT_MS + 3_000)
            emit(found(source2, dub))
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 100)
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
            delay(StartPolicy.DUB_WAIT_MS + 2_000)
            emit(found(source2, dub1))
            delay(5_000)
            emit(found(StreamSource(3, "C"), dub2))
        }, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        advanceTimeBy(StartPolicy.DUB_WAIT_MS + 100)
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
    @Test
    fun probeRejectsNotMediaAndAdvancesToNextCandidate() = runTest {
        // Boyutu bilinmeyen (bu yüzden SourceMediaRules ile elenmemiş) ama yoklamada HTML/NotMedia dönen sahte kaynak
        val badHtml = stream(source1, "https://a/fake_html", res = 1080, size = null)
        val goodStream = stream(source2, "https://b/real_movie", res = 720, size = 1_500_000_000L)
        val rig = Rig(
            scope = this,
            results = flow { emit(found(source1, badHtml)); emit(found(source2, goodStream)) },
            preferDub = false,
            request = request,
            sourceProbe = { s ->
                if (s.url == badHtml.url) Probe.Rejected(Probe.Reason.NotMedia)
                else Probe.Ok(SourceKind.Progressive, s.sizeBytes)
            },
        )
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        advanceTimeBy(100) // probe badHtml -> NotMedia -> startFailed -> nextCandidate(goodStream) -> probe goodStream -> Ok -> load(goodStream)
        advanceTimeBy(100)

        // Sahte kaynak (badHtml) yoklamada elenmeli ve iyi kaynağa (goodStream) geçmeli
        assertEquals(listOf(goodStream.url), rig.loaded.map { it.first.url })
        assertEquals(SourcePhase.Starting, rig.coordinator.phase)
        rig.coordinator.onFirstFrame()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
    }

    @Test
    fun probeTimeoutDoesNotEliminateAndStartsPlayback() = runTest {
        val slowStream = stream(source1, "https://a/slow", size = 2_000_000_000L)
        val rig = Rig(
            scope = this,
            results = flow { emit(found(source1, slowStream)) },
            preferDub = false,
            request = request,
            sourceProbe = { s ->
                delay(10_000) // 5s zaman aşımını aşıyor
                Probe.Ok(SourceKind.Progressive, s.sizeBytes)
            },
        )
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        advanceTimeBy(5_100) // 5s probe zaman aşımı

        // Zaman aşımı durumunda kaynak elenmez ve oynatılmaya başlanır
        assertEquals(listOf(slowStream.url), rig.loaded.map { it.first.url })
        assertEquals(SourcePhase.Starting, rig.coordinator.phase)
        rig.coordinator.onFirstFrame()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
    }

    @Test
    fun probeSmallSizeBelowThresholdEliminatesAndAdvances() = runTest {
        // Boyutu başta bilinmeyen ve yüksek çözünürlüklü olduğu için ilk denenen, ama probe ile 1 MB olduğu anlaşılan sahte film kaynağı
        val fakeProbeStream = stream(source1, "https://a/fake_probe", res = 1080, size = null)
        val realStream = stream(source2, "https://b/real_movie", res = 720, size = 800_000_000L)
        val rig = Rig(
            scope = this,
            results = flow { emit(found(source1, fakeProbeStream)); emit(found(source2, realStream)) },
            preferDub = false,
            request = request, // Movie (eşik 20 MB)
            sourceProbe = { s ->
                if (s.url == fakeProbeStream.url) {
                    Probe.Ok(SourceKind.Progressive, 1_000_000L) // 1 MB
                } else {
                    Probe.Ok(SourceKind.Progressive, s.sizeBytes)
                }
            },
        )
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        advanceTimeBy(100) // fakeProbeStream probe edilir, 1 MB bulunur, elenir
        advanceTimeBy(100) // realStream probe edilir, oynatılır

        // 1 MB olan sahte kaynak elenmeli, 800 MB olan gerçek kaynak oynatılmalı
        assertEquals(listOf(realStream.url), rig.loaded.map { it.first.url })
        rig.coordinator.onFirstFrame()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
    }

    @Test
    fun allSourcesFailingProbeResultsInFailedPhase() = runTest {
        val bad1 = stream(source1, "https://a/bad1", size = null)
        val bad2 = stream(source2, "https://b/bad2", size = null)
        val rig = Rig(
            scope = this,
            results = flow { emit(found(source1, bad1, bad2)) },
            preferDub = false,
            request = request,
            sourceProbe = { s ->
                Probe.Rejected(Probe.Reason.NotMedia)
            },
        )
        rig.coordinator.start(null, emptyList(), 0)
        advanceUntilIdle()

        // Tüm kaynaklar elendiğinde siyah ekranda kalmaz, phase = Failed olur
        assertTrue(rig.loaded.isEmpty())
        assertEquals(SourcePhase.Failed, rig.coordinator.phase)
    }
    @Test
    fun fallbackWaitsForDubbedSourceWhenNextCandidateIsSubtitled() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val subB = stream(source1, "https://b/sub", dub = false)
        val dubC = stream(source2, "https://c/dub", dub = true)

        val rig = Rig(this, flow {
            emit(found(source1, dubA, subB))
            delay(1_500)
            emit(found(source2, dubC))
            delay(60_000)
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()

        assertEquals(listOf("https://a/dub"), rig.loaded.map { it.first.url })

        rig.coordinator.onFailure("playback-error")
        runCurrent()

        advanceTimeBy(1_600)
        assertEquals(listOf("https://a/dub", "https://c/dub"), rig.loaded.map { it.first.url })
        advanceUntilIdle()
    }

    @Test
    fun fallbackSwitchesToSubtitledWhenDubWaitTimesOut() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val subB = stream(source1, "https://b/sub", dub = false)

        val rig = Rig(this, flow {
            emit(found(source1, dubA, subB))
            delay(60_000)
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()

        assertEquals(listOf("https://a/dub"), rig.loaded.map { it.first.url })

        rig.coordinator.onFailure("playback-error")
        runCurrent()

        // 3 sn sonra henüz 6 sn dolmadığı için altyazılıya geçmez
        advanceTimeBy(3_000)
        assertEquals(listOf("https://a/dub"), rig.loaded.map { it.first.url })
        assertEquals(SourcePhase.Resolving, rig.coordinator.phase)

        // 6 sn tamamlanınca altyazılı kaynak başlar
        advanceTimeBy(3_100)
        assertEquals(listOf("https://a/dub", "https://b/sub"), rig.loaded.map { it.first.url })
        advanceUntilIdle()
    }

    @Test
    fun fallbackSwitchesToSubtitledImmediatelyWhenCollectionFinishes() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val subB = stream(source1, "https://b/sub", dub = false)

        val rig = Rig(this, flow {
            emit(found(source1, dubA, subB))
            delay(500)
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()

        assertEquals(listOf("https://a/dub"), rig.loaded.map { it.first.url })

        rig.coordinator.onFailure("playback-error")
        runCurrent()

        assertEquals(SourcePhase.Resolving, rig.coordinator.phase)

        // 500 ms sonra akış biter (finished = true) -> 6 sn beklemeden hemen altyazılı açılır
        advanceTimeBy(600)
        assertEquals(listOf("https://a/dub", "https://b/sub"), rig.loaded.map { it.first.url })
        advanceUntilIdle()
    }

    @Test
    fun fallbackDoesNotWaitWhenPreferDubIsFalse() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val subB = stream(source1, "https://b/sub", dub = false)

        val rig = Rig(this, flow {
            emit(found(source1, dubA, subB))
            delay(60_000)
        }, preferDub = false, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()

        assertEquals(listOf("https://a/dub"), rig.loaded.map { it.first.url })

        rig.coordinator.onFailure("playback-error")
        runCurrent()

        // preferDub kapalı olduğu için beklemeden hemen sıradaki aday başlar
        assertEquals(listOf("https://a/dub", "https://b/sub"), rig.loaded.map { it.first.url })
        advanceUntilIdle()
    }

    @Test
    fun fallbackDoesNotReselectTriedDubbedSource() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val subB = stream(source1, "https://b/sub", dub = false)
        val dubA_repeat = stream(source2, "https://a/dub", dub = true)

        val rig = Rig(this, flow {
            emit(found(source1, dubA, subB))
            delay(1_000)
            // Başka bir eklenti aynı adresi veya daha önce denenmiş kaynağı tekrar döndü
            emit(found(source2, dubA_repeat))
            delay(60_000)
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()

        assertEquals(listOf("https://a/dub"), rig.loaded.map { it.first.url })

        rig.coordinator.onFailure("playback-error")
        runCurrent()

        // 1 sn sonra a/dub tekrar geldi ama attempted içinde olduğu için seçilmez
        advanceTimeBy(1_500)
        assertEquals(listOf("https://a/dub"), rig.loaded.map { it.first.url })
        assertEquals(SourcePhase.Resolving, rig.coordinator.phase)

        // 6 sn süre dolunca subB başlar (a/dub tekrar denenmez)
        advanceTimeBy(5_000)
        assertEquals(listOf("https://a/dub", "https://b/sub"), rig.loaded.map { it.first.url })
        advanceUntilIdle()
    }

    @Test
    fun stallSwitchesToCandidateAtSamePositionAndShowsNotice() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val dubB = stream(source2, "https://b/dub", dub = true)

        val rig = Rig(this, flow {
            emit(found(source1, dubA))
            emit(found(source2, dubB))
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()

        assertEquals("https://a/dub", rig.lastUrl)
        rig.coordinator.onFirstFrame()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)

        // Oynatma 42. saniyeye geldi
        rig.positionMs = 42_000L

        // Takılma tetikle (10 sn kesintisiz)
        val stallStart = currentTime
        advanceTimeBy(10_000L)

        rig.coordinator.checkStall(
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = stallStart,
            pastStalls = emptyList(),
        )
        runCurrent()

        // Bildirim gösterildi
        assertEquals(SourceNotice.StallSwitched, rig.coordinator.notice)
        // Yeni kaynağa aynı saniyeden (42_000 ms) geçildi
        assertEquals("https://b/dub", rig.lastUrl)
        assertEquals(42_000L, rig.loaded.last().second)
        // Eski kaynak yavaş işaretlendi
        assertTrue(SlowHostMemory.isSlow(dubA.url, currentTime))
    }

    @Test
    fun stallRevertsToPreviousSourceIfNewSourceFails() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val dubB = stream(source2, "https://b/dub", dub = true)

        val rig = Rig(this, flow {
            emit(found(source1, dubA))
            emit(found(source2, dubB))
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        rig.coordinator.onFirstFrame()
        rig.positionMs = 30_000L

        // Takılma ile dubB'ye geç
        val stallStart = currentTime
        advanceTimeBy(10_000L)
        rig.coordinator.checkStall(
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = stallStart,
            pastStalls = emptyList(),
        )
        runCurrent()
        assertEquals("https://b/dub", rig.lastUrl)

        // dubB açılamadı (hata verdi) -> önceki kaynağa (dubA) kaldığı saniyeden geri dönmeli
        rig.coordinator.onFailure("timeout")
        runCurrent()
        assertEquals("https://a/dub", rig.lastUrl)
        assertEquals(30_000L, rig.loaded.last().second)
    }

    @Test
    fun stallOnManualSourceShowsManualSlowNoticeWithoutSwitching() = runTest {
        val dubA = stream(source1, "https://a/dub", dub = true)
        val dubB = stream(source2, "https://b/dub", dub = true)

        val rig = Rig(this, flow {
            emit(found(source1, dubA))
            emit(found(source2, dubB))
        }, preferDub = true, request = request)

        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        // Kullanıcı elle dubA seçti
        rig.coordinator.pick(dubA)
        advanceTimeBy(350)
        rig.coordinator.onFirstFrame()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)

        val stallStart = currentTime
        advanceTimeBy(10_000L)
        rig.coordinator.checkStall(
            firstFrameRendered = true,
            isSeeking = false,
            currentStallStartMs = stallStart,
            pastStalls = emptyList(),
        )
        runCurrent()

        // Elle seçim olduğu için otomatik geçiş yapılmaz, ManualSlow bildirimi verilir
        assertEquals(SourceNotice.ManualSlow, rig.coordinator.notice)
        assertEquals("https://a/dub", rig.lastUrl)
    }

    @Test
    fun twoHundredConsecutiveResultsDoNotRecomputeRankingEveryTime() = runTest {
        val streams = (1..200).map { i ->
            stream(source1, "https://example.com/stream_$i.mp4", dub = false, res = 720, size = 1_000_000_000L + i)
        }
        val rig = Rig(
            this,
            flow {
                for (s in streams) {
                    emit(found(source1, s))
                }
                delay(60_000)
            },
            preferDub = true,
            request = request,
        )
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        // 200 ardışık sonuçta sıralama her sonuçta baştan yapılmamalıdır
        assertTrue(
            "200 sonuçta sıralama sayacı (${rig.coordinator.rankComputationCount}) 10'dan küçük olmalı",
            rig.coordinator.rankComputationCount < 10,
        )
    }

    @Test
    fun loadIsAlwaysCalledOnScopeDispatcherNeverOnRankingDispatcher() = runTest {
        val testMainThread = Thread.currentThread()
        val rankingThreadRef = java.util.concurrent.atomic.AtomicReference<Thread>()
        val rankingExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ranking-test-worker-thread").also { rankingThreadRef.set(it) }
        }
        val customRankingDispatcher = rankingExecutor.asCoroutineDispatcher()

        val loadThreads = java.util.Collections.synchronizedList(mutableListOf<Thread>())
        val loadedStreams = java.util.Collections.synchronizedList(mutableListOf<PlayableStream>())

        val streams = (1..200).map { i ->
            stream(source1, "https://example.com/stream_$i.mp4", dub = false, res = 720, size = 1_000_000_000L + i)
        }

        try {
            val coordinator = SourceCoordinator(
                scope = this,
                request = request,
                streamsFor = {
                    flow {
                        for (s in streams) {
                            emit(found(source1, s))
                        }
                        delay(60_000)
                    }
                },
                preferenceFor = { null },
                remember = { _, _ -> },
                sizeProbe = { null },
                preferDub = false,
                autoSource = true,
                load = { s, p ->
                    loadThreads.add(Thread.currentThread())
                    loadedStreams.add(s)
                },
                position = { 0L },
                clock = { testScheduler.currentTime },
                sourceProbe = null,
                rankingDispatcher = customRankingDispatcher,
            )

            coordinator.start(null, emptyList(), 0)
            advanceUntilIdle()

            assertTrue("En az bir kaynak yuklenmeli", loadedStreams.isNotEmpty())
            assertTrue("Load en az bir kez cagirilmali", loadThreads.isNotEmpty())

            for (thread in loadThreads) {
                val isRankingThread = (rankingThreadRef.get() != null && thread == rankingThreadRef.get()) ||
                    thread.name.contains("ranking-test-worker-thread")
                assertFalse("load asla rankingDispatcher threadinde cagrilmamali", isRankingThread)
                assertEquals("load her zaman scope threadinde cagrilmali", testMainThread, thread)
            }
        } finally {
            customRankingDispatcher.close()
            rankingExecutor.shutdown()
        }
    }

    @Test
    fun firstRenderedFrameCancelsBackgroundDiscoveryAndPanelCanRestartIt() = runTest {
        val a = stream(source1, "https://a/1")
        var runs = 0
        var cancellations = 0
        val results = flow {
            runs++
            emit(found(source1, a))
            if (runs == 1) {
                try {
                    awaitCancellation()
                } finally {
                    cancellations++
                }
            }
        }
        val rig = Rig(this, results, preferDub = false, request = request)
        rig.coordinator.start(null, emptyList(), 0)
        runCurrent()
        assertEquals(SourcePhase.Starting, rig.coordinator.phase)
        assertTrue(rig.coordinator.loading)
        assertEquals(1, runs)

        rig.coordinator.onFirstFrame()
        runCurrent()
        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
        assertTrue("late dub/source results remain available during the grace window", rig.coordinator.loading)
        advanceTimeBy(PLAYBACK_DISCOVERY_GRACE_MS)
        runCurrent()
        assertFalse(rig.coordinator.loading)
        assertEquals(1, cancellations)

        // Source panel is an explicit user request and may resume discovery while playback continues.
        rig.coordinator.ensureSources()
        runCurrent()
        assertEquals(2, runs)
        assertFalse(rig.coordinator.loading)
    }

    @Test
    fun lateProviderResultsDoNotProbeAlternativeSizesDuringPlayback() = runTest {
        val initial = stream(source1, "https://a/initial.mp4", dub = true, size = null)
        val late = stream(source2, "https://b/late.mp4", dub = true, size = null)
        var sizeProbeCalls = 0
        val rig = Rig(
            this,
            flow {
                delay(100)
                emit(found(source2, late))
                delay(60_000)
            },
            preferDub = true,
            request = request,
            probe = { sizeProbeCalls++; null },
        )

        rig.coordinator.start(initial, emptyList(), 0)
        runCurrent()
        assertEquals(SourcePhase.Starting, rig.coordinator.phase)
        rig.coordinator.onFirstFrame()
        advanceTimeBy(100)
        runCurrent()

        assertEquals(SourcePhase.Playing, rig.coordinator.phase)
        assertEquals("Late source should still be listed", listOf(late), rig.coordinator.streams)
        assertEquals("Background HEAD probes must not compete with video traffic", 0, sizeProbeCalls)
    }
}
