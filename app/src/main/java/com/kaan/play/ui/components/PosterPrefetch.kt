package com.kaan.play.ui.components

import android.content.Context
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Precision
import coil3.size.Scale
import com.kaan.play.BuildConfig
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Yalnızca DEBUG derlemede intent bayraklarıyla denetlenen prefetch anahtarları.
 * Release derlemede BuildConfig.DEBUG sabit 'false' olduğu için derleme aşamasında yok sayılır.
 */
object PosterPrefetchFlags {
    var noPrefetch: Boolean = false

    val isPrefetchEnabled: Boolean
        get() = !BuildConfig.DEBUG || !noPrefetch
}

/**
 * Prefetch ve AsyncImage bellek önbellek isabet (memory cache hit) oranını ölçen DEBUG sayacı.
 */
object PosterPrefetchMetrics {
    val totalLoads = AtomicInteger(0)
    val memoryCacheHits = AtomicInteger(0)
    val diskCacheHits = AtomicInteger(0)
    val networkHits = AtomicInteger(0)
    val prefetchesTriggered = AtomicInteger(0)
    val prefetchesSuccess = AtomicInteger(0)

    fun reset() {
        totalLoads.set(0)
        memoryCacheHits.set(0)
        diskCacheHits.set(0)
        networkHits.set(0)
        prefetchesTriggered.set(0)
        prefetchesSuccess.set(0)
    }

    val hitRate: Double
        get() {
            val total = totalLoads.get()
            return if (total > 0) (memoryCacheHits.get().toDouble() / total) * 100.0 else 0.0
        }

    fun dump(): String =
        "total=${totalLoads.get()},memHits=${memoryCacheHits.get()},diskHits=${diskCacheHits.get()},netHits=${networkHits.get()},hitRate=${"%.2f".format(hitRate)},prefetchesTriggered=${prefetchesTriggered.get()},prefetchesSuccess=${prefetchesSuccess.get()}"
}

/**
 * Prefetch istekleri ile PosterCard içindeki AsyncImage isteklerinin
 * BİREBİR AYNI bellek anahtarı ve boyut parametrelerini paylaşmasını sağlayan fabrika.
 */
object PosterPrefetch {

    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefetchSemaphore = Semaphore(PosterPrefetchRules.MAX_CONCURRENT_PREFETCH)

    /**
     * Dp cinsinden kart genişliğini ve 2:3 en-boy oranını piksel cinsine dönüştürür.
     * Hem prefetch hem de PosterCard bu fonksiyonu kullanarak birebir aynı boyutları elde eder.
     */
    fun calculateCardPixelSize(cardWidth: Dp, density: Density): Pair<Int, Int> {
        val widthPx = with(density) { cardWidth.roundToPx() }
        val heightPx = with(density) { (cardWidth * 1.5f).roundToPx() }
        return Pair(widthPx, heightPx)
    }

    /**
     * Hem AsyncImage hem de prefetch tarafından kullanılan ortak ImageRequest oluşturucu.
     */
    fun buildRequest(
        context: Context,
        url: String,
        widthPx: Int,
        heightPx: Int,
        isDisplay: Boolean = false,
    ): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey(url)
            .size(widthPx, heightPx)
            .precision(Precision.EXACT)
            .scale(Scale.FILL)

        if (isDisplay && BuildConfig.DEBUG) {
            builder.listener(
                onSuccess = { _, result: SuccessResult ->
                    PosterPrefetchMetrics.totalLoads.incrementAndGet()
                    when (result.dataSource) {
                        DataSource.MEMORY_CACHE, DataSource.MEMORY -> {
                            PosterPrefetchMetrics.memoryCacheHits.incrementAndGet()
                        }
                        DataSource.DISK -> {
                            PosterPrefetchMetrics.diskCacheHits.incrementAndGet()
                        }
                        DataSource.NETWORK -> {
                            PosterPrefetchMetrics.networkHits.incrementAndGet()
                        }
                    }
                },
                onError = { _, _ ->
                    if (BuildConfig.DEBUG) {
                        PosterPrefetchMetrics.totalLoads.incrementAndGet()
                    }
                }
            )
        }

        return builder.build()
    }

    /**
     * Verilen URL listesini arka planda Coil bellek önbelleğine yükler (prefetch).
     * Aynı anda en çok [PosterPrefetchRules.MAX_CONCURRENT_PREFETCH] (4) istek işlenir.
     * Ağ hataları sessizce yutulur, log'a hiçbir URL yazılmaz.
     */
    fun prefetchUrls(
        context: Context,
        urls: List<String>,
        widthPx: Int,
        heightPx: Int,
        imageLoader: ImageLoader = SingletonImageLoader.get(context),
        coroutineScope: CoroutineScope = prefetchScope,
    ): Job {
        if (!PosterPrefetchFlags.isPrefetchEnabled || urls.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            return Job().apply { complete() }
        }

        return coroutineScope.launch(Dispatchers.IO) {
            for (url in urls) {
                if (!isActive) break
                if (url.isBlank()) continue

                launch {
                    prefetchSemaphore.withPermit {
                        if (!isActive) return@withPermit
                        if (BuildConfig.DEBUG) {
                            PosterPrefetchMetrics.prefetchesTriggered.incrementAndGet()
                        }
                        val request = buildRequest(context, url, widthPx, heightPx, isDisplay = false)
                        runCatching {
                            imageLoader.execute(request)
                            if (BuildConfig.DEBUG) {
                                PosterPrefetchMetrics.prefetchesSuccess.incrementAndGet()
                            }
                        }
                    }
                }
            }
        }
    }
}
