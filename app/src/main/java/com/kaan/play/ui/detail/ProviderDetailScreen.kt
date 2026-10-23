package com.kaan.play.ui.detail

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kaan.play.R
import com.kaan.play.data.cloudstream.CsApiResult
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.SeasonInfo
import com.kaan.play.data.stream.PlayTarget
import com.kaan.play.data.stream.StreamRequest
import com.kaan.play.ui.components.CenteredLoading
import com.kaan.play.ui.components.Loadable
import com.kaan.play.ui.components.StatusBarScrim
import com.kaan.play.ui.tv.focusRing
import com.kaan.play.ui.nav.PlayIcons
import com.kaan.play.ui.rememberContainer
import com.kaan.play.ui.tv.rememberPlayLayout
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.kaan.play.data.net.PlayJson
import com.kaan.play.data.stream.fetchAddonText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class StremioMetaDetailResponse(
    val meta: StremioMetaDetail? = null,
)

@Serializable
private data class StremioMetaDetail(
    val id: String,
    val type: String = "movie",
    val name: String,
    val genres: List<String> = emptyList(),
    val poster: String? = null,
    val background: String? = null,
    val banner: String? = null,
    val backdrop: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val year: String? = null,
    val imdbRating: String? = null,
    val videos: List<StremioMetaVideo> = emptyList(),
)

@Serializable
private data class StremioMetaVideo(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
)

private data class ProviderEpisodeItem(
    val data: String,
    val name: String?,
    val season: Int?,
    val episode: Int?,
    val description: String?,
    val posterUrl: String?,
    val runTime: Int? = null,
)

private sealed interface ProviderDetailUiState {
    data object Loading : ProviderDetailUiState
    data class Error(val messageRes: Int, val fallbackText: String? = null) : ProviderDetailUiState
    data class Ready(
        val detail: MediaDetail,
        val isSeries: Boolean,
        val episodes: List<ProviderEpisodeItem>,
        val movieDataUrl: String?,
        val syntheticId: Int,
        val addonType: String? = null,
        val addonContentId: String? = null,
    ) : ProviderDetailUiState
}

/**
 * Sağlayıcı Detay Ekranı (Faz D / Aşama 2):
 * Eklentinin load(url) sonucunu mevcut Detay bileşenleriyle gösterir.
 * TMDB'ye gitmez. Oynat basılınca doğrudan eklentinin loadLinks akışına gider.
 */
