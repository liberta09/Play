package com.kaan.play.data.net

import com.kaan.play.data.plugin.NetworkPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uygulama içi merkezi DNS çözücü.
 *
 * - Varsayılan [DnsMode.Auto]: Android sistem DNS'ini dener; çözümleme başarısız olursa bir kez Cloudflare DoH,
 *   o da olmazsa bir kez Google DoH dener.
 * - Seçilen DoH sağlayıcısı ayar değişir değişmez yeni isteklerde geçerli olur (yeniden başlatma gerekmez).
 * - Tüm sonuçlarda [Ipv4FirstDns.order] korunur; DNS sorgu adı/adresi günlüklenmez.
 */
class PlayDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val initialLoader: (() -> Pair<DnsMode, String>)? = null,
    bootstrapBaseClient: OkHttpClient = defaultBootstrapClient(),
    private val customOverrideResolver: ((String) -> Dns)? = null,
) : Dns {

    private data class Config(
        val mode: DnsMode = DnsMode.Auto,
        val customUrl: String = "",
    )

    @Volatile
    private var config = Config()

    private val initialized = AtomicBoolean(initialLoader == null)

    private val bootstrapClient: OkHttpClient = bootstrapBaseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    // Özel DoH sunucu alan adının kendisini çözerken de özel/yerel IP dönüşü engellenir.
    private val customBootstrapDns: Dns = NetworkPolicy(upstreamDns = systemDns).policyDns()

    private val dohCache = ConcurrentHashMap<String, Dns>()

    fun updateConfig(mode: DnsMode, customUrl: String = config.customUrl) {
        initialized.set(true)
        config = Config(mode = mode, customUrl = customUrl)
    }

    fun currentMode(): DnsMode {
        ensureInitialized()
        return config.mode
    }

    override fun lookup(hostname: String): List<InetAddress> {
        ensureInitialized()
        val snap = config
        return lookupWithConfig(hostname, snap.mode, snap.customUrl)
    }

    internal fun lookupWithConfig(
        hostname: String,
        mode: DnsMode,
        customUrl: String = "",
    ): List<InetAddress> {
        // IP sabitleri (örn. yerel torrent sunucusu 127.0.0.1) DoH sunucusuna sorulmaz.
        NetworkPolicy.literalAddress(hostname)?.let { return listOf(it) }
        if (hostname.equals("localhost", ignoreCase = true)) {
            return Ipv4FirstDns.order(systemDns.lookup(hostname))
        }

        val resolved = when (mode) {
            DnsMode.Auto -> resolveAuto(
                hostname = hostname,
                systemResolve = { systemDns.lookup(it) },
                cloudflareResolve = { resolverForPreset(DnsMode.Cloudflare).lookup(it) },
                googleResolve = { resolverForPreset(DnsMode.Google).lookup(it) },
            )
            DnsMode.Custom -> {
                val normalized = (DnsRules.normalizeCustomDohUrl(customUrl) as? CustomDohResult.Valid)?.url
                if (normalized != null) {
                    resolverForCustom(normalized).lookup(hostname)
                } else {
                    resolveAuto(
                        hostname = hostname,
                        systemResolve = { systemDns.lookup(it) },
                        cloudflareResolve = { resolverForPreset(DnsMode.Cloudflare).lookup(it) },
                        googleResolve = { resolverForPreset(DnsMode.Google).lookup(it) },
                    )
                }
            }
            else -> resolverForPreset(mode).lookup(hostname)
        }
        return Ipv4FirstDns.order(resolved)
    }

    /** Seçili sağlayıcıyla yalnızca `example.com` için 5 sn içinde deneme çözümlemesi yapar. */
    suspend fun checkExampleCom(
        mode: DnsMode = currentMode(),
        customUrl: String = config.customUrl,
    ): DnsCheckResult = checkHostWithTimeout(
        mode = mode,
        customUrl = customUrl,
        resolveBlock = { host -> lookupForCheck(host, mode, customUrl) },
    )

    private fun lookupForCheck(hostname: String, mode: DnsMode, customUrl: String): List<InetAddress> {
        if (mode == DnsMode.Custom && DnsRules.normalizeCustomDohUrl(customUrl) !is CustomDohResult.Valid) {
            throw UnknownHostException("invalid custom doh")
        }
        return lookupWithConfig(hostname, mode, customUrl)
    }

    private fun ensureInitialized() {
        if (!initialized.get() && initialized.compareAndSet(false, true)) {
            val loaded = runCatching { initialLoader?.invoke() }.getOrNull()
            if (loaded != null) {
                config = Config(mode = loaded.first, customUrl = loaded.second)
            }
        }
    }

    private fun resolverForPreset(mode: DnsMode): Dns {
        val url = requireNotNull(mode.dohUrl)
        customOverrideResolver?.invoke(url)?.let { return it }
        return dohCache.getOrPut("preset:${mode.name}") {
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url(url.toHttpUrl())
                .bootstrapDnsHosts(mode.bootstrapInetAddresses())
                .includeIPv6(true)
                .build()
        }
    }

    private fun resolverForCustom(normalizedUrl: String): Dns {
        customOverrideResolver?.invoke(normalizedUrl)?.let { return it }
        return dohCache.getOrPut("custom:$normalizedUrl") {
            DnsOverHttps.Builder()
                .client(bootstrapClient.newBuilder().dns(customBootstrapDns).build())
                .url(normalizedUrl.toHttpUrl())
                .includeIPv6(true)
                .build()
        }
    }

    companion object {
        fun defaultBootstrapClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        internal fun resolveAuto(
            hostname: String,
            systemResolve: (String) -> List<InetAddress>,
            cloudflareResolve: (String) -> List<InetAddress>,
            googleResolve: (String) -> List<InetAddress>,
        ): List<InetAddress> {
            val steps = listOf(systemResolve, cloudflareResolve, googleResolve)
            var lastFailure: UnknownHostException? = null
            for (step in steps) {
                val addrs = try {
                    step(hostname)
                } catch (e: UnknownHostException) {
                    lastFailure = e
                    emptyList()
                } catch (e: Exception) {
                    lastFailure = UnknownHostException("dns resolution failed").apply { initCause(e) }
                    emptyList()
                }
                if (addrs.isNotEmpty()) {
                    return Ipv4FirstDns.order(addrs)
                }
            }
            throw lastFailure ?: UnknownHostException("dns resolution failed")
        }

        internal suspend fun checkHostWithTimeout(
            mode: DnsMode,
            customUrl: String,
            timeoutMs: Long = DnsRules.CHECK_TIMEOUT_MS,
            dispatcher: CoroutineDispatcher = Dispatchers.IO,
            resolveBlock: (String) -> List<InetAddress>,
        ): DnsCheckResult {
            if (mode == DnsMode.Custom && DnsRules.normalizeCustomDohUrl(customUrl) !is CustomDohResult.Valid) {
                return DnsCheckResult.Failed
            }
            val result = CompletableDeferred<DnsCheckResult>()
            val job = CoroutineScope(SupervisorJob() + dispatcher).launch {
                val computed = try {
                    val addresses = resolveBlock(DnsRules.CHECK_TEST_HOST)
                    if (addresses.isNotEmpty()) DnsCheckResult.Resolved else DnsCheckResult.Empty
                } catch (e: CancellationException) {
                    throw e
                } catch (e: UnknownHostException) {
                    if (isEmptyServerResponse(e)) DnsCheckResult.Empty else DnsCheckResult.Failed
                } catch (_: IOException) {
                    DnsCheckResult.Failed
                } catch (_: Exception) {
                    DnsCheckResult.Failed
                }
                result.complete(computed)
            }
            return try {
                withTimeout(timeoutMs) { result.await() }
            } catch (_: TimeoutCancellationException) {
                job.cancel()
                DnsCheckResult.Failed
            } catch (e: CancellationException) {
                job.cancel()
                throw e
            }
        }

        private fun isEmptyServerResponse(error: UnknownHostException): Boolean {
            // OkHttp DnsOverHttps sunucudan yanıt alıp kayıt bulamadığında IO ağ hatası bastırılmaz ve NXDOMAIN/NODATA döner.
            val hasNetworkFailure = error.cause is IOException || error.suppressed.any { it is IOException }
            if (hasNetworkFailure) return false
            val msg = error.message.orEmpty()
            return msg.contains("NXDOMAIN", ignoreCase = true) ||
                msg.contains("no response", ignoreCase = true) ||
                msg.contains("empty", ignoreCase = true)
        }
    }
}
