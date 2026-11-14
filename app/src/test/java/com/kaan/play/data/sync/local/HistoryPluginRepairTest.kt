package com.kaan.play.data.sync.local

import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.FavoriteEntity
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.ProviderContentId
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class RepairHistoryDao : WatchHistoryDao {
    override suspend fun fillMissingPoster(userId: String, profileId: String, tmdbId: Int, type: com.kaan.play.data.model.MediaType, posterPath: String) = Unit
    val rows = MutableStateFlow<List<WatchHistoryEntity>>(emptyList())
    private var nextId = 1L

    override fun observeLatestPerContent(userId: String, profileId: String): Flow<List<WatchHistoryEntity>> =
        rows.map { list -> list.filter { it.userId == userId && it.profileId == profileId } }

    override suspend fun upsert(entity: WatchHistoryEntity) {
        val kept = rows.value.filterNot {
            (entity.id != 0L && it.id == entity.id) || it.userId == entity.userId && it.profileId == entity.profileId && it.tmdbId == entity.tmdbId && it.type == entity.type && it.season == entity.season && it.episode == entity.episode
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

class HistoryPluginRepairTest {
    private val hosts = mapOf(
        718L to setOf("dizimom.de"),
        801L to setOf("dramacix.com"),
        802L to setOf("dizipal.com"),
        803L to setOf("dizipal.com"),
    )
    private val url = "https://dramacix.com/dizi/kahraman-koca"

    @Test
    fun target_movesOnlyToTheSingleHostMatch() {
        assertEquals("cs:801", HistoryPluginRepair.target("cs:718", url, hosts))
        assertEquals("cs:801", HistoryPluginRepair.target("cs:718", "https://www.dramacix.com/x", hosts))
        assertNull(HistoryPluginRepair.target("cs:801", url, hosts)) // numara doğru
        assertNull(HistoryPluginRepair.target("cs:718", "https://dizipal.com/x", hosts)) // iki aday: dokunma
        assertNull(HistoryPluginRepair.target("cs:718", "https://bilinmeyen.org/x", hosts)) // aday yok
        assertNull(HistoryPluginRepair.target("cs:999", null, hosts)) // adres yok
        assertNull(HistoryPluginRepair.target("nuvio_addon:3", url, hosts)) // CloudStream değil
    }

    @Test
    fun repair_movesStaleRow_recomputesSyntheticId_keepsRealTmdbId() = runTest {
        val dao = RepairHistoryDao()
        val stale = row(id = 0, tmdbId = ProviderContentId.legacy("cs:718", url), pluginId = "cs:718", episode = 1, at = 10)
        val real = row(id = 0, tmdbId = 555, pluginId = "cs:718", episode = 2, at = 11)
        dao.upsert(stale); dao.upsert(real)

        val fixed = HistoryPluginRepair(dao, pluginHosts = { hosts }).repair("u", listOf("p"))

        assertEquals(2, fixed)
        val rows = dao.all("u", "p").sortedBy { it.episode }
        assertEquals(listOf("cs:801", "cs:801"), rows.map { it.pluginId })
        assertEquals(ProviderContentId.of("cs:801", url), rows[0].tmdbId)
        assertEquals(555, rows[1].tmdbId)
    }

    @Test
    fun repair_onClashKeepsTheNewerRow() = runTest {
        val dao = RepairHistoryDao()
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.legacy("cs:718", url), pluginId = "cs:718", episode = 1, at = 10))
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.of("cs:801", url), pluginId = "cs:801", episode = 1, at = 20))

        HistoryPluginRepair(dao, pluginHosts = { hosts }).repair("u", listOf("p"))

        val rows = dao.all("u", "p")
        assertEquals(1, rows.size)
        assertEquals(20, rows.single().lastWatchedAt)
        assertEquals("cs:801", rows.single().pluginId)
    }

    @Test
    fun repair_onClashMovedRowNewerReplacesOlder() = runTest {
        val dao = RepairHistoryDao()
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.legacy("cs:718", url), pluginId = "cs:718", episode = 1, at = 30))
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.of("cs:801", url), pluginId = "cs:801", episode = 1, at = 20))

        HistoryPluginRepair(dao, pluginHosts = { hosts }).repair("u", listOf("p"))

        val rows = dao.all("u", "p")
        assertEquals(1, rows.size)
        assertEquals(30, rows.single().lastWatchedAt)
        assertEquals("cs:801", rows.single().pluginId)
    }

    @Test
    fun repair_twoStaleRowsToSameKey_keepsNewest() = runTest {
        val dao = RepairHistoryDao()
        val synth718 = ProviderContentId.legacy("cs:718", url)
        dao.upsert(row(id = 0, tmdbId = synth718, pluginId = "cs:718", episode = 1, at = 10))
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.legacy("cs:900", url), pluginId = "cs:900", episode = 1, at = 40))
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.of("cs:801", url), pluginId = "cs:801", episode = 1, at = 20))

        HistoryPluginRepair(dao, pluginHosts = { hosts }).repair("u", listOf("p"))

        val rows = dao.all("u", "p")
        assertEquals(1, rows.size)
        assertEquals(40, rows.single().lastWatchedAt)
    }

    @Test
    fun target_samePluginFromTwoReposIsNotAmbiguous() {
        // Telefonda Dramacix iki depodan kurulu (100 ve 747); TV'nin cs:738'i telefonda SetFilmIzle.
        val twin = mapOf(738L to setOf("setfilmizle.uk"), 747L to setOf("dramacix.com"), 100L to setOf("dramacix.com"))
        val names = mapOf(738L to "SetFilmIzle", 747L to "Dramacix", 100L to "Dramacix")
        assertEquals("cs:100", HistoryPluginRepair.target("cs:738", url, twin, names))
        assertNull(HistoryPluginRepair.target("cs:747", url, twin, names)) // kopyalardan birinde zaten: dokunma
        assertNull(HistoryPluginRepair.target("cs:738", url, twin)) // ad bilgisi yok: eskisi gibi belirsiz
        // Farklı eklentiler aynı siteyi gösteriyorsa yine dokunulmaz.
        assertNull(HistoryPluginRepair.target("cs:718", "https://dizipal.com/x", hosts, mapOf(802L to "A", 803L to "B")))
    }

    @Test
    fun target_ownPluginEnabledButNotLoaded_untouched() {
        assertNull(HistoryPluginRepair.target("cs:718", url, hosts + (718L to emptySet())))
        assertEquals("cs:801", HistoryPluginRepair.target("cs:5", url, hosts)) // kapalı/silinmiş eklenti: haritada yok → taşınır
    }

    @Test
    fun repair_withoutCloudStreamRows_doesNotLoadPlugins() = runTest {
        val dao = RepairHistoryDao()
        dao.upsert(row(id = 0, tmdbId = 42, pluginId = null, episode = 0, at = 1))
        var loaded = false
        val fixed = HistoryPluginRepair(dao, pluginHosts = { loaded = true; hosts }).repair("u", listOf("p"))
        assertEquals(0, fixed)
        assertFalse(loaded)
    }

    @Test
    fun repair_keepsProgressSavedWhilePluginsLoad() = runTest {
        val dao = RepairHistoryDao()
        dao.upsert(row(id = 0, tmdbId = 555, pluginId = "cs:718", episode = 1, at = 10))
        val id = dao.all("u", "p").single().id
        // Eklentiler yüklenirken oynatıcı ilerleme kaydeder.
        val fixed = HistoryPluginRepair(dao, pluginHosts = {
            dao.upsert(dao.all("u", "p").single().copy(positionMs = 9_000, lastWatchedAt = 50))
            hosts
        }).repair("u", listOf("p"))

        assertEquals(1, fixed)
        val after = dao.all("u", "p").single()
        assertEquals(id, after.id)
        assertEquals("cs:801", after.pluginId)
        assertEquals(9_000, after.positionMs)
        assertEquals(50, after.lastWatchedAt)
    }

    @Test
    fun repair_readsPluginSitesEveryCall() = runTest {
        val dao = RepairHistoryDao()
        var current = hosts - 801L // Dramacix henüz kurulu değil
        val repair = HistoryPluginRepair(dao, pluginHosts = { current })
        dao.upsert(row(id = 0, tmdbId = 555, pluginId = "cs:718", episode = 1, at = 10))

        assertEquals(0, repair.repair("u", listOf("p")))
        current = hosts // eklenti kuruldu
        assertEquals(1, repair.repair("u", listOf("p")))
        assertEquals("cs:801", dao.all("u", "p").single().pluginId)
    }

    @Test
    fun repairSafely_pluginErrorReturnsZeroAndIsLogged() = runTest {
        val dao = RepairHistoryDao()
        dao.upsert(row(id = 0, tmdbId = 555, pluginId = "cs:718", episode = 1, at = 10))
        val lines = mutableListOf<String>()
        // Eklenti kodundan Exception değil Error gelebilir; kaydı da kalmalı.
        val fixed = HistoryPluginRepair(dao, pluginHosts = { throw LinkageError("eklenti") }, log = { lines += it }).repairSafely("u", listOf("p"))
        assertEquals(0, fixed)
        assertTrue(lines.single().contains("başarısız: LinkageError"))
        assertEquals("cs:718", dao.all("u", "p").single().pluginId)
    }

    @Test
    fun repairSafely_doesNotSwallowCancellation() {
        val dao = RepairHistoryDao()
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.upsert(row(id = 0, tmdbId = 555, pluginId = "cs:718", episode = 1, at = 10))
                HistoryPluginRepair(dao, pluginHosts = { throw CancellationException("iptal") }).repairSafely("u", listOf("p"))
            }
        }
    }

    @Test
    fun repairSafely_runsOneAtATime() = runTest {
        val dao = RepairHistoryDao()
        dao.upsert(row(id = 0, tmdbId = 555, pluginId = "cs:718", episode = 1, at = 10))
        var active = 0
        var maxActive = 0
        val repair = HistoryPluginRepair(dao, pluginHosts = {
            active++; maxActive = maxOf(maxActive, active)
            delay(100) // eklenti yüklemesi sürerken ikinci çağrı gelir
            active--
            hosts
        })
        val first = async { repair.repairSafely("u", listOf("p")) }
        val second = async { repair.repairSafely("u", listOf("p")) }
        assertEquals(1, first.await() + second.await()) // ikinci, birincinin bitmiş hâlini görür
        assertEquals(1, maxActive)
        assertEquals("cs:801", dao.all("u", "p").single().pluginId)
    }

    @Test
    fun migrateIds_movesLegacyRowsFavoriteAndLeavesTombstone() = runTest {
        val dao = RepairHistoryDao()
        val favs = RepairFavoriteDao()
        val legacy = ProviderContentId.legacy("cs:718", url)
        dao.upsert(row(id = 0, tmdbId = legacy, pluginId = "cs:718", episode = 1, at = 10))
        dao.upsert(row(id = 0, tmdbId = legacy, pluginId = "cs:718", episode = 2, at = 11))
        dao.upsert(row(id = 0, tmdbId = 555, pluginId = "cs:718", episode = 3, at = 12)) // TMDB kimliği: dokunulmaz
        favs.add(FavoriteEntity(tmdbId = legacy, type = MediaType.Tv, userId = "u", profileId = "p", title = "Kahraman Koca", posterPath = null, addedAt = 5))
        // 1.1.52 test derlemesinin bıraktığı geçiş mezar taşı (eski kimlik, satırlardan yeni) temizlenir; gerçek silme kalır.
        val stored = mutableListOf(
            com.kaan.play.data.sync.DeletedHistoryItem(profileId = "p", tmdbId = legacy, type = "tv", deletedAt = 100),
            com.kaan.play.data.sync.DeletedHistoryItem(profileId = "p", tmdbId = -77, type = "tv", deletedAt = 100),
        )
        val repair = HistoryPluginRepair(dao, pluginHosts = { error("eklenti yüklenmemeli") }, favorites = favs,
            tombstones = { stored.toList() }, dropTombstones = { stored.removeAll(it) })

        assertEquals(2, repair.migrateIds("u", listOf("p")))

        val permanent = ProviderContentId.of("cs:718", url)
        assertEquals(listOf(permanent, permanent, 555), dao.all("u", "p").sortedBy { it.episode }.map { it.tmdbId })
        assertEquals(listOf(permanent), favs.rows.map { it.tmdbId })
        assertEquals(5, favs.rows.single().addedAt)
        assertEquals(listOf(-77), stored.map { it.tmdbId }) // geçiş mezar taşı bırakmaz, eskisini temizler
        assertEquals(0, repair.migrateIds("u", listOf("p"))) // ikinci çalıştırma bir şey yapmaz
    }

    @Test
    fun migrateIds_sameContentFromTwoDevicesBecomesOneRow() = runTest {
        // Telefonda cs:801, TV'de cs:718 numarasıyla izlenen aynı bölüm: eskiden iki kayıt, artık tek (yeni izlenen kalır).
        val dao = RepairHistoryDao()
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.legacy("cs:801", url), pluginId = "cs:801", episode = 1, at = 10))
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.legacy("cs:718", url), pluginId = "cs:718", episode = 1, at = 20))

        HistoryPluginRepair(dao, pluginHosts = { hosts }).migrateIds("u", listOf("p"))

        val single = dao.all("u", "p").single()
        assertEquals(ProviderContentId.of("cs:801", url), single.tmdbId)
        assertEquals(20, single.lastWatchedAt)
    }

    @Test
    fun providerContentId_isPermanentAcrossDevicesAndUrlSpelling() {
        assertEquals(ProviderContentId.of("cs:718", url), ProviderContentId.of("cs:801", url))
        assertEquals(ProviderContentId.of("cs:1", url), ProviderContentId.of("cs:2", "http://WWW.Dramacix.com/dizi/kahraman-koca/"))
        assertFalse(ProviderContentId.of("cs:1", url) == ProviderContentId.of("cs:1", "https://dizipal.com/dizi/kahraman-koca"))
        assertFalse(ProviderContentId.of("cs:1", url) == ProviderContentId.of("nuvio_addon:1", url))
        assertTrue(ProviderContentId.of("cs:1", url) < 0)
        // Eski hesap birebir korunur (geçiş eski satırı tanıyabilsin).
        assertEquals(-(kotlin.math.abs("cs:718:$url".hashCode()).coerceAtLeast(1)), ProviderContentId.legacy("cs:718", url))
        assertEquals("tt123/1", ProviderContentId.normalize("tt123/1"))
    }

    @Test
    fun migrateIds_foldsUrlLessCloudCopiesAndFavorites() = runTest {
        // Buluttan daha önce gelmiş adressiz, eski kimlikli kopyalar veritabanında kalıyordu (yinelenen kart).
        val dao = RepairHistoryDao()
        val favs = RepairFavoriteDao()
        val permanent = ProviderContentId.of("cs:718", url)
        val legacy = ProviderContentId.legacy("cs:718", url)
        dao.upsert(row(id = 0, tmdbId = permanent, pluginId = "cs:718", episode = 1, at = 30))
        dao.upsert(row(id = 0, tmdbId = legacy, pluginId = "cloud:x", episode = 1, at = 10).copy(contentUrl = null)) // eski kopya
        dao.upsert(row(id = 0, tmdbId = legacy, pluginId = null, episode = 2, at = 20).copy(contentUrl = null)) // yalnız bulutta izlenmiş
        dao.upsert(row(id = 0, tmdbId = -999, pluginId = null, episode = 1, at = 5).copy(contentUrl = null, title = "Başka dizi"))
        favs.add(FavoriteEntity(tmdbId = legacy, type = MediaType.Tv, userId = "u", profileId = "p", title = "Kahraman Koca", posterPath = null, addedAt = 5))
        favs.add(FavoriteEntity(tmdbId = permanent, type = MediaType.Tv, userId = "u", profileId = "p", title = "Kahraman Koca", posterPath = null, addedAt = 9))

        HistoryPluginRepair(dao, pluginHosts = { hosts }, favorites = favs).migrateIds("u", listOf("p"))

        val rows = dao.all("u", "p").sortedBy { it.episode }
        val ours = rows.filter { it.tmdbId == permanent }
        assertEquals(listOf(1, 2), ours.map { it.episode })
        assertEquals(30, ours.first().lastWatchedAt) // eski kopya yeni izlenene katlandı
        assertTrue(ours.all { it.contentUrl == url && it.pluginId == "cs:718" }) // kart açılabilir
        assertTrue(rows.any { it.tmdbId == -999 }) // eşlenemeyen dokunulmaz
        assertEquals(listOf(permanent), favs.rows.map { it.tmdbId }) // tek favori
    }

    @Test
    fun rekey_onEqualTimeKeepsTheRowWithUrl() = runTest {
        // Eşit zamanda adressiz kopya kalınca eklenti/adres/oynatma verisi gidiyordu.
        val dao = RepairHistoryDao()
        val permanent = ProviderContentId.of("cs:718", url)
        dao.upsert(row(id = 0, tmdbId = permanent, pluginId = null, episode = 1, at = 10).copy(contentUrl = null))
        dao.upsert(row(id = 0, tmdbId = ProviderContentId.legacy("cs:718", url), pluginId = "cs:718", episode = 1, at = 10).copy(dataUrl = "d"))

        HistoryPluginRepair(dao, pluginHosts = { hosts }).migrateIds("u", listOf("p"))

        val single = dao.all("u", "p").single()
        assertEquals(url, single.contentUrl)
        assertEquals("d", single.dataUrl)
    }

    private fun row(id: Long, tmdbId: Int, pluginId: String?, episode: Int, at: Long) = WatchHistoryEntity(
        id = id, tmdbId = tmdbId, type = MediaType.Tv, userId = "u", profileId = "p", season = 1, episode = episode,
        positionMs = 1, durationMs = 2, lastWatchedAt = at, title = "Kahraman Koca", posterPath = null,
        pluginId = pluginId, dataUrl = null, contentUrl = url,
    )
}

