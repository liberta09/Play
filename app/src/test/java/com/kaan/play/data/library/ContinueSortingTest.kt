package com.kaan.play.data.library

import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueSortingTest {

    private fun entry(
        tmdbId: Int,
        type: MediaType,
        title: String,
        lastWatchedAt: Long,
    ): ContinueItem = ContinueItem.Resume(
        WatchHistoryEntity(
            id = tmdbId.toLong(),
            tmdbId = tmdbId,
            type = type,
            positionMs = 15_000L,
            durationMs = 100_000L,
            lastWatchedAt = lastWatchedAt,
            title = title,
            posterPath = null,
        ),
    )

    @Test
    fun sortOrdersContinueItemsAcrossAll5Modes() {
        val items = listOf(
            entry(1, MediaType.Movie, "Zeta Filmi", lastWatchedAt = 100L),
            entry(2, MediaType.Tv, "Beta Dizisi", lastWatchedAt = 400L),
            entry(3, MediaType.Movie, "Alfa Filmi", lastWatchedAt = 300L),
            entry(4, MediaType.Tv, "Çınar Dizisi", lastWatchedAt = 200L),
        )

        assertEquals(
            listOf(2, 3, 4, 1),
            ContinueSorting.sort(items, ContinueSortMode.Recent).map { it.entry.tmdbId },
        )
        assertEquals(
            listOf(1, 4, 3, 2),
            ContinueSorting.sort(items, ContinueSortMode.Oldest).map { it.entry.tmdbId },
        )
        assertEquals(
            listOf(3, 1, 2, 4),
            ContinueSorting.sort(items, ContinueSortMode.MoviesFirst).map { it.entry.tmdbId },
        )
        assertEquals(
            listOf(2, 4, 3, 1),
            ContinueSorting.sort(items, ContinueSortMode.SeriesFirst).map { it.entry.tmdbId },
        )
        assertEquals(
            listOf(3, 2, 4, 1),
            ContinueSorting.sort(items, ContinueSortMode.TitleAsc).map { it.entry.tmdbId },
        )
    }
}
