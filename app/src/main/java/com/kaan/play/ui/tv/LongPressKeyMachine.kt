package com.kaan.play.ui.tv

/**
 * Android ve Compose bağımsız, TV kumandası veya dokunmatikte uzun basma / basılı tutma
 * davranışını yöneten saf durum makinesi.
 *
 * Temel kural: Uzun basma 500 ms veya sistemin FLAG_LONG_PRESS bayrağı ile ALGILANIR
 * (tuş basılıyken görsel geri bildirim/ripple sürer), ancak onay penceresi kullanıcı
 * tuşu BIRAKTIĞINDA (KeyUp) açılır. Böylece basılı tutarken otomatik tekrar eden KeyDown
 * ve nihai KeyUp olayları henüz açılmamış olan penceredeki odaklı düğmeye ("Vazgeç")
 * sızıp pencereyi anında kapatamaz.
 */
class LongPressKeyMachine(
    val longPressThresholdMs: Long = 500L,
) {
    enum class State {
        IDLE,
        PRESSED,
        LONG_PRESS_ARMED,
    }

    sealed interface Action {
        /** Tuşa ilk kez basıldı: görsel geri bildirim başlatılmalı, 500 ms zamanlayıcı kurulmalı. */
        data object Press : Action

        /** Kısa bas-bırak tamamlandı: normal tıklama işletilmeli. */
        data object Click : Action

        /** Uzun basılı tutulduktan sonra tuş bırakıldı: onay penceresi açılmalı (Click işletilmez). */
        data object ShowDialogOnRelease : Action

        /** Odak kaybı veya iptal: basılı tutma/zamanlayıcı iptal edilmeli, görsel sıfırlanmalı. */
        data object Cancel : Action

        /** Herhangi bir ek eylem yok. */
        data object Nothing : Action
    }

    var state: State = State.IDLE
        private set

    /**
     * KeyDown olayı geldiğinde çağrılır.
     *
     * @param repeatCount Android KeyEvent.repeatCount (0 = ilk basış, >0 = otomatik tekrar)
     * @param isLongPress Android KeyEvent.isLongPress veya FLAG_LONG_PRESS bayrağı
     */
    fun onKeyDown(repeatCount: Int = 0, isLongPress: Boolean = false): Action {
        return when (state) {
            State.IDLE -> {
                if (isLongPress) {
                    state = State.LONG_PRESS_ARMED
                    Action.Nothing
                } else {
                    state = State.PRESSED
                    Action.Press
                }
            }
            State.PRESSED -> {
                if (isLongPress) {
                    state = State.LONG_PRESS_ARMED
                }
                Action.Nothing
            }
            State.LONG_PRESS_ARMED -> {
                Action.Nothing
            }
        }
    }

    /**
     * 500 ms (veya yapılandırılan eşik) dolduğunda çağrılır.
     */
    fun onTimeout(): Action {
        return when (state) {
            State.PRESSED -> {
                state = State.LONG_PRESS_ARMED
                Action.Nothing
            }
            State.IDLE, State.LONG_PRESS_ARMED -> {
                Action.Nothing
            }
        }
    }

    /**
     * KeyUp olayı geldiğinde (tuş bırakıldığında) çağrılır.
     */
    fun onKeyUp(): Action {
        return when (state) {
            State.LONG_PRESS_ARMED -> {
                state = State.IDLE
                Action.ShowDialogOnRelease
            }
            State.PRESSED -> {
                state = State.IDLE
                Action.Click
            }
            State.IDLE -> {
                Action.Nothing
            }
        }
    }

    /**
     * Odak kaybedildiğinde veya bileşen kapsam dışı kaldığında çağrılır.
     */
    fun onFocusLost(): Action {
        val wasActive = state != State.IDLE
        state = State.IDLE
        return if (wasActive) Action.Cancel else Action.Nothing
    }

    /**
     * Tüm iç durumu IDLE'a döndürür.
     */
    fun reset() {
        state = State.IDLE
    }
}
