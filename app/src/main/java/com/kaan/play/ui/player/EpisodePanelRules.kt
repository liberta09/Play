package com.kaan.play.ui.player

import com.kaan.play.data.cloudstream.CsEpisodeRef
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonDetail

/** Oynatıcıdaki Bölümler paneli kuralları (Android'den bağımsız, birim testli). */
object EpisodePanelRules {

    /** Paneldeki bir bölüm satırı. [dataUrl] yalnızca eklentinin kendi listesinden gelir (bölümü açmak için). */
    data class EpisodeRow(
        val season: Int,
        val episode: Int,
        val dataUrl: String?,
        val watched: Boolean,
        val isCurrent: Boolean,
    )

    /** "Bölümler" düğmesi: yalnızca dizide (sezon/bölüm dolu), canlı TV ve Watch Party konuğunda yok. */
    fun canShowButton(type: MediaType?, season: Int?, episode: Int?, addonType: String?, isGuest: Boolean): Boolean {
        if (isGuest) return false
        if (addonType?.lowercase() in listOf("tv", "channel")) return false
        if (type != MediaType.Tv) return false
        if (season == null || episode == null) return false
        return true
    }

    /** Panel açılınca (ya da sezon değişince) odaklanacak satır: oynayan bölüm; bu sezonda yoksa ilk satır. */
    fun initialIndex(rows: List<EpisodeRow>): Int = rows.indexOfFirst { it.isCurrent }.coerceAtLeast(0)

    /** Önceki/sonraki sezon numarası; 1'in altına inilmez (özel bölümler sezonu 0 gösterilmez). */
    fun neighborSeason(season: Int, forward: Boolean): Int? =
        if (forward) season + 1 else (season - 1).takeIf { it >= 1 }

    /** Oynayan bölüme Tamam/dokunuş yalnızca paneli kapatır; başka bölüm açılır. */
    fun shouldOpen(row: EpisodeRow): Boolean = !row.isCurrent

    private fun watched(history: List<WatchHistoryEntity>, season: Int, episode: Int): Boolean =
        history.any { it.season == season && it.episode == episode && WatchRules.isWatched(it.positionMs, it.durationMs) }

    /**
     * Eklentinin kendi bölüm listesi → [shownSeason] sezonunun satırları. Numarası olmayan girdide sezon 1, bölüm liste
     * sırası ([CsNextEpisodeFinder.pickNext] ile tutarlı). Oynayan bölüm önce eklenti verisiyle ([playingDataUrl]) bulunur.
     */
    fun mapPluginEpisodes(
        csEpisodes: List<CsEpisodeRef>,
        shownSeason: Int,
        playingSeason: Int,
        playingEpisode: Int,
        playingDataUrl: String?,
        history: List<WatchHistoryEntity>,
    ): List<EpisodeRow> {
        val byData = !playingDataUrl.isNullOrBlank() && csEpisodes.any { it.data == playingDataUrl }
        return csEpisodes.mapIndexed { index, ref ->
            val s = ref.season ?: 1
            val e = ref.episode ?: (index + 1)
            val current = if (byData) ref.data == playingDataUrl else s == playingSeason && e == playingEpisode
            EpisodeRow(s, e, ref.data, watched(history, s, e), current)
        }.filter { it.season == shownSeason }
    }

    /** TMDB sezonu → satırlar. Yayın tarihi bugünden sonra olan bölüm gösterilmez; tarihi bilinmeyen gösterilir. */
    fun mapTmdbEpisodes(
        seasonDetail: SeasonDetail,
        shownSeason: Int,
        playingSeason: Int,
        playingEpisode: Int,
        history: List<WatchHistoryEntity>,
        today: String,
    ): List<EpisodeRow> =
        seasonDetail.episodes
            .filter { ep -> ep.airDate.isNullOrBlank() || ep.airDate <= today }
            .map { ep ->
                val e = ep.number
                EpisodeRow(
                    season = shownSeason,
                    episode = e,
                    dataUrl = null,
                    watched = watched(history, shownSeason, e),
                    isCurrent = shownSeason == playingSeason && e == playingEpisode,
                )
            }

    /** Çevrimdışı oturum: yalnızca indirilmiş bölümler, sıralı ve tekrarsız. */
    fun mapDownloadedEpisodes(
        episodes: List<Int>,
        shownSeason: Int,
        playingSeason: Int,
        playingEpisode: Int,
        history: List<WatchHistoryEntity>,
    ): List<EpisodeRow> =
        episodes.filter { it > 0 }.distinct().sorted().map { e ->
            EpisodeRow(
                season = shownSeason,
                episode = e,
                dataUrl = null,
                watched = watched(history, shownSeason, e),
                isCurrent = shownSeason == playingSeason && e == playingEpisode,
            )
        }
}
