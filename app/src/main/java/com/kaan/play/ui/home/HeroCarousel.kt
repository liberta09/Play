package com.kaan.play.ui.home

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ana sayfanın kapak karuseli: arka plan görseli, başlık logosu (yoksa metin başlık), "Tür · Yıl" satırı,
 * "Detayları gör" düğmesi, sayfa noktaları ve Wi-Fi'deyken sessiz fragman oynatımı.
 * Fragmansız kapak 6 sn, fragmanlı kapak en çok 14 sn kalır; sondan sonra başa döner.
 * Kumandada odak düğmedeyken kapak asla kendiliğinden kaymaz; bırakınca 6 sn sonra sürer.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val navPosition = com.kaan.play.ui.tv.LocalNavBarPosition.current
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

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

    var activeTrailerKey by remember { mutableStateOf<String?>(null) }
    var isTrailerPlaying by remember { mutableStateOf(false) }
    var trailerErrorOrEnded by remember { mutableStateOf(false) }

    val trailerAlpha by animateFloatAsState(
        targetValue = if (isTrailerPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "hero_trailer_alpha",
    )

    // Odak düğmedeyken sayfa değişince odak yeni sayfanın düğmesine taşınır.
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
            activeTrailerKey = null
            isTrailerPlaying = false
            return@LaunchedEffect
        }

        activeTrailerKey = null
        isTrailerPlaying = false
        trailerErrorOrEnded = false

        val canPlay = HeroTrailerGuard.canPlay(
            context,
            trailerSettingEnabled = trailerSetting,
            heroAutoAdvanceSetting = autoAdvanceSetting,
            perfPolicyHeroEnabled = perfPolicy.heroAutoAdvanceEnabled,
        )

        // Fragman yükleme (~2 sn sonra)
        if (canPlay) {
            launch {
                delay(HeroAutoAdvanceRules.TRAILER_START_DELAY_MS)
                val currentItem = items.getOrNull(pager.currentPage) ?: return@launch
                val key = container.tmdb.trailerKey(currentItem.media.type, currentItem.media.id)
                if (HeroTrailerEmbed.isValidKey(key)) {
                    activeTrailerKey = key
                    // 8 sn içinde PLAYING gelmezse fragman zaman aşımına uğrar
                    delay(HeroAutoAdvanceRules.TRAILER_PLAY_TIMEOUT_MS)
                    if (!isTrailerPlaying) {
                        activeTrailerKey = null
                    }
                }
            }
        }

        val startTime = System.currentTimeMillis()
        var advanced = false

        while (!advanced) {
            delay(200)

            // Odak karuselde veya kullanıcı sürüklerken veya ekran resumed değilken durur
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
                // Bırakınca 6 sn sonra sürer:
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
            val hasActiveTrailer = activeTrailerKey != null && isTrailerPlaying
            val maxDuration = HeroAutoAdvanceRules.pageDurationMs(hasActiveTrailer)

            val shouldAdvanceEarly = isTrailerPlaying && trailerErrorOrEnded
            val reachedMaxTime = elapsed >= maxDuration

            if (shouldAdvanceEarly || reachedMaxTime) {
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
            val isCurrentPage = page == pager.currentPage
            HeroPage(
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
                trailerContent = if (isCurrentPage && activeTrailerKey != null) {
                    {
                        HeroTrailerView(
                            videoKey = activeTrailerKey!!,
                            onPlaying = { isTrailerPlaying = true },
                            onEnded = { trailerErrorOrEnded = true },
                            onError = {
                                isTrailerPlaying = false
                                trailerErrorOrEnded = true
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(trailerAlpha),
                        )
                    }
                } else null,
            )
        }
        PageDots(
            count = items.size,
            selected = pager.currentPage,
            animateWidth = perfPolicy.heroAutoAdvanceEnabled,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 14.dp),
        )
    }
}

@Composable
private fun HeroPage(
    item: HeroItem,
    onOpen: () -> Unit,
    buttonModifier: Modifier,
    trailerContent: (@Composable () -> Unit)? = null,
) {
    val media = item.media
    val background = MaterialTheme.colorScheme.background
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

        // Sessiz fragman varsa görselin üstüne gelir (fade-in ile)
        trailerContent?.invoke()

        // Üstte durum çubuğu için hafif karartma, altta sayfa rengine eriyen geçiş (yazı okunur, görsel "kesilmiş" görünmez).
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.4f), 0.28f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.85f to background.copy(alpha = 0.92f), 1f to background)))

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 560.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 38.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TitleOrLogo(title = media.title, logoPath = item.logoPath)
            heroLine(item)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onOpen, modifier = buttonModifier.height(44.dp)) {
                Text(stringResource(R.string.home_hero_details))
            }
        }
    }
}

private class HeroTrailerBridge(
    private val onPlayingAction: () -> Unit,
    private val onEndedAction: () -> Unit,
    private val onErrorAction: () -> Unit,
) {
    @android.webkit.JavascriptInterface
    fun onPlaying() {
        onPlayingAction()
    }

    @android.webkit.JavascriptInterface
    fun onEnded() {
        onEndedAction()
    }

    @android.webkit.JavascriptInterface
    fun onError() {
        onErrorAction()
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun HeroTrailerView(
    videoKey: String,
    onPlaying: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val html = remember(videoKey) { HeroTrailerEmbed.silentTrailerHtml(videoKey) }

    if (html == null) {
        LaunchedEffect(Unit) { onError() }
        return
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(videoKey) {
        onDispose {
            webViewRef?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
            webViewRef = null
        }
    }

    val bridge: HeroTrailerBridge = remember(onPlaying, onEnded, onError) {
        HeroTrailerBridge(
            onPlayingAction = { (context as? android.app.Activity)?.runOnUiThread { onPlaying() } ?: onPlaying() },
            onEndedAction = { (context as? android.app.Activity)?.runOnUiThread { onEnded() } ?: onEnded() },
            onErrorAction = { (context as? android.app.Activity)?.runOnUiThread { onError() } ?: onError() },
        )
    }

    AndroidView(
        factory = { ctx ->
            object : WebView(ctx) {
                override fun onTouchEvent(event: MotionEvent?): Boolean = false
                override fun onGenericMotionEvent(event: MotionEvent?): Boolean = false
                override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
            }.apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.userAgentString = settings.userAgentString
                    .replace("; wv", "")
                    .replace(Regex("Version/\\d+\\.\\d+ "), "")

                addJavascriptInterface(bridge, "HeroTrailerBridge")

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val uri = request?.url ?: return true
                        return HeroTrailerEmbed.shouldBlockTrailerNavigation(request.isForMainFrame, uri.scheme, uri.host)
                    }
                }
                webChromeClient = WebChromeClient()

                loadDataWithBaseURL(
                    "https://www.youtube-nocookie.com",
                    html,
                    "text/html",
                    "utf-8",
                    null,
                )
                webViewRef = this
            }
        },
        modifier = modifier,
    )
}

/** "Aksiyon · Macera · 2024": en çok iki tür ve yıl. */
@Composable
private fun heroLine(item: HeroItem): String? {
    val parts = item.genres + listOfNotNull(item.media.year?.toString())
    if (parts.isEmpty()) return null
    return parts.reduce { acc, s -> stringResource(R.string.dot_join, acc, s) }
}

@Composable
private fun PageDots(count: Int, selected: Int, animateWidth: Boolean, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val targetWidth = if (index == selected) 18.dp else 6.dp
            val width by if (animateWidth) {
                animateDpAsState(targetWidth, label = "dot")
            } else {
                androidx.compose.runtime.rememberUpdatedState(targetWidth)
            }
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
            )
        }
    }
}
