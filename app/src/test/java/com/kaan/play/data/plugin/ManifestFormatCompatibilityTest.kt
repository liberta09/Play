package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.AddonManifestParser
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.data.cloudstream.CsDexStore
import com.kaan.play.data.cloudstream.CsRepoManifestParser
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.PluginDexLoader
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Nuvio ve CloudStream eklenti/depo biçimlerinin uyum testleri (Madde 7).
 * Gerçek biçimlerden küçültülmüş sahte JSON'larla her biçim için test edilir.
 */
class ManifestFormatCompatibilityTest {

    // -------------------------------------------------------------------------
    // Küçültülmüş Sahte JSON'lar
    // -------------------------------------------------------------------------

    // 1. Saf Video Addon (Torrentio / SineWix tarzı)
    private val pureVideoAddonJson = """
        {
            "id": "com.stremio.torrentio.addon",
            "name": "Torrentio",
            "version": "1.0.14",
            "description": "Torrent video streams provider",
            "types": ["movie", "series"],
            "resources": ["stream"],
            "idPrefixes": ["tt", "kitsu"]
        }
    """.trimIndent()

    // 2. Katalog Addon'u (Jasoelys / İzlelan katalog tarzı)
    private val catalogAddonJson = """
        {
            "id": "org.jasoelys.catalog.v1000",
            "name": "Jasoelys Katalog",
            "version": "1.0.0",
            "description": "Film ve Dizi Katalogları",
            "types": ["movie", "series"],
            "resources": ["catalog", "meta"],
            "catalogs": [
                {"type": "movie", "id": "jasoelys_movies", "name": "Popüler Filmler"},
                {"type": "series", "id": "jasoelys_series", "name": "Popüler Diziler"}
            ]
        }
    """.trimIndent()

    // 3. Altyazı Addon'u (ALTYAZI tarzı)
    private val subtitleAddonJson = """
        {
            "id": "com.ege.altyazitr",
            "name": "ALTYAZI",
            "version": "1.0.0",
            "description": "Türkçe Altyazı Sağlayıcı",
            "types": ["movie", "series"],
            "resources": ["subtitles"],
            "idPrefixes": ["tt"]
        }
    """.trimIndent()

    // 4. Saf Nuvio Plugin Deposu (wekmed / GSREPO tarzı)
    private val purePluginRepoJson = """
        {
            "name": "wekmed",
            "version": "1.0.0",
            "description": "Nuvio Scrapers Repository",
            "scrapers": [
                {
                    "id": "filmmodu",
                    "name": "FilmModu",
                    "version": "1.0.0",
                    "filename": "providers/filmmodu.js",
                    "supportedTypes": ["movie"]
                }
            ]
        }
    """.trimIndent()

    // 5. Scrapers + Resources Karışık Plugin Deposu (Jasoelys nuvio / HANS.MEGA / BronzeCloud tarzı)
    // Önemli: id alanı yok, resources ve types var, ama scrapers dolu nesne dizisi -> PLUGIN DEPOSUDUR.
    private val mixedPluginRepoJson = """
        {
            "name": "jasoelys",
            "version": "1.0.0",
            "author": "jasoelysss",
            "description": "Nuvio Sağlayıcı Deposu",
            "resources": ["stream", "subtitles"],
            "types": ["movie", "series", "tv"],
            "scrapers": [
                {
                    "id": "animecix",
                    "name": "AnimeCix",
                    "version": "1.0.0",
                    "filename": "providers/animecix.js",
                    "supportedTypes": "series tv"
                },
                {
                    "id": "fullhdfilmizlesene",
                    "name": "FullHDFilmizlesene",
                    "version": "1.0.0",
                    "filename": "providers/fullhdfilmizlesene.js",
                    "supportedTypes": ["movie"]
                }
            ]
        }
    """.trimIndent()

    // 6. CloudStream Deposu (NEO / BronzeCloud CS tarzı)
    private val cloudStreamRepoJson = """
        {
            "name": "NEO",
            "description": "CloudStream Turkish Repository",
            "manifestVersion": 1,
            "pluginLists": [
                "https://raw.githubusercontent.com/example/cs/builds/plugins.json"
            ]
        }
    """.trimIndent()

    // -------------------------------------------------------------------------
    // Test Altyapısı
    // -------------------------------------------------------------------------

    private class TestDb {
        val addonRows = MutableStateFlow<List<AddonEntity>>(emptyList())
        val pluginRows = MutableStateFlow<List<PluginEntity>>(emptyList())
        private var addonSeq = 1L
        private var pluginSeq = 1L

