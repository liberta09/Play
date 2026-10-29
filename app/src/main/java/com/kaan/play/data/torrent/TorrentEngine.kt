package com.kaan.play.data.torrent

import android.util.Log
import com.kaan.play.data.stream.TorrentLinks
import org.libtorrent4j.AlertListener
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Torrent açılamadı; [reason] yerel sunucunun HTTP durumuna çevrilir. */
class TorrentException(val reason: Reason, message: String) : IOException(message) {
    enum class Reason { BadLink, NoMetadata, NoFile, NoSpace, Engine }
}

/**
 * Yerleşik torrent motoru (libtorrent4j). Aynı anda tek torrent açıktır: yalnızca oynatılan dosya indirilir, okuma konumunun
 * önündeki parçalara süre sınırı (deadline) verilir, böylece oynatıcının istediği bayt önce gelir. Veriler [dir] içinde geçicidir:
 * son oynatıcı kapanınca ([release]) ve oturum her başladığında silinir. Oturum ilk torrentte başlar, son oynatıcı kapanınca durur.
 */
class TorrentEngine(private val dir: File) {

    /** Açık torrentteki oynatılan dosya. */
    class Opened internal constructor(
        val hash: String,
        val fileHint: Int?,
        internal val handle: TorrentHandle,
        val name: String,
        val size: Long,
        /** Dosyanın torrent içindeki başlangıç baytı. */
        internal val offset: Long,
        internal val pieceLength: Int,
        internal val path: File,
    ) {
        /** Öncelik penceresinin başladığı parça (-1: yok). */
        @Volatile internal var windowStart: Int = -1
    }

    private val lock = Any()
    private var session: SessionManager? = null
    @Volatile private var active: Opened? = null
    private val users = AtomicInteger()
    private val background = Executors.newSingleThreadExecutor { r -> Thread(r, "torrent-engine").apply { isDaemon = true } }

    /** Kaynak listesinde torrent görünce oturumu önceden başlatır (DHT ısınsın, açılış hızlansın). */
    fun warmUp() {
        background.execute { runCatching { synchronized(lock) { ensureSession() } } }
    }

    /**
     * Magnet'i açar (gerekirse üst bilgiyi bekler) ve oynatılacak dosyayı seçer. Aynı torrent+dosya zaten açıksa onu döndürür;
     * başka bir torrent açıksa o kapatılıp dosyaları silinir. Engelleyicidir: arka plan iş parçacığında çağrılır.
     */
    fun open(magnet: String, fileHint: Int?): Opened {
        val hash = TorrentLinks.hexHashOf(magnet) ?: throw TorrentException(TorrentException.Reason.BadLink, "bad magnet")
        TorrentDiag.d("motor open: hash=$hash fileHint=$fileHint")
        synchronized(lock) {
            active?.let { if (it.hash == hash && it.fileHint == fileHint && it.handle.isValid) return it }
            val s = ensureSession()
            active?.let { removeLocked(s, it) }

            val sha1 = Sha1Hash.parseHex(hash)
            var handle = s.find(sha1)
            if (handle == null || !handle.isValid) {
                // Dosyalar varsayılan olarak indirilmez: üst bilgi gelince yalnızca seçilen dosya açılır.
                s.download(magnet, dir, TorrentFlags.DEFAULT_DONT_DOWNLOAD)
                handle = waitFor(METADATA_TIMEOUT_MS) { s.find(sha1)?.takeIf { it.isValid } }
                    ?: throw TorrentException(TorrentException.Reason.Engine, "torrent not added")
                PUBLIC_TRACKERS.forEach { runCatching { handle.addTracker(AnnounceEntry(it)) } }
            }
            val h = handle
            waitFor(METADATA_TIMEOUT_MS) { h.takeIf { it.isValid && it.status().hasMetadata() } }
                ?: run {
                    s.remove(h, SessionHandle.DELETE_FILES)
                    throw TorrentException(TorrentException.Reason.NoMetadata, "no metadata")
                }

            val info = h.torrentFile()
            val files = info.files()
            val entries = (0 until files.numFiles()).map {
                TorrentRules.FileEntry(files.fileName(it), files.fileSize(it), files.padFileAt(it))
            }
            val index = TorrentRules.pickFile(entries, fileHint) ?: run {
                s.remove(h, SessionHandle.DELETE_FILES)
                throw TorrentException(TorrentException.Reason.NoFile, "no playable file")
            }
            val size = files.fileSize(index)
            if (dir.usableSpace in 1 until size + SPACE_MARGIN) {
                s.remove(h, SessionHandle.DELETE_FILES)
                throw TorrentException(TorrentException.Reason.NoSpace, "not enough space")
            }

            h.prioritizeFiles(Array(files.numFiles()) { if (it == index) Priority.DEFAULT else Priority.IGNORE })
            h.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            val offset = files.fileOffset(index)
            val pieceLength = info.pieceLength()
            // MKV/MP4 dizini çoğu zaman dosyanın sonundadır: baş ve son parçalar önce gelsin.
            val first = TorrentRules.pieceAt(offset, 0, pieceLength)
            val last = TorrentRules.pieceAt(offset, size - 1, pieceLength)
            listOf(first, first + 1, last - 1, last).filter { it in first..last }.forEach { h.piecePriority(it, Priority.TOP_PRIORITY) }

            TorrentDiag.d("torrent açıldı: hash=$hash dosya[$index]=${files.fileName(index)} boyut=$size parça=$pieceLength (dosya sayısı ${files.numFiles()}, ipucu=$fileHint)")
            return Opened(hash, fileHint, h, files.fileName(index), size, offset, pieceLength, File(h.savePath(), files.filePath(index)))
                .also { active = it }
        }
    }

