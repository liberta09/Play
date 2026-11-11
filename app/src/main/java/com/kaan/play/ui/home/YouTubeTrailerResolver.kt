package com.kaan.play.ui.home

import android.util.Log
import com.kaan.play.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object YouTubeTrailerResolver {
    private val initialized = AtomicBoolean(false)
    private val cache = ConcurrentHashMap<String, CachedTrailerStream>()
    private const val CACHE_TTL_MS = 60 * 60 * 1000L // 1 saat
    private const val RESOLVE_TIMEOUT_MS = 8_000L // 8 sn zaman aşımı

    data class CachedTrailerStream(
        val streamUrl: String,
        val durationSeconds: Long,
        val height: Int,
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        val isValid: Boolean get() = System.currentTimeMillis() - timestamp < CACHE_TTL_MS
    }

    fun init() {
        if (initialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(NewPipeDownloader())
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) {
                    Log.w("PlayHero", "NewPipe init error: ${t.message}")
                }
            }
        }
    }

    fun isValidKey(key: String?): Boolean =
        !key.isNullOrBlank() && key.matches(Regex("^[a-zA-Z0-9_-]{11}$"))

    fun getCached(key: String): CachedTrailerStream? {
        val cached = cache[key] ?: return null
        return if (cached.isValid) cached else {
            cache.remove(key)
            null
        }
    }

    suspend fun resolveStream(
        videoKey: String,
        isLowPerf: Boolean = false,
    ): CachedTrailerStream? = withContext(Dispatchers.IO) {
        if (!isValidKey(videoKey)) return@withContext null
        getCached(videoKey)?.let { return@withContext it }

        init()

        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            val startTime = System.currentTimeMillis()
            try {
                val service = ServiceList.YouTube
                val url = "https://www.youtube.com/watch?v=$videoKey"
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()

                val duration = extractor.length
                val allVideoStreams = extractor.videoOnlyStreams.ifEmpty { extractor.videoStreams }
                val chosen = pickBestStream(allVideoStreams, isLowPerf)
                val streamUrl = chosen?.content ?: chosen?.url
                if (chosen != null && !streamUrl.isNullOrBlank()) {
                    val resolved = CachedTrailerStream(
                        streamUrl = streamUrl,
                        durationSeconds = duration,
                        height = chosen.height,
                    )
                    cache[videoKey] = resolved
                    val elapsed = System.currentTimeMillis() - startTime
                    if (BuildConfig.DEBUG) {
                        Log.d("PlayHero", "stream resolved in ${elapsed}ms: ${chosen.height}p duration=${duration}s")
                    }
                    resolved
                } else {
                    if (BuildConfig.DEBUG) {
                        Log.w("PlayHero", "no matching video stream found for key")
                    }
                    null
                }
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) {
                    Log.w("PlayHero", "stream resolve error: ${t.javaClass.simpleName}: ${t.message}")
                }
                null
            }
        }
    }

    internal fun pickBestStream(
        streams: List<VideoStream>?,
        isLowPerf: Boolean,
    ): VideoStream? {
        if (streams.isNullOrEmpty()) return null
        val maxHeight = if (isLowPerf) 480 else 720

        // 1. MP4 / AVC1 ve <= maxHeight
        val avc1Matches = streams.filter { stream ->
            val formatMatch = stream.format?.name?.contains("MP4", ignoreCase = true) == true ||
                stream.codec?.contains("avc", ignoreCase = true) == true ||
                stream.codec?.contains("h264", ignoreCase = true) == true
            val heightMatch = stream.height in 1..maxHeight
            formatMatch && heightMatch
        }

        if (avc1Matches.isNotEmpty()) {
            return avc1Matches.maxByOrNull { it.height }
        }

        // 2. Fallback: herhangi bir <= maxHeight stream
        val anyMatchingHeight = streams.filter { it.height in 1..maxHeight }
        if (anyMatchingHeight.isNotEmpty()) {
            return anyMatchingHeight.maxByOrNull { it.height }
        }

        // 3. Fallback: en küçük boyutlu stream
        return streams.minByOrNull { it.height }
    }
}
