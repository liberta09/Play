package com.kaan.play.data.sync.local

import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.library.ProviderFavoriteRefs
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.plugin.PluginSettingsRepository
import com.kaan.play.data.plugin.SourceInstaller
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.FavoriteSourceTransfer
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
    /** Geçmişteki yanlış eklenti numaralarının onarımı (eşitlemeden önce ve sonra); null: onarım yok (testler). */
    private val pluginRepair: HistoryPluginRepair? = null,
    /**
     * Kurulu CS deposuna adı verilen eklentileri depodan indirip kurar (depo satırı, depo adresi, eklenti adları → kurulan
     * sayı); null: yapılmaz (testler). Yalnız [apply]'da `installPlugins = true` ile.
     */
    private val installCsPlugins: (suspend (repoId: Long, repoUrl: String, names: Set<String>) -> Int)? = null,
    /** Eklenti favorilerinin açılış bilgileri (yalnız Wi-Fi'de taşınır); null: taşınmaz (testler). */
    private val favoriteRefs: ProviderFavoriteRefs? = null,
) {

    private val migrator = ProfileDataMigrator(profiles, backups, history, favorites)

    private suspend fun userId(): String = profiles.userId.first().ifEmpty { "legacy-user" }

    suspend fun activeProfileId(): String = profiles.id.first()

    /**
     * Tüm profillerin geçmişinde başka cihazdan yanlış numarayla gelmiş eklenti kayıtlarını düzeltir ([HistoryPluginRepair]).
     * Eşitlemeyi asla bozmaz: hata olursa 0 döner. Eklenti kodundan gelen Error (ör. LinkageError) de yakalanır; yoksa Wi-Fi
     * birleştirmesini ve TV'nin açılış onarımını düşürür. Kilit ve onarım hatasının kaydı
     * [HistoryPluginRepair.repairSafely] içinde; burada yalnız profil okuma hatası kalır.
     */
    suspend fun repairHistoryPlugins(): Int {
        val repair = pluginRepair ?: return 0
        migrateContentIds()
        return try {
            repair.repairSafely(userId(), profiles.allProfiles().map { it.id })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // iptal yutulmaz
        } catch (_: Throwable) {
            0
        }
    }

    /**
     * Eski (eklenti numaralı) içerik kimliklerini kalıcı kimliğe taşır ([HistoryPluginRepair.migrateIds]). Eklenti yüklemez;
     * açılışta hemen ve her eşitleme yazımından sonra (bulut dahil) çalışır. Hata 0 döner, iptal yutulmaz.
     */
    suspend fun migrateContentIds(): Int {
        val repair = pluginRepair ?: return 0
        return try {
            repair.migrateIdsSafely(userId(), profiles.allProfiles().map { it.id })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            0
        }
    }

    suspend fun consolidateLocalDefaultProfiles() {
        profiles.consolidateDefaultProfiles()
        migrator.migrateAllPending(userId())
    }

    /** [includePins]: yalnız Wi-Fi (şifreli yerel kanal) için PIN özeti ve damgası yüke girer; bulut yükünde hiç yoktur. */
    suspend fun export(includePins: Boolean = false): LocalProfileTransfer {
        consolidateLocalDefaultProfiles()
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
            p.id to backup
        }
        val tombstones = profiles.allDeletedHistory()
        val favTombstones = profiles.allDeletedFavorites()
        // Bölüm/temizle/favori silinenleri ve favori açılış bilgileri de yüke girer (yeni alanlar; eski sürüm bilinmeyen alanı atar).
        return LocalSyncMerge.snapshot(
            all, libs, sources(), deviceName, tombstones, includePins = includePins,
            deletedItems = profiles.allDeletedItems(),
            favoriteSources = favoriteSources(libs.values.flatMap { it.favorites }),
            deletedFavorites = favTombstones,
        )
    }

    private suspend fun favoriteSources(favs: List<FavoriteBackup>): List<FavoriteSourceTransfer> {
        val refs = favoriteRefs?.all().orEmpty()
        if (refs.isEmpty()) return emptyList()
        val keys = favs.mapNotNull { f -> MediaType.fromTmdb(f.type)?.let { it to f.tmdbId } }.toSet()
        val names = pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first().associate { it.id to it.pluginId }
        return LocalSyncMerge.outgoingFavoriteSources(refs, keys, names)
    }

    /**
     * Karşıdan gelen eklenti favorisi açılış bilgilerini bu cihazın eklentisine çevirip yerel kayda yazar (burada zaten
     * bilinen favoriye dokunulmaz). Favori satırlarından ÖNCE yazılır: Kütüphane favoriyi gördüğünde açılış bilgisi hazırdır.
     */
    private suspend fun storeFavoriteSources(items: List<FavoriteSourceTransfer>) {
        val refs = favoriteRefs ?: return
        if (items.isEmpty()) return
        val plugins = pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO).first()
        var stored = 0
        var unresolved = 0
        for (item in items) {
            if (MediaType.fromTmdb(item.type)?.let { refs.get(it, item.tmdbId) } != null) continue
            val target = LocalSyncMerge.favoriteSourceTarget(item, plugins)
            if (target == null) {
                unresolved++
                continue
            }
            refs.put(target.first, item.tmdbId, target.second, item.contentUrl)
            stored++
        }
        if (stored > 0 || unresolved > 0) {
            android.util.Log.i("PlaySync", "favorite sources: ${items.size} received, $stored stored, $unresolved unresolved")
        }
    }

    /** Tüm kaynaklar: Nuvio addon'ları (altyazı addon'ları dahil), Nuvio eklenti depoları, CloudStream depoları, web siteleri. */
    private suspend fun sources(): List<SourceTransferItem> {
        val disabled = settings.disabledCatalogs.first()
        return SYNCED_TYPES.flatMap { type -> addonDao.observeOfType(type).first() }
            .filter { LocalSyncFilterRules.isSyncableSource(it.sourceUrl, it.addonId) }
            .map { row ->
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

    /**
     * Planı yazar: profiller, geçmiş + favoriler, (karşı esas ise) ortak kaynakların durumları.
     * [preserveProfileSecrets]: bulutta PIN yerelden korunur (varsayılan); Wi-Fi (false) birleştirmenin seçtiği PIN'i yazar.
     * [repairPlugins]: yalnız Wi-Fi birleştirmesi (true) yazdıktan sonra geçmişi bu cihazın eklentilerine çevirir; bulut
     * eşitlemesi sık çalıştığından ve onarım tüm eklentileri yüklediğinden orada yapılmaz.
     */
    suspend fun apply(
        plan: LocalSyncPlan,
        includePluginSettings: Boolean = true,
        preserveProfileSecrets: Boolean = true,
        repairPlugins: Boolean = false,
        installPlugins: Boolean = false,
    ) {
        profiles.replaceProfilesKeepingActive(plan.profiles, plan.remappedProfileIds, preserveLocalSecrets = preserveProfileSecrets)
        val uId = userId()
        storeFavoriteSources(plan.favoriteSources)
        for ((profileId, library) in plan.libraries) {
            backups.mergeJsonFor(uId, profileId, LocalSyncMerge.encodeBackup(library)).getOrThrow()
        }
        if (plan.deletedHistory.isNotEmpty()) {
            profiles.saveDeletedHistory(plan.deletedHistory)
            for (item in plan.deletedHistory) {
                val profId = plan.remappedProfileIds[item.profileId] ?: item.profileId
                val existing = history.all(uId, profId)
                for (entity in existing) {
                    if (entity.tmdbId == item.tmdbId &&
                        entity.type.name.equals(item.type, ignoreCase = true) &&
                        entity.lastWatchedAt <= item.deletedAt) {
                        history.delete(uId, profId, entity.id)
                    }
                }
            }
        }
        if (plan.deletedFavorites.isNotEmpty()) {
            profiles.saveDeletedFavorites(plan.deletedFavorites)
            for (item in plan.deletedFavorites) {
                val profId = plan.remappedProfileIds[item.profileId] ?: item.profileId
                val existing = favorites.all(uId, profId)
                val targetType = if (item.type.equals("tv", ignoreCase = true)) MediaType.Tv else MediaType.Movie
                for (entity in existing) {
                    if (entity.tmdbId == item.tmdbId &&
                        entity.type == targetType &&
                        entity.addedAt <= item.deletedAt) {
                        favorites.remove(uId, profId, entity.tmdbId, entity.type)
                    }
                }
            }
        }
        // Wi-Fi silinenleri (bölüm, geçmişi temizle, favori): birleşim yalnız ekler; bu cihazda duran eski kopya burada silinir.
        if (plan.deletedItems.isNotEmpty()) {
            profiles.saveDeletedItems(plan.deletedItems)
            for ((profId, items) in plan.deletedItems.groupBy { plan.remappedProfileIds[it.profileId] ?: it.profileId }) {
                for (e in history.all(uId, profId)) {
                    if (LocalSyncMerge.isHistoryDeleted(e.tmdbId, e.type.name, e.season, e.episode, e.lastWatchedAt, items)) {
                        history.delete(uId, profId, e.id)
                    }
                }
                for (f in favorites.all(uId, profId)) {
                    if (LocalSyncMerge.isFavoriteDeleted(f.tmdbId, f.type.name, f.addedAt, items)) favorites.remove(uId, profId, f.tmdbId, f.type)
                }
            }
        }
        migrator.migrateAllPending(uId, plan.remappedProfileIds)
        if (installPlugins) {
            for (item in plan.sharedSources) rowFor(item.sourceUrl)?.let { installMissingPlugins(it, item) }
        }
        for (item in plan.sourceStates) {
            rowFor(item.sourceUrl)?.let { applyState(it, item, includePluginSettings) }
        }
        // Karşı cihazın numarasıyla gelen kayıtlar bu cihazın eklentilerine çevrilir. Göndermeden önce onarım gerekmez: her
        // cihaz aldığını kendi eklentileriyle onarır (karşının numarasına güvenmez).
        // Başka cihazın eski kimlikleri her yolda (bulut dahil) kalıcı kimliğe çevrilir; eklenti onarımı yalnız Wi-Fi'de.
        if (repairPlugins) repairHistoryPlugins() else migrateContentIds()
    }

    /**
     * Ortak CS deposunda karşıda açık olup burada kurulu olmayan eklentiler (depoya sonradan eklenenler dahil) kurulur.
     * Burada kurulu eklentiler kaldırılmaz ve kapatılmaz. Hata eşitlemeyi bozmaz.
     */
    private suspend fun installMissingPlugins(row: AddonEntity, item: SourceTransferItem) {
        val install = installCsPlugins ?: return
        if (row.type != AddonType.CLOUDSTREAM_REPO) return
        val have = pluginDao.ofRepo(row.id).map { it.pluginId }.toSet()
        val wanted = item.plugins.filter { it.enabled && it.pluginId !in have }.map { it.pluginId }.toSet()
        if (wanted.isEmpty()) return
        try {
            val n = install(row.id, row.sourceUrl, wanted)
            android.util.Log.i("PlaySync", "plugins installed from shared repo: $n/${wanted.size}")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("PlaySync", "plugin install failed: ${e.javaClass.simpleName}")
        }
    }

    /** Onaylanan eksik kaynağı bu cihazda kurar ve durumlarını gönderenle aynı yapar. Başarısızsa false. */
    suspend fun install(item: SourceTransferItem, includePluginSettings: Boolean = true): Boolean {
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
        applyState(row, item, includePluginSettings)
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
    private suspend fun applyState(row: AddonEntity, item: SourceTransferItem, includePluginSettings: Boolean) {
        if (row.enabled != item.enabled) addonDao.setEnabled(row.id, item.enabled)
        val wanted = item.plugins.associateBy { it.pluginId }
        for (plugin in pluginDao.ofRepo(row.id)) {
            val state = wanted[plugin.pluginId] ?: continue
            if (plugin.enabled != state.enabled) pluginDao.setEnabled(plugin.id, state.enabled)
            if (includePluginSettings && plugin.hasSettings) {
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
