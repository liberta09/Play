package com.kaan.play.ui.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import com.kaan.play.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.BuildConfig
import com.kaan.play.PlayApp
import com.kaan.play.data.web.MoveDirection
import com.kaan.play.data.web.WebCursorController
import com.kaan.play.ui.player.DeviceLevels
import com.kaan.play.R
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.data.web.WebPolicy
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.theme.PlayTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Sayfanın Compose'a yansıyan durumu. */
private class WebUiState {
    var loading by mutableStateOf(true)
    var progress by mutableIntStateOf(0)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var failed by mutableStateOf(false)
    /** Kumandada sayfadan "Yukarı" ile çıkıldıkça artar; çubuk odağı alır. */
    var barFocusTick by mutableIntStateOf(0)
}

/**
 * Reklamsız web görünümü: üstte sade bir çubuk (kapat, site adı, geri, ileri, yenile), altında WebView.
 * Sistem Geri tuşu önce tam ekran videodan, sonra web geçmişinden geri gider; geçmiş bitince ekran kapanır.
 * Engelleme kuralları [WebPolicy] ve [PlayWebViewClient]/[PlayWebChromeClient]'tedir.
 */
class WebActivity : AppCompatActivity(), WebPageListener, PlayWebChromeClient.FullscreenHost {

    private val ui = WebUiState()
    private var web: WebView? = null
    private lateinit var root: FrameLayout
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var isRemoteMode: Boolean = false
    private var cursorController: WebCursorController? = null
    private var cursorView: WebCursorView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val policy = WebPolicy()
        // Yalnızca http/https ve engelli olmayan adres; aksi halde hiçbir şey açılmaz.
        if (url == null || !policy.allowsNavigation(url)) {
            finish()
            return
        }

        val settings = (application as PlayApp).container.settings
        val initialTheme = runBlocking { settings.themeMode.first() }

