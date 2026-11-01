package com.kaan.play.data.sync.local

import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.PluginStateTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wi-Fi senkronizasyonu birleştirme kuralları. */
class LocalSyncMergeTest {

    private fun h(tmdb: Int, at: Long, pos: Long = 1_000, season: Int = 0, episode: Int = 0, type: String = "movie") =
        HistoryBackup(tmdb, type, season, episode, pos, 10_000, at, "T$tmdb", null)

    private fun f(tmdb: Int, at: Long) = FavoriteBackup(tmdb, "movie", "F$tmdb", null, at)

    private fun transfer(
        profiles: List<LocalProfileSummary>,
        libs: Map<String, LocalProfileBackup> = emptyMap(),
        sources: List<SourceTransferItem> = emptyList(),
    ) = LocalProfileTransfer(profiles = profiles, backups = libs.mapValues { LocalSyncMerge.encodeBackup(it.value) }, sources = sources)

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
}
