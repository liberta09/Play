package com.kaan.play.data.cloudstream

import android.util.Log
import com.lagradost.cloudstream3.MainAPI
import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.kaan.play.data.plugin.RepoInstallPolicy
import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

private const val TAG = "PlayCs"

/** Kurulu bir CloudStream deposu özeti. */
data class CsRepo(
    val id: Long,
    val sourceUrl: String,
    val name: String,
    val description: String?,
    val addedAt: Long,
    val pluginCount: Int,
)

/** Kurulu tek bir CS eklentisi. Yeni depolarda [enabled] varsayılan olarak açıktır. */
data class InstalledCsPlugin(
    val id: Long,
    val repoId: Long,
    val pluginId: String,     // internalName
    val name: String,
    val version: String?,
    val description: String?,
    val language: String,
    val tvTypes: List<String>,
    val enabled: Boolean,
)

data class CsRepoAdded(val repo: CsRepo, val installed: Int, val skipped: Int)

fun interface Cs3Downloader {
    suspend fun download(url: String, expectedHash: String, pluginId: Long): Cs3Contents
}

/**
 * CloudStream eklenti depolarının tek kapısı (Faz 3). Depo satırı `addons` tablosunda
 * ([AddonType.CLOUDSTREAM_REPO]), eklentiler `plugins` tablosunda durur.
 * DEX dosyaları [CsDexStore] üzerinden `filesDir/cloudstream/` içinde saklanır.
 */
