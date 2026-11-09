package com.kaan.play.ui.addons

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkHostTest {

    @Test
    fun formatRepoUrlForDisplayReturnsHostAndPathForNormalUrl() {
        assertEquals(
            "example.com/repo.json",
            formatRepoUrlForDisplay("https://example.com/repo.json"),
        )
        assertEquals(
            "example.com/a/b",
            formatRepoUrlForDisplay("https://example.com/a/b"),
        )
        assertEquals(
            "example.com/a/b/...",
            formatRepoUrlForDisplay("https://example.com/a/b/c/d"),
        )
    }

    @Test
    fun formatRepoUrlForDisplayShowsRealHostFirstForUserInfoUrl() {
        assertEquals(
            "baska-site.com/x.json",
            formatRepoUrlForDisplay("https://raw.githubusercontent.com@baska-site.com/x.json"),
        )
    }

    @Test
    fun formatRepoUrlForDisplayReturnsEmptyWhenUriCannotBeResolved() {
        assertEquals("", formatRepoUrlForDisplay("not a valid uri :::"))
        assertEquals("", formatRepoUrlForDisplay("https://"))
        assertEquals("", formatRepoUrlForDisplay(""))
    }
}
