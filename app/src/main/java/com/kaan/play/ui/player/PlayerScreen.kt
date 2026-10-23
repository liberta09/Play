package com.kaan.play.ui.player

import android.content.Context
import android.graphics.Color as AndroidColor
import com.kaan.play.BuildConfig
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kaan.play.R
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.settings.ResizeMode
import com.kaan.play.data.settings.SubtitleStyle
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.SizeText
import com.kaan.play.data.subtitle.SubtitleItem
import com.kaan.play.i18n.AppLanguage
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.FilledIconButton
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.components.TextButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.settings.SubtitlePreview
import com.kaan.play.ui.settings.SubtitleStyleControls
import com.kaan.play.ui.sources.SourcesSheet
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.tv.requestWhenAttached
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val HIDE_AFTER_MS = 4_000L
private const val RESUME_NOTICE_MS = 5_000L
private const val SEEK_HOLD_MS = 1_200L
private const val NOTICE_MS = 4_000L
private const val SOURCE_NOTICE_MS = 5_000L
private const val RESIZE_LABEL_MS = 1_500L

/**
 * Tam ekran oynatıcı. Üst çubuk: geri, çalan kaynağın adı · çözünürlüğü · dosya boyutu, altında film adı. Ortada 10 sn geri / oynat-duraklat /
 * 10 sn ileri; altta arama çubuğu ve düğmeler: Kilitle, Yeniden boyutlandır, Altyazı senkronu (altyazı seçiliyken), Kaynak, Parçalar.
 * Hepsi dokunmayla ve yön tuşlarıyla (D-pad/klavye/kumanda) kullanılır: kontroller gizliyken Orta/Enter oynat-duraklatır... Kaynak/altyazı
 * ve Parçalar iki sütunlu panellerdir (Uygula / İptal). Kilitliyken dokunma ve jestler kapanır, yalnızca açma simgesi görünür.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    controller: PlaybackController,
    subtitles: SubtitleController,
    target: PlayTarget,
    showResumeNotice: Boolean,
    /** false: indirilmiş kopya oynuyor, internetten başka kaynak seçilemez (kaynak paneli yalnızca altyazı içindir). */
    canSwitchSource: Boolean = true,
    /** Dikey kaydırmayla ses/parlaklık; null: jest yok (TV/kumanda). */
    levels: DeviceLevels? = null,
    /** Dizide sonraki bölüm kartı ve geçişi; null: yok (film). */
    autoNext: AutoNextController? = null,
    /** "İptal" ya da kart açıkken Geri: kart kapanır; video bitmişse etkinlik de kapanır. */
    onAutoNextCancel: () -> Unit = {},
    /** Dizide sonraki bölüm düğmesine basıldığında çağrılır; null: sonraki bölüm düğmesi yok (film). */
    onNextEpisode: (() -> Unit)? = null,
    /** Kaynak seçimi (çevrimiçi); null: indirilmiş kopya. */
    coordinator: SourceCoordinator? = null,
    /** İndirilmiş kopyanın kaynak bilgisi (üst çubuk için). */
    offlineStream: PlayableStream? = null,
    /** Seçili ses parçası Türkçe (dosyanın içinde dublaj): "Dublajlı (gömülü ses)". */
    embeddedDub: Boolean = false,
    /** Kullanıcı sesi Parçalar panelinden seçti. */
    onAudioChosen: (Int) -> Unit = {},
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val subtitleStyle by container.settings.subtitleStyle.collectAsStateWithLifecycle(initialValue = SubtitleStyle())
    val resize by container.settings.resizeMode.collectAsStateWithLifecycle(initialValue = ResizeMode.Fit)
    val locale = remember { Locale.forLanguageTag(AppLanguage.effective().iso) }

    val ui = controller.ui
    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var showPanel by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    val lockHint = remember { LockHintState() }
    var lockHintVisible by remember { mutableStateOf(false) }
    var unlockFocused by remember { mutableStateOf(false) }
    var lockHintTrigger by remember { mutableIntStateOf(0) }

    fun showUnlockHint() {
        lockHint.show(SystemClock.uptimeMillis())
        lockHintVisible = true
        lockHintTrigger++
    }
    var resizeToast by remember { mutableStateOf<ResizeMode?>(null) }
    var lastSeekKeyAt by remember { mutableLongStateOf(0L) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    // "Kaldığın yerden devam ediliyor · Baştan başla": ~5 sn; düğmede odak varken (kumanda) süre işlemez.
    var resumeNotice by remember { mutableStateOf(showResumeNotice) }
    var noticeFocused by remember { mutableStateOf(false) }
    val zones = remember { GestureZones() }
    val levelIndicator = remember { LevelIndicatorState() }
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val noticeFocus = remember { FocusRequester() }
    val barFocus = remember { FocusRequester() }
    val bottomFocus = remember { FocusRequester() }
    val unlockFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var barFocused by remember { mutableStateOf(false) }
    val keyboard = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    // "Dosyadan altyazı ekle": sistem dosya seçicisi (.srt / .vtt / .ass); seçilen dosya hemen açılır, oynatma sürdürülür.
    val context = LocalContext.current
    var resumeAfterPick by remember { mutableStateOf(false) }
    val pickSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) subtitles.addLocal(uri.toString(), displayName(context, uri))
        if (resumeAfterPick) controller.player.play()
        resumeAfterPick = false
    }

    val phase = coordinator?.phase ?: SourcePhase.Playing
    val resolving = phase == SourcePhase.Resolving
    val sourcesFailed = phase == SourcePhase.Failed

    // Sonraki bölüm kartı: kumandada odak ilk düğmededir (Tamam = "Şimdi oynat", Geri = "İptal").
    val cardPhase = autoNext?.phase ?: AutoNextPhase.Hidden
    val cardActive = cardPhase != AutoNextPhase.Hidden
    val cardFocus = remember { FocusRequester() }
    var cardFocused by remember { mutableStateOf(false) }
    // "Dublajlı kaynak bulundu, geçilsin mi?" (köşede; sonraki bölüm kartı varken gösterilmez).
    val dubOffer = coordinator?.dubSuggestion
    val dubVisible = dubOffer != null && !cardActive && !locked && ui.error == null
    val dubFocus = remember { FocusRequester() }
    var dubFocused by remember { mutableStateOf(false) }

    val dialogOpen = showPanel || showTracks || showAppearance || showSync || autoNext?.picking == true
    // Senkron paneli açıkken kontroller gizlenir: altyazı (altta) ilerleme çubuğunun altında kalmasın.
    // Kaynaklar aranırken ve hiçbiri açılmadığında geri düğmesi ve kontroller görünür kalır.
    val showControls = !locked && ((controlsVisible && !showSync) || ui.error != null || resolving || sourcesFailed)

    fun bump() { interaction++ }
    fun reveal() { controlsVisible = true; bump() }
    fun seek(deltaMs: Long) {
        controller.seekBy(deltaMs)
        position = controller.positionMs()
        reveal()
    }
    fun seekByKey(direction: Int, repeat: Int) {
        val step = SeekAcceleration.stepMs(repeat)
        lastSeekKeyAt = SystemClock.uptimeMillis()
        if (step > 0) seek(direction * step) else bump()
    }
    fun nextResize() {
        val next = ResizeMode.entries[(resize.ordinal + 1) % ResizeMode.entries.size]
        scope.launch { container.settings.setResizeMode(next) }
        resizeToast = next
        bump()
    }
    fun openPanel() {
        showPanel = true
        bump()
    }

    // Kontroller bir süre dokunulmazsa (ve oynatma sürerken) kendiliğinden gizlenir.
    LaunchedEffect(controlsVisible, interaction, ui.isPlaying, dialogOpen, ui.error, sourcesFailed) {
        if (controlsVisible && ui.isPlaying && !dialogOpen && ui.error == null && !sourcesFailed) {
            delay(HIDE_AFTER_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(resumeNotice, noticeFocused) {
        if (resumeNotice && !noticeFocused) {
            delay(RESUME_NOTICE_MS)
            resumeNotice = false
        }
    }
    LaunchedEffect(ui.error) { if (ui.error != null) resumeNotice = false }
    LaunchedEffect(ui.tracksVersion) { subtitles.onTracksChanged() }
    // "Altyazı yüklenemedi" kısa süre görünür.
    LaunchedEffect(subtitles.status) {
        if (subtitles.status == SubtitleStatus.Failed) {
            delay(NOTICE_MS)
            subtitles.clearStatus()
        }
    }
    // "Altyazı çevrilemedi" kısa süre görünür.
    LaunchedEffect(subtitles.translationState) {
        if (subtitles.translationState is TranslationState.Failed) {
            delay(NOTICE_MS)
            subtitles.clearTranslationNotice()
        }
    }
    // Kaynak bildirimi ("kaynak açılamadı, önceki kaynağa dönüldü") kısa süre görünür.
    LaunchedEffect(coordinator?.notice) {
        if (coordinator?.notice != null) {
            delay(SOURCE_NOTICE_MS)
            coordinator.clearNotice()
        }
    }
    LaunchedEffect(resizeToast) {
        if (resizeToast != null) {
            delay(RESIZE_LABEL_MS)
            resizeToast = null
        }
    }
    LaunchedEffect(locked) {
        if (locked) {
            controlsVisible = false
            showUnlockHint()
        } else {
            lockHint.hide()
            lockHintVisible = false
            reveal()
        }
    }
    LaunchedEffect(locked, lockHintTrigger) {
        if (locked) {
            val remaining = lockHint.remainingMs(SystemClock.uptimeMillis())
            if (remaining > 0L) {
                lockHintVisible = true
                delay(remaining)
            }
            lockHintVisible = false
            unlockFocused = false
        } else {
            lockHint.hide()
            lockHintVisible = false
            unlockFocused = false
        }
    }
    LaunchedEffect(cardActive) { if (!cardActive) cardFocused = false }
    LaunchedEffect(dubVisible) { if (!dubVisible) dubFocused = false }
    // Odak: kilitliyken ve simge görünürken açma simgesinde; kilitli ve simge gizliyken kök yüzeyde;
    // hata/kaynak yokken hata düğmesinde; sonraki bölüm kartında; kontroller açıkken ilerleme
    // çubuğunda; gizliyken kök yüzeyde (yön tuşları orada yakalanır).
    LaunchedEffect(controlsVisible, ui.error, sourcesFailed, cardPhase, locked, lockHintVisible, dubVisible, keyboard) {
        when {
            locked && lockHintVisible -> unlockFocus.requestWhenAttached()
            locked -> runCatching { rootFocus.requestFocus() }
            ui.error != null || sourcesFailed -> runCatching { playFocus.requestFocus() }
            cardActive -> cardFocus.requestWhenAttached()
            dubVisible && keyboard -> dubFocus.requestWhenAttached()
            controlsVisible -> barFocus.requestWhenAttached()
            else -> runCatching { rootFocus.requestFocus() }
        }
    }
    LaunchedEffect(showControls) {
        while (showControls) {
            position = controller.positionMs()
            duration = controller.durationMs()
            delay(500)
        }
    }

    val effectiveLevels = if (locked) null else levels

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { event ->
                // Geri tuşu burada ele alınır: aralıklı olarak (kontroller açıkken) görünüm ağacındaki bir öğe
                // DOWN olayını tüketiyor ve etkinliğe hiç ulaşmıyordu; kök yakalayıcı ise olayı her zaman görür.
                if (event.key == Key.Back) {
                    // Kilitliyken Geri kilidi açar; dublaj önerisi ya da sonraki bölüm kartı açıkken kartı kapatır (oynatıcıyı kapatmaz).
                    if (event.type == KeyEventType.KeyUp) {
                        when {
                            locked -> locked = false
                            dubVisible -> coordinator?.dismissDubSuggestion()
                            cardActive -> onAutoNextCancel()
                            else -> onBack()
                        }
                    }
                    return@onPreviewKeyEvent true
                }
                // İlerleme çubuğu odaktayken Sol/Sağ/Tamam'ın KeyUp'ı da burada tüketilir: KeyDown'ı biz işliyoruz,
                // KeyUp Slider'a giderse kendi "sürükleme bitti" mantığını çalıştırıp konumu bozar.
                if (event.type == KeyEventType.KeyUp &&
                    PlayerKeys.consumesBarKeyUp(event.key, controlsVisible = showControls, progressFocused = barFocused)
                ) return@onPreviewKeyEvent true
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (locked) {
                    if (event.key in UNLOCK_TRIGGER_KEYS) {
                        if (lockHintVisible && unlockFocused && event.key in UNLOCK_ENTER_KEYS) {
                            locked = false
                        } else {
                            showUnlockHint()
                            runCatching { unlockFocus.requestFocus() }
                        }
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent when (event.key) {
                        Key.VolumeUp, Key.VolumeDown, Key.VolumeMute -> false
                        else -> true
                    }
                }
                bump()
                // Kart açıkken yön tuşları ve Tamam kartın düğmelerine kalır (sarma/oynat-duraklat yerine).
                if (cardActive && event.key in CARD_KEYS) {
                    if (cardFocused) return@onPreviewKeyEvent false
                    // Odak karta henüz geçmedi (ör. dokunmatik moddayken kumanda tuşuna basıldı): Tamam ana düğmeye basar,
                    // yön tuşu odağı karta alır; olay kökün "kontrolleri aç/kapat" tıklamasına düşmez.
                    return@onPreviewKeyEvent when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            when (cardPhase) {
                                AutoNextPhase.Countdown -> autoNext?.playNow()
                                AutoNextPhase.NoSource -> autoNext?.startPicking()
                                else -> Unit
                            }
                            true
                        }
                        else -> {
                            runCatching { cardFocus.requestFocus() }
                            true
                        }
                    }
                }
                if (dubVisible && dubFocused && event.key in CARD_KEYS) return@onPreviewKeyEvent false
                val repeat = event.nativeKeyEvent.repeatCount
                // Sarma sürerken (basılı tutma) kontroller açılsa da Sol/Sağ sarmaya devam eder, odağı gezdirmez.
                val seeking = SystemClock.uptimeMillis() - lastSeekKeyAt < SEEK_HOLD_MS
                when (PlayerKeys.actionFor(event.key, controlsVisible = showControls && (!seeking || barFocused), progressFocused = barFocused)) {
                    PlayerKeyAction.PassThrough -> false
                    PlayerKeyAction.TogglePlay -> { if (repeat == 0) controller.togglePlay(); reveal(); true }
                    PlayerKeyAction.SeekBack -> { seekByKey(-1, repeat); true }
                    PlayerKeyAction.SeekForward -> { seekByKey(1, repeat); true }
                    PlayerKeyAction.Reveal -> { reveal(); true }
                    PlayerKeyAction.OpenMenu -> { reveal(); openPanel(); true }
                    PlayerKeyAction.GoToButtons -> { runCatching { bottomFocus.requestFocus() }; true }
                    PlayerKeyAction.LeaveBarUp -> focusManager.moveFocus(FocusDirection.Up)
                }
            }
            // Açık focusable(): clickable dokunmatik modda odak almaz; gizli kontrollerde tuşlar buraya gelmeli.
            .focusable()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (locked) {
                    showUnlockHint()
                } else {
                    controlsVisible = !controlsVisible
                    bump()
                }
            }
            // clickable'dan sonra: eşik aşılınca olayı tüketir, dokunuş tıklama sayılmaz. Kilitliyken jest yok.
            .levelGestures(effectiveLevels, zones, levelIndicator),
    ) {
        // Görüntü: sığdır / doldur (kırp) / uzat; 16:9 ve 4:3'te görüntü o oranda bir alana uzatılır.
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = when (resize) {
                ResizeMode.Ratio16x9 -> 16f / 9f
                ResizeMode.Ratio4x3 -> 4f / 3f
                else -> null
            }
            val viewModifier = when {
                ratio == null -> Modifier.fillMaxSize()
                maxWidth / maxHeight > ratio -> Modifier.fillMaxHeight().aspectRatio(ratio)
                else -> Modifier.fillMaxWidth().aspectRatio(ratio)
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = controller.player
                        setShutterBackgroundColor(AndroidColor.BLACK)
                        // Odak ve tuşlar Compose katmanında; görünüm odak çalmasın.
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        // Harici altyazı satırlarını (kaydırmayla birlikte) kendimiz veririz; videonun içindekileri Media3 çizer.
                        subtitles.attach { cues -> subtitleView?.setCues(SubtitleCues.toCues(cues)) }
                    }
                },
                // Ayar değişince anında uygulanır; oynatma kesilmez.
                update = { view ->
                    view.resizeMode = when (resize) {
                        ResizeMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        ResizeMode.Fill -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                    view.subtitleView?.let { SubtitleStyling.apply(it, subtitleStyle) }
                },
                onRelease = { subtitles.attach(null) },
                modifier = viewModifier,
            )
        }

        subtitles.status.takeIf { it != SubtitleStatus.Idle }?.let { status ->
            SubtitleNotice(
                text = stringResource(if (status == SubtitleStatus.Loading) R.string.subtitle_loading else R.string.subtitle_load_failed),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp),
            )
        }

        subtitles.translationState.takeIf { it != TranslationState.Idle }?.let { state ->
            SubtitleNotice(
                text = when (state) {
                    TranslationState.DownloadingModel -> stringResource(R.string.subtitle_translate_model)
                    is TranslationState.Translating -> stringResource(R.string.subtitle_translating, state.percent)
                    is TranslationState.Failed ->
                        stringResource(if (state.needsWifi) R.string.subtitle_translate_wifi else R.string.subtitle_translate_failed)
                    TranslationState.Idle -> ""
                },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 144.dp),
            )
        }

        LevelIndicator(levelIndicator)

        if (resolving && ui.error == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(40.dp), color = Color.White)
                Text(stringResource(R.string.player_sources_loading), style = MaterialTheme.typography.bodyLarge, color = Color.White)
            }
        } else if ((ui.buffering || coordinator?.switching == true) && ui.error == null && !sourcesFailed) {
            // Kaynak değişirken (kaldığı saniyeden devam) ve tamponlarken dönen yükleme göstergesi.
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(40.dp), color = Color.White)
        }

        if (showControls) {
            val isLive = target.request.addonType in listOf("tv", "channel")
            val playing = coordinator?.current ?: offlineStream
            val sizeText = if (isLive) null else playing?.let { SizeText.format(coordinator?.sizeOf(it) ?: it.sizeBytes, locale) }
            val dims = SizeText.dimensions(ui.videoWidth, ui.videoHeight)
            val dubTag = when {
                embeddedDub -> stringResource(R.string.tag_dubbed_embedded)
                playing?.dubbed == true -> stringResource(R.string.tag_dubbed)
                else -> null
            }
            val infoLine = listOfNotNull(playing?.source?.addonName, dims, sizeText, dubTag)
                .reduceOrNull { acc, part -> stringResource(R.string.dot_join, acc, part) }
            val canSync = subtitles.selected is SubtitleItem.External && subtitles.status == SubtitleStatus.Idle
            val actions = buildList {
                add(BarAction(PlayIcons.Lock, stringResource(R.string.player_btn_lock)) { locked = true })
                add(BarAction(PlayIcons.Resize, stringResource(R.string.player_btn_resize), ::nextResize))
                if (canSync) add(BarAction(PlayIcons.SubtitleSync, stringResource(R.string.player_btn_sync)) { showSync = true })
                add(BarAction(PlayIcons.SwitchSource, stringResource(R.string.player_btn_source), ::openPanel))
                add(BarAction(PlayIcons.Tracks, stringResource(R.string.player_btn_tracks)) { showTracks = true; bump() })
                val canShowNext = onNextEpisode != null && AutoNextRules.canShowNextEpisodeButton(target.request.type, target.request.season, target.request.episode, target.request.addonType, duration)
                if (canShowNext) {
                    add(BarAction(PlayIcons.NextEpisode, stringResource(R.string.player_btn_next_episode)) {
                        onNextEpisode()
                        bump()
                    })
                }
            }
            Controls(
                infoLine = infoLine,
                title = titleOf(target),
                isPlaying = ui.isPlaying,
                // Hata, kaynak yok ya da kaynaklar aranırken yalnızca üst çubuk (geri) görünür; oynat/sar/alt düğmeler anlamsızdır.
                minimal = ui.error != null || sourcesFailed || resolving,
                position = position,
                duration = duration,
                zones = zones,
                playFocus = playFocus,
                barFocus = barFocus,
                bottomFocus = bottomFocus,
                onBarFocusChange = { barFocused = it },
                noticeFocus = if (resumeNotice) noticeFocus else null,
                actions = actions,
                onBack = onBack,
                onTogglePlay = { controller.togglePlay(); bump() },
                onSeek = ::seek,
                onSeekTo = { controller.seekTo(it); position = it; bump() },
                onInteract = ::bump,
            )
        }

        if (resumeNotice && ui.error == null && !locked) {
            ResumeNotice(
                onRestart = { controller.seekTo(0L); position = 0L; resumeNotice = false; bump() },
                focus = noticeFocus,
                upFocus = playFocus,
                onFocusChange = { noticeFocused = it },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 156.dp)
                    .gestureZone(zones, "notice"),
            )
        }

        if (autoNext != null && cardActive && ui.error == null && !locked) {
            NextEpisodeCard(
                state = autoNext,
                primaryFocus = cardFocus,
                onCancel = onAutoNextCancel,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 156.dp)
                    .gestureZone(zones, "next")
                    .onFocusChanged { cardFocused = it.hasFocus },
            )
        }

        if (coordinator != null && dubVisible) {
            DubSuggestionCard(
                onSwitch = coordinator::acceptDubSuggestion,
                onClose = coordinator::dismissDubSuggestion,
                primaryFocus = dubFocus,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 24.dp, bottom = 156.dp)
                    .gestureZone(zones, "dub")
                    .onFocusChanged { dubFocused = it.hasFocus },
            )
        }

        // Kaynak açılmadı / hata: ana ileti sade, hata kodu ayrıntı satırında.
        if (ui.error != null || sourcesFailed) {
            Column(
                modifier = Modifier.align(Alignment.Center).gestureZone(zones, "error").padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val isLive = target.request.addonType in listOf("tv", "channel")
                val message = when {
                    isLive && sourcesFailed -> R.string.live_channel_unavailable
                    sourcesFailed && coordinator?.streams.isNullOrEmpty() -> R.string.player_no_sources
                    sourcesFailed -> R.string.player_all_failed
                    canSwitchSource -> ui.error!!.messageRes()
                    else -> R.string.player_error_download
                }
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (canSwitchSource) {
                    Button(onClick = { openPanel() }, modifier = Modifier.focusRequester(playFocus)) {
                        Text(stringResource(R.string.player_error_pick))
                    }
                }
            }
        }

        val activeNotice = coordinator?.notice ?: if (BuildConfig.DEBUG && PlayerNoticeLogic.forceNoticeForTest) {
            SourceNotice.Skipped("TEST_SIMULATED_ERROR (500)")
        } else null
        activeNotice?.let { notice ->
            SourceNoticePill(notice, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 84.dp))
        }

        resizeToast?.let { mode ->
            Text(
                text = resizeText(mode),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                // Üstte (oynat düğmesinin üstüne binmesin).
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 96.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }

        // Kilitliyken açma simgesi geçici olarak (3 sn) görünür; dokununca veya kumanda tuşuyla yeniden belirir.
        AnimatedVisibility(
            visible = locked && lockHintVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
        ) {
            IconButton(
                onClick = { locked = false },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .focusRing(CircleShape)
                    .focusRequester(unlockFocus)
                    .onFocusChanged { unlockFocused = it.isFocused },
            ) {
                Icon(PlayIcons.Unlock, contentDescription = stringResource(R.string.player_btn_unlock), tint = Color.White)
            }
        }
    }

    if (showPanel) {
        SourcePanel(
            coordinator = coordinator,
            offlineStream = offlineStream,
            subtitles = subtitles,
            embeddedDub = embeddedDub,
            online = canSwitchSource,
            onAddFile = {
                showPanel = false
                resumeAfterPick = controller.player.isPlaying
                runCatching { pickSubtitle.launch(arrayOf("*/*")) }
            },
            onAppearance = { showPanel = false; showAppearance = true },
            onDismiss = { showPanel = false },
        )
    }

    if (showTracks) {
        TracksPanel(
            controller = controller,
            tracksVersion = ui.tracksVersion,
            onAudioChosen = onAudioChosen,
            onDismiss = { showTracks = false },
        )
    }

    // Senkron paneli videonun üstünde durur ve karartmaz: altyazı değişirken satırlar görünmeye devam eder.
    if (showSync) SubtitleSyncPanel(subtitles = subtitles, onDismiss = { showSync = false })

    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text(stringResource(R.string.player_subtitle_appearance)) },
            text = {
                // Yatay ekranda alçak: kaydırılabilir. Önizleme, karartılmış videoda altyazı görünmese de etkiyi gösterir.
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SubtitlePreview(subtitleStyle)
                    SubtitleStyleControls(subtitleStyle) { transform ->
                        scope.launch { container.settings.updateSubtitleStyle(transform) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppearance = false }) { Text(stringResource(R.string.common_done)) } },
        )
    }

    // Sonraki bölümün kaynağı hiç bulunamadıysa: kullanıcı listeden seçer, seçilen akışla bölüm açılır.
    if (autoNext != null && autoNext.picking) {
        SourcesSheet(target = autoNext.nextTarget(), onDismiss = autoNext::dismissPicking, onPicked = autoNext::picked)
    }
}

