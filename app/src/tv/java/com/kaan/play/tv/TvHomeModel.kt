package com.kaan.play.tv

import com.kaan.play.R
import androidx.annotation.StringRes
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.PlayApp
import com.kaan.play.data.addon.CatalogVisibility
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import com.kaan.play.data.provider.LiveCatalogRules
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogResult
import com.kaan.play.data.provider.CatalogRow
import com.kaan.play.data.provider.ProviderItem
import com.kaan.play.data.provider.ProviderKind
import com.kaan.play.data.provider.ProviderRules
import com.kaan.play.ui.home.HomeRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Ana sayfadaki bir sağlayıcının satırları; sağlayıcılar birbirini beklemeden ayrı ayrı dolar. */
data class ProviderRows(val provider: ProviderItem, val rows: List<CatalogRow>?)

sealed interface HomeState {
    data class Preparing(@StringRes val message: Int) : HomeState
    data class Ready(val sections: List<ProviderRows>) : HomeState
    data class Failed(@StringRes val message: Int) : HomeState
}

/**
 * Sol menüdeki içerik bölümü: Ana sayfa Play ana sayfasının sırasıyla; Filmler ve Diziler ayrı sayfalar, her biri yalnız kendi
 * türünün addon katalog satırlarını ve TMDB satırlarını gösterir.
 */
enum class HomeKind {
    Home, Movies, Series,
}

