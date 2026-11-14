package com.kaan.play.tv

import com.kaan.play.R
import android.app.ActivityManager
import android.graphics.SurfaceTexture
import android.os.Debug
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.content.Intent
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import com.kaan.play.data.provider.CatalogItem
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kaan.play.PlayApp
import com.kaan.play.data.library.WatchRules
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.player.PlaybackSpeeds
import com.kaan.play.data.settings.ResizeMode
import com.kaan.play.data.stream.SizeText
import com.kaan.play.data.stream.SourceRanking
import com.kaan.play.data.stream.SourceSort
import com.kaan.play.data.subtitle.SubtitleOffset
import com.kaan.play.data.torrent.TorrentLoadProgressRules
import com.kaan.play.data.stream.DeviceCodecs
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.data.stream.ranked
import com.kaan.play.data.subtitle.SubtitleDiag
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.data.subtitle.SubtitleLanguages
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.player.AudioTrack
import com.kaan.play.ui.player.AutoNextConfig
import com.kaan.play.ui.player.AutoNextController
import com.kaan.play.ui.player.AutoNextPhase
import com.kaan.play.ui.player.AutoNextRules
import com.kaan.play.ui.player.NextPlayback
import com.kaan.play.ui.player.PlaySession
import com.kaan.play.ui.player.PlaybackController
import com.kaan.play.ui.player.SkipController
import com.kaan.play.ui.player.SkipSegment
import com.kaan.play.ui.player.SkipSegmentKind
import com.kaan.play.ui.player.SourceCoordinator
import com.kaan.play.ui.player.SourcePhase
import com.kaan.play.ui.player.SubtitleController
import com.kaan.play.ui.player.SubtitleCues
import com.kaan.play.ui.player.SubtitleStatus
import com.kaan.play.ui.player.SubtitleStyling
import com.kaan.play.ui.player.TrackLabels
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * TV oynatıcı. Oynatma, kaynak seçimi/geçişi, altyazı ve sonraki bölüm Play'in katmanlarıyla yapılır
 * ([PlaybackController], [SourceCoordinator], [SubtitleController], [AutoNextController]); bağlantılar Play'in
 * PlayerActivity'sindeki gibidir. Play'in dosyaları değişmez. TV'ye ait olan: ekran düzeni ve kumanda.
 *
 * - Dikey video: video ortada, solda karartılmış afiş, sağda dizi bilgisi ve bölüm listesi.
 * - Yatay video: tam ekran; bölüm listesi ve ses/altyazı sağda yarı saydam katman, video küçülmez.
 * - Kumanda: sağ/sol 10 sn (basılı tutunca 30/60 sn), OK oynat/duraklat, yukarı bölümler, aşağı (ya da Menü) ses/altyazı ve
 *   oynatıcı ayarları (kaynak değiştir, altyazı gecikmesi, hız, görüntü oranı, kalite; Play oynatıcısındaki düğmelerin karşılığı).
 * - İntro/özet/jenerik/önizleme: Play'in [SkipController]'ı; segmentte "İntroyu geç" düğmesi görünür, OK atlar, Geri düğmeyi kapatır.
 */