    /** Yeni bir okuma (HTTP isteği) [position]'dan başlıyor: eski süre sınırları kalkar, bu konumun önü öne alınır. */
    fun seek(file: Opened, position: Long) {
        if (!file.handle.isValid) return
        file.handle.clearPieceDeadlines()
        file.windowStart = -1
    }

    /**
     * [position]'dan en çok [max] bayt okur; parça henüz yoksa gelmesini bekler. Torrent kapatılırsa ya da parça uzun süre
     * gelmezse [IOException]. Okunan bayt sayısını döner (dosya sonunda -1).
     */
    fun read(file: Opened, position: Long, buffer: ByteArray, max: Int, raf: RandomAccessFile): Int {
        if (position >= file.size) return -1
        awaitPiece(file, position)
        val n = minOf(max.toLong(), TorrentRules.bytesLeftInPiece(file.offset, position, file.pieceLength, file.size)).toInt()
        raf.seek(position)
        return raf.read(buffer, 0, n)
    }

    /** [position] baytını içeren parça inene dek bekler (dosya diske ilk parçayla birlikte yazılır). */
    fun awaitPiece(file: Opened, position: Long) {
        val piece = TorrentRules.pieceAt(file.offset, position, file.pieceLength)
        if (active !== file) throw IOException("torrent closed")
        // Okuma yeni bir parçaya geçtikçe öncelik penceresi kayar.
        if (file.windowStart != piece) {
            file.windowStart = piece
            prioritize(file, piece)
        }
        var waited = 0L
        while (!file.handle.havePiece(piece)) {
            if (active !== file || !file.handle.isValid) throw IOException("torrent closed")
            if (waited >= PIECE_TIMEOUT_MS) throw IOException("piece timeout")
            Thread.sleep(POLL_MS)
            waited += POLL_MS
            if (waited % REPRIORITIZE_MS == 0L) prioritize(file, piece)
        }
    }

    private fun prioritize(file: Opened, piece: Int) {
        val last = TorrentRules.pieceAt(file.offset, file.size - 1, file.pieceLength)
        val window = TorrentRules.readAheadPieces(file.pieceLength)
        for (i in 0 until window) {
            val p = piece + i
            if (p > last) break
            if (!file.handle.havePiece(p)) file.handle.setPieceDeadline(p, DEADLINE_STEP_MS * (i + 1))
        }
    }

    /** Oynatıcı açıldı: motor, [release] edilene dek kapanmaz. Ana iş parçacığından çağrılabilir (kilit almaz). */
    fun retain() {
        users.incrementAndGet()
    }

