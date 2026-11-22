package com.kaan.play.ui.player

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary

/**
 * Cihaz çözücüleri + FFmpeg ses çözücüsü (NextLib).
 *
 * Yalnızca ses eklenir: video ve altyazı Media3'ün varsayılan renderer'larıyla kalır.
 * [EXTENSION_RENDERER_MODE_ON]: önce cihazın MediaCodec ses çözücüsü denenir, çözemediği biçimde
 * (DTS, DTS-HD, TrueHD, E-AC-3, AC-3 …) FFmpeg devreye girer. Yerel kütüphane yüklenemezse
 * (ör. desteklenmeyen ABI) sessizce yalnızca cihaz çözücüleri kalır.
 */
class PlayRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        super.buildAudioRenderers(
            context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
            audioSink, eventHandler, eventListener, out,
        )
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF || !ffmpegAvailable()) return
        val ffmpeg = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) out.add(0, ffmpeg) else out.add(ffmpeg)
    }

    private fun ffmpegAvailable(): Boolean = runCatching { FfmpegLibrary.isAvailable() }.getOrDefault(false)
}