class TvPlayerActivity : AppCompatActivity() {
    private lateinit var session: PlaySession
    private lateinit var controller: PlaybackController
    private lateinit var subtitles: SubtitleController
    private lateinit var autoNext: AutoNextController
    private lateinit var skips: SkipController
    private var coordinator: SourceCoordinator? = null
    private var currentStream by mutableStateOf<PlayableStream?>(null)
    private var preferDub = true
    private var userAudioLanguage: String? = null
    private var leaving = false
    private var finishedForAdvance = false
    private var nextCsDataUrl: String? = null
    private var libraryScope: Pair<String, String>? = null
    /** "Hâlâ izliyor musunuz?" sorusu açık: oynatma duraklatıldı, herhangi bir tuş devam ettirir. */
    private var stillWatching by mutableStateOf(false)
    /** Açık panel ve alt bilgi çubuğunun görüneceği son an. Tuşlar etkinlik düzeyinde işlenir (Compose odağına bağlı değil). */
    private var panel by mutableStateOf(Panel.None)
    private var osdUntil by mutableLongStateOf(SystemClock.elapsedRealtime() + OSD_MS)
    private var details: TvDetailModel? = null
    /**
     * Oynarken akış durdu: [STALL_MS] boyunca arabellek ilerlemedi ve kendiliğinden bir kez yeniden açmak da yetmedi.
     * Ekranda "Video akışı durdu · OK: yeniden dene" görünür (Play'in sarma bekçisi 2×8 sn sonra vazgeçip oynatıcıyı arabellekte bırakıyordu).
     */
    private var stalled by mutableStateOf(false)
    /** Kaynak bulunamadı ve site bot doğrulaması istiyor: OK Play'in doğrulama ekranını açar, başarılıysa oynatma yeniden denenir. */
    private var verifySite by mutableStateOf<VerifySite?>(null)
    private val openedAt = System.currentTimeMillis()
    private val verifyLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode != RESULT_OK) return@registerForActivityResult
        Log.i(TAG, "site doğrulandı (${verifySite?.host}); oynatma yeniden deneniyor")
        startActivity(Intent(intent))
        finish()
    }
    /** Bu takılmada akış kendiliğinden yeniden açıldı mı? Oynatma yeniden başlayınca sıfırlanır. */
    private var stallRetried = false
    /** Oynatmanın en son ilerlediği (ya da son yeniden açmanın yapıldığı) an. */
    private var stallSince = 0L
    /** Bu oynatıcıda en az bir kare gösterildi: takılma izlemesi ancak bundan sonra devreye girer (öncesi "kaynak bulunamadı" yolu). */
    private var playedOnce = false
    /** Deneysel "Daha fazla kaynak ara" durumu ([startDeepSearch]); kaynak paneli gösterir. */
    private var deepSearch by mutableStateOf(DeepSearch.Off)
    /** Derin arama bellek bekçisi yüzünden kesildi. */
    @Volatile private var deepStopped = false

    private val container get() = (application as PlayApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = PlaySession.from(intent)?.takeIf { it.offline == null } ?: return finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = container
        libraryScope = runBlocking { container.localProfile.libraryScope.first() }
        preferDub = runBlocking { container.settings.preferDub.first() }

        container.torrentEngine.retain()
        controller = PlaybackController(this, container.httpClient, torrentUrl = container.torrentServer::urlFor)
        // Play'in atlama denetleyicisi (PlayerActivity'deki gibi): düğmeler, otomatik atlama ve türler Ayarlar > Oynatıcı'dan.
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
                if (!isPlaying) saveProgress()
                if (isPlaying) {
                    stallRetried = false
                    stalled = false
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    saveProgress()
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
            onNoNext = { Toast.makeText(this, getString(R.string.tv_play_last_episode), Toast.LENGTH_SHORT).show() },
        )
        autoNext.start()

        subtitles = SubtitleController(
            scope = lifecycleScope,
            playback = controller,
            request = session.target.request,
            loader = SubtitleDiag.loader(container.translatedSubtitles.loader(container.subtitleLoader)),
            memory = container.subtitleMemory,
            provider = SubtitleDiag.provider(container.subtitleProvider),
            preferredLanguage = { container.settings.subtitleLanguage.first() },
            persist = container.appScope,
            translation = null,
            translationStore = null,
        )
        subtitles.start()
        lifecycleScope.launch { snapshotFlow { controller.ui.tracksVersion }.collect { syncAudio() } }

        val sources = SourceCoordinator(
            scope = lifecycleScope,
            request = session.target.request,
            streamsFor = ::guardedStreams,
            askedFor = container.streams::sources,
            preferenceFor = container.sourceMemory::get,
            remember = container.sourceMemory::remember,
            sizeProbe = container.sizeProbe::size,
            runtimeFor = ::expectedRuntimeMinutes,
            verdicts = container.streamVerdicts,
            maxHeight = DeviceCodecs.maxDecodableHeight(),
            preferDub = preferDub,
            // "Kaynağı otomatik seç" ayarı TV'de başlangıç ekranında uygulanır (kapalıysa kullanıcı listeden seçer). Oynatıcıda hep
            // açık: elle seçilen kaynak ve yedekleri tükenirse Play'in kaynak araması sürer; kapalıyken kaynak araması hiç
            // başlamadığı için oynatıcı "Kaynaklar aranıyor…"da kalırdı (SourceCoordinator: finished hiç true olmaz → StartPolicy.Wait).
            autoSource = true,
            load = ::loadStream,
            position = { controller.positionMs() },
            clock = SystemClock::elapsedRealtime,
            sourceProbe = { s -> container.sourceProbe.probe(s.url, s.headers, timeoutMs = 1_500L, interceptor = s.httpInterceptor) },
        )
        coordinator = sources
        controller.onFirstFrame = {
            playedOnce = true
            sources.onFirstFrame()
            container.torrentEngine.onFirstFrame()
        }
        controller.onDurationKnown = { sources.onDuration(it) }
        controller.onFailure = { detail -> leaving || sources.onFailure(detail) }
        // Takılma izleme (500 ms; yalnız ekran açıkken). Arabelleğe alırken Play'in analizi çalışır ve başka kaynağa geçişe o karar
        // verir. Bir kez oynamış akış, kullanıcı oynatmak isterken STALL_MS boyunca ilerlemezse (arabellek ya da yeniden açılıp hiç
        // kare gelmemesi) bir kez kendiliğinden yeniden açılır, yine ilerlemezse kullanıcıya söylenir. Duraklatma, konum değişimi (sarma), Play'in
        // kaynak geçişi ve kaynak değişimi sayacı sıfırlar; uygulama arka plandayken hiçbir şey yapılmaz.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var lastPosition = -1L
                var lastUrl: String? = null
                stallSince = SystemClock.elapsedRealtime()
                while (true) {
                    delay(500)
                    val ui = controller.ui
                    val t = controller.stallTracker
                    if (ui.buffering && !leaving) sources.checkStall(t.firstFrameRendered, t.isSeeking, t.currentStallStartMs, t.pastStalls)
                    val now = SystemClock.elapsedRealtime()
                    val position = controller.positionMs()
                    val url = currentStream?.url
                    val waiting = playedOnce && !leaving && !stillWatching && controller.player.playWhenReady &&
                        !ui.isPlaying && !ui.ended && ui.error == null && !sources.switching
                    // Sarma ayrıca beklenmez: konum sıçraması sayacı zaten sıfırlar; yüklenmeyen yere sarınca Play'in "sarılıyor"
                    // bayrağı hiç kalkmayabildiği için ona bakmak takılmayı sonsuz "Yükleniyor…"da bırakıyordu.
                    if (!waiting || position != lastPosition || url != lastUrl) {
                        if (url != lastUrl) stallRetried = false
                        stallSince = now
                        lastPosition = position
                        lastUrl = url
                        if (!waiting) stalled = false
                        continue
                    }
                    if (now - stallSince < STALL_MS) continue
                    if (!stallRetried) {
                        stallRetried = true
                        Log.i(TAG, "akış ${STALL_MS / 1000} sn ilerlemedi, yeniden açılıyor")
                        retryStream()
                    } else if (!stalled) {
                        Log.i(TAG, "akış yeniden açıldıktan sonra da ilerlemedi")
                        stalled = true
                    }
                }
            }
        }
        container.torrentServer.onOpened = { magnet, size -> runOnUiThread { sources.reportSize(magnet, size) } }
        sources.start(
            initial = if (session.autoSource) null else session.stream,
            fallbacks = session.fallbacks,
            startMs = session.startPositionMs,
        )

        val details = seriesRef()?.let { (provider, url) ->
            ViewModelProvider(this)[TvDetailModel::class.java].also { it.load(provider, url, cached = true) }
        }
        this.details = details
        val launcher = PlayLauncher(this, lifecycleScope)
        setContent {
            PlayTvTheme(forceDark = true) {
                TvPlayerScreen(
                    session = session,
                    controller = controller,
                    subtitles = subtitles,
                    autoNext = autoNext,
                    stream = currentStream,
                    details = details,
                    stillWatching = stillWatching,
                    skip = ::shownSkip,
                    sourcePhase = sources.phase,
                    noStreams = sources.streams.isEmpty() && sources.current == null,
                    verifySite = verifySite,
                    onNoSource = {
                        lifecycleScope.launch {
                            verifySite = TvSiteVerify.verifySite((application as PlayApp).container, session.target.request.pluginId, openedAt, "")
                            verifySite?.let { Log.i(TAG, "kaynak yok, doğrulama gerekiyor: ${it.host}") }
                        }
                    },
                    stalled = stalled,
                    panel = panel,
                    osdUntil = osdUntil,
                    onClosePanel = { panel = Panel.None },
                    sources = sources,
                    onPanel = { panel = it },
                    deepSearch = deepSearch,
                    onDeepSearch = ::startDeepSearch,
                    onPickSource = { s ->
                        panel = Panel.None
                        // Play'in panelindeki "Uygula" gibi: kaldığı saniyeden geçilir, açılmazsa önceki kaynağa dönülür.
                        Log.i(TAG, "kaynak elle değiştirildi: ${s.source.addonName} ${s.resolution ?: "?"}p @${controller.positionMs() / 1000}s")
                        sources.pick(s)
                    },
                    onAudio = ::onAudioChosen,
                    onEpisode = { d, ep ->
                        if (leaving) return@TvPlayerScreen
                        leaving = true
                        controller.player.pause()
                        saveProgress()
                        launcher.play(details!!.target(d, ep), then = ::finish)
                    },
                    onChannel = { c ->
                        if (leaving) return@TvPlayerScreen
                        leaving = true
                        controller.player.pause()
                        launcher.playLive(c, then = ::finish)
                    },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(WatchRules.SAVE_INTERVAL_MS)
                    if (controller.ui.isPlaying) saveProgress()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(500)
                    autoNext.tick(SystemClock.elapsedRealtime(), controller.positionMs(), controller.durationMs(), controller.ui.isPlaying, controller.ui.ended)
                    skips.tick(controller.positionMs(), controller.ui.isPlaying)
                    publishNowPlaying()
                    if (controller.ui.isPlaying && SystemClock.elapsedRealtime() - lastInputAt > STILL_WATCHING_MS) {
                        controller.player.pause()
                        stillWatching = true
                    }
                }
            }
        }
    }

    /**
     * Kumanda. Panel kapalıyken tuşlar burada işlenir; panel açıkken yön/OK tuşları panelin listesine gider, Geri paneli kapatır.
     * "Hâlâ izliyor musunuz?" açıksa herhangi bir tuş yalnız onu kapatır.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::controller.isInitialized) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        if (event.action != KeyEvent.ACTION_DOWN) {
            // Geri'nin bırakılışı da tüketilir; varsayılan geri davranışı (etkinliği kapatma) aşağıda kendimiz yapıyoruz.
            return if (code == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
        }
        lastInputAt = SystemClock.elapsedRealtime()
        if (stillWatching) {
            stillWatching = false
            controller.player.play()
            return true
        }
        // Tuştan önce görünen atlama düğmesi: tuşun açtığı alt bilgi çubuğu gizli düğmeyi de gösterir; OK/Geri yalnız ekranda
        // zaten görünen düğmeye gitmeli (yoksa duraklat/çık yerine atlardı).
        val skip = shownSkip()
        osdUntil = SystemClock.elapsedRealtime() + OSD_MS
        if (code == KeyEvent.KEYCODE_BACK) {
            when {
                // Ayar alt paneli Geri'yle ses/altyazı ve ayarlar paneline döner (odak açılan satırda).
                panel in SETTING_PANELS -> panel = Panel.Tracks
                panel != Panel.None -> panel = Panel.None
                autoNext.phase != AutoNextPhase.Hidden -> {
                    autoNext.cancel()
                    if (controller.ui.ended) finish()
                }
                // Atlama düğmesi görünürken Geri yalnız düğmeyi kapatır (Play'deki gibi), oynatıcı kapanmaz.
                skip != null -> skips.dismiss(skips.active)
                else -> finish()
            }
            return true
        }
        if (panel != Panel.None) return super.dispatchKeyEvent(event)
        val repeat = event.repeatCount
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> controller.seekBy(-seekStep(repeat))
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> controller.seekBy(seekStep(repeat))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> if (repeat == 0) {
                when {
                    autoNext.phase == AutoNextPhase.Countdown -> autoNext.playNow()
                    stalled -> retryStream()
                    verifySite != null && !controller.ui.firstFrameRendered -> verifySite?.let { verifyLauncher.launch(it.intent(this)) }
                    skip != null -> skips.skip(skip)
                    else -> controller.togglePlay()
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> controller.togglePlay()
            KeyEvent.KEYCODE_MEDIA_PLAY -> controller.player.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> controller.player.pause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> autoNext.skipToNext()
            KeyEvent.KEYCODE_DPAD_UP -> {
                val d = (details?.state?.value as? DetailState.Ready)?.data
                if (d != null && d.episodes.isNotEmpty()) panel = Panel.Episodes
                else if (isLive) panel = Panel.Tracks // canlıda alt tuş kanal listesi; ses/altyazı yukarı tuşta
            }
            // Canlı TV: alt tuş kanal listesini açar; kanal +/- tuşları sıradaki/önceki kanala geçer.
            KeyEvent.KEYCODE_DPAD_DOWN -> panel = if (isLive && LiveChannels.list.isNotEmpty()) Panel.Channels else Panel.Tracks
            // Menü tuşu (kumandada varsa) her zaman ses/altyazı ve oynatıcı ayarları panelini açar.
            KeyEvent.KEYCODE_MENU -> panel = Panel.Tracks
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> if (isLive && repeat == 0) stepChannel(if (code == KeyEvent.KEYCODE_CHANNEL_UP) 1 else -1)
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    /** Telefon kumandasının üst kartı için oynayan yapım ([TvRemote.nowPlaying]). */
    private fun publishNowPlaying() {
        val r = session.target.request
        TvRemote.nowPlaying = TvRemote.NowPlaying(
            title = session.target.title,
            subtitle = listOfNotNull(
                r.episode?.let { getString(R.string.tv_play_season_episode, r.season ?: 1, it) },
                currentStream?.source?.addonName,
            ).joinToString(" · ").ifEmpty { null },
            posterUrl = com.kaan.play.data.tmdb.TmdbImages.url(session.target.posterPath, com.kaan.play.data.tmdb.TmdbImages.Size.PosterSmall),
            playing = controller.ui.isPlaying,
            positionMs = controller.positionMs(),
            durationMs = controller.durationMs(),
        )
    }

    override fun onStop() {
        super.onStop()
        TvRemote.nowPlaying = null
        if (!::controller.isInitialized) return
        controller.player.pause()
        saveProgress()
        subtitles.flush()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!::controller.isInitialized) return
        controller.release()
        container.torrentServer.onOpened = null
        container.torrentEngine.release()
    }

    /**
     * Deneysel "Daha fazla kaynak ara": kullanıcı uyarıyı kabul edince Play'in telefondaki kaynak
     * paneli gibi aramayı yeniden başlatır ([SourceCoordinator.ensureSources]; oynatma başındaki arama ilk kareden 10 sn sonra
     * kesilmişti, şimdi tüm açık eklentiler sorulur). 2 GB'lık kutuda bu arama süreci ~1 GB'a çıkarıp sistemin uygulamayı
     * kapatmasına yol açmıştı: arama [guardedStreams]'in bellek bekçisiyle çalışır. Arama zaten tamamlanmışsa
     * (oynatma başında her eklenti cevap verdi) yeni arama yapılmaz.
     */
    private fun startDeepSearch() {
        val sources = coordinator ?: return
        if (deepSearch == DeepSearch.Running || sources.loading) return
        val before = sources.streams.size
        deepStopped = false
        deepSearch = DeepSearch.Running
        Log.i(TAG, "derin arama başladı: $before kaynak var; ${memoryLine(memorySnapshot())}")
        sources.ensureSources()
        if (!sources.loading) {
            deepSearch = DeepSearch.AllSearched
            Log.i(TAG, "derin arama: oynatma başındaki arama zaten tamamlanmış, yeni arama yok")
            return
        }
        lifecycleScope.launch {
            snapshotFlow { sources.loading }.first { !it }
            deepSearch = if (deepStopped) DeepSearch.MemoryStopped else DeepSearch.Done
            Log.i(TAG, "derin arama bitti${if (deepStopped) " (bellek sınırı)" else ""}: ${sources.streams.size - before} yeni kaynak, toplam ${sources.streams.size}; ${memoryLine(memorySnapshot())}")
        }
    }

    /**
     * Play'in kaynak araması. Derin aramada ([DeepSearch.Running]) bellek bekçisiyle: 2 sn'de bir Java belleğine, sürecin
     * PSS'ine ve sistemin boş belleğine bakar (günlüğe yazar); sınır aşılırsa arama kesilir, o ana kadar bulunanlar listede kalır.
     * Oynatma başındaki arama değişmez. Eş zamanlılık, eklenti bırakma ve doğrulama tarayıcısı kuralları Play'de
     * ([com.kaan.play.data.cloudstream.CsSearchBudget]; 2 GB'lık kutu az bellekli sayılır).
     */
    private fun guardedStreams(request: StreamRequest): Flow<StreamResult> {
        val inner = container.streams.streams(request)
        if (deepSearch != DeepSearch.Running) return inner
        return channelFlow {
            val search = launch { inner.collect { send(it) } }
            val guard = launch(Dispatchers.IO) {
                while (true) {
                    delay(DEEP_GUARD_MS)
                    var m = memorySnapshot()
                    Log.i(TAG, "derin arama bellek: ${memoryLine(m)}")
                    // Java okuması toplanmamış çöpü de sayar (ölçüm: 240/256 MB okundu, durunca 45 MB'a indi): yüksekse
                    // bir kez çöp toplanıp yeniden bakılır, ancak yine yüksekse kesilir.
                    if (m.javaMb > m.javaMaxMb * 3 / 4) {
                        Runtime.getRuntime().gc()
                        m = memorySnapshot()
                        Log.i(TAG, "derin arama bellek (çöp toplandı): ${memoryLine(m)}")
                    }
                    val tight = m.javaMb > m.javaMaxMb * 3 / 4 || m.pssMb > DEEP_PSS_LIMIT_MB || m.availMb < DEEP_MIN_AVAIL_MB || m.low
                    if (tight) {
                        Log.w(TAG, "derin arama bellek sınırında durduruldu: ${memoryLine(m)}")
                        deepStopped = true
                        search.cancel()
                        break
                    }
                }
            }
            search.join()
            guard.cancel()
        }
    }

    private class MemorySnapshot(val javaMb: Long, val javaMaxMb: Long, val pssMb: Long, val availMb: Long, val low: Boolean)

    private fun memorySnapshot(): MemorySnapshot {
        val rt = Runtime.getRuntime()
        val info = ActivityManager.MemoryInfo().also { (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        return MemorySnapshot((rt.totalMemory() - rt.freeMemory()) shr 20, rt.maxMemory() shr 20, Debug.getPss() shr 10, info.availMem shr 20, info.lowMemory)
    }

    private fun memoryLine(m: MemorySnapshot) =
        "java ${m.javaMb}/${m.javaMaxMb} MB, pss ${m.pssMb} MB, sistem boş ${m.availMb} MB${if (m.low) " (az)" else ""}"

    /** Bölüm paneli için içeriğin detay kimliği (yalnız dizide): eklentide eklenti + içerik adresi, TMDB'de tür + kimlik. */
    private fun seriesRef(): Pair<String, String>? {
        val r = session.target.request
        if (r.season == null || r.episode == null) return null
        val plugin = r.pluginId
        val content = r.contentUrl
        return when {
            plugin != null && !content.isNullOrBlank() -> plugin to content
            plugin == null && r.tmdbId > 0 -> TMDB_PROVIDER to "${MediaType.Tv.tmdbPath}:${r.tmdbId}"
            else -> null
        }
    }

    // ---- Play'in PlayerActivity'sindeki bağlantıların aynısı (çevrimdışı kopya TV'de yok) ----

    private fun loadStream(stream: PlayableStream, startMs: Long) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread { loadStream(stream, startMs) }
            return
        }
        val previous = currentStream
        if (previous != null && previous.url != stream.url && TorrentLinks.isMagnet(previous.url)) container.torrentEngine.closeActive()
        currentStream = stream
        skips.onSourceChanged(startMs)
        if (stream.dubbed) subtitles.suppressAuto() else subtitles.resumeAuto()
        subtitles.setStream(stream)
        if (previous != null) saveProgress()
        controller.load(stream, startMs)
    }

    /** Takılan akışı kaldığı saniyeden yeniden açar (aynı kaynak; yeni bağlantı). */
    private fun retryStream() {
        val stream = currentStream ?: return
        stalled = false
        stallSince = SystemClock.elapsedRealtime()
        loadStream(stream, controller.positionMs())
    }

    /** Elle seçilen ses dili korunur; yoksa (dublaj tercihi açıkken) Türkçe ses seçilir. Türkçe seste otomatik altyazı kapanır. */
    private fun syncAudio() {
        val tracks = controller.audioTracks()
        if (tracks.isEmpty()) return
        fun lang(t: AudioTrack) = SubtitleLanguages.normalize(t.language)
        val wanted = userAudioLanguage ?: if (preferDub) "tr" else null
        tracks.firstOrNull { wanted != null && lang(it) == wanted }?.takeIf { !it.selected }?.let { controller.selectAudio(it.index) }
        val selected = controller.audioTracks().firstOrNull { it.selected }
        val turkish = selected != null && lang(selected) == "tr" && (tracks.size > 1 || currentStream?.dubbed == true)
        if (turkish) subtitles.suppressAuto() else if (currentStream?.dubbed != true) subtitles.resumeAuto()
    }

    private fun onAudioChosen(track: AudioTrack) {
        userAudioLanguage = SubtitleLanguages.normalize(track.language)
        controller.selectAudio(track.index)
    }

    private suspend fun expectedRuntimeMinutes(request: StreamRequest): Int? = runCatching {
        val detail = container.tmdb.detail(request.type, request.tmdbId)
        val season = request.season
        val episode = request.episode
        if (request.type == MediaType.Tv && season != null && episode != null) {
            container.tmdb.season(request.tmdbId, season).episodes.firstOrNull { it.number == episode }?.runtimeMinutes ?: detail.runtimeMinutes
        } else detail.runtimeMinutes
    }.getOrNull()

    private suspend fun findNextEpisode(): Pair<Int, Int>? {
        val r = session.target.request
        val season = r.season ?: return null
        val episode = r.episode ?: return null
        val pluginId = r.pluginId
        val contentUrl = r.contentUrl
        if (pluginId != null && !contentUrl.isNullOrBlank()) {
            container.csNextEpisodes.next(pluginId, contentUrl, r.dataUrl, season, episode)?.let {
                nextCsDataUrl = it.dataUrl
                return it.season to it.episode
            }
        }
        return if (r.tmdbId > 0) container.nextEpisodes.next(r.tmdbId, season, episode) else null
    }

    /** Sonraki bölümün kaynakları (Play'in AutoNext'i bölüm bitmeden ~45 sn önce çağırır: geçişte arama beklenmez). */
    private suspend fun prepareNext(season: Int, episode: Int): NextPlayback? {
        val r = session.target.request
        val dataUrl = if (r.pluginId != null) nextCsDataUrl ?: r.dataUrl else r.dataUrl
        val request = r.copy(season = season, episode = episode, dataUrl = dataUrl, refresh = false)
        val target = session.target.copy(request = request)
        val playing = currentStream
        val found = AutoNextRules.collect(container.streams.streams(request), playing?.source?.addonId)
        val ordered = if (playing != null) AutoNextRules.order(found, playing) else found.ranked().take(AutoNextRules.MAX_CANDIDATES)
        return if (ordered.isEmpty()) null else NextPlayback.Online(target, ordered)
    }

    /** Sonraki bölüm yeni bir TV oynatıcısında açılır (altyazı, başlık, geçmiş o bölüm için sıfırdan). */
    private fun advanceTo(next: NextPlayback) {
        if (leaving || next !is NextPlayback.Online) return
        leaving = true
        Log.i(TAG, "sonraki bölüm: S${next.target.request.season} B${next.target.request.episode}, ${next.streams.size} kaynak hazır")
        lifecycleScope.launch {
            val saved = container.library.progress(next.target)
            val resumeAt = saved?.takeIf { WatchRules.isResumable(it.positionMs, it.durationMs) && container.settings.alwaysResume.first() }?.positionMs ?: 0L
            finishedForAdvance = true
            saveProgress()
            if (currentStream?.url?.let(TorrentLinks::isMagnet) == true) container.torrentEngine.closeActive()
            val nextSession = PlaySession(
                target = next.target, stream = next.streams.first(), startPositionMs = resumeAt, autoResumed = resumeAt > 0,
                fallbacks = next.streams.drop(1), fromAutoNext = true,
            )
            startActivity(nextSession.toIntent(this@TvPlayerActivity).setClass(this@TvPlayerActivity, TvPlayerActivity::class.java))
            @Suppress("DEPRECATION") overridePendingTransition(0, 0)
            finish()
        }
    }

    private val isLive get() = session.target.request.addonType in LIVE_TYPES

    /**
     * Ekranda gösterilen atlama düğmesinin segmenti (Play'in PlayerScreen kuralı): düğmeler açık ve tür açıkken, segmentin ilk
     * [SKIP_INITIAL_VISIBILITY_MS]'sinde ya da alt bilgi çubuğu görünürken. Panel, sonraki bölüm kartı, "Hâlâ izliyor musunuz?"
     * ya da hata varken gösterilmez. Hem ekran hem kumanda (OK atlar, Geri kapatır) bunu okur.
     */
    private fun shownSkip(): SkipSegment? {
        val s = skips.settings
        if (!s.showButtons || panel != Panel.None || stillWatching || stalled || autoNext.phase != AutoNextPhase.Hidden || controller.ui.error != null) return null
        val osd = SystemClock.elapsedRealtime() < osdUntil || !controller.ui.isPlaying
        return skips.active.firstOrNull { s.isEnabled(it.category) && (osd || skips.positionMs - it.startMs < SKIP_INITIAL_VISIBILITY_MS) }
    }

    /** Listede [delta] kadar ötedeki kanal (başa/sona sarar). */
    private fun stepChannel(delta: Int) {
        val list = LiveChannels.list
        if (list.isEmpty() || leaving) return
        val i = list.indexOfFirst { it.isChannelOf(session.target.request.addonContentId, session.target.request.pluginId) }
        val next = list[((if (i < 0) 0 else i + delta) % list.size + list.size) % list.size]
        leaving = true
        controller.player.pause()
        PlayLauncher(this, lifecycleScope).playLive(next, then = ::finish)
    }

    private fun saveProgress() {
        if (!::controller.isInitialized || session.noHistory) return
        if (session.target.request.addonType in listOf("tv", "channel")) return
        val duration = controller.durationMs()
        if (duration <= 0 || controller.ui.error != null) return
        val position = if (controller.ui.ended || finishedForAdvance) duration else controller.positionMs()
        val target = session.target
        val scope = libraryScope ?: return
        container.appScope.launch { container.library.saveProgress(target, position, duration, scope) }
    }

    private companion object {
        const val TAG = "PlayTvPlayer"

        /** Bu kadar süre tuşa basılmazsa oynatma durur ve "Hâlâ izliyor musunuz?" sorulur. */
        const val STILL_WATCHING_MS = 90 * 60_000L

        /** Bölümden bölüme geçişte (yeni etkinlik) sıfırlanmasın diye süreç düzeyinde. */
        var lastInputAt = SystemClock.elapsedRealtime()
    }
}

