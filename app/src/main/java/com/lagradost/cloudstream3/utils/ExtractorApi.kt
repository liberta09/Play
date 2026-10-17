package com.lagradost.cloudstream3.utils

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

/** Çıkarıcı temel sınıfı. Eklentiler kendi çıkarıcılarını buradan türetebilir. */
abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    open val requiresSubUrl: Boolean get() = false

    abstract suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    )

    open fun getExtractorUrl(id: String): String = "$mainUrl/$id"
}

/**
 * Eklentilerin `registerExtractorAPI` ile kaydettiği çıkarıcılar. Aynı sınıf yeniden kaydedilince
 * eskisinin yerine geçer (eklentiler her kaynak isteğinde yeniden yüklenir).
 */
val extractorApis: MutableList<ExtractorApi> = CopyOnWriteArrayList()

fun registerExtractor(extractor: ExtractorApi) {
    extractorApis.removeAll { it.javaClass.name == extractor.javaClass.name && it.mainUrl == extractor.mainUrl }
    extractorApis += extractor
}

private fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase()?.removePrefix("www.") }.getOrNull()

/** [url]'nin sunucusu çıkarıcının `mainUrl` sunucusuyla aynı ya da onun alt alan adıysa eşleşir. */
private fun findExtractor(url: String): ExtractorApi? {
    val host = hostOf(url) ?: return null
    return extractorApis.firstOrNull { api ->
        val apiHost = hostOf(api.mainUrl) ?: return@firstOrNull false
        host == apiHost || host.endsWith(".$apiHost")
    }
}

/**
 * Dışarıdan çağrılabilir `loadExtractor`: bir video host URL'sinden kaynak çıkarmayı dener.
 * Önce eklentilerin kaydettiği çıkarıcılar ([extractorApis]) sunucu adına göre denenir.
 * CloudStream'in yerleşik çıkarıcıları UYGULANMAZ; eşleşen çıkarıcı yoksa yalnızca adres
 * doğrudan medya gibi görünüyorsa (m3u8, mpd, mp4 …) `callback`'e gönderilir, aksi halde
 * oynatılamayacak bir gömme sayfası kaynak listesine girmesin diye atlanır.
 */
suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    findExtractor(url)?.let { extractor ->
        Log.d("PlayCs", "loadExtractor: ${extractor.name} ← ${hostOf(url)}")
        extractor.getUrl(url, referer, subtitleCallback, callback)
        return true
    }

    // Built-in fallback for common embed pages. Plugin-registered host-specific extractors always
    // take priority; this resolver only follows a small, bounded iframe chain and direct media URLs.
    val resolved = BuiltInEmbedResolver.resolve(url, referer, callback)
    if (resolved) return true

    val path = lower.substringBefore('?')
    val isM3u8 = lower.contains("m3u8")
    val isDash = path.endsWith(".mpd")
    val isFile = MEDIA_EXTENSIONS.any { path.endsWith(it) }
    if (!isM3u8 && !isDash && !isFile) {
        Log.d("PlayCs", "loadExtractor: çıkarıcı yok, atlandı ← ${hostOf(url)}")
        return false
    }
    callback(
        ExtractorLink(
            source = "Direct",
            name = "Direct",
            url = url,
            type = when { isM3u8 -> ExtractorLinkType.M3U8; isDash -> ExtractorLinkType.DASH; else -> ExtractorLinkType.VIDEO },
        ).apply { this.referer = referer ?: "" },
    )
    return true
}

private val MEDIA_EXTENSIONS = listOf(".mp4", ".mkv", ".webm", ".m4v", ".mov", ".ts")

/** Eski imza — eklentiler referer'sız da çağırabilir. */
suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
): Boolean = loadExtractor(url, null, subtitleCallback, callback)

/** `newExtractorLink { ... }` builder'ı; eklentiler `quality`/`referer`/`headers` burada ayarlar. */
suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    initializer: suspend ExtractorLink.() -> Unit = {},
): ExtractorLink {
    val link = ExtractorLink(source, name, url, type)
    link.initializer()
    return link
}

/** Görünen ad metninden kalite tahmini ("1080p", "HD", "CAM" vb.). */
fun getQualityFromName(name: String?): Int = getQualityFromString(name)

/**
 * `eval(function(p,a,c,k,e,d){...})` biçimindeki paketlenmiş (Dean Edwards "packer") JS'i açar.
 * Genel/kamuya açık bir teknik uygulanır (CloudStream'e özgü kod kopyalanmaz); eşleşme yoksa
 * metin olduğu gibi döner.
 */
fun getAndUnpack(script: String): String {
    val match = PACKER_REGEX.find(script) ?: return script
    return try {
        val (payload, radixRaw, countRaw, keywordsRaw) = match.destructured
        val radix = radixRaw.toIntOrNull() ?: 36
        val count = countRaw.toIntOrNull() ?: 0
        val keywords = keywordsRaw.split("|")
        var result = payload
        for (i in count - 1 downTo 0) {
            val word = keywords.getOrNull(i)
            if (!word.isNullOrEmpty()) {
                result = Regex("\\b${unbase(i, radix)}\\b").replace(result, Regex.escapeReplacement(word))
            }
        }
        result
    } catch (_: Exception) {
        script
    }
}

