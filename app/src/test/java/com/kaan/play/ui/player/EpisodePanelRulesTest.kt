package com.kaan.play.ui.player

import com.kaan.play.data.cloudstream.CsEpisodeRef
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodePanelRulesTest {

    private fun watchedEntry(season: Int, episode: Int) = WatchHistoryEntity(
        tmdbId = 1, type = MediaType.Tv, title = "", posterPath = null,
        season = season, episode = episode, positionMs = 100, durationMs = 100, lastWatchedAt = 0,
    )

    private fun ep(n: Int, airDate: String?) =
        Episode(number = n, name = "E$n", overview = "", stillPath = null, runtimeMinutes = null, airDate = airDate)

    @Test
    fun canShowButton_onlyForSeriesAndNotForGuestOrLive() {
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "tv", false))
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "channel", false))
        assertTrue(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "series", false))
        assertTrue(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, null, false))
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Movie, 1, 1, "series", false))
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, 1, 1, "series", true))
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, null, 1, "series", false))
        assertFalse(EpisodePanelRules.canShowButton(MediaType.Tv, 1, null, "series", false))
    }

    @Test
    fun mapPluginEpisodes_filtersShownSeasonAndMarksCurrentAndWatched() {
        val cs = listOf(
            CsEpisodeRef(1, 1, "url1"),
            CsEpisodeRef(1, 2, "url2"),
            CsEpisodeRef(2, 1, "url3"),
            CsEpisodeRef(null, null, "url4"), // sezon 1, bölüm = liste sırası (4)
        )
        val rows = EpisodePanelRules.mapPluginEpisodes(cs, shownSeason = 1, playingSeason = 1, playingEpisode = 2, playingDataUrl = null, history = listOf(watchedEntry(1, 1)))

        assertEquals(listOf(1, 2, 4), rows.map { it.episode })
        assertTrue(rows[0].watched)
        assertTrue(rows[1].isCurrent)
        assertFalse(rows[0].isCurrent)
        assertEquals("url2", rows[1].dataUrl)
    }

    @Test
    fun mapPluginEpisodes_prefersDataUrlForCurrent() {
        val cs = listOf(CsEpisodeRef(1, 1, "a"), CsEpisodeRef(1, 2, "b"))
        // Numaralar 1/1 dese de oynayan verisi "b": şimdi oynuyor 2. satır.
        val rows = EpisodePanelRules.mapPluginEpisodes(cs, 1, playingSeason = 1, playingEpisode = 1, playingDataUrl = "b", history = emptyList())
        assertFalse(rows[0].isCurrent)
        assertTrue(rows[1].isCurrent)
    }

    @Test
    fun mapPluginEpisodes_otherSeasonHasNoCurrent() {
        val cs = listOf(CsEpisodeRef(1, 1, "a"), CsEpisodeRef(2, 1, "b"), CsEpisodeRef(2, 2, "c"))
        val rows = EpisodePanelRules.mapPluginEpisodes(cs, shownSeason = 2, playingSeason = 1, playingEpisode = 1, playingDataUrl = "a", history = emptyList())
        assertEquals(listOf(1, 2), rows.map { it.episode })
        assertTrue(rows.none { it.isCurrent })
        assertEquals(0, EpisodePanelRules.initialIndex(rows))
    }

    @Test
    fun mapTmdbEpisodes_hidesFutureKeepsUnknownDate() {
        val season = SeasonDetail(number = 1, episodes = listOf(ep(1, "2026-01-01"), ep(2, "2026-11-13"), ep(3, null), ep(4, "2026-12-01")))
        val rows = EpisodePanelRules.mapTmdbEpisodes(season, shownSeason = 1, playingSeason = 1, playingEpisode = 2, history = listOf(watchedEntry(1, 1)), today = "2026-11-13")

        assertEquals(listOf(1, 2, 3), rows.map { it.episode })
        assertTrue(rows[0].watched)
        assertTrue(rows[1].isCurrent)
        assertNull(rows[0].dataUrl)
        assertEquals(1, EpisodePanelRules.initialIndex(rows))
    }

    @Test
    fun mapTmdbEpisodes_otherSeasonNotCurrent() {
        val season = SeasonDetail(number = 2, episodes = listOf(ep(1, "2026-01-01"), ep(2, "2026-01-08")))
        val rows = EpisodePanelRules.mapTmdbEpisodes(season, shownSeason = 2, playingSeason = 1, playingEpisode = 2, history = emptyList(), today = "2026-11-13")
        assertTrue(rows.none { it.isCurrent })
        assertTrue(rows.all { it.season == 2 })
    }

    @Test
    fun mapDownloadedEpisodes_sortedDistinctPositive() {
        val rows = EpisodePanelRules.mapDownloadedEpisodes(listOf(3, 1, 3, 0), shownSeason = 1, playingSeason = 1, playingEpisode = 3, history = emptyList())
        assertEquals(listOf(1, 3), rows.map { it.episode })
        assertTrue(rows[1].isCurrent)
    }

    @Test
    fun neighborSeason_neverBelowOne() {
        assertEquals(2, EpisodePanelRules.neighborSeason(1, forward = true))
        assertNull(EpisodePanelRules.neighborSeason(1, forward = false))
        assertEquals(1, EpisodePanelRules.neighborSeason(2, forward = false))
    }

    @Test
    fun shouldOpen_onlyOtherEpisodes() {
        val current = EpisodePanelRules.EpisodeRow(1, 2, null, watched = false, isCurrent = true)
        val other = current.copy(episode = 3, isCurrent = false)
        assertFalse(EpisodePanelRules.shouldOpen(current))
        assertTrue(EpisodePanelRules.shouldOpen(other))
    }

    @Test
    fun initialIndex_defaultsToFirst() {
        assertEquals(0, EpisodePanelRules.initialIndex(emptyList()))
    }
}