/** Sources..Quality: ▼ panelinin "Oynatıcı" satırlarından açılan ayar alt panelleri (Geri ▼ paneline döner). */
private enum class Panel { None, Episodes, Tracks, Channels, Sources, SubDelay, Speed, Resize, Quality }

/** Deneysel derin kaynak araması: yapılmadı / sürüyor / bitti / zaten hepsi aranmıştı / bellek sınırında kesildi. */
private enum class DeepSearch { Off, Running, Done, AllSearched, MemoryStopped }

private val SETTING_PANELS = setOf(Panel.Sources, Panel.SubDelay, Panel.Speed, Panel.Resize, Panel.Quality)

/** Bu katalog öğesi oynatılan kanal mı (playLive addonContentId = rawUrl ?: id). */
/** Oynayan kanal mı: içerik kimliği ve sağlayıcı (iki eklenti aynı kimliği verebilir). */
private fun CatalogItem.isChannelOf(contentId: String?, pluginId: String?) =
    contentId != null && (rawUrl ?: id) == contentId && (pluginId == null || providerId == pluginId)

@Composable
private fun TvPlayerScreen(
    session: PlaySession,
    controller: PlaybackController,
    subtitles: SubtitleController,
    autoNext: AutoNextController,
    stream: PlayableStream?,
    details: TvDetailModel?,
    stillWatching: Boolean,
    /** Gösterilecek atlama düğmesinin segmenti (her çizimde yeniden okunur: süreye bağlı görünürlük). */
    skip: () -> SkipSegment?,
    sourcePhase: SourcePhase,
    noStreams: Boolean,
    /** Kaynak yokken bot doğrulaması isteyen site: iletide "OK: Doğrula" görünür. */
    verifySite: VerifySite?,
    /** "Kaynak bulunamadı" ilk göründüğünde: etkinlik doğrulama isteyen siteyi arar. */
    onNoSource: () -> Unit,
    stalled: Boolean,
    panel: Panel,
    osdUntil: Long,
    onClosePanel: () -> Unit,
    /** Play'in kaynak düzenleyicisi: izlerken kaynak değiştir paneli onun listesini gösterir, seçimi ona verir. */
    sources: SourceCoordinator,
    onPanel: (Panel) -> Unit,
    deepSearch: DeepSearch,
    onDeepSearch: () -> Unit,
    onPickSource: (PlayableStream) -> Unit,
    onAudio: (AudioTrack) -> Unit,
    onEpisode: (DetailData, DetailEpisode) -> Unit,
    onChannel: (CatalogItem) -> Unit,
) {
    val ui = controller.ui
    val request = session.target.request
    val detailState = details?.state?.collectAsStateWithLifecycle()?.value
    val detail = (detailState as? DetailState.Ready)?.data
    val portrait = ui.videoWidth > 0 && ui.videoHeight > ui.videoWidth
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { delay(500); now = SystemClock.elapsedRealtime() } }
    LaunchedEffect(ui.tracksVersion) { subtitles.onTracksChanged() }
    val context = LocalContext.current
    val subtitleStyle by remember { (context.applicationContext as PlayApp).container.settings.subtitleStyle }
        .collectAsStateWithLifecycle(initialValue = null)
    val container = remember { (context.applicationContext as PlayApp).container }
    // Görüntü oranı Play'in ayarıyla aynı anahtarda (Play'deki "Boyut" düğmesi); dikey düzende video kendi oranındaki kutuda, uygulanmaz.
    val resize by remember { container.settings.resizeMode }.collectAsStateWithLifecycle(initialValue = ResizeMode.Fit)
    val shownResize = if (portrait) ResizeMode.Fit else resize
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    // Ayar alt panelinden Geri'yle dönünce ▼ panelinde odak o satıra gelir.
    var focusRow by remember { mutableStateOf(Panel.None) }
    LaunchedEffect(panel) { if (panel in SETTING_PANELS) focusRow = panel else if (panel == Panel.None) focusRow = Panel.None }
    // Torrent kaynağı açılırken (ilk kareye kadar) yükleme yüzdesi: Play'in torrent yükleme ekranıyla aynı hesap.
    val torrent = stream?.url?.let(TorrentLinks::isMagnet) == true
    var torrentPercent by remember(stream?.url) { mutableStateOf<Int?>(null) }
    LaunchedEffect(stream?.url, ui.firstFrameRendered) {
        if (!torrent || ui.firstFrameRendered) return@LaunchedEffect
        var progress = 0f
        while (true) {
            val snapshot = withContext(Dispatchers.IO) { container.torrentEngine.getLoadSnapshot() }
            val state = TorrentLoadProgressRules.safeCalculateProgress(snapshot, previousProgress = progress)
            if (state.fallbackToStandard) {
                torrentPercent = null
                break
            }
            progress = state.progress
            torrentPercent = progress.toInt()
            delay(500)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val videoWidth = if (portrait) maxHeight * (ui.videoWidth.toFloat() / ui.videoHeight) else maxWidth
        val sideWidth = (maxWidth - videoWidth) / 2
        val screenRatio = maxWidth / maxHeight
        val poster = TmdbImages.url(session.target.posterPath, TmdbImages.Size.BackdropLarge)
        if (portrait && poster != null) {
            AsyncImage(poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.22f))
        }
        Row(Modifier.fillMaxSize()) {
            Spacer(if (portrait) Modifier.weight(1f) else Modifier.width(0.dp))
            Box(
                if (portrait) Modifier.fillMaxHeight().aspectRatio(ui.videoWidth.toFloat() / ui.videoHeight) else Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Play'deki gibi: 16:9 ve 4:3'te görüntü o oranda bir alana uzatılır.
                val ratio = when (shownResize) {
                    ResizeMode.Ratio16x9 -> 16f / 9f
                    ResizeMode.Ratio4x3 -> 4f / 3f
                    else -> null
                }
                val viewModifier = when {
                    ratio == null -> Modifier.fillMaxSize()
                    screenRatio > ratio -> Modifier.fillMaxHeight().aspectRatio(ratio)
                    else -> Modifier.fillMaxWidth().aspectRatio(ratio)
                }
                AndroidView(
                    factory = { ctx ->
                        // Play'in düzeni (TextureView): PlaybackController yüzey hazır olunca yükler; SurfaceView bu kutuda
                        // ekranı beyaz bırakıyordu (ölçüm 2026-11-09).
                        (LayoutInflater.from(ctx).inflate(com.kaan.play.R.layout.view_player, null, false) as PlayerView).apply {
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = controller.player
                            isFocusable = false
                            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            post {
                                subtitles.attach { cues -> runCatching { subtitleView?.setCues(SubtitleCues.toCues(cues)) } }
                                val texture = videoSurfaceView as? TextureView
                                if (texture == null || texture.isAvailable) controller.onSurfaceReady()
                                if (texture != null) {
                                    val inner = texture.surfaceTextureListener
                                    texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                            inner?.onSurfaceTextureAvailable(st, w, h)
                                            controller.onSurfaceReady()
                                        }
                                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                                            inner?.onSurfaceTextureSizeChanged(st, w, h)
                                        }
                                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                            controller.onSurfaceDestroyed()
                                            return inner?.onSurfaceTextureDestroyed(st) ?: true
                                        }
                                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                                            inner?.onSurfaceTextureUpdated(st)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.resizeMode = when (shownResize) {
                            ResizeMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            ResizeMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                        subtitleStyle?.let { s -> view.subtitleView?.let { SubtitleStyling.apply(it, s) } }
                    },
                    onRelease = { view ->
                        controller.onSurfaceDestroyed()
                        subtitles.attach(null)
                        view.player = null
                    },
                    modifier = viewModifier,
                )
            }
            Box(if (portrait) Modifier.weight(1f).fillMaxHeight().background(Bg.copy(alpha = 0.85f)) else Modifier.width(0.dp)) {
                if (portrait) EpisodePanel(session, detail, focus = panel == Panel.Episodes, onEpisode = { d, ep ->
                    if (ep.season == request.season && ep.episode == request.episode) onClosePanel() else onEpisode(d, ep)
                })
            }
        }

        // Yükleniyor / hata. Kaynak araması süresiz sürerse (eklenti yanıt vermiyor) RESOLVE_GIVE_UP_MS sonra "bulunamadı" denir;
        // kaynak sonradan gelirse oynatma yine başlar ve ileti kalkar.
        val resolvingSince = remember(sourcePhase) { SystemClock.elapsedRealtime() }
        val noSource = !ui.firstFrameRendered && (sourcePhase == SourcePhase.Failed ||
            (sourcePhase == SourcePhase.Resolving && now - resolvingSince > RESOLVE_GIVE_UP_MS))
        LaunchedEffect(noSource && noStreams) { if (noSource && noStreams) onNoSource() }
        when {
            ui.error != null -> Message(stringResource(R.string.tv_play_video_failed), stringResource(R.string.tv_play_back_to_exit))
            noSource -> Message(
                when {
                    request.addonType in LIVE_TYPES -> stringResource(R.string.tv_play_channel_failed)
                    noStreams && verifySite != null -> stringResource(com.kaan.play.R.string.provider_verification_required) + " (" + verifySite.host + ")"
                    noStreams -> stringResource(if (request.episode != null) R.string.tv_play_no_sources_episode else R.string.tv_play_no_sources_title)
                    else -> stringResource(R.string.tv_play_all_failed)
                },
                stringResource(if (noStreams && verifySite != null) R.string.tv_play_verify_hint else R.string.tv_play_back_to_exit),
            )
            // Yeniden açılan akış hiç kare vermese de (ilk kare bayrağı sıfırlanır) "durdu" iletisi görünür.
            stalled -> Message(stringResource(R.string.tv_play_stalled), stringResource(R.string.tv_play_stalled_hint))
            !ui.firstFrameRendered && sourcePhase == SourcePhase.Resolving -> Message(stringResource(R.string.tv_play_searching_sources), null)
            !ui.firstFrameRendered -> Message(
                stringResource(R.string.tv_play_loading),
                listOfNotNull(stream?.source?.addonName, torrentPercent?.let { stringResource(R.string.tv_play_torrent_loading, it) }).joinToString(" · "),
            )
        }

        // Alt bilgi çubuğu: tuşa basınca, duraklatınca, arabelleğe alırken.
        val showOsd = panel == Panel.None && (now < osdUntil || !ui.isPlaying) && ui.firstFrameRendered
        // Dikey düzende çubuk sağdaki paneli örtmez.
        if (showOsd) Osd(session, stream, controller.positionMs(), controller.durationMs(), ui.isPlaying, ui.buffering,
            endPadding = if (portrait) sideWidth else 0.dp, hasEpisodes = detail?.episodes?.isNotEmpty() == true)

        if (!portrait && panel == Panel.Episodes) {
            SidePanel(null) { EpisodePanel(session, detail, focus = true, onEpisode = { d, ep ->
                if (ep.season == request.season && ep.episode == request.episode) onClosePanel() else onEpisode(d, ep)
            }) }
        }
        if (panel == Panel.Channels) {
            SidePanel(null) { ChannelPanel(LiveChannels.list, request.addonContentId, request.pluginId, onChannel = { c ->
                if (c.isChannelOf(request.addonContentId, request.pluginId)) onClosePanel() else onChannel(c)
            }) }
        }
        val videoOptions = remember(ui.tracksVersion) { controller.videoOptions() }
        if (panel == Panel.Tracks) {
            // Play oynatıcısının Kaynak / Senkron / Parçalar (kalite, hız) / Boyut düğmelerinin karşılığı; Senkron Play'deki gibi
            // yalnız dış altyazı seçiliyken, görüntü oranı yalnız yatay düzende, kalite yalnız birden çok seçenek varken.
            val pinned = if (controller.videoPinned()) videoOptions.firstOrNull { it.selected } else null
            val settings = buildList {
                add(SettingRow(Panel.Sources, stringResource(R.string.tv_play_source), stream?.let { listOfNotNull(it.source.addonName, resolutionText(it)).joinToString(" · ") } ?: "—"))
                if (subtitles.selected is SubtitleItem.External && subtitles.status == SubtitleStatus.Idle) {
                    add(SettingRow(Panel.SubDelay, stringResource(R.string.tv_play_sub_delay), stringResource(R.string.tv_play_sub_delay_value, signedSeconds(subtitles.offsetMs, locale))))
                }
                add(SettingRow(Panel.Speed, stringResource(R.string.tv_play_speed), speedText(ui.speed)))
                if (!portrait) add(SettingRow(Panel.Resize, stringResource(R.string.tv_play_resize), resizeText(resize)))
                if (videoOptions.size > 1) add(SettingRow(Panel.Quality, stringResource(R.string.tv_play_quality), pinned?.let { qualityText(it, locale) } ?: stringResource(R.string.tv_play_quality_auto)))
            }
            SidePanel(if (portrait) sideWidth else null) {
                TracksPanel(controller, subtitles, ui.tracksVersion, settings = settings, focusRow = focusRow, onOpen = onPanel, onAudio = onAudio, onClose = onClosePanel)
            }
        }
        if (panel == Panel.Sources) {
            SidePanel(if (portrait) sideWidth else null) { SourcesPanel(sources, locale, deepSearch, onDeepSearch, onPick = onPickSource) }
        }
        if (panel == Panel.SubDelay) SubDelayPanel(subtitles, locale)
        if (panel == Panel.Speed) {
            val labels = PlaybackSpeeds.ALL.map { speedText(it) }
            SidePanel(if (portrait) sideWidth else null) {
                ChoicePanel(stringResource(R.string.tv_play_speed), labels, PlaybackSpeeds.ALL.indexOf(ui.speed)) { i ->
                    controller.speed = PlaybackSpeeds.ALL[i]
                    Log.i(LOG_TAG, "hız: ${PlaybackSpeeds.ALL[i]}")
                    onClosePanel()
                }
            }
        }
        if (panel == Panel.Resize) {
            val labels = ResizeMode.entries.map { resizeText(it) }
            SidePanel(null) {
                ChoicePanel(stringResource(R.string.tv_play_resize), labels, resize.ordinal) { i ->
                    val mode = ResizeMode.entries[i]
                    scope.launch { container.settings.setResizeMode(mode) }
                    Log.i(LOG_TAG, "görüntü oranı: $mode")
                    onClosePanel()
                }
            }
        }
        if (panel == Panel.Quality) {
            val pinned = if (controller.videoPinned()) videoOptions.indexOfFirst { it.selected } else -1
            val labels = listOf(stringResource(R.string.tv_play_quality_auto)) + videoOptions.map { qualityText(it, locale) }
            SidePanel(if (portrait) sideWidth else null) {
                ChoicePanel(stringResource(R.string.tv_play_quality), labels, if (pinned >= 0) pinned + 1 else 0) { i ->
                    val option = if (i == 0) null else videoOptions[i - 1]
                    controller.selectVideo(option)
                    Log.i(LOG_TAG, "kalite: ${option?.let { "${it.width}x${it.height} ${it.bitrate}" } ?: "otomatik"}")
                    onClosePanel()
                }
            }
        }

        // Alt bilgi çubuğunun üstünde, sağda; dikey düzende video alanının içinde kalır.
        if (ui.firstFrameRendered) skip()?.let { SkipButton(it, endPadding = if (portrait) sideWidth else 0.dp) }
        AutoNextCard(autoNext)
        if (stillWatching) Message(stringResource(R.string.tv_play_still_watching), stringResource(R.string.tv_play_still_watching_hint))
    }
}

