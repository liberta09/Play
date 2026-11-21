package com.kaan.play.tv

import com.kaan.play.data.cloudstream.CsRepo
import com.kaan.play.data.cloudstream.InstalledCsPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shorts Drama ([ShortsDrama]): kısa diziler Ana sayfanın "İzlemeye devam et" satırında değil, yalnız Shorts Drama'da.
 * Eklenti ada göre değil depoya göre tanınır (eski kurulumlarda adlar farklı; başka bir depoda aynı adlı yabancı eklenti
 * olabilir).
 */
class ShortDramaRecordTest {
    private fun repo(id: Long, url: String) = CsRepo(id, url, "r$id", null, 0, 0)
    private fun plugin(id: Long, repo: Long, internal: String, name: String) =
        InstalledCsPlugin(id, repo, internal, name, null, null, "tr", emptyList(), true)

    private val repos = listOf(
        repo(1, ShortsDrama.ROOT_REPO),
        repo(2, "https://arcanadrama.download/ArcanaDrama/dramadizilerim-v3-repo.json"),
        repo(3, "https://raw.githubusercontent.com/biri/TurkSinema/main/repo.json"),
    )
    private val ids = ShortsDrama.repoIds(repos)
    private val plugins = listOf(
        plugin(4, 1, "ArcanaFullDrama", "Arcana Drama FullDrama"),
        plugin(5, 1, "ArcanaDramaDizilerim", "Arcana Drama Dizilerim"),
        plugin(737, 2, "ArcanaDramaDizilerim", "(Arcana Drama) DramaDizilerim"),
        plugin(744, 1, "ArcanaAnyShort", "(Arcana Drama) AnyShort"),
        plugin(259, 3, "ArcanaDrama", "Arcana Drama"),
        plugin(9, 3, "FilmSite", "FilmSite"),
    )

    @Test fun shortsReposAreRootAndDownloadSite() {
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test fun lookalikeRepoAddressesAreRejected() {
        assertFalse(ShortsDrama.isRepo("http://arcanadrama.download/ArcanaDrama/x-repo.json"))
        assertFalse(ShortsDrama.isRepo("https://arcanadrama.download:8443/ArcanaDrama/x-repo.json"))
        assertFalse(ShortsDrama.isRepo("https://arcanadrama.download/ArcanaDrama/../x-repo.json"))
        assertFalse(ShortsDrama.isRepo("https://arcanadrama.download/ArcanaDrama/x-repo.json?y=1"))
        assertFalse(ShortsDrama.isRepo("https://user@arcanadrama.download/ArcanaDrama/x-repo.json"))
        assertFalse(ShortsDrama.isRepo("https://evil.example/ArcanaDrama/x-repo.json"))
    }

    @Test fun pluginRecordsAreShortDramaWhateverTheName() {
        assertTrue(ShortsDrama.isRecord("cs:4", plugins, ids))
        assertTrue(ShortsDrama.isRecord("cs:737", plugins, ids))
        assertTrue(ShortsDrama.isRecord("cs:744", plugins, ids))
    }

    @Test fun tmdbForeignAndUnknownRecordsAreNot() {
        assertFalse(ShortsDrama.isRecord(null, plugins, ids))
        assertFalse(ShortsDrama.isRecord("cs:259", plugins, ids))
        assertFalse(ShortsDrama.isRecord("cs:9", plugins, ids))
        assertFalse(ShortsDrama.isRecord("cs:404", plugins, ids))
    }

    @Test fun shortsShowsEveryPluginOnce() {
        val shown = ShortsDrama.plugins(plugins, ids).map { it.id }.toSet()
        assertEquals(setOf(4L, 5L, 744L), shown)
    }

    @Test fun rowTitleDropsOldAndNewPrefix() {
        assertEquals("AnyShort", ShortsDrama.shortName("(Arcana Drama) AnyShort"))
        assertEquals("Narto", ShortsDrama.shortName("Arcana Drama Narto"))
        assertEquals("FilmSite", ShortsDrama.shortName("FilmSite"))
    }
}
