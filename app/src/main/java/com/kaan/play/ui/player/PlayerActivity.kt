package com.kaan.play.ui.player

import android.media.AudioManager
import com.kaan.play.BuildConfig
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.kaan.play.PlayApp
import com.kaan.play.StartupGuardRules
import com.kaan.play.StartupWindowGuard
import com.kaan.play.R
import com.kaan.play.data.db.DownloadStatus
import com.kaan.play.data.download.DownloadedSubtitleLoader
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.AgeRating
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.player.AudioTrackLearning
import com.kaan.play.data.player.AudioTrackRules
import com.kaan.play.data.player.TurkishAudioNotice
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.data.player.TurkishAudioTrackRules
import com.kaan.play.data.player.TurkishAudioVerifier
import com.kaan.play.data.player.TurkishVerifyDecision
import com.kaan.play.data.provider.LiveCatalogRules
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.provider.LiveRoutePlanner
import com.kaan.play.data.provider.LiveSourceRoute
import com.kaan.play.data.provider.LiveZapChannel
import com.kaan.play.data.provider.LiveZapListHolder
import com.kaan.play.data.provider.LiveZapListSource
import com.kaan.play.data.provider.LiveZapRequestSequencer
import com.kaan.play.data.provider.LiveZapRules
import com.kaan.play.data.provider.LiveZapTicket
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
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
    private var coordinator by mutableStateOf<SourceCoordinator?>(null)
    private var activeTarget by mutableStateOf<PlayTarget?>(null)
    private var resolvedAgeRating by mutableStateOf<AgeRating?>(null)
    private var sessionDetailKey: Pair<MediaType, Int>? = null
    private var sessionDetailDeferred: Deferred<MediaDetail?>? = null

    /** Şu an oynayan akış (kaynak değişince güncellenir); sonraki bölümde "aynı eklenti" bundan bulunur. */
    private var currentStream: PlayableStream? = null

    /** Seçili ses parçası Türkçe mi (dosyanın içindeki dublaj): üst çubukta/panelde "Dublajlı (gömülü ses)". */
    private var embeddedDub by mutableStateOf(false)

    /** Kullanıcı sesi elle seçtiyse otomatik Türkçe ses seçimi dokunmaz; kaynak değişince aynı dil aranır. */
    private var userAudioChosen = false
    private var userAudioLanguage: String? = null
    private var preferDub = true
    private var tryUntilTurkishAudio = SettingsRepository.DEFAULT_TRY_SOURCES_UNTIL_TURKISH_AUDIO
    private var showDubSuggestionSetting = SettingsRepository.DEFAULT_SHOW_DUB_SUGGESTION
    private var revertOnSelectedFailureSetting = SettingsRepository.DEFAULT_REVERT_ON_SELECTED_FAILURE
    private var audioProbeTimeoutSeconds = SettingsRepository.DEFAULT_AUDIO_PROBE_TIMEOUT_SECONDS
    private lateinit var audioVerifier: TurkishAudioVerifier
    private var audioVerificationScanning by mutableStateOf(false)
    private var audioVerificationNotice by mutableStateOf<TurkishAudioNotice?>(null)
    private var pendingDubRevertStream: PlayableStream? = null
    private var audioTrackBanner by mutableStateOf<String?>(null)
    private var unknownAudioHintStreamUrl: String? = null
    private var learnedAppliedForUrl: String? = null
    private var pendingAudioLearnJob: Job? = null
    private var slowConnectionPromptVisible by mutableStateOf(false)
    private var slowConnectionPromptedSession = false
    private var slowConnectionModeState = SettingsRepository.DEFAULT_SLOW_CONNECTION_MODE
    private var suggestSlowConnectionState = SettingsRepository.DEFAULT_SUGGEST_SLOW_CONNECTION

    /** Sonraki bölüme geçiliyor: bu bölüm "izlendi" yazılır, oynatıcı hataları (kapanan torrent vb.) gösterilmez. */
    private var leaving = false
    private var finishedForAdvance = false

    /** CloudStream dizilerinde sıradaki bölümün dataUrl adresi. */
    private var nextCsDataUrl: String? = null

    /** Dikey kaydırmayla ses/parlaklık; dokunmatik olmayan cihazlarda (TV/kumanda) null. */
    private var levels: DeviceLevels? = null

    /** İntro/özet/jenerik/önizleme atlama düğmeleri ve isteğe bağlı otomatik atlama (TheIntroDB + chapter işaretleri). */
    private lateinit var skips: SkipController

    private var liveZapKey: String? = null
    private var liveZapChannels: List<LiveZapChannel> = emptyList()
    private var currentZapIndex by mutableIntStateOf(-1)
    private var canZapChannels by mutableStateOf(false)
    private var pendingZapIndex: Int? = null
    private val zapSequencer = LiveZapRequestSequencer()
    private var pendingZapTicket: LiveZapTicket? = null
    private var zapJob: Job? = null
    private var bannerJob: Job? = null
    private var noticeJob: Job? = null
    private var liveChannelBanner by mutableStateOf<String?>(null)
    private var liveZapSwitching by mutableStateOf(false)
    private var liveNotice by mutableStateOf<String?>(null)
    private var playbackLocked = false
    private var lastZapConsumedDownTime = -1L
    private var activeZapRollback: ZapRollback? = null
    private var autoSourceSetting = true
    private val sessionFailedRouteKeys = LinkedHashSet<String>()
    private var currentChannelRoutes: List<LiveSourceRoute> = emptyList()
    private var currentRouteIndex = 0
    private var allLiveRoutesExhausted by mutableStateOf(false)
    private var routeSwitchJob: Job? = null

    private data class ZapRollback(
        val previousSession: PlaySession,
        val previousStream: PlayableStream?,
        val previousIndex: Int,
        val previousRoutes: List<LiveSourceRoute>,
        val previousRouteIndex: Int,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val intentSafe = intent?.getBooleanExtra(StartupGuardRules.EXTRA_SAFE_WINDOW, false) ?: false
        val storedSafe = StartupGuardRules.isSafeWindow(this)
        if (StartupGuardRules.shouldUseSafeTheme(intentSafe, storedSafe)) {
            setTheme(R.style.Theme_Play_Safe)
        }
        super.onCreate(savedInstanceState)
        val incomingIntent = intent ?: run {
            finish()
            return
        }
        val parsed = parseSessionSafely(incomingIntent) ?: run {
            finish()
            return
        }
        session = parsed
        activeTarget = parsed.target
        if (parsed.fromAutoNext) noTransition(open = true)
        val incomingZapKey = PlaySession.liveZapKey(incomingIntent)
        liveZapKey = incomingZapKey
        val holderChannels = LiveZapListHolder.get(incomingZapKey) ?: emptyList()
        val zapCatalogId = PlaySession.liveCatalogId(incomingIntent)
        val zapSource = LiveZapRules.sourceFor(zapCatalogId, holderChannels.isNotEmpty())
        val isLiveSession = parsed.offline == null && LiveCatalogRules.isLiveRequest(parsed.target.request)
        liveZapChannels = if (isLiveSession) holderChannels else emptyList()
        val rawZapIdx = PlaySession.liveZapIndex(incomingIntent)
        currentZapIndex = when {
            !isLiveSession || liveZapChannels.isEmpty() -> -1
            rawZapIdx in liveZapChannels.indices -> rawZapIdx
            !parsed.liveChannelKey.isNullOrBlank() -> liveZapChannels.indexOfFirst { it.channelKey == parsed.liveChannelKey }
            else -> 0
        }
        canZapChannels = isLiveSession && zapSource != LiveZapListSource.Unavailable && liveZapChannels.isNotEmpty()
        currentChannelRoutes = if (isLiveSession) {
            liveZapChannels.getOrNull(currentZapIndex)?.routes?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(LiveRoutePlanner.routeFromRequest(parsed.target.request))
        } else emptyList()
        currentRouteIndex = 0
        allLiveRoutesExhausted = false
        if (isLiveSession) {
            showLiveChannelBanner(parsed.target.title, switching = false)
        }
        val container = (application as PlayApp).container
        if (!isLiveSession && parsed.offline == null) {
            val targetRating = parsed.target.ageRating?.takeIf { it.isNotBlank() }
            val targetRegion = parsed.target.ageRatingRegion?.takeIf { it.isNotBlank() }
            if (targetRating != null && targetRegion != null) {
                resolvedAgeRating = AgeRating(targetRating, targetRegion)
            } else if (parsed.target.request.tmdbId > 0) {
                lifecycleScope.launch {
                    resolvedAgeRating = sessionDetailAsync(parsed.target.request.type, parsed.target.request.tmdbId).await()?.ageRating
                }
            }
        }

        runCatching {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            volumeControlStream = AudioManager.STREAM_MUSIC
            if (DeviceLevels.supportsTouch(this)) {
                levels = DeviceLevels(window, getSystemService(AUDIO_SERVICE) as AudioManager, contentResolver)
            }
        }.onFailure { t ->
            android.util.Log.w("PlayerActivity", "Pencere bayrakları başlatma hatası: ${t.message}")
        }

        val offline = session.offline
        // Torrent motoru bu oynatıcı kapanana dek açık kalır (kapanınca geçici dosyalar silinir).
        container.torrentEngine.retain()
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
        skips = SkipController(
            scope = lifecycleScope,
            request = { session.target.request },
            provider = container.skipSegmentProvider,
            settings = container.settings.skipPlayback,
            seekTo = controller::seekTo,
            startPositionMs = session.startPositionMs,
        )
        skips.attach(controller.player)
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

        val settings = container.settings
        slowConnectionModeState = runBlocking { settings.slowConnectionMode.first() }
        suggestSlowConnectionState = runBlocking { settings.suggestSlowConnection.first() }
        lifecycleScope.launch { settings.slowConnectionMode.collect { slowConnectionModeState = it } }
        lifecycleScope.launch { settings.suggestSlowConnection.collect { suggestSlowConnectionState = it } }
        if (offline == null) {
            // Ayarlar: tek seferlik okuma (milisaniyeler); oynatma başlamadan önce bilinmeli.
            autoSourceSetting = runBlocking { settings.autoSource.first() }
            preferDub = runBlocking { settings.preferDub.first() }
            tryUntilTurkishAudio = runBlocking { settings.trySourcesUntilTurkishAudio.first() }
            showDubSuggestionSetting = runBlocking { settings.showDubSuggestion.first() }
            revertOnSelectedFailureSetting = runBlocking { settings.revertOnSelectedFailure.first() }
            audioProbeTimeoutSeconds = runBlocking { settings.audioProbeTimeoutSeconds.first() }
        }
        audioVerifier = TurkishAudioVerifier(scanTimeoutMs = audioProbeTimeoutSeconds * 1000L)

        if (offline == null) {
            val sources = createSourceCoordinator(session.target.request)
            coordinator = sources
            controller.onFirstFrame = {
                allLiveRoutesExhausted = false
                coordinator?.onFirstFrame()
                evaluateTurkishAudio()
                container.torrentEngine.onFirstFrame()
                val ticket = pendingZapTicket
                val targetZapIdx = pendingZapIndex
                if (targetZapIdx != null && (ticket == null || zapSequencer.isLatest(ticket))) {
                    currentZapIndex = targetZapIdx
                    pendingZapIndex = null
                    pendingZapTicket = null
                }
                activeZapRollback = null
                if (liveZapSwitching) {
                    liveZapSwitching = false
                    showLiveChannelBanner(session.target.title, switching = false)
                }
                if (!session.noHistory && LiveCatalogRules.isLiveRequest(session.target.request)) {
                    val channelKey = session.liveChannelKey
                        ?: LiveCatalogRules.channelGroupKey(session.target.title)
                    if (channelKey.isNotBlank()) {
                        lifecycleScope.launch { container.liveChannels.markWatched(channelKey) }
                    }
                }
                if (BuildConfig.DEBUG && intent.getBooleanExtra("play_test_slow_prompt", false) && !slowConnectionPromptedSession) {
                    intent.removeExtra("play_test_slow_prompt")
                    val isTorrent = currentStream?.url?.let(TorrentLinks::isMagnet) == true
                    if (offline == null && !isTorrent) {
                        slowConnectionPromptedSession = true
                        lifecycleScope.launch {
                            delay(1_200L)
                            if (!leaving) slowConnectionPromptVisible = true
                        }
                    }
                }
                if (BuildConfig.DEBUG && PlayerNoticeLogic.testForceStall) {
                    PlayerNoticeLogic.testForceStall = false
                    lifecycleScope.launch {
                        delay(5_000L)
                        if (!leaving && session.offline == null && coordinator?.phase == SourcePhase.Playing) {
                            coordinator?.forceStallForTest(11_000L)
                        }
                    }
                }
                if (BuildConfig.DEBUG) {
                    val failAfterMs = intent.getLongExtra("play_test_fail_after_ms", 0L)
                    val repeatsLeft = intent.getIntExtra("play_test_fail_repeats", 1)
                    if (failAfterMs > 0 && repeatsLeft > 0) {
                        intent.putExtra("play_test_fail_repeats", repeatsLeft - 1)
                        if (repeatsLeft - 1 <= 0) {
                            intent.removeExtra("play_test_fail_after_ms")
                        }
                        lifecycleScope.launch {
                            delay(failAfterMs)
                            if (!leaving && session.offline == null && coordinator?.phase == SourcePhase.Playing) {
                                android.util.Log.d("PlayMatch", "play_test_fail_after_ms tetiklendi ($failAfterMs ms, kalan=$repeatsLeft) -> ERROR_CODE_IO_NETWORK_CONNECTION_CLOSED (-1004)")
                                controller.player.stop()
                                controller.onFailure?.invoke("ERROR_CODE_IO_NETWORK_CONNECTION_CLOSED (-1004)")
                            }
                        }
                    }
                }
            }
            controller.onDurationKnown = { coordinator?.onDuration(it) }
            controller.onFailure = { detail -> leaving || (coordinator?.onFailure(detail) == true) }
            lifecycleScope.launch {
                snapshotFlow { Pair(coordinator, coordinator?.phase) }.collect { (coord, phase) ->
                    if (coord != null && phase == SourcePhase.Failed && !leaving) {
                        if (LiveCatalogRules.isLiveRequest(session.target.request)) {
                            handleLiveSourceCoordinatorFailed(coord)
                        } else if (activeZapRollback != null) {
                            revertFailedLiveZap()
                        }
                    }
                }
            }
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
                            val isTorrent = currentStream?.url?.let(TorrentLinks::isMagnet) == true
                            if (com.kaan.play.data.net.SlowConnectionPromptRules.shouldShowPrompt(
                                    suggestEnabled = suggestSlowConnectionState,
                                    slowModeEnabled = slowConnectionModeState,
                                    promptedThisSession = slowConnectionPromptedSession,
                                    isOffline = false,
                                    isTorrent = isTorrent,
                                    firstFrameRendered = tracker.firstFrameRendered,
                                    isSeeking = tracker.isSeeking,
                                    currentBufferingStartMs = tracker.currentStallStartMs,
                                    nowMs = tracker.now(),
                                )
                            ) {
                                slowConnectionPromptedSession = true
                                slowConnectionPromptVisible = true
                            }
                        }
                    }
            }
            // Türkçe ses izi doğrulama zaman aşımı ve iz değişikliklerini düzenli kontrol et.
            lifecycleScope.launch {
                snapshotFlow { Pair(audioVerificationScanning, controller.ui.firstFrameRendered) }
                    .collectLatest { (scanning, _) ->
                        if (!scanning || leaving) return@collectLatest
                        while (isActive && audioVerificationScanning && !leaving) {
                            evaluateTurkishAudio()
                            delay(400L)
                        }
                    }
            }
            // Torrent açılınca dosya boyutu panelde ve üst çubukta görünsün.
            container.torrentServer.onOpened = { magnet, size ->
                runOnUiThread { coordinator?.reportSize(magnet, size) }
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

        try {
            StartupWindowGuard.checkDebugFailSetContent(intent)
            setContent {
            PlayTheme(darkTheme = true) {
                val isSeries = AutoNextRules.canShowNextEpisodeButton(session.target.request.type, session.target.request.season, session.target.request.episode, session.target.request.addonType, 1L)
                PlayerScreen(
                    controller = controller,
                    subtitles = subtitles,
                    target = activeTarget ?: session.target,
                    showResumeNotice = session.autoResumed && session.startPositionMs > 0,
                    canSwitchSource = offline == null,
                    levels = levels,
                    autoNext = autoNext,
                    onAutoNextCancel = ::cancelAutoNext,
                    onNextEpisode = if (isSeries) ::onNextEpisodeClicked else null,
                    coordinator = coordinator,
                    initialStream = session.stream,
                    offlineStream = if (offline != null) session.stream else null,
                    embeddedDub = embeddedDub,
                    onAudioChosen = ::onAudioChosen,
                    skips = skips,
                    getTorrentLoadSnapshot = container.torrentEngine::getLoadSnapshot,
                    canZapChannels = canZapChannels,
                    onZapChannel = if (canZapChannels) ::zapChannel else null,
                    onZapKeyConsumed = { downTime -> lastZapConsumedDownTime = downTime },
                    liveChannelBanner = liveChannelBanner,
                    liveZapSwitching = liveZapSwitching,
                    liveNotice = liveNotice,
                    allLiveRoutesExhausted = allLiveRoutesExhausted,
                    onScanLiveProvider = if (offline == null && LiveCatalogRules.isLiveRequest((activeTarget ?: session.target).request)) ::scanAnotherLiveProvider else null,
                    audioVerificationScanning = audioVerificationScanning,
                    audioVerificationNotice = audioVerificationNotice,
                    onClearAudioVerificationNotice = { audioVerificationNotice = null },
                    onStopTurkishAudioScan = ::stopTurkishAudioScan,
                    onNextAudioTrack = ::selectNextAudioTrack,
                    audioTrackBanner = audioTrackBanner,
                    onClearAudioTrackBanner = { audioTrackBanner = null },
                    onAcceptDubSuggestion = ::acceptDubSuggestionWithVerification,
                    slowPromptVisible = slowConnectionPromptVisible,
                    onEnableSlowConnection = {
                        slowConnectionPromptVisible = false
                        slowConnectionModeState = true
                        container.appScope.launch { container.settings.setSlowConnectionMode(true) }
                    },
                    onDismissSlowPrompt = { slowConnectionPromptVisible = false },
                    ageRating = resolvedAgeRating?.rating,
                    ageRatingRegion = resolvedAgeRating?.region,
                    onLockedChanged = { playbackLocked = it },
                    onBack = ::finish,
                )
            }
        }
            StartupGuardRules.recordSuccess(this, "PlayerActivity")
        } catch (e: RuntimeException) {
            StartupWindowGuard.handleStartupContentFailure(this, "PlayerActivity", e)
            return
        }
        // TV Box zırhı: Immersive mode setContent sonrasında güvenle uygulanır
        enterImmersive()

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
                    autoNext.tick(
                        nowMs = SystemClock.elapsedRealtime(),
                        positionMs = controller.positionMs(),
                        durationMs = controller.durationMs(),
                        playing = controller.ui.isPlaying,
                        ended = controller.ui.ended,
                    )
                    skips.tick(controller.positionMs(), controller.ui.isPlaying)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Dönüşte (ör. başka uygulamadan) kontroller açık gelir; oynatma kullanıcıya bırakılır.
        enterImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterImmersive()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!::controller.isInitialized) return
        controller.player.pause()
        saveProgress()
        if (::subtitles.isInitialized) {
            subtitles.flush()
        }
    }

    /** Çıkışta pencere parlaklığı bırakılır; sistem değeri geçerli olur. */
    override fun finish() {
        levels?.restoreBrightness()
        super.finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::session.isInitialized && ::controller.isInitialized) {
            val isLive = session.offline == null && LiveCatalogRules.isLiveRequest(session.target.request)
            val direction = PlayerKeys.zapDirectionForKeyCode(keyCode, isLive = isLive && canZapChannels)
            if (direction != 0 && !leaving && !playbackLocked) {
                if (event.repeatCount == 0 && event.downTime != lastZapConsumedDownTime) {
                    lastZapConsumedDownTime = event.downTime
                    zapChannel(direction)
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            LiveZapListHolder.remove(liveZapKey)
        }
        levels?.restoreBrightness()
        if (::controller.isInitialized) {
            controller.release()
            val container = (application as PlayApp).container
            container.torrentServer.onOpened = null
            container.torrentEngine.release()
        }
    }

    private fun createSourceCoordinator(request: StreamRequest): SourceCoordinator {
        val container = appContainer
        return SourceCoordinator(
            scope = lifecycleScope,
            request = request,
            streamsFor = container.streams::streams,
            askedFor = container.streams::sources,
            preferenceFor = container.sourceMemory::get,
            remember = container.sourceMemory::remember,
            sizeProbe = container.sizeProbe::size,
            runtimeFor = ::expectedRuntimeMinutes,
            verdicts = container.streamVerdicts,
            maxHeight = DeviceCodecs.maxDecodableHeight(),
            preferDub = preferDub,
            autoSource = autoSourceSetting,
            showDubSuggestion = showDubSuggestionSetting,
            revertOnSelectedFailure = revertOnSelectedFailureSetting,
            loadBlocked = { container.wrongContentBlocks.ensureLoaded() },
            isSourceBlocked = { src -> container.wrongContentBlocks.isBlockedCached(request, src, session.target.title, request.year) },
            persistSourceBlock = { src -> container.wrongContentBlocks.block(request, src, session.target.title, request.year) },
            persistAudioSyncReport = { src -> container.audioSyncReports.increment(src) },
            load = ::loadStream,
            position = { controller.positionMs() },
            clock = SystemClock::elapsedRealtime,
            sourceProbe = { stream ->
                container.sourceProbe.probe(stream.url, stream.headers, timeoutMs = 1_500L)
            },
        )
    }

    private fun showLiveChannelBanner(title: String, switching: Boolean) {
        liveChannelBanner = title
        liveZapSwitching = switching
        bannerJob?.cancel()
        if (!switching) {
            bannerJob = lifecycleScope.launch {
                delay(3_000L)
                liveChannelBanner = null
            }
        }
    }

    private fun showLiveNotice(message: String) {
        liveNotice = message
        noticeJob?.cancel()
        noticeJob = lifecycleScope.launch {
            delay(4_500L)
            liveNotice = null
        }
    }

    private fun zapChannel(direction: Int) {
        if (!canZapChannels || leaving || liveZapChannels.size <= 1) return
        val base = (pendingZapIndex ?: currentZapIndex).takeIf { it in liveZapChannels.indices } ?: 0
        val nextIdx = LiveZapRules.nextAvailableIndex(liveZapChannels, base, direction) ?: return
        val targetChannel = liveZapChannels[nextIdx]
        pendingZapIndex = nextIdx
        val ticket = zapSequencer.issue(nextIdx)
        pendingZapTicket = ticket
        liveNotice = null
        showLiveChannelBanner(targetChannel.title, switching = true)
        routeSwitchJob?.cancel()
        zapJob?.cancel()
        zapJob = lifecycleScope.launch {
            val found = discoverFirstLiveRouteStreams(targetChannel.routes, startIndex = 0, title = targetChannel.title, markEmptyFailed = true)
            if (!zapSequencer.isLatest(ticket)) return@launch
            if (found == null) {
                pendingZapIndex = null
                pendingZapTicket = null
                liveZapSwitching = false
                showLiveChannelBanner(session.target.title, switching = false)
                showLiveNotice(getString(R.string.live_zap_reverted))
                return@launch
            }
            val (routeIdx, route, streams) = found
            val rollback = activeZapRollback ?: ZapRollback(
                previousSession = session,
                previousStream = currentStream,
                previousIndex = currentZapIndex.takeIf { it in liveZapChannels.indices } ?: base,
                previousRoutes = currentChannelRoutes,
                previousRouteIndex = currentRouteIndex,
            )
            activeZapRollback = rollback
            currentChannelRoutes = targetChannel.routes
            currentRouteIndex = routeIdx
            allLiveRoutesExhausted = false
            controller.clearError()
            startLiveRouteCoordinator(
                route = route,
                title = targetChannel.title,
                posterUrl = targetChannel.logoUrl,
                channelKey = targetChannel.channelKey,
                streams = streams,
            )
        }
    }

    private fun scanAnotherLiveProvider() {
        if (leaving || !LiveCatalogRules.isLiveRequest(session.target.request)) return
        val nextIdx = LiveRoutePlanner.nextUntriedRouteIndex(
            routes = currentChannelRoutes,
            currentIndex = currentRouteIndex,
            failedKeys = sessionFailedRouteKeys,
        )
        if (nextIdx == null) {
            showLiveNotice(getString(R.string.live_route_not_found))
            return
        }
        switchToLiveRoute(
            startRouteIndex = nextIdx,
            statusNotice = getString(R.string.live_route_retrying),
            markFailedOnEmpty = true,
            revertZapIfExhausted = false,
        )
    }

    private fun handleLiveSourceCoordinatorFailed(failedCoordinator: SourceCoordinator) {
        if (leaving || coordinator !== failedCoordinator) return
        val currentRoute = currentChannelRoutes.getOrNull(currentRouteIndex)
            ?: LiveRoutePlanner.routeFromRequest(session.target.request)
        if (currentRoute != null) {
            sessionFailedRouteKeys += LiveRoutePlanner.routeKey(currentRoute)
        }
        val nextIdx = LiveRoutePlanner.nextUntriedRouteIndex(
            routes = currentChannelRoutes,
            currentIndex = currentRouteIndex,
            failedKeys = sessionFailedRouteKeys,
        )
        if (nextIdx != null) {
            switchToLiveRoute(
                startRouteIndex = nextIdx,
                statusNotice = getString(R.string.live_route_retrying),
                markFailedOnEmpty = true,
                revertZapIfExhausted = activeZapRollback != null,
            )
            return
        }
        if (activeZapRollback != null) {
            revertFailedLiveZap()
        } else {
            allLiveRoutesExhausted = true
            showLiveNotice(getString(R.string.live_route_not_found))
        }
    }

    private fun switchToLiveRoute(
        startRouteIndex: Int,
        statusNotice: String,
        markFailedOnEmpty: Boolean,
        revertZapIfExhausted: Boolean,
    ) {
        val routes = currentChannelRoutes
        if (routes.isEmpty()) {
            if (revertZapIfExhausted && activeZapRollback != null) {
                revertFailedLiveZap()
            } else {
                showLiveNotice(getString(R.string.live_route_not_found))
            }
            return
        }
        val channelTitle = session.target.title
        val channelLogo = session.target.posterPath
        val channelKey = session.liveChannelKey
        showLiveNotice(statusNotice)
        showLiveChannelBanner(channelTitle, switching = true)
        routeSwitchJob?.cancel()
        val avoidIdx = currentRouteIndex
        routeSwitchJob = lifecycleScope.launch {
            val found = discoverFirstLiveRouteStreams(
                routes = routes,
                startIndex = startRouteIndex,
                title = channelTitle,
                markEmptyFailed = markFailedOnEmpty,
                excludeIndex = avoidIdx,
            )
            if (found == null) {
                liveZapSwitching = false
                if (revertZapIfExhausted && activeZapRollback != null) {
                    revertFailedLiveZap()
                } else {
                    allLiveRoutesExhausted = allLiveRoutesExhausted || (coordinator?.phase == SourcePhase.Failed)
                    showLiveChannelBanner(channelTitle, switching = false)
                    showLiveNotice(getString(R.string.live_route_not_found))
                }
                return@launch
            }
            val (foundIdx, route, streams) = found
            currentRouteIndex = foundIdx
            allLiveRoutesExhausted = false
            controller.clearError()
            startLiveRouteCoordinator(
                route = route,
                title = channelTitle,
                posterUrl = channelLogo,
                channelKey = channelKey,
                streams = streams,
            )
        }
    }

    private fun startLiveRouteCoordinator(
        route: LiveSourceRoute,
        title: String,
        posterUrl: String?,
        channelKey: String?,
        streams: List<PlayableStream>,
    ) {
        coordinator?.cancel()
        val newRequest = LiveZapRules.toStreamRequest(route, title)
        val newTarget = PlayTarget(
            request = newRequest,
            title = title,
            posterPath = posterUrl,
        )
        session = session.copy(
            target = newTarget,
            stream = streams.first(),
            fallbacks = streams.drop(1),
            startPositionMs = 0L,
            autoSource = false,
            liveChannelKey = channelKey,
        )
        activeTarget = newTarget
        val nextCoord = createSourceCoordinator(newRequest)
        coordinator = nextCoord
        controller.onDurationKnown = { nextCoord.onDuration(it) }
        controller.onFailure = { detail -> leaving || nextCoord.onFailure(detail) }
        nextCoord.start(initial = streams.first(), fallbacks = streams.drop(1), startMs = 0L)
    }

    private suspend fun discoverFirstLiveRouteStreams(
        routes: List<LiveSourceRoute>,
        startIndex: Int,
        title: String,
        markEmptyFailed: Boolean,
        excludeIndex: Int = -1,
    ): Triple<Int, LiveSourceRoute, List<PlayableStream>>? {
        if (routes.isEmpty()) return null
        val start = startIndex.coerceIn(0, routes.lastIndex)
        for (offset in routes.indices) {
            val idx = (start + offset) % routes.size
            if (idx == excludeIndex) continue
            val route = routes[idx]
            val key = LiveRoutePlanner.routeKey(route)
            if (key in sessionFailedRouteKeys) continue
            val request = LiveZapRules.toStreamRequest(route, title)
            val collected = withTimeoutOrNull(8_000L) {
                AutoNextRules.collect(appContainer.streams.streams(request), preferredAddonId = null)
            }.orEmpty().filter { !it.url.isNullOrBlank() }
            if (collected.isNotEmpty()) {
                return Triple(idx, route, collected)
            }
            if (markEmptyFailed) {
                sessionFailedRouteKeys += key
            }
        }
        return null
    }

    private fun revertFailedLiveZap() {
        val rollback = activeZapRollback ?: return
        activeZapRollback = null
        pendingZapIndex = null
        pendingZapTicket = null
        liveZapSwitching = false
        allLiveRoutesExhausted = false
        routeSwitchJob?.cancel()
        coordinator?.cancel()
        controller.clearError()
        session = rollback.previousSession
        activeTarget = rollback.previousSession.target
        currentZapIndex = rollback.previousIndex
        currentChannelRoutes = rollback.previousRoutes
        currentRouteIndex = rollback.previousRouteIndex
        val restored = createSourceCoordinator(rollback.previousSession.target.request)
        coordinator = restored
        controller.onDurationKnown = { restored.onDuration(it) }
        controller.onFailure = { detail -> leaving || restored.onFailure(detail) }
        restored.start(
            initial = rollback.previousStream,
            fallbacks = rollback.previousSession.fallbacks,
            startMs = 0L,
        )
        showLiveChannelBanner(rollback.previousSession.target.title, switching = false)
        showLiveNotice(getString(R.string.live_zap_reverted))
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
            appContainer.torrentEngine.closeActive()
        }
        pendingAudioLearnJob?.cancel()
        currentStream = stream
        val isLive = LiveCatalogRules.isLiveRequest(session.target.request)
        val fromDubSuggestion = pendingDubRevertStream != null
        val shouldVerifyTurkish = session.offline == null && !isLive && !userAudioChosen && ((preferDub && tryUntilTurkishAudio && stream.dubbed) || fromDubSuggestion)
        val revertStream = pendingDubRevertStream
        pendingDubRevertStream = null
        if (::audioVerifier.isInitialized) {
            audioVerifier.begin(stream, shouldVerify = shouldVerifyTurkish, revertIfUnverified = revertStream)
            audioVerificationScanning = audioVerifier.scanning
        }
        skips.onSourceChanged(startMs)
        embeddedDub = false
        // Dublajlı kaynakta Türkçe altyazı gereksiz: otomatik seçim kapanır (kullanıcının elle seçtiğine dokunulmaz).
        if (stream.dubbed) subtitles.suppressAuto() else subtitles.resumeAuto()
        subtitles.setStream(stream)
        if (previous != null) saveProgress()
        controller.load(stream, startMs)
    }

    private fun acceptDubSuggestionWithVerification() {
        val coord = coordinator ?: return
        pendingDubRevertStream = coord.current ?: currentStream
        if (::audioVerifier.isInitialized) {
            audioVerifier.resetForExplicitDubSwitch()
        }
        coord.acceptDubSuggestion()
    }

    private fun stopTurkishAudioScan() {
        if (::audioVerifier.isInitialized) {
            audioVerifier.stopByUser()
        }
        audioVerificationScanning = false
        audioVerificationNotice = null
    }

    private fun evaluateTurkishAudio(): Boolean {
        if (!::audioVerifier.isInitialized || !audioVerifier.scanning) {
            audioVerificationScanning = false
            return false
        }
        val coord = coordinator ?: return false
        val stream = currentStream ?: return false
        val decision = audioVerifier.evaluate(
            stream = stream,
            tracks = controller.allAudioTrackInfos(),
            playbackStarted = controller.ui.firstFrameRendered,
            nowMs = SystemClock.elapsedRealtime(),
            remainingDubbedCandidates = coord.dubbedCandidatesExcluding(emptySet()).filterNot { audioVerifier.hasFailedVerification(it.url) },
            remainingAlternativeCandidates = coord.alternativeCandidatesExcluding(emptySet()).filterNot { audioVerifier.hasFailedVerification(it.url) },
            lastWorking = coord.lastWorkingStream,
            sizes = coord.sizes,
            maxHeight = DeviceCodecs.maxDecodableHeight(),
        )
        return when (decision) {
            TurkishVerifyDecision.KeepWaiting -> true
            is TurkishVerifyDecision.SelectTrack -> {
                controller.selectAudio(decision.index)
                true
            }
            TurkishVerifyDecision.Verified -> {
                audioVerificationScanning = false
                coord.markCurrentVerifiedWorking()
                false
            }
            is TurkishVerifyDecision.TryNextDubbed -> {
                audioVerificationScanning = false
                audioVerificationNotice = decision.notice
                coord.switchAfterUnverifiedDub(decision.stream)
                true
            }
            is TurkishVerifyDecision.FallbackToAlternative -> {
                audioVerificationScanning = false
                audioVerificationNotice = decision.notice
                coord.switchAfterUnverifiedDub(decision.stream)
                true
            }
            is TurkishVerifyDecision.ContinueCurrent -> {
                audioVerificationScanning = false
                audioVerificationNotice = decision.notice
                false
            }
        }
    }

    /** Ses parçası listesi değişti: elle seçilen dil korunur; yoksa Türkçe ses izi doğrulama veya otomatik seçim çalışır. */
    private fun syncAudio() {
        val handledByVerifier = evaluateTurkishAudio()
        val tracks = controller.audioTracks()
        if (tracks.isEmpty()) {
            embeddedDub = false
            return
        }
        fun lang(t: AudioTrack) = SubtitleLanguages.normalize(t.language)
        if (!handledByVerifier) {
            if (userAudioChosen) {
                val wanted = userAudioLanguage
                val match = if (wanted != null) tracks.firstOrNull { lang(it) == wanted } else null
                if (match != null && !match.selected) controller.selectAudio(match.index)
            } else if (preferDub) {
                val turkish = tracks.firstOrNull { TurkishAudioTrackRules.isTurkishTrack(it.language, it.label) }
                if (turkish != null && !turkish.selected) controller.selectAudio(turkish.index)
            }
        }
        val selected = tracks.firstOrNull { it.selected }
        embeddedDub = selected != null &&
            TurkishAudioTrackRules.isTurkishTrack(selected.language, selected.label) &&
            (tracks.size > 1 || currentStream?.dubbed == true)
        val infos = controller.allAudioTrackInfos()
        val supported = infos.filter { it.isSupported }
        val stream = currentStream
        val streamUrl = stream?.url
        if (session.offline == null && !userAudioChosen && !audioVerificationScanning && stream != null && streamUrl != null && supported.size > 1 && !AudioTrackRules.hasAnyLanguageInfo(infos)) {
            if (learnedAppliedForUrl != streamUrl) {
                learnedAppliedForUrl = streamUrl
                val reqSnapshot = session.target.request
                lifecycleScope.launch {
                    val matchedIdx = appContainer.audioTrackLearning.findMatch(stream, reqSnapshot, infos)
                    if (currentStream?.url != streamUrl || userAudioChosen || leaving) return@launch
                    if (matchedIdx != null) {
                        val currentSel = controller.allAudioTrackInfos().firstOrNull { it.isSelected }?.index
                        if (currentSel != matchedIdx) {
                            controller.selectAudio(matchedIdx)
                        }
                        audioTrackBanner = getString(R.string.player_audio_selecting_learned)
                    } else if (unknownAudioHintStreamUrl != streamUrl) {
                        unknownAudioHintStreamUrl = streamUrl
                        audioTrackBanner = getString(R.string.player_audio_unknown_hint)
                    }
                }
            }
        }
        // Türkçe ses seçiliyken Türkçe altyazıya gerek yok; yoksa otomatik altyazı yeniden açılır.
        if (embeddedDub) subtitles.suppressAuto() else if (currentStream?.dubbed != true) subtitles.resumeAuto()
    }

    /** Alt çubuktan "Sonraki ses izi": sıradaki ses parçasına geçer ve elle seçim sayılır. */
    private fun selectNextAudioTrack() {
        val infos = controller.allAudioTrackInfos()
        val next = AudioTrackRules.nextTrack(infos) ?: return
        val hasLang = AudioTrackRules.hasAnyLanguageInfo(infos)
        onAudioChosen(next.index)
        val supportedPos = infos.filter { it.isSupported }.indexOfFirst { it.index == next.index }.coerceAtLeast(0) + 1
        val uiIso = AppLanguage.effective().iso
        val name = AudioTrackRules.displayNameForUi(
            track = next,
            displayIndexOneBased = supportedPos,
            fallbackLabel = { getString(R.string.player_audio_track, it) },
            languageDisplayName = { SubtitleLanguages.displayName(it, uiIso) },
        )
        audioTrackBanner = if (hasLang) name else getString(R.string.player_audio_unknown_named, name)
    }

    /** Kullanıcı sesi Parçalar panelinden seçti: dil hatırlanır (kaynak değişince aynı dil aranır), otomatik seçim ve tarama durur. */
    private fun onAudioChosen(index: Int) {
        val infos = controller.allAudioTrackInfos()
        val track = controller.audioTracks().firstOrNull { it.index == index } ?: return
        stopTurkishAudioScan()
        userAudioChosen = true
        userAudioLanguage = SubtitleLanguages.normalize(track.language)
        controller.selectAudio(index)
        scheduleAudioTrackLearning(index, infos)
    }

    private fun scheduleAudioTrackLearning(chosenIndex: Int, initialInfos: List<com.kaan.play.data.player.AudioTrackInfo>) {
        pendingAudioLearnJob?.cancel()
        val stream = currentStream ?: return
        if (session.offline != null) return
        val supported = initialInfos.filter { it.isSupported }
        if (supported.size <= 1 || AudioTrackRules.hasAnyLanguageInfo(supported)) return
        val streamUrl = stream.url
        val requestSnapshot = session.target.request
        pendingAudioLearnJob = lifecycleScope.launch {
            var activeMs = 0L
            while (isActive && !leaving && currentStream?.url == streamUrl) {
                delay(1_000L)
                if (controller.ui.isPlaying) {
                    activeMs += 1_000L
                    if (activeMs >= AudioTrackLearning.MIN_PLAYBACK_MS) {
                        val latestInfos = controller.allAudioTrackInfos()
                        val selectedIdx = latestInfos.firstOrNull { it.isSelected }?.index
                        if (selectedIdx == chosenIndex) {
                            appContainer.audioTrackLearning.record(stream, requestSnapshot, latestInfos, chosenIndex)
                        }
                        break
                    }
                }
            }
        }
    }

    // ---- otomatik sonraki bölüm ----

    private val appContainer get() = (application as PlayApp).container

    private fun onNextEpisodeClicked() {
        if (leaving) return
        autoNext.skipToNext()
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
    private fun sessionDetailAsync(type: MediaType, tmdbId: Int): Deferred<MediaDetail?> {
        val key = type to tmdbId
        val existing = sessionDetailDeferred
        if (sessionDetailKey == key && existing != null) return existing
        val created = lifecycleScope.async(Dispatchers.IO) {
            runCatching { appContainer.tmdb.detail(type, tmdbId) }.getOrNull()
        }
        sessionDetailKey = key
        sessionDetailDeferred = created
        return created
    }

    private suspend fun expectedRuntimeMinutes(request: com.kaan.play.data.stream.StreamRequest): Int? = runCatching {
        val detail = if (request.tmdbId > 0) sessionDetailAsync(request.type, request.tmdbId).await() else null
        val season = request.season
        val episode = request.episode
        if (request.type == MediaType.Tv && season != null && episode != null) {
            appContainer.tmdb.season(request.tmdbId, season).episodes.firstOrNull { it.number == episode }?.runtimeMinutes
                ?: detail?.runtimeMinutes
        } else {
            detail?.runtimeMinutes
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
        val target = session.target.copy(
            request = request,
            ageRating = resolvedAgeRating?.rating ?: session.target.ageRating,
            ageRatingRegion = resolvedAgeRating?.region ?: session.target.ageRatingRegion,
        )
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
            if (currentStream?.url?.let(TorrentLinks::isMagnet) == true) container.torrentEngine.closeActive()
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
        if (!::session.isInitialized || !::controller.isInitialized) return
        if (session.noHistory) return
        if (session.target.request.addonType in listOf("tv", "channel")) return
        val duration = controller.durationMs()
        if (duration <= 0 || controller.ui.error != null) return
        val position = if (controller.ui.ended || finishedForAdvance) duration else controller.positionMs()
        val container = (application as PlayApp).container
        // Etkinlik kapanırken de tamamlansın diye uygulama kapsamında.
        container.appScope.launch { container.library.saveProgress(session.target, position, duration) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val parsed = parseSessionSafely(intent) ?: run {
            finish()
            return
        }
        session = parsed
        if (::controller.isInitialized) {
            val stream = parsed.stream
            if (stream != null && !stream.url.isNullOrBlank()) {
                loadStream(stream, parsed.startPositionMs)
            } else if (parsed.autoSource) {
                coordinator?.start(
                    initial = null,
                    fallbacks = parsed.fallbacks,
                    startMs = parsed.startPositionMs,
                )
            }
        }
    }

    private fun parseSessionSafely(intent: android.content.Intent?): PlaySession? {
        if (intent == null) return null
        return try {
            PlaySession.from(intent)
        } catch (t: Throwable) {
            android.util.Log.e("PlayerActivity", "Intent veya Uri çözümlenemedi: ${t.message}", t)
            null
        }
    }

    private fun enterImmersive() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                runCatching {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
            val decor = runCatching { window.peekDecorView() ?: window.decorView }.getOrNull()
            if (decor != null) {
                decor.post {
                    runCatching {
                        WindowCompat.setDecorFitsSystemWindows(window, false)
                        WindowInsetsControllerCompat(window, decor).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }.onFailure { t ->
                        android.util.Log.w("PlayerActivity", "enterImmersive post hatası: ${t.message}")
                    }
                }
            }
        }.onFailure { t ->
            android.util.Log.w("PlayerActivity", "enterImmersive hatası: ${t.message}")
        }
    }

    private companion object {
        const val AUTO_NEXT_TICK_MS = 500L
    }
}
