package com.kaan.play.ui.player

import com.kaan.play.data.cloudstream.CsEpisodeRef
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.SeasonDetail
import com.kaan.play.data.model.Episode
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EpisodePanelRulesTest {

    @Test
    fun testCanShowButton() {
        // Canlı tv/channel
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "tv", false))
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "channel", false))
        // Dizi
        assertTrue(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "series", false))
        // Film
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Movie, 1, 1, "series", false))
        // Guest
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "series", true))
        // Null season
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, null, 1, "series", false))
    }

    @Test
    fun testMapPluginEpisodes() {
        val csEpisodes = listOf(
            CsEpisodeRef(1, 1, "url1"),
            CsEpisodeRef(1, 2, "url2"), // current
            CsEpisodeRef(2, 1, "url3"), // different season
            CsEpisodeRef(null, null, "url4") // fallback 1,3
        )
        val history = listOf(
            WatchHistoryEntity(tmdbId = 1, type = MediaType.Tv, title = "", posterPath = null, season = 1, episode = 1, positionMs = 100, durationMs = 100, lastWatchedAt = 0) // Watched
        )

        val rows = EpisodePanelRules.mapPluginEpisodes(csEpisodes, currentSeason = 1, currentEpisode = 2, history)

        assertEquals(3, rows.size)
        // first
        assertEquals(1, rows[0].season)
        assertEquals(1, rows[0].episode)
        assertTrue(rows[0].watched)
        assertFalse(rows[0].isCurrent)

        // second
        assertEquals(1, rows[1].season)
        assertEquals(2, rows[1].episode)
        assertFalse(rows[1].watched)
        assertTrue(rows[1].isCurrent)

        // fourth mapped to s=1, e=4
        assertEquals(1, rows[2].season)
        assertEquals(4, rows[2].episode)
        assertFalse(rows[2].watched)
        assertFalse(rows[2].isCurrent)
    }

    @Test
    fun testMapTmdbEpisodes() {
        val today = LocalDate.now().toString()
        val future = LocalDate.now().plusDays(2).toString()

        val seasonDetail = SeasonDetail(
            number = 1,
            episodes = listOf(
                Episode(number = 1, name = "E1", overview = "", stillPath = null, runtimeMinutes = null, airDate = today),
                Episode(number = 2, name = "E2", overview = "", stillPath = null, runtimeMinutes = null, airDate = today),
                Episode(number = 3, name = "E3", overview = "", stillPath = null, runtimeMinutes = null, airDate = future)
            )
        )
        val history = listOf(
            WatchHistoryEntity(tmdbId = 1, type = MediaType.Tv, title = "", posterPath = null, season = 1, episode = 1, positionMs = 100, durationMs = 100, lastWatchedAt = 0) // Watched
        )

        val rows = EpisodePanelRules.mapTmdbEpisodes(seasonDetail, currentSeason = 1, currentEpisode = 2, history)

        assertEquals(2, rows.size) // future episode removed

        assertEquals(1, rows[0].season)
        assertEquals(1, rows[0].episode)
        assertTrue(rows[0].watched)
        assertFalse(rows[0].isCurrent)

        assertEquals(1, rows[1].season)
        assertEquals(2, rows[1].episode)
        assertFalse(rows[1].watched)
        assertTrue(rows[1].isCurrent)
    }
}
