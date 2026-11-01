package com.kaan.play.data.sync.local

import android.util.Log
import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileRepository
import com.kaan.play.data.sync.LocalSyncBackup
import kotlinx.coroutines.CancellationException

/**
 * Atılan "Varsayılan" profillerin Room veritabanındaki kayıtlarını (favoriler ve geçmiş)
 * korunan profile güvenle taşır.
 *
 * Taşıma (mergeJsonFor) başarısız olursa ASLA clear çağrılmaz, kayıtlar yerinde kalır,
 * hata loglanır ve atılan kimlik bir sonraki eşitlemede yeniden denenmek üzere DataStore'daki
 * pendingRemappedIds içinde saklanır.
 */
class ProfileDataMigrator(
    private val profiles: LocalProfileRepository,
    private val backups: LocalSyncBackup,
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val logWarn: (String) -> Unit = { Log.w(TAG, it) },
) {

    suspend fun migrateAllPending(userId: String, extraRemaps: Map<String, String> = emptyMap()) {
        if (extraRemaps.isNotEmpty()) {
            profiles.recordPendingRemaps(extraRemaps)
        }
        val pending = profiles.getPendingRemappedIds()
        for ((discardedId, keptId) in pending) {
            migrateProfile(userId, discardedId, keptId)
        }
    }

    suspend fun migrateProfile(userId: String, discardedId: String, keptId: String): Boolean {
        var favOk = true
        val discFavs = favorites.all(userId, discardedId)
        if (discFavs.isNotEmpty()) {
            val favBackups = discFavs.map { FavoriteBackup(it.tmdbId, it.type.name.lowercase(), it.title, it.posterPath, it.addedAt) }
            val res = backups.mergeJsonFor(userId, keptId, "", extraFavorites = favBackups)
            val e = res.exceptionOrNull()
            if (e is CancellationException) throw e
            if (res.isSuccess) {
                favorites.clear(userId, discardedId)
            } else {
                favOk = false
                logWarn("taşıma başarısız: favoriler")
            }
        }

        var histOk = true
        val discHist = history.all(userId, discardedId)
        if (discHist.isNotEmpty()) {
            val histBackups = discHist.map {
                HistoryBackup(
                    it.tmdbId,
                    it.type.name.lowercase(),
                    it.season,
                    it.episode,
                    it.positionMs,
                    it.durationMs,
                    it.lastWatchedAt,
                    it.title,
                    it.posterPath,
                    it.pluginId,
                    it.dataUrl,
                    it.contentUrl,
                )
            }
            val res = backups.mergeJsonFor(userId, keptId, "", extraHistory = histBackups)
            val e = res.exceptionOrNull()
            if (e is CancellationException) throw e
            if (res.isSuccess) {
                history.clear(userId, discardedId)
            } else {
                histOk = false
                logWarn("taşıma başarısız: geçmiş")
            }
        }

        val allOk = favOk && histOk
        if (allOk) {
            profiles.removePendingRemappedId(discardedId)
        }
        return allOk
    }

    companion object {
        private const val TAG = "LocalSyncData"
    }
}
