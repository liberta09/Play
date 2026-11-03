package com.kaan.play.data.library

import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinueDeletionRulesTest {

    private fun episode(
        id: Long,
        tmdbId: Int,
        season: Int,
        episode: Int,
        title: String = "Test Series",
    ): WatchHistoryEntity = WatchHistoryEntity(
        id = id,
        tmdbId = tmdbId,
        type = MediaType.Tv,
        season = season,
        episode = episode,
        positionMs = 150_000,
        durationMs = 300_000,
        lastWatchedAt = 1000L + id,
        title = title,
        posterPath = null,
    )

    private fun movie(
        id: Long,
        tmdbId: Int,
        title: String = "Test Movie",
    ): WatchHistoryEntity = WatchHistoryEntity(
        id = id,
        tmdbId = tmdbId,
        type = MediaType.Movie,
        season = 0,
        episode = 0,
        positionMs = 150_000,
        durationMs = 300_000,
        lastWatchedAt = 1000L + id,
        title = title,
        posterPath = null,
    )

    @Test
    fun seriesDeletion_removesAllEpisodesOfThatSeries() {
        val series1Ep1 = episode(id = 1, tmdbId = 100, season = 1, episode = 1)
        val series1Ep2 = episode(id = 2, tmdbId = 100, season = 1, episode = 2)
        val series1Ep3 = episode(id = 3, tmdbId = 100, season = 2, episode = 1)
        val series2Ep1 = episode(id = 4, tmdbId = 200, season = 1, episode = 1)
        val movie1 = movie(id = 5, tmdbId = 300)

        val history = listOf(series1Ep1, series1Ep2, series1Ep3, series2Ep1, movie1)
        val target = ContinueDeletionRules.Target(tmdbId = 100, type = MediaType.Tv)

        val remaining = ContinueDeletionRules.filterRemaining(history, target)

        assertEquals(2, remaining.size)
        assertTrue(remaining.contains(series2Ep1))
        assertTrue(remaining.contains(movie1))
        assertFalse(remaining.any { it.tmdbId == 100 })
    }

    @Test
    fun movieDeletion_removesOnlySingleMovieRecord() {
        val movie1 = movie(id = 1, tmdbId = 100, title = "Movie A")
        val movie2 = movie(id = 2, tmdbId = 200, title = "Movie B")
        val series1 = episode(id = 3, tmdbId = 300, season = 1, episode = 1)

        val history = listOf(movie1, movie2, series1)
        val target = ContinueDeletionRules.Target(tmdbId = 100, type = MediaType.Movie)

        val remaining = ContinueDeletionRules.filterRemaining(history, target)

        assertEquals(2, remaining.size)
        assertTrue(remaining.contains(movie2))
        assertTrue(remaining.contains(series1))
        assertFalse(remaining.contains(movie1))
    }

    @Test
    fun nonexistentTarget_doesNotThrowAndLeavesHistoryIntact() {
        val movie1 = movie(id = 1, tmdbId = 100)
        val series1 = episode(id = 2, tmdbId = 200, season = 1, episode = 1)
        val history = listOf(movie1, series1)

        val nonexistentTarget = ContinueDeletionRules.Target(tmdbId = 999, type = MediaType.Movie)
        val remaining = ContinueDeletionRules.filterRemaining(history, nonexistentTarget)

        assertEquals(history, remaining)
    }

    @Test
    fun targetFromItemAndEntity_correctlyExtractsProperties() {
        val entity = episode(id = 1, tmdbId = 42, season = 1, episode = 1)
        val itemResume = ContinueItem.Resume(entity)
        val itemNext = ContinueItem.Next(entity, season = 1, episode = 2)

        val targetFromEntity = ContinueDeletionRules.targetFrom(entity)
        val targetFromResume = ContinueDeletionRules.targetFrom(itemResume)
        val targetFromNext = ContinueDeletionRules.targetFrom(itemNext)

        assertEquals(42, targetFromEntity.tmdbId)
        assertEquals(MediaType.Tv, targetFromEntity.type)
        assertEquals(targetFromEntity, targetFromResume)
        assertEquals(targetFromEntity, targetFromNext)
    }

    @Test
    fun continueItem_filterRemaining_and_shouldDelete_workForResumeAndNext() {
        val seriesEp = episode(id = 1, tmdbId = 100, season = 1, episode = 1)
        val itemSeries = ContinueItem.Next(seriesEp, season = 1, episode = 2)

        val movieEntity = movie(id = 2, tmdbId = 200)
        val itemMovie = ContinueItem.Resume(movieEntity)

        val items = listOf<ContinueItem>(itemSeries, itemMovie)

        val seriesTarget = ContinueDeletionRules.targetFrom(itemSeries)
        assertTrue(ContinueDeletionRules.shouldDelete(itemSeries, seriesTarget))
        assertFalse(ContinueDeletionRules.shouldDelete(itemMovie, seriesTarget))

        val remaining = ContinueDeletionRules.filterRemaining(items, seriesTarget)
        assertEquals(1, remaining.size)
        assertEquals(itemMovie, remaining.first())
    }

    @Test
    fun nextFocusedItem_choosesCorrectNeighbor() {
        val list = listOf("A", "B", "C")

        // First item removed (index 0), remaining = ["B", "C"]
        val remainingAfter0 = listOf("B", "C")
        assertEquals("B", ContinueDeletionRules.nextFocusedItem(0, remainingAfter0))

        // Middle item removed (index 1), remaining = ["A", "C"]
        val remainingAfter1 = listOf("A", "C")
        assertEquals("C", ContinueDeletionRules.nextFocusedItem(1, remainingAfter1))

        // Last item removed (index 2), remaining = ["A", "B"]
        val remainingAfter2 = listOf("A", "B")
        assertEquals("B", ContinueDeletionRules.nextFocusedItem(2, remainingAfter2))

        // Only item removed (index 0), remaining = []
        val emptyList = emptyList<String>()
        assertNull(ContinueDeletionRules.nextFocusedItem(0, emptyList))
    }
}
