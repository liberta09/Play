package com.kaan.play.data.sync.local

import com.kaan.play.data.sync.DeletedHistoryItem
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.PluginStateTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wi-Fi senkronizasyonu birleştirme kuralları. */
class LocalSyncMergeTest {

    private fun h(tmdb: Int, at: Long, pos: Long = 1_000, season: Int = 0, episode: Int = 0, type: String = "movie") =
        HistoryBackup(tmdb, type, season, episode, pos, 10_000, at, "T$tmdb", null)

    private fun f(tmdb: Int, at: Long) = FavoriteBackup(tmdb, "movie", "F$tmdb", null, at)

    private fun dh(tmdb: Int, at: Long, profileId: String = "p1", type: String = "movie") =
        DeletedHistoryItem(userId = "u", profileId = profileId, tmdbId = tmdb, type = type, deletedAt = at)

    private fun transfer(
        profiles: List<LocalProfileSummary>,
        libs: Map<String, LocalProfileBackup> = emptyMap(),
        sources: List<SourceTransferItem> = emptyList(),
        deletedHistory: List<DeletedHistoryItem> = emptyList(),
    ) = LocalProfileTransfer(
        profiles = profiles,
        backups = libs.mapValues { LocalSyncMerge.encodeBackup(it.value) },
        sources = sources,
        deletedHistory = deletedHistory,
    )

    @Test
    fun newestLastWatchedWinsPerContent() {
        val local = LocalProfileBackup(history = listOf(h(1, at = 100, pos = 5), h(2, at = 300, pos = 7), h(3, at = 50, season = 1, episode = 2, type = "tv")))
        val remote = LocalProfileBackup(history = listOf(h(1, at = 200, pos = 9), h(2, at = 250, pos = 1), h(3, at = 60, season = 1, episode = 3, type = "tv")))
        val merged = LocalSyncMerge.mergeLibrary(local, remote).history.associateBy { LocalSyncMerge.historyKey(it) }
        assertEquals(4, merged.size) // farklı bölümler ayrı kalır
        assertEquals(9L, merged["1:movie:0:0"]!!.positionMs) // uzak daha yeni
        assertEquals(7L, merged["2:movie:0:0"]!!.positionMs) // yerel daha yeni
        assertEquals(2, merged.keys.count { it.startsWith("3:tv:1:") })
    }

    @Test
    fun equalTimestampsPreferFurtherPositionSoBothSidesAgree() {
        val a = LocalProfileBackup(history = listOf(h(1, at = 100, pos = 5)))
        val b = LocalProfileBackup(history = listOf(h(1, at = 100, pos = 8)))
        assertEquals(LocalSyncMerge.mergeLibrary(a, b).history, LocalSyncMerge.mergeLibrary(b, a).history)
        assertEquals(8L, LocalSyncMerge.mergeLibrary(a, b).history.single().positionMs)
    }

    @Test
    fun favoritesAreUnion() {
        val merged = LocalSyncMerge.mergeLibrary(
            LocalProfileBackup(favorites = listOf(f(1, 10), f(2, 20))),
            LocalProfileBackup(favorites = listOf(f(2, 25), f(3, 30))),
        )
        assertEquals(setOf(1, 2, 3), merged.favorites.map { it.tmdbId }.toSet())
    }

    @Test
    fun pinIsNeverTransferredAndLocalPinIsKept() {
        val mine = LocalProfileSummary("p1", "Ben", pinHash = "pbkdf2-sha256\$x", syncCode = "ABC")
        val theirsSame = LocalProfileSummary("p1", "Ben (TV)", avatar = "ocean", pinHash = "other")
        val theirsNew = LocalProfileSummary("p2", "Çocuk", isChild = true, pinHash = "secret")
        assertNull(LocalSyncMerge.forTransfer(mine).pinHash)
        assertNull(LocalSyncMerge.forTransfer(mine).syncCode)

        val asClient = LocalSyncMerge.mergeProfiles(listOf(mine), listOf(theirsSame, theirsNew), remoteIsAuthority = true)
        assertEquals(listOf("p1", "p2"), asClient.map { it.id })
        assertEquals("Ben (TV)", asClient[0].name) // kodu gösteren cihaz esas
        assertEquals("pbkdf2-sha256\$x", asClient[0].pinHash) // yerel PIN korunur
        assertNull("yeni profil PIN'siz gelir", asClient[1].pinHash)

        val asServer = LocalSyncMerge.mergeProfiles(listOf(mine), listOf(theirsSame, theirsNew), remoteIsAuthority = false)
        assertEquals("Ben", asServer[0].name)
        assertNull(asServer[1].pinHash)
    }

