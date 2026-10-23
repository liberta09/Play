package com.kaan.play.ui.player

import java.util.Locale

/**
 * Oynatıcı takılma (arabelleğe alma / stall) ölçüm sınıfı (Android'den bağımsız).
 *
 * Kurallar:
 * - ExoPlayer STATE_BUFFERING'e İLK KARE geldikten SONRA her girişte sayacı artır, süreyi topla.
 * - İlk kareden önceki ilk yükleme arabelleğe alması sayılmaz.
 * - Seek (kullanıcının ileri/geri sarması) sırasındaki arabelleğe alma sayılmaz.
 * - Oynatıcı kapanırken tek satır loglanır:
 *   "PlayMatch: takılma kaynak=<eklenti adı> sayı=N toplam=X.X sn oynama=Y dk"
 * - Adres, çerez, token ASLA yazılmaz (yalnızca kaynak/eklenti adı).
 */
class PlaybackStallTracker(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    var sourceName: String = "bilinmeyen"
        private set

    var firstFrameRendered: Boolean = false
        private set

    var isSeeking: Boolean = false
        private set

    var isBuffering: Boolean = false
        private set

    var stallCount: Int = 0
        private set

    var totalStallMs: Long = 0L
        private set

    var totalPlayMs: Long = 0L
        private set

    private var bufferingStartMs: Long? = null
    private val _pastStalls = mutableListOf<StallInterval>()

    val currentStallStartMs: Long?
        get() = bufferingStartMs

    val pastStalls: List<StallInterval>
        get() = _pastStalls.toList()

    fun now(): Long = clock()
    private var lastPlayStartMs: Long? = null
    private var isPlaying: Boolean = false

    fun reset(source: String?) {
        sourceName = source?.takeIf { it.isNotBlank() } ?: "bilinmeyen"
        firstFrameRendered = false
        isSeeking = false
        isBuffering = false
        stallCount = 0
        totalStallMs = 0L
        totalPlayMs = 0L
        bufferingStartMs = null
        _pastStalls.clear()
        lastPlayStartMs = null
        isPlaying = false
    }

    fun onFirstFrameRendered() {
        if (firstFrameRendered) return
        val now = clock()
        firstFrameRendered = true
        // İlk kareden önceki arabelleğe almayı sıfırla (sayılmaz)
        isBuffering = false
        bufferingStartMs = null
        if (isPlaying && lastPlayStartMs == null) {
            lastPlayStartMs = now
        }
    }

    fun onSeekStarted() {
        val now = clock()
        isSeeking = true
        // Eğer sarma anında bir takılma sürüyorsa, sarma öncesi süreyi ekle ve takılmayı kapat
        if (isBuffering) {
            bufferingStartMs?.let { start ->
                val duration = (now - start).coerceAtLeast(0L)
                totalStallMs += duration
                _pastStalls += StallInterval(start, now)
                _pastStalls.removeAll { it.endMs < now - StallPolicy.STALL_WINDOW_MS }
            }
            isBuffering = false
            bufferingStartMs = null
        }
    }

    fun onSeekEnded() {
        isSeeking = false
    }

    fun onPlaybackStateChanged(buffering: Boolean, isReady: Boolean): Boolean {
        val now = clock()
        var stallStarted = false
        if (buffering) {
            // Yalnızca ilk kare geldikten sonra VE kullanıcı sarma yapmıyorken takılma sayılır
            if (firstFrameRendered && !isSeeking && !isBuffering) {
                isBuffering = true
                bufferingStartMs = now
                stallCount++
                stallStarted = true
            }
        } else {
            if (isBuffering) {
                bufferingStartMs?.let { start ->
                    val duration = (now - start).coerceAtLeast(0L)
                    totalStallMs += duration
                    _pastStalls += StallInterval(start, now)
                    _pastStalls.removeAll { it.endMs < now - StallPolicy.STALL_WINDOW_MS }
                }
                isBuffering = false
                bufferingStartMs = null
            }
            if (isReady && isSeeking) {
                isSeeking = false
            }
        }
        return stallStarted
    }

    fun onIsPlayingChanged(playing: Boolean) {
        val now = clock()
        isPlaying = playing
        if (playing) {
            if (firstFrameRendered && lastPlayStartMs == null) {
                lastPlayStartMs = now
            }
        } else {
            lastPlayStartMs?.let { start ->
                totalPlayMs += (now - start).coerceAtLeast(0L)
                lastPlayStartMs = null
            }
        }
    }

    fun finish(): String {
        val now = clock()
        if (isBuffering) {
            bufferingStartMs?.let { start ->
                val duration = (now - start).coerceAtLeast(0L)
                totalStallMs += duration
                _pastStalls += StallInterval(start, now)
            }
            isBuffering = false
            bufferingStartMs = null
        }
        lastPlayStartMs?.let { start ->
            totalPlayMs += (now - start).coerceAtLeast(0L)
            lastPlayStartMs = null
        }
        return buildLogMessage()
    }

    fun buildLogMessage(): String {
        val stallSec = String.format(Locale.ROOT, "%.1f", totalStallMs / 1000.0)
        val playMin = (totalPlayMs / 60_000L).coerceAtLeast(0L)
        return "takılma kaynak=$sourceName sayı=$stallCount toplam=$stallSec sn oynama=$playMin dk"
    }

    companion object {
        /**
         * Takılma anındaki veya oynatıcı kapanışındaki ayrıntı satırını biçimlendirir.
         * Biçim: "takılma-ayrıntı kaynak=<ad> bitHızı=X Mbps bantTahmini=Y Mbps tampon=Z sn"
         */
        fun formatStallDetail(
            sourceName: String,
            videoBitrate: Int?,
            bandwidthEstimate: Long?,
            bufferedPositionMs: Long,
            currentPositionMs: Long,
        ): String {
            val bitHizi = if (videoBitrate != null && videoBitrate > 0) {
                String.format(Locale.ROOT, "%.2f Mbps", videoBitrate / 1_000_000.0)
            } else {
                "bilinmiyor"
            }
            val bantTahmini = if (bandwidthEstimate != null && bandwidthEstimate > 0L) {
                String.format(Locale.ROOT, "%.2f Mbps", bandwidthEstimate / 1_000_000.0)
            } else {
                "bilinmiyor"
            }
            val tamponSec = String.format(Locale.ROOT, "%.1f sn", (bufferedPositionMs - currentPositionMs).coerceAtLeast(0L) / 1000.0)
            return "takılma-ayrıntı kaynak=$sourceName bitHızı=$bitHizi bantTahmini=$bantTahmini tampon=$tamponSec"
        }
    }
}
