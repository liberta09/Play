package com.kaan.play.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRepoRequestRulesTest {

    @Test
    fun rejectsNonHttpsUrls() {
        val candidates = listOf(
            PluginRepoItem("HTTP Repo", "http://example.com/repo.json"),
            PluginRepoItem("File Repo", "file:///sdcard/repo.json"),
            PluginRepoItem("HTTPS Repo", "https://example.com/repo.json"),
        )
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = emptySet(),
            declinedUrls = emptySet(),
            isBlockedHost = { _, _ -> false },
        )
        assertEquals(1, filtered.size)
        assertEquals("https://example.com/repo.json", filtered[0].url)
    }

    @Test
    fun deduplicatesUrls() {
        val candidates = listOf(
            PluginRepoItem("Repo 1", "https://example.com/repo.json"),
            PluginRepoItem("Repo 1 Dup", "https://example.com/repo.json/"),
            PluginRepoItem("Repo 1 Case", "HTTPS://EXAMPLE.COM/REPO.JSON"),
            PluginRepoItem("Repo 2", "https://example.org/repo.json"),
        )
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = emptySet(),
            declinedUrls = emptySet(),
            isBlockedHost = { _, _ -> false },
        )
        assertEquals(2, filtered.size)
        assertEquals("https://example.com/repo.json", filtered[0].url)
        assertEquals("https://example.org/repo.json", filtered[1].url)
    }

    @Test
    fun excludesInstalledAndDeclinedUrls() {
        val candidates = listOf(
            PluginRepoItem("Installed", "https://example.com/installed.json"),
            PluginRepoItem("Declined", "https://example.com/declined.json"),
            PluginRepoItem("Fresh", "https://example.com/fresh.json"),
        )
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = setOf("https://example.com/installed.json"),
            declinedUrls = setOf("https://example.com/declined.json/"),
            isBlockedHost = { _, _ -> false },
        )
        assertEquals(1, filtered.size)
        assertEquals("https://example.com/fresh.json", filtered[0].url)
    }

    @Test
    fun blocksPrivateAndLocalAddresses() {
        val candidates = listOf(
            PluginRepoItem("Localhost", "https://localhost/repo.json"),
            PluginRepoItem("Private IP", "https://192.168.1.1/repo.json"),
            PluginRepoItem("Valid Public", "https://raw.githubusercontent.com/repo.json"),
        )
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = emptySet(),
            declinedUrls = emptySet(),
            isBlockedHost = { host, _ ->
                host == "localhost" || host.startsWith("192.168.")
            },
        )
        assertEquals(1, filtered.size)
        assertEquals("https://raw.githubusercontent.com/repo.json", filtered[0].url)
    }

    @Test
    fun capsAtMax20Repos() {
        val candidates = (1..30).map {
            PluginRepoItem("Repo $it", "https://example.com/repo$it.json")
        }
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = emptySet(),
            declinedUrls = emptySet(),
            isBlockedHost = { _, _ -> false },
        )
        assertEquals(PluginRepoRequestRules.MAX_REPOS, filtered.size)
        assertEquals(20, filtered.size)
    }

    @Test
    fun returnsEmptyWhenAllFiltered() {
        val candidates = listOf(
            PluginRepoItem("Installed", "https://example.com/installed.json"),
        )
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = setOf("https://example.com/installed.json"),
            declinedUrls = emptySet(),
            isBlockedHost = { _, _ -> false },
        )
        assertTrue(filtered.isEmpty())
    }

    @Test
    fun rejectsUrlsWithUserInfo() {
        val candidates = listOf(
            PluginRepoItem("Spoofed Repo", "https://raw.githubusercontent.com@baska-site.com/x.json"),
            PluginRepoItem("User Pass Repo", "https://user:password@example.com/repo.json"),
            PluginRepoItem("Legit Repo", "https://raw.githubusercontent.com/user/repo/main/repo.json"),
        )
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = emptySet(),
            declinedUrls = emptySet(),
            isBlockedHost = { _, _ -> false },
        )
        assertEquals(1, filtered.size)
        assertEquals("https://raw.githubusercontent.com/user/repo/main/repo.json", filtered[0].url)
    }

    @Test
    fun userInfoUrlCannotEnterList() {
        val candidates = listOf(
            PluginRepoItem("Spoofed Repo", "https://raw.githubusercontent.com@baska-site.com/x.json"),
        )
        val filtered = PluginRepoRequestRules.filterRequests(
            candidates = candidates,
            installedUrls = emptySet(),
            declinedUrls = emptySet(),
            isBlockedHost = { _, _ -> false },
        )
        assertTrue(filtered.isEmpty())
    }
}
