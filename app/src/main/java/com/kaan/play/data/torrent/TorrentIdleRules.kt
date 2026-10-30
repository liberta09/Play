package com.kaan.play.data.torrent

/**
 * Torrent oturumunun boşta ne zaman kapanacağı (Android'den ve libtorrent'ten bağımsız, birim testli).
 *
 * Kök neden (2026-10-30, "Torrent bazen geç açılıyor/açılmıyor"): oturum son oynatıcı kapanır kapanmaz durduruluyor ve
 * dizin siliniyordu; ardından açılan her film soğuk DHT + eş arama + üst bilgi (metadata) beklemesiyle başlıyordu. Oturum
 * artık boşta [IDLE_MS] boyunca açık kalır: DHT ve izleyici bağlantıları sıcak kalır, art arda açılan filmler hızlı başlar.
 * Açık torrent'in verisi yine hemen silinir; yalnızca boş oturum bekler.
 */
object TorrentIdleRules {

    /** Son oynatıcı kapandıktan sonra boş oturumun açık kalacağı süre. */
    const val IDLE_MS = 10 * 60 * 1000L

    /**
     * Boş oturum kapatılsın mı? [users]: açık oynatıcı sayısı, [hasActive]: açık torrent var mı, [idleSinceMs]: oturumun
     * en son boşa düştüğü an (hiç boşa düşmediyse null), [now]: şimdi.
     */
    fun shouldStop(users: Int, hasActive: Boolean, idleSinceMs: Long?, now: Long, idleMs: Long = IDLE_MS): Boolean {
        if (users > 0 || hasActive) return false
        val since = idleSinceMs ?: return false
        return now - since >= idleMs
    }
}
