package com.kaan.play.data.stream

import com.kaan.play.data.match.EpisodeSourceLabelRules
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Birden çok kaynak sağlayıcıyı (Faz 1: addon'lar, Faz 2: eklenti kazıyıcıları) tek sağlayıcı olarak birleştirir: aynı Flow,
 * aynı kaynak listesi. Sağlayıcılar birbirini beklemeden paralel çalışır; sonuçlar hazır oldukça akar; akış hepsi bitince biter.
 *
 * Tek çıkış kapısı olduğu için içerik güvencesi de burada verilir: çıkan her kaynak istenen içeriğin anahtarıyla damgalanır
 * ([StreamOwnership]); başka bir içeriğin damgasını taşıyanlar ve oynatma sırasında "bu içerik değil" diye reddedilenler
 * ([StreamVerdicts]) listeye girmez.
 */
class CompositeStreamProvider(
    private val providers: List<StreamProvider>,
    private val verdicts: StreamVerdicts? = null,
    private val isBlocked: (StreamRequest, StreamSource) -> Boolean = { _, _ -> false },
) : StreamProvider {

    constructor(vararg providers: StreamProvider) : this(providers.toList())

    override fun streams(request: StreamRequest): Flow<StreamResult> = channelFlow {
        val targetProvider = when {
            request.pluginId?.startsWith("cs:") == true -> providers.filterIsInstance<com.kaan.play.data.cloudstream.CsStreamProvider>()
            request.pluginId?.startsWith("nuvio_addon:") == true -> providers.filterIsInstance<AddonStreamProvider>()
            else -> providers
        }
        val key = StreamOwnership.keyOf(request)
        for (provider in targetProvider) {
            launch { provider.streams(request).collect { send(it.forContent(request, key)) } }
        }
    }

    private fun StreamResult.forContent(request: StreamRequest, key: String): StreamResult = when (this) {
        is StreamResult.Found -> StreamResult.Found(
            source,
            if (isBlocked(request, source)) emptyList()
            else streams
                .filter { stream ->
                    StreamOwnership.belongs(stream, request) &&
                        verdicts?.isRejected(key, stream.url) != true &&
                        SourceMediaRules.isAcceptable(stream, request.type) &&
                        passesLabelGuard(stream, request)
                }
                .map { it.copy(forKey = key) },
        )
        is StreamResult.Failed -> this
    }

    private fun passesLabelGuard(stream: PlayableStream, request: StreamRequest): Boolean {
        val decision = EpisodeSourceLabelRules.evaluate(stream.label, request)
        if (!decision.accept) {
            runCatching {
                android.util.Log.d(
                    "PlayMatch",
                    "Etiket elemesi (${stream.source.addonName}): ${decision.reason.orEmpty()}${if (!stream.label.isNullOrBlank()) " [etiket=${stream.label}]" else ""}",
                )
            }
        }
        return decision.accept
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> = coroutineScope {
        val targetProvider = when {
            request.pluginId?.startsWith("cs:") == true -> providers.filterIsInstance<com.kaan.play.data.cloudstream.CsStreamProvider>()
            request.pluginId?.startsWith("nuvio_addon:") == true -> providers.filterIsInstance<AddonStreamProvider>()
            else -> providers
        }
        targetProvider.map { async { it.sources(request) } }.awaitAll().flatten()
    }

}
