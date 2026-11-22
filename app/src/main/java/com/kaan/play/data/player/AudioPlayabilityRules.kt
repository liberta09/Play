package com.kaan.play.data.player

import java.util.Locale

/**
 * Kaynağın sesi bu cihazda çalınabilir mi (Android'den bağımsız, birim testli).
 *
 * Görüntü oynarken ses parçalarının hiçbiri çözülemiyorsa (cihaz çözücüsü de FFmpeg de desteklemiyor) ExoPlayer videoyu sessiz
 * oynatır. Böyle kaynak otomatik modda elenir, elle seçildiyse kullanıcıya söylenir. Ses parçası hiç olmayan video (sessiz klip)
 * elenmez: sorun ses biçimi değildir.
 */
object AudioPlayabilityRules {

    /** Ses parçası var ama hiçbiri desteklenmiyorsa true; parça yoksa ya da en az biri destekliyse false. */
    fun isUnplayable(tracks: List<AudioTrackInfo>): Boolean =
        tracks.isNotEmpty() && tracks.none { it.isSupported }

    /** Tanı satırı (adres yok): "audio/true-hd 8k destek=hayır seçili=hayır; audio/ac3 6k destek=evet seçili=evet". */
    fun describe(tracks: List<AudioTrackInfo>): String =
        if (tracks.isEmpty()) {
            "yok"
        } else {
            tracks.joinToString("; ") { t ->
                val lang = t.language?.lowercase(Locale.ROOT) ?: "und"
                "${t.mime ?: "?"} ${t.channelCount}k dil=$lang destek=${yesNo(t.isSupported)} seçili=${yesNo(t.isSelected)}"
            }
        }

    private fun yesNo(value: Boolean) = if (value) "evet" else "hayır"
}
