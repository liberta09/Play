package com.kaan.play.ui.home

import android.util.Log
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kaan.play.BuildConfig
import com.kaan.play.R
import com.kaan.play.data.model.HeroItem
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.settings.SettingsRepository
import com.kaan.play.data.tmdb.TmdbImages
import com.kaan.play.ui.components.Button
import com.kaan.play.ui.components.TitleOrLogo
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.theme.LocalPlayPerformancePolicy
import com.kaan.play.ui.tv.FocusMemory
import com.kaan.play.ui.tv.focusMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GradientBase = Color(0xFF07111A)

/**
 * Ana sayfanın kapak karuseli:
 * - Fragman koşulları sağlanıyorsa: Media3 ExoPlayer ile NewPipeExtractor tabanlı sessiz MP4 video akışı.
 * - Fragman koşulları sağlanmıyorsa veya fragman yoksa: Resimli klasik karusel.
 */
@Composable
fun HeroCarousel(
    items: List<HeroItem>,
    height: Dp,
    focus: FocusMemory,
    isDefaultFocus: Boolean,
    onOpen: (MediaType, Int) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val perfPolicy = LocalPlayPerformancePolicy.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val autoAdvanceSetting by container.settings.heroAutoAdvance.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_HERO_AUTO_ADVANCE,
    )
    val trailerSetting by container.settings.heroTrailerEnabled.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.DEFAULT_HERO_TRAILER_ENABLED,
    )

    var isResumed by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isResumed = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val canPlayTrailer = remember(trailerSetting, autoAdvanceSetting, perfPolicy.heroAutoAdvanceEnabled) {
        HeroTrailerGuard.canPlay(
            context,
            trailerSettingEnabled = trailerSetting,
            heroAutoAdvanceSetting = autoAdvanceSetting,
            perfPolicyHeroEnabled = perfPolicy.heroAutoAdvanceEnabled,
        )
    }

    var trailerItems by remember { mutableStateOf<List<Pair<HeroItem, String>>?>(null) }

    LaunchedEffect(items, canPlayTrailer) {
        if (!canPlayTrailer || items.isEmpty()) {
            trailerItems = emptyList()
            return@LaunchedEffect
        }
        val validList = mutableListOf<Pair<HeroItem, String>>()
        for (item in items) {
            val key = withContext(Dispatchers.IO) {
                container.tmdb.trailerKey(item.media.type, item.media.id)
            }
            if (YouTubeTrailerResolver.isValidKey(key)) {
                validList.add(item to key!!)
            }
        }
        trailerItems = validList
    }

    val activeTrailers = trailerItems
    val isTrailerMode = canPlayTrailer && !activeTrailers.isNullOrEmpty()

    if (isTrailerMode && activeTrailers != null) {
        HeroTrailerCarousel(
            trailerItems = activeTrailers,
            height = height,
            focus = focus,
            isDefaultFocus = isDefaultFocus,
            onOpen = onOpen,
            isResumed = isResumed,
            contentFocusRequester = contentFocusRequester,
            topBarFocusRequester = topBarFocusRequester,
            modifier = modifier,
        )
    } else {
        HeroImageCarousel(
            items = items,
            height = height,
            focus = focus,
            isDefaultFocus = isDefaultFocus,
            onOpen = onOpen,
            isResumed = isResumed,
            autoAdvanceSetting = autoAdvanceSetting,
            contentFocusRequester = contentFocusRequester,
            topBarFocusRequester = topBarFocusRequester,
            modifier = modifier,
        )
    }
}

/**
 * Media3 + NewPipeExtractor destekli sessiz kapak fragmanı karuseli.
 */
