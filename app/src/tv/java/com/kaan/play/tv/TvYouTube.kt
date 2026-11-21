package com.kaan.play.tv

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit

/**
 * YouTube fragmanının oynatılabilir adresleri (NewPipe Extractor; CloudStream kütüphanesiyle zaten uygulamada). Gömülü YouTube oynatıcısı (WebView) Amlogic kutularda
 * görüntüyü ayrı donanım katmanına verip arayüzün altında bırakıyor (ses var, görüntü yok); bu yüzden adres çözülüp önizleme
 * ExoPlayer'da oynatılır. Görüntü ve ses YouTube'da ayrı akışlardır: en çok 1080p H.264 (MP4) görüntü + M4A ses.
 */
internal object TvYouTube {
    class Streams(val video: String, val audio: String?)

    @Volatile private var initialized = false

    fun resolve(key: String): Streams? {
        init()
        val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$key")
        val video = info.videoOnlyStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 && it.codec?.startsWith("avc1") == true }
            .filter { it.height in 1..1080 }
            .maxByOrNull { it.height }
        if (video != null) {
            val audio = info.audioStreams
                .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A }
                .maxByOrNull { it.averageBitrate }
            return Streams(video.content, audio?.content)
        }
        // Ayrı akış yoksa görüntü+sesli tek dosya (genelde 360p).
        val muxed = info.videoStreams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }.maxByOrNull { it.height }
        return muxed?.let { Streams(it.content, null) }
    }

    @Synchronized private fun init() {
        if (initialized) return
        // CloudStream (NewPipe'ı o da kullanır) önce kurduysa onun ayarı korunur.
        if (NewPipe.getDownloader() == null) NewPipe.init(OkDownloader, Localization("tr", "TR"), ContentCountry("TR"))
        initialized = true
    }

    private object OkDownloader : Downloader() {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
        // callTimeout: önizleme iptal edilse de takılan istek en çok bu kadar sürer.
        private val client = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS).build()

        override fun execute(request: Request): Response {
            val body = request.dataToSend()?.toRequestBody()
                ?: if (request.httpMethod() == "POST") ByteArray(0).toRequestBody() else null
            val builder = okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(), body).header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { r ->
                if (r.code == 429) throw ReCaptchaException("reCaptcha", request.url())
                return Response(r.code, r.message, r.headers.toMultimap(), r.body?.string(), r.request.url.toString())
            }
        }
    }
}
