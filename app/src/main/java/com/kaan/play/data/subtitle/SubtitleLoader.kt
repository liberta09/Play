package com.kaan.play.data.subtitle

import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface SubtitleLoad {
    /** [charset]: dosyanın bulunan özgün kodlaması (önbellekten gelirse "UTF-8"). */
    data class Loaded(val cues: List<SubtitleCue>, val charset: String, val fromCache: Boolean) : SubtitleLoad

    data class Failed(val reason: Reason) : SubtitleLoad

    enum class Reason { Timeout, Network, Http, TooLarge, Invalid }
}

/** [SubtitleLoader.loadText] sonucu. */
sealed interface SubtitleTextResult {
    data class Text(val utf8: String) : SubtitleTextResult

    data class Failed(val reason: SubtitleLoad.Reason) : SubtitleTextResult
}

/** Bir altyazı parçasını satırlara çeviren şey ([SubtitleLoader]); oynatıcı denetleyicisinin testlerinde sahtesi kullanılır. */
fun interface TrackLoader {
    suspend fun load(track: SubtitleTrack): SubtitleLoad
}

/**
 * Altyazıyı kendimiz indirir: boyut ve süre sınırı, kodlamayı bulma ([SubtitleCharset]), UTF-8'e çevirip önbelleğe yazma,
 * SRT/VTT/ASS ayrıştırma. Oynatıcı satırları kendisi zamanlar (kaydırma anında uygulansın diye, bkz. [SubtitleParser]).
 *
 * Önbellek: adresin SHA-256'sı adlı UTF-8 metin dosyaları ([cacheDir], uygulamanın `cacheDir`'i; sistem boşaltabilir).
 * Aynı altyazı tekrar açılınca ağa gidilmez. Yalnızca en az bir satır çıkaran dosya önbelleğe girer. Dizin [MAX_FILES]
 * dosya / [MAX_TOTAL_BYTES] bayt ve [MAX_AGE_MS] yaş sınırıyla budanır.
 *
 * Altyazı adresine akışın `proxyHeaders`'ı gönderilmez (üçüncü taraf sunucu).
 */
