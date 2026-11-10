package com.kaan.play.data.sync.local

import com.kaan.play.data.sync.DeletedFavoriteItem
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Favori silme mezar taşları (tombstone) senkronizasyon kuralları. */
class FavoriteTombstoneSyncTest {

    @Serializable
    private data class LegacyPayloadWithoutFavTombstones(
        val profiles: List<LocalProfileSummary> = emptyList(),
        val backups: Map<String, String> = emptyMap(),
        val sources: List<SourceTransferItem> = emptyList(),
        val deviceName: String = "",
    )

    private fun fav(tmdb: Int, addedAt: Long, type: String = "movie") =
        FavoriteBackup(tmdb, type, "F$tmdb", null, addedAt)

    private fun tombstone(tmdb: Int, deletedAt: Long, profileId: String = "p1", type: String = "movie") =
        DeletedFavoriteItem(userId = "u", profileId = profileId, tmdbId = tmdb, type = type, deletedAt = deletedAt)

    private fun transfer(
        profiles: List<LocalProfileSummary> = listOf(LocalProfileSummary("p1", "Ev")),
        favorites: List<FavoriteBackup> = emptyList(),
        deletedFavorites: List<DeletedFavoriteItem> = emptyList(),
        deviceName: String = "Dev",
    ) = LocalSyncMerge.snapshot(
        profiles = profiles,
        libraries = mapOf("p1" to LocalProfileBackup(favorites = favorites)),
        sources = emptyList(),
        deviceName = deviceName,
        deletedFavorites = deletedFavorites,
    )

    @Test
    fun deletedFavoriteStaysDeletedAfterSync() {
        val now = 1_000_000_000_000L
        val local = transfer(
            favorites = listOf(fav(1, now - 10_000)),
            deletedFavorites = listOf(tombstone(2, deletedAt = now - 2_000)),
            deviceName = "TV",
        )
        val remote = transfer(
            favorites = listOf(fav(1, now - 10_000), fav(2, now - 5_000)),
            deviceName = "Telefon",
        )

        val plan = LocalSyncMerge.plan(local, remote, remoteIsAuthority = false, nowMs = now)
        assertEquals(listOf(1), plan.libraries["p1"]!!.favorites.map { it.tmdbId })
        assertEquals(listOf(2), plan.deletedFavorites.map { it.tmdbId })
    }

    @Test
    fun deletedFavoriteReaddedOnOtherDeviceWithNewerAddedAtStays() {
        val now = 1_000_000_000_000L
        val deletedAt = now - 5_000
        val readdedNewer = fav(2, addedAt = now - 1_000) // addedAt > deletedAt -> KALIR
        val readdedSameOrOlder = fav(3, addedAt = deletedAt) // addedAt <= deletedAt -> SİLİNİR

        val local = transfer(
            deletedFavorites = listOf(tombstone(2, deletedAt), tombstone(3, deletedAt)),
        )
        val remote = transfer(
            favorites = listOf(readdedNewer, readdedSameOrOlder),
        )

        val plan = LocalSyncMerge.plan(local, remote, remoteIsAuthority = false, nowMs = now)
        assertEquals(listOf(2), plan.libraries["p1"]!!.favorites.map { it.tmdbId })
    }

    @Test
    fun legacyPayloadWithoutDeletedFavoritesParsesCleanlyAndReverseCompatible() {
        val oldJson = """{"profiles":[{"id":"p1","name":"Ev"}],"backups":{},"sources":[]}"""
        val decoded = LocalSyncMerge.decodeTransfer(oldJson)
        assertTrue(decoded.deletedFavorites.isEmpty())
        assertEquals(1, decoded.profiles.size)

        val modern = transfer(deletedFavorites = listOf(tombstone(7, 500_000L)))
        val raw = LocalSyncMerge.encode(modern)
        assertTrue(raw.contains("deletedFavorites"))
        val legacyParsed = Json { ignoreUnknownKeys = true }.decodeFromString(LegacyPayloadWithoutFavTombstones.serializer(), raw)
        assertEquals("Ev", legacyParsed.profiles.single().name)
    }

    @Test
    fun wifiAndCloudMergeProduceIdenticalFavoritesAndTombstones() {
        val now = 1_000_000_000_000L
        val local = transfer(
            favorites = listOf(fav(1, now - 20_000), fav(4, now - 1_000)),
            deletedFavorites = listOf(tombstone(2, deletedAt = now - 5_000)),
            deviceName = "TV",
        )
        val remoteOriginal = transfer(
            favorites = listOf(fav(1, now - 20_000), fav(2, now - 10_000), fav(3, now - 15_000)),
            deletedFavorites = listOf(tombstone(4, deletedAt = now - 4_000)), // 4 sonradan yerelde eklendiği için kalır
            deviceName = "Telefon",
        )

        // Wi-Fi senkronizasyonu: LocalSyncMerge.plan
        val wifiPlan = LocalSyncMerge.plan(local, remoteOriginal, remoteIsAuthority = true, nowMs = now)

        // Bulut (SupabaseCloudSync play_cloud_state.payload jsonb) birebir aynı JSON aktarımı ve LocalSyncMerge.plan'ı kullanır.
        val cloudJsonPayload = LocalSyncMerge.encode(remoteOriginal)
        val cloudDecoded = LocalSyncMerge.decodeTransfer(cloudJsonPayload)
        val cloudPlan = LocalSyncMerge.plan(local, cloudDecoded, remoteIsAuthority = true, nowMs = now)

        assertEquals(wifiPlan.libraries, cloudPlan.libraries)
        assertEquals(wifiPlan.deletedFavorites, cloudPlan.deletedFavorites)
        assertEquals(listOf(4, 3, 1), wifiPlan.libraries["p1"]!!.favorites.map { it.tmdbId })
        assertFalse(wifiPlan.libraries["p1"]!!.favorites.any { it.tmdbId == 2 })
    }

    @Test
    fun prunesFavoriteTombstonesTo500PerProfileAnd90Days() {
        val now = 1_000_000_000_000L
        val dayMs = 24L * 60 * 60 * 1000L
        val stale = tombstone(999, deletedAt = now - 91 * dayMs, profileId = "p1")
        val many = (1..505).map { i -> tombstone(i + 1000, deletedAt = now - i * 100L, profileId = "p1") }
        val otherProfile = (1..10).map { i -> tombstone(i, deletedAt = now - 1_000L, profileId = "p2") }

        val pruned = LocalSyncMerge.pruneDeletedFavorites(listOf(stale) + many + otherProfile, nowMs = now)
        assertFalse(pruned.any { it.tmdbId == 999 })
        assertEquals(500, pruned.count { it.profileId == "p1" })
        assertEquals(10, pruned.count { it.profileId == "p2" })
    }
}
