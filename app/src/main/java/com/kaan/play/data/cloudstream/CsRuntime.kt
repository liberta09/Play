package com.kaan.play.data.cloudstream

import android.util.Log
import androidx.media3.common.MimeTypes
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

/**
 * CloudStream eklentisi çalıştırıcısı. Yüklenmiş [MainAPI] örnekleriyle şu akışı izler:
 *  1. [MainAPI.search] ile başlık arar
 *  2. Başlık eşlemesi ([matchResult])
 *  3. [MainAPI.load] ile detay yükler
 *  4. Film: `dataUrl` ile [MainAPI.loadLinks] çağırır
 *     Dizi: hedef sezonu/bölümü [Episode] listesinden bulup [MainAPI.loadLinks] çağırır
 *  5. Gelen [ExtractorLink]'leri [PlayableStream]'e çevirir
 *
 * [timeoutMs] her adım için ayrı uygulanır.
 */
class CsRuntime {

    /**
     * Kayıtlı [MainAPI] örneklerinden oynatılabilir kaynak listesi döner.
     * [title] TMDB'den gelen başlık; CS eklentisi bu başlıkla arama yapar.
     */
    suspend fun streams(
        apis: List<MainAPI>,
        request: StreamRequest,
        title: String,
        pluginRowId: Long,
        pluginName: String,
        timeoutMs: Long = 120_000L,
    ): List<PlayableStream> {
        val source = StreamSource(addonId = pluginRowId, addonName = pluginName)
        val results = mutableListOf<PlayableStream>()

        Log.d(TAG, "$pluginName: ${apis.size} MainAPI ile deneniyor (başlık=\"$title\")")
        for (api in apis) {
            val started = System.currentTimeMillis()
            try {
                val streams = runForApi(api, request, title, source, timeoutMs)
                Log.d(TAG, "$pluginName/${api.name}: ${streams.size} akış (${System.currentTimeMillis() - started} ms)")
                results += streams
                if (results.isNotEmpty()) break
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Exception) {
                Log.w(TAG, "$pluginName/${api.name}: hata ${t.javaClass.simpleName}: ${t.message} (${System.currentTimeMillis() - started} ms)", t)
            }
        }
        return results
    }

    private suspend fun runForApi(
        api: MainAPI,
        request: StreamRequest,
        title: String,
        source: StreamSource,
        timeoutMs: Long,
    ): List<PlayableStream> {
        // 1. Arama; sonuç yoksa noktalamasız başlıkla bir kez daha (bazı site aramaları ":" / "-" ile boş döner)
        var searchResults = search(api, title, timeoutMs) ?: return emptyList()
        val plain = normalize(title)
        if (searchResults.isEmpty() && plain != title.lowercase()) {
            searchResults = search(api, plain, timeoutMs) ?: return emptyList()
        }

        // 2. Eşleştirme
        val match = matchResult(searchResults, title)
        Log.d(TAG, "${api.name}: matchResult ${if (match != null) "bulundu" else "yok"}")
        if (match == null) return emptyList()

        // 3. Detay yükle
        val loadResp = withTimeout(timeoutMs) {
            try { api.load(match.url) } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Exception) {
                Log.w(TAG, "${api.name}: load hata ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
        }
        Log.d(TAG, "${api.name}: load ${if (loadResp != null) "başarılı" else "boş"}")
        if (loadResp == null) return emptyList()

        // 4. Link alma
        val links = mutableListOf<ExtractorLink>()
        val subs = mutableListOf<SubtitleFile>()
        val data = resolveData(loadResp, request)
        if (data == null) {
            Log.d(TAG, "${api.name}: resolveData boş (sezon/bölüm eşleşmedi)")
            return emptyList()
        }

        withTimeout(timeoutMs) {
            try {
                api.loadLinks(
                    data = data,
                    isCasting = false,
                    subtitleCallback = { subtitle -> synchronized(subs) { subs += subtitle } },
                    callback = { link -> synchronized(links) { links += link } },
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Exception) {
                Log.w(TAG, "${api.name}: loadLinks hata ${t.javaClass.simpleName}: ${t.message}", t)
            }
        }
        Log.d(TAG, "${api.name}: loadLinks ${links.size} ExtractorLink " +
            links.map { "${it.name}/${it.type}/${it.quality}@${runCatching { java.net.URI(it.url).host }.getOrNull()}" })

        // 5. PlayableStream'e çevir
        return links.map { link ->
            PlayableStream(
                source = source,
                url = link.url,
                resolution = qualityToResolution(link.quality),
                sizeBytes = null,
                headers = buildMap {
                    if (link.referer.isNotEmpty()) put("Referer", link.referer)
                    putAll(link.headers)
                },
                subtitles = subs.map { StreamSubtitle(url = it.url, language = it.lang) },
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

    private suspend fun search(api: MainAPI, query: String, timeoutMs: Long): List<SearchResponse>? {
        val results = withTimeout(timeoutMs) {
            try { api.search(query) } catch (cancel: CancellationException) {
                throw cancel
            } catch (t: Exception) {
                Log.w(TAG, "${api.name}: search hata ${t.javaClass.simpleName}: ${t.message}", t)
                null
            }
        } ?: return null
        Log.d(TAG, "${api.name}: search \"$query\" ${results.size} sonuç ${results.take(5).map { it.name }}")
        return results
    }

    /** Önce birebir, sonra noktalama/büyük-küçük harf yok sayılarak birebir, en son kelime sınırlı içerme. */
    private fun matchResult(results: List<SearchResponse>, title: String): SearchResponse? {
        val plain = normalize(title)
        return results.firstOrNull { it.name == title }
            ?: results.firstOrNull { normalize(it.name) == plain }
            ?: results.firstOrNull {
                val name = normalize(it.name)
                name.isNotEmpty() && (" $name ".contains(" $plain ") || " $plain ".contains(" $name "))
            }
    }

    /** Harf/rakam dışını boşluğa çevirir, boşlukları teke indirir; Türkçe büyük harfler (İ, I) doğru küçülür. */
    private fun normalize(text: String): String =
        text.lowercase(TURKISH).replace(NON_WORD, " ").trim().replace(SPACES, " ")

    private fun resolveData(resp: LoadResponse, request: StreamRequest): String? = when (resp) {
        is MovieLoadResponse -> resp.dataUrl
        is LiveStreamLoadResponse -> resp.dataUrl
        is TvSeriesLoadResponse -> {
            val s = request.season ?: return null
            val e = request.episode ?: return null
            resp.episodes.firstOrNull { it.season == s && it.episode == e }?.data
                ?: resp.episodes.firstOrNull { it.episode == e }?.data
        }
        is AnimeLoadResponse -> {
            val e = request.episode ?: return null
            resp.episodes.values.flatten().firstOrNull { it.episode == e }?.data
        }
        else -> null
    }

    private fun qualityToResolution(quality: Int): Int? = when {
        quality <= 0 -> null
        quality < 480 -> 360
        quality < 720 -> 480
        quality < 1080 -> 720
        quality < 2160 -> 1080
        else -> 2160
    }

    private companion object {
        const val TAG = "PlayCs"
        val TURKISH: java.util.Locale = java.util.Locale.forLanguageTag("tr")
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        val SPACES = Regex("\\s+")
    }
}
