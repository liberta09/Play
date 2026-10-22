package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.PluginEntity
import com.kaan.play.data.net.PlayJson
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
import java.security.MessageDigest

/** Kurulu bir eklenti deposu (JS kazıyıcı deposu). */
data class PluginRepo(
    val id: Long,
    val sourceUrl: String,
    val name: String,
    val version: String?,
    val description: String?,
    val addedAt: Long,
    val pluginCount: Int,
)

/** Depodaki tek bir kazıyıcı. [enabled] varsayılan KAPALIDIR. */
data class InstalledPlugin(
    val id: Long,
    val repoId: Long,
    val pluginId: String,
    val name: String,
    val version: String?,
    val description: String?,
    /** Normalleştirilmiş türler (`movie`, `tv`). */
    val supportedTypes: Set<String>,
    /** Ham `supportedTypes` (`series` ayrımı için). */
    val rawTypes: List<String>,
    val contentLanguage: List<String>,
    val hasSettings: Boolean,
    val enabled: Boolean,
)

data class PluginRepoAdded(val repo: PluginRepo, val installed: Int, val skipped: Int)

/** Güncelleme denetiminin sonucu; [isEmpty] ise depo günceldir. */
data class UpdatePlan(
    val repoId: Long,
    internal val manifest: PluginRepoManifest,
    val changed: List<String>,
    val added: List<String>,
    val removed: List<String>,
    internal val changedEntries: List<PluginScraperEntry>,
    internal val addedEntries: List<PluginScraperEntry>,
    internal val removedIds: List<Long>,
) {
    val count: Int get() = changed.size + added.size + removed.size
    val isEmpty: Boolean get() = count == 0
}

data class UpdateResult(val updated: Int, val skipped: Int)

/**
 * Eklenti depolarının tek kapısı (Faz 2). Depo satırı `addons` tablosunda ([AddonType.NUVIO_PLUGIN_REPO]), kazıyıcılar
 * `plugins` tablosunda, kodları uygulamaya özel dizinde ([PluginCodeStore]) durur. Hiçbir varsayılan depo yoktur; her şey
 * kullanıcının girdiği adresten gelir. Yeni eklenen depolarda kazıyıcılar **açık** kurulur (Kullanıcı kararı 2026-10-23).
 */
