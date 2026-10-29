package com.kaan.play.data.skip

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.net.await
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit

/** TheIntroDB’den gelen zaman aralığı kategorisi. */
enum class SkipTimeType(val markerTitle: String) {
    Intro("intro"),
    Recap("recap"),
    Credits("end credits"),
    Preview("preview"),
}

/** Sağlayıcının açık uçlu bitiş bilgisini arayüzde/oynatma kararında kullanabilmek için saklar. */
data class SkipTimestamp(
    val type: SkipTimeType,
    val startMs: Long,
    val endMs: Long,
    val openEndedEnd: Boolean = false,
)

/** Sağlayıcıdan bağımsız atlama zamanları sözleşmesi; sonuçlar video zaman çizelgesinde normalize edilmiş ms’dir. */
fun interface SkipSegmentProvider {
    suspend fun timestamps(request: StreamRequest, durationMs: Long): List<SkipTimestamp>
}

/** İleride başka sağlayıcılar eklenirse sonuçları tek bir birleştirilebilir sözleşmede toplar. */
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
 * Read-only TheIntroDB API v4 istemcisi. Stream URL’si/başlıkları, hesap veya profil verisi gönderilmez.
 * Çağrı oynatmayı bekletmez; her HTTP isteğinin toplam süresi 7 saniyeyle sınırlıdır.
 */
class IntroDbRepository(
    private val client: OkHttpClient,
    private val json: Json = PlayJson,
    endpoint: String = DEFAULT_ENDPOINT,
) : SkipSegmentProvider {
    private val endpoint: HttpUrl = endpoint.toHttpUrl()
    private val cache = object : LinkedHashMap<String, List<SkipTimestamp>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SkipTimestamp>>?): Boolean = size > CACHE_ENTRIES
    }

    override suspend fun timestamps(request: StreamRequest, durationMs: Long): List<SkipTimestamp> {
        if (durationMs <= 0L || request.addonType?.lowercase() in setOf("tv", "channel")) return emptyList()
        if (request.type == MediaType.Tv && (request.season == null || request.episode == null)) return emptyList()
        val tmdbId = request.tmdbId.takeIf { it > 0 }
        val imdbId = request.imdbId?.trim()?.takeIf { IMDB_ID.matches(it) }
        if (tmdbId == null && imdbId == null) return emptyList()

        val identityKey = tmdbId?.let { "tmdb:$it" } ?: "imdb:$imdbId"
        val key = listOf(request.type.name, identityKey, request.season, request.episode, durationMs).joinToString(":")
        synchronized(cache) { cache[key]?.let { return it } }

        val url = endpoint.newBuilder()
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
        try {
            call.await().use { response ->
                if (response.code == 404 || response.code == 204) {
                    synchronized(cache) { cache[key] = emptyList() }
                    return emptyList()
                }
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val parsed = parse(body, durationMs)
                synchronized(cache) { cache[key] = parsed }
                return parsed
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }
    }

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

    private fun kotlinx.serialization.json.JsonObject.readSegments(
        key: String,
        type: SkipTimeType,
        durationMs: Long,
    ): List<SkipTimestamp> {
        val records = runCatching { this[key]?.jsonArray }.getOrNull() ?: return emptyList()
        return records.mapNotNull { record ->
            runCatching {
                val fields = record.jsonObject
                // v4 null start means media start; null end means media end for every segment kind.
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

    private companion object {
        const val DEFAULT_ENDPOINT = "https://api.theintrodb.org/v4/media"
        const val CACHE_ENTRIES = 128
        const val MIN_SEGMENT_MS = 3_000L
        const val REQUEST_TIMEOUT_SECONDS = 7L
        val IMDB_ID = Regex("tt[0-9]+", RegexOption.IGNORE_CASE)
    }
}
