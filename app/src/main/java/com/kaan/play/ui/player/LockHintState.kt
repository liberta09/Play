package com.kaan.play.ui.player

/**
 * Kilitliyken kilit açma simgesinin zamanlama ve görünürlük mantığı (Android'den bağımsız).
 *
 * Kilitlenince veya kilitliyken ekrana dokunulunca / kumanda tuşuna basılınca
 * simge [timeoutMs] (varsayılan 3000 ms) boyunca görünür, ardından kaybolur.
 */
class LockHintState(
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    var visibleUntil: Long = 0L
        private set

    fun show(now: Long) {
        visibleUntil = now + timeoutMs
    }

    fun hide() {
        visibleUntil = 0L
    }

    fun isVisible(now: Long): Boolean = now < visibleUntil

    fun remainingMs(now: Long): Long = maxOf(0L, visibleUntil - now)

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 3000L
    }
}
