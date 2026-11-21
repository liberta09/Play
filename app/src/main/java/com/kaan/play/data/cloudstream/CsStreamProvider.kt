package com.kaan.play.data.cloudstream

import android.util.Log
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    /** Eş zamanlılık, eklenti bırakma ve doğrulama tarayıcısı kuralları (az bellekli cihazda daha sıkı; [CsSearchBudget]). */
    private val budget: CsSearchBudget = CsSearchBudget.Default,
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
        // Bu yol yalnız film/dizi kaynağı arar (canlı kanal yukarıda doğrudan açılır): yalnız canlı yayın veren eklentiler sorulmaz.
        val enabled = repository.enabledPlugins()
        val plugins = enabled.filterNot { p -> budget.skipPlugins.any { it.equals(p.name, ignoreCase = true) } || isLivePlugin(p) }
        Log.d(TAG, "streams: ${plugins.size} açık CS eklentisi (${enabled.count { isLivePlugin(it) }} yalnız canlı yayın eklentisi atlandı)")
        if (plugins.isEmpty()) return@channelFlow

        Log.d(TAG, "streams: bütçe $budget")
        CloudflareSolver.sourceSearchStarted(skipSolve = budget.skipChallengeSolve)
        val gate = Semaphore(budget.maxParallel)
        // Sayaç arama bitince (ya da kesilince) düşer: doğrulama tarayıcısı yeniden açılabilir.
        try { coroutineScope { for (plugin in plugins) {
            launch {
                gate.withPermit {
                    awaitHeapHeadroom()
                    val source = StreamSource(addonId = plugin.id, addonName = plugin.name)
                    try {
                        val streams: List<PlayableStream> = withTimeoutOrNull(totalBudgetMs) {
                            // Az bellekli cihazda yalnız bu arama için yüklenen eklenti iş bitince bırakılır (kaynak bulduysa kalır).
                            repository.useApis(plugin.id, release = budget.releaseAfterSearch, keep = { it.isNotEmpty() }) { loaded ->
                                val apis = loaded.apisOrEmpty()
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
        } } } finally {
            CloudflareSolver.sourceSearchFinished(skipSolve = budget.skipChallengeSolve)
        }
    }.flowOn(Dispatchers.IO) // DEX yükleme ve eklentilerin ayrıştırma işi ana iş parçacığını dondurmasın

    /**
     * Az bellekli cihazda Java belleği sıkışıkken ([CsSearchBudget.heapTight]) sıradaki eklenti hemen başlamaz: bir kez çöp
     * toplanır, bellek düşene kadar en çok [CsSearchBudget.HEAP_WAIT_MS] beklenir. Telefonda (sınır yok) hiç beklenmez.
     */
    private suspend fun awaitHeapHeadroom() {
        if (budget.heapStartFraction == null) return
        val rt = Runtime.getRuntime()
        fun used() = rt.totalMemory() - rt.freeMemory()
        if (!budget.heapTight(used(), rt.maxMemory())) return
        Log.d(TAG, "streams: Java belleği ${used() shr 20}/${rt.maxMemory() shr 20} MB, sıradaki eklenti bekliyor")
        System.gc()
        val until = System.currentTimeMillis() + CsSearchBudget.HEAP_WAIT_MS
        while (budget.heapTight(used(), rt.maxMemory()) && System.currentTimeMillis() < until) delay(250)
    }

    override suspend fun sources(request: StreamRequest): List<StreamSource> =
        repository.enabledPlugins().map { StreamSource(addonId = it.id, addonName = it.name) }

    private companion object {
        const val TAG = "PlayCs"
    }
}

/** Film/dizi döndürebilen CloudStream türleri (`tvTypes`; harf dışı karakterler atılmış, küçük harf). */
private val ON_DEMAND_TYPES = setOf(
    "movie", "animemovie", "tvseries", "series", "tv", "cartoon", "anime", "ova", "torrent", "documentary", "asiandrama", "others",
)

/**
 * Eklenti yalnız canlı yayın mı veriyor: `tvTypes` içinde "Live" / "Live Stream" var, film/dizi türü yok. Film ya da dizi kaynağı
 * aranırken bu eklentiler sorulmaz; film/dizi döndüremezler ama az bellekli cihazlarda derin aramada belleği en çok
 * şişirenler arasındaydı. Tür listesi boşsa eklenti atlanmaz.
 *
 * True when a plugin's `tvTypes` only offer live streams; such plugins are skipped when searching movie/series sources.
 */
internal fun isLiveOnly(tvTypes: List<String>): Boolean {
    val kinds = tvTypes.map { t -> t.filter(Char::isLetter).lowercase() }.filter { it.isNotEmpty() }
    return kinds.any { it.startsWith("live") } && kinds.none { it in ON_DEMAND_TYPES }
}

private fun isLivePlugin(plugin: InstalledCsPlugin): Boolean = isLiveOnly(plugin.tvTypes)
