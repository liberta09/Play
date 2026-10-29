package com.kaan.play.data.sync.local

import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** İstemci hatası; arayüz [kind]'a göre kısa mesaj gösterir. */
class LocalSyncException(val kind: Kind, cause: Throwable? = null) : IOException(kind.name, cause) {
    enum class Kind {
        /** Bağlanılamadı / zaman aşımı. */
        Unreachable,

        /** Kod yanlış (401) ya da sunucu yanıtı doğrulanamadı. */
        WrongCode,

        /** Karşı taraf Play değil ya da beklenmeyen yanıt. */
        Invalid,

        /** Adres özel ağda değil. */
        NotLocal,
    }
}

/**
 * Wi-Fi senkronizasyonu istemcisi. Uygulamanın ortak istemcisini (eklenti ağ koruması özel adresleri engeller)
 * kullanmaz; kendi sade istemcisi yalnızca özel IPv4 adreslere bağlanır (DNS yok, vekil sunucu yok, yönlendirme yok).
 */
class LocalSyncClient(
    private val addressAllowed: (InetAddress) -> Boolean = LocalNetwork::isPrivateLan,
    private val random: SecureRandom = SecureRandom(),
    private val http: OkHttpClient = plainClient(addressAllowed),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Oturum: sunucunun verdiği nonce + bu istemcinin nonce'u. */
    private class Session(val base: String, val serverNonce: String, val clientNonce: String, val keys: SyncKeys)

    /** Sunucu Play mi? (şifresiz) */
    suspend fun health(host: String, port: Int = LocalSyncProtocol.PORT): Boolean =
        runCatching { openSession(host, port, "000000") }.isSuccess

    /** Karşı cihazın profil özetleri (şifreli). */
    suspend fun profiles(host: String, port: Int = LocalSyncProtocol.PORT, code: String): String {
        val session = openSession(host, port, code)
        val request = authed(session, "GET", LocalSyncProtocol.PATH_PROFILES).get().build()
        return call(request) { LocalSyncCrypto.open(session.keys, LocalSyncProtocol.PATH_PROFILES, it) }
    }

    /** Bu cihazın anlık görüntüsünü gönderir, karşınınkini alır (ikisi de şifreli). */
    suspend fun exchange(host: String, port: Int = LocalSyncProtocol.PORT, code: String, localJson: String): String {
        val session = openSession(host, port, code)
        val sealed = LocalSyncCrypto.seal(session.keys, LocalSyncProtocol.PATH_SYNC, localJson, random)
        val request = authed(session, "POST", LocalSyncProtocol.PATH_SYNC)
            .post(sealed.toRequestBody(JSON_TYPE))
            .build()
        return call(request) { LocalSyncCrypto.open(session.keys, LocalSyncProtocol.PATH_SYNC, it) }
    }

    private suspend fun openSession(host: String, port: Int, code: String): Session {
        val address = LocalNetwork.parseIpv4(host) ?: throw LocalSyncException(LocalSyncException.Kind.NotLocal)
        if (!addressAllowed(address)) throw LocalSyncException(LocalSyncException.Kind.NotLocal)
        val base = "http://${address.hostAddress}:$port"
        val body = call(Request.Builder().url(base + LocalSyncProtocol.PATH_HEALTH).get().build()) { it }
        val nonce = try {
            val obj = json.parseToJsonElement(body).jsonObject
            if (obj["app"]?.jsonPrimitive?.content != "play") null else obj["nonce"]?.jsonPrimitive?.content
        } catch (_: IllegalArgumentException) {
            null
        }
        if (!LocalSyncCrypto.isNonce(nonce)) throw LocalSyncException(LocalSyncException.Kind.Invalid)
        val clientNonce = LocalSyncCrypto.newNonce(random)
        return Session(base, nonce!!, clientNonce, LocalSyncCrypto.deriveKeys(code, nonce, clientNonce))
    }

    private fun authed(session: Session, method: String, path: String): Request.Builder =
        Request.Builder()
            .url(session.base + path)
            .header(LocalSyncProtocol.HEADER_SERVER_NONCE, session.serverNonce)
            .header(LocalSyncProtocol.HEADER_CLIENT_NONCE, session.clientNonce)
            .header(LocalSyncProtocol.HEADER_PROOF, LocalSyncCrypto.proof(session.keys, method, path, session.serverNonce, session.clientNonce))

    private suspend fun <T> call(request: Request, parse: (String) -> T): T {
        val response: Response = try {
            http.newCall(request).await()
        } catch (e: IOException) {
            throw LocalSyncException(LocalSyncException.Kind.Unreachable, e)
        }
        val text = response.use { resp ->
            when {
                resp.code == 401 -> throw LocalSyncException(LocalSyncException.Kind.WrongCode)
                !resp.isSuccessful -> throw LocalSyncException(LocalSyncException.Kind.Invalid)
            }
            withContext(Dispatchers.IO) {
                try {
                    val source = resp.body.source()
                    val buffer = Buffer()
                    while (source.read(buffer, 64 * 1024L) != -1L) {
                        if (buffer.size > LocalSyncProtocol.MAX_BODY_BYTES) throw LocalSyncException(LocalSyncException.Kind.Invalid)
                    }
                    buffer.readUtf8()
                } catch (e: LocalSyncException) {
                    throw e
                } catch (e: IOException) {
                    throw LocalSyncException(LocalSyncException.Kind.Unreachable, e)
                }
            }
        }
        return try {
            parse(text)
        } catch (_: SyncAuthException) {
            throw LocalSyncException(LocalSyncException.Kind.WrongCode)
        }
    }

    companion object {
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

        fun plainClient(addressAllowed: (InetAddress) -> Boolean): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .proxy(Proxy.NO_PROXY)
            .retryOnConnectionFailure(false)
            // Yalnızca rakamlı özel IPv4: ad çözümlemesi yapılmaz, internete çıkılmaz.
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val address = LocalNetwork.parseIpv4(hostname)?.takeIf(addressAllowed)
                        ?: throw UnknownHostException("not a local address")
                    return listOf(address)
                }
            })
            .build()
    }
}
