package com.kaan.play.data.torrent

import android.os.SystemClock
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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

/** Torrent açılamadı; [reason] yerel sunucunun HTTP durumuna çevrilir. */
class TorrentException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason { BadLink, NoMetadata, NoFile, NoSpace, Engine }
}

/**
 * Yerleşik torrent motoru (libtorrent4j). Aynı anda tek torrent açıktır: yalnızca oynatılan dosya indirilir, okuma konumunun
 * önündeki parçalara süre sınırı (deadline) verilir, böylece oynatıcının istediği bayt önce gelir. Veriler [dir] içinde geçicidir:
 * son oynatıcı kapanınca ([release]) ve oturum her başladığında silinir. Oturum ilk torrentte başlar, son oynatıcı kapanınca durur.
 */
class TorrentEngine(private val dir: File, private val stateDir: File? = null) {

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
    @Volatile private var openingHandle: TorrentHandle? = null
    @Volatile private var isOpening: Boolean = false
    private val users = AtomicInteger()
    private val background = Executors.newSingleThreadExecutor { r -> Thread(r, "torrent-engine").apply { isDaemon = true } }
    @Volatile private var firstFrameArrived: Boolean = false
    private val idleExecutor = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "torrent-idle").apply { isDaemon = true } }
    @Volatile private var idleTask: ScheduledFuture<*>? = null

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

    /** Oynatıcıya HTTP üzerinden verilen kümülatif bayt sayısı. */
    val totalBytesServed = java.util.concurrent.atomic.AtomicLong(0L)

    @Volatile private var firstPeerLogged = false
    @Volatile private var piece0Block0Logged = false

    /** Kaynak listesinde torrent görünce oturumu önceden başlatır (DHT ısınsın, açılış hızlansın). */
    fun warmUp() {
        if (!TorrentSupport.isBuiltInSupported()) return
        background.execute { runCatching { synchronized(lock) { ensureSession() } } }
    }

    /** [warmUp] için takma ad. */
    fun prewarm() = warmUp()

    /**
     * Magnet'i açar (gerekirse üst bilgiyi bekler) ve oynatılacak dosyayı seçer. Aynı torrent+dosya zaten açıksa onu döndürür;
     * başka bir torrent açıksa o kapatılıp dosyaları silinir. Engelleyicidir: arka plan iş parçacığında çağrılır.
     */
    fun open(magnet: String, fileHint: Int?): Opened {
        val hash = TorrentLinks.hexHashOf(magnet) ?: throw TorrentException(TorrentException.Reason.BadLink, "bad magnet")
        TorrentDiag.d("motor open: hash=$hash fileHint=$fileHint")
        isOpening = true
        try {
            synchronized(lock) {
            idleTask?.cancel(false)
            idleTask = null
            active?.let { if (it.hash == hash && it.fileHint == fileHint && it.handle.isValid) { TorrentDiag.d("motor open: hash=$hash (REUSING EXISTING ACTIVE TORRENT)"); return it } }
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
            blockModeEnabled = BLOCK_MODE_ENABLED
            totalBytesServed.set(0L)
            firstPeerLogged = false
            piece0Block0Logged = false
            firstFrameArrived = false

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
            openingHandle = handle
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
            val offset = files.fileOffset(index)
            val pieceLength = info.pieceLength()
            val first = TorrentRules.pieceAt(offset, 0, pieceLength)
            val last = TorrentRules.pieceAt(offset, size - 1, pieceLength)

            if (STARTUP_FOCUS_ENABLED) {
                h.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                for (p in first..last) {
                    h.piecePriority(p, Priority.LOW)
                }
                val startupDeadlines = TorrentBlockRangeRules.startupFocusPieces(first, last) { h.havePiece(it) }
                for (d in startupDeadlines) {
                    h.piecePriority(d.pieceIndex, Priority.TOP_PRIORITY)
                    h.setPieceDeadline(d.pieceIndex, d.deadlineMs)
                }
            } else {
                h.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                listOf(first, first + 1, last - 1, last).filter { it in first..last }.forEach { h.piecePriority(it, Priority.TOP_PRIORITY) }
                listOf(first, first + 1).filter { it in first..last }.forEachIndexed { idx, p ->
                    if (!h.havePiece(p)) h.setPieceDeadline(p, (idx + 1) * 250)
                }
                listOf(last - 1, last).filter { it in first..last }.forEachIndexed { idx, p ->
                    if (!h.havePiece(p)) h.setPieceDeadline(p, (idx + 1) * 500)
                }
            }

            TorrentDiag.d("MILESTONE: metadata_ready pieces=${info.numPieces()} size=$size pieceLength=$pieceLength")
            TorrentDiag.d("torrent açıldı: hash=$hash dosya[$index]=${files.fileName(index)} boyut=$size parça=$pieceLength (dosya sayısı ${files.numFiles()}, ipucu=$fileHint, blokModu=$blockModeEnabled)")
            startOpeningStatsTracker(h)
            return Opened(hash, fileHint, h, files.fileName(index), size, offset, pieceLength, File(h.savePath(), files.filePath(index)))
                .also { active = it }
            }
        } finally {
            isOpening = false
            openingHandle = null
        }
    }

    /**
     * Yeni bir okuma (HTTP isteği) [position]'dan başlıyor:
     * ADIM 2 (PARALEL öncelik): Başlık ve kuyruk önceliklerini silmeden, yeni konumun parçalarını öne alır.
     */
    /** İlk video karesi ekrana gelince çağrılır: sequential_download açılır ve normal pencere önceliği başlar. */

    /**
     * Torrent yükleme ekranı için anlık durum görüntüsü (kilitsiz, ana iş parçacığını engellemez).
     */
    fun getLoadSnapshot(): TorrentLoadProgressRules.TorrentLoadSnapshot {
        return runCatching {
            val currentActive = active
            if (currentActive != null && currentActive.handle.isValid) {
                val st = currentActive.handle.status()
                val buffered = maxOf(st.totalWantedDone(), totalBytesServed.get())
                TorrentLoadProgressRules.TorrentLoadSnapshot(
                    hasMetadata = true,
                    metadataFraction = 1f,
                    bufferedBytes = buffered,
                    requiredBytes = TorrentLoadProgressRules.DEFAULT_REQUIRED_BYTES,
                )
            } else {
                val h = openingHandle
                if (h != null && h.isValid) {
                    val st = h.status()
                    val meta = st.hasMetadata()
                    if (meta) {
                        val buffered = maxOf(st.totalWantedDone(), totalBytesServed.get())
                        TorrentLoadProgressRules.TorrentLoadSnapshot(
                            hasMetadata = true,
                            metadataFraction = 1f,
                            bufferedBytes = buffered,
                            requiredBytes = TorrentLoadProgressRules.DEFAULT_REQUIRED_BYTES,
                        )
                    } else {
                        val frac = st.progress().coerceIn(0f, 1f)
                        TorrentLoadProgressRules.TorrentLoadSnapshot(
                            hasMetadata = false,
                            metadataFraction = frac,
                            bufferedBytes = 0L,
                            requiredBytes = TorrentLoadProgressRules.DEFAULT_REQUIRED_BYTES,
                        )
                    }
                } else {
                    TorrentLoadProgressRules.TorrentLoadSnapshot(
                        hasMetadata = false,
                        metadataFraction = 0f,
                        bufferedBytes = 0L,
                        requiredBytes = TorrentLoadProgressRules.DEFAULT_REQUIRED_BYTES,
                    )
                }
            }
        }.getOrElse {
            TorrentLoadProgressRules.TorrentLoadSnapshot(
                hasMetadata = false,
                metadataFraction = 0f,
                bufferedBytes = 0L,
                requiredBytes = TorrentLoadProgressRules.DEFAULT_REQUIRED_BYTES,
            )
        }
    }

    fun onFirstFrame() {
        TorrentDiag.d("motor: ilk kare bildirildi")
        firstFrameArrived = true
        val file = active ?: return
        if (!file.handle.isValid) return
        background.execute {
            synchronized(lock) {
                if (!file.handle.isValid) return@synchronized
                if (STARTUP_FOCUS_ENABLED) {
                    file.handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                    val first = TorrentRules.pieceAt(file.offset, 0, file.pieceLength)
                    val last = TorrentRules.pieceAt(file.offset, file.size - 1, file.pieceLength)
                    for (p in first..last) {
                        if (!file.handle.havePiece(p)) {
                            file.handle.piecePriority(p, Priority.DEFAULT)
                        }
                    }
                    val piece = file.windowStart.takeIf { it >= 0 } ?: TorrentRules.pieceAt(file.offset, 0, file.pieceLength)
                    if (blockModeEnabled) {
                        prioritizeAtOffset(file, piece)
                    } else {
                        prioritize(file, piece)
                    }
                }
            }
        }
    }

    fun seek(file: Opened, position: Long) {
        if (!file.handle.isValid) return
        firstFrameArrived = true
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

        // DÜZELTME 2: servable == 0 ise asla bitmemiş/doğrulanmamış bayt okuma, tekrar bekle
        var servable = 0
        var attempts = 0
        while (servable <= 0) {
            awaitData(file, position, minOf(max, TorrentBlockRangeRules.BLOCK_SIZE))

            servable = TorrentBlockRangeRules.maxServableLength(
                file.offset, position, max, file.pieceLength, file.size,
                { isPieceVerified(file, it) },
                { p, b -> isBlockFinished(p, b) },
            )
            if (servable <= 0) {
                if (active !== file || !file.handle.isValid) throw IOException("torrent closed")
                attempts++
                if (attempts >= 500) {
                    throw IOException("read timeout: no finished blocks available at $position")
                }
                Thread.sleep(POLL_MS_FAST)
            }
        }

        raf.seek(position)
        val n = raf.read(buffer, 0, servable)
        if (n > 0) {
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
        if (STARTUP_FOCUS_ENABLED && !firstFrameArrived) {
            return
        }
        val first = TorrentRules.pieceAt(file.offset, 0, file.pieceLength)
        val last = TorrentRules.pieceAt(file.offset, file.size - 1, file.pieceLength)

        val activeDeadlines = TorrentBlockRangeRules.activeDeadlinePieces(
            currentPiece = piece,
            firstPiece = first,
            lastPiece = last,
            windowAhead = 2,
            isPieceVerified = { isPieceVerified(file, it) },
        )

        // DÜZELTME 1: Eski sarmalardan kalan acil son tarihleri kaldır
        h.clearPieceDeadlines()

        for (ad in activeDeadlines) {
            h.piecePriority(ad.pieceIndex, Priority.TOP_PRIORITY)
            h.setPieceDeadline(ad.pieceIndex, ad.deadlineMs)
        }
    }

    private fun onBlockFinished(piece: Int, block: Int) {
        val set = finishedBlocks.computeIfAbsent(piece) { BitSet() }
        synchronized(set) { set.set(block) }
        if (!piece0Block0Logged && piece == 0 && block == 0) {
            piece0Block0Logged = true
            TorrentDiag.d("MILESTONE: piece0_first_block")
        }
    }

    // DÜZELTME 3: Disk I/O alert iş parçacığından arka plan yürütücüsüne taşındı
    private fun onPieceFinished(piece: Int) {
        verifiedPieces[piece] = true
        val currentActive = active
        if (currentActive != null) {
            background.execute {
                verifyServedBlocks(currentActive, piece)
            }
        }
    }

    private fun startOpeningStatsTracker(h: TorrentHandle) {
        if (!BuildConfig.DEBUG) return
        background.execute {
            val milestones = listOf(1, 3, 5, 10)
            val startMono = SystemClock.elapsedRealtime()
            for (sec in milestones) {
                val targetMs = startMono + sec * 1000L
                val sleepMs = targetMs - SystemClock.elapsedRealtime()
                if (sleepMs > 0) Thread.sleep(sleepMs)
                if (!h.isValid || active == null) break
                runCatching {
                    val st = h.status()
                    if (!firstPeerLogged && st.numPeers() > 0) {
                        firstPeerLogged = true
                        TorrentDiag.d("MILESTONE: first_peer_connected")
                    }
                    TorrentDiag.d("MILESTONE: stats at ${sec}s peers=${st.numPeers()} seeds=${st.numSeeds()} dlRate=${st.downloadPayloadRate() / 1024}KB/s inen=${st.totalPayloadDownload() / 1024}KB")
                }
            }
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
        TorrentDiag.d("motor retain: users=${users.get()}")
        synchronized(lock) {
            idleTask?.cancel(false)
            idleTask = null
        }
    }

    /**
     * Oynatıcı kapandı. Başka oynatıcı kalmadıysa açık torrent kapanır, dosyaları silinir ve oturum durur. Arka planda yapılır;
     * o arada yeni bir oynatıcı [retain] ettiyse hiçbir şey kapanmaz.
     */
    fun release() {
        val rem = users.decrementAndGet()
        TorrentDiag.d("motor release: remainingUsers=$rem, keepAlive=$KEEP_ALIVE_ENABLED, idleTimeout=${IDLE_TIMEOUT_MS}ms")
        if (rem > 0) return
        if (KEEP_ALIVE_ENABLED) {
            synchronized(lock) {
                idleTask?.cancel(false)
                idleTask = idleExecutor.schedule({
                    background.execute {
                        synchronized(lock) {
                            if (users.get() > 0) return@synchronized
                            TorrentDiag.d("motor: 3 dk boşta kaldı, oturum ve dosyalar temizleniyor")
                            saveSessionState()
                            val s = session
                            if (s != null) {
                                active?.let { removeLocked(s, it) }
                                runCatching { s.stop() }
                                session = null
                            }
                            dir.deleteRecursively()
                        }
                    }
                }, IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
            return
        }
        background.execute {
            synchronized(lock) {
                if (users.get() > 0) return@synchronized
                saveSessionState()
                val s = session ?: return@synchronized
                active?.let { removeLocked(s, it) }
                runCatching { s.stop() }
                session = null
                dir.deleteRecursively()
            }
        }
    }

    private fun saveSessionState() {
        if (!SESSION_STATE_ENABLED) return
        val s = session ?: return
        val targetDir = stateDir ?: return
        try {
            val bytes = s.saveState()
            if (bytes != null && bytes.isNotEmpty()) {
                targetDir.mkdirs()
                val tmp = File(targetDir, "session.dat.tmp")
                val finalFile = File(targetDir, "session.dat")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(finalFile)) {
                    tmp.copyTo(finalFile, overwrite = true)
                    tmp.delete()
                }
                TorrentDiag.d("DHT oturum durumu kaydedildi (${bytes.size} B)")
            }
        } catch (e: Throwable) {
            TorrentDiag.e("DHT oturum durumu kaydedilemedi: ${e.message}", e)
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
        if (TorrentSupport.isEngineUnavailable || android.os.Build.VERSION.SDK_INT in 1..27) {
            throw TorrentException(TorrentException.Reason.Engine, "engine unavailable")
        }
        // Önceki oturumdan (ör. uygulama öldürüldüyse) kalanlar silinir.
        dir.deleteRecursively()
        dir.mkdirs()
        return try {
            if (TorrentSupport.debugForceFailNative) {
                throw UnsatisfiedLinkError("simulated native failure (aligned_alloc)")
            }
            val settings = SettingsPack()
                .connectionsLimit(CONNECTIONS)
                .activeDownloads(2)
                .apply {
                    setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
                    setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
                    if (TUNED_SETTINGS_ENABLED) {
                        setInteger(settings_pack.int_types.torrent_connect_boost.swigValue(), 60)
                        setInteger(settings_pack.int_types.connection_speed.swigValue(), 100)
                        setBoolean(settings_pack.bool_types.smooth_connects.swigValue(), false)
                        setInteger(settings_pack.int_types.peer_connect_timeout.swigValue(), 5)
                        setInteger(settings_pack.int_types.handshake_timeout.swigValue(), 5)
                        setInteger(settings_pack.int_types.request_timeout.swigValue(), 8)
                        setInteger(settings_pack.int_types.piece_timeout.swigValue(), 8)
                        setInteger(settings_pack.int_types.min_reconnect_time.swigValue(), 5)
                    }
                }
            val stateFile = if (SESSION_STATE_ENABLED && stateDir != null) File(stateDir, "session.dat") else null
            val params = if (stateFile != null && stateFile.exists() && stateFile.length() > 0) {
                try {
                    val bytes = stateFile.readBytes()
                    TorrentDiag.d("DHT oturum durumu yükleniyor (${bytes.size} B)")
                    SessionParams(bytes).apply {
                        setPosixDiskIO()
                        setSettings(settings)
                    }
                } catch (e: Throwable) {
                    TorrentDiag.e("DHT durumu yüklenemedi, sıfırdan başlanıyor", e)
                    SessionParams(settings).apply { setPosixDiskIO() }
                }
            } else {
                SessionParams(settings).apply { setPosixDiskIO() }
            }
            TorrentDiag.d("libtorrent oturumu başlıyor (ABI=${android.os.Build.SUPPORTED_ABIS.joinToString()}, dizin=$dir, blokModu=$BLOCK_MODE_ENABLED, odak=$STARTUP_FOCUS_ENABLED, hafıza=$SESSION_STATE_ENABLED, agresif=$TUNED_SETTINGS_ENABLED)")
            SessionManager(false).also {
                it.addListener(object : AlertListener {
                    override fun types(): IntArray? = null
                    override fun alert(alert: Alert<*>) {
                        when (alert) {
                            is BlockFinishedAlert -> onBlockFinished(alert.pieceIndex(), alert.blockIndex())
                            is PieceFinishedAlert -> onPieceFinished(alert.pieceIndex())
                            else -> {
                                val type = alert.type().name
                                if (!firstPeerLogged && type.startsWith("PEER_CONNECT")) {
                                    firstPeerLogged = true
                                    TorrentDiag.d("MILESTONE: first_peer_connected")
                                }
                                if (NOISY_ALERTS.any { type.startsWith(it) }) return
                                TorrentDiag.d("alert $type: ${runCatching { alert.message() }.getOrNull()}")
                            }
                        }
                    }
                })
                it.start(params)
                session = it
                TorrentDiag.d("MILESTONE: session_ready")
                TorrentDiag.d("libtorrent oturumu başladı")
            }
        } catch (e: Throwable) {
            TorrentSupport.markUnavailable()
            // Yerel kütüphane bu işlemcide yoksa (UnsatisfiedLinkError) ya da başlatılamazsa.
            TorrentDiag.e("libtorrent oturumu başlatılamadı", e)
            Log.w(TAG, "libtorrent session failed", e)
            throw TorrentException(TorrentException.Reason.Engine, "engine unavailable", e)
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

        /** Açılış odağı: ilk kareye kadar yalnızca parça 0 ve son parça top öncelik alır. */
        @JvmField @Volatile var STARTUP_FOCUS_ENABLED: Boolean = true

        /** DHT / oturum durumunu dosyaya kaydedip açılışta yükleme. */
        @JvmField @Volatile var SESSION_STATE_ENABLED: Boolean = true

        /** Son oynatıcı kapanınca oturumu 3 dk boyunca açık tutma. */
        @JvmField @Volatile var KEEP_ALIVE_ENABLED: Boolean = true

        /** Bağlantı agresifliği ayarları (hızlı eş bağlantısı). */
        @JvmField @Volatile var TUNED_SETTINGS_ENABLED: Boolean = true

        /** Boşta bekleme süresi (varsayılan 3 dakika). */
        @JvmField @Volatile var IDLE_TIMEOUT_MS: Long = 180_000L

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
