package com.kaan.play.data.library

import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.sync.DeletedLibraryItem
import com.kaan.play.data.sync.LocalProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Yerel kütüphane: izleme geçmişi ve favoriler. */
class LibraryRepository(
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val profile: LocalProfileRepository? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val libraryScope = profile?.libraryScope ?: kotlinx.coroutines.flow.flowOf("legacy-user" to "legacy-profile")
    private suspend fun scope(): Pair<String, String> = libraryScope.first()

    val latestPerContent: Flow<List<WatchHistoryEntity>> = libraryScope.flatMapLatest { (u, p) ->
        history.observeLatestPerContent(u, p)
    }
    val watchHistory: Flow<List<WatchHistoryEntity>> = libraryScope.flatMapLatest { (u, p) ->
        history.observeHistory(u, p)
    }
    val allFavorites: Flow<List<FavoriteEntity>> = libraryScope.flatMapLatest { (u, p) ->
        favorites.observeAll(u, p)
    }

    suspend fun removeFavorite(type: MediaType, tmdbId: Int) {
        val (u, p) = scope()
        favorites.remove(u, p, tmdbId, type)
        profile?.recordDeletedFavorite(u, p, tmdbId, type.name.lowercase(), clock())
        recordFavoriteRemoved(p, type, tmdbId)
    }

    /**
     * Favoriden çıkarma silinenler kaydına yazılır: eşitlemede öteki cihazın kopyası geri getirmez. Bulut biçiminde
     * favori silme kaydı yok (sunucu ve yayındaki sürümler tanımaz) → yalnız Wi-Fi'de taşınır, bulutta bu cihazda süzer.
     */
    private suspend fun recordFavoriteRemoved(profileId: String, type: MediaType, tmdbId: Int) {
        profile?.saveDeletedItems(
            listOf(DeletedLibraryItem(profileId, DeletedLibraryItem.KIND_FAVORITE, tmdbId, type.name.lowercase(), deletedAt = clock())),
        )
    }

    /**
     * Geçmişten tek kayıt silme. İçeriğin son kaydıysa "Devam et'ten kaldır" ile aynı içerik mezar taşı (bulut dahil
     * her yolda, yayındaki sürümler de uygular); dizinin başka bölümleri duruyorsa yalnız bu bölüm (Wi-Fi kaydı — içerik
     * mezar taşı öteki bölümleri de silerdi, bulut biçiminde bölüm kaydı yok).
     */
    suspend fun removeHistory(id: Long) {
        val (u, p) = scope()
        val row = history.all(u, p).firstOrNull { it.id == id }
        history.delete(u, p, id)
        row ?: return
        val now = clock()
        val type = row.type.name.lowercase()
        if (history.all(u, p).none { it.tmdbId == row.tmdbId && it.type == row.type }) {
            profile?.recordDeletedHistory(u, p, row.tmdbId, type, now)
        } else {
            profile?.saveDeletedItems(
                listOf(DeletedLibraryItem(p, DeletedLibraryItem.KIND_EPISODE, row.tmdbId, type, row.season, row.episode, now)),
            )
        }
    }

    suspend fun removeContinueWatching(type: MediaType, tmdbId: Int) {
        val (u, p) = scope()
        history.deleteForMedia(u, p, tmdbId, type.name)
        profile?.recordDeletedHistory(u, p, tmdbId, type.name.lowercase(), clock())
    }

    suspend fun removeContinueWatching(item: ContinueItem) {
        removeContinueWatching(item.entry.type, item.entry.tmdbId)
    }

    /** "İzlemeye devam et" kartını izlendi olarak işaretler (konum = süre). */
    suspend fun markAsWatched(item: ContinueItem) {
        val (u, p) = scope()
        val now = clock()
        when (item) {
            is ContinueItem.Resume -> {
                val entry = item.entry
                if (entry.durationMs > 0L) {
                    history.markFinished(u, p, entry.id, now)
                } else {
                    val dur = entry.positionMs.coerceAtLeast(1L)
                    history.upsert(
                        entry.copy(
                            userId = u,
                            profileId = p,
                            positionMs = dur,
                            durationMs = dur,
                            lastWatchedAt = now,
                        ),
                    )
                }
            }
            is ContinueItem.Next -> {
                val entry = item.entry
                val existing = history.find(u, p, entry.tmdbId, entry.type, item.season, item.episode)
                val dur = (existing?.durationMs ?: entry.durationMs).coerceAtLeast(1L)
                history.upsert(
                    WatchHistoryEntity(
                        id = existing?.id ?: 0L,
                        tmdbId = entry.tmdbId,
                        type = entry.type,
                        userId = u,
                        profileId = p,
                        season = item.season,
                        episode = item.episode,
                        positionMs = dur,
                        durationMs = dur,
                        lastWatchedAt = now,
                        title = entry.title,
                        posterPath = entry.posterPath,
                        pluginId = entry.pluginId,
                        dataUrl = entry.dataUrl,
                        contentUrl = entry.contentUrl,
                    ),
                )
            }
        }
    }

    /**
     * Geçmişi temizle: silinen her içerik için içerik mezar taşı (bulut dahil) + profilin "temizle" kaydı (Wi-Fi; bu
     * cihazda henüz olmayan, öteki cihazdaki eski kayıtları da kapsar).
     */
    suspend fun clearHistory() {
        val (u, p) = scope()
        val rows = history.all(u, p)
        history.clear(u, p)
        val now = clock()
        // Yukarı akış 1.3.x toplu kayıt yolu (tek işlem, budama silme zamanına göre); aynı DeletedHistoryItem kaydı.
        profile?.recordDeletedHistory(u, p, rows.map { it.tmdbId to it.type.name.lowercase() }.distinct(), now)
        profile?.saveDeletedItems(listOf(DeletedLibraryItem(p, DeletedLibraryItem.KIND_CLEAR, deletedAt = now)))
    }

    /**
     * Eklenti içeriği favorisinin (negatif kimlik; ör. TV'de eklenip eşitlemeyle gelen) açılacağı eklenti + içerik adresi:
     * aynı kimlikli izleme geçmişi satırından. Bilinmiyorsa null (favori satırı eklentiyi taşımaz).
     */
    suspend fun providerSourceOf(type: MediaType, tmdbId: Int): Pair<String, String>? {
        if (tmdbId >= 0) return null
        val (u, p) = scope()
        val row = history.all(u, p).firstOrNull {
            it.tmdbId == tmdbId && it.type == type && it.pluginId?.startsWith("cs:") == true && !it.contentUrl.isNullOrBlank()
        } ?: return null
        return row.pluginId!! to row.contentUrl!!
    }

    /** Detay açılınca: bu içeriğin resimsiz geçmiş satırlarına detaydaki resmi yazar (kendiliğinden onarım). */
    suspend fun fillMissingPoster(type: MediaType, tmdbId: Int, posterPath: String?) {
        if (posterPath.isNullOrBlank()) return
        val (u, p) = scope()
        history.fillMissingPoster(u, p, tmdbId, type, posterPath)
    }

    suspend fun clearFavorites() {
        val (u, p) = scope()
        val rows = favorites.all(u, p)
        val entries = rows
            .map { it.tmdbId to it.type.name.lowercase() }
            .distinct()
        if (entries.isNotEmpty()) {
            profile?.recordDeletedFavorites(u, p, entries, clock())
        }
        // Wi-Fi eşitlemesinin silinenler kaydı (DeletedLibraryItem): toplu silme de oraya yazılır.
        if (rows.isNotEmpty()) {
            val now = clock()
            profile?.saveDeletedItems(rows.map { DeletedLibraryItem(p, DeletedLibraryItem.KIND_FAVORITE, it.tmdbId, it.type.name.lowercase(), deletedAt = now) })
        }
        favorites.clear(u, p)
    }

    /** Tüm profillerdeki benzersiz favori dizileri (en son eklenen üstte, üst sınır [limit]). */
    suspend fun distinctFavoriteSeries(
        limit: Int = com.kaan.play.data.notify.NewEpisodeRules.MAX_SERIES_PER_CHECK,
    ): List<com.kaan.play.data.db.SeriesFavoriteRef> = favorites.distinctByType(MediaType.Tv, limit)

    fun isFavorite(type: MediaType, tmdbId: Int): Flow<Boolean> =
        libraryScope.flatMapLatest { (u, p) -> favorites.observeIsFavorite(u, p, tmdbId, type) }

    suspend fun setFavorite(media: MediaSummary, favorite: Boolean) {
        val (u, p) = scope()
        if (favorite) {
            favorites.add(
                FavoriteEntity(
                    tmdbId = media.id,
                    type = media.type,
                    userId = u,
                    profileId = p,
                    title = media.title,
                    posterPath = media.posterPath,
                    addedAt = clock(),
                ),
            )
            profile?.removeDeletedFavorite(p, media.id, media.type.name)
        } else {
            favorites.remove(u, p, media.id, media.type)
            profile?.recordDeletedFavorite(u, p, media.id, media.type.name.lowercase(), clock())
            recordFavoriteRemoved(p, media.type, media.id)
        }
    }

    suspend fun addHistoryToFavorites(entry: WatchHistoryEntity) {
        val (u, p) = scope()
        favorites.add(
            FavoriteEntity(
                tmdbId = entry.tmdbId,
                type = entry.type,
                userId = u,
                profileId = p,
                title = entry.title,
                posterPath = entry.posterPath,
                addedAt = clock(),
            )
        )
        profile?.removeDeletedFavorite(p, entry.tmdbId, entry.type.name)
    }

    suspend fun progress(target: PlayTarget): WatchHistoryEntity? {
        val (u, p) = scope()
        val r = target.request
        return history.find(u, p, r.tmdbId, r.type, r.season ?: 0, r.episode ?: 0)
    }

    suspend fun saveProgress(target: PlayTarget, positionMs: Long, durationMs: Long, playbackScope: Pair<String, String>? = null): Boolean {
        if (target.request.addonType in listOf("tv", "channel")) return false
        if (!WatchRules.shouldRecord(positionMs, durationMs)) return false
        val (u, p) = playbackScope ?: scope()
        val r = target.request
        val season = r.season ?: 0
        val episode = r.episode ?: 0
        val existing = history.find(u, p, r.tmdbId, r.type, season, episode)
        history.upsert(
            WatchHistoryEntity(
                id = existing?.id ?: 0,
                tmdbId = r.tmdbId,
                type = r.type,
                userId = u,
                profileId = p,
                season = season,
                episode = episode,
                positionMs = positionMs.coerceAtMost(durationMs),
                durationMs = durationMs,
                lastWatchedAt = clock(),
                title = target.title,
                posterPath = target.posterPath,
                pluginId = r.pluginId,
                dataUrl = r.dataUrl,
                contentUrl = r.contentUrl,
            ),
        )

        return true
    }
}
