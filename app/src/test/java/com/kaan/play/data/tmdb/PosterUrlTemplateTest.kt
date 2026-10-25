package com.kaan.play.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosterUrlTemplateTest {

    @Test
    fun defaultPosterTemplateIsPrefilledAndBuildsPosterUrl() {
        assertEquals(
            "https://btttr.cc/poster-qa/imdb/poster-default/{imdb_id}.jpg?lang=tr",
            PosterUrlTemplate.DEFAULT,
        )
        assertEquals(
            "https://btttr.cc/poster-qa/imdb/poster-default/tt1234567.jpg?lang=tr",
            PosterUrlTemplate.build(PosterUrlTemplate.DEFAULT, "tt1234567"),
        )
    }

    @Test
    fun buildsUrlFromPlainOrEmbeddedImdbId() {
        val template = "https://images.example/{imdb_id}.jpg"

        assertEquals("https://images.example/tt1234567.jpg", PosterUrlTemplate.build(template, "tt1234567"))
        assertEquals(
            "https://images.example/tt7654321.jpg",
            PosterUrlTemplate.build(template, "https://provider.example/title/tt7654321/season-1"),
        )
    }

    @Test
    fun findsIdInAnyReferenceAndNormalizesCase() {
        assertEquals(
            "tt12345678",
            PosterUrlTemplate.findImdbId("provider:xyz", "https://cdn.example/poster/TT12345678.webp"),
        )
    }

    @Test
    fun extractsTmdbIdFromNuvioPrefixedIdsAndUrls() {
        assertEquals(1514026, PosterUrlTemplate.findTmdbId("tmdb:1514026"))
        assertEquals(98765, PosterUrlTemplate.findTmdbId("https://addon.example/catalog/tv/Show/tmdb%3A98765"))
        assertEquals(42, PosterUrlTemplate.findTmdbId("https://addon.example/item/tmdb://42?lang=tr"))
        assertNull(PosterUrlTemplate.findTmdbId("provider:1514026"))
    }

    @Test
    fun rejectsMissingOrMalformedIdsAndInvalidTemplate() {
        assertNull(PosterUrlTemplate.build("https://images.example/{imdb_id}.jpg", "not-an-imdb-id"))
        assertNull(PosterUrlTemplate.build("http://images.example/{imdb_id}.jpg", "tt1234567"))
        assertNull(PosterUrlTemplate.findImdbId("no-id-here"))
    }
}
