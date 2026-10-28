package com.kaan.play.data.sync.local

import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Sunucu + istemci arasında 127.0.0.1 üzerinden **gerçek soketle** uçtan uca deneme (JVM): şifreli el sıkışma,
 * yanlış kodda 401, 5. yanlışta sunucunun kapanması, çift yönlü birleştirme ve UDP bulma.
 * (Üretimde yalnızca özel IPv4 kabul edilir; test geri döngü adresine izin verir.)
 */
class LocalSyncIntegrationTest {

    private val loopbackOnly: (InetAddress) -> Boolean = { it.isLoopbackAddress }
    private val servers = mutableListOf<LocalSyncServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private fun h(tmdb: Int, at: Long, pos: Long) = HistoryBackup(tmdb, "movie", 0, 0, pos, 10_000, at, "T$tmdb", null)

    private val profile = LocalProfileSummary("p1", "Ev", pinHash = "secret-hash")

    /** Kodu gösteren cihaz (sunucu). */
    private val deviceA = LocalProfileTransfer(
        profiles = listOf(LocalSyncMerge.forTransfer(profile)),
        backups = mapOf("p1" to LocalSyncMerge.encodeBackup(LocalProfileBackup(history = listOf(h(1, at = 100, pos = 1), h(2, at = 500, pos = 2))))),
        sources = listOf(SourceTransferItem("NUVIO_ADDON", "https://a.example/manifest.json")),
        deviceName = "TV",
    )

    /** Bağlanan cihaz (istemci). */
    private val deviceB = LocalProfileTransfer(
        profiles = listOf(LocalSyncMerge.forTransfer(profile), LocalProfileSummary("p2", "Telefon")),
        backups = mapOf(
            "p1" to LocalSyncMerge.encodeBackup(
                LocalProfileBackup(history = listOf(h(1, at = 300, pos = 9)), favorites = listOf(FavoriteBackup(7, "movie", "F", null, 1))),
            ),
        ),
        sources = listOf(SourceTransferItem("CLOUDSTREAM_REPO", "https://b.example/repo.json")),
        deviceName = "Telefon",
    )

    private inner class FakeDevice(val data: LocalProfileTransfer) : LocalSyncServer.Handler {
        val received = AtomicReference<String?>(null)
        override fun profiles(): String = """{"profiles":${data.profiles.size}}"""
        override fun exchange(remoteJson: String): String {
            received.set(remoteJson)
            return LocalSyncMerge.encode(data)
        }
    }

    private fun server(code: String, handler: LocalSyncServer.Handler, maxAttempts: Int = 5): Pair<LocalSyncServer, Int> {
        val s = LocalSyncServer(code, handler, port = 0, addressAllowed = loopbackOnly, maxAttempts = maxAttempts)
        servers += s
        return s to s.start()
    }

    private val client = LocalSyncClient(addressAllowed = loopbackOnly)

    @Test
    fun encryptedHandshakeAndTwoWayMerge() = runBlocking {
        val a = FakeDevice(deviceA)
        val (_, port) = server("482913", a)

        assertTrue(client.health("127.0.0.1", port))
        assertEquals("""{"profiles":1}""", client.profiles("127.0.0.1", port, "482913"))

        val remoteJson = client.exchange("127.0.0.1", port, "482913", LocalSyncMerge.encode(deviceB))
        val gotOnClient = LocalSyncMerge.decodeTransfer(remoteJson)
        val gotOnServer = LocalSyncMerge.decodeTransfer(a.received.get()!!)
        assertEquals(deviceA, gotOnClient)
        assertEquals(deviceB, gotOnServer)

        // Her cihaz aynı kurallarla birleştirir: sonunda aynı veri.
        val planB = LocalSyncMerge.plan(deviceB, gotOnClient, remoteIsAuthority = true)
        val planA = LocalSyncMerge.plan(deviceA, gotOnServer, remoteIsAuthority = false)
        assertEquals(planA.profiles.map { it.id }.toSet(), planB.profiles.map { it.id }.toSet())
        assertEquals(planA.libraries, planB.libraries)
        val p1 = planA.libraries.getValue("p1")
        assertEquals(9L, p1.history.first { it.tmdbId == 1 }.positionMs) // en yeni lastWatchedAt kazandı
        assertEquals(2, p1.history.size)
        assertEquals(listOf(7), p1.favorites.map { it.tmdbId })
        // Kaynaklar kendiliğinden eklenmez: her iki tarafta da "eklensin mi?" listesine düşer.
        assertEquals(listOf("https://b.example/repo.json"), planA.missingSources.map { it.sourceUrl })
        assertEquals(listOf("https://a.example/manifest.json"), planB.missingSources.map { it.sourceUrl })
        // PIN ağdan geçmez.
        assertFalse(remoteJson.contains("secret-hash"))
        assertFalse(a.received.get()!!.contains("secret-hash"))
    }

