package com.kaan.play.data.web

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * `app/src/main/assets/adhide.txt` dosyasını arka planda (Dispatchers.IO) okuyarak
 * [AdHideRules.default] deposuna yükleyen yardımcı sınıf.
 *
 * Ana iş parçacığını kilitlemez, yalnızca bir kez çalışır (idempotent).
 * Yükleme tamamlanana kadar hiçbir kural işletilmez, sayfalar normal açılır.
 */
object AdHideLoader {

    private const val TAG = "AdHideLoader"
    private const val ASSET_FILE = "adhide.txt"
    private val started = AtomicBoolean(false)

    fun ensureLoaded(
        context: Context,
        rules: AdHideRules = AdHideRules.default,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        if (rules.isLoaded) return
        if (started.compareAndSet(false, true)) {
            val appContext = context.applicationContext
            scope.launch(dispatcher) {
                try {
                    val start = System.currentTimeMillis()
                    appContext.assets.open(ASSET_FILE).bufferedReader().use { reader ->
                        rules.loadFromReader(reader)
                    }
                    val elapsed = System.currentTimeMillis() - start
                    Log.d(TAG, "adhide.txt yuklendi: ${rules.genericRuleCount} genel, ${rules.domainRuleCount} alan kurali ($elapsed ms)")
                } catch (e: Exception) {
                    Log.w(TAG, "adhide.txt yuklenemedi; sayfa normal acilacak", e)
                }
            }
        }
    }
}
