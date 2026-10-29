package com.kaan.play.data.addon

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AddonUrlTest {

    private fun norm(s: String) = AddonUrl.normalize(s)

    @Test
    fun keepsManifestUrlAsIs() {
        assertEquals("https://a.example.com/manifest.json", norm("https://a.example.com/manifest.json"))
    }

    @Test
    fun appendsManifestToBaseUrls() {
        assertEquals("https://a.example.com/manifest.json", norm("https://a.example.com"))
        assertEquals("https://a.example.com/manifest.json", norm("https://a.example.com/"))
        assertEquals("https://a.example.com/addon/cfg/manifest.json", norm("https://a.example.com/addon/cfg"))
        assertEquals("https://a.example.com/addon/cfg/manifest.json", norm("https://a.example.com/addon/cfg/"))
    }

    @Test
    fun addsHttpsWhenSchemeIsMissing() {
        assertEquals("https://a.example.com/manifest.json", norm("a.example.com"))
        assertEquals("https://a.example.com/x/manifest.json", norm("a.example.com/x/manifest.json"))
    }

    @Test
    fun allowsPlainHttpAndPorts() {
        assertEquals("http://192.168.1.5:7000/manifest.json", norm("http://192.168.1.5:7000/manifest.json"))
    }

    @Test
    fun trimsWhitespaceAndDropsFragmentAndLowercasesHost() {
        assertEquals("https://a.example.com/manifest.json", norm("  HTTPS://A.Example.com/manifest.json#top \n"))
    }

    @Test
    fun differentWritingsOfSameAddonNormalizeToSameString() {
        assertEquals(norm("https://a.example.com"), norm("a.example.com/manifest.json"))
    }

    @Test
    fun rejectsInvalidInput() {
        listOf("", "   ", "ftp://a.example.com/manifest.json", "https://", "not a url")
            .forEach { input ->
                try {
                    norm(input)
                    fail("Reddedilmeliydi: '$input'")
                } catch (e: AddonException) {
                    assertEquals(input, AddonException.Kind.InvalidUrl, e.kind)
                }
            }
    }

    @Test
    fun baseUrlStripsManifest() {
        assertEquals("https://a.example.com", AddonUrl.baseOf("https://a.example.com/manifest.json"))
        assertEquals("https://a.example.com/x/y", AddonUrl.baseOf("https://a.example.com/x/y/manifest.json"))
    }

    @Test
    fun stremioAuthQueryIsPreservedOnProtocolEndpoints() {
        val manifest = norm("https://a.example.com/addon/manifest.json?token=abc123&client=play")
        assertEquals("https://a.example.com/addon/manifest.json?token=abc123&client=play", manifest)
        assertEquals(
            "https://a.example.com/addon/stream/movie/tt123.json?token=abc123&client=play",
            AddonUrl.endpoint(manifest, "stream", "movie", "tt123.json").toString(),
        )
        assertEquals("https://a.example.com/addon?token=abc123&client=play", AddonUrl.baseOf(manifest))
    }

    @Test
    fun `baska bir json dosyasi olan adrese manifest eklenmez`() {
        assertEquals(
            "https://raw.example.com/u/r/main/repo.json",
            norm("https://raw.example.com/u/r/main/repo.json"),
        )
        assertEquals("https://a.example.com/plugins.JSON", norm("a.example.com/plugins.JSON"))
    }
}
