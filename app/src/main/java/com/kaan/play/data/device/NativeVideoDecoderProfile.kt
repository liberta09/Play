package com.kaan.play.data.device

import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** Observed Android 16 x86 emulator AVC incompatibility. Other devices/codecs keep platform order. */
@androidx.annotation.OptIn(UnstableApi::class)
object NativeVideoDecoderProfile {
 fun applyTo(factory: DefaultRenderersFactory): DefaultRenderersFactory {
  if (Build.HARDWARE != "ranchu" || Build.VERSION.SDK_INT != 36 ||
      !Build.SUPPORTED_ABIS.contains("x86_64")) return factory
  factory.setMediaCodecSelector { mime, secure, tunneling ->
   val available = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
   if (mime != "video/avc" || secure || tunneling ||
       available.firstOrNull()?.name != "c2.goldfish.h264.decoder") available
   else {
    val software = available.filter { it.name == "c2.android.avc.decoder" }
    if (software.isEmpty()) available
    else {
     Log.i("PlayCodec", "EMULATOR_AVC_SOFTWARE_PREFERRED")
     software + available.filter { it.name != "c2.android.avc.decoder" }
    }
   }
  }
  return factory
 }
}
