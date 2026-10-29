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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

private const val TAG = "PlayCs"
internal const val CLOUDSTREAM_MEGA_REPOSITORY_INDEX_URL = "https://raw.githubusercontent.com/recloudstream/cs-repos/master/repos-db.json"
internal const val WIO_MEGA_REPOSITORY_INDEX_URL = "https://raw.githubusercontent.com/Wiojelt/WioLand/main/repos-db.json"

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
    private val repoCatalog: CsRepoCatalogStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val parallelDownloads: Int = 2,
    private val cs3Downloader: Cs3Downloader = Cs3Downloader { url, hash, id ->
        downloadCs3(url, hash, httpClient, store, id)
    },
) {
    private val megaInstallMutex = Mutex()
    private val pendingMegaIndexLock = Any()
    private val pendingMegaIndexUrls = linkedMapOf<String, LinkedHashSet<String>>()

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
        val root = CsRepoManifestParser.parseRoot(raw, url)

        // pluginLists adreslerinden eklenti listelerini paralel indir
        val nestedRepositories = mutableListOf<CsRepoReference>()
        val allEntries = fetchAllPluginLists(root.pluginLists, url, nestedRepositories)
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

        val childRepositories = mergeReferences(root.subRepositories + nestedRepositories)
        installRepositories(childRepositories)
        val indexUrls = downloads.asSequence()
            .filter { it.success }
            .flatMap { megaRepoIndexUrlsFor(it.entry.internalName, it.entry.name, it.entry.description).asSequence() }
            .distinct()
            .toList()
        if (indexUrls.isNotEmpty()) discoverMegaRepositories(indexUrls, url)

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

        val indexUrls = megaRepoIndexUrlsFor(internalName, pluginName, rawDesc)
        if (indexUrls.isNotEmpty()) discoverMegaRepositories(indexUrls, url)

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
        val root = addonDao.byId(repoId) ?: return
        if (root.type != AddonType.CLOUDSTREAM_REPO) return

        // Eski kurulumlarda grafiğe henüz kaydedilmemiş doğrudan alt depoları da silme sırasında yakala.
        repoCatalog?.let { catalog ->
            val declaredChildren = runCatching {
                CsRepoManifestParser.parseRoot(root.manifestJson, root.sourceUrl).subRepositories
            }.getOrDefault(emptyList())
            catalog.merge(declaredChildren)
        }
        val descendantUrls = repoCatalog?.removeOwnedDescendants(root.sourceUrl).orEmpty()
        val rowsByUrl = addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first()
            .associateBy { canonicalRepoUrl(it.sourceUrl) }
        val rowsToRemove = (descendantUrls.mapNotNull { rowsByUrl[canonicalRepoUrl(it)] } + root)
            .distinctBy { it.id }
        rowsToRemove.forEach { row ->
            pluginDao.ofRepo(row.id).forEach { store.delete(it.id) }
            addonDao.delete(row.id)
            repoCatalog?.remove(row.sourceUrl)
        }
    }

    // ---------------------------------------------------------------------------
    // Açık/kapalı
    // ---------------------------------------------------------------------------

    suspend fun setEnabled(pluginId: Long, enabled: Boolean) {
        pluginDao.setEnabled(pluginId, enabled)
        if (enabled) {
            val row = pluginDao.byId(pluginId)
            val plugin = row?.toInstalled()
            val indexUrls = plugin?.let { megaRepoIndexUrlsFor(it.pluginId, it.name, it.description) }.orEmpty()
            val ownerUrl = row?.let { addonDao.byId(it.repoId)?.sourceUrl }
            if (indexUrls.isNotEmpty() && ownerUrl != null) discoverMegaRepositories(indexUrls, ownerUrl)
        }
    }

    suspend fun setRepoEnabled(repoId: Long, enabled: Boolean) {
        pluginDao.setEnabledForRepo(repoId, enabled)
        if (enabled) refreshMegaRepositories()
    }

    suspend fun setAllEnabled(enabled: Boolean) {
        pluginDao.setAllEnabledOfType(AddonType.CLOUDSTREAM_REPO, enabled)
        if (enabled) refreshMegaRepositories()
    }

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

    /** App startup/repo enable sonrası, daha önce kurulmuş Mega loader varsa alt depoları keşfeder ve kurar. */
    suspend fun refreshMegaRepositories() {
        val repoUrls = addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first().associate { it.id to it.sourceUrl }
        val indexesByOwner = pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first()
            .mapNotNull { row ->
                val owner = repoUrls[row.repoId] ?: return@mapNotNull null
                owner to megaRepoIndexUrlsFor(row.pluginId, row.name, row.description)
            }
            .groupBy({ it.first }, { it.second })
        indexesByOwner.forEach { (owner, indexGroups) ->
            val indexes = indexGroups.flatten().distinct()
            if (indexes.isNotEmpty()) discoverMegaRepositories(indexes, owner)
        }
    }

    suspend fun add(url: String): CsRepoAdded = addFetched(url, fetcher.fetch(url))

    // ---------------------------------------------------------------------------
    // İç yardımcılar
    // ---------------------------------------------------------------------------

    private suspend fun fetchAllPluginLists(
        listUrls: List<String>,
        baseUrl: String,
        nestedRepositories: MutableList<CsRepoReference>,
    ): List<CsPluginEntry> {
        val visited = linkedSetOf<String>()

        suspend fun visit(rawUrl: String, parentUrl: String, depth: Int): List<CsPluginEntry> {
            if (depth > MAX_NESTED_REPO_DEPTH || visited.size >= MAX_NESTED_REPO_FILES) return emptyList()
            val resolvedUrl = rawUrl.toHttpUrlOrNull()?.toString()
                ?: parentUrl.toHttpUrlOrNull()?.resolve(rawUrl)?.toString()
                ?: return emptyList()
            if (!visited.add(resolvedUrl)) return emptyList()
            return try {
                val raw = fetcher.fetch(resolvedUrl)
                val entries = CsRepoManifestParser.parsePluginList(raw, resolvedUrl)
                if (entries.isNotEmpty()) return entries

                // Bazı depolar pluginLists içine doğrudan plugins.json yerine başka repo.json koyuyor.
                val nestedRoot = runCatching { CsRepoManifestParser.parseRoot(raw, resolvedUrl) }.getOrNull()
                    ?: run {
                        Log.d(TAG, "fetchAllPluginLists: boş/çözümlenemedi host=${resolvedUrl.toHttpUrlOrNull()?.host} bytes=${raw.length}")
                        return emptyList()
                    }
                nestedRepositories += nestedRoot.subRepositories.map { it.copy(discoveredFrom = listOf(baseUrl)) }
                nestedRoot.pluginLists.flatMap { child -> visit(child, resolvedUrl, depth + 1) }
            } catch (e: Exception) {
                Log.d(TAG, "fetchAllPluginLists: indirilemedi host=${resolvedUrl.toHttpUrlOrNull()?.host} ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
        }

        return listUrls.flatMap { visit(it, baseUrl, 0) }
            .distinctBy { it.internalName }
            .take(CsRepoManifestParser.MAX_PLUGINS)
    }

    private suspend fun discoverMegaRepositories(indexUrls: Collection<String>, ownerUrl: String) {
        synchronized(pendingMegaIndexLock) {
            if (ownerUrl.isNotBlank()) {
                indexUrls.forEach { indexUrl -> pendingMegaIndexUrls.getOrPut(indexUrl) { linkedSetOf() }.add(ownerUrl) }
            }
        }
        if (!megaInstallMutex.tryLock()) return
        try {
            while (true) {
                val batch = synchronized(pendingMegaIndexLock) {
                    pendingMegaIndexUrls.mapValues { it.value.toList() }.also { pendingMegaIndexUrls.clear() }
                }
                if (batch.isEmpty()) break
                val found = mutableListOf<CsRepoReference>()
                for ((indexUrl, owners) in batch) {
                    try {
                        val raw = fetcher.fetch(indexUrl)
                        found += CsRepoManifestParser.parseRepoIndex(raw, indexUrl).map { reference ->
                            reference.copy(discoveredFrom = (reference.discoveredFrom + owners).distinct())
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Mega alt depo kataloğu alınamadı: $indexUrl (${e.javaClass.simpleName}: ${e.message})")
                    }
                }
                installRepositories(mergeReferences(found))
            }
        } finally {
            megaInstallMutex.unlock()
        }
        val workArrivedDuringUnlock = synchronized(pendingMegaIndexLock) { pendingMegaIndexUrls.isNotEmpty() }
        if (workArrivedDuringUnlock) discoverMegaRepositories(emptyList(), "")
    }

    private suspend fun installRepositories(references: List<CsRepoReference>) {
        if (references.isEmpty()) return
        val unique = mergeReferences(references)
        repoCatalog?.merge(unique)
        var installed = 0
        var alreadyInstalled = 0
        var failed = 0
        for (item in unique) {
            try {
                if (addonDao.countBySourceUrl(item.url) > 0) {
                    alreadyInstalled++
                    repoCatalog?.remove(item.url)
                    continue
                }
                add(item.url)
                installed++
                repoCatalog?.remove(item.url)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                Log.w(TAG, "Mega alt repo kurulamadı: ${item.name.ifBlank { item.url }} (${e.javaClass.simpleName}: ${e.message})")
            }
        }
        Log.i(TAG, "Mega alt depoları: keşfedilen=${unique.size}, kurulan=$installed, zaten kurulu=$alreadyInstalled, başarısız=$failed")
    }

    private fun mergeReferences(references: List<CsRepoReference>): List<CsRepoReference> =
        references.groupBy { canonicalRepoUrl(it.url) }.values.map { duplicates ->
            val first = duplicates.first()
            first.copy(discoveredFrom = duplicates.flatMap { it.discoveredFrom }.distinct())
        }

    private fun canonicalRepoUrl(url: String): String = url.trim().removeSuffix("/").lowercase()

    private fun CsPluginEntry.isMegaRepositoryLoader(): Boolean =
        isMegaRepoLoaderIdentity(internalName, name, description)

    private fun InstalledCsPlugin.isMegaRepositoryLoader(): Boolean =
        isMegaRepoLoaderIdentity(pluginId, name, description)

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

    private companion object {
        const val MAX_NESTED_REPO_DEPTH = 4
        const val MAX_NESTED_REPO_FILES = 64
    }
}

/** Mega loaders use different names and descriptions; match the loader identity, not one exact English phrase. */
internal fun isMegaRepoLoaderIdentity(pluginId: String, name: String, description: String?): Boolean {
    if (pluginId.contains("megaprovider", ignoreCase = true)) return true
    if (pluginId.contains("mega", ignoreCase = true) || name.contains("mega", ignoreCase = true)) return true
    val descriptionText = description.orEmpty().lowercase()
    return listOf("all repositories", "all repos", "add all repositories", "add all repos")
        .any(descriptionText::contains)
}

internal fun isWioMegaRepoLoaderIdentity(pluginId: String, name: String, description: String?): Boolean =
    listOf(pluginId, name, description.orEmpty()).any { it.contains("megawio", ignoreCase = true) }

internal fun megaRepoIndexUrlsFor(pluginId: String, name: String, description: String?): List<String> {
    if (!isMegaRepoLoaderIdentity(pluginId, name, description)) return emptyList()
    return if (isWioMegaRepoLoaderIdentity(pluginId, name, description)) {
        listOf(WIO_MEGA_REPOSITORY_INDEX_URL)
    } else {
        listOf(CLOUDSTREAM_MEGA_REPOSITORY_INDEX_URL)
    }
}