    @Test
    fun missingSourcesAreAskedNotAdded_sharedStatesFollowAuthority() {
        val shared = SourceTransferItem("NUVIO_PLUGIN_REPO", "https://Repo.example/manifest.json", plugins = listOf(PluginStateTransfer("a", true)))
        val sharedRemote = SourceTransferItem(
            "NUVIO_PLUGIN_REPO", "https://repo.example/manifest.json/",
            plugins = listOf(PluginStateTransfer("a", false, mapOf("k" to JsonPrimitive("v")))),
        )
        val onlyRemote = SourceTransferItem("CLOUDSTREAM_REPO", "https://cs.example/repo.json", name = "CS")
        val onlyLocal = SourceTransferItem("WEB_SITE", "https://site.example")
        val p = listOf(LocalProfileSummary("p1", "Ben"))
        val local = transfer(p, sources = listOf(shared, onlyLocal))
        val remote = transfer(p, sources = listOf(sharedRemote, onlyRemote))

        val client = LocalSyncMerge.plan(local, remote, remoteIsAuthority = true)
        assertEquals(listOf(onlyRemote), client.missingSources)
        assertEquals(listOf(sharedRemote), client.sourceStates)

        val server = LocalSyncMerge.plan(local, remote, remoteIsAuthority = false)
        assertEquals(listOf(onlyRemote), server.missingSources)
        assertTrue(server.sourceStates.isEmpty())
    }

    @Test
    fun urlKeyIgnoresTrailingSlashAndHostCase() {
        assertEquals(LocalSyncMerge.urlKey("https://A.example/x/manifest.json/"), LocalSyncMerge.urlKey(" https://a.example/x/manifest.json"))
        // Yol büyük/küçük harfe duyarlı kalır.
        assertTrue(LocalSyncMerge.urlKey("https://a.example/X") != LocalSyncMerge.urlKey("https://a.example/x"))
    }

    @Test
    fun bothSidesEndWithSameLibraries() {
        val pa = LocalProfileSummary("pa", "A")
        val pb = LocalProfileSummary("pb", "B")
        val a = transfer(listOf(pa), mapOf("pa" to LocalProfileBackup(history = listOf(h(1, 100)), favorites = listOf(f(1, 1)))))
        val b = transfer(
            listOf(pa, pb),
            mapOf(
                "pa" to LocalProfileBackup(history = listOf(h(1, 200), h(2, 50))),
                "pb" to LocalProfileBackup(favorites = listOf(f(9, 9))),
            ),
        )
        val onServer = LocalSyncMerge.plan(a, b, remoteIsAuthority = false)
        val onClient = LocalSyncMerge.plan(b, a, remoteIsAuthority = true)
        assertEquals(onServer.profiles.map { it.id }.toSet(), onClient.profiles.map { it.id }.toSet())
        assertEquals(onServer.libraries, onClient.libraries.filterKeys { it in onServer.libraries })
        assertEquals(onServer.libraries.keys, onClient.libraries.keys)
        assertEquals(2, onServer.historyCount) // pa: film 1 (birleşik) + film 2
    }

