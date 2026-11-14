package com.kaan.play.tv

import com.kaan.play.R
import androidx.annotation.StringRes
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaan.play.PlayApp
import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.MediaIdentityResolver
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.kaan.play.data.tmdb.TmdbImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailEpisode(val season: Int, val episode: Int, val name: String?, val posterUrl: String?, val data: String)

/** Eklentinin `load(url)` yanıtından TV detay ekranının ihtiyacı. Kimlik (tmdbId) Play'deki gibi çözülür. */
data class DetailData(
    val title: String,
    val plot: String?,
    val posterUrl: String?,
    val year: Int?,
    val tags: List<String>,
    val isSeries: Boolean,
    val episodes: List<DetailEpisode>,
    val movieDataUrl: String?,
    val tmdbId: Int,
    val imdbId: String?,
    val type: MediaType,
    /** TMDB içeriği (film/dizi): oynatma isteği eklentisiz kurulur, yayını kurulu Nuvio eklentileri bulur. */
    val fromTmdb: Boolean = false,
    /** Fragman: TMDB'de YouTube anahtarı, eklentide `trailers` içindeki ilk adres (ana sayfa önizlemesi). */
    val trailerKey: String? = null,
    val trailerUrl: String? = null,
    /** TMDB içeriğinin afiş yolu (`/abc.jpg`): favori Play satırına bununla yazılır (telefon ve bulut TMDB yolunu okur). */
    val tmdbPosterPath: String? = null,
    /** TMDB içeriğinde Play detayındaki gibi: oyuncular, benzer içerikler, yönetmen / yazar / yaratıcı. */
    val cast: List<com.kaan.play.data.model.CastMember> = emptyList(),
    val similar: List<com.kaan.play.data.model.MediaSummary> = emptyList(),
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
) {
    val seasons: List<Int> get() = episodes.map { it.season }.distinct().sorted()
}

sealed interface DetailState {
    data object Loading : DetailState
    /** [message]: kaynak kimliği; arayüz seçili dilde çözer. */
    data class Failed(@StringRes val message: Int) : DetailState
    data class Ready(val data: DetailData) : DetailState
}

/** Bölüm başına izleme durumu: (sezon, bölüm) → ilerleme 0..1. */
typealias EpisodeProgress = Map<Pair<Int, Int>, Float>

/**
 * TV detayı. Play'in ProviderDetailScreen'indeki CloudStream yükleme adımlarının aynısı (orada ekranın içinde ve dışarıya
 * kapalı olduğu için burada yeniden yazıldı): loadApis → api.load(url) → kimlik → bölümler.
 */
class TvDetailModel(app: Application) : AndroidViewModel(app) {
    private val container get() = getApplication<PlayApp>().container

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    private var providerId = ""
    private var url = ""
    /** Bu içeriğin kayıtlarını seçen kural: eklentide eklenti + adres, TMDB'de tür + TMDB kimliği. */
    private val content = MutableStateFlow<((WatchHistoryEntity) -> Boolean)?>(null)