/** TV ana sayfası. Etkinlik ömrü boyunca yaşar: detaydan dönünce satırlar yeniden yüklenmez, odak son karta döner. */
class TvHomeModel(app: Application) : AndroidViewModel(app) {
    private val container get() = getApplication<PlayApp>().container
    private val prefs = app.getSharedPreferences("play_tv", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<HomeState>(HomeState.Preparing(R.string.tv_common_loading))
    val state: StateFlow<HomeState> = _state

    /** Odaktaki kart: üstteki büyük alan onu gösterir. */
    val focused = MutableStateFlow<CatalogItem?>(null)

    /** Odaktaki kartın satırı yatay görselli mi (film/dizi karesi 16:9) yoksa dikey afişli mi (kısa drama 2:3). */
    val focusedLandscape = MutableStateFlow(false)

    /** TMDB satırları (film kaynağı kuruluysa): Play'deki gibi addon ve eklenti satırlarından sonra. */
    private val _tmdbRows = MutableStateFlow<List<CatalogRow>>(emptyList())
    val tmdbRows: StateFlow<List<CatalogRow>> = _tmdbRows

    /**
     * Bot doğrulaması isteyen sağlayıcılar (Play ana sayfasındaki "Doğrula"): sağlayıcı kimliği → site. Katalog boş döndü,
     * site engel sayfası veriyor; doğrulama başarılı olunca [reloadProvider] satırları yeniden ister.
     */
    private val _blocked = MutableStateFlow<Map<String, VerifySite>>(emptyMap())
    internal val blocked: StateFlow<Map<String, VerifySite>> = _blocked

    /** Detaydan dönüşte odağın geri verileceği kart. */
    var lastFocusedKey: String? = null

    /** Detaydan / "Tümünü gör" ızgarasından dönüşte listenin kaydırma yeri (satırlar çoğalınca derindeki kart oluşmuyordu). */
    var listIndex = 0
    var listOffset = 0
    var listKey: Any? = null

    /**
     * Bu modelin gösterdiği bölüm: Ana sayfa (addon katalogları + seçili drama eklentileri + TMDB), Filmler ya da Diziler
     * (yalnız o türün addon satırları + TMDB satırları). İlk [configure] çağrısında sabitlenir.
     */
    var kind = HomeKind.Home
        private set
    private var configured = false

    private lateinit var diskCache: TvHomeCache
    private var saveJob: kotlinx.coroutines.Job? = null
    /** Süren yükleme: yeni [load] öncekini keser (Ayarlar'da katalog aç/kapa art arda basılınca yüklemeler üst üste binmesin). */
    private var loadJob: kotlinx.coroutines.Job? = null

    fun configure(kind: HomeKind) {
        if (configured) return
        configured = true
        this.kind = kind
        diskCache = TvHomeCache(getApplication(), kind)
        load()
    }

    /** Satırlar değiştikçe diske yazılır (2 sn biriktirilerek). */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.delay(2_000)
            val sections = (_state.value as? HomeState.Ready)?.sections ?: return@launch
            diskCache.write(sections, _tmdbRows.value)
        }
    }

    fun load() {
        if (!configured) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Play'in katalog aç/kapa ayarı (Ayarlar > Eklentiler > Kataloglar; telefonla aynı anahtar, Wi-Fi eşitlemesi taşır):
            // kapalı addon katalogları gösterilmez. Play'in bellek önbelleği süzülmemiş satırları tutar, disk önbelleği ve önceki
            // satırlar ayar değişmeden önce yazılmış olabilir; üçü de burada süzülür.
            val disabled = container.settings.disabledCatalogs.first()
            fun visible(rows: List<CatalogRow>) = CatalogVisibility.filterRows(rows, disabled)
            // Önce disk önbelleği: son görülen satırlar anında; eklentiler ve ağ arkada yenilenir.
            val stale = if (_state.value is HomeState.Ready) null
                else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { diskCache.read() }
            if (stale != null && (stale.sections.isNotEmpty() || stale.tmdb.isNotEmpty())) {
                _tmdbRows.value = stale.tmdb
                _state.value = HomeState.Ready(stale.sections.map { it.copy(rows = it.rows?.let(::visible)) })
            } else if (_state.value !is HomeState.Ready) {
                _state.value = HomeState.Preparing(R.string.tv_common_loading)
            }
            val previous = (_state.value as? HomeState.Ready)?.sections.orEmpty()
            // TMDB satırları Play ana sayfasındaki sırayla ve yerde: addon/eklenti satırlarının altında. Filmler / Diziler yalnız
            // kendi türünün satırlarını alır.
            val filmSources = container.hasFilmSources()
            launch {
                val fresh = if (filmSources) container.tmdbRows(tmdbRowsOf(kind)) else emptyList()
                // Ağ hatasında (boş yanıt) önbellekteki satırlar korunur.
                if (fresh.isNotEmpty() || !filmSources) { _tmdbRows.value = fresh; scheduleSave() }
            }
            // CloudStream eklentileri ve katalog veren Nuvio addon'ları (ör. izlelan katalog); canlı TV satırları ayıklanır.
            // Yalnız canlı yayın yapan eklentiler (IPTV) hiç yüklenmez: satırları burada zaten atılır (withoutLiveRows), bölümleri
            // kutuyu boşuna yorar (her bölümde zaman aşımı). Canlı TV ayrı bölümde. Yalnız gösterilecek sağlayıcılar yüklenir:
            // Play'in `allProviders` listesi her açık CloudStream eklentisinin kodunu yükleyip katalog verip vermediğine bakar;
            // eklenti sayısı artınca 2 GB'lık kutuda Java belleği doluyordu. Ana sayfa Play'deki gibi seçili eklentileri gösterir
            // ([dramaProviders]).
            // Katalog veren addon'lar Play'in "Tümü" ana sayfasındaki sırayla (Play Özel Kataloglar önde, diğerleri eklenme
            // sırasıyla); Ana sayfada ardından seçili drama eklentileri. Addon katalogları HTTP'dir, eklenti kodu yüklemez.
            val addons = ProviderRules.buildHomeProviders(container.providers.nuvioProviders.first(), emptyList(), emptySet())
            val providers = when (kind) {
                HomeKind.Home -> addons + dramaProviders()
                else -> addons
            }
            if ((kind == HomeKind.Movies || kind == HomeKind.Series) && !filmSources) {
                _state.value = HomeState.Failed(R.string.tv_home_no_film_addons)
                return@launch
            }
            // Addon'u olmayan kutuda boş Ana sayfa hata değildir (devam et + TMDB kalır).
            // Önce bellek, sonra disk önbelleği; önbellekten gelen satırlar görünür kalır, yerinde yenilenir.
            val sections = providers.map { p ->
                ProviderRows(p, container.catalogRepository.cache.get(p.id)?.let { ofKind(ProviderRules.withoutLiveRows(visible(it))) }
                    ?: previous.firstOrNull { it.provider.id == p.id }?.rows?.let(::visible))
            }
            _state.value = HomeState.Ready(sections)
            val gate = Semaphore(3) // Play ana sayfasıyla aynı: aynı anda en fazla üç sağlayıcı
            providers.filter { p -> container.catalogRepository.cache.get(p.id) == null }.forEach { provider ->
                launch {
                    val rows = gate.withPermit { loadProvider(provider) }
                    update(provider.id, rows)
                }
            }
        }
    }

    /** Sağlayıcının satırları; site bot doğrulaması isterse (Play'deki "Doğrula" durumu) [blocked]'a yazılır. */
    private suspend fun loadProvider(provider: ProviderItem, force: Boolean = false): List<CatalogRow> = runCatching {
        val result = container.catalogRepository.loadCatalog(setOf(provider.id), forceRefresh = force)
        val verify = (result as? CatalogResult.Error)?.takeIf { it.isBotBlocked }?.let { e ->
            val host = e.verifyHost ?: e.verifyUrl?.let { CloudflareCache.normalizeHost(it) }
            host?.let { VerifySite(it, e.verifyUrl ?: "https://$it", provider.name) }
        }
        _blocked.value = if (verify != null) _blocked.value + (provider.id to verify) else _blocked.value - provider.id
        if (verify != null) Log.i(TAG, "doğrulama gerekiyor: ${provider.name} (${verify.host})")
        ofKind(ProviderRules.withoutLiveRows(ProviderRules.homeSectionRows(result)))
    }.getOrElse { emptyList() }

    /** Doğrulamadan sonra (ya da "Yeniden dene") tek sağlayıcı önbelleksiz yeniden yüklenir. */
    fun reloadProvider(providerId: String) {
        val current = _state.value as? HomeState.Ready ?: return
        val section = current.sections.firstOrNull { it.provider.id == providerId } ?: return
        _blocked.value = _blocked.value - providerId
        _state.value = HomeState.Ready(current.sections.map { if (it.provider.id == providerId) it.copy(rows = null) else it })
        viewModelScope.launch {
            val rows = loadProvider(section.provider, force = true)
            val now = _state.value as? HomeState.Ready ?: return@launch
            _state.value = HomeState.Ready(now.sections.map { if (it.provider.id == providerId) it.copy(rows = rows) else it })
            scheduleSave()
        }
    }

    /** Filmler / Diziler: addon satırlarından yalnız o türünkiler (Nuvio satır kimliği `tür:katalog`, ör. `movie:top`). */
    private fun ofKind(rows: List<CatalogRow>): List<CatalogRow> = when (kind) {
        HomeKind.Home -> rows
        HomeKind.Movies -> rows.filter { it.id.substringBefore(':') == "movie" }
        HomeKind.Series -> rows.filter { it.id.substringBefore(':') == "series" }
    }

    /**
     * Yüklenen sağlayıcının satırları yerinde güncellenir: sıra Play'deki gibi sabittir (önce yüklenen öne geçmez). Liste
     * kaymaz: LazyColumn yerini anahtarla
     * korur. Önbellekten görünen sağlayıcıda ağ boş döndüyse eskisi kalır.
     */
    private fun update(providerId: String, rows: List<CatalogRow>) {
        val current = _state.value as? HomeState.Ready ?: return
        val existing = current.sections.firstOrNull { it.provider.id == providerId } ?: return
        if (existing.rows != null && rows.isEmpty()) return
        _state.value = HomeState.Ready(current.sections.map { if (it.provider.id == providerId) it.copy(rows = rows) else it })
        scheduleSave()
    }

    /** Açık, canlı yayın olmayan CloudStream eklentileri (veritabanından; eklenti kodu yüklenmez), ada göre. */
    private suspend fun csOptions(): List<InstalledCsPlugin> =
        container.csRepos.enabledPlugins().filterNot(::isLiveOnlyPlugin).sortedBy { it.name.lowercase() }

    /**
     * "Eklenti seç" listesi = Play'in sağlayıcı seçicisi: katalog veren addon'lar + CloudStream eklentileri; sabitlenenler
     * önde, sonra ada göre (Play [ProviderRules.applyPinningAndSort]). Play'den farkı: CloudStream listesi veritabanından gelir,
     * katalog verip vermediğini anlamak için her eklentinin kodu yüklenmez (2 GB'lık kutu).
     */
    suspend fun pickerOptions(): List<ProviderItem> {
        val cs = csOptions().map { ProviderItem(id = "cs:${it.id}", name = it.name, subtitle = "CloudStream", kind = ProviderKind.CloudStream) }
        return ProviderRules.applyPinningAndSort(container.providers.nuvioProviders.first() + cs, pinnedIds())
    }

    /** Play ile ortak sabitleme (`pinned_providers`, telefondaki iğne). Eski TV seçimi ilk okumada sabitlemeye taşınır. */
    suspend fun pinnedIds(): Set<String> {
        migrateSelection()
        return container.providers.pinnedProviders.first()
    }

    /** Seçicide işaretlenenler sabitlenir, işareti kaldırılanların sabitlemesi kalkar (yalnız listede görünenler değişir). */
    fun setPinned(shown: List<ProviderItem>, chosen: Set<String>) {
        viewModelScope.launch {
            val current = pinnedIds()
            val changed = shown.map { it.id }.filter { (it in current) != (it in chosen) }
            if (changed.isEmpty()) return@launch
            changed.forEach { container.providers.togglePin(it) }
            _state.value = HomeState.Preparing(R.string.tv_common_loading)
            load()
        }
    }

    /**
     * Önceki TV sürümü drama seçimini Play'in `selected_providers` anahtarına yazıyordu (Play'de o anahtar "yalnız bu
     * sağlayıcıyı göster" süzgecidir). Seçilen CloudStream eklentileri bir kez sabitlenir: ana sayfadaki dramalar aynı kalır.
     */
    private suspend fun migrateSelection() {
        if (prefs.getBoolean(KEY_PINS_MIGRATED, false)) return
        val old = container.providers.selectedProviders.first().filter { it.startsWith("cs:") }
        val pinned = container.providers.pinnedProviders.first()
        old.filterNot { it in pinned }.forEach { container.providers.togglePin(it) }
        if (old.isNotEmpty()) Log.i(TAG, "eski TV drama seçimi sabitlemeye taşındı: ${old.size}")
        prefs.edit().putBoolean(KEY_PINS_MIGRATED, true).apply()
    }

    /**
     * Ana sayfanın eklenti satırlarını veren CloudStream eklentileri: Play'in "Tümü" ana sayfası gibi sabitlenenler (ada göre;
     * hiçbiri sabitlenmemişse eklenti satırı yok). Yalnız bunların kodu yüklenir (bellek).
     */
    private suspend fun dramaProviders(): List<ProviderItem> {
        val pinned = pinnedIds()
        val chosen = csOptions().filter { "cs:${it.id}" in pinned }
        return chosen.map { ProviderItem(id = "cs:${it.id}", name = it.name, subtitle = "CloudStream", kind = ProviderKind.CloudStream) }
    }

    private companion object {
        const val TAG = "PlayTv"
        const val KEY_PINS_MIGRATED = "drama_pick_to_pins_migrated"
    }
}