    @Test
    fun wrongCodeGets401() = runBlocking {
        val a = FakeDevice(deviceA)
        val (srv, port) = server("111111", a)
        try {
            client.exchange("127.0.0.1", port, "222222", LocalSyncMerge.encode(deviceB))
            fail("WrongCode bekleniyordu")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
        }
        assertEquals(null, a.received.get())
        assertTrue(srv.isRunning)
        // Doğru kodla hâlâ bağlanılır.
        client.exchange("127.0.0.1", port, "111111", LocalSyncMerge.encode(deviceB))
        assertTrue(a.received.get() != null)
    }

    @Test
    fun fifthWrongAttemptClosesServer() = runBlocking {
        val (srv, port) = server("135790", FakeDevice(deviceA))
        val closed = CountDownLatch(1)
        val reason = AtomicReference<LocalSyncServer.CloseReason>()
        srv.onClosed = { reason.set(it); closed.countDown() }
        repeat(4) {
            try {
                client.profiles("127.0.0.1", port, "000000")
                fail("WrongCode bekleniyordu")
            } catch (e: LocalSyncException) {
                assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
            }
            assertTrue(srv.isRunning)
        }
        try {
            client.profiles("127.0.0.1", port, "000001")
            fail("WrongCode bekleniyordu")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
        }
        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertEquals(LocalSyncServer.CloseReason.TooManyAttempts, reason.get())
        assertFalse(srv.isRunning)
        // Kapandıktan sonra doğru kod da işe yaramaz: yeni kod gerekir.
        try {
            client.exchange("127.0.0.1", port, "135790", "{}")
            fail("Unreachable bekleniyordu")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.Unreachable, e.kind)
        }
    }

    @Test
    fun replayedRequestIsRejected() {
        val (_, port) = server("246802", FakeDevice(deviceA))
        val http = okhttp3.OkHttpClient()
        val base = "http://127.0.0.1:$port"
        val health = http.newCall(okhttp3.Request.Builder().url("$base/health").build()).execute().use { it.body.string() }
        val sN = Regex("\"nonce\":\"([0-9a-f]+)\"").find(health)!!.groupValues[1]
        val cN = LocalSyncCrypto.newNonce()
        val keys = LocalSyncCrypto.deriveKeys("246802", sN, cN)
        val request = okhttp3.Request.Builder().url("$base/profiles")
            .header(LocalSyncProtocol.HEADER_SERVER_NONCE, sN)
            .header(LocalSyncProtocol.HEADER_CLIENT_NONCE, cN)
            .header(LocalSyncProtocol.HEADER_PROOF, LocalSyncCrypto.proof(keys, "GET", "/profiles", sN, cN))
            .build()
        val first = http.newCall(request).execute().use { it.code to it.body.string() }
        assertEquals(200, first.first)
        assertFalse("yanıt şifreli", first.second.contains("profiles"))
        assertEquals("""{"profiles":1}""", LocalSyncCrypto.open(keys, "/profiles", first.second))
        // Aynı istek ikinci kez: yeniden oynatma reddedilir.
        assertEquals(401, http.newCall(request).execute().use { it.code })
    }

    @Test
    fun nonAllowedAddressIsDropped() = runBlocking {
        val s = LocalSyncServer("123456", FakeDevice(deviceA), port = 0, addressAllowed = { false })
        servers += s
        val port = s.start()
        try {
            client.health("127.0.0.1", port).let { assertFalse(it) }
            client.profiles("127.0.0.1", port, "123456")
            fail("bağlantı kesilmeliydi")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.Unreachable, e.kind)
        }
    }

    @Test
    fun clientRefusesNonPrivateTarget() = runBlocking {
        try {
            LocalSyncClient().exchange("8.8.8.8", 43821, "123456", "{}")
            fail("NotLocal bekleniyordu")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.NotLocal, e.kind)
        }
        try {
            LocalSyncClient().exchange("example.com", 43821, "123456", "{}")
            fail("NotLocal bekleniyordu")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.NotLocal, e.kind)
        }
    }

    @Test
    fun udpDiscoveryFindsResponder() = runBlocking {
        val responder = LocalSyncDiscovery.Responder(port = 0, addressAllowed = loopbackOnly)
        val port = responder.start()
        try {
            val loop = InetAddress.getByName("127.0.0.1")
            val found = LocalSyncDiscovery.discover(listOf(loop), port, timeoutMs = 1_500, addressAllowed = loopbackOnly)
            assertEquals(listOf(loop), found)
            // Kendi adresimiz atlanır.
            assertTrue(LocalSyncDiscovery.discover(listOf(loop), port, timeoutMs = 500, ownAddresses = setOf(loop), addressAllowed = loopbackOnly).isEmpty())
        } finally {
            responder.stop()
        }
    }
}
