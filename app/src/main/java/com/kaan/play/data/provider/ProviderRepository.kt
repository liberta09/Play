package com.kaan.play.data.provider

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.ConcurrentHashMap

/**
 * Sağlayıcıların (yalnızca kataloğu olan CloudStream ve Nuvio sağlayıcıları) listesini ve
 * seçili/sabitlenmiş durumlarını DataStore üzerinden yöneten depo.
 */
class ProviderRepository(
    private val addonDao: AddonDao,
    private val pluginDao: PluginDao,
    private val dataStore: DataStore<Preferences>,
    private val csRepository: CsRepository? = null,
    /** Verilirse CloudStream listesi bu kapsamda bir kez hesaplanıp paylaşılır (her toplayıcı DEX yüklemesin). */
    private val scope: CoroutineScope? = null,
) {

    val pinnedProviders: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[PINNED_PROVIDERS] ?: emptySet()
    }.distinctUntilChanged()

    val selectedProviders: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[SELECTED_PROVIDERS] ?: emptySet()
    }.distinctUntilChanged()

    private val csHasCatalogCache = ConcurrentHashMap<Long, Boolean>()

    private suspend fun csPluginHasCatalog(pluginId: Long): Boolean {
        val repo = csRepository ?: return true
        csHasCatalogCache[pluginId]?.let { return it }
        // İptal yutulmaz ve hata önbelleğe girmez: eskiden runCatching iptali de yakalayıp "katalog yok" diye kalıcı
        // önbelleğe yazıyordu; liste hesaplanırken uygulama arka plana geçince o eklenti oturum boyunca listeden
        // düşüyor, seçiliyse seçim de temizleniyordu.
        val apis = try {
            repo.loadApis(pluginId).apisOrEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return false
        }
        val hasCatalog = apis.any { api ->
            api.hasMainPage || runCatching { !api.mainPage.isNullOrEmpty() }.getOrDefault(false)
        }
        csHasCatalogCache[pluginId] = hasCatalog
        return hasCatalog
    }

    private val csProviders: Flow<List<ProviderItem>> = combine(
        pluginDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
        addonDao.observeOfType(AddonType.CLOUDSTREAM_REPO),
    ) { plugins, repos ->
        plugins to repos
    }.map { (plugins, repos) ->
        val repoMap = repos.associateBy { it.id }
        plugins.filter { it.enabled && csPluginHasCatalog(it.id) }.map { p ->
            val repoName = repoMap[p.repoId]?.name.orEmpty()
            val sub = if (repoName.isNotBlank()) "CloudStream · $repoName" else "CloudStream"
            val id = "cs:${p.id}"
            ProviderItem(id = id, name = p.name, subtitle = sub, kind = ProviderKind.CloudStream)
        }
    }
        // DEX yükleme ana iş parçacığında değil; listeyi toplayan her ekran ayrı ayrı yeniden hesaplamasın.
        .flowOn(Dispatchers.IO)
        .let { flow -> scope?.let { flow.shareIn(it, SharingStarted.WhileSubscribed(5_000), replay = 1) } ?: flow }

    /**
     * Katalog veren Nuvio sağlayıcıları: addon'lar ve manifestinde `catalogs` olan plugin depoları
     * ([ProviderRules.nuvioCatalogProviders]). Plugin deposu manifesti addon manifesti olarak okunamıyorsa
     * (yalnızca `scrapers` listesi) katalog sunmaz sayılır.
     */
    private val nuvioProviders: Flow<List<ProviderItem>> = combine(
        addonDao.observeOfType(AddonType.NUVIO_ADDON),
        addonDao.observeOfType(AddonType.NUVIO_PLUGIN_REPO),
    ) { addons, pluginRepos ->
        ProviderRules.nuvioCatalogProviders(
            (addons + pluginRepos).map { a ->
                NuvioSource(
                    rowId = a.id,
                    name = a.name,
                    addonId = a.addonId,
                    enabled = a.enabled,
                    manifest = runCatching { PlayJson.decodeFromString(AddonManifest.serializer(), a.manifestJson) }.getOrNull(),
                )
            },
        )
    }

    /**
     * Açık (enabled=true) olan tüm CloudStream ve Nuvio sağlayıcılarının birleşik listesi.
     * Sabitlenenler en üstte, ardından alfabetik sıralı gelir.
     */
    val allProviders: Flow<List<ProviderItem>> = combine(
        csProviders,
        nuvioProviders,
        pinnedProviders,
    ) { cs, nuvio, pinned ->
        val combined = cs + nuvio
        ProviderRules.applyPinningAndSort(combined, pinned)
    }

    /**
     * Varsayılan ana sayfada ("Tüm sağlayıcılar") görünecek sağlayıcıların sıralı listesi:
     * 1) Kurulu ve açık Nuvio katalog addon'ları (Play Özel Kataloglar en önde, diğerleri eklenme sırasıyla)
     * 2) İğnelenmiş (pinned) CloudStream eklentileri (kendi aralarında alfabetik)
     */
    val homeProviders: Flow<List<ProviderItem>> = combine(
        nuvioProviders,
        csProviders,
        pinnedProviders,
    ) { nuvio, cs, pinned ->
        ProviderRules.buildHomeProviders(nuvio, cs, pinned)
    }

    suspend fun setSelectedProviders(selected: Set<String>) {
        dataStore.edit { prefs ->
            val filtered = selected - ProviderConstants.ALL_PROVIDERS_ID
            if (filtered.isEmpty()) {
                prefs.remove(SELECTED_PROVIDERS)
            } else {
                prefs[SELECTED_PROVIDERS] = filtered
            }
        }
    }

    suspend fun togglePin(id: String) {
        if (id == ProviderConstants.ALL_PROVIDERS_ID) return
        dataStore.edit { prefs ->
            val current = prefs[PINNED_PROVIDERS] ?: emptySet()
            val next = if (id in current) current - id else current + id
            if (next.isEmpty()) {
                prefs.remove(PINNED_PROVIDERS)
            } else {
                prefs[PINNED_PROVIDERS] = next
            }
        }
    }

    companion object {
        private val SELECTED_PROVIDERS = stringSetPreferencesKey("selected_providers")
        private val PINNED_PROVIDERS = stringSetPreferencesKey("pinned_providers")
    }
}
