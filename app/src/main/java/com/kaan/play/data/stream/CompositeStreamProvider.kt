package com.kaan.play.data.stream

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val dispatcher: CoroutineDispatcher? = null,
) : StreamProvider {

    constructor(vararg providers: StreamProvider) : this(providers.toList())

    override fun streams(request: StreamRequest): Flow<StreamResult> {
        val flow = channelFlow {
            val targetProvider = when {
                request.pluginId?.startsWith("cs:") == true -> providers.filterIsInstance<com.kaan.play.data.cloudstream.CsStreamProvider>()
                request.pluginId?.startsWith("nuvio_addon:") == true -> providers.filterIsInstance<AddonStreamProvider>()
                else -> providers
            }
            val key = StreamOwnership.keyOf(request)
            for (provider in targetProvider) {
                val block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit = {
                    try {
                        provider.streams(request).collect { send(it.forContent(request, key)) }
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        android.util.Log.e("CompositeStream", "Provider ${provider::class.simpleName} error: ${t.message}", t)
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

    private fun StreamResult.forContent(request: StreamRequest, key: String): StreamResult = when (this) {
        is StreamResult.Found -> StreamResult.Found(
            source,
            streams
                .filter { StreamOwnership.belongs(it, request) && verdicts?.isRejected(key, it.url) != true && SourceMediaRules.isAcceptable(it, request.type) }
                .map { it.copy(forKey = key) },
        )
        is StreamResult.Failed -> this
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> {
        val block: suspend CoroutineScope.() -> List<StreamSource> = {
            val targetProvider = when {
                request.pluginId?.startsWith("cs:") == true -> providers.filterIsInstance<com.kaan.play.data.cloudstream.CsStreamProvider>()
                request.pluginId?.startsWith("nuvio_addon:") == true -> providers.filterIsInstance<AddonStreamProvider>()
                else -> providers
            }
            targetProvider.map { async { it.sources(request) } }.awaitAll().flatten()
        }
        return if (dispatcher != null) withContext(dispatcher, block) else coroutineScope(block)
    }

}