    /** Bu içeriğin izleme geçmişi (en yeni önce): bölüm işaretleri ve "Devam et" bundan. */
    val history: StateFlow<List<WatchHistoryEntity>> =
        combine(container.library.watchHistory, content) { all, match ->
            if (match == null) emptyList() else all.filter(match).sortedByDescending { it.lastWatchedAt }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * [cached]: oynatıcının bölüm paneli için; aynı içerik bu süreçte yüklendiyse yeniden indirilmez (1–3 dakikalık
     * bölümlerde her geçişte eklentiye sorulmasın). Detay ekranı her açılışta taze yükler.
     */
    fun load(providerId: String, url: String, cached: Boolean = false) {
        if (this.providerId == providerId && this.url == url && _state.value !is DetailState.Failed) return
        this.providerId = providerId
        this.url = url
        content.value = if (providerId.startsWith("cs:")) { e -> e.pluginId == providerId && e.contentUrl == url } else null
        val key = "$providerId|$url"
        val hit = if (cached) synchronized(recent) { recent[key] } else null
        if (hit != null) {
            if (providerId == TMDB_PROVIDER) content.value = { e -> e.pluginId == null && e.tmdbId == hit.tmdbId && e.type == hit.type }
            _state.value = DetailState.Ready(hit)
            return
        }
        _state.value = DetailState.Loading
        viewModelScope.launch { _state.value = withContext(Dispatchers.IO) { fetch(providerId, url) }.also { it.remember(key) } }
    }

    private fun DetailState.remember(key: String) {
        if (this is DetailState.Ready) synchronized(recent) { recent[key] = data; if (recent.size > 8) recent.remove(recent.keys.first()) }
    }

    fun retry() {
        _state.value = DetailState.Loading
        viewModelScope.launch { _state.value = withContext(Dispatchers.IO) { fetch(providerId, url) }.also { it.remember("$providerId|$url") } }
    }

    private suspend fun fetch(providerId: String, url: String): DetailState {
        if (providerId == TMDB_PROVIDER || providerId == IMDB_PROVIDER) {
            val ready = loader.fetch(providerId, url)
            (ready as? DetailState.Ready)?.data?.let { d -> content.value = { e -> e.pluginId == null && e.tmdbId == d.tmdbId && e.type == d.type } }
            return ready
        }
        val ready = loader.fetch(providerId, url)
        // Buluttan gelen eklenti kayıtları resimsizdir (bulut tam adres taşımaz): detay açılınca geçmişe yazılır.
        (ready as? DetailState.Ready)?.data?.let { d -> runCatching { container.library.fillMissingPoster(d.type, d.tmdbId, d.posterUrl) } }
        return ready
    }

    private val loader get() = TvDetailLoader(container)

    /** Play'deki PlayTarget'ın aynısı: oynatıcı ve izleme geçmişi bu kimlikle eşleşir. */
    fun target(d: DetailData, episode: DetailEpisode?): PlayTarget = loader.target(providerId, url, d, episode)

    private companion object {
        /** Son yüklenen içerikler (süreç boyunca, en çok 8); yalnız [load] `cached` ile okunur. */
        val recent = LinkedHashMap<String, DetailData>()
    }
}

/**
 * Detay yükleme (Play'in ProviderDetailScreen adımları): eklentide loadApis → api.load(url) → kimlik → bölümler; TMDB'de detay +
 * sezonlar. Detay ekranı ([TvDetailModel]) ve ana sayfa önizlemesi ([TvPreviewModel]) kullanır.
 */
internal class TvDetailLoader(private val container: com.kaan.play.AppContainer) {
    /**
     * Wi-Fi eşitlemesiyle gelen geçmiş kaydı telefonun eklenti satır numarasını (`cs:N`) taşır; TV'de o numara başka bir eklenti
     * olabilir (ör. telefonda Dramacix olan 718, TV'de kapalı DiziMom). Numaradaki eklentinin adresi içerik adresiyle aynı alan
     * adında değilse, alan adı tutan etkin eklenti kullanılır; bulunamazsa numara olduğu gibi döner. Sonuç süreç boyunca saklanır:
     * tarama tüm etkin eklentileri yükler, her önizlemede tekrarlanmasın.
     */
    suspend fun csProviderFor(providerId: String, url: String): String = csProviderOrNull(providerId, url) ?: providerId

