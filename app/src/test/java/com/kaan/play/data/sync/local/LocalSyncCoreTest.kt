package com.kaan.play.data.sync.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.security.SecureRandom

/** Kod, deneme sınırı, şifreleme ve ağ denetimi (Android'siz). */
class LocalSyncCoreTest {

    // ---- kod ----

    @Test
    fun codeIsSixDigitsAndFresh() {
        val random = SecureRandom()
        val codes = List(50) { LocalSyncCode.generate(random) }
        assertTrue(codes.all { it.length == 6 && it.all(Char::isDigit) })
        assertTrue("kodlar her seferinde yeniden üretilir", codes.toSet().size > 40)
    }

    @Test
    fun codeNormalization() {
        assertEquals("123456", LocalSyncCode.normalize(" 123 456 "))
        assertEquals("012345", LocalSyncCode.normalize("012-345"))
        assertNull(LocalSyncCode.normalize("12345"))
        assertNull(LocalSyncCode.normalize("1234567"))
        assertNull(LocalSyncCode.normalize("12a456"))
    }

    // ---- deneme sınırı ----

    @Test
    fun fifthWrongAttemptLocks() {
        val limiter = SyncAttemptLimiter(5)
        repeat(4) { assertFalse(limiter.fail()) }
        assertFalse(limiter.locked)
        assertEquals(1, limiter.remaining)
        assertTrue(limiter.fail())
        assertTrue(limiter.locked)
        assertEquals(0, limiter.remaining)
    }

    // ---- ağ ----

    private fun ip(s: String) = InetAddress.getByName(s)

    @Test
    fun onlyPrivateIpv4IsAllowed() {
        assertTrue(LocalNetwork.isPrivateLan(ip("10.0.0.5")))
        assertTrue(LocalNetwork.isPrivateLan(ip("172.16.0.1")))
        assertTrue(LocalNetwork.isPrivateLan(ip("172.31.255.255")))
        assertTrue(LocalNetwork.isPrivateLan(ip("192.168.1.20")))
        assertFalse(LocalNetwork.isPrivateLan(ip("172.32.0.1")))
        assertFalse(LocalNetwork.isPrivateLan(ip("172.15.0.1")))
        assertFalse(LocalNetwork.isPrivateLan(ip("8.8.8.8")))
        assertFalse(LocalNetwork.isPrivateLan(ip("127.0.0.1")))
        assertFalse(LocalNetwork.isPrivateLan(ip("169.254.1.1")))
        assertFalse(LocalNetwork.isPrivateLan(ip("::1")))
    }

    @Test
    fun manualAddressMustBeNumericIpv4() {
        assertEquals(ip("192.168.1.2"), LocalNetwork.parseIpv4("192.168.1.2"))
        assertNull(LocalNetwork.parseIpv4("example.com"))
        assertNull(LocalNetwork.parseIpv4("192.168.1"))
        assertNull(LocalNetwork.parseIpv4("192.168.1.256"))
        assertNull(LocalNetwork.parseIpv4("192.168..2"))
    }

    // ---- şifreleme ----

    private val sN = "00112233445566778899aabbccddeeff"
    private val cN = "ffeeddccbbaa99887766554433221100"

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val okm = LocalSyncCrypto.hkdf(ikm, salt, info, 42).joinToString("") { "%02x".format(it) }
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
            okm,
        )
    }

    @Test
    fun sealOpenRoundTrip() {
        val keys = LocalSyncCrypto.deriveKeys("123456", sN, cN)
        val sealed = LocalSyncCrypto.seal(keys, "/sync", """{"a":"çğış"}""")
        assertFalse("düz metin gövdede görünmez", sealed.contains("çğış"))
        assertFalse("kod gövdede görünmez", sealed.contains("123456"))
        assertEquals("""{"a":"çğış"}""", LocalSyncCrypto.open(LocalSyncCrypto.deriveKeys("123456", sN, cN), "/sync", sealed))
    }

    @Test
    fun wrongCodeOrNonceCannotOpen() {
        val sealed = LocalSyncCrypto.seal(LocalSyncCrypto.deriveKeys("123456", sN, cN), "/sync", "gizli")
        expectAuthFailure { LocalSyncCrypto.open(LocalSyncCrypto.deriveKeys("123457", sN, cN), "/sync", sealed) }
        expectAuthFailure { LocalSyncCrypto.open(LocalSyncCrypto.deriveKeys("123456", cN, sN), "/sync", sealed) }
        // Başka uca yönlendirilmiş gövde (ek doğrulanmış veri = yol) açılmaz.
        expectAuthFailure { LocalSyncCrypto.open(LocalSyncCrypto.deriveKeys("123456", sN, cN), "/profiles", sealed) }
        expectAuthFailure { LocalSyncCrypto.open(LocalSyncCrypto.deriveKeys("123456", sN, cN), "/sync", "{bozuk") }
    }

    @Test
    fun tamperedBodyIsRejected() {
        val keys = LocalSyncCrypto.deriveKeys("123456", sN, cN)
        val sealed = LocalSyncCrypto.seal(keys, "/sync", "gizli veri")
        val data = Regex("\"data\":\"([^\"]+)\"").find(sealed)!!.groupValues[1]
        val flipped = (if (data[0] == 'A') 'B' else 'A') + data.substring(1)
        expectAuthFailure { LocalSyncCrypto.open(keys, "/sync", sealed.replace(data, flipped)) }
    }

    @Test
    fun proofDependsOnCodePathAndNonces() {
        val keys = LocalSyncCrypto.deriveKeys("123456", sN, cN)
        val p = LocalSyncCrypto.proof(keys, "POST", "/sync", sN, cN)
        assertEquals(p, LocalSyncCrypto.proof(LocalSyncCrypto.deriveKeys("123456", sN, cN), "POST", "/sync", sN, cN))
        assertNotEquals(p, LocalSyncCrypto.proof(LocalSyncCrypto.deriveKeys("654321", sN, cN), "POST", "/sync", sN, cN))
        assertNotEquals(p, LocalSyncCrypto.proof(keys, "GET", "/profiles", sN, cN))
        assertTrue(LocalSyncCrypto.proofMatches(p, p))
        assertFalse(LocalSyncCrypto.proofMatches(p, null))
        assertFalse(p.contains("123456"))
    }

    @Test
    fun nonceFormat() {
        assertTrue(LocalSyncCrypto.isNonce(LocalSyncCrypto.newNonce()))
        assertFalse(LocalSyncCrypto.isNonce("abc"))
        assertFalse(LocalSyncCrypto.isNonce("ZZ112233445566778899aabbccddeeff"))
        assertFalse(LocalSyncCrypto.isNonce(null))
    }

    private fun expectAuthFailure(block: () -> Unit) {
        try {
            block()
            fail("SyncAuthException bekleniyordu")
        } catch (_: SyncAuthException) {
        }
    }
}