/** Basılı tutuldukça adım büyür: 10 sn, sonra 30 sn, sonra 60 sn. */
private fun seekStep(repeat: Int): Long = when {
    repeat < 6 -> 10_000L
    repeat < 20 -> 30_000L
    else -> 60_000L
}

private const val OSD_MS = 4_000L

/** Oynarken arabellek bu kadar ilerlemezse akış bir kez yeniden açılır, yine ilerlemezse kullanıcıya söylenir. */
private const val STALL_MS = 20_000L

/** Segment başladıktan sonra atlama düğmesi bu kadar süre çubuk gizliyken de görünür (Play: SKIP_INITIAL_VISIBILITY_MS). */
private const val SKIP_INITIAL_VISIBILITY_MS = 20_000L

/** Kaynak araması bu kadar sürer de oynatılacak kaynak çıkmazsa "bulunamadı" denir (TvPlayStartActivity 25 sn'yi önceden harcamış olabilir). */
private const val RESOLVE_GIVE_UP_MS = 45_000L

/** Derin arama bellek bekçisinin bakma aralığı. */
private const val DEEP_GUARD_MS = 1_000L

/**
 * Derin arama bu PSS'i aşınca ya da sistemin boş belleği [DEEP_MIN_AVAIL_MB]'ın altına inince kesilir. 2 GB'lık kutuda oynarken
 * ~300 MB. Ölçüm: sınır 700 MB iken bir eklentinin bot doğrulama tarayıcısı (WebView) PSS'i 6 sn'de 345 → 773 MB'a
 * çıkardı, kesildikten sonra da büyüdü ve sistem uygulamayı ~1 GB'da kapattı; sınır o gün 450 MB'a indirildi.
 * Asıl düzeltme Play'in kaynak aramasında ([com.kaan.play.data.cloudstream.CsSearchBudget]: az bellekli cihazda 3 eklenti aynı
 * anda, arama için yüklenen eklenti bırakılır, arama sırasında doğrulama tarayıcısı açılmaz); bekçi yalnız güvenlik ağı, bu
 * yüzden sınır 600 MB. Java belleği için sınır üst sınırın dörtte üçü (çöp toplandıktan sonra), ayrıca sistem "bellek az" derse kesilir.
 */
