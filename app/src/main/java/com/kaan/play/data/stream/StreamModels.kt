package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Bir içerik için kaynak isteği. Birincil kimlik TMDB id; addon'lar IMDb [imdbId] ister.
 * Dizide [season] ve [episode] zorunludur.
 */
@Serializable
data class StreamRequest(
    val tmdbId: Int,
    val imdbId: String?,
    val type: MediaType,
    val season: Int? = null,
    val episode: Int? = null,
    /** true: önbellekteki (eklenti) sonuçları yok say, yeniden çalıştır (kaynak listesindeki Yenile). */
    val refresh: Boolean = false,
    val pluginId: String? = null,
    val dataUrl: String? = null,
    val contentUrl: String? = null,
    val title: String? = null,
    val year: Int? = null,
    val addonType: String? = null,
    val addonContentId: String? = null,
) {
    /**
     * Addon'a giden kimlik: film `tt123`, dizi `tt123:sezon:bölüm`.
     * IMDb kimliği (ya da dizide sezon/bölüm) yoksa null: sorgulanacak bir şey yok.
     */
    val contentId: String?
        get() {
            if (!addonContentId.isNullOrBlank()) return addonContentId
            val imdb = imdbId ?: return null
            return when (type) {
                MediaType.Movie -> imdb
                MediaType.Tv -> if (season != null && episode != null) "$imdb:$season:$episode" else null
            }
        }
}

/**
 * Oynatılacak şey: kaynak isteği + geçmişe yazılacak kopya bilgiler. [title] filmde film adı,
 * dizide dizi adıdır; [posterPath] dizinin/filmin afişidir.
 */
@Serializable
data class PlayTarget(val request: StreamRequest, val title: String, val posterPath: String?)

/** Sonucu veren addon. */
@Serializable
data class StreamSource(val addonId: Long, val addonName: String)

/**
 * Oynatılabilir tek kaynak (yalnızca `url` tipli akışlar buraya girer).
 * [resolution]: dikey çözünürlük (1080, 2160 ...), bilinmiyorsa null. [headers]: addon'un
 * `behaviorHints.proxyHeaders.request` değeri; oynatıcı (Media3 OkHttp datasource) bunları istekle gönderir.
 */
@Serializable
data class PlayableStream(
    val source: StreamSource,
    val url: String,
    val resolution: Int?,
    val sizeBytes: Long?,
    val headers: Map<String, String>,
    val subtitles: List<StreamSubtitle>,
    /** Akış metninden çıkarılan ses dili ("EN", "TR", "MULTI" ...); bulunamazsa null. */
    val language: String? = null,
    /** Kaynağın bildirdiği tür (HLS/DASH); adreste uzantı yoksa oynatıcı türü bundan alır. Bilinmiyorsa null. */
    val mimeType: String? = null,
    /** Yalnızca `magnet:` kaynaklarda: torrent içindeki dosya (0 tabanlı); yerleşik motor ya da torrent sunucusu bu dosyayı oynatır. */
    val torrentFileIdx: Int? = null,
    /** Ad/başlık/açıklamada Türkçe dublaj işareti var ([DubDetector]); belirsizse false = altyazılı. */
    val dubbed: Boolean = false,
    /** Kaynağın ait olduğu içeriğin anahtarı ([StreamOwnership.keyOf]); birleşik sağlayıcı damgalar. Başka içeriğin kaynağı oynatılmaz. */
    val forKey: String? = null,
)

/** Akışla gelen altyazı; [language] addon'un verdiği ham etiket (`eng`, `tur` ...). */
@Serializable
data class StreamSubtitle(val url: String, val language: String?, val headers: Map<String, String> = emptyMap())

enum class FailureReason { Timeout, Network, Http, InvalidResponse }

/** Bir addon'un cevabı: ya kaynaklar (boş olabilir) ya da "yanıt vermedi" bilgisi. */
sealed interface StreamResult {
    val source: StreamSource

    data class Found(override val source: StreamSource, val streams: List<PlayableStream>) : StreamResult

    data class Failed(override val source: StreamSource, val reason: FailureReason) : StreamResult
}

/**
 * Kaynak toplama arayüzü. Sonuçlar, kaynak başına geldikçe akar; akış tüm kaynaklar bitince tamamlanır.
 * Faz 1'de tek uygulama [AddonStreamProvider]; sonraki fazların kaynakları da buraya takılır.
 */
fun interface StreamProvider {
    fun streams(request: StreamRequest): Flow<StreamResult>

    /**
     * Bu istek için sorulacak kaynaklar (cevap gelmeden önce bilinen liste): arayüz hangileri beklenirken hangileri
     * bitti diye gösterebilsin. Bilinmiyorsa boş.
     */
    suspend fun sources(request: StreamRequest): List<StreamSource> = emptyList()
}
