package com.kaan.play.data.match

import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaType

/** TMDB detayından doğrulama kimliği: [season]/[episode] istenen bölümdür (dizide). */
fun MediaDetail.toContentIdentity(season: Int? = null, episode: Int? = null): ContentIdentity = ContentIdentity.of(
    kind = if (summary.type == MediaType.Tv) ContentKind.Tv else ContentKind.Movie,
    title = summary.title,
    originalTitle = originalTitle,
    alternatives = alternativeTitles,
    year = summary.year,
    tmdbId = summary.id,
    imdbId = imdbId,
    runtimeMinutes = runtimeMinutes,
    season = season,
    episode = episode,
    isAnimation = summary.genreIds.takeIf { it.isNotEmpty() }?.contains(TMDB_ANIMATION_GENRE_ID),
)

private const val TMDB_ANIMATION_GENRE_ID = 16
