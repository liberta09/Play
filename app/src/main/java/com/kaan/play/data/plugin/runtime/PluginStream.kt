package com.kaan.play.data.plugin.runtime

import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamLabels
import com.kaan.play.data.stream.StreamLog
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSubtitle
import com.kaan.play.data.stream.TorrentLinks
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Eklentinin döndürdüğü tek akış (docs/nuvio-plugin-format.md §2.2). Alanların çoğu isteğe bağlı ve serbest metindir. */
data class PluginStream(
    val url: String,
    val name: String?,
    val title: String?,
    /** Serbest metin: `Auto`, `1080p`, `1080P`, `1080p • HLS`... */
    val quality: String?,
    val size: String?,
    val language: String?,
    val headers: Map<String, String>,
    val subtitles: List<PluginSubtitle>,
    /** Yalnızca `magnet:` akışlarda: torrent içindeki dosya (0 tabanlı). */
    val fileIdx: Int? = null,
    /** Eklentinin bildirdiği biçim (`m3u8`, `hls`, `mpd`, `dash`, `mp4` ...). Adreste uzantı yoksa oynatıcı türü buradan alır. */
    val format: String? = null,
) {
    companion object {
        private const val MAX_TEXT = 500
        private const val MAX_SUBTITLES = 40

        /**
         * `url` zorunlu (metin ya da `{url}` nesnesi) ve http(s) olmalı. Metin alanlarındaki **görünmez sıralama karakterleri**
         * (`U+FEFF`, `U+200B..200F`, `U+2060`; eklentiler adı bunlarla sıralatıyor) temizlenir, başlık değerlerinde satır sonu
         * barındıranlar atılır. Bozuk tek kayıt null döner ve yalnızca kendini düşürür.
         */
        fun fromJson(obj: JsonObject): PluginStream? {
            val declared = when (val u = obj["url"]) {
                is JsonPrimitive -> u.contentOrNull
                is JsonObject -> (u["url"] as? JsonPrimitive)?.contentOrNull
                else -> null
            }?.trim()
            // `url` yoksa `infoHash` varsa magnet kurulur (torrent sunucusu ayarlıysa oynar).
            val rawUrl = declared?.takeIf { it.isNotEmpty() }
                ?: TorrentLinks.magnet((obj["infoHash"] as? JsonPrimitive)?.contentOrNull)
                ?: return null
            if (rawUrl.contains("[object") || !(isHttp(rawUrl) || TorrentLinks.isMagnet(rawUrl))) {
                StreamLog.rejected("eklenti", rawUrl)
                return null
            }

            return PluginStream(
                url = rawUrl,
                name = obj.text("name"),
                title = obj.text("title"),
                quality = obj.text("quality"),
                size = obj.text("size"),
                language = obj.text("language"),
                headers = (obj["headers"] as? JsonObject).headerMap(),
                subtitles = (obj["subtitles"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                    .asSequence().mapNotNull { (it as? JsonObject)?.let(PluginSubtitle::fromJson) }
                    .take(MAX_SUBTITLES).toList(),
                fileIdx = (obj["fileIdx"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 && TorrentLinks.isMagnet(rawUrl) },
                format = obj.text("format"),
            )
        }

        internal fun isHttp(u: String) = u.startsWith("http://", ignoreCase = true) || u.startsWith("https://", ignoreCase = true)

        internal fun JsonObject.text(key: String): String? {
            val v = (this[key] as? JsonPrimitive)?.contentOrNull ?: return null
            return clean(v)
        }

        internal fun clean(value: String): String? =
            value.filter { c -> c == '\n' || (c !in INVISIBLE && c != '�' && Character.getType(c).toByte() !in UNWANTED_TYPES) }
                .trim().take(MAX_TEXT).takeIf { it.isNotEmpty() && !it.contains("[object") }

        internal fun JsonObject?.headerMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for ((k, v) in this) {
                val value = (v as? JsonPrimitive)?.contentOrNull ?: continue
                if (k.isBlank() || listOf(k, value).any { s -> s.any { it == '\r' || it == '\n' || it == '\u0000' } }) continue
                out[k] = value
            }
            return out
        }

        /** Görünmez/garip karakter sınıfları: denetim, biçim (sıfır genişlik), özel kullanım, atanmamış, satır/paragraf ayırıcı. */
        private val UNWANTED_TYPES = setOf(
            Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.UNASSIGNED,
            Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
        )

        private val INVISIBLE = ('\u200B'..'\u200F').toSet() + '\uFEFF' + '\u2060' + ('\u202A'..'\u202E').toSet()
    }
}

data class PluginSubtitle(val url: String, val language: String?, val name: String?, val headers: Map<String, String>) {
    companion object {
        fun fromJson(obj: JsonObject): PluginSubtitle? {
            val url = (obj["url"] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
            if (!PluginStream.isHttp(url)) return null
            fun s(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.let(PluginStream::clean)
            return PluginSubtitle(
                url = url,
                language = s("language") ?: s("lang"),
                name = s("name"),
                headers = with(PluginStream) { (obj["headers"] as? JsonObject).headerMap() },
            )
        }
    }
}

// ---- Bizim modele çeviri ----

/** Eklenti akışını Play'in oynatılabilir akışına çevirir: kalite/boyut/dil serbest metinden çıkarılır (mevcut kurallarla). */
fun PluginStream.toPlayable(source: StreamSource): PlayableStream {
    val text = listOfNotNull(quality, title, name).joinToString(" ")
    return PlayableStream(
        source = source,
        url = url,
        resolution = StreamLabels.resolution(text),
        sizeBytes = size?.let(StreamLabels::sizeBytes) ?: StreamLabels.sizeBytes(text),
        headers = headers,
        subtitles = subtitles.map { sub ->
            val subHeaders = buildMap {
                put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                if (headers.containsKey("Referer")) put("Referer", headers["Referer"]!!)
                if (headers.containsKey("User-Agent")) put("User-Agent", headers["User-Agent"]!!)
                putAll(headers)
                putAll(sub.headers)
            }
            StreamSubtitle(sub.url, subtitleLanguage(sub.language ?: sub.name), subHeaders)
        },
        language = language?.let { StreamLabels.language(it) } ?: StreamLabels.language(text),
        torrentFileIdx = fileIdx,
        mimeType = mimeTypeOfFormat(format),
        dubbed = com.kaan.play.data.stream.DubDetector.isDubbed(listOfNotNull(text, language).joinToString(" ")),
    )
}

/** Çalıştırma sonucunu kaynak toplama modeline çevirir: başarı (boş olabilir) ya da "yanıt vermedi". */
fun PluginOutcome.toStreamResult(source: StreamSource): StreamResult = when (this) {
    is PluginOutcome.Success -> StreamResult.Found(source, streams.map { it.toPlayable(source) })
    is PluginOutcome.Failure -> StreamResult.Failed(
        source,
        if (reason == PluginOutcome.Reason.Timeout) FailureReason.Timeout else FailureReason.InvalidResponse,
    )
}

/**
 * Eklentinin `format` alanı → medya türü. Uzantısız HLS adresleri (`.../play?id=...`) bu ipucu olmadan oynatılamaz
 * (ExoPlayer "container desteklenmiyor" 3003 verir); Nuvio da bu alana bakar. Bilinmeyen biçimde null: tür adresten/başlıktan çıkarılır.
 */
internal fun mimeTypeOfFormat(format: String?): String? = when (format?.trim()?.lowercase()) {
    "m3u8", "hls", "application/x-mpegurl", "application/vnd.apple.mpegurl" -> "application/x-mpegURL"
    "mpd", "dash", "application/dash+xml" -> "application/dash+xml"
    else -> null
}

private val LANGUAGE_CODE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,4})?$")

/** Altyazı dili: kısa kod ("tur", "en", "pt-BR") olduğu gibi; "English"/"Turkish" gibi adlar iki harfli koda çevrilir; tanınmazsa null. */
internal fun subtitleLanguage(raw: String?): String? {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty()) return null
    if (LANGUAGE_CODE.matches(v)) return v
    return StreamLabels.language(v)?.takeIf { it != "MULTI" }?.lowercase()
}
