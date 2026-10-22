package com.kaan.play.data.plugin.runtime

import com.kaan.play.data.plugin.BlockedAddressException
import com.kaan.play.data.plugin.NetworkPolicy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Eklenti çalıştırıcısının kaynak sınırları. Hepsi çalıştırma (çağrı) başınadır. */
data class PluginLimits(
    val maxRequests: Int = 80,
    val maxResponseBytes: Int = 1 * 1024 * 1024,
    val maxTotalBytes: Long = 12L * 1024 * 1024,
    val maxRequestBodyBytes: Int = 512 * 1024,
    val requestTimeoutMs: Long = 20_000,
    val maxHtmlChars: Int = 2 * 1024 * 1024,
    val maxDomNodes: Int = 200_000,
    val memoryLimitBytes: Long = 32L * 1024 * 1024,
    val maxStackBytes: Long = 1L * 1024 * 1024,
    val maxStreams: Int = 200,
)

/**
 * Eklentinin `fetch`'inin yerel tarafı (docs/nuvio-plugin-format.md §3.2/§3.4). Kurallar:
 * - Yalnızca http/https; yerel/özel ağ adresleri reddedilir ([NetworkPolicy]); yönlendirmeler her adımda denetlenir.
 * - Yanıt gövdesi [PluginLimits.maxResponseBytes] ile kırpılır (`truncated`); toplam bayt ve istek sayısı bütçelidir.
 * - Çerezler bu çağrıya özeldir (bellek içi, çağrı bitince atılır): eklentiler arası ve çağrılar arası paylaşım yok.
 * - **TMDB anahtarı:** eklentiye yalnızca yer tutucu verilir; `https://api.themoviedb.org` isteklerinin sorgusundaki yer tutucu
 *   burada gerçek anahtarla değiştirilir (anahtar yalnızca bu sınıfın içinde yaşar). Başka bir ana makineye giden isteklerde
 *   yer tutucu aynen kalır. Anahtarlı istekte yönlendirme izlenmez (anahtar başka bir adrese sızmasın).
 * - Hata durumunda `fetch` atmaz: `ok:false, status:0` döner. Hata metnine adres (dolayısıyla anahtar) yazılmaz.
 */
