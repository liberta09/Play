package com.kaan.play.data.web

import kotlin.math.roundToInt

/**
 * Kumanda ok tuşlarının yönleri.
 */
enum class MoveDirection {
    UP, DOWN, LEFT, RIGHT
}

/**
 * İmlecin hareket sonucu.
 *
 * @property x İmlecin güncel X koordinatı (ekran sınırları içinde)
 * @property y İmlecin güncel Y koordinatı (ekran sınırları içinde)
 * @property scrollDx Kenara dayanıldığında sayfayı yatay kaydırma miktarı (piksel)
 * @property scrollDy Kenara dayanıldığında sayfayı dikey kaydırma miktarı (piksel)
 * @property step Bu adımda kullanılan hız / adım büyüklüğü
 */
data class MoveResult(
    val x: Float,
    val y: Float,
    val scrollDx: Int,
    val scrollDy: Int,
    val step: Float
)

/**
 * TV / kumanda kipinde web sayfası üzerinde gezinen sanal imlecin hareket, ivmelenme,
 * sınır kırpma, kenar kaydırma ve soluklaşma mantığını yöneten Android'den bağımsız denetleyici.
 */
class WebCursorController(
    var viewportWidth: Float = 0f,
    var viewportHeight: Float = 0f,
    val baseStep: Float = 12f,
    val maxStep: Float = 40f,
    val accelerationDurationMs: Long = 800L,
    val inactivityTimeoutMs: Long = DEFAULT_INACTIVITY_TIMEOUT_MS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    var x: Float = 0f
        private set

    var y: Float = 0f
        private set

    var isVisible: Boolean = true
        private set

    private var _isFaded: Boolean = false

    /** 3 sn tuş basılmazsa imleç soluklaşır. */
    val isFaded: Boolean
        get() = _isFaded || (clock() - lastInteractionTimeMs >= inactivityTimeoutMs)

    private var lastInteractionTimeMs: Long = clock()
    private var pressStartTimeMs: Long = 0L
    private var lastMoveTimeMs: Long = 0L
    private var currentDirection: MoveDirection? = null

    /** Durum değiştiğinde (konum, görünürlük, soluklaşma) tetiklenen dinleyici. */
    var onChange: (() -> Unit)? = null

    /**
     * Görüntü alanı boyutunu günceller. İlk çağrıda imleci ekranın ortasına yerleştirir.
     */
    fun setViewport(width: Float, height: Float, resetToCenter: Boolean = false) {
        val wasZero = viewportWidth <= 0f || viewportHeight <= 0f
        viewportWidth = width.coerceAtLeast(0f)
        viewportHeight = height.coerceAtLeast(0f)
        if (wasZero || resetToCenter) {
            x = (viewportWidth / 2f).coerceAtLeast(0f)
            y = (viewportHeight / 2f).coerceAtLeast(0f)
        } else {
            clampPosition()
        }
        notifyChange()
    }

    /**
     * İmleç konumunu doğrudan belirler.
     */
    fun setPosition(newX: Float, newY: Float) {
        x = newX
        y = newY
        clampPosition()
        notifyChange()
    }

    private fun clampPosition() {
        x = x.coerceIn(0f, viewportWidth)
        y = y.coerceIn(0f, viewportHeight)
    }

    /**
     * Kullanıcı etkileşiminde (tuşa basıldığında, tıklandığında vb.) soluklaşmayı sıfırlar.
     */
    fun onUserInteraction(timeMs: Long = clock()) {
        val wasFaded = isFaded
        lastInteractionTimeMs = timeMs
        _isFaded = false
        if (wasFaded) {
            notifyChange()
        }
    }

    /**
     * Zaman aşımı kontrolü; 3 sn geçmişse soluklaşma durumuna geçer.
     * @return Soluklaşma durumu değiştiyse true
     */
    fun updateInactivity(timeMs: Long = clock()): Boolean {
        val shouldFade = (timeMs - lastInteractionTimeMs) >= inactivityTimeoutMs
        if (shouldFade != _isFaded) {
            _isFaded = shouldFade
            notifyChange()
            return true
        }
        return false
    }

    /**
     * İmleci görünür yapar ve soluklaşmayı sıfırlar.
     */
    fun show(timeMs: Long = clock()) {
        isVisible = true
        _isFaded = false
        lastInteractionTimeMs = timeMs
        notifyChange()
    }

    /**
     * Üst çubuğa geçildiğinde veya video tam ekran olduğunda imleci gizler.
     */
    fun hide() {
        isVisible = false
        resetAcceleration()
        notifyChange()
    }

    /**
     * Basılı tutma ivmelenmesini sıfırlar (tuş bırakıldığında çağrılır).
     */
    fun resetAcceleration() {
        pressStartTimeMs = 0L
        lastMoveTimeMs = 0L
        currentDirection = null
    }

    /**
     * Belirtilen yönde imleci hareket ettirir.
     * Basılı tutuldukça [baseStep]'ten [maxStep]'e doğru ivmelenir.
     * Ekran sınırına dayandığında imleç sınırda kalır ve aşan miktar kadar sayfa kaydırma çıktısı üretir.
     */
    fun move(direction: MoveDirection, timeMs: Long = clock()): MoveResult {
        onUserInteraction(timeMs)

        // Aynı yönde basılı tutuluyorsa ivmelenme süresini hesapla
        val isConsecutive = currentDirection == direction && (timeMs - lastMoveTimeMs) <= HOLD_TIMEOUT_MS
        if (!isConsecutive) {
            pressStartTimeMs = timeMs
            currentDirection = direction
        }
        lastMoveTimeMs = timeMs

        val holdDuration = (timeMs - pressStartTimeMs).coerceAtLeast(0L)
        val accelProgress = if (accelerationDurationMs > 0L) {
            (holdDuration.toFloat() / accelerationDurationMs).coerceIn(0f, 1f)
        } else 0f

        val step = baseStep + (maxStep - baseStep) * accelProgress

        var dx = 0f
        var dy = 0f
        when (direction) {
            MoveDirection.UP -> dy = -step
            MoveDirection.DOWN -> dy = step
            MoveDirection.LEFT -> dx = -step
            MoveDirection.RIGHT -> dx = step
        }

        val targetX = x + dx
        val targetY = y + dy

        var scrollDx = 0
        var scrollDy = 0

        if (targetX < 0f) {
            scrollDx = targetX.roundToInt().coerceAtMost(-1)
            x = 0f
        } else if (targetX > viewportWidth) {
            scrollDx = (targetX - viewportWidth).roundToInt().coerceAtLeast(1)
            x = viewportWidth
        } else {
            x = targetX
        }

        if (targetY < 0f) {
            scrollDy = targetY.roundToInt().coerceAtMost(-1)
            y = 0f
        } else if (targetY > viewportHeight) {
            scrollDy = (targetY - viewportHeight).roundToInt().coerceAtLeast(1)
            y = viewportHeight
        } else {
            y = targetY
        }

        notifyChange()

        return MoveResult(
            x = x,
            y = y,
            scrollDx = scrollDx,
            scrollDy = scrollDy,
            step = step
        )
    }

    private fun notifyChange() {
        onChange?.invoke()
    }

    companion object {
        const val HOLD_TIMEOUT_MS = 400L
        const val DEFAULT_INACTIVITY_TIMEOUT_MS = 3000L
    }
}