    /** [csProviderFor] gibi; ama bu cihazda alan adıyla eşleşen eklenti yoksa null (başka cihazın numarası körlemesine kullanılmasın). */
    suspend fun csProviderOrNull(providerId: String, url: String): String? {
        if (!providerId.startsWith("cs:")) return providerId
        val host = hostOf(url) ?: return null
        val key = "$providerId|$host"
        synchronized(resolved) { resolved[key] }?.let { return it }
        fun matches(r: CsApiResult) = r.apisOrEmpty().any { hostOf(it.mainUrl) == host }
        val id = providerId.removePrefix("cs:").toLongOrNull()
        val result = if (id != null && matches(container.csRepos.loadApis(id))) providerId
            else container.csRepos.enabledPlugins().firstOrNull { it.id != id && matches(container.csRepos.loadApis(it.id)) }?.let { "cs:${it.id}" }
        // Yalnız bulunan eşleşme saklanır; bulunamadıysa (geçici yükleme hatası olabilir) sonraki çağrı yeniden tarar.
        if (result != null) synchronized(resolved) { resolved[key] = result }
        return result
    }

    private fun hostOf(url: String): String? = runCatching { java.net.URI(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()

    companion object {
        /** "cs:N|alan adı" → kullanılacak eklenti. Ayarlarda depo/eklenti değişince [forgetProviders] ile temizlenir. */
        private val resolved = HashMap<String, String>()

        fun forgetProviders() = synchronized(resolved) { resolved.clear() }
    }

    suspend fun fetch(providerId: String, url: String): DetailState {
        when (providerId) {
            TMDB_PROVIDER -> {
                val (path, id) = url.split(":").let { it.getOrNull(0) to it.getOrNull(1)?.toIntOrNull() }
                val type = MediaType.entries.firstOrNull { it.tmdbPath == path } ?: return DetailState.Failed(R.string.tv_detail_error_unrecognized)
                return fetchTmdb(type, id ?: return DetailState.Failed(R.string.tv_detail_error_unrecognized))
            }
            IMDB_PROVIDER -> {
                val found = runCatching { container.tmdb.findByImdb(url) }.getOrNull() ?: return DetailState.Failed(R.string.tv_detail_error_tmdb_not_found)
                return fetchTmdb(found.first, found.second)
            }
        }
        if (!providerId.startsWith("cs:")) return DetailState.Failed(R.string.tv_detail_error_unsupported)
        val result = container.csRepos.loadApis(providerId.removePrefix("cs:").toLongOrNull() ?: 0L)
        val api = result.apisOrEmpty().firstOrNull() ?: return DetailState.Failed(
            when (result) {
                is CsApiResult.PluginRowMissing -> R.string.tv_detail_error_plugin_missing
                is CsApiResult.LoadFailed -> R.string.tv_detail_error_plugin_load
                is CsApiResult.DexFileMissing, is CsApiResult.IncompatibleLibrary -> R.string.tv_detail_error_plugin_incompatible
                else -> R.string.tv_detail_error_plugin_empty
            },
        )
        val resp = runCatching { api.load(url) }.getOrNull() ?: return DetailState.Failed(R.string.tv_detail_error_load)
        val isSeries = resp is TvSeriesLoadResponse || resp is AnimeLoadResponse
        val raw = when (resp) {
            is TvSeriesLoadResponse -> resp.episodes
            is AnimeLoadResponse -> resp.episodes.values.flatten()
            else -> emptyList()
        }
        // Bölüm numarası olmayan eklenti bölümleri sıra numarasını alır (Play'deki ekranla aynı: ep.episode ?: sıra+1).
        // Aynı bölümü iki kez veren eklentide bölüm satırının anahtarı (sezon-bölüm-veri) yinelenip detay çöküyordu.
        val episodes = raw.mapIndexed { i, e -> DetailEpisode(e.season ?: 1, e.episode ?: (i + 1), e.name, e.posterUrl, e.data) }
            .distinctBy { Triple(it.season, it.episode, it.data) }
        val movieDataUrl = when (resp) {
            is MovieLoadResponse -> resp.dataUrl
            is LiveStreamLoadResponse -> resp.dataUrl
            else -> null
        }
        val type = if (isSeries) MediaType.Tv else MediaType.Movie
        val identity = runCatching {
            MediaIdentityResolver.resolveIdentity(
                rawTitle = resp.name, rawYear = resp.year, syncData = runCatching { resp.syncData }.getOrNull(),
                type = type, tmdb = container.tmdb,
            )
        }.getOrNull()
        // Kalıcı kimlik (telefonla aynı hesap): eklentinin cihaza özel numarası değil, türü + içerik adresi.
        val syntheticId = com.kaan.play.data.library.ProviderContentId.of(providerId, url)
        return DetailState.Ready(
            DetailData(
                title = identity?.cleanTitle ?: resp.name,
                plot = resp.plot,
                posterUrl = resp.posterUrl,
                year = identity?.year ?: resp.year,
                tags = resp.tags.orEmpty(),
                isSeries = isSeries,
                episodes = episodes,
                movieDataUrl = movieDataUrl,
                tmdbId = identity?.tmdbId ?: syntheticId,
                imdbId = identity?.imdbId,
                type = type,
                trailerUrl = runCatching { resp.trailers.firstOrNull()?.extractorUrl }.getOrNull(),
            ),
        )
    }

    /** TMDB filmi/dizisi: detay ve (dizide) bütün sezonların bölümleri, sezonlar paralel. Özel bölümler (0. sezon) atlanır. */
    private suspend fun fetchTmdb(type: MediaType, id: Int): DetailState {
        val d = runCatching { container.tmdb.detail(type, id) }.getOrNull() ?: return DetailState.Failed(R.string.tv_detail_error_load)
        val episodes = if (type != MediaType.Tv) emptyList() else coroutineScope {
            val numbers = d.seasons.map { it.number }.filter { it > 0 }.ifEmpty { d.seasons.map { it.number } }
            val gate = Semaphore(4)
            numbers.map { n -> async { gate.withPermit { runCatching { container.tmdb.season(id, n) }.getOrNull() } } }
                .awaitAll().filterNotNull()
                .flatMap { sd -> sd.episodes.map { DetailEpisode(sd.number, it.number, it.name, TmdbImages.url(it.stillPath, TmdbImages.Size.Still), "") } }
        }
        return DetailState.Ready(
            DetailData(
                title = d.summary.title, plot = d.summary.overview.ifBlank { null },
                posterUrl = TmdbImages.url(d.summary.backdropPath ?: d.summary.posterPath, TmdbImages.Size.BackdropLarge),
                year = d.summary.year, tags = d.genres, isSeries = type == MediaType.Tv,
                episodes = episodes, movieDataUrl = null, tmdbId = id, imdbId = d.imdbId, type = type, fromTmdb = true,
                trailerKey = d.trailerKey, tmdbPosterPath = d.summary.posterPath,
                cast = d.cast, similar = d.similar, directors = d.directors, writers = d.writers, creators = d.creators,
            ),
        )
    }

    /** Play'deki PlayTarget'ın aynısı: oynatıcı ve izleme geçmişi bu kimlikle eşleşir. */
    fun target(providerId: String, url: String, d: DetailData, episode: DetailEpisode?): PlayTarget {
        if (d.fromTmdb) return PlayTarget(
            request = StreamRequest(
                tmdbId = d.tmdbId, imdbId = d.imdbId, type = d.type,
                season = episode?.season, episode = episode?.episode, title = d.title, year = d.year,
            ),
            title = d.title, posterPath = d.posterUrl,
        )
        val dataUrl = episode?.data ?: d.movieDataUrl ?: url
        return PlayTarget(
            request = StreamRequest(
                tmdbId = d.tmdbId, imdbId = d.imdbId, type = d.type,
                season = episode?.season, episode = episode?.episode,
                pluginId = providerId, dataUrl = dataUrl, contentUrl = url,
                title = d.title, year = d.year,
                addonContentId = if (!d.isSeries) dataUrl else null,
            ),
            title = d.title, posterPath = d.posterUrl,
        )
    }

}
