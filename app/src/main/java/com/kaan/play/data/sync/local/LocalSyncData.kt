package com.kaan.play.data.sync.local

import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.plugin.PluginSettingsRepository
import com.kaan.play.data.plugin.SourceInstaller
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileRepository
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.LocalSyncBackup
import com.kaan.play.data.sync.PluginStateTransfer
import com.kaan.play.data.sync.SourceTransferItem
import com.kaan.play.data.web.WebSiteRepository
import kotlinx.coroutines.flow.first

/**
 * Wi-Fi senkronizasyonunun uygulama verisiyle bağlantısı: bu cihazın anlık görüntüsünü çıkarır ([export]) ve
 * [LocalSyncMerge] planını yazar ([apply]). Eklenti **dosyaları** kopyalanmaz: onaylanan kaynak adresleri mevcut
 * [SourceInstaller] ile bu cihazda indirilir, sonra açık/kapalı durumları, ayarlar ve katalog anahtarları gönderenle
 * aynı yapılır ([install]).
 */
class LocalSyncData(
    private val profiles: LocalProfileRepository,
    private val backups: LocalSyncBackup,
    private val history: WatchHistoryDao,
    private val favorites: FavoriteDao,
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val pluginSettings: PluginSettingsRepository,
    private val settings: SettingsRepository,
    private val installer: SourceInstaller,
    private val webSites: WebSiteRepository,
    private val deviceName: String,
) {

    private suspend fun userId(): String = profiles.userId.first().ifEmpty { "legacy-user" }

    suspend fun export(): LocalProfileTransfer {
        profiles.ensureId()
        val uId = userId()
        val all = profiles.allProfiles()
        val libs = all.associate { p ->
            val backup = LocalProfileBackup(
                profileId = p.id,
                profileName = p.name,
                favorites = favorites.all(uId, p.id).map {
                    FavoriteBackup(it.tmdbId, it.type.name.lowercase(), it.title, it.posterPath, it.addedAt)
                },
                history = history.all(uId, p.id).map {
                    HistoryBackup(
                        tmdbId = it.tmdbId,
                        type = it.type.name.lowercase(),
                        season = it.season,
                        episode = it.episode,
                        positionMs = it.positionMs,
                        durationMs = it.durationMs,
                        lastWatchedAt = it.lastWatchedAt,
                        title = it.title,
                        posterPath = it.posterPath,
                        pluginId = it.pluginId,
                        dataUrl = it.dataUrl,
                        contentUrl = it.contentUrl,
                    )
                },
            )
            p.id to LocalSyncMerge.encodeBackup(backup)
        }
        return LocalProfileTransfer(
            profiles = all.map(LocalSyncMerge::forTransfer),
            backups = libs,
            sources = sources(),
            deviceName = deviceName,
        )
    }

    /** Tüm kaynaklar: Nuvio addon'ları (altyazı addon'ları dahil), Nuvio eklenti depoları, CloudStream depoları, web siteleri. */
    private suspend fun sources(): List<SourceTransferItem> {
        val disabled = settings.disabledCatalogs.first()
        return SYNCED_TYPES.flatMap { type -> addonDao.observeOfType(type).first() }.map { row ->
            val prefix = "${row.id}:"
            SourceTransferItem(
                type = row.type.name,
                sourceUrl = row.sourceUrl,
                enabled = row.enabled,
                name = row.name,
                plugins = pluginDao.ofRepo(row.id).map { p ->
                    PluginStateTransfer(p.pluginId, p.enabled, if (p.hasSettings) pluginSettings.get(p.id) else emptyMap())
                },
                disabledCatalogs = disabled.filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.sorted(),
            )
        }
    }

    /** Planı yazar: profiller, geçmiş + favoriler, (karşı esas ise) ortak kaynakların durumları. */
    suspend fun apply(plan: LocalSyncPlan) {
        profiles.replaceProfilesKeepingActive(plan.profiles)
        val uId = userId()
        for ((profileId, library) in plan.libraries) {
            backups.mergeJsonFor(uId, profileId, LocalSyncMerge.encodeBackup(library)).getOrThrow()
        }
        for (item in plan.sourceStates) {
            rowFor(item.sourceUrl)?.let { applyState(it, item) }
        }
    }

    /** Onaylanan eksik kaynağı bu cihazda kurar ve durumlarını gönderenle aynı yapar. Başarısızsa false. */
    suspend fun install(item: SourceTransferItem): Boolean {
        val ok = try {
            if (item.type == AddonType.WEB_SITE.name) {
                webSites.add(item.name, item.sourceUrl)
            } else {
                installer.add(item.sourceUrl)
            }
            true
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
        val row = rowFor(item.sourceUrl) ?: return ok
        applyState(row, item)
        return true
    }

    /** Bu cihazdaki sayılar (sonuç satırı için). */
    suspend fun counts(): Counts {
        val uId = userId()
        val all = profiles.allProfiles()
        return Counts(
            profiles = all.size,
            history = all.sumOf { history.all(uId, it.id).size },
            addons = addonDao.observeOfType(AddonType.NUVIO_ADDON).first().size,
            repos = addonDao.observeOfType(AddonType.NUVIO_PLUGIN_REPO).first().size +
                addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first().size,
        )
    }

    data class Counts(val profiles: Int, val history: Int, val addons: Int, val repos: Int)

    private suspend fun rowFor(url: String): AddonEntity? {
        val key = LocalSyncMerge.urlKey(url)
        return SYNCED_TYPES.flatMap { addonDao.observeOfType(it).first() }.firstOrNull { LocalSyncMerge.urlKey(it.sourceUrl) == key }
    }

    /** Açık/kapalı, eklenti başına açık/kapalı + ayarlar, katalog anahtarları gönderenle aynı yapılır. */
    private suspend fun applyState(row: AddonEntity, item: SourceTransferItem) {
        if (row.enabled != item.enabled) addonDao.setEnabled(row.id, item.enabled)
        val wanted = item.plugins.associateBy { it.pluginId }
        for (plugin in pluginDao.ofRepo(row.id)) {
            val state = wanted[plugin.pluginId] ?: continue
            if (plugin.enabled != state.enabled) pluginDao.setEnabled(plugin.id, state.enabled)
            if (plugin.hasSettings) {
                val current = pluginSettings.get(plugin.id)
                if (current != state.settings) {
                    pluginSettings.clear(plugin.id)
                    for ((k, v) in state.settings) pluginSettings.set(plugin.id, k, v)
                }
            }
        }
        if (row.type == AddonType.NUVIO_ADDON) {
            val prefix = "${row.id}:"
            val current = settings.disabledCatalogs.first().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }.toSet()
            val target = item.disabledCatalogs.toSet()
            for (key in current - target) setCatalog(row.id, key, enabled = true)
            for (key in target - current) setCatalog(row.id, key, enabled = false)
        }
    }

    /** [key] = `tür:katalogKimliği` (katalog kimliğinde ":" olabilir; tür olamaz). */
    private suspend fun setCatalog(addonId: Long, key: String, enabled: Boolean) {
        val type = key.substringBefore(':', missingDelimiterValue = "")
        val catalogId = key.substringAfter(':', missingDelimiterValue = "")
        if (type.isEmpty() || catalogId.isEmpty()) return
        settings.setCatalogEnabled(addonId, type, catalogId, enabled)
    }

    private companion object {
        val SYNCED_TYPES = listOf(AddonType.NUVIO_ADDON, AddonType.NUVIO_PLUGIN_REPO, AddonType.CLOUDSTREAM_REPO, AddonType.WEB_SITE)
    }
}
