package com.kaan.play.data.library

import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import kotlin.jvm.JvmName

/**
 * "İzlemeye devam et" listesinden kaldırma kuralları (Android'den bağımsız saf sınıf).
 *
 * Kurallar:
 * 1. Dizide bir kart kaldırılınca o dizinin TÜM bölüm kayıtları silinir (tek bölüm değil).
 * 2. Filmde o filmin tek izleme kaydı silinir.
 * 3. Olmayan bir kayıt silinmek istendiğinde hata verilmez (sessizce atlanır).
 * 4. Silme sonrasında odak komşu karta geçer: aynı indeksteki yeni kart, yoksa son kart,
 *    satır tamamen boşalırsa null döner.
 */
object ContinueDeletionRules {

    data class Target(val tmdbId: Int, val type: MediaType)

    fun targetFrom(item: ContinueItem): Target = Target(item.entry.tmdbId, item.entry.type)

    fun targetFrom(entry: WatchHistoryEntity): Target = Target(entry.tmdbId, entry.type)

    /**
     * Verilen izleme kaydının hedef silme kriterine uyup uymadığını belirler.
     * Dizide aynı tmdbId'ye sahip tüm sezon ve bölümler eşleşir.
     * Filmde aynı tmdbId'ye sahip film kaydı eşleşir.
     */
    fun shouldDelete(entry: WatchHistoryEntity, target: Target): Boolean =
        entry.tmdbId == target.tmdbId && entry.type == target.type

    fun shouldDelete(item: ContinueItem, target: Target): Boolean =
        shouldDelete(item.entry, target)

    /**
     * Geçmiş listesinden hedef içeriğe ait tüm kayıtları filtreler (çıkarır).
     */
    fun filterRemaining(history: List<WatchHistoryEntity>, target: Target): List<WatchHistoryEntity> =
        history.filterNot { shouldDelete(it, target) }

    @JvmName("filterRemainingItems")
    fun filterRemaining(items: List<ContinueItem>, target: Target): List<ContinueItem> =
        items.filterNot { shouldDelete(it, target) }

    /**
     * Bir kart silindiğinde odağın geçeceği bir sonraki kartı belirler.
     * Satır boşaldıysa null döner (böylece odak üst/alt satıra devredilir).
     */
    fun <T> nextFocusedItem(removedIndex: Int, remaining: List<T>): T? {
        if (remaining.isEmpty()) return null
        return remaining.getOrNull(removedIndex) ?: remaining.lastOrNull()
    }
}
