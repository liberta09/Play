package com.kaan.play.data.cloudstream

import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsRuntimeTypeRulesTest {
    @Test
    fun animeOnlyProviderIsRecognizedFromDeclaredSupportedTypes() {
        assertTrue(isAnimeOnlyProvider(setOf(TvType.Anime)))
        assertTrue(isAnimeOnlyProvider(setOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)))
    }

    @Test
    fun mixedOrNonAnimeProviderIsNotClassifiedAsAnimeOnly() {
        assertFalse(isAnimeOnlyProvider(setOf(TvType.Anime, TvType.TvSeries)))
        assertFalse(isAnimeOnlyProvider(setOf(TvType.Movie, TvType.TvSeries)))
        assertFalse(isAnimeOnlyProvider(emptySet()))
    }
}
