package com.kaan.play.ui.web

import android.graphics.Bitmap
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.kaan.play.data.web.AdHideRules
import com.kaan.play.data.web.WebPolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val TAG = "PlayWeb"

/** Sayfanın arayüze yansıyan durumu; [WebActivity] okur. */
interface WebPageListener {
    fun onPageStarted()
    fun onPageFinished()
    fun onMainFrameError()
    fun onHistoryChanged()
    fun onRendererGone()
    fun onFocusLeaveUp()
}

/**
 * Reklam/izleyici alan adlarına giden istekleri boş cevapla düşürür, yalnızca http/https gezinmesine izin verir.
 * Kurallar [WebPolicy]'dedir; burada yalnızca WebView'a bağlanır. Her reklamı yakalamaz.
 */
class PlayWebViewClient(
    private val policy: WebPolicy,
    private val listener: WebPageListener,
    private val hideRules: AdHideRules = AdHideRules.default,
) : WebViewClient() {

    companion object {
        /** CSS element gizleme etkinleştirme bayrağı. */
        var enableCssElementHiding: Boolean = true
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val host = request.url.host
        if (!policy.isBlockedHost(host)) return null
        Log.d(TAG, "blocked request to $host")
        // 204: gövdesiz "içerik yok". Betik/görsel yüklenmez, sayfa takılmaz.
        return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (!policy.allowsNavigation(url)) {
            // intent://, market://, tel:, javascript:, data: ve reklam alan adlarına gezinme: sessizce yok sayılır.
            Log.d(TAG, "blocked navigation (${request.url.scheme}:${request.url.host.orEmpty()})")
            return true
        }
        // Kullanıcı hareketi olmadan, sunucu yönlendirmesi de olmadan başka siteye atlama: reklam yönlendirmesidir.
        if (request.isForMainFrame && !request.hasGesture() && !request.isRedirect &&
            !WebPolicy.sameSite(view.url, url)
        ) {
            Log.d(TAG, "blocked cross-site navigation without gesture (${request.url.host})")
            return true
        }
        return false
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        injectElementHiding(view, url)
        listener.onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String?) {
        injectElementHiding(view, url)
        listener.onPageFinished()
        schedulePlayerGuardCheck(view)
    }

    /**
     * Sayfanın alan adına uyan element gizleme seçicilerini tek bir CSS olarak enjekte eder:
     * `seçiciler { display:none !important; }` biçiminde <style> etiketi eklenir veya güncellenir.
     * Video oynatıcıların çalıştığı iç çerçevelere (iframe) dokunmaz, yalnızca ana belgeye uygulanır.
     * Ağ isteği yapmaz, veri toplamaz, console.log yazmaz, tamamen try/catch içindedir.
     */
    private fun injectElementHiding(view: WebView, url: String?) {
        if (!enableCssElementHiding || !hideRules.isLoaded) return
        val targetUrl = url ?: view.url ?: return
        val host = targetUrl.toHttpUrlOrNull()?.host ?: return
        val css = hideRules.buildCssForHost(host) ?: return
        val quotedCss = JSONObject.quote(css)
        val script = """
            (function() {
                try {
                    var css = $quotedCss;
                    var style = document.getElementById('__play_ad_hide__');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = '__play_ad_hide__';
                        style.type = 'text/css';
                        var target = document.head || document.documentElement || document.body;
                        if (target) {
                            target.appendChild(style);
                        } else {
                            document.addEventListener('DOMContentLoaded', function() {
                                try {
                                    var t = document.head || document.documentElement || document.body;
                                    if (t && !document.getElementById('__play_ad_hide__')) {
                                        t.appendChild(style);
                                    }
                                } catch(e1) {}
                            });
                        }
                    }
                    if (style && style.textContent !== css) {
                        style.textContent = css;
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    /**
     * Sayfa yüklendikten (onPageFinished + 1,5 sn sonra) oynatıcı koruma denetimi çalıştırır.
     * Sayfadaki video/oynatıcı iframe'i ya da ata kutuları display:none ise CSS'i kaldırır.
     * Adres veya URL loglamaz, yalnızca tek bir debug kaydı düşer.
     */
    private fun schedulePlayerGuardCheck(view: WebView) {
        if (!enableCssElementHiding) return
        view.postDelayed({
            try {
                view.evaluateJavascript(com.kaan.play.data.web.AdHidePlayerGuard.buildGuardScript()) { result ->
                    if (result?.contains("REMOVED") == true) {
                        Log.d(TAG, "player guard triggered: ad hide style removed to protect video playback")
                    }
                }
            } catch (e: Exception) {
                // Sessizce yutulur
            }
        }, 1500)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = listener.onHistoryChanged()

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) listener.onMainFrameError()
    }

    /**
     * Kumanda: sayfa "Yukarı"yı karşılayamıyorsa (en üstteyiz) odak üst çubuğa geçer. WebView bu tuşu yutar,
     * bu yüzden çubuğa başka türlü ulaşılamazdı.
     */
    override fun onUnhandledKeyEvent(view: WebView, event: KeyEvent) {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
            listener.onFocusLeaveUp()
        } else {
            super.onUnhandledKeyEvent(view, event)
        }
    }

    /** Sayfa süreci çökerse uygulama düşmesin; ekran kapanır. */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        listener.onRendererGone()
        return true
    }
}

/**
 * Açılır pencereleri reddeder, JS alert/confirm/prompt'u sessizce iptal eder, tam ekran videoyu [fullscreen]'e verir.
 */
class PlayWebChromeClient(
    private val policy: WebPolicy,
    private val onProgress: (Int) -> Unit,
    private val fullscreen: FullscreenHost,
) : WebChromeClient() {

    interface FullscreenHost {
        fun show(view: View, callback: CustomViewCallback)
        fun hide()
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)

    /**
     * Yeni pencere her durumda reddedilir (popunder ve açılır pencereler engellenir).
     * Kullanıcı aynı sekmede gezinmeye devam eder (<a target="_blank"> DOCUMENT_START_SCRIPT ile _self'e çevrilir).
     */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
        Log.d(TAG, "popup rejected")
        return false
    }

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.cancel(); return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.cancel(); return true
    }

    override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
        result.cancel(); return true
    }

    /** "Sayfadan ayrılsın mı?" kutusu çıkışı engellemesin: sessizce onaylanır. */
    override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
        result.confirm(); return true
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = fullscreen.show(view, callback)

    override fun onHideCustomView() = fullscreen.hide()
}
