package com.kaan.play.tv

import com.kaan.play.R
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Detaydaki "Fragman" (Play'in fragman oynatıcısının TV karşılığı). Play telefonda YouTube'u WebView'de açar; Amlogic
 * kutularda gömülü YouTube görüntüsü arayüzün altında kaldığı için TV, ana sayfa önizlemesindeki yolu kullanır: fragman adresi
 * [TvYouTube] ile çözülür, ExoPlayer'da (TextureView) oynar. OK/oynat-duraklat tuşları oynatıcı çubuğunu açar; Geri ya da
 * fragmanın sonu detaya döner.
 */
@OptIn(UnstableApi::class)
class TvTrailerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra(EXTRA_KEY) ?: return finish()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            text = getString(R.string.tv_trailer_loading, intent.getStringExtra(EXTRA_TITLE).orEmpty())
        }
        val view = LayoutInflater.from(this).inflate(R.layout.tv_trailer_player, root, false) as PlayerView
        root.addView(view)
        root.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        lifecycleScope.launch {
            val streams = withContext(Dispatchers.IO) {
                withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                    runCatching { runInterruptible { TvYouTube.resolve(key) } }
                        .onFailure { Log.w(TAG, "fragman çözülemedi: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
                }
            }
            if (streams == null) return@launch fail()
            val factory = ProgressiveMediaSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
            val video = factory.createMediaSource(MediaItem.fromUri(streams.video))
            val source = streams.audio?.let { MergingMediaSource(video, factory.createMediaSource(MediaItem.fromUri(it))) } ?: video
            player = ExoPlayer.Builder(this@TvTrailerActivity).build().apply {
                setMediaSource(source)
                addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { status.visibility = android.view.View.GONE }
                    override fun onPlayerError(error: PlaybackException) { Log.w(TAG, "fragman oynatılamadı: ${error.errorCodeName}"); fail() }
                    override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) finish() }
                })
                prepare()
                playWhenReady = true
            }
            view.player = player
            view.requestFocus()
            Log.i(TAG, "fragman oynatılıyor: $key")
        }
    }

    private fun fail() {
        Toast.makeText(this, R.string.tv_trailer_failed, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onStop() {
        super.onStop()
        // Arka plana geçince (Ana ekran tuşu) fragman kapanır; detaya dönünce yeniden açılır.
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlayTvTrailer"
        private const val EXTRA_KEY = "com.kaan.play.tv.TRAILER_KEY"
        private const val EXTRA_TITLE = "com.kaan.play.tv.TRAILER_TITLE"
        private const val RESOLVE_TIMEOUT_MS = 25_000L

        fun intent(context: Context, youtubeKey: String, title: String): Intent =
            Intent(context, TvTrailerActivity::class.java).putExtra(EXTRA_KEY, youtubeKey).putExtra(EXTRA_TITLE, title)
    }
}
