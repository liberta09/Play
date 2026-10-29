package com.kaan.play.ui.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import com.kaan.play.data.torrent.TorrentDiag
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import android.os.Handler
import android.os.Looper
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamLog
import com.kaan.play.data.stream.StreamPlaybackCookieJar
import com.kaan.play.data.stream.StreamSessionCookies
import com.kaan.play.data.stream.TorrentLinks
import okhttp3.OkHttpClient
import java.util.Locale
import java.util.concurrent.TimeUnit

private class PlaybackHttpSession(
    headers: Map<String, String>,
    val cookieJar: StreamPlaybackCookieJar,
) {
    @Volatile var headers: Map<String, String> = headers
}

/** Oynatma hatasının kullanıcıya gösterilen üç sınıfı. */
enum class PlaybackError { Network, Unavailable, Format }

/** Arayüzün okuduğu anlık durum (Compose anlık görüntü durumu). */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val buffering: Boolean = true,
    val ended: Boolean = false,
    val error: PlaybackError? = null,
    /** Altyazı parçaları değişince artar; seçenek listesi buna göre yeniden okunur. */
    val tracksVersion: Int = 0,
    /** Çalan videonun çözünürlüğü (bilinmiyorsa 0). */
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    /** Oynatma hızı (varsayılan 1×; oturumluk). */
    val speed: Float = 1.0f,
)

/** Videonun içindeki (Media3'ün okuduğu) altyazı parçası; [index] parça listesindeki sıradır. */
data class EmbeddedSubtitle(val index: Int, val language: String?, val selected: Boolean, val label: String? = null)

/**
 * Videonun ses parçası; [index] ses parçaları listesindeki sıradır, [label] parçanın kendi adı (yoksa null), [channels] kanal sayısı
 * (bilinmiyorsa 0), [mime] ses codec türü.
 */
data class AudioTrack(
    val index: Int,
    val language: String?,
    val label: String?,
    val selected: Boolean,
    val channels: Int = 0,
    val mime: String? = null,
)

/** Videonun çözünürlük/bitrate seçeneği (uyarlanabilir akışlarda birden çok); [group] ve [track] oynatıcının parça konumudur. */
data class VideoOption(val group: Int, val track: Int, val width: Int, val height: Int, val bitrate: Int, val selected: Boolean)

/**
 * Media3 ExoPlayer'ı sarar: kaynağı yükler (proxyHeaders), durumu Compose'a açar. Başlıklar yalnızca akış isteklerine eklenir.
 * Harici altyazılar (addon / eklenti / akış adresleri) Media3'e verilmez: [SubtitleController] indirir, ayrıştırır ve satırları
 * kendisi gösterir (kaydırma anında uygulansın diye). Burada yalnızca videonun kendi altyazı parçaları seçilir.
 */
