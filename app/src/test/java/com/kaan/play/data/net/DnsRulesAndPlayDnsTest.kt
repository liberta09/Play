package com.kaan.play.data.net

import com.kaan.play.data.cloudstream.BlockedAddressException
import com.kaan.play.data.cloudstream.PlayNetworkGuard
import com.kaan.play.data.plugin.NetworkPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

class DnsRulesAndPlayDnsTest {

    private val v6a = InetAddress.getByAddress("example.com", ByteArray(16) { if (it == 15) 1 else 0x20 })
    private val v6b = InetAddress.getByAddress("example.com", ByteArray(16) { if (it == 15) 2 else 0x20 })
    private val v4a = InetAddress.getByAddress("example.com", byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34))
    private val v4b = InetAddress.getByAddress("example.com", byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 35))
    private val private192 = InetAddress.getByAddress("evil.example", byteArrayOf(192.toByte(), 168.toByte(), 1, 10))
    private val loopbackV4 = InetAddress.getByAddress("rebind.example", byteArrayOf(127, 0, 0, 1))

    @Before
    fun clearPorts() {
        PlayNetworkGuard.clearAllowedLoopbackPorts()
    }

    @After
    fun restorePorts() {
        PlayNetworkGuard.clearAllowedLoopbackPorts()
    }

    @Test
    fun `ozel DoH adresi duzeltilir, sorgu ve parca atilir, gecersizler reddedilir`() {
        // Yalnızca sunucu adı: https:// ve /dns-query eklenir.
        assertEquals(
            CustomDohResult.Valid("https://dns.example.com/dns-query"),
            DnsRules.normalizeCustomDohUrl("  dns.example.com  "),
        )
        // Yol verilmişse korunur, sorgu (?) ve parça (#) atılır.
        assertEquals(
            CustomDohResult.Valid("https://dns.example.com/custom-doh"),
            DnsRules.normalizeCustomDohUrl("dns.example.com/custom-doh?dns=AA#section"),
        )
        assertEquals(
            CustomDohResult.Valid("https://dns.google/dns-query"),
            DnsRules.normalizeCustomDohUrl("https://dns.google/dns-query?name=example.com#top"),
        )
        // Şemasız sorgu ve parça temizlenince hâlâ yalnızca sunucu adı ise /dns-query eklenir.
        assertEquals(
            CustomDohResult.Valid("https://doh.example.net/dns-query"),
            DnsRules.normalizeCustomDohUrl("doh.example.net?foo=bar#baz"),
        )

        // Geçersiz: boş, http, ftp, kullanıcı bilgisi, yerel/özel adresler.
        val invalidInputs = listOf(
            "",
            "   ",
            "http://dns.google/dns-query",
            "ftp://dns.example.com/dns-query",
            "http:dns.google",
            "https://user:pass@dns.google/dns-query",
            "127.0.0.1",
            "https://127.0.0.1/dns-query",
            "localhost",
            "192.168.1.1",
            "10.0.0.2",
            "100.64.0.5",
            "https://[::1]/dns-query",
            "my-router.local",
            "intranet",
            "https://bad host/dns-query",
        )
        for (input in invalidInputs) {
            assertEquals("Reddedilmeli: $input", CustomDohResult.Invalid, DnsRules.normalizeCustomDohUrl(input))
        }
    }

    @Test
    fun `tum hazir DoH saglayicilari yalnizca https ve bootstrap IP tanimli`() {
        val presets = DnsMode.SELECTABLE.filter { it != DnsMode.Auto && it != DnsMode.Custom }
        assertEquals(8, presets.size)
        for (preset in presets) {
            val url = preset.dohUrl
            assertTrue("HTTPS olmali: ${preset.name}", url != null && url.startsWith("https://"))
            assertTrue("Bootstrap IP tanimli olmali: ${preset.name}", preset.bootstrapIps.isNotEmpty())
            assertTrue("InetAddress parse edilmeli: ${preset.name}", preset.bootstrapInetAddresses().size == preset.bootstrapIps.size)
        }
        assertEquals(DnsMode.Auto, DnsMode.fromStored(null))
        assertEquals(DnsMode.Auto, DnsMode.fromStored("unknown_udp53"))
        assertEquals(DnsMode.Quad9, DnsMode.fromStored("quad9"))
    }

    @Test
    fun `otomatik mod sirasi sistem sonra Cloudflare sonra Google DoH`() {
        val logCalls = mutableListOf<String>()
        val cloudflareIp = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
        val googleIp = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))

        // 1) Sistem DNS başarılıysa DoH denenmez.
        val sysOk = PlayDns.resolveAuto(
            hostname = "tmdb.org",
            systemResolve = { logCalls += "system"; listOf(v4a) },
            cloudflareResolve = { logCalls += "cf"; listOf(cloudflareIp) },
            googleResolve = { logCalls += "google"; listOf(googleIp) },
        )
        assertEquals(listOf(v4a), sysOk)
        assertEquals(listOf("system"), logCalls)

        // 2) Sistem başarısız -> Cloudflare bir kez denenir.
        logCalls.clear()
        val cfOk = PlayDns.resolveAuto(
            hostname = "tmdb.org",
            systemResolve = { logCalls += "system"; throw UnknownHostException("fail") },
            cloudflareResolve = { logCalls += "cf"; listOf(cloudflareIp) },
            googleResolve = { logCalls += "google"; listOf(googleIp) },
        )
        assertEquals(listOf(cloudflareIp), cfOk)
        assertEquals(listOf("system", "cf"), logCalls)

        // 3) Sistem + Cloudflare başarısız -> Google bir kez denenir.
        logCalls.clear()
        val googleOk = PlayDns.resolveAuto(
            hostname = "tmdb.org",
            systemResolve = { logCalls += "system"; throw UnknownHostException("fail1") },
            cloudflareResolve = { logCalls += "cf"; throw UnknownHostException("fail2") },
            googleResolve = { logCalls += "google"; listOf(googleIp) },
        )
        assertEquals(listOf(googleIp), googleOk)
        assertEquals(listOf("system", "cf", "google"), logCalls)

        // 4) Üçü de başarısızsa hata döner (ana makine adı mesajda sızmaz).
        logCalls.clear()
        try {
            PlayDns.resolveAuto(
                hostname = "secret-host.example",
                systemResolve = { logCalls += "system"; throw UnknownHostException("sys fail") },
                cloudflareResolve = { logCalls += "cf"; emptyList() },
                googleResolve = { logCalls += "google"; throw UnknownHostException("google fail") },
            )
            fail("Expected UnknownHostException")
        } catch (e: UnknownHostException) {
            assertFalse(e.message.orEmpty().contains("secret-host.example"))
        }
        assertEquals(listOf("system", "cf", "google"), logCalls)
    }

    @Test
    fun `saglayici secimi canli olarak degisir ve DoH sonucu IPv4 once sirasini korur`() {
        val playDns = PlayDns(
            systemDns = Dns { listOf(v6a, v4a) },
            customOverrideResolver = { url ->
                when (url) {
                    DnsMode.Cloudflare.dohUrl -> Dns { listOf(v6a, v4b, v6b, v4a) }
                    "https://custom.doh.example/dns-query" -> Dns { listOf(v6b, v4b) }
                    else -> Dns { throw UnknownHostException("unexpected") }
                }
            },
        )

        // Varsayılan Auto -> sistem sonucu IPv4-önce sıralanır.
        val autoRes = playDns.lookup("video.example.com")
        assertEquals(listOf(v4a, v6a), autoRes)
        assertTrue(autoRes[0] is Inet4Address)
        assertTrue(autoRes[1] is Inet6Address)

        // Yeniden başlatmadan Cloudflare'e geçiş -> yalnızca Cloudflare sonucunu IPv4-önce döner.
        playDns.updateConfig(DnsMode.Cloudflare)
        val cfRes = playDns.lookup("video.example.com")
        assertEquals(listOf(v4b, v4a, v6a, v6b), cfRes)

        // Özel DoH -> normalize edilip kullanılır ve Ipv4FirstDns.wrapping ile uyumludur.
        playDns.updateConfig(DnsMode.Custom, "https://custom.doh.example/dns-query")
        val wrapped = Ipv4FirstDns.wrapping(playDns)
        assertEquals(listOf(v4b, v6b), wrapped.lookup("video.example.com"))

        // IP sabiti (yerel torrent sunucusu 127.0.0.1) DoH sunucusuna sorulmaz.
        assertEquals(listOf(InetAddress.getByName("127.0.0.1")), playDns.lookup("127.0.0.1"))
    }

    @Test
    fun `DoH sonucu yerel veya ozel adres donerse NetworkPolicy ve PlayNetworkGuard reddeder`() {
        val poisonedDns = PlayDns(
            systemDns = Dns { listOf(v4a) },
            customOverrideResolver = {
                Dns { host ->
                    if (host == "mixed.example") listOf(private192, v4a)
                    else listOf(loopbackV4, private192)
                }
            },
        )
        poisonedDns.updateConfig(DnsMode.Cloudflare)

        // NetworkPolicy, active PlayDns üzerinden çözer ve yalnızca yerel adres varsa reddeder.
        val policyDns = NetworkPolicy(upstreamDns = poisonedDns).policyDns()
        try {
            policyDns.lookup("evil.example")
            fail("Expected UnknownHostException for poisoned DoH response")
        } catch (e: UnknownHostException) {
            assertEquals("blocked", e.message)
        }

        // Karma yanıtta özel IP elenir, genel IPv4 kalır.
        assertEquals(listOf(v4a), policyDns.lookup("mixed.example"))

        // Ağ katmanında PlayNetworkGuard: rebind edilmiş loopback adres port izinli değilse reddedilir.
        assertTrue(PlayNetworkGuard.isBlockedResolvedAddress(private192, 443))
        assertTrue(PlayNetworkGuard.isBlockedResolvedAddress(loopbackV4, 8080))
        PlayNetworkGuard.allowLoopbackPort(8080)
        assertFalse(PlayNetworkGuard.isBlockedResolvedAddress(loopbackV4, 8080))

        // Gerçek MockWebServer üzerinde DNS rebind koruması: "rebind.example" 127.0.0.1'e çözünürse BlockedAddressException atar.
        PlayNetworkGuard.clearAllowedLoopbackPorts()
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().body("secret").build())
            val guarded = OkHttpClient.Builder()
                .dns(poisonedDns)
                .addInterceptor(PlayNetworkGuard.interceptor)
                .addNetworkInterceptor(PlayNetworkGuard.interceptor)
                .build()
            val req = Request.Builder().url("http://rebind.example:${server.port}/steal").build()
            try {
                guarded.newCall(req).execute().use { fail("Expected BlockedAddressException") }
            } catch (_: BlockedAddressException) {
                // beklenen: DoH'tan dönen 127.0.0.1 ağ katmanında reddedildi
            }
        } finally {
            server.close()
        }
    }

    @Test
    fun `exampleCom kontrolu uc sonucu dogru siniflandirir`() = runTest {
        val d = Dispatchers.Unconfined
        assertEquals(
            DnsCheckResult.Resolved,
            PlayDns.checkHostWithTimeout(DnsMode.Cloudflare, "", dispatcher = d) { listOf(v4a) },
        )
        assertEquals(
            DnsCheckResult.Empty,
            PlayDns.checkHostWithTimeout(DnsMode.Cloudflare, "", dispatcher = d) { emptyList() },
        )
        assertEquals(
            DnsCheckResult.Empty,
            PlayDns.checkHostWithTimeout(DnsMode.Cloudflare, "", dispatcher = d) { throw UnknownHostException("example.com: NXDOMAIN") },
        )
        assertEquals(
            DnsCheckResult.Failed,
            PlayDns.checkHostWithTimeout(DnsMode.Cloudflare, "", dispatcher = d) {
                throw UnknownHostException("network error").apply { initCause(java.net.ConnectException("refused")) }
            },
        )
        assertEquals(
            DnsCheckResult.Failed,
            PlayDns.checkHostWithTimeout(DnsMode.Custom, "http://insecure.example/dns-query", dispatcher = d) { listOf(v4a) },
        )
    }
}
