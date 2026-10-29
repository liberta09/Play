package com.kaan.play.data.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TMDB yanıt şemaları. Her alan bilerek nullable/varsayılanlı: eksik alan tüm yanıtı düşürmesin.

@Serializable
internal data class ListDto(
    val page: Int = 1,
    val results: List<ItemDto> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
internal data class FindResultDto(
    @SerialName("movie_results") val movieResults: List<ItemDto> = emptyList(),
    @SerialName("tv_results") val tvResults: List<ItemDto> = emptyList(),
)

@Serializable
internal data class ItemDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val popularity: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
)

@Serializable
internal data class DetailDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val seasons: List<SeasonDto> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
    val credits: CreditsDto? = null,
    val images: ImagesDto? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesDto? = null,
    @SerialName("content_ratings") val contentRatings: ContentRatingsDto? = null,
    @SerialName("created_by") val createdBy: List<PersonDto> = emptyList(),
    @SerialName("alternative_titles") val alternativeTitles: AlternativeTitlesDto? = null,
    val similar: ListDto? = null,
    val recommendations: ListDto? = null,
    val keywords: KeywordsDto? = null,
    val videos: VideosDto? = null,
)

@Serializable
internal data class KeywordsDto(
    val keywords: List<KeywordDto> = emptyList(),
    val results: List<KeywordDto> = emptyList(),
) {
    fun all(): List<KeywordDto> = keywords + results
}

@Serializable
internal data class KeywordDto(val id: Int = 0, val name: String = "")

@Serializable
internal data class VideosDto(val results: List<VideoDto> = emptyList())

@Serializable
internal data class VideoDto(
    val key: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean? = null,
)

/** Film: `titles`; dizi: `results`. Yalnızca eşleştirme için (arayüzde gösterilmez). */
@Serializable
internal data class AlternativeTitlesDto(
    val titles: List<AlternativeTitleDto> = emptyList(),
    val results: List<AlternativeTitleDto> = emptyList(),
)

@Serializable
internal data class AlternativeTitleDto(
    @SerialName("iso_3166_1") val country: String? = null,
    val title: String? = null,
)

@Serializable
internal data class PersonDto(val name: String? = null)

@Serializable
internal data class CreditsDto(val cast: List<CastDto> = emptyList(), val crew: List<CrewDto> = emptyList())

@Serializable
internal data class CastDto(
    val id: Int = 0,
    val name: String? = null,
    val character: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    val order: Int = Int.MAX_VALUE,
)

@Serializable
internal data class CrewDto(val name: String? = null, val job: String? = null)

@Serializable
internal data class ImagesDto(val logos: List<LogoDto> = emptyList())

@Serializable
internal data class LogoDto(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("iso_639_1") val language: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
internal data class ReleaseDatesDto(val results: List<ReleaseCountryDto> = emptyList())

@Serializable
internal data class ReleaseCountryDto(
    @SerialName("iso_3166_1") val country: String? = null,
    @SerialName("release_dates") val releases: List<ReleaseDto> = emptyList(),
)

@Serializable
internal data class ReleaseDto(val certification: String? = null)

@Serializable
internal data class ContentRatingsDto(val results: List<ContentRatingDto> = emptyList())

@Serializable
internal data class ContentRatingDto(@SerialName("iso_3166_1") val country: String? = null, val rating: String? = null)

@Serializable
internal data class GenreListDto(val genres: List<GenreIdDto> = emptyList())

@Serializable
internal data class GenreIdDto(val id: Int = 0, val name: String = "")

@Serializable
internal data class GenreDto(val id: Int = 0, val name: String = "")

@Serializable
internal data class ExternalIdsDto(@SerialName("imdb_id") val imdbId: String? = null)

@Serializable
internal data class SeasonDto(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
internal data class SeasonDetailDto(
    @SerialName("season_number") val seasonNumber: Int,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
internal data class EpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
)
