package com.kaan.play.tv

import com.kaan.play.R
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.kaan.play.PlayApp
import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.stream.PlayableStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Ana sayfa önizlemesi: YouTube fragmanının çözülmüş akışları ya da (fragman yoksa) ilk bölümün çalışan akışı. */
sealed interface Preview {
    data class YouTube(val video: String, val audio: String?) : Preview
    data class Stream(val stream: PlayableStream) : Preview
}

/**
 * kart [PREVIEW_DELAY_MS] odakta kalınca üstteki tanıtımda sesli önizleme. Sıra: içeriğin fragmanı (TMDB'de YouTube,
 * eklentide `trailers` YouTube adresiyse) → yoksa dizinin 1. bölümü / filmin kendisi, TV'nin kaynak sınayıcısıyla
 * ([TvSourcePicker]) bulunan çalışan akıştan ilk [PREVIEW_STREAM_MS]. Kart değişince iş iptal edilir; aynı anda tek önizleme.
 * Çözülen önizleme süreç boyunca (en çok 24 içerik) önbellekte kalır.
 */
class TvPreviewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = getApplication<PlayApp>().container
    private val _preview = MutableStateFlow<Pair<String, Preview>?>(null)
    val preview: StateFlow<Pair<String, Preview>?> = _preview
    private var job: Job? = null
    private var focusedKey: String? = null

    fun focus(item: CatalogItem?) {
        val key = item?.let { "${it.providerId}|${it.rawUrl ?: it.id}" }
        if (key == focusedKey) return
        focusedKey = key
        job?.cancel()
        _preview.value = null
        if (item == null || key == null) return
        job = viewModelScope.launch {
            delay(PREVIEW_DELAY_MS)
            val hit = cached(key)
            val p = if (hit != null) hit.second else resolve(item).let { (p, final) -> if (final) remember(key, p); p }
            if (p != null && focusedKey == key) _preview.value = key to p
        }
    }

    /** Oynatılamayan önizleme (oynatıcı hatası) önbellekten çıkar; sonraki odakta yeniden çözülür. */
    fun forgetFocused() {
        focusedKey?.let { k -> synchronized(cache) { cache.remove(k) } }
    }

    fun stop() {
        focusedKey = null
        job?.cancel()
        _preview.value = null
    }

    /** (önizleme, önbelleğe yazılabilir mi): eklenti açılamadı / zaman aşımı / hata kesin sonuç değildir, sonraki odakta yeniden denenir. */
    private suspend fun resolve(item: CatalogItem): Pair<Preview?, Boolean> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            runCatching {
                val ref = item.toRef(false) ?: return@runCatching (null to true)
                val loader = TvDetailLoader(container)
                val provider = loader.csProviderFor(ref.providerId, ref.url)
                val state = loader.fetch(provider, ref.url)
                val d = (state as? DetailState.Ready)?.data
                    ?: return@runCatching (null to false).also { Log.d(TAG, "detay yok: ${(state as? DetailState.Failed)?.message}") }
                (d.trailerKey ?: youtubeKey(d.trailerUrl))?.let { key ->
                    // Çözülemezse (YouTube değişikliği, ağ) içeriğin kendi akışına düşülür.
                    // runInterruptible: kart değişince/zaman aşımında NewPipe'ın engelleyen çağrısı da kesilir.
                    val yt = runCatching { runInterruptible { TvYouTube.resolve(key) } }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it // iptal yutulmasın
                        Log.d(TAG, "fragman çözülemedi: ${it.javaClass.simpleName}: ${it.message}")
                    }.getOrNull()
                    if (yt != null) return@runCatching Preview.YouTube(yt.video, yt.audio) to true
                }
                val episode = if (d.isSeries) d.episodes.firstOrNull() ?: return@runCatching (null to true) else null
                val target = loader.target(provider, ref.url, d, episode)
                TvSourcePicker(container).pick(target.request) {}?.best?.let { Preview.Stream(it) } to true
            }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Log.d(TAG, "önizleme çözülemedi: ${it.javaClass.simpleName}") }.getOrNull()
        } ?: (null to false)
    }

    private fun remember(key: String, p: Preview?) = synchronized(cache) {
        cache[key] = System.currentTimeMillis() to p
        while (cache.size > 24) cache.remove(cache.keys.first())
    }

    /** Önbellekteki sonuç (zaman, önizleme); [CACHE_TTL_MS]'den eskiyse yok sayılır: akış/YouTube adresleri bayatlar. */
    private fun cached(key: String): Pair<Long, Preview?>? = synchronized(cache) {
        cache[key]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS } ?: run { cache.remove(key); null }
    }

    private companion object {
        const val TAG = "PlayTvPreview"
        const val RESOLVE_TIMEOUT_MS = 25_000L
        const val CACHE_TTL_MS = 10 * 60_000L
        /** İçerik → (çözüldüğü an, önizleme); null önizleme = yok, süre dolana kadar yeniden aranmaz. */
        val cache = LinkedHashMap<String, Pair<Long, Preview?>>()
    }
}