class SubtitleLoader(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val timeout: Duration = 15.seconds,
    private val maxBytes: Long = MAX_BYTES,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * Kullanıcının seçtiği yerel dosya (`content://`, `file://`): en çok [maxBytes]+1 bayt okur; açılamazsa null.
     * Yerel dosya önbelleğe yazılmaz (her açılışta kaynağından okunur).
     */
    private val openLocal: ((uri: String, limit: Long) -> ByteArray?)? = null,
): TrackLoader {

    override suspend fun load(track: SubtitleTrack): SubtitleLoad =
        when (val fetched = fetch(track)) {
            is Fetched.Ok -> SubtitleLoad.Loaded(fetched.cues, fetched.charset, fetched.fromCache)
            is Fetched.Err -> SubtitleLoad.Failed(fetched.reason)
        }

    /**
     * İndirmeler (Faz 4/B) için: altyazının UTF-8'e çevrilmiş **metni** (önbellekten ya da indirilerek). Çevrimdışı oynatmada
     * bu metin [load] ile aynı ayrıştırıcıdan geçer. Çözülemeyen/boş içerik [SubtitleTextResult.Failed] olarak döner.
     */
    suspend fun loadText(track: SubtitleTrack): SubtitleTextResult =
        when (val fetched = fetch(track)) {
            is Fetched.Ok -> SubtitleTextResult.Text(fetched.text)
            is Fetched.Err -> SubtitleTextResult.Failed(fetched.reason)
        }

    private sealed interface Fetched {
        class Ok(val text: String, val cues: List<SubtitleCue>, val charset: String, val fromCache: Boolean) : Fetched

        class Err(val reason: SubtitleLoad.Reason) : Fetched
    }

    private suspend fun fetch(track: SubtitleTrack): Fetched = withContext(Dispatchers.IO) {
        if (isLocal(track.url)) return@withContext fetchLocal(track)
        val file = File(cacheDir, cacheName(track.url))
        readCached(file)?.let { (text, cues) -> return@withContext Fetched.Ok(text, cues, "UTF-8", fromCache = true) }

        val url = track.url.toHttpUrlOrNull()
            ?: return@withContext Fetched.Err(SubtitleLoad.Reason.Network)
        val downloaded = try {
            download(url, track.headers)
        } catch (e: LoadFailure) {
            return@withContext Fetched.Err(e.reason)
        }
        // Altyazı siteleri (SubDL …) zip verir: içindeki .srt/.vtt/.ass çıkarılır, kodlama çıkarılan dosyadan bulunur.
        val bytes = if (SubtitleZip.isZip(downloaded)) {
            SubtitleZip.extract(downloaded, maxBytes, season = track.season, episode = track.episode) ?: return@withContext Fetched.Err(SubtitleLoad.Reason.Invalid)
        } else {
            downloaded
        }

        val decoded = SubtitleCharset.decode(bytes, track.language)
        val cues = SubtitleParser.parse(decoded.text)
        if (cues.isEmpty()) return@withContext Fetched.Err(SubtitleLoad.Reason.Invalid)

        writeCache(file, decoded.text)
        Fetched.Ok(decoded.text, cues, decoded.charset.name(), fromCache = false)
    }

    private fun fetchLocal(track: SubtitleTrack): Fetched {
        val open = openLocal ?: return Fetched.Err(SubtitleLoad.Reason.Network)
        val bytes = try {
            open(track.url, maxBytes + 1)
        } catch (_: Exception) { // IOException, SecurityException (izin geri alındı)
            null
        } ?: return Fetched.Err(SubtitleLoad.Reason.Network)
        if (bytes.size > maxBytes) return Fetched.Err(SubtitleLoad.Reason.TooLarge)
        val decoded = SubtitleCharset.decode(bytes, track.language)
        val cues = SubtitleParser.parse(decoded.text)
        if (cues.isEmpty()) return Fetched.Err(SubtitleLoad.Reason.Invalid)
        return Fetched.Ok(decoded.text, cues, decoded.charset.name(), fromCache = false)
    }

    private fun readCached(file: File): Pair<String, List<SubtitleCue>>? {
        if (!file.isFile) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            SubtitleParser.parse(text).takeIf { it.isNotEmpty() }
                ?.also { file.setLastModified(clock()) }
                ?.let { text to it }
        } catch (_: IOException) {
            null
        }
    }

    private fun writeCache(file: File, utf8Text: String) {
        try {
            cacheDir.mkdirs()
            val temp = File(cacheDir, file.name + ".tmp")
            temp.writeText(utf8Text, Charsets.UTF_8)
            if (!temp.renameTo(file)) temp.delete()
            prune()
        } catch (_: IOException) {
            // Önbellek yazılamıyorsa altyazı yine çalışır; yalnızca bir sonraki açılışta yeniden indirilir.
        }
    }

    private fun prune() {
        val files = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEachIndexed { index, f ->
            total += f.length()
            val tooOld = clock() - f.lastModified() > MAX_AGE_MS
            if (index >= MAX_FILES || total > MAX_TOTAL_BYTES || tooOld) f.delete()
        }
    }

    private suspend fun download(url: okhttp3.HttpUrl, headers: Map<String, String> = emptyMap()): ByteArray {
        if (url.scheme != "http" && url.scheme != "https") throw LoadFailure(SubtitleLoad.Reason.Network)
        val http = client.newBuilder().callTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS).build()
        val requestBuilder = Request.Builder().url(url)
        if (!headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
        headers.forEach { (k, v) ->
            requestBuilder.header(k, v)
        }
        val request = requestBuilder.get().build()
        return try {
            http.newCall(request).await().use { response ->
                if (!response.isSuccessful) throw LoadFailure(SubtitleLoad.Reason.Http)
                val body = response.body
                if (body.contentLength() > maxBytes) throw LoadFailure(SubtitleLoad.Reason.TooLarge)
                val source = body.source()
                source.request(maxBytes + 1)
                if (source.buffer.size > maxBytes) throw LoadFailure(SubtitleLoad.Reason.TooLarge)
                source.readByteArray()
            }
        } catch (_: InterruptedIOException) { // SocketTimeoutException da bunun alt türü
            throw LoadFailure(SubtitleLoad.Reason.Timeout)
        } catch (_: IOException) {
            throw LoadFailure(SubtitleLoad.Reason.Network)
        }
    }

    private class LoadFailure(val reason: SubtitleLoad.Reason) : Exception()

    companion object {
        /** Bir altyazı dosyası genelde 50–200 KB'tır; 2 MB cömert bir üst sınırdır. */
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_FILES = 200
        const val MAX_TOTAL_BYTES = 40L * 1024 * 1024
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

        /** Kullanıcının seçtiği yerel dosya adresi mi (`content://`, `file://`)? */
        fun isLocal(url: String): Boolean = url.startsWith("content://", true) || url.startsWith("file://", true)

        internal fun cacheName(url: String): String =
            MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".txt"
    }
}
