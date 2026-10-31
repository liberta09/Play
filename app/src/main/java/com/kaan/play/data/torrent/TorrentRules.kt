package com.kaan.play.data.torrent

import java.util.Locale

/** Yerleşik torrent motorunun Android'den ve libtorrent'ten bağımsız kuralları (birim testli). */
object TorrentRules {

    /** Torrent içindeki tek dosya; [pad] libtorrent'in hizalama dolgusu (oynatılmaz). */
    data class FileEntry(val name: String, val size: Long, val pad: Boolean = false)

    private val VIDEO = setOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts", "m2ts", "wmv", "flv", "mpg", "mpeg")

    fun isVideo(name: String): Boolean = extension(name) in VIDEO

    /**
     * Oynatılacak dosya: eklentinin verdiği [hint] geçerliyse o; yoksa en büyük video dosyası; video yoksa en büyük dosya.
     * Hiç dosya yoksa null.
     */
    fun pickFile(files: List<FileEntry>, hint: Int?, filenameHint: String? = null): Int? {
        val usable = files.indices.filter { !files[it].pad && files[it].size > 0 }
        if (hint != null && hint in usable) return hint
        if (!filenameHint.isNullOrBlank()) {
            val target = filenameHint.trim().lowercase(Locale.ROOT)
            val match = usable.firstOrNull { files[it].name.substringAfterLast('/').lowercase(Locale.ROOT) == target }
                ?: usable.firstOrNull { files[it].name.lowercase(Locale.ROOT).endsWith(target) }
            if (match != null) return match
        }
        return usable.filter { isVideo(files[it].name) }.maxByOrNull { files[it].size }
            ?: usable.maxByOrNull { files[it].size }
    }

    /** Dosyanın [position] baytının bulunduğu parça. [fileOffset]: dosyanın torrent içindeki başlangıcı. */
    fun pieceAt(fileOffset: Long, position: Long, pieceLength: Int): Int = ((fileOffset + position) / pieceLength).toInt()

    /** [position]'dan başlayan parçanın sonuna (ya da dosya sonuna) kadar kalan bayt. */
    fun bytesLeftInPiece(fileOffset: Long, position: Long, pieceLength: Int, fileSize: Long): Long {
        val absolute = fileOffset + position
        val pieceEnd = (absolute / pieceLength + 1) * pieceLength
        return minOf(pieceEnd - absolute, fileSize - position)
    }

    /** Okuma konumunun önünde öncelik verilecek parça sayısı: ~16 MB, en az 4, en çok 48 parça. */
    fun readAheadPieces(pieceLength: Int): Int = (READ_AHEAD_BYTES / pieceLength.coerceAtLeast(1)).toInt().coerceIn(4, 48)

    private const val READ_AHEAD_BYTES = 16L * 1024 * 1024

    sealed interface Range {
        /** Range başlığı yok: tüm dosya (200). */
        data object Full : Range
        /** [start]..[end] (ikisi dahil; 206). */
        data class Part(val start: Long, val end: Long) : Range
        /** Karşılanamaz (416). */
        data object Unsatisfiable : Range
    }

    /** `bytes=a-`, `bytes=a-b`, `bytes=-n` (tek aralık). Bozuk/çoklu başlık yok sayılır (tüm dosya). */
    fun parseRange(header: String?, length: Long): Range {
        val spec = header?.trim()?.takeIf { it.startsWith("bytes=", ignoreCase = true) }?.substring(6)?.trim() ?: return Range.Full
        if (',' in spec || '-' !in spec) return Range.Full
        val first = spec.substringBefore('-').trim()
        val last = spec.substringAfter('-').trim()
        if (length <= 0) return Range.Unsatisfiable
        return if (first.isEmpty()) {
            val n = last.toLongOrNull() ?: return Range.Full
            if (n <= 0) Range.Unsatisfiable else Range.Part(maxOf(0, length - n), length - 1)
        } else {
            val start = first.toLongOrNull() ?: return Range.Full
            val end = if (last.isEmpty()) length - 1 else (last.toLongOrNull() ?: return Range.Full).coerceAtMost(length - 1)
            if (start >= length || end < start) Range.Unsatisfiable else Range.Part(start, end)
        }
    }

    /** Yerel sunucunun `Content-Type` başlığı (dosya adından). */
    fun contentType(name: String): String = when (extension(name)) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }

    private fun extension(name: String): String = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}