private const val DEEP_PSS_LIMIT_MB = 600L
private const val DEEP_MIN_AVAIL_MB = 400L

@Composable
private fun BoxScope.Message(title: String, sub: String?) {
    Column(
        Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Bg.copy(alpha = 0.8f)).padding(horizontal = 32.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (!sub.isNullOrBlank()) Text(sub, color = TextDim, fontSize = 16.sp)
    }
}

@Composable
private fun BoxScope.Osd(session: PlaySession, stream: PlayableStream?, position: Long, duration: Long, playing: Boolean, buffering: Boolean, endPadding: Dp, hasEpisodes: Boolean) {
    val r = session.target.request
    Column(
        Modifier
            .align(Alignment.BottomStart)
            .padding(end = endPadding)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
            .padding(horizontal = Gutter, vertical = 28.dp),
    ) {
        Text(session.target.title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val meta = listOfNotNull(
            r.episode?.let { stringResource(R.string.tv_play_season_episode, r.season ?: 1, it) },
            stream?.source?.addonName,
            when {
                buffering -> stringResource(R.string.tv_play_loading)
                playing -> null
                else -> stringResource(R.string.tv_play_paused)
            },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, color = TextDim, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.25f))) {
            val f = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Accent))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${clock(position)} / ${clock(duration)}", color = TextMain, fontSize = 14.sp)
            Text(stringResource(if (hasEpisodes) R.string.tv_play_osd_hint_episodes else R.string.tv_play_osd_hint), color = TextDim, fontSize = 14.sp)
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun BoxScope.SidePanel(width: Dp?, content: @Composable () -> Unit) {
    // Dikey düzende sağ sütunu tam örter (alttaki bölüm listesi görünmesin); yatayda video üstünde yarı saydam.
    val size = if (width != null) Modifier.width(width).background(Bg) else Modifier.fillMaxWidth(0.36f).background(Bg.copy(alpha = 0.88f))
    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().then(size)) { content() }
}