internal class PluginHttp(
    base: OkHttpClient,
    private val policy: NetworkPolicy,
    private val tmdbKey: String,
    private val limits: PluginLimits,
    private val tmdbHost: String = TMDB_HOST,
    private val tmdbHttpsOnly: Boolean = true,
    private val notes: PluginHttpNotes = PluginHttpNotes(),
) {
    private val client: OkHttpClient = policy.apply(base.newBuilder())
        .cookieJar(MemoryCookieJar())
        .callTimeout(limits.requestTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    private val requests = AtomicInteger()
    private val totalBytes = AtomicLong()

    /** @param optionsJson JS `fetch` sarmalayıcısının gönderdiği `{url, method, headers, bodyKind, body, redirect}`. */
    suspend fun fetch(optionsJson: String): String {
        val opts = try {
            Json.parseToJsonElement(optionsJson).jsonObject
        } catch (_: Exception) {
            return failure("bad request")
        }
        if (requests.incrementAndGet() > limits.maxRequests) return failure("request budget exceeded")
        if (totalBytes.get() > limits.maxTotalBytes) return failure("data budget exceeded")

        val rawUrl = opts.str("url") ?: return failure("bad url")
        val parsed = rawUrl.toHttpUrlOrNull() ?: return failure("bad url")
        val (url, usesTmdbKey) = substituteTmdbKey(parsed)
        val host = parsed.host // tanı notlarında yalnızca ana makine adı (yol/sorgu ve dolayısıyla anahtar asla)

        val request = try {
            buildRequest(url, opts)
        } catch (_: IllegalArgumentException) {
            return failure("bad request")
        } ?: return failure("request too large")

        val follow = !usesTmdbKey && opts.str("redirect") != "manual"
        return try {
            policy.execute(client, request, follow).use { response ->
                if (!response.isSuccessful) notes.add(host, response.code.toString())
                val source = response.body.source()
                val cap = limits.maxResponseBytes.toLong()
                source.request(cap + 1)
                val truncated = source.buffer.size > cap
                val bytes = source.buffer.readByteArray(minOf(source.buffer.size, cap))
                totalBytes.addAndGet(bytes.size.toLong())

                val charset = response.body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                // Tanı: yalnızca ana makine, yöntem, durum, içerik türü, bayt uzunluğu, yönlendirme olduysa son ana makine (yol/sorgu/çerez/başlık yok).
                val finalHost = response.request.url.host.takeIf { it != host && !usesTmdbKey }
                android.util.Log.d(
                    "PlayPluginNet",
                    "$host ${request.method} -> ${response.code} ctype=${response.body.contentType()?.let { "${it.type}/${it.subtype}" }} " +
                        "len=${bytes.size}${if (truncated) "+" else ""} charset=${charset.name()}${finalHost?.let { " redirect->$it" }.orEmpty()}",
                )
                val headers = buildJsonObject {
                    response.headers.names().forEach { name -> put(name.lowercase(), response.headers.values(name).joinToString(", ")) }
                }
                buildJsonObject {
                    put("ok", response.isSuccessful)
                    put("status", response.code)
                    put("statusText", response.message)
                    // Anahtarlı adres yanıta/günlüğe geri verilmez: gerçek anahtar yalnızca yer tutucuyla yazılmış hâlini görür.
                    put("url", if (usesTmdbKey) parsed.toString() else response.request.url.toString())
                    put("redirected", response.request.url != url && !usesTmdbKey)
                    put("headers", headers)
                    put("body", String(bytes, charset))
                    put("bodyBase64", Base64.getEncoder().encodeToString(bytes))
                    put("truncated", truncated)
                }.toString()
            }
        } catch (e: BlockedAddressException) {
            notes.add(host, "blocked")
            failure("blocked")
        } catch (e: IOException) {
            notes.add(host, "network error")
            failure("network error")
        } catch (e: IllegalArgumentException) {
            failure("bad request")
        }
    }

    /** `api_key` (ya da herhangi bir sorgu değeri) yer tutucuysa gerçek anahtarla değiştirir; yalnızca TMDB ana makinesinde. */
    private fun substituteTmdbKey(url: HttpUrl): Pair<HttpUrl, Boolean> {
        if (tmdbKey.isBlank() || url.host != tmdbHost || (tmdbHttpsOnly && url.scheme != "https")) return url to false
        val hasMatch = (0 until url.querySize).any {
            val name = url.queryParameterName(it)
            val value = url.queryParameterValue(it)
            value == PluginPrelude.TMDB_PLACEHOLDER || name.equals("api_key", ignoreCase = true)
        }
        if (!hasMatch) return url to false
        // Parametre sırası ve adları korunur; yer tutucu veya api_key değerleri gerçek anahtarla değişir.
        val builder = url.newBuilder().query(null)
        for (i in 0 until url.querySize) {
            val name = url.queryParameterName(i)
            val value = url.queryParameterValue(i)
            val newValue = if (value == PluginPrelude.TMDB_PLACEHOLDER || name.equals("api_key", ignoreCase = true)) {
                tmdbKey
            } else {
                value
            }
            builder.addQueryParameter(name, newValue)
        }
        return builder.build() to true
    }

    /** @return null: gövde çok büyük. @throws IllegalArgumentException geçersiz başlık adı/değeri. */
    private fun buildRequest(url: HttpUrl, opts: JsonObject): Request? {
        val method = (opts.str("method") ?: "GET").uppercase().takeIf { it in METHODS } ?: "GET"
        val builder = Request.Builder().url(url)

        var contentType: String? = null
        var hasUa = false
        (opts["headers"] as? JsonObject)?.forEach { (name, value) ->
            val v = (value as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val lower = name.lowercase()
            if (lower in DROPPED_HEADERS) return@forEach
            if (lower == "content-type") contentType = v
            if (lower == "user-agent") hasUa = true
            builder.header(name, v)
        }
        if (!hasUa) builder.header("User-Agent", DEFAULT_UA)

        val kind = opts.str("bodyKind") ?: "none"
        val payload: ByteArray? = when (kind) {
            "text" -> opts.str("body").orEmpty().toByteArray(Charsets.UTF_8)
            "base64" -> try {
                Base64.getDecoder().decode(opts.str("body").orEmpty())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("bad body")
            }
            else -> null
        }
        if (payload != null && payload.size > limits.maxRequestBodyBytes) return null

        val needsBody = method in setOf("POST", "PUT", "PATCH")
        val body = when {
            payload != null && method != "GET" && method != "HEAD" ->
                payload.toRequestBody((contentType ?: defaultContentType(method)).toMediaTypeOrNull())
            needsBody -> ByteArray(0).toRequestBody((contentType ?: defaultContentType(method)).toMediaTypeOrNull())
            else -> null
        }
        return builder.method(method, body).build()
    }

    private fun defaultContentType(method: String) =
        if (method == "POST") "application/x-www-form-urlencoded" else "application/json"

    private fun failure(reason: String): String = buildJsonObject {
        put("ok", false)
        put("status", 0)
        put("statusText", reason)
        put("url", "")
        put("headers", buildJsonObject { })
        put("body", "")
        put("bodyBase64", "")
    }.toString()

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull

    companion object {
        const val TMDB_HOST = "api.themoviedb.org"
        private val METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")

        /** OkHttp'nin kendisinin yönettiği ya da güvenliği ilgilendiren başlıklar eklentiden kabul edilmez. */
        private val DROPPED_HEADERS = setOf("host", "content-length", "accept-encoding", "connection", "transfer-encoding", "te", "upgrade", "expect")
        private const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}

/**
 * Bellek içi çerez kavanozu. Her çalıştırma kendi kavanozunu kurar ve atar: kalıcı depoya yazılmaz, uygulamanın başka
 * çerezleriyle (Web görünümü dahil) ve diğer eklentilerle paylaşılmaz.
 */
internal class MemoryCookieJar : CookieJar {
    private val store = ArrayList<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { c ->
            store.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            if (c.expiresAt > now && store.size < 200) store.add(c)
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.removeAll { it.expiresAt <= now }
        return store.filter { it.matches(url) }
    }
}

/** Ağ köprüsünün kısa tanı kayıtları (ana makine + durum). Adres yolu/sorgusu içermez; arayüzde gösterilmez. */
internal class PluginHttpNotes {
    private val list = java.util.concurrent.CopyOnWriteArrayList<String>()

    fun add(host: String, what: String) {
        val entry = "$host $what"
        if (list.size < 8 && entry !in list) list += entry
    }

    fun snapshot(): List<String> = list.toList()
}
