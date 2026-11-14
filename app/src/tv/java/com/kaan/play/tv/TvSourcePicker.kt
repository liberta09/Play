package com.kaan.play.tv

import android.os.SystemClock
import android.util.Log
import com.kaan.play.AppContainer
import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.UnknownServiceException
import java.util.concurrent.TimeUnit

/** Kaynak seçiminin sonucu: oynatılacak kaynak ve sıralı yedekler. */
data class Picked(val best: PlayableStream, val fallbacks: List<PlayableStream>, val tookMs: Long, val found: Int, val alive: Int)

/** Hazırlanırken ekranda gösterilen sayılar. */
data class PickProgress(val found: Int, val alive: Int, val dead: Int)

/** Elle seçim listesinin bir satırı ("Kaynağı otomatik seç" kapalı): kaynak ve yoklama sonucu (null: sınanıyor). */
data class SourceChoice(val stream: PlayableStream, val probe: TvSourcePicker.Probe?)

/**
 * Hızlı başlangıç ("kanal geçişinde tek istek" fikri). Play'in oynatıcısı kaynakları sırayla
 * dener ve ölü bir sunucuda zaman aşımını bekler (ölçüm: bir filmde ilk iki kaynak zaman aşımı → ilk görüntü ~45 sn).
 * Burada kaynaklar geldikçe her biri kısa bir istekle (en çok 4 sn) aynı anda yoklanır; ölüler elenir, oynatıcıya ilk
 * çalışan kaynak ve sıralı yedekleri verilir. Oynatma, başlıklar, ara katman ve altyazı yine Play'in oynatıcısındadır.
 *
 * Seçim: dublaj tercihi açıksa (Play ayarı, varsayılan açık) ilk çalışan Türkçe sesli (dublaj ya da ses dili TR) kaynak; ilk çalışan kaynaktan sonra
 * [DUB_WAIT_MS] (3,5 sn) içinde dublajlı gelmezse en iyi çalışan. Sıra: dublaj, 1080'e yakın çözünürlük, yoklama hızı.
 */
class TvSourcePicker(private val container: AppContainer) {

