package com.kaan.play.data.diag

import android.content.Context
import android.util.Log
import com.kaan.play.AppContainer
import com.kaan.play.BuildConfig
import com.kaan.play.data.addon.DefaultSubtitleAddons
import com.kaan.play.data.cloudstream.CsNextEpisodeFinder
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.plugin.runtime.PluginOutcome
import com.kaan.play.data.plugin.runtime.PluginRequest
import com.kaan.play.data.plugin.runtime.toPlayable
import com.kaan.play.data.stream.AddonStreamProvider
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.subtitle.AddonSubtitleProvider
import com.kaan.play.data.subtitle.OnlineSubtitleProvider
import com.kaan.play.data.subtitle.SubtitleLoad
import com.kaan.play.data.subtitle.SubtitleResult
import com.kaan.play.data.subtitle.SubtitleTrack
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private const val TAG = "PlayDiag"
/** Katalog adımı: uygulama (CsCatalog) 27 sn tanıyor; DEX yükleme + Cloudflare çözücü 6 sn içinde bitmiyor. */
private const val CATALOG_TIMEOUT_MS = 30_000L

/**
 * Play Otomatik Duman Testi ve Oynatma Tanısı Çalıştırıcısı.
 *
 * Kapsam:
 * a) CloudStream: açık her eklenti (en çok 4 paralel) -> katalog -> load -> bölüm -> loadLinks -> medya (~512KB) -> eklenti altyazısı.
 * b) Nuvio addon: akış -> medya imzası (~512KB); torrent akışlar "torrent, atlandı".
 * c) Nuvio JS eklentileri: sabit TMDB filmi (tt0137523) ve dizi bölümü (tt0903747 S1B1) ile akış -> medya.
 * d) Altyazı: AltyazıDB, AltyazıTR ve yerleşik çevrimiçi arama (tt0137523) Türkçe sonuç ve ilk altyazı indirme/ayrıştırma.
 * e) Devam et: açık ilk CloudStream dizisinde CsNextEpisodeFinder S1B1'den sonraki bölümü bulma (dataUrl farkı).
 * f) Güncelleme ve duyuru: GitHub releases/latest ve announcements.json okunup ayrıştırılması (yalnızca okuma).
 *
 * Güvenlik ve Gizlilik:
 * - Hiçbir URL, IP, çerez, anahtar veya token dosya ve loga yazılmaz.
 * - Tanı kodu yalnızca debug derlemede çalışır.
 */
object PlayDiagRunner {

    data class CsDiagResult(
        val pluginName: String,
        val catalogStatus: String,
        val loadStatus: String,
        val episodeStatus: String,
        val linkStatus: String,
        val mediaStatus: String,
        val subPluginStatus: String,
        val status: String,
        val errorReason: String,
    )

    data class NuvioAddonDiagResult(
        val name: String,
        val kind: String,
        val streamStatus: String,
        val mediaStatus: String,
        val subSourceStatus: String,
        val status: String,
        val errorReason: String,
    )

    data class NuvioJsDiagResult(
        val name: String,
        val target: String,
        val streamStatus: String,
        val mediaStatus: String,
        val subSourceStatus: String,
        val status: String,
        val category: String = "-",
        val errorReason: String,
    )

    data class SubtitleDiagResult(
        val name: String,
        val target: String,
        val searchStatus: String,
        val parseStatus: String,
        val status: String,
        val errorReason: String,
    )

    data class ContinueDiagResult(
        val name: String,
        val target: String,
        val details: String,
        val status: String,
        val errorReason: String,
    )

    data class UpdateAnnounceDiagResult(
        val name: String,
        val details: String,
        val status: String,
        val errorReason: String,
    )

