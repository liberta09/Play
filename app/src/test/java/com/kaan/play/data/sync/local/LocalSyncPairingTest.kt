package com.kaan.play.data.sync.local

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Kalıcı eşleşme (`/pair` + eşli istek): 127.0.0.1 üzerinden gerçek soketle (bkz. [LocalSyncIntegrationTest]). */
class LocalSyncPairingTest {

    private val loopbackOnly: (InetAddress) -> Boolean = { it.isLoopbackAddress }
    private val servers = mutableListOf<LocalSyncServer>()
    private val client = LocalSyncClient(addressAllowed = loopbackOnly)
    private val phone = DeviceIdentity(id = "a".repeat(32), name = "Telefon")

    @After
    fun tearDown() = servers.forEach { it.stop() }

    private class Directory : PairDirectory {
        val records = ConcurrentHashMap<String, PairRecord>()
        override val identity = DeviceIdentity(id = "b".repeat(32), name = "TV")
        override fun secretFor(pairId: String): String? = records[pairId]?.secret
        override fun paired(record: PairRecord) {
            records[record.pairId] = record
        }
    }

    private class Device : LocalSyncServer.Handler {
        val received = AtomicReference<String?>(null)
        val exchanges = AtomicInteger()
        override fun profiles(): String = "{}"
        override fun exchange(remoteJson: String): String {
            received.set(remoteJson)
            exchanges.incrementAndGet()
            return """{"from":"tv"}"""
        }
    }

    private fun server(code: String?, directory: PairDirectory?, handler: LocalSyncServer.Handler = Device()): Pair<LocalSyncServer, Int> {
        val s = LocalSyncServer(code, handler, port = 0, addressAllowed = loopbackOnly, pairs = directory)
        servers += s
        return s to s.start()
    }

    @Test
    fun pairOnceThenSyncWithoutCode() = runBlocking<Unit> {
        val dir = Directory()
        val tv = Device()
        val (srv, port) = server("482913", dir, tv)

        val record = client.pair("127.0.0.1", port, "482913", phone)

        val stored = dir.records.getValue(record.pairId)
        assertEquals(stored.secret, record.secret) // iki taraf aynı sırrı ECDH'den türetti, sır ağdan geçmedi
        assertEquals("TV", record.peerName)
        assertEquals("b".repeat(32), record.peerId)
        assertEquals("Telefon", stored.peerName)
        assertEquals(phone.id, stored.peerId)
        assertEquals("127.0.0.1", record.lastHost)

        srv.setCode(null) // kart kapandı: kod yok, eşleşme sürer
        val reply = client.exchangePaired("127.0.0.1", port, record, """{"from":"phone"}""")
        assertEquals("""{"from":"tv"}""", reply)
        assertEquals("""{"from":"phone"}""", tv.received.get())
        try {
            client.exchange("127.0.0.1", port, "482913", "{}")
            fail("kod kapandıktan sonra kodlu istek reddedilmeliydi")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
        }
    }

    @Test
    fun eachPairingGetsItsOwnSecret() = runBlocking<Unit> {
        val dir = Directory()
        val (_, port) = server("111111", dir)
        val first = client.pair("127.0.0.1", port, "111111", phone)
        val second = client.pair("127.0.0.1", port, "111111", phone)
        assertNotEquals(first.pairId, second.pairId)
        assertNotEquals(first.secret, second.secret)
    }

    @Test
    fun wrongSecretIsRejectedAndSuspendsOnlyThatPair() = runBlocking<Unit> {
        val dir = Directory()
        val tv = Device()
        val (srv, port) = server("222222", dir, tv)
        val good = client.pair("127.0.0.1", port, "222222", phone)
        val other = client.pair("127.0.0.1", port, "222222", phone)
        val forged = good.copy(secret = "0".repeat(64))
        repeat(LocalSyncProtocol.MAX_ATTEMPTS) {
            try {
                client.exchangePaired("127.0.0.1", port, forged, "{}")
                fail("sahte sır kabul edilmemeli")
            } catch (e: LocalSyncException) {
                assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
            }
        }
        // O eşleşme bu oturumda askıda (doğru sırla da); sunucu ve diğer eşleşme çalışır.
        try {
            client.exchangePaired("127.0.0.1", port, good, "{}")
            fail("askıdaki eşleşme reddedilmeli")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
        }
        assertTrue(srv.isRunning)
        client.exchangePaired("127.0.0.1", port, other, "{}")
        assertEquals(1, tv.exchanges.get())
    }

    @Test
    fun unknownOrRemovedPairIs401WithoutClosingServer() = runBlocking<Unit> {
        val dir = Directory()
        val (srv, port) = server("333333", dir)
        val record = client.pair("127.0.0.1", port, "333333", phone)
        dir.records.remove(record.pairId) // karşı tarafta "Eşleşmeyi kaldır"
        repeat(LocalSyncProtocol.MAX_ATTEMPTS + 2) {
            try {
                client.exchangePaired("127.0.0.1", port, record, "{}")
                fail("silinmiş eşleşme reddedilmeli")
            } catch (e: LocalSyncException) {
                assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
            }
        }
        assertTrue(srv.isRunning)
        client.exchange("127.0.0.1", port, "333333", "{}") // kod hâlâ geçerli (sayaç bilinmeyen eşleşmeden etkilenmez)
    }

