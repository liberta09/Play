package com.kaan.play.data.stream

import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Cevap `{"streams":[...]}` biçiminde değil (ya da JSON değil). */
class InvalidStreamResponse : Exception()

/**
 * Addon'un `stream/...json` cevabını [PlayableStream] listesine çevirir.
 * `url` alanı http(s) olan akışlar alınır; `magnet:` adresi ya da `infoHash` olan akışlar magnet'e çevrilip alınır
 * (yerleşik torrent motoru ya da kullanıcının torrent sunucusu oynatır, bkz. [TorrentStreamProvider]). `ytId`, `externalUrl` yok sayılır.
 * Tek bir bozuk akış kaydı yalnızca kendini düşürür, cevabın geri kalanı kullanılır.
 */
object StreamParser {

    fun parse(source: StreamSource, body: String): List<PlayableStream> {
        val root = try {
            PlayJson.parseToJsonElement(body)
        } catch (_: IllegalArgumentException) { // SerializationException de buradan geçer
            throw InvalidStreamResponse()
        }
        val array = (root as? JsonObject)?.get("streams") as? JsonArray ?: throw InvalidStreamResponse()

        return array.mapNotNull { element ->
            val dto = try {
                PlayJson.decodeFromJsonElement(StreamDto.serializer(), element)
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            }
            dto.toPlayable(source)
        }
    }

    private fun StreamDto.toPlayable(source: StreamSource): PlayableStream? {
        val httpLink = url?.trim()?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        val trackers = sources.orEmpty().filter { it.startsWith("tracker:", ignoreCase = true) }.map { it.substringAfter(':') }
        val rawMagnet = url?.trim()?.takeIf(TorrentLinks::isMagnet)
        val link = httpLink
            ?: (if (rawMagnet != null) TorrentLinks.appendTrackers(rawMagnet, trackers) else TorrentLinks.magnet(infoHash, trackers))
            ?: run {
                url?.let { StreamLog.rejected("addon", it) }
                return null
            }
        val text = listOfNotNull(name, title, description, behaviorHints?.filename).joinToString(" ")
        val proxyHeaders = behaviorHints?.proxyHeaders?.request.orEmpty().filter { (k, v) -> isSafeHeader(k, v) }
        return PlayableStream(
            source = source,
            url = link,
            resolution = StreamLabels.resolution(text),
            sizeBytes = behaviorHints?.videoSize?.takeIf { it > 0 }?.toLong() ?: StreamLabels.sizeBytes(text),
            headers = proxyHeaders,
            subtitles = subtitles.orEmpty().mapNotNull { s ->
                val subUrl = s.url?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
                    ?: return@mapNotNull null
                val subHeaders = buildMap {
                    put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    putAll(proxyHeaders)
                }
                StreamSubtitle(subUrl, s.lang?.takeIf { it.isNotBlank() }, subHeaders)
            },
            language = StreamLabels.language(text),
            torrentFileIdx = fileIdx?.takeIf { TorrentLinks.isMagnet(link) && it >= 0 },
            torrentFilename = behaviorHints?.filename?.trim()?.takeIf { it.isNotEmpty() },
            dubbed = DubDetector.isDubbed(text),
        )
    }

    /** Üstbilgi adı/değeri satır sonu içeremez (başlık enjeksiyonu). */
    private fun isSafeHeader(name: String, value: String): Boolean =
        name.isNotBlank() && listOf(name, value).none { s -> s.any { it == '\r' || it == '\n' || it == '\u0000' } }
}

/** Akış adı/başlığı/açıklamasındaki serbest metinden kalite ve boyut çıkarır. */
object StreamLabels {

    private val RESOLUTION = Regex("""\b(2160p|4k|uhd|1440p|1080p|1080i|720p|576p|480p|360p)\b""", RegexOption.IGNORE_CASE)
    private val SIZE = Regex("""\b(\d+(?:[.,]\d+)?)\s?(TB|GB|MB|TiB|GiB|MiB)\b""", RegexOption.IGNORE_CASE)

    fun resolution(text: String): Int? {
        val token = RESOLUTION.find(text)?.groupValues?.get(1)?.lowercase() ?: return null
        return when (token) {
            "2160p", "4k", "uhd" -> 2160
            "1440p" -> 1440
            "1080p", "1080i" -> 1080
            "720p" -> 720
            "576p", "480p" -> 480
            else -> 360
        }
    }

    /** Dil ipuçları: sözcük sınırlı ad/kod ve bayrak emojisi (bölge göstergesi harf çiftleri). */
    private val LANGUAGES: List<Pair<String, Regex>> = listOf(
        "EN" to hint("english|eng", "🇬🇧", "🇺🇸"),
        "TR" to hint("turkish|türkçe|turkce|tur", "🇹🇷"),
        "ES" to hint("spanish|español|castellano|latino|spa", "🇪🇸"),
        "FR" to hint("french|français|francais|fra", "🇫🇷"),
        "DE" to hint("german|deutsch|deu", "🇩🇪"),
        "IT" to hint("italian|italiano", "🇮🇹"),
        "PT" to hint("portuguese|português|portugues", "🇵🇹", "🇧🇷"),
        "RU" to hint("russian|rus", "🇷🇺"),
        "JA" to hint("japanese|jpn", "🇯🇵"),
        "KO" to hint("korean|kor", "🇰🇷"),
        "HI" to hint("hindi", "🇮🇳"),
        "AR" to hint("arabic"),
        "MULTI" to hint("multi|dual"),
    )

    private fun hint(words: String, vararg flags: String): Regex {
        val alternatives = buildList {
            add("""\b($words)\b""")
            flags.forEach { add(Regex.escape(it)) }
        }
        return Regex(alternatives.joinToString("|"), RegexOption.IGNORE_CASE)
    }

    /** Köşeli/parantezli/dikey çizgili iki harfli kod: "[EN]", "(TR)", "|DE|". */
    private val BRACKETED = Regex("""[\[(|]\s*(en|tr|es|fr|de|it|pt|ru|ja|ko|hi|ar)\s*[\])|]""", RegexOption.IGNORE_CASE)

    /**
     * Akış metnindeki ses dili ipucu (ilk geçen): "EN", "TR" ... ya da "MULTI". Serbest metinden tahmindir;
     * bulunamazsa null.
     */
    fun language(text: String): String? {
        val hits = buildList {
            LANGUAGES.forEach { (code, regex) -> regex.find(text)?.let { add(it.range.first to code) } }
            BRACKETED.find(text)?.let { add(it.range.first to it.groupValues[1].uppercase()) }
        }
        return hits.minByOrNull { it.first }?.second
    }

    fun sizeBytes(text: String): Long? {
        val match = SIZE.find(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit: Double = when (match.groupValues[2].lowercase()) {
            "tb" -> 1e12
            "gb" -> 1e9
            "mb" -> 1e6
            "tib" -> 1024.0 * 1024 * 1024 * 1024
            "gib" -> 1024.0 * 1024 * 1024
            else -> 1024.0 * 1024
        }
        return Math.round(number * unit).takeIf { it > 0 }
    }
}

@Serializable
private data class StreamDto(
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val sources: List<String>? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val subtitles: List<SubtitleDto>? = null,
    val behaviorHints: HintsDto? = null,
)

@Serializable
private data class SubtitleDto(val id: String? = null, val url: String? = null, val lang: String? = null)

@Serializable
private data class HintsDto(
    val videoSize: Double? = null,
    val filename: String? = null,
    val proxyHeaders: ProxyHeadersDto? = null,
)

@Serializable
private data class ProxyHeadersDto(val request: Map<String, String>? = null)