    /**
     * Oynatıcı kapandı. Başka oynatıcı kalmadıysa açık torrent kapanır, dosyaları silinir ve oturum durur. Arka planda yapılır;
     * o arada yeni bir oynatıcı [retain] ettiyse hiçbir şey kapanmaz.
     */
    fun release() {
        if (users.decrementAndGet() > 0) return
        background.execute {
            synchronized(lock) {
                if (users.get() > 0) return@synchronized
                val s = session ?: return@synchronized
                active?.let { removeLocked(s, it) }
                runCatching { s.stop() }
                session = null
                dir.deleteRecursively()
            }
        }
    }

    /**
     * Sonraki bölüme geçilirken önceki torrent hemen kapatılır (dosyaları silinir, eşlere bağlantı kesilir); oturum açık kalır,
     * yeni oynatıcı onu kullanır. Arka planda yapılır; açık torrent yoksa bir şey olmaz.
     */
    fun closeActive() {
        val closing = active ?: return
        background.execute {
            synchronized(lock) {
                val s = session ?: return@synchronized
                // Arada yeni bir torrent açıldıysa (aynı anda başlayan bölüm) ona dokunulmaz.
                if (active === closing) removeLocked(s, closing)
            }
        }
    }

    private fun removeLocked(s: SessionManager, file: Opened) {
        active = null
        if (file.handle.isValid) s.remove(file.handle, SessionHandle.DELETE_FILES)
    }

    private fun ensureSession(): SessionManager {
        session?.let { return it }
        // Önceki oturumdan (ör. uygulama öldürüldüyse) kalanlar silinir.
        dir.deleteRecursively()
        dir.mkdirs()
        val settings = SettingsPack()
            .connectionsLimit(CONNECTIONS)
            .activeDownloads(2)
        val params = SessionParams(settings).apply { setPosixDiskIO() }
        return try {
            TorrentDiag.d("libtorrent oturumu başlıyor (ABI=${android.os.Build.SUPPORTED_ABIS.joinToString()}, dizin=$dir)")
            SessionManager(false).also {
                it.addListener(object : AlertListener {
                    override fun types(): IntArray? = null
                    override fun alert(alert: Alert<*>) {
                        val type = alert.type().name
                        if (NOISY_ALERTS.any { type.startsWith(it) }) return
                        TorrentDiag.d("alert $type: ${runCatching { alert.message() }.getOrNull()}")
                    }
                })
                it.start(params)
                session = it
                TorrentDiag.d("libtorrent oturumu başladı")
            }
        } catch (e: Throwable) {
            // Yerel kütüphane bu işlemcide yoksa (UnsatisfiedLinkError) ya da başlatılamazsa.
            TorrentDiag.e("libtorrent oturumu başlatılamadı", e)
            Log.w(TAG, "libtorrent session failed", e)
            throw TorrentException(TorrentException.Reason.Engine, "engine unavailable")
        }
    }

    private inline fun <T : Any> waitFor(timeoutMs: Long, probe: () -> T?): T? {
        val until = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < until) {
            probe()?.let { return it }
            Thread.sleep(POLL_MS)
        }
        return probe()
    }

    private companion object {
        const val TAG = "PlayTorrent"

        /** Teşhis logunda atlanan, çok sık gelen alert türleri. */
        val NOISY_ALERTS = listOf("BLOCK_", "PIECE_FINISHED", "STATS", "STATE_UPDATE", "SESSION_STATS", "PEER_", "INCOMING_", "DHT_", "UNWANTED_BLOCK", "LOG", "PICKER_LOG")
        const val METADATA_TIMEOUT_MS = 60_000L
        const val PIECE_TIMEOUT_MS = 90_000L
        const val POLL_MS = 100L
        const val REPRIORITIZE_MS = 2_000L
        const val DEADLINE_STEP_MS = 250
        const val CONNECTIONS = 200
        const val SPACE_MARGIN = 200L * 1024 * 1024

        /** Yalnızca infoHash veren kaynaklarda eş bulmayı hızlandıran, herkese açık bilinen izleyiciler. */
        val PUBLIC_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://explodie.org:6969/announce",
        )
    }
}
