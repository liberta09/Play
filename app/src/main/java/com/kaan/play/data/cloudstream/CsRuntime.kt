package com.kaan.play.data.cloudstream

import android.util.Log
import androidx.media3.common.MimeTypes
import com.kaan.play.data.match.ContentIdentity
import com.kaan.play.data.match.ContentKind
import com.kaan.play.data.match.CsMatchRules
import com.kaan.play.data.match.EpisodePicker
import com.kaan.play.data.match.EpisodeRef
import com.kaan.play.data.match.MatchCandidate
import com.kaan.play.data.match.MatchOutcome
import com.kaan.play.data.match.TitleText
import com.kaan.play.data.match.Verdict
import com.kaan.play.data.model.MediaType
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * CloudStream eklentisi çalıştırıcısı. Yüklenmiş [MainAPI] örnekleriyle şu akışı izler:
 *  1. [MainAPI.search] ile başlık arar
 *  2. Eşleştirme: başlık + yıl ([CsMatchRules]); yılı farklı aday asla, alt başlıklı aday yılsız asla, eşik altı yok
 *  3. [MainAPI.load] ile detay yükler; yüklenen ad/yıl/kimlik istekle uyuşmuyorsa kaynak atılır ([CsMatchRules.verifyLoaded])
 *  4. Film: `dataUrl` ile [MainAPI.loadLinks] çağırır
 *     Dizi: hedef sezonu/bölümü [Episode] listesinden bulup [MainAPI.loadLinks] çağırır
 *  5. Gelen [ExtractorLink]'leri [PlayableStream]'e çevirir
 *
 * [timeoutMs] her adım için ayrı uygulanır.
 */
class CsRuntime {

    /**
     * Kayıtlı [MainAPI] örneklerinden oynatılabilir kaynak listesi döner.
     * [identity] TMDB'den gelen kimlik (başlıklar, yıl, kimlikler): eklenti bu başlıklarla aranır, sonuç yalnızca kimlikle
     * uyuşuyorsa kullanılır. Doğrulanamayan kaynak listeye girmez.
     */
    suspend fun streams(
        apis: List<MainAPI>,
        request: StreamRequest,
        identity: ContentIdentity,
        pluginRowId: Long,
        pluginName: String,
        timeoutMs: Long = 15_000L,
    ): List<PlayableStream> {
        val source = StreamSource(addonId = pluginRowId, addonName = pluginName)
        val results = mutableListOf<PlayableStream>()

        Log.d(TAG, "$pluginName: ${apis.size} MainAPI ile deneniyor (sorgular=${identity.searchQueries()}, yıl=${identity.year}, tmdb=${identity.tmdbId}, imdb=${identity.imdbId})")
        for (api in apis) {
            val started = System.currentTimeMillis()
            try {
                val streams = runForApi(api, request, identity, source, timeoutMs)
                Log.d(TAG, "$pluginName/${api.name}: ${streams.size} akış (${System.currentTimeMillis() - started} ms)")
                results += streams
                if (results.isNotEmpty()) break
            } catch (t: Throwable) {
                Log.d(TAG, "$pluginName/${api.name}: hata ${t.javaClass.simpleName}: ${t.message} (${System.currentTimeMillis() - started} ms)")
            }
        }
        return results
    }

