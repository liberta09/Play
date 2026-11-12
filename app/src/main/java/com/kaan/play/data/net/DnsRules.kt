package com.kaan.play.data.net

import com.kaan.play.data.plugin.NetworkPolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.util.Locale

/**
 * Uygulama içi şifreli DNS (DoH) sağlayıcı seçenekleri.
 * Düz/şifresiz (UDP 53) seçenek içermez; ayar yalnızca Play uygulamasının isteklerini etkiler.
 */
enum class DnsMode(
    val dohUrl: String?,
    val bootstrapIps: List<String>,
) {
    Auto(
        dohUrl = null,
        bootstrapIps = emptyList(),
    ),
    Cloudflare(
        dohUrl = "https://cloudflare-dns.com/dns-query",
        bootstrapIps = listOf("1.1.1.1", "1.0.0.1"),
    ),
    Google(
        dohUrl = "https://dns.google/dns-query",
        bootstrapIps = listOf("8.8.8.8", "8.8.4.4"),
    ),
    Quad9(
        dohUrl = "https://dns.quad9.net/dns-query",
        bootstrapIps = listOf("9.9.9.9", "149.112.112.112"),
    ),
    AdGuard(
        dohUrl = "https://dns.adguard-dns.com/dns-query",
        bootstrapIps = listOf("94.140.14.14", "94.140.15.15"),
    ),
    Mullvad(
        dohUrl = "https://doh.mullvad.net/dns-query",
        bootstrapIps = listOf("194.242.2.2", "194.242.2.3"),
    ),
    DnsSb(
        dohUrl = "https://doh.sb/dns-query",
        bootstrapIps = listOf("185.222.222.222", "45.11.45.11"),
    ),
    CanadianShield(
        dohUrl = "https://private.canadianshield.cira.ca/dns-query",
        bootstrapIps = listOf("149.112.121.10", "149.112.122.10"),
    ),
    CleanBrowsingFamily(
        dohUrl = "https://doh.cleanbrowsing.org/doh/family-filter/",
        bootstrapIps = listOf("185.228.168.168", "185.228.169.168"),
    ),
    Custom(
        dohUrl = null,
        bootstrapIps = emptyList(),
    );

    fun bootstrapInetAddresses(): List<InetAddress> =
        bootstrapIps.mapNotNull { ip -> runCatching { InetAddress.getByName(ip) }.getOrNull() }

    companion object {
        val SELECTABLE: List<DnsMode> = entries.toList()

        fun fromStored(raw: String?, defaultMode: DnsMode = Auto): DnsMode {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return defaultMode
            return entries.firstOrNull { it.name.equals(trimmed, ignoreCase = true) } ?: defaultMode
        }
    }
}

sealed interface CustomDohResult {
    data class Valid(val url: String) : CustomDohResult
    data object Invalid : CustomDohResult
}

enum class DnsCheckResult {
    Resolved,
    Empty,
    Failed,
}

/** Özel DoH adresi doğrulama ve normalizasyon kuralları (Android'den bağımsız). */
object DnsRules {
    const val CHECK_TEST_HOST = "example.com"
    const val CHECK_TIMEOUT_MS = 5_000L

    private val SCHEME_PREFIX_RE = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")
    private val HOST_PORT_ONLY_RE = Regex("^(\\[[0-9a-fA-F:]+]|[^/:]+):\\d{1,5}(/.*)?$")

    /**
     * Özel DoH sunucu adresini doğrular ve normalleştirir:
     * - Yalnızca HTTPS kabul edilir (`http://` ve diğer şemalar reddedilir).
     * - Sorgu (`?`) ve parça (`#`) atılır.
     * - Yalnızca sunucu adı girilirse `https://` ve `/dns-query` eklenir.
     * - Yerel/özel adresler ([NetworkPolicy]) reddedilir.
     */
    fun normalizeCustomDohUrl(raw: String): CustomDohResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return CustomDohResult.Invalid
        if (trimmed.any { it.isWhitespace() || it.code < 0x20 }) return CustomDohResult.Invalid

        val withoutFragOrQuery = trimmed.substringBefore('#').substringBefore('?').trim()
        if (withoutFragOrQuery.isEmpty() || withoutFragOrQuery.contains('@')) return CustomDohResult.Invalid

        val candidate = if (withoutFragOrQuery.contains("://")) {
            val scheme = withoutFragOrQuery.substringBefore("://").lowercase(Locale.ROOT)
            if (scheme != "https") return CustomDohResult.Invalid
            "https://" + withoutFragOrQuery.substringAfter("://")
        } else {
            // `http:...`, `ftp:...` gibi şemasız ama `:` içeren şema girişlerini reddet; host:port geçebilir.
            if (SCHEME_PREFIX_RE.matches(withoutFragOrQuery) && !HOST_PORT_ONLY_RE.matches(withoutFragOrQuery)) {
                return CustomDohResult.Invalid
            }
            if (withoutFragOrQuery.startsWith("//") || withoutFragOrQuery.startsWith('/')) {
                return CustomDohResult.Invalid
            }
            val cleanHostPath = withoutFragOrQuery.trimEnd('/')
            if (cleanHostPath.isEmpty()) return CustomDohResult.Invalid
            if ('/' !in cleanHostPath) {
                "https://$cleanHostPath/dns-query"
            } else {
                "https://$withoutFragOrQuery"
            }
        }

        val parsed = candidate.toHttpUrlOrNull() ?: return CustomDohResult.Invalid
        if (parsed.scheme != "https") return CustomDohResult.Invalid
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return CustomDohResult.Invalid

        val host = parsed.host
        if (host.isBlank()) return CustomDohResult.Invalid
        // Tek etiketli yerel adları (örn. "intranet", "router") reddet.
        if ('.' !in host && ':' !in host) return CustomDohResult.Invalid

        // Yerel/özel ağ adreslerini ve loopback/link-local sabitlerini reddet.
        if (NetworkPolicy().checkUrl(parsed) != null) return CustomDohResult.Invalid

        val normalized = parsed.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
        return CustomDohResult.Valid(normalized)
    }
}
