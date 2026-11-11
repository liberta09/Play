package com.kaan.play.ui.detail

import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesPlayTargetTest {

    private fun createEpisode(number: Int, airDate: String? = "2026-01-01"): Episode =
        Episode(
            number = number,
            name = "Episode $number",
            overview = "",
            stillPath = null,
            runtimeMinutes = null,
            airDate = airDate,
        )

    private fun createHistory(
        season: Int,
        episode: Int,
        positionMs: Long,
        durationMs: Long,
    ): WatchHistoryEntity =
        WatchHistoryEntity(
            id = 1L,
            userId = "u1",
            profileId = "p1",
            tmdbId = 100,
            type = MediaType.Tv,
            season = season,
            episode = episode,
            positionMs = positionMs,
            durationMs = durationMs,
            lastWatchedAt = 1000L,
            title = "Test Series",
            posterPath = null,
        )

    @Test
    fun noHistory_episodesNotLoaded_defaultsToSeasonAndEpisode1() {
        val target = SeriesPlayTarget.resolve(
            history = null,
            selectedSeason = 2,
            episodes = null,
        )
        assertEquals(2 to 1, target)
    }

    @Test
    fun noHistory_noSelectedSeason_defaultsToS1E1() {
        val target = SeriesPlayTarget.resolve(
            history = null,
            selectedSeason = null,
            episodes = emptyList(),
        )
        assertEquals(1 to 1, target)
    }

    @Test
    fun noHistory_seasonZero_avoidsSeasonZero() {
        // Sezon 0 otomatik seçilmez
        val target = SeriesPlayTarget.resolve(
            history = null,
            selectedSeason = 0,
            episodes = null,
        )
        assertEquals(1 to 1, target)
    }

    @Test
    fun noHistory_episodesLoaded_picksFirstPublishedEpisode() {
        val episodes = listOf(
            createEpisode(1, airDate = "2026-01-01"),
            createEpisode(2, airDate = "2026-01-08"),
            createEpisode(3, airDate = "2026-12-01"), // Gelecekte
        )
        val target = SeriesPlayTarget.resolve(
            history = null,
            selectedSeason = 1,
            episodes = episodes,
            today = "2026-05-01",
        )
        assertEquals(1 to 1, target)
    }

    @Test
    fun historyInProgress_returnsResumePosition() {
        // %50 izlenmiş (devam et)
        val history = createHistory(season = 2, episode = 4, positionMs = 50_000L, durationMs = 100_000L)
        val target = SeriesPlayTarget.resolve(
            history = history,
            selectedSeason = 1,
            episodes = null,
        )
        assertEquals(2 to 4, target)
    }

    @Test
    fun historyFinished_resolverProvidesNextEpisode() {
        // %95 izlenmiş (bitmiş)
        val history = createHistory(season = 1, episode = 3, positionMs = 95_000L, durationMs = 100_000L)
        val target = SeriesPlayTarget.resolve(
            history = history,
            selectedSeason = 1,
            episodes = null,
            nextEpisode = { s, e -> if (s == 1 && e == 3) 1 to 4 else null },
        )
        assertEquals(1 to 4, target)
    }

    @Test
    fun historyFinished_episodesListProvidesNextInSeason() {
        val history = createHistory(season = 2, episode = 1, positionMs = 95_000L, durationMs = 100_000L)
        val episodes = listOf(
            createEpisode(1, airDate = "2026-01-01"),
            createEpisode(2, airDate = "2026-01-08"),
        )
        val target = SeriesPlayTarget.resolve(
            history = history,
            selectedSeason = 2,
            episodes = episodes,
            today = "2026-05-01",
        )
        assertEquals(2 to 2, target)
    }

    @Test
    fun historyFinished_fallbackIncrementsEpisode() {
        val history = createHistory(season = 3, episode = 5, positionMs = 95_000L, durationMs = 100_000L)
        val target = SeriesPlayTarget.resolve(
            history = history,
            selectedSeason = 3,
            episodes = null,
        )
        assertEquals(3 to 6, target)
    }
}
