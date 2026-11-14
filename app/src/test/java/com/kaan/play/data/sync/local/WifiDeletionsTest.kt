package com.kaan.play.data.sync.local

import com.kaan.play.data.db.PluginEntity
import com.kaan.play.data.library.ProviderContentId
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.sync.DeletedHistoryItem
import com.kaan.play.data.sync.DeletedLibraryItem
import com.kaan.play.data.sync.DeletedLibraryItem.Companion.KIND_CLEAR
import com.kaan.play.data.sync.DeletedLibraryItem.Companion.KIND_EPISODE
import com.kaan.play.data.sync.DeletedLibraryItem.Companion.KIND_FAVORITE
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.FavoriteSourceTransfer
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wi-Fi silinenler kaydı ve eklenti favorisinin açılış bilgisi (Wi-Fi'de): birleştirme kuralları,
 * eski sürümlerin yükü okuyabilmesi ve bulut yükünün DEĞİŞMEDİĞİ.
 */
class WifiDeletionsTest {

    private val now = System.currentTimeMillis()
    private val p = "p1"

    private fun ep(tmdb: Int, s: Int, e: Int, at: Long) = HistoryBackup(tmdb, "tv", s, e, 1000, 10_000, at, "Dizi$tmdb", null)
    private fun fav(tmdb: Int, at: Long, type: String = "tv") = FavoriteBackup(tmdb, type, "F$tmdb", null, at)
    private fun del(kind: String, tmdb: Int = 0, s: Int = 0, e: Int = 0, at: Long, type: String = if (kind == KIND_CLEAR) "" else "tv") =
        DeletedLibraryItem(p, kind, tmdb, type, s, e, at)

    @Test
    fun episodeRecordRemovesOnlyThatEpisodeAndKeepsLaterRewatch() {
        val remote = LocalProfileBackup(history = listOf(ep(5, 1, 1, now - 5000), ep(5, 1, 2, now - 4000), ep(6, 1, 2, now - 4000)))
        val merged = LocalSyncMerge.mergeLibrary(LocalProfileBackup(), remote, deletedItems = listOf(del(KIND_EPISODE, 5, 1, 2, now - 1000)))
        assertEquals(listOf(5 to 1, 6 to 2), merged.history.map { it.tmdbId to it.episode }.sortedBy { it.first })
        // Silmeden SONRA yeniden izlenen bölüm geri gelir (silme kaydı eski kopyayı süzer, yeni izlemeyi değil).
        val rewatched = LocalProfileBackup(history = listOf(ep(5, 1, 2, now)))
        val again = LocalSyncMerge.mergeLibrary(LocalProfileBackup(), rewatched, deletedItems = listOf(del(KIND_EPISODE, 5, 1, 2, now - 1000)))
        assertEquals(1, again.history.size)
    }

    @Test
    fun clearRecordRemovesEverythingOlderIncludingContentUnknownHere() {
        val remote = LocalProfileBackup(history = listOf(ep(5, 1, 1, now - 5000), ep(99, 2, 3, now - 3000), ep(7, 1, 1, now + 1000)))
        val merged = LocalSyncMerge.mergeLibrary(LocalProfileBackup(), remote, deletedItems = listOf(del(KIND_CLEAR, at = now)))
        assertEquals(listOf(7), merged.history.map { it.tmdbId })
    }

    @Test
    fun favoriteRecordRemovesOlderFavoriteButNotReAddedOne() {
        val remote = LocalProfileBackup(favorites = listOf(fav(-42, now - 5000), fav(8, now - 5000, "movie")))
        val merged = LocalSyncMerge.mergeLibrary(LocalProfileBackup(), remote, deletedItems = listOf(del(KIND_FAVORITE, -42, at = now - 1000)))
        assertEquals(listOf(8), merged.favorites.map { it.tmdbId })
        // Favori kaydı geçmişi etkilemez; geçmiş kaydı favoriyi etkilemez.
        val mixed = LocalSyncMerge.mergeLibrary(
            LocalProfileBackup(history = listOf(ep(-42, 1, 1, now - 5000))),
            LocalProfileBackup(favorites = listOf(fav(-42, now))),
            deletedItems = listOf(del(KIND_FAVORITE, -42, at = now - 1000), del(KIND_EPISODE, -42, 1, 9, at = now - 1000)),
        )
        assertEquals(1, mixed.history.size)
        assertEquals(1, mixed.favorites.size)
    }

    @Test
    fun planUnitesBothSidesRemapsProfilesAndCarriesRemoteFavoriteSources() {
        val profile = LocalProfileSummary(p, "Ev")
        val src = FavoriteSourceTransfer(-42, "tv", "Dramacix", "https://dramacix.example/dizi/x")
        val local = LocalProfileTransfer(
            profiles = listOf(profile),
            backups = mapOf(p to LocalSyncMerge.encodeBackup(LocalProfileBackup(favorites = listOf(fav(8, now - 9000))))),
            deletedItems = listOf(del(KIND_EPISODE, 5, 1, 2, now - 1000)),
        )
        val remote = LocalProfileTransfer(
            profiles = listOf(profile),
            backups = mapOf(p to LocalSyncMerge.encodeBackup(LocalProfileBackup(history = listOf(ep(5, 1, 2, now - 4000)), favorites = listOf(fav(8, now - 9000))))),
            deletedItems = listOf(del(KIND_FAVORITE, 8, at = now - 2000, type = "tv")),
            favoriteSources = listOf(src),
        )
        val plan = LocalSyncMerge.plan(local, remote, remoteIsAuthority = true, localActiveProfileId = p, nowMs = now)
        assertTrue(plan.libraries.getValue(p).history.isEmpty())
        assertTrue(plan.libraries.getValue(p).favorites.isEmpty())
        assertEquals(setOf(KIND_EPISODE, KIND_FAVORITE), plan.deletedItems.map { it.kind }.toSet())
        assertEquals(listOf(src), plan.favoriteSources)
        // Yeni kayıtlar içerik mezar taşı listesine girmez (eski sürüm onları bütün dizinin silinmesi sanırdı).
        assertTrue(plan.deletedHistory.isEmpty())
    }

    @Test
    fun pruneKeepsNewestPerKeyDropsExpiredAndUnknownKinds() {
        val old = now - LocalSyncMerge.DELETED_HISTORY_RETENTION_MS - 1
        val items = listOf(
            del(KIND_CLEAR, at = now - 10), del(KIND_CLEAR, at = now), del(KIND_EPISODE, 5, 1, 2, at = old),
            DeletedLibraryItem(p, "future-kind", 1, "tv", deletedAt = now),
        )
        val pruned = LocalSyncMerge.pruneDeletedItems(items, now)
        assertEquals(listOf(del(KIND_CLEAR, at = now)), pruned)
    }

    /** Yayındaki eski sürümlerin Wi-Fi yükü: bu alanlar yoktu. */
    @Serializable
    private data class OldDeleted(val userId: String = "", val profileId: String = "", val tmdbId: Int, val type: String, val deletedAt: Long)

    @Serializable
    private data class OldTransfer(
        val profiles: List<LocalProfileSummary> = emptyList(),
        val backups: Map<String, String> = emptyMap(),
        val deviceName: String = "",
        val deletedHistory: List<OldDeleted> = emptyList(),
    )

    @Serializable
    private data class OldFavorite(val tmdbId: Int, val type: String, val title: String, val posterPath: String?, val addedAt: Long)

    @Serializable
    private data class OldBackup(val favorites: List<OldFavorite> = emptyList())

    @Test
    fun oldDecoderReadsNewWifiPayloadAndNewDecoderReadsOldPayload() {
        val transfer = LocalSyncMerge.snapshot(
            listOf(LocalProfileSummary(p, "Ev")),
            mapOf(p to LocalProfileBackup(favorites = listOf(fav(-42, now)))),
            emptyList(), "Telefon",
            deletedHistory = listOf(DeletedHistoryItem(profileId = p, tmdbId = 3, type = "movie", deletedAt = now)),
            deletedItems = listOf(del(KIND_FAVORITE, -7, at = now), del(KIND_EPISODE, 5, 1, 2, at = now)),
            favoriteSources = listOf(FavoriteSourceTransfer(-42, "tv", "X", "https://x.example/a")),
        )
        val wire = LocalSyncMerge.encode(transfer)
        // Eski sürümlerin Wi-Fi çözücüsü (LocalSyncMerge, 7 Kas'tan beri): ignoreUnknownKeys = true.
        val oldJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val old = oldJson.decodeFromString(OldTransfer.serializer(), wire)
        assertEquals(listOf(OldDeleted("", p, 3, "movie", now)), old.deletedHistory) // yalnız içerik mezar taşı
        assertEquals(-42, oldJson.decodeFromString(OldBackup.serializer(), old.backups.getValue(p)).favorites.single().tmdbId)
        // Eski cihazdan gelen yük (yeni alanlar yok) yeni çözücüde boş liste olur.
        val fromOld = LocalSyncMerge.decodeTransfer(oldJson.encodeToString(OldTransfer.serializer(), old))
        assertTrue(fromOld.deletedItems.isEmpty())
        assertTrue(fromOld.favoriteSources.isEmpty())
    }

    private fun plugin(id: Long, name: String, enabled: Boolean) =
        PluginEntity(id, 1, name, name, null, scriptUrl = "https://p.example/$name", installedAt = 0, enabled = enabled)

    @Test
    fun favoriteSourceMapsToLocalPluginByNamePreferringEnabled() {
        val url = "https://dramacix.example/dizi/x"
        val id = ProviderContentId.of("cs:718", url)
        val item = FavoriteSourceTransfer(id, "tv", "Dramacix", url)
        val plugins = listOf(plugin(3, "Dramacix", false), plugin(9, "Dramacix", true), plugin(718, "DiziMom", true))
        // Telefonun 718'i TV'de başka eklenti: numaraya değil ada bakılır.
        assertEquals(MediaType.Tv to "cs:9", LocalSyncMerge.favoriteSourceTarget(item, plugins))
        assertNull(LocalSyncMerge.favoriteSourceTarget(item, listOf(plugin(718, "DiziMom", true))))
        assertNull(LocalSyncMerge.favoriteSourceTarget(item.copy(tmdbId = id + 1), plugins)) // kimlik adresle tutmuyor
        assertNull(LocalSyncMerge.favoriteSourceTarget(item.copy(contentUrl = "javascript:alert(1)"), plugins))
        assertNull(LocalSyncMerge.favoriteSourceTarget(item.copy(tmdbId = 77), plugins)) // TMDB kimliği taşınmaz
    }

    @Test
    fun outgoingSourcesOnlyForPluginFavoritesWithKnownPluginName() {
        val refs = mapOf(
            (MediaType.Tv to -42) to ("cs:718" to "https://a.example/x"),
            (MediaType.Tv to -43) to ("cs:999" to "https://a.example/y"), // eklenti artık yok
            (MediaType.Tv to -44) to ("cs:718" to "https://a.example/z"), // favori değil (çıkarılmış)
            (MediaType.Movie to -45) to ("nuvio_addon:3" to "https://a.example/m"),
        )
        val favorites = setOf(MediaType.Tv to -42, MediaType.Tv to -43, MediaType.Movie to -45)
        val out = LocalSyncMerge.outgoingFavoriteSources(refs, favorites, mapOf(718L to "Dramacix"))
        assertEquals(listOf(FavoriteSourceTransfer(-42, "tv", "Dramacix", "https://a.example/x")), out)
    }
}
