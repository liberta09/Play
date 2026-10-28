package com.kaan.play.ui.player

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.settings.SkipPlaybackSettings
import com.kaan.play.data.skip.SkipSegmentProvider
import com.kaan.play.data.skip.SkipTimeType
import com.kaan.play.data.skip.SkipTimestamp
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Oynatıcı tarafı: sorgu oynatmayı beklemez, kaynak değişince eski sonuç atılır, otomatik atlama döngüye girmez. */
class SkipControllerTest {
    private val episode = StreamRequest(tmdbId = 1399, imdbId = null, type = MediaType.Tv, season = 1, episode = 3)
    private val duration = 45 * 60_000L
    private val intro = SkipTimestamp(SkipTimeType.Intro, 10_000L, 70_000L)

    private class Harness(
        val request: StreamRequest,
        provider: SkipSegmentProvider,
        settings: SkipPlaybackSettings = SkipPlaybackSettings.Default,
        startMs: Long = 0L,
    ) {
        val scope = TestScope(UnconfinedTestDispatcher())
        val seeks = mutableListOf<Long>()
        val calls = mutableListOf<StreamRequest>()
        val controller = SkipController(
            scope = scope,
            request = { request },
            provider = { r, d -> calls += r; provider.timestamps(r, d) },
            settings = MutableStateFlow(settings),
            seekTo = { seeks += it },
            startPositionMs = startMs,
        )
    }

    @Test
    fun introButtonAppearsInsideTheSegmentAndSkipSeeksToItsEnd() {
        val h = Harness(episode, { _, _ -> listOf(intro) })
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = 5_000L, playing = true)
        assertTrue(h.controller.active.isEmpty())
        h.controller.tick(position = 12_000L, playing = true)
        assertEquals(listOf(SkipSegmentKind.Intro), h.controller.active.map { it.kind })

        h.controller.skip(h.controller.active.single())
        assertEquals(listOf(70_000L), h.seeks)
        assertTrue(h.controller.active.isEmpty())
    }

    @Test
    fun automaticSkipHappensOnceAndRewindingDoesNotLoop() {
        val h = Harness(episode, { _, _ -> listOf(intro) }, SkipPlaybackSettings(autoSkip = true))
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = 12_000L, playing = true)
        assertEquals(listOf(70_000L), h.seeks)
        // Kullanıcı intronun içine geri sardı: ikinci kez atlanmaz.
        h.controller.tick(position = 20_000L, playing = true)
        h.controller.tick(position = 30_000L, playing = true)
        assertEquals(listOf(70_000L), h.seeks)
        // Kaynak değişse de aynı segment bu oturumda bir daha otomatik geçilmez.
        h.controller.onSourceChanged(30_000L)
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = 30_000L, playing = true)
        assertEquals(listOf(70_000L), h.seeks)
    }

    @Test
    fun automaticSkipWaitsWhilePaused() {
        val h = Harness(episode, { _, _ -> listOf(intro) }, SkipPlaybackSettings(autoSkip = true))
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = 12_000L, playing = false)
        assertTrue(h.seeks.isEmpty())
    }

    @Test
    fun aLateResultForThePreviousSourceIsDiscarded() {
        val pending = CompletableDeferred<List<SkipTimestamp>>()
        var call = 0
        val h = Harness(episode, { _, _ -> if (call++ == 0) pending.await() else emptyList() })
        h.controller.onDurationKnown(duration)
        // Sorgu sürerken kaynak değişti (yeni kaynakta veri yok); eski cevap gelince düğme çıkmamalı.
        h.controller.onSourceChanged(0L)
        h.controller.onDurationKnown(duration)
        pending.complete(listOf(intro))
        h.scope.runCurrent()
        h.controller.tick(position = 12_000L, playing = true)
        assertTrue(h.controller.segments.isEmpty())
        assertTrue(h.controller.active.isEmpty())
    }

    @Test
    fun liveChannelsAndUnknownDurationShowNothing() {
        val live = Harness(episode.copy(addonType = "channel"), { _, _ -> listOf(intro) })
        live.controller.onDurationKnown(duration)
        live.controller.tick(position = 12_000L, playing = true)
        assertTrue(live.controller.active.isEmpty())

        val unknown = Harness(episode, { _, _ -> listOf(intro) })
        unknown.controller.tick(position = 12_000L, playing = true)
        assertTrue(unknown.controller.active.isEmpty())
        assertTrue(unknown.calls.isEmpty())
    }

    @Test
    fun disabledCategoryIsNeitherShownNorAutoSkipped() {
        val h = Harness(
            episode,
            { _, _ -> listOf(intro) },
            SkipPlaybackSettings(autoSkip = true, enabledCategories = setOf(com.kaan.play.data.settings.SkipCategory.Credits)),
        )
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = 12_000L, playing = true)
        assertTrue(h.controller.active.isEmpty())
        assertTrue(h.seeks.isEmpty())
    }

    @Test
    fun openEndedCreditsSkipToTheEndOfTheVideo() {
        val credits = SkipTimestamp(SkipTimeType.Credits, duration - 90_000L, duration, openEndedEnd = true)
        val h = Harness(episode, { _, _ -> listOf(credits) })
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = duration - 60_000L, playing = true)
        h.controller.skip(h.controller.active.single())
        assertEquals(listOf(duration), h.seeks)
    }

    @Test
    fun backDismissesTheButtonWithoutSeeking() {
        val h = Harness(episode, { _, _ -> listOf(intro) })
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = 12_000L, playing = true)
        h.controller.dismiss(h.controller.active)
        h.controller.tick(position = 13_000L, playing = true)
        assertTrue(h.controller.active.isEmpty())
        assertTrue(h.seeks.isEmpty())
    }

    @Test
    fun aFinishedIntroIsNotOfferedOrAutoSkippedBeforeTheResumeSeekApplies() {
        val h = Harness(episode, { _, _ -> listOf(intro) }, SkipPlaybackSettings(autoSkip = true), startMs = 20 * 60_000L)
        h.controller.onDurationKnown(duration)
        h.controller.tick(position = 12_000L, playing = true)
        assertTrue(h.controller.active.isEmpty())
        assertTrue(h.seeks.isEmpty())
    }
}