/** Seçilen dosyanın görünen adı (`film.tr.srt`); okunamazsa adresin son parçası. */
private fun displayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
}

/** Kart açıkken kartın düğmelerine bırakılan tuşlar (kök yakalayıcı bunları sarma/oynat için kullanmaz). */
private val CARD_KEYS = setOf(
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
)

private val UNLOCK_ENTER_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
private val UNLOCK_TRIGGER_KEYS = setOf(
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
    Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
)

@Composable
private fun resizeText(mode: ResizeMode): String = stringResource(
    when (mode) {
        ResizeMode.Fit -> R.string.resize_fit
        ResizeMode.Fill -> R.string.resize_fill
        ResizeMode.Stretch -> R.string.resize_stretch
        ResizeMode.Ratio16x9 -> R.string.resize_16_9
        ResizeMode.Ratio4x3 -> R.string.resize_4_3
    },
)

/** Alt düğme satırındaki bir eylem. */
private class BarAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** Altyazı yükleniyor / yüklenemedi: tek satırlık, sade bildirim. */
@Composable
private fun SubtitleNotice(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** Kaynak bildirimi: ana ileti sade; hata kodu küçük ayrıntı satırında. */
@Composable
private fun SourceNoticePill(notice: SourceNotice, modifier: Modifier = Modifier, isDebug: Boolean = BuildConfig.DEBUG) {
    val (message, detail) = when (notice) {
        is SourceNotice.SwitchFailed -> R.string.player_switch_failed to notice.detail
        is SourceNotice.Skipped -> R.string.player_source_skipped to notice.detail
        SourceNotice.Mismatch -> R.string.player_source_mismatch to "content"
        SourceNotice.StallSwitched -> R.string.player_stall_switched to null
        SourceNotice.ManualSlow -> R.string.player_manual_slow to null
    }
    Column(
        modifier = modifier
            .widthIn(max = 480.dp)
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = Color.White, textAlign = TextAlign.Center)
        if (PlayerNoticeLogic.shouldShowDetail(isDebug, detail)) {
            Text(
                stringResource(R.string.player_error_detail, detail.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Sade, kısa ömürlü bildirim: konum korunur, tek düğme baştan başlatır. */
@Composable
private fun ResumeNotice(
    onRestart: () -> Unit,
    focus: FocusRequester,
    upFocus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(24.dp))
            .padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.player_resuming),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.padding(end = 4.dp),
        )
        // Kumanda: oynat düğmesinden Aşağı ile gelinir (Controls), Yukarı ile oynat düğmesine dönülür.
        TextButton(
            onClick = onRestart,
            modifier = Modifier
                .focusRequester(focus)
                .focusProperties { up = upFocus }
                .onFocusChanged { onFocusChange(it.hasFocus) },
        ) {
            Text(stringResource(R.string.resume_restart))
        }
    }
}

/**
 * Köşedeki "Sonraki bölüm" kartı: geri sayım (Şimdi oynat / İptal), kaynak hazırlanırken (İptal) ya da kaynak bulunamayınca
 * (Başka kaynak seç / İptal). [primaryFocus] aşamanın ana düğmesindedir: Tamam ona basar.
 */
@Composable
private fun NextEpisodeCard(
    state: AutoNextController,
    primaryFocus: FocusRequester,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val season = state.season
    val episode = state.episode
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f), shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = when (state.phase) {
                AutoNextPhase.Countdown -> stringResource(R.string.next_episode_countdown, season, episode, state.secondsLeft)
                AutoNextPhase.NoSource -> stringResource(R.string.next_episode_no_source, season, episode)
                else -> stringResource(R.string.next_episode_loading, season, episode)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (state.phase) {
                AutoNextPhase.Countdown -> {
                    Button(onClick = state::playNow, modifier = Modifier.focusRequester(primaryFocus)) {
                        Text(stringResource(R.string.next_episode_play_now))
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                }
                AutoNextPhase.NoSource -> {
                    Button(onClick = state::startPicking, modifier = Modifier.focusRequester(primaryFocus)) {
                        Text(stringResource(R.string.next_episode_pick_source))
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                }
                else -> TextButton(onClick = onCancel, modifier = Modifier.focusRequester(primaryFocus)) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    }
}

/** Sol alt köşede "Dublajlı kaynak bulundu, geçilsin mi?" (Geç / Kapat). Oynatma kesilmez; Geç kaldığı saniyeden devam ettirir. */
@Composable
private fun DubSuggestionCard(
    onSwitch: () -> Unit,
    onClose: () -> Unit,
    primaryFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .widthIn(max = 340.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f), shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.dub_suggest_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSwitch, modifier = Modifier.focusRequester(primaryFocus)) { Text(stringResource(R.string.dub_suggest_switch)) }
            TextButton(onClick = onClose) { Text(stringResource(R.string.dub_suggest_close)) }
        }
    }
}

