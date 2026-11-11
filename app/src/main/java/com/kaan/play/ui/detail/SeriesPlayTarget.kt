package com.kaan.play.ui.detail

import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.Episode

object SeriesPlayTarget {

    /**
     * Dizi için "Oynat" tıklandığında oynatılacak (sezon, bölüm) ikilisini belirler.
     * Android'den bağımsızdır, birim testlenebilir.
     *
     * @param history İlgili dizinin en son izleme geçmişi kaydı (varsa)
     * @param selectedSeason Kullanıcının seçtiği sezon numarası
     * @param episodes Seçili sezonun yüklenmiş bölüm listesi (henüz yüklenmediyse null)
     * @param nextEpisode Bitmiş bölümden sonraki bölümü çözen işlev (ör. NextEpisodeResolver / CsNextEpisode)
     * @param today Bugünün tarihi (yyyy-MM-dd), yayımlanma kontrolü için
     */
    fun resolve(
        history: WatchHistoryEntity?,
        selectedSeason: Int?,
        episodes: List<Episode>?,
        nextEpisode: ((season: Int, episode: Int) -> Pair<Int, Int>?)? = null,
        today: String? = null,
    ): Pair<Int, Int> {
        val fallbackSeason = (selectedSeason ?: 1).let { if (it <= 0) 1 else it }

        if (history != null) {
            val isFinished = WatchRules.isWatched(history.positionMs, history.durationMs)
            if (!isFinished) {
                // Kaldığı bölüm
                val s = if (history.season <= 0) fallbackSeason else history.season
                val ep = if (history.episode <= 0) 1 else history.episode
                return s to ep
            } else {
                // Bitmişse sonraki bölüm
                val next = nextEpisode?.invoke(history.season, history.episode)
                if (next != null) {
                    return next
                }
                // Seçili sezonun yüklü bölümleri içinden sonrakini ara
                if (history.season == selectedSeason && !episodes.isNullOrEmpty()) {
                    val later = episodes.filter {
                        it.number > history.episode && (today == null || (it.airDate != null && it.airDate <= today))
                    }.minByOrNull { it.number }
                    if (later != null) {
                        return history.season to later.number
                    }
                }
                // Varsayılan: bir sonraki bölüm numarası
                val nextEp = (history.episode + 1).coerceAtLeast(1)
                val s = if (history.season <= 0) fallbackSeason else history.season
                return s to nextEp
            }
        }

        // Geçmişte kayıt yok:
        // Bölüm listesi yüklenmediyse onPlay(sezon, 1)
        if (episodes.isNullOrEmpty()) {
            return fallbackSeason to 1
        }

        // SEÇİLİ sezonun ilk yayımlanmış bölümü (varsayılan S1B1; sezon 0 otomatik seçilmez)
        val published = if (today != null) {
            episodes.filter { it.number > 0 && it.airDate != null && it.airDate <= today }
        } else {
            episodes.filter { it.number > 0 }
        }

        val targetEp = published.minByOrNull { it.number }?.number
            ?: episodes.firstOrNull { it.number > 0 }?.number
            ?: 1

        return fallbackSeason to targetEp
    }
}
