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
        // 64 KiB = 4 blok (blok 0..3)
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
        // Parça 0'ın son 10 baytı + Parça 1'in ilk 20 baytı (toplam 30 bayt)
        val pos = pieceLength8M.toLong() - 10L
        val blocks = TorrentBlockRangeRules.requiredBlocks(
            fileOffset = 0L,
            position = pos,
            length = 30,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
        )
        // Parça 0'ın son bloğu (blok 511) ve Parça 1'in ilk bloğu (blok 0)
        assertEquals(2, blocks.size)
        assertEquals(TorrentBlockRangeRules.BlockCoord(0, 511), blocks[0])
        assertEquals(TorrentBlockRangeRules.BlockCoord(1, 0), blocks[1])
    }

    @Test
    fun testRequiredBlocksWithFileOffset() {
        // Dosya torrent içinde 100.000 bayt ofsetle başlıyor
        val fileOffset = 100_000L
        val blocks = TorrentBlockRangeRules.requiredBlocks(
            fileOffset = fileOffset,
            position = 0L,
            length = 16384,
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
        )
        // 100.000 / 16384 = 6.1 -> Blok 6
        // (100.000 + 16383) / 16384 = 7.1 -> Blok 7
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
        // Parça 0 doğrulanmış, isBlockFinished false dönse bile servable olmalı
        val servable = TorrentBlockRangeRules.isRangeServable(
            blocks = blocks,
            isPieceVerified = { p -> p == 0 },
            isBlockFinished = { _, _ -> false },
        )
        assertTrue(servable)
    }

    @Test
    fun testMaxServableLengthContiguous() {
        // Blok 0 ve Blok 1 hazır, Blok 2 eksik
        val readyBlocks = setOf("0:0", "0:1")
        val servableLen = TorrentBlockRangeRules.maxServableLength(
            fileOffset = 0L,
            position = 0L,
            maxBytes = 65536, // 4 blok talep edildi
            pieceLength = pieceLength8M,
            fileSize = fileSize10G,
            isPieceVerified = { false },
            isBlockFinished = { p, b -> "$p:$b" in readyBlocks },
        )
        // İlk 2 blok (32768 bayt) kesintisiz verilebilmeli
        assertEquals(32768, servableLen)
    }

    @Test
    fun testMaxServableLengthFirstBlockMissing() {
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
}