@Composable
private fun EpisodePanel(session: PlaySession, detail: DetailData?, focus: Boolean, onEpisode: (DetailData, DetailEpisode) -> Unit) {
    val r = session.target.request
    Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 36.dp)) {
        Text(detail?.title ?: session.target.title, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        r.episode?.let { Text(stringResource(R.string.tv_play_season_episode, r.season ?: 1, it), color = Accent, fontSize = 15.sp) }
        detail?.plot?.let { Text(it, color = TextDim, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(Modifier.height(16.dp))
        val episodes = detail?.episodes.orEmpty()
        if (detail == null || episodes.isEmpty()) return@Column
        val current = episodes.indexOfFirst { it.season == r.season && it.episode == r.episode }.coerceAtLeast(0)
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 2).coerceAtLeast(0))
        val currentFocus = remember { FocusRequester() }
        LaunchedEffect(focus) {
            if (focus) {
                list.scrollToItem((current - 2).coerceAtLeast(0))
                runCatching { currentFocus.requestFocus() }
            }
        }
        val seasons = detail.seasons.size > 1
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
            // Sıra numarası anahtarda: eklenti aynı sezon/bölüm numarasını farklı bağlantıyla iki kez verebilir (altyazılı +
            // dublaj); yalnız numara yinelenen anahtar çökmesi veriyordu.
            itemsIndexed(episodes, key = { i, e -> "$i-${e.season}-${e.episode}" }) { i, ep ->
                val playing = i == current
                val name = ep.name?.takeIf { it.isNotBlank() && !it.contains("Bölüm", ignoreCase = true) }
                PanelRow(
                    text = (if (seasons) "S${ep.season} · " else "") + stringResource(R.string.tv_play_episode, ep.episode) + (name?.let { " · $it" } ?: ""),
                    marked = playing,
                    focusable = focus,
                    modifier = if (playing) Modifier.focusRequester(currentFocus) else Modifier,
                    onClick = { onEpisode(detail, ep) },
                )
            }
        }
    }
}