    enum class Probe { Alive, Dead, Unknown }
    private data class Tested(val stream: PlayableStream, val probe: Probe, val ms: Long)

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PROBE_TIMEOUT_MS + 1500, TimeUnit.MILLISECONDS)
        .build()

    suspend fun pick(request: StreamRequest, onProgress: (PickProgress) -> Unit): Picked? = coroutineScope {
        val started = SystemClock.elapsedRealtime()
        val preferDub = runCatching { container.settings.preferDub.first() }.getOrDefault(true)
        val tested = mutableListOf<Tested>()
        var found = 0
        var pending = 0
        val gate = Semaphore(PARALLEL_PROBES)
        fun report() = synchronized(tested) {
            onProgress(PickProgress(found, tested.count { it.probe == Probe.Alive }, tested.count { it.probe == Probe.Dead }))
        }

        val collecting = launch {
            container.streams.streams(request).collect { r ->
                if (r !is StreamResult.Found) return@collect
                // Torrent (magnet) kaynaklar yoklanamaz; Play'in oynatıcısı onları kendi motoruyla yedek olarak bulur.
                r.streams.filterNot { it.url.startsWith("magnet:") }.forEach { s ->
                    synchronized(tested) { found++; pending++ }
                    Log.d(TAG, "  +${SystemClock.elapsedRealtime() - started}ms found ${s.source.addonName} dub=${s.dubbed} res=${s.resolution}")
                    launch {
                        val t = gate.withPermit { probe(s) }
                        synchronized(tested) { tested += t; pending-- }
                        report()
                    }
                }
                report()
            }
        }

        var firstAliveAt = 0L
        var result: Picked? = null
        while (true) {
            val snapshot = synchronized(tested) { tested.toList() }
            val alive = snapshot.filter { it.probe == Probe.Alive }
            val now = SystemClock.elapsedRealtime()
            if (alive.isNotEmpty() && firstAliveAt == 0L) firstAliveAt = now
            val allDone = collecting.isCompleted && synchronized(tested) { pending == 0 }
            val ordered = order(alive, preferDub)
            val best = ordered.firstOrNull()
            val ready = best != null && (!preferDub || best.stream.turkishAudio() || now - firstAliveAt >= DUB_WAIT_MS || allDone)
            if (ready) {
                result = finish(best!!, ordered, snapshot, preferDub, started, found)
                break
            }
            if (allDone) {
                // Hiç çalışan yok: yoklanamayan (ör. yerel aktarım) varsa onlar denenir; o da yoksa Play'in yolu.
                val unknown = snapshot.filter { it.probe == Probe.Unknown }
                if (unknown.isNotEmpty()) {
                    val o = order(unknown, preferDub)
                    result = finish(o.first(), o, snapshot, preferDub, started, found)
                }
                break
            }
            delay(150)
        }
        collecting.cancel()
        coroutineContext.cancelChildren()
        result
    }

    /**
     * "Kaynağı otomatik seç" kapalıyken (Play ayarı) kullanıcıya gösterilen liste: kaynaklar geldikçe eklenir, her biri [pick]'teki
     * kısa yoklamayla sınanır; her değişimde [onUpdate] sıralı listeyle çağrılır. Sıra yoklamaya göre değişmez (odak zıplamasın):
     * dublaj tercihi açıksa Türkçe sesliler, sonra 1080'e yakın çözünürlük. Torrent (magnet) yoklanmaz, listede "bilinmiyor" görünür.
     * Tüm kaynaklar ve yoklamalar bitince döner.
     */
    suspend fun list(request: StreamRequest, onUpdate: (List<SourceChoice>) -> Unit) = coroutineScope {
        val preferDub = runCatching { container.settings.preferDub.first() }.getOrDefault(true)
        val rows = mutableListOf<SourceChoice>()
        val gate = Semaphore(PARALLEL_PROBES)
        fun publish() {
            val snapshot = synchronized(rows) { rows.toList() }
            onUpdate(
                snapshot.sortedWith(
                    compareByDescending<SourceChoice> { preferDub && it.stream.turkishAudio() }
                        .thenBy { kotlin.math.abs((it.stream.resolution ?: 720) - 1080) },
                ),
            )
        }
        container.streams.streams(request).collect { r ->
            if (r !is StreamResult.Found) return@collect
            r.streams.forEach { s ->
                // Aynı adres birden çok eklentiden gelebilir: listede bir kez.
                val added = synchronized(rows) {
                    if (rows.any { it.stream.url == s.url }) false
                    else { rows += SourceChoice(s, if (s.url.startsWith("magnet:")) Probe.Unknown else null); true }
                }
                if (added && !s.url.startsWith("magnet:")) launch {
                    val t = gate.withPermit { probe(s) }
                    synchronized(rows) {
                        val i = rows.indexOfFirst { it.stream === s }
                        if (i >= 0) rows[i] = SourceChoice(s, t.probe)
                    }
                    publish()
                }
            }
            publish()
        }
    }

    private fun finish(best: Tested, ordered: List<Tested>, all: List<Tested>, preferDub: Boolean, started: Long, found: Int): Picked {
        val unknown = order(all.filter { it.probe == Probe.Unknown }, preferDub)
        val fallbacks = (ordered + unknown).map { it.stream }.filter { it !== best.stream }
        val took = SystemClock.elapsedRealtime() - started
        Log.i(TAG, "pick ${took}ms found=$found alive=${all.count { it.probe == Probe.Alive }} dubAlive=${all.count { it.probe == Probe.Alive && it.stream.dubbed }} dead=${all.count { it.probe == Probe.Dead }} unknown=${all.count { it.probe == Probe.Unknown }} " +
            "best=${best.stream.source.addonName} ${best.stream.resolution ?: "?"}p dub=${best.stream.dubbed} probe=${best.ms}ms")
        all.forEach { t -> Log.d(TAG, "  ${t.probe} ${t.ms}ms ${t.stream.source.addonName} res=${t.stream.resolution} dub=${t.stream.dubbed} lang=${t.stream.language}") }
        return Picked(best.stream, fallbacks, took, found, all.count { it.probe == Probe.Alive })
    }

    private fun order(list: List<Tested>, preferDub: Boolean): List<Tested> = list.sortedWith(
        compareByDescending<Tested> { if (preferDub) it.stream.turkishAudio() else false }
            .thenBy { kotlin.math.abs((it.stream.resolution ?: 720) - 1080) }
            .thenBy { it.ms },
    )

    /**
     * Kısa yoklama: ilk 2 KB istenir. 2xx ve HTML olmayan yanıt → çalışıyor (HLS'de `#EXTM3U` de beklenir).
     * Bağlantı/zaman aşımı/4xx-5xx/HTML hata sayfası → ölü. Düz HTTP'ye izin olmayan yerel adresler ya da şeması
     * desteklenmeyenler → bilinmiyor (oynatıcı dener).
     */
    private suspend fun probe(s: PlayableStream): Tested = withContext(Dispatchers.IO) {
        val t0 = SystemClock.elapsedRealtime()
        val verdict = try {
            if (!s.url.startsWith("http")) Probe.Unknown else {
                val client = s.httpInterceptor?.let { baseClient.newBuilder().addInterceptor(it).build() } ?: baseClient
                val req = Request.Builder().url(s.url).apply {
                    s.headers.forEach { (k, v) -> runCatching { header(k, v) } }
                    header("Range", "bytes=0-2047")
                }.build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) Probe.Dead else {
                        val type = resp.header("Content-Type").orEmpty().lowercase()
                        val head = resp.body?.byteStream()?.readNBytesCompat(512)?.toString(Charsets.UTF_8).orEmpty()
                        val hls = s.mimeType?.contains("mpegurl", true) == true || s.url.contains(".m3u8", true) || type.contains("mpegurl")
                        when {
                            type.contains("text/html") || head.trimStart().startsWith("<", ignoreCase = true) -> Probe.Dead
                            hls && !head.trimStart().startsWith("#EXTM3U") -> Probe.Dead
                            else -> Probe.Alive
                        }
                    }
                }
            }
        } catch (e: UnknownServiceException) {
            Probe.Unknown // düz HTTP (ör. 127.0.0.1 yerel aktarım): ağ politikası yoklamaya izin vermez, oynatıcı dener
        } catch (e: IllegalArgumentException) {
            Probe.Unknown
        } catch (e: Exception) {
            Probe.Dead
        }
        Tested(s, verdict, SystemClock.elapsedRealtime() - t0)
    }

    /**
     * Türkçe ses: dublaj işareti ya da kaynağın bildirdiği ses dili TR. Ölçüm: jasoelys kaynaklarında ses dili `TR`
     * yazılı ama dublaj işareti yok; yalnız işarete bakınca İngilizce ses seçiliyordu.
     */
    private fun PlayableStream.turkishAudio() = dubbed || language.equals("TR", ignoreCase = true)

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = read(buf, read, n - read)
            if (r < 0) break
            read += r
        }
        return buf.copyOf(read)
    }

    companion object {
        /**
         * Elle seçilen kaynak açılmazsa denenecek yedekler: listedeki diğerleri, çalışanlar önce, sonra bilinmeyen ve henüz
         * sınanmayanlar. Ölü bulunanlar eklenmez (oynatıcı her birinde zaman aşımı beklerdi).
         */
        fun fallbacks(chosen: PlayableStream, rows: List<SourceChoice>): List<PlayableStream> = rows
            .filter { it.stream !== chosen && it.probe != Probe.Dead }
            .sortedBy { when (it.probe) { Probe.Alive -> 0; Probe.Unknown -> 1; else -> 2 } }
            .map { it.stream }

        private const val TAG = "PlayTvPick"
        private const val PROBE_TIMEOUT_MS = 4_000L
        private const val PARALLEL_PROBES = 4
        /** Play'in StartPolicy.DUB_WAIT_MS ile aynı: dublajlı kaynak için ilk çalışan kaynaktan sonra bu kadar beklenir. */
        private const val DUB_WAIT_MS = 3_500L
    }
}
