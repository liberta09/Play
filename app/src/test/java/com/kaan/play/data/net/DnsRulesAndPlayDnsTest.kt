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

    @Test
    fun `olu ad ikinci sorguda beklemeden olumsuz onbellekten duser ve mod degisince temizlenir`() {
        var nowMs = 1_000_000L
        var calls = 0
        val playDns = PlayDns(
            systemDns = Dns { calls++; throw UnknownHostException("dead") },
            customOverrideResolver = { Dns { calls++; throw UnknownHostException("dead doh") } },
            clock = { nowMs },
        )

        // İlk sorgu Auto modda sistem + CF + Google dener -> 3 çağrı; sonra olumsuz önbelleğe alır.
        try {
            playDns.lookup("dead.example")
            fail("Expected UnknownHostException")
        } catch (_: UnknownHostException) {
        }
        assertEquals(3, calls)
        assertEquals(1, playDns.negativeCacheSize())
        assertEquals(0, playDns.positiveCacheSize())

        // 10 dk dolmadan ikinci sorgu: hiçbir çözücü çağrılmadan hemen UnknownHostException döner.
        calls = 0
        nowMs += 60_000L
        try {
            playDns.lookup("dead.example")
            fail("Expected cached UnknownHostException")
        } catch (_: UnknownHostException) {
        }
        assertEquals(0, calls)

        // 10 dk TTL dolunca yeniden sorgular.
        nowMs += PlayDns.NEGATIVE_CACHE_TTL_MS
        try {
            playDns.lookup("dead.example")
            fail("Expected UnknownHostException after TTL")
        } catch (_: UnknownHostException) {
        }
        assertEquals(3, calls)

        // Mod değişince iki önbellek de sıfırlanır.
        playDns.updateConfig(DnsMode.Cloudflare)
        assertEquals(0, playDns.negativeCacheSize())
        assertEquals(0, playDns.positiveCacheSize())
    }

    @Test
    fun `DoH olumlu sonucu 5 dk onbelleklenir sistem sonucu alinmaz ve yerel IP onbellekten de engellenir`() {
        var nowMs = 5_000_000L
        var sysCalls = 0
        var dohCalls = 0
        val playDns = PlayDns(
            systemDns = Dns { sysCalls++; listOf(v4a) },
            customOverrideResolver = {
                Dns { host ->
                    dohCalls++
                    if (host == "rebind.example") listOf(loopbackV4) else listOf(v6a, v4b)
                }
            },
            clock = { nowMs },
        )

        // Auto modda sistem DNS başarılıysa olumlu önbelleğe yazılmaz.
        assertEquals(listOf(v4a), playDns.lookup("sys.example"))
        assertEquals(listOf(v4a), playDns.lookup("sys.example"))
        assertEquals(2, sysCalls)
        assertEquals(0, playDns.positiveCacheSize())

        // DoH modunda çözülen adres 5 dk olumlu önbelleğe girer ve IPv4-önce korunur.
        playDns.updateConfig(DnsMode.Cloudflare)
        assertEquals(listOf(v4b, v6a), playDns.lookup("doh.example"))
        assertEquals(1, dohCalls)
        assertEquals(1, playDns.positiveCacheSize())

        nowMs += 120_000L // 2 dk sonra -> önbellekten gelir
        assertEquals(listOf(v4b, v6a), playDns.lookup("doh.example"))
        assertEquals(1, dohCalls)

        // 5 dk dolunca önbellek düşer ve yeniden sorulur.
        nowMs += PlayDns.POSITIVE_CACHE_TTL_MS
        assertEquals(listOf(v4b, v6a), playDns.lookup("doh.example"))
        assertEquals(2, dohCalls)

        // DoH sonucu yerel adres döndürüp olumlu önbelleğe girse bile NetworkPolicy ikinci çağrıda da engeller.
        val policyDns = NetworkPolicy(upstreamDns = playDns).policyDns()
        try {
            policyDns.lookup("rebind.example")
            fail("Expected blocked")
        } catch (e: UnknownHostException) {
            assertEquals("blocked", e.message)
        }
        try {
            policyDns.lookup("rebind.example")
            fail("Expected cached local IP to remain blocked")
        } catch (e: UnknownHostException) {
            assertEquals("blocked", e.message)
        }

        // Mod değişince olumlu önbellek temizlenir.
        playDns.updateConfig(DnsMode.Google)
        assertEquals(0, playDns.positiveCacheSize())
    }

    @Test
    fun `otomatik mod adim ve toplam zaman asimi sinirlarini uygular ve 512 kayitla sinirlidir`() {
        assertEquals(2_500L, PlayDns.AUTO_FALLBACK_STEP_TIMEOUT_MS)
        assertEquals(5_000L, PlayDns.AUTO_TOTAL_TIMEOUT_MS)
        assertEquals(512, PlayDns.MAX_CACHE_ENTRIES)

        // Yavaş CF adımı stepTimeoutMs'te kesilir, Google hızlıca yanıt verirse döner.
        var googleCalled = false
        val started = System.nanoTime()
        val ok = PlayDns.resolveAuto(
            hostname = "slow-cf.example",
            systemResolve = { throw UnknownHostException("sys dead") },
            cloudflareResolve = { Thread.sleep(1_500); listOf(v4a) },
            googleResolve = { googleCalled = true; listOf(v4b) },
            stepTimeoutMs = 100L,
            totalTimeoutMs = 400L,
        )
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertTrue(googleCalled)
        assertEquals(listOf(v4b), ok)
        assertTrue("Toplam süre toplam sınırı aşmamalı: $elapsedMs", elapsedMs < 400L)

        // Her iki DoH adımı da takılırsa toplam sınırda düşer.
        val bothStart = System.nanoTime()
        try {
            PlayDns.resolveAuto(
                hostname = "all-hang.example",
                systemResolve = { throw UnknownHostException("sys dead") },
                cloudflareResolve = { Thread.sleep(1_000); listOf(v4a) },
                googleResolve = { Thread.sleep(1_000); listOf(v4b) },
                stepTimeoutMs = 80L,
                totalTimeoutMs = 150L,
            )
            fail("Expected timeout failure")
        } catch (_: UnknownHostException) {
        }
        val bothMs = (System.nanoTime() - bothStart) / 1_000_000L
        assertTrue("Takılan fallback toplam süre sınırı içinde dönmeli: $bothMs", bothMs < 400L)

        // 512 kayıt sınırı: 600 farklı ölü ve canlı giriş 512'yi aşmaz.
        val boundedDns = PlayDns(
            systemDns = Dns { throw UnknownHostException("dead") },
            customOverrideResolver = { Dns { host -> if (host.startsWith("dead")) throw UnknownHostException("x") else listOf(v4a) } },
        )
        boundedDns.updateConfig(DnsMode.Cloudflare)
        repeat(600) { i ->
            boundedDns.lookup("live-$i.example")
            try {
                boundedDns.lookup("dead-$i.example")
            } catch (_: UnknownHostException) {
            }
        }
        assertEquals(512, boundedDns.positiveCacheSize())
        assertEquals(512, boundedDns.negativeCacheSize())
    }
}