/** Canlı yayında sağdan açılan kanal listesi: numara, logo, kanal adı; odak oynayan kanalda başlar. */
@Composable
private fun ChannelPanel(channels: List<CatalogItem>, playingId: String?, playingProvider: String?, onChannel: (CatalogItem) -> Unit) {
    val current = channels.indexOfFirst { it.isChannelOf(playingId, playingProvider) } // -1: oynayan listede yok, hiçbiri işaretlenmez
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { currentFocus.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
        Text(stringResource(R.string.tv_play_channel_list), color = Accent, fontSize = TvText.Row, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
        LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(channels, key = { _, c -> "${c.providerId}|${c.rawUrl ?: c.id}" }) { i, c ->
                ChannelRow(i + 1, c, playing = i == current, modifier = if (i == current.coerceAtLeast(0)) Modifier.focusRequester(currentFocus) else Modifier) { onChannel(c) }
            }
        }
    }
}

@Composable
private fun ChannelRow(number: Int, channel: CatalogItem, playing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (focused) Surface1 else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$number", color = if (playing) Accent else TextDim, fontSize = TvText.Meta, modifier = Modifier.width(28.dp))
        Box(Modifier.width(64.dp).height(36.dp).clip(RoundedCornerShape(4.dp)).background(Bg.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            if (channel.posterUrl != null) {
                AsyncImage(channel.posterUrl, channel.title, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            channel.title, color = if (playing) Accent else TextMain, fontSize = TvText.Body,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (playing) Text(stringResource(R.string.tv_play_live_badge), color = LiveRed, fontSize = TvText.Label, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class) // LocalBringIntoViewSpec
@Composable
private fun TracksPanel(
    controller: PlaybackController,
    subtitles: SubtitleController,
    version: Int,
    /** Üstteki "Oynatıcı" satırları; OK ilgili alt paneli açar. */
    settings: List<SettingRow>,
    /** Alt panelden dönülüyorsa odaklanacak satır; yoksa odak eskisi gibi ilk ses/altyazı satırında. */
    focusRow: Panel,
    onOpen: (Panel) -> Unit,
    onAudio: (AudioTrack) -> Unit,
    onClose: () -> Unit,
) {
    val audio = remember(version) { controller.audioTracks() }
    val first = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val returning = settings.any { it.panel == focusRow }
    LaunchedEffect(Unit) { runCatching { (if (returning) rowFocus else first).requestFocus() } }
    // TV'nin varsayılan odak kaydırması odaklanan satırı listenin ~%30'una çekiyor; ilk odak ses/altyazıdayken üstteki "Oynatıcı"
    // satırları kesiliyordu (küçük TV ekranı). Bu listede yalnız görünmeyen satır için gerektiği kadar kaydırılır.
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalScroll) {
    LazyColumn(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 36.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (settings.isNotEmpty()) {
            item { Header(stringResource(R.string.tv_play_settings)) }
            itemsIndexed(settings, key = { _, r -> "set-${r.panel}" }) { _, row ->
                PanelRow(
                    text = stringResource(R.string.tv_play_setting_row, row.label, row.value),
                    marked = false,
                    modifier = if (row.panel == focusRow) Modifier.focusRequester(rowFocus) else Modifier,
                    onClick = { onOpen(row.panel) },
                )
            }
        }
        if (audio.size > 1) {
            item { Header(stringResource(R.string.tv_play_audio)) }
            itemsIndexed(audio) { i, t ->
                PanelRow(
                    text = listOfNotNull(languageName(t.language), t.label?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { stringResource(R.string.tv_play_audio_track, t.index + 1) },
                    marked = t.selected,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { onAudio(t); onClose() },
                )
            }
        }
        item { Header(stringResource(R.string.tv_play_subtitles)) }
        item {
            PanelRow(
                text = stringResource(R.string.tv_off),
                marked = subtitles.selected == null,
                modifier = if (audio.size <= 1) Modifier.focusRequester(first) else Modifier,
                onClick = { subtitles.choose(null); onClose() },
            )
        }
        itemsIndexed(subtitles.items) { _, item ->
            val text = when (item) {
                is SubtitleItem.Embedded -> stringResource(
                    R.string.tv_play_embedded,
                    listOfNotNull(languageName(item.language), item.label).joinToString(" · ").ifEmpty { stringResource(R.string.tv_play_language_unknown) },
                )
                is SubtitleItem.External -> listOfNotNull(languageName(item.language), item.track.origin.toString().takeIf { it.isNotBlank() }).joinToString(" · ")
            }
            PanelRow(text = text, marked = subtitles.selected == item, onClick = { subtitles.choose(item); onClose() })
        }
    }
    }
}

/** Odaktaki satır görünür alandaysa kaydırmaz; dışındaysa yalnız gerektiği kadar kaydırır. */
@OptIn(ExperimentalFoundationApi::class)
private val MinimalScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < 0f -> offset
        offset + size > containerSize -> offset + size - containerSize
        else -> 0f
    }
}

/** ▼ panelindeki bir "Oynatıcı" satırı: açtığı alt panel, ad ve şu anki değer. */
private data class SettingRow(val panel: Panel, val label: String, val value: String)

/** Alt panellerdeki seçimlerin logu (cihaz denemesinde kanıt). */
private const val LOG_TAG = "PlayTvPlayer"

@Composable
private fun resolutionText(stream: PlayableStream): String? = stream.resolution?.let { if (it == 2160) "4K" else "${it}p" }

@Composable
private fun speedText(speed: Float): String =
    if (speed == PlaybackSpeeds.DEFAULT) stringResource(R.string.tv_play_speed_normal) else PlaybackSpeeds.formatMultiplier(speed)

@Composable
private fun resizeText(mode: ResizeMode): String = when (mode) {
    ResizeMode.Fit -> stringResource(R.string.tv_play_resize_fit)
    ResizeMode.Fill -> stringResource(R.string.tv_play_resize_fill)
    ResizeMode.Stretch -> stringResource(R.string.tv_play_resize_stretch)
    ResizeMode.Ratio16x9 -> "16:9"
    ResizeMode.Ratio4x3 -> "4:3"
}

private fun qualityText(option: com.kaan.play.ui.player.VideoOption, locale: Locale): String =
    TrackLabels.video(option.height, option.bitrate, locale).ifBlank { "${option.width}×${option.height}" }

/** "+0,5" / "-1,0" / "0,0" (Play'in senkron panelindeki biçim). */
private fun signedSeconds(offsetMs: Long, locale: Locale): String =
    if (offsetMs == 0L) String.format(locale, "%.1f", 0.0) else String.format(locale, "%+.1f", offsetMs / 1000.0)

/** Tek seçimli liste (hız, görüntü oranı, kalite): odak seçili satırda başlar, OK seçer. */
@Composable
private fun ChoicePanel(title: String, labels: List<String>, selected: Int, onChoose: (Int) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val start = selected.coerceAtLeast(0)
    LazyColumn(
        Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 36.dp),
        state = rememberLazyListState(initialFirstVisibleItemIndex = (start - 3).coerceAtLeast(0)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { Header(title) }
        itemsIndexed(labels) { i, label ->
            PanelRow(text = label, marked = i == selected, modifier = if (i == start) Modifier.focusRequester(focus) else Modifier, onClick = { onChoose(i) })
        }
    }
}

/**
 * İzlerken kaynak değiştir: Play'in kaynak panelinin sol sütunu (kaynaklar eklentiye göre gruplu, önerilen sırada; dublaj/altyazı,
 * çözünürlük, boyut, torrent/doğrudan; çalan işaretli). OK seçer: Play'in [SourceCoordinator.pick]'i kaldığı saniyeden geçer.
 * Liste, Play'in oynatma başında bulduklarıdır (arama ilk kareden 10 sn sonra durur; o sırada açılırsa gelenler canlı eklenir).
 * Telefondaki gibi panel açılınca arama YENİDEN BAŞLATILMAZ: 2 GB'lık kutuda oynarken tüm eklentilerde yeniden arama süreci
 * ~1 GB'a çıkardı ve sistem uygulamayı kapattı (lowmemorykiller).
 */
@Composable
private fun SourcesPanel(sources: SourceCoordinator, locale: Locale, deepSearch: DeepSearch, onDeepSearch: () -> Unit, onPick: (PlayableStream) -> Unit) {
    val ctx = sources.ctx
    val current = sources.current?.takeIf { sources.phase == SourcePhase.Playing || sources.phase == SourcePhase.Starting }
    val shown = SourceRanking.sorted(sources.streams, SourceSort.Recommended, ctx)
    val groups = shown.groupBy { it.source.addonId }.values.toList()
    val target = current?.url?.takeIf { u -> shown.any { it.url == u } } ?: shown.firstOrNull()?.url
    val focus = remember { FocusRequester() }
    val list = rememberLazyListState()
    LaunchedEffect(target != null) {
        if (target == null) return@LaunchedEffect
        // Başlıklarla birlikte sıra: çalan satıra kaydırıp odaklan.
        var index = 1
        for (g in groups) {
            index++
            val i = g.indexOfFirst { it.url == target }
            if (i >= 0) { index += i; break }
            index += g.size
        }
        list.scrollToItem((index - 3).coerceAtLeast(0))
        runCatching { focus.requestFocus() }
    }
    var confirmDeep by remember { mutableStateOf(false) }
    if (confirmDeep) ActionDialog(
        title = stringResource(R.string.tv_play_deep_confirm_title),
        message = stringResource(R.string.tv_play_deep_confirm_body),
        actions = listOf(stringResource(R.string.tv_play_deep_confirm_ok) to onDeepSearch),
        onDismiss = { confirmDeep = false },
        focusCancel = true,
    )
    val dubbed = stringResource(R.string.tv_play_tag_dubbed)
    val subbed = stringResource(R.string.tv_play_tag_subbed)
    val torrent = stringResource(R.string.tv_play_tag_torrent)
    val direct = stringResource(R.string.tv_play_tag_direct)
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 36.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(key = "title") { Header(stringResource(R.string.tv_play_source_switch)) }
        // Deneysel derin arama: oynatma başındaki arama sürerken gösterilmez (aşağıda "aranıyor" yazar).
        if (deepSearch == DeepSearch.Running) item(key = "deep") {
            Text(stringResource(R.string.tv_play_deep_running), color = Accent, fontSize = 14.sp, modifier = Modifier.padding(vertical = 6.dp))
        } else if (!sources.loading) item(key = "deep") {
            Column {
                // Bir kez: arama bittikten (ya da kesildikten) sonra Play yeniden aramaz, satır yalnız sonucu söyler.
                if (deepSearch == DeepSearch.Off) PanelRow(text = stringResource(R.string.tv_play_deep_search), marked = false, onClick = { confirmDeep = true })
                val note = when (deepSearch) {
                    DeepSearch.Done -> R.string.tv_play_deep_done
                    DeepSearch.AllSearched -> R.string.tv_play_deep_all_searched
                    DeepSearch.MemoryStopped -> R.string.tv_play_deep_memory_stopped
                    else -> null
                }
                note?.let { Text(stringResource(it), color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp, top = 2.dp)) }
            }
        }
        groups.forEach { rows ->
            item(key = "h-${rows.first().source.addonId}") {
                Text(rows.first().source.addonName, color = TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            }
            itemsIndexed(rows, key = { _, s -> "s-${s.url}" }) { _, s ->
                val size = if (ctx.isLive) null else SizeText.format(SourceRanking.sizeOf(s, ctx.sizes), locale)
                val text = listOfNotNull(if (s.dubbed) dubbed else subbed, resolutionText(s), size, if (TorrentLinks.isMagnet(s.url)) torrent else direct).joinToString(" · ")
                PanelRow(text = text, marked = s.url == current?.url, modifier = if (s.url == target) Modifier.focusRequester(focus) else Modifier, onClick = { onPick(s) })
            }
        }
        if (sources.loading) item(key = "loading") { Text(stringResource(R.string.tv_play_sources_checking), color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)) }
        else if (shown.isEmpty()) item(key = "empty") { Text(stringResource(R.string.tv_play_sources_empty), color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

/**
 * Altyazı gecikmesi (Play'in senkron paneli): üstte ortada, video karartılmaz; altyazı alttaki yerinde görünmeye devam eder.
 * ◀ ▶ düğmeler arasında gezer, OK uygular (Play'deki adımlar: ±0,5 / ±0,1 sn), Geri ▼ paneline döner. Değer Play gibi altyazı
 * başına saklanır ([SubtitleController.adjustOffset]).
 */
@Composable
private fun BoxScope.SubDelayPanel(subtitles: SubtitleController, locale: Locale) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        Modifier.align(Alignment.TopCenter).padding(top = 36.dp).clip(RoundedCornerShape(12.dp)).background(Bg.copy(alpha = 0.9f))
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.tv_play_sub_delay), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.tv_play_sub_delay_value, signedSeconds(subtitles.offsetMs, locale)), color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.tv_play_sub_delay_hint), color = TextDim, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-SubtitleOffset.COARSE_MS, -SubtitleOffset.FINE_MS, SubtitleOffset.FINE_MS, SubtitleOffset.COARSE_MS).forEachIndexed { i, delta ->
                DelayButton(String.format(locale, "%+.1f", delta / 1000.0), if (i == 0) Modifier.focusRequester(first) else Modifier) {
                    subtitles.adjustOffset(delta)
                    Log.i(LOG_TAG, "altyazı gecikmesi: ${subtitles.offsetMs} ms")
                }
            }
            DelayButton(stringResource(R.string.tv_play_sub_delay_reset)) {
                subtitles.resetOffset()
                Log.i(LOG_TAG, "altyazı gecikmesi sıfırlandı")
            }
        }
    }
}

