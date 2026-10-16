package com.kaan.play

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.kaan.play.data.addon.AddonRepository
import com.kaan.play.data.addon.OkHttpManifestFetcher
import com.kaan.play.data.db.PlayDatabase
import com.kaan.play.data.download.DownloadCenter
import com.kaan.play.data.download.DownloadFiles
import com.kaan.play.data.download.DownloadSubtitleFetcher
import com.kaan.play.data.download.PlayDownloadService
import com.kaan.play.data.library.ContinueWatching
import com.kaan.play.data.library.LibraryRepository
import com.kaan.play.data.library.NextEpisodeResolver
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.cloudstream.CsDexLoader
import com.kaan.play.data.cloudstream.CsDexStore
import com.kaan.play.data.cloudstream.CsRepository
import com.kaan.play.data.cloudstream.CsCatalogRepository
import com.kaan.play.data.cloudstream.CsRuntime
import com.kaan.play.data.cloudstream.CsStreamProvider
import com.kaan.play.data.plugin.FilePluginCodeStore
import com.kaan.play.data.plugin.OkHttpPluginCodeFetcher
import com.kaan.play.data.plugin.PluginRepository
import com.kaan.play.data.plugin.PluginSettingsRepository
import com.kaan.play.data.plugin.PluginStreamProvider
import com.kaan.play.data.plugin.DeepLinkHandler
import com.kaan.play.data.plugin.SourceInstaller
import com.kaan.play.data.plugin.runtime.PluginRuntime
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.stream.AddonStreamProvider
import com.kaan.play.data.stream.CompositeStreamProvider
import com.kaan.play.data.stream.SizeProbe
import com.kaan.play.data.stream.SourceMemory
import com.kaan.play.data.stream.TorrentStreamProvider
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.subtitle.AddonSubtitleProvider
import com.kaan.play.data.subtitle.CompositeSubtitleProvider
import com.kaan.play.data.subtitle.OnlineSubtitleProvider
import com.kaan.play.data.subtitle.SubdlSubtitleProvider
import com.kaan.play.data.subtitle.SubtitleLoader
import com.kaan.play.data.subtitle.SubtitleSearchCache
import com.kaan.play.data.subtitle.SubtitleMemory
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.translate.MlKitTranslationEngine
import com.kaan.play.data.subtitle.translate.TranslatedSubtitleStore
import com.kaan.play.data.subtitle.translate.TranslationEngine
import com.kaan.play.data.tmdb.TmdbRepository
import com.kaan.play.data.torrent.TorrentEngine
import com.kaan.play.data.torrent.TorrentHttpServer
import com.kaan.play.data.web.WebSiteRepository
import com.kaan.play.ui.web.AndroidWebDataCleaner
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.data.update.AndroidInstallPlatform
import com.kaan.play.data.update.DownloadLauncher
import com.kaan.play.data.update.UpdateDownloadService
import com.kaan.play.data.update.UpdateChecker
import com.kaan.play.data.update.UpdateDownloader
import com.kaan.play.data.update.UpdateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Elle kurulan bağımlılık kabı. DI framework'ü yok; katmanlar eklendikçe
 * (DataStore, StreamProvider) buraya tembel alanlar olarak eklenir.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val json: Json = PlayJson

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        // CloudStream eklentileri `app.get()/post()` için bu global'i bekler (bkz. App.kt).
        com.lagradost.cloudstream3.initApp(httpClient)
    }

    /** Dil her istekte [AppLanguage.effective] ile okunur: Ayarlar'da dil değişince yeni istekler yeni dilde gider. */
    val tmdb: TmdbRepository by lazy {
        TmdbRepository(httpClient, json, BuildConfig.TMDB_API_KEY, language = AppLanguage::effective)
    }

    val database: PlayDatabase by lazy {
        Room.databaseBuilder(appContext, PlayDatabase::class.java, "play.db")
            .addMigrations(PlayDatabase.MIGRATION_1_2, PlayDatabase.MIGRATION_2_3, PlayDatabase.MIGRATION_3_4)
            .build()
    }

    val settings: SettingsRepository by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") })
    }

    /** Elle seçilen kaynak (film/dizi başına): sonraki açılışta ve dizinin sonraki bölümünde önce denenir. Ayarlarla aynı DataStore. */
    val sourceMemory: SourceMemory by lazy { SourceMemory(settings.store) }

    /** Doğrudan HTTP kaynakların dosya boyutu (HEAD ile; hata sessizce geçilir). */
    val sizeProbe: SizeProbe by lazy { SizeProbe(httpClient) }

    val library: LibraryRepository by lazy {
        LibraryRepository(database.watchHistoryDao(), database.favoriteDao())
    }

    /** Etkinliklerden bağımsız işler için (ör. oynatıcı kapanırken konum kaydı tamamlansın). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Bir bölümden sonraki izlenebilir bölüm (ana sayfa "devam et" ve oynatıcının otomatik sonraki bölümü ortak kullanır). */
    val nextEpisodes: NextEpisodeResolver by lazy {
        NextEpisodeResolver(
            loadSeason = { tvId, season -> tmdb.season(tvId, season) },
            today = { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
        )
    }

    val continueWatching: ContinueWatching by lazy {
        ContinueWatching(library) { row -> nextEpisodes.next(row.tmdbId, row.season, row.episode) }
    }

    val addons: AddonRepository by lazy {
        AddonRepository(database.addonDao(), OkHttpManifestFetcher(httpClient))
    }

    /** Eklenti ayarları (`onSettings` ile bildirilen değerler), eklenti başına; ayrı bir DataStore dosyasında. */
    val pluginSettings: PluginSettingsRepository by lazy {
        PluginSettingsRepository(PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("plugin_settings") })
    }

    /** Nuvio eklenti depoları (JS kazıyıcılar; Faz 2). Kodlar uygulamaya özel `filesDir/plugins` içinde durur. */
    val plugins: PluginRepository by lazy {
        PluginRepository(
            addonDao = database.addonDao(),
            pluginDao = database.pluginDao(),
            fetcher = OkHttpManifestFetcher(httpClient),
            codeFetcher = OkHttpPluginCodeFetcher(httpClient),
            store = FilePluginCodeStore(File(appContext.filesDir, "plugins")),
            onPluginRemoved = { pluginSettings.clear(it) },
        )
    }

    /** CloudStream .cs3 eklenti DEX dosyaları: `filesDir/cloudstream/`. */
    val csDexStore: CsDexStore by lazy { CsDexStore(File(appContext.filesDir, "cloudstream")) }

    /** CloudStream DEX yükleyicisi (PathClassLoader; tek seferde bir yükleme). */
    val csDexLoader: CsDexLoader by lazy { CsDexLoader(appContext) }

    /** CloudStream eklenti çalıştırıcısı (search → load → loadLinks akışı). */
    val csRuntime: CsRuntime by lazy { CsRuntime() }

    /** CloudStream depo yöneticisi (Faz 3). Depo satırı `addons`, eklentiler `plugins` tablosunda. */
    val csRepos: CsRepository by lazy {
        CsRepository(
            addonDao = database.addonDao(),
            pluginDao = database.pluginDao(),
            fetcher = OkHttpManifestFetcher(httpClient),
            httpClient = httpClient,
            store = csDexStore,
            loader = csDexLoader,
        )
    }

    /** CloudStream ana sayfa kataloglarını yükler; mevcut akış oynatımı ve Nuvio'dan bağımsızdır. */
    val csCatalogs: CsCatalogRepository by lazy { CsCatalogRepository(csRepos) }

    /** "Eklenti ekle": adresin içeriğine bakıp addon mu eklenti deposu mu CloudStream deposu mu olduğunu kendisi anlar. */
    val sourceInstaller: SourceInstaller by lazy { SourceInstaller(addons, plugins, csRepos, OkHttpManifestFetcher(httpClient)) }

    /**
     * JS kazıyıcı çalıştırıcısı (QuickJS). Gerçek TMDB anahtarı yalnızca ağ köprüsüne verilir, eklentilere yer tutucu görünür.
     * Aynı anda en çok [PluginStreamProvider.MAX_PARALLEL] eklenti çalışır (her biri ayrı bir QuickJS örneği).
     */
    val pluginRuntime: PluginRuntime by lazy {
        PluginRuntime(
            httpClient, BuildConfig.TMDB_API_KEY,
            maxConcurrent = PluginStreamProvider.MAX_PARALLEL,
            log = { level, message -> Log.d("PlayPlugin", "[$level] $message") },
        )
    }

    /** Kullanıcının eklediği web siteleri (reklamsız web görünümü). */
    val webSites: WebSiteRepository by lazy {
        WebSiteRepository(database.addonDao(), AndroidWebDataCleaner(appContext))
    }

    /**
     * Kaynaklar tek birleşik sağlayıcıdan gelir: aktif addon'lar ([AddonStreamProvider]),
     * açık Nuvio JS eklentileri ([PluginStreamProvider]) ve açık CS .cs3 eklentileri ([CsStreamProvider]).
     * Zaman aşımı Ayarlar'daki ortak kaynak zaman aşımıdır; her istekte yeniden okunur.
     */
    val streams: StreamProvider by lazy {
        TorrentStreamProvider(rawStreams, server = { settings.torrentServer.first() }, onBuiltIn = torrentEngine::warmUp)
    }

    /**
     * Yerleşik torrent motoru (libtorrent4j): `magnet:`/infoHash kaynakları, kullanıcı torrent sunucusu girmediyse bununla oynar.
     * Geçici veriler uygulamaya özel önbellek dizininde durur ve son oynatıcı kapanınca silinir.
     */
    val torrentEngine: TorrentEngine by lazy {
        TorrentEngine(File(appContext.externalCacheDir ?: appContext.cacheDir, "torrents"))
    }

    /** Torrent dosyasını oynatıcıya `127.0.0.1` üzerinden http olarak veren yerel sunucu. */
    val torrentServer: TorrentHttpServer by lazy { TorrentHttpServer(torrentEngine) }

    private val rawStreams: StreamProvider by lazy {
        CompositeStreamProvider(
            AddonStreamProvider(addons = addons::enabledAddons, client = httpClient, timeout = sourceTimeout),
            PluginStreamProvider(
                plugins = plugins::enabledPlugins,
                code = plugins::loadCode,
                runner = { code, request, scraperId, timeoutMs, saved -> pluginRuntime.run(code, request, scraperId, timeoutMs, saved) },
                settings = pluginSettings::get,
                timeout = sourceTimeout,
            ),
            CsStreamProvider(
                repository = csRepos,
                runtime = csRuntime,
                titleResolver = { req -> runCatching { tmdb.detail(req.type, req.tmdbId).summary.title }.getOrNull() },
                timeoutMs = 15_000L,
            ),
        )
    }

    private val sourceTimeout: suspend () -> kotlin.time.Duration = { settings.sourceTimeoutSeconds.first().seconds }

    /**
     * Altyazı (Faz 4/A): `subtitles` kaynağı olan aktif addon'lar paralel sorulur (zaman aşımı kaynaklarla ortak). Yalnızca altyazı
     * veren addon'lar kaynak listesinde görünmez; eklentilerin ve akışın kendi altyazıları seçilen akışla gelir.
     */
    /** Altyazı arama sonuçları (film/bölüm bazında): aynı içerik tekrar açılınca sağlayıcılara yeniden sorulmaz. */
    val subtitleSearchCache: SubtitleSearchCache by lazy { SubtitleSearchCache(File(appContext.cacheDir, "subtitle-search")) }

    val subtitleProvider: SubtitleProvider by lazy {
        val languages: suspend () -> List<String> = { listOf(settings.subtitleLanguage.first(), "en") }
        val imdbFor: suspend (com.kaan.play.data.stream.StreamRequest) -> String? = { req -> tmdb.detail(req.type, req.tmdbId).imdbId }
        CompositeSubtitleProvider(
            listOf(
                AddonSubtitleProvider(addons = addons::enabledAddons, client = httpClient, timeout = sourceTimeout),
                // Yerleşik çevrimiçi arama, IMDb (+ sezon/bölüm) ile; sıra: önce SubDL (anahtar yoksa sessizce atlanır), sonra OpenSubtitles.
                // Tercih edilen dil önce, sonra İngilizce.
                SubdlSubtitleProvider(
                    client = httpClient,
                    apiKey = BuildConfig.SUBDL_API_KEY,
                    languages = languages,
                    imdbFor = imdbFor,
                    cache = subtitleSearchCache,
                ),
                OnlineSubtitleProvider(
                    client = httpClient,
                    languages = languages,
                    imdbFor = imdbFor,
                    cache = subtitleSearchCache,
                ),
            ),
        )
    }

    /**
     * Altyazıyı kendimiz indirir: kodlama UTF-8'e çevrilip uygulamanın önbellek dizinine yazılır (sistem boşaltabilir).
     * Kullanıcının dosyadan eklediği altyazı (`content://`) ContentResolver ile okunur.
     */
    val subtitleLoader: SubtitleLoader by lazy {
        SubtitleLoader(httpClient, File(appContext.cacheDir, "subtitles"), openLocal = { uri, limit ->
            appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (out.size() < limit) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        })
    }

    /** Çevrilmiş (İngilizce -> Türkçe) altyazıların önbelleği: `cacheDir/subtitles/tr-*.srt`. */
    val translatedSubtitles: TranslatedSubtitleStore by lazy { TranslatedSubtitleStore(File(appContext.cacheDir, "subtitles")) }

    /** Cihaz üstü çeviri (ML Kit); metin sunucuya gitmez. Model ilk kullanımda iner ("yalnızca Wi-Fi" ayarına uyar). */
    val translationEngine: TranslationEngine by lazy {
        MlKitTranslationEngine(
            context = appContext,
            enabledSetting = { settings.autoTranslateSubtitles.first() },
            wifiOnlySetting = { settings.downloadWifiOnly.first() },
        )
    }

    /** Seçilen altyazı dili (film/dizi başına) ve elle senkron kaydırması (içerik + altyazı başına). */
    val subtitleMemory: SubtitleMemory by lazy { SubtitleMemory(database.subtitleDao()) }

    /**
     * Çevrimdışı izleme (Faz 4/B): Media3 indirme yöneticisi + tek `SimpleCache`, Room `downloads` ile eşli. Uygulama açılırken
     * ([PlayApp]) oluşturulur ki yarım kalan indirmeler kendiliğinden devam etsin. Altyazılar kaynak listesindeki kurallarla
     * (uygulama dili + İngilizce + kullanıcının seçtiği) indirme sırasında saklanır.
     */
    val downloads: DownloadCenter by lazy {
        val files = DownloadFiles.forContext(appContext)
        DownloadCenter(
            context = appContext,
            dao = database.downloadDao(),
            files = files,
            client = httpClient,
            settings = settings,
            subtitleFetcher = DownloadSubtitleFetcher(
                loader = subtitleLoader,
                provider = subtitleProvider,
                memory = subtitleMemory,
                appLanguage = { settings.subtitleLanguage.first() },
                files = files,
            ),
            scope = appScope,
            serviceClass = PlayDownloadService::class.java,
        ).also { it.begin() }
    }

    /**
     * Oynatılabilecek en az bir açık kaynak (akış veren addon, Nuvio eklentisi veya CS eklentisi) var mı?
     * Yoksa Oynat "eklenti ekle"ye götürür. Yalnızca altyazı veren bir addon kaynak sayılmaz.
     */
    suspend fun hasEnabledSources(): Boolean =
        addons.enabledAddons().any { it.manifest.providesStreams } ||
            plugins.enabledPlugins().isNotEmpty() ||
            csRepos.enabledPlugins().isNotEmpty()

    /** Ana sayfa boş durumu için: hiç akış addon'u, Nuvio deposu veya CS deposu yok mu? (null: henüz yüklenmedi). */
    val hasAnySource: Flow<Boolean> by lazy {
        combine(addons.addons, plugins.repos, csRepos.repos) { a, r, cs ->
            a.any { it.manifest.providesStreams } || r.isNotEmpty() || cs.isNotEmpty()
        }
    }

    /**
     * Dışarıdan (https eklenti bağlantısı) gelen addon adresi. Etkinlik yazar,
     * [com.kaan.play.ui.addons.AddonsViewModel] alıp sıfırlar.
     */
    val pendingAddonUrl = MutableStateFlow<String?>(null)

    /** `play://add?repo=` / `play://add?nuvio=` bağlantıları: mevcut [sourceInstaller] ile ekler, sonucu Snackbar'a iletir. */
    val deepLinks: DeepLinkHandler by lazy { DeepLinkHandler(sourceInstaller, appScope) }

    /** Uygulama içi güncelleme kontrolü (GitHub releases/latest); oturumda bir kez, etkinlik yeniden oluşsa da tekrarlanmaz. */
    val updateChecker: UpdateChecker by lazy {
        UpdateChecker(httpClient, json, BuildConfig.VERSION_NAME, abis = Build.SUPPORTED_ABIS.toList())
    }

    /** Uygulama içi güncelleme akışı: indir, SHA-256 doğrula, sistem kurulum ekranını aç (bkz. UpdateFlow). */
    val updateFlow: UpdateFlow by lazy {
        lateinit var flow: UpdateFlow
        flow = UpdateFlow(
            checker = updateChecker,
            downloader = UpdateDownloader(httpClient) { updateChecker.debugHost },
            platform = AndroidInstallPlatform(appContext),
            dir = File(appContext.cacheDir, "updates"),
            scope = appScope,
            // İndirme ön plan servisinde (bildirimde ilerleme, uygulamadan çıkılsa da sürer); servis başlatılamazsa doğrudan sürer.
            launcher = DownloadLauncher { info ->
                if (!UpdateDownloadService.start(appContext, info)) flow.launchDownload(info, appScope)
            },
        )
        flow
    }
}
