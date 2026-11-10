package com.kaan.play.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.kaan.play.data.library.ContinueSortMode
import com.kaan.play.data.player.AutoRetryPolicy
import com.kaan.play.data.provider.LiveTvSortMode
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.data.tmdb.PosterUrlTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Koyu tema varsayılandır. */
enum class ThemeMode { Dark, Light, System }

/** Oynatıcıda görüntünün ekrana oturma biçimi (seçim kaydedilir): sığdır, doldur (kırp), uzat, 16:9, 4:3. */
enum class ResizeMode { Fit, Fill, Stretch, Ratio16x9, Ratio4x3 }

/** DataStore'daki uygulama ayarları. Bozuk/bilinmeyen değerler varsayılana düşer. */
class SettingsRepository(val store: DataStore<Preferences>) {

    /** IMDb kimliği içeren özel poster adresi. Boş değer özel posterleri kapatır. */
    val posterUrlTemplate: Flow<String> = store.data.map { prefs ->
        val value = prefs[POSTER_URL_TEMPLATE]
        when {
            value == null -> PosterUrlTemplate.DEFAULT
            value.isBlank() -> ""
            PosterUrlTemplate.isValid(value) -> value
            else -> PosterUrlTemplate.DEFAULT
        }
    }.distinctUntilChanged()