@Composable
private fun DelayButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Accent else Surface1)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(text, color = if (focused) Bg else TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun languageName(code: String?): String? = when (SubtitleLanguages.normalize(code)) {
    null -> null
    "tr" -> stringResource(R.string.tv_play_lang_turkish)
    "en" -> stringResource(R.string.tv_play_lang_english)
    else -> code?.uppercase()
}

@Composable
private fun Header(text: String) {
    Text(text, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

/** [focusable] false: dikey düzende panel kapalıyken görünen liste odak almaz (tuşlar oynatıcıda). */
@Composable
private fun PanelRow(text: String, marked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, focusable: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Accent else if (marked) Surface1 else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusable) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            (if (marked) "▶  " else "") + text,
            color = if (focused) Bg else if (marked) Accent else TextMain,
            fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "İntroyu geç" düğmesi. Tuşlar etkinlik düzeyinde işlendiği için Compose odağı almaz; görünürken OK onu çalıştırır, bu yüzden
 * hep odaklı (vurgulu) çizilir.
 */
@Composable
private fun BoxScope.SkipButton(segment: SkipSegment, endPadding: Dp) {
    val label = when (segment.kind) {
        SkipSegmentKind.Intro -> stringResource(R.string.tv_play_skip_intro)
        SkipSegmentKind.Recap -> stringResource(R.string.tv_play_skip_recap)
        SkipSegmentKind.Outro -> stringResource(R.string.tv_play_skip_credits)
        SkipSegmentKind.Preview -> stringResource(R.string.tv_play_skip_preview)
    }
    Box(
        Modifier.align(Alignment.BottomEnd).padding(end = endPadding + Gutter, bottom = 130.dp)
            .clip(RoundedCornerShape(24.dp)).background(Accent).padding(horizontal = 24.dp, vertical = 12.dp),
    ) { Text("$label  ·  OK", color = Bg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun BoxScope.AutoNextCard(autoNext: AutoNextController) {
    val text = when (autoNext.phase) {
        AutoNextPhase.Hidden -> return
        AutoNextPhase.Countdown -> stringResource(R.string.tv_play_next_countdown, autoNext.episode, autoNext.secondsLeft)
        AutoNextPhase.Loading -> stringResource(R.string.tv_play_next_loading)
        AutoNextPhase.NoSource -> stringResource(R.string.tv_play_next_no_source)
    }
    Box(
        Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = Gutter).clip(RoundedCornerShape(10.dp))
            .background(Bg.copy(alpha = 0.9f)).padding(horizontal = 20.dp, vertical = 12.dp),
    ) { Text(text, color = TextMain, fontSize = 16.sp) }
}

/** Canlı TV hedefi (Play: addonType tv/channel). */
private val LIVE_TYPES = setOf("tv", "channel")
