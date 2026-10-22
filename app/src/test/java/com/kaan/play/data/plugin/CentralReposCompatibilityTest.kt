package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.AddonManifestParser
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.AddonUrl
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
import com.kaan.play.data.net.PlayJson
import com.lagradost.cloudstream3.MainAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 57 Gerçek Depo Otomatik Uyum Testi (Madde 3, 4, 5).
 * https://raw.githubusercontent.com/liberta09/CloudStreamRepoManager/main/central_repos.json
 * adresindeki 57 depoyu çalışma anında indirir (repoya kopyalamaz).
 */
class CentralReposCompatibilityTest {

    private data class RepoEntry(
        val id: Int,
        val name: String,
        val platform: String,
        val nuvioKind: String?,
        val category: String?,
        val url: String,
    )

    private data class CompatibilityResult(
        val id: Int,
        val name: String,
        val expectedType: String,
        val detectedType: String,
        val added: Boolean,
        val contentSummary: String,
        val error: String?,
        val wrongBoxSuggested: Boolean,
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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

    @Test
    fun testAll57ReposCompatibility() = runTest {
        org.junit.Assume.assumeTrue(
            "CentralReposCompatibilityTest canli internet testi; -Dplay.test.liveCentralRepos=true ile calisir",
            System.getProperty("play.test.liveCentralRepos") == "true"
        )
        val centralReposUrl = "https://raw.githubusercontent.com/liberta09/CloudStreamRepoManager/main/central_repos.json"
        val centralJsonRaw: String = try {
            val req = Request.Builder().url(centralReposUrl).build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw Exception("HTTP ${res.code}")
                res.body.string()
            }
        } catch (e: Exception) {
            println("central_repos.json indirilemedi: ${e.message}")
            return@runTest
        }

        val rootJson = PlayJson.parseToJsonElement(centralJsonRaw).jsonObject
        val reposArray = rootJson["repos"]?.jsonArray ?: run {
            println("repos alanı bulunamadı")
            return@runTest
        }

        val repos = reposArray.mapNotNull { elem ->
            val obj = elem.jsonObject
            val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val platform = obj["platform"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val nuvioKind = obj["nuvioKind"]?.jsonPrimitive?.contentOrNull
            val category = obj["category"]?.jsonPrimitive?.contentOrNull
            val url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            RepoEntry(id, name, platform, nuvioKind, category, url)
        }

        println("Toplam test edilecek depo sayısı: ${repos.size}")
        assertEquals(57, repos.size)

        val results = mutableListOf<CompatibilityResult>()

        for (entry in repos) {
            val expectedTypeStr = when {
                entry.platform.equals("cloudstream", ignoreCase = true) -> "CloudStream"
                entry.nuvioKind.equals("plugin", ignoreCase = true) -> "Nuvio Plugin"
                entry.nuvioKind.equals("addon", ignoreCase = true) -> "Nuvio Addon"
                else -> "Bilinmiyor"
            }

            val normUrl = AddonUrl.normalize(entry.url)

            var rawManifest: String? = null
            var fetchError: String? = null
            try {
                val req = Request.Builder()
                    .url(normUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                httpClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        fetchError = "Site kapalı (HTTP ${res.code})"
                    } else {
                        rawManifest = res.body.string()
                    }
                }
            } catch (e: Exception) {
                fetchError = "Site kapalı (${e.javaClass.simpleName}: ${e.message})"
            }

            if (rawManifest == null) {
                results.add(
                    CompatibilityResult(
                        id = entry.id,
                        name = entry.name,
                        expectedType = expectedTypeStr,
                        detectedType = "-",
                        added = false,
                        contentSummary = "-",
                        error = fetchError ?: "Site kapalı",
                        wrongBoxSuggested = false,
                    )
                )
                continue
            }

            val classifiedKind = try {
                ManifestKind.classify(rawManifest!!)
            } catch (e: Exception) {
                null
            }

            val detectedSourceType = when (classifiedKind) {
                ManifestKind.PluginRepo -> SourceType.NUVIO_PLUGIN
                ManifestKind.External -> SourceType.CLOUDSTREAM
                ManifestKind.Addon -> SourceType.NUVIO_REPO
                else -> null
            }

            val detectedTypeStr = when (classifiedKind) {
                ManifestKind.PluginRepo -> "Nuvio Plugin"
                ManifestKind.External -> "CloudStream"
                ManifestKind.Addon -> "Nuvio Addon"
                else -> "Tanınmayan"
            }

            var wrongBoxOk = false
            if (detectedSourceType != null) {
                val wrongType = when (detectedSourceType) {
                    SourceType.NUVIO_PLUGIN -> SourceType.NUVIO_REPO
                    SourceType.NUVIO_REPO -> SourceType.NUVIO_PLUGIN
                    SourceType.CLOUDSTREAM -> SourceType.NUVIO_PLUGIN
                }

                val db = TestDb()
                val testFetcher = ManifestFetcher { u ->
                    if (u == normUrl) rawManifest!! else throw AddonException(Kind.Http, "404")
                }
                val testCodeFetcher = PluginCodeFetcher { _, _ -> "// stub" }
                val testCodeStore = object : PluginCodeStore {
                    override fun write(pluginId: Long, code: String) {}
                    override fun read(pluginId: Long): String? = null
                    override fun delete(pluginId: Long) {}
                }
                val csDexDir = File.createTempFile("csdex_tmp", "").also { it.delete(); it.mkdirs() }
                val testCsLoader = object : PluginDexLoader {
                    override suspend fun loadPlugin(dexFile: File, className: String, pluginId: String): CsApiResult = CsApiResult.NoApiRegistered(pluginId)
                }
                val aRepo = AddonRepository(db.addons, testFetcher, clock = { 1000L })
                val pRepo = PluginRepository(db.addons, db.plugins, testFetcher, testCodeFetcher, testCodeStore)
                val cRepo = CsRepository(db.addons, db.plugins, testFetcher, httpClient, CsDexStore(csDexDir), testCsLoader)
                val inst = SourceInstaller(aRepo, pRepo, cRepo, testFetcher)

                try {
                    inst.add(normUrl, expectedType = wrongType)
                } catch (e: AddonException) {
                    if (e.kind == Kind.KindMismatch && e.detectedType == detectedSourceType) {
                        wrongBoxOk = true
                    }
                }
            }

            var addedSuccessfully = false
            var contentSummary = ""
            var addError: String? = null

            try {
                when (detectedSourceType) {
                    SourceType.NUVIO_REPO -> {
                        val parsed = AddonManifestParser.parse(rawManifest!!)
                        val catalogsCount = parsed.catalogs.size
                        val hasStream = parsed.providesStreams
                        val hasSubs = parsed.providesSubtitles
                        contentSummary = buildString {
                            if (catalogsCount > 0) append("$catalogsCount katalog ")
                            if (hasStream) append("video ")
                            if (hasSubs) append("altyazı ")
                        }.trim().ifEmpty { "1 addon" }
                        addedSuccessfully = true
                    }
                    SourceType.NUVIO_PLUGIN -> {
                        val parsed = PluginRepoManifestParser.parse(rawManifest!!, normUrl)
                        contentSummary = "${parsed.scrapers.size} kazıyıcı"
                        addedSuccessfully = parsed.scrapers.isNotEmpty()
                    }
                    SourceType.CLOUDSTREAM -> {
                        val root = CsRepoManifestParser.parseRoot(rawManifest!!)
                        contentSummary = "${root.pluginLists.size} eklenti listesi"
                        addedSuccessfully = root.pluginLists.isNotEmpty()
                    }
                    null -> {
                        addError = "Desteklenmeyen format"
                    }
                }
            } catch (e: Exception) {
                addError = e.message ?: e.javaClass.simpleName
            }

            results.add(
                CompatibilityResult(
                    id = entry.id,
                    name = entry.name,
                    expectedType = expectedTypeStr,
                    detectedType = detectedTypeStr,
                    added = addedSuccessfully,
                    contentSummary = if (addedSuccessfully) contentSummary else "-",
                    error = addError,
                    wrongBoxSuggested = wrongBoxOk,
                )
            )
        }

        val sb = StringBuilder()
        sb.appendLine("## 57 Depo Otomatik Uyum Test Raporu\n")
        sb.appendLine("| No | Ad | Beklenen Tür | Play'in Bulduğu Tür | Eklendi mi | İçerik | Yanlış Kutu Önerisi | Hata |")
        sb.appendLine("|---|---|---|---|:---:|---|:---:|---|")

        var successCount = 0
        var unreachableCount = 0
        var mismatchCount = 0

        for (r in results) {
            val status = if (r.added) "✅ Evet" else "❌ Hayır"
            val wrongBox = if (r.wrongBoxSuggested) "✅ TR+EN" else if (r.error?.contains("Site kapalı") == true) "-" else "❌"
            val err = r.error ?: "-"
            sb.appendLine("| ${r.id} | ${r.name} | ${r.expectedType} | ${r.detectedType} | $status | ${r.contentSummary} | $wrongBox | $err |")

            if (r.added) successCount++
            if (r.error?.contains("Site kapalı") == true) unreachableCount++
            if (r.expectedType != r.detectedType && r.detectedType != "-") mismatchCount++
        }

        sb.appendLine("\n### Özet:")
        sb.appendLine("- Toplam Depo: ${results.size}")
        sb.appendLine("- Başarıyla Eklenebilen: $successCount")
        sb.appendLine("- Ulaşılamayan / Kapalı: $unreachableCount")
        sb.appendLine("- Liste ile Tür Farkı Olan (İçeriğe Göre Düzeltilen): $mismatchCount")

        val tableOutput = sb.toString()
        println(tableOutput)

        val reportFile = File("../docs/57-repo-raporu.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(tableOutput)

        assertTrue("En az 50 depo başarıyla eklenmeli", successCount >= 50)
    }
}
