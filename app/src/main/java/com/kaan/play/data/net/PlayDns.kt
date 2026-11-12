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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uygulama içi merkezi DNS çözücü.
 *
 * - Varsayılan [DnsMode.Auto]: Android sistem DNS'ini dener; başarısız olursa adım başına 2,5 sn, toplamda en çok 5 sn
 *   sınırıyla bir kez Cloudflare DoH, o da olmazsa bir kez Google DoH dener.
 * - Seçilen DoH sağlayıcısı ayar değişir değişmez yeni isteklerde geçerli olur (yeniden başlatma gerekmez).
 * - Olumlu DoH sonuçları 5 dk (en çok 512 kayıt) ve tüm adımları başarısız olan adlar 10 dk (en çok 512 kayıt)
 *   önbellekte tutulur; sistem DNS sonuçları önbelleğe alınmaz; DNS modu değişince iki önbellek de temizlenir.
 * - Tüm sonuçlarda [Ipv4FirstDns.order] korunur; DNS sorgu adı/adresi günlüklenmez.
 */
class PlayDns(
    private val systemDns: Dns = Dns.SYSTEM,
    private val initialLoader: (() -> Pair<DnsMode, String>)? = null,
    bootstrapBaseClient: OkHttpClient = defaultBootstrapClient(),
    private val customOverrideResolver: ((String) -> Dns)? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val fallbackStepTimeoutMs: Long = AUTO_FALLBACK_STEP_TIMEOUT_MS,
    private val autoTotalTimeoutMs: Long = AUTO_TOTAL_TIMEOUT_MS,
    private val fallbackExecutor: ExecutorService = DEFAULT_FALLBACK_EXECUTOR,
) : Dns {

    private data class Config(
        val mode: DnsMode = DnsMode.Auto,
        val customUrl: String = "",
    )

    private class CacheEntry<V>(val value: V, val createdAtMs: Long)

    private class BoundedTtlCache<V>(
        private val maxEntries: Int,
        private val ttlMs: Long,
        private val clock: () -> Long,
    ) {
        private val map = object : LinkedHashMap<String, CacheEntry<V>>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<V>>?): Boolean =
                size > maxEntries
        }

        @Synchronized
        fun get(key: String): V? {
            val entry = map[key] ?: return null
            if (clock() - entry.createdAtMs >= ttlMs) {
                map.remove(key)
                return null
            }
            return entry.value
        }

        @Synchronized
        fun put(key: String, value: V) {
            map[key] = CacheEntry(value, clock())
        }

        @Synchronized
        fun clear() {
            map.clear()
        }

        @Synchronized
        fun size(): Int = map.size
    }

    @Volatile
    private var config = Config()

    private val initialized = AtomicBoolean(initialLoader == null)

    private val positiveCache = BoundedTtlCache<List<InetAddress>>(
        maxEntries = MAX_CACHE_ENTRIES,
        ttlMs = POSITIVE_CACHE_TTL_MS,
        clock = clock,
    )

    private val negativeCache = BoundedTtlCache<Boolean>(
        maxEntries = MAX_CACHE_ENTRIES,
        ttlMs = NEGATIVE_CACHE_TTL_MS,
        clock = clock,
    )

    private val bootstrapClient: OkHttpClient = bootstrapBaseClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val autoFallbackClient: OkHttpClient = bootstrapBaseClient.newBuilder()
        .connectTimeout(fallbackStepTimeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .readTimeout(fallbackStepTimeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .callTimeout(fallbackStepTimeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .build()

    // Özel DoH sunucu alan adının kendisini çözerken de özel/yerel IP dönüşü engellenir.
    private val customBootstrapDns: Dns = NetworkPolicy(upstreamDns = systemDns).policyDns()

    private val dohCache = ConcurrentHashMap<String, Dns>()

    fun updateConfig(mode: DnsMode, customUrl: String = config.customUrl) {
        initialized.set(true)
        val prev = config
        val next = Config(mode = mode, customUrl = customUrl)
        config = next
        if (prev.mode != next.mode || (next.mode == DnsMode.Custom && prev.customUrl != next.customUrl)) {
            clearCaches()
        }
    }

    internal fun clearCaches() {
        positiveCache.clear()
        negativeCache.clear()
    }

    internal fun positiveCacheSize(): Int = positiveCache.size()

    internal fun negativeCacheSize(): Int = negativeCache.size()

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
        bypassCache: Boolean = false,
    ): List<InetAddress> {
        // IP sabitleri (örn. yerel torrent sunucusu 127.0.0.1) DoH sunucusuna sorulmaz.
        NetworkPolicy.literalAddress(hostname)?.let { return listOf(it) }
        if (hostname.equals("localhost", ignoreCase = true)) {
            return Ipv4FirstDns.order(systemDns.lookup(hostname))
        }

        val cacheKey = hostname.trim().lowercase(Locale.ROOT)
        if (!bypassCache) {
            if (negativeCache.get(cacheKey) == true) {
                throw UnknownHostException("cached dns failure")
            }
            positiveCache.get(cacheKey)?.let { return it }
        }

        var solvedViaDoh = false
        val resolved = try {
            when (mode) {
                DnsMode.Auto -> {
                    val outcome = resolveAutoWithOrigin(
                        hostname = hostname,
                        systemResolve = { systemDns.lookup(it) },
                        cloudflareResolve = { resolverForPreset(DnsMode.Cloudflare, autoFallbackClient).lookup(it) },
                        googleResolve = { resolverForPreset(DnsMode.Google, autoFallbackClient).lookup(it) },
                        stepTimeoutMs = fallbackStepTimeoutMs,
                        totalTimeoutMs = autoTotalTimeoutMs,
                        clock = clock,
                        executor = fallbackExecutor,
                    )
                    solvedViaDoh = outcome.fromDoh
                    outcome.addresses
                }
                DnsMode.Custom -> {
                    val normalized = (DnsRules.normalizeCustomDohUrl(customUrl) as? CustomDohResult.Valid)?.url
                    if (normalized != null) {
                        solvedViaDoh = true
                        resolverForCustom(normalized).lookup(hostname)
                    } else {
                        val outcome = resolveAutoWithOrigin(
                            hostname = hostname,
                            systemResolve = { systemDns.lookup(it) },
                            cloudflareResolve = { resolverForPreset(DnsMode.Cloudflare, autoFallbackClient).lookup(it) },
                            googleResolve = { resolverForPreset(DnsMode.Google, autoFallbackClient).lookup(it) },
                            stepTimeoutMs = fallbackStepTimeoutMs,
                            totalTimeoutMs = autoTotalTimeoutMs,
                            clock = clock,
                            executor = fallbackExecutor,
                        )
                        solvedViaDoh = outcome.fromDoh
                        outcome.addresses
                    }
                }
                else -> {
                    solvedViaDoh = true
                    resolverForPreset(mode, bootstrapClient).lookup(hostname)
                }
            }
        } catch (e: UnknownHostException) {
            if (!bypassCache) negativeCache.put(cacheKey, true)
            throw e
        } catch (e: Exception) {
            if (!bypassCache) negativeCache.put(cacheKey, true)
            throw UnknownHostException("dns resolution failed").apply { initCause(e) }
        }

        val ordered = Ipv4FirstDns.order(resolved)
        if (ordered.isEmpty()) {
            if (!bypassCache) negativeCache.put(cacheKey, true)
            throw UnknownHostException("dns empty result")
        }
        // Yalnızca DoH sonuçları olumlu önbelleğe alınır; Android sistem DNS sonuçları önbelleğe alınmaz.
        if (!bypassCache && solvedViaDoh) {
            positiveCache.put(cacheKey, ordered)
        }
        return ordered
    }

    /** Seçili sağlayıcıyla yalnızca `example.com` için 5 sn içinde canlı deneme çözümlemesi yapar. */
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
        return lookupWithConfig(hostname, mode, customUrl, bypassCache = true)
    }

    private fun ensureInitialized() {
        if (!initialized.get() && initialized.compareAndSet(false, true)) {
            val loaded = runCatching { initialLoader?.invoke() }.getOrNull()
            if (loaded != null) {
                val prev = config
                val next = Config(mode = loaded.first, customUrl = loaded.second)
                config = next
                if (prev.mode != next.mode || prev.customUrl != next.customUrl) {
                    clearCaches()
                }
            }
        }
    }

    private fun resolverForPreset(mode: DnsMode, client: OkHttpClient): Dns {
        val url = requireNotNull(mode.dohUrl)
        customOverrideResolver?.invoke(url)?.let { return it }
        val key = "preset:${mode.name}:${client === autoFallbackClient}"
        return dohCache.getOrPut(key) {
            DnsOverHttps.Builder()
                .client(client)
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

    internal data class AutoResolutionOutcome(
        val addresses: List<InetAddress>,
        val fromDoh: Boolean,
    )

    companion object {
        const val AUTO_FALLBACK_STEP_TIMEOUT_MS = 2_500L
        const val AUTO_TOTAL_TIMEOUT_MS = 5_000L
        const val POSITIVE_CACHE_TTL_MS = 5L * 60L * 1000L
        const val NEGATIVE_CACHE_TTL_MS = 10L * 60L * 1000L
        const val MAX_CACHE_ENTRIES = 512

        private val DEFAULT_FALLBACK_EXECUTOR: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "play-dns-fallback").apply { isDaemon = true }
        }

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
            stepTimeoutMs: Long = AUTO_FALLBACK_STEP_TIMEOUT_MS,
            totalTimeoutMs: Long = AUTO_TOTAL_TIMEOUT_MS,
            clock: () -> Long = { System.currentTimeMillis() },
            executor: ExecutorService = DEFAULT_FALLBACK_EXECUTOR,
        ): List<InetAddress> = resolveAutoWithOrigin(
            hostname = hostname,
            systemResolve = systemResolve,
            cloudflareResolve = cloudflareResolve,
            googleResolve = googleResolve,
            stepTimeoutMs = stepTimeoutMs,
            totalTimeoutMs = totalTimeoutMs,
            clock = clock,
            executor = executor,
        ).addresses

        internal fun resolveAutoWithOrigin(
            hostname: String,
            systemResolve: (String) -> List<InetAddress>,
            cloudflareResolve: (String) -> List<InetAddress>,
            googleResolve: (String) -> List<InetAddress>,
            stepTimeoutMs: Long = AUTO_FALLBACK_STEP_TIMEOUT_MS,
            totalTimeoutMs: Long = AUTO_TOTAL_TIMEOUT_MS,
            clock: () -> Long = { System.currentTimeMillis() },
            executor: ExecutorService = DEFAULT_FALLBACK_EXECUTOR,
        ): AutoResolutionOutcome {
            val startedAtMs = clock()
            var lastFailure: UnknownHostException? = null

            // 1) Android sistem DNS (Android kendi önbelleğini tutar, adım doğrudan çalıştırılır).
            val sysAddrs = try {
                systemResolve(hostname)
            } catch (e: UnknownHostException) {
                lastFailure = e
                emptyList()
            } catch (e: Exception) {
                lastFailure = UnknownHostException("dns resolution failed").apply { initCause(e) }
                emptyList()
            }
            if (sysAddrs.isNotEmpty()) {
                return AutoResolutionOutcome(Ipv4FirstDns.order(sysAddrs), fromDoh = false)
            }

            // 2) Yedek DoH adımları (Cloudflare -> Google): adım başına <= 2,5 sn, toplam Otomatik <= 5 sn.
            for (dohStep in listOf(cloudflareResolve, googleResolve)) {
                val elapsed = (clock() - startedAtMs).coerceAtLeast(0L)
                val remainingTotal = totalTimeoutMs - elapsed
                if (remainingTotal <= 0L) {
                    lastFailure = lastFailure ?: UnknownHostException("dns total timeout")
                    break
                }
                val budgetMs = minOf(stepTimeoutMs, remainingTotal).coerceAtLeast(1L)
                val future = executor.submit<List<InetAddress>> { dohStep(hostname) }
                val stepAddrs = try {
                    future.get(budgetMs, TimeUnit.MILLISECONDS)
                } catch (_: TimeoutException) {
                    future.cancel(true)
                    lastFailure = UnknownHostException("dns step timeout")
                    emptyList()
                } catch (e: java.util.concurrent.ExecutionException) {
                    val cause = e.cause
                    lastFailure = (cause as? UnknownHostException)
                        ?: UnknownHostException("dns resolution failed").apply { initCause(cause ?: e) }
                    emptyList()
                } catch (e: Exception) {
                    future.cancel(true)
                    lastFailure = UnknownHostException("dns resolution failed").apply { initCause(e) }
                    emptyList()
                }
                if (stepAddrs.isNotEmpty()) {
                    return AutoResolutionOutcome(Ipv4FirstDns.order(stepAddrs), fromDoh = true)
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
