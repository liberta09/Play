package com.kaan.play.ui.player

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * ExoPlayer DefaultLoadControl bellek ve arabellek boyutu yapılandırması.
 *
 * TV kutuları ve düşük RAM'li cihazlarda OOM riskini önlemek için commit b7dab38 ile
 * getirilen 16 MB sınırı ve boyut önceliği korunur.
 * Yeterli belleği olan modern telefon ve tabletlerde yüksek bit hızlı (4K / 20+ Mbps)
 * akışların DefaultLoadControl tavanına çarparak Media3 StuckPlayerException (1004) hatasına
 * düşmesini engellemek için 48-64 MB tavan ve zaman önceliği uygulanır.
 */
object PlayerBufferConfig {
    const val BUFFER_BYTES_TV_OR_LOW_RAM = 16 * 1024 * 1024 // 16 MB (b7dab38)
    const val BUFFER_BYTES_PHONE_STANDARD = 48 * 1024 * 1024 // 48 MB
    const val BUFFER_BYTES_PHONE_HIGH = 64 * 1024 * 1024 // 64 MB

    /**
     * Donanım özelliklerine göre ExoPlayer hedef arabellek bayt boyutunu hesaplar.
     * Saf fonksiyondur (Android Framework bağımlılığı olmadan birim test edilebilir).
     *
     * @param isLowRam Cihaz düşük RAM profiline mi sahip (ActivityManager.isLowRamDevice)
     * @param isTv Cihaz Android TV / TV box modunda mı (FEATURE_LEANBACK veya UI_MODE_TYPE_TELEVISION)
     * @param memoryClass Standart Dalvik yığın sınıfı (MB)
     * @param largeMemoryClass Large heap açıkken ayrılan Dalvik yığın sınıfı (MB)
     * @return Hedef arabellek boyutu (bayt)
     */
    fun computeTargetBufferBytes(
        isLowRam: Boolean,
        isTv: Boolean,
        memoryClass: Int,
        largeMemoryClass: Int,
    ): Int {
        if (isLowRam || isTv) {
            return BUFFER_BYTES_TV_OR_LOW_RAM
        }
        val effectiveMemory = maxOf(memoryClass, largeMemoryClass)
        return when {
            effectiveMemory >= 256 -> BUFFER_BYTES_PHONE_HIGH
            effectiveMemory >= 192 -> BUFFER_BYTES_PHONE_STANDARD
            else -> BUFFER_BYTES_TV_OR_LOW_RAM
        }
    }

    /**
     * TV ve düşük bellekli cihazlarda OOM riskini önlemek için boyut sınırı zamana baskın gelir (false).
     * Telefon ve tabletlerde ise akışın duraksamaması için zaman eşiklerine (minBufferMs) öncelik verilir (true).
     */
    fun shouldPrioritizeTimeOverSize(
        isLowRam: Boolean,
        isTv: Boolean,
    ): Boolean = !isLowRam && !isTv

    /**
     * Android Context üzerinden donanım parametrelerini okuyarak hedef arabellek baytını döner.
     */
    fun fromContext(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice ?: false
        val memoryClass = am?.memoryClass ?: 128
        val largeMemoryClass = am?.largeMemoryClass ?: memoryClass

        val pm = context.packageManager
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        return computeTargetBufferBytes(isLowRam, isTv, memoryClass, largeMemoryClass)
    }

    /**
     * Android Context üzerinden zaman önceliği bayrağını döner.
     */
    fun prioritizeTimeFromContext(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice ?: false

        val pm = context.packageManager
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        return shouldPrioritizeTimeOverSize(isLowRam, isTv)
    }
}