    @Test
    fun twoDevicesTwoDefaultsMergeIntoOneWithCombinedHistory() {
        val dev1Default = LocalProfileSummary("uuid-1", "Varsayılan")
        val dev2Default = LocalProfileSummary("uuid-2", "Varsayılan")

        val lib1 = LocalProfileBackup(
            history = listOf(h(101, at = 1000, pos = 5000)),
            favorites = listOf(f(201, at = 100)),
        )
        val lib2 = LocalProfileBackup(
            history = listOf(h(102, at = 2000, pos = 3000)),
            favorites = listOf(f(202, at = 200)),
        )

        val dev1Transfer = transfer(listOf(dev1Default), mapOf("uuid-1" to lib1))
        val dev2Transfer = transfer(listOf(dev2Default), mapOf("uuid-2" to lib2))

        val onServer = LocalSyncMerge.plan(dev1Transfer, dev2Transfer, remoteIsAuthority = false)
        val onClient = LocalSyncMerge.plan(dev2Transfer, dev1Transfer, remoteIsAuthority = true)

        assertEquals(1, onServer.profiles.size)
        assertEquals(1, onClient.profiles.size)
        assertEquals("Varsayılan", onServer.profiles.single().name)
        assertEquals("Varsayılan", onClient.profiles.single().name)

        val keptId = "uuid-1"
        assertEquals(keptId, onServer.profiles.single().id)
        assertEquals(keptId, onClient.profiles.single().id)
        assertEquals(mapOf("uuid-2" to "uuid-1"), onServer.remappedProfileIds)
        assertEquals(mapOf("uuid-2" to "uuid-1"), onClient.remappedProfileIds)

        val serverLib = onServer.libraries[keptId]!!
        val clientLib = onClient.libraries[keptId]!!

        val expectedHistoryTmdbs = setOf(101, 102)
        assertEquals(expectedHistoryTmdbs, serverLib.history.map { it.tmdbId }.toSet())
        assertEquals(expectedHistoryTmdbs, clientLib.history.map { it.tmdbId }.toSet())

        val expectedFavTmdbs = setOf(201, 202)
        assertEquals(expectedFavTmdbs, serverLib.favorites.map { it.tmdbId }.toSet())
        assertEquals(expectedFavTmdbs, clientLib.favorites.map { it.tmdbId }.toSet())
    }

    @Test
    fun fixedDefaultIdPreferredOverRandomUuidWhenMergingDefaults() {
        val oldDev = LocalProfileSummary("random-uuid-999", "Varsayılan")
        val newDev = LocalProfileSummary("default", "Varsayılan")

        val oldLib = LocalProfileBackup(history = listOf(h(1, at = 500)))
        val newLib = LocalProfileBackup(history = listOf(h(2, at = 600)))

        val plan = LocalSyncMerge.plan(
            local = transfer(listOf(oldDev), mapOf("random-uuid-999" to oldLib)),
            remote = transfer(listOf(newDev), mapOf("default" to newLib)),
            remoteIsAuthority = true,
        )

        assertEquals(1, plan.profiles.size)
        assertEquals("default", plan.profiles.single().id)
        assertEquals(mapOf("random-uuid-999" to "default"), plan.remappedProfileIds)
        assertEquals(setOf(1, 2), plan.libraries["default"]!!.history.map { it.tmdbId }.toSet())
    }

    @Test
    fun pinnedDefaultProfileNeverMergesAutomatically() {
        val mineWithPin = LocalProfileSummary("p1", "Varsayılan", pinHash = "pbkdf2-sha256\$hash")
        val theirsNoPin = LocalProfileSummary("p2", "Varsayılan", pinHash = null)

        val plan = LocalSyncMerge.plan(
            local = transfer(listOf(mineWithPin), mapOf("p1" to LocalProfileBackup(favorites = listOf(f(1, 10))))),
            remote = transfer(listOf(theirsNoPin), mapOf("p2" to LocalProfileBackup(favorites = listOf(f(2, 20))))),
            remoteIsAuthority = false,
        )

        assertEquals(2, plan.profiles.size)
        assertEquals(setOf("p1", "p2"), plan.profiles.map { it.id }.toSet())
        assertTrue(plan.remappedProfileIds.isEmpty())

        assertEquals(setOf(1), plan.libraries["p1"]?.favorites?.map { it.tmdbId }?.toSet())
        assertEquals(setOf(2), plan.libraries["p2"]?.favorites?.map { it.tmdbId }?.toSet())
    }

