package com.kaan.play.data.plugin

import android.util.Log
import com.kaan.play.data.match.ContentKind
import com.kaan.play.data.match.PluginCallRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.plugin.runtime.PluginOutcome
import com.kaan.play.data.plugin.runtime.PluginRequest
import com.kaan.play.data.plugin.runtime.PluginStream
import com.kaan.play.data.plugin.runtime.toPlayable
import com.kaan.play.data.plugin.runtime.toStreamResult
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamLog
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.StreamSessionCookies
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration

/** Bir kazıyıcıyı çalıştıran şey ([com.kaan.play.data.plugin.runtime.PluginRuntime]); testlerde sahte uygulanır. */
fun interface PluginRunner {
    suspend fun run(code: String, request: PluginRequest, scraperId: String, timeoutMs: Long, settings: Map<String, JsonElement>): PluginOutcome
}

/**
 * Açık eklentileri (JS kazıyıcılar) kaynak toplama arayüzüne takar. [com.kaan.play.data.stream.AddonStreamProvider] ile aynı
 * sözleşme: istenen türü destekleyen **açık** eklentiler paralel çalışır (en çok [maxParallel] aynı anda; sıradakiler bekler),
 * her biri kendi zaman aşımıyla ([timeout]: mevcut kaynak zaman aşımı ayarı), sonuçlar hazır oldukça akar; biri takılınca ya da
 * hata verince diğerleri beklemez ("yanıt vermedi" bilgisi [StreamResult.Failed] olarak gelir). Her çalıştırma kendi QuickJS
 * örneğini kurup kapatır. Parametreler (tmdbId metin, tür, sezon/bölüm) Detay'dan gelen [StreamRequest]'ten türetilir.
 *
 * Kaynak kimliği: eklentinin satır kimliğinin **eksi**si (addon kimlikleriyle çakışmasın).
 *
 * @param plugins açık eklentiler (istek anında sorgulanır).
 * @param code eklentinin saklı kodu (bütünlük denetimli); yoksa o eklenti "yanıt vermedi" sayılır.
 */
class PluginStreamProvider(
    private val plugins: suspend () -> List<InstalledPlugin>,
    private val code: suspend (Long) -> String?,
    private val runner: PluginRunner,
    private val settings: suspend (Long) -> Map<String, JsonElement>,
    private val timeout: suspend () -> Duration,
    private val cache: PluginResultCache = PluginResultCache(),
    private val maxParallel: Int = MAX_PARALLEL,
    private val dispatcher: CoroutineDispatcher? = null,
) : StreamProvider {

    override fun streams(request: StreamRequest): Flow<StreamResult> {
        val flow = channelFlow {
            val chosen = askable(request)
            if (chosen.isEmpty()) return@channelFlow
            val limit = timeout()
            val gate = Semaphore(maxParallel)
            for (plugin in chosen) {
                val block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit = {
                    gate.withPermit {
                        try {
                            send(query(plugin, request, limit))
                        } catch (t: Throwable) {
                            Log.e(TAG, "${plugin.name} eklenti hatası: ${t.message}", t)
                            send(StreamResult.Failed(plugin.toSource(), FailureReason.Network))
                        }
                    }
                }
                if (dispatcher != null) {
                    launch(dispatcher, block = block)
                } else {
                    launch(block = block)
                }
            }
        }
        return if (dispatcher != null) flow.flowOn(dispatcher) else flow
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> =
        askable(request).map { it.toSource() }

    /** Açık ve istenen türü (film/dizi) destekleyen eklentiler. IMDb kimliği gerekmez: eklentiler TMDB kimliğiyle çalışır. */
    private suspend fun askable(request: StreamRequest): List<InstalledPlugin> {
        val wanted = if (request.type == MediaType.Movie) "movie" else "tv"
        return plugins().filter { it.enabled && wanted in it.supportedTypes }
    }

    private suspend fun query(plugin: InstalledPlugin, request: StreamRequest, limit: Duration): StreamResult {
        val source = plugin.toSource()
        val saved = settings(plugin.id)
        val isTv = request.type == MediaType.Tv
        val pluginRequest = PluginRequest(
            tmdbId = request.tmdbId.toString(),
            mediaType = pluginMediaType(request.type, plugin.rawTypes, request.season, request.episode),
            season = request.season.takeIf { isTv },
            episode = request.episode.takeIf { isTv },
        )
        Log.d(TAG, "${plugin.name}: istek tmdbId=${pluginRequest.tmdbId} tür=${pluginRequest.mediaType} sezon=${pluginRequest.season} bölüm=${pluginRequest.episode}")
        val problem = PluginCallRules.problem(
            pluginRequest.tmdbId, pluginRequest.mediaType, pluginRequest.season, pluginRequest.episode,
            if (isTv) ContentKind.Tv else ContentKind.Movie,
        )
        if (problem != null) {
            // Tutarsız kimlik/tür: eklentiye gönderilmez, yanlış içerik getirmesin.
            Log.d(TAG, "${plugin.name}: çağrı reddedildi: $problem")
            return found(source, emptyList())
        }
        val key = PluginResultCache.Key(plugin.id, plugin.version, pluginRequest, saved.hashCode())
        if (!request.refresh) {
            cache.get(key)?.let { cached -> return found(source, cached) }
        }

        val script = code(plugin.id) ?: return StreamResult.Failed(source, FailureReason.InvalidResponse)
        val outcome = runner.run(script, pluginRequest, plugin.pluginId, limit.inWholeMilliseconds, saved)
        if (outcome is PluginOutcome.Success) {
            outcome.streams.forEach { stream ->
                StreamSessionCookies.remember(source.addonId, stream.url, outcome.sessionCookies)
            }
        }
        if (outcome is PluginOutcome.Success) {
            // Tanı: hangi akış geldi (adres yok; ana makine + yol biçimi + parametre özetleri).
            outcome.streams.take(3).forEach { Log.d(TAG, "${plugin.name}: akış ${StreamLog.fingerprint(it.url)}") }
        }
        // Yalnızca dolu başarılı sonuçlar saklanır: boş/hatalı sonuç (geçici site sorunu) yeniden denenebilsin.
        if (outcome is PluginOutcome.Success && outcome.streams.isNotEmpty()) cache.put(key, outcome.streams)
        return outcome.toStreamResult(source)
    }

    private fun found(source: StreamSource, streams: List<PluginStream>) =
        StreamResult.Found(source, streams.map { it.toPlayable(source) })

    private fun InstalledPlugin.toSource() = StreamSource(-id, name)

    companion object {
        private const val TAG = "PlayPlugin"

        /** Aynı anda en çok çalışan eklenti sayısı (bellek/işlemci koruması: her biri ayrı bir QuickJS örneğidir). */
        const val MAX_PARALLEL = 6

        /**
         * Eklentiye verilecek tür: `movie`/`tv`; manifest `series` bildiriyorsa ve sezon+bölüm varsa `series`
         * (belge §2.1; eklentiler `tv` ve `series` ikisini de karşılamalı).
         */
        fun pluginMediaType(type: MediaType, rawTypes: List<String>, season: Int?, episode: Int?): String = when (type) {
            MediaType.Movie -> "movie"
            MediaType.Tv -> if (season != null && episode != null && rawTypes.any { it.equals("series", ignoreCase = true) }) "series" else "tv"
        }
    }
}
