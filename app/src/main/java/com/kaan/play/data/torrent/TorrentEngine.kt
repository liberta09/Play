package com.kaan.play.data.torrent

import android.util.Log
import com.kaan.play.BuildConfig
import com.kaan.play.data.stream.TorrentLinks
import org.libtorrent4j.AlertListener
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.BlockFinishedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

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
        val handle: TorrentHandle,
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

    /** Blok düzeyinde okuma modu (false ise parça düzeyinde eski davranış geçerlidir). */
    @Volatile var blockModeEnabled: Boolean = BLOCK_MODE_ENABLED

    /** Oturum boyunca tespit edilen blok uyuşmazlık sayısı. */
    val blockMismatches = AtomicInteger(0)

    // Parça indeksi -> inen blokların bit kümesi
    private val finishedBlocks = ConcurrentHashMap<Int, BitSet>()

    // Parça indeksi -> doğrulandı mı bayrağı
    private val verifiedPieces = ConcurrentHashMap<Int, Boolean>()

    // "parça:blok" -> doğrulanmamışken sunulan bloğun CRC32'si
    private val servedBlockCrcs = ConcurrentHashMap<String, Long>()

    private data class ServedRange(val filePosition: Long, val length: Int, val crc: Long)
    private val servedRanges = java.util.concurrent.CopyOnWriteArrayList<ServedRange>()

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
            active?.let { prev ->
                if (prev.hash != hash) {
                    removeLocked(s, prev)
                } else {
                    active = null
                }
            }

            finishedBlocks.clear()
            verifiedPieces.clear()
            servedBlockCrcs.clear()
            servedRanges.clear()
            blockModeEnabled = BLOCK_MODE_ENABLED

            val sha1 = Sha1Hash.parseHex(hash)
            var handle = s.find(sha1)
            if (handle == null || !handle.isValid) {
                // DEFAULT_DONT_DOWNLOAD KULLANILMAZ (2026-10-31 ölçümü): üst bilgi gelince tüm dosyalar "indirme" olur, torrent anında
                // "bitti" sayılır ve libtorrent tohum eşleri "gereksiz bağlantı" diye koparır; yeniden bağlanma uzun sürdüğü için soğuk
                // açılışlarda 40 sn boyunca eş=0 kalıp ilk kare gelmiyordu (5 denemenin 4'ü). Şimdi dosyalar varsayılan önceliktedir;
                // oynatılmayanlar üst bilgi gelir gelmez aşağıda IGNORE yapılır (en çok ~0,1 sn'lik boşluk, veri sonunda silinir).
                val fullMagnet = TorrentLinks.appendTrackers(magnet, PUBLIC_TRACKERS)
                s.download(fullMagnet, dir, TorrentFlags.SEQUENTIAL_DOWNLOAD)
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
            // MKV/MP4 dizini çoğu zaman dosyanın sonundadır: baş ve son parçalar önce gelsin (paralel öncelik).
            val first = TorrentRules.pieceAt(offset, 0, pieceLength)
            val last = TorrentRules.pieceAt(offset, size - 1, pieceLength)
            listOf(first, first + 1, last - 1, last).filter { it in first..last }.forEach { h.piecePriority(it, Priority.TOP_PRIORITY) }
            listOf(first, first + 1).filter { it in first..last }.forEachIndexed { idx, p ->
                if (!h.havePiece(p)) h.setPieceDeadline(p, (idx + 1) * 250)
            }
            listOf(last - 1, last).filter { it in first..last }.forEachIndexed { idx, p ->
                if (!h.havePiece(p)) h.setPieceDeadline(p, (idx + 1) * 500)
            }

            TorrentDiag.d("torrent açıldı: hash=$hash dosya[$index]=${files.fileName(index)} boyut=$size parça=$pieceLength (dosya sayısı ${files.numFiles()}, ipucu=$fileHint, blokModu=$blockModeEnabled)")
            return Opened(hash, fileHint, h, files.fileName(index), size, offset, pieceLength, File(h.savePath(), files.filePath(index)))
                .also { active = it }
        }
    }

    /**
     * Yeni bir okuma (HTTP isteği) [position]'dan başlıyor:
     * ADIM 2 (PARALEL öncelik): Başlık ve kuyruk önceliklerini silmeden, yeni konumun parçalarını öne alır.
     */
    fun seek(file: Opened, position: Long) {
        if (!file.handle.isValid) return
        val piece = TorrentRules.pieceAt(file.offset, position, file.pieceLength)
        file.windowStart = piece
        if (!blockModeEnabled) {
            file.handle.clearPieceDeadlines()
            prioritize(file, piece)
        } else {
            prioritizeAtOffset(file, piece)
        }
    }

    /**
     * [position]'dan en çok [max] bayt okur; blok modundaysa istenen bloklar gelene dek bekler ve okur.
     * Blok modu kapalıysa veya parça moduna dönülmüşse eski parça bekleyen yolu kullanır.
     */
    fun read(file: Opened, position: Long, buffer: ByteArray, max: Int, raf: RandomAccessFile): Int {
        if (position >= file.size) return -1
        if (!blockModeEnabled) {
            awaitPiece(file, position)
            val n = minOf(max.toLong(), TorrentRules.bytesLeftInPiece(file.offset, position, file.pieceLength, file.size)).toInt()
            raf.seek(position)
            return raf.read(buffer, 0, n)
        }

        awaitData(file, position, minOf(max, TorrentBlockRangeRules.BLOCK_SIZE))

        val servable = TorrentBlockRangeRules.maxServableLength(
            file.offset, position, max, file.pieceLength, file.size,
            { isPieceVerified(file, it) },
            { p, b -> isBlockFinished(p, b) },
        )
        val toRead = if (servable > 0) servable else minOf(max.toLong(), TorrentRules.bytesLeftInPiece(file.offset, position, file.pieceLength, file.size)).toInt()

        raf.seek(position)
        val n = raf.read(buffer, 0, toRead)
        if (n > 0) {
            recordServedRange(file, position, buffer, n)
            recordServedCrc(file, position, buffer, n)
        }
        return n
    }

    /**
     * [position] baytından itibaren en az [length] baytlık bloklar inene dek bekler.
     * İstenen bloklar bittiği anda (tüm parça henüz doğrulanmamış olsa bile) döner.
     */
    fun awaitData(file: Opened, position: Long, length: Int = TorrentBlockRangeRules.BLOCK_SIZE) {
        if (!blockModeEnabled) {
            awaitPiece(file, position)
            return
        }
        val piece = TorrentRules.pieceAt(file.offset, position, file.pieceLength)
        if (active !== file) throw IOException("torrent closed")

        if (file.windowStart != piece) {
            file.windowStart = piece
            prioritizeAtOffset(file, piece)
        }

        if (isPieceVerified(file, piece)) return

        val reqLength = length.coerceIn(1, 65536)
        val required = TorrentBlockRangeRules.requiredBlocks(
            file.offset, position, reqLength, file.pieceLength, file.size,
        )
        if (required.isEmpty()) return

        if (TorrentBlockRangeRules.isRangeServable(required, { isPieceVerified(file, it) }, { p, b -> isBlockFinished(p, b) })) {
            return
        }

        var waited = 0L
        while (!TorrentBlockRangeRules.isRangeServable(required, { isPieceVerified(file, it) }, { p, b -> isBlockFinished(p, b) })) {
            if (active !== file || !file.handle.isValid) throw IOException("torrent closed")
            if (waited >= PIECE_TIMEOUT_MS) throw IOException("piece timeout")
            Thread.sleep(POLL_MS_FAST)
            waited += POLL_MS_FAST
            if (waited % REPRIORITIZE_MS == 0L) {
                prioritizeAtOffset(file, piece)
                logWait(file, piece, waited)
            }
        }
    }

    /** [position] baytını içeren parça inene dek bekler (eski / yedek parça düzeyi yol). */
    fun awaitPiece(file: Opened, position: Long) {
        val piece = TorrentRules.pieceAt(file.offset, position, file.pieceLength)
        if (active !== file) throw IOException("torrent closed")
        if (file.windowStart != piece) {
            file.windowStart = piece
            prioritize(file, piece)
        }
        var waited = 0L
        while (!haveOrClosed(file, piece)) {
            if (active !== file || !file.handle.isValid) throw IOException("torrent closed")
            if (waited >= PIECE_TIMEOUT_MS) throw IOException("piece timeout")
            Thread.sleep(POLL_MS)
            waited += POLL_MS
            if (waited % REPRIORITIZE_MS == 0L) {
                prioritize(file, piece)
                logWait(file, piece, waited)
            }
        }
    }

    /** Kapatılmış (geçersiz) tutamakta [TorrentHandle.havePiece] RuntimeException atar; bunu "torrent closed" olarak çevirir. */
    private fun haveOrClosed(file: Opened, piece: Int): Boolean =
        try {
            file.handle.havePiece(piece)
        } catch (e: RuntimeException) {
            throw IOException("torrent closed", e)
        }

    fun isPieceVerified(file: Opened, piece: Int): Boolean {
        if (verifiedPieces[piece] == true) return true
        val verified = runCatching { file.handle.havePiece(piece) }.getOrDefault(false)
        if (verified) verifiedPieces[piece] = true
        return verified
    }

    fun isBlockFinished(piece: Int, block: Int): Boolean {
        val set = finishedBlocks[piece] ?: return false
        return synchronized(set) { set.get(block) }
    }

    /** Teşhis (yalnızca debug): parça gelmeyi beklerken eş sayısı, hız ve durum; "neden geç açılıyor" ölçümü için. */
    private fun logWait(file: Opened, piece: Int, waitedMs: Long) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val st = file.handle.status()
            TorrentDiag.d(
                "bekleniyor: parça=$piece süre=${waitedMs / 1000}sn eş=${st.numPeers()} tohum=${st.numSeeds()} " +
                    "hız=${st.downloadPayloadRate() / 1024}KB/s inen=${st.totalDone() / 1024}KB durum=${st.state()}",
            )
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

    /**
     * ADIM 2: PARALEL önceliklendirme.
     * İstenen ofset için ilgili parçaya hemen en yüksek öncelik ve kısa süre sınırı (150ms) verir.
     * Başlık ve kuyruk parçalarının önceliklerini bozmaz.
     */
    private fun prioritizeAtOffset(file: Opened, piece: Int) {
        val h = file.handle
        if (!h.isValid) return
        val first = TorrentRules.pieceAt(file.offset, 0, file.pieceLength)
        val last = TorrentRules.pieceAt(file.offset, file.size - 1, file.pieceLength)

        if (piece in first..last && !isPieceVerified(file, piece)) {
            h.piecePriority(piece, Priority.TOP_PRIORITY)
            h.setPieceDeadline(piece, 150)
        }
        if (piece + 1 in first..last && !isPieceVerified(file, piece + 1)) {
            h.piecePriority(piece + 1, Priority.TOP_PRIORITY)
            h.setPieceDeadline(piece + 1, 350)
        }

        // Başlık ve kuyruk parçalarını da canlı tut (paralel öncelik)
        listOf(first, first + 1, last - 1, last).filter { it in first..last }.forEachIndexed { idx, p ->
            if (!isPieceVerified(file, p)) {
                h.piecePriority(p, Priority.TOP_PRIORITY)
                h.setPieceDeadline(p, 500 + idx * 250)
            }
        }
    }

    private fun onBlockFinished(piece: Int, block: Int) {
        val set = finishedBlocks.computeIfAbsent(piece) { BitSet() }
        synchronized(set) { set.set(block) }
    }

    private fun onPieceFinished(piece: Int) {
        verifiedPieces[piece] = true
        verifyServedBlocks(active, piece)
        verifyServedRanges(active)
    }

    private fun arePiecesVerified(file: Opened, pos: Long, len: Int): Boolean {
        if (len <= 0) return true
        val p1 = TorrentRules.pieceAt(file.offset, pos, file.pieceLength)
        val p2 = TorrentRules.pieceAt(file.offset, pos + len - 1L, file.pieceLength)
        for (p in p1..p2) {
            if (!isPieceVerified(file, p)) return false
        }
        return true
    }

    private fun recordServedRange(file: Opened, position: Long, buffer: ByteArray, readBytes: Int) {
        if (!blockModeEnabled) return
        if (arePiecesVerified(file, position, readBytes)) return
        val crc = CRC32().apply { update(buffer, 0, readBytes) }.value
        servedRanges.add(ServedRange(position, readBytes, crc))
    }

    private fun verifyServedRanges(file: Opened?) {
        val f = file ?: return
        if (!f.path.exists()) return
        val toRemove = mutableListOf<ServedRange>()
        try {
            RandomAccessFile(f.path, "r").use { raf ->
                for (range in servedRanges) {
                    if (arePiecesVerified(f, range.filePosition, range.length)) {
                        toRemove.add(range)
                        val buf = ByteArray(range.length)
                        raf.seek(range.filePosition)
                        raf.readFully(buf)
                        val verifiedCrc = CRC32().apply { update(buf) }.value
                        if (range.crc != verifiedCrc) {
                            Log.e(TAG, "BLOK_UYUMSUZ: pos=${range.filePosition} len=${range.length} oncekiCrc=${range.crc} dogrulanmisCrc=$verifiedCrc")
                            TorrentDiag.d("BLOK_UYUMSUZ: pos=${range.filePosition} len=${range.length}! Parça düzeyine dönülüyor.")
                            blockMismatches.incrementAndGet()
                            blockModeEnabled = false
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "verifyServedRanges hatası: ${e.message}")
        }
        if (toRemove.isNotEmpty()) {
            servedRanges.removeAll(toRemove)
        }
    }

    /**
     * BÜTÜNLÜK: Doğrulanmamışken servis edilmiş blokların CRC32'sini, parça tamamlanıp
     * doğrulandığında diskteki gerçek veriyle karşılaştırır.
     */
    private fun verifyServedBlocks(file: Opened?, piece: Int) {
        val f = file ?: return
        if (!f.path.exists()) return
        val prefix = "$piece:"
        val keysToCheck = servedBlockCrcs.keys.filter { it.startsWith(prefix) }
        if (keysToCheck.isEmpty()) return

        try {
            RandomAccessFile(f.path, "r").use { raf ->
                for (key in keysToCheck) {
                    val servedCrc = servedBlockCrcs[key] ?: continue
                    val b = key.substringAfter(':').toIntOrNull() ?: continue
                    val blockAbsOffset = piece.toLong() * f.pieceLength + b.toLong() * TorrentBlockRangeRules.BLOCK_SIZE
                    val fileBlockOffset = blockAbsOffset - f.offset
                    if (fileBlockOffset >= 0 && fileBlockOffset + TorrentBlockRangeRules.BLOCK_SIZE <= f.size) {
                        val buf = ByteArray(TorrentBlockRangeRules.BLOCK_SIZE)
                        raf.seek(fileBlockOffset)
                        raf.readFully(buf)
                        val verifiedCrc = CRC32().apply { update(buf) }.value
                        if (servedCrc != verifiedCrc) {
                            Log.e(TAG, "BLOK_UYUMSUZ: parca=$piece blok=$b oncekiCrc=$servedCrc dogrulanmisCrc=$verifiedCrc")
                            TorrentDiag.d("BLOK_UYUMSUZ: parca=$piece blok=$b! Parça düzeyine dönülüyor.")
                            blockMismatches.incrementAndGet()
                            blockModeEnabled = false // Geri kalanında parça düzeyine dön
                        } else {
                            servedBlockCrcs.remove(key)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "verifyServedBlocks hatası: ${e.message}")
        }
    }

    private fun recordServedCrc(file: Opened, position: Long, buffer: ByteArray, readBytes: Int) {
        if (!blockModeEnabled) return
        val p = TorrentRules.pieceAt(file.offset, position, file.pieceLength)
        if (isPieceVerified(file, p)) return

        val blocks = TorrentBlockRangeRules.requiredBlocks(
            file.offset, position, readBytes, file.pieceLength, file.size,
        )
        for (coord in blocks) {
            val blockAbsStart = coord.pieceIndex.toLong() * file.pieceLength + coord.blockIndex.toLong() * TorrentBlockRangeRules.BLOCK_SIZE
            val fileBlockStart = blockAbsStart - file.offset
            val fileBlockEnd = fileBlockStart + TorrentBlockRangeRules.BLOCK_SIZE - 1L

            val bufStartOffset = (fileBlockStart - position).toInt()
            val bufEndOffset = (fileBlockEnd - position).toInt()
            if (bufStartOffset >= 0 && bufEndOffset < readBytes) {
                val key = "${coord.pieceIndex}:${coord.blockIndex}"
                if (!servedBlockCrcs.containsKey(key)) {
                    val crc = CRC32().apply { update(buffer, bufStartOffset, TorrentBlockRangeRules.BLOCK_SIZE) }.value
                    servedBlockCrcs[key] = crc
                }
            }
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
                if (active === closing) removeLocked(s, closing)
            }
        }
    }

    private fun removeLocked(s: SessionManager, file: Opened) {
        active = null
        finishedBlocks.clear()
        verifiedPieces.clear()
        servedBlockCrcs.clear()
        blockModeEnabled = BLOCK_MODE_ENABLED
        if (file.handle.isValid) s.remove(file.handle, SessionHandle.DELETE_FILES)
    }

    private fun ensureSession(): SessionManager {
        session?.let { return it }
        dir.deleteRecursively()
        dir.mkdirs()
        val settings = SettingsPack()
            .connectionsLimit(CONNECTIONS)
            .activeDownloads(2)
            .apply {
                setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
                setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
            }
        val params = SessionParams(settings).apply { setPosixDiskIO() }
        return try {
            TorrentDiag.d("libtorrent oturumu başlıyor (ABI=${android.os.Build.SUPPORTED_ABIS.joinToString()}, dizin=$dir, blokModu=$BLOCK_MODE_ENABLED)")
            SessionManager(false).also {
                it.addListener(object : AlertListener {
                    override fun types(): IntArray? = null
                    override fun alert(alert: Alert<*>) {
                        when (alert) {
                            is BlockFinishedAlert -> onBlockFinished(alert.pieceIndex(), alert.blockIndex())
                            is PieceFinishedAlert -> onPieceFinished(alert.pieceIndex())
                            else -> {
                                val type = alert.type().name
                                if (NOISY_ALERTS.any { type.startsWith(it) }) return
                                TorrentDiag.d("alert $type: ${runCatching { alert.message() }.getOrNull()}")
                            }
                        }
                    }
                })
                it.start(params)
                session = it
                TorrentDiag.d("libtorrent oturumu başladı")
            }
        } catch (e: Throwable) {
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

    companion object {
        const val TAG = "PlayTorrent"

        /** Blok düzeyinde okuma modu (false ise parça düzeyinde eski davranış geçerlidir). */
        @Volatile var BLOCK_MODE_ENABLED: Boolean = true

        /** Teşhis logunda atlanan, çok sık gelen alert türleri. */
        val NOISY_ALERTS = listOf("BLOCK_", "PIECE_FINISHED", "STATS", "STATE_UPDATE", "SESSION_STATS", "PEER_", "INCOMING_", "DHT_", "UNWANTED_BLOCK", "LOG", "PICKER_LOG")
        const val METADATA_TIMEOUT_MS = 60_000L
        const val PIECE_TIMEOUT_MS = 90_000L
        const val POLL_MS = 100L
        const val POLL_MS_FAST = 20L
        const val REPRIORITIZE_MS = 2_000L
        const val DEADLINE_STEP_MS = 250
        const val CONNECTIONS = 200
        const val SPACE_MARGIN = 200L * 1024 * 1024

        /** Yalnızca infoHash veren kaynaklarda eş bulmayı hızlandıran, herkese açık bilinen izleyiciler. */
        val PUBLIC_TRACKERS = listOf(
            "udp://zer0day.ch:1337/announce",
            "udp://tracker.publictracker.xyz:6969/announce",
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.demonii.com:1337/announce",
            "udp://open.stealth.si:80/announce",
            "http://tracker.renfei.net:8080/announce",
            "udp://udp.tracker.projectk.org:23333/announce",
            "udp://tracker.tryhackx.org:6969/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.theoks.net:6969/announce",
            "udp://tracker.startwork.cv:1337/announce",
            "udp://tracker.qu.ax:6969/announce",
            "udp://tracker.plx.im:6969/announce",
            "udp://tracker.nyaa.vc:6969/announce",
            "udp://tracker.iperson.xyz:6969/announce",
            "udp://tracker.gmi.gd:6969/announce",
            "udp://tracker.fnix.net:6969/announce",
            "udp://tracker.flatuslifir.is:6969/announce",
            "udp://tracker.ducks.party:1984/announce",
            "udp://tracker.bluefrog.pw:2710/announce",
        )
    }
}
