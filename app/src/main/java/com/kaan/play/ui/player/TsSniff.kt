package com.kaan.play.ui.player

import java.util.Locale

/**
 * TS parçalarının başındaki sahte resim/çöp baytları kırpma kararları (Android'den bağımsız, birim testli).
 *
 * Kök neden (2026-10-30, Joker 3002): adresinde `/hls` geçen her yanıt TS parçası sayılıyordu; oynatma LİSTESİ de
 * (`…/hls/…/txt/master.txt`) bu yoldan geçince metindeki ilk uygun büyük "G" harfine (0x47 = TS eşitleme baytı) kadar
 * kesiliyor, liste `#EXTM3U` ile başlamadığı için ExoPlayer ERROR_CODE_PARSING_MANIFEST_MALFORMED (3002) veriyordu.
 * Hangi içeriğin bozulacağı listedeki harflere (GROUP-ID, LANGUAGE, imzalar) bağlıydı. Kural: oynatma listesi/manifest
 * ASLA kırpılmaz; yalnızca gerçekten TS olmayan bir başlıkla başlayan ikili parça kırpılır.
 */
internal object TsSniff {
    const val SYNC: Byte = 0x47
    const val PACKET = 188

    /** Yanıt bir oynatma listesi ya da manifest mi (içerik türüne ya da ilk baytlara göre)? Öyleyse dokunulmaz. */
    fun isPlaylistOrManifest(bytes: ByteArray, length: Int, contentType: String?): Boolean {
        val type = contentType?.lowercase(Locale.ROOT).orEmpty()
        if ("mpegurl" in type || "dash+xml" in type) return true
        var i = 0
        // UTF-8 BOM ve baştaki boşluklar atlanır.
        if (length >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) i = 3
        while (i < length && (bytes[i] == ' '.code.toByte() || bytes[i] == '\t'.code.toByte() ||
                bytes[i] == '\r'.code.toByte() || bytes[i] == '\n'.code.toByte())) i++
        return startsWith(bytes, length, i, "#EXT") || startsWith(bytes, length, i, "<?xml") || startsWith(bytes, length, i, "<MPD")
    }

    /**
     * Kırpılacak bayt sayısı: ilk bayt zaten eşitleme baytıysa 0; değilse 188 bayt arayla doğrulanan (ya da tamponun
     * sonuna yakın) ilk eşitleme baytının konumu; hiç yoksa 0 (olduğu gibi bırakılır).
     */
    fun syncOffset(bytes: ByteArray, length: Int): Int {
        if (length <= 0 || bytes[0] == SYNC) return 0
        for (i in 0 until length) {
            if (bytes[i] != SYNC) continue
            if (i + PACKET >= length || bytes[i + PACKET] == SYNC) return i
        }
        return 0
    }

    /** Okunan ilk parça için kırpma miktarı: liste/manifestte her zaman 0. */
    fun trimFor(bytes: ByteArray, length: Int, contentType: String?): Int =
        if (isPlaylistOrManifest(bytes, length, contentType)) 0 else syncOffset(bytes, length)

    private fun startsWith(bytes: ByteArray, length: Int, from: Int, prefix: String): Boolean {
        if (from + prefix.length > length) return false
        for (k in prefix.indices) if (bytes[from + k] != prefix[k].code.toByte()) return false
        return true
    }
}
