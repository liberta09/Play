package com.kaan.play.data.subtitle

import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.AddonFetchFailure
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.data.stream.StreamSource
import com.kaan.play.data.stream.fetchAddonText
import com.kaan.play.data.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** SubDL uç noktaları: arama [api], indirme [dl]. Yalnızca debug derlemede sahte sunucuya yönlendirilebilir ([DebugEndpoints]). */
data class SubdlEndpoints(val api: String = "https://api.subdl.com", val dl: String = "https://dl.subdl.com")

/**
 * Debug derlemede sahte altyazı sunucusu için adres geçersiz kılma (`--es play_subs_endpoint http://host:port`). Yayın
 * derlemesinde hiç ayarlanmaz; sağlayıcılar yalnızca bu değer doluysa kullanır.
 */
object DebugEndpoints {
    @Volatile var subsBase: String? = null

    /** Sahte TheIntroDB uç noktası (`--es play_introdb_endpoint`); yalnızca debug derlemede ayarlanır. */
    @Volatile var introDbBase: String? = null
}

/**
 * SubDL altyazı araması (<https://subdl.com/api-doc>, v1): `GET {api}/api/v1/subtitles?api_key=…&imdb_id=tt…&type=movie|tv
 * [&season_number=&episode_number=]&languages=TR,EN&subs_per_page=30`. Cevap `{status, results[], subtitles[{release_name, name,
 * lang, url, season, episode, full_season …}]}`; `url` göreli yoldur ve indirme adresi `https://dl.subdl.com` + `url`'dir (zip).
 *
 * - **Anahtar:** `SUBDL_API_KEY` (local.properties → BuildConfig). Boşsa SubDL **sessizce atlanır** (hiç istek atılmaz, hata yok).
 *   Anahtar yalnızca arama isteğinin adresinde gider; loglanmaz, hata iletilerine girmez. Cevaptaki `url`lerde SubDL anahtarı da
 *   döndürür; [stripApiKey] onu siler (önbelleğe/oynatıcıya anahtarsız adres gider, indirme anonimdir).
 * - **Diller:** dil dil, öncelik sırasıyla aranır (önce Türkçe; hiç sonuç yoksa İngilizce); aynı istekte iki dil sayfa sınırını İngilizceyle doldurur.
 * - **Limitler (api-doc, 2026-10):** ücretsiz anahtar günde 2000 istek; anonim indirme IP başına günde 300. Bu yüzden arama sonuçları
 *   film/bölüm bazında önbelleğe alınır ([SubtitleSearchCache]) ve indirilen metin adres bazında saklanır ([SubtitleLoader]).
 * - Tam sezon paketleri (`full_season`) atılır. Hata/zaman aşımı/bozuk cevap boş liste döner; oynatmayı etkilemez.
 */
