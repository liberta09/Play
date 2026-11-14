package com.lagradost.cloudstream3.network

import android.util.Log
import androidx.annotation.AnyThread
import com.kaan.play.data.cloudstream.cloudflare.CloudflareCache
import com.kaan.play.data.cloudstream.cloudflare.CloudflareDetector
import com.kaan.play.data.cloudstream.cloudflare.CloudflareResult
import com.kaan.play.data.cloudstream.cloudflare.CloudflareSolver
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * CloudStream eklentilerinin kullandığı Cloudflare çözücü Interceptor.
 * Play'in `CloudflareSolver`, `CloudflareCache` ve `CloudflareVerifyActivity`
 * mekanizmalarıyla tam entegre çalışır.
 */
@AnyThread
open class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
        }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        val request = chain.request()
        val host = request.url.host

        // 1. Önce önbellekte (CloudflareCache veya CookieManager) cf_clearance var mı?
        val cachedCreds = CloudflareCache.get(host)
        val requestWithCf = if (cachedCreds != null) {
            val builder = request.newBuilder()
            if (!cachedCreds.userAgent.isNullOrBlank()) {
                builder.header("user-agent", cachedCreds.userAgent)
            }
            val cookieHeader = cachedCreds.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (cookieHeader.isNotBlank()) {
                val existingCookie = request.header("Cookie")
                val merged = if (existingCookie.isNullOrBlank()) cookieHeader else "$existingCookie; $cookieHeader"
                builder.header("Cookie", merged)
            }
            builder.build()
        } else {
            request
        }

        val response = chain.proceed(requestWithCf)

        val peekBody = try { response.peekBody(16384).string() } catch (_: Throwable) { null }
        val isCfBlocked = CloudflareDetector.isChallenge(response, peekBody)

        if (!isCfBlocked) {
            return@runBlocking response
        }

        // Az bellekli cihazda kaynak araması sürerken gizli WebView açılmaz: site çözülmeden "doğrulama gerekiyor" diye işaretlenir
        // (telefondaki Doğrula akışı bu kaydı kullanır), eklenti engel sayfasını alır ve kaynak vermez.
        if (CloudflareSolver.solveSkipped()) {
            Log.d(TAG, "Cloudflare engeli, kaynak araması sürüyor: çözücü açılmadı ($host)")
            CloudflareSolver.markBlocked(host, request.url.toString())
            CloudflareSolver.noteUnresolved(host, request.url.toString())
            return@runBlocking response
        }

        // 2. Cloudflare engeli ile karşılaşıldı: taze çözümleme dene
        response.close()
        Log.d(TAG, "Cloudflare engeli algılandı: ${request.url}")

        // Elimizdeki cf_clearance (bellek + WebView deposu) bu yanıtla geçersiz çıktı. Silinmezse çözücü onu
        // önbellekten/çerez deposundan anında "çözüldü" sayar ve hiç yeniden denemez (DiziBox boş liste hatası).
        CloudflareSolver.invalidate(host, request.url.toString())
        val solveResult = CloudflareSolver.solve(request.url.toString())
        if (solveResult is CloudflareResult.Success) {
            val creds = solveResult.credentials
            val retryBuilder = request.newBuilder()
            if (!creds.userAgent.isNullOrBlank()) {
                retryBuilder.header("user-agent", creds.userAgent)
            }
            val cookieHeader = creds.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (cookieHeader.isNotBlank()) {
                val existingCookie = request.header("Cookie")
                val merged = if (existingCookie.isNullOrBlank()) cookieHeader else "$existingCookie; $cookieHeader"
                retryBuilder.header("Cookie", merged)
            }
            val secondTry = chain.proceed(retryBuilder.build())
            val secondPeek = try { secondTry.peekBody(16384).string() } catch (_: Throwable) { null }
            if (CloudflareDetector.isChallenge(secondTry, secondPeek)) {
                CloudflareSolver.markBlocked(host, request.url.toString())
                CloudflareSolver.noteUnresolved(host, request.url.toString())
            }
            return@runBlocking secondTry
        } else {
            CloudflareSolver.markBlocked(host, request.url.toString())
            CloudflareSolver.noteUnresolved(host, request.url.toString())
            return@runBlocking chain.proceed(requestWithCf)
        }
    }
}
