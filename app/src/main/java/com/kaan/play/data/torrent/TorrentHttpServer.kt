package com.kaan.play.data.torrent

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * Torrent dosyasını oynatıcıya düz HTTP olarak veren yerel sunucu. Yalnızca `127.0.0.1`'i dinler; adres her açılışta rastgele
 * bir belirteç içerir, böylece cihazdaki başka uygulamalar motoru kullanamaz. `Range` desteklenir (oynatıcı ileri sarınca yeni bir
 * aralık ister). Her bağlantı tek istektir (`Connection: close`).
 */
class TorrentHttpServer(private val engine: TorrentEngine) {

    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "torrent-http").apply { isDaemon = true } }
    private var server: ServerSocket? = null

    /** Torrent açılıp dosya boyutu belli olunca (magnet, bayt); oynatıcı üst çubukta ve kaynak panelinde gösterir. Havuz iş parçacığından çağrılır. */
    @Volatile var onOpened: ((magnet: String, size: Long) -> Unit)? = null

    /** Magnet'in yerel oynatma adresi. Sunucu gerekirse burada başlar (yalnızca soket açar; motor ilk istekte başlar). */
    fun urlFor(magnet: String, fileIdx: Int?): String {
        val port = ensureStarted()
        val index = fileIdx?.let { "&index=$it" }.orEmpty()
        return "http://127.0.0.1:$port/$token/stream?link=${URLEncoder.encode(magnet, "UTF-8")}$index"
    }

    @Synchronized
    private fun ensureStarted(): Int {
        server?.let { return it.localPort }
        val socket = ServerSocket(0, BACKLOG, InetAddress.getByName("127.0.0.1"))
        server = socket
        com.kaan.play.data.cloudstream.PlayNetworkGuard.allowLoopbackPort(socket.localPort)
        Thread({
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: IOException) { break }
                pool.execute { client.use(::serve) }
            }
        }, "torrent-http-accept").apply { isDaemon = true }.start()
        return socket.localPort
    }

    private fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(client.getInputStream())
        val out = BufferedOutputStream(client.getOutputStream(), BUFFER)
        try {
            val requestLine = readLine(input) ?: return
            val headers = generateSequence { readLine(input)?.takeIf { it.isNotEmpty() } }
                .mapNotNull { line -> line.indexOf(':').takeIf { it > 0 }?.let { line.substring(0, it).trim().lowercase() to line.substring(it + 1).trim() } }
                .toMap()
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val target = parts.getOrNull(1).orEmpty()
            TorrentDiag.d("yerel sunucu isteği: $method range=${headers["range"]} hedefOk=${target.startsWith("/$token/stream?")}")
            if (method != "GET" && method != "HEAD") return status(out, 405, "Method Not Allowed")
            val prefix = "/$token/stream?"
            if (!target.startsWith(prefix)) return status(out, 404, "Not Found")
            val query = target.removePrefix(prefix).split('&').associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            val magnet = query["link"] ?: return status(out, 400, "Bad Request")

            val file = try {
                engine.open(magnet, query["index"]?.toIntOrNull())
            } catch (e: TorrentException) {
                Log.w(TAG, "open failed: ${e.reason}")
                TorrentDiag.e("motor açamadı: ${e.reason}", e)
                return when (e.reason) {
                    TorrentException.Reason.NoMetadata, TorrentException.Reason.Engine -> status(out, 503, "Service Unavailable")
                    TorrentException.Reason.NoSpace -> status(out, 507, "Insufficient Storage")
                    else -> status(out, 404, "Not Found")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "engine.open beklenmeyen hata: ${t.message}", t)
                TorrentDiag.e("motor genel hata", t)
                return status(out, 503, "Service Unavailable")
            }

            onOpened?.invoke(magnet, file.size)

            val (start, end, code) = when (val range = TorrentRules.parseRange(headers["range"], file.size)) {
                TorrentRules.Range.Full -> Triple(0L, file.size - 1, 200)
                is TorrentRules.Range.Part -> Triple(range.start, range.end, 206)
                TorrentRules.Range.Unsatisfiable -> {
                    out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */${file.size}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }
            }
            val head = buildString {
                append(if (code == 206) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                append("Content-Type: ${TorrentRules.contentType(file.name)}\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: ${end - start + 1}\r\n")
                if (code == 206) append("Content-Range: bytes $start-$end/${file.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            TorrentDiag.d("yerel sunucu yanıtı: $code ${file.name} $start-$end/${file.size}")
            out.write(head.toByteArray())
            if (method == "HEAD") return out.flush()

            engine.seek(file, start)
            copy(file, start, end, out)
        } catch (e: IOException) {
            TorrentDiag.d("yerel sunucu bağlantısı bitti: ${e.javaClass.simpleName} ${e.message}")
            // Oynatıcı bağlantıyı kapattı (ileri sarma, kapanış) ya da torrent kapandı: sessizce biter.
        } catch (e: RuntimeException) {
            // libtorrent kapatılmış torrentin tutamacında "invalid torrent handle" fırlatır (oynatıcı kapanırken okuma sürüyordu).
            Log.d(TAG, "connection ended: ${e.message}")
            TorrentDiag.e("yerel sunucu istisnası", e)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (t: Throwable) {
            Log.w(TAG, "yerel sunucu kritik hata: ${t.message}", t)
            TorrentDiag.e("yerel sunucu kritik hata", t)
        }
    }

    private fun copy(file: TorrentEngine.Opened, start: Long, end: Long, out: OutputStream) {
        // Dosya henüz yazılmamış olabilir: ilk parça gelene dek bekle (read bekler), sonra aç.
        val buffer = ByteArray(BUFFER)
        var position = start
        var raf: RandomAccessFile? = null
        try {
            while (position <= end) {
                val max = minOf(BUFFER.toLong(), end - position + 1).toInt()
                if (raf == null) {
                    engine.awaitPiece(file, position)
                    raf = RandomAccessFile(file.path, "r")
                }
                val n = engine.read(file, position, buffer, max, raf)
                if (n <= 0) break
                out.write(buffer, 0, n)
                position += n
            }
            out.flush()
        } finally {
            raf?.close()
        }
    }

    private fun status(out: OutputStream, code: Int, text: String) {
        out.write("HTTP/1.1 $code $text\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    /** ASCII satır (CRLF ya da LF); bağlantı kapanırsa null. Aşırı uzun satırlar kesilir. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length < MAX_LINE) sb.append(b.toChar())
        }
    }

    private companion object {
        const val TAG = "PlayTorrent"
        const val BACKLOG = 8
        const val BUFFER = 64 * 1024
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_LINE = 16 * 1024
    }
}
