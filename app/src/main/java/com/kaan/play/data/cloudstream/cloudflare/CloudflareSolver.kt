package com.kaan.play.data.cloudstream.cloudflare

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kaan.play.PlayApp
import com.lagradost.cloudstream3.PlayNetworkGuard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ekran dışı (gizli) Android WebView kullanarak Cloudflare JavaScript challenge'larını çözen yardımcı.
 */
object CloudflareSolver {
    private const val TAG = "PlayCs"
    const val DEFAULT_TIMEOUT_MS = 25_000L

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying = _isVerifying.asStateFlow()

    private val blockedHosts = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val blockedUrls = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val hostMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    var customContext: Context? = null

    /**
     * Süren kaynak aramalarından gizli doğrulama tarayıcısını istemeyenlerin sayısı ([CsSearchBudget.skipChallengeSolve], az
     * bellekli cihaz). Sayı sıfırdan büyükken [solveSkipped] doğrudur: bot doğrulaması isteyen site çözülmeden "doğrulama
     * gerekiyor" diye işaretlenir. 2 GB'lık kutuda arama sırasında açılan bu WebView süreci 6 sn'de 345 → 773 MB'a çıkarmıştı.
     */
    private val noSolveSearches = java.util.concurrent.atomic.AtomicInteger()

    /** Kaynak araması başladı; [skipSolve] doğruysa arama sürerken gizli WebView açılmaz. Aynı bayrakla [sourceSearchFinished] kapatır. */
    fun sourceSearchStarted(skipSolve: Boolean) {
        if (skipSolve) noSolveSearches.incrementAndGet()
    }

