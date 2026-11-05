package com.kaan.play.data.torrent

/**
 * Android ve libtorrent'ten bağımsız blok ve aralık çözümleme kuralları (birim testli).
 *
 * BitTorrent standardında bloklar 16 KiB (16384 bayt) boyutundadır.
 * Bir bayt aralığının servis edilebilmesi için, aralığın kestiği tüm blokların inmiş olması gerekir.
 */
object TorrentBlockRangeRules {

    const val BLOCK_SIZE = 16384

    data class BlockCoord(val pieceIndex: Int, val blockIndex: Int)
    data class ActiveDeadline(val pieceIndex: Int, val deadlineMs: Int)

    /**
     * Dosya içindeki [position] baytından başlayarak [length] baytlık bir aralığın
     * ihtiyaç duyduğu parça ve blok koordinatlarının listesini döner.
     */
    fun requiredBlocks(
        fileOffset: Long,
        position: Long,
        length: Int,
        pieceLength: Int,
        fileSize: Long,
    ): List<BlockCoord> {
        if (length <= 0 || position >= fileSize || pieceLength <= 0) return emptyList()

        val startAbs = fileOffset + position
        val endAbs = minOf(fileOffset + position + length.toLong() - 1L, fileOffset + fileSize - 1L)
        if (startAbs > endAbs) return emptyList()

        val startPiece = (startAbs / pieceLength).toInt()
        val endPiece = (endAbs / pieceLength).toInt()

        val result = mutableListOf<BlockCoord>()
        for (p in startPiece..endPiece) {
            val pStart = maxOf(startAbs, p.toLong() * pieceLength)
            val pEnd = minOf(endAbs, (p.toLong() + 1L) * pieceLength - 1L)
            val relStart = pStart - p.toLong() * pieceLength
            val relEnd = pEnd - p.toLong() * pieceLength

            val startBlock = (relStart / BLOCK_SIZE).toInt()
            val endBlock = (relEnd / BLOCK_SIZE).toInt()

            for (b in startBlock..endBlock) {
                result.add(BlockCoord(p, b))
            }
        }
        return result
    }

    /**
     * Verilen blok koordinatlarının tümü hazır mı (parça doğrulanmış ya da blok inmiş)?
     */
    fun isRangeServable(
        blocks: List<BlockCoord>,
        isPieceVerified: (piece: Int) -> Boolean,
        isBlockFinished: (piece: Int, block: Int) -> Boolean,
    ): Boolean {
        if (blocks.isEmpty()) return false
        for (coord in blocks) {
            if (!isPieceVerified(coord.pieceIndex) && !isBlockFinished(coord.pieceIndex, coord.blockIndex)) {
                return false
            }
        }
        return true
    }

    /**
     * İstenen blok koordinatlarından henüz hazır olmayan (eksik) olanları döner.
     */
    fun missingBlocks(
        blocks: List<BlockCoord>,
        isPieceVerified: (piece: Int) -> Boolean,
        isBlockFinished: (piece: Int, block: Int) -> Boolean,
    ): List<BlockCoord> {
        if (blocks.isEmpty()) return emptyList()
        val missing = mutableListOf<BlockCoord>()
        for (coord in blocks) {
            if (!isPieceVerified(coord.pieceIndex) && !isBlockFinished(coord.pieceIndex, coord.blockIndex)) {
                missing.add(coord)
            }
        }
        return missing
    }

    /**
     * [position] ofsetinden başlayarak [maxBytes] baytlık alanda, kesintisiz olarak
     * hemen okunup servis edilebilecek maksimum bayt miktarını hesaplar.
     */
    fun maxServableLength(
        fileOffset: Long,
        position: Long,
        maxBytes: Int,
        pieceLength: Int,
        fileSize: Long,
        isPieceVerified: (piece: Int) -> Boolean,
        isBlockFinished: (piece: Int, block: Int) -> Boolean,
    ): Int {
        if (maxBytes <= 0 || position >= fileSize || pieceLength <= 0) return 0

        val maxPossible = minOf(maxBytes.toLong(), fileSize - position).toInt()
        val allRequired = requiredBlocks(fileOffset, position, maxPossible, pieceLength, fileSize)
        if (allRequired.isEmpty()) return 0

        // İlk blok hazır değilse hiçbir şey servis edilemez
        val first = allRequired.first()
        if (!isPieceVerified(first.pieceIndex) && !isBlockFinished(first.pieceIndex, first.blockIndex)) {
            return 0
        }

        var contiguousBytes = 0
        val startAbs = fileOffset + position
        for (coord in allRequired) {
            val isReady = isPieceVerified(coord.pieceIndex) || isBlockFinished(coord.pieceIndex, coord.blockIndex)
            if (!isReady) break

            val blockStartAbs = coord.pieceIndex.toLong() * pieceLength + coord.blockIndex.toLong() * BLOCK_SIZE
            val blockEndAbs = minOf(blockStartAbs + BLOCK_SIZE - 1L, fileOffset + fileSize - 1L)

            val coveredStart = maxOf(startAbs, blockStartAbs)
            val coveredEnd = minOf(startAbs + maxPossible - 1L, blockEndAbs)

            if (coveredEnd >= coveredStart) {
                contiguousBytes += (coveredEnd - coveredStart + 1L).toInt()
            }
        }

        return minOf(contiguousBytes, maxPossible)
    }

