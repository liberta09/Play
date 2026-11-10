package com.kaan.play.data.addon

import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Room'un unique/IGNORE davranışını taklit eden bellek içi DAO. */
private class FakeAddonDao : AddonDao {
    private val rows = MutableStateFlow<List<AddonEntity>>(emptyList())
    private var nextId = 1L

    override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> =
        rows.map { list -> list.filter { it.type == type } }

    fun all(): List<AddonEntity> = rows.value

    override suspend fun enabledOfType(type: AddonType) =
        rows.value.filter { it.enabled && it.type == type }

    override suspend fun countOfType(type: AddonType) = rows.value.count { it.type == type }

    override suspend fun countBySourceUrl(url: String) = rows.value.count { it.sourceUrl == url }

    override suspend fun insert(entity: AddonEntity): Long {
        if (rows.value.any { it.sourceUrl == entity.sourceUrl }) return -1
        val id = nextId++
        rows.value += entity.copy(id = id)
        return id
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {
        rows.value = rows.value.map { if (it.type == type) it.copy(enabled = enabled) else it }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(name = name, version = version, description = description, manifestJson = manifestJson) else it }
    }

    override suspend fun byId(id: Long): AddonEntity? = rows.value.firstOrNull { it.id == id }
}

class AddonRepositoryTest {

    private val validManifest = """
        {"id":"org.example","name":"Örnek Addon","version":"2.0.0","description":"d","types":["movie","series"],
         "idPrefixes":["tt"],"resources":["catalog",{"name":"stream","types":["movie","series"]}]}
    """

    private val dao = FakeAddonDao()
    private val fetched = mutableListOf<String>()
    private var response: () -> String = { validManifest }
    private val fetcher = ManifestFetcher { url -> fetched += url; response() }
    private val repo = AddonRepository(dao, fetcher, clock = { 1_000L })

    @Test
    fun addStoresValidatedAddonAndExposesItViaFlow() = runTest {
        val added = repo.add("a.example.com/cfg")

        assertEquals(listOf("https://a.example.com/cfg/manifest.json"), fetched)
        assertEquals("Örnek Addon", added.manifest.name)
        assertEquals("https://a.example.com/cfg", added.baseUrl)
        assertEquals(AddonType.NUVIO_ADDON, added.type)
        assertTrue(added.enabled)
        assertEquals(1_000L, added.addedAt)

        val listed = repo.addons.first().single()
        assertEquals(added.id, listed.id)
        assertEquals(added.manifest, listed.manifest) // JSON'dan eksiksiz geri okunur
        assertTrue(listed.manifest.supportsStream("movie", "tt1"))
    }

    @Test
    fun duplicateIsRejectedWithoutDownloadingAgain() = runTest {
        repo.add("https://a.example.com/manifest.json")
        try {
            repo.add("a.example.com") // aynı adresin başka yazımı
            fail()
        } catch (e: AddonException) {
            assertEquals(Kind.AlreadyAdded, e.kind)
        }
        assertEquals(1, fetched.size)
        assertEquals(1, repo.addons.first().size)
    }

    @Test
    fun invalidManifestIsNotStored() = runTest {
        response = { """{"id":"x","name":"y","types":["movie"],"resources":["catalog"]}""" }
        try {
            repo.add("https://a.example.com")
            fail()
        } catch (e: AddonException) {
            assertEquals(Kind.NoStreamResource, e.kind)
        }
        assertTrue(repo.addons.first().isEmpty())
    }

    @Test
    fun fetchErrorsPropagateAndNothingIsStored() = runTest {
        val failing = AddonRepository(dao, { throw AddonException(Kind.Http, "404") })
        try {
            failing.add("https://a.example.com")
            fail()
        } catch (e: AddonException) {
            assertEquals(Kind.Http, e.kind)
            assertEquals("404", e.detail)
        }
        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun invalidUrlNeverReachesTheNetwork() = runTest {
        try {
            repo.add("ftp://nope")
            fail()
        } catch (e: AddonException) {
            assertEquals(Kind.InvalidUrl, e.kind)
        }
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun onlyNuvioAddonsAreSupportedInPhase1() = runTest {
        listOf(AddonType.NUVIO_PLUGIN_REPO, AddonType.CLOUDSTREAM_REPO, AddonType.WEB_SITE).forEach { type ->
            try {
                repo.add("https://a.example.com", type)
                fail()
            } catch (e: AddonException) {
                assertEquals(Kind.UnsupportedKind, e.kind)
            }
        }
        assertTrue(fetched.isEmpty())
    }

    @Test
    fun enableDisableAndRemove() = runTest {
        val a = repo.add("https://a.example.com")
        val b = repo.add("https://b.example.com")
        assertEquals(listOf(a.id, b.id), repo.enabledAddons().map { it.id })

        repo.setEnabled(a.id, false)
        assertEquals(listOf(b.id), repo.enabledAddons().map { it.id })
        assertFalse(repo.addons.first().first { it.id == a.id }.enabled)

        repo.remove(b.id)
        assertEquals(listOf(a.id), repo.addons.first().map { it.id })
        assertTrue(repo.enabledAddons().isEmpty())
    }

    @Test
    fun enabledAddonsExcludeOtherTypes() = runTest {
        // Faz 2/3 türleri tabloda olabilir ama akış toplamaya girmemeli.
        dao.insert(
            AddonEntity(
                type = AddonType.NUVIO_PLUGIN_REPO, sourceUrl = "https://repo.example.com/manifest.json",
                addonId = "r", name = "Repo", version = null, description = null, logoUrl = null,
                manifestJson = """{"id":"r","name":"Repo","types":["movie"],"resources":[{"name":"stream"}]}""", addedAt = 1,
            ),
        )
        repo.add("https://a.example.com")
        assertEquals(1, repo.enabledAddons().size)
        assertEquals(1, repo.addons.first().size) // depo satırı listeye girmez
        assertEquals(2, dao.all().size)
    }

    @Test
    fun catalogOnlyAddonNeverProvidesStreams() = runTest {
        response = {
            """{"id":"org.cat","name":"Harici Katalog","types":["movie","series"],"resources":["catalog","stream"],"catalogs":[{"type":"movie","id":"top"}]}"""
        }
        val added = repo.addFetched("https://cat.example/manifest.json", response(), catalogOnly = true)
        assertTrue(added.manifest.providesCatalogs)
        assertFalse(added.manifest.providesStreams)
        assertFalse(added.manifest.supportsStream("movie", "tt0111161"))
        assertFalse(added.manifest.supportsStream("series", "tt0944947:1:1"))
    }
}
