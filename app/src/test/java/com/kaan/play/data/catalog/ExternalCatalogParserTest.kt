package com.kaan.play.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class ExternalCatalogParserTest {

    @Test
    fun testValidCatalogParsesCorrectly() {
        val json = """
            {
              "name": "Favori Filmler",
              "metas": [
                {
                  "id": "tt7286456",
                  "type": "movie",
                  "name": "Joker",
                  "poster": "https://image.tmdb.org/t/p/w500/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg"
                },
                {
                  "id": "tt0452046",
                  "type": "series",
                  "name": "Criminal Minds"
                },
                {
                  "id": "tmdb:155",
                  "type": "movie",
                  "name": "The Dark Knight",
                  "poster": "http://image.tmdb.org/t/p/w500/sample.jpg"
                }
              ]
            }
        """.trimIndent()

        val parsed = ExternalCatalogParser.parse(json)
        assertEquals("Favori Filmler", parsed.name)
        assertEquals(3, parsed.metas.size)
        assertEquals("tt7286456", parsed.metas[0].id)
        assertEquals("movie", parsed.metas[0].type)
        assertEquals("Joker", parsed.metas[0].name)
        assertEquals("https://image.tmdb.org/t/p/w500/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg", parsed.metas[0].poster)
        assertEquals("tt0452046", parsed.metas[1].id)
        assertEquals("series", parsed.metas[1].type)
        assertEquals(null, parsed.metas[1].poster)
        assertEquals("tmdb:155", parsed.metas[2].id)
    }

    @Test
    fun testBozukJsonThrowsInvalidJson() {
        val brokenJson = """{ "name": "Bozuk", "metas": [ { "id": "tt123", "type": "movie" """
        try {
            ExternalCatalogParser.parse(brokenJson)
            fail("Expected InvalidJson")
        } catch (e: ExternalCatalogException.InvalidJson) {
            assertEquals(ExternalCatalogException.Reason.InvalidJson, e.reason)
        }
    }

    @Test
    fun testTooLargeThrowsTooLarge() {
        val validJson = """{ "name": "Büyük", "metas": [ { "id": "tt1", "type": "movie", "name": "Film" } ] }"""
        try {
            ExternalCatalogParser.parse(validJson, byteSize = 1024 * 1024 + 1)
            fail("Expected TooLarge")
        } catch (e: ExternalCatalogException.TooLarge) {
            assertEquals(ExternalCatalogException.Reason.TooLarge, e.reason)
        }
    }

    @Test
    fun testTooManyItemsThrowsTooManyItems() {
        val metas = (1..501).joinToString(",") { i ->
            """{ "id": "tt$i", "type": "movie", "name": "Film $i" }"""
        }
        val json = """{ "name": "Çok Kalabalık", "metas": [ $metas ] }"""
        try {
            ExternalCatalogParser.parse(json)
            fail("Expected TooManyItems")
        } catch (e: ExternalCatalogException.TooManyItems) {
            assertEquals(ExternalCatalogException.Reason.TooManyItems, e.reason)
            assertEquals(501, e.count)
        }
    }

    @Test
    fun testEmptyMetasThrowsEmptyMetas() {
        val json = """{ "name": "Boş Liste", "metas": [] }"""
        try {
            ExternalCatalogParser.parse(json)
            fail("Expected EmptyMetas")
        } catch (e: ExternalCatalogException.EmptyMetas) {
            assertEquals(ExternalCatalogException.Reason.EmptyMetas, e.reason)
        }
    }

    @Test
    fun testMissingIdThrowsMissingId() {
        val json = """{ "name": "Eksik Id", "metas": [ { "id": "", "type": "movie", "name": "Film" } ] }"""
        try {
            ExternalCatalogParser.parse(json)
            fail("Expected MissingId")
        } catch (e: ExternalCatalogException.MissingId) {
            assertEquals(ExternalCatalogException.Reason.MissingId, e.reason)
        }
    }

    @Test
    fun testUnknownTypeThrowsUnknownType() {
        val json = """{ "name": "Bilinmeyen Tür", "metas": [ { "id": "tt123", "type": "anime", "name": "Test" } ] }"""
        try {
            ExternalCatalogParser.parse(json)
            fail("Expected UnknownType")
        } catch (e: ExternalCatalogException.UnknownType) {
            assertEquals(ExternalCatalogException.Reason.UnknownType, e.reason)
            assertEquals("anime", e.type)
        }
    }

    @Test
    fun testInvalidPosterThrowsInvalidPoster() {
        val json = """{ "name": "Geçersiz Afiş", "metas": [ { "id": "tt123", "type": "movie", "name": "Test", "poster": "ftp://example.com/poster.jpg" } ] }"""
        try {
            ExternalCatalogParser.parse(json)
            fail("Expected InvalidPoster")
        } catch (e: ExternalCatalogException.InvalidPoster) {
            assertEquals(ExternalCatalogException.Reason.InvalidPoster, e.reason)
        }
    }
}