    @Test
    fun differentlyNamedProfilesRemainUntouched() {
        val mineDef = LocalProfileSummary("def-1", "Varsayılan")
        val mineCustom = LocalProfileSummary("p-mine", "Ahmet")
        val theirsDef = LocalProfileSummary("def-2", "Varsayılan")
        val theirsChild = LocalProfileSummary("p-child", "Çocuk", isChild = true)

        val plan = LocalSyncMerge.plan(
            local = transfer(listOf(mineDef, mineCustom)),
            remote = transfer(listOf(theirsDef, theirsChild)),
            remoteIsAuthority = true,
        )

        val profileNames = plan.profiles.map { it.name }.toSet()
        assertEquals(setOf("Varsayılan", "Ahmet", "Çocuk"), profileNames)
        assertEquals(3, plan.profiles.size)

        assertTrue(plan.profiles.any { it.id == "p-mine" && it.name == "Ahmet" })
        assertTrue(plan.profiles.any { it.id == "p-child" && it.name == "Çocuk" })
    }

    @Test
    fun activeProfileSwitchesToKeptWhenCurrentIsDiscarded() {
        val dev1 = LocalProfileSummary("uuid-1", "Varsayılan")
        val dev2 = LocalProfileSummary("uuid-2", "Varsayılan")

        val plan = LocalSyncMerge.plan(
            local = transfer(listOf(dev2)),
            remote = transfer(listOf(dev1)),
            remoteIsAuthority = false,
            localActiveProfileId = "uuid-2",
        )

        assertEquals("uuid-1", plan.activeProfileId)
        assertEquals("uuid-1", plan.remappedProfileIds["uuid-2"])

        val other = LocalProfileSummary("uuid-other", "Film Odası")
        val plan2 = LocalSyncMerge.plan(
            local = transfer(listOf(dev2, other)),
            remote = transfer(listOf(dev1)),
            remoteIsAuthority = false,
            localActiveProfileId = "uuid-other",
        )
        assertEquals("uuid-other", plan2.activeProfileId)
    }

    @Test
    fun threeDuplicatedDefaultProfilesOnSameDeviceConsolidateToOne() {
        val p1 = LocalProfileSummary("d1", "Varsayılan")
        val p2 = LocalProfileSummary("d2", "Varsayılan")
        val p3 = LocalProfileSummary("d3", "Varsayılan")

        val libs = mapOf(
            "d1" to LocalProfileBackup(history = listOf(h(10, at = 100)), favorites = listOf(f(100, 1))),
            "d2" to LocalProfileBackup(history = listOf(h(20, at = 200)), favorites = listOf(f(200, 2))),
            "d3" to LocalProfileBackup(history = listOf(h(30, at = 300)), favorites = listOf(f(300, 3))),
        )

        val result = LocalSyncMerge.consolidateProfiles(
            profiles = listOf(p1, p2, p3),
            libraries = libs,
            activeProfileId = "d3",
        )

        assertEquals(1, result.profiles.size)
        val kept = result.profiles.single()
        assertEquals("d1", kept.id)
        assertEquals("Varsayılan", kept.name)

        assertEquals(mapOf("d2" to "d1", "d3" to "d1"), result.remappedIds)
        assertEquals("d1", result.activeProfileId)

        val mergedLib = result.libraries["d1"]!!
        assertEquals(setOf(10, 20, 30), mergedLib.history.map { it.tmdbId }.toSet())
        assertEquals(setOf(100, 200, 300), mergedLib.favorites.map { it.tmdbId }.toSet())
    }

    @Serializable
    private data class LegacyTransferPayload(
        val profiles: List<LocalProfileSummary> = emptyList(),
        val backups: Map<String, String> = emptyMap(),
        val sources: List<SourceTransferItem> = emptyList(),
        val deviceName: String = "",
    )