    suspend fun runDiagnostics(
        context: Context,
        container: AppContainer,
        filterNames: List<String>? = null,
    ): File = withContext(Dispatchers.IO) {
        Log.i(TAG, "=== Play Otomatik Duman Testi (Smoke Test) Başlatıldı ===")

        val outDir = File(context.filesDir, "diag")
        if (!outDir.exists()) outDir.mkdirs()
        val jsonFile = File(outDir, "smoke_test.json")
        val mdFile = File(outDir, "smoke-test.md")
        val oldMdFile = File(outDir, "oynatma-tanisi.md")

        // 1. Gerekli varsayılan depoları ve addon'ları etkinleştir
        val defaultRepos = listOf(
            "https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json",
            "https://raw.githubusercontent.com/Wiojelt/WioSinema/builds/repo.json",
            "https://raw.githubusercontent.com/csprofesor/gsrepo/builds/repo.json",
        )
        for (repoUrl in defaultRepos) {
            try {
                if (container.database.addonDao().countBySourceUrl(repoUrl) == 0) {
                    Log.i(TAG, "Depo kuruluyor: $repoUrl")
                    container.sourceInstaller.add(repoUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Depo kurulumu atlandı: $repoUrl (${e.message})")
            }
        }
        runCatching { container.csRepos.setAllEnabled(true) }

        // Nuvio eklenti ve altyazı addon'larını aktif yap
        runCatching {
            container.database.addonDao().setAllEnabledOfType(AddonType.NUVIO_ADDON, true)
            container.database.addonDao().setAllEnabledOfType(AddonType.NUVIO_PLUGIN_REPO, true)
            container.database.pluginDao().setAllEnabled(true)
        }

        // Varsayılan altyazı addon'larını tohumla (AltyazıDB ve AltyazıTR)
        runCatching {
            DefaultSubtitleAddons.seedIfNeeded(container.database.addonDao(), container.settings.store)
            container.database.addonDao().setAllEnabledOfType(AddonType.NUVIO_ADDON, true)
        }

        val allInstalled = runCatching {
            container.database.pluginDao().observeOfType(AddonType.CLOUDSTREAM_REPO).first().map {
                InstalledCsPlugin(
                    id = it.id,
                    repoId = it.repoId,
                    pluginId = it.pluginId,
                    name = it.name,
                    version = it.version,
                    description = it.description,
                    language = it.contentLanguage,
                    tvTypes = it.supportedTypes.split(',').filter { s -> s.isNotBlank() },
                    enabled = it.enabled,
                )
            }
        }.getOrDefault(emptyList())

        val allPlugins = if (!filterNames.isNullOrEmpty()) {
            allInstalled
        } else {
            val enabled = container.csRepos.enabledPlugins()
            if (enabled.isNotEmpty()) enabled else allInstalled
        }

        val repoMap = runCatching {
            container.database.addonDao().enabledOfType(AddonType.CLOUDSTREAM_REPO).associate { it.id to it.name }
        }.getOrDefault(emptyMap())

        val preferredRepos = mapOf(
            "AnimeciX" to "gsrepo",
            "Anizm" to "gsrepo",
            "BelgeselX" to "gsrepo",
            "DiziGom" to "gsrepo",
            "DiziKorea" to "gsrepo",
            "FullHDFilm" to "gsrepo",
            "SetFilmIzle" to "gsrepo",
            "SinemaCX" to "gsrepo",
            "TavsiyeFilmIzle" to "gsrepo",
            "SinemaTV" to "WioSinema",
            "Watch2Movies" to "gsrepo",
        )

        val targetPlugins = if (!filterNames.isNullOrEmpty()) {
            filterNames.mapNotNull { f ->
                val candidates = allPlugins.filter { p -> p.name.contains(f, ignoreCase = true) || p.pluginId.contains(f, ignoreCase = true) }
                if (candidates.isEmpty()) null
                else {
                    val prefKey = preferredRepos.keys.firstOrNull { it.contains(f, ignoreCase = true) || f.contains(it, ignoreCase = true) }
                    val targetRepo = if (prefKey != null) preferredRepos[prefKey] else "TurkSinema"
                    candidates.firstOrNull { repoMap[it.repoId]?.contains(targetRepo ?: "", ignoreCase = true) == true }
                        ?: candidates.firstOrNull { repoMap[it.repoId]?.contains("TurkSinema", ignoreCase = true) == true }
                        ?: candidates.first()
                }
            }
        } else {
            val standardNames = listOf(
                "AnimeciX", "Anizm", "BelgeselX", "DDizi", "DiziBox",
                "DiziGom", "DiziKorea", "Dizilla", "DiziMag", "DiziMom",
                "DiziPal", "FilmMakinesi", "FilmModu", "FullHDFilm", "HDFilmCehennemi",
                "HDFilmIzle", "HintFilmIzle", "JetFilmizle", "KultFilmler", "SelcukFlix",
                "SetFilmIzle", "SezonlukDizi", "SinemaCX", "SinemaTV", "SineWix",
                "TavsiyeFilmIzle", "Watch2Movies", "WebteIzle", "YabanciDizi"
            )
            standardNames.mapNotNull { stdName ->
                val candidates = allPlugins.filter { it.name.equals(stdName, ignoreCase = true) }
                if (candidates.isEmpty()) null
                else {
                    val targetRepo = preferredRepos[stdName] ?: "TurkSinema"
                    candidates.firstOrNull { repoMap[it.repoId]?.contains(targetRepo, ignoreCase = true) == true }
                        ?: candidates.firstOrNull { repoMap[it.repoId]?.contains("TurkSinema", ignoreCase = true) == true }
                        ?: candidates.first()
                }
            }
        }

        Log.i(TAG, "Test edilecek CloudStream eklenti sayısı: ${targetPlugins.size}")

        // -------------------------------------------------------------------------
        // (a) CloudStream: Açık eklentiler (en çok 4 paralel)
        // -------------------------------------------------------------------------
        val semaphore = Semaphore(4)
        val firstPass = coroutineScope {
            targetPlugins.map { plugin ->
                async {
                    semaphore.withPermit {
                        testPlugin(container, plugin)
                    }
                }
            }.awaitAll()
        }
        // Paralel turda başarısız olanlar bir kez SIRAYLA yeniden denenir: aynı anda 4 site + ortak Cloudflare
        // çözücü/DEX yükleyici yüzünden çıkan yanlış "zaman aşımı"lar tabloya gerileme gibi düşmesin.
        val csResults = firstPass.mapIndexed { i, result ->
            if (result.status == "OK") return@mapIndexed result
            val retry = testPlugin(container, targetPlugins[i])
            if (retry.status == "OK") retry.copy(status = "OK", errorReason = "2. denemede") else retry
        }

        savePartialJson(jsonFile, csResults = csResults)

        // -------------------------------------------------------------------------
        // (b) Nuvio Video Addon'ları
        // -------------------------------------------------------------------------
        val addonResults = testNuvioAddons(container)
        savePartialJson(jsonFile, csResults = csResults, addonResults = addonResults)

        // -------------------------------------------------------------------------
        // (c) Nuvio JS Eklentileri (Scrapers)
        // -------------------------------------------------------------------------
        val jsResults = testNuvioJsPlugins(container)
        savePartialJson(jsonFile, csResults = csResults, addonResults = addonResults, jsResults = jsResults)

        // -------------------------------------------------------------------------
        // (d) Altyazı Sağlayıcıları (AltyazıDB, AltyazıTR, Yerleşik Çevrimiçi Arama)
        // -------------------------------------------------------------------------
        val subResults = testSubtitles(container)
        savePartialJson(jsonFile, csResults = csResults, addonResults = addonResults, jsResults = jsResults, subResults = subResults)

        // -------------------------------------------------------------------------
        // (e) Devam Et: CsNextEpisodeFinder testi (S1B1 -> sonraki bölüm)
        // -------------------------------------------------------------------------
        val continueResult = testCsNextEpisode(container, targetPlugins.ifEmpty { allPlugins })
        savePartialJson(jsonFile, csResults = csResults, addonResults = addonResults, jsResults = jsResults, subResults = subResults, continueResult = continueResult)

        // -------------------------------------------------------------------------
        // (f) Güncelleme ve Duyuru (GitHub releases/latest ve announcements.json)
        // -------------------------------------------------------------------------
        val updateResults = testUpdatesAndAnnouncements(container)

        // Nihai JSON kaydı
        val finalJson = buildSmokeTestJson(
            versionName = BuildConfig.VERSION_NAME,
            csResults = csResults,
            addonResults = addonResults,
            jsResults = jsResults,
            subResults = subResults,
            continueResult = continueResult,
            updateResults = updateResults,
        )
        jsonFile.writeText(finalJson, Charsets.UTF_8)

        // Markdown oluştur ve cihazda kaydet
        val md = buildMarkdown(
            versionName = BuildConfig.VERSION_NAME,
            csResults = csResults,
            addonResults = addonResults,
            jsResults = jsResults,
            subResults = subResults,
            continueResult = continueResult,
            updateResults = updateResults,
        )
        mdFile.writeText(md, Charsets.UTF_8)
        oldMdFile.writeText(md, Charsets.UTF_8)

        Log.i(TAG, "=== Play Otomatik Duman Testi Tamamlandı ===")
        Log.i(TAG, "JSON: ${jsonFile.absolutePath}")
        Log.i(TAG, "SMOKE_TEST_COMPLETED: ${jsonFile.absolutePath}")
        Log.i(TAG, "DIAGNOSTIC_COMPLETED: ${jsonFile.absolutePath}")

        jsonFile
    }

    private suspend fun testPlugin(
        container: AppContainer,
        plugin: InstalledCsPlugin,
    ): CsDiagResult {
        Log.i(TAG, "[CS:${plugin.name}] Test başlıyor...")
        val apis = runCatching { container.csRepos.loadApis(plugin.id).apisOrEmpty() }.getOrNull()
        if (apis.isNullOrEmpty()) {
            Log.w(TAG, "[CS:${plugin.name}] Dex yüklenemedi / API sınıfı bulunamadı")
            return CsDiagResult(
                pluginName = plugin.name,
                catalogStatus = "X (Dex yüklenemedi)",
                loadStatus = "-",
                episodeStatus = "-",
                linkStatus = "-",
                mediaStatus = "-",
                subPluginStatus = "-",
                status = "X",
                errorReason = "Dex yükleme / API sınıfı bulunamadı",
            )
        }

        val api = apis.first()

        // 1. Katalog (mainPage; uygulamayla aynı süre, 6 sn yanlış "boş katalog" veriyordu)
        val sections = runCatching {
            withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                container.csRuntime.mainPage(api, 1, timeoutMs = CATALOG_TIMEOUT_MS)
            }
        }.getOrNull() ?: emptyList()

        val allItems = sections.flatMap { it.items }.distinctBy { it.url.ifBlank { it.name } }
        if (allItems.isEmpty()) {
            val cfBlocked = com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver.wasBlocked(api.mainUrl)
            val reason = if (cfBlocked) "Cloudflare engeli / Turnstile" else "Katalog boş ya da zaman aşımı"
            Log.w(TAG, "[CS:${plugin.name}] Katalog başarısız: $reason")
            return CsDiagResult(
                pluginName = plugin.name,
                catalogStatus = "X ($reason)",
                loadStatus = "-",
                episodeStatus = "-",
                linkStatus = "-",
                mediaStatus = "-",
                subPluginStatus = "-",
                status = "X",
                errorReason = reason,
            )
        }

        val catalogStatus = "OK (${sections.size} satır, ${allItems.size} içerik)"
        Log.i(TAG, "[CS:${plugin.name}] Katalog başarılı: $catalogStatus")

        val testItems = allItems.take(2)
        var lastResult: CsDiagResult? = null

        for (item in testItems) {
            val itemTitle = item.name

            // 2. Load (zaman aşımı: 12 sn)
            var loadErr: Throwable? = null
            val loadResp = withTimeoutOrNull(12_000L) {
                try {
                    api.load(item.url)
                } catch (t: Throwable) {
                    loadErr = t
                    null
                }
            }

            if (loadResp == null) {
                val errName = loadErr?.javaClass?.simpleName ?: "zaman aşımı"
                lastResult = CsDiagResult(
                    pluginName = plugin.name,
                    catalogStatus = catalogStatus,
                    loadStatus = "X ($errName)",
                    episodeStatus = "-",
                    linkStatus = "-",
                    mediaStatus = "-",
                    subPluginStatus = "-",
                    status = "X",
                    errorReason = "load() hata: $errName",
                )
                continue
            }

            val loadStatus = "OK (${loadResp.name.take(20)})"

            // 3. Bölüm / dataUrl çözümü
            val (dataUrl, episodeLabel) = resolveDataUrl(loadResp)
            if (dataUrl.isNullOrBlank()) {
                lastResult = CsDiagResult(
                    pluginName = plugin.name,
                    catalogStatus = catalogStatus,
                    loadStatus = loadStatus,
                    episodeStatus = "X ($episodeLabel)",
                    linkStatus = "-",
                    mediaStatus = "-",
                    subPluginStatus = "-",
                    status = "X",
                    errorReason = "Bölüm/dataUrl bulunamadı",
                )
                continue
            }

            val episodeStatus = "OK ($episodeLabel)"

            // 4. Link çıkarımı (loadLinks; uygulamadaki CsStreamProvider ile aynı: 30 sn)
            val source = StreamSource(addonId = plugin.id, addonName = plugin.name)
            val streams = withTimeoutOrNull(31_000L) {
                runCatching { container.csRuntime.loadLinksDirect(api, dataUrl, source, 30_000L) }.getOrNull()
            } ?: emptyList()

            if (streams.isEmpty()) {
                lastResult = CsDiagResult(
                    pluginName = plugin.name,
                    catalogStatus = catalogStatus,
                    loadStatus = loadStatus,
                    episodeStatus = episodeStatus,
                    linkStatus = "X (0 link / zaman aşımı)",
                    mediaStatus = "-",
                    subPluginStatus = "-",
                    status = "X",
                    errorReason = "Extractor link çıkaramadı",
                )
                continue
            }

            val firstStream = streams.first()
            val qualityStr = firstStream.resolution?.let { "${it}p" } ?: "?"
            val linkStatus = "OK (${streams.size} link, $qualityStr)"

            // 5. Medya imzası (~512KB GET)
            var mediaOk = false
            var probeMsg = ""
            for (st in streams.take(2)) {
                val (ok, msg) = probeMedia(container.httpClient, st)
                probeMsg = msg
                if (ok) {
                    mediaOk = true
                    break
                }
            }

            val mediaStatus = if (probeMsg == "torrent, atlandı") {
                "torrent, atlandı"
            } else if (mediaOk) {
                "OK ($probeMsg)"
            } else {
                "X ($probeMsg)"
            }

            // 6. Eklenti altyazısı
            val allSubs = streams.flatMap { it.subtitles }.distinctBy { it.url }
            var subPluginStatus = "Yok (0)"
            if (allSubs.isNotEmpty()) {
                val trSub = allSubs.firstOrNull {
                    it.language?.contains("tr", ignoreCase = true) == true ||
                            it.language?.contains("tur", ignoreCase = true) == true
                }
                val candidate = trSub ?: allSubs.first()
                val track = SubtitleTrack(
                    url = candidate.url,
                    language = candidate.language,
                    origin = plugin.name,
                    headers = candidate.headers,
                )
                val loadRes = withTimeoutOrNull(6_000L) {
                    container.subtitleLoader.load(track)
                }
                subPluginStatus = when (loadRes) {
                    is SubtitleLoad.Loaded -> {
                        val langTag = (candidate.language ?: "TR").uppercase()
                        "OK (${allSubs.size} sub, $langTag ${loadRes.cues.size} satır)"
                    }
                    is SubtitleLoad.Failed -> "X (${loadRes.reason})"
                    null -> "X (zaman aşımı)"
                }
            }

            val finalStatus = if (mediaOk || probeMsg == "torrent, atlandı") "OK" else "X"
            val errorReason = if (finalStatus == "OK") "-" else "Medya doğrulanamadı: $probeMsg"

            val result = CsDiagResult(
                pluginName = plugin.name,
                catalogStatus = catalogStatus,
                loadStatus = loadStatus,
                episodeStatus = episodeStatus,
                linkStatus = linkStatus,
                mediaStatus = mediaStatus,
                subPluginStatus = subPluginStatus,
                status = finalStatus,
                errorReason = errorReason,
            )

            if (mediaOk || probeMsg == "torrent, atlandı") {
                return result
            } else {
                lastResult = result
            }
        }

        return lastResult ?: CsDiagResult(
            pluginName = plugin.name,
            catalogStatus = catalogStatus,
            loadStatus = "X",
            episodeStatus = "-",
            linkStatus = "-",
            mediaStatus = "-",
            subPluginStatus = "-",
            status = "X",
            errorReason = "İçerik testleri başarısız",
        )
    }

    private suspend fun testNuvioAddons(container: AppContainer): List<NuvioAddonDiagResult> {
        val results = mutableListOf<NuvioAddonDiagResult>()
        val testMovie = StreamRequest(
            tmdbId = 550,
            imdbId = "tt0137523",
            type = MediaType.Movie,
            title = "Fight Club",
            year = 1999,
        )
        val testSeries = StreamRequest(
            tmdbId = 1396,
            imdbId = "tt0903747",
            type = MediaType.Tv,
            season = 1,
            episode = 1,
            title = "Breaking Bad",
            year = 2008,
        )

        val allAddons = runCatching { container.addons.enabledAddons() }.getOrDefault(emptyList())
        val videoAddons = allAddons.filter { addon ->
            addon.manifest.resources.any { it.name.equals("stream", ignoreCase = true) }
        }

        for (addon in videoAddons) {
            Log.i(TAG, "[Nuvio Addon: ${addon.manifest.name}] Test başlıyor...")
            val req = if (addon.manifest.types.contains("movie")) testMovie else testSeries
            val streamProvider = AddonStreamProvider(
                addons = { listOf(addon) },
                client = container.httpClient,
                timeout = { 10.seconds },
            )
            val streams = withTimeoutOrNull(12_000L) {
                streamProvider.streams(req).filterIsInstance<StreamResult.Found>()
                    .flatMapConcat { it.streams.asFlow() }
                    .toList()
            } ?: emptyList()

            var mediaOk = false
            var probeMsg = ""
            for (st in streams.take(2)) {
                val (ok, msg) = probeMedia(container.httpClient, st)
                probeMsg = msg
                if (ok) {
                    mediaOk = true
                    break
                }
            }

            val streamStatus = if (streams.isNotEmpty()) "OK (${streams.size} akış)" else "X (0 akış / zaman aşımı)"
            val mediaStatus = if (probeMsg == "torrent, atlandı") {
                "torrent, atlandı"
            } else if (mediaOk) {
                "OK ($probeMsg)"
            } else if (streams.isNotEmpty()) {
                "X ($probeMsg)"
            } else {
                "-"
            }

            val subs = streams.flatMap { it.subtitles }
            val subSourceStatus = if (subs.isNotEmpty()) {
                val candidate = subs.firstOrNull { it.language?.contains("tr", ignoreCase = true) == true } ?: subs.first()
                val loadRes = withTimeoutOrNull(6_000L) {
                    container.subtitleLoader.load(SubtitleTrack(candidate.url, candidate.language, addon.manifest.name, headers = candidate.headers))
                }
                if (loadRes is SubtitleLoad.Loaded) "OK (${subs.size} sub, TR ${loadRes.cues.size} satır)" else "X (${subs.size} sub)"
            } else "Yok (0)"

            val finalStatus = if (mediaOk || probeMsg == "torrent, atlandı") "OK" else "X"
            val errorReason = if (finalStatus == "OK") "-" else if (streams.isEmpty()) "Akış bulunamadı / zaman aşımı" else probeMsg

            results += NuvioAddonDiagResult(
                name = addon.manifest.name,
                kind = "Video Addon",
                streamStatus = streamStatus,
                mediaStatus = mediaStatus,
                subSourceStatus = subSourceStatus,
                status = finalStatus,
                errorReason = errorReason,
            )
        }

        return results
    }

    private suspend fun testNuvioJsPlugins(container: AppContainer): List<NuvioJsDiagResult> {
        val results = mutableListOf<NuvioJsDiagResult>()
        val plugins = runCatching { container.plugins.enabledPlugins() }.getOrDefault(emptyList())

        for (plugin in plugins) {
            Log.i(TAG, "[Nuvio Scraper: ${plugin.name}] Test başlıyor...")
            val code = runCatching { container.plugins.loadCode(plugin.id) }.getOrNull()
            if (code.isNullOrBlank()) {
                results += NuvioJsDiagResult(
                    name = plugin.name,
                    target = "Film/Dizi",
                    streamStatus = "X (Kod yok)",
                    mediaStatus = "-",
                    subSourceStatus = "-",
                    status = "X",
                    category = "uygulama",
                    errorReason = "Betik dosyası okunamadı",
                )
                continue
            }

            val savedSettings = container.pluginSettings.get(plugin.id)
            val types = plugin.supportedTypes
            val isAnime = plugin.name.contains("anime", ignoreCase = true) ||
                plugin.name.contains("aniz", ignoreCase = true) ||
                types.any { it.contains("anime", ignoreCase = true) }

            if (isAnime) {
                // Anime eklentisi (AnimeCix, Anizium): bilinen anime hedefi (One Piece tt0388629 S1B1, tmdbId=37854)
                val animeReq = PluginRequest(tmdbId = "37854", mediaType = "tv", season = 1, episode = 1)
                results += testScraperTarget(container, plugin, code, savedSettings, animeReq, "Anime (tt0388629 S1B1)")
            } else {
                val supportsMovie = types.isEmpty() || types.any { it.contains("movie", ignoreCase = true) }
                val supportsTv = types.isEmpty() || types.any { it.contains("tv", ignoreCase = true) || it.contains("series", ignoreCase = true) }

                // Yalnızca desteklenen türler denenir; desteklenmeyen türler için 'atlandı' satırı eklenmez
                if (supportsMovie) {
                    val movieReq = PluginRequest(tmdbId = "550", mediaType = "movie", season = 1, episode = 1)
                    results += testScraperTarget(container, plugin, code, savedSettings, movieReq, "Film (tt0137523)")
                }

                if (supportsTv) {
                    val tvReq = PluginRequest(tmdbId = "1396", mediaType = "tv", season = 1, episode = 1)
                    results += testScraperTarget(container, plugin, code, savedSettings, tvReq, "Dizi (tt0903747 S1B1)")
                }
            }
        }

        return results
    }

    private suspend fun testScraperTarget(
        container: AppContainer,
        plugin: com.kaan.play.data.plugin.InstalledPlugin,
        code: String,
        savedSettings: Map<String, kotlinx.serialization.json.JsonElement>,
        req: PluginRequest,
        targetLabel: String,
    ): NuvioJsDiagResult {
        val outcome = withTimeoutOrNull(15_000L) {
            container.pluginRuntime.run(code, req, plugin.pluginId, timeoutMs = 12_000L, savedSettings)
        }

        if (outcome == null || outcome !is PluginOutcome.Success) {
            val err = (outcome as? PluginOutcome.Failure)?.message ?: if (outcome == null) "Zaman aşımı (12 sn)" else "Hata"
            val category = classifyJsFailure(outcome, emptyList(), "", err)
            return NuvioJsDiagResult(
                name = plugin.name,
                target = targetLabel,
                streamStatus = "X (Hata)",
                mediaStatus = "-",
                subSourceStatus = "-",
                status = "X",
                category = category,
                errorReason = err.take(40),
            )
        }

        val source = StreamSource(-plugin.id, plugin.name)
        val playableStreams = outcome.streams.map { it.toPlayable(source) }
        val streamStatus = if (playableStreams.isNotEmpty()) "OK (${playableStreams.size} akış)" else "X (0 akış)"

        var mediaOk = false
        var probeMsg = ""
        for (st in playableStreams.take(2)) {
            val (ok, msg) = probeMedia(container.httpClient, st)
            probeMsg = msg
            if (ok) {
                mediaOk = true
                break
            }
        }

        val mediaStatus = if (probeMsg == "torrent, atlandı") {
            "torrent, atlandı"
        } else if (mediaOk) {
            "OK ($probeMsg)"
        } else if (playableStreams.isNotEmpty()) {
            "X ($probeMsg)"
        } else {
            "-"
        }

        val subs = playableStreams.flatMap { it.subtitles }
        val subSourceStatus = if (subs.isNotEmpty()) {
            val candidate = subs.firstOrNull { it.language?.contains("tr", ignoreCase = true) == true } ?: subs.first()
            val loadRes = withTimeoutOrNull(6_000L) {
                container.subtitleLoader.load(SubtitleTrack(candidate.url, candidate.language, plugin.name, headers = candidate.headers))
            }
            if (loadRes is SubtitleLoad.Loaded) "OK (${subs.size} sub, TR ${loadRes.cues.size} satır)" else "X (${subs.size} sub)"
        } else "Yok (0)"

        val finalStatus = if (mediaOk || probeMsg == "torrent, atlandı") "OK" else "X"
        val errorReason = if (finalStatus == "OK") "-" else if (playableStreams.isEmpty()) "Akış bulunamadı" else probeMsg
        val category = if (finalStatus == "OK") "-" else classifyJsFailure(outcome, playableStreams, probeMsg, errorReason)

        return NuvioJsDiagResult(
            name = plugin.name,
            target = targetLabel,
            streamStatus = streamStatus,
            mediaStatus = mediaStatus,
            subSourceStatus = subSourceStatus,
            status = finalStatus,
            category = category,
            errorReason = errorReason,
        )
    }

    private fun classifyJsFailure(
        outcome: PluginOutcome?,
        playableStreams: List<PlayableStream>,
        probeMsg: String,
        errorReason: String,
    ): String {
        // 1. Akış bulundu ancak medya URL'si doğrulanamadı (HTTP 4xx/5xx, bağlantı hatası vb.)
        if (playableStreams.isNotEmpty()) {
            val lowerProbe = probeMsg.lowercase()
            return if (lowerProbe.contains("40") || lowerProbe.contains("50") ||
                lowerProbe.contains("connection") || lowerProbe.contains("closed") ||
                lowerProbe.contains("reset") || lowerProbe.contains("timeout") ||
                lowerProbe.contains("protocol") || lowerProbe.contains("refused")
            ) {
                "site"
            } else {
                "uygulama"
            }
        }

        // 2. Zaman aşımı bizim belirlediğimiz süreden kaynaklanıyorsa -> uygulama
        if (outcome == null || (outcome is PluginOutcome.Failure && outcome.reason == PluginOutcome.Reason.Timeout)) {
            return "uygulama"
        }

        val notes = outcome.notes
        val notesStr = notes.joinToString(" ").lowercase()
        val err = errorReason.lowercase()
        val msg = (outcome as? PluginOutcome.Failure)?.message?.lowercase().orEmpty()

        // 3. Site taraflı hata: HTTP 4xx/5xx, Cloudflare engeli, DNS/ağ kopması, sunucu yanıt vermiyor
        val siteKeywords = listOf(
            "blocked", "cloudflare", "400", "401", "403", "404", "429", "500", "502", "503", "504",
            "network error", "connect", "dns", "unknownhost", "reset", "refused", "closed by peer"
        )
        if (siteKeywords.any { notesStr.contains(it) || err.contains(it) || msg.contains(it) }) {
            return "site"
        }

        // 4. Uygulama taraflı hata: JS motoru, eksik API (Buffer, crypto, window...), söz dizimi, JSON hatası
        val appKeywords = listOf(
            "referenceerror", "typeerror", "syntaxerror", "not defined", "is not a function",
            "require", "buffer", "crypto", "window", "json", "bad result", "nullpointer"
        )
        if (appKeywords.any { notesStr.contains(it) || err.contains(it) || msg.contains(it) }) {
            return "uygulama"
        }

        // 5. İçerik yok: Eklenti temiz çalıştı, ağ hatası veya JS hatası almadı ama bu içerik için akış yok
        if (outcome is PluginOutcome.Success && outcome.streams.isEmpty()) {
            return "içerik yok"
        }

        return "uygulama"
    }

    private suspend fun testSubtitles(container: AppContainer): List<SubtitleDiagResult> {
        val results = mutableListOf<SubtitleDiagResult>()
        val movieReq = StreamRequest(
            tmdbId = 550,
            imdbId = "tt0137523",
            type = MediaType.Movie,
            title = "Fight Club",
            year = 1999,
        )

        val allAddons = runCatching { container.addons.enabledAddons() }.getOrDefault(emptyList())

        // 1. AltyazıDB
        val altyaziDbAddon = allAddons.firstOrNull { it.manifest.id == "com.blast.altyazidb.subtitles" || it.manifest.name.contains("AltyazıDB", ignoreCase = true) }
        if (altyaziDbAddon != null) {
            val provider = AddonSubtitleProvider(
                addons = { listOf(altyaziDbAddon) },
                client = container.httpClient,
                timeout = { 8.seconds },
            )
            val tracks = withTimeoutOrNull(8_000L) {
                provider.subtitles(movieReq)
                    .filterIsInstance<SubtitleResult.Found>()
                    .flatMapConcat { it.tracks.asFlow() }
                    .toList()
            } ?: emptyList()

            val trTracks = tracks.filter { it.language?.equals("tr", ignoreCase = true) == true || it.language?.equals("tur", ignoreCase = true) == true }
            // OpenSubtitles'ta bazı filmlerin Türkçesi yok (ör. tt0137523): arama çalışıyorsa başarı sayılır.
            val usable = trTracks.ifEmpty { tracks }
            if (usable.isNotEmpty()) {
                val candidate = usable.first()
                val loadRes = withTimeoutOrNull(8_000L) { container.subtitleLoader.load(candidate) }
                if (loadRes is SubtitleLoad.Loaded && loadRes.cues.isNotEmpty()) {
                    results += SubtitleDiagResult(
                        name = "AltyazıDB",
                        target = "tt0137523",
                        searchStatus = "OK (TR ${trTracks.size}, toplam ${tracks.size})",
                        parseStatus = "OK (${loadRes.cues.size} satır)",
                        status = "OK",
                        errorReason = "-",
                    )
                } else {
                    val err = (loadRes as? SubtitleLoad.Failed)?.reason ?: "Ayrıştırma hatası"
                    results += SubtitleDiagResult(
                        name = "AltyazıDB",
                        target = "tt0137523",
                        searchStatus = "OK (TR ${trTracks.size}, toplam ${tracks.size})",
                        parseStatus = "X ($err)",
                        status = "X",
                        errorReason = "Altyazı ayrıştırılamadı: $err",
                    )
                }
            } else {
                results += SubtitleDiagResult(
                    name = "AltyazıDB",
                    target = "tt0137523",
                    searchStatus = if (tracks.isEmpty()) "X (0 sonuç)" else "X (TR yok)",
                    parseStatus = "-",
                    status = "X",
                    errorReason = "Türkçe altyazı bulunamadı",
                )
            }
        } else {
            results += SubtitleDiagResult(
                name = "AltyazıDB",
                target = "tt0137523",
                searchStatus = "X (Addon bulunamadı)",
                parseStatus = "-",
                status = "X",
                errorReason = "AltyazıDB addon yüklü/aktif değil",
            )
        }

        // 2. AltyazıTR
        val altyaziTrAddon = allAddons.firstOrNull { it.manifest.id == "com.ege.altyazitr" || it.manifest.name.contains("AltyazıTR", ignoreCase = true) }
        if (altyaziTrAddon != null) {
            val provider = AddonSubtitleProvider(
                addons = { listOf(altyaziTrAddon) },
                client = container.httpClient,
                timeout = { 8.seconds },
            )
            val tracks = withTimeoutOrNull(8_000L) {
                provider.subtitles(movieReq)
                    .filterIsInstance<SubtitleResult.Found>()
                    .flatMapConcat { it.tracks.asFlow() }
                    .toList()
            } ?: emptyList()

            val trTracks = tracks.filter { it.language?.equals("tr", ignoreCase = true) == true || it.language?.equals("tur", ignoreCase = true) == true }
            // OpenSubtitles'ta bazı filmlerin Türkçesi yok (ör. tt0137523): arama çalışıyorsa başarı sayılır.
            val usable = trTracks.ifEmpty { tracks }
            if (usable.isNotEmpty()) {
                val candidate = usable.first()
                val loadRes = withTimeoutOrNull(8_000L) { container.subtitleLoader.load(candidate) }
                if (loadRes is SubtitleLoad.Loaded && loadRes.cues.isNotEmpty()) {
                    results += SubtitleDiagResult(
                        name = "AltyazıTR",
                        target = "tt0137523",
                        searchStatus = "OK (TR ${trTracks.size}, toplam ${tracks.size})",
                        parseStatus = "OK (${loadRes.cues.size} satır)",
                        status = "OK",
                        errorReason = "-",
                    )
                } else {
                    val err = (loadRes as? SubtitleLoad.Failed)?.reason ?: "Ayrıştırma hatası"
                    results += SubtitleDiagResult(
                        name = "AltyazıTR",
                        target = "tt0137523",
                        searchStatus = "OK (TR ${trTracks.size}, toplam ${tracks.size})",
                        parseStatus = "X ($err)",
                        status = "X",
                        errorReason = "Altyazı ayrıştırılamadı: $err",
                    )
                }
            } else {
                results += SubtitleDiagResult(
                    name = "AltyazıTR",
                    target = "tt0137523",
                    searchStatus = if (tracks.isEmpty()) "X (0 sonuç)" else "X (TR yok)",
                    parseStatus = "-",
                    status = "X",
                    errorReason = "Türkçe altyazı bulunamadı",
                )
            }
        } else {
            results += SubtitleDiagResult(
                name = "AltyazıTR",
                target = "tt0137523",
                searchStatus = "X (Addon bulunamadı)",
                parseStatus = "-",
                status = "X",
                errorReason = "AltyazıTR addon yüklü/aktif değil",
            )
        }

        // 3. Yerleşik Çevrimiçi Arama
        try {
            val onlineProvider = OnlineSubtitleProvider(
                client = container.httpClient,
                languages = { listOf("tr", "en") },
                timeout = 8.seconds,
            )
            val tracks = withTimeoutOrNull(8_000L) { onlineProvider.search(movieReq) } ?: emptyList()
            val trTracks = tracks.filter { it.language?.equals("tr", ignoreCase = true) == true || it.language?.equals("tur", ignoreCase = true) == true }

            // OpenSubtitles'ta bazı filmlerin Türkçesi yok (ör. tt0137523): arama çalışıyorsa başarı sayılır.
            val usable = trTracks.ifEmpty { tracks }
            if (usable.isNotEmpty()) {
                val candidate = usable.first()
                val loadRes = withTimeoutOrNull(8_000L) { container.subtitleLoader.load(candidate) }
                if (loadRes is SubtitleLoad.Loaded && loadRes.cues.isNotEmpty()) {
                    results += SubtitleDiagResult(
                        name = "Yerleşik Çevrimiçi Arama",
                        target = "tt0137523",
                        searchStatus = "OK (TR ${trTracks.size}, toplam ${tracks.size})",
                        parseStatus = "OK (${loadRes.cues.size} satır)",
                        status = "OK",
                        errorReason = "-",
                    )
                } else {
                    val err = (loadRes as? SubtitleLoad.Failed)?.reason ?: "Ayrıştırma hatası"
                    results += SubtitleDiagResult(
                        name = "Yerleşik Çevrimiçi Arama",
                        target = "tt0137523",
                        searchStatus = "OK (TR ${trTracks.size}, toplam ${tracks.size})",
                        parseStatus = "X ($err)",
                        status = "X",
                        errorReason = "Altyazı ayrıştırılamadı: $err",
                    )
                }
            } else {
                results += SubtitleDiagResult(
                    name = "Yerleşik Çevrimiçi Arama",
                    target = "tt0137523",
                    searchStatus = if (tracks.isEmpty()) "X (0 sonuç)" else "X (TR yok)",
                    parseStatus = "-",
                    status = "X",
                    errorReason = "Türkçe çevrimiçi altyazı bulunamadı",
                )
            }
        } catch (e: Exception) {
            results += SubtitleDiagResult(
                name = "Yerleşik Çevrimiçi Arama",
                target = "tt0137523",
                searchStatus = "X (${e.javaClass.simpleName})",
                parseStatus = "-",
                status = "X",
                errorReason = e.message?.take(40) ?: "İstisna",
            )
        }

        return results
    }

    private suspend fun testCsNextEpisode(container: AppContainer, plugins: List<InstalledCsPlugin>): ContinueDiagResult {
        Log.i(TAG, "testCsNextEpisode: Açık ilk CloudStream dizisi aranıyor...")
        val seriesPlugins = plugins.filter { p ->
            p.tvTypes.any { it.contains("tv", ignoreCase = true) || it.contains("anime", ignoreCase = true) || it.contains("series", ignoreCase = true) }
        }

        for (plugin in seriesPlugins) {
            val apis = runCatching { container.csRepos.loadApis(plugin.id).apisOrEmpty() }.getOrNull() ?: continue
            val api = apis.firstOrNull() ?: continue
            val sections = runCatching {
                withTimeoutOrNull(CATALOG_TIMEOUT_MS) {
                    container.csRuntime.mainPage(api, 1, timeoutMs = CATALOG_TIMEOUT_MS)
                }
            }.getOrNull() ?: continue

            val items = sections.flatMap { it.items }.distinctBy { it.url }
            for (item in items.take(4)) {
                val loadResp = withTimeoutOrNull(10_000L) {
                    runCatching { api.load(item.url) }.getOrNull()
                } ?: continue

                val episodes = when (loadResp) {
                    is TvSeriesLoadResponse -> loadResp.episodes
                    is AnimeLoadResponse -> loadResp.episodes.values.flatten()
                    else -> emptyList()
                }

                if (episodes.size >= 2) {
                    val ep1 = episodes[0]
                    val s = ep1.season ?: 1
                    val e = ep1.episode ?: 1
                    val data1 = ep1.data
                    val finder = CsNextEpisodeFinder(container.csRepos)
                    val nextEp = withTimeoutOrNull(10_000L) {
                        finder.next("cs:${plugin.id}", item.url, data1, s, e)
                    }

                    if (nextEp != null && nextEp.dataUrl.isNotBlank() && nextEp.dataUrl != data1) {
                        val epName = "S${nextEp.season}E${nextEp.episode}"
                        Log.i(TAG, "testCsNextEpisode: Başarılı [${plugin.name}] -> $epName")
                        return ContinueDiagResult(
                            name = "CsNextEpisodeFinder",
                            target = "${plugin.name} (${loadResp.name.take(20)}) S1B1",
                            details = "OK ($epName, dataUrl farklı)",
                            status = "OK",
                            errorReason = "-",
                        )
                    } else if (nextEp != null && nextEp.dataUrl == data1) {
                        Log.w(TAG, "testCsNextEpisode: dataUrl aynı [${plugin.name}]")
                        return ContinueDiagResult(
                            name = "CsNextEpisodeFinder",
                            target = "${plugin.name} (${loadResp.name.take(20)}) S1B1",
                            details = "X (dataUrl aynı)",
                            status = "X",
                            errorReason = "dataUrl aynı",
                        )
                    }
                }
            }
        }

        return ContinueDiagResult(
            name = "CsNextEpisodeFinder",
            target = "CloudStream Dizisi S1B1",
            details = "X (Dizi içeriği bulunamadı)",
            status = "X",
            errorReason = "Uygun dizi içeriği bulunamadı",
        )
    }

    private suspend fun testUpdatesAndAnnouncements(container: AppContainer): List<UpdateAnnounceDiagResult> {
        val results = mutableListOf<UpdateAnnounceDiagResult>()

        // 1. GitHub releases/latest (yalnızca okuma)
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/liberta09/Play/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Play-Android-UpdateCheck")
                .build()
            val text = withTimeoutOrNull(8_000L) {
                container.httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }
            if (text != null) {
                val jsonElem = PlayJson.parseToJsonElement(text)
                val tagName = (jsonElem as? kotlinx.serialization.json.JsonObject)?.get("tag_name")?.let {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
                if (!tagName.isNullOrBlank()) {
                    results += UpdateAnnounceDiagResult(
                        name = "GitHub releases/latest",
                        details = "OK (tag: $tagName)",
                        status = "OK",
                        errorReason = "-",
                    )
                } else {
                    results += UpdateAnnounceDiagResult(
                        name = "GitHub releases/latest",
                        details = "X (tag_name alanı yok)",
                        status = "X",
                        errorReason = "tag_name alanı bulunamadı",
                    )
                }
            } else {
                results += UpdateAnnounceDiagResult(
                    name = "GitHub releases/latest",
                    details = "X (HTTP hatası / zaman aşımı)",
                    status = "X",
                    errorReason = "HTTP hatası veya zaman aşımı",
                )
            }
        } catch (e: Exception) {
            results += UpdateAnnounceDiagResult(
                name = "GitHub releases/latest",
                details = "X (${e.javaClass.simpleName})",
                status = "X",
                errorReason = e.message?.take(40) ?: "İstisna",
            )
        }

        // 2. announcements.json (yalnızca okuma)
        try {
            val req = Request.Builder()
                .url("https://raw.githubusercontent.com/liberta09/Play/main/announcements.json")
                .header("User-Agent", "Play-Android-AnnouncementCheck")
                .build()
            val text = withTimeoutOrNull(8_000L) {
                container.httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            }
            if (text != null) {
                val list = com.kaan.play.data.announcement.AnnouncementRules.parse(text, PlayJson)
                if (list != null) {
                    results += UpdateAnnounceDiagResult(
                        name = "announcements.json",
                        details = "OK (${list.size} duyuru)",
                        status = "OK",
                        errorReason = "-",
                    )
                } else {
                    results += UpdateAnnounceDiagResult(
                        name = "announcements.json",
                        details = "X (Ayrıştırma hatası)",
                        status = "X",
                        errorReason = "JSON duyuru listesi ayrıştırılamadı",
                    )
                }
            } else {
                results += UpdateAnnounceDiagResult(
                    name = "announcements.json",
                    details = "X (HTTP hatası / zaman aşımı)",
                    status = "X",
                    errorReason = "HTTP hatası veya zaman aşımı",
                )
            }
        } catch (e: Exception) {
            results += UpdateAnnounceDiagResult(
                name = "announcements.json",
                details = "X (${e.javaClass.simpleName})",
                status = "X",
                errorReason = e.message?.take(40) ?: "İstisna",
            )
        }

        return results
    }

    private fun resolveDataUrl(loadResp: com.lagradost.cloudstream3.LoadResponse): Pair<String?, String> {
        return when (loadResp) {
            is MovieLoadResponse -> loadResp.dataUrl to "Film"
            is LiveStreamLoadResponse -> loadResp.dataUrl to "Canlı Yayın"
            is TvSeriesLoadResponse -> {
                val ep = loadResp.episodes.firstOrNull()
                val label = if (ep != null) "S${ep.season ?: 1}E${ep.episode ?: 1}" else "Bölüm yok"
                ep?.data to label
            }
            is AnimeLoadResponse -> {
                val ep = loadResp.episodes.values.flatten().firstOrNull()
                val label = if (ep != null) "Bölüm ${ep.episode ?: 1}" else "Bölüm yok"
                ep?.data to label
            }
            else -> null to "Bilinmeyen tür"
        }
    }

    private suspend fun probeMedia(
        httpClient: OkHttpClient,
        stream: PlayableStream,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (stream.url.startsWith("magnet:") || stream.url.startsWith("torrent:") || stream.torrentFileIdx != null) {
            return@withContext true to "torrent, atlandı"
        }
        val maxBytes = 512 * 1024
        try {
            val isM3u8 = stream.url.contains(".m3u8", ignoreCase = true)
            fun buildReq(withRange: Boolean): Request {
                val b = Request.Builder().url(stream.url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                stream.headers.forEach { (k, v) -> b.header(k, v) }
                if (withRange) b.header("Range", "bytes=0-${maxBytes - 1}")
                return b.get().build()
            }
            fun executeWithHttp1Fallback(req: Request): okhttp3.Response {
                return try {
                    httpClient.newCall(req).execute()
                } catch (e: Exception) {
                    val isProtocol = e.message?.contains("PROTOCOL_ERROR", ignoreCase = true) == true
                    if (isProtocol) {
                        val http1Client = httpClient.newBuilder()
                            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
                            .build()
                        http1Client.newCall(req).execute()
                    } else {
                        throw e
                    }
                }
            }
            var getResp = executeWithHttp1Fallback(buildReq(withRange = !isM3u8))
            if (getResp.code in listOf(400, 416) && !isM3u8) {
                getResp.close()
                getResp = executeWithHttp1Fallback(buildReq(withRange = false))
            }
            val code = getResp.code
            val body = getResp.body
            var bytesRead = 0
            val buffer = ByteArray(maxBytes)
            if (body != null) {
                body.byteStream().use { input ->
                    while (bytesRead < maxBytes) {
                        val r = input.read(buffer, bytesRead, maxBytes - bytesRead)
                        if (r == -1) break
                        bytesRead += r
                    }
                }
            }
            getResp.close()

            Log.d(TAG, "probeMedia [${stream.source.addonName}]: code=$code bytes=$bytesRead")
            if (code !in 200..299 && code != 206) {
                return@withContext false to "HTTP $code"
            }

            val signature = detectMediaSignature(buffer, bytesRead)
            if (signature != null) {
                val kb = (bytesRead + 1023) / 1024
                return@withContext true to "$signature ${kb}KB"
            } else {
                return@withContext false to "Geçersiz imza (HTTP $code, ${bytesRead / 1024}KB)"
            }
        } catch (e: Exception) {
            val msg = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "probeMedia [${stream.source.addonName}] GET istisna: $msg")
            return@withContext false to msg.take(40)
        }
    }

    private fun detectMediaSignature(bytes: ByteArray, length: Int): String? {
        if (length < 4) return null

        val headText = String(bytes, 0, minOf(length, 1024), Charsets.ISO_8859_1)
        if (headText.contains("#EXTM3U") || headText.contains("#EXT-X-STREAM-INF") || headText.contains("#EXTINF")) {
            return "M3U8"
        }

        if (length >= 4 &&
            bytes[0] == 0x1A.toByte() &&
            bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() &&
            bytes[3] == 0xA3.toByte()
        ) {
            return "MKV"
        }

        if (length >= 8) {
            val scanLimit = minOf(length - 4, 64)
            for (i in 0..scanLimit) {
                if (bytes[i] == 'f'.code.toByte() &&
                    bytes[i + 1] == 't'.code.toByte() &&
                    bytes[i + 2] == 'y'.code.toByte() &&
                    bytes[i + 3] == 'p'.code.toByte()
                ) {
                    return "MP4"
                }
            }
        }

        if (length >= 188 && bytes[0] == 0x47.toByte()) {
            if (length < 376 || bytes[188] == 0x47.toByte()) {
                return "TS"
            }
        }

        return null
    }

    private fun savePartialJson(
        jsonFile: File,
        csResults: List<CsDiagResult> = emptyList(),
        addonResults: List<NuvioAddonDiagResult> = emptyList(),
        jsResults: List<NuvioJsDiagResult> = emptyList(),
        subResults: List<SubtitleDiagResult> = emptyList(),
        continueResult: ContinueDiagResult? = null,
        updateResults: List<UpdateAnnounceDiagResult> = emptyList(),
    ) {
        try {
            val json = buildSmokeTestJson(
                versionName = BuildConfig.VERSION_NAME,
                csResults = csResults,
                addonResults = addonResults,
                jsResults = jsResults,
                subResults = subResults,
                continueResult = continueResult,
                updateResults = updateResults,
            )
            jsonFile.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "savePartialJson hata: ${e.message}")
        }
    }

