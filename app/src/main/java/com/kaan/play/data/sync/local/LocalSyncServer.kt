package com.kaan.play.data.sync.local

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wi-Fi senkronizasyonu sunucusu (Android'den bağımsız; düz soketle en küçük HTTP/1.1). Her bağlantı tek istek
 * (`Connection: close`).
 *
 * - `GET /health`: şifresiz; uygulama kimliği ve bu oturum için yeni bir sunucu nonce'u döner.
 * - `GET /profiles`, `POST /sync`: istek [LocalSyncProtocol.HEADER_PROOF] kanıtı taşımalı (koddan + iki nonce'tan türeyen
 *   anahtarla HMAC); yanlışsa **401**. Gövdeler AES-GCM ile şifreli. [maxAttempts]. yanlış denemede sunucu kapanır.
 * - Yalnızca [addressAllowed] (özel IPv4) adreslerden gelen bağlantılar kabul edilir; gövde [maxBodyBytes] ile sınırlı,
 *   okuma zaman aşımı [readTimeoutMs].
 *
 * Sunucu yalnızca kart açıkken çalışır; kapatmak (ya da kilitlenmek) kodu geçersiz kılar.
 */
class LocalSyncServer(
    private val code: String,
    private val handler: Handler,
    private val port: Int = LocalSyncProtocol.PORT,
    private val addressAllowed: (InetAddress) -> Boolean = LocalNetwork::isPrivateLan,
    maxAttempts: Int = LocalSyncProtocol.MAX_ATTEMPTS,
    private val readTimeoutMs: Int = LocalSyncProtocol.READ_TIMEOUT_MS,
    private val maxBodyBytes: Int = LocalSyncProtocol.MAX_BODY_BYTES,
    private val random: SecureRandom = SecureRandom(),
    private val log: (String) -> Unit = {},
) {
    /** Uygulamanın verisi. Sunucu iş parçacıklarında (engelleyerek) çağrılır. */
    interface Handler {
        /** Profil özetleri (PIN'siz) JSON'u. */
        fun profiles(): String

        /** Karşı cihazın anlık görüntüsü gelir; bu cihazın (birleştirmeden önceki) anlık görüntüsü döner. */
        fun exchange(remoteJson: String): String
    }

    enum class CloseReason { Stopped, TooManyAttempts, Failed }

    /** Sunucu kapandığında (bir kez) çağrılır. */
    @Volatile
    var onClosed: ((CloseReason) -> Unit)? = null

    private val limiter = SyncAttemptLimiter(maxAttempts)
    private val running = AtomicBoolean(false)
    private var socket: ServerSocket? = null
    private var workers: ExecutorService? = null
    private val issuedNonces = ArrayDeque<String>()
    private val usedProofs = HashSet<String>()

    val isRunning: Boolean get() = running.get()

    /** Dinlemeye başlar; bağlanılan port döner (testte 0 verilirse rastgele port). */
    @Synchronized
    fun start(): Int {
        check(!running.get()) { "already running" }
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(port))
        socket = server
        workers = Executors.newFixedThreadPool(WORKERS) { r -> Thread(r, "PlaySyncWorker").apply { isDaemon = true } }
        running.set(true)
        Thread({ acceptLoop(server) }, "PlaySyncServer").apply { isDaemon = true }.start()
        return server.localPort
    }

    fun stop() = close(CloseReason.Stopped)

    private fun close(reason: CloseReason) {
        if (!running.compareAndSet(true, false)) return
        runCatching { socket?.close() }
        workers?.shutdownNow()
        log("sync server closed: $reason")
        onClosed?.invoke(reason)
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running.get()) {
            val client = try {
                server.accept()
            } catch (_: SocketException) {
                break // kapatıldı
            } catch (e: IOException) {
                log("sync server accept failed: ${e.javaClass.simpleName}")
                close(CloseReason.Failed)
                break
            }
            if (!addressAllowed(client.inetAddress)) {
                log("sync server: non-private address rejected")
                runCatching { client.close() }
                continue
            }
            try {
                workers?.execute { serve(client) } ?: client.close()
            } catch (_: Exception) {
                runCatching { client.close() }
            }
        }
    }

    private fun serve(client: Socket) {
        client.use { s ->
            try {
                s.soTimeout = readTimeoutMs
                val request = readRequest(s.getInputStream())
                if (request == null) {
                    respond(s.getOutputStream(), 400, """{"error":"bad_request"}""")
                    return
                }
                handle(request, s.getOutputStream())
            } catch (_: BodyTooLarge) {
                respond(s.getOutputStream(), 413, """{"error":"too_large"}""")
            } catch (e: IOException) {
                log("sync server request failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun handle(req: Request, out: OutputStream) {
        when {
            req.method == "GET" && req.path == LocalSyncProtocol.PATH_HEALTH -> {
                val nonce = issueNonce()
                respond(out, 200, """{"app":"play","version":1,"nonce":"$nonce"}""")
            }
            req.method == "GET" && req.path == LocalSyncProtocol.PATH_PROFILES -> authed(req, out) { keys ->
                LocalSyncCrypto.seal(keys, req.path, handler.profiles(), random)
            }
            req.method == "POST" && req.path == LocalSyncProtocol.PATH_SYNC -> authed(req, out) { keys ->
                val remote = try {
                    LocalSyncCrypto.open(keys, req.path, req.body.toString(Charsets.UTF_8))
                } catch (_: SyncAuthException) {
                    null
                } ?: return@authed null
                LocalSyncCrypto.seal(keys, req.path, handler.exchange(remote), random)
            }
            else -> respond(out, 404, """{"error":"not_found"}""")
        }
    }

    /** Kanıtı denetler; [body] null dönerse (şifre çözülemedi) o da yanlış deneme sayılır. */
    private fun authed(req: Request, out: OutputStream, body: (SyncKeys) -> String?) {
        val serverNonce = req.headers[LocalSyncProtocol.HEADER_SERVER_NONCE.lowercase()]
        val clientNonce = req.headers[LocalSyncProtocol.HEADER_CLIENT_NONCE.lowercase()]
        val proof = req.headers[LocalSyncProtocol.HEADER_PROOF.lowercase()]
        val keys = if (LocalSyncCrypto.isNonce(serverNonce) && LocalSyncCrypto.isNonce(clientNonce) && knownNonce(serverNonce!!)) {
            LocalSyncCrypto.deriveKeys(code, serverNonce, clientNonce!!)
        } else {
            null
        }
        val ok = keys != null &&
            LocalSyncCrypto.proofMatches(LocalSyncCrypto.proof(keys, req.method, req.path, serverNonce!!, clientNonce!!), proof) &&
            markUsed("$clientNonce|${req.path}")
        val payload = if (ok) {
            try {
                body(keys!!)
            } catch (e: Exception) {
                log("sync server handler failed: ${e.javaClass.simpleName}")
                respond(out, 500, """{"error":"internal"}""")
                return
            }
        } else {
            null
        }
        if (payload == null) {
            val locked = limiter.fail()
            log("sync server: wrong key (remaining=${limiter.remaining})")
            respond(out, 401, """{"error":"unauthorized"}""")
            if (locked) close(CloseReason.TooManyAttempts)
            return
        }
        respond(out, 200, payload)
    }

    @Synchronized
    private fun issueNonce(): String {
        val nonce = LocalSyncCrypto.newNonce(random)
        issuedNonces.addLast(nonce)
        while (issuedNonces.size > MAX_NONCES) issuedNonces.removeFirst()
        return nonce
    }

    @Synchronized
    private fun knownNonce(nonce: String): Boolean = nonce in issuedNonces

    /** Aynı istemci nonce'u aynı uca ikinci kez kullanılamaz (yeniden oynatma). */
    @Synchronized
    private fun markUsed(key: String): Boolean = usedProofs.add(key)

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)

    private class BodyTooLarge : IOException()

    private fun readRequest(input: InputStream): Request? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val parts = lines.first().split(' ')
        if (parts.size < 3) return null
        val headers = lines.drop(1).filter { ':' in it }.associate { line ->
            line.substringBefore(':').trim().lowercase() to line.substringAfter(':').trim()
        }
        val length = headers["content-length"]?.toLongOrNull() ?: 0L
        if (length < 0) return null
        if (length > maxBodyBytes) throw BodyTooLarge()
        val body = ByteArray(length.toInt())
        var read = 0
        while (read < body.size) {
            val n = input.read(body, read, body.size - read)
            if (n < 0) return null
            read += n
        }
        return Request(parts[0].uppercase(), parts[1].substringBefore('?'), headers, body)
    }

    /** Başlıklar (`\r\n\r\n`'e kadar), en çok [MAX_HEAD_BYTES]. */
    private fun readHead(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        var matched = 0
        while (buf.size() < MAX_HEAD_BYTES) {
            val b = input.read()
            if (b < 0) return null
            buf.write(b)
            matched = when {
                b == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                b == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) return buf.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n")
        }
        return null
    }

    private fun respond(out: OutputStream, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            413 -> "Payload Too Large"
            else -> "Internal Server Error"
        }
        val head = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        try {
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            out.write(bytes)
            out.flush()
        } catch (_: IOException) {
            // karşı taraf bağlantıyı kapattı
        }
    }

    private companion object {
        const val WORKERS = 2
        const val MAX_NONCES = 16
        const val MAX_HEAD_BYTES = 16 * 1024
    }
}
