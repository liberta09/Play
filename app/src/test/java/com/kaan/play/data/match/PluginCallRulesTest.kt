package com.kaan.play.data.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PluginCallRulesTest {
    @Test
    fun validMovieAndEpisodeCalls() {
        assertNull(PluginCallRules.problem("120", "movie", null, null, ContentKind.Movie))
        assertNull(PluginCallRules.problem("1399", "tv", 1, 2, ContentKind.Tv))
        assertNull(PluginCallRules.problem("1399", "series", 3, 10, ContentKind.Tv))
    }

    @Test
    fun badIdsAreRejected() {
        assertNotNull(PluginCallRules.problem("0", "movie", null, null, ContentKind.Movie))
        assertNotNull(PluginCallRules.problem("-5", "movie", null, null, ContentKind.Movie))
        assertNotNull(PluginCallRules.problem("tt0120737", "movie", null, null, ContentKind.Movie))
        assertNotNull(PluginCallRules.problem("", "movie", null, null, ContentKind.Movie))
    }

    @Test
    fun typeMustMatchContent() {
        assertNotNull(PluginCallRules.problem("120", "tv", null, null, ContentKind.Movie))
        assertNotNull(PluginCallRules.problem("120", "movie", 1, 1, ContentKind.Movie)) // filmde sezon/bolum yok
        assertNotNull(PluginCallRules.problem("1399", "movie", 1, 1, ContentKind.Tv))
        assertNotNull(PluginCallRules.problem("1399", "tv", null, null, ContentKind.Tv))
        assertNotNull(PluginCallRules.problem("1399", "tv", 1, 0, ContentKind.Tv))
        assertEquals("dizide geçerli sezon ve bölüm gerekli", PluginCallRules.problem("1399", "tv", 1, null, ContentKind.Tv))
    }
}
