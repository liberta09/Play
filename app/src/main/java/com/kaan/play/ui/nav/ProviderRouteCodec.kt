package com.kaan.play.ui.nav

/**
 * Sağlayıcı kimliğini (providerId) Compose Navigation rota parametrelerinde güvenle ve kayıpsız taşımak için kodlayıcı.
 * Android bağımlılığı yoktur; birim testlerinde doğrudan çalıştırılabilir.
 */
object ProviderRouteCodec {
    private const val ESCAPE_CHAR = '~'
    private const val COLON_ESCAPE = "~c~"
    private const val ESCAPED_TILDE = "~~"

    /**
     * providerId'deki iki nokta ':' karakterini güvenli biçimde kaçırır.
     */
    fun encode(providerId: String): String {
        if (providerId.isEmpty()) return ""
        val sb = StringBuilder(providerId.length + 8)
        for (i in 0 until providerId.length) {
            when (val ch = providerId[i]) {
                '~' -> sb.append(ESCAPED_TILDE)
                ':' -> sb.append(COLON_ESCAPE)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Kodlanmış sağlayıcı kimliğini çözer. Eski biçimleri de (cs_..., nuvio_addon_...) geriye dönük destekler.
     */
    fun decode(encoded: String): String {
        if (encoded.isEmpty()) return ""

        // Eski (legacy) rotalar için geriye dönük uyumluluk
        if (!encoded.contains(ESCAPE_CHAR)) {
            if (encoded.startsWith("cs_")) {
                return "cs:" + encoded.removePrefix("cs_")
            }
            if (encoded.startsWith("nuvio_addon_")) {
                return "nuvio_addon:" + encoded.removePrefix("nuvio_addon_")
            }
            return encoded
        }

        val sb = StringBuilder(encoded.length)
        var i = 0
        while (i < encoded.length) {
            if (encoded[i] == ESCAPE_CHAR) {
                if (encoded.startsWith(COLON_ESCAPE, i)) {
                    sb.append(':')
                    i += COLON_ESCAPE.length
                } else if (encoded.startsWith(ESCAPED_TILDE, i)) {
                    sb.append('~')
                    i += ESCAPED_TILDE.length
                } else {
                    sb.append(encoded[i])
                    i++
                }
            } else {
                sb.append(encoded[i])
                i++
            }
        }
        return sb.toString()
    }
}
