package com.kaan.play.data.sync.local

import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.PluginStateTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bulut hesabı senkronizasyonunun kaynak kuralları (Wi-Fi ile ortak yük ve birleştirme): yükte PIN/kod yok, eklenti
 * dosyası yok; eksik kaynak onaya düşer ve buluttan silinmez; ortak kaynakta `updated_at`'e göre daha yeni kazanır.
 */
class CloudSourcesSyncTest {

    private val pinHash = "pbkdf2-sha256\$210000\$U0FMVA==\$SEFTSF9TRUNSRVQ="
    private val wifiCode = LocalSyncCode.generate()

    private val repo = SourceTransferItem(
        type = "NUVIO_PLUGIN_REPO",
        sourceUrl = "https://repo.example/manifest.json",
        name = "Depo",
        plugins = listOf(PluginStateTransfer("scraper-a", enabled = true, settings = mapOf("quality" to JsonPrimitive("1080p")))),
    )
    private val addon = SourceTransferItem("NUVIO_ADDON", "https://addon.example/manifest.json", disabledCatalogs = listOf("movie:top"))
    private val subtitles = SourceTransferItem("NUVIO_ADDON", "https://subs.example/manifest.json", name = "Altyazı")
    private val cs = SourceTransferItem("CLOUDSTREAM_REPO", "https://cs.example/repo.json")
    private val site = SourceTransferItem("WEB_SITE", "https://site.example", name = "Site")

    private fun device(sources: List<SourceTransferItem>, profiles: List<LocalProfileSummary> = listOf(LocalProfileSummary("p1", "Ev"))) =
        LocalSyncMerge.snapshot(
            profiles = profiles,
            libraries = mapOf("p1" to LocalProfileBackup(history = listOf(HistoryBackup(1, "movie", 0, 0, 5, 10, 100, "T", null)))),
            sources = sources,
            deviceName = "TV",
        )

    @Test
    fun payloadHasNoPinAndNoSyncCode() {
        val profile = LocalProfileSummary("p1", "Ev", pinHash = pinHash, syncCode = wifiCode, isChild = true)
        val payload = LocalSyncMerge.encode(device(listOf(repo), listOf(profile)))
        assertFalse("PIN özeti yüke girmez", payload.contains(pinHash))
        assertFalse(payload.contains("SEFTSF9TRUNSRVQ="))
        assertFalse("senkron kodu yüke girmez", payload.contains(wifiCode))
        val decoded = LocalSyncMerge.decodeTransfer(payload).profiles.single()
        assertNull(decoded.pinHash)
        assertNull(decoded.syncCode)
        assertTrue("profilin geri kalanı taşınır", decoded.isChild)
    }

    @Test
    fun pluginFilesAreNeverInThePayload() {
        // Yük modeli yalnızca adres + durum taşır: kod/dosya/özet alanı yok.
        assertEquals(
            listOf("pluginId", "enabled", "settings"),
            (0 until PluginStateTransfer.serializer().descriptor.elementsCount).map { PluginStateTransfer.serializer().descriptor.getElementName(it) },
        )
        assertEquals(
            listOf("type", "sourceUrl", "enabled", "plugins", "name", "disabledCatalogs"),
            (0 until SourceTransferItem.serializer().descriptor.elementsCount).map { SourceTransferItem.serializer().descriptor.getElementName(it) },
        )
        val payload = LocalSyncMerge.encode(device(listOf(repo, addon, subtitles, cs, site)))
        for (forbidden in listOf("scriptUrl", "sha256", "classes.dex", ".cs3\"", "function(", "getStreams", "filename")) {
            assertFalse("yükte '$forbidden' olmamalı", payload.contains(forbidden))
        }
        // Tüm kaynak türleri, durumları, ayarları ve kapalı katalogları taşınır.
        val sources = LocalSyncMerge.decodeTransfer(payload).sources
        assertEquals(listOf("NUVIO_PLUGIN_REPO", "NUVIO_ADDON", "NUVIO_ADDON", "CLOUDSTREAM_REPO", "WEB_SITE"), sources.map { it.type })
        assertEquals(JsonPrimitive("1080p"), sources[0].plugins.single().settings["quality"])
        assertEquals(listOf("movie:top"), sources[1].disabledCatalogs)
    }