@OptIn(UnstableApi::class)
@Composable
private fun HeroTrailerCarousel(
    trailerItems: List<Pair<HeroItem, String>>,
    height: Dp,
    focus: FocusMemory,
    isDefaultFocus: Boolean,
    onOpen: (MediaType, Int) -> Unit,
    isResumed: Boolean,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val navPosition = com.kaan.play.ui.tv.LocalNavBarPosition.current
    val focusManager = LocalFocusManager.current
    val perfPolicy = LocalPlayPerformancePolicy.current
    val isLowPerf = !perfPolicy.heroAutoAdvanceEnabled

    var hasFocus by remember { mutableStateOf(false) }
    var currentTrailerIndex by remember { mutableIntStateOf(0) }
    var displayedItemIndex by remember { mutableIntStateOf(0) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    LaunchedEffect(isResumed) {
        if (!isResumed) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                exoPlayer.play()
            }
        }
    }

    val buttonFocusRequester = remember { FocusRequester() }

    // Otomatik döngü ve video yönetimi
    LaunchedEffect(trailerItems, isResumed) {
        if (!isResumed || trailerItems.isEmpty()) return@LaunchedEffect

        var candidateIndex = currentTrailerIndex.coerceIn(0, trailerItems.lastIndex)
        var consecutiveFailures = 0

        while (isActive) {
            if (candidateIndex !in trailerItems.indices) {
                candidateIndex = 0
            }

            val (item, videoKey) = trailerItems[candidateIndex]
            val startTime = System.currentTimeMillis()

            if (BuildConfig.DEBUG) {
                Log.d("PlayHero", "trailer cover $candidateIndex starting")
            }

            // Stream çözümü (8 sn zaman aşımı)
            val stream = YouTubeTrailerResolver.resolveStream(videoKey, isLowPerf)
            val resolveDuration = System.currentTimeMillis() - startTime

            if (stream == null) {
                if (BuildConfig.DEBUG) {
                    Log.w("PlayHero", "stream resolve failed in ${resolveDuration}ms, skipping cover $candidateIndex")
                }
                consecutiveFailures++
                if (consecutiveFailures >= trailerItems.size) {
                    delay(3000)
                }
                candidateIndex = HeroTrailerRules.nextCandidateIndex(candidateIndex, trailerItems.size)
                currentTrailerIndex = candidateIndex
                continue
            }

            consecutiveFailures = 0

            // Sıradaki kapağı önceden çöz (pre-fetch)
            val nextIndex = HeroTrailerRules.nextCandidateIndex(candidateIndex, trailerItems.size)
            if (nextIndex != candidateIndex) {
                val nextKey = trailerItems[nextIndex].second
                launch(Dispatchers.IO) {
                    YouTubeTrailerResolver.resolveStream(nextKey, isLowPerf)
                }
            }

            // ExoPlayer'a yükle
            val mediaItem = MediaItem.fromUri(stream.streamUrl)
            val startPos = HeroTrailerRules.startPositionMs(stream.durationSeconds)

            var firstFrameReceived = false
            val loadTime = System.currentTimeMillis()

            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    firstFrameReceived = true
                    displayedItemIndex = candidateIndex
                    val firstFrameDuration = System.currentTimeMillis() - loadTime
                    if (BuildConfig.DEBUG) {
                        Log.d("PlayHero", "first frame rendered in ${firstFrameDuration}ms for cover $candidateIndex")
                    }
                }
            }

            exoPlayer.addListener(listener)
            exoPlayer.setMediaItem(mediaItem)
            if (startPos > 0) {
                exoPlayer.seekTo(startPos)
            }
            exoPlayer.prepare()
            exoPlayer.play()

            // İlk kareyi bekle (en çok 5 sn)
            val frameWaitStart = System.currentTimeMillis()
            while (!firstFrameReceived && (System.currentTimeMillis() - frameWaitStart < HeroTrailerRules.FIRST_FRAME_TIMEOUT_MS)) {
                delay(50)
            }

            if (!firstFrameReceived) {
                if (BuildConfig.DEBUG) {
                    Log.w("PlayHero", "first frame timeout (5s), skipping cover $candidateIndex")
                }
                exoPlayer.removeListener(listener)
                candidateIndex = HeroTrailerRules.nextCandidateIndex(candidateIndex, trailerItems.size)
                currentTrailerIndex = candidateIndex
                continue
            }

            // 12 saniyelik oynatma süresi
            val playStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - playStart < HeroTrailerRules.MAX_PLAY_DURATION_MS) {
                delay(200)
                // Kumanda veya kullanıcı odağı varsa durur
                if (hasFocus) {
                    while (hasFocus && isResumed) {
                        delay(200)
                    }
                    delay(HeroAutoAdvanceRules.RESUME_DELAY_MS)
                    break
                }
                if (!isResumed) {
                    while (!isResumed) {
                        delay(200)
                    }
                    delay(500)
                }
            }

            exoPlayer.removeListener(listener)
            candidateIndex = HeroTrailerRules.nextCandidateIndex(candidateIndex, trailerItems.size)
            currentTrailerIndex = candidateIndex
        }
    }

    val currentItem = trailerItems.getOrNull(displayedItemIndex)?.first ?: trailerItems.first().first

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (!hasFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        currentTrailerIndex = HeroTrailerRules.nextCandidateIndex(currentTrailerIndex, trailerItems.size)
                        true
                    }
                    Key.DirectionLeft -> {
                        val prev = if (currentTrailerIndex <= 0) trailerItems.lastIndex else currentTrailerIndex - 1
                        currentTrailerIndex = prev
                        true
                    }
                    Key.DirectionUp -> if (navPosition == com.kaan.play.ui.nav.NavBarPosition.Top && topBarFocusRequester != null) {
                        runCatching { topBarFocusRequester.requestFocus() }.getOrDefault(false)
                    } else false
                    else -> false
                }
            },
    ) {
        // 1. Arka plan afiş görseli (ilk video başlayana kadar görünür)
        TmdbImages.url(currentItem.media.backdropPath, TmdbImages.backdropSize(perfPolicy.useSmallerPosters))?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 2. Video PlayerView (TextureView, zoom resize, keepContentOnPlayerReset)
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_hero_player, null, false) as PlayerView).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    isFocusable = false
                    isClickable = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 3. Koyu gradyanlar (#07111A)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.25f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.85f to GradientBase.copy(alpha = 0.95f),
                        1f to GradientBase,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to GradientBase.copy(alpha = 0.90f),
                        0.35f to GradientBase.copy(alpha = 0.65f),
                        0.65f to GradientBase.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                    )
                )
        )

        // 4. Sol altta sırayla: Başlık/Logo -> Tür/Yıl -> "Detayları gör" düğmesi -> Noktalar
        Crossfade(
            targetState = currentItem,
            animationSpec = tween(HeroTrailerRules.TRANSITION_CROSSFADE_MS),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 560.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) { item ->
            val media = item.media
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TitleOrLogo(title = media.title, logoPath = item.logoPath)
                heroLine(item)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = { onOpen(media.type, media.id) },
                    modifier = Modifier
                        .focusRequester(buttonFocusRequester)
                        .let { if (contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                        .focusMemory(focus, "hero", isDefault = isDefaultFocus)
                        .let {
                            if (topBarFocusRequester != null) {
                                it.focusProperties {
                                    when (navPosition) {
                                        com.kaan.play.ui.nav.NavBarPosition.Top -> up = topBarFocusRequester
                                        com.kaan.play.ui.nav.NavBarPosition.Left -> left = topBarFocusRequester
                                        else -> Unit
                                    }
                                }
                            } else it
                        }
                        .height(44.dp),
                ) {
                    Text(stringResource(R.string.home_hero_details))
                }
                PageDots(
                    count = trailerItems.size,
                    selected = displayedItemIndex,
                    animateWidth = true,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * Fragman kapalıyken veya fragman bulunamadığında çalışan resimli klasik karusel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroImageCarousel(
    items: List<HeroItem>,
    height: Dp,
    focus: FocusMemory,
    isDefaultFocus: Boolean,
    onOpen: (MediaType, Int) -> Unit,
    isResumed: Boolean,
    autoAdvanceSetting: Boolean,
    contentFocusRequester: FocusRequester? = null,
    topBarFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val navPosition = com.kaan.play.ui.tv.LocalNavBarPosition.current
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val perfPolicy = LocalPlayPerformancePolicy.current

    fun go(target: Int) {
        scope.launch {
            pager.animateScrollToPage(target)
            runCatching { requesters[target].requestFocus() }
        }
    }

    LaunchedEffect(
        pager.currentPage,
        isResumed,
        autoAdvanceSetting,
        perfPolicy.heroAutoAdvanceEnabled,
    ) {
        if (!autoAdvanceSetting || !perfPolicy.heroAutoAdvanceEnabled || !isResumed || items.size <= 1) {
            return@LaunchedEffect
        }

        while (pager.isScrollInProgress) {
            delay(50)
        }

        val startTime = System.currentTimeMillis()
        var advanced = false

        while (!advanced) {
            delay(200)

            if (!HeroAutoAdvanceRules.shouldAutoAdvance(
                    hasUserFocus = hasFocus,
                    isScrollInProgress = pager.isScrollInProgress,
                    isResumed = isResumed,
                    autoAdvanceSetting = autoAdvanceSetting,
                    perfPolicyEnabled = perfPolicy.heroAutoAdvanceEnabled,
                )
            ) {
                while (!HeroAutoAdvanceRules.shouldAutoAdvance(
                        hasUserFocus = hasFocus,
                        isScrollInProgress = pager.isScrollInProgress,
                        isResumed = isResumed,
                        autoAdvanceSetting = autoAdvanceSetting,
                        perfPolicyEnabled = perfPolicy.heroAutoAdvanceEnabled,
                    )
                ) {
                    delay(200)
                }
                delay(HeroAutoAdvanceRules.RESUME_DELAY_MS)
                if (HeroAutoAdvanceRules.shouldAutoAdvance(
                        hasUserFocus = hasFocus,
                        isScrollInProgress = pager.isScrollInProgress,
                        isResumed = isResumed,
                        autoAdvanceSetting = autoAdvanceSetting,
                        perfPolicyEnabled = perfPolicy.heroAutoAdvanceEnabled,
                    )
                ) {
                    val next = HeroAutoAdvanceRules.nextPageIndex(pager.currentPage, items.size)
                    pager.animateScrollToPage(next)
                    advanced = true
                }
                continue
            }

            val elapsed = System.currentTimeMillis() - startTime
            val maxDuration = HeroAutoAdvanceRules.NO_TRAILER_DURATION_MS

            if (elapsed >= maxDuration) {
                val next = HeroAutoAdvanceRules.nextPageIndex(pager.currentPage, items.size)
                pager.animateScrollToPage(next)
                advanced = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (!hasFocus || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val page = pager.currentPage
                when (event.key) {
                    Key.DirectionRight -> when {
                        page < items.lastIndex -> { go(page + 1); true }
                        navPosition == com.kaan.play.ui.nav.NavBarPosition.Right && topBarFocusRequester != null ->
                            runCatching { topBarFocusRequester.requestFocus() }.getOrDefault(false)
                        else -> false
                    }
                    Key.DirectionLeft -> when {
                        page > 0 -> { go(page - 1); true }
                        navPosition == com.kaan.play.ui.nav.NavBarPosition.Left && topBarFocusRequester != null ->
                            runCatching { topBarFocusRequester.requestFocus() }.getOrDefault(false)
                        else -> focusManager.moveFocus(FocusDirection.Left)
                    }
                    Key.DirectionUp -> if (navPosition == com.kaan.play.ui.nav.NavBarPosition.Top && topBarFocusRequester != null) {
                        runCatching { topBarFocusRequester.requestFocus() }.getOrDefault(false)
                    } else false
                    else -> false
                }
            },
    ) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = if (perfPolicy.heroAutoAdvanceEnabled) 1 else 0,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            HeroImagePage(
                item = item,
                onOpen = { onOpen(item.media.type, item.media.id) },
                buttonModifier = Modifier
                    .focusRequester(requesters[page])
                    .let { if (page == pager.currentPage && contentFocusRequester != null) it.focusRequester(contentFocusRequester) else it }
                    .let { if (page == pager.currentPage) it.focusMemory(focus, "hero", isDefault = isDefaultFocus) else it }
                    .let {
                        if (topBarFocusRequester != null) {
                            it.focusProperties {
                                when (navPosition) {
                                    com.kaan.play.ui.nav.NavBarPosition.Top -> up = topBarFocusRequester
                                    com.kaan.play.ui.nav.NavBarPosition.Left -> if (page == 0) left = topBarFocusRequester
                                    com.kaan.play.ui.nav.NavBarPosition.Right -> if (page == items.lastIndex) right = topBarFocusRequester
                                    com.kaan.play.ui.nav.NavBarPosition.Bottom -> Unit
                                }
                            }
                        } else it
                    },
                dotsContent = {
                    PageDots(
                        count = items.size,
                        selected = pager.currentPage,
                        animateWidth = perfPolicy.heroAutoAdvanceEnabled,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun HeroImagePage(
    item: HeroItem,
    onOpen: () -> Unit,
    buttonModifier: Modifier,
    dotsContent: @Composable () -> Unit,
) {
    val media = item.media
    val perfPolicy = LocalPlayPerformancePolicy.current
    val context = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        TmdbImages.url(media.backdropPath, TmdbImages.backdropSize(perfPolicy.useSmallerPosters))?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(perfPolicy.heroAutoAdvanceEnabled)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.25f to Color.Transparent,
                        0.55f to Color.Transparent,
                        0.85f to GradientBase.copy(alpha = 0.95f),
                        1f to GradientBase,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to GradientBase.copy(alpha = 0.90f),
                        0.35f to GradientBase.copy(alpha = 0.65f),
                        0.65f to GradientBase.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 560.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TitleOrLogo(title = media.title, logoPath = item.logoPath)
            heroLine(item)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onOpen, modifier = buttonModifier.height(44.dp)) {
                Text(stringResource(R.string.home_hero_details))
            }
            dotsContent()
        }
    }
}

/**
 * Kapak karuselinin sayfa gÃ¶sterge noktalarÄ±.
 */
@Composable
private fun PageDots(
    count: Int,
    selected: Int,
    animateWidth: Boolean,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until count) {
            val isCurrent = i == selected
            val width by animateDpAsState(
                targetValue = if (isCurrent && animateWidth) 24.dp else 6.dp,
                animationSpec = tween(250),
                label = "dot_width_$i",
            )
            val alpha by animateFloatAsState(
                targetValue = if (isCurrent) 1f else 0.45f,
                animationSpec = tween(200),
                label = "dot_alpha_$i",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .size(width = width, height = 6.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(if (isCurrent) accent else muted),
            )
        }
    }
}

/** "Aksiyon Â· Macera Â· 2024": en Ã§ok iki tÃ¼r ve yÄ±l. */
@Composable
private fun heroLine(item: HeroItem): String? {
    val parts = item.genres + listOfNotNull(item.media.year?.toString())
    if (parts.isEmpty()) return null
    return parts.reduce { acc, s -> stringResource(R.string.dot_join, acc, s) }
}
