package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.data.cloudstream.CsDexStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** `addons` + `plugins` tablolarını (CASCADE dahil) taklit eden bellek içi DAO çifti. */
private class MemDb {
    val addonRows = MutableStateFlow<List<AddonEntity>>(emptyList())
    val pluginRows = MutableStateFlow<List<PluginEntity>>(emptyList())
    private var addonSeq = 1L
    private var pluginSeq = 1L

    val addons = object : AddonDao {
        override fun observeOfType(type: AddonType): Flow<List<AddonEntity>> = addonRows.map { l -> l.filter { it.type == type } }
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
            pluginRows.value = pluginRows.value.filterNot { it.repoId == id } // ON DELETE CASCADE
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
            if (pluginRows.value.any { it.repoId == entity.repoId && it.pluginId == entity.pluginId }) return -1
            val id = pluginSeq++
            pluginRows.value += entity.copy(id = id)
            return id
        }
        override suspend fun updateInfo(
            id: Long, name: String, version: String?, description: String?, filename: String, scriptUrl: String,
            supportedTypes: String, contentLanguage: String, hasSettings: Boolean, sha256: String, sizeBytes: Long,
        ) {
            pluginRows.value = pluginRows.value.map {
                if (it.id == id) it.copy(
                    name = name, version = version, description = description, filename = filename, scriptUrl = scriptUrl,
                    supportedTypes = supportedTypes, contentLanguage = contentLanguage, hasSettings = hasSettings,
                    sha256 = sha256, sizeBytes = sizeBytes,
                ) else it
            }
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
    }
}

private class MemStore : PluginCodeStore {
    val files = HashMap<Long, String>()
    override fun write(pluginId: Long, code: String) { files[pluginId] = code }
    override fun read(pluginId: Long) = files[pluginId]
    override fun delete(pluginId: Long) { files.remove(pluginId) }
}

class PluginRepositoryTest {

    private val repoUrl = "https://example.com/repo/manifest.json"
    private val good = "async function getStreams() { return []; } module.exports = { getStreams };"

    private fun manifest(vararg scrapers: String, name: String = "Deneme Deposu", version: String = "1.0.0") =
        """{"name":"$name","version":"$version","scrapers":[${scrapers.joinToString(",")}]}"""

    private fun scraper(id: String, version: String = "1.0.0", file: String = "providers/$id.js", extra: String = "") =
        """{"id":"$id","name":"Kaz $id","version":"$version","filename":"$file","supportedTypes":["movie","series"],"contentLanguage":["tr","en"]$extra}"""

