package com.lagradost.cloudstream3

import android.util.Log
import com.lagradost.nicehttp.Requests
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
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
 * CloudStream çalışma zamanı başlatma köprüsü.
 * Play'in sağladığı OkHttpClient'ı CloudStream'in global `app` nesnesine aktarır.
 */
fun initApp(client: OkHttpClient) {
    val safeClient = client.newBuilder()
        .addInterceptor(SafePluginInterceptor())
        .build()
    app.baseClient = safeClient
    insecureApp.baseClient = safeClient
}