@OptIn(UnstableApi::class)
class PlaybackController(
    context: Context,
    private val client: OkHttpClient,
    private val offlineCache: Cache? = null,
    /** `magnet:` kaynağın yerleşik torrent motorundaki yerel http adresi (magnet, dosya sırası). */
    private val torrentUrl: (String, Int?) -> String = { _, _ -> throw IllegalStateException("no torrent engine") },
    bigBuffer: Boolean = false,
) : SubtitleHost {

    @Volatile private var torrent = false
    @Volatile private var activeHttpSession = PlaybackHttpSession(emptyMap(), StreamPlaybackCookieJar())
    private var headers: Map<String, String>
        get() = activeHttpSession.headers
        set(value) { activeHttpSession.headers = value }

    // Yerel torrent sunucusu üst bilgi ve ilk parçalar gelene dek yanıtı bekletir: okuma zaman aşımı uzun tutulur.
    private val torrentSource = OkHttpDataSource.Factory(client.newBuilder().readTimeout(TORRENT_READ_TIMEOUT_S, TimeUnit.SECONDS).build())
        .setUserAgent(DEFAULT_USER_AGENT)
    @Volatile private var onlineSource = createOnlineSource(activeHttpSession)

    private fun createOnlineSource(session: PlaybackHttpSession): ResolvingDataSource.Factory = ResolvingDataSource.Factory(
        DataSource.Factory {
            val sessionClient = client.newBuilder()
                .cookieJar(session.cookieJar)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            TsSniffingDataSource(OkHttpDataSource.Factory(sessionClient).setUserAgent(DEFAULT_USER_AGENT).createDataSource())
        }
    ) { spec ->
        val streamHeaders = session.headers
        val merged = buildMap {
            put("User-Agent", streamHeaders["User-Agent"] ?: DEFAULT_USER_AGENT)
            putAll(streamHeaders)
        }
        spec.withAdditionalHeaders(merged)
    }

    private val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)

    val player: ExoPlayer = run {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        // [ADIM 4] TV Box RAM sınırı (DefaultLoadControl)
        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(16 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .setBufferDurationsMs(15000, 30000, 2500, 5000)
            .setBackBuffer(0, false)
            .build()

        val builder = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    if (offlineCache != null) {
                        // İndirilmiş kopya: yalnızca önbellekten okunur (üst akış yok); internet hiç kullanılmaz.
                        CacheDataSource.Factory().setCache(offlineCache).setCacheWriteDataSinkFactory(null)
                    } else {
                        DataSource.Factory { if (torrent) torrentSource.createDataSource() else onlineSource.createDataSource() }
                    },
                ).setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5)),
            )
            .setBandwidthMeter(bandwidthMeter)
        builder.build().also { exo ->
            // Altyazı varsayılan olarak kapalıdır; seçilince açılır (videonun içindekiler Media3'le, diğerleri kendi katmanımızla).
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    var speed: Float
        get() = player.playbackParameters.speed
        set(value) {
            player.playbackParameters = PlaybackParameters(value, 1f)
            ui = ui.copy(speed = value)
        }

    val stallTracker = PlaybackStallTracker()
    private var released = false

    // [ADIM 5] Seek Watchdog ve Otomatik Kurtarma (Auto-Recovery)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var seekWatchdogRunnable: Runnable? = null
    private var isSeeking = false
    private var recoveryAttempts = 0

    private fun startSeekWatchdog() {
        cancelSeekWatchdog()
        val r = Runnable {
            if (!released && player.playbackState == Player.STATE_BUFFERING) {
                val currentPos = player.currentPosition
                Log.w("PlayMatch", "Seek sonrası 8 saniye tamponlama aşıldı: akış otomatik kurtarılıyor pos=$currentPos")
                player.seekTo(currentPos)
                player.prepare()
                player.play()
            }
        }
        seekWatchdogRunnable = r
        mainHandler.postDelayed(r, 8_000L)
    }

    private fun cancelSeekWatchdog() {
        seekWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        seekWatchdogRunnable = null
    }

    var ui by mutableStateOf(PlayerUiState())
        private set

    /**
     * Kaynak açılamayınca hata ekranından önce çağrılır; true dönerse (ör. yedek kaynağa geçildi) hata gösterilmez.
     * Yalnızca otomatik sonraki bölümde kullanılır.
     */
    var onFailure: ((String) -> Boolean)? = null

    /** İlk video karesi çizildi (kaynak gerçekten açıldı); kaynak seçimi 10 sn'lik süreyi buna göre izler. */
    var onFirstFrame: (() -> Unit)? = null

    /** Videonun süresi öğrenildi (ms; canlı yayında çağrılmaz): içerik doğrulaması süreyi TMDB süresiyle karşılaştırır. */
    var onDurationKnown: ((Long) -> Unit)? = null

    /** Kaynağın taşıdığı bölüm/intro/outro metadata'sı; otomatik atlama algılayıcısı bunu dinler. */
    var onMetadata: ((Metadata) -> Unit)? = null

    init {
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long,
            ) {
                if (droppedFrames >= 3) {
                    Log.w("PlayMatch", "video kareleri atlandı sayı=$droppedFrames aralık=${elapsedMs}ms kaynak=${stallTracker.sourceName}")
                }
            }
        })
        player.addListener(object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                ui = ui.copy(speed = playbackParameters.speed)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui = ui.copy(isPlaying = isPlaying)
                stallTracker.onIsPlayingChanged(isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                val buffering = state == Player.STATE_BUFFERING
                ui = ui.copy(buffering = buffering, ended = state == Player.STATE_ENDED)
                val stallStarted = stallTracker.onPlaybackStateChanged(buffering = buffering, isReady = state == Player.STATE_READY)
                if (stallStarted) logStallDetail()

                if (state == Player.STATE_READY) {
                    cancelSeekWatchdog()
                    isSeeking = false
                    recoveryAttempts = 0
                    stallTracker.onSeekEnded()
                    if (!player.isCurrentMediaItemLive) {
                        val duration = player.duration
                        if (duration != C.TIME_UNSET && duration > 0) onDurationKnown?.invoke(duration)
                    }
                } else if (state == Player.STATE_BUFFERING && isSeeking) {
                    startSeekWatchdog()
                }

                // Görüntüsüz içerik (yalnızca ses) için ilk kare gelmez: hazır olunca açıldı sayılır.
                if (state == Player.STATE_READY && player.currentTracks.groups.none { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }) {
                    stallTracker.onFirstFrameRendered()
                    onFirstFrame?.invoke()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                try {
                    // [ADIM 5] Hata anında otomatik kurtarma (BehindLiveWindow veya HttpDataSource)
                    val isBehindLive = generateSequence<Throwable>(error) { it.cause }
                        .any { it is androidx.media3.exoplayer.source.BehindLiveWindowException }
                    val isHttpError = generateSequence<Throwable>(error) { it.cause }
                        .any { it is HttpDataSource.HttpDataSourceException }

                    if ((isBehindLive || isHttpError) && recoveryAttempts < 3 && !released) {
                        recoveryAttempts++
                        val currentPos = player.currentPosition
                        Log.w("PlayMatch", "Ağ veya HLS canlı pencere hatası yakalandı (${error.errorCodeName}): akış tazeleniyor pos=$currentPos deneme=$recoveryAttempts")
                        player.seekTo(currentPos)
                        player.prepare()
                        player.play()
                        return
                    }

                    val http = generateSequence<Throwable>(error) { it.cause }
                        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
                    TorrentDiag.e(
                        "ExoPlayer hata: ${error.errorCodeName} (${error.errorCode}) → ${errorOf(error)}" +
                            (http?.let { " http=${it.responseCode} ctype=${it.headerFields["Content-Type"]}" } ?: ""),
                        error,
                    )
                    // Eklentinin verdiği başlıklar (Referer / tarayıcı User-Agent) bazı CDN'lerde tam tersine reddedilir
                    // (ör. 428/429). Başlıklı istek 4xx alırsa aynı konumdan bir kez başlıksız denenir.
                    if (retryWithoutHeaders(http?.responseCode, headersSent = headers.isNotEmpty())) {
                        TorrentDiag.d("başlıklı istek ${http?.responseCode} aldı: başlıksız yeniden deneniyor")
                        headers = emptyMap()
                        player.prepare()
                        return
                    }
                    if (onFailure?.invoke(failureDetail(error, http?.responseCode)) == true) return
                    ui = ui.copy(error = errorOf(error), buffering = false)
                } catch (t: Throwable) {
                    android.util.Log.e("PlaybackController", "onPlayerError içinde hata yakalandı: ${t.message}", t)
                    ui = ui.copy(error = errorOf(error), buffering = false)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                ui = ui.copy(tracksVersion = ui.tracksVersion + 1)
            }

            override fun onMetadata(metadata: Metadata) {
                onMetadata?.invoke(metadata)
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                ui = ui.copy(videoWidth = videoSize.width, videoHeight = videoSize.height)
            }

            override fun onRenderedFirstFrame() {
                stallTracker.onFirstFrameRendered()
                onFirstFrame?.invoke()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    if (player.playbackState == Player.STATE_READY && !player.isLoading) {
                        stallTracker.onSeekEnded()
                        cancelSeekWatchdog()
                    } else {
                        startSeekWatchdog()
                    }
                }
            }
        })
    }

    /** [startMs] konumundan oynatmaya başlar. Önceki hata temizlenir. */
    fun load(stream: PlayableStream, startMs: Long) {
        player.stop()
        player.clearMediaItems()
        activeHttpSession.cookieJar.clear()
        val streamCookieJar = StreamPlaybackCookieJar().apply {
            replace(StreamSessionCookies.forPlayback(stream.source.addonId, stream.url))
        }
        activeHttpSession = PlaybackHttpSession(stream.headers, streamCookieJar)
        onlineSource = createOnlineSource(activeHttpSession)
        stallTracker.reset(stream.source.addonName)
        torrent = TorrentLinks.isMagnet(stream.url)
        // Magnet: yerleşik motorun yerel adresi; tür içerikten çıkarılır (dosya adı üst bilgi gelince belli olur).
        val uri = if (torrent) torrentUrl(stream.url, stream.torrentFileIdx) else stream.url

        val mime = if (torrent) null else stream.mimeType ?: streamMimeType(stream.url)
        StreamLog.opening(stream.url, mime)
        // Yalnızca şema/host/uzantı: tam adres, magnet ve yerel sunucu belirteci loglanmaz.
        TorrentDiag.d(
            "oynatıcıya veriliyor: kaynak=${stream.source.addonName} ${StreamLog.describe(stream.url)} torrent=$torrent " +
                "fileIdx=${stream.torrentFileIdx} başlıklar=${stream.headers.keys} mime=$mime",
        )
        val item = MediaItem.Builder()
            .setUri(uri)
            .apply { mime?.let(::setMimeType) }
            .build()

        ui = ui.copy(error = null, ended = false, buffering = true)
        val isHls = mime == MimeTypes.APPLICATION_M3U8 || stream.url.lowercase(Locale.ROOT).contains(".m3u8")
        if (isHls && offlineCache == null && !torrent) {
            val hlsExtractorFactory = DefaultHlsExtractorFactory(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
                true,
            )
            val dataSourceFactory = DataSource.Factory { onlineSource.createDataSource() }
            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                .setExtractorFactory(hlsExtractorFactory)
                .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(5))
                .createMediaSource(item)
            player.setMediaSource(hlsSource, startMs)
        } else {
            player.setMediaItem(item, startMs)
        }
        player.prepare()
        player.playWhenReady = true
    }

    /** İndirilmiş kopyayı oynatır ([item] indirme isteğinden gelir; HLS/DASH'te indirilen kaliteyle sınırlıdır). */
    fun loadDownloaded(item: MediaItem, startMs: Long) {
        player.stop()
        player.clearMediaItems()
        activeHttpSession.cookieJar.clear()
        activeHttpSession = PlaybackHttpSession(emptyMap(), StreamPlaybackCookieJar())
        onlineSource = createOnlineSource(activeHttpSession)
        stallTracker.reset("İndirilen")
        ui = ui.copy(error = null, ended = false, buffering = true)
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** İndirilen kopya bulunamadı (silinmiş/bozuk): oynatıcı hata ekranını gösterir. */
    fun markUnavailable() {
        ui = ui.copy(error = PlaybackError.Unavailable, buffering = false)
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        stallTracker.onSeekStarted()
        isSeeking = true
        startSeekWatchdog()
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, duration))
    }

    fun seekTo(positionMs: Long) {
        stallTracker.onSeekStarted()
        isSeeking = true
        startSeekWatchdog()
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun positionMs(): Long = player.currentPosition

    /** Süre bilinmiyorsa 0. */
    fun durationMs(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    // ---- altyazı ----

    private fun textGroups(): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }

    /** Videonun kendi altyazı parçaları (dil etiketi parçanın söylediğidir, çoğu zaman `en`/`tr` gibi kısa kod). */
    override fun embeddedSubtitles(): List<EmbeddedSubtitle> {
        val textOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        return textGroups().mapIndexed { i, group ->
            val format = group.mediaTrackGroup.getFormat(0)
            EmbeddedSubtitle(i, format.language, selected = !textOff && group.isSelected, label = format.label)
        }
    }

    private fun audioGroups(): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }

    /** Videonun ses parçaları (dil etiketiyle; altyazı menüsünde birden çoksa seçilir). */
    fun audioTracks(): List<AudioTrack> = audioGroups().mapIndexed { i, group ->
        val format = group.mediaTrackGroup.getFormat(0)
        AudioTrack(i, format.language, format.label, group.isSelected, format.channelCount.coerceAtLeast(0), format.sampleMimeType)
    }

    private fun videoGroups(): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }

    /** Videonun çözünürlük/bitrate seçenekleri (uyarlanabilir akışlarda birden çok; düz dosyada tek). */
    fun videoOptions(): List<VideoOption> = videoGroups().flatMapIndexed { gi, group ->
        (0 until group.length).filter { group.isTrackSupported(it) }.map { ti ->
            val f = group.mediaTrackGroup.getFormat(ti)
            VideoOption(gi, ti, f.width.coerceAtLeast(0), f.height.coerceAtLeast(0), f.bitrate.coerceAtLeast(0), group.isTrackSelected(ti))
        }
    }

    /** Video parçasını seçer; [option] null: "Otomatik" (oynatıcı kendi seçer). */
    fun selectVideo(option: VideoOption?) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            val group = videoGroups().getOrNull(option.group) ?: return
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.track))
        }
        player.trackSelectionParameters = builder.build()
    }

    /** Video seçimi elle sabitlenmiş mi (aksi hâlde "Otomatik")? */
    fun videoPinned(): Boolean = player.trackSelectionParameters.overrides.keys.any { it.type == C.TRACK_TYPE_VIDEO }

    fun selectAudio(index: Int) {
        val group = audioGroups().getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    /** Videonun içindeki parçayı açar (Media3 çizer). */
    override fun selectEmbeddedSubtitle(index: Int) {
        val group = textGroups().getOrNull(index) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
            .build()
    }

    /** Media3'ün altyazı çizimini kapatır (altyazı kapalıyken ya da harici altyazıyı biz çizerken). */
    override fun disableEmbeddedSubtitles() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    private fun currentVideoBitrate(): Int? {
        val direct = player.videoFormat?.bitrate?.takeIf { it > 0 && it != Format.NO_VALUE }
        if (direct != null) return direct
        return player.currentTracks.groups
            .firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
            ?.mediaTrackGroup
            ?.let { group ->
                (0 until group.length)
                    .map { group.getFormat(it).bitrate }
                    .firstOrNull { it > 0 && it != Format.NO_VALUE }
            }
    }

    private fun logStallDetail() {
        val detail = PlaybackStallTracker.formatStallDetail(
            sourceName = stallTracker.sourceName,
            videoBitrate = currentVideoBitrate(),
            bandwidthEstimate = bandwidthMeter.bitrateEstimate.takeIf { it > 0L },
            bufferedPositionMs = player.bufferedPosition,
            currentPositionMs = player.currentPosition,
        )
        Log.d("PlayMatch", detail)
    }

    fun release() {
        if (released) return
        released = true
        cancelSeekWatchdog()
        val summary = stallTracker.finish()
        Log.d("PlayMatch", summary)
        logStallDetail()
        activeHttpSession.cookieJar.clear()
        if (Looper.myLooper() == Looper.getMainLooper()) {
            player.release()
        } else {
            Handler(Looper.getMainLooper()).post {
                player.release()
            }
        }
    }

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val TORRENT_READ_TIMEOUT_S = 120L

        /** Kaynağın başlıklarıyla gönderilen istek 4xx aldıysa başlıksız bir kez daha denenir (başlıksız istek yeniden denenmez). */
        internal fun retryWithoutHeaders(responseCode: Int?, headersSent: Boolean): Boolean =
            headersSent && responseCode != null && responseCode in 400..499

        /** `https://x/y.m3u8?token` gibi adreslerde ya da uzantısız HLS/DASH'te Media3 türü kendi çıkaramaz. */
        internal fun streamMimeType(url: String): String? {
            val lower = url.lowercase(Locale.ROOT)
            return when {
                ".m3u8" in lower -> MimeTypes.APPLICATION_M3U8
                ".mpd" in lower -> MimeTypes.APPLICATION_MPD
                else -> fileMimeType(lower)
            }
        }

        /** Sorgu/parça atıldıktan sonra yolun uzantısına göre düz dosya türü; uzantı yoksa/bilinmiyorsa null (Media3 içerikten çıkarır). */
        private fun fileMimeType(lowerUrl: String): String? {
            val path = lowerUrl.substringBefore('#').substringBefore('?')
            return when (path.substringAfterLast('/').substringAfterLast('.', "")) {
                "mkv" -> MimeTypes.VIDEO_MATROSKA
                "webm" -> MimeTypes.VIDEO_WEBM
                "mp4", "m4v" -> MimeTypes.VIDEO_MP4
                "avi" -> MimeTypes.VIDEO_AVI
                else -> null
            }
        }

        /** Hata ayrıntısı (ana iletide gösterilmez): "ERROR_CODE_IO_BAD_HTTP_STATUS (2004) http=404". */
        internal fun failureDetail(e: PlaybackException, httpCode: Int?): String =
            "${e.errorCodeName} (${e.errorCode})" + (httpCode?.let { " http=$it" } ?: "")

        internal fun errorOf(e: PlaybackException): PlaybackError = when (e.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.Network

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackError.Unavailable

            else -> PlaybackError.Format
        }
    }
}

