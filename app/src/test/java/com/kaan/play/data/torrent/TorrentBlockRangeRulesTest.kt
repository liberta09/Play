package com.kaan.play.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentBlockRangeRulesTest {

    private val pieceLength8M = 8 * 1024 * 1024 // 8 MiB = 8388608 bayt = 512 blok
    private val fileSize10G = 10L * 1024 * 1024 * 1024 // 10 GB

    @Test
    fun testRequiredBlocksSingleBlockAtStart() {
        val blocks = TorrentBlockRangeRules.requiredBlocks(
            fileOffset = 0L,
            position = 0L,
            length = 16384,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
        )
        assertEquals(1, blocks.size)
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 0), blocks[0])
    }

    @Test
    fun testRequiredBlocksMultipleBlocksInSamePiece() {
        val blocks = TorrentBlockRangeRules.requiredBlocks(
            fileOffset = 0L,
            position = 0L,
            length = 65536,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
        )
        assertEquals(4, blocks.size)
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 0), blocks[0])
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 1), blocks[1])
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 2), blocks[2])
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 3), blocks[3])
    }

    @Test
    fun testRequiredBlocksCrossingPieceBoundary() {
        val pos = pieceLength8M.toLong() - 10L
        val blocks = TorrentBlockRangeRules.requiredBlocks(
            fileOffset = 0L,
            position = pos,
            length = 30,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
        )
        assertEquals(2, blocks.size)
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 511), blocks[0])
        assertEquals(TorrentBlockRangeRules.BlockCoord(1, 0), blocks[1])
    }

    @Test
    fun testRequiredBlocksWithFileOffset() {
        val fileOffset = 100_000L
        val blocks = TorrentBlockRangeRules.requiredBlocks(
            fileOffset = fileOffset,
            position = 0L,
            length = 16384,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
        )
        assertEquals(2, blocks.size)
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 6), blocks[0])
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 7), blocks[1])
    }

    @Test
    fun testIsRangeServableWhenAllBlocksReady() {
        val blocks = listOf(
            TorrentBlockRangeRules.BlockCoord(0, 0),
            TorrentBlockRangeRules.BlockCoord(0, 1),
        )
        val readyBlocks = setOf("0:0", "0:1")
        val servable = TorrentBlockRangeRules.isRangeServable(
            blocks = blocks,
            isPieceVerified = { false },
            isBlockFinished = { p, b -> "$p:$b" in readyBlocks },
        )
        assertTrue(servable)
    }

    @Test
    fun testIsRangeServableWhenOneBlockMissing() {
        val blocks = listOf(
            TorrentBlockRangeRules.BlockCoord(0, 0),
            TorrentBlockRangeRules.BlockCoord(0, 1),
        )
        val readyBlocks = setOf("0:0") // 0:1 eksik
        val servable = TorrentBlockRangeRules.isRangeServable(
            blocks = blocks,
            isPieceVerified = { false },
            isBlockFinished = { p, b -> "$p:$b" in readyBlocks },
        )
        assertFalse(servable)

        val missing = TorrentBlockRangeRules.missingBlocks(
            blocks = blocks,
            isPieceVerified = { false },
            isBlockFinished = { p, b -> "$p:$b" in readyBlocks },
        )
        assertEquals(1, missing.size)
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 1), missing[0])
    }

    @Test
    fun testIsRangeServableWhenPieceIsVerified() {
        val blocks = listOf(
            TorrentBlockRangeRules.BlockCoord(0, 0),
            TorrentBlockRangeRules.BlockCoord(0, 1),
        )
        val servable = TorrentBlockRangeRules.isRangeServable(
            blocks = blocks,
            isPieceVerified = { p -> p == 0 },
            isBlockFinished = { _, _ -> false },
        )
        assertTrue(servable)
    }

    @Test
    fun testMaxServableLengthContiguous() {
        val readyBlocks = setOf("0:0", "0:1")
        val servableLen = TorrentBlockRangeRules.maxServableLength(
            fileOffset = 0L,
            position = 0L,
            maxBytes = 65536,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
            isPieceVerified = { false },
            isBlockFinished = { p, b -> "$p:$b" in readyBlocks },
        )
        assertEquals(32768, servableLen)
    }

    @Test
    fun testMaxServableLengthFirstBlockMissing_returnsZero() {
        val readyBlocks = setOf("0:1", "0:2") // Blok 0 eksik!
        val servableLen = TorrentBlockRangeRules.maxServableLength(
            fileOffset = 0L,
            position = 0L,
            maxBytes = 65536,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
            isPieceVerified = { false },
            isBlockFinished = { p, b -> "$p:$b" in readyBlocks },
        )
        assertEquals(0, servableLen)
    }

    @Test
    fun testBlocksInPiece() {
        assertEquals(512, TorrentBlockRangeRules.blocksInPiece(8 * 1024 * 1024))
        assertEquals(128, TorrentBlockRangeRules.blocksInPiece(2 * 1024 * 1024))
        assertEquals(1, TorrentBlockRangeRules.blocksInPiece(1024))
    }

    // --- DÜZELTME 1 BİRİM TESTLERİ (activeDeadlinePieces) ---

    @Test
    fun testActiveDeadlinePieces_normalSeekRemovesOldPositions() {
        // Kullanıcı 600. parçaya sarıyor.
        // Baş (0, 1), kuyruk (999, 1000) ve mevcut (600, 601) parçalar kalmalı;
        // Eski sarılan parça 150 kesinlikle bulunmamalı!
        val deadlines = TorrentBlockRangeRules.activeDeadlinePieces(
            currentPiece = 600,
            firstPiece = 0,
            lastPiece = 1000,
            windowAhead = 2,
        )
        val pieces = deadlines.map { it.pieceIndex }
        assertEquals(listOf(600, 601, 0, 1, 999, 1000), pieces)
        assertFalse(pieces.contains(150))

        // Öncelik süreleri kontrolü: mevcut konum en acil olmalı
        assertEquals(150, deadlines.first { it.pieceIndex == 600 }.deadlineMs)
        assertEquals(350, deadlines.first { it.pieceIndex == 601 }.deadlineMs)
        assertEquals(500, deadlines.first { it.pieceIndex == 0 }.deadlineMs)
        assertEquals(750, deadlines.first { it.pieceIndex == 1 }.deadlineMs)
        assertEquals(1000, deadlines.first { it.pieceIndex == 999 }.deadlineMs)
        assertEquals(1250, deadlines.first { it.pieceIndex == 1000 }.deadlineMs)
    }

    @Test
    fun testActiveDeadlinePieces_nearHead_noDuplicates() {
        // Mevcut konum baş parça (0) olduğunda parça 0 ve 1 iki kez eklenmemeli
        val deadlines = TorrentBlockRangeRules.activeDeadlinePieces(
            currentPiece = 0,
            firstPiece = 0,
            lastPiece = 1000,
            windowAhead = 2,
        )
        val pieces = deadlines.map { it.pieceIndex }
        assertEquals(listOf(0, 1, 999, 1000), pieces)
    }

    @Test
    fun testActiveDeadlinePieces_nearTail_noDuplicates() {
        // Mevcut konum kuyruk parça (1000) olduğunda kuyruk parçaları tekilleştirilmeli
        val deadlines = TorrentBlockRangeRules.activeDeadlinePieces(
            currentPiece = 999,
            firstPiece = 0,
            lastPiece = 1000,
            windowAhead = 2,
        )
        val pieces = deadlines.map { it.pieceIndex }
        assertEquals(listOf(999, 1000, 0, 1), pieces)
    }

    @Test
    fun testActiveDeadlinePieces_verifiedPiecesExcluded() {
        // Parça 0 ve parça 600 zaten inip doğrulanmışsa onlara deadline verilmemeli
        val deadlines = TorrentBlockRangeRules.activeDeadlinePieces(
            currentPiece = 600,
            firstPiece = 0,
            lastPiece = 1000,
            windowAhead = 2,
            isPieceVerified = { p -> p == 0 || p == 600 },
        )
        val pieces = deadlines.map { it.pieceIndex }
        assertEquals(listOf(601, 1, 999, 1000), pieces)
        assertFalse(pieces.contains(0))
        assertFalse(pieces.contains(600))
    }

    @Test
    fun testActiveDeadlinePieces_smallTorrent() {
        // Toplam 3 parçalı küçük torrent (0..2): çakışmalar düzgün yönetilmeli
        val deadlines = TorrentBlockRangeRules.activeDeadlinePieces(
            currentPiece = 1,
            firstPiece = 0,
            lastPiece = 2,
            windowAhead = 2,
        )
        val pieces = deadlines.map { it.pieceIndex }
        // 1, 2, 0 tekilleştirilmiş olmalı
        assertEquals(listOf(1, 2, 0), pieces)
    }