/** Kartta önizleme başlamadan önceki bekleme. */
internal const val PREVIEW_DELAY_MS = 3_000L
/** Bölüm önizlemesinin süresi: dikey dramada bu aralık başa dönerek sürer, yatayda sonra görsel geri gelir. */
internal const val PREVIEW_STREAM_MS = 30_000L

/** youtube.com/watch?v=, youtu.be/, /embed/ adreslerinden 11 haneli video kimliği. */
internal fun youtubeKey(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val m = Regex("""(?:youtube(?:-nocookie)?\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/)|youtu\.be/)([A-Za-z0-9_-]{11})""").find(url)
    return m?.groupValues?.get(1)
}

/**
 * Önizleme alanı (sesli), ExoPlayer: [Preview.Stream] — [loop] ise ilk [PREVIEW_STREAM_MS] başa dönerek sürer, değilse
 * sonra [onEnded]; [Preview.YouTube] — fragman sonuna kadar. [onRendered] ilk görüntü gelince çağrılır (üstteki görsel o zaman çekilir).
 */
@OptIn(UnstableApi::class)
@Composable
internal fun PreviewView(preview: Preview, modifier: Modifier, loop: Boolean, onRendered: () -> Unit, onEnded: (failed: Boolean) -> Unit) {
    val context = LocalContext.current
    val limited = preview is Preview.Stream
    // Kaynak/oynatıcı kurulamazsa (ör. APK'da olmayan rtsp/SmoothStreaming modülü) çökmek yerine önizleme biter.
    val player = remember(preview) {
        var built: ExoPlayer? = null
        runCatching {
        val source: MediaSource = when (preview) {
            is Preview.Stream -> {
                val stream = preview.stream
                val http = DefaultHttpDataSource.Factory()
                    .setDefaultRequestProperties(stream.headers)
                    .setAllowCrossProtocolRedirects(true)
                    .also { f -> stream.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.let { f.setUserAgent(it.value) } }
                val item = MediaItem.Builder().setUri(Uri.parse(stream.url)).apply {
                    val mime = stream.mimeType ?: if (stream.url.contains(".m3u8", true)) MimeTypes.APPLICATION_M3U8 else null
                    if (mime != null) setMimeType(mime)
                }.build()
                DefaultMediaSourceFactory(http).createMediaSource(item)
            }
            is Preview.YouTube -> {
                val factory = ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
                val video = factory.createMediaSource(MediaItem.fromUri(preview.video))
                preview.audio?.let { MergingMediaSource(video, factory.createMediaSource(MediaItem.fromUri(it))) } ?: video
            }
        }
        ExoPlayer.Builder(context).build().also { built = it }.apply {
            setMediaSource(source)
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() = onRendered()
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { Log.d("PlayTvPreview", "oynatıcı hatası: ${error.errorCodeName}"); onEnded(true) }
                override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) onEnded(false) }
            })
            prepare()
            playWhenReady = true
        }
        }.onFailure {
            built?.release() // yarıda kalan kurulumun oynatıcısı serbest bırakılır
            Log.d("PlayTvPreview", "önizleme kurulamadı: ${it.javaClass.simpleName}")
        }.getOrNull()
    }
    if (player == null) {
        LaunchedEffect(preview) { onEnded(true) }
        return
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player) {
        if (!limited) return@LaunchedEffect
        if (!loop) { delay(PREVIEW_STREAM_MS); onEnded(false); return@LaunchedEffect }
        while (true) { delay(PREVIEW_STREAM_MS); player.seekTo(0) }
    }
    AndroidView(
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx).inflate(R.layout.tv_preview_player, null) as PlayerView).apply { this.player = player }
        },
        modifier = modifier,
    )
}
