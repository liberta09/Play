package com.kaan.play.data.sync.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.sync.LocalProfileRepository
import com.kaan.play.data.sync.LocalSyncBackup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemoryPreferencesStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

private class TestFavoritesDao : FavoriteDao {
    val rows = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    var shouldThrowOnAdd = false

    override fun observeAll(userId: String, profileId: String): Flow<List<FavoriteEntity>> =
        rows.map { list -> list.filter { it.userId == userId && it.profileId == profileId } }

    override suspend fun all(userId: String, profileId: String): List<FavoriteEntity> =
        rows.value.filter { it.userId == userId && it.profileId == profileId }

    override fun observeIsFavorite(userId: String, profileId: String, tmdbId: Int, type: MediaType): Flow<Boolean> =
        rows.map { list -> list.any { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type } }

    override suspend fun add(entity: FavoriteEntity) {
        if (shouldThrowOnAdd) throw RuntimeException("Simulated favorites.add DB error")
        rows.value = rows.value.filterNot {
            it.userId == entity.userId && it.profileId == entity.profileId && it.tmdbId == entity.tmdbId && it.type == entity.type
        } + entity
    }

    override suspend fun remove(userId: String, profileId: String, tmdbId: Int, type: MediaType) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type }
    }

    override suspend fun clear(userId: String, profileId: String) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId }
    }

    override suspend fun adoptLegacy(userId: String, profileId: String) {
        rows.value = rows.value.map { if (it.userId == "legacy-user") it.copy(userId = userId, profileId = profileId) else it }
    }
}

private class TestHistoryDao : WatchHistoryDao {
    val rows = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
    var shouldThrowOnUpsert = false
    private var nextId = 1L

    override fun observeLatestPerContent(userId: String, profileId: String): Flow<List<WatchHistoryEntity>> =
        rows.map { list -> list.filter { it.userId == userId && it.profileId == profileId } }

    override suspend fun upsert(entity: WatchHistoryEntity) {
        if (shouldThrowOnUpsert) throw RuntimeException("Simulated history.upsert DB error")
        val kept = rows.value.filterNot {
            it.userId == entity.userId && it.profileId == entity.profileId && it.tmdbId == entity.tmdbId && it.type == entity.type && it.season == entity.season && it.episode == entity.episode
        }
        rows.value = kept + entity.copy(id = if (entity.id != 0L) entity.id else nextId++)
    }

    override fun observeHistory(userId: String, profileId: String): Flow<List<WatchHistoryEntity>> =
        rows.map { list -> list.filter { it.userId == userId && it.profileId == profileId } }

    override suspend fun all(userId: String, profileId: String): List<WatchHistoryEntity> =
        rows.value.filter { it.userId == userId && it.profileId == profileId }

    override suspend fun delete(userId: String, profileId: String, id: Long) {
        rows.value = rows.value.filterNot { it.id == id && it.userId == userId && it.profileId == profileId }
    }

    override suspend fun deleteForMedia(userId: String, profileId: String, tmdbId: Int, type: String) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type.name.equals(type, ignoreCase = true) }
    }

    override suspend fun clear(userId: String, profileId: String) {
        rows.value = rows.value.filterNot { it.userId == userId && it.profileId == profileId }
    }

    override suspend fun find(userId: String, profileId: String, tmdbId: Int, type: MediaType, season: Int, episode: Int) =
        rows.value.firstOrNull { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type && it.season == season && it.episode == episode }

    override suspend fun adoptLegacy(userId: String, profileId: String) {
        rows.value = rows.value.map { if (it.userId == "legacy-user") it.copy(userId = userId, profileId = profileId) else it }
    }

    override suspend fun markFinished(userId: String, profileId: String, id: Long, lastWatchedAt: Long) {
        rows.value = rows.value.map {
            if (it.id == id && it.userId == userId && it.profileId == profileId && it.durationMs > 0) {
                it.copy(positionMs = it.durationMs, lastWatchedAt = lastWatchedAt)
            } else it
        }
    }
}

class ProfileDataMigratorTest {

    private val store = MemoryPreferencesStore()
    private val profiles = LocalProfileRepository(store)
    private val favDao = TestFavoritesDao()
    private val histDao = TestHistoryDao()
    private val backups = LocalSyncBackup(history = histDao, favorites = favDao, profile = profiles)
    private val warnings = mutableListOf<String>()
    private val migrator = ProfileDataMigrator(
        profiles = profiles,
        backups = backups,
        history = histDao,
        favorites = favDao,
        logWarn = { warnings.add(it) },
    )

    private val userId = "user-1"
    private val discardedId = "disc-1"
    private val keptId = "kept-1"

