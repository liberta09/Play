package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun stream(addon: Long, url: String, res: Int? = 1080, size: Long? = 1_000L) =
    PlayableStream(StreamSource(addon, "A$addon"), url, res, size, emptyMap(), emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class AutoNextRulesTest {

    @Test
    fun cardWindowIsTheLastTwentySeconds() {
        val duration = 1_200_000L
        assertFalse(AutoNextRules.inCardWindow(duration - 21_000, duration))
        assertTrue(AutoNextRules.inCardWindow(duration - 20_000, duration))
        assertTrue(AutoNextRules.inCardWindow(duration - 1, duration))
        assertTrue(AutoNextRules.inCardWindow(duration, duration))
    }

    @Test
    fun prefetchStartsEarlierThanTheCard() {
        val duration = 1_200_000L
        assertFalse(AutoNextRules.inPrefetchWindow(duration - 46_000, duration))
        assertTrue(AutoNextRules.inPrefetchWindow(duration - 45_000, duration))
    }

    @Test
    fun veryShortVideosHaveNoWindow() {
        assertFalse(AutoNextRules.inCardWindow(50_000, 60_000))
        assertFalse(AutoNextRules.inPrefetchWindow(50_000, 60_000))
    }

    @Test
    fun seekingWellBeforeTheWindowLeavesIt() {
        val duration = 1_200_000L
        assertFalse(AutoNextRules.leftCardWindow(duration - 21_000, duration)) // sınırda: kart titremesin
        assertTrue(AutoNextRules.leftCardWindow(duration - 23_000, duration))
    }

    @Test
    fun sameAddonComesFirstThenTheRecommendedOthers() {
        val current = stream(addon = 1, url = "https://a.example/s1e3.mp4", res = 1080)
        val all = listOf(
            stream(2, "https://b.example/x.mp4", res = 2160),
            stream(2, "https://b.example/y.mp4", res = 1080),
            stream(1, "https://a.example/s1e4-720.mp4", res = 720),
            stream(1, "https://a.example/s1e4-1080.mp4", res = 1080),
        )
        val ordered = AutoNextRules.order(all, current).map { it.url }
        assertEquals(
            listOf(
                "https://a.example/s1e4-1080.mp4", // aynı eklenti, en yakın kalite
                "https://a.example/s1e4-720.mp4",
                "https://b.example/y.mp4", // diğerlerinde önerilen (1080p, 4K değil)
                "https://b.example/x.mp4",
            ),
            ordered,
        )
    }

    @Test
    fun sameServerBeatsSameQualityWithinTheAddon() {
        val current = stream(1, "https://cdn1.example/s1e3.mp4", res = 1080)
        val all = listOf(
            stream(1, "https://cdn2.example/s1e4.mp4", res = 1080, size = 9_000),
            stream(1, "https://cdn1.example/s1e4.mp4", res = 720, size = 1_000),
        )
        assertEquals("https://cdn1.example/s1e4.mp4", AutoNextRules.order(all, current).first().url)
    }

    @Test
    fun directLinkBeatsMagnetWhenCurrentIsDirect() {
        val current = stream(1, "https://a.example/s1e3.mp4")
        val all = listOf(
            stream(1, "magnet:?xt=urn:btih:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", res = 1080, size = 9_000),
            stream(1, "https://a.example/s1e4.mp4", res = 1080, size = 1_000),
        )
        assertEquals("https://a.example/s1e4.mp4", AutoNextRules.order(all, current).first().url)
    }

    @Test
    fun duplicatesAreDroppedAndTheListIsCapped() {
        val current = stream(1, "https://a.example/s1e3.mp4")
        val all = (1..9).map { stream(1, "https://a.example/$it.mp4", res = 1080, size = it.toLong()) } +
            stream(1, "https://a.example/1.mp4", res = 1080, size = 1)
        val ordered = AutoNextRules.order(all, current)
        assertEquals(AutoNextRules.MAX_CANDIDATES, ordered.size)
        assertEquals(ordered.size, ordered.map { it.url }.toSet().size)
    }

    @Test
    fun emptyInputGivesEmptyList() {
        assertTrue(AutoNextRules.order(emptyList(), stream(1, "https://a.example/s1e3.mp4")).isEmpty())
    }

    @Test
    fun collectReturnsAsSoonAsThePreferredAddonAnswersPlusAShortGrace() = runTest {
        val results = flow {
            emit(StreamResult.Found(StreamSource(2, "B"), listOf(stream(2, "https://b.example/1.mp4"))))
            delay(100)
            emit(StreamResult.Found(StreamSource(1, "A"), listOf(stream(1, "https://a.example/1.mp4"))))
            delay(500) // grace içinde
            emit(StreamResult.Found(StreamSource(3, "C"), listOf(stream(3, "https://c.example/1.mp4"))))
            delay(60_000) // çok yavaş eklenti: beklenmez
            emit(StreamResult.Found(StreamSource(4, "D"), listOf(stream(4, "https://d.example/1.mp4"))))
        }
        val collected = AutoNextRules.collect(results, preferredAddonId = 1)
        assertEquals(setOf(2L, 1L, 3L), collected.map { it.source.addonId }.toSet())
        assertTrue(testScheduler.currentTime < 5_000)
    }

    @Test
    fun collectWithoutThePreferredAddonWaitsForAllThenReturns() = runTest {
        val results = flow {
            emit(StreamResult.Failed(StreamSource(1, "A"), FailureReason.Timeout))
            emit(StreamResult.Found(StreamSource(2, "B"), listOf(stream(2, "https://b.example/1.mp4"))))
        }
        val collected = AutoNextRules.collect(results, preferredAddonId = 1)
        assertEquals(listOf(2L), collected.map { it.source.addonId })
    }

    @Test
    fun collectOfNothingIsEmpty() = runTest {
        assertTrue(AutoNextRules.collect(flow { }, preferredAddonId = 1).isEmpty())
    }

    @Test
    fun canShowNextEpisodeButtonRules() {
        // Normal dizi bölümü: görünür
        assertTrue(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, 1, 2, addonType = null, durationMs = 1_200_000L))
        assertTrue(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, 1, 2, addonType = "series", durationMs = 1_200_000L))

        // Canlı tv/channel türleri: gizlenir
        assertFalse(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, 1, 2, addonType = "tv", durationMs = 1_200_000L))
        assertFalse(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, 1, 2, addonType = "channel", durationMs = 1_200_000L))

        // Film türü: gizlenir
        assertFalse(AutoNextRules.canShowNextEpisodeButton(MediaType.Movie, 1, 2, addonType = null, durationMs = 1_200_000L))

        // Sezon ya da bölüm numarası eksik: gizlenir
        assertFalse(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, null, 2, addonType = null, durationMs = 1_200_000L))
        assertFalse(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, 1, null, addonType = null, durationMs = 1_200_000L))

        // Süre bilinmiyor ya da sıfır (canlı akış): gizlenir
        assertFalse(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, 1, 2, addonType = null, durationMs = 0L))
        assertFalse(AutoNextRules.canShowNextEpisodeButton(MediaType.Tv, 1, 2, addonType = null, durationMs = -1L))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutoNextControllerTest {

    private val duration = 1_200_000L

    private val seriesTarget = PlayTarget(StreamRequest(10, "tt10", MediaType.Tv, season = 1, episode = 3), "Dizi", null)
    private val movieTarget = PlayTarget(StreamRequest(10, "tt10", MediaType.Movie), "Film", null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private class Rig(
        scope: TestScope,
        target: PlayTarget,
        enabled: Boolean = true,
        seconds: Int = SettingsRepository.DEFAULT_AUTO_NEXT_SECONDS,
        next: Pair<Int, Int>? = 1 to 4,
        prepared: NextPlayback? = null,
        failLookups: Int = 0,
    ) {
        val advanced = mutableListOf<NextPlayback>()
        var prepareCalls = 0
        var lookupCalls = 0
        var noNextCalls = 0
        val controller: AutoNextController
        private var clock = 0L

        init {
            controller = AutoNextController(
                scope = scope,
                target = target,
                config = { AutoNextConfig(enabled, seconds) },
                findNext = {
                    lookupCalls++
                    if (lookupCalls <= failLookups) throw java.io.IOException("offline")
                    next
                },
                prepare = { _, _ -> prepareCalls++; prepared },
                onAdvance = { advanced += it },
                onNoNext = { noNextCalls++ },
            )
            controller.start()
            scope.testScheduler.runCurrent()
        }

        /** [remainingMs]: bölümün bitmesine kalan süre. Zaman 500 ms ilerler. */
        fun tick(remainingMs: Long, playing: Boolean = true, ended: Boolean = false, step: Long = 500) {
            clock += step
            controller.tick(clock, 1_200_000L - remainingMs, 1_200_000L, playing, ended)
        }
    }

    private fun online(target: PlayTarget, vararg streams: PlayableStream) =
        NextPlayback.Online(target.copy(request = target.request.copy(episode = 4)), streams.toList())

    @Test
    fun cardCountsDownThenAdvances() = runTest {
        val next = online(seriesTarget, stream(1, "https://a.example/s1e4.mp4"))
        val rig = Rig(this, seriesTarget, prepared = next)

        rig.tick(remainingMs = 300_000)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)

        rig.tick(remainingMs = 19_500)
        assertEquals(AutoNextPhase.Countdown, rig.controller.phase)
        assertEquals(1, rig.controller.season)
        assertEquals(4, rig.controller.episode)
        assertEquals(10, rig.controller.secondsLeft)

        repeat(10) { rig.tick(remainingMs = 19_000 - it * 500L) }
        assertEquals(5, rig.controller.secondsLeft)

        repeat(10) { rig.tick(remainingMs = 14_000 - it * 500L) }
        runCurrent()
        assertEquals(listOf<NextPlayback>(next), rig.advanced)
    }

    @Test
    fun countdownFollowsTheConfiguredSeconds() = runTest {
        val rig = Rig(this, seriesTarget, seconds = 5, prepared = online(seriesTarget, stream(1, "https://a.example/x.mp4")))
        rig.tick(remainingMs = 19_000)
        assertEquals(5, rig.controller.secondsLeft)
    }

    @Test
    fun countdownDoesNotRunWhilePaused() = runTest {
        val rig = Rig(this, seriesTarget, prepared = online(seriesTarget, stream(1, "https://a.example/x.mp4")))
        rig.tick(remainingMs = 19_000)
        repeat(40) { rig.tick(remainingMs = 19_000, playing = false) }
        assertEquals(10, rig.controller.secondsLeft)
        runCurrent()
        assertTrue(rig.advanced.isEmpty())
    }

    @Test
    fun playNowAdvancesWithoutWaitingForTheCountdown() = runTest {
        val next = online(seriesTarget, stream(1, "https://a.example/s1e4.mp4"))
        val rig = Rig(this, seriesTarget, prepared = next)
        rig.tick(remainingMs = 19_000)
        rig.controller.playNow()
        runCurrent()
        assertEquals(listOf<NextPlayback>(next), rig.advanced)
    }

    @Test
    fun cancelHidesTheCardForGood() = runTest {
        val rig = Rig(this, seriesTarget, prepared = online(seriesTarget, stream(1, "https://a.example/x.mp4")))
        rig.tick(remainingMs = 19_000)
        rig.controller.cancel()
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)

        repeat(60) { rig.tick(remainingMs = 19_000 - it * 300L) }
        runCurrent()
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        assertTrue(rig.advanced.isEmpty())
        assertFalse(rig.controller.onEnded()) // video bitince de geçilmez: eskisi gibi kapanır
    }

    @Test
    fun moviesNeverShowTheCard() = runTest {
        val rig = Rig(this, movieTarget, prepared = online(movieTarget, stream(1, "https://a.example/x.mp4")))
        repeat(5) { rig.tick(remainingMs = 10_000) }
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        assertEquals(0, rig.lookupCalls)
        assertFalse(rig.controller.onEnded())
    }

    @Test
    fun switchedOffMeansNothingHappens() = runTest {
        val rig = Rig(this, seriesTarget, enabled = false, prepared = online(seriesTarget, stream(1, "https://a.example/x.mp4")))
        repeat(5) { rig.tick(remainingMs = 10_000) }
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        assertEquals(0, rig.lookupCalls)
        assertFalse(rig.controller.onEnded())
    }

    @Test
    fun creditsCanPrepareManualNextEvenWhenAutomaticAdvanceIsOff() = runTest {
        val next = online(seriesTarget, stream(1, "https://a.example/s1e4.mp4"))
        val rig = Rig(this, seriesTarget, enabled = false, prepared = next)

        assertTrue(rig.controller.prepareManualNext())
        runCurrent()
        assertEquals(1, rig.lookupCalls)
        assertEquals(1, rig.prepareCalls)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)

        rig.controller.skipToNext()
        runCurrent()
        assertEquals(listOf<NextPlayback>(next), rig.advanced)
    }

    @Test
    fun lastEpisodeShowsNoCard() = runTest {
        val rig = Rig(this, seriesTarget, next = null, prepared = online(seriesTarget, stream(1, "https://a.example/x.mp4")))
        repeat(5) { rig.tick(remainingMs = 10_000) }
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        assertFalse(rig.controller.onEnded())
        assertEquals(0, rig.prepareCalls)
    }

    @Test
    fun sourcesAreSearchedBeforeTheCardAppears() = runTest {
        val rig = Rig(this, seriesTarget, prepared = online(seriesTarget, stream(1, "https://a.example/x.mp4")))
        rig.tick(remainingMs = 40_000)
        runCurrent()
        assertEquals(1, rig.prepareCalls)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        repeat(30) { rig.tick(remainingMs = 19_000 - it * 100L) }
        assertEquals(1, rig.prepareCalls) // bir kez
    }

    @Test
    fun seekingBackOutOfTheWindowClosesTheCard() = runTest {
        val rig = Rig(this, seriesTarget, prepared = online(seriesTarget, stream(1, "https://a.example/x.mp4")))
        rig.tick(remainingMs = 19_000)
        assertEquals(AutoNextPhase.Countdown, rig.controller.phase)
        rig.tick(remainingMs = 200_000)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
    }

    @Test
    fun endingTheVideoGoesStraightToTheNextEpisode() = runTest {
        val next = online(seriesTarget, stream(1, "https://a.example/s1e4.mp4"))
        val rig = Rig(this, seriesTarget, prepared = next)
        assertTrue(rig.controller.onEnded())
        runCurrent()
        assertEquals(listOf<NextPlayback>(next), rig.advanced)
    }

    @Test
    fun noSourceAsksForAnotherSourceInsteadOfLeavingABlackScreen() = runTest {
        val rig = Rig(this, seriesTarget, prepared = null)
        rig.tick(remainingMs = 19_000)
        rig.controller.playNow()
        runCurrent()
        assertEquals(AutoNextPhase.NoSource, rig.controller.phase)
        assertTrue(rig.advanced.isEmpty())

        rig.controller.startPicking()
        assertTrue(rig.controller.picking)
        val chosen = stream(7, "https://z.example/s1e4.mp4")
        rig.controller.picked(chosen)
        assertFalse(rig.controller.picking)

        val advanced = rig.advanced.single() as NextPlayback.Online
        assertEquals(listOf(chosen), advanced.streams)
        assertEquals(1, advanced.target.request.season)
        assertEquals(4, advanced.target.request.episode)
    }

    @Test
    fun cancelAfterNoSourceCloses() = runTest {
        val rig = Rig(this, seriesTarget, prepared = null)
        assertTrue(rig.controller.onEnded())
        runCurrent()
        assertEquals(AutoNextPhase.NoSource, rig.controller.phase)
        rig.controller.cancel()
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
    }

    @Test
    fun aFailedLookupIsRetriedOnceNearTheEnd() = runTest {
        val next = online(seriesTarget, stream(1, "https://a.example/s1e4.mp4"))
        val rig = Rig(this, seriesTarget, prepared = next, failLookups = 1)
        assertEquals(1, rig.lookupCalls)
        rig.tick(remainingMs = 300_000)
        assertEquals(1, rig.lookupCalls) // henüz pencerede değil
        rig.tick(remainingMs = 40_000)
        runCurrent()
        assertEquals(2, rig.lookupCalls)
        rig.tick(remainingMs = 19_000)
        assertEquals(AutoNextPhase.Countdown, rig.controller.phase)
    }

    @Test
    fun gaveUpAfterTwoFailedLookups() = runTest {
        val rig = Rig(this, seriesTarget, failLookups = 5)
        repeat(6) { rig.tick(remainingMs = 40_000 - it * 100L) }
        runCurrent()
        assertEquals(2, rig.lookupCalls)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        assertFalse(rig.controller.onEnded())
    }

    @Test
    fun nextTargetKeepsTheShowButMovesTheEpisode() = runTest {
        val rig = Rig(this, seriesTarget)
        rig.tick(remainingMs = 19_000)
        val target = rig.controller.nextTarget()
        assertEquals("Dizi", target.title)
        assertEquals(10, target.request.tmdbId)
        assertEquals(1, target.request.season)
        assertEquals(4, target.request.episode)
        assertNull(seriesTarget.request.refresh.takeIf { it })
    }

    @Test
    fun skipToNextAdvancesImmediatelyWithoutWaitingForCardOrCountdown() = runTest {
        val next = online(seriesTarget, stream(1, "https://a.example/s1e4.mp4"))
        val rig = Rig(this, seriesTarget, prepared = next)
        // Video henüz başladı (~20. dakikada, pencerenin çok uzağında)
        rig.tick(remainingMs = 500_000)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)

        // Kullanıcı "Sonraki bölüm" düğmesine bastı
        rig.controller.skipToNext()
        runCurrent()

        assertEquals(1, rig.advanced.size)
        assertEquals(next, rig.advanced.single())
    }

    @Test
    fun skipToNextWorksEvenWhenAutoNextSettingIsDisabled() = runTest {
        val next = online(seriesTarget, stream(1, "https://a.example/s1e4.mp4"))
        val rig = Rig(this, seriesTarget, enabled = false, prepared = next)
        // Ayar kapalı, otomatik hiçbir şey olmaz
        rig.tick(remainingMs = 10_000)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        assertEquals(0, rig.lookupCalls)

        // Manuel düğme yine de sonraki bölüme geçer
        rig.controller.skipToNext()
        runCurrent()

        assertEquals(1, rig.advanced.size)
        assertEquals(next, rig.advanced.single())
    }

    @Test
    fun skipToNextInvokesOnNoNextWhenLastEpisode() = runTest {
        val rig = Rig(this, seriesTarget, next = null)
        rig.controller.skipToNext()
        runCurrent()

        assertEquals(1, rig.noNextCalls)
        assertTrue(rig.advanced.isEmpty())
    }

    @Test
    fun liveTargetNeverArmsOrShowsCard() = runTest {
        val liveTarget = PlayTarget(StreamRequest(10, "tt10", MediaType.Tv, addonType = "tv"), "Canli", null)
        val rig = Rig(this, liveTarget, enabled = true)
        assertEquals(0, rig.lookupCalls)
        rig.tick(10_000)
        assertEquals(AutoNextPhase.Hidden, rig.controller.phase)
        assertEquals(0, rig.prepareCalls)
        assertFalse(rig.controller.onEnded())
        assertTrue(rig.advanced.isEmpty())
    }
}
