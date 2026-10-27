package com.kaan.play.data.download

import com.kaan.play.data.stream.PlayableStream
import com.kaan.play.data.stream.TorrentLinks
import com.kaan.play.data.stream.recommended

/**
 * Kaynak listesinin "indirme modu" kuralları (Android'den bağımsız, birim testli): torrent ve http(s) olmayan kaynaklar
 * baştan elenir; geri kalanlar [SourceProbe] ile denetlenir, yalnızca indirilebilir çıkanlar gösterilir; önerilen en üstte.
 */
object DownloadPick {

    /** Denetlenecek adaylar: yalnızca doğrudan http(s) bağlantılar (torrent yok). Sıra korunur. */
    fun candidates(streams: List<PlayableStream>): List<PlayableStream> =
        streams.filter { s ->
            !TorrentLinks.isMagnet(s.url) &&
                (s.url.startsWith("https://", ignoreCase = true) || s.url.startsWith("http://", ignoreCase = true))
        }

    /**
     * Gösterilecek liste: [probed]'da indirilebilir (true) çıkanlar, sıralı listeden ([streams]) gelen sırayla; önerilen
     * (indirilebilirler arasından seçilir) en üstte.
     */
    fun visible(streams: List<PlayableStream>, probed: Map<String, Boolean>): List<PlayableStream> {
        val ok = candidates(streams).filter { probed[it.url] == true }
        val top = ok.recommended() ?: return ok
        return listOf(top) + (ok - top)
    }

    /** Denetimi bitmemiş aday kaldı mı? */
    fun pending(streams: List<PlayableStream>, probed: Map<String, Boolean>): Boolean =
        candidates(streams).any { it.url !in probed }
}