@Composable
fun ProviderDetailScreen(
    providerId: String,
    url: String,
    onBack: () -> Unit,
    onPlay: (PlayTarget) -> Unit,
) {
    val container = rememberContainer()
    val layout = rememberPlayLayout()
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext

    var state by remember(providerId, url) { mutableStateOf<ProviderDetailUiState>(ProviderDetailUiState.Loading) }
    var selectedSeason by remember(providerId, url) { mutableStateOf<Int?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val syntheticId = remember(providerId, url) {
        -(abs("$providerId:$url".hashCode()).coerceAtLeast(1))
    }

    LaunchedEffect(providerId, url, refreshKey) {
        state = ProviderDetailUiState.Loading
        val loadedState: ProviderDetailUiState = withContext(Dispatchers.IO) {
            try {
                if (providerId.startsWith("nuvio_addon:")) {
                    val addonRowId = providerId.removePrefix("nuvio_addon:").toLongOrNull() ?: 0L
                    val addon = container.addons.byId(addonRowId)
                        ?: return@withContext ProviderDetailUiState.Error(R.string.provider_error_not_found)

                    val hasMeta = addon.manifest.resources.any { it.name == com.kaan.play.data.addon.AddonManifest.META }
                    val pathParts = if (url.contains('/')) url.split('/', limit = 2) else listOf("movie", url)
                    val parsedType = pathParts.getOrNull(0) ?: "movie"
                    val parsedId = pathParts.getOrNull(1) ?: url
                    val isLiveType = parsedType.lowercase() in listOf("tv", "channel")

                    if (!hasMeta && isLiveType) {
                        val summary = MediaSummary(
                            id = syntheticId,
                            type = MediaType.Movie,
                            title = parsedId,
                            overview = "",
                            posterPath = null,
                            backdropPath = null,
                            year = null,
                            rating = null,
                        )
                        val detail = MediaDetail(
                            summary = summary,
                            imdbId = if (parsedId.startsWith("tt")) parsedId else null,
                            tagline = "",
                            genres = emptyList(),
                            runtimeMinutes = null,
                            seasons = emptyList(),
                        )
                        return@withContext ProviderDetailUiState.Ready(
                            detail = detail,
                            isSeries = false,
                            episodes = emptyList(),
                            movieDataUrl = parsedId,
                            syntheticId = syntheticId,
                            addonType = parsedType.lowercase(),
                            addonContentId = parsedId,
                        )
                    }

                    val metaUrl = when {
                        url.startsWith("http://") || url.startsWith("https://") -> url
                        url.contains('/') -> "${addon.baseUrl}/meta/$url.json"
                        else -> "${addon.baseUrl}/meta/movie/$url.json"
                    }
                    val httpUrl = metaUrl.toHttpUrlOrNull()
                        ?: return@withContext ProviderDetailUiState.Error(R.string.provider_error_invalid_meta_url)

                    val jsonText = fetchAddonText(
                        container.httpClient,
                        httpUrl,
                        limit = 15.seconds,
                        maxBytes = 5L * 1024 * 1024,
                    )
                    val resp = PlayJson.decodeFromString(StremioMetaDetailResponse.serializer(), jsonText)
                    val meta = resp.meta ?: return@withContext ProviderDetailUiState.Error(R.string.provider_error_meta_not_found)

                    val isLive = meta.type.lowercase() in listOf("tv", "channel") || meta.videos.isEmpty()
                    val isSeries = !isLive && meta.type.lowercase() == "series"
                    val episodes: List<ProviderEpisodeItem> = if (isSeries) {
                        meta.videos.map { v ->
                            ProviderEpisodeItem(
                                data = v.id,
                                name = v.title ?: v.name,
                                season = v.season ?: 1,
                                episode = v.episode ?: 1,
                                description = v.overview,
                                posterUrl = v.thumbnail,
                            )
                        }
                    } else {
                        emptyList()
                    }

                    val seasonNums = if (isSeries) {
                        episodes.map { it.season ?: 1 }.distinct().sorted()
                    } else {
                        emptyList()
                    }

                    val seasonInfos = seasonNums.map { sNum ->
                        SeasonInfo(
                            number = sNum,
                            name = context.getString(R.string.season_number_format, sNum),
                            episodeCount = episodes.count { (it.season ?: 1) == sNum },
                            posterPath = null,
                        )
                    }

                    val mediaType = if (isSeries) MediaType.Tv else MediaType.Movie
                    val summary = MediaSummary(
                        id = syntheticId,
                        type = mediaType,
                        title = meta.name,
                        overview = meta.description.orEmpty(),
                        posterPath = meta.poster,
                        backdropPath = meta.background ?: meta.backdrop ?: meta.banner ?: meta.poster,
                        year = meta.releaseInfo?.take(4)?.toIntOrNull() ?: meta.year?.take(4)?.toIntOrNull(),
                        rating = meta.imdbRating?.toDoubleOrNull(),
                    )

                    val detail = MediaDetail(
                        summary = summary,
                        imdbId = if (meta.id.startsWith("tt")) meta.id else null,
                        tagline = "",
                        genres = meta.genres,
                        runtimeMinutes = null,
                        seasons = seasonInfos,
                    )

                    if (selectedSeason == null && seasonNums.isNotEmpty()) {
                        selectedSeason = seasonNums.first()
                    }

                    val resolvedAddonType = when {
                        isLive -> if (meta.type.lowercase() in listOf("tv", "channel")) meta.type.lowercase() else parsedType.lowercase()
                        isSeries -> "series"
                        else -> "movie"
                    }
                    val resolvedContentId = meta.id.ifBlank { parsedId }

                    ProviderDetailUiState.Ready(
                        detail = detail,
                        isSeries = isSeries,
                        episodes = episodes,
                        movieDataUrl = meta.id,
                        syntheticId = syntheticId,
                        addonType = resolvedAddonType,
                        addonContentId = resolvedContentId,
                    )
                } else if (providerId.startsWith("cs:")) {
                    val pluginRowId = providerId.removePrefix("cs:").toLongOrNull() ?: 0L
                    val result = container.csRepos.loadApis(pluginRowId)
                    val apis = result.apisOrEmpty()
                    val api = apis.firstOrNull()
                    if (api == null) {
                        val errorRes = when (result) {
                            is CsApiResult.DexFileMissing -> R.string.provider_error_dex_missing
                            is CsApiResult.IncompatibleLibrary -> R.string.provider_error_incompatible
                            is CsApiResult.LoadFailed -> R.string.provider_error_load_failed
                            is CsApiResult.PluginRowMissing -> R.string.provider_error_not_found
                            is CsApiResult.NoApiRegistered,
                            is CsApiResult.Success -> R.string.provider_error_no_api
                        }
                        return@withContext ProviderDetailUiState.Error(errorRes)
                    }

                    val loadResp: LoadResponse? = try {
                        api.load(url)
                    } catch (t: Throwable) {
                        Log.e("ProviderDetail", "api.load failed: ${t.message}", t)
                        null
                    }

                    if (loadResp == null) {
                        return@withContext ProviderDetailUiState.Error(R.string.provider_error_content_load_failed)
                    }

                    val isSeries = loadResp is TvSeriesLoadResponse || loadResp is AnimeLoadResponse
                    val rawEpisodes: List<com.lagradost.cloudstream3.Episode> = when (loadResp) {
                        is TvSeriesLoadResponse -> loadResp.episodes
                        is AnimeLoadResponse -> loadResp.episodes.values.flatten()
                        else -> emptyList()
                    }
                    val episodes: List<ProviderEpisodeItem> = rawEpisodes.map {
                        ProviderEpisodeItem(
                            data = it.data,
                            name = it.name,
                            season = it.season,
                            episode = it.episode,
                            description = it.description,
                            posterUrl = it.posterUrl,
                            runTime = it.runTime,
                        )
                    }

                    val movieDataUrl = when (loadResp) {
                        is MovieLoadResponse -> loadResp.dataUrl
                        is LiveStreamLoadResponse -> loadResp.dataUrl
                        else -> url
                    }

                    val seasonNums = if (isSeries) {
                        episodes.map { it.season ?: 1 }.distinct().sorted()
                    } else {
                        emptyList()
                    }

                    val seasonInfos = seasonNums.map { sNum ->
                        SeasonInfo(
                            number = sNum,
                            name = context.getString(R.string.season_number_format, sNum),
                            episodeCount = episodes.count { (it.season ?: 1) == sNum },
                            posterPath = null,
                        )
                    }

                    val mediaType = if (isSeries) MediaType.Tv else MediaType.Movie
                    val identity = com.kaan.play.data.stream.MediaIdentityResolver.resolveIdentity(
                        rawTitle = loadResp.name,
                        rawYear = loadResp.year,
                        syncData = runCatching { loadResp.syncData }.getOrNull(),
                        type = mediaType,
                        tmdb = container.tmdb,
                    )
                    val resolvedTmdbId = identity.tmdbId ?: syntheticId
                    val summary = MediaSummary(
                        id = resolvedTmdbId,
                        type = mediaType,
                        title = identity.cleanTitle,
                        overview = loadResp.plot.orEmpty(),
                        posterPath = loadResp.posterUrl,
                        backdropPath = loadResp.posterUrl,
                        year = identity.year ?: loadResp.year,
                        rating = null,
                    )

                    val detail = MediaDetail(
                        summary = summary,
                        imdbId = identity.imdbId,
                        tagline = "",
                        genres = loadResp.tags ?: emptyList(),
                        runtimeMinutes = null,
                        seasons = seasonInfos,
                    )

                    if (selectedSeason == null && seasonNums.isNotEmpty()) {
                        selectedSeason = seasonNums.first()
                    }

                    ProviderDetailUiState.Ready(
                        detail = detail,
                        isSeries = isSeries,
                        episodes = episodes,
                        movieDataUrl = movieDataUrl,
                        syntheticId = syntheticId,
                    )
                } else {
                    ProviderDetailUiState.Error(R.string.provider_error_not_found)
                }
            } catch (t: Throwable) {
                Log.e("ProviderDetail", "Error in ProviderDetailScreen", t)
                ProviderDetailUiState.Error(R.string.provider_error_unexpected)
            }
        }
        state = loadedState
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is ProviderDetailUiState.Loading -> CenteredLoading()
            is ProviderDetailUiState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(s.messageRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = { refreshKey++ },
                        modifier = Modifier.focusRing(MaterialTheme.shapes.medium),
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            is ProviderDetailUiState.Ready -> {
                val currentEpisodes = if (s.isSeries) {
                    val sNum = selectedSeason ?: 1
                    val filtered = s.episodes.filter { (it.season ?: 1) == sNum }
                    filtered.mapIndexed { idx, ep ->
                        val epNumber = ep.episode ?: (idx + 1)
                        Episode(
                            number = epNumber,
                            name = ep.name.orEmpty(),
                            overview = ep.description.orEmpty(),
                            stillPath = ep.posterUrl,
                            runtimeMinutes = ep.runTime,
                            airDate = null,
                        )
                    }
                } else {
                    emptyList()
                }

                DetailContent(
                    detail = s.detail,
                    isFavorite = false,
                    selectedSeason = selectedSeason ?: s.detail.seasons.firstOrNull()?.number ?: 1,
                    episodes = Loadable.Ready(currentEpisodes),
                    twoPane = layout.twoPane,
                    screenHeight = layout.heightDp.dp,
                    onToggleFavorite = { /* Favori */ },
                    onSelectSeason = { sNum -> selectedSeason = sNum },
                    onRetryEpisodes = { refreshKey++ },
                    onPlay = { season, episode ->
                        val dataUrl = if (s.isSeries) {
                            val sNum = season ?: selectedSeason ?: 1
                            val ep = s.episodes.firstOrNull {
                                (it.season ?: 1) == sNum && (it.episode == episode || (episode == null))
                            } ?: s.episodes.firstOrNull()
                            ep?.data ?: url
                        } else {
                            s.movieDataUrl ?: url
                        }

                        val target = PlayTarget(
                            request = StreamRequest(
                                tmdbId = s.detail.summary.id,
                                imdbId = s.detail.imdbId,
                                type = if (s.isSeries || s.addonType in listOf("tv", "channel")) MediaType.Tv else s.detail.summary.type,
                                season = if (s.isSeries) (season ?: selectedSeason) else null,
                                episode = if (s.isSeries) episode else null,
                                pluginId = providerId,
                                dataUrl = dataUrl,
                                contentUrl = url,
                                title = s.detail.summary.title,
                                year = s.detail.summary.year,
                                addonType = s.addonType,
                                addonContentId = s.addonContentId ?: if (!s.isSeries) (s.movieDataUrl ?: dataUrl) else null,
                            ),
                            title = s.detail.summary.title,
                            posterPath = s.detail.summary.posterPath,
                        )
                        onPlay(target)
                    },
                )
            }
        }

        StatusBarScrim()

        // Geri oku: kapağın üstünde de okunsun diye yarı saydam daire içinde modern buton.
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .size(40.dp)
                .focusRing(CircleShape),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = PlayIcons.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
