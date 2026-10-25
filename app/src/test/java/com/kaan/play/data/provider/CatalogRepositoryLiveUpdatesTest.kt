package com.kaan.play.data.provider

import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class CatalogRepositoryLiveUpdatesTest {

    @Test
    fun fastProviderRowsAreEmittedBeforeSlowProviderCompletes(): Unit = runBlocking {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val slow = request.url.encodedPath.startsWith("/slow/")
                val name = if (slow) "Slow News" else "Fast News"
                return MockResponse.Builder()
                    .code(200)
                    .body("""{"metas":[{"id":"${name.lowercase().replace(' ', '-')}","type":"channel","name":"$name"}]}""")
                    .apply { if (slow) bodyDelay(600, TimeUnit.MILLISECONDS) }
                    .build()
            }
        }
        server.start()
        try {
            val dao = StaticAddonDao(
                listOf(
                    addon(1L, "fast", server.url("/fast/manifest.json").toString()),
                    addon(2L, "slow", server.url("/slow/manifest.json").toString()),
                ),
            )
            val repository = CatalogRepository(addonDao = dao, httpClient = OkHttpClient())

            val updates = repository.loadLiveCatalogUpdates(forceRefresh = true).toList()
            val successes = updates.filterIsInstance<CatalogResult.Success>()
            val fastOnly = successes.firstOrNull { result ->
                val titles = result.rows.flatMap { it.items }.map { it.title }.toSet()
                "Fast News" in titles && "Slow News" !in titles
            }

            assertNotNull("fast provider should be visible while slow provider is still loading", fastOnly)
            val finalTitles = (updates.last() as CatalogResult.Success)
                .rows.flatMap { it.items }.map { it.title }.toSet()
            assertEquals(setOf("Fast News", "Slow News"), finalTitles)
        } finally {
            server.close()
        }
    }

    private fun addon(id: Long, name: String, manifestUrl: String) = AddonEntity(
        id = id,
        type = AddonType.NUVIO_ADDON,
        sourceUrl = manifestUrl,
        addonId = "org.example.$name",
        name = name,
        version = null,
        description = null,
        logoUrl = null,
        enabled = true,
        manifestJson = """
            {"id":"org.example.$name","name":"${name.replaceFirstChar(Char::uppercase)}","types":["tv"],
             "resources":["catalog"],"catalogs":[{"type":"tv","id":"channels","name":"Live"}]}
        """.trimIndent(),
        addedAt = id,
    )

    private class StaticAddonDao(private val rows: List<AddonEntity>) : AddonDao {
        override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = flowOf(rows.filter { it.type == type })
        override suspend fun enabledOfType(type: AddonType): List<AddonEntity> = rows.filter { it.enabled && it.type == type }
        override suspend fun countBySourceUrl(url: String): Int = rows.count { it.sourceUrl == url }
        override suspend fun countOfType(type: AddonType): Int = rows.count { it.type == type }
        override suspend fun insert(entity: AddonEntity): Long = -1L
        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit
        override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) = Unit
        override suspend fun byId(id: Long): AddonEntity? = rows.firstOrNull { it.id == id }
    }
}