        val addons = object : AddonDao {
            override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> =
                addonRows.map { l -> l.filter { it.type == type } }
            override suspend fun enabledOfType(type: AddonType) = addonRows.value.filter { it.enabled && it.type == type }
            override suspend fun countBySourceUrl(url: String) = addonRows.value.count { it.sourceUrl == url }
            override suspend fun countOfType(type: AddonType) = addonRows.value.count { it.type == type }
            override suspend fun insert(entity: AddonEntity): Long {
                if (addonRows.value.any { it.sourceUrl == entity.sourceUrl }) return -1
                val id = addonSeq++
                addonRows.value += entity.copy(id = id)
                return id
            }
            override suspend fun setEnabled(id: Long, enabled: Boolean) {
                addonRows.value = addonRows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {
                addonRows.value = addonRows.value.map { if (it.type == type) it.copy(enabled = enabled) else it }
            }
            override suspend fun delete(id: Long) {
                addonRows.value = addonRows.value.filterNot { it.id == id }
                pluginRows.value = pluginRows.value.filterNot { it.repoId == id }
            }
            override suspend fun updateInfo(id: Long, name: String, version: String?, description: String?, manifestJson: String) {
                addonRows.value = addonRows.value.map {
                    if (it.id == id) it.copy(name = name, version = version, description = description, manifestJson = manifestJson) else it
                }
            }
            override suspend fun byId(id: Long) = addonRows.value.firstOrNull { it.id == id }
        }

        val plugins = object : PluginDao {
            override fun observeAll(): Flow<List<PluginEntity>> = pluginRows
            override suspend fun ofRepo(repoId: Long) = pluginRows.value.filter { it.repoId == repoId }
            override suspend fun byId(id: Long) = pluginRows.value.firstOrNull { it.id == id }
            override suspend fun enabled() = pluginRows.value.filter { it.enabled }
            override fun observeOfType(type: AddonType): Flow<List<PluginEntity>> =
                combine(pluginRows, addonRows) { plugins, addons ->
                    val repoIds = addons.filter { it.type == type }.map { it.id }.toSet()
                    plugins.filter { it.repoId in repoIds }
                }
            override suspend fun enabledOfType(type: AddonType): List<PluginEntity> {
                val repoIds = addonRows.value.filter { it.type == type }.map { it.id }.toSet()
                return pluginRows.value.filter { it.enabled && it.repoId in repoIds }
            }
            override suspend fun insert(entity: PluginEntity): Long {
                val id = pluginSeq++
                pluginRows.value += entity.copy(id = id)
                return id
            }
            override suspend fun setEnabled(id: Long, enabled: Boolean) {
                pluginRows.value = pluginRows.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
            }
            override suspend fun setEnabledForRepo(repoId: Long, enabled: Boolean) {
                pluginRows.value = pluginRows.value.map { if (it.repoId == repoId) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabledOfType(type: AddonType, enabled: Boolean) {
                val repoIds = addonRows.value.filter { it.type == type }.map { it.id }.toSet()
                pluginRows.value = pluginRows.value.map { if (it.repoId in repoIds) it.copy(enabled = enabled) else it }
            }
            override suspend fun setAllEnabled(enabled: Boolean) {
                pluginRows.value = pluginRows.value.map { it.copy(enabled = enabled) }
            }
            override suspend fun delete(id: Long) {
                pluginRows.value = pluginRows.value.filterNot { it.id == id }
            }
            override suspend fun updateInfo(
                id: Long, name: String, version: String?, description: String?, filename: String,
                scriptUrl: String, supportedTypes: String, contentLanguage: String, hasSettings: Boolean,
                sha256: String, sizeBytes: Long,
            ) {
                pluginRows.value = pluginRows.value.map {
                    if (it.id == id) it.copy(
                        name = name, version = version, description = description, filename = filename,
                        scriptUrl = scriptUrl, supportedTypes = supportedTypes, contentLanguage = contentLanguage,
                        hasSettings = hasSettings, sha256 = sha256, sizeBytes = sizeBytes,
                    ) else it
                }
            }
        }
    }

    private class TestEnv {
        val db = TestDb()
        val networkData = mutableMapOf<String, String>()
        val fetcher = ManifestFetcher { url ->
            networkData[url] ?: throw AddonException(Kind.Http, "404")
        }
        val codeFetcher = PluginCodeFetcher { url, _ ->
            networkData[url] ?: "// stub code\nasync function getStreams(){ return []; }\nmodule.exports = { getStreams };"
        }
        val codeStore = object : PluginCodeStore {
            private val files = mutableMapOf<Long, String>()
            override fun write(pluginId: Long, code: String) { files[pluginId] = code }
            override fun read(pluginId: Long): String? = files[pluginId]
            override fun delete(pluginId: Long) { files.remove(pluginId) }
        }
        val csDexDir = File.createTempFile("csdex_test", "").also { it.delete(); it.mkdirs() }
        val csLoader = object : PluginDexLoader {
            override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult = CsApiResult.NoApiRegistered(pluginId)
        }

        val addonRepo = AddonRepository(db.addons, fetcher, clock = { 1000L })
        val pluginRepo = PluginRepository(db.addons, db.plugins, fetcher, codeFetcher, codeStore)
        val csRepo = CsRepository(db.addons, db.plugins, fetcher, OkHttpClient(), CsDexStore(csDexDir), csLoader)
        val installer = SourceInstaller(addonRepo, pluginRepo, csRepo, fetcher)
    }

    // -------------------------------------------------------------------------
    // 1. Saf Video Addon Testi
    // -------------------------------------------------------------------------

    @Test
    fun testPureVideoAddon() = runTest {
        val env = TestEnv()
        val url = "https://torrentio.strem.fun/manifest.json"
        env.networkData[url] = pureVideoAddonJson

        // Doğru sınıflandırma
        assertEquals(ManifestKind.Addon, ManifestKind.classify(pureVideoAddonJson))

        // Ayrıştırma
        val parsed = AddonManifestParser.parse(pureVideoAddonJson)
        assertEquals("com.stremio.torrentio.addon", parsed.id)
        assertTrue(parsed.providesStreams)

        // Doğru kutudan ekleme
        val result = env.installer.add(url, SourceType.NUVIO_REPO)
        assertTrue(result is AddResult.Addon)
        assertEquals("Torrentio", (result as AddResult.Addon).name)

        // Yanlış kutudan ekleme -> NUVIO_REPO önerisi
        val envWrong = TestEnv()
        envWrong.networkData[url] = pureVideoAddonJson
        try {
            envWrong.installer.add(url, SourceType.NUVIO_PLUGIN)
            fail("KindMismatch bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals(SourceType.NUVIO_REPO, e.detectedType)
        }
    }

    // -------------------------------------------------------------------------
    // 2. Katalog Addon Testi
    // -------------------------------------------------------------------------

    @Test
    fun testCatalogAddon() = runTest {
        val env = TestEnv()
        val url = "https://katalog.jasoelys.workers.dev/manifest.json"
        env.networkData[url] = catalogAddonJson

        assertEquals(ManifestKind.Addon, ManifestKind.classify(catalogAddonJson))

        val parsed = AddonManifestParser.parse(catalogAddonJson)
        assertEquals("org.jasoelys.catalog.v1000", parsed.id)
        assertTrue(parsed.providesCatalogs)
        assertEquals(2, parsed.catalogs.size)

        val result = env.installer.add(url, SourceType.NUVIO_REPO)
        assertTrue(result is AddResult.Addon)

        // Yanlış kutu (CloudStream) -> NUVIO_REPO önerisi
        val envWrong = TestEnv()
        envWrong.networkData[url] = catalogAddonJson
        try {
            envWrong.installer.add(url, SourceType.CLOUDSTREAM)
            fail("KindMismatch bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals(SourceType.NUVIO_REPO, e.detectedType)
        }
    }

    // -------------------------------------------------------------------------
    // 3. Altyazı Addon Testi
    // -------------------------------------------------------------------------

    @Test
    fun testSubtitleAddon() = runTest {
        val env = TestEnv()
        val url = "https://altyazi-stremio.online/manifest.json"
        env.networkData[url] = subtitleAddonJson

        assertEquals(ManifestKind.Addon, ManifestKind.classify(subtitleAddonJson))

        val parsed = AddonManifestParser.parse(subtitleAddonJson)
        assertEquals("com.ege.altyazitr", parsed.id)
        assertTrue(parsed.providesSubtitles)

        val result = env.installer.add(url, SourceType.NUVIO_REPO)
        assertTrue(result is AddResult.Addon)
    }

    // -------------------------------------------------------------------------
    // 4. Saf Plugin Deposu Testi
    // -------------------------------------------------------------------------

    @Test
    fun testPurePluginRepo() = runTest {
        val env = TestEnv()
        val url = "https://raw.githubusercontent.com/Wekmed/nuvio/main/manifest.json"
        env.networkData[url] = purePluginRepoJson

        assertEquals(ManifestKind.PluginRepo, ManifestKind.classify(purePluginRepoJson))

        val parsed = PluginRepoManifestParser.parse(purePluginRepoJson, url)
        assertEquals("wekmed", parsed.name)
        assertEquals(1, parsed.scrapers.size)

        val result = env.installer.add(url, SourceType.NUVIO_PLUGIN)
        assertTrue(result is AddResult.PluginRepo)
        assertEquals(1, (result as AddResult.PluginRepo).installed)

        // Yanlış kutu (NUVIO_REPO) -> NUVIO_PLUGIN önerisi
        val envWrong = TestEnv()
        envWrong.networkData[url] = purePluginRepoJson
        try {
            envWrong.installer.add(url, SourceType.NUVIO_REPO)
            fail("KindMismatch bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals(SourceType.NUVIO_PLUGIN, e.detectedType)
        }
    }

    // -------------------------------------------------------------------------
    // 5. Scrapers + Resources Karışık Plugin Deposu (BİLİNEN HATA)
    // -------------------------------------------------------------------------

    @Test
    fun testMixedPluginRepoWithResourcesAndTypes() = runTest {
        val env = TestEnv()
        val url = "https://nuvio.jasoelys.workers.dev/manifest.json"
        env.networkData[url] = mixedPluginRepoJson

        // Nuvio kuralı: scrapers nesne dizisi varsa resources/types'a bakılmaksızın PLUGIN DEPOSUDUR.
        assertEquals(ManifestKind.PluginRepo, ManifestKind.classify(mixedPluginRepoJson))

        // Plugin ayrıştırıcısı üst düzey resources/types alanlarını yok sayarak başarıyla ayrıştırır.
        val parsed = PluginRepoManifestParser.parse(mixedPluginRepoJson, url)
        assertEquals("jasoelys", parsed.name)
        assertEquals(2, parsed.scrapers.size)
        assertEquals("animecix", parsed.scrapers[0].id)
        assertEquals("fullhdfilmizlesene", parsed.scrapers[1].id)
        // Dize olarak verilen "series tv" başarıyla ayrıştırılmıştır
        assertTrue(parsed.scrapers[0].supportedTypes.contains("series"))
        assertTrue(parsed.scrapers[0].supportedTypes.contains("tv"))

        // Doğru kutudan sorunsuz eklenir
        val result = env.installer.add(url, SourceType.NUVIO_PLUGIN)
        assertTrue(result is AddResult.PluginRepo)
        val pluginResult = result as AddResult.PluginRepo
        assertEquals("jasoelys", pluginResult.name)
        assertEquals(2, pluginResult.installed)

        // Yanlış kutudan (Nuvio Deposu) verilince hata yerine Nuvio Plugin önerisi üretir
        val envWrong = TestEnv()
        envWrong.networkData[url] = mixedPluginRepoJson
        try {
            envWrong.installer.add(url, SourceType.NUVIO_REPO)
            fail("KindMismatch bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals(SourceType.NUVIO_PLUGIN, e.detectedType)
        }
    }

    // -------------------------------------------------------------------------
    // 6. CloudStream Deposu Testi
    // -------------------------------------------------------------------------

    @Test
    fun testCloudStreamRepo() = runTest {
        val env = TestEnv()
        val url = "https://raw.githubusercontent.com/neoser1984/neowatch/main/repo.json"
        env.networkData[url] = cloudStreamRepoJson
        val pluginsListUrl = "https://raw.githubusercontent.com/example/cs/builds/plugins.json"
        env.networkData[pluginsListUrl] = "[]" // boş eklenti listesi

        assertEquals(ManifestKind.External, ManifestKind.classify(cloudStreamRepoJson))

        val parsed = CsRepoManifestParser.parseRoot(cloudStreamRepoJson)
        assertEquals("NEO", parsed.name)
        assertEquals(1, parsed.pluginLists.size)

        // Yanlış kutudan (Nuvio Plugin) verilince CloudStream önerisi
        val envWrong = TestEnv()
        envWrong.networkData[url] = cloudStreamRepoJson
        try {
            envWrong.installer.add(url, SourceType.NUVIO_PLUGIN)
            fail("KindMismatch bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals(SourceType.CLOUDSTREAM, e.detectedType)
        }
    }

    // -------------------------------------------------------------------------
    // 7. Bozuk / Boş Manifest Testi
    // -------------------------------------------------------------------------

    @Test
    fun testCorruptAndEmptyManifests() = runTest {
        // HTML hata sayfası
        try {
            ManifestKind.classify("<html><body>502 Bad Gateway</body></html>")
            fail("NotJson bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.NotJson, e.kind)
        }

        // Boş metin
        try {
            ManifestKind.classify("")
            fail("NotJson bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.NotJson, e.kind)
        }

        // Boş JSON nesnesi -> Unknown
        assertEquals(ManifestKind.Unknown, ManifestKind.classify("{}"))

        // Düz dizi -> NotJson
        try {
            ManifestKind.classify("[1, 2, 3]")
            fail("NotJson bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(Kind.NotJson, e.kind)
        }
    }
}
