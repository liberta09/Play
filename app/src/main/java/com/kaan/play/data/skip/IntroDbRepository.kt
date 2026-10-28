package com.kaan.play.data.skip

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.net.await
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

/** TheIntroDB'den gelen zaman aralığı türü; [markerTitle] bölüm işareti başlığı olarak SkipSegmentDetector'a verilir. */
enum class SkipTimeType(val markerTitle: String) {
    Intro("intro"),
    Recap("recap"),
    Credits("end credits"),
    Preview("preview"),
}

/** Video zaman çizelgesinde ms; [openEndedEnd]: kaynak `end_ms: null` verdi (medyanın sonu). */
data class SkipTimestamp(
    val type: SkipTimeType,
    val startMs: Long,
    val endMs: Long,
    val openEndedEnd: Boolean = false,
)

/** Sağlayıcıdan bağımsız atlama zamanları sözleşmesi; sonuçlar video zaman çizelgesinde ms'dir. */
fun interface SkipSegmentProvider {
    suspend fun timestamps(request: StreamRequest, durationMs: Long): List<SkipTimestamp>
}

/** İleride başka sağlayıcılar eklenirse sonuçları tek listede toplar; bir sağlayıcının hatası diğerlerini etkilemez. */
class CombinedSkipSegmentProvider(
    private val providers: List<SkipSegmentProvider>,
) : SkipSegmentProvider {
    override suspend fun timestamps(request: StreamRequest, durationMs: Long): List<SkipTimestamp> {
        val results = ArrayList<SkipTimestamp>()
        for (provider in providers) {
            try {
                results += provider.timestamps(request, durationMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Bir sağlayıcının hatası diğer sağlayıcıları veya oynatmayı etkilemez.
            }
        }
        return results.distinctBy { "${it.type}:${it.startMs}:${it.endMs}" }
            .sortedWith(compareBy<SkipTimestamp> { it.startMs }.thenBy { it.type.ordinal })
    }
}

/**
 * Salt-okunur TheIntroDB API v4 istemcisi (`GET {endpoint}?tmdb_id=…|imdb_id=…&duration_ms=…[&season=&episode=]`).
 *
 * - **Gönderilen:** yalnızca TMDB kimliği (yoksa geçerli IMDb `tt…`), dizide sezon/bölüm ve bilinen video süresi. Akış adresi,
 *   başlıklar, çerez, profil, hesap ya da API anahtarı **gönderilmez**. Adres/kimlik loglanmaz.
 * - Canlı TV (`addonType` tv/channel), süresi bilinmeyen ve kimliksiz içerikte istek atılmaz.
 * - Çağrı oynatmayı bekletmez; toplam süre 7 sn. 404/204 boş sonuç olarak önbelleğe girer; ağ/HTTP/ayrıştırma hataları
 *   sessizce boş liste döner (ağ/HTTP hatası önbelleğe girmez). Önbellek bellekte, (tür, kimlik, sezon, bölüm, süre) başına, en çok 128 kayıt.
 * - [endpoint] her çağrıda okunur: debug derlemede sahte sunucuya yönlendirilebilir (`DebugEndpoints.introDbBase`).
 */
class IntroDbRepository(
    private val client: OkHttpClient,
    private val json: Json = PlayJson,
    private val endpoint: () -> String = { DEFAULT_ENDPOINT },
) : SkipSegmentProvider {
    constructor(client: OkHttpClient, json: Json = PlayJson, endpoint: String) : this(client, json, { endpoint })

    private val cache = object : LinkedHashMap<String, List<SkipTimestamp>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipTimestamp>>?): Boolean = size > CACHE_ENTRIES
    }

    override suspend fun timestamps(request: StreamRequest, durationMs: Long): List<SkipTimestamp> {
        if (durationMs <= 0L || request.addonType?.lowercase() in setOf("tv", "channel")) return emptyList()
        if (request.type == MediaType.Tv && (request.season == null || request.episode == null)) return emptyList()
        val tmdbId = request.tmdbId.takeIf { it > 0 }
        val imdbId = request.imdbId?.trim()?.takeIf { IMDB_ID.matches(it) }
        if (tmdbId == null && imdbId == null) return emptyList()
        val base = endpoint().toHttpUrlOrNull() ?: return emptyList()

        val identityKey = tmdbId?.let { "tmdb:$it" } ?: "imdb:$imdbId"
        val key = listOf(request.type.name, identityKey, request.season, request.episode, durationMs).joinToString(":")
        synchronized(cache) { cache[key]?.let { return it } }

        val url = base.newBuilder()
            .apply {
                if (tmdbId != null) addQueryParameter("tmdb_id", tmdbId.toString())
                else addQueryParameter("imdb_id", imdbId)
                addQueryParameter("duration_ms", durationMs.toString())
                if (request.type == MediaType.Tv) {
                    addQueryParameter("season", request.season.toString())
                    addQueryParameter("episode", request.episode.toString())
                }
            }
            .build()
        val call = client.newCall(Request.Builder().url(url).get().build())
        call.timeout().timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return try {
            call.await().use { response ->
                if (response.code == 404 || response.code == 204) {
                    synchronized(cache) { cache[key] = emptyList() }
                    return emptyList()
                }
                if (!response.isSuccessful) return emptyList()
                val body = response.body.string()
                val parsed = parse(body, durationMs)
                synchronized(cache) { cache[key] = parsed }
                parsed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Ağ hatası, zaman aşımı, PlayNetworkGuard engeli: sessizce boş; oynatma etkilenmez.
            emptyList()
        }
    }

    /** Bozuk gövde: boş liste. */
    private fun parse(body: String, durationMs: Long): List<SkipTimestamp> = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        buildList {
            addAll(root.readSegments("intro", SkipTimeType.Intro, durationMs))
            addAll(root.readSegments("recap", SkipTimeType.Recap, durationMs))
            addAll(root.readSegments("credits", SkipTimeType.Credits, durationMs))
            addAll(root.readSegments("preview", SkipTimeType.Preview, durationMs))
        }.distinctBy { "${it.type}:${it.startMs}:${it.endMs}" }
            .sortedWith(compareBy<SkipTimestamp> { it.startMs }.thenBy { it.type.ordinal })
    }.getOrDefault(emptyList())

    private fun JsonObject.readSegments(key: String, type: SkipTimeType, durationMs: Long): List<SkipTimestamp> {
        val records = runCatching { this[key]?.jsonArray }.getOrNull() ?: return emptyList()
        return records.mapNotNull { record ->
            runCatching {
                val fields = record.jsonObject
                // v4: null başlangıç medyanın başı, null bitiş medyanın sonudur (her türde).
                val startMs = fields["start_ms"]?.jsonPrimitive?.longOrNull ?: 0L
                val rawEnd = fields["end_ms"]?.jsonPrimitive?.longOrNull
                val endMs = rawEnd ?: durationMs
                if (startMs < 0L || endMs <= startMs || endMs > durationMs || endMs - startMs < MIN_SEGMENT_MS) {
                    return@mapNotNull null
                }
                SkipTimestamp(type, startMs, endMs, openEndedEnd = rawEnd == null)
            }.getOrNull()
        }
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.theintrodb.org/v4/media"
        private const val CACHE_ENTRIES = 128
        private const val MIN_SEGMENT_MS = 3_000L
        private const val REQUEST_TIMEOUT_SECONDS = 7L
        private val IMDB_ID = Regex("tt[0-9]+", RegexOption.IGNORE_CASE)
    }
}