    fun buildSmokeTestJson(
        versionName: String,
        csResults: List<CsDiagResult> = emptyList(),
        addonResults: List<NuvioAddonDiagResult> = emptyList(),
        jsResults: List<NuvioJsDiagResult> = emptyList(),
        subResults: List<SubtitleDiagResult> = emptyList(),
        continueResult: ContinueDiagResult? = null,
        updateResults: List<UpdateAnnounceDiagResult> = emptyList(),
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val root = buildJsonObject {
            put("timestamp", timestamp)
            put("appVersion", versionName)
            put("cloudstream", buildJsonArray {
                csResults.forEach { r ->
                    add(buildJsonObject {
                        put("name", r.pluginName)
                        put("catalog", r.catalogStatus)
                        put("load", r.loadStatus)
                        put("episode", r.episodeStatus)
                        put("link", r.linkStatus)
                        put("media", r.mediaStatus)
                        put("subtitle", r.subPluginStatus)
                        put("status", r.status)
                        put("reason", r.errorReason)
                    })
                }
            })
            put("nuvio_addons", buildJsonArray {
                addonResults.forEach { r ->
                    add(buildJsonObject {
                        put("name", r.name)
                        put("kind", r.kind)
                        put("stream", r.streamStatus)
                        put("media", r.mediaStatus)
                        put("subtitle", r.subSourceStatus)
                        put("status", r.status)
                        put("reason", r.errorReason)
                    })
                }
            })
            put("nuvio_js", buildJsonArray {
                jsResults.forEach { r ->
                    add(buildJsonObject {
                        put("name", r.name)
                        put("target", r.target)
                        put("stream", r.streamStatus)
                        put("media", r.mediaStatus)
                        put("subtitle", r.subSourceStatus)
                        put("status", r.status)
                        put("category", r.category)
                        put("reason", r.errorReason)
                    })
                }
            })
            put("subtitles", buildJsonArray {
                subResults.forEach { r ->
                    add(buildJsonObject {
                        put("name", r.name)
                        put("target", r.target)
                        put("search", r.searchStatus)
                        put("parse", r.parseStatus)
                        put("status", r.status)
                        put("reason", r.errorReason)
                    })
                }
            })
            put("continue_watching", buildJsonArray {
                if (continueResult != null) {
                    add(buildJsonObject {
                        put("name", continueResult.name)
                        put("target", continueResult.target)
                        put("details", continueResult.details)
                        put("status", continueResult.status)
                        put("reason", continueResult.errorReason)
                    })
                }
            })
            put("updates_and_announcements", buildJsonArray {
                updateResults.forEach { r ->
                    add(buildJsonObject {
                        put("name", r.name)
                        put("details", r.details)
                        put("status", r.status)
                        put("reason", r.errorReason)
                    })
                }
            })
        }
        return root.toString()
    }

