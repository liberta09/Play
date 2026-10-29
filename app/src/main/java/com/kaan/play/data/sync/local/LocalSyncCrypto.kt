package com.kaan.play.data.sync.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

/** Şifreli gövde: `iv` (onaltılık, 12 bayt) + `data` (Base64, AES-GCM şifreli metin + etiket). */
@Serializable
data class SyncEnvelope(val iv: String, val data: String)

/** Oturum anahtarları: biri şifreleme (AES-256-GCM), biri istek kanıtı (HMAC-SHA256) için. */
class SyncKeys internal constructor(internal val enc: ByteArray, internal val mac: ByteArray)

/**
 * Kod ağda düz gönderilmez. İstemci ve sunucu birer rastgele değer (nonce) üretir; anahtarlar koddan ve iki nonce'tan
 * HKDF-SHA256 ile türetilir. İstek, yöntem + yol + iki nonce üzerinde HMAC kanıtı taşır (sunucu yanlış kodu bundan
 * anlar: 401); /profiles ve /sync gövdeleri AES-GCM ile şifrelenir (ek doğrulanmış veri: yol).
 */
object LocalSyncCrypto {

    const val NONCE_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val INFO = "play-sync-v1".toByteArray(Charsets.UTF_8)
    private val json = Json { ignoreUnknownKeys = true }

    fun newNonce(random: SecureRandom = SecureRandom()): String = ByteArray(NONCE_BYTES).also(random::nextBytes).toHex()

    /** Nonce biçimi: tam [NONCE_BYTES] baytlık onaltılık metin. */
    fun isNonce(value: String?): Boolean =
        value != null && value.length == NONCE_BYTES * 2 && value.all { it in '0'..'9' || it in 'a'..'f' }

    fun deriveKeys(code: String, serverNonce: String, clientNonce: String): SyncKeys {
        val salt = serverNonce.hexToBytes() + clientNonce.hexToBytes()
        val okm = hkdf(code.toByteArray(Charsets.UTF_8), salt, INFO, 64)
        return SyncKeys(okm.copyOfRange(0, 32), okm.copyOfRange(32, 64))
    }

    /** İstek kanıtı (onaltılık). */
    fun proof(keys: SyncKeys, method: String, path: String, serverNonce: String, clientNonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keys.mac, "HmacSHA256"))
        return mac.doFinal("PLAY_SYNC|$method $path|$serverNonce|$clientNonce".toByteArray(Charsets.UTF_8)).toHex()
    }

    /** Sabit zamanlı karşılaştırma. */
    fun proofMatches(expected: String, actual: String?): Boolean =
        actual != null && MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))

    fun seal(keys: SyncKeys, path: String, plain: String, random: SecureRandom = SecureRandom()): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keys.enc, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(path.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return json.encodeToString(SyncEnvelope.serializer(), SyncEnvelope(iv.toHex(), Base64.encode(sealed)))
    }

    /** Yanlış anahtar, değiştirilmiş ya da bozuk gövde: [SyncAuthException]. */
    fun open(keys: SyncKeys, path: String, envelopeJson: String): String {
        try {
            val env = json.decodeFromString(SyncEnvelope.serializer(), envelopeJson)
            val iv = env.iv.hexToBytes()
            if (iv.size != IV_BYTES) throw SyncAuthException()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keys.enc, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(path.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(Base64.decode(env.data)).toString(Charsets.UTF_8)
        } catch (e: SyncAuthException) {
            throw e
        } catch (_: GeneralSecurityException) {
            throw SyncAuthException()
        } catch (_: IllegalArgumentException) { // bozuk JSON / Base64 / onaltılık
            throw SyncAuthException()
        }
    }

    /** RFC 5869 HKDF (HMAC-SHA256). */
    internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val n = minOf(previous.size, length - offset)
            previous.copyInto(out, offset, 0, n)
            offset += n
            counter++
        }
        return out
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "hex" }
        return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }
}

/** Kod yanlış ya da gövde doğrulanamadı (sunucuda 401). */
class SyncAuthException : Exception()
