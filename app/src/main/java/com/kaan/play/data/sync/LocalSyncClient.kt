package com.kaan.play.data.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.net.HttpURLConnection
import java.net.URL

/** Yüzde, tamamlanan senkronizasyon aşamalarını gösterir; bayt aktarım oranı değildir. */
data class LocalSyncProgress(
    val percent: Int,
    val message: String,
    val complete: Boolean = false,
    val failed: Boolean = false,
)

class LocalSyncClient(private val backup: LocalSyncBackup, private val profiles: LocalProfileRepository, private val sources: LocalSourceTransfer) {
    /** Aynı Wi‑Fi ağındaki sunucuları bulur ve verilen eşleştirme kodunu deneyerek bağlanır. */
    suspend fun discoverAndSync(
        code: String,
        port: Int = 43821,
        onProgress: suspend (LocalSyncProgress) -> Unit = {},
    ): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        var latestPercent = 0
        suspend fun report(progress: LocalSyncProgress) {
            latestPercent = progress.percent
            onProgress(progress)
        }
        report(LocalSyncProgress(0, "Aynı Wi‑Fi ağındaki cihazlar aranıyor"))
        val hosts = LocalSyncDiscovery.discover()
        if (hosts.isEmpty()) {
            val error = IllegalStateException("Aynı Wi‑Fi ağında Play cihazı bulunamadı")
            report(LocalSyncProgress(0, error.message.orEmpty(), complete = true, failed = true))
            return@withContext Result.failure(error)
        }
        var last: Throwable = IllegalStateException("Eşleştirme kodu reddedildi")
        for (host in hosts) {
            report(LocalSyncProgress(12, "Cihaz bulundu; eşleştirme kodu doğrulanıyor"))
            val result = sync(host, port, code, ::report)
            if (result.isSuccess) {
                report(LocalSyncProgress(100, "Eşitleme tamamlandı", complete = true))
                return@withContext Result.success(host to result.getOrThrow())
            }
            last = result.exceptionOrNull() ?: last
        }
        report(LocalSyncProgress(latestPercent, last.message ?: "Bağlantı kurulamadı", complete = true, failed = true))
        Result.failure(last)
    }

    /** Ağ kısa süreli kesilirse 1, 2, 4, 8 ve 16 saniyelik aralıklarla yeniden dener. */
    suspend fun syncWithRetry(host: String, port: Int, code: String, attempts: Int = 5): Result<Int> {
        var last: Result<Int> = Result.failure(IllegalStateException("Senkronizasyon başlatılamadı"))
        repeat(attempts.coerceIn(1, 8)) { index ->
            last = sync(host, port, code)
            if (last.isSuccess) return last
            if (index < attempts - 1) delay((1L shl index.coerceAtMost(4)) * 1_000L)
        }
        return last
    }

    suspend fun sync(
        host: String,
        port: Int,
        code: String,
        onProgress: suspend (LocalSyncProgress) -> Unit = {},
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(LocalSyncProgress(18, "Profil, izleme geçmişi ve eklenti yedekleri hazırlanıyor"))
            profiles.saveBackup(profiles.id.first(), backup.exportJson())
            val local = profiles.exportTransfer(sources.exportTransfer())
            onProgress(LocalSyncProgress(30, "Eşleştirme koduyla karşı cihaza bağlanılıyor"))
            val connection = (URL("http://${host.trim()}:$port/profiles").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                // İlk eşitlemede uzak depolar internetten kurulabilir; buna 3 dakikaya kadar izin ver.
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Play-Sync-Code", code)
            }
            onProgress(LocalSyncProgress(42, "Profiller, izleme geçmişi ve ayarlar gönderiliyor"))
            connection.outputStream.use { it.write(local.toByteArray(Charsets.UTF_8)) }
            onProgress(LocalSyncProgress(55, "Karşı cihaz profilleri ve eklenti depolarını uyguluyor"))
            if (connection.responseCode !in 200..299) error("TV Box bağlantısı reddetti (${connection.responseCode})")
            onProgress(LocalSyncProgress(70, "Karşı cihazdaki ortak profil listesi alınıyor"))
            val merged = connection.inputStream.bufferedReader().use { it.readText() }
            val incoming = profiles.decodeTransfer(merged)
            onProgress(LocalSyncProgress(80, "Profil ve izleme geçmişi kayıtları birleştiriliyor"))
            val activeBackup = profiles.importTransfer(merged)
            onProgress(LocalSyncProgress(90, "Eklenti depoları ve eklenti durumları eşitleniyor"))
            val sourceWarnings = incoming.warnings + sources.importTransfer(incoming.sources)
            val importedRecords = activeBackup?.let { backup.importJson(it).getOrThrow() } ?: 0
            if (sourceWarnings.isNotEmpty()) error("Profiller eşitlendi; ancak bazı eklentiler aktarılamadı: ${sourceWarnings.take(3).joinToString()}")
            importedRecords
        }
    }

    suspend fun transferAllProfiles(host: String, port: Int, code: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            profiles.saveBackup(profiles.id.first(), backup.exportJson())
            val connection = (URL("http://${host.trim()}:$port/profiles").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("X-Play-Sync-Code", code)
            }
            connection.outputStream.use { it.write(profiles.exportTransfer(sources.exportTransfer()).toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) error("TV Box aktarımı reddetti (${connection.responseCode})")
            val merged = connection.inputStream.bufferedReader().use { it.readText() }
            val incoming = profiles.decodeTransfer(merged)
            val activeBackup = profiles.importTransfer(merged)
            val sourceWarnings = incoming.warnings + sources.importTransfer(incoming.sources)
            val importedRecords = activeBackup?.let { backup.importJson(it).getOrThrow() } ?: 0
            if (sourceWarnings.isNotEmpty()) error("Profiller eşitlendi; ancak bazı eklentiler aktarılamadı: ${sourceWarnings.take(3).joinToString()}")
            importedRecords
        }
    }
}