    private suspend fun runForApi(
        api: MainAPI,
        request: StreamRequest,
        identity: ContentIdentity,
        source: StreamSource,
        timeoutMs: Long,
    ): List<PlayableStream> {
        // 1-2. Arama (yerel başlık, özgün başlık, alternatif) ve yıl/başlık eşleştirmesi
        val match = findMatch(api, identity, timeoutMs) ?: return emptyList()

        // 3. Detay yükle
        val loadResp = withTimeout(timeoutMs) {
            try { api.load(match.url) } catch (t: Throwable) {
                Log.d(TAG, "${api.name}: load hata ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
        }
        Log.d(TAG, "${api.name}: load ${if (loadResp != null) "başarılı" else "boş"}")
        if (loadResp == null) return emptyList()

        // 3b. Yükleme sonrası doğrulama: eklentinin bildirdiği ad/yıl/kimlik istenen içerikle uyuşmuyorsa kaynak atılır.
        val info = loadedInfo(loadResp)
        when (val verdict = CsMatchRules.verifyLoaded(info, identity)) {
            Verdict.Ok -> Log.d(TAG, "${api.name}: doğrulandı (ad=\"${info.name}\", yıl=${info.year}, tmdb=${info.tmdbId}, imdb=${info.imdbId})")
            is Verdict.Reject -> {
                Log.d(TAG, "${api.name}: kaynak ATILDI, yüklenen içerik istekle uyuşmuyor: ${verdict.reason} (ad=\"${info.name}\", yıl=${info.year})")
                return emptyList()
            }
        }

        // 4. Link alma
        val data = resolveData(loadResp, request)
        if (data == null) {
            Log.d(TAG, "${api.name}: resolveData boş (sezon/bölüm eşleşmedi)")
            return emptyList()
        }
        return loadLinksDirect(api, data, source, timeoutMs)
    }

    /**
     * Başlık sorgularını sırayla arar (ilk eşleşmede durur); sonuç boşsa noktalamasız halini de dener (bazı site aramaları ":" / "-"
     * ile boş döner). Dönen aday [CsMatchRules] eşiğini geçmiştir; geçen yoksa null (günlükte en çok 3 ret sebebi).
     */
    private suspend fun findMatch(api: MainAPI, identity: ContentIdentity, timeoutMs: Long): MatchCandidate? {
        val collected = LinkedHashMap<String, MatchCandidate>()
        var lastRejections: List<String> = emptyList()
        val queries = buildList {
            addAll(identity.searchQueries(max = 5))
            identity.imdbId?.takeIf { it.startsWith("tt") }?.let { if (it !in this) add(it) }
            identity.year?.let { y ->
                val primaryTitle = identity.titles.firstOrNull()?.trim().orEmpty()
                if (primaryTitle.isNotBlank()) {
                    val ty = "$primaryTitle $y"
                    if (ty !in this) add(ty)
                }
                val origTitle = identity.titles.drop(1).firstOrNull()?.trim().orEmpty()
                if (origTitle.isNotBlank() && origTitle != primaryTitle) {
                    val oy = "$origTitle $y"
                    if (oy !in this) add(oy)
                }
            }
        }.distinct()
        for (query in queries) {
            var results = search(api, query, timeoutMs) ?: continue
            if (results.isEmpty()) {
                val plain = TitleText.plainQuery(query)
                if (plain != query) results = search(api, plain, timeoutMs) ?: emptyList()
            }
            for (r in results) collected.putIfAbsent(r.url, candidateOf(r))
            when (val outcome = CsMatchRules.pick(collected.values.toList(), identity)) {
                is MatchOutcome.Match -> {
                    val best = outcome.best
                    Log.d(TAG, "${api.name}: eşleşme \"${best.candidate.name}\" (yıl=${best.candidate.year}) puan=${"%.2f".format(java.util.Locale.ROOT, best.score)}")
                    return best.candidate
                }
                is MatchOutcome.NoMatch -> lastRejections = outcome.rejections
            }
        }
        Log.d(TAG, "${api.name}: matchResult yok${if (lastRejections.isEmpty()) "" else " (reddedilen: ${lastRejections.joinToString("; ")})"}")
        return null
    }

    private fun candidateOf(r: SearchResponse): MatchCandidate = MatchCandidate(
        name = r.name,
        year = when (r) {
            is MovieSearchResponse -> r.year
            is TvSeriesSearchResponse -> r.year
            is AnimeSearchResponse -> r.year
            else -> null
        },
        url = r.url,
        kind = when (r.type) {
            TvType.Movie, TvType.AnimeMovie -> ContentKind.Movie
            TvType.TvSeries, TvType.Anime, TvType.OVA, TvType.AsianDrama -> ContentKind.Tv
            else -> null
        },
        otherNames = (r as? AnimeSearchResponse)?.otherName?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty(),
    )

    private fun loadedInfo(resp: LoadResponse): CsMatchRules.LoadedInfo = CsMatchRules.LoadedInfo(
        name = resp.name,
        year = resp.year,
        imdbId = runCatching { with(LoadResponse.Companion) { resp.getImdbId() } }.getOrNull(),
        tmdbId = runCatching { with(LoadResponse.Companion) { resp.getTMDbId() } }.getOrNull(),
        durationMinutes = resp.duration,
    )

    suspend fun loadLinksDirect(
        api: MainAPI,
        data: String,
        source: StreamSource,
        timeoutMs: Long,
    ): List<PlayableStream> {
        val extApis = com.lagradost.cloudstream3.utils.extractorApis
        val customExts = listOf(
            com.kaan.play.data.cloudstream.extractors.Molystream(),
            com.kaan.play.data.cloudstream.extractors.DbxMolystream(),
            com.kaan.play.data.cloudstream.extractors.MolyTo(),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Pichive", "https://four.pichive.online"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Pichive", "https://pichive.online"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Hotlinger", "https://sn.hotlinger.com"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("Hotlinger", "https://hotlinger.com"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("DPlayer", "https://four.dplayer82.site"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("DPlayer", "https://sn.dplayer82.site"),
            com.kaan.play.data.cloudstream.extractors.PichiveExtractor("DPlayer", "https://dplayer82.site"),
            com.kaan.play.data.cloudstream.extractors.JapierdoleExtractor("Japierdole", "https://japierdolevid.com"),
            com.kaan.play.data.cloudstream.extractors.ByseJikuar(),
        )
        synchronized(extApis) {
            extApis.removeAll {
                it.mainUrl.contains("molystream", ignoreCase = true) ||
                    it.mainUrl.contains("moly.to", ignoreCase = true) ||
                    it.mainUrl.contains("hotlinger", ignoreCase = true) ||
                    it.mainUrl.contains("pichive", ignoreCase = true) ||
                    it.mainUrl.contains("dplayer", ignoreCase = true) ||
                    it.mainUrl.contains("japierdolevid", ignoreCase = true) ||
                    it.mainUrl.contains("bysejikuar", ignoreCase = true)
            }
            customExts.asReversed().forEach { ext ->
                if (extApis.none { it.mainUrl.equals(ext.mainUrl, ignoreCase = true) }) extApis.add(0, ext)
            }
        }

        val links = mutableListOf<ExtractorLink>()
        val subs = mutableListOf<SubtitleFile>()
        withTimeout(timeoutMs) {
            try {
                api.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { subs += it },
                    callback = { link ->
                        registerLoopbackPortIfAny(link.url)
                        links += link
                    },
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                Log.d(TAG, "${api.name}: loadLinks hata ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }
        Log.d(TAG, "${api.name}: loadLinks ${links.size} ExtractorLink, ${subs.size} SubtitleFile " +
            links.map { "${it.name}/${it.type}/${it.quality}@${runCatching { java.net.URI(it.url).host }.getOrNull()}" })

        return links.map { link ->
            PlayableStream(
                source = source,
                url = link.url,
                resolution = qualityToResolution(link.quality),
                sizeBytes = null,
                headers = buildMap {
                    put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    if (link.referer.isNotEmpty()) put("Referer", link.referer)
                    putAll(link.headers)
                },
                subtitles = subs.map { sub ->
                    val subHeaders = buildMap {
                        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        if (link.referer.isNotEmpty()) put("Referer", link.referer)
                        sub.headers?.let { putAll(it) }
                    }
                    StreamSubtitle(url = sub.url, language = sub.lang, headers = subHeaders)
                },
                language = null,
                dubbed = com.kaan.play.data.stream.DubDetector.isDubbed(link.name),
                mimeType = when (link.type) {
                    ExtractorLinkType.M3U8 -> MimeTypes.APPLICATION_M3U8
                    ExtractorLinkType.DASH -> MimeTypes.APPLICATION_MPD
                    else -> null
                },
            )
        }
    }

    /** TV kataloğundaki search URL’sini API.load ile canlı dataUrl’ye çözer; eski eklentiler için raw URL yedeği vardır. */
    suspend fun loadLiveLinks(
        api: MainAPI,
        catalogUrl: String,
        source: StreamSource,
        timeoutMs: Long,
    ): List<PlayableStream> {
        val response = try {
            withTimeout(timeoutMs) { api.load(catalogUrl) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (t: Throwable) {
            Log.d(TAG, "${api.name}: live load hata ${t.javaClass.simpleName}: ${t.message}")
            null
        }
        val resolvedData = when (response) {
            is LiveStreamLoadResponse -> response.dataUrl
            // Bazı eklentiler canlı kanalı yanlışlıkla MovieSearchResponse/MovieLoadResponse olarak beyan ediyor.
            is MovieLoadResponse -> response.dataUrl
            else -> null
        }?.takeIf(String::isNotBlank)
        val candidates = listOfNotNull(resolvedData, catalogUrl.takeIf(String::isNotBlank)).distinct()
        for (data in candidates) {
            val streams = try {
                loadLinksDirect(api, data, source, timeoutMs)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Throwable) {
                Log.d(TAG, "${api.name}: live link adayı başarısız (${t.javaClass.simpleName})")
                emptyList()
            }
            if (streams.isNotEmpty()) return streams
        }
        return emptyList()
    }


    private suspend fun search(api: MainAPI, query: String, timeoutMs: Long): List<SearchResponse>? {
        val results = withTimeout(timeoutMs) {
            try { api.search(query) } catch (t: Throwable) {
                Log.d(TAG, "${api.name}: search hata ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
        } ?: return null
        Log.d(TAG, "${api.name}: search \"$query\" ${results.size} sonuç ${results.take(5).map { it.name }}")
        return results
    }

    /**
     * Yüklenen içerikten oynatma verisini seçer. Tür tutarlılığı: film isteğine çok bölümlü dizi, dizi isteğine tek parça film verilmez
     * (yanlış içerik yerine boş döner); bölüm seçimi [EpisodePicker] kurallarıyla (başka sezonun bölümü asla).
     */
    private fun resolveData(resp: LoadResponse, request: StreamRequest): String? = when (resp) {
        is MovieLoadResponse -> if (request.type == MediaType.Tv) null else resp.dataUrl
        is LiveStreamLoadResponse -> if (request.addonType?.lowercase() in setOf("tv", "channel")) resp.dataUrl else null
        is TvSeriesLoadResponse -> pickEpisode(resp.episodes, request)
        is AnimeLoadResponse -> pickEpisode(resp.episodes.values.flatten(), request)
        else -> null
    }

    private fun pickEpisode(episodes: List<com.lagradost.cloudstream3.Episode>, request: StreamRequest): String? {
        if (request.type == MediaType.Movie) {
            // Film isteği: tek parçalı "dizi" kabul edilir; çok bölümlü gerçek bir dizi film yerine geçemez.
            return if (episodes.size == 1) episodes.first().data else null
        }
        return EpisodePicker.pick(episodes.map { EpisodeRef(it.data, it.name, it.season, it.episode) }, request.season, request.episode)
    }

    private fun qualityToResolution(quality: Int): Int? = when {
        quality <= 0 -> null
        quality < 480 -> 360
        quality < 720 -> 480
        quality < 1080 -> 720
        quality < 2160 -> 1080
        else -> 2160
    }

    /**
     * Eklentinin ana sayfa satırlarını çeker.
     * Her [MainPageData] girdisi için ayrı [withTimeout] uygulanır;
     * hata olursa o satır atlanır / uygulama asla çökmez.
     */
    suspend fun mainPage(
        api: MainAPI,
        page: Int = 1,
        timeoutMs: Long = 6_000L,
        maxSectionsPerApi: Int = Int.MAX_VALUE,
        sectionNameFilter: ((String) -> Boolean)? = null,
    ): List<CsCatalogSection> {
        val sectionsResult = runCatching { api.mainPage }
        val sections = sectionsResult.getOrNull()
        val selectedSections = sections?.filter { sectionNameFilter?.invoke(it.name) != false }
        val results = mutableListOf<CsCatalogSection>()

        if (sectionsResult.isFailure) {
            val err = sectionsResult.exceptionOrNull()
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: api.mainPage istisna fırlattı -> ${err?.javaClass?.name}: ${err?.message}")
        } else if (sections.isNullOrEmpty()) {
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: api.mainPage boş liste (hasMainPage=${api.hasMainPage})")
        } else {
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: api.mainPage dolu, ${sections.size} bölüm bulundu: ${sections.map { it.name }}")
        }

        val mainPageStarted = System.currentTimeMillis()
        if (!selectedSections.isNullOrEmpty()) {
            for (section in selectedSections.take(maxSectionsPerApi.coerceAtLeast(0))) {
                // Eklenti mainUrl'den farklı (güncel) bir alan adına gidebilir: engeli o istekten öğren.
                val cf = com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
                if (cf.wasBlocked(api.mainUrl) || cf.unresolvedSince(mainPageStarted) != null) {
                    Log.w(TAG, "[DEBUG_MAINPAGE] ${api.name}: Bot koruması aşılamadığı için kalan bölümler atlanıyor.")
                    break
                }
                // DiziPal gibi 20+ bölüm tanımlayan sitelerde 10+ satır alındıysa ve süre uzadıysa sonlandır
                if (results.size >= 10 && System.currentTimeMillis() - mainPageStarted > 12_000L) {
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: Yeterli bölüm (${results.size}) toplandı, kalan bölümler atlanıyor.")
                    break
                }
                val started = System.currentTimeMillis()
                try {
                    if (results.isNotEmpty()) {
                        kotlinx.coroutines.delay(100L)
                    }
                    val req = com.lagradost.cloudstream3.MainPageRequest(section.name, section.data, section.horizontalImages)
                    val resp = withTimeout(timeoutMs) {
                        api.getMainPage(page, req)
                    }
                    if (resp == null) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: getMainPage null döndü (${System.currentTimeMillis() - started} ms)")
                    } else if (resp.items.isEmpty()) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: resp.items boş liste (hasNext=${resp.hasNext}) (${System.currentTimeMillis() - started} ms)")
                    } else {
                        val totalItems = resp.items.sumOf { it.list.size }
                        if (totalItems == 0) {
                            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: resp.items dolu (${resp.items.size} satır) ama içindeki item.list'ler boş (${System.currentTimeMillis() - started} ms)")
                        } else {
                            for (item in resp.items) {
                                val title = item.name.ifBlank { section.name }
                                results += CsCatalogSection(
                                    title = title,
                                    items = item.list,
                                    isHorizontalImages = item.isHorizontalImages,
                                    hasNext = resp.hasNext,
                                )
                            }
                            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: başarılı, $totalItems öğe (${System.currentTimeMillis() - started} ms)")
                        }
                    }
                } catch (t: Throwable) {
                    currentCoroutineContext().ensureActive()
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: istisna -> ${t.javaClass.name}: ${t.message} (${System.currentTimeMillis() - started} ms)")
                    if (t.cause != null) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}/${section.name}: kök neden -> ${t.cause?.javaClass?.name}: ${t.cause?.message}")
                    }
                }
            }
        } else if (api.hasMainPage) {
            val started = System.currentTimeMillis()
            try {
                Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage=true, tekil getMainPage çağrılıyor (sayfa=$page)...")
                val req = com.lagradost.cloudstream3.MainPageRequest(api.name, api.mainUrl, horizontalImages = false)
                val resp = withTimeout(timeoutMs) { api.getMainPage(page, req) }
                if (resp == null) {
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage ile getMainPage null döndü (${System.currentTimeMillis() - started} ms)")
                } else if (resp.items.isEmpty()) {
                    Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage ile resp.items boş (${System.currentTimeMillis() - started} ms)")
                } else {
                    val totalItems = resp.items.sumOf { it.list.size }
                    if (totalItems == 0) {
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage ile resp.items dolu ama listeler boş (${System.currentTimeMillis() - started} ms)")
                    } else {
                        for (item in resp.items) {
                            results += CsCatalogSection(
                                title = item.name.ifBlank { api.name },
                                items = item.list,
                                isHorizontalImages = item.isHorizontalImages,
                                hasNext = resp.hasNext,
                            )
                        }
                        Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage başarılı, $totalItems öğe (${System.currentTimeMillis() - started} ms)")
                    }
                }
            } catch (t: Throwable) {
                currentCoroutineContext().ensureActive()
                Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hasMainPage istisna -> ${t.javaClass.name}: ${t.message} (${System.currentTimeMillis() - started} ms)")
            }
        } else {
            Log.d(TAG, "[DEBUG_MAINPAGE] ${api.name}: hem api.mainPage boş hem de hasMainPage=false, katalog yok")
        }

        return results
    }

    /** Ana sayfa kataloğu olmayan Live API'ler için kısa arama yedeği. Yalnız Live türündeki sonuçlar döner. */
    suspend fun liveSearch(
        apis: List<MainAPI>,
        queries: List<String> = listOf("live", "tv", "canlı"),
        timeoutMs: Long = 1_000L,
        isLiveCandidate: (SearchResponse) -> Boolean = { it is LiveSearchResponse || it.type == TvType.Live },
    ): List<CsCatalogSection> {
        val sections = mutableListOf<CsCatalogSection>()
        for (api in apis) {
            val found = LinkedHashMap<String, SearchResponse>()
            for (query in queries) {
                val results: List<SearchResponse> = try {
                    withTimeout(timeoutMs) { api.search(query) }.orEmpty()
                } catch (t: Throwable) {
                    currentCoroutineContext().ensureActive()
                    Log.d(TAG, "${api.name}: live search '$query' başarısız: ${t.javaClass.simpleName}")
                    emptyList()
                }
                results.filter(isLiveCandidate)
                    .forEach { found.putIfAbsent(it.url.ifBlank { it.name }, it) }
                if (found.isNotEmpty()) break
            }
            if (found.isNotEmpty()) {
                sections += CsCatalogSection(title = "Canlı Kanallar", items = found.values.toList())
            }
        }
        return sections
    }

    suspend fun mainPage(
        apis: List<MainAPI>,
        page: Int = 1,
        timeoutMs: Long = 27_000L,
        maxSectionsPerApi: Int = Int.MAX_VALUE,
    ): List<CsCatalogSection> {
        val results = mutableListOf<CsCatalogSection>()
        for (api in apis) {
            results += mainPage(api, page, timeoutMs, maxSectionsPerApi)
        }
        return results
    }

    private fun registerLoopbackPortIfAny(url: String) {
        runCatching {
            val parsed = java.net.URL(url)
            val host = (parsed.host ?: "").removePrefix("[").removeSuffix("]").trim().lowercase()
            val port = if (parsed.port != -1) parsed.port else parsed.defaultPort
            if (port in 1..65535 && (host == "localhost" || host == "127.0.0.1" || host.startsWith("127."))) {
                PlayNetworkGuard.allowLoopbackPort(port)
            }
        }
    }

    private companion object {
        const val TAG = "PlayCs"
    }
}

/** CS ana sayfa satırı sonucu (başlık + SearchResponse listesi). */
data class CsCatalogSection(
    val title: String,
    val items: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
    val hasNext: Boolean = false,
)