/**
 * TS segmentlerinin başında bulunan sahte resim (PNG/JPG) ve çöp başlıkları 0x47 (TS sync byte) baytına kadar kırpar.
 */
@OptIn(UnstableApi::class)
private class TsSniffingDataSource(private val upstream: DataSource) : DataSource {
    private var isTsSegment = false
    private var headerSkipped = false

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val path = dataSpec.uri.path?.lowercase(Locale.ROOT) ?: ""
        val query = dataSpec.uri.query?.lowercase(Locale.ROOT) ?: ""
        isTsSegment = path.endsWith(".ts") || path.endsWith(".image") || path.contains("/hls") ||
            path.contains("/segment") || query.contains(".ts") || query.contains("format=ts")
        headerSkipped = false
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!isTsSegment || headerSkipped || length <= 0) {
            return upstream.read(buffer, offset, length)
        }
        val temp = ByteArray(minOf(length, 4096))
        val readBytes = upstream.read(temp, 0, temp.size)
        if (readBytes <= 0) return readBytes

        if (temp[0] == 0x47.toByte()) {
            headerSkipped = true
            System.arraycopy(temp, 0, buffer, offset, readBytes)
            return readBytes
        }

        var syncIdx = -1
        for (i in 0 until readBytes) {
            if (temp[i] == 0x47.toByte()) {
                if (i + 188 < readBytes) {
                    if (temp[i + 188] == 0x47.toByte()) {
                        syncIdx = i
                        break
                    }
                } else {
                    syncIdx = i
                    break
                }
            }
        }

        headerSkipped = true
        return if (syncIdx >= 0) {
            val validLen = readBytes - syncIdx
            System.arraycopy(temp, syncIdx, buffer, offset, validLen)
            validLen
        } else {
            System.arraycopy(temp, 0, buffer, offset, readBytes)
            readBytes
        }
    }

    override fun getUri(): android.net.Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        upstream.close()
    }
}