    @Test
    fun missingSourcesGoToConfirmationAndStayInTheCloud() {
        val local = device(listOf(repo))
        val cloud = device(listOf(repo, cs, site))
        val plan = LocalSyncMerge.plan(local, cloud, remoteIsAuthority = true)
        assertEquals(listOf(cs, site), plan.missingSources) // kendiliğinden kurulmaz, sorulur
        // Onay bekleyenler buluta geri yazılır (silinmez); yerel kaynak bir kez.
        val upload = LocalSyncMerge.withPending(local, plan.missingSources)
        assertEquals(listOf(repo.sourceUrl, cs.sourceUrl, site.sourceUrl), upload.sources.map { it.sourceUrl })
        assertEquals(upload.sources, LocalSyncMerge.withPending(upload, plan.missingSources).sources)
    }

    @Test
    fun newerSideWinsForSharedSources() {
        val stamp = "2026-10-28T09:00:00.000000+00:00"
        // Bu cihaz 09:00'da yazdı; başka cihaz 09:05'te yazdı → bulut daha yeni.
        assertTrue(LocalSyncMerge.remoteIsNewer("2026-10-28T09:05:00.123+00:00", stamp))
        // Bulut kaydı bizim son yazdığımız (değişmemiş) → yerel korunur.
        assertFalse(LocalSyncMerge.remoteIsNewer(stamp, stamp))
        assertFalse(LocalSyncMerge.remoteIsNewer("2026-10-28T08:59:59+00:00", stamp))
        // Bulut damgasızsa yerel korunur.
        assertFalse(LocalSyncMerge.remoteIsNewer(null, stamp))
        assertFalse(LocalSyncMerge.remoteIsNewer("", stamp))
        assertFalse(LocalSyncMerge.remoteIsNewer(null, null))
        // Bu cihaz hiç yazmamışsa damgalı bulut kaydı kazanır.
        assertTrue(LocalSyncMerge.remoteIsNewer(stamp, null))

        val localRepo = repo.copy(enabled = true, plugins = listOf(PluginStateTransfer("scraper-a", enabled = true)))
        val cloudRepo = repo.copy(enabled = false, plugins = listOf(PluginStateTransfer("scraper-a", enabled = false)))
        val newer = LocalSyncMerge.plan(device(listOf(localRepo)), device(listOf(cloudRepo)), remoteIsAuthority = true)
        assertEquals(listOf(cloudRepo), newer.sourceStates)
        val older = LocalSyncMerge.plan(device(listOf(localRepo)), device(listOf(cloudRepo)), remoteIsAuthority = false)
        assertTrue("yerel korunur", older.sourceStates.isEmpty())
    }

    @Test
    fun cloudStampParsing() {
        val base = CloudStamp.parseMicros("2026-10-28T09:22:43+00:00")!!
        assertEquals(base, CloudStamp.parseMicros("2026-10-28T09:22:43Z"))
        assertEquals(base, CloudStamp.parseMicros("2026-10-28 12:22:43+03:00"))
        assertEquals(base + 123_456, CloudStamp.parseMicros("2026-10-28T09:22:43.123456+00:00"))
        assertEquals(base + 120_000, CloudStamp.parseMicros("2026-10-28T09:22:43.12+00"))
        assertEquals(0L, CloudStamp.parseMicros("1970-01-01T00:00:00Z"))
        assertEquals(1_000_000_000L * 1_000_000L, CloudStamp.parseMicros("2001-09-09T01:46:40Z"))
        assertNull(CloudStamp.parseMicros("dün"))
        assertNull(CloudStamp.parseMicros("2026-13-01T00:00:00Z"))
    }
}
