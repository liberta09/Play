package com.kaan.play.data.subtitle

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Altyazı sitelerinin (SubDL …) ve eklentilerin verdiği zip paketinden altyazı dosyasını çıkarır (yalnızca bellekte; diske zip yazılmaz).
 * `.srt`, `.vtt`, `.ass`, `.ssa` kabul edilir (bu sırayla tercih; `.sub`/`.idx` ayrıştırılamaz, atlanır).
 *
 * Zip bombasına karşı girdi sayısı ve her girdinin açılmış boyutu sınırlıdır.
 * Birden çok uygun dosya varsa:
 * 1. Bölüm eşleşmesi (S01E21 / 1x21)
 * 2. Bölüm numarası eşleşmesi (E21 / EP21)
 * 3. Bağımsız bölüm numarası eşleşmesi (21)
 * 4. Hiçbiri uymazsa veya filmse en büyük `.srt` (yoksa en büyük geçerli altyazı).
 */
object SubtitleZip {
    internal val EXTENSIONS = listOf("srt", "vtt", "ass", "ssa")
    const val MAX_ENTRIES = 128
    const val MAX_ZIP_BYTES = 25L * 1024 * 1024 // 25 MB
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 10L * 1024 * 1024 // 10 MB

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2].toInt() == 3 && bytes[3].toInt() == 4

    internal data class Candidate(
        val name: String,
        val ext: String,
        val bytes: ByteArray,
    )

    /**
     * Uygun girdinin açılmış baytları; yoksa, bozuksa ya da sınırları aşıyorsa null.
     */
    fun extract(
        bytes: ByteArray,
        maxEntryBytes: Long,
        season: Int? = null,
        episode: Int? = null,
    ): ByteArray? {
        if (!isZip(bytes) || bytes.size > MAX_ZIP_BYTES) return null
        val candidates = ArrayList<Candidate>()
        var totalUncompressed = 0L

        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var count = 0
                while (count++ < MAX_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val rawName = entry.name
                    val name = rawName.substringAfterLast('/')
                    if (rawName.startsWith("__MACOSX") || name.startsWith("._")) continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in EXTENSIONS) continue

                    val remainingBudget = minOf(maxEntryBytes, MAX_TOTAL_UNCOMPRESSED_BYTES - totalUncompressed)
                    if (remainingBudget <= 0) break

                    val data = readLimited(zip, remainingBudget) ?: continue
                    totalUncompressed += data.size
                    candidates.add(Candidate(name, ext, data))
                }
            }
        } catch (_: IOException) {
            // Bozuk arşiv: o ana kadar çıkarılabilen adaylarla devam et
        } catch (_: IllegalArgumentException) {
            // Geçersiz girdi adı
        }

        return selectBestCandidate(candidates, season, episode)
    }

    internal fun selectBestCandidate(
        candidates: List<Candidate>,
        season: Int?,
        episode: Int?,
    ): ByteArray? {
        if (candidates.isEmpty()) return null

        if (episode != null) {
            val rank1 = candidates.filter { matchRank1(it.name, season, episode) }
            if (rank1.isNotEmpty()) return pickBestFromPool(rank1)

            val rank2 = candidates.filter { matchRank2(it.name, season, episode) }
            if (rank2.isNotEmpty()) return pickBestFromPool(rank2)

            val rank3 = candidates.filter { matchRank3(it.name, season, episode) }
            if (rank3.isNotEmpty()) return pickBestFromPool(rank3)
        }

        // Fallback: yoksa en büyük .srt, yoksa en büyük diğer uzantı
        val srtCandidates = candidates.filter { it.ext == "srt" }
        return if (srtCandidates.isNotEmpty()) {
            pickBestFromPool(srtCandidates)
        } else {
            pickBestFromPool(candidates)
        }
    }

    private fun pickBestFromPool(pool: List<Candidate>): ByteArray {
        return pool.maxWithOrNull(
            compareBy<Candidate> { -EXTENSIONS.indexOf(it.ext).let { idx -> if (idx > 0) -99 else idx } }
                .thenBy { it.bytes.size }
        )!!.bytes
    }

    /**
     * Rank 1: S01E21 ya da 1x21 eşleşmesi
     */
    internal fun matchRank1(name: String, season: Int?, episode: Int): Boolean {
        if (season == null) {
            // Sezon bilinmiyorsa S..E21 tam kalıbı
            val pattern = Regex("""(?i)(?:^|[^a-zA-Z0-9])s\d{1,2}[._ -]?e0*$episode(?:[^a-zA-Z0-9]|$)""")
            return pattern.containsMatchIn(name)
        }
        val sAndE = Regex("""(?i)(?:^|[^a-zA-Z0-9])s0*$season[._ -]?e0*$episode(?:[^a-zA-Z0-9]|$)""")
        if (sAndE.containsMatchIn(name)) return true

        val sXx = Regex("""(?i)(?:^|[^a-zA-Z0-9])0*$season[._ -]?x[._ -]?0*$episode(?:[^a-zA-Z0-9]|$)""")
        return sXx.containsMatchIn(name)
    }

    /**
     * Rank 2: E21 ya da EP21 eşleşmesi (çelişen başka bir sezon yoksa)
     */
    internal fun matchRank2(name: String, season: Int?, episode: Int): Boolean {
        if (season != null && hasConflictingSeason(name, season)) return false
        val epPattern = Regex("""(?i)(?:^|[^a-zA-Z0-9])(?:e|ep|episode)[._ -]?0*$episode(?:[^a-zA-Z0-9]|$)""")
        return epPattern.containsMatchIn(name)
    }

    /**
     * Rank 3: Bağımsız 21 sayısı (yıl ya da çözünürlük olmayan, çelişen sezon içermeyen)
     */
    internal fun matchRank3(name: String, season: Int?, episode: Int): Boolean {
        if (season != null && hasConflictingSeason(name, season)) return false
        val matches = Regex("""(?:^|[^a-zA-Z0-9])0*($episode)(?:[^a-zA-Z0-9]|$)""").findAll(name)
        for (m in matches) {
            val numStr = m.groupValues[1]
            val num = numStr.toIntOrNull() ?: continue
            if (num == episode) {
                // Yıl kontrolü: 1900..2099 aralığında bağımsız 4 basamaklı yıl parçasının sonu olmasın
                val start = m.range.first
                val prefix = name.substring(maxOf(0, start - 4), start)
                if (Regex("""\b(19|20)$""").containsMatchIn(prefix)) continue
                return true
            }
        }
        return false
    }

    internal fun hasConflictingSeason(name: String, targetSeason: Int): Boolean {
        val matches = Regex("""(?i)(?:^|[^a-zA-Z0-9])(?:s0*(\d{1,2})|0*(\d{1,2})x)""").findAll(name)
        for (m in matches) {
            val sNum = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
            if (sNum != null && sNum != targetSeason) return true
        }
        return false
    }

    private fun readLimited(zip: ZipInputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
