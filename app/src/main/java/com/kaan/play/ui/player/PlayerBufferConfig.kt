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
 * Büyük arabellek kararı (64 MB) yalnızca ActivityManager.memoryClass >= 256 VE
 * toplam RAM >= 4 GB olduğunda verilir; aksi halde (leanback bildirmeyen 1-2 GB RAM'li TV kutuları dahil)
 * 16 MB kullanılır.
 * TV modunda ise memoryClass >= 192 VE toplam RAM >= 3 GB olduğunda 48 MB arabellek ayrılır.
 */
object PlayerBufferConfig {
    const val BUFFER_BYTES_TV_OR_LOW_RAM = 16 * 1024 * 1024 // 16 MB (b7dab38)
    const val BUFFER_BYTES_TV_HIGH = 48 * 1024 * 1024 // 48 MB
    const val BUFFER_BYTES_PHONE_HIGH = 64 * 1024 * 1024 // 64 MB
    const val MIN_TOTAL_MEM_BYTES_FOR_LARGE_BUFFER = 4L * 1024 * 1024 * 1024 // 4 GB
    const val MIN_TOTAL_MEM_BYTES_FOR_TV_HIGH = 3L * 1024 * 1024 * 1024 // 3 GB
    const val MIN_MEMORY_CLASS_FOR_TV_HIGH = 192

    /**
     * Donanım özelliklerine göre ExoPlayer hedef arabellek bayt boyutunu hesaplar.
     * Saf fonksiyondur (Android Framework bağımlılığı olmadan birim test edilebilir).
     *
     * @param isLowRam Cihaz düşük RAM profiline mi sahip (ActivityManager.isLowRamDevice)
     * @param isTv Cihaz Android TV / TV box modunda mı (FEATURE_LEANBACK veya UI_MODE_TYPE_TELEVISION)
     * @param memoryClass Standart Dalvik yığın sınıfı (MB)
     * @param totalMemBytes Cihazın toplam fiziksel bellek miktarı (bayt)
     * @return Hedef arabellek boyutu (bayt)
     */
    fun computeTargetBufferBytes(
        isLowRam: Boolean,
        isTv: Boolean,
        memoryClass: Int,
        totalMemBytes: Long,
    ): Int {
        if (isLowRam) {
            return BUFFER_BYTES_TV_OR_LOW_RAM
        }
        if (isTv) {
            return if (memoryClass >= MIN_MEMORY_CLASS_FOR_TV_HIGH && totalMemBytes >= MIN_TOTAL_MEM_BYTES_FOR_TV_HIGH) {
                BUFFER_BYTES_TV_HIGH
            } else {
                BUFFER_BYTES_TV_OR_LOW_RAM
            }
        }
        return if (memoryClass >= 256 && totalMemBytes >= MIN_TOTAL_MEM_BYTES_FOR_LARGE_BUFFER) {
            BUFFER_BYTES_PHONE_HIGH
        } else {
            BUFFER_BYTES_TV_OR_LOW_RAM
        }
    }

    /**
     * TV ve düşük bellekli cihazlarda OOM riskini önlemek için boyut sınırı zamana baskın gelir (false).
     * Yeterli belleğe sahip (memoryClass >= 256 ve totalMem >= 4 GB) telefon/tabletlerde ise
     * 4K ve yüksek bit hızlı içeriklerde DefaultLoadControl'ün yüklemeyi durdurarak 1004 StuckPlayerException
     * vermesini önlemek için zaman eşiklerine (minBufferMs) öncelik verilir (true).
     */
    fun shouldPrioritizeTimeOverSize(
        isLowRam: Boolean,
        isTv: Boolean,
        memoryClass: Int,
        totalMemBytes: Long,
    ): Boolean {
        if (isLowRam || isTv) return false
        return memoryClass >= 256 && totalMemBytes >= MIN_TOTAL_MEM_BYTES_FOR_LARGE_BUFFER
    }

    /**
     * Cihazın Android TV / TV kutusu olup olmadığını belirler.
     * FEATURE_LEANBACK veya UI_MODE_TYPE_TELEVISION kontrol edilir.
     */
    fun isTv(context: Context): Boolean {
        val pm = context.packageManager
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * PlayerView layout seçimi (TextureView vs SurfaceView) için TV kontrolü.
     */
    fun isTvLayout(context: Context): Boolean {
        return isTv(context) ||
            (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Android Context üzerinden donanım parametrelerini okuyarak hedef arabellek baytını döner.
     */
    fun fromContext(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice ?: false
        val memoryClass = am?.memoryClass ?: 128
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalMemBytes = memInfo.totalMem

        val isTv = isTv(context)

        return computeTargetBufferBytes(isLowRam, isTv, memoryClass, totalMemBytes)
    }

    /**
     * Android Context üzerinden zaman önceliği bayrağını belirler.
     */
    fun prioritizeTimeFromContext(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice ?: false
        val memoryClass = am?.memoryClass ?: 128
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalMemBytes = memInfo.totalMem

        val isTv = isTv(context)

        return shouldPrioritizeTimeOverSize(isLowRam, isTv, memoryClass, totalMemBytes)
    }
}
