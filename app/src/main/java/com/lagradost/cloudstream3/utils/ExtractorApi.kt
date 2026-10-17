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
 * Eşleşen eklenti çıkarıcısı yoksa sınırlı yerleşik HTML/iframe çözümleyicisi denenir.
 * Bu çözümleyici doğrudan medya adreslerini ve HTML'de açıkça görünen medya kaynaklarını
 * bulabilir; token/şifreleme kullanan hostlar için tam hosta özel CloudStream çıkarıcısı gerekir.
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

    // Dedicated host-specific extractors run before the generic HTML/iframe fallback.
    val host = hostOf(url)
    if (host in DOOD_HOSTS && DoodHostExtractor.resolve(url, referer, callback)) return true
    if (host in MIXDROP_HOSTS && MixDropHostExtractor.resolve(url, referer, callback)) return true

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
        """(?i)(https?://[^\s"'<>\\]+?\.(?:m3u8|mpd|mp4|m4v|webm|mp3)(?:\?[^\s"'<>\\]*)?)"""
    )
    private val iframePattern = Regex("""(?is)<iframe\b[^>]*?src\s*=\s*["']([^"']+)["']""")

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
                mediaPattern.findAll(response.replace("\\/", "/")).forEach { match ->
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
            this.quality = qualityFromUrl(url)
            this.headers = mapOf("User-Agent" to DEFAULT_UA)
        }

    private fun qualityFromUrl(value: String): Int {
        val match = Regex("""(?i)(2160|1440|1080|720|480|360)p?""").find(value)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: Qualities.Unknown.value
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


/** Domains sharing CloudStream's DoodStream pass_md5 token flow. */
private val DOOD_HOSTS = setOf(
    "dood.la", "dood.ws", "dood.to", "dood.so", "dood.pm", "dood.sh", "dood.watch",
    "dood.cx", "dood.wf", "dood.yt", "dood.li", "doods.pro", "dsvplay.com",
    "d0000d.com", "d000d.com", "doodstream.com", "dooood.com", "ds2play.com",
    "ds2video.com", "myvidplay.com", "playmogo.com", "vide0.net"
)
private val MIXDROP_HOSTS = setOf(
    "mixdrop.co", "mixdrop.to", "mixdrop.bz", "mixdrop.ch", "mixdrop.ag",
    "mixdrop.ps", "mixdrop.si", "mxdrop.to", "mdy48tn97.com"
)

/**
 * DoodStream's host-specific flow. The final URL is time/token based, so resolve it at playback
 * time rather than caching it. This is adapted to Play's local ExtractorLink API.
 */
private object DoodHostExtractor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).followRedirects(true).build()
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"
    private val random = java.security.SecureRandom()
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    suspend fun resolve(url: String, referer: String?, callback: (ExtractorLink) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val embedUrl = url.replace("/d/", "/e/").replace("/v/", "/e/")
                val page = get(embedUrl, referer)
                val passPath = Regex("""/pass_md5/[^'""\s<>]+""").find(page)?.value ?: return@withContext false
                val origin = URI(embedUrl)
                val passUrl = "${origin.scheme}://${origin.host}" + passPath
                val token = passPath.substringAfterLast("/")
                val mediaBase = get(passUrl, embedUrl).trim()
                if (!mediaBase.startsWith("http")) return@withContext false
                val suffix = buildString { repeat(10) { append(alphabet[random.nextInt(alphabet.length)]) } }
                val streamUrl = mediaBase + suffix + "?token=" + token
                val quality = Regex("""(?i)(2160|1440|1080|720|480|360)p""")
                    .find(Jsoup.parse(page).title())?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: Qualities.Unknown.value
                callback(ExtractorLink("DoodStream", "DoodStream", streamUrl,
                    referer = "${origin.scheme}://${origin.host}/",
                    quality = quality,
                    headers = mapOf("User-Agent" to UA),
                    type = ExtractorLinkType.VIDEO))
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("PlayCs", "DoodStream extractor failed for ${hostOf(url)}", e)
                false
            }
        }

    private fun get(url: String, referer: String? = null): String {
        val request = Request.Builder().url(url).header("User-Agent", UA)
            .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }
}

/** MixDrop embeds frequently expose their media URL in the wurl JavaScript variable. */
private object MixDropHostExtractor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS).followRedirects(true).build()
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

    suspend fun resolve(url: String, referer: String?, callback: (ExtractorLink) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val embedUrl = url.replace("/f/", "/e/")
                val request = Request.Builder().url(embedUrl).header("User-Agent", UA)
                    .apply { if (!referer.isNullOrBlank()) header("Referer", referer) }.build()
                val html = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    response.body?.string().orEmpty()
                }
                val decoded = html.replace("\\\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
                val candidate = Regex("""(?is)wurl\\s*[^=]*=\\s*["']([^"']+)["']""")
                    .find(decoded)?.groupValues?.getOrNull(1)
                    ?: Regex("""(?i)["'](https?:)?//[^"'\\s]+\\.(?:mp4|m3u8)(?:\\?[^"'\\s]*)?["']""")
                        .find(decoded)?.value?.trim('"', '\'')
                    ?: return@withContext false
                val mediaUrl = when {
                    candidate.startsWith("//") -> "https:$candidate"
                    candidate.startsWith("http") -> candidate
                    else -> return@withContext false
                }
                val type = if (mediaUrl.substringBefore("?").endsWith(".m3u8", true))
                    ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                callback(ExtractorLink("MixDrop", "MixDrop", mediaUrl,
                    referer = embedUrl, quality = Qualities.Unknown.value,
                    headers = mapOf("User-Agent" to UA), type = type))
                true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w("PlayCs", "MixDrop extractor failed for ${hostOf(url)}", e)
                false
            }
        }
}
