package com.kaan.play.data.torrent

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.kaan.play.data.stream.TorrentLinks
import org.libtorrent4j.AlertListener
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionHandle
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.BlockFinishedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.swig.settings_pack
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/**
 * ADIM 0: libtorrent4j 2.1.0-39 blok düzeyi fizibilite deneyi.
 *
 * İncelenen unsurlar:
 * 1. TorrentHandle.getDownloadQueue() (PartialPieceInfo, blok durumları)
 * 2. BlockFinishedAlert (block_finished_alert)
 * 3. torrent_status parça bit alanı (status(QUERY_PIECES).pieces())
 * 4. setPosixDiskIO() ile biten blokların dosyada henüz doğrulanmamış parçada HEMEN OKUNABİLİR olması
 */
object TorrentBlockFeasibility {

    private const val TAG = "ADIM0_FEASIBILITY"
    const val CIVIL_WAR_MAGNET = "magnet:?xt=urn:btih:cf5290d3f602bbeb0de9cb4419251c98c0e6db70"

    fun runExperiment(context: Context) {
        Thread {
            try {
                Log.i(TAG, "=== ADIM 0 DENEYİ BAŞLIYOR ===")
                val resultJson = JSONObject()
                val testDir = File(context.cacheDir, "feasibility_test_${System.currentTimeMillis()}").apply {
                    deleteRecursively()
                    mkdirs()
                }

                val settings = SettingsPack()
                    .connectionsLimit(200)
                    .activeDownloads(2)
                    .apply {
                        setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
                        setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
                    }

                // setPosixDiskIO() ile oturum parametresi oluştur
                val params = SessionParams(settings).apply { setPosixDiskIO() }
                val session = SessionManager(false)

                val blockResults = JSONArray()
                val queueSnapshots = JSONArray()
                val unverifiedBlockCrcMap = ConcurrentHashMap<String, Long>()
                val verifiedBlockCrcMap = ConcurrentHashMap<String, Long>()
                val pieceFinishedLatch = CountDownLatch(1)

                var torrentHandle: TorrentHandle? = null
                var fileOffset = 0L
                var fileSize = 0L
                var pieceLength = 0
                var targetFile: File? = null
                var isMetadataReady = false

                session.addListener(object : AlertListener {
                    override fun types(): IntArray? = null

                    override fun alert(alert: Alert<*>) {
                        val handle = torrentHandle ?: return
                        if (!isMetadataReady) return

                        when (alert) {
                            is BlockFinishedAlert -> {
                                val p = alert.pieceIndex()
                                val b = alert.blockIndex()
                                val key = "$p:$b"

                                val havePiece = runCatching { handle.havePiece(p) }.getOrDefault(false)
                                val queue = runCatching { handle.downloadQueue }.getOrNull()
                                val partialInfo = queue?.firstOrNull { it.pieceIndex() == p }

                                // Kuyruk durumu kaydet
                                if (queueSnapshots.length() < 10 && partialInfo != null) {
                                    queueSnapshots.put(JSONObject().apply {
                                        put("pieceIndex", partialInfo.pieceIndex())
                                        put("blocksInPiece", partialInfo.blocksInPiece())
                                        put("finished", partialInfo.finished())
                                        put("writing", partialInfo.writing())
                                        put("requested", partialInfo.requested())
                                    })
                                }

                                val tFile = targetFile
                                if (tFile != null && tFile.exists() && !havePiece) {
                                    val blockAbsOffset = p.toLong() * pieceLength + b.toLong() * 16384L
                                    val fileBlockOffset = blockAbsOffset - fileOffset
                                    if (fileBlockOffset >= 0 && fileBlockOffset + 16384L <= fileSize) {
                                        try {
                                            val buf = ByteArray(16384)
                                            RandomAccessFile(tFile, "r").use { raf ->
                                                raf.seek(fileBlockOffset)
                                                raf.readFully(buf)
                                            }
                                            val crc = CRC32().apply { update(buf) }.value
                                            val nonZero = buf.count { it != 0.toByte() }
                                            unverifiedBlockCrcMap[key] = crc

                                            val blockLog = JSONObject().apply {
                                                put("pieceIndex", p)
                                                put("blockIndex", b)
                                                put("havePiece", havePiece)
                                                put("fileBlockOffset", fileBlockOffset)
                                                put("nonZeroBytes", nonZero)
                                                put("crc32", crc)
                                                put("partialFinished", partialInfo?.finished() ?: -1)
                                                put("partialBlocksInPiece", partialInfo?.blocksInPiece() ?: -1)
                                            }
                                            blockResults.put(blockLog)
                                            Log.i(TAG, "BLOK_OKUNDU: parca=$p blok=$b havePiece=$havePiece ofset=$fileBlockOffset nonZero=$nonZero crc=$crc queueFin=${partialInfo?.finished()}")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Blok okuma hatası ($key): ${e.message}")
                                        }
                                    }
                                }
                            }

                            is PieceFinishedAlert -> {
                                val p = alert.pieceIndex()
                                val havePiece = runCatching { handle.havePiece(p) }.getOrDefault(false)
                                Log.i(TAG, "PARCA_DOGRULANDI: parca=$p havePiece=$havePiece")

                                val tFile = targetFile
                                if (tFile != null && tFile.exists()) {
                                    // Parçanın biten bloklarını diskten doğrulanmış halde tekrar oku ve karşılaştır
                                    for ((key, unverifiedCrc) in unverifiedBlockCrcMap) {
                                        val (pieceStr, blockStr) = key.split(":")
                                        if (pieceStr.toInt() == p) {
                                            val b = blockStr.toInt()
                                            val blockAbsOffset = p.toLong() * pieceLength + b.toLong() * 16384L
                                            val fileBlockOffset = blockAbsOffset - fileOffset
                                            try {
                                                val buf = ByteArray(16384)
                                                RandomAccessFile(tFile, "r").use { raf ->
                                                    raf.seek(fileBlockOffset)
                                                    raf.readFully(buf)
                                                }
                                                val verifiedCrc = CRC32().apply { update(buf) }.value
                                                verifiedBlockCrcMap[key] = verifiedCrc
                                                val match = (unverifiedCrc == verifiedCrc)
                                                Log.i(TAG, "CRC_KARSILASTIRMA: parca=$p blok=$b oncekiCrc=$unverifiedCrc dogrulanmisCrc=$verifiedCrc eslesiyor=$match")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Doğrulanmış parça blok okuma hatası ($key): ${e.message}")
                                            }
                                        }
                                    }
                                }
                                pieceFinishedLatch.countDown()
                            }
                        }
                    }
                })

                session.start(params)
                Log.i(TAG, "SessionManager başlatıldı (posix_disk_io)")

                val fullMagnet = TorrentLinks.appendTrackers(CIVIL_WAR_MAGNET, TorrentEngine.PUBLIC_TRACKERS)
                session.download(fullMagnet, testDir, TorrentFlags.SEQUENTIAL_DOWNLOAD)

                val sha1 = Sha1Hash.parseHex(TorrentLinks.hexHashOf(CIVIL_WAR_MAGNET)!!)
                var handle: TorrentHandle? = null
                val startWait = SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime() - startWait < 60_000L) {
                    val h = session.find(sha1)
                    if (h != null && h.isValid && h.status().hasMetadata()) {
                        handle = h
                        break
                    }
                    Thread.sleep(200)
                }

                if (handle == null) {
                    Log.e(TAG, "Metadata alınamadı, zaman aşımı!")
                    resultJson.put("success", false)
                    resultJson.put("reason", "metadata_timeout")
                    writeProofFile(context, resultJson)
                    return@Thread
                }

                torrentHandle = handle
                val info = handle.torrentFile()
                val files = info.files()
                val entries = (0 until files.numFiles()).map {
                    TorrentRules.FileEntry(files.fileName(it), files.fileSize(it), files.padFileAt(it))
                }
                val chosenIndex = TorrentRules.pickFile(entries, null) ?: 0
                fileOffset = files.fileOffset(chosenIndex)
                fileSize = files.fileSize(chosenIndex)
                pieceLength = info.pieceLength()
                targetFile = File(handle.savePath(), files.filePath(chosenIndex))
                isMetadataReady = true

                Log.i(TAG, "Metadata hazır: dosya=${files.fileName(chosenIndex)} boyut=$fileSize parcaUzunlugu=$pieceLength hedefDosya=${targetFile?.absolutePath}")

                // Yalnızca oynatılan dosyayı indir
                handle.prioritizeFiles(Array(files.numFiles()) { if (it == chosenIndex) Priority.DEFAULT else Priority.IGNORE })
                handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

                // Parça 0 ve Parça 1'e en yüksek önceliği ver
                handle.piecePriority(0, Priority.TOP_PRIORITY)
                handle.setPieceDeadline(0, 100)

                // Parça doğrulanana veya yeterli blok doğrulanana kadar bekle
                val waitStart = SystemClock.elapsedRealtime()
                while (SystemClock.elapsedRealtime() - waitStart < 45_000L) {
                    if (verifiedBlockCrcMap.size >= 5) {
                        Log.i(TAG, "En az ${verifiedBlockCrcMap.size} blok doğrulandı ve karşılaştırıldı!")
                        break
                    }
                    Thread.sleep(500)
                }

                // torrent_status pieces bitfield incelemesi (güvenli)
                val bitfieldSnapshot = JSONObject().apply {
                    runCatching {
                        val st = handle.status(TorrentHandle.QUERY_PIECES)
                        val pieces = st.pieces()
                        if (pieces != null) {
                            put("size", pieces.size())
                            put("count", pieces.count())
                            put("bit0", pieces.getBit(0))
                        }
                    }
                    put("havePiece0", runCatching { handle.havePiece(0) }.getOrDefault(false))
                }

                // Sonuçları toparla
                val comparisons = JSONArray()
                var allMatched = true
                for ((key, unverifiedCrc) in unverifiedBlockCrcMap) {
                    val verifiedCrc = verifiedBlockCrcMap[key]
                    val match = (verifiedCrc != null && unverifiedCrc == verifiedCrc)
                    if (verifiedCrc != null && !match) allMatched = false
                    if (verifiedCrc != null) {
                        comparisons.put(JSONObject().apply {
                            put("key", key)
                            put("unverifiedCrc", unverifiedCrc)
                            put("verifiedCrc", verifiedCrc)
                            put("match", match)
                        })
                    }
                }

                resultJson.put("success", unverifiedBlockCrcMap.isNotEmpty())
                resultJson.put("posixDiskIO", true)
                resultJson.put("unverifiedBlocksReadCount", unverifiedBlockCrcMap.size)
                resultJson.put("verifiedBlocksComparedCount", verifiedBlockCrcMap.size)
                resultJson.put("allCrcsMatched", allMatched && verifiedBlockCrcMap.isNotEmpty())
                resultJson.put("bitfield", bitfieldSnapshot)
                resultJson.put("downloadQueueSnapshots", queueSnapshots)
                resultJson.put("sampleBlocks", JSONArray().apply {
                    for (i in 0 until minOf(15, blockResults.length())) {
                        put(blockResults.getJSONObject(i))
                    }
                })
                resultJson.put("sampleComparisons", JSONArray().apply {
                    for (i in 0 until minOf(15, comparisons.length())) {
                        put(comparisons.getJSONObject(i))
                    }
                })

                writeProofFile(context, resultJson)
                Log.i(TAG, "=== ADIM 0 DENEYİ TAMAMLANDI: OKUNAN=${unverifiedBlockCrcMap.size}, DOĞRULANAN=${verifiedBlockCrcMap.size}, ESLESTI=$allMatched ===")

                runCatching {
                    session.remove(handle, SessionHandle.DELETE_FILES)
                    session.stop()
                    testDir.deleteRecursively()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Deney hatası: ${t.message}", t)
            }
        }.apply {
            name = "adim0-feasibility"
            start()
        }
    }

    private fun writeProofFile(context: Context, json: JSONObject) {
        try {
            val file = File(context.filesDir, "adim0_proof.json")
            file.writeText(json.toString(2))
            Log.i(TAG, "Fizibilite kanıt dosyası yazıldı: ${file.absolutePath} (${file.length()} bayt)")
        } catch (e: Exception) {
            Log.e(TAG, "Kanıt dosyası yazılamadı: ${e.message}")
        }
    }
}
