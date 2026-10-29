package com.kaan.play.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoManagerCatalogTest {
    @Test
    fun mapsAllSupportedPlatformKindsAndFiltersUnsafeRows() {
        val json = """
            {"version":1,"updatedAt":"now","repos":[
              {"id":"1","name":"CS","url":"https://example.org/repo.json","category":"Türkçe","type":"cloudstream","platform":"cloudstream"},
              {"id":"2","name":"Nuvio plugin","url":"https://example.org/plugins/manifest.json","category":"Plugin","type":"nuvio","platform":"nuvio","nuvioKind":"plugin"},
              {"id":"3","name":"Nuvio addon","url":"https://example.org/addon/manifest.json","category":"Addon","type":"nuvio","platform":"nuvio","nuvioKind":"addon"},
              {"id":"4","name":"HTTP unsafe","url":"http://example.org/x","type":"cloudstream"},
              {"id":"5","name":"Unknown type","url":"https://example.org/x","type":"other"},
              {"id":"6","name":"Duplicate","url":"https://example.org/repo.json","type":"cloudstream"}
            ]}
        """.trimIndent()

        val actual = RepoManagerCatalog.parse(json)
        assertEquals(3, actual.size)
        assertEquals(listOf(SourceType.CLOUDSTREAM, SourceType.NUVIO_PLUGIN, SourceType.NUVIO_REPO), actual.map { it.sourceType })
        assertEquals(listOf("Türkçe", "Plugin", "Addon"), actual.map { it.category })
        assertTrue(actual.all { it.url.startsWith("https://") })
    }

    @Test
    fun malformedOrMissingCatalogReturnsEmptyList() {
        assertTrue(RepoManagerCatalog.parse("not-json").isEmpty())
        assertTrue(RepoManagerCatalog.parse("{\"repos\":[]}").isEmpty())
    }
}
