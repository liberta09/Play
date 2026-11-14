package com.kaan.play.tv

import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.kaan.play.data.sync.local.RemoteInput
import com.kaan.play.data.sync.local.RemoteKey
import com.kaan.play.data.sync.local.RemoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Eşleşmiş telefondan gelen kumanda ve klavye (deney D). Tuşlar uygulamanın o an önde olan penceresine gerçek
 * kumanda tuşu olarak basılır (yalnız kendi penceremize basılabilir; başka uygulama öndeyse sistem reddeder). Yazı odaktaki
 * yazı alanına ([register] ile kaydolan son hedef) gider; hedef yoksa Ara ekranı açılır ve yazı oraya yazılır.
 */
internal object TvRemote {
    private const val TAG = "PlayTv"
    private const val PENDING_MS = 5_000L

    private val targets = ArrayList<(String, Boolean) -> Unit>()
    private var pending: RemoteInput? = null
    private var pendingAt = 0L

    /** Oynatıcı önde iken oynayan yapım (oynatıcı 500 ms'de bir yazar, durunca null); telefon kumandası sorar. */
    data class NowPlaying(
        val title: String,
        val subtitle: String?,
        val posterUrl: String?,
        val playing: Boolean,
        val positionMs: Long,
        val durationMs: Long,
    )

    @Volatile
    var nowPlaying: NowPlaying? = null

    /** Kumandanın durum sorusuna yanıt (sunucu iş parçacığında): oynayan yapım + müzik akışının ses düzeyi. */
    fun state(app: Application): RemoteState {
        val audio = app.getSystemService(AudioManager::class.java)
        val now = nowPlaying
        return RemoteState(
            v = 1,
            title = now?.title,
            subtitle = now?.subtitle,
            posterUrl = now?.posterUrl,
            playing = now?.playing == true,
            positionMs = now?.positionMs ?: 0,
            durationMs = now?.durationMs ?: 0,
            volume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1,
            maxVolume = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1,
            muted = audio?.isStreamMute(AudioManager.STREAM_MUSIC) == true,
        )
    }

    /** Telefondan istenen bölüm (Ana sayfa / Ara); TvApp açar ve null yapar. */
    val openSection = MutableStateFlow<Section?>(null)

    /** Ana iş parçacığında: yazı hedefi olur (son kaydolan alır); dönen işlev kaydı siler. */
    fun register(target: (text: String, submit: Boolean) -> Unit): () -> Unit {
        targets += target
        val waiting = pending?.takeIf { SystemClock.elapsedRealtime() - pendingAt < PENDING_MS }
        pending = null
        if (waiting != null) target(waiting.text.orEmpty(), waiting.submit)
        return { targets -= target }
    }

    fun start(app: Application, inputs: Flow<RemoteInput>, scope: CoroutineScope) {
        scope.launch { inputs.collect { handle(app, it) } }
    }

    private suspend fun handle(app: Application, input: RemoteInput) {
        val key = input.key
        Log.i(TAG, "telefon kumandası: " + (key?.name ?: "yazı ${input.text?.length ?: 0} harf${if (input.submit) " + ara" else ""}"))
        when {
            key == RemoteKey.Home -> withContext(Dispatchers.Main) { open(app, Section.Home) }
            // Ses: sistem müzik akışı (izin gerekmez; başka uygulama öndeyken de çalışır), TV'de ses çubuğu görünür.
            key == RemoteKey.VolumeUp || key == RemoteKey.VolumeDown || key == RemoteKey.Mute -> withContext(Dispatchers.Main) {
                val direction = when (key) {
                    RemoteKey.VolumeUp -> AudioManager.ADJUST_RAISE
                    RemoteKey.VolumeDown -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_TOGGLE_MUTE
                }
                app.getSystemService(AudioManager::class.java)
                    ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            }
            key != null -> withContext(Dispatchers.IO) {
                try {
                    Instrumentation().sendKeyDownUpSync(keyCode(key))
                } catch (e: SecurityException) {
                    Log.i(TAG, "telefon kumandası: önde başka uygulama var, tuş basılmadı")
                }
            }
            input.text != null -> withContext(Dispatchers.Main) {
                val target = targets.lastOrNull()
                if (target != null) {
                    target(input.text.orEmpty(), input.submit)
                } else {
                    pending = input
                    pendingAt = SystemClock.elapsedRealtime()
                    open(app, Section.Search)
                }
            }
        }
    }

    /** Ana ekran öne gelir (singleTask: üstündeki oynatıcı kapanır) ve [section] açılır. */
    private fun open(app: Application, section: Section) {
        openSection.value = section
        app.startActivity(Intent(app, TvMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun keyCode(key: RemoteKey): Int = when (key) {
        RemoteKey.Up -> KeyEvent.KEYCODE_DPAD_UP
        RemoteKey.Down -> KeyEvent.KEYCODE_DPAD_DOWN
        RemoteKey.Left -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteKey.Right -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteKey.Ok -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteKey.Back -> KeyEvent.KEYCODE_BACK
        RemoteKey.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        RemoteKey.Home -> KeyEvent.KEYCODE_HOME // yukarıda ayrıca işlenir
        RemoteKey.Menu -> KeyEvent.KEYCODE_MENU // oynatıcıda ses/altyazı paneli
        RemoteKey.ListUp -> KeyEvent.KEYCODE_CHANNEL_UP // canlı yayında kanal değiştirir
        RemoteKey.ListDown -> KeyEvent.KEYCODE_CHANNEL_DOWN
        RemoteKey.Rewind -> KeyEvent.KEYCODE_MEDIA_REWIND // oynatıcıda geri sarar
        RemoteKey.FastForward -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        RemoteKey.VolumeUp, RemoteKey.VolumeDown, RemoteKey.Mute -> KeyEvent.KEYCODE_UNKNOWN // yukarıda ayrıca işlenir
    }
}
