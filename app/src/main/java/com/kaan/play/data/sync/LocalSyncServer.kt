package com.kaan.play.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.io.IOException
import kotlinx.coroutines.delay

/** Hesapsız, yalnızca aynı ağda çalışan küçük senkronizasyon sunucusu. */
class LocalSyncServer(private val backup: LocalSyncBackup, private val profiles: LocalProfileRepository, private val sources: LocalSourceTransfer, private val port: Int = 43821) {
    @Volatile var running: Boolean = false
        private set
    private val _progress = MutableStateFlow<LocalSyncProgress?>(null)
    val progress: StateFlow<LocalSyncProgress?> = _progress

    fun clearProgress() {
        _progress.value = null
    }
    private var server: ServerSocket? = null
    private var job: Job? = null
    private var discoverySocket: DatagramSocket? = null

    fun start(scope: CoroutineScope, codeProvider: suspend () -> String) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    ServerSocket().use { socketServer ->
                        socketServer.reuseAddress = true
                        socketServer.bind(InetSocketAddress("0.0.0.0", port), 16)
                        server = socketServer
                        running = true
                        while (isActive) {
                            try {
                                handle(socketServer.accept(), codeProvider)
                            } catch (e: IOException) {
                                if (isActive) throw e
                            }
                        }
                    }
                } catch (e: IOException) {
                    // Android TV'lerde ağ/Wi-Fi arayüzü geç hazır olabilir veya port geçici olarak meşgul olabilir.
                    // Sessizce tamamen durmak yerine sunucuyu uygulama çalıştığı sürece yeniden dene.
                    running = false
                    server = null
                    if (isActive) delay(1_500L)
                }
            }
            running = false
            server = null
        }
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    DatagramSocket(null).use { socket ->
                        socket.reuseAddress = true
                        socket.bind(InetSocketAddress(LocalSyncDiscovery.DISCOVERY_PORT))
                    discoverySocket = socket
                    while (isActive) LocalSyncDiscovery.serveOnce(socket)
                    }
                } catch (e: IOException) {
                    if (isActive) delay(1_500L)
                } finally {
                    discoverySocket = null
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        runCatching { server?.close() }
        runCatching { discoverySocket?.close() }
        server = null
        running = false
    }

    private suspend fun handle(socket: Socket, codeProvider: suspend () -> String) {
        socket.use { client ->
            // Eklenti depoları ilk eşitlemede karşı cihazda indirilirken istek uzun sürebilir.
            client.soTimeout = 180_000
            val input = client.getInputStream()
            val request = readHttpLine(input)
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readHttpLine(input)
                if (line.isEmpty()) break
                val split = line.indexOf(':')
                if (split > 0) headers[line.substring(0, split).trim().lowercase()] = line.substring(split + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull()?.takeIf { it >= 0 }?.coerceAtMost(32_000_000) ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(body, read, length - read)
                if (n <= 0) break
                read += n
            }
            if (read != length) throw IOException("HTTP isteği eksik gövde ile tamamlandı")
            val parts = request.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty()
            val authorized = headers["x-play-sync-code"] == codeProvider()
            val response = when {
                path == "/health" && method == "GET" -> "200 OK" to "{\"ok\":true}"
                !authorized -> "401 Unauthorized" to "{\"error\":\"pairing_required\"}"
                path == "/sync" && method == "GET" -> "200 OK" to backup.exportJson()
                path == "/sync" && method == "POST" -> backup.mergeJson(String(body, Charsets.UTF_8)).fold({ "200 OK" to backup.exportJson() }, { "400 Bad Request" to "{\"error\":\"invalid_backup\"}" })
                path == "/profiles" && method == "GET" -> {
                    profiles.saveBackup(profiles.id.first(), backup.exportJson())
                    "200 OK" to profiles.exportTransfer(sources.exportTransfer())
                }
                path == "/profiles" && method == "POST" -> {
                    _progress.value = LocalSyncProgress(35, "Diğer cihazdan profil ve geçmiş verisi alındı")
                    runCatching {
                        // Karşı cihazın verisini birleştirmeden önce bu cihazın etkin profilini de yedekle.
                        profiles.saveBackup(profiles.id.first(), backup.exportJson())
                        _progress.value = LocalSyncProgress(55, "Profiller ve izleme geçmişleri birleştiriliyor")
                        val incoming = profiles.decodeTransfer(String(body, Charsets.UTF_8))
                        val activeBackup = profiles.importTransfer(String(body, Charsets.UTF_8))
                        _progress.value = LocalSyncProgress(78, "Eklenti depoları ve durumları içe aktarılıyor")
                        val sourceWarnings = sources.importTransfer(incoming.sources)
                        activeBackup?.let { backup.importJson(it) }
                        val responseBody = profiles.exportTransfer(sources.exportTransfer(), sourceWarnings)
                        _progress.value = if (sourceWarnings.isEmpty()) {
                            LocalSyncProgress(100, "Profil ve veri aktarımı tamamlandı", complete = true)
                        } else {
                            LocalSyncProgress(95, "Profil ve geçmiş eşitlendi; bazı eklentiler aktarılamadı", complete = true, failed = true)
                        }
                        "200 OK" to responseBody
                    }.getOrElse {
                        _progress.value = LocalSyncProgress(78, "Aktarım başarısız: ${it.message.orEmpty()}", complete = true, failed = true)
                        "400 Bad Request" to "{\"error\":\"invalid_profiles\"}"
                    }
                }
                else -> "404 Not Found" to "{\"error\":\"not_found\"}"
            }
            val bytes = response.second.toByteArray(Charsets.UTF_8)
            val out = client.getOutputStream()
            out.write("HTTP/1.1 ${response.first}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    /** HTTP satırlarını byte stream olarak okur; Content-Length UTF-8 karakterini değil bayt sayısını belirtir. */
    private fun readHttpLine(input: java.io.InputStream): String {
        val line = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1 || value == '\n'.code) break
            if (line.size() >= 8_192) throw IOException("HTTP başlık satırı çok uzun")
            if (value != '\r'.code) line.write(value)
        }
        return String(line.toByteArray(), Charsets.ISO_8859_1)
    }
}
