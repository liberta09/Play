package com.kaan.play.data.cloudstream

import android.util.Log
import android.content.Context
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

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
    private val ownershipStore: SubRepoOwnershipStore? = null,
    private val onSubReposDiscovered: ((parentUrl: String, subRepos: List<PluginRepoItem>) -> Unit)? = null,
) {

    private val runtimeLock = Mutex()
    private val runtime = CsPluginRuntimeCache()

    val repos: Flow<List<CsRepo>> = combine(
        addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
        pluginDao.observeAll(),
    ) { rows, plugins ->
        val counts = plugins.groupingBy { it.repoId }.eachCount()
        rows.map { it.toCsRepo(counts[it.id] ?: 0) }
    }

    val plugins: Flow<List<InstalledCsPlugin>> =
        pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO)
            // UI emissions can be queued snapshots; they must never retire a newer runtime handle.
            .map { rows -> rows.map { it.toInstalled() } }

    // ---------------------------------------------------------------------------
    // Ekle
    // ---------------------------------------------------------------------------

    /**
     * Zaten indirilmiş [raw] repo.json içeriğinden depoyu kurar.
     * Her CS eklentisi indirilip DEX ayıklanır; başarısızlar atlanır.
     * @throws AddonException
     */
    suspend fun addFetched(url: String, raw: String, recordManual: Boolean = true): CsRepoAdded {
        if (addonDao.countBySourceUrl(url) > 0) throw AddonException(Kind.AlreadyAdded)
        val root = CsRepoManifestParser.parseRoot(raw, baseUrl = url)

        // pluginLists adreslerinden eklenti listelerini (ve varsa gömülü alt depo manifestlerini) paralel indir
        val listsResult = fetchAllPluginLists(root.pluginLists, url)
        val allEntries = listsResult.entries
        val allSubRepos = (root.subRepos + listsResult.subRepos)
            .distinctBy { PluginRepoRequestRules.normalizeUrl(it.url) }
            .filter { PluginRepoRequestRules.normalizeUrl(it.url) != PluginRepoRequestRules.normalizeUrl(url) }
        if (allEntries.isEmpty() && allSubRepos.isEmpty()) throw AddonException(Kind.NoPlugins)

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
        val downloads = if (allEntries.isNotEmpty()) downloadAll(allEntries, repoId) else emptyList()
        val good = downloads.filter { it.success }
        val reasonCounts = downloads.filter { !it.success }.groupingBy { it.reason ?: "?" }.eachCount()
        if (reasonCounts.isNotEmpty()) {
            Log.d(TAG, "addFetched: atlanan eklentiler $reasonCounts (toplam ${downloads.size}, başarılı ${good.size})")
        }

        if (good.isEmpty() && allSubRepos.isEmpty()) {
            addonDao.delete(repoId)
            val first = downloads.firstOrNull()
            val detail = first?.let { "${it.entry.name}::${it.reason.orEmpty()}" }
            Log.d(TAG, "addFetched: tüm eklentiler başarısız (${downloads.size} denendi)")
            throw AddonException(Kind.PluginFileFailed, detail)
        }

        if (recordManual) {
            ownershipStore?.recordManual(url)
        }
        if (allSubRepos.isNotEmpty()) {
            onSubReposDiscovered?.invoke(url, allSubRepos)
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
    suspend fun addDirectCs3(url: String, recordManual: Boolean = true): CsRepoAdded {
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
        if (recordManual) {
            ownershipStore?.recordManual(url)
        }

        return CsRepoAdded(
            repo = repoEntity.copy(id = repoId).toCsRepo(1),
            installed = 1,
            skipped = 0,
        )
    }

    // ---------------------------------------------------------------------------
    // Kaldır
    // ---------------------------------------------------------------------------

    /** Bu depo kaldırılırken güvenle kaldırılabilecek alt depolar (ortak/elle eklenen hariç). */
    suspend fun removableSubRepos(parentRepo: CsRepo): List<CsRepo> {
        val ownership = ownershipStore ?: return emptyList()
        val allEntities = addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first()
        val removableNorm = ownership.removableChildren(parentRepo.sourceUrl, allEntities.map { it.sourceUrl })
        if (removableNorm.isEmpty()) return emptyList()
        return allEntities
            .filter { PluginRepoRequestRules.normalizeUrl(it.sourceUrl) in removableNorm }
            .map { it.toCsRepo(pluginDao.ofRepo(it.id).size) }
    }

    // Çalışan eklenti örnekleri silmeden önce kilit altında geçersiz kılınır.
    suspend fun remove(repoId: Long, cascadeSubRepos: Boolean = false): Unit = runtimeLock.withLock {
        val targetEntity = addonDao.byId(repoId)
        val childEntities = if (cascadeSubRepos && targetEntity != null && ownershipStore != null) {
            val allEntities = addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first()
            val removableNorm = ownershipStore.removableChildren(targetEntity.sourceUrl, allEntities.map { it.sourceUrl })
            allEntities.filter { PluginRepoRequestRules.normalizeUrl(it.sourceUrl) in removableNorm && it.id != repoId }
        } else {
            emptyList()
        }

        for (child in childEntities) {
            pluginDao.ofRepo(child.id).forEach {
                runtime.invalidate(it.id)
                store.delete(it.id)
            }
            addonDao.delete(child.id)
        }
        pluginDao.ofRepo(repoId).forEach {
            runtime.invalidate(it.id)
            store.delete(it.id)
        }
        addonDao.delete(repoId)

        if (targetEntity != null) {
            ownershipStore?.onRepoRemoved(
                removedRepoUrl = targetEntity.sourceUrl,
                deletedChildUrls = childEntities.mapTo(HashSet()) { it.sourceUrl },
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Açık/kapalı
    // ---------------------------------------------------------------------------

    suspend fun setEnabled(pluginId: Long, enabled: Boolean) = runtimeLock.withLock {
        pluginDao.setEnabled(pluginId, enabled)
        if (!enabled) runtime.invalidate(pluginId)
    }
    suspend fun setRepoEnabled(repoId: Long, enabled: Boolean) = runtimeLock.withLock {
        pluginDao.setEnabledForRepo(repoId, enabled)
        if (!enabled) pluginDao.ofRepo(repoId).forEach { runtime.invalidate(it.id) }
    }
    suspend fun setAllEnabled(enabled: Boolean) = runtimeLock.withLock {
        pluginDao.setAllEnabledOfType(AddonType.CLOUDSTREAM_REPO, enabled)
        if (!enabled) runtime.clear()
    }

    // ---------------------------------------------------------------------------
    // Çalışma zamanı
    // ---------------------------------------------------------------------------

    /**
     * Açık eklentiler için [MainAPI] listesini döner.
     * Aynı kurulu DEX için API ve ayar callback'i birlikte korunur; değişim eski örneği bırakır.
     */
    suspend fun loadApis(pluginRowId: Long, allowRedownload: Boolean = true): CsApiResult =
        runtimeLock.withLock { loadApisLocked(pluginRowId, allowRedownload) }

    /**
     * [pluginRowId]'nin API'leriyle [block]'u çalıştırır. [release] açıksa ve eklenti bu çağrıdan önce yüklü değilse, iş bitince
     * (iptalde de) bırakılır: kodu ve kayıtlı çıkarıcıları bellekten çıkar, sonraki [loadApis] yeniden yükler. [keep] sonuca bakıp
     * eklentiyi yerinde tutabilir (ör. kaynak bulduysa: oynatıcı onun bağlantı yakalayıcısını kullanır). Önceden yüklü eklenti
     * (ana sayfa, detay) hiç bırakılmaz. Az bellekli cihazda kaynak araması için ([CsSearchBudget.releaseAfterSearch]).
     *
     * Runs [block] with the plugin's APIs; with [release], a plugin that was not loaded before this call is unloaded afterwards
     * (also on cancellation) unless [keep] says otherwise. Plugins loaded earlier stay loaded.
     */
    suspend fun <T> useApis(
        pluginRowId: Long,
        release: Boolean,
        keep: (T) -> Boolean = { false },
        block: suspend (CsApiResult) -> T,
    ): T {
        var loadedHere = false
        val result = runtimeLock.withLock {
            loadedHere = !runtime.contains(pluginRowId)
            loadApisLocked(pluginRowId)
        }
        val success = result as? CsApiResult.Success
        if (!release || !loadedHere || success == null) return block(result)
        var kept = false
        try {
            return block(result).also { kept = keep(it) }
        } finally {
            if (!kept) withContext(NonCancellable) { runtimeLock.withLock { runtime.releaseIfSame(pluginRowId, success) } }
        }
    }

    private suspend fun loadApisLocked(pluginRowId: Long, allowRedownload: Boolean = true): CsApiResult {
        val row = pluginDao.byId(pluginRowId) ?: run {
            runtime.invalidate(pluginRowId)
            val failure = CsApiResult.PluginRowMissing(pluginRowId)
            Log.w(TAG, formatCsLog(failure))
            return failure
        }
        if (!row.enabled) {
            runtime.invalidate(pluginRowId)
            return CsApiResult.LoadFailed("PluginDisabled", row.pluginId)
        }
        if (row.pluginId.contains("traktsync", ignoreCase = true) ||
            row.name.contains("traktsync", ignoreCase = true) ||
            row.pluginId.contains("pltmustafa", ignoreCase = true)
        ) {
            runtime.invalidate(pluginRowId)
            Log.w(TAG, "Trakt eklentisi yuklenmedi (devre disi): ${row.pluginId}")
            return CsApiResult.LoadFailed("TraktSyncDisabled", row.pluginId)
        }
        val dexFile = store.dexFile(pluginRowId)
        if (!dexFile.exists()) {
            runtime.invalidate(pluginRowId)
            if (allowRedownload) {
                Log.w(TAG, "${row.pluginId}: DexFileMissing (tek seferlik yeniden indirme deneniyor)")
                val downloaded = try {
                    cs3Downloader.download(row.scriptUrl, row.sha256, pluginRowId)
                    dexFile.exists()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    false
                }
                if (downloaded) {
                    return loadApisLocked(pluginRowId, allowRedownload = false)
                }
            }
            val failure = CsApiResult.DexFileMissing(row.pluginId)
            Log.w(TAG, formatCsLog(failure))
            return failure
        }
        val identity = runtimeIdentity(row)
        runtime.get(identity)?.let { return it }
        val className = resolveMainClassName(identity.manifest, row.pluginId)
        var result: CsApiResult? = null
        try {
            currentCoroutineContext().ensureActive()
            result = loader.loadPlugin(dexFile, className, row.pluginId)
            currentCoroutineContext().ensureActive()
            val current = pluginDao.byId(pluginRowId)
            if (current == null || !current.enabled || !matchesRow(identity, current) || runtimeIdentity(current) != identity) {
                (result as? CsApiResult.Success)?.plugin?.unload()
                return CsApiResult.LoadFailed("PluginChangedDuringLoad", row.pluginId)
            }
            if (result is CsApiResult.Success) runtime.put(identity, result)
            if (result is CsApiResult.Failure) Log.w(TAG, formatCsLog(result))
            return result
        } catch (cancelled: CancellationException) {
            (result as? CsApiResult.Success)?.plugin?.unload()
            throw cancelled
        } catch (error: Throwable) {
            (result as? CsApiResult.Success)?.plugin?.unload()
            val failure = classifyDexError(error, row.pluginId)
            Log.w(TAG, formatCsLog(failure))
            return failure
        }
    }

    /** Explicit lazy UI action: do not execute every plugin just to discover settings. */
    suspend fun openSettings(pluginRowId: Long, context: Context): CsPluginSettingsResult {
        try {
            if (withContext(Dispatchers.Main.immediate) { settingsActivity(context) } == null) {
                return CsPluginSettingsResult.Unavailable
            }
            return withContext(Dispatchers.IO) {
                runtimeLock.withLock {
                    val row = pluginDao.byId(pluginRowId) ?: return@withLock CsPluginSettingsResult.Unavailable
                    if (!isCsRow(row)) {
                        runtime.invalidate(pluginRowId)
                        return@withLock CsPluginSettingsResult.Unavailable
                    }
                    if (!row.enabled) {
                        runtime.invalidate(pluginRowId)
                        return@withLock CsPluginSettingsResult.Disabled
                    }
                    val result = loadApisLocked(pluginRowId)
                    if (result !is CsApiResult.Success) {
                        return@withLock CsPluginSettingsResult.Failed(
                            when (result) {
                                is CsApiResult.IncompatibleLibrary -> result.causeClass
                                is CsApiResult.LoadFailed -> result.causeClass
                                else -> result.javaClass.simpleName
                            }
                        )
                    }
                    val identity = runtimeIdentity(row)
                    withContext(Dispatchers.Main.immediate) settings@{
                        currentCoroutineContext().ensureActive()
                        // DAO work can suspend: recheck type, enabled state and file identity immediately before invoking.
                        val current = pluginDao.byId(pluginRowId)
                        val eligible = current != null && current.enabled && isCsRow(current) && matchesRow(identity, current)
                        val unchanged = eligible && withContext(Dispatchers.IO) { runtimeIdentity(current!!) == identity }
                        if (!unchanged || runtime.get(identity) !== result) {
                            runtime.invalidate(pluginRowId)
                            return@settings CsPluginSettingsResult.Unavailable
                        }
                        currentCoroutineContext().ensureActive()
                        if (settingsActivity(context) == null) return@settings CsPluginSettingsResult.Unavailable
                        if (result.plugin?.openSettings(context) == true) CsPluginSettingsResult.Opened
                        else CsPluginSettingsResult.NotSupported
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w(TAG, "Plugin settings failed: ${error.javaClass.simpleName}")
            return CsPluginSettingsResult.Failed(error.javaClass.simpleName)
        }
    }

    private suspend fun isCsRow(row: PluginEntity): Boolean =
        addonDao.byId(row.repoId)?.let { it.type == AddonType.CLOUDSTREAM_REPO && it.enabled } == true

    private fun matchesRow(key: CsRuntimeIdentity, row: PluginEntity): Boolean =
        key.rowId == row.id && key.repoId == row.repoId && key.pluginId == row.pluginId && key.version == row.version && key.artifactHash == row.sha256

    private fun runtimeIdentity(row: PluginEntity): CsRuntimeIdentity {
        val dex = store.dexFile(row.id)
        val attributes = runCatching {
            Files.readAttributes(dex.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        }.getOrNull()
        return CsRuntimeIdentity(row.id, row.repoId, row.pluginId, row.version, row.sha256, dex.absolutePath,
            dex.length(), dex.lastModified(), attributes?.fileKey()?.toString(), store.readManifest(row.id))
    }

    /**
     * Kurulu depoya ([repoId], adresi [repoUrl]) depoda olup burada kurulu olmayan [internalNames] eklentilerini indirip
     * kurar (Wi-Fi eşitlemesi: karşı cihazda açık olanlar; depoya sonradan eklenenler dahil). Kurulan sayıyı döndürür.
     */
    suspend fun installMissing(repoId: Long, repoUrl: String, internalNames: Set<String>): Int {
        val have = pluginDao.ofRepo(repoId).map { it.pluginId }.toSet()
        val wanted = internalNames - have
        if (wanted.isEmpty()) return 0
        val root = CsRepoManifestParser.parseRoot(fetcher.fetch(repoUrl))
        val entries = fetchAllPluginLists(root.pluginLists, repoUrl).entries.filter { it.internalName in wanted }
        return downloadAll(entries, repoId).count { it.success }
    }

    suspend fun enabledPlugins(): List<InstalledCsPlugin> =
        pluginDao.enabledOfType(AddonType.CLOUDSTREAM_REPO).map { it.toInstalled() }

    // ---------------------------------------------------------------------------
    // İç yardımcılar
    // ---------------------------------------------------------------------------

    private class PluginListsResult(
        val entries: List<CsPluginEntry>,
        val subRepos: List<PluginRepoItem>,
    )

    private suspend fun fetchAllPluginLists(listUrls: List<String>, baseUrl: String): PluginListsResult {
        val entries = mutableListOf<CsPluginEntry>()
        val discoveredSubRepos = mutableListOf<PluginRepoItem>()
        for (listUrl in listUrls) {
            try {
                val raw = fetcher.fetch(listUrl)
                val parsed = CsRepoManifestParser.parsePluginList(raw, listUrl)
                if (parsed.isNotEmpty()) {
                    entries += parsed
                } else {
                    val nested = runCatching { CsRepoManifestParser.parseRoot(raw, baseUrl = listUrl) }.getOrNull()
                    if (nested != null) {
                        discoveredSubRepos += PluginRepoItem(name = nested.name, url = listUrl)
                        discoveredSubRepos += nested.subRepos
                    } else {
                        Log.d(TAG, "fetchAllPluginLists: boş/çözümlenemedi host=${listUrl.toHttpUrlOrNull()?.host} bytes=${raw.length}")
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "fetchAllPluginLists: indirilemedi host=${listUrl.toHttpUrlOrNull()?.host} ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return PluginListsResult(
            entries = entries.distinctBy { it.internalName }.take(CsRepoManifestParser.MAX_PLUGINS),
            subRepos = discoveredSubRepos.distinctBy { PluginRepoRequestRules.normalizeUrl(it.url) },
        )
    }

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
