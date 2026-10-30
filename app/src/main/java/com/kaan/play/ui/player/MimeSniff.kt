package com.kaan.play.ui.player

import java.util.Locale

/**
 * Bir akışın ilk baytlarından medya türü kararı (Android'den bağımsız, birim testli).
 *
 * Neden: adresten tahmin edilen tür (`.txt`, `/hls/`, `/txt/`) yanlış çıkabilir; oynatıcı ayrıştırma hatası verince bir kez
 * ağdan ilk baytlara bakılıp doğru türle yeniden denenir (davranış Nuvio'dan örnek alındı, kod kopyalanmadı).
 */
internal object MimeSniff {

    const val HLS = "application/x-mpegURL"
    const val DASH = "application/dash+xml"

    /** Düz dosya: tür verilmez, Media3 içerikten çıkarır. */
    const val PROGRESSIVE = ""

    /**
     * [HLS], [DASH], [PROGRESSIVE] (ikili/bilinmeyen medya) ya da null (karar verilemez: boş yanıt, HTML hata sayfası, JSON).
     */
    fun fromHead(bytes: ByteArray, length: Int): String? {
        if (length <= 0) return null
        var i = 0
        if (length >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) i = 3
        while (i < length && (bytes[i] == ' '.code.toByte() || bytes[i] == '\t'.code.toByte() ||
                bytes[i] == '\r'.code.toByte() || bytes[i] == '\n'.code.toByte())) i++
        if (i >= length) return null
        val head = String(bytes, i, minOf(length - i, 64), Charsets.ISO_8859_1).lowercase(Locale.ROOT)
        return when {
            head.startsWith("#extm3u") -> HLS
            head.startsWith("<mpd") || (head.startsWith("<?xml") && head.contains("<mpd")) -> DASH
            // Metin tabanlı başka şeyler (HTML, JSON, XML hata sayfası): medya değil.
            head.startsWith("<") || head.startsWith("{") || head.startsWith("[") -> null
            else -> PROGRESSIVE
        }
    }

    /** Bu ayrıştırma hataları yeniden yoklamaya değer (tür tahmini yanlış olabilir). */
    fun isParsingErrorCode(code: Int): Boolean = code in PARSING_CODES

    // PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED=3001, MANIFEST_MALFORMED=3002,
    // CONTAINER_UNSUPPORTED=3003, MANIFEST_UNSUPPORTED=3004
    private val PARSING_CODES = setOf(3001, 3002, 3003, 3004)
}
