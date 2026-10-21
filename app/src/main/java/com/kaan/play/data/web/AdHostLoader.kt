package com.kaan.play.data.web

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `app/src/main/assets/adhosts.txt` dosyasını arka planda (Dispatchers.IO) okuyarak
 * [AdHostMatcher.default] eşleştiricisine yükleyen yardımcı sınıf.
 *
 * Ana iş parçacığını kilitlemez, yalnızca bir kez çalışır (idempotent).
 * Yükleme tamamlanana kadar [AdDomains.all] etkindir; tamamlandığında yeni liste ile birleşir.
 */
object AdHostLoader {

    private const val TAG = "AdHostLoader"
    private const val ASSET_FILE = "adhosts.txt"
    private val started = AtomicBoolean(false)

    fun ensureLoaded(
        context: Context,
        matcher: AdHostMatcher = AdHostMatcher.default,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        if (matcher.isLoaded) return
        if (started.compareAndSet(false, true)) {
            val appContext = context.applicationContext
            scope.launch(dispatcher) {
                try {
                    val start = System.currentTimeMillis()
                    appContext.assets.open(ASSET_FILE).bufferedReader().use { reader ->
                        matcher.loadFromReader(reader)
                    }
                    val elapsed = System.currentTimeMillis() - start
                    Log.d(TAG, "adhosts.txt yuklendi: ${matcher.domainCount} alan adi ($elapsed ms)")
                } catch (e: Exception) {
                    Log.w(TAG, "adhosts.txt yuklenemedi; AdDomains.all devrede kalacak", e)
                }
            }
        }
    }
}
