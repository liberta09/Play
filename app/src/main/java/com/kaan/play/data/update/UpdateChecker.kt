package com.kaan.play.data.update

import com.kaan.play.data.net.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Yeni sürüm bilgisi. [tag] ekranda gösterilen etiket ("v1.0.1"), [pageUrl] tarayıcıda açılan release sayfası.
 * [apk]: bu cihazın işlemci türüne uyan, SHA-256 dosyası da olan APK; yoksa (eşleşme yok) eski tarayıcı yöntemi kullanılır.
 */
@Serializable
data class UpdateInfo(val tag: String, val pageUrl: String, val apk: UpdateApk? = null)

/**
 * Uygulama içi güncelleme kontrolü: GitHub `releases/latest` API'sinden son sürüm etiketi alınır, yüklü sürümle
 * karşılaştırılır ve release'teki cihaza uygun APK seçilir. Yalnızca haber verir; indirme/kurma [UpdateFlow]'dadır
 * (uygun APK yoksa kullanıcı release sayfasını tarayıcıda açar).
 *
 * Kurallar: oturum (süreç) başına en çok bir kontrol; hata/zaman aşımı sessizce geçilir; kullanıcı "Sonra" derse
 * aynı oturumda bir daha gösterilmez. Hiçbir ayar/kimlik/cihaz bilgisi gönderilmez (yalnızca herkese açık bir GET).
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val json: Json,
    private val installedVersion: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val timeoutSeconds: Long = 8,
    /** Cihazın işlemci türleri, tercih sırasıyla (`Build.SUPPORTED_ABIS`). */
    private val abis: List<String> = emptyList(),
) {
    private val started = AtomicBoolean(false)
    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    /**
     * Yalnızca debug derlemede, sahte release sunucusuyla denemek için: bu ana bilgisayar https şartı olmadan da kabul edilir.
     * Yayın derlemesinde hiçbir zaman ayarlanmaz.
     */
    @Volatile
    var debugHost: String? = null
        private set
    @Volatile
    private var endpointOverride: String? = null
    private val _available = MutableStateFlow<UpdateInfo?>(null)

    /** Kullanıcıya gösterilecek güncelleme; yoksa (ya da kapatıldıysa) null. */
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    /** Oturumda yalnızca ilk çağrı ağa gider; sonrakiler (ekran döndürme, yeniden oluşturma) hiçbir şey yapmaz. */
    fun checkOnce(scope: CoroutineScope, debugEndpoint: String? = null) {
        if (!started.compareAndSet(false, true)) return
        if (debugEndpoint != null) {
            endpointOverride = debugEndpoint
            debugHost = debugEndpoint.toHttpUrlOrNull()?.host
        }
        scope.launch {
            try {
                _available.value = fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _available.value = null // sessizce geç
            } finally {
                _completed.value = true
            }
        }
    }

    /** "Sonra" ya da "İndir": bu oturumda bir daha gösterilmez. */
    fun dismiss() {
        _available.value = null
    }

    /** Son sürüm yüklüden yeniyse bilgisini, aksi halde (ya da herhangi bir sorunda) null döner. */
    internal suspend fun fetch(): UpdateInfo? {
        val request = Request.Builder()
            .url((endpointOverride ?: endpoint).toHttpUrlOrNull() ?: return null)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Play-Android-UpdateCheck")
            .build()
        val call = client.newBuilder().callTimeout(timeoutSeconds, TimeUnit.SECONDS).build().newCall(request)
        val text = call.await().use { response ->
            if (!response.isSuccessful) return null
            val source = response.body.source()
            source.request(MAX_BODY_BYTES + 1)
            if (source.buffer.size > MAX_BODY_BYTES) return null
            source.readUtf8()
        }
        val release = json.decodeFromString<Release>(text)
        val tag = release.tagName?.trim()?.take(MAX_TAG_CHARS).orEmpty()
        val page = release.htmlUrl.orEmpty()
        if (release.draft || release.prerelease) return null
        if (!UpdateRules.isNewer(tag, installedVersion) || !UpdateRules.isSafePage(page)) return null
        val assets = release.assets.map { ReleaseAsset(it.name.orEmpty(), it.url.orEmpty(), it.size) }
        return UpdateInfo(tag, page, UpdateApkRules.pick(tag, assets, abis, debugHost))
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String? = null,
        @SerialName("browser_download_url") val url: String? = null,
        val size: Long = 0,
    )

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.github.com/repos/liberta09/Play/releases/latest"
        private const val MAX_BODY_BYTES = 512L * 1024
        private const val MAX_TAG_CHARS = 24
    }
}

/** Android'den bağımsız sürüm kuralları (birim testli). */
object UpdateRules {

    private class Version(val numbers: List<Int>, val preRelease: Boolean)

    /** "v1.2.3", "1.2", "1.0.0-beta+7" → sayılar [1,2,3] + ön sürüm bayrağı; sayısal olmayan etiket null. */
    private fun parse(raw: String): Version? {
        val text = raw.trim().removePrefix("v").removePrefix("V")
        val core = text.substringBefore('+').substringBefore('-')
        if (core.isEmpty()) return null
        val numbers = core.split('.').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        return Version(numbers, preRelease = text.substringBefore('+').contains('-'))
    }

    /**
     * [latestTag] yüklü sürümden yeniyse true. Sayılar bileşen bileşen karşılaştırılır (eksik bileşen 0).
     * Sayılar eşitse yalnızca "yüklü ön sürüm, son sürüm kararlı" (1.0.0-beta → 1.0.0) yeni sayılır; tersi ve eşitlik değil.
     * Ayrıştırılamayan etiket/sürüm adı için false: şüphede kullanıcıyı rahatsız etme.
     */
    fun isNewer(latestTag: String, installed: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val current = parse(installed) ?: return false
        val size = maxOf(latest.numbers.size, current.numbers.size)
        for (i in 0 until size) {
            val a = latest.numbers.getOrElse(i) { 0 }
            val b = current.numbers.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return current.preRelease && !latest.preRelease
    }

    /** Tarayıcıda yalnızca GitHub'ın https sayfası açılır; yanıt başka bir şey gösterse de gidilmez. */
    fun isSafePage(url: String): Boolean {
        val http = url.toHttpUrlOrNull() ?: return false
        return http.isHttps && http.host == "github.com"
    }
}