private class RepairFavoriteDao : FavoriteDao {
    val rows = mutableListOf<FavoriteEntity>()
    override suspend fun adoptLegacy(userId: String, profileId: String) = Unit
    override fun observeAll(userId: String, profileId: String): Flow<List<FavoriteEntity>> = MutableStateFlow(rows.toList())
    override suspend fun all(userId: String, profileId: String): List<FavoriteEntity> =
        rows.filter { it.userId == userId && it.profileId == profileId }
    override fun observeIsFavorite(userId: String, profileId: String, tmdbId: Int, type: MediaType): Flow<Boolean> =
        MutableStateFlow(rows.any { it.tmdbId == tmdbId && it.type == type })
    override suspend fun add(entity: FavoriteEntity) {
        rows.removeAll { it.userId == entity.userId && it.profileId == entity.profileId && it.tmdbId == entity.tmdbId && it.type == entity.type }
        rows += entity
    }
    override suspend fun remove(userId: String, profileId: String, tmdbId: Int, type: MediaType) {
        rows.removeAll { it.userId == userId && it.profileId == profileId && it.tmdbId == tmdbId && it.type == type }
    }
    override suspend fun clear(userId: String, profileId: String) {
        rows.removeAll { it.userId == userId && it.profileId == profileId }
    }
    override suspend fun distinctByType(type: MediaType, limit: Int): List<com.kaan.play.data.db.SeriesFavoriteRef> = emptyList()
}
