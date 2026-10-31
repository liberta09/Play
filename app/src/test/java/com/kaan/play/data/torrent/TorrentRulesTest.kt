package com.kaan.play.data.torrent

import com.kaan.play.data.torrent.TorrentRules.FileEntry
import com.kaan.play.data.torrent.TorrentRules.Range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TorrentRulesTest {

    private val files = listOf(
        FileEntry("Movie/sample.mkv", 50_000_000),
        FileEntry("Movie/readme.txt", 2_000),
        FileEntry(".pad/1", 900_000_000, pad = true),
        FileEntry("Movie/Movie.2024.1080p.mkv", 4_000_000_000),
        FileEntry("Movie/extras.zip", 6_000_000_000),
    )

    @Test
    fun hintIsUsedWhenItPointsToARealFile() {
        assertEquals(0, TorrentRules.pickFile(files, 0))
        assertEquals(1, TorrentRules.pickFile(files, 1))
    }

    @Test
    fun filenameHintIsUsedWhenHintIsAbsent() {
        assertEquals(0, TorrentRules.pickFile(files, null, "sample.mkv"))
        assertEquals(3, TorrentRules.pickFile(files, null, "Movie.2024.1080p.mkv"))
        assertEquals(1, TorrentRules.pickFile(files, null, "readme.txt"))
        assertEquals(3, TorrentRules.pickFile(files, null, "nonexistent.mkv"))
    }

    @Test
    fun largestVideoIsPickedWithoutAValidHint() {
        assertEquals(3, TorrentRules.pickFile(files, null))
        assertEquals(3, TorrentRules.pickFile(files, 2)) // dolgu dosyası
        assertEquals(3, TorrentRules.pickFile(files, 99))
        assertEquals(3, TorrentRules.pickFile(files, -1))
    }

    @Test
    fun largestFileIsPickedWhenThereIsNoVideo() {
        assertEquals(1, TorrentRules.pickFile(listOf(FileEntry("a.txt", 1), FileEntry("b.bin", 9)), null))
        assertNull(TorrentRules.pickFile(listOf(FileEntry("empty.mkv", 0)), null))
        assertNull(TorrentRules.pickFile(emptyList(), null))
    }

    @Test
    fun pieceMathAccountsForTheFileOffset() {
        val piece = 1_048_576
        assertEquals(0, TorrentRules.pieceAt(0, 0, piece))
        assertEquals(2, TorrentRules.pieceAt(piece * 2L - 10, 10, piece))
        // Dosya parçanın ortasında başlıyor: ilk okumada parçanın sonuna kadar kalan.
        assertEquals(10L, TorrentRules.bytesLeftInPiece(piece - 10L, 0, piece, 5_000_000))
        // Dosya sonu parçadan önce geliyor.
        assertEquals(100L, TorrentRules.bytesLeftInPiece(0, 900, piece, 1_000))
    }

    @Test
    fun readAheadIsAboutSixteenMegabytesWithinBounds() {
        assertEquals(16, TorrentRules.readAheadPieces(1_048_576))
        assertEquals(4, TorrentRules.readAheadPieces(16 * 1_048_576))
        assertEquals(48, TorrentRules.readAheadPieces(16_384))
    }

    @Test
    fun rangeHeaderIsParsed() {
        assertEquals(Range.Full, TorrentRules.parseRange(null, 1000))
        assertEquals(Range.Part(0, 999), TorrentRules.parseRange("bytes=0-", 1000))
        assertEquals(Range.Part(100, 199), TorrentRules.parseRange("bytes=100-199", 1000))
        assertEquals(Range.Part(100, 999), TorrentRules.parseRange("bytes=100-5000", 1000))
        assertEquals(Range.Part(900, 999), TorrentRules.parseRange("bytes=-100", 1000))
        assertEquals(Range.Part(0, 999), TorrentRules.parseRange("bytes=-5000", 1000))
        assertEquals(Range.Unsatisfiable, TorrentRules.parseRange("bytes=1000-", 1000))
        assertEquals(Range.Unsatisfiable, TorrentRules.parseRange("bytes=500-100", 1000))
        assertEquals(Range.Full, TorrentRules.parseRange("bytes=0-1,5-9", 1000))
        assertEquals(Range.Full, TorrentRules.parseRange("items=0-1", 1000))
        assertEquals(Range.Full, TorrentRules.parseRange("bytes=x-", 1000))
    }

    @Test
    fun contentTypeFollowsTheExtension() {
        assertEquals("video/x-matroska", TorrentRules.contentType("A.MKV"))
        assertEquals("video/mp4", TorrentRules.contentType("a.mp4"))
        assertEquals("application/octet-stream", TorrentRules.contentType("a"))
    }
}