// --- TUR 3 BİRİM TESTLERİ (startupFocusPieces) ---

    @Test
    fun testStartupFocusPieces_normalCivilWar() {
        // Civil War FLUX (parça 0..2357):
        // İlk parça (0) ve son parça (2357) seçilmeli (İlk parça başta);
        // Parça 1 ve son-1 (2356) KESİNLİKLE yer almamalıdır.
        val deadlines = TorrentBlockRangeRules.startupFocusPieces(
            firstPiece = 0,
            lastPiece = 2357,
        )
        val pieces = deadlines.map { it.pieceIndex }
        assertEquals(listOf(0, 2357), pieces)
        assertFalse(pieces.contains(1))
        assertFalse(pieces.contains(2356))

        // İlk parçaya en kısa deadline (150ms), son parçaya ikinci (350ms)
        assertEquals(150, deadlines.first { it.pieceIndex == 0 }.deadlineMs)
        assertEquals(350, deadlines.first { it.pieceIndex == 2357 }.deadlineMs)
    }

    @Test
    fun testStartupFocusPieces_singlePieceTorrent() {
        // Tek parçalı torrent (0..0): iki kez eklenmemeli, tek parça 150ms almalı
        val deadlines = TorrentBlockRangeRules.startupFocusPieces(
            firstPiece = 0,
            lastPiece = 0,
        )
        val pieces = deadlines.map { it.pieceIndex }
        assertEquals(listOf(0), pieces)
        assertEquals(150, deadlines.first().deadlineMs)
    }

    @Test
    fun testStartupFocusPieces_verifiedPieceExcluded() {
        // İlk parça zaten inip doğrulanmışsa yalnızca son parça kalmalı
        val deadlines = TorrentBlockRangeRules.startupFocusPieces(
            firstPiece = 0,
            lastPiece = 2357,
            isPieceVerified = { p -> p == 0 },
        )
        val pieces = deadlines.map { it.pieceIndex }
        assertEquals(listOf(2357), pieces)
        assertEquals(350, deadlines.first().deadlineMs)
    }

    @Test
    fun testStartupFocusPieces_invalidRange_returnsEmpty() {
        val deadlines = TorrentBlockRangeRules.startupFocusPieces(
            firstPiece = 10,
            lastPiece = 5,
        )
        assertTrue(deadlines.isEmpty())
    }
}
