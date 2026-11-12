package com.kaan.play.ui.player

import com.kaan.play.data.cloudstream.CsEpisodeRef
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.SeasonDetail
import com.kaan.play.data.library.WatchRules

object EpisodePanelRules {
    data class EpisodeRow(
        val season: Int,
        val episode: Int,
        val dataUrl: String?,
        val watched: Boolean,
        val isCurrent: Boolean
    )

    fun canShowButton(type: MediaType?, season: Int?, episode: Int?, addonType: String?, isGuest: Boolean): Boolean {
        if (isGuest) return false
        if (addonType?.lowercase() in listOf("tv", "channel")) return false
        if (type != MediaType.Tv) return false
        if (season == null || episode == null) return false
        return true
    }

    fun mapPluginEpisodes(
        csEpisodes: List<CsEpisodeRef>,
        currentSeason: Int,
        currentEpisode: Int,
        history: List<WatchHistoryEntity>
    ): List<EpisodeRow> {
        return csEpisodes.mapIndexed { index, ref ->
            val s = ref.season ?: 1
            val e = ref.episode ?: (index + 1)
            val isCurrent = s == currentSeason && e == currentEpisode
            val watched = history.any {
                it.season == s && it.episode == e && WatchRules.isWatched(it.positionMs, it.durationMs)
            }
            EpisodeRow(s, e, ref.data, watched, isCurrent)
        }.filter { it.season == currentSeason }
    }

    fun mapTmdbEpisodes(
        seasonDetail: SeasonDetail,
        currentSeason: Int,
        currentEpisode: Int,
        history: List<WatchHistoryEntity>
    ): List<EpisodeRow> {
        val today = java.time.LocalDate.now().toString()
        return seasonDetail.episodes.filter { ep ->
            ep.airDate != null && ep.airDate <= today 
        }.map { ep ->
            val e = ep.number
            val isCurrent = currentSeason == currentSeason && e == currentEpisode
            val watched = history.any {
                it.season == currentSeason && it.episode == e && WatchRules.isWatched(it.positionMs, it.durationMs)
            }
            EpisodeRow(currentSeason, e, null, watched, isCurrent)
        }
    }
}
