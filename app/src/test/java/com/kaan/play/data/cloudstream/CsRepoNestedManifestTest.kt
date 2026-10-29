package com.kaan.play.data.cloudstream

import org.junit.Assert.assertEquals
import org.junit.Test

class CsRepoNestedManifestTest {
    @Test
    fun `root manifest relative plugin lists and nested repositories are resolved`() {
        val root = CsRepoManifestParser.parseRoot(
            """
            {
              "name": "Parent",
              "pluginLists": ["lists/plugins.json"],
              "subRepositories": [
                "children/one/repo.json",
                {"url": "https://child.example/repo.json", "name": "Child", "description": "Nested repo"}
              ]
            }
            """.trimIndent(),
            "https://example.com/root/repo.json",
        )

        assertEquals(listOf("https://example.com/root/lists/plugins.json"), root.pluginLists)
        assertEquals(2, root.subRepositories.size)
        assertEquals("https://example.com/root/children/one/repo.json", root.subRepositories[0].url)
        assertEquals("Child", root.subRepositories[1].name)
        assertEquals("Nested repo", root.subRepositories[1].description)
    }

    @Test
    fun `Mega repo index accepts URL strings and objects`() {
        val repos = CsRepoManifestParser.parseRepoIndex(
            """
            [
              "https://one.example/repo.json",
              {"url":"https://two.example/repo.json", "verified":true}
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("https://one.example/repo.json", "https://two.example/repo.json"), repos.map { it.url })
    }

    @Test
    fun `MegaWio live index contains all ten child repositories`() {
        val repos = CsRepoManifestParser.parseRepoIndex(
            """
            [
              "https://raw.githubusercontent.com/Wiojelt/WioSinema/main/repo.json",
              "https://raw.githubusercontent.com/Wiojelt/WioCinema/main/repo.json",
              "https://raw.githubusercontent.com/Wiojelt/WioKids/main/repo.json",
              "https://raw.githubusercontent.com/Wiojelt/WioSpor/main/repo.json",
              "https://raw.githubusercontent.com/Wiojelt/TurkSpor/builds/repo.json",
              "https://raw.githubusercontent.com/Wiojelt/WioAnime/builds/repo.json",
              "https://raw.githubusercontent.com/Wiojelt/WioAsya/builds/repo.json",
              "https://raw.githubusercontent.com/Wiojelt/WioDrama/builds/repo.json",
              "https://raw.githubusercontent.com/pltmustafa/plt-stream/master/repo.json",
              "https://raw.githubusercontent.com/sika200581/cloudstream-live-hub/main/repo.json"
            ]
            """.trimIndent(),
            WIO_MEGA_REPOSITORY_INDEX_URL,
        )

        assertEquals(10, repos.size)
        assertEquals("https://raw.githubusercontent.com/Wiojelt/TurkSpor/builds/repo.json", repos[4].url)
        assertEquals("https://raw.githubusercontent.com/pltmustafa/plt-stream/master/repo.json", repos[8].url)
        assertEquals("https://raw.githubusercontent.com/sika200581/cloudstream-live-hub/main/repo.json", repos[9].url)
    }

    @Test
    fun `MegaWio and other Mega loaders are recognized without fixed English description`() {
        assertEquals(true, isMegaRepoLoaderIdentity("MegaWio", "MegaWio", "WioCinema, WioSpor, WioAnime"))
        assertEquals(true, isMegaRepoLoaderIdentity("MegaProvider", "MegaProvider", "Adds every source"))
        assertEquals(true, isMegaRepoLoaderIdentity("repo-loader", "Repository Loader", "Adds all repositories"))
        assertEquals(false, isMegaRepoLoaderIdentity("WebDramaTurkey", "Web Drama Turkey", "Turkish drama provider"))
    }

    @Test
    fun `MegaWio uses its own index while other Mega loaders use the generic index`() {
        assertEquals(
            listOf(WIO_MEGA_REPOSITORY_INDEX_URL),
            megaRepoIndexUrlsFor("MegaWio", "MegaWio", "Toplu depo yükleyici"),
        )
        assertEquals(
            listOf(CLOUDSTREAM_MEGA_REPOSITORY_INDEX_URL),
            megaRepoIndexUrlsFor("MegaProvider", "MegaProvider", "Adds all repositories"),
        )
        assertEquals(emptyList<String>(), megaRepoIndexUrlsFor("WioCinema", "WioCinema", "Movie providers"))
    }
}
