package com.kaan.play.data.subtitle

import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Seçilebilir tek harici altyazı. [language]: [SubtitleLanguages.normalize] ile iki harfli temel dil (bilinmiyorsa null);
 * [origin]: satırda gösterilen kaynak adı (altyazı addon'unun, eklentinin ya da akışı veren addon'un adı).
 * [url] kimliktir: aynı adres tek kez listelenir ve kaydırma değeri adrese bağlı saklanır. [kind]: menüde hangi bölümde durduğu;
 * [label]: çevrimiçi altyazıda dosya/sürüm adı (eşleşen sürümü seçmeye yarar), yoksa null.
 */
@Serializable
data class SubtitleTrack(
    val url: String,
    val language: String?,
    val origin: String,
    val kind: SubtitleKind = SubtitleKind.Source,
    val label: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val season: Int? = null,
    val episode: Int? = null,
)

/**
 * [Source]: akışın, eklentinin ya da altyazı addon'unun verdiği; [Online]: yerleşik çevrimiçi arama; [Local]: kullanıcının seçtiği
 * dosya; [Translated]: İngilizceden cihazda çevrilmiş Türkçe (önbellekteki .srt; gerçek Türkçeyle karışmaz).
 */
@Serializable
enum class SubtitleKind { Source, Online, Local, Translated }

/** Bir altyazı addon'unun cevabı: ya parçalar (boş olabilir) ya da "yanıt vermedi". */
sealed interface SubtitleResult {
    val source: StreamSource

    data class Found(override val source: StreamSource, val tracks: List<SubtitleTrack>) : SubtitleResult

    data class Failed(override val source: StreamSource, val reason: FailureReason) : SubtitleResult
}

/** Altyazı toplama arayüzü; [com.kaan.play.data.stream.StreamProvider] ile aynı biçim: sonuçlar kaynak başına geldikçe akar. */
fun interface SubtitleProvider {
    fun subtitles(request: StreamRequest): Flow<SubtitleResult>

    /** Bu istek için sorulacak altyazı addon'ları (cevaplardan önce bilinir). Bilinmiyorsa boş. */
    suspend fun sources(request: StreamRequest): List<StreamSource> = emptyList()
}
