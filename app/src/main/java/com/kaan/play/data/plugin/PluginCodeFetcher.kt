package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Kazıyıcı kodunu indirir. Arayüz olarak ayrı: testlerde sahte uygulanır. */
fun interface PluginCodeFetcher {
    /**
     * @param allowPrivateHost özel/yerel ağ adresine yalnızca bu ana makine için izin (kullanıcı deponun kendisini yerel ağda
     * verdiyse); null ise özel adresler reddedilir.
     * @throws AddonException ağ/HTTP/boyut hatalarında.
     */
    suspend fun fetch(url: String, allowPrivateHost: String?): String
}

class OkHttpPluginCodeFetcher(
    client: OkHttpClient,
    timeoutSeconds: Long = 20,
    private val maxBytes: Long = MAX_BYTES,
) : PluginCodeFetcher {

    private val base = client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build()

    override suspend fun fetch(url: String, allowPrivateHost: String?): String = withContext(Dispatchers.IO) {
        val policy = NetworkPolicy(allowPrivateHosts = setOfNotNull(allowPrivateHost))
        val client = policy.withDns(base.dns).apply(base.newBuilder()).build()
        val request = Request.Builder().url(url).header("Accept", "text/javascript, application/javascript, */*").get().build()
        try {
            policy.execute(client, request).use { response ->
                if (!response.isSuccessful) throw AddonException(Kind.Http, response.code.toString())
                val source = response.body.source()
                source.request(maxBytes + 1)
                if (source.buffer.size > maxBytes) throw AddonException(Kind.TooLarge)
                source.readUtf8()
            }
        } catch (e: IOException) {
            throw AddonException(Kind.Network, cause = e)
        }
    }

    companion object {
        /** Gerçek kazıyıcılar 3–20 KB; 1 MB cömert bir üst sınırdır. */
        const val MAX_BYTES = 1L * 1024 * 1024
    }
}