    @Test
    fun tombstoneDeletesOldRecordOnMerge() {
        // 1) A siler, B'de eski kayıt var -> birleşimde ikisinde de OLMAZ (mezar taşı kazanır)
        val now = System.currentTimeMillis()
        val p = listOf(LocalProfileSummary("p1", "Ben"))
        val devA = transfer(
            profiles = p,
            deletedHistory = listOf(dh(10, at = now - 5_000)),
        )
        val devB = transfer(
            profiles = p,
            libs = mapOf("p1" to LocalProfileBackup(history = listOf(h(10, at = now - 10_000)))),
        )

        val planA = LocalSyncMerge.plan(devA, devB, remoteIsAuthority = false, nowMs = now)
        val planB = LocalSyncMerge.plan(devB, devA, remoteIsAuthority = true, nowMs = now)

        assertTrue(planA.libraries["p1"]!!.history.none { it.tmdbId == 10 })
        assertTrue(planB.libraries["p1"]!!.history.none { it.tmdbId == 10 })
        assertEquals(1, planA.deletedHistory.size)
        assertEquals(10, planA.deletedHistory.single().tmdbId)
        assertEquals(1, planB.deletedHistory.size)
        assertEquals(10, planB.deletedHistory.single().tmdbId)
    }

    @Test
    fun reWatchedAfterDeletionReturns() {
        // 2) B silindikten SONRA tekrar izledi (lastWatchedAt > deletedAt) -> kayıt geri GELİR
        val now = System.currentTimeMillis()
        val p = listOf(LocalProfileSummary("p1", "Ben"))
        val devA = transfer(
            profiles = p,
            deletedHistory = listOf(dh(10, at = now - 5_000)),
        )
        val devB = transfer(
            profiles = p,
            libs = mapOf("p1" to LocalProfileBackup(history = listOf(h(10, at = now - 1_000)))),
        )

        val plan = LocalSyncMerge.plan(devA, devB, remoteIsAuthority = false, nowMs = now)
        val historyItem = plan.libraries["p1"]!!.history.singleOrNull { it.tmdbId == 10 }
        assertTrue("Silindikten sonra izlenen kayıt geri gelmeli", historyItem != null)
        assertEquals(now - 1_000, historyItem!!.lastWatchedAt)
    }

    @Test
    fun seriesTombstoneDeletesAllEpisodesAndNewEpisodeSurvives() {
        // 3) Dizi silindi -> mezar taşı o dizinin TÜM bölümlerini siler; sonradan yeni bölüm izlenirse sadece o bölüm gelir
        val now = System.currentTimeMillis()
        val p = listOf(LocalProfileSummary("p1", "Ben"))
        val devA = transfer(
            profiles = p,
            deletedHistory = listOf(dh(50, at = now - 5_000, type = "tv")),
        )
        val devB = transfer(
            profiles = p,
            libs = mapOf(
                "p1" to LocalProfileBackup(
                    history = listOf(
                        h(50, at = now - 20_000, season = 1, episode = 1, type = "tv"),
                        h(50, at = now - 10_000, season = 1, episode = 2, type = "tv"),
                        h(50, at = now - 1_000, season = 2, episode = 1, type = "tv"),
                    ),
                ),
            ),
        )

        val plan = LocalSyncMerge.plan(devA, devB, remoteIsAuthority = false, nowMs = now)
        val episodes = plan.libraries["p1"]!!.history.filter { it.tmdbId == 50 }
        assertEquals(1, episodes.size)
        assertEquals(2, episodes.single().season)
        assertEquals(1, episodes.single().episode)
        assertEquals(now - 1_000, episodes.single().lastWatchedAt)
    }

    @Test
    fun tombstoneIsolatedToProfile() {
        // 4) Farklı profil yalıtımı: profil 1'deki mezar taşı profil 2'deki aynı içeriği SİLMEZ
        val now = System.currentTimeMillis()
        val p1 = LocalProfileSummary("p1", "Ben")
        val p2 = LocalProfileSummary("p2", "Misafir")
        val local = transfer(
            profiles = listOf(p1, p2),
            libs = mapOf(
                "p1" to LocalProfileBackup(history = listOf(h(100, at = now - 10_000))),
                "p2" to LocalProfileBackup(history = listOf(h(100, at = now - 10_000))),
            ),
            deletedHistory = listOf(dh(100, at = now - 5_000, profileId = "p1")),
        )
        val remote = transfer(
            profiles = listOf(p1, p2),
        )

        val plan = LocalSyncMerge.plan(local, remote, remoteIsAuthority = false, nowMs = now)
        assertTrue("p1 mezar taşı yüzünden içeriği kaybetmeli", plan.libraries["p1"]!!.history.none { it.tmdbId == 100 })
        assertTrue("p2 mezar taşından etkilenmemeli", plan.libraries["p2"]!!.history.any { it.tmdbId == 100 })
    }