@Composable
private fun Controls(
    infoLine: String?,
    title: String,
    isPlaying: Boolean,
    minimal: Boolean,
    position: Long,
    duration: Long,
    zones: GestureZones,
    playFocus: FocusRequester,
    barFocus: FocusRequester,
    bottomFocus: FocusRequester,
    onBarFocusChange: (Boolean) -> Unit,
    noticeFocus: FocusRequester?,
    actions: List<BarAction>,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onInteract: () -> Unit,
) {
    val tint = Color.White
    Box(Modifier.fillMaxSize()) {
        // Üst ve alt kenarda okunabilirlik için yumuşak karartma.
        Box(Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))))
        if (!minimal) {
            Box(Modifier.fillMaxWidth().height(190.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))))
        }

        // Üst çubuk: geri, çalan kaynak (ad · çözünürlük · boyut), altında içerik adı.
        Row(
            modifier = Modifier.fillMaxWidth().gestureZone(zones, "top").statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(PlayIcons.Back, contentDescription = stringResource(R.string.common_back), tint = tint)
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (infoLine != null) {
                    Text(
                        text = infoLine,
                        style = MaterialTheme.typography.titleMedium,
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = title,
                    style = if (infoLine != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                    color = if (infoLine != null) tint.copy(alpha = 0.75f) else tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (!minimal) {
            Row(
                modifier = Modifier.align(Alignment.Center).gestureZone(zones, "center"),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val downToBar = Modifier.focusProperties { down = barFocus }
                SeekButton(PlayIcons.SeekBack, stringResource(R.string.player_rewind), tint, modifier = downToBar) { onSeek(-10_000L) }
                FilledIconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(72.dp)
                        .focusRequester(playFocus)
                        .focusProperties { down = noticeFocus ?: barFocus },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) PlayIcons.Pause else PlayIcons.PlayTriangle,
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                SeekButton(PlayIcons.SeekForward, stringResource(R.string.player_forward), tint, iconFirst = false, modifier = downToBar) { onSeek(10_000L) }
            }

            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
                ProgressBar(
                    position = position,
                    duration = duration,
                    onSeekTo = onSeekTo,
                    onInteract = onInteract,
                    focus = barFocus,
                    upFocus = playFocus,
                    downFocus = bottomFocus,
                    onFocusChange = onBarFocusChange,
                    modifier = Modifier.gestureZone(zones, "bar"),
                )
                // Alt düğmeler: Kilitle, Yeniden boyutlandır, Altyazı senkronu (altyazı seçiliyken), Kaynak, Parçalar.
                Row(
                    modifier = Modifier.fillMaxWidth().gestureZone(zones, "buttons"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    actions.forEachIndexed { i, action ->
                        BarButton(
                            icon = action.icon,
                            label = action.label,
                            onClick = action.onClick,
                            modifier = Modifier.weight(1f)
                                .then(if (i == 0) Modifier.focusRequester(bottomFocus) else Modifier)
                                .focusProperties { up = barFocus },
                        )
                    }
                }
            }
        }
    }
}

/** Alt düğme: ikon + küçük etiket; odakta turkuaz halka (kumanda). */
@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.clip(shape).focusRing(shape).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeekButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    iconFirst: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier.size(width = 72.dp, height = 56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = stringResource(R.string.player_seconds)
            if (!iconFirst) Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
            Icon(icon, contentDescription = description, tint = tint)
            if (iconFirst) Text(label, style = MaterialTheme.typography.titleMedium, color = tint)
        }
    }
}

