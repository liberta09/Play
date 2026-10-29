package com.kaan.play.ui.player

import android.media.AudioManager
import android.util.Log
import android.text.format.Formatter
import com.kaan.play.BuildConfig
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.TextureView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Metadata
import com.kaan.play.PlayApp
import com.kaan.play.R
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.download.DownloadedSubtitleLoader
import com.kaan.play.data.download.Probe
import com.kaan.play.data.download.StartResult
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.settings.SkipPlaybackSettings
import com.kaan.play.data.stream.DeviceCodecs
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.data.stream.ranked
import com.kaan.play.data.subtitle.SubtitleDiag
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleKind
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.data.subtitle.SubtitleLoader
import com.kaan.play.data.subtitle.SubtitleProvider
import com.kaan.play.data.subtitle.TrackLoader
import com.kaan.play.ui.theme.PlayTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Tam ekran yatay oynatıcı. Ekran açık kalır. Konum her 10 sn'de bir, duraklatınca, kaynak değişince,
 * bitince ve etkinlik durunca (Geri dahil) geçmişe yazılır; kurallar [WatchRules]'tadır.
 *
 * Kaynak seçimi [SourceCoordinator]'dadır: "Kaynağı otomatik seç" açıkken kaynak listesi beklenmez, kaynaklar arka planda yüklenirken
 * uygun ilk kaynakla başlanır; kaynak/altyazı/ses/çözünürlük oynatıcının içinden değiştirilir.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var session: PlaySession
    private lateinit var controller: PlaybackController
    private lateinit var subtitles: SubtitleController
    private lateinit var autoNext: AutoNextController

    /** Çevrimiçi oynatmada kaynak seçimi; indirilmiş kopyada (çevrimdışı) null. */
    private var coordinator: SourceCoordinator? = null

    /** Şu an oynayan akış (kaynak değişince güncellenir); sonraki bölümde "aynı eklenti" bundan bulunur. */
    private var currentStream: PlayableStream? = null

    /** Seçili ses parçası Türkçe mi (dosyanın içindeki dublaj): üst çubukta/panelde "Dublajlı (gömülü ses)". */
    private var embeddedDub by mutableStateOf(false)

    /** Kullanıcı sesi elle seçtiyse otomatik Türkçe ses seçimi dokunmaz; kaynak değişince aynı dil aranır. */
    private var userAudioChosen = false
    private var userAudioLanguage: String? = null
    private var preferDub = true

    /** Sonraki bölüme geçiliyor: bu bölüm "izlendi" yazılır, oynatıcı hataları (kapanan torrent vb.) gösterilmez. */
    private var leaving = false
    private var finishedForAdvance = false

    /** CloudStream dizilerinde sıradaki bölümün dataUrl adresi. */
    private var nextCsDataUrl: String? = null

    /** Kaynak chapter metadata’sı ve TheIntroDB’den gelen, süre kurallarıyla doğrulanacak işaretler. */
    private val skipMarkers = mutableListOf<SkipMarker>()
    private val externalSkipMarkers = mutableListOf<SkipMarker>()
    private var skipSegments by mutableStateOf<List<SkipSegment>>(emptyList())
    private var activeSkipSegments by mutableStateOf<List<SkipSegment>>(emptyList())
    private val dismissedSkipSegments = mutableSetOf<String>()
    private val autoSkippedSegments = mutableSetOf<String>()
    private var skipPlaybackSettings = SkipPlaybackSettings.Default
    private var openCreditsPrefetchStarted = false
    private var knownDurationMs = 0L
    private var streamGeneration = 0
    /** İlk resume seek'i uygulanırken eski intro/özet pencerelerinin görünmesini engeller. */
    private var skipResumePositionMs = 0L
    /** Ekranda gösterilen video surface; jenerik karesi yalnızca cihaz üzerinde analiz edilir. */
    private var videoTextureView: TextureView? = null
    private var creditsRollDetector: CreditsRollDetector? = null
    private var creditsNextVisible by mutableStateOf(false)
    private var creditsLookupStarted = false
    private var creditsDetectedAtMs = -1L
    private var creditsGeneration = 0
    private var creditsLookupJob: Job? = null

    /** Dikey kaydırmayla ses/parlaklık; dokunmatik olmayan cihazlarda (TV/kumanda) null. */
    private var levels: DeviceLevels? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val parsed = PlaySession.from(intent)
        if (parsed == null) {
            Log.e("PlayerActivity", "PlaySession parsed is null, finishing! raw session extra: ${intent.getStringExtra("session")}")
            finish()
            return
        }
        session = parsed
        skipResumePositionMs = parsed.startPositionMs
        if (AutoNextRules.canShowNextEpisodeButton(
                parsed.target.request.type,
                parsed.target.request.season,
                parsed.target.request.episode,
                parsed.target.request.addonType,
                1L,
            )
        ) {
            creditsRollDetector = CreditsRollDetector()
        }
        if (parsed.fromAutoNext) noTransition(open = true)
        val container = (application as PlayApp).container

        volumeControlStream = AudioManager.STREAM_MUSIC
        if (DeviceLevels.supportsTouch(this)) {
            levels = DeviceLevels(window, getSystemService(AUDIO_SERVICE) as AudioManager, contentResolver)
        }

        val offline = session.offline
        // Torrent motoru bu oynatıcı kapanana dek açık kalır (kapanınca geçici dosyalar silinir).
        try {
            container.torrentEngine.retain()
        } catch (t: Throwable) {
            Log.w("PlayerActivity", "torrentEngine.retain() hatası (zırhlandı): ${t.message}", t)
        }
        val bigBuffer = if (BuildConfig.DEBUG) {
            PlayerNoticeLogic.testForceStall = intent.getBooleanExtra("test_force_stall", PlayerNoticeLogic.testForceStall)
            intent.getBooleanExtra("test_big_buffer", PlayerNoticeLogic.testBigBuffer)
        } else false
        controller = PlaybackController(
            this, container.httpClient,
            offlineCache = offline?.let { container.downloads.cache },
            torrentUrl = container.torrentServer::urlFor,
            bigBuffer = bigBuffer,
        )
        lifecycleScope.launch {
            container.settings.skipPlayback.collect { settings -> skipPlaybackSettings = settings }
        }
        controller.onMetadata = { metadata ->
            val incoming = SkipSegmentDetector.markersFromMetadata(metadata)
            if (incoming.isNotEmpty()) {
                skipMarkers.removeAll { old -> incoming.any { it.title == old.title && it.startMs == old.startMs && it.endMs == old.endMs } }
                skipMarkers += incoming
                rebuildSkipSegments()
            }
        }
        controller.onDurationKnown = { duration ->
            coordinator?.onDuration(duration)
            knownDurationMs = duration
            rebuildSkipSegments()
            val request = session.target.request
            val generation = streamGeneration
            lifecycleScope.launch {
                val incoming = (application as PlayApp).container.skipSegmentProvider.timestamps(request, duration)
                    .map { timestamp -> SkipMarker(timestamp.type.markerTitle, timestamp.startMs, timestamp.endMs, timestamp.openEndedEnd) }
                if (generation != streamGeneration || leaving || knownDurationMs != duration || incoming.isEmpty()) return@launch
                externalSkipMarkers.clear()
                externalSkipMarkers += incoming
                rebuildSkipSegments()
            }
        }
        controller.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveProgress() // duraklatma
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    saveProgress() // konum süreye eşitlenir: izlendi
                    // Dizide sonraki bölüm varsa geçişi kart üstlenir (kapanmaz); yoksa eskisi gibi kapan.
                    if (!autoNext.onEnded()) finish()
                }
            }
        })
        autoNext = AutoNextController(
            scope = lifecycleScope,
            target = session.target,
            config = { AutoNextConfig(container.settings.autoNext.first(), container.settings.autoNextSeconds.first()) },
            findNext = ::findNextEpisode,
            prepare = ::prepareNext,
            onAdvance = ::advanceTo,
            onNoNext = {
                Toast.makeText(this@PlayerActivity, R.string.player_no_next_episode, Toast.LENGTH_SHORT).show()
            },
        )
        autoNext.start()
        subtitles = SubtitleController(
            scope = lifecycleScope,
            playback = controller,
            request = session.target.request,
            // İndirilmiş kopya: altyazılar yalnızca indirilenler (yerel dosya), altyazı addon'larına ve çevrimiçi aramaya
            // sorulmaz; kullanıcının dosyadan eklediği altyazı yine okunur.
            loader = SubtitleDiag.loader(
                if (offline != null) {
                    val downloaded = DownloadedSubtitleLoader(offline.subtitleFiles)
                    TrackLoader { track ->
                        if (SubtitleLoader.isLocal(track.url)) container.subtitleLoader.load(track) else downloaded.load(track)
                    }
                } else {
                    container.translatedSubtitles.loader(container.subtitleLoader)
                },
            ),
            memory = container.subtitleMemory,
            provider = if (offline != null) SubtitleProvider { emptyFlow() } else SubtitleDiag.provider(container.subtitleProvider),
            preferredLanguage = { container.settings.subtitleLanguage.first() },
            persist = container.appScope,
            // İndirilmiş kopyada çevrimiçi altyazı da aranmaz; otomatik çeviri yalnızca çevrimiçi izlemede.
            translation = if (offline == null) container.translationEngine else null,
            translationStore = if (offline == null) container.translatedSubtitles else null,
        )
        subtitles.start()
        // Teşhis (yalnızca debug): videonun içindeki parçalar ve geçerli seçim.
        lifecycleScope.launch {
            snapshotFlow { subtitles.items.filterIsInstance<SubtitleItem.Embedded>().map { it.language } }
                .collect { SubtitleDiag.d("videonun içindeki altyazılar: $it") }
        }
        lifecycleScope.launch {
            snapshotFlow { subtitles.selected }.collect { item ->
                SubtitleDiag.d(
                    when (item) {
                        null -> "seçim: kapalı"
                        is SubtitleItem.Embedded -> "seçim: videonun içindeki parça ${item.index} dil=${item.language}"
                        is SubtitleItem.External -> {
                            val origin = if (item.track.kind == SubtitleKind.Local) "dosya" else item.track.origin
                            "seçim: ${item.track.kind} $origin dil=${item.language}"
                        }
                    },
                )
            }
        }
        // Ses parçaları değişince (kaynak yüklendi / kaynak değişti): Türkçe ses otomatik seçimi ve elle seçimin korunması.
        lifecycleScope.launch {
            snapshotFlow { controller.ui.tracksVersion }.collect { syncAudio() }
        }

        if (offline == null) {
            // Ayarlar: tek seferlik okuma (milisaniyeler); oynatma başlamadan önce bilinmeli.
            val settings = container.settings
            val autoSource = runBlocking { settings.autoSource.first() }
            preferDub = runBlocking { settings.preferDub.first() }
            val sources = SourceCoordinator(
                scope = lifecycleScope,
                request = session.target.request,
                streamsFor = container.streams::streams,
                askedFor = container.streams::sources,
                preferenceFor = container.sourceMemory::get,
                remember = container.sourceMemory::remember,
                sizeProbe = container.sizeProbe::size,
                runtimeFor = ::expectedRuntimeMinutes,
                verdicts = container.streamVerdicts,
                maxHeight = DeviceCodecs.maxDecodableHeight(),
                preferDub = preferDub,
                autoSource = autoSource,
                load = ::loadStream,
                position = { controller.positionMs() },
                clock = SystemClock::elapsedRealtime,
                sourceProbe = { stream ->
                    container.sourceProbe.probe(stream.url, stream.headers, timeoutMs = 2_000L)
                },
                sourceDispatcher = Dispatchers.IO.limitedParallelism(16),
            )
            coordinator = sources
            controller.onFirstFrame = {
                sources.onFirstFrame()
                if (BuildConfig.DEBUG && PlayerNoticeLogic.testForceStall) {
                    PlayerNoticeLogic.testForceStall = false
                    lifecycleScope.launch {
                        delay(5_000L)
                        if (!leaving && session.offline == null && coordinator?.phase == SourcePhase.Playing) {
                            coordinator?.forceStallForTest(11_000L)
                        }
                    }
                }
            }
            controller.onFailure = { detail -> leaving || sources.onFailure(detail) }
            // Arabelleğe alma sırasında takılma analizi (500 ms aralıkla)
            lifecycleScope.launch {
                snapshotFlow { controller.ui.buffering }
                    .collectLatest { buffering ->
                        if (!buffering || leaving || session.offline != null) return@collectLatest
                        while (isActive) {
                            delay(500)
                            val coord = coordinator ?: break
                            val tracker = controller.stallTracker
                            coord.checkStall(
                                firstFrameRendered = tracker.firstFrameRendered,
                                isSeeking = tracker.isSeeking,
                                currentStallStartMs = tracker.currentStallStartMs,
                                pastStalls = tracker.pastStalls,
                            )
                        }
                    }
            }
            // Torrent açılınca dosya boyutu panelde ve üst çubukta görünsün.
            container.torrentServer.onOpened = { magnet, size ->
                runOnUiThread { sources.reportSize(magnet, size) }
            }
            sources.start(
                initial = if (session.autoSource) null else session.stream,
                fallbacks = session.fallbacks,
                startMs = session.startPositionMs,
            )
        } else {
            val stream = session.stream
            if (stream != null) {
                currentStream = stream
                subtitles.setStream(stream)
            }
            lifecycleScope.launch {
                val item = container.downloads.mediaItem(offline.downloadId)
                if (item == null) controller.markUnavailable() else controller.loadDownloaded(item, session.startPositionMs)
            }
        }

        setContent {
            PlayTheme(darkTheme = true) {
                val isSeries = AutoNextRules.canShowNextEpisodeButton(session.target.request.type, session.target.request.season, session.target.request.episode, session.target.request.addonType, 1L)
                PlayerScreen(
                    controller = controller,
                    subtitles = subtitles,
                    target = session.target,
                    showResumeNotice = session.autoResumed && session.startPositionMs > 0,
                    canSwitchSource = offline == null,
                    levels = levels,
                    autoNext = autoNext,
                    onAutoNextCancel = ::cancelAutoNext,
                    onNextEpisode = if (isSeries) ::onNextEpisodeClicked else null,
                    onVideoTextureView = { videoTextureView = it },
                    creditsNextVisible = creditsNextVisible,
                    skipSegments = activeSkipSegments,
                    onSkipSegment = ::skipSegment,
                    onRestartFromBeginning = { skipResumePositionMs = 0L },
                    coordinator = coordinator,
                    offlineStream = if (offline != null) session.stream else null,
                    embeddedDub = embeddedDub,
                    onAudioChosen = ::onAudioChosen,
                    onDownload = { stream, notificationsOff ->
                        lifecycleScope.launch {
                            val message = when (val result = container.downloads.start(session.target, stream)) {
                                is StartResult.Started -> getString(
                                    if (notificationsOff) R.string.download_notifications_off else R.string.download_started,
                                )
                                is StartResult.AlreadyDownloaded -> getString(R.string.download_already)
                                is StartResult.NotDownloadable -> getString(
                                    if (result.reason == Probe.Reason.Network) R.string.download_unreachable else R.string.download_unavailable,
                                )
                                is StartResult.NoSpace -> getString(
                                    R.string.download_no_space,
                                    Formatter.formatShortFileSize(this@PlayerActivity, result.neededBytes),
                                    Formatter.formatShortFileSize(this@PlayerActivity, result.freeBytes),
                                )
                            }
                            Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onBack = ::finish,
                )
            }
        }

        // Window couldn't find content container view / ViewGroup.getChildAt çökmelerini önlemek için
        // pencere ayarları ve immersive mode setContent sonrasında window.decorView.post içine ve try-catch ile zırhlanır.
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window.decorView.post {
                try {
                    enterImmersive()
                } catch (t: Throwable) {
                    Log.e("PlayerActivity", "Pencere ve immersive ayarları uygulanamadı (decorView.post): ${t.message}", t)
                }
            }
        } catch (t: Throwable) {
            Log.e("PlayerActivity", "Pencere ayarları uygulanamadı (zırhlandı): ${t.message}", t)
        }

        // Oynatma sürerken konumu düzenli kaydet; sadece ekran görünürken çalışır.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(WatchRules.SAVE_INTERVAL_MS)
                    if (controller.ui.isPlaying) saveProgress()
                }
            }
        }
        // Sonraki bölüm kartı: yarım saniyede bir konuma bakar (geri sayım yalnızca oynarken işler).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(AUTO_NEXT_TICK_MS)
                    val position = controller.positionMs()
                    val duration = controller.durationMs()
                    if (skipResumePositionMs > 0L && position >= (skipResumePositionMs - RESUME_SEEK_TOLERANCE_MS).coerceAtLeast(0L)) {
                        skipResumePositionMs = 0L
                    }
                    autoNext.tick(
                        nowMs = SystemClock.elapsedRealtime(),
                        positionMs = position,
                        durationMs = duration,
                        playing = controller.ui.isPlaying,
                        ended = controller.ui.ended,
                    )
                    val active = SkipSegmentDetector.activeSegments(
                        segments = skipSegments.filter { skipPlaybackSettings.isEnabled(it.category) },
                        positionMs = position,
                        dismissed = dismissedSkipSegments,
                        resumePositionMs = skipResumePositionMs,
                    )
                    val autoSkip = SkipSegmentDetector.autoSkipCandidate(
                        active = active,
                        enabled = skipPlaybackSettings.autoSkip,
                        playing = controller.ui.isPlaying,
                        attemptedThisSession = autoSkippedSegments,
                    )
                    if (autoSkip != null) {
                        autoSkippedSegments += autoSkip.key
                        skipSegment(autoSkip)
                    }
                    val displaySegments = if (autoSkip == null) active else active.filterNot { it.key == autoSkip.key }
                    if (activeSkipSegments.map { it.key } != displaySegments.map { it.key }) activeSkipSegments = displaySegments
                    if (active.any { it.kind == SkipSegmentKind.Outro && it.openEndedEnd } && !openCreditsPrefetchStarted) {
                        openCreditsPrefetchStarted = true
                        lifecycleScope.launch { autoNext.prepareManualNext() }
                    }
                }
            }
        }
        if (creditsRollDetector != null) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        delay(CREDITS_SCAN_INTERVAL_MS)
                        val detector = creditsRollDetector ?: continue
                        val position = controller.positionMs()
                        val duration = controller.durationMs()
                        if (duration < MIN_CREDITS_DETECTION_DURATION_MS) {
                            detector.reset()
                            continue
                        }

                        if (creditsDetectedAtMs > 0L && position < creditsDetectedAtMs - CREDITS_SEEK_BACK_RESET_MS) {
                            creditsLookupJob?.cancel()
                            creditsLookupJob = null
                            creditsNextVisible = false
                            creditsLookupStarted = false
                            creditsDetectedAtMs = -1L
                            creditsGeneration++
                            detector.reset()
                        }
                        if (creditsLookupStarted) continue

                        val activeSegment = SkipSegmentDetector.active(
                            segments = skipSegments,
                            positionMs = position,
                            dismissed = dismissedSkipSegments,
                            resumePositionMs = skipResumePositionMs,
                        )
                        val metadataCredits = activeSegment?.kind == SkipSegmentKind.Outro
                        if (!metadataCredits && (!controller.ui.isPlaying || position < duration * CREDITS_SCAN_START_FRACTION)) {
                            if (position < duration * CREDITS_SCAN_START_FRACTION) detector.reset()
                            continue
                        }

                        val creditsStarted = metadataCredits || detector.inspect(videoTextureView)
                        if (creditsStarted) {
                            creditsLookupStarted = true
                            creditsDetectedAtMs = position
                            val generation = creditsGeneration
                            creditsLookupJob = lifecycleScope.launch {
                                if (autoNext.prepareManualNext() && generation == creditsGeneration && !leaving) {
                                    creditsNextVisible = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Dönüşte (ör. başka uygulamadan) kontroller açık gelir; oynatma kullanıcıya bırakılır.
        try {
            enterImmersive()
        } catch (t: Throwable) {
            Log.e("PlayerActivity", "onStart enterImmersive hatası (zırhlandı): ${t.message}", t)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!::controller.isInitialized) return
        controller.player.pause()
        saveProgress()
        subtitles.flush()
    }

    /** Çıkışta pencere parlaklığı bırakılır; sistem değeri geçerli olur. */
    override fun finish() {
        levels?.restoreBrightness()
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        creditsLookupJob?.cancel()
        creditsRollDetector?.close()
        creditsRollDetector = null
        videoTextureView = null
        levels?.restoreBrightness()
        if (::controller.isInitialized) {
            controller.release()
            val container = (application as PlayApp).container
            try {
                container.torrentServer.onOpened = null
                container.torrentEngine.release()
            } catch (t: Throwable) {
                Log.w("PlayerActivity", "torrentEngine.release() hatası (zırhlandı): ${t.message}", t)
            }
        }
    }

    // ---- kaynak ----

    /**
     * [SourceCoordinator] kaynağı oynatıcıya verir (ilk başlangıç, otomatik geçiş, elle seçim, geri dönüş). Torrent kaynaktan başka
     * kaynağa geçilirken eski torrent oturumu kapatılır; altyazı hafızası ve ses seçimi korunur (dublajlı kaynakta otomatik altyazı yok).
     */
    private fun loadStream(stream: PlayableStream, startMs: Long) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread { loadStream(stream, startMs) }
            return
        }
        if (BuildConfig.DEBUG && PlayerNoticeLogic.simulateSkipOnce) {
            PlayerNoticeLogic.simulateSkipOnce = false
            coordinator?.onFailure("TEST_SIMULATED_ERROR (500)")
            return
        }
        val previous = currentStream
        if (previous != null && previous.url != stream.url && TorrentLinks.isMagnet(previous.url)) {
            try {
                appContainer.torrentEngine.closeActive()
            } catch (t: Throwable) {
                Log.w("PlayerActivity", "torrentEngine.closeActive() hatası (zırhlandı): ${t.message}", t)
            }
        }
        streamGeneration++
        creditsGeneration++
        creditsLookupJob?.cancel()
        creditsLookupJob = null
        creditsNextVisible = false
        creditsLookupStarted = false
        creditsDetectedAtMs = -1L
        creditsRollDetector?.reset()
        currentStream = stream
        if (skipResumePositionMs > 0L) skipResumePositionMs = startMs
        knownDurationMs = 0L
        skipMarkers.clear()
        externalSkipMarkers.clear()
        skipSegments = emptyList()
        activeSkipSegments = emptyList()
        openCreditsPrefetchStarted = false
        dismissedSkipSegments.clear()
        embeddedDub = false
        // Dublajlı kaynakta Türkçe altyazı gereksiz: otomatik seçim kapanır (kullanıcının elle seçtiğine dokunulmaz).
        if (stream.dubbed) subtitles.suppressAuto() else subtitles.resumeAuto()
        subtitles.setStream(stream)
        if (previous != null) saveProgress()
        controller.load(stream, startMs)
    }

    /** Ses parçası listesi değişti: elle seçilen dil korunur; yoksa (Türkçe dublaj önceliği açıkken) Türkçe ses seçilir. */
    private fun syncAudio() {
        val tracks = controller.audioTracks()
        if (tracks.isEmpty()) {
            embeddedDub = false
            return
        }
        fun lang(t: AudioTrack) = SubtitleLanguages.normalize(t.language)
        if (userAudioChosen) {
            val wanted = userAudioLanguage
            val match = if (wanted != null) tracks.firstOrNull { lang(it) == wanted } else null
            if (match != null && !match.selected) controller.selectAudio(match.index)
        } else if (preferDub) {
            val turkish = tracks.firstOrNull { lang(it) == "tr" }
            if (turkish != null && !turkish.selected) controller.selectAudio(turkish.index)
        }
        val selected = tracks.firstOrNull { it.selected }
        embeddedDub = selected != null && lang(selected) == "tr" && (tracks.size > 1 || currentStream?.dubbed == true)
        // Türkçe ses seçiliyken Türkçe altyazıya gerek yok; yoksa otomatik altyazı yeniden açılır.
        if (embeddedDub) subtitles.suppressAuto() else if (currentStream?.dubbed != true) subtitles.resumeAuto()
    }

    /** Kullanıcı sesi Parçalar panelinden seçti: dil hatırlanır (kaynak değişince aynı dil aranır), otomatik seçim bir daha karışmaz. */
    private fun onAudioChosen(index: Int) {
        val track = controller.audioTracks().firstOrNull { it.index == index } ?: return
        userAudioChosen = true
        userAudioLanguage = SubtitleLanguages.normalize(track.language)
        controller.selectAudio(index)
    }

    // ---- otomatik sonraki bölüm ----

    private val appContainer get() = (application as PlayApp).container

    private fun onNextEpisodeClicked() {
        if (leaving) return
        creditsNextVisible = false
        autoNext.skipToNext()
    }

    /** Kullanıcı onayladığında algılanan intro/özet/outro bölümünün sonuna seek eder. */
    private fun skipSegment(segment: SkipSegment) {
        dismissedSkipSegments += segment.key
        activeSkipSegments = activeSkipSegments.filterNot { it.key == segment.key }
        lifecycleScope.launch {
            if (segment.kind == SkipSegmentKind.Outro && segment.openEndedEnd && autoNext.prepareManualNext()) {
                autoNext.skipToNext()
            } else {
                controller.seekTo(segment.endMs)
            }
        }
    }

    private fun rebuildSkipSegments() {
        if (!::session.isInitialized) return
        val request = session.target.request
        val localSegments = SkipSegmentDetector.detect(
            type = request.type,
            durationMs = knownDurationMs,
            markers = skipMarkers,
            addonType = request.addonType,
        )
        val localKinds = localSegments.mapTo(mutableSetOf()) { it.kind }
        val externalSegments = SkipSegmentDetector.detect(
            type = request.type,
            durationMs = knownDurationMs,
            markers = externalSkipMarkers,
            addonType = request.addonType,
        ).filter { it.kind !in localKinds }
        skipSegments = (localSegments + externalSegments)
            .distinctBy { it.key }
            .filter { it.key !in dismissedSkipSegments }
    }

    /**
     * CloudStream eklentisiyle oynatılan dizide sıradaki (sezon, bölüm, dataUrl).
     */
    private suspend fun findNextCloudStreamEpisode(): Triple<Int, Int, String>? {
        val r = session.target.request
        val pluginId = r.pluginId ?: return null
        val contentUrl = r.contentUrl ?: return null
        val next = appContainer.csNextEpisodes.next(pluginId, contentUrl, r.dataUrl, r.season, r.episode) ?: return null
        return Triple(next.season, next.episode, next.dataUrl)
    }

    /**
     * İçerik doğrulaması için TMDB süresi (dk): film süresi; dizide önce o bölümün süresi, yoksa dizinin bölüm süresi.
     * Ağ/TMDB hatasında null: süre kontrolü yapılmaz (kaynak elenmez).
     */
    private suspend fun expectedRuntimeMinutes(request: com.kaan.play.data.stream.StreamRequest): Int? = runCatching {
        val detail = appContainer.tmdb.detail(request.type, request.tmdbId)
        val season = request.season
        val episode = request.episode
        if (request.type == MediaType.Tv && season != null && episode != null) {
            appContainer.tmdb.season(request.tmdbId, season).episodes.firstOrNull { it.number == episode }?.runtimeMinutes
                ?: detail.runtimeMinutes
        } else {
            detail.runtimeMinutes
        }
    }.getOrNull()

    /**
     * Bu bölümden sonraki (sezon, bölüm). Çevrimiçi: TMDB bölüm listesinden (aynı sezonun sonraki bölümü, bitince sonraki
     * sezonun ilki; yayımlanmamış/yok = son bölüm, null). İndirilmiş kopya: internet olmadığı için yalnızca indirilmiş bölümlerden.
     */
    private suspend fun findNextEpisode(): Pair<Int, Int>? {
        val r = session.target.request
        val season = r.season ?: return null
        val episode = r.episode ?: return null
        if (r.pluginId != null && !r.contentUrl.isNullOrBlank()) {
            val csNext = findNextCloudStreamEpisode()
            if (csNext != null) {
                nextCsDataUrl = csNext.third
                return csNext.first to csNext.second
            }
        }
        // Eklenti içeriğinin TMDB kimliği yoktur (eksi sayılı yerel kimlik): TMDB'ye sorulmaz.
        if (session.offline == null) return if (r.tmdbId > 0) appContainer.nextEpisodes.next(r.tmdbId, season, episode) else null
        return appContainer.downloads.rows.first()
            .filter {
                it.tmdbId == r.tmdbId && it.type == MediaType.Tv && it.status == DownloadStatus.Completed &&
                    (it.season > season || (it.season == season && it.episode > episode))
            }
            .minWithOrNull(compareBy({ it.season }, { it.episode }))
            ?.let { it.season to it.episode }
    }

    /**
     * Sonraki bölümü oynatmaya hazırlar: indirilmişse o kopya; yoksa izlenen eklentiyle aynısı önce olacak biçimde sıralı kaynaklar
     * ([AutoNextRules.order]). Hiç kaynak yoksa null (kart "Başka kaynak seç" der).
     */
    private suspend fun prepareNext(season: Int, episode: Int): NextPlayback? {
        val dataUrl = if (session.target.request.pluginId != null) {
            nextCsDataUrl ?: findNextCloudStreamEpisode()?.also { nextCsDataUrl = it.third }?.third ?: session.target.request.dataUrl
        } else session.target.request.dataUrl
        val request = session.target.request.copy(
            season = season,
            episode = episode,
            dataUrl = dataUrl,
            refresh = false,
        )
        val target = session.target.copy(request = request)
        appContainer.downloads.completedFor(request)?.let { return NextPlayback.Offline(target, it) }
        if (session.offline != null) return null
        val playing = currentStream
        val preferredAddonId = playing?.source?.addonId
        val found = AutoNextRules.collect(appContainer.streams.streams(request), preferredAddonId)
        val ordered = if (playing != null) AutoNextRules.order(found, playing) else found.ranked().take(AutoNextRules.MAX_CANDIDATES)
        return if (ordered.isEmpty()) null else NextPlayback.Online(target, ordered)
    }

    /**
     * Sonraki bölümü yeni bir oynatıcı etkinliğinde açar (altyazı, başlık, geçmiş hepsi o bölüm için sıfırdan kurulur) ve bunu
     * kapatır. Bu bölüm "izlendi" yazılır; önceki torrent oturumu kapatılır; kayıtlı konumu varsa oradan devam edilir.
     */
    private fun advanceTo(next: NextPlayback) {
        if (leaving) return
        leaving = true
        lifecycleScope.launch {
            val container = appContainer
            val saved = container.library.progress(next.target)
            val resumeAt = saved?.takeIf {
                WatchRules.isResumable(it.positionMs, it.durationMs) && container.settings.alwaysResume.first()
            }?.positionMs ?: 0L
            val nextSession = when (next) {
                is NextPlayback.Online -> PlaySession(
                    target = next.target,
                    stream = next.streams.first(),
                    startPositionMs = resumeAt,
                    autoResumed = resumeAt > 0,
                    fallbacks = next.streams.drop(1),
                    fromAutoNext = true,
                )
                is NextPlayback.Offline -> PlaySession.offline(next.download, resumeAt, autoResumed = resumeAt > 0, fromAutoNext = true)
            }
            finishedForAdvance = true
            saveProgress() // bu bölüm izlendi
            if (currentStream?.url?.let(TorrentLinks::isMagnet) == true) {
                try {
                    container.torrentEngine.closeActive()
                } catch (t: Throwable) {
                    Log.w("PlayerActivity", "torrentEngine.closeActive() hatası (zırhlandı): ${t.message}", t)
                }
            }
            startActivity(nextSession.toIntent(this@PlayerActivity))
            noTransition(open = false)
            finish()
        }
    }

    /** Kartta "İptal"/Geri: kart kapanır; video zaten bitmişse eskisi gibi oynatıcı da kapanır. */
    private fun cancelAutoNext() {
        autoNext.cancel()
        if (controller.ui.ended) finish()
    }

    /** Etkinlik geçişi animasyonsuz: bölümden bölüme geçiş kesintisiz görünür. */
    private fun noTransition(open: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                if (open) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE,
                0,
                0,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    /** Duraklatma/bitiş anlarında da çağrılır: bitişte (ya da sonraki bölüme geçerken) konum süreye eşitlenir ("izlendi"). */
    private fun saveProgress() {
        if (session.noHistory) return
        if (session.target.request.addonType in listOf("tv", "channel")) return
        val duration = controller.durationMs()
        if (duration <= 0 || controller.ui.error != null) return
        val position = if (controller.ui.ended || finishedForAdvance) duration else controller.positionMs()
        val container = (application as PlayApp).container
        // Etkinlik kapanırken de tamamlansın diye uygulama kapsamında.
        container.appScope.launch { container.library.saveProgress(session.target, position, duration) }
    }

    private fun enterImmersive() {
        try {
            window.decorView.post {
                try {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        window.attributes = window.attributes.apply {
                            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        }
                    }
                    val decor = window.decorView
                    WindowInsetsControllerCompat(window, decor).apply {
                        hide(WindowInsetsCompat.Type.systemBars())
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } catch (t: Throwable) {
                    Log.e("PlayerActivity", "enterImmersive decorView.post hatası (zırhlandı): ${t.message}", t)
                }
            }
        } catch (t: Throwable) {
            Log.e("PlayerActivity", "enterImmersive hatası (zırhlandı): ${t.message}", t)
        }
    }

    private companion object {
        const val AUTO_NEXT_TICK_MS = 500L
        const val RESUME_SEEK_TOLERANCE_MS = 10_000L
        const val CREDITS_SCAN_INTERVAL_MS = 1_500L
        const val MIN_CREDITS_DETECTION_DURATION_MS = 5 * 60_000L
        const val CREDITS_SCAN_START_FRACTION = 0.5
        const val CREDITS_SEEK_BACK_RESET_MS = 15_000L
    }
}
