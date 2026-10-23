package com.kaan.play.ui.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDetailRulesTest {

    @Test
    fun `tv type is recognized as live and not series even if videos exist`() {
        assertTrue(ProviderDetailRules.isLive("tv", 5))
        assertFalse(ProviderDetailRules.isSeries("tv", 5))
    }

    @Test
    fun `channel type is recognized as live and not series`() {
        assertTrue(ProviderDetailRules.isLive("channel", 0))
        assertFalse(ProviderDetailRules.isSeries("channel", 0))
    }

    @Test
    fun `empty videos list is treated as live single-piece playable`() {
        assertTrue(ProviderDetailRules.isLive("series", 0))
        assertFalse(ProviderDetailRules.isSeries("series", 0))

        assertTrue(ProviderDetailRules.isLive("movie", 0))
        assertFalse(ProviderDetailRules.isSeries("movie", 0))
    }

    @Test
    fun `series type with episodes is recognized as series`() {
        assertFalse(ProviderDetailRules.isLive("series", 12))
        assertTrue(ProviderDetailRules.isSeries("series", 12))
    }

    @Test
    fun `movie type with videos is not live and not series`() {
        assertFalse(ProviderDetailRules.isLive("movie", 1))
        assertFalse(ProviderDetailRules.isSeries("movie", 1))
    }
}