    @Test
    fun oldPayloadWithoutDeletedHistoryParses() {
        // 5) Eski yük (deletedHistory alanı hiç olmayan JSON) hatasız okunur ve deletedHistory = [] kabul edilir
        val oldJson = """{"profiles":[],"backups":{},"sources":[]}"""
        val decoded = LocalSyncMerge.decodeTransfer(oldJson)
        assertTrue(decoded.deletedHistory.isEmpty())
        assertTrue(decoded.profiles.isEmpty())
        assertTrue(decoded.backups.isEmpty())
    }

    @Test
    fun newPayloadReadByOldSchemaWithIgnoreUnknownKeys() {
        // 6) Yeni yük (deletedHistory alanı dolu), ignoreUnknownKeys = true olan eski şema tarafından çökmeden okunur ve diğer alanlar korunur
        val modern = LocalProfileTransfer(
            profiles = listOf(LocalProfileSummary("p1", "Ad")),
            backups = mapOf("p1" to "backup_str"),
            sources = listOf(SourceTransferItem("KIND", "https://example.com")),
            deviceName = "ModernCihaz",
            deletedHistory = listOf(dh(1, 100)),
        )
        val jsonString = LocalSyncMerge.encode(modern)
        assertTrue(jsonString.contains("deletedHistory"))

        val legacyJsonParser = Json { ignoreUnknownKeys = true }
        val legacyDecoded = legacyJsonParser.decodeFromString(LegacyTransferPayload.serializer(), jsonString)
        assertEquals(1, legacyDecoded.profiles.size)
        assertEquals("p1", legacyDecoded.profiles.single().id)
        assertEquals("backup_str", legacyDecoded.backups["p1"])
        assertEquals("https://example.com", legacyDecoded.sources.single().sourceUrl)
        assertEquals("ModernCihaz", legacyDecoded.deviceName)
    }

    @Test
    fun pruningMax500And90Days() {
        // 7) Budama kuralı: 90 günden eski veya profil başına 500'ü aşan mezar taşları temizlenir, en yeniler kalır
        val now = 1_000_000_000_000L
        val dayMs = 24L * 60 * 60 * 1000L
        val oldTombstone = dh(999, at = now - (91 * dayMs), profileId = "p1")
        val validTombstone = dh(1, at = now - (10 * dayMs), profileId = "p1")
        val duplicateOld = dh(2, at = now - 5000, profileId = "p1")
        val duplicateNew = dh(2, at = now - 1000, profileId = "p1")

        // 505 adet farklı içerik oluştur
        val manyItems = (1..505).map { i ->
            dh(i + 1000, at = now - i * 100L, profileId = "p1")
        }

        val allItems = listOf(oldTombstone, validTombstone, duplicateOld, duplicateNew) + manyItems
        val pruned = LocalSyncMerge.pruneDeletedHistory(allItems, nowMs = now)

        // 90 günden eski olan budanmalı
        assertFalse(pruned.any { it.tmdbId == 999 })
        // duplicate olanlardan daha yeni olan kalmalı
        val dup2 = pruned.filter { it.tmdbId == 2 }
        assertEquals(1, dup2.size)
        assertEquals(now - 1000, dup2.single().deletedAt)

        // Toplam p1 sayısı en fazla 500 olmalı
        val p1Count = pruned.count { it.profileId == "p1" }
        assertEquals(500, p1Count)

        // Çoklu profil desteği: p2 de 10 öğeye sahipse p1 500, p2 10 toplam 510 kalmalı
        val p2Items = (1..10).map { i -> dh(i, at = now - 1000, profileId = "p2") }
        val multiPruned = LocalSyncMerge.pruneDeletedHistory(allItems + p2Items, nowMs = now)
        assertEquals(500, multiPruned.count { it.profileId == "p1" })
        assertEquals(10, multiPruned.count { it.profileId == "p2" })
    }
}