class SubdlSubtitleProvider(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val languages: suspend () -> List<String>,
    private val imdbFor: suspend (StreamRequest) -> String? = { null },
    private val cache: SubtitleSearchCache? = null,
    private val endpoints: () -> SubdlEndpoints = { DebugEndpoints.subsBase?.let { SubdlEndpoints(it, it) } ?: SubdlEndpoints() },
    private val timeout: Duration = 15.seconds,
) : SubtitleProvider {

    override fun subtitles(request: StreamRequest): Flow<SubtitleResult> = channelFlow {
        if (apiKey.isBlank()) return@channelFlow // anahtar yok: sessizce atla
        val tracks = lookup(request)
        // Ağ hatası: kısa "yanıt vermedi" bilgisi (oynatmayı etkilemez); boş sonuç hata değildir.
        send(if (tracks == null) SubtitleResult.Failed(SOURCE, com.kaan.play.data.stream.FailureReason.Network) else SubtitleResult.Found(SOURCE, tracks))
    }

    suspend fun search(request: StreamRequest): List<SubtitleTrack> = lookup(request).orEmpty()

    /** Bulunan parçalar; arama yapılamadıysa (ağ/HTTP hatası) null, sonuç yoksa boş liste. */
    private suspend fun lookup(request: StreamRequest): List<SubtitleTrack>? {
        if (apiKey.isBlank()) return emptyList()
        val imdb = request.imdbId ?: try {
            imdbFor(request)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }?.takeIf { it.startsWith("tt") }
        val title = request.title?.takeIf { it.isNotBlank() }
        if (imdb == null && title == null) return emptyList()

        val wanted = languages().distinct()
        if (wanted.isEmpty()) return emptyList()
        val tv = request.type == MediaType.Tv && request.season != null && request.episode != null
        val ep = endpoints()
        // Dil dil, öncelik sırasıyla (Türkçe → İngilizce …): birden çok dil tek istekte istenince sayfa sınırı (30) çoğunlukla İngilizceyle
        // dolup Türkçe neredeyse hiç gelmiyordu. İlk sonuç veren dilde durulur; sonraki dil için ikinci istek yalnızca öncekinde sonuç yoksa atılır.
        // Her (film/bölüm, dil) çifti ayrı önbelleklenir; hata (null) önbelleğe yazılmaz ve aramayı "yanıt vermedi" yapar.
        for (language in wanted) {
            val searchId = imdb ?: "title:$title"
            val key = searchKey("subdl", if (tv) "tv" else "movie", searchId, request.season.takeIf { tv }, request.episode.takeIf { tv }, listOf(language))
            val fetch: suspend () -> List<SubtitleTrack>? = {
                val url = "${ep.api.trimEnd('/')}/api/v1/subtitles".toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("api_key", apiKey)
                    ?.addQueryParameter("type", if (tv) "tv" else "movie")
                    ?.apply {
                        if (imdb != null) {
                            addQueryParameter("imdb_id", imdb)
                        } else {
                            addQueryParameter("film_name", title)
                            request.year?.let { addQueryParameter("year", it.toString()) }
                        }
                        if (tv) {
                            addQueryParameter("season_number", request.season.toString())
                            addQueryParameter("episode_number", request.episode.toString())
                        }
                    }
                    ?.addQueryParameter("languages", language.uppercase(Locale.ROOT))
                    ?.addQueryParameter("subs_per_page", MAX_PER_LANGUAGE.toString())
                    ?.addQueryParameter("client", "custom_integration")
                    ?.build()
                if (url == null) {
                    null
                } else {
                    try {
                        parse(fetchAddonText(client, url, timeout, MAX_BYTES), listOf(language), ep.dl)
                    } catch (_: AddonFetchFailure) {
                        null // hata: önbelleğe yazılmaz
                    }
                }
            }
            val found = (if (cache != null) cache.getOrFetch(key, fetch) else fetch()) ?: return null
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    companion object {
        const val ORIGIN = "SubDL"
        val SOURCE = StreamSource(-2L, ORIGIN)
        const val MAX_BYTES = 5L * 1024 * 1024

        /** API sayfa başına en çok 30 altyazı verir. */
        const val MAX_PER_LANGUAGE = 30

        /**
         * `{status, subtitles:[…]}` → [languages] sırasıyla süzülmüş parçalar. `status:false`, bozuk biçim ya da hata boş liste verir.
         * [dlBase]: göreli `url`'nin önüne eklenir. Aynı adres tek kez; tam sezon paketleri atılır.
         */
        fun parse(body: String, languages: List<String>, dlBase: String): List<SubtitleTrack> {
            val root = try {
                PlayJson.parseToJsonElement(body)
            } catch (_: IllegalArgumentException) {
                return emptyList()
            } as? JsonObject ?: return emptyList()
            if ((root["status"] as? JsonPrimitive)?.booleanOrNull == false) return emptyList()
            val array = root["subtitles"] as? JsonArray ?: return emptyList()
            val wanted = languages.distinct()
            val tracks = array.asSequence()
                .mapNotNull { it as? JsonObject }
                .filter { (it["full_season"] as? JsonPrimitive)?.booleanOrNull != true }
                .mapNotNull { obj ->
                    val raw = obj.string("url")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    // SubDL cevaptaki adrese `api_key` ekler; anahtar adreste (önbellek, kayıtlar) taşınmaz, indirme anonim yapılır.
                    val url = stripApiKey(when {
                        raw.startsWith("https://", true) || raw.startsWith("http://", true) -> raw
                        else -> dlBase.trimEnd('/') + "/" + raw.trimStart('/')
                    })
                    val lang = language(obj.string("lang") ?: obj.string("language"))?.takeIf { it in wanted } ?: return@mapNotNull null
                    val label = (obj.string("release_name") ?: obj.string("name"))?.trim()?.takeIf { it.isNotEmpty() }
                    SubtitleTrack(url, lang, ORIGIN, SubtitleKind.Online, label)
                }
                .distinctBy { it.url }
                .toList()
            return wanted.flatMap { lang -> tracks.filter { it.language == lang }.take(MAX_PER_LANGUAGE) }
        }

        /** SubDL kodları: `TR`, `EN`, `BR_PT` (Brezilya Portekizcesi) ... */
        internal fun language(raw: String?): String? {
            val text = raw?.trim().orEmpty()
            if (text.uppercase(Locale.ROOT).startsWith("BR_")) return "pt"
            return SubtitleLanguages.normalize(text)
        }

        private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    }
}