class PluginRepository(
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val fetcher: ManifestFetcher,
    private val codeFetcher: PluginCodeFetcher,
    private val store: PluginCodeStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val parallelDownloads: Int = 4,
    /** Bir kazıyıcı silinince çağrılır (kayıtlı ayarlarını temizlemek için). */
    private val onPluginRemoved: suspend (Long) -> Unit = {},
) {

    val repos: Flow<List<PluginRepo>> = combine(
        addonDao.observeOfType(AddonType.NUVIO_PLUGIN_REPO),
        pluginDao.observeAll(),
    ) { rows, plugins ->
        val counts = plugins.groupingBy { it.repoId }.eachCount()
        rows.map { it.toRepo(counts[it.id] ?: 0) }
    }

    val plugins: Flow<List<InstalledPlugin>> = pluginDao.observeAll().map { rows -> rows.map { it.toInstalled() } }

    /**
     * Zaten indirilmiş [raw] manifest'ten depoyu kurar: kazıyıcı kodları indirilir, doğrulanır, kaydedilir. Tek tek
     * başarısız olan dosyalar atlanır; hiçbiri kurulamazsa [Kind.PluginFileFailed]. Hata durumunda hiçbir şey kalmaz.
     * @throws AddonException
     */
    suspend fun addFetched(url: String, raw: String): PluginRepoAdded {
        if (addonDao.countBySourceUrl(url) > 0) throw AddonException(Kind.AlreadyAdded)
        val manifest = PluginRepoManifestParser.parse(raw, url)

        val downloads = downloadAll(manifest.scrapers, url)
        val good = downloads.filter { it.code != null }
        if (good.isEmpty()) throw AddonException(Kind.PluginFileFailed, downloads.firstOrNull()?.entry?.name)

        val repoEntity = AddonEntity(
            type = AddonType.NUVIO_PLUGIN_REPO,
            sourceUrl = url,
            addonId = manifest.name,
            name = manifest.name,
            version = manifest.version,
            description = manifest.description,
            logoUrl = null,
            enabled = true,
            manifestJson = PlayJson.encodeToString(PluginRepoManifest.serializer(), manifest),
            addedAt = clock(),
        )
        val repoId = addonDao.insert(repoEntity)
        if (repoId == -1L) throw AddonException(Kind.AlreadyAdded)

        try {
            good.forEach { insertPlugin(repoId, it) }
        } catch (e: Exception) {
            // Yarım kalan kurulum bırakılmaz: dosyalar ve depo satırı (kazıyıcı satırları CASCADE ile) silinir.
            removeFiles(repoId)
            addonDao.delete(repoId)
            throw if (e is AddonException) e else AddonException(Kind.PluginFileFailed, cause = e)
        }
        return PluginRepoAdded(repoEntity.copy(id = repoId).toRepo(good.size), installed = good.size, skipped = downloads.size - good.size)
    }

    /** Deponun manifest'ini yeniden indirip yerel sürümlerle karşılaştırır; kodlar bu adımda indirilmez. */
    suspend fun checkForUpdates(repoId: Long): UpdatePlan {
        val repo = addonDao.byId(repoId) ?: throw AddonException(Kind.UnsupportedKind)
        val manifest = PluginRepoManifestParser.parse(fetcher.fetch(repo.sourceUrl), repo.sourceUrl)
        val existing = pluginDao.ofRepo(repoId).associateBy { it.pluginId }

        val changed = manifest.scrapers.filter { s ->
            val e = existing[s.id] ?: return@filter false
            VersionCompare.compare(e.version ?: "0", s.version) > 0
        }
        val added = manifest.scrapers.filter { it.id !in existing }
        val newIds = manifest.scrapers.mapTo(HashSet()) { it.id }
        val removed = existing.values.filter { it.pluginId !in newIds }
        return UpdatePlan(
            repoId = repoId,
            manifest = manifest,
            changed = changed.map { it.name },
            added = added.map { it.name },
            removed = removed.map { it.name },
            changedEntries = changed,
            addedEntries = added,
            removedIds = removed.map { it.id },
        )
    }

    /**
     * [checkForUpdates] planını uygular. Güncellenen kazıyıcıların açık/kapalı durumu korunur; yeniler açık gelir;
     * indirilemeyen dosyada eski kod kalır (atlanır). Depodan kalkanlar silinir.
     */
    suspend fun applyUpdate(plan: UpdatePlan): UpdateResult {
        val repo = addonDao.byId(plan.repoId) ?: throw AddonException(Kind.UnsupportedKind)
        val existing = pluginDao.ofRepo(plan.repoId).associateBy { it.pluginId }
        val downloads = downloadAll(plan.changedEntries + plan.addedEntries, repo.sourceUrl)
        var updated = 0

        downloads.filter { it.code != null }.forEach { d ->
            val row = existing[d.entry.id]
            if (row != null) {
                store.write(row.id, d.code!!)
                pluginDao.updateInfo(
                    id = row.id, name = d.entry.name, version = d.entry.version, description = d.entry.description,
                    filename = d.entry.filename, scriptUrl = d.entry.scriptUrl, supportedTypes = d.entry.supportedTypes.joinToString(","),
                    contentLanguage = d.entry.contentLanguage.joinToString(","), hasSettings = d.entry.hasSettings,
                    sha256 = d.sha256, sizeBytes = d.size,
                )
            } else {
                insertPlugin(plan.repoId, d)
            }
            updated++
        }
        plan.removedIds.forEach { id ->
            store.delete(id)
            pluginDao.delete(id)
            onPluginRemoved(id)
        }
        addonDao.updateInfo(
            id = plan.repoId, name = plan.manifest.name, version = plan.manifest.version, description = plan.manifest.description,
            manifestJson = PlayJson.encodeToString(PluginRepoManifest.serializer(), plan.manifest),
        )
        return UpdateResult(updated = updated + plan.removedIds.size, skipped = downloads.count { it.code == null })
    }

    suspend fun remove(repoId: Long) {
        pluginDao.ofRepo(repoId).forEach { onPluginRemoved(it.id) }
        removeFiles(repoId)
        addonDao.delete(repoId) // kazıyıcı satırları CASCADE ile silinir
    }

    suspend fun setPluginEnabled(id: Long, enabled: Boolean) = pluginDao.setEnabled(id, enabled)
    suspend fun setRepoEnabled(repoId: Long, enabled: Boolean) = pluginDao.setEnabledForRepo(repoId, enabled)
    suspend fun setAllEnabled(enabled: Boolean) = pluginDao.setAllEnabledOfType(AddonType.NUVIO_PLUGIN_REPO, enabled)

    /**
     * Açık JS kazıyıcılar (2. turda kaynak toplama bunları sorar). Yalnızca Nuvio depolarının satırları:
     * CloudStream eklentileri de `plugins` tablosunda durur ama onları [com.kaan.play.data.cloudstream.CsStreamProvider] çalıştırır.
     */
    suspend fun enabledPlugins(): List<InstalledPlugin> =
        pluginDao.enabledOfType(AddonType.NUVIO_PLUGIN_REPO).map { it.toInstalled() }

    /** Saklanan kod; dosya yoksa ya da özeti kayıttakiyle uyuşmuyorsa (bozulmuş/değişmiş) null. */
    suspend fun loadCode(pluginRowId: Long): String? {
        val row = pluginDao.byId(pluginRowId) ?: return null
        val code = store.read(pluginRowId) ?: return null
        return code.takeIf { row.sha256.isEmpty() || sha256(code) == row.sha256 }
    }

    // ---- iç ----

    private class Download(val entry: PluginScraperEntry, val code: String?, val sha256: String, val size: Long, val failure: Kind?)

    private suspend fun downloadAll(entries: List<PluginScraperEntry>, manifestUrl: String): List<Download> {
        val allowPrivateHost = manifestUrl.toHttpUrlOrNull()
            ?.takeIf { NetworkPolicy().checkUrl(it) == "private" }?.host
        val gate = Semaphore(parallelDownloads)
        return coroutineScope {
            entries.map { entry ->
                async(Dispatchers.IO) { gate.withPermit { download(entry, allowPrivateHost) } }
            }.awaitAll()
        }
    }

    private suspend fun download(entry: PluginScraperEntry, allowPrivateHost: String?): Download {
        var failure: Kind = Kind.Network
        repeat(2) { attempt ->
            try {
                val code = codeFetcher.fetch(entry.scriptUrl, allowPrivateHost)
                if (!looksLikePlugin(code)) return Download(entry, null, "", 0, Kind.NotJson)
                return Download(entry, code, sha256(code), code.toByteArray(Charsets.UTF_8).size.toLong(), null)
            } catch (e: AddonException) {
                failure = e.kind
                // Kalıcı hatalarda (4xx, çok büyük) yeniden denemenin anlamı yok; ağ/5xx bir kez daha denenir.
                if (e.kind == Kind.TooLarge || (e.kind == Kind.Http && e.detail?.startsWith("4") == true)) {
                    return Download(entry, null, "", 0, e.kind)
                }
                if (attempt == 1) return Download(entry, null, "", 0, failure)
            }
        }
        return Download(entry, null, "", 0, failure)
    }

    private suspend fun insertPlugin(repoId: Long, d: Download) {
        val e = d.entry
        val id = pluginDao.insert(
            PluginEntity(
                repoId = repoId, pluginId = e.id, name = e.name, version = e.version, description = e.description,
                filename = e.filename, scriptUrl = e.scriptUrl, supportedTypes = e.supportedTypes.joinToString(","),
                contentLanguage = e.contentLanguage.joinToString(","), hasSettings = e.hasSettings,
                sha256 = d.sha256, sizeBytes = d.size, enabled = RepoInstallPolicy.initialPluginEnabled(AddonType.NUVIO_PLUGIN_REPO), installedAt = clock(),
            ),
        )
        if (id == -1L) return // aynı kimlik zaten var (yinelenen giriş)
        store.write(id, d.code!!)
    }

    private suspend fun removeFiles(repoId: Long) {
        pluginDao.ofRepo(repoId).forEach { store.delete(it.id) }
    }

    private fun AddonEntity.toRepo(count: Int) = PluginRepo(id, sourceUrl, name, version, description, addedAt, count)

    private fun PluginEntity.toInstalled(): InstalledPlugin {
        val raw = supportedTypes.split(',').filter { it.isNotBlank() }
        return InstalledPlugin(
            id = id, repoId = repoId, pluginId = pluginId, name = name, version = version, description = description,
            supportedTypes = raw.map { PluginScraperEntry.normalizeType(it) }.toSet(), rawTypes = raw,
            contentLanguage = contentLanguage.split(',').filter { it.isNotBlank() }, hasSettings = hasSettings, enabled = enabled,
        )
    }

    companion object {
        /**
         * Bozuk dosya denetimi: boş değil, HTML hata sayfası değil, ve bir `getStreams` tanımı içeriyor (sözleşme
         * `docs/nuvio-plugin-format.md` §2.1). Kodun doğruluğunu garanti etmez; çalıştırma sırasında da hata yutulur.
         */
        internal fun looksLikePlugin(code: String): Boolean {
            val trimmed = code.trimStart()
            if (trimmed.isEmpty()) return false
            if (trimmed.startsWith("<")) return false // HTML (404/oturum sayfası)
            return "getStreams" in code
        }

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