class CsRepository(
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val fetcher: ManifestFetcher,
    private val httpClient: OkHttpClient,
    private val store: CsDexStore,
    private val loader: PluginDexLoader,
    private val clock: () -> Long = System::currentTimeMillis,
    private val parallelDownloads: Int = 2,
    private val cs3Downloader: Cs3Downloader = Cs3Downloader { url, hash, id ->
        downloadCs3(url, hash, httpClient, store, id)
    },
) {

    val repos: Flow<List<CsRepo>> = combine(
        addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
        pluginDao.observeAll(),
    ) { rows, plugins ->
        val counts = plugins.groupingBy { it.repoId }.eachCount()
        rows.map { it.toCsRepo(counts[it.id] ?: 0) }
    }

    val plugins: Flow<List<InstalledCsPlugin>> =
        pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO)
            .map { rows -> rows.map { it.toInstalled() } }

    // ---------------------------------------------------------------------------
    // Ekle
    // ---------------------------------------------------------------------------

    /**
     * Zaten indirilmiş [raw] repo.json içeriğinden depoyu kurar.
     * Her CS eklentisi indirilip DEX ayıklanır; başarısızlar atlanır.
     * @throws AddonException
     */
    suspend fun addFetched(url: String, raw: String): CsRepoAdded {
        if (addonDao.countBySourceUrl(url) > 0) throw AddonException(Kind.AlreadyAdded)
        val root = CsRepoManifestParser.parseRoot(raw)

        // pluginLists adreslerinden eklenti listelerini paralel indir
        val allEntries = fetchAllPluginLists(root.pluginLists, url)
        if (allEntries.isEmpty()) throw AddonException(Kind.NoPlugins)

        // Depo satırını ekle
        val repoEntity = AddonEntity(
            type = AddonType.CLOUDSTREAM_REPO,
            sourceUrl = url,
            addonId = url,
            name = root.name,
            version = null,
            description = root.description,
            logoUrl = null,
            enabled = true,
            manifestJson = raw.take(8000), // manifest çok büyük olabilir
            addedAt = clock(),
        )
        val repoId = addonDao.insert(repoEntity)
        if (repoId == -1L) throw AddonException(Kind.AlreadyAdded)

        // .cs3 dosyalarını paralel indir
        val downloads = downloadAll(allEntries, repoId)
        val good = downloads.filter { it.success }
        val reasonCounts = downloads.filter { !it.success }.groupingBy { it.reason ?: "?" }.eachCount()
        if (reasonCounts.isNotEmpty()) {
            Log.d(TAG, "addFetched: atlanan eklentiler $reasonCounts (toplam ${downloads.size}, başarılı ${good.size})")
        }

        if (good.isEmpty()) {
            addonDao.delete(repoId)
            val first = downloads.firstOrNull()
            val detail = first?.let { "${it.entry.name}::${it.reason.orEmpty()}" }
            Log.d(TAG, "addFetched: tüm eklentiler başarısız (${downloads.size} denendi)")
            throw AddonException(Kind.PluginFileFailed, detail)
        }

        return CsRepoAdded(
            repo = repoEntity.copy(id = repoId).toCsRepo(good.size),
            installed = good.size,
            skipped = downloads.size - good.size,
        )
    }

    /**
     * Doğrudan bir `.cs3` eklenti dosyasını tekil depo olarak kurar.
     * @throws AddonException
     */
    suspend fun addDirectCs3(url: String): CsRepoAdded {
        if (addonDao.countBySourceUrl(url) > 0) throw AddonException(Kind.AlreadyAdded)

        val tempId = System.nanoTime()
        val contents = cs3Downloader.download(url, "", tempId)

        val parsed = runCatching {
            PlayJson.parseToJsonElement(contents.pluginManifest) as? JsonObject
        }.getOrNull()

        val rawName = (parsed?.get("name") as? JsonPrimitive)?.content
        val rawInternalName = (parsed?.get("internalName") as? JsonPrimitive)?.content
        val rawVersion = (parsed?.get("version") as? JsonPrimitive)?.content
        val rawDesc = (parsed?.get("description") as? JsonPrimitive)?.content
        val rawTypes = runCatching {
            (parsed?.get("tvTypes") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrNull() ?: emptyList()
        val rawLang = (parsed?.get("lang") as? JsonPrimitive)?.content ?: ""

        val filename = url.substringAfterLast('/')
        val pluginName = rawName?.takeIf { it.isNotBlank() } ?: filename.removeSuffix(".cs3")
        val internalName = rawInternalName?.takeIf { it.isNotBlank() } ?: pluginName

        val repoEntity = AddonEntity(
            type = AddonType.CLOUDSTREAM_REPO,
            sourceUrl = url,
            addonId = url,
            name = pluginName,
            version = rawVersion,
            description = rawDesc,
            logoUrl = null,
            enabled = true,
            manifestJson = contents.pluginManifest.take(8000),
            addedAt = clock(),
        )
        val repoId = addonDao.insert(repoEntity)
        if (repoId == -1L) {
            store.delete(tempId)
            throw AddonException(Kind.AlreadyAdded)
        }

        val pluginEntity = PluginEntity(
            repoId = repoId,
            pluginId = internalName,
            name = pluginName,
            version = rawVersion ?: "1",
            description = rawDesc,
            filename = filename,
            scriptUrl = url,
            supportedTypes = rawTypes.joinToString(","),
            contentLanguage = rawLang,
            hasSettings = false,
            sha256 = contents.sha256,
            sizeBytes = contents.sizeBytes,
            enabled = RepoInstallPolicy.initialPluginEnabled(AddonType.CLOUDSTREAM_REPO),
            installedAt = clock(),
        )
        val rowId = pluginDao.insert(pluginEntity)
        if (rowId == -1L) {
            store.delete(tempId)
            addonDao.delete(repoId)
            throw AddonException(Kind.AlreadyAdded)
        }

        val tmpDex = store.dexFile(tempId)
        val tmpMf = store.manifestFile(tempId)
        if (tmpDex.exists()) tmpDex.renameTo(store.dexFile(rowId))
        if (tmpMf.exists()) tmpMf.renameTo(store.manifestFile(rowId))
        store.setReadOnly(rowId)

        return CsRepoAdded(
            repo = repoEntity.copy(id = repoId).toCsRepo(1),
            installed = 1,
            skipped = 0,
        )
    }

    // ---------------------------------------------------------------------------
    // Kaldır
    // ---------------------------------------------------------------------------

    suspend fun remove(repoId: Long) {
        pluginDao.ofRepo(repoId).forEach { store.delete(it.id) }
        addonDao.delete(repoId)
    }

    // ---------------------------------------------------------------------------
    // Açık/kapalı
    // ---------------------------------------------------------------------------

    suspend fun setEnabled(pluginId: Long, enabled: Boolean) = pluginDao.setEnabled(pluginId, enabled)
    suspend fun setRepoEnabled(repoId: Long, enabled: Boolean) = pluginDao.setEnabledForRepo(repoId, enabled)
    suspend fun setAllEnabled(enabled: Boolean) = pluginDao.setAllEnabledOfType(AddonType.CLOUDSTREAM_REPO, enabled)

    // ---------------------------------------------------------------------------
    // Çalışma zamanı
    // ---------------------------------------------------------------------------

    /**
     * Açık eklentiler için [MainAPI] listesini döner.
     * Her çağrıda DEX'i [PathClassLoader] ile yükler (Android sınıf yükleyici önbelleği bunu ucuzlatır).
     */
    suspend fun loadApis(pluginRowId: Long, allowRedownload: Boolean = true): CsApiResult {
        val row = pluginDao.byId(pluginRowId) ?: run {
            val failure = CsApiResult.PluginRowMissing(pluginRowId)
            Log.w(TAG, formatCsLog(failure))
            return failure
        }
        if (row.pluginId.contains("traktsync", ignoreCase = true) ||
            row.name.contains("traktsync", ignoreCase = true) ||
            row.pluginId.contains("pltmustafa", ignoreCase = true)
        ) {
            Log.w(TAG, "Trakt eklentisi yuklenmedi (devre disi): ${row.pluginId}")
            return CsApiResult.LoadFailed("TraktSyncDisabled", row.pluginId)
        }
        val dexFile = store.dexFile(pluginRowId)
        if (!dexFile.exists()) {
            if (allowRedownload) {
                Log.w(TAG, "${row.pluginId}: DexFileMissing (tek seferlik yeniden indirme deneniyor)")
                val downloaded = try {
                    cs3Downloader.download(row.scriptUrl, row.sha256, pluginRowId)
                    dexFile.exists()
                } catch (e: Exception) {
                    false
                }
                if (downloaded) {
                    return loadApis(pluginRowId, allowRedownload = false)
                }
            }
            val failure = CsApiResult.DexFileMissing(row.pluginId)
            Log.w(TAG, formatCsLog(failure))
            return failure
        }
        val className = resolveMainClassName(store.readManifest(pluginRowId), row.pluginId)
        val result = loader.loadPlugin(dexFile, className, row.pluginId)
        if (result is CsApiResult.Failure) {
            Log.w(TAG, formatCsLog(result))
        }
        return result
    }

    suspend fun enabledPlugins(): List<InstalledCsPlugin> =
        pluginDao.enabledOfType(AddonType.CLOUDSTREAM_REPO).map { it.toInstalled() }

    // ---------------------------------------------------------------------------
    // İç yardımcılar
    // ---------------------------------------------------------------------------

    private suspend fun fetchAllPluginLists(listUrls: List<String>, baseUrl: String): List<CsPluginEntry> =
        listUrls.flatMap { listUrl ->
            try {
                val raw = fetcher.fetch(listUrl)
                val entries = CsRepoManifestParser.parsePluginList(raw, listUrl)
                if (entries.isEmpty()) {
                    Log.d(TAG, "fetchAllPluginLists: boş/çözümlenemedi host=${listUrl.toHttpUrlOrNull()?.host} bytes=${raw.length}")
                }
                entries
            } catch (e: Exception) {
                Log.d(TAG, "fetchAllPluginLists: indirilemedi host=${listUrl.toHttpUrlOrNull()?.host} ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
        }.distinctBy { it.internalName }.take(CsRepoManifestParser.MAX_PLUGINS)

    private class Dl(val entry: CsPluginEntry, val success: Boolean, val reason: String? = null)

    private suspend fun downloadAll(entries: List<CsPluginEntry>, repoId: Long): List<Dl> {
        val gate = Semaphore(parallelDownloads)
        return coroutineScope {
            entries.map { entry ->
                async(Dispatchers.IO) {
                    gate.withPermit { downloadOne(entry, repoId) }
                }
            }.awaitAll()
        }
    }

    private suspend fun downloadOne(entry: CsPluginEntry, repoId: Long): Dl {
        return try {
            // Geçici bir id ile geçici dosya yolu belirle; eklenti satırı eklendikten sonra gerçek id alınır.
            val tempId = System.nanoTime()
            val contents = cs3Downloader.download(entry.url, entry.fileHash, tempId)

            // Veritabanı satırını ekle
            val pluginEntity = PluginEntity(
                repoId = repoId,
                pluginId = entry.internalName,    // DEX'te yüklenecek sınıf adı
                name = entry.name,
                version = entry.version.toString(),
                description = entry.description,
                filename = entry.url.substringAfterLast('/'),
                scriptUrl = entry.url,
                supportedTypes = entry.tvTypes.joinToString(","),
                contentLanguage = entry.language,
                hasSettings = false,
                sha256 = contents.sha256,
                sizeBytes = contents.sizeBytes,
                enabled = RepoInstallPolicy.initialPluginEnabled(AddonType.CLOUDSTREAM_REPO),
                installedAt = clock(),
            )
            val rowId = pluginDao.insert(pluginEntity)
            if (rowId == -1L) {
                store.delete(tempId)
                Log.d(TAG, "downloadOne: ${entry.internalName} eklenemedi (zaten var/benzersizlik ihlali)")
                return Dl(entry, false, "db")
            }
            // Geçici dosyaları gerçek id ile yeniden adlandır
            val tmpDex = store.dexFile(tempId)
            val tmpMf = store.manifestFile(tempId)
            if (tmpDex.exists()) tmpDex.renameTo(store.dexFile(rowId))
            if (tmpMf.exists()) tmpMf.renameTo(store.manifestFile(rowId))
            store.setReadOnly(rowId)

            Dl(entry, true)
        } catch (e: AddonException) {
            Log.d(TAG, "downloadOne: ${entry.internalName} başarısız host=${entry.url.toHttpUrlOrNull()?.host} kind=${e.kind} detail=${e.detail}")
            val reason = when (e.kind) {
                Kind.Http -> "http:${e.detail.orEmpty()}"
                Kind.TooLarge -> "toolarge"
                Kind.Network -> "network"
                Kind.InvalidField -> if (e.detail?.startsWith("hash mismatch") == true) "hash" else "invalid"
                Kind.MissingField -> "invalid"
                else -> "invalid"
            }
            Dl(entry, false, reason)
        } catch (e: Exception) {
            Log.d(TAG, "downloadOne: ${entry.internalName} beklenmeyen hata ${e.javaClass.simpleName}: ${e.message}")
            Dl(entry, false, "invalid")
        }
    }

    private fun AddonEntity.toCsRepo(count: Int) = CsRepo(id, sourceUrl, name, description, addedAt, count)

    private fun PluginEntity.toInstalled() = InstalledCsPlugin(
        id = id, repoId = repoId, pluginId = pluginId, name = name,
        version = version, description = description,
        language = contentLanguage, tvTypes = supportedTypes.split(',').filter { it.isNotBlank() },
        enabled = enabled,
    )
}