    fun sourceSearchFinished(skipSolve: Boolean) {
        if (skipSolve) noSolveSearches.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    fun solveSkipped(): Boolean = noSolveSearches.get() > 0

    private fun getEffectiveContext(): Context? {
        return customContext ?: runCatching { PlayApp.appContext }.getOrNull()
    }

    fun wasBlocked(hostOrUrl: String): Boolean {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        val time = blockedHosts[host] ?: return false
        return (System.currentTimeMillis() - time) < 5 * 60 * 1000L
    }

    fun getBlockedUrl(hostOrUrl: String): String? {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        return blockedUrls[host]
    }

    fun unblock(hostOrUrl: String) {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        blockedHosts.remove(host)
        blockedUrls.remove(host)
        unresolved.remove(host)
    }

    /** Çözücü denendi ama site hâlâ engel sayfası veriyor: kullanıcıya "Doğrula" gösterilsin. */
    fun markBlocked(hostOrUrl: String, url: String) {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        blockedHosts[host] = System.currentTimeMillis()
        blockedUrls[host] = url
    }

    /**
     * Engel sayfasıyla dönen son yanıtlar (alan adı → zaman, adres). Eklentiler çoğu zaman
     * `mainUrl`'den farklı bir alan adına (güncel alan adı listesi, yönlendirme) istek atar;
     * bu yüzden "engellendi mi" sorusu `mainUrl` yerine bu kayıtla yanıtlanır.
     */
    private val unresolved = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()

    fun noteUnresolved(hostOrUrl: String, url: String) {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        unresolved[host] = System.currentTimeMillis() to url
    }

    /** [sinceMs]'ten beri engel sayfasıyla kalan en son alan adı ve adresi; yoksa null. */
    fun unresolvedSince(sinceMs: Long): Pair<String, String>? =
        unresolved.entries
            .filter { it.value.first >= sinceMs }
            .maxByOrNull { it.value.first }
            ?.let { it.key to it.value.second }

    /**
     * Alan adının `cf_clearance` çerezini her yerden siler: bellek önbelleği + WebView çerez deposu.
     * WebView çerezleri uygulama yeniden açılınca da kalır; geçersiz kalmış eski bir çerez
     * çözücüde ve doğrulama ekranında "çözüldü" sanılıp sonsuz boş sonuca yol açıyordu.
     */
    fun invalidate(hostOrUrl: String, url: String? = null) {
        val host = CloudflareCache.normalizeHost(hostOrUrl)
        CloudflareCache.clear(host)
        runCatching {
            val cm = CookieManager.getInstance()
            val targets = listOfNotNull(url, "https://$host", "https://www.$host", "http://$host")
            for (t in targets) {
                cm.setCookie(t, "cf_clearance=; Max-Age=0; path=/")
                cm.setCookie(t, "cf_clearance=; Max-Age=0; path=/; domain=.$host")
            }
            cm.flush()
        }.onFailure { Log.w(TAG, "[Cloudflare] Çerez silinemedi ($host): ${it.javaClass.simpleName}") }
    }

    /** WebView deposundaki mevcut `cf_clearance` değeri (yoksa null). Değer loglanmaz. */
    fun storedClearance(url: String): String? = runCatching {
        parseCookieString(CookieManager.getInstance().getCookie(url))["cf_clearance"]
    }.getOrNull()

    suspend fun solve(
        url: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): CloudflareResult {
        PlayNetworkGuard.checkUrl(url)

        val host = CloudflareCache.normalizeHost(url)
        CloudflareCache.get(host)?.let { cached ->
            return CloudflareResult.Success(cached)
        }

        val mutex = hostMutexes.computeIfAbsent(host) { Mutex() }
        return mutex.withLock {
            CloudflareCache.get(host)?.let { cached ->
                return CloudflareResult.Success(cached)
            }

            val ctx = getEffectiveContext()
            if (ctx == null) {
                return CloudflareResult.Failure("Context bulunamadı")
            }

            withContext(Dispatchers.Main) {
                _isVerifying.value = true
                try {
                    withTimeoutOrNull(timeoutMs) {
                        solveInternal(ctx, url, host)
                    } ?: run {
                        blockedHosts[host] = System.currentTimeMillis()
                        blockedUrls[host] = url
                        Log.w(TAG, "[Cloudflare] Çözümleme zaman aşımına uğradı ($host, ${timeoutMs}ms)")
                        CloudflareResult.Failure("Zaman aşımı: İntraktif Turnstile doğrulaması gerekebilir", isInteractive = true)
                    }
                } finally {
                    _isVerifying.value = false
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun solveInternal(
        context: Context,
        url: String,
        host: String,
    ): CloudflareResult {
        val deferred = CompletableDeferred<CloudflareResult>()
        val webView = WebView(context)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true

            // Standart Chrome User-Agent'ı kullanarak WebView engelleyicilerini hafiflet
            val originalUa = userAgentString
            val cleanUa = originalUa
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+ "), "")
            userAgentString = cleanUa
        }

        val handler = Handler(Looper.getMainLooper())
        var isDone = false

        fun finish(result: CloudflareResult) {
            if (isDone) return
            isDone = true
            handler.removeCallbacksAndMessages(null)
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            } catch (_: Throwable) {}
            deferred.complete(result)
        }

        fun checkCookies(): Boolean {
            if (isDone) return false
            val cookieStr = cookieManager.getCookie(url)
            if (!cookieStr.isNullOrBlank() && cookieStr.contains("cf_clearance=")) {
                val cookies = parseCookieString(cookieStr)
                val ua = webView.settings.userAgentString
                val creds = CloudflareCredentials(cookies = cookies, userAgent = ua)
                CloudflareCache.put(host, creds)
                blockedHosts.remove(host)
                blockedUrls.remove(host)
                cookies.forEach { (k, v) ->
                    cookieManager.setCookie(url, "$k=$v; path=/")
                    cookieManager.setCookie("https://$host", "$k=$v; path=/")
                    cookieManager.setCookie("https://www.$host", "$k=$v; path=/")
                }
                cookieManager.flush()
                Log.d(TAG, "[Cloudflare] cf_clearance başarıyla alındı ($host)")
                finish(CloudflareResult.Success(creds))
                return true
            }
            return false
        }

        val checkRunnable = object : Runnable {
            override fun run() {
                if (isDone) return
                if (!checkCookies() && !isDone) {
                    handler.postDelayed(this, 350)
                }
            }
        }
        handler.postDelayed(checkRunnable, 350)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString() ?: return false
                try {
                    PlayNetworkGuard.checkUrl(nextUrl)
                    return false
                } catch (_: SecurityException) {
                    return true
                }
            }

            override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                super.onPageFinished(view, finishedUrl)
                val currentTitle = view?.title.orEmpty()
                Log.d(TAG, "[Cloudflare] onPageFinished: host=$host title='$currentTitle' url=$finishedUrl")
                checkCookies()
            }
        }

        webView.loadUrl(url)

        return try {
            deferred.await()
        } finally {
            handler.removeCallbacksAndMessages(null)
            if (!isDone) {
                isDone = true
                try {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.destroy()
                } catch (_: Throwable) {}
            }
        }
    }

    fun parseCookieString(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(';')
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) part.take(idx).trim() to part.substring(idx + 1).trim() else null
            }
            .toMap()
    }
}
