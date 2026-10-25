package com.kaan.play.data.tmdb

import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedMediaRankingTest {
    @Test
    fun filtersSelfWrongTypeAndGenreOnlyNoiseAndDeduplicatesSources() {
        val related = item(1, "Soruşturma", "Bir dedektif seri katilin izini sürerken gizemi çözer.", listOf(28, 80), rating = 7.8)
        val unrelatedPopular = item(2, "Köyde Yeni Hayat", "Bir aile yeni bir kasabaya taşınır ve komşularıyla tanışır.", listOf(28), rating = 9.7, popularity = 900.0)
        val genreOnlyNoise = item(5, "Alakasız tür benzerliği", "Bir şef uzak bir adada restoran açar.", listOf(28, 80))
        val wrongType = item(3, "Dizi", "Dedektif seri katil gizem soruşturması.", listOf(28, 80), type = MediaType.Tv)
        val source = item(99, "Dedektif", "Dedektif seri katil soruşturma gizem.", listOf(28, 80))

        val result = RelatedMediaRanking.rank(
            type = MediaType.Movie,
            sourceId = source.id,
            sourceTitle = source.title,
            sourceOverview = source.overview,
            sourceGenreIds = source.genreIds,
            discovered = emptyList(),
            recommendations = listOf(related, unrelatedPopular, wrongType, source),
            similar = listOf(related, genreOnlyNoise),
        )

        assertEquals(listOf(related), result)
    }

    @Test
    fun keywordDiscoveryCanAddStrongThemeMatchWithoutSynopsisWordOverlap() {
        val candidate = item(4, "Tema eşleşmesi", "A mysterious journey across distant worlds.", listOf(878))
        val result = RelatedMediaRanking.rank(
            type = MediaType.Movie,
            sourceId = 99,
            sourceTitle = "Rüya Hırsızı",
            sourceOverview = "Bir hırsız rüya zihin plan.",
            sourceGenreIds = listOf(878, 28),
            discovered = listOf(candidate),
            recommendations = emptyList(),
            similar = emptyList(),
            limit = 1,
        )

        assertEquals(listOf(candidate), result)
    }

    private fun item(
        id: Int,
        title: String,
        overview: String,
        genreIds: List<Int>,
        type: MediaType = MediaType.Movie,
        rating: Double? = null,
        popularity: Double? = null,
    ) = MediaSummary(
        id = id,
        type = type,
        title = title,
        overview = overview,
        posterPath = null,
        backdropPath = null,
        year = null,
        rating = rating,
        genreIds = genreIds,
        popularity = popularity,
    )
}