        // Arka plan WebView'ın varsayılanı (beyaz) kalır: arka planı olmayan sayfalar tarayıcıdaki gibi okunur.
        val view = WebView(this).apply {
            webViewClient = PlayWebViewClient(policy, this@WebActivity)
            webChromeClient = PlayWebChromeClient(policy, onProgress = { ui.progress = it }, fullscreen = this@WebActivity)
            configure(this.settings)
            // İndirme istekleri yok sayılır (uygulama dosya indirmez).
            setDownloadListener { _, _, _, _, _ -> }
        }
        web = view
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, false) // üçüncü taraf çerezleri kapalı
        }

        val forceRemote = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_FORCE_REMOTE, false)
        isRemoteMode = forceRemote || !DeviceLevels.supportsTouch(this)
        if (isRemoteMode) {
            // KeyEvent.eventTime uptimeMillis tabanlıdır; denetleyicinin saati de aynı olmalı (yoksa imleç hep soluk kalır).
            val controller = WebCursorController(clock = { SystemClock.uptimeMillis() })
            cursorController = controller
            cursorView = WebCursorView(this).apply {
                bind(controller)
            }
        }

        // Kök: Compose çubuğu + WebView, üstünde tam ekran video için boş bir katman.
        root = FrameLayout(this)
        root.addView(
            ComposeView(this).apply {
                setContent {
                    val mode by settings.themeMode.collectAsStateWithLifecycle(initialValue = initialTheme)
                    val dark = when (mode) {
                        ThemeMode.Dark -> true
                        ThemeMode.Light -> false
                        ThemeMode.System -> isSystemInDarkTheme()
                    }
                    DisposableEffect(dark) {
                        val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                        onDispose { }
                    }
                    PlayTheme(darkTheme = dark) {
                        WebScreen(
                            name = name.ifEmpty { url },
                            web = view,
                            cursorView = cursorView,
                            state = ui,
                            onClose = ::finish,
                            onBack = { view.goBack() },
                            onForward = { view.goForward() },
                            onRefresh = { ui.failed = false; view.reload() },
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    customView != null -> hide()
                    view.canGoBack() -> view.goBack()
                    else -> finish()
                }
            }
        })

        view.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(s: WebSettings) {
        s.javaScriptEnabled = true // çoğu site JS ister
        s.domStorageEnabled = true
        s.setSupportMultipleWindows(true) // onCreateWindow reddetsin diye; pencere hiç açılmaz
        s.javaScriptCanOpenWindowsAutomatically = false // hareketsiz window.open gelmez
        s.mediaPlaybackRequiresUserGesture = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(false)
    }

    // ---- WebPageListener ----

    override fun onPageStarted() {
        ui.loading = true
        ui.failed = false
        syncHistory()
    }

    override fun onPageFinished() {
        ui.loading = false
        syncHistory()
        web?.requestFocus() // D-pad ile kaydırma hemen çalışsın
    }

    override fun onMainFrameError() {
        ui.failed = true
        ui.loading = false
    }

    override fun onHistoryChanged() = syncHistory()

    override fun onRendererGone() {
        finish()
    }

    override fun onFocusLeaveUp() {
        ui.barFocusTick++
        cursorController?.hide()
    }

    private fun syncHistory() {
        ui.canGoBack = web?.canGoBack() == true
        ui.canGoForward = web?.canGoForward() == true
    }

    // ---- Tam ekran video ----

    override fun show(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        view.setBackgroundColor(Color.BLACK)
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        view.requestFocus()
    }

    override fun hide() {
        val view = customView ?: return
        customView = null
        root.removeView(view)
        customCallback?.onCustomViewHidden()
        customCallback = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        web?.requestFocus()
    }

    // ---- yaşam döngüsü ----

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onPause() {
        web?.onPause() // arka planda video/ses sürmesin
        super.onPause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isRemoteMode) {
            return super.dispatchKeyEvent(event)
        }

        // Tam ekran video varsa imleç müdahale etmesin (Parça 3 kapsamı)
        if (customView != null) {
            return super.dispatchKeyEvent(event)
        }

        val controller = cursorController ?: return super.dispatchKeyEvent(event)

        // Durum A: İmleç gizli (üst çubukta odak var)
        if (!controller.isVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    controller.show()
                    web?.requestFocus()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        // Durum B: İmleç görünür (sayfada gezinme)
        controller.onUserInteraction(event.eventTime)

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val webView = web
                    val cannotScrollUp = webView == null || !webView.canScrollVertically(-1)
                    if (controller.y <= 0f && cannotScrollUp) {
                        onFocusLeaveUp()
                        return true
                    }
                    val result = controller.move(MoveDirection.UP, event.eventTime)
                    if (result.scrollDy != 0) {
                        webView?.scrollBy(0, result.scrollDy)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    controller.resetAcceleration()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val result = controller.move(MoveDirection.DOWN, event.eventTime)
                    if (result.scrollDy != 0) {
                        web?.scrollBy(0, result.scrollDy)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    controller.resetAcceleration()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val result = controller.move(MoveDirection.LEFT, event.eventTime)
                    if (result.scrollDx != 0) {
                        web?.scrollBy(result.scrollDx, 0)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    controller.resetAcceleration()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    val result = controller.move(MoveDirection.RIGHT, event.eventTime)
                    if (result.scrollDx != 0) {
                        web?.scrollBy(result.scrollDx, 0)
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    controller.resetAcceleration()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        dispatchClick(controller.x, controller.y)
                    }
                }
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun dispatchClick(x: Float, y: Float) {
        val target = web ?: return
        val downTime = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x,
            y,
            0
        )
        target.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        val upEvent = MotionEvent.obtain(
            downTime,
            downTime + 30,
            MotionEvent.ACTION_UP,
            x,
            y,
            0
        )
        target.dispatchTouchEvent(upEvent)
        upEvent.recycle()
    }

    override fun onDestroy() {
        if (customView != null) hide()
        cursorView?.let { cv ->
            (cv.parent as? ViewGroup)?.removeView(cv)
        }
        cursorView = null
        cursorController = null
        web?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.stopLoading()
            w.destroy()
        }
        web = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_FORCE_REMOTE = "play_force_remote"

        fun intent(context: Context, name: String, url: String, forceRemote: Boolean = false): Intent =
            Intent(context, WebActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_NAME, name)
                .apply {
                    if (BuildConfig.DEBUG && forceRemote) {
                        putExtra(EXTRA_FORCE_REMOTE, true)
                    }
                }
    }
}

@Composable
private fun WebScreen(
    name: String,
    web: WebView,
    cursorView: WebCursorView?,
    state: WebUiState,
    onClose: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
) {
    val barFocus = remember { FocusRequester() }
    LaunchedEffect(state.barFocusTick) {
        if (state.barFocusTick > 0) runCatching { barFocus.requestFocus() }
    }

    // Surface: yazı/ikon rengi (onBackground) tanımlansın; yoksa varsayılan içerik rengi siyah olur.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.focusRequester(barFocus)) {
                    Icon(PlayIcons.Close, contentDescription = stringResource(R.string.web_close), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = onBack, enabled = state.canGoBack) {
                    Icon(PlayIcons.Back, contentDescription = stringResource(R.string.common_back))
                }
                IconButton(onClick = onForward, enabled = state.canGoForward) {
                    Icon(PlayIcons.Forward, contentDescription = stringResource(R.string.web_forward))
                }
                IconButton(onClick = onRefresh) {
                    Icon(PlayIcons.Refresh, contentDescription = stringResource(R.string.web_refresh))
                }
            }
            // İnce yükleme çizgisi; yer kaplamaması için sabit yükseklikte durur.
            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                if (state.loading && state.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                if (cursorView != null) {
                    AndroidView(
                        factory = {
                            cursorView.apply {
                                (parent as? ViewGroup)?.removeView(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (state.failed) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.web_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