    /**
     * Bir parçanın kaç adet 16 KiB blok içerdiğini hesaplar.
     */
    fun blocksInPiece(pieceLength: Int): Int =
        ((pieceLength + BLOCK_SIZE - 1) / BLOCK_SIZE).coerceAtLeast(1)

    /**
     * DÜZELTME 1:
     * Kullanıcı yeni bir [currentPiece] konumuna atladığında (veya ilk açılışta),
     * acil öncelikte kalması gereken parçaların listesini ve son tarihlerini döner.
     *
     * Kurallar:
     * 1. Mevcut okuma penceresi: [currentPiece] .. [currentPiece] + [windowAhead] - 1 (150ms, 350ms...)
     * 2. Baş parçalar: [firstPiece] ve [firstPiece] + 1 (500ms, 750ms)
     * 3. Kuyruk parçaları: [lastPiece] - 1 ve [lastPiece] (1000ms, 1250ms)
     *
     * Bu üç kümenin DIŞINDA kalan eski konumlara ait parçalar elenir (deadline verilmez).
     * Doğrulanmış parçalar ([isPieceVerified] == true) elenir.
     * Parçalar tekilleştirilir; öncelik sırası korunur.
     */
    fun activeDeadlinePieces(
        currentPiece: Int,
        firstPiece: Int,
        lastPiece: Int,
        windowAhead: Int = 2,
        isPieceVerified: (piece: Int) -> Boolean = { false },
    ): List<ActiveDeadline> {
        if (firstPiece > lastPiece) return emptyList()

        val result = mutableListOf<ActiveDeadline>()
        val seen = mutableSetOf<Int>()

        // 1. Mevcut oynatma ofseti (en acil: 150ms, 350ms...)
        for (i in 0 until windowAhead) {
            val p = currentPiece + i
            if (p in firstPiece..lastPiece && seen.add(p)) {
                if (!isPieceVerified(p)) {
                    result.add(ActiveDeadline(p, 150 + i * 200))
                }
            }
        }

        // 2. Baş parçalar (500ms, 750ms)
        listOf(firstPiece, firstPiece + 1).filter { it in firstPiece..lastPiece }.forEachIndexed { idx, p ->
            if (seen.add(p)) {
                if (!isPieceVerified(p)) {
                    result.add(ActiveDeadline(p, 500 + idx * 250))
                }
            }
        }

        // 3. Kuyruk parçaları (1000ms, 1250ms)
        listOf(lastPiece - 1, lastPiece).filter { it in firstPiece..lastPiece }.forEachIndexed { idx, p ->
            if (seen.add(p)) {
                if (!isPieceVerified(p)) {
                    result.add(ActiveDeadline(p, 1000 + idx * 250))
                }
            }
        }

        return result
    }

    /**
     * AÇILIŞ ODAĞI:
     * Metadata geldiğinde ilk N parça (varsayılan 4) azalan öncelikle (150ms, 300ms, 450ms, 600ms)
     * ve son parça (Cues/moov indeksi için: 1000ms) öncelik alır.
     * Son parçaya 1000ms verilerek ilk parçanın (header, attachments, ilk cluster) bant genişliğini
     * tüketmesi önlenir.
     */
    fun startupFocusPieces(
        firstPiece: Int,
        lastPiece: Int,
        initialPiecesCount: Int = 4,
        isPieceVerified: (piece: Int) -> Boolean = { false },
    ): List<ActiveDeadline> {
        if (firstPiece > lastPiece) return emptyList()
        val result = mutableListOf<ActiveDeadline>()
        val seen = mutableSetOf<Int>()

        // 1. İlk N parça (150ms, 300ms, 450ms, 600ms)
        for (i in 0 until initialPiecesCount) {
            val p = firstPiece + i
            if (p in firstPiece..lastPiece && seen.add(p)) {
                if (!isPieceVerified(p)) {
                    result.add(ActiveDeadline(p, 150 + i * 150))
                }
            }
        }

        // 2. Kuyruk parçası (Cues/moov için: 1000ms)
        if (firstPiece != lastPiece && seen.add(lastPiece)) {
            if (!isPieceVerified(lastPiece)) {
                result.add(ActiveDeadline(lastPiece, 1000))
            }
        }

        return result
    }
}