@Composable
private fun ProgressBar(
    position: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    onInteract: () -> Unit,
    focus: FocusRequester,
    upFocus: FocusRequester,
    downFocus: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shown = if (dragging) dragFraction else fraction

    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveColor = Color.White.copy(alpha = 0.3f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onFocusChange(it.hasFocus) }
            .focusRing(RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(formatTime((shown * duration).toLong()), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .padding(horizontal = 12.dp)
                .focusRequester(focus)
                .focusProperties { up = upFocus; down = downFocus }
                .focusable()
                .pointerInput(duration) {
                    detectTapGestures(
                        onPress = { offset ->
                            onInteract()
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            dragging = true
                            dragFraction = frac
                            val success = tryAwaitRelease()
                            if (success) {
                                ProgressSeek.finishTargetMs(true, frac, duration)?.let(onSeekTo)
                            }
                            dragging = false
                        }
                    )
                }
                .pointerInput(duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onInteract()
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            onInteract()
                        },
                        onDragEnd = {
                            ProgressSeek.finishTargetMs(dragging, dragFraction, duration)?.let(onSeekTo)
                            dragging = false
                        },
                        onDragCancel = {
                            dragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
            ) {
                val trackStroke = 4.dp.toPx()
                val thumbRadius = 6.dp.toPx()
                val cy = size.height / 2f
                val startX = thumbRadius
                val endX = size.width - thumbRadius
                val availableWidth = (endX - startX).coerceAtLeast(0f)

                // Inaktif iz (arkaplan)
                drawLine(
                    color = inactiveColor,
                    start = Offset(startX, cy),
                    end = Offset(endX, cy),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round,
                )

                val thumbX = startX + availableWidth * shown
                // Aktif iz
                if (thumbX > startX) {
                    drawLine(
                        color = primaryColor,
                        start = Offset(startX, cy),
                        end = Offset(thumbX, cy),
                        strokeWidth = trackStroke,
                        cap = StrokeCap.Round,
                    )
                }

                // Yuvarlak tutamaç (12dp çap = 6dp yarıçap)
                drawCircle(
                    color = primaryColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, cy),
                )
            }
        }
        Text(formatTime(duration), style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun titleOf(target: PlayTarget): String {
    val r = target.request
    return if (r.type == MediaType.Tv && r.season != null && r.episode != null) {
        stringResource(R.string.player_title_episode, target.title, r.season, r.episode)
    } else {
        target.title
    }
}

private fun PlaybackError.messageRes(): Int = when (this) {
    PlaybackError.Network -> R.string.player_error_network
    PlaybackError.Unavailable -> R.string.player_error_unavailable
    PlaybackError.Format -> R.string.player_error_format
}

/** 75_000 -> "1:15", 3_725_000 -> "1:02:05". */
internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}