    @Test
    fun whenMergeJsonForFailsForFavorites_clearIsNotCalled_recordsRemainInDiscardedProfile() = runTest {
        favDao.add(FavoriteEntity(tmdbId = 101, type = MediaType.Movie, userId = userId, profileId = discardedId, title = "Film 1", posterPath = null, addedAt = 100))
        favDao.shouldThrowOnAdd = true
        profiles.recordPendingRemaps(mapOf(discardedId to keptId))

        val success = migrator.migrateProfile(userId, discardedId, keptId)

        assertFalse(success)
        assertEquals(listOf("taşıma başarısız: favoriler"), warnings)

        // Clear ÇAĞRILMADI: kayıtlar atılan profilde sağlam kalmalı
        assertEquals(1, favDao.all(userId, discardedId).size)
        assertEquals(101, favDao.all(userId, discardedId).single().tmdbId)
        assertEquals(0, favDao.all(userId, keptId).size)

        // Atılan kimlik bekleyenler listesinde kalmalı (sonraki eşitlemede yeniden denenir)
        assertEquals(mapOf(discardedId to keptId), profiles.getPendingRemappedIds())
    }

    @Test
    fun whenMergeJsonForFailsForHistory_clearIsNotCalled_recordsRemainInDiscardedProfile() = runTest {
        histDao.upsert(WatchHistoryEntity(tmdbId = 202, type = MediaType.Movie, userId = userId, profileId = discardedId, season = 0, episode = 0, positionMs = 5000, durationMs = 10000, lastWatchedAt = 200, title = "Film 2", posterPath = null))
        histDao.shouldThrowOnUpsert = true
        profiles.recordPendingRemaps(mapOf(discardedId to keptId))

        val success = migrator.migrateProfile(userId, discardedId, keptId)

        assertFalse(success)
        assertEquals(listOf("taşıma başarısız: geçmiş"), warnings)

        // Clear ÇAĞRILMADI: geçmiş kayıtları atılan profilde sağlam kalmalı
        assertEquals(1, histDao.all(userId, discardedId).size)
        assertEquals(202, histDao.all(userId, discardedId).single().tmdbId)
        assertEquals(0, histDao.all(userId, keptId).size)

        // Atılan kimlik bekleyenler listesinde kalmalı
        assertEquals(mapOf(discardedId to keptId), profiles.getPendingRemappedIds())
    }

    @Test
    fun whenMergeJsonForSucceeds_recordsAreMigratedAndCleared_andRemovedFromPending() = runTest {
        favDao.add(FavoriteEntity(tmdbId = 101, type = MediaType.Movie, userId = userId, profileId = discardedId, title = "Film 1", posterPath = null, addedAt = 100))
        histDao.upsert(WatchHistoryEntity(tmdbId = 202, type = MediaType.Movie, userId = userId, profileId = discardedId, season = 0, episode = 0, positionMs = 5000, durationMs = 10000, lastWatchedAt = 200, title = "Film 2", posterPath = null))
        profiles.recordPendingRemaps(mapOf(discardedId to keptId))

        val success = migrator.migrateProfile(userId, discardedId, keptId)

        assertTrue(success)
        assertTrue(warnings.isEmpty())

        // Başarılı taşıma: eski profildeki kayıtlar temizlendi
        assertEquals(0, favDao.all(userId, discardedId).size)
        assertEquals(0, histDao.all(userId, discardedId).size)

        // Korunan profile eksiksiz taşındı (veri kaybı yok)
        assertEquals(1, favDao.all(userId, keptId).size)
        assertEquals(101, favDao.all(userId, keptId).single().tmdbId)
        assertEquals(1, histDao.all(userId, keptId).size)
        assertEquals(202, histDao.all(userId, keptId).single().tmdbId)

        // Pending remap temizlendi
        assertTrue(profiles.getPendingRemappedIds().isEmpty())
    }

    @Test
    fun failedMigrationIsRetriedAndSuccessfullyMigratedOnNextConsolidateCall() = runTest {
        favDao.add(FavoriteEntity(tmdbId = 303, type = MediaType.Movie, userId = userId, profileId = discardedId, title = "Film 3", posterPath = null, addedAt = 300))
        favDao.shouldThrowOnAdd = true

        // 1. Deneme: Hata aldı, clear yapılmadı
        migrator.migrateAllPending(userId, extraRemaps = mapOf(discardedId to keptId))
        assertEquals(1, favDao.all(userId, discardedId).size)
        assertEquals(0, favDao.all(userId, keptId).size)
        assertEquals(mapOf(discardedId to keptId), profiles.getPendingRemappedIds())

        // Veritabanı düzeldi
        favDao.shouldThrowOnAdd = false

        // 2. Deneme: Sonraki consolidate/apply çağrısı pending'i yeniden dener ve başarır
        migrator.migrateAllPending(userId)

        assertEquals(0, favDao.all(userId, discardedId).size)
        assertEquals(1, favDao.all(userId, keptId).size)
        assertEquals(303, favDao.all(userId, keptId).single().tmdbId)
        assertTrue(profiles.getPendingRemappedIds().isEmpty())
    }

    @Test
    fun chainedPendingRemapsResolveToLatestKeptProfile() = runTest {
        profiles.recordPendingRemaps(mapOf("d1" to "d2"))
        profiles.recordPendingRemaps(mapOf("d2" to "default"))

        val pending = profiles.getPendingRemappedIds()
        assertEquals("default", pending["d1"])
        assertEquals("default", pending["d2"])
    }
}
