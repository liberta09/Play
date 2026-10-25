package com.kaan.play

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.kaan.play.data.web.AdHostLoader
import okio.Path.Companion.toOkioPath
import java.util.concurrent.ConcurrentHashMap

class PlayApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    companion object {
        lateinit var appContext: android.content.Context
            private set
    }

    private val wrappedLifecycleCallbacks = ConcurrentHashMap<ActivityLifecycleCallbacks, ActivityLifecycleCallbacks>()

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // [ADIM 2] Küresel Çökme Kalkanı (UncaughtExceptionHandler + CrashReporter)
        CrashReporter.init(this)
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

    override fun registerActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        val safeCallback = object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                try {
                    callback.onActivityCreated(activity, savedInstanceState)
                } catch (t: Throwable) {
                    Log.e("PlayApp", "ActivityLifecycleCallbacks.onActivityCreated hatası engellendi [${t.javaClass.simpleName}]: ${t.message}")
                }
            }

            override fun onActivityStarted(activity: Activity) {
                try {
                    callback.onActivityStarted(activity)
                } catch (t: Throwable) {
                    Log.e("PlayApp", "ActivityLifecycleCallbacks.onActivityStarted hatası engellendi [${t.javaClass.simpleName}]: ${t.message}")
                }
            }

            override fun onActivityResumed(activity: Activity) {
                try {
                    callback.onActivityResumed(activity)
                } catch (t: Throwable) {
                    Log.e("PlayApp", "ActivityLifecycleCallbacks.onActivityResumed hatası engellendi [${t.javaClass.simpleName}]: ${t.message}")
                }
            }

            override fun onActivityPaused(activity: Activity) {
                try {
                    callback.onActivityPaused(activity)
                } catch (t: Throwable) {
                    Log.e("PlayApp", "ActivityLifecycleCallbacks.onActivityPaused hatası engellendi [${t.javaClass.simpleName}]: ${t.message}")
                }
            }

            override fun onActivityStopped(activity: Activity) {
                try {
                    callback.onActivityStopped(activity)
                } catch (t: Throwable) {
                    Log.e("PlayApp", "ActivityLifecycleCallbacks.onActivityStopped hatası engellendi [${t.javaClass.simpleName}]: ${t.message}")
                }
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                try {
                    callback.onActivitySaveInstanceState(activity, outState)
                } catch (t: Throwable) {
                    Log.e("PlayApp", "ActivityLifecycleCallbacks.onActivitySaveInstanceState hatası engellendi [${t.javaClass.simpleName}]: ${t.message}")
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                try {
                    callback.onActivityDestroyed(activity)
                } catch (t: Throwable) {
                    Log.e("PlayApp", "ActivityLifecycleCallbacks.onActivityDestroyed hatası engellendi [${t.javaClass.simpleName}]: ${t.message}")
                }
            }
        }
        wrappedLifecycleCallbacks[callback] = safeCallback
        super.registerActivityLifecycleCallbacks(safeCallback)
    }

    override fun unregisterActivityLifecycleCallbacks(callback: ActivityLifecycleCallbacks) {
        val safeCallback = wrappedLifecycleCallbacks.remove(callback) ?: callback
        super.unregisterActivityLifecycleCallbacks(safeCallback)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("poster-cache").toOkioPath())
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
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