/** Bölümün TMDB satırları, Play ana sayfasındaki sırayla. */
private fun tmdbRowsOf(kind: HomeKind): List<HomeRow> = when (kind) {
    HomeKind.Home -> HomeRow.entries
    HomeKind.Movies -> listOf(HomeRow.TrendingMovies, HomeRow.PopularMovies)
    HomeKind.Series -> listOf(HomeRow.TrendingSeries, HomeRow.PopularSeries)
}

internal fun isLiveOnlyPlugin(p: InstalledCsPlugin): Boolean =
    (p.tvTypes.isNotEmpty() && p.tvTypes.all { it.equals("Live", ignoreCase = true) }) ||
        LiveCatalogRules.isKnownLiveChannel(p.name) || LIVE_PLUGIN_NAME.containsMatchIn(p.name)

/**
 * Yalnız canlı yayın (IPTV) veren CloudStream eklentisi: depo bilgisinde türleri yalnız "Live" ya da adı canlı kanal listesi
 * olduğunu söylüyor ([LiveCatalogRules] kanal adları + "canlı/iptv/live tv"). Ad kuralı dar tutulur: "Puhu TV" gibi dizi
 * eklentileri düşmesin; yalnız açıkça canlı/IPTV diyen adlar.
 */
private val LIVE_PLUGIN_NAME = Regex("canlı|canli|iptv|live ?tv|aile tv", RegexOption.IGNORE_CASE)
