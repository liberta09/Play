package com.kaan.play.data.addon

import com.kaan.play.data.addon.AddonException.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AddonManifestParserTest {

    private fun fails(kind: Kind, detail: String? = null, json: String) {
        try {
            AddonManifestParser.parse(json)
            fail("AddonException($kind) bekleniyordu")
        } catch (e: AddonException) {
            assertEquals(kind, e.kind)
            if (detail != null) assertEquals(detail, e.detail)
        }
    }

    @Test
    fun parsesMinimalManifestWithStringResources() {
        val m = AddonManifestParser.parse(
            """{"id":"org.example.src","name":"Örnek","version":"1.2.3","types":["movie","series"],"resources":["catalog","stream"]}""",
        )
        assertEquals("org.example.src", m.id)
        assertEquals("Örnek", m.name)
        assertEquals("1.2.3", m.version)
        assertEquals(listOf("movie", "series"), m.types)
        assertEquals(listOf("catalog", "stream"), m.resources.map { it.name })
        assertNull(m.idPrefixes)
    }

    @Test
    fun parsesObjectAndMixedResourcesAndKeepsOverrides() {
        val m = AddonManifestParser.parse(
            """
            {"id":"a","name":"B","types":["movie","series","anime"],"idPrefixes":["tt"],
             "resources":["catalog",
               {"name":"stream","types":["movie"],"idPrefixes":["tt","kitsu:"]},
               {"name":"meta"}],
             "catalogs":[{"type":"movie","id":"top"}],"behaviorHints":{"configurable":true},"unknown":123}
            """,
        )
        val stream = m.resources.single { it.name == "stream" }
        assertEquals(listOf("movie"), stream.types)
        assertEquals(listOf("tt", "kitsu:"), stream.idPrefixes)
        assertEquals(listOf("tt"), m.idPrefixes)
        assertNull(m.resources.single { it.name == "meta" }.types)
    }

    @Test
    fun trimsAndKeepsOptionalInfoOnlyWhenTextual() {
        val m = AddonManifestParser.parse(
            """{"id":" a ","name":" B ","description":"  ","logo":5,"version":"1","types":["movie"],"resources":["stream"]}""",
        )
        assertEquals("a", m.id)
        assertEquals("B", m.name)
        assertNull(m.description)
        assertNull(m.logo)
    }

    @Test
    fun rejectsNonJsonAndNonObject() {
        fails(Kind.NotJson, json = "<html>404</html>")
        fails(Kind.NotJson, json = "")
        fails(Kind.NotJson, json = "[1,2,3]")
        fails(Kind.NotJson, json = "\"just a string\"")
    }

    @Test
    fun reportsMissingRequiredFields() {
        fails(Kind.MissingField, "id", """{"name":"B","types":["movie"],"resources":["stream"]}""")
        fails(Kind.MissingField, "id", """{"id":"  ","name":"B","types":["movie"],"resources":["stream"]}""")
        fails(Kind.MissingField, "name", """{"id":"a","types":["movie"],"resources":["stream"]}""")
        fails(Kind.MissingField, "types", """{"id":"a","name":"B","resources":["stream"]}""")
        fails(Kind.MissingField, "types", """{"id":"a","name":"B","types":[],"resources":["stream"]}""")
        fails(Kind.MissingField, "resources", """{"id":"a","name":"B","types":["movie"]}""")
        fails(Kind.MissingField, "resources", """{"id":"a","name":"B","types":["movie"],"resources":[]}""")
        fails(Kind.MissingField, "id", """{"id":null,"name":"B","types":["movie"],"resources":["stream"]}""")
    }

    @Test
    fun reportsWrongFieldShapes() {
        fails(Kind.InvalidField, "id", """{"id":5,"name":"B","types":["movie"],"resources":["stream"]}""")
        fails(Kind.InvalidField, "name", """{"id":"a","name":{"x":1},"types":["movie"],"resources":["stream"]}""")
        fails(Kind.InvalidField, "types", """{"id":"a","name":"B","types":"movie","resources":["stream"]}""")
        fails(Kind.InvalidField, "types", """{"id":"a","name":"B","types":[1],"resources":["stream"]}""")
        fails(Kind.InvalidField, "resources", """{"id":"a","name":"B","types":["movie"],"resources":"stream"}""")
        fails(Kind.InvalidField, "resources", """{"id":"a","name":"B","types":["movie"],"resources":[42]}""")
        fails(Kind.InvalidField, "resources", """{"id":"a","name":"B","types":["movie"],"resources":[{"types":["movie"]}]}""")
        fails(Kind.InvalidField, "resources", """{"id":"a","name":"B","types":["movie"],"resources":[""]}""")
        fails(Kind.InvalidField, "idPrefixes", """{"id":"a","name":"B","types":["movie"],"idPrefixes":"tt","resources":["stream"]}""")
    }

    @Test
    fun rejectsAddonsWithoutStreamOrSubtitlesOrCatalogsResource() {
        fails(Kind.NoStreamResource, json = """{"id":"a","name":"B","types":["movie"],"resources":["meta"]}""")
        fails(Kind.NoStreamResource, json = """{"id":"a","name":"B","types":["movie"],"resources":["catalog","meta"]}""")
        fails(Kind.NoStreamResource, json = """{"id":"a","name":"B","types":["movie"],"resources":["catalog","meta"],"catalogs":[]}""")
    }

    @Test
    fun acceptsCatalogAddonsWhenCatalogsIsNotEmpty() {
        val m = AddonManifestParser.parse(
            """{"id":"org.jasoelys.catalog","name":"Jasoelys","types":["movie","series"],"resources":["catalog","meta"],"idPrefixes":["tt","tmdb:"],"catalogs":[{"type":"movie","id":"top"}]}""",
        )
        assertEquals("org.jasoelys.catalog", m.id)
        assertTrue(m.providesCatalogs)
        assertFalse(m.providesStreams)
        assertFalse(m.providesSubtitles)
        assertFalse(m.supportsStream("movie", "tmdb:123"))
        assertEquals(1, m.catalogs.size)
    }

    @Test
    fun acceptsSubtitleOnlyAddonsButTheyProvideNoStreams() {
        val m = AddonManifestParser.parse("""{"id":"a","name":"B","types":["movie","series"],"resources":["subtitles"],"idPrefixes":["tt"]}""")
        assertTrue(m.supportsSubtitles("series", "tt1:1:1"))
        assertFalse(m.supportsSubtitles("series", "kitsu:1"))
        assertFalse(m.supportsStream("movie", "tt1")) // kaynak listesinde sorulmaz
        assertFalse(m.providesStreams)

        val both = AddonManifestParser.parse("""{"id":"a","name":"B","types":["movie"],"resources":["stream","subtitles"]}""")
        assertTrue(both.providesStreams)
        assertTrue(both.supportsSubtitles("movie", "tt1"))
    }

    @Test
    fun subtitleOnlyAddonMustServeMoviesOrSeries() {
        fails(Kind.UnsupportedTypes, json = """{"id":"a","name":"B","types":["tv"],"resources":["subtitles"]}""")
        // Kaynak düzeyindeki types manifest düzeyindekini geçersiz kılar.
        val m = AddonManifestParser.parse("""{"id":"a","name":"B","types":["tv"],"resources":[{"name":"subtitles","types":["movie"]}]}""")
        assertTrue(m.supportsSubtitles("movie", "tt1"))
        assertFalse(m.supportsSubtitles("series", "tt1:1:1"))
    }

    @Test
    fun rejectsStreamAddonsThatServeNeitherMoviesNorSeries() {
        fails(Kind.UnsupportedTypes, json = """{"id":"a","name":"B","types":["tv","channel"],"resources":["stream"]}""")
        // Kaynak düzeyindeki types, manifest düzeyindekini geçersiz kılar.
        fails(Kind.UnsupportedTypes, json = """{"id":"a","name":"B","types":["movie"],"resources":[{"name":"stream","types":["tv"]}]}""")
    }

    @Test
    fun acceptsStreamForSeriesOnly() {
        val m = AddonManifestParser.parse("""{"id":"a","name":"B","types":["series"],"resources":["stream"]}""")
        assertTrue(m.supportsStream("series", "tt1:1:1"))
    }

    @Test
    fun acceptsMultiCatalogManifestWithDifferentTypes() {
        val json = """
        {
            "id": "org.wiospor",
            "name": "WioSpor",
            "types": ["tv", "channel", "movie"],
            "resources": ["stream", "catalog"],
            "catalogs": [
                {"type": "tv", "id": "canli-tv", "name": "Canlı Kanallar"},
                {"type": "tv", "id": "spor", "name": "Spor Kanalları"},
                {"type": "movie", "id": "filmler"}
            ]
        }
        """.trimIndent()
        val m = AddonManifestParser.parse(json)
        assertEquals(3, m.catalogs.size)
        assertEquals("canli-tv", m.catalogs[0].id)
        assertEquals("Canlı Kanallar", m.catalogs[0].name)
        assertEquals("tv", m.catalogs[0].type)
        assertEquals("spor", m.catalogs[1].id)
        assertEquals("filmler", m.catalogs[2].id)
        assertEquals("movie", m.catalogs[2].type)
        assertNull(m.catalogs[2].name)
        assertTrue(m.providesCatalogs)
        assertTrue(m.providesStreams)
    }

    @Test
    fun parsesAddonWithoutCatalogsCorrectly() {
        val json = """
        {
            "id": "org.stream.only",
            "name": "Stream Only",
            "types": ["movie", "series"],
            "resources": ["stream"]
        }
        """.trimIndent()
        val m = AddonManifestParser.parse(json)
        assertTrue(m.catalogs.isEmpty())
        assertFalse(m.providesCatalogs)
        assertTrue(m.providesStreams)
    }
}
