package com.kaan.play.ui.player

import androidx.compose.ui.input.key.Key

/** Oynatıcıda bir tuşun yaptığı iş. [PassThrough]: olayı dokunma kontrollerine (odaklı düğme/çubuk) bırak. */
enum class PlayerKeyAction { PassThrough, TogglePlay, SeekBack, SeekForward, Reveal, OpenMenu, GoToButtons, LeaveBarUp, ZapPrev, ZapNext }

/**
 * Oynatıcı kumanda eşlemesi (Android'den bağımsız, birim testli).
 *
 * Kontroller **gizliyken**: Tamam/Enter = oynat/duraklat; Sol/Sağ = geri/ileri sar; Yukarı = kontrolleri göster;
 * Aşağı (ya da Menü) = altyazı/kaynak menüsü; medya tuşları her zaman çalışır; başka herhangi bir tuş kontrolleri geri getirir.
 * Kontroller **açıkken** yön tuşları ve Tamam odaktaki düğmeye bırakılır (düğmeler arasında gezilir, Tamam basar).
 * Başlangıç odağı ilerleme çubuğundadır ([actionFor] `progressFocused`): Sol/Sağ 10 sn sarar, Tamam oynat/duraklatır,
 * Aşağı düğmelere geçer, düğmelerden Yukarı çubuğa döner.
 */
object PlayerKeys {

    fun actionFor(
        key: Key,
        controlsVisible: Boolean,
        progressFocused: Boolean = false,
        isLive: Boolean = false,
    ): PlayerKeyAction = when (key) {
        // Ses tuşlarını sistem işler; asla yutulmaz.
        Key.VolumeUp, Key.VolumeDown, Key.VolumeMute -> PlayerKeyAction.PassThrough
        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> PlayerKeyAction.TogglePlay
        Key.MediaFastForward, Key.MediaStepForward -> PlayerKeyAction.SeekForward
        Key.MediaRewind, Key.MediaStepBackward -> PlayerKeyAction.SeekBack
        Key.ChannelUp, Key.PageUp -> if (isLive) PlayerKeyAction.ZapNext else if (controlsVisible) PlayerKeyAction.PassThrough else PlayerKeyAction.Reveal
        Key.ChannelDown, Key.PageDown -> if (isLive) PlayerKeyAction.ZapPrev else if (controlsVisible) PlayerKeyAction.PassThrough else PlayerKeyAction.Reveal
        else -> if (controlsVisible) {
            // İlerleme çubuğu odaktayken Sol/Sağ 10 sn sarar, Tamam oynat/duraklatır, Aşağı düğmelere geçer, Yukarı çubuktan çıkar.
            if (progressFocused) when (key) {
                Key.DirectionLeft -> PlayerKeyAction.SeekBack
                Key.DirectionRight -> PlayerKeyAction.SeekForward
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerKeyAction.TogglePlay
                // Material Slider Yukarı/Aşağı ile değeri oynatır; odak gezintisi için olay burada alınır.
                Key.DirectionDown -> PlayerKeyAction.GoToButtons
                Key.DirectionUp -> PlayerKeyAction.LeaveBarUp
                else -> PlayerKeyAction.PassThrough
            } else PlayerKeyAction.PassThrough
        } else when (key) {
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerKeyAction.TogglePlay
            Key.DirectionLeft -> PlayerKeyAction.SeekBack
            Key.DirectionRight -> PlayerKeyAction.SeekForward
            Key.DirectionUp -> PlayerKeyAction.Reveal
            Key.DirectionDown, Key.Menu -> PlayerKeyAction.OpenMenu
            else -> PlayerKeyAction.Reveal
        }
    }

    /**
     * Activity.onKeyDown geri dönüşü için ham Android tuş kodu -> kanal yönü (`+1` ileri, `-1` geri, `0` değil).
     * Compose odak ağaçları dokunmatik kipte tuş almadığında yalnızca canlı yayında çağrılır.
     */
    fun zapDirectionForKeyCode(keyCode: Int, isLive: Boolean): Int = if (!isLive) 0 else when (keyCode) {
        KEYCODE_CHANNEL_UP, KEYCODE_PAGE_UP -> +1
        KEYCODE_CHANNEL_DOWN, KEYCODE_PAGE_DOWN -> -1
        else -> 0
    }

    private const val KEYCODE_PAGE_UP = 92
    private const val KEYCODE_PAGE_DOWN = 93
    private const val KEYCODE_CHANNEL_UP = 166
    private const val KEYCODE_CHANNEL_DOWN = 167

    /**
     * İlerleme çubuğu odaktayken Sol/Sağ/Tamam'ın **KeyUp**'ı kök yakalayıcıda tüketilir mi? KeyDown'ı [actionFor]
     * işler (10 sn sarma, oynat/duraklat); KeyUp Slider'a ulaşırsa onun "sürükleme bitti" mantığını çalıştırıp
     * konumu bozuyordu (video başa dönüyordu).
     */
    fun consumesBarKeyUp(key: Key, controlsVisible: Boolean, progressFocused: Boolean): Boolean =
        controlsVisible && progressFocused && key in BAR_KEYS

    private val BAR_KEYS = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter)
}

/**
 * İlerleme çubuğunda sürükleme bitince sarılacak konum (ms). Yalnızca gerçekten sürüklendiyse sarılır: kumandanın
 * KeyUp'ı ya da sürüklemesiz bir dokunma "bitti" geri çağrısını tetikleyebilir; o zaman sürükleme kesri 0'dır ve
 * sarmak videoyu başa döndürürdü. null: sarma.
 */
object ProgressSeek {
    fun finishTargetMs(dragging: Boolean, dragFraction: Float, durationMs: Long): Long? =
        if (dragging && durationMs > 0) (dragFraction.coerceIn(0f, 1f) * durationMs).toLong() else null
}

/**
 * Sol/Sağ tuşu basılı tutulunca sarma hızlanır. İlk basış 10 sn; tuş tekrarları (repeatCount 1, 2, …) önce kısa bir
 * bekleme (yanlışlıkla uzun basışı yutar), sonra 10 sn, ardından 30 sn, sonunda 60 sn'lik adımlarla ilerler.
 * Her tekrar olayı sarmaz: yaklaşık her 4. tekrar bir adım atar (çok sık sarıp oynatıcıyı boğmasın).
 */
object SeekAcceleration {
    const val BASE_STEP_MS = 10_000L

    /** [repeatCount]: Android `KeyEvent.repeatCount` (ilk basışta 0). 0 → sarılacak ms; atlanacaksa 0. */
    fun stepMs(repeatCount: Int): Long = when {
        repeatCount <= 0 -> BASE_STEP_MS
        repeatCount < 3 -> 0L // uzun basışın ilk gecikmesi
        repeatCount % 4 != 3 -> 0L
        repeatCount < 20 -> BASE_STEP_MS
        repeatCount < 48 -> 30_000L
        else -> 60_000L
    }
}
