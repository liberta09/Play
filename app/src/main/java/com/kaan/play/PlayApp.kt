package com.kaan.play

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.kaan.play.data.web.AdHostLoader

class PlayApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    companion object {
        lateinit var appContext: android.content.Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // [ADIM 2] Küresel Çökme Kalkanı (UncaughtExceptionHandler)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper() || thread == android.os.Looper.getMainLooper().thread
            val tName = thread.name
            val isBgTarget = !isMain ||
                tName.contains("PlayPlugin", ignoreCase = true) ||
                tName.contains("PlayCs", ignoreCase = true) ||
                tName.contains("PlayAddon", ignoreCase = true) ||
                tName.contains("OkHttp", ignoreCase = true) ||
                tName.contains("DefaultDispatcher", ignoreCase = true) ||
                tName.contains("ExoPlayer", ignoreCase = true)

            if (!isMain && isBgTarget) {
                android.util.Log.e("PlayCrashGuard", "Arka plan çökmesi engellendi [thread=${thread.name}]: ${throwable.message}", throwable)
            } else {
                try {
                    val crashFile = java.io.File(filesDir, "last_crash.txt")
                    val writer = java.io.StringWriter()
                    throwable.printStackTrace(java.io.PrintWriter(writer))
                    crashFile.writeText("Thread: ${thread.name}\nTime: ${System.currentTimeMillis()}\nMessage: ${throwable.message}\nStack:\n$writer")
                } catch (t: Throwable) {
                    android.util.Log.e("PlayCrashGuard", "last_crash.txt yazılamadı: ${t.message}")
                }
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        container = AppContainer(this)
        // Reklam ve izleyici alan adı engelleme listesi arka planda yüklenir (AdDomains.all ile birleşir).
        AdHostLoader.ensureLoaded(this)
        // Element gizleme kuralları arka planda yüklenir (Dispatchers.IO).
        com.kaan.play.data.web.AdHideLoader.ensureLoaded(this)
        // İndirme yöneticisi açılışta kurulur: yarım kalan indirmeler (uygulama öldürüldükten sonra) kendiliğinden devam eder.
        container.downloads
        // Varsayılan altyazı ve katalog addon'ları ilk açılışta bir kez tohumlanır (kullanıcı kararları 2026-10-20/21; ağa çıkmaz).
        container.seedDefaultAddons()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            container.httpClient.newBuilder()
                                .addInterceptor { chain ->
                                    val req = chain.request().newBuilder()
                                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        .build()
                                    chain.proceed(req)
                                }
                                .build()
                        }
                    )
                )
            }
            .build()
    }
}
