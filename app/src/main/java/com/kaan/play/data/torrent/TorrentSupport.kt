package com.kaan.play.data.torrent

import android.os.Build

/**
 * Eski Android sürümlerinde (API < 28) veya libtorrent4j yerel kütüphanesinin yüklenemediği
 * cihazlarda (UnsatisfiedLinkError vb.) torrent desteğini yöneten kurallar.
 * Oturum boyunca kalıcı olmayan (in-memory) bayrak tutar.
 */
object TorrentSupport {

    /** Oturum boyunca tek seferlik "torrent kullanılamıyor" bayrağı (hata yakalanınca set edilir). */
    @Volatile
    var isEngineUnavailable: Boolean = false

    /** Yalnızca test/hata ayıklama amaçlı: yerel kütüphane çökmesini simüle eder. */
    @Volatile
    var debugForceFailNative: Boolean = false

    /**
     * Yerleşik (built-in libtorrent) motorun bu cihazda/oturumda kullanılıp kullanılamayacağını döner.
     * SDK < 28 ise (minSdkVersion 21 olduğundan sdkInt 1..27 arası) ya da oturumda native kütüphane
     * yüklenirken hata alındıysa false döner. JVM unit testlerinde Build.VERSION.SDK_INT = 0 olduğu
     * için 0 değeri gerçek cihaz sayılmaz.
     */
    fun isBuiltInSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        if (debugForceFailNative) return false
        if (isEngineUnavailable) return false
        if (sdkInt in 1..27) return false
        return true
    }

    /**
     * Kullanıcının harici bir TorrServer adresi olup olmadığını da dikkate alarak
     * torrent kaynaklarının (yerleşik veya harici sunucu ile) kullanılabilirliğini döner.
     */
    fun isTorrentUsable(
        hasTorrServer: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean {
        if (hasTorrServer) return true
        return isBuiltInSupported(sdkInt)
    }

    fun markUnavailable() {
        isEngineUnavailable = true
    }

    fun resetForTest() {
        isEngineUnavailable = false
        debugForceFailNative = false
    }
}
