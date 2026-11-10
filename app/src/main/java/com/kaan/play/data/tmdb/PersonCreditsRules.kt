package com.kaan.play.data.tmdb

import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.PersonCredit
import com.kaan.play.data.model.PersonCreditSort
import com.kaan.play.data.model.PersonDetail

/**
 * TMDB kişi filmografisi kuralları (Android bağımsız):
 * - `adult == true` içerikler dışlanır (yetişkin modu kapsam dışı).
 * - Oyuncu ve ekip (cast + crew) arasında aynı içerik birden fazla rolle gelse bile tek kartta birleşir.
 * - Popülerlik ve yayın tarihine göre tutarlı sıralanır.
 */
internal object PersonCreditsRules {

    fun toPersonDetail(dto: PersonDetailDto, fallbackBiography: String? = null): PersonDetail {
        val raw = dto.combinedCredits?.let { it.cast + it.crew }.orEmpty()
        val deduped = linkedMapOf<Pair<MediaType, Int>, PersonCredit>()

        for (credit in raw) {
            if (credit.adult) continue
            if (credit.id <= 0) continue
            val type = MediaType.fromTmdb(credit.mediaType) ?: continue
            val title = (credit.title ?: credit.name)?.trim().orEmpty()
            if (title.isEmpty()) continue

            val rawDate = (credit.releaseDate ?: credit.firstAirDate)?.trim()?.takeIf { it.isNotEmpty() }
            val popularity = (credit.popularity ?: 0.0).coerceAtLeast(0.0)
            val key = type to credit.id
            val candidate = PersonCredit(
                media = MediaSummary(
                    id = credit.id,
                    type = type,
                    title = title,
                    overview = credit.overview.orEmpty(),
                    posterPath = credit.posterPath?.takeIf { it.isNotBlank() },
                    backdropPath = credit.backdropPath?.takeIf { it.isNotBlank() },
                    year = rawDate?.take(4)?.toIntOrNull(),
                    rating = credit.voteAverage?.takeIf { it > 0.0 },
                    popularity = popularity.takeIf { it > 0.0 },
                ),
                releaseDate = rawDate,
                popularity = popularity,
            )

            val existing = deduped[key]
            if (existing == null) {
                deduped[key] = candidate
            } else {
                deduped[key] = PersonCredit(
                    media = existing.media.copy(
                        posterPath = existing.media.posterPath ?: candidate.media.posterPath,
                        backdropPath = existing.media.backdropPath ?: candidate.media.backdropPath,
                        year = existing.media.year ?: candidate.media.year,
                        rating = existing.media.rating ?: candidate.media.rating,
                    ),
                    releaseDate = existing.releaseDate ?: candidate.releaseDate,
                    popularity = maxOf(existing.popularity, candidate.popularity),
                )
            }
        }

        val all = deduped.values.toList()
        val bio = dto.biography?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackBiography?.trim().orEmpty()
        return PersonDetail(
            id = dto.id,
            name = dto.name?.trim().orEmpty(),
            biography = bio,
            profilePath = dto.profilePath?.takeIf { it.isNotBlank() },
            movieCredits = all.filter { it.media.type == MediaType.Movie },
            tvCredits = all.filter { it.media.type == MediaType.Tv },
        )
    }

    fun sort(credits: List<PersonCredit>, sort: PersonCreditSort): List<PersonCredit> = when (sort) {
        PersonCreditSort.Popular -> credits.sortedWith(
            compareByDescending<PersonCredit> { it.popularity }
                .thenByDescending { it.media.rating ?: 0.0 }
                .thenBy { it.media.title },
        )
        PersonCreditSort.Date -> credits.sortedWith(
            compareByDescending<PersonCredit> { it.releaseDate.orEmpty() }
                .thenByDescending { it.popularity }
                .thenBy { it.media.title },
        )
    }
}
