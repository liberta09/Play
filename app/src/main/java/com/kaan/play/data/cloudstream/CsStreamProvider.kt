package com.kaan.play.data.cloudstream

import android.util.Log
import com.kaan.play.data.match.ContentIdentity
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamProvider
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * CloudStream eklentilerini [StreamProvider] arayüzüne bağlar.
 *
 * Her açık eklenti için:
 *  1. [CsRepository.loadApis] ile DEX yüklenir → [MainAPI] listesi alınır.
 *  2. [CsRuntime.streams] ile arama→detay→link akışı çalıştırılır.
 *  3. Sonuçlar tek [StreamResult.Found] olarak akar; hata → [StreamResult.Failed].
 *
 * Eklentiler paralel çalışır (her biri ayrı coroutine). İçeriğin kimliğini (başlıklar, yıl, imdb/tmdb) [identityResolver] çözer:
 * eklenti bu kimlikle aranır ve sonuç doğrulanır. Kimlik çözülemezse (TMDB'ye ulaşılamadı) eşleştirme yapılamaz: CS atlanır.
 */
class CsStreamProvider(
    private val repository: CsRepository,
    private val runtime: CsRuntime,
    private val identityResolver: suspend (request: StreamRequest) -> ContentIdentity?,
    private val timeoutMs: Long = 30_000L,
    /** Bir eklentinin arama→detay→bağlantı akışının toplam sınırı ([timeoutMs] adım başınadır). */
    private val totalBudgetMs: Long = timeoutMs,
) : StreamProvider {

    override fun streams(request: StreamRequest): Flow<StreamResult> = channelFlow {
        if (request.pluginId != null && request.dataUrl != null) {
            val plugin = repository.enabledPlugins().firstOrNull {
                "cs:${it.id}" == request.pluginId || it.pluginId == request.pluginId || it.id.toString() == request.pluginId
            }
            if (plugin != null) {
                val source = StreamSource(addonId = plugin.id, addonName = plugin.name)
                try {
                    val apis = repository.loadApis(plugin.id).apisOrEmpty()
                    val streams = mutableListOf<PlayableStream>()
                    for (api in apis) {
                        val links = runtime.loadLiveLinks(
                            api = api,
                            catalogUrl = request.dataUrl,
                            source = source,
                            timeoutMs = timeoutMs,
                        )
                        streams += links
                        if (streams.isNotEmpty()) break
                    }
                    if (streams.isEmpty()) {
                        Log.w(TAG, "${plugin.name}: canlı kanal için akış bulunamadı (${request.dataUrl})")
                        send(StreamResult.Failed(source, FailureReason.Network))
                    } else {
                        send(StreamResult.Found(source, streams))
                    }
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (t: Throwable) {
                    Log.d(TAG, "${plugin.name}: direct loadLinks error: ${t.message}")
                    send(StreamResult.Failed(source, FailureReason.Network))
                }
            }
            return@channelFlow
        }

        val identity = identityResolver(request)
        if (identity == null) {
            Log.d(TAG, "streams: içerik kimliği çözülemedi (TMDB), CS atlanıyor")
            return@channelFlow
        }
        val plugins = repository.enabledPlugins()
        Log.d(TAG, "streams: ${plugins.size} açık CS eklentisi")
        if (plugins.isEmpty()) return@channelFlow

        val gate = Semaphore(6)
        for (plugin in plugins) {
            launch {
                gate.withPermit {
                    val source = StreamSource(addonId = plugin.id, addonName = plugin.name)
                    try {
                        val streams: List<PlayableStream> = withTimeoutOrNull(totalBudgetMs) {
                            val apis = repository.loadApis(plugin.id).apisOrEmpty()
                            Log.d(TAG, "${plugin.name}: ${apis.size} MainAPI yüklendi")
                            if (apis.isEmpty()) {
                                emptyList()
                            } else {
                                runtime.streams(
                                    apis = apis,
                                    request = request,
                                    identity = identity,
                                    pluginRowId = plugin.id,
                                    pluginName = plugin.name,
                                    timeoutMs = timeoutMs,
                                )
                            }
                        } ?: emptyList()
                        if (streams.isNotEmpty()) {
                            send(StreamResult.Found(source, streams))
                        } else {
                            send(StreamResult.Failed(source, FailureReason.Network))
                        }
                    } catch (t: Throwable) {
                        Log.d(TAG, "${plugin.name}: streams hata ${t.javaClass.simpleName}: ${t.message}")
                        send(StreamResult.Failed(source, FailureReason.Network))
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO) // DEX yükleme ve eklentilerin ayrıştırma işi ana iş parçacığını dondurmasın

    override suspend fun sources(request: StreamRequest): List<StreamSource> =
        repository.enabledPlugins().map { StreamSource(addonId = it.id, addonName = it.name) }

    private companion object {
        const val TAG = "PlayCs"
    }
}