    suspend fun setPosterUrlTemplate(template: String) {
        val value = template.trim()
        store.edit { prefs ->
            when {
                value.isBlank() -> prefs[POSTER_URL_TEMPLATE] = ""
                PosterUrlTemplate.isValid(value) -> prefs[POSTER_URL_TEMPLATE] = value
                else -> prefs[POSTER_URL_TEMPLATE] = PosterUrlTemplate.DEFAULT
            }
        }
    }

    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[THEME] } ?: DEFAULT_THEME
    }

    /** Addon başına kaynak isteği zaman aşımı (saniye). */
    val sourceTimeoutSeconds: Flow<Int> = store.data.map { prefs ->
        prefs[TIMEOUT]?.takeIf { it in TIMEOUT_OPTIONS } ?: DEFAULT_TIMEOUT_SECONDS
    }

    /** Altyazı görünümü; oynatıcı ve Ayarlar aynı akıştan okur, değişiklik anında yansır. */
    val subtitleStyle: Flow<SubtitleStyle> = store.data.map { it.toSubtitleStyle() }.distinctUntilChanged()

    /**
     * Tercih edilen altyazı dili (iki harfli). Otomatik seçim ve çevrimiçi arama buna göre yapılır (sonra İngilizce).
     * Varsayılan Türkçe; bilinmeyen değer varsayılana düşer.
     */
    val subtitleLanguage: Flow<String> = store.data.map { prefs ->
        prefs[SUB_LANGUAGE]?.takeIf { it in SUBTITLE_LANGUAGES } ?: DEFAULT_SUBTITLE_LANGUAGE
    }.distinctUntilChanged()

    suspend fun setSubtitleLanguage(language: String) {
        require(language in SUBTITLE_LANGUAGES) { "Desteklenmeyen altyazı dili: $language" }
        store.edit { it[SUB_LANGUAGE] = language }
    }

    /** Açıkken yarım kalan içerik soru sorulmadan kaldığı yerden başlar. Varsayılan açık. */
    val alwaysResume: Flow<Boolean> = store.data.map { it[ALWAYS_RESUME] ?: DEFAULT_ALWAYS_RESUME }

    val rememberLastProfile: Flow<Boolean> =
        store.data.map { it[REMEMBER_LAST_PROFILE] ?: DEFAULT_REMEMBER_LAST_PROFILE }.distinctUntilChanged()

    suspend fun setRememberLastProfile(remember: Boolean) {
        store.edit { it[REMEMBER_LAST_PROFILE] = remember }
    }

    /** Açıkken film/bölüm açılınca kaynak listesi beklenmez: uygun ilk kaynakla oynatma başlar. Varsayılan açık. */
    val autoSource: Flow<Boolean> = store.data.map { it[AUTO_SOURCE] ?: DEFAULT_AUTO_SOURCE }.distinctUntilChanged()

    /** Açıkken Türkçe dublajlı kaynaklar önce denenir (dublaj yoksa altyazılıyla başlanır). Varsayılan açık. */
    val preferDub: Flow<Boolean> = store.data.map { it[PREFER_DUB] ?: DEFAULT_PREFER_DUB }.distinctUntilChanged()

    /** Açıkken dublajlı kaynaklarda Türkçe ses izi bulunana dek kaynaklar denenir; bulunamazsa en hızlı alternatif açılır. */
    val trySourcesUntilTurkishAudio: Flow<Boolean> =
        store.data.map { it[TRY_SOURCES_UNTIL_TURKISH_AUDIO] ?: DEFAULT_TRY_SOURCES_UNTIL_TURKISH_AUDIO }.distinctUntilChanged()

    /** Açıkken kullanıcı başka bir kaynak seçtiğinde açılmazsa önceki kaynağa dönülür; kapalıyken sıradaki uygun kaynağa geçilir. */
    val revertOnSelectedFailure: Flow<Boolean> =
        store.data.map { it[REVERT_ON_SELECTED_FAILURE] ?: DEFAULT_REVERT_ON_SELECTED_FAILURE }.distinctUntilChanged()

    suspend fun setRevertOnSelectedFailure(enabled: Boolean) {
        store.edit { it[REVERT_ON_SELECTED_FAILURE] = enabled }
    }

    /** Otomatik kaynak deneme sayısı sınırı (`0` = Sınırsız; seçenekler: 3 / 5 / 10 / 0). */
    val autoSourceRetryLimit: Flow<Int> =
        store.data.map { prefs -> AutoRetryPolicy.normalize(prefs[AUTO_SOURCE_RETRY_LIMIT]) }.distinctUntilChanged()

    suspend fun setAutoSourceRetryLimit(limit: Int) {
        require(limit in AUTO_SOURCE_RETRY_OPTIONS) { "Desteklenmeyen kaynak deneme sınırı: $limit" }
        store.edit { it[AUTO_SOURCE_RETRY_LIMIT] = limit }
    }

    /** Açıkken altyazılı oynarken sonradan bulunan dublajlı kaynak köşe kartıyla önerilir. Varsayılan açık. */
    val showDubSuggestion: Flow<Boolean> =
        store.data.map { it[SHOW_DUB_SUGGESTION] ?: DEFAULT_SHOW_DUB_SUGGESTION }.distinctUntilChanged()

    /** Dublajlı kaynakta Türkçe ses izi tarama süresi (5 / 7 / 10 sn). */
    val audioProbeTimeoutSeconds: Flow<Int> = store.data.map { prefs ->
        prefs[AUDIO_PROBE_TIMEOUT_SECONDS]?.takeIf { it in AUDIO_PROBE_TIMEOUT_OPTIONS } ?: DEFAULT_AUDIO_PROBE_TIMEOUT_SECONDS
    }.distinctUntilChanged()

    suspend fun setAutoSource(enabled: Boolean) {
        store.edit { it[AUTO_SOURCE] = enabled }
    }

    suspend fun setPreferDub(enabled: Boolean) {
        store.edit { it[PREFER_DUB] = enabled }
    }

    suspend fun setTrySourcesUntilTurkishAudio(enabled: Boolean) {
        store.edit { it[TRY_SOURCES_UNTIL_TURKISH_AUDIO] = enabled }
    }

    suspend fun setShowDubSuggestion(enabled: Boolean) {
        store.edit { it[SHOW_DUB_SUGGESTION] = enabled }
    }

    suspend fun setAudioProbeTimeoutSeconds(seconds: Int) {
        require(seconds in AUDIO_PROBE_TIMEOUT_OPTIONS) { "Desteklenmeyen tarama süresi: $seconds" }
        store.edit { it[AUDIO_PROBE_TIMEOUT_SECONDS] = seconds }
    }

    /** Oynatıcıdaki "Yeniden boyutlandır" seçimi; bilinmeyen değer "sığdır"a düşer. */
    val resizeMode: Flow<ResizeMode> = store.data.map { prefs ->
        ResizeMode.entries.firstOrNull { it.name == prefs[RESIZE_MODE] } ?: ResizeMode.Fit
    }.distinctUntilChanged()

    suspend fun setResizeMode(mode: ResizeMode) {
        store.edit { it[RESIZE_MODE] = mode.name }
    }

    /** Açıkken Türkçe altyazı yoksa İngilizce altyazı cihazda Türkçeye çevrilir. Varsayılan açık. */
    val autoTranslateSubtitles: Flow<Boolean> =
        store.data.map { it[AUTO_TRANSLATE_SUBS] ?: DEFAULT_AUTO_TRANSLATE_SUBS }.distinctUntilChanged()

    suspend fun setAutoTranslateSubtitles(enabled: Boolean) {
        store.edit { it[AUTO_TRANSLATE_SUBS] = enabled }
    }

    /** Açıkken dizide bölüm bitmeye yaklaşınca sonraki bölüm geri sayımla kendiliğinden açılır. Varsayılan açık. */
    val autoNext: Flow<Boolean> = store.data.map { it[AUTO_NEXT] ?: DEFAULT_AUTO_NEXT }.distinctUntilChanged()

    /** Sonraki bölüm geri sayımı (saniye): 5, 10 ya da 15; bilinmeyen değer varsayılana düşer. */
    val autoNextSeconds: Flow<Int> = store.data.map { prefs ->
        prefs[AUTO_NEXT_SECONDS]?.takeIf { it in AUTO_NEXT_OPTIONS } ?: DEFAULT_AUTO_NEXT_SECONDS
    }.distinctUntilChanged()

    /** Atlama düğmeleri varsayılan açık; otomatik atlama isteğe bağlı ve varsayılan kapalı; dört tür ayrı ayrı. */
    val skipPlayback: Flow<SkipPlaybackSettings> = store.data.map { prefs ->
        val defaults = SkipPlaybackSettings.Default
        val enabled = prefs[SKIP_ENABLED_CATEGORIES]?.mapNotNull { value ->
            SkipCategory.entries.firstOrNull { it.name == value }
        }?.toSet() ?: defaults.enabledCategories
        SkipPlaybackSettings(
            showButtons = prefs[SKIP_BUTTONS_ENABLED] ?: defaults.showButtons,
            autoSkip = prefs[SKIP_AUTO_ENABLED] ?: defaults.autoSkip,
            enabledCategories = enabled,
        )
    }.distinctUntilChanged()

    suspend fun setSkipButtonsEnabled(enabled: Boolean) {
        store.edit { it[SKIP_BUTTONS_ENABLED] = enabled }
    }

    suspend fun setAutoSkipEnabled(enabled: Boolean) {
        store.edit { it[SKIP_AUTO_ENABLED] = enabled }
    }

    suspend fun setSkipCategoryEnabled(category: SkipCategory, enabled: Boolean) {
        store.edit { prefs ->
            val current = prefs[SKIP_ENABLED_CATEGORIES]?.mapNotNull { value ->
                SkipCategory.entries.firstOrNull { it.name == value }
            }?.toSet() ?: SkipPlaybackSettings.Default.enabledCategories
            val next = if (enabled) current + category else current - category
            prefs[SKIP_ENABLED_CATEGORIES] = next.mapTo(mutableSetOf()) { it.name }
        }
    }

    suspend fun setAutoNext(enabled: Boolean) {
        store.edit { it[AUTO_NEXT] = enabled }
    }

    suspend fun setAutoNextSeconds(seconds: Int) {
        require(seconds in AUTO_NEXT_OPTIONS) { "Desteklenmeyen geri sayım: $seconds" }
        store.edit { it[AUTO_NEXT_SECONDS] = seconds }
    }

    /** Açıkken indirmeler yalnızca ölçümsüz (Wi-Fi/ethernet) ağda sürer. Varsayılan açık (mobil veri sessizce harcanmasın). */
    val downloadWifiOnly: Flow<Boolean> = store.data.map { it[DOWNLOAD_WIFI_ONLY] ?: DEFAULT_DOWNLOAD_WIFI_ONLY }.distinctUntilChanged()

    /** Kullanıcının torrent sunucusu (TorrServer uyumlu) adresi, `http(s)://host[:port]`; yoksa null ve torrent kaynakları görünmez. */
    val torrentServer: Flow<String?> = store.data.map { prefs -> TorrentLinks.normalizeServer(prefs[TORRENT_SERVER]) }.distinctUntilChanged()

    /** Geçersiz/boş adres ayarı siler. Kaydedilen değer (ya da null) döner. */
    suspend fun setTorrentServer(input: String?): String? {
        val normalized = TorrentLinks.normalizeServer(input)
        store.edit { if (normalized == null) it.remove(TORRENT_SERVER) else it[TORRENT_SERVER] = normalized }
        return normalized
    }

    /** Açıkken Play'in HTTP istemcisinde zaman aşımları 3× ve GET/HEAD ağ hatasında 1 kez yenilenir. Varsayılan kapalı. */
    val slowConnectionMode: Flow<Boolean> =
        store.data.map { it[SLOW_CONNECTION_MODE] ?: DEFAULT_SLOW_CONNECTION_MODE }.distinctUntilChanged()

    /** Açıkken çevrimiçi oynatmada ~15 sn kesintisiz beklemede "Bağlantı yavaş mı?" önerisi gösterilir. Varsayılan açık. */
    val suggestSlowConnection: Flow<Boolean> =
        store.data.map { it[SUGGEST_SLOW_CONNECTION] ?: DEFAULT_SUGGEST_SLOW_CONNECTION }.distinctUntilChanged()

    suspend fun setSlowConnectionMode(enabled: Boolean) {
        store.edit { it[SLOW_CONNECTION_MODE] = enabled }
    }

    suspend fun setSuggestSlowConnection(enabled: Boolean) {
        store.edit { it[SUGGEST_SLOW_CONNECTION] = enabled }
    }

    suspend fun setDownloadWifiOnly(enabled: Boolean) {
        store.edit { it[DOWNLOAD_WIFI_ONLY] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setSourceTimeoutSeconds(seconds: Int) {
        require(seconds in TIMEOUT_OPTIONS) { "Desteklenmeyen zaman aşımı: $seconds" }
        store.edit { it[TIMEOUT] = seconds }
    }

    /** Mevcut görünümü okuyup değiştirir ve tek yazmada kaydeder (aynı anda iki değişiklik birbirini ezmez). */
    suspend fun updateSubtitleStyle(transform: (SubtitleStyle) -> SubtitleStyle) {
        store.edit { prefs ->
            val next = transform(prefs.toSubtitleStyle())
            prefs[SUB_SIZE] = next.size.name
            prefs[SUB_BOLD] = next.bold
            prefs[SUB_ITALIC] = next.italic
            prefs[SUB_COLOR] = next.color.name
            prefs[SUB_BACKGROUND] = next.background.name
        }
    }

    suspend fun setAlwaysResume(enabled: Boolean) {
        store.edit { it[ALWAYS_RESUME] = enabled }
    }

    /** İlk açılışta varsayılan altyazı addon'larının eklendiğini gösteren bayrak (kullanıcı kararı, 2026-10-20). */
    val defaultSubtitleAddonsSeeded: Flow<Boolean> =
        store.data.map { it[DEFAULT_SUBTITLE_ADDONS_SEEDED] ?: false }

    suspend fun setDefaultSubtitleAddonsSeeded(seeded: Boolean = true) {
        store.edit { it[DEFAULT_SUBTITLE_ADDONS_SEEDED] = seeded }
    }

    private fun Preferences.toSubtitleStyle(): SubtitleStyle {
        val d = SubtitleStyle()
        return SubtitleStyle(
            size = SubtitleSize.entries.firstOrNull { it.name == this[SUB_SIZE] } ?: d.size,
            bold = this[SUB_BOLD] ?: d.bold,
            italic = this[SUB_ITALIC] ?: d.italic,
            color = SubtitleColor.entries.firstOrNull { it.name == this[SUB_COLOR] } ?: d.color,
            background = SubtitleBackground.entries.firstOrNull { it.name == this[SUB_BACKGROUND] } ?: d.background,
        )
    }

    /** Görülen uygulama içi duyuru kimlikleri kümesi. */
    val seenAnnouncementIds: Flow<Set<String>> = store.data.map { prefs ->
        prefs[SEEN_ANNOUNCEMENT_IDS] ?: emptySet()
    }.distinctUntilChanged()

    suspend fun markAnnouncementSeen(id: String) {
        store.edit { prefs ->
            val current = prefs[SEEN_ANNOUNCEMENT_IDS] ?: emptySet()
            prefs[SEEN_ANNOUNCEMENT_IDS] = current + id
        }
    }

    /** Katalog görünürlüğü değiştiğinde tetiklenir (önbellek temizliği için). */
    var onCatalogVisibilityChanged: ((addonId: Long) -> Unit)? = null

    /** Canlı TV ekranı kanal sıralaması; bilinmeyen değer varsayılan A–Z'ye düşer. */
    val liveTvSortMode: Flow<LiveTvSortMode> = store.data.map { prefs ->
        LiveTvSortMode.entries.firstOrNull { it.name == prefs[LIVE_TV_SORT_MODE] } ?: DEFAULT_LIVE_TV_SORT_MODE
    }.distinctUntilChanged()

    suspend fun setLiveTvSortMode(mode: LiveTvSortMode) {
        store.edit { it[LIVE_TV_SORT_MODE] = mode.name }
    }

    /** Ana sayfadaki "İzlemeye devam et" sıralaması; varsayılan en son izlenen. */
    val continueSortMode: Flow<ContinueSortMode> = store.data.map { prefs ->
        ContinueSortMode.entries.firstOrNull { it.name == prefs[CONTINUE_SORT_MODE] } ?: DEFAULT_CONTINUE_SORT_MODE
    }.distinctUntilChanged()

    suspend fun setContinueSortMode(mode: ContinueSortMode) {
        store.edit { it[CONTINUE_SORT_MODE] = mode.name }
    }

    /**
     * Yalnızca oynatıcı, tema, altyazı görünümü/dili, poster şablonu ve liste sıralama ayarlarını varsayılana döndürür.
     * Eklenti görünürlüğü, seed bayrakları, torrent/profil ayarları, geçmiş, favoriler ve indirmeler SİLİNMEZ.
     */
    suspend fun resetAppearanceAndPlayerDefaults() {
        store.edit { prefs ->
            APPEARANCE_AND_PLAYER_KEYS.forEach { prefs.remove(it) }
        }
    }

    /** Kullanıcının kapattığı Nuvio addon katalog anahtarları (`addonId:type:catalogId`). */
    val disabledCatalogs: Flow<Set<String>> = store.data.map { prefs ->
        prefs[DISABLED_CATALOGS] ?: emptySet()
    }.distinctUntilChanged()

    suspend fun setCatalogEnabled(addonId: Long, type: String, catalogId: String, enabled: Boolean) {
        val key = com.kaan.play.data.addon.CatalogVisibility.key(addonId, type, catalogId)
        store.edit { prefs ->
            val current = prefs[DISABLED_CATALOGS] ?: emptySet()
            val next = com.kaan.play.data.addon.CatalogVisibility.updateDisabled(current, key, enabled)
            if (next.isEmpty()) {
                prefs.remove(DISABLED_CATALOGS)
            } else {
                prefs[DISABLED_CATALOGS] = next
            }
        }
        onCatalogVisibilityChanged?.invoke(addonId)
    }

    suspend fun clearCatalogVisibilityForAddon(addonId: Long) {
        store.edit { prefs ->
            val current = prefs[DISABLED_CATALOGS] ?: emptySet()
            val next = com.kaan.play.data.addon.CatalogVisibility.cleanForAddon(current, addonId)
            if (next.isEmpty()) {
                prefs.remove(DISABLED_CATALOGS)
            } else {
                prefs[DISABLED_CATALOGS] = next
            }
        }
        onCatalogVisibilityChanged?.invoke(addonId)
    }

    companion object {
        val DEFAULT_THEME = ThemeMode.Dark
        const val DEFAULT_TIMEOUT_SECONDS = 15
        const val DEFAULT_ALWAYS_RESUME = true
        const val DEFAULT_REMEMBER_LAST_PROFILE = true
        const val DEFAULT_DOWNLOAD_WIFI_ONLY = true
        const val DEFAULT_SLOW_CONNECTION_MODE = false
        const val DEFAULT_SUGGEST_SLOW_CONNECTION = true
        const val DEFAULT_AUTO_NEXT = true
        const val DEFAULT_AUTO_TRANSLATE_SUBS = true
        const val DEFAULT_AUTO_SOURCE = true
        const val DEFAULT_PREFER_DUB = true
        const val DEFAULT_TRY_SOURCES_UNTIL_TURKISH_AUDIO = true
        const val DEFAULT_REVERT_ON_SELECTED_FAILURE = true
        const val DEFAULT_AUTO_SOURCE_RETRY_LIMIT = AutoRetryPolicy.UNLIMITED
        val AUTO_SOURCE_RETRY_OPTIONS = AutoRetryPolicy.OPTIONS
        const val DEFAULT_SHOW_DUB_SUGGESTION = true
        const val DEFAULT_AUDIO_PROBE_TIMEOUT_SECONDS = 7
        val AUDIO_PROBE_TIMEOUT_OPTIONS = listOf(5, 7, 10)
        const val DEFAULT_AUTO_NEXT_SECONDS = 10
        val DEFAULT_LIVE_TV_SORT_MODE = LiveTvSortMode.TitleAscending
        val DEFAULT_CONTINUE_SORT_MODE = ContinueSortMode.Recent
        val AUTO_NEXT_OPTIONS = listOf(5, 10, 15)
        val TIMEOUT_OPTIONS = listOf(10, 15, 30, 60)
        const val DEFAULT_SUBTITLE_LANGUAGE = "tr"
        val SUBTITLE_LANGUAGES = listOf("tr", "en")

        private val THEME = stringPreferencesKey("theme_mode")
        private val POSTER_URL_TEMPLATE = stringPreferencesKey("poster_url_template")
        private val TIMEOUT = intPreferencesKey("source_timeout_seconds")
        private val ALWAYS_RESUME = booleanPreferencesKey("always_resume")
        private val REMEMBER_LAST_PROFILE = booleanPreferencesKey("remember_last_profile")
        private val TORRENT_SERVER = stringPreferencesKey("torrent_server")
        private val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        private val SLOW_CONNECTION_MODE = booleanPreferencesKey("slow_connection_mode")
        private val SUGGEST_SLOW_CONNECTION = booleanPreferencesKey("suggest_slow_connection")
        private val AUTO_NEXT = booleanPreferencesKey("auto_next")
        private val AUTO_TRANSLATE_SUBS = booleanPreferencesKey("auto_translate_subs")
        private val AUTO_SOURCE = booleanPreferencesKey("auto_source")
        private val PREFER_DUB = booleanPreferencesKey("prefer_dub")
        private val TRY_SOURCES_UNTIL_TURKISH_AUDIO = booleanPreferencesKey("try_sources_until_turkish_audio")
        private val REVERT_ON_SELECTED_FAILURE = booleanPreferencesKey("revert_on_selected_failure")
        private val AUTO_SOURCE_RETRY_LIMIT = intPreferencesKey("auto_source_retry_limit")
        private val SHOW_DUB_SUGGESTION = booleanPreferencesKey("show_dub_suggestion")
        private val AUDIO_PROBE_TIMEOUT_SECONDS = intPreferencesKey("audio_probe_timeout_seconds")
        private val RESIZE_MODE = stringPreferencesKey("resize_mode")
        private val AUTO_NEXT_SECONDS = intPreferencesKey("auto_next_seconds")
        private val SKIP_BUTTONS_ENABLED = booleanPreferencesKey("skip_buttons_enabled")
        private val SKIP_AUTO_ENABLED = booleanPreferencesKey("skip_auto_enabled")
        private val SKIP_ENABLED_CATEGORIES = stringSetPreferencesKey("skip_enabled_categories")
        private val SUB_SIZE = stringPreferencesKey("subtitle_size")
        private val SUB_BOLD = booleanPreferencesKey("subtitle_bold")
        private val SUB_ITALIC = booleanPreferencesKey("subtitle_italic")
        private val SUB_COLOR = stringPreferencesKey("subtitle_color")
        private val SUB_BACKGROUND = stringPreferencesKey("subtitle_background")
        private val SUB_LANGUAGE = stringPreferencesKey("subtitle_language")
        val DEFAULT_SUBTITLE_ADDONS_SEEDED = booleanPreferencesKey("default_subtitle_addons_seeded")
        /** Play Özel Kataloglar addon'unun ilk kurulumda eklendiğini gösteren ayrı bayrak (kullanıcı kararı, 2026-10-21). */
        val DEFAULT_CATALOG_ADDON_SEEDED = booleanPreferencesKey("default_catalog_addon_seeded")
        val SEEN_ANNOUNCEMENT_IDS = stringSetPreferencesKey("seen_announcement_ids")
        private val DISABLED_CATALOGS = stringSetPreferencesKey("disabled_catalogs")
        private val LIVE_TV_SORT_MODE = stringPreferencesKey("live_tv_sort_mode")
        private val CONTINUE_SORT_MODE = stringPreferencesKey("continue_sort_mode")

        private val APPEARANCE_AND_PLAYER_KEYS: List<Preferences.Key<*>> = listOf(
            THEME,
            POSTER_URL_TEMPLATE,
            ALWAYS_RESUME,
            DOWNLOAD_WIFI_ONLY,
            SLOW_CONNECTION_MODE,
            SUGGEST_SLOW_CONNECTION,
            AUTO_NEXT,
            AUTO_TRANSLATE_SUBS,
            AUTO_SOURCE,
            PREFER_DUB,
            TRY_SOURCES_UNTIL_TURKISH_AUDIO,
            REVERT_ON_SELECTED_FAILURE,
            AUTO_SOURCE_RETRY_LIMIT,
            SHOW_DUB_SUGGESTION,
            AUDIO_PROBE_TIMEOUT_SECONDS,
            RESIZE_MODE,
            AUTO_NEXT_SECONDS,
            SKIP_BUTTONS_ENABLED,
            SKIP_AUTO_ENABLED,
            SKIP_ENABLED_CATEGORIES,
            SUB_SIZE,
            SUB_BOLD,
            SUB_ITALIC,
            SUB_COLOR,
            SUB_BACKGROUND,
            SUB_LANGUAGE,
            LIVE_TV_SORT_MODE,
            CONTINUE_SORT_MODE,
        )
    }
}
