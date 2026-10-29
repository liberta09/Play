package com.kaan.play.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

    /** Açıkken uygulama açılışında son aktif profil otomatik seçilir; varsayılan kapalıdır. */
    val rememberLastProfile: Flow<Boolean> = store.data.map { it[REMEMBER_LAST_PROFILE] ?: DEFAULT_REMEMBER_LAST_PROFILE }.distinctUntilChanged()

    suspend fun setRememberLastProfile(enabled: Boolean) {
        store.edit { it[REMEMBER_LAST_PROFILE] = enabled }
    }

    /** Açıkken film/bölüm açılınca kaynak listesi beklenmez: uygun ilk kaynakla oynatma başlar. Varsayılan açık. */
    val autoSource: Flow<Boolean> = store.data.map { it[AUTO_SOURCE] ?: DEFAULT_AUTO_SOURCE }.distinctUntilChanged()

    /** Açıkken Türkçe dublajlı kaynaklar önce denenir (dublaj yoksa altyazılıyla başlanır). Varsayılan açık. */
    val preferDub: Flow<Boolean> = store.data.map { it[PREFER_DUB] ?: DEFAULT_PREFER_DUB }.distinctUntilChanged()

    suspend fun setAutoSource(enabled: Boolean) {
        store.edit { it[AUTO_SOURCE] = enabled }
    }

    suspend fun setPreferDub(enabled: Boolean) {
        store.edit { it[PREFER_DUB] = enabled }
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

    /** Skip controls are on by default; automatic seeking remains opt-in and off by default. */
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
        const val DEFAULT_REMEMBER_LAST_PROFILE = false
        const val DEFAULT_DOWNLOAD_WIFI_ONLY = true
        const val DEFAULT_AUTO_NEXT = true
        const val DEFAULT_AUTO_TRANSLATE_SUBS = true
        const val DEFAULT_AUTO_SOURCE = true
        const val DEFAULT_PREFER_DUB = true
        const val DEFAULT_AUTO_NEXT_SECONDS = 10
        val AUTO_NEXT_OPTIONS = listOf(5, 10, 15)
        val TIMEOUT_OPTIONS = listOf(10, 15, 30, 60)
        const val DEFAULT_SUBTITLE_LANGUAGE = "tr"
        val SUBTITLE_LANGUAGES = listOf("tr", "en")

        private val THEME = stringPreferencesKey("theme_mode")
        private val POSTER_URL_TEMPLATE = stringPreferencesKey("poster_url_template")
        private val TIMEOUT = intPreferencesKey("source_timeout_seconds")
        private val ALWAYS_RESUME = booleanPreferencesKey("always_resume")
        // v2: Önceki sürümlerde açık kalan değer, güncelleme sonrası ilk açılışta profil ekranını atlamasın.
        private val REMEMBER_LAST_PROFILE = booleanPreferencesKey("remember_last_profile_v2")
        private val TORRENT_SERVER = stringPreferencesKey("torrent_server")
        private val DOWNLOAD_WIFI_ONLY = booleanPreferencesKey("download_wifi_only")
        private val AUTO_NEXT = booleanPreferencesKey("auto_next")
        private val AUTO_TRANSLATE_SUBS = booleanPreferencesKey("auto_translate_subs")
        private val AUTO_SOURCE = booleanPreferencesKey("auto_source")
        private val PREFER_DUB = booleanPreferencesKey("prefer_dub")
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
    }
}
