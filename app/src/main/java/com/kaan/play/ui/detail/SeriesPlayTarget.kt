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
    suspend fun resolve(
        history: WatchHistoryEntity?,
        selectedSeason: Int?,
        episodes: List<Episode>?,
        nextEpisode: (suspend (season: Int, episode: Int) -> Pair<Int, Int>?)? = null,
        today: String? = null,
    ): Pair<Int, Int> {
        val fallbackSeason = (selectedSeason ?: 1).let { if (it <= 0) 1 else it }

        if (history != null) {
            val isFinished = WatchRules.isWatched(history.positionMs, history.durationMs)
            val currentSeason = if (history.season <= 0) fallbackSeason else history.season
            val currentEpisode = if (history.episode <= 0) 1 else history.episode

            if (!isFinished) {
                // Kaldığı bölüm
                return currentSeason to currentEpisode
            } else {
                // Bitmişse sonraki bölüm
                if (nextEpisode != null) {
                    val next = nextEpisode.invoke(currentSeason, currentEpisode)
                    if (next != null) {
                        return next
                    }
                    // Sonraki yoksa kaldığı bölüm
                    return currentSeason to currentEpisode
                }

                // Seçili sezonun yüklü bölümleri içinden sonrakini ara (nextEpisode verilmediyse eski davranış)
                if (history.season == selectedSeason && !episodes.isNullOrEmpty()) {
                    val later = episodes.filter {
                        it.number > currentEpisode && (today == null || (it.airDate != null && it.airDate <= today))
                    }.minByOrNull { it.number }
                    if (later != null) {
                        return currentSeason to later.number
                    }
                }
                // Varsayılan: bir sonraki bölüm numarası
                val nextEp = (currentEpisode + 1).coerceAtLeast(1)
                return currentSeason to nextEp
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