private val PACKER_REGEX = Regex(
    """\}\('(.*)',\s*(\d+),\s*(\d+),\s*'(.*?)'\.split\('\|'\)""",
    RegexOption.DOT_MATCHES_ALL,
)

private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

private fun unbase(value: Int, base: Int): String {
    if (value == 0) return ALPHABET[0].toString()
    if (base in 2..36) return value.toString(base)
    var v = value
    val sb = StringBuilder()
    while (v > 0) { sb.append(ALPHABET[v % base]); v /= base }
    return sb.reverse().toString()
}


/**
 * Lightweight built-in embed resolver used when no plugin-specific ExtractorApi matches.
 *
 * It supports direct media URLs and HTML pages exposing <video>/<source>, common player config
 * fields, or iframe embeds. It deliberately does not claim to replace CloudStream's complete
 * host-specific extractor catalogue: obfuscated/tokenized hosts still need dedicated extractors.
 */
private object BuiltInEmbedResolver {
    private const val MAX_IFRAME_DEPTH = 2
    private const val MAX_BODY_BYTES = 2 * 1024 * 1024
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val mediaPattern = Regex(
        """(?i)(https?:\\?/\\?/[^\\s"'<>\\\\]+?\\.(?:m3u8|mpd|mp4|m4v|webm|mp3)(?:\\?[^\\s"'<>\\\\]*)?)"""
    )
    private val iframePattern = Regex("""(?is)<iframe\\b[^>]*?src\\s*=\\s*["']([^"']+)["']""")

    suspend fun resolve(
        initialUrl: String,
        initialReferer: String?,
        callback: (ExtractorLink) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val visited = HashSet<String>()
        val queue = ArrayDeque<Pair<String, String?>>()
        queue.add(initialUrl to initialReferer)
        var emitted = false
        var depth = 0
        while (queue.isNotEmpty() && depth <= MAX_IFRAME_DEPTH) {
            val levelCount = queue.size
            repeat(levelCount) {
                val (currentUrl, parentReferer) = queue.removeFirst()
                val normalized = currentUrl.trim().replace("\\/", "/")
                if (!isHttpUrl(normalized) || !visited.add(normalized)) return@repeat
                val directType = directType(normalized)
                if (directType != null) {
                    callback(makeLink(normalized, parentReferer, directType))
                    emitted = true
                    return@repeat
                }
                val response = runCatching {
                    val request = Request.Builder()
                        .url(normalized)
                        .header("User-Agent", DEFAULT_UA)
                        .apply { if (!parentReferer.isNullOrBlank()) header("Referer", parentReferer) }
                        .build()
                    client.newCall(request).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        val body = res.body ?: return@use null
                        val type = body.contentType()?.toString().orEmpty()
                        if (!type.contains("html", ignoreCase = true) && !type.contains("text", ignoreCase = true)) return@use null
                        body.source().let { source ->
                            source.request(MAX_BODY_BYTES.toLong())
                            source.buffer.clone().readUtf8(minOf(source.buffer.size, MAX_BODY_BYTES.toLong()))
                        }
                    }
                }.getOrNull() ?: return@repeat
                val doc = Jsoup.parse(response, normalized)
                val candidates = LinkedHashSet<String>()
                doc.select("video[src], video source[src], source[src], a[href]").forEach { el ->
                    val value = el.attr("abs:src").ifBlank { el.attr("abs:href") }
                    if (directType(value) != null) candidates += value
                }
                mediaPattern.findAll(response).forEach { match ->
                    val candidate = match.value.replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
                    if (directType(candidate) != null) candidates += resolveUrl(normalized, candidate)
                }
                candidates.forEach { candidate ->
                    val type = directType(candidate) ?: return@forEach
                    callback(makeLink(candidate, normalized, type))
                    emitted = true
                }
                if (depth < MAX_IFRAME_DEPTH) {
                    iframePattern.findAll(response).forEach { match ->
                        val iframe = resolveUrl(normalized, match.groupValues[1].replace("&amp;", "&"))
                        if (isHttpUrl(iframe) && iframe !in visited) queue.add(iframe to normalized)
                    }
                }
            }
            depth++
        }
        if (emitted) Log.d("PlayCs", "built-in embed resolver: medya bulundu ← ${hostOf(initialUrl)}")
        emitted
    }

    private fun makeLink(url: String, referer: String?, type: ExtractorLinkType) =
        ExtractorLink(source = "CloudStream Built-in", name = "Built-in", url = url, type = type).apply {
            this.referer = referer.orEmpty()
            this.quality = getQualityFromString(url)
            this.headers = mapOf("User-Agent" to DEFAULT_UA)
        }

    private fun directType(url: String): ExtractorLinkType? {
        if (!isHttpUrl(url)) return null
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        return when {
            ".m3u8" in path -> ExtractorLinkType.M3U8
            path.endsWith(".mpd") -> ExtractorLinkType.DASH
            MEDIA_EXTENSIONS.any(path::endsWith) -> ExtractorLinkType.VIDEO
            else -> null
        }
    }

    private fun isHttpUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    private fun resolveUrl(base: String, value: String): String =
        runCatching { URI(base).resolve(value).toString() }.getOrDefault(value)

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}