    @Test
    fun serverWithoutPairingKeepsTheOldCodeFlow() = runBlocking<Unit> {
        val tv = Device()
        val (_, port) = server("444444", directory = null, handler = tv)
        try {
            client.pair("127.0.0.1", port, "444444", phone)
            fail("eşleşmeyi bilmeyen sunucu /pair'e 404 vermeli")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.Invalid, e.kind)
        }
        client.exchange("127.0.0.1", port, "444444", "{}")
        assertEquals(1, tv.exchanges.get())
    }

    @Test
    fun codeLockInPairingModeKeepsServerAndPairs() = runBlocking<Unit> {
        val dir = Directory()
        val (srv, port) = server("555555", dir)
        val record = client.pair("127.0.0.1", port, "555555", phone)
        val locked = AtomicInteger()
        srv.onCodeLocked = { locked.incrementAndGet() }
        repeat(LocalSyncProtocol.MAX_ATTEMPTS) {
            try {
                client.exchange("127.0.0.1", port, "000000", "{}")
            } catch (_: LocalSyncException) {
            }
        }
        assertEquals(1, locked.get())
        assertTrue(srv.isRunning)
        try {
            client.exchange("127.0.0.1", port, "555555", "{}")
            fail("kilitlenen kod artık geçmemeli")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
        }
        client.exchangePaired("127.0.0.1", port, record, "{}")
    }

    @Test
    fun playRequestOnlyFromPairedDevice() = runBlocking<Unit> {
        val dir = Directory()
        val played = AtomicReference<String?>(null)
        val srv = LocalSyncServer("666666", Device(), port = 0, addressAllowed = loopbackOnly, pairs = dir, remotePlay = { played.set(it); """{"error":"no_plugin"}""" })
        servers += srv
        val port = srv.start()
        val record = client.pair("127.0.0.1", port, "666666", phone)
        srv.setCode(null)

        val reply = client.playPaired("127.0.0.1", port, record, """{"title":"Naruto"}""")
        assertEquals("""{"title":"Naruto"}""", played.get()) // gövde şifreli gitti, sunucuda açıldı
        assertEquals("""{"error":"no_plugin"}""", reply) // hedefin yanıtı (ör. eklenti yok) şifreli geri döndü

        played.set(null)
        try {
            client.playPaired("127.0.0.1", port, record.copy(secret = "0".repeat(64)), "{}")
            fail("sahte sırla oynatma isteği reddedilmeli")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.WrongCode, e.kind)
        }
        assertEquals(null, played.get())
    }

    @Test
    fun remoteInputOnlyFromPairedDevice() = runBlocking<Unit> {
        val dir = Directory()
        val got = AtomicReference<String?>(null)
        val srv = LocalSyncServer("888888", Device(), port = 0, addressAllowed = loopbackOnly, pairs = dir, remoteInput = { got.set(it); """{"ok":true}""" })
        servers += srv
        val port = srv.start()
        val record = client.pair("127.0.0.1", port, "888888", phone)

        val input = RemoteInput(text = "kraliçe", submit = true)
        val reply = client.remotePaired("127.0.0.1", port, record, com.kaan.play.data.net.PlayJson.encodeToString(RemoteInput.serializer(), input))
        assertEquals("""{"ok":true}""", reply)
        assertEquals(input, com.kaan.play.data.net.PlayJson.decodeFromString(RemoteInput.serializer(), got.get()!!)) // Türkçe harfler bozulmadan

        // Eşleşmesiz (kod da açıkken) istek alıcıya ulaşmaz.
        got.set(null)
        val raw = java.net.URL("http://127.0.0.1:$port${LocalSyncProtocol.PATH_REMOTE}").openConnection() as java.net.HttpURLConnection
        raw.requestMethod = "POST"
        raw.doOutput = true
        raw.outputStream.use { it.write("{}".toByteArray()) }
        assertEquals(401, raw.responseCode)
        assertEquals(null, got.get())
    }

    @Test
    fun deviceThatIsNotAPlayTargetAnswers404() = runBlocking<Unit> {
        val dir = Directory()
        val (_, port) = server("777777", dir) // remotePlay yok (ör. telefon)
        val record = client.pair("127.0.0.1", port, "777777", phone)
        try {
            client.playPaired("127.0.0.1", port, record, "{}")
            fail("oynatma hedefi olmayan cihaz 404 vermeli")
        } catch (e: LocalSyncException) {
            assertEquals(LocalSyncException.Kind.Invalid, e.kind)
        }
    }

    @Test
    fun pairSecretIsSymmetric() {
        val a = LocalSyncCrypto.newPairKeyPair()
        val b = LocalSyncCrypto.newPairKeyPair()
        val sn = LocalSyncCrypto.newNonce()
        val cn = LocalSyncCrypto.newNonce()
        val ab = LocalSyncCrypto.pairSecret(a.private, LocalSyncCrypto.encodePublicKey(b), sn, cn)
        val ba = LocalSyncCrypto.pairSecret(b.private, LocalSyncCrypto.encodePublicKey(a), sn, cn)
        assertEquals(ab, ba)
        assertEquals(64, ab.length)
        assertNotEquals(ab, LocalSyncCrypto.pairSecret(a.private, LocalSyncCrypto.encodePublicKey(b), cn, sn))
        try {
            LocalSyncCrypto.pairSecret(a.private, "bozuk", sn, cn)
            fail("bozuk açık anahtar reddedilmeli")
        } catch (_: SyncAuthException) {
        }
    }
}