    fun buildMarkdown(
        versionName: String,
        csResults: List<CsDiagResult> = emptyList(),
        addonResults: List<NuvioAddonDiagResult> = emptyList(),
        jsResults: List<NuvioJsDiagResult> = emptyList(),
        subResults: List<SubtitleDiagResult> = emptyList(),
        continueResult: ContinueDiagResult? = null,
        updateResults: List<UpdateAnnounceDiagResult> = emptyList(),
        durationStr: String = "-",
    ): String = buildString {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        appendLine("# Play Otomatik Duman Testi (Smoke Test)")
        appendLine()
        appendLine("- **Tarih:** $timestamp")
        appendLine("- **Uygulama Sürümü:** $versionName")
        if (durationStr != "-") {
            appendLine("- **Toplam Süre:** $durationStr")
        }
        appendLine()

        val csOk = csResults.count { it.status == "OK" }
        val csTotal = csResults.size
        val addonOk = addonResults.count { it.status == "OK" }
        val addonTotal = addonResults.size
        val jsOk = jsResults.count { it.status == "OK" }
        val jsTotal = jsResults.count { it.status != "atlandı" }
        val subOk = subResults.count { it.status == "OK" }
        val subTotal = subResults.size

        appendLine("## Özet")
        appendLine("- **CloudStream:** $csOk/$csTotal oynatılabilir")
        appendLine("- **Nuvio Addon:** $addonOk/$addonTotal oynatılabilir")
        appendLine("- **Nuvio JS Eklentileri:** $jsOk/$jsTotal akış/medya başarılı")
        appendLine("- **Altyazı Sağlayıcıları:** $subOk/$subTotal Türkçe altyazı başarılı")
        appendLine("- **Bölüm Devam:** ${continueResult?.status ?: "-"} (${continueResult?.details ?: "-"})")
        val updOk = updateResults.count { it.status == "OK" }
        val updTotal = updateResults.size
        appendLine("- **Güncelleme ve Duyuru:** $updOk/$updTotal başarılı")
        appendLine()

        // 1. CloudStream Tablosu
        appendLine("## 1. CloudStream Eklentileri")
        appendLine("| Eklenti | Katalog | Load | Bölüm | Link | Medya | Altyazı | Durum | Hata/Neden |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        for (r in csResults) {
            appendLine("| ${r.pluginName} | ${r.catalogStatus} | ${r.loadStatus} | ${r.episodeStatus} | ${r.linkStatus} | ${r.mediaStatus} | ${r.subPluginStatus} | ${r.status} | ${r.errorReason} |")
        }
        appendLine()

        // 2. Nuvio Addon Tablosu
        appendLine("## 2. Nuvio Addon'ları")
        appendLine("| Addon | Tür | Akış | Medya | Altyazı | Durum | Hata/Neden |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        for (r in addonResults) {
            appendLine("| ${r.name} | ${r.kind} | ${r.streamStatus} | ${r.mediaStatus} | ${r.subSourceStatus} | ${r.status} | ${r.errorReason} |")
        }
        appendLine()

        // 3. Nuvio JS Eklentileri Tablosu
        appendLine("## 3. Nuvio JS Eklentileri (Scrapers)")
        appendLine("| Eklenti | Hedef | Akış | Medya | Altyazı | Durum | Sınıf | Hata/Neden |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for (r in jsResults) {
            appendLine("| ${r.name} | ${r.target} | ${r.streamStatus} | ${r.mediaStatus} | ${r.subSourceStatus} | ${r.status} | ${r.category} | ${r.errorReason} |")
        }
        appendLine()

        // 4. Altyazı Sağlayıcıları Tablosu
        appendLine("## 4. Altyazı Sağlayıcıları")
        appendLine("| Sağlayıcı | Hedef | Türkçe Arama | İndirme & Ayrıştırma | Durum | Hata/Neden |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        for (r in subResults) {
            appendLine("| ${r.name} | ${r.target} | ${r.searchStatus} | ${r.parseStatus} | ${r.status} | ${r.errorReason} |")
        }
        appendLine()

        // 5. Devam Et Tablosu
        appendLine("## 5. Devam Et (CsNextEpisodeFinder)")
        appendLine("| Bileşen | Hedef | Sonuç | Durum | Hata/Neden |")
        appendLine("| --- | --- | --- | --- | --- |")
        if (continueResult != null) {
            appendLine("| ${continueResult.name} | ${continueResult.target} | ${continueResult.details} | ${continueResult.status} | ${continueResult.errorReason} |")
        }
        appendLine()

        // 6. Güncelleme ve Duyuru Kontrolü
        appendLine("## 6. Güncelleme ve Duyuru Kontrolü")
        appendLine("| Kaynak | Detay | Durum | Hata/Neden |")
        appendLine("| --- | --- | --- | --- |")
        for (r in updateResults) {
            appendLine("| ${r.name} | ${r.details} | ${r.status} | ${r.errorReason} |")
        }
    }
}
