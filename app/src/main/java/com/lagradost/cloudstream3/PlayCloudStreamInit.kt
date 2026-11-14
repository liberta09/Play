package com.lagradost.cloudstream3

import android.util.Log
import com.lagradost.nicehttp.Requests
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import java.io.IOException

/**
 * Eklentilerin OkHttp Interceptor çağrılarında fırlattığı Error/Throwable
 * (NoClassDefFoundError, NoSuchFieldError vb.) hatalarının ana uygulamayı
 * çökertmemesi için güvenli try-catch(Throwable) sarmalayıcısı.
 */
class SafePluginInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        return try {
            chain.proceed(chain.request())
        } catch (t: Throwable) {
            if (t is IOException) throw t
            Log.e("PlayCs", "Plugin OkHttp Interceptor zincirinde kritik hata yakalandı: ${t.javaClass.name}: ${t.message}", t)
            throw IOException("Plugin interceptor hatası (${t.javaClass.simpleName}): ${t.message}", t)
        }
    }
}

/**
 * Eklentinin tek bir yanıtta okuyabileceği en çok bayt (az bellekli cihaz, [com.kaan.play.data.cloudstream.CsSearchBudget]).
 * Bildirilen boyut sınırı aşarsa yanıt hiç okunmaz; bildirilmemişse okuma sınırda kesilir (IOException, eklenti hata olarak görür).
 * Yanlışlıkla bir video dosyasını ya da dev bir sayfayı belleğe okuyan eklenti 256 MB'lık yığını doldurmasın.
 *
 * Caps how many bytes a plugin may read from a single response (low-memory devices).
 */
class ResponseSizeLimitInterceptor(private val maxBytes: Long) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val declared = body.contentLength()
        if (declared > maxBytes) {
            response.close()
            throw IOException("Yanıt çok büyük: $declared bayt (sınır $maxBytes)")
        }
        val limited = object : ForwardingSource(body.source()) {
            private var total = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val n = super.read(sink, byteCount)
                if (n > 0) {
                    total += n
                    if (total > maxBytes) throw IOException("Yanıt çok büyük: $maxBytes bayttan fazla")
                }
                return n
            }
        }.buffer()
        return response.newBuilder().body(limited.asResponseBody(body.contentType(), declared)).build()
    }
}

/**
 * CloudStream çalışma zamanı başlatma köprüsü.
 * Play'in sağladığı OkHttpClient'ı CloudStream'in global `app` nesnesine aktarır. [maxResponseBytes] verilirse eklentilerin tek
 * yanıtı bu boyutla sınırlanır ([ResponseSizeLimitInterceptor]).
 */
fun initApp(client: OkHttpClient, maxResponseBytes: Long? = null) {
    val safeClient = client.newBuilder()
        .addInterceptor(SafePluginInterceptor())
        .apply { if (maxResponseBytes != null) addInterceptor(ResponseSizeLimitInterceptor(maxResponseBytes)) }
        .build()
    app.baseClient = safeClient
    insecureApp.baseClient = safeClient
}
