package com.kaan.play.ui.cloudflare

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaan.play.PlayApp
import com.kaan.play.R
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCredentials
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import com.kaan.play.data.settings.ThemeMode
import com.kaan.play.ui.components.IconButton
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.theme.PlayTheme
import com.kaan.play.ui.tv.focusRing
import com.lagradost.cloudstream3.PlayNetworkGuard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Cloudflare Turnstile / interaktif bot doğrulama ekranı.
 * Kullanıcıya görünür WebView açar, kullanıcı ekrandaki "Ben robot değilim" kutusuna basar.
 * cf_clearance çerezi oluşunca ekran otomatik olarak RESULT_OK ile kapanır ve katalog yenilenir.
 */
class CloudflareVerifyActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isCompleted = false
    private var targetHost: String = ""
    private var targetUrl: String = ""
    /** Ekran açılmadan önce depoda duran (geçersiz) cf_clearance; yeni değer gelmeden "çözüldü" sayılmaz. */
    private var staleClearance: String? = null

    private var loading by mutableStateOf(true)
    private var pageProgress by mutableIntStateOf(0)
    private var barFocusTick by mutableIntStateOf(0)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        targetHost = intent.getStringExtra(EXTRA_HOST).orEmpty().ifEmpty {
            CloudflareCache.normalizeHost(targetUrl)
        }
        val providerName = intent.getStringExtra(EXTRA_NAME).orEmpty()

        if (targetUrl.isBlank()) {
            finish()
            return
        }

        try {
            PlayNetworkGuard.checkUrl(targetUrl)
        } catch (_: SecurityException) {
            finish()
            return
        }

        val appSettings = (application as PlayApp).container.settings
        val initialTheme = runBlocking { appSettings.themeMode.first() }

        val view = WebView(this).apply {
            this.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = true

                // Standart Chrome User-Agent'ı (WebView izlerini kaldır)
                val originalUa = userAgentString
                val cleanUa = originalUa
                    .replace("; wv", "")
                    .replace(Regex("Version/\\d+\\.\\d+ "), "")
                userAgentString = cleanUa
            }

            isFocusable = true
            isFocusableInTouchMode = true

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    this@CloudflareVerifyActivity.pageProgress = newProgress
                    if (newProgress >= 100) this@CloudflareVerifyActivity.loading = false
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val nextUrl = request?.url?.toString() ?: return false
                    try {
                        PlayNetworkGuard.checkUrl(nextUrl)
                    } catch (_: SecurityException) {
                        return true
                    }
                    val reqHost = request.url?.host.orEmpty().lowercase()
                    val normalizedTarget = CloudflareCache.normalizeHost(targetHost)
                    val isTargetHost = reqHost == normalizedTarget || reqHost.endsWith(".$normalizedTarget")
                    val isCloudflareAsset = reqHost.contains("cloudflare.com") || reqHost.contains("challenges.cloudflare.com")
                    if (!isTargetHost && !isCloudflareAsset) {
                        Log.w(TAG, "[CloudflareVerify] Farklı alan adına yönlendirme engellendi: $reqHost")
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    this@CloudflareVerifyActivity.loading = false
                    checkClearanceCookie(view)
                    view?.requestFocus()
                }

                override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) {
                    if (event?.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                        this@CloudflareVerifyActivity.barFocusTick++
                    } else {
                        super.onUnhandledKeyEvent(view, event)
                    }
                }
            }
        }
        webView = view

        // Düzenli aralıklarla cf_clearance çerezi oluştu mu kontrol et (kullanıcı kutuya basınca)
        val pollCheck = object : Runnable {
            override fun run() {
                if (isCompleted || isFinishing) return
                if (!checkClearanceCookie(view)) {
                    handler.postDelayed(this, 350)
                }
            }
        }
        handler.postDelayed(pollCheck, 350)

        val root = FrameLayout(this)
        root.addView(
            ComposeView(this).apply {
                setContent {
                    val mode by appSettings.themeMode.collectAsStateWithLifecycle(initialValue = initialTheme)
                    val dark = when (mode) {
                        ThemeMode.Dark -> true
                        ThemeMode.Light -> false
                        ThemeMode.System -> isSystemInDarkTheme()
                    }
                    DisposableEffect(dark) {
                        runCatching {
                            val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                        }
                        onDispose { }
                    }
                    PlayTheme(darkTheme = dark) {
                        CloudflareVerifyScreen(
                            providerName = providerName.ifEmpty { targetHost },
                            host = targetHost,
                            webView = view,
                            loading = loading,
                            progress = pageProgress,
                            barFocusTick = barFocusTick,
                            onClose = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            },
                        )
                    }
                }
            },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        })

        // Eski (geçersiz) çerez depoda kalırsa ekran kullanıcı hiçbir şey yapmadan kapanıp
        // aynı engel sayfasına dönüyordu: önce onu sil, yalnızca yeni çıkan çerezi kabul et.
        staleClearance = CloudflareSolver.storedClearance(targetUrl)
        CloudflareSolver.invalidate(targetHost, targetUrl)
        view.loadUrl(targetUrl)
    }

    private fun checkClearanceCookie(view: WebView?): Boolean {
        if (isCompleted || view == null) return false
        val cookieManager = CookieManager.getInstance()
        val cookieStr = cookieManager.getCookie(targetUrl)
        val clearance = CloudflareSolver.parseCookieString(cookieStr)["cf_clearance"]
        if (!clearance.isNullOrBlank() && clearance != staleClearance) {
            isCompleted = true
            handler.removeCallbacksAndMessages(null)
            val cookies = CloudflareSolver.parseCookieString(cookieStr)
            val ua = view.settings.userAgentString
            val creds = CloudflareCredentials(cookies = cookies, userAgent = ua)
            val host = CloudflareCache.normalizeHost(targetHost)
            CloudflareCache.put(host, creds)
            CloudflareSolver.unblock(host)

            cookies.forEach { (k, v) ->
                cookieManager.setCookie(targetUrl, "$k=$v; path=/")
                cookieManager.setCookie("https://$host", "$k=$v; path=/")
                cookieManager.setCookie("https://www.$host", "$k=$v; path=/")
            }
            cookieManager.flush()

            Log.d(TAG, "[CloudflareVerify] cf_clearance başarıyla alındı ve önbelleklendi ($host)")
            setResult(Activity.RESULT_OK)
            finish()
            return true
        }
        return false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.stopLoading()
            w.destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlayCs"
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_HOST = "extra_host"
        private const val EXTRA_NAME = "extra_name"

        fun intent(context: Context, url: String, host: String, name: String = ""): Intent =
            Intent(context, CloudflareVerifyActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_HOST, host)
                .putExtra(EXTRA_NAME, name)
    }
}

@Composable
private fun CloudflareVerifyScreen(
    providerName: String,
    host: String,
    webView: WebView,
    loading: Boolean,
    progress: Int,
    barFocusTick: Int,
    onClose: () -> Unit,
) {
    val barFocus = remember { FocusRequester() }
    LaunchedEffect(barFocusTick) {
        if (barFocusTick > 0) runCatching { barFocus.requestFocus() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onClose,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(40.dp)
                        .focusRequester(barFocus)
                        .focusRing(androidx.compose.foundation.shape.CircleShape),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = PlayIcons.Close,
                            contentDescription = stringResource(R.string.provider_verify_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = "$providerName · ${stringResource(R.string.provider_verify_title)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.provider_verify_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                if (loading && progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