    private val db = MemDb()
    private val store = MemStore()
    private var manifestText = manifest(scraper("a"), scraper("b"))
    private val code = HashMap<String, String>()
    private val failing = HashMap<String, AddonException>()
    private val fetched: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())
    private var clock = 1_000L

    private val manifestFetcher = ManifestFetcher { fetched += "M:$it"; manifestText }
    private val codeFetcher = PluginCodeFetcher { url, _ ->
        fetched += "C:$url"
        failing[url]?.let { throw it }
        code[url] ?: throw AddonException(Kind.Http, "404")
    }
    private val plugins = PluginRepository(db.addons, db.plugins, manifestFetcher, codeFetcher, store, clock = { clock++ })
    private val addonRepo = AddonRepository(db.addons, manifestFetcher, clock = { clock++ })

    // CloudStream (Faz 3): plugins.json içeriği boş gelir, gerçek ağ/DEX yükleme bu testte sınanmaz
    // (yalnızca "Eklenti ekle" yönlendirmesi sınanır; indirme/DEX CsRepository'nin kendi dosyalarında).
    private var csPluginListJson = "[]"
    private val csFetcher = ManifestFetcher { csPluginListJson }
    private val csDexDir = java.io.File.createTempFile("csdex", "").also { it.delete(); it.mkdirs() }
    private val csLoader = object : PluginDexLoader {
        override suspend fun loadPlugin(dexFile: java.io.File, className: String, pluginId: String): CsApiResult = CsApiResult.NoApiRegistered(pluginId)
    }
    private val csRepos = CsRepository(
        addonDao = db.addons,
        pluginDao = db.plugins,
        fetcher = csFetcher,
        httpClient = OkHttpClient(),
        store = CsDexStore(csDexDir),
        loader = csLoader,
        clock = { clock++ },
    )
    private val installer = SourceInstaller(addonRepo, plugins, csRepos, manifestFetcher)

    private fun stock(vararg ids: String) = ids.forEach { code["https://example.com/repo/providers/$it.js"] = good }

    // ---- sınıflandırma ve ayrıştırma ----

    @Test
    fun manifestKindIsDecidedByContentNotByNamesOrLabels() {
        assertEquals(ManifestKind.PluginRepo, ManifestKind.classify(manifest(scraper("a"))))
        // Üst düzey id/logo gibi fazladan alanlar (gerçek bir depoda var) depo olmaktan çıkarmaz.
        assertEquals(ManifestKind.PluginRepo, ManifestKind.classify("""{"id":"x","name":"n","logo":"l","version":"1","scrapers":[{"id":"a","filename":"a.js"}]}"""))
        assertEquals(ManifestKind.PluginRepo, ManifestKind.classify("""{"name":"n","scrapers":[]}"""))
        assertEquals(ManifestKind.Addon, ManifestKind.classify("""{"id":"org.x","name":"A","version":"1","resources":["stream"],"types":["movie"],"catalogs":[]}"""))
        // Nuvio kuralı: nesne dizisi olarak scrapers varsa PLUGIN DEPOSU; resources/types bunu değiştirmez.
        assertEquals(ManifestKind.PluginRepo, ManifestKind.classify("""{"id":"x","name":"n","resources":["stream"],"types":["movie"],"scrapers":[{"id":"a","filename":"a.js"}]}"""))
        // jasoelys örneği: id alanı yok, resources [stream, subtitles], scrapers var -> PluginRepo
        assertEquals(ManifestKind.PluginRepo, ManifestKind.classify("""{"name":"jasoelys","version":"1.0.0","description":"Nuvio Sağlayıcı Deposu","resources":["stream","subtitles"],"types":["movie","series","tv"],"scrapers":[{"id":"animecix","name":"AnimeCix","filename":"providers/animecix.js"}]}"""))
        assertEquals(ManifestKind.External, ManifestKind.classify("""{"name":"n","manifestVersion":1,"pluginLists":["https://x/plugins.json"]}"""))
        assertEquals(ManifestKind.Unknown, ManifestKind.classify("""{"name":"n"}"""))
        try { ManifestKind.classify("<html>"); fail() } catch (e: AddonException) { assertEquals(Kind.NotJson, e.kind) }
        try { ManifestKind.classify("[1,2]"); fail() } catch (e: AddonException) { assertEquals(Kind.NotJson, e.kind) }
    }

    @Test
    fun parserResolvesRelativeAndAbsoluteFilenamesAndSkipsBrokenEntries() {
        val raw = manifest(
            scraper("rel", file = "providers/x.js"),
            scraper("abs", file = "https://cdn.example.org/y.js"),
            scraper("up", file = "../shared/z.js"),
            """{"id":"nofile","name":"n","version":"1"}""",
            """{"filename":"noid.js"}""",
            scraper("bad", file = "ftp://example.com/b.js"),
            scraper("js", file = "javascript:alert(1)"),
            scraper("rel", file = "providers/dup.js"), // yinelenen kimlik
            "\"metin\"", "null",
            """{"id":"min","filename":"m.js"}""",
        )
        val parsed = PluginRepoManifestParser.parse(raw, "https://example.com/repo/manifest.json")
        assertEquals(listOf("rel", "abs", "up", "min"), parsed.scrapers.map { it.id })
        assertEquals("https://example.com/repo/providers/x.js", parsed.scrapers[0].scriptUrl)
        assertEquals("https://cdn.example.org/y.js", parsed.scrapers[1].scriptUrl)
        assertEquals("https://example.com/shared/z.js", parsed.scrapers[2].scriptUrl)
        // Yalnızca id+filename olan (manitux tarzı) girişler de kabul: varsayılanlar dolar.
        val min = parsed.scrapers[3]
        assertEquals(listOf("movie", "tv"), min.supportedTypes)
        assertEquals("0", min.version)
        assertEquals("min", min.name)
        assertEquals(setOf("movie", "tv"), parsed.scrapers[0].normalizedTypes) // series -> tv
    }

    @Test
    fun parserErrorsAreSpecific() {
        fun kind(raw: String) = try { PluginRepoManifestParser.parse(raw, repoUrl); null } catch (e: AddonException) { e.kind to e.detail }
        assertEquals(Kind.NotJson to null, kind("nope"))
        assertEquals(Kind.MissingField to "name", kind("""{"scrapers":[{"id":"a","filename":"a.js"}]}"""))
        assertEquals(Kind.MissingField to "scrapers", kind("""{"name":"n"}"""))
        assertEquals(Kind.NoPlugins to null, kind("""{"name":"n","scrapers":[]}"""))
        assertEquals(Kind.NoPlugins to null, kind("""{"name":"n","scrapers":[{"id":"a"}]}"""))
    }

    @Test
    fun versionComparisonIsNumericWhenPossible() {
        assertTrue(VersionCompare.compare("1.0.0", "1.0.1") > 0)
        assertTrue(VersionCompare.compare("1.9.0", "1.10.0") > 0) // sayısal, metin sırası değil
        assertTrue(VersionCompare.compare("2.0", "1.9.9") < 0)
        assertEquals(0, VersionCompare.compare("1.0", "1.0.0"))
        assertEquals(0, VersionCompare.compare("v1.2", "1.2"))
        assertTrue(VersionCompare.compare("1.0.0-beta", "1.0.0") > 0) // sayısal olmayan: farklıysa güncelleme
        assertEquals(0, VersionCompare.compare("abc", "abc"))
    }

    // ---- kurulum ----

    @Test
    fun addingARepoDownloadsEveryFileAndInstallsPluginsDisabled() = runTest {
        stock("a", "b")
        val result = plugins.addFetched(repoUrl, manifestText)

        assertEquals(2, result.installed)
        assertEquals(0, result.skipped)
        assertEquals("Deneme Deposu", result.repo.name)
        assertEquals(2, result.repo.pluginCount)

        val repo = db.addonRows.value.single()
        assertEquals(AddonType.NUVIO_PLUGIN_REPO, repo.type)
        assertEquals(repoUrl, repo.sourceUrl)
        assertEquals("1.0.0", repo.version)

        val rows = db.pluginRows.value
        assertEquals(setOf("a", "b"), rows.map { it.pluginId }.toSet())
        assertTrue("varsayılan KAPALI olmalı", rows.none { it.enabled })
        assertEquals("providers/a.js", rows.first { it.pluginId == "a" }.filename)
        assertEquals("tr,en", rows[0].contentLanguage)
        assertEquals("movie,series", rows[0].supportedTypes)
        assertEquals(PluginRepository.sha256(good), rows[0].sha256)
        assertEquals(good.toByteArray().size.toLong(), rows[0].sizeBytes)
        rows.forEach { assertEquals(good, store.files[it.id]) }

        // Kayıt Flow'ları: depo sayısı ve kazıyıcılar.
        assertEquals(2, plugins.repos.first().single().pluginCount)
        val installed = plugins.plugins.first()
        assertEquals(setOf("movie", "tv"), installed[0].supportedTypes)
        assertTrue(installed.none { it.enabled })
        assertTrue(plugins.enabledPlugins().isEmpty())
    }

    @Test
    fun brokenFilesAreSkippedAndAllFailingIsAClearError() = runTest {
        manifestText = manifest(scraper("ok"), scraper("html"), scraper("empty"), scraper("noentry"), scraper("gone"), scraper("big"))
        code["https://example.com/repo/providers/ok.js"] = good
        code["https://example.com/repo/providers/html.js"] = "<html><body>404 Not Found</body></html>"
        code["https://example.com/repo/providers/empty.js"] = "   "
        code["https://example.com/repo/providers/noentry.js"] = "var x = 1; // getStreams yok değil: yorumda geçiyor ama..."
        failing["https://example.com/repo/providers/big.js"] = AddonException(Kind.TooLarge)
        // "gone": 404

        val result = plugins.addFetched(repoUrl, manifestText)
        // "noentry" metni "getStreams" sözcüğünü içerdiği için geçer (bilinçli gevşek denetim; çalıştırmada yutulur).
        assertEquals(setOf("ok", "noentry"), db.pluginRows.value.map { it.pluginId }.toSet())
        assertEquals(2, result.installed)
        assertEquals(4, result.skipped)

        // Hepsi bozuksa: hiçbir şey kaydedilmez ve hata ilk başarısız kazıyıcının adını taşır.
        val db2 = MemDb()
        val repo2 = PluginRepository(db2.addons, db2.plugins, manifestFetcher, { _, _ -> "<html></html>" }, MemStore())
        try {
            repo2.addFetched("https://example.com/other/manifest.json", manifest(scraper("x"), scraper("y")))
            fail()
        } catch (e: AddonException) {
            assertEquals(Kind.PluginFileFailed, e.kind)
            assertEquals("Kaz x", e.detail)
        }
        assertTrue(db2.addonRows.value.isEmpty() && db2.pluginRows.value.isEmpty())
    }

    @Test
    fun networkErrorIsRetriedOnceButNotFoundIsNot() = runTest {
        stock("a")
        var aCalls = 0
        var bCalls = 0
        val flaky = PluginCodeFetcher { url, _ ->
            when {
                url.endsWith("/a.js") -> { aCalls++; if (aCalls == 1) throw AddonException(Kind.Network) else good }
                else -> { bCalls++; throw AddonException(Kind.Http, "404") }
            }
        }
        val repo = PluginRepository(db.addons, db.plugins, manifestFetcher, flaky, store)
        val r = repo.addFetched(repoUrl, manifest(scraper("a"), scraper("b")))
        assertEquals(1, r.installed)
        assertEquals(2, aCalls) // ağ hatasında bir kez daha
        assertEquals(1, bCalls) // 404'te yeniden deneme yok
    }

    @Test
    fun duplicateRepoIsRejectedAndNothingChanges() = runTest {
        stock("a", "b")
        plugins.addFetched(repoUrl, manifestText)
        try { plugins.addFetched(repoUrl, manifestText); fail() } catch (e: AddonException) { assertEquals(Kind.AlreadyAdded, e.kind) }
        assertEquals(1, db.addonRows.value.size)
        assertEquals(2, db.pluginRows.value.size)
    }

    // ---- "Eklenti ekle": içeriğe göre yönlendirme ----

    @Test
    fun installerRoutesByContent() = runTest {
        stock("a", "b")
        manifestText = manifest(scraper("a"), scraper("b"))
        val repoResult = installer.add("example.com/repo")
        assertEquals(AddResult.PluginRepo("Deneme Deposu", 2, 0), repoResult)
        assertEquals("M:https://example.com/repo/manifest.json", fetched.first())
        assertEquals(1, db.addonRows.value.count { it.type == AddonType.NUVIO_PLUGIN_REPO })
        assertEquals(0, db.addonRows.value.count { it.type == AddonType.NUVIO_ADDON })

        manifestText = """{"id":"org.x","name":"Örnek Addon","version":"1","types":["movie"],"resources":["stream"]}"""
        val addonResult = installer.add("https://other.example/addon/manifest.json")
        assertEquals(AddResult.Addon("Örnek Addon"), addonResult)
        assertEquals(1, db.addonRows.value.count { it.type == AddonType.NUVIO_ADDON })
        assertEquals(2, db.pluginRows.value.size) // addon eklemek kazıyıcıya dokunmaz

        try { installer.add("https://other.example/addon/manifest.json"); fail() } catch (e: AddonException) { assertEquals(Kind.AlreadyAdded, e.kind) }
        try { installer.add("example.com/repo"); fail() } catch (e: AddonException) { assertEquals(Kind.AlreadyAdded, e.kind) }

        // CloudStream (External) deposu: yönlendirme CsRepository.addFetched'e gider (artık desteklenir,
        // bkz. Faz 3); plugins.json boş geldiği için NoPlugins ile biter.
        manifestText = """{"name":"n","pluginLists":["https://x/p.json"]}"""
        try { installer.add("https://cs.example/repo.json"); fail() } catch (e: AddonException) { assertEquals(Kind.NoPlugins, e.kind) }

        // Tanınmayan JSON: addon ayrıştırıcısının somut hatası (eksik alan) kullanıcıya gider.
        manifestText = """{"name":"n"}"""
        try { installer.add("https://x.example/y"); fail() } catch (e: AddonException) { assertEquals(Kind.MissingField, e.kind); assertEquals("id", e.detail) }
        manifestText = "<html>login</html>"
        try { installer.add("https://x.example/z"); fail() } catch (e: AddonException) { assertEquals(Kind.NotJson, e.kind) }
    }

    @Test
    fun installerValidatesExpectedTypeAndRejectsMismatch() = runTest {
        stock("a", "b")
        // 1. Nuvio Plugin seçiliyken scrapers içeren manifest başarıyla kurulur:
        manifestText = manifest(scraper("a"), scraper("b"))
        val pluginResult = installer.add("https://example.com/repo", expectedType = SourceType.NUVIO_PLUGIN)
        assertEquals(AddResult.PluginRepo("Deneme Deposu", 2, 0), pluginResult)

        // 2. Nuvio Plugin seçiliyken addon manifest'i verilirse KindMismatch("nuvio_plugin") hatası fırlatılır:
        manifestText = """{"id":"org.x","name":"Örnek Addon","version":"1","types":["movie"],"resources":["stream"]}"""
        try {
            installer.add("https://mismatch.example/addon", expectedType = SourceType.NUVIO_PLUGIN)
            fail("Beklenen tür uyuşmazlığı hatası fırlatılmalıydı")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals("nuvio_plugin", e.detail)
        }

        // 3. Nuvio Deposu seçiliyken plugin manifest'i verilirse KindMismatch("nuvio_repo") hatası fırlatılır:
        manifestText = manifest(scraper("a"))
        try {
            installer.add("https://mismatch.example/plugin", expectedType = SourceType.NUVIO_REPO)
            fail("Beklenen tür uyuşmazlığı hatası fırlatılmalıydı")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals("nuvio_repo", e.detail)
        }

        // 4. CloudStream Deposu seçiliyken plugin manifest'i verilirse KindMismatch("cloudstream") hatası fırlatılır:
        try {
            installer.add("https://mismatch.example/plugin2", expectedType = SourceType.CLOUDSTREAM)
            fail("Beklenen tür uyuşmazlığı hatası fırlatılmalıydı")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals("cloudstream", e.detail)
        }

        // 5. Doğrudan .cs3 dosyası NUVIO_REPO veya NUVIO_PLUGIN seçiliyken reddedilir:
        try {
            installer.add("https://example.com/plugin.cs3", expectedType = SourceType.NUVIO_REPO)
            fail("cs3 dosyası nuvio repo olarak eklenemez")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals("nuvio_repo", e.detail)
        }
        try {
            installer.add("https://example.com/plugin.cs3", expectedType = SourceType.NUVIO_PLUGIN)
            fail("cs3 dosyası nuvio plugin olarak eklenemez")
        } catch (e: AddonException) {
            assertEquals(Kind.KindMismatch, e.kind)
            assertEquals("nuvio_plugin", e.detail)
        }
    }

    @Test
    fun addonListStaysFreeOfRepoRows() = runTest {
        stock("a", "b")
        installer.add("example.com/repo")
        assertTrue(addonRepo.addons.first().isEmpty()) // eklenti listeleri ve kaynak toplama yalnızca addon'ları görür
        assertTrue(addonRepo.enabledAddons().isEmpty())
    }

    // ---- açma/kapama, kod, silme ----

    @Test
    fun togglingLoadingCodeAndRemovingTheRepo() = runTest {
        stock("a", "b")
        plugins.addFetched(repoUrl, manifestText)
        val a = db.pluginRows.value.first { it.pluginId == "a" }

        plugins.setPluginEnabled(a.id, true)
        assertEquals(listOf("a"), plugins.enabledPlugins().map { it.pluginId })
        plugins.setPluginEnabled(a.id, false)
        assertTrue(plugins.enabledPlugins().isEmpty())

        assertEquals(good, plugins.loadCode(a.id))
        store.files[a.id] = good + "// değiştirildi"
        assertNull("özet uyuşmazsa kod verilmez", plugins.loadCode(a.id))
        store.files.remove(a.id)
        assertNull(plugins.loadCode(a.id))
        assertNull(plugins.loadCode(9999))

        val repoId = db.addonRows.value.single().id
        plugins.remove(repoId)
        assertTrue(db.addonRows.value.isEmpty())
        assertTrue(db.pluginRows.value.isEmpty()) // kazıyıcı satırları da gitti
        assertTrue("kod dosyaları da silinmeli", store.files.isEmpty())
    }

    // ---- güncelleme ----

    @Test
    fun updateCheckComparesVersionsAndApplyKeepsEnabledState() = runTest {
        stock("a", "b")
        plugins.addFetched(repoUrl, manifestText)
        val repoId = db.addonRows.value.single().id
        val a = db.pluginRows.value.first { it.pluginId == "a" }
        plugins.setPluginEnabled(a.id, true)

        // Aynı sürümler: güncel.
        assertTrue(plugins.checkForUpdates(repoId).isEmpty)

        // a: sürüm yükseldi; b: kalktı; c: yeni; yeni sürüm kodu başka.
        val newCode = "async function getStreams() { return [{url:'https://x/'}]; } module.exports = { getStreams };"
        manifestText = manifest(scraper("a", version = "1.1.0"), scraper("c"), version = "1.1.0")
        code["https://example.com/repo/providers/a.js"] = newCode
        stock("c")

        val plan = plugins.checkForUpdates(repoId)
        assertEquals(listOf("Kaz a"), plan.changed)
        assertEquals(listOf("Kaz c"), plan.added)
        assertEquals(listOf("Kaz b"), plan.removed)
        assertEquals(3, plan.count)
        assertFalse(plan.isEmpty)
        // Denetim kod indirmez (yalnızca manifest).
        assertEquals(0, fetched.count { it.startsWith("C:") && it.endsWith("/c.js") })

        val result = plugins.applyUpdate(plan)
        assertEquals(3, result.updated)
        val rows = db.pluginRows.value.associateBy { it.pluginId }
        assertEquals(setOf("a", "c"), rows.keys)
        assertTrue("güncellenen kazıyıcının açık durumu korunmalı", rows.getValue("a").enabled)
        assertFalse("yeni kazıyıcı kapalı gelir", rows.getValue("c").enabled)
        assertEquals("1.1.0", rows.getValue("a").version)
        assertEquals(newCode, store.files[rows.getValue("a").id])
        assertEquals(PluginRepository.sha256(newCode), rows.getValue("a").sha256)
        assertEquals("1.1.0", db.addonRows.value.single().version)
        assertEquals(2, store.files.size) // b'nin dosyası silindi

        // Uygulandıktan sonra tekrar denetim: güncel.
        assertTrue(plugins.checkForUpdates(repoId).isEmpty)
    }

    @Test
    fun olderRemoteVersionIsNotAnUpdateAndFailedDownloadKeepsOldCode() = runTest {
        stock("a")
        plugins.addFetched(repoUrl, manifest(scraper("a", version = "2.0.0")))
        val repoId = db.addonRows.value.single().id
        manifestText = manifest(scraper("a", version = "1.0.0"))
        assertTrue(plugins.checkForUpdates(repoId).isEmpty) // eskiye dönüş güncelleme sayılmaz

        manifestText = manifest(scraper("a", version = "3.0.0"))
        val plan = plugins.checkForUpdates(repoId)
        failing["https://example.com/repo/providers/a.js"] = AddonException(Kind.Http, "500")
        val r = plugins.applyUpdate(plan)
        assertEquals(1, r.skipped)
        assertEquals("2.0.0", db.pluginRows.value.single().version) // eski sürüm ve kod kaldı
        assertEquals(good, store.files[db.pluginRows.value.single().id])
    }

    @Test
    fun fileStoreWritesAtomicallyAndUsesOnlyNumbersAsNames() {
        val dir = java.io.File.createTempFile("plugins", "").also { it.delete(); it.mkdirs() }
        try {
            val s = FilePluginCodeStore(java.io.File(dir, "plugins"))
            assertNull(s.read(7))
            s.write(7, "birinci")
            s.write(7, "ikinci ünicode ✓")
            assertEquals("ikinci ünicode ✓", s.read(7))
            assertEquals(listOf("7.js"), java.io.File(dir, "plugins").list()!!.toList()) // geçici dosya kalmaz
            s.delete(7)
            assertNull(s.read(7))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun looksLikePluginCheck() {
        assertTrue(PluginRepository.looksLikePlugin("module.exports = { getStreams };"))
        assertFalse(PluginRepository.looksLikePlugin("<!doctype html><title>x</title> getStreams"))
        assertFalse(PluginRepository.looksLikePlugin(""))
        assertFalse(PluginRepository.looksLikePlugin("var a = 1;"))
        assertNotNull(PluginRepository.sha256("x"))
    }
}
