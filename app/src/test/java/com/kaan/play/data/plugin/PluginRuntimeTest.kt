package com.kaan.play.data.plugin

import com.kaan.play.data.plugin.runtime.PluginLimits
import com.kaan.play.data.plugin.runtime.PluginOutcome
import com.kaan.play.data.plugin.runtime.PluginRequest
import com.kaan.play.data.plugin.runtime.PluginRuntime
import com.kaan.play.data.plugin.runtime.toStreamResult
import com.kaan.play.data.stream.FailureReason
import com.kaan.play.data.stream.StreamResult
import com.kaan.play.data.stream.StreamSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * QuickJS çalıştırıcısı: kendi yazdığımız deneme eklentileriyle (gerçek bir depoya bağlanmadan). Yerel sahte sunucu için
 * [NetworkPolicy] `allowPrivate = true`; SSRF engelinin kendisi ayrı testlerde varsayılan kuralla denenir.
 */
class PluginRuntimeTest {

    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<RecordedRequest>()
    private var handler: (RecordedRequest) -> MockResponse = { MockResponse.Builder().code(404).build() }
    private val logs = CopyOnWriteArrayList<String>()

    private val realKey = "REAL_TMDB_KEY_XYZ_123"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return handler(request)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun base() = "http://127.0.0.1:${server.port}"

    private fun runtime(
        policy: NetworkPolicy = NetworkPolicy(allowPrivate = true),
        limits: PluginLimits = PluginLimits(),
    ) = PluginRuntime(
        client = OkHttpClient(),
        tmdbApiKey = realKey,
        policy = policy,
        limits = limits,
        tmdbHost = "127.0.0.1",
        tmdbHttpsOnly = false,
        log = { level, message -> logs += "[$level] $message" },
    )

    private val movie = PluginRequest("27205", "movie")

    private fun run(code: String, request: PluginRequest = movie, timeoutMs: Long = 10_000, rt: PluginRuntime = runtime()): PluginOutcome =
        runBlocking { rt.run(code, request, "test", timeoutMs) }

    /** Eklenti tek bir akış döndürür; başlığı `value` ifadesinin JSON'udur. Test başlığı geri okur. */
    private fun probe(expr: String, setup: String = "", rt: PluginRuntime = runtime()): String {
        val code = """
            module.exports = { getStreams: async function (tmdbId, mediaType, season, episode) {
              $setup
              var value = $expr;
              return [{ url: 'https://t.example/v', title: JSON.stringify(value) }];
            } };
        """.trimIndent()
        val outcome = run(code, rt = rt)
        check(outcome is PluginOutcome.Success) { "beklenmeyen: $outcome" }
        return outcome.streams.single().title!!
    }

    private fun json(body: String, code: Int = 200, vararg headers: Pair<String, String>) =
        MockResponse.Builder().code(code).body(body).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()

    // ---- temel sözleşme ----

    @Test
    fun getStreamsReceivesDocumentedArgumentsAndReturnsStreams() {
        val code = """
            module.exports = { getStreams: async function (tmdbId, mediaType, season, episode) {
              return [{ name: 'Kaynak', title: tmdbId + '|' + mediaType + '|' + season + '|' + episode,
                        url: 'https://cdn.example/a.mp4', quality: '1080p', size: '2.1 GB', language: 'English',
                        headers: { Referer: 'https://x.example/' } }];
            } };
        """.trimIndent()

        val movieOut = run(code, PluginRequest("27205", "movie")) as PluginOutcome.Success
        val s = movieOut.streams.single()
        assertEquals("27205|movie|undefined|undefined", s.title) // film: sezon/bölüm yok
        assertEquals("https://cdn.example/a.mp4", s.url)
        assertEquals("1080p", s.quality)
        assertEquals(mapOf("Referer" to "https://x.example/"), s.headers)

        val tv = run(code, PluginRequest("1399", "tv", 2, 5)) as PluginOutcome.Success
        assertEquals("1399|tv|2|5", tv.streams.single().title)
    }

    @Test
    fun globalGetStreamsWithoutModuleExportsAlsoWorks() {
        val out = run("async function getStreams(id) { return [{ url: 'https://a.example/x', title: 'id' + id }]; }") as PluginOutcome.Success
        assertEquals("id27205", out.streams.single().title)
    }

    @Test
    fun throwingAndMissingEntryPointsGiveEmptyListWithFailure() = runBlocking {
        val thrown = run("module.exports = { getStreams: async () => { throw new Error('boom'); } };")
        assertTrue(thrown is PluginOutcome.Failure && thrown.reason == PluginOutcome.Reason.Script && thrown.message == "boom")
        assertTrue(thrown.streams.isEmpty())

        val missing = run("var x = 1;")
        assertTrue(missing is PluginOutcome.Failure && missing.reason == PluginOutcome.Reason.Script)

        val syntax = run("module.exports = { getStreams: async () => { ")
        assertTrue(syntax is PluginOutcome.Failure && syntax.reason == PluginOutcome.Reason.Script)

        // getStreams (kolaylık) her durumda boş liste verir.
        assertTrue(runtime().getStreams("module.exports = { getStreams: () => { throw 1; } };", movie).isEmpty())
        // Dizi olmayan dönüş ve boş dizi: başarı, boş liste.
        assertEquals(emptyList<Any>(), (run("module.exports = { getStreams: async () => 'x' };") as PluginOutcome.Success).streams)
    }

    @Test
    fun infiniteLoopIsInterruptedByTheTimeout() {
        val started = System.nanoTime()
        val out = run("module.exports = { getStreams: async () => { while (true) {} } };", timeoutMs = 700)
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue("$out", out is PluginOutcome.Failure && out.reason == PluginOutcome.Reason.Timeout)
        assertTrue("çok geç: $tookMs ms", tookMs < 5_000)

        // Çok uzun bekleyen zamanlayıcı da süreyle biter; hiçbir şeyin çözemeyeceği söz ise hemen "sonuç yok" hatasıdır.
        val slow = run("module.exports = { getStreams: () => new Promise(function (res) { setTimeout(res, 60000); }) };", timeoutMs = 500)
        assertTrue("$slow", slow is PluginOutcome.Failure && slow.reason == PluginOutcome.Reason.Timeout)
        val never = run("module.exports = { getStreams: () => new Promise(function () {}) };", timeoutMs = 5_000)
        assertTrue("$never", never is PluginOutcome.Failure && never.reason == PluginOutcome.Reason.Script)
    }

    @Test
    fun memoryHogIsStoppedWithoutCrashing() {
        val out = run(
            "module.exports = { getStreams: async () => { var a = []; while (true) { a.push(new Array(200000).fill(1)); } } };",
            rt = runtime(limits = PluginLimits(memoryLimitBytes = 16L * 1024 * 1024)),
            timeoutMs = 8_000,
        )
        assertTrue("$out", out is PluginOutcome.Failure)
    }

    @Test
    fun requireIsLimitedToCheerioAndCryptoJs() {
        val title = probe(
            "r",
            setup = """
              var r = {};
              ['fs', 'path', 'child_process', 'crypto', 'http', 'node:fs'].forEach(function (m) {
                try { require(m); r[m] = 'LOADED'; } catch (e) { r[m] = e.message; }
              });
              r.cheerio = typeof require('cheerio').load; r.cryptoJs = typeof require('crypto-js').MD5;
            """.trimIndent(),
        )
        assertTrue(title, title.contains("\"fs\":\"Module 'fs' is not available\""))
        assertTrue(title, title.contains("\"child_process\":\"Module 'child_process' is not available\""))
        assertTrue(title, title.contains("\"crypto\":\"Module 'crypto' is not available\""))
        assertFalse(title, title.contains("LOADED"))
        assertTrue(title, title.contains("\"cheerio\":\"function\"") && title.contains("\"cryptoJs\":\"function\""))
    }

    @Test
    fun noHostAccessGlobalsExist() {
        val title = probe("t", setup = "var t = {}; ['process','Buffer','XMLHttpRequest','localStorage','WebAssembly','Android','java','Packages'].forEach(function (n) { t[n] = typeof globalThis[n]; });")
        assertTrue(title, title.contains("\"process\":\"undefined\"") && title.contains("\"Buffer\":\"undefined\""))
        assertFalse(title, title.contains("object") || title.contains("function"))
    }

    // ---- fetch ----

    @Test
    fun fetchTalksToLocalServerWithHeadersPostAndJson() {
        handler = { req ->
            when (req.url.encodedPath) {
                "/api" -> json("""{"items":[{"u":"https://cdn.example/1.m3u8"},{"u":"https://cdn.example/2.m3u8"}]}""", 200, "X-Test" to "yes")
                "/post" -> json("""{"got":"${req.body?.utf8()}"}""")
                "/bad" -> json("not json", 500)
                else -> MockResponse.Builder().code(404).build()
            }
        }
        val title = probe(
            "r",
            setup = """
              var r = {};
              var a = await fetch('${base()}/api', { headers: { 'X-Custom': 'abc', 'Accept-Encoding': 'br' } });
              r.ok = a.ok; r.status = a.status; r.hdr = a.headers.get('X-Test'); r.json = await a.json();
              var p = await fetch('${base()}/post', { method: 'POST', body: new URLSearchParams({ q: 'a b', n: '1' }) });
              r.post = (await p.json()).got;
              var b = await fetch('${base()}/bad');
              r.badOk = b.ok; r.badStatus = b.status; r.badJson = await b.json(); r.badText = await b.text();
              var n = await fetch('${base()}/nope'); r.nope = n.status;
            """.trimIndent(),
        )
        assertTrue(title, title.contains("\"ok\":true") && title.contains("\"status\":200") && title.contains("\"hdr\":\"yes\""))
        assertTrue(title, title.contains("https://cdn.example/2.m3u8"))
        assertTrue(title, title.contains("\"post\":\"q=a+b&n=1\""))
        assertTrue(title, title.contains("\"badOk\":false") && title.contains("\"badStatus\":500") && title.contains("\"badJson\":null") && title.contains("\"badText\":\"not json\""))
        assertTrue(title, title.contains("\"nope\":404"))

        val api = seen.first { it.url.encodedPath == "/api" }
        assertEquals("abc", api.headers["X-Custom"])
        assertTrue(api.headers["User-Agent"]!!.startsWith("Mozilla/5.0")) // varsayılan UA
        assertNotEquals("br", api.headers["Accept-Encoding"]) // OkHttp kendi başlığını yönetir
        val post = seen.first { it.url.encodedPath == "/post" }
        assertEquals("application/x-www-form-urlencoded;charset=UTF-8", post.headers["Content-Type"])
    }

    @Test
    fun redirectsFollowedOrLeftManual() {
        handler = { req ->
            when (req.url.encodedPath) {
                "/r1" -> MockResponse.Builder().code(302).addHeader("Location", "/r2").build()
                "/r2" -> json("end")
                else -> MockResponse.Builder().code(404).build()
            }
        }
        val title = probe(
            "r",
            setup = """
              var r = {};
              var f = await fetch('${base()}/r1'); r.follow = f.status + ':' + (await f.text());
              var m = await fetch('${base()}/r1', { redirect: 'manual' }); r.manual = m.status + ':' + m.headers.get('location');
            """.trimIndent(),
        )
        assertTrue(title, title.contains("\"follow\":\"200:end\"") && title.contains("\"manual\":\"302:/r2\""))
    }

    @Test
    fun responseBodyIsCappedAndRequestBudgetEnforced() {
        handler = { json("x".repeat(5000)) }
        val rt = runtime(limits = PluginLimits(maxResponseBytes = 1000, maxRequests = 2))
        val title = probe(
            "r",
            setup = """
              var r = {};
              var a = await fetch('${base()}/a'); r.len = (await a.text()).length;
              await fetch('${base()}/b');
              var c = await fetch('${base()}/c'); r.third = c.status + ':' + c.ok;
            """.trimIndent(),
            rt = rt,
        )
        assertTrue(title, title.contains("\"len\":1000"))
        assertTrue(title, title.contains("\"third\":\"0:false\""))
        assertEquals(2, seen.size)
    }

    @Test
    fun cookiesStayInsideOneRunAndAreNeverShared() {
        handler = { req ->
            if (req.url.encodedPath == "/set") json("ok", 200, "Set-Cookie" to "sid=abc123; Path=/") else json("c=" + (req.headers["Cookie"] ?: "none"))
        }
        val code = """
            module.exports = { getStreams: async function () {
              await fetch('${base()}/set');
              var r = await fetch('${base()}/read');
              return [{ url: 'https://t.example/v', title: await r.text() }];
            } };
        """.trimIndent()
        val first = (run(code) as PluginOutcome.Success).streams.single().title
        assertEquals("c=sid=abc123", first) // aynı çalıştırmada çerez gider
        seen.clear()
        handler = { req -> json("c=" + (req.headers["Cookie"] ?: "none")) }
        val second = (run("module.exports = { getStreams: async () => { var r = await fetch('${base()}/read'); return [{ url: 'https://t.example/v', title: await r.text() }]; } };") as PluginOutcome.Success)
        assertEquals("c=none", second.streams.single().title) // başka çalıştırmaya sızmaz
    }

    @Test
    fun fetchesRunInParallelAndTimersWork() {
        handler = { MockResponse.Builder().code(200).body("ok").bodyDelay(400, TimeUnit.MILLISECONDS).build() }
        val started = System.nanoTime()
        val title = probe(
            "r",
            setup = """
              var r = {};
              var all = await Promise.all([fetch('${base()}/1'), fetch('${base()}/2'), fetch('${base()}/3')]);
              r.n = all.length;
              r.timer = await new Promise(function (res) { setTimeout(function () { res('fired'); }, 50); });
              var cleared = false; var id = setTimeout(function () { cleared = true; }, 20); clearTimeout(id);
              await new Promise(function (res) { setTimeout(res, 60); }); r.cleared = cleared;
            """.trimIndent(),
        )
        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
        assertTrue(title, title.contains("\"n\":3") && title.contains("\"timer\":\"fired\"") && title.contains("\"cleared\":false"))
        assertTrue("paralel olmalı: $tookMs ms", tookMs < 1_100) // 3 x 400 ms art arda olsaydı > 1200 ms
    }

    // ---- SSRF ----

    @Test
    fun localAndPrivateAddressesAreRejectedWithoutAnyRequest() {
        val strict = runtime(policy = NetworkPolicy())
        val targets = listOf(
            base() + "/x", // loopback
            "http://localhost:${server.port}/x",
            "http://169.254.169.254/latest/meta-data/",
            "http://10.0.0.1/", "http://192.168.1.1/", "http://172.16.0.1/", "http://100.64.0.1/",
            "http://[::1]/", "http://[fe80::1]/", "http://[fd00::1]/", "http://0.0.0.0/",
            "ftp://example.com/x", "file:///etc/passwd", "javascript:alert(1)",
        )
        val title = probe(
            "r",
            setup = """
              var r = [];
              var targets = ${targets.joinToString(prefix = "[", postfix = "]") { "'$it'" }};
              for (var i = 0; i < targets.length; i++) { var x = await fetch(targets[i]); r.push(x.status + ':' + x.ok + ':' + x.statusText); }
            """.trimIndent(),
            rt = strict,
        )
        // Hiçbiri sunucuya ulaşmadı ve hiçbiri başarılı olmadı.
        assertEquals(0, seen.size)
        assertFalse(title, title.contains(":true:"))
        assertTrue(title, title.contains("0:false:blocked")) // adres kuralı
    }

    @Test
    fun redirectToLocalAddressIsBlockedAtEveryHop() {
        handler = { MockResponse.Builder().code(302).addHeader("Location", "http://localhost:${server.port}/secret").build() }
        // İlk adım (127.0.0.1) yalnızca bu testte izinli ana makine; yönlendirme hedefi "localhost" reddedilmeli.
        val rt = runtime(policy = NetworkPolicy(allowPrivateHosts = setOf("127.0.0.1")))
        val title = probe("r", "var x = await fetch('${base()}/start'); var r = x.status + ':' + x.statusText;", rt = rt)
        assertEquals("\"0:blocked\"", title)
        assertEquals(1, seen.size)
    }

    @Test
    fun networkPolicyClassifiesAddresses() {
        val p = NetworkPolicy()
        fun blocked(ip: String) = p.isBlocked(java.net.InetAddress.getByName(ip))
        listOf("127.0.0.1", "127.1.2.3", "10.1.2.3", "172.16.5.5", "172.31.255.255", "192.168.0.10", "169.254.169.254", "100.64.0.1",
            "0.0.0.0", "224.0.0.1", "240.0.0.1", "::1", "::", "fe80::1", "fc00::1", "fd12:3456::1", "::ffff:127.0.0.1", "::ffff:10.0.0.1")
            .forEach { assertTrue("$it engellenmeli", blocked(it)) }
        listOf("8.8.8.8", "1.1.1.1", "172.15.0.1", "172.32.0.1", "100.63.255.255", "100.128.0.1", "93.184.216.34", "2606:4700:4700::1111")
            .forEach { assertFalse("$it izinli olmalı", blocked(it)) }
    }

    // ---- TMDB anahtarı ----

    @Test
    fun pluginsNeverSeeTheRealTmdbKeyYetTmdbRequestsGetIt() {
        handler = { json("""{"title":"Film"}""") } // gerçek TMDB de anahtarı yanıta yazmaz
        val title = probe(
            "r",
            setup = """
              var r = { global: TMDB_API_KEY };
              var t = await fetch('${base()}/3/movie/1?api_key=' + TMDB_API_KEY + '&language=tr-TR');
              r.status = t.status; r.body = await t.text();
              var other = await fetch('http://localhost:${server.port}/3/movie/1?api_key=' + TMDB_API_KEY);
              r.otherBody = await other.text();
            """.trimIndent(),
        )
        assertFalse("eklentiye gerçek anahtar sızdı: $title", title.contains(realKey))
        assertFalse(title, title.contains("\"global\":\"$realKey\""))

        val tmdbReq = seen.first { it.url.host == "127.0.0.1" }
        assertEquals(realKey, tmdbReq.url.queryParameter("api_key")) // TMDB ana makinesinde yerine konur
        assertEquals("tr-TR", tmdbReq.url.queryParameter("language")) // diğer parametreler korunur
        val otherReq = seen.first { it.url.host == "localhost" }
        assertEquals("PLAY_TMDB_KEY_PLACEHOLDER", otherReq.url.queryParameter("api_key")) // başka ana makinede yer tutucu aynen
    }

    @Test
    fun tmdbRequestsDoNotFollowRedirectsSoTheKeyCannotLeak() {
        handler = { MockResponse.Builder().code(302).addHeader("Location", "http://localhost:${server.port}/x").build() }
        val title = probe("r", "var x = await fetch('${base()}/3/movie/1?api_key=' + TMDB_API_KEY); var r = x.status;")
        assertEquals("302", title)
        assertEquals(1, seen.size) // yönlendirilen adres hiç istenmedi
        assertEquals(realKey, seen.single().url.queryParameter("api_key"))
    }

    @Test
    fun consoleOutputMasksKeysAndSecrets() {
        run(
            """
            module.exports = { getStreams: async function () {
              console.log('GET https://api.themoviedb.org/3/movie/1?api_key=' + TMDB_API_KEY + '&x=1');
              console.warn('token=abcdef123456 and apikey=SECRETVALUE');
              console.error('raw $realKey value'); console.info({ a: 1 });
              return [];
            } };
            """.trimIndent(),
        )
        val all = logs.joinToString("\n")
        assertFalse(all, all.contains(realKey) || all.contains("PLAY_TMDB_KEY_PLACEHOLDER") || all.contains("SECRETVALUE") || all.contains("abcdef123456"))
        assertTrue(all, all.contains("api_key=***") && all.contains("[warn]") && all.contains("[error]") && all.contains("{\"a\":1}"))
    }

    // ---- cheerio benzeri kabuk ----

    @Test
    fun cheerioParsesHtmlWithTheDocumentedSubset() {
        val html = """
            <html><body><h1 class="t main">Başlık</h1>
              <ul id="list"><li class="a"><a href="/one" data-q="1080">Bir</a></li><li class="b"><a href="/two">İki</a> <span>x</span></li><li><a href="/three">Üç</a></li></ul>
              <input id="i" value="deger"><div id="d"><p>para <b>kalın</b></p></div>
            </body></html>
        """.trimIndent()
        handler = { json(html) }
        val title = probe(
            "r",
            setup = """
              var $ = cheerio.load(await (await fetch('${base()}/p')).text());
              var r = {};
              r.h1 = $('h1').text(); r.hasClass = $('h1').hasClass('main'); r.n = $('#list li').length;
              r.hrefs = []; $('#list li a').each(function (i, el) { r.hrefs.push($(el).attr('href') + '=' + $(el).text()); });
              r.map = $('#list li a').map(function (i, el) { return $(el).attr('href'); }).get().join(',');
              r.first = $('#list li').first().find('a').attr('data-q'); r.last = $('#list li').last().text().trim();
              r.eq = $('#list li').eq(1).attr('class'); r.missing = String($('#nope').attr('href')); r.len0 = $('#nope').length;
              r.next = $('li.a').next().attr('class'); r.prev = $('li.b').prev().attr('class'); r.parent = $('li.a').parent().attr('id');
              r.closest = $('li.a a').closest('ul').attr('id'); r.filter = $('#list li').filter('.b').length;
              r.fnFilter = $('#list li').filter(function (i, el) { return $(el).find('span').length > 0; }).length;
              r.has = $('#list li').has('span').length; r.is = $('li.a').is('.a'); r.val = $('#i').val();
              r.contains = $('a:contains("İki")').attr('href'); r.html = $('#d').html(); r.outer = $.html($('#d p'));
              r.scoped = $('a', $('li.b')).length; r.toArray = $('li').toArray().length; r.get1 = $($('li').get(1)).attr('class');
              r.children = $('#list').children().length; r.siblings = $('li.a').siblings().length; r.not = $('li').not('.a').length;
            """.trimIndent(),
        )
        val map = com.kaan.play.data.net.PlayJson.parseToJsonElement(title) as kotlinx.serialization.json.JsonObject
        fun v(k: String) = (map[k] as kotlinx.serialization.json.JsonPrimitive).content
        assertEquals("Başlık", v("h1"))
        assertEquals("true", v("hasClass"))
        assertEquals("3", v("n"))
        assertEquals("/one=Bir", ((map["hrefs"] as kotlinx.serialization.json.JsonArray)[0] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("/one,/two,/three", v("map"))
        assertEquals("1080", v("first"))
        assertEquals("Üç", v("last"))
        assertEquals("b", v("eq"))
        assertEquals("undefined", v("missing"))
        assertEquals("0", v("len0"))
        assertEquals("b", v("next"))
        assertEquals("a", v("prev"))
        assertEquals("list", v("parent"))
        assertEquals("list", v("closest"))
        assertEquals("1", v("filter"))
        assertEquals("1", v("fnFilter"))
        assertEquals("1", v("has"))
        assertEquals("true", v("is"))
        assertEquals("deger", v("val"))
        assertEquals("/two", v("contains"))
        assertEquals("<p>para <b>kalın</b></p>", v("html"))
        assertEquals("<p>para <b>kalın</b></p>", v("outer"))
        assertEquals("1", v("scoped"))
        assertEquals("3", v("toArray"))
        assertEquals("b", v("get1"))
        assertEquals("3", v("children"))
        assertEquals("2", v("siblings"))
        assertEquals("2", v("not"))
    }

    // ---- CryptoJS benzeri ----

    @Test
    fun cryptoJsDigestsHmacAndEncodings() {
        val title = probe(
            "r",
            setup = """
              var C = CryptoJS, r = {};
              r.md5 = C.MD5('abc').toString(); r.sha1 = C.SHA1('abc').toString(); r.sha256 = C.SHA256('abc').toString();
              r.sha512 = C.SHA512('abc').toString().slice(0, 16);
              r.hmac = C.HmacSHA256('The quick brown fox jumps over the lazy dog', 'key').toString();
              r.hmacMd5 = C.HmacMD5('what do ya want for nothing?', 'Jefe').toString();
              r.b64 = C.enc.Base64.stringify(C.enc.Utf8.parse('Merhaba Dünya €')); r.b64back = C.enc.Utf8.stringify(C.enc.Base64.parse(r.b64));
              r.hex = C.enc.Hex.stringify(C.enc.Utf8.parse('hi')); r.hexback = C.enc.Hex.parse('6869').toString(C.enc.Utf8);
              r.wa = C.lib.WordArray.create([0x41424344], 4).toString(C.enc.Utf8); r.md5b64 = C.MD5('abc').toString(C.enc.Base64);
              r.atob = atob(btoa('héllo')); r.te = new TextDecoder().decode(new TextEncoder().encode('Ünï €')) ;
            """.trimIndent(),
        )
        assertTrue(title, title.contains("\"md5\":\"900150983cd24fb0d6963f7d28e17f72\""))
        assertTrue(title, title.contains("\"sha1\":\"a9993e364706816aba3e25717850c26c9cd0d89d\""))
        assertTrue(title, title.contains("\"sha256\":\"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad\""))
        assertTrue(title, title.contains("\"sha512\":\"ddaf35a193617aba\""))
        assertTrue(title, title.contains("\"hmac\":\"f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8\""))
        assertTrue(title, title.contains("\"hmacMd5\":\"750c783e6ab0b503eaa86e310a5db738\""))
        assertTrue(title, title.contains("\"b64back\":\"Merhaba Dünya €\"") && title.contains("\"hexback\":\"hi\"") && title.contains("\"hex\":\"6869\""))
        assertTrue(title, title.contains("\"wa\":\"ABCD\"") && title.contains("\"md5b64\":\"kAFQmDzST7DWlj99KOF/cg==\""))
        assertTrue(title, title.contains("\"atob\":\"héllo\"") && title.contains("\"te\":\"Ünï €\""))
    }

    @Test
    fun cryptoJsAesDecryptsWhatJavaEncrypted() {
        val key = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(16) { (0x40 + it).toByte() }
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val b64 = java.util.Base64.getEncoder().encodeToString(c.doFinal("{\"url\":\"https://cdn.example/x.m3u8\"}".toByteArray()))
        fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

        val title = probe(
            "r",
            setup = """
              var C = CryptoJS, r = {};
              var k = C.enc.Hex.parse('${hex(key)}'), iv = C.enc.Hex.parse('${hex(iv)}');
              var dec = C.AES.decrypt(C.lib.CipherParams.create({ ciphertext: C.enc.Base64.parse('$b64') }), k, { iv: iv, mode: C.mode.CBC, padding: C.pad.Pkcs7 });
              r.plain = dec.toString(C.enc.Utf8);
              // Çift yön: bizim şifrelediğimizi biz çözeriz; ve Java'nın çözebildiği çıktı üretiriz.
              var enc = C.AES.encrypt('merhaba dünya', k, { iv: iv, mode: C.mode.CBC, padding: C.pad.Pkcs7 });
              r.encB64 = enc.toString();
              r.round = C.AES.decrypt(enc, k, { iv: iv }).toString(C.enc.Utf8);
              // Parola kipi (OpenSSL "Salted__" biçimi): rastgele tuzla şifrele, aynı parolayla çöz.
              var p = C.AES.encrypt('gizli metin', 'parola').toString();
              r.salted = p.length > 20; r.pass = C.AES.decrypt(p, 'parola').toString(C.enc.Utf8);
              var ecb = C.AES.encrypt('0123456789abcdef', k, { mode: C.mode.ECB, padding: C.pad.NoPadding });
              r.ecbLen = ecb.ciphertext.sigBytes;
              var t = C.TripleDES.encrypt('3des metin', C.enc.Hex.parse('0123456789abcdeffedcba9876543210'), { iv: C.enc.Hex.parse('0001020304050607') });
              r.des = C.TripleDES.decrypt(t, C.enc.Hex.parse('0123456789abcdeffedcba9876543210'), { iv: C.enc.Hex.parse('0001020304050607') }).toString(C.enc.Utf8);
              try { C.AES.decrypt(C.lib.CipherParams.create({ ciphertext: C.enc.Hex.parse('00') }), k, { iv: iv }); r.bad = 'no error'; } catch (e) { r.bad = 'threw'; }
            """.trimIndent(),
        )
        assertTrue(title, title.contains("\"plain\":\"{\\\"url\\\":\\\"https://cdn.example/x.m3u8\\\"}\""))
        assertTrue(title, title.contains("\"round\":\"merhaba dünya\"") && title.contains("\"pass\":\"gizli metin\"") && title.contains("\"salted\":true"))
        assertTrue(title, title.contains("\"ecbLen\":16") && title.contains("\"des\":\"3des metin\"") && title.contains("\"bad\":\"threw\""))
        // JS'in ürettiği şifreli metni Java da çözebilmeli (uyumluluk).
        val encB64 = Regex("\"encB64\":\"([^\"]+)\"").find(title)!!.groupValues[1]
        val d = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        assertEquals("merhaba dünya", String(d.doFinal(java.util.Base64.getDecoder().decode(encB64))))
    }

    // ---- URL ve küçük API'ler ----

    @Test
    fun urlUrlSearchParamsAndAbortController() {
        val title = probe(
            "r",
            setup = """
              var r = {};
              var u = new URL('/yol/dosya.m3u8?a=1&b=iki#h', 'https://ornek.com:8443/kok/');
              r.href = u.href; r.host = u.host; r.path = u.pathname; r.search = u.search; r.origin = u.origin; r.a = u.searchParams.get('a');
              r.rel = new URL('x.ts', 'https://ornek.com/a/b/c.m3u8').href; r.bad = 'ok'; try { new URL('not a url'); } catch (e) { r.bad = e.name; }
              var sp = new URLSearchParams({ q: 'a b', z: '&' }); sp.append('q', '2'); r.sp = sp.toString(); r.all = sp.getAll('q').join('|');
              var c = new AbortController(); var fired = false; c.signal.addEventListener('abort', function () { fired = true; }); c.abort(); r.abort = c.signal.aborted + ':' + fired;
              try { await fetch('https://ornek.com/', { signal: c.signal }); r.f = 'no'; } catch (e) { r.f = e.name; }
            """.trimIndent(),
        )
        assertTrue(title, title.contains("\"href\":\"https://ornek.com:8443/yol/dosya.m3u8?a=1&b=iki#h\""))
        assertTrue(title, title.contains("\"host\":\"ornek.com:8443\"") && title.contains("\"path\":\"/yol/dosya.m3u8\"") && title.contains("\"origin\":\"https://ornek.com:8443\""))
        assertTrue(title, title.contains("\"rel\":\"https://ornek.com/a/b/x.ts\"") && title.contains("\"bad\":\"TypeError\"") && title.contains("\"a\":\"1\""))
        assertTrue(title, title.contains("\"sp\":\"q=a+b&z=%26&q=2\"") && title.contains("\"all\":\"a b|2\""))
        assertTrue(title, title.contains("\"abort\":\"true:true\"") && title.contains("\"f\":\"AbortError\""))
    }

    // ---- sonuç ayrıştırma ve çeviri ----

    @Test
    fun resultsAreSanitizedAndMappedToStreamResults() {
        val code = """
            module.exports = { getStreams: async function () {
              return [
                { name: '﻿​​Hızlı • EN', title: 'Film [TR] 2.5 GB', url: 'https://cdn.example/a.m3u8', quality: '1080P',
                  headers: { Referer: 'https://x.example', 'Bad': 'a\r\nInjected: 1', '': 'empty', Num: 5 },
                  subtitles: [{ url: 'https://s.example/t.srt', language: 'tur' }, { url: 'javascript:alert(1)' }, { url: 'https://s.example/e.srt', lang: 'eng', name: 'EN' }] },
                { name: 'çift', url: 'https://cdn.example/a.m3u8' },
                { name: 'ftp', url: 'ftp://cdn.example/b.mp4' },
                { name: 'nesne', url: { url: 'https://cdn.example/c.mp4' }, quality: 'Auto', size: '700 MB', language: 'Turkish' },
                { name: 'yok' }, 'metin', null, { name: 'x', url: 42 }
              ];
            } };
        """.trimIndent()
        val out = run(code) as PluginOutcome.Success
        assertEquals(listOf("https://cdn.example/a.m3u8", "https://cdn.example/c.mp4"), out.streams.map { it.url })
        val a = out.streams[0]
        assertEquals("Hızlı • EN", a.name) // görünmez sıralama karakterleri temizlendi
        assertEquals(mapOf("Referer" to "https://x.example", "Num" to "5"), a.headers) // CRLF'li ve boş adlı başlık atıldı
        assertEquals(listOf("https://s.example/t.srt", "https://s.example/e.srt"), a.subtitles.map { it.url })
        assertEquals("eng", a.subtitles[1].language)

        val source = StreamSource(7, "Eklenti")
        val found = out.toStreamResult(source) as StreamResult.Found
        assertEquals(2, found.streams.size)
        assertEquals(1080, found.streams[0].resolution)
        assertEquals(2_500_000_000L, found.streams[0].sizeBytes) // başlık metninden
        assertEquals("TR", found.streams[0].language)
        assertEquals(1, found.streams[0].subtitles.size.coerceAtMost(1))
        assertNull(found.streams[1].resolution) // "Auto": bilinmiyor
        assertEquals(700_000_000L, found.streams[1].sizeBytes)
        assertEquals("TR", found.streams[1].language) // "Turkish"
        assertEquals(source, found.streams[0].source)
    }

    @Test
    fun failuresMapToTheFailedResultAndEmptySuccessToEmptyFound() {
        val source = StreamSource(1, "A")
        val timeout = PluginOutcome.Failure(PluginOutcome.Reason.Timeout).toStreamResult(source) as StreamResult.Failed
        assertEquals(FailureReason.Timeout, timeout.reason)
        val script = PluginOutcome.Failure(PluginOutcome.Reason.Script, "x").toStreamResult(source) as StreamResult.Failed
        assertEquals(FailureReason.InvalidResponse, script.reason)
        assertTrue((PluginOutcome.Success(emptyList()).toStreamResult(source) as StreamResult.Found).streams.isEmpty())
    }

    // ---- 2. tur: ayarlar, rastgele, tanı notları, temizlik ----

    @Test
    fun savedSettingsArriveAsScraperSettingsAndDefaultIsAnEmptyObject() {
        val code = "module.exports = { getStreams: async () => [{ url: 'https://t.example/v', title: JSON.stringify(SCRAPER_SETTINGS) }] };"
        val empty = run(code) as PluginOutcome.Success
        assertEquals("{}", empty.streams.single().title)

        val withSettings = runBlocking {
            runtime().run(
                code, movie, "test", 10_000,
                mapOf("enable4K" to JsonPrimitive(false), "max" to JsonPrimitive("2"), "token" to JsonPrimitive("a\"b")),
            )
        } as PluginOutcome.Success
        assertEquals("""{"enable4K":false,"max":"2","token":"a\"b"}""", withSettings.streams.single().title)
    }

    @Test
    fun onSettingsLayoutIsReadFromThePlugin() {
        val code = """
            const COUNT = [{ value: '1', label: '1' }, { value: 'unlimited', label: 'Unlimited' }];
            async function onSettings() {
              return [{ type: 'header', label: 'Resolution' }, { type: 'toggle', key: 'k', label: 'K', defaultValue: true },
                      { type: 'select', key: 'm', label: 'M', defaultValue: 'unlimited', options: COUNT },
                      { type: 'text', key: 't', isPassword: true, placeholder: '...' }];
            }
            module.exports = { getStreams: async () => [], onSettings };
        """.trimIndent()
        val fields = runBlocking { runtime().settingsLayout(code, "x") }!!
        assertEquals(4, fields.size)
        assertTrue(fields[3] is com.kaan.play.data.plugin.runtime.PluginSettingField.Text)

        // onSettings yok: boş liste; hata atıyor ya da süre aşıyor: null.
        assertEquals(emptyList<Any>(), runBlocking { runtime().settingsLayout("module.exports = { getStreams: async () => [] };") })
        assertNull(runBlocking { runtime().settingsLayout("module.exports = { onSettings: async () => { throw new Error('x'); } };") })
        assertNull(runBlocking { runtime().settingsLayout("module.exports = { onSettings: async () => { while (true) {} } };", "x", 500) })
        // global onSettings (module.exports'suz) da bulunur.
        assertEquals(0, runBlocking { runtime().settingsLayout("async function onSettings() { return []; }") }!!.size)
    }

    @Test
    fun randomValuesAndUuidWorkWhileSubtleIsAbsent() {
        val title = probe(
            "r",
            setup = """
              var r = {};
              var a = new Uint8Array(32); crypto.getRandomValues(a); var b = new Uint8Array(32); crypto.getRandomValues(b);
              var allZero = true, same = true; for (var i = 0; i < 32; i++) { if (a[i] !== 0) allZero = false; if (a[i] !== b[i]) same = false; }
              r.allZero = allZero; r.same = same;
              var big = new Uint32Array(100); r.same32 = crypto.getRandomValues(big) === big;
              r.uuid = crypto.randomUUID(); r.subtle = typeof crypto.subtle;
              try { crypto.getRandomValues(new Uint8Array(70000)); r.quota = 'no'; } catch (e) { r.quota = 'threw'; }
            """.trimIndent(),
        )
        assertTrue(title, title.contains("\"allZero\":false") && title.contains("\"same\":false") && title.contains("\"same32\":true"))
        assertTrue(title, Regex("\"uuid\":\"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\"").containsMatchIn(title))
        assertTrue(title, title.contains("\"subtle\":\"undefined\"") && title.contains("\"quota\":\"threw\""))
    }

    @Test
    fun diagnosticNotesNameHostsAndStatusesButNeverUrlsOrKeys() {
        handler = { MockResponse.Builder().code(403).body("no").build() }
        val out = run(
            """
            module.exports = { getStreams: async function () {
              await fetch('${base()}/secret/path?api_key=' + TMDB_API_KEY + '&x=1');
              console.error('hata: ' + TMDB_API_KEY + ' https://x.example/?api_key=ABCDEF');
              return [];
            } };
            """.trimIndent(),
        )
        assertTrue(out is PluginOutcome.Success && out.streams.isEmpty())
        val notes = out.notes.joinToString("|")
        assertTrue(notes, notes.contains("127.0.0.1 403"))
        assertTrue(notes, notes.contains("console: hata:"))
        listOf("secret", "path", "api_key=A", realKey, "PLAY_TMDB_KEY_PLACEHOLDER", "ABCDEF").forEach { assertFalse("$it sızdı: $notes", notes.contains(it)) }

        val blocked = run("module.exports = { getStreams: async () => { await fetch('http://10.1.2.3/x'); return []; } };", rt = runtime(policy = NetworkPolicy()))
        assertTrue(blocked.notes.joinToString(), blocked.notes.contains("10.1.2.3 blocked"))
        val timeout = run("module.exports = { getStreams: async () => { await fetch('${base()}/a'); while (true) {} } };", timeoutMs = 600)
        assertTrue(timeout is PluginOutcome.Failure && timeout.notes.isNotEmpty()) // zaman aşımında da o ana kadarki notlar var
    }

    @Test
    fun invisibleAndOddCharactersAreStrippedFromTextFieldsButUsefulOnesStay() {
        val code = """
            module.exports = { getStreams: async () => [
              { name: 'A​‌﻿⁠B\u0007C­DE�F‮G ğüşiöç İ 日本 😀', title: 'tab\there', url: 'https://t.example/v', quality: '1080p' }
            ] };
        """.trimIndent()
        val s = (run(code) as PluginOutcome.Success).streams.single()
        assertEquals("ABCDEFG ğüşiöç İ 日本 😀", s.name)
        assertEquals("tabhere", s.title) // sekme gibi denetim karakterleri de gider
    }

    @Test
    fun subtitleLanguagesAreNormalizedForThePlayer() {
        val code = """
            module.exports = { getStreams: async () => [{ url: 'https://t.example/v', subtitles: [
              { url: 'https://s/1.srt', language: 'tur' }, { url: 'https://s/2.srt', language: 'English' }, { url: 'https://s/3.srt', lang: 'pt-BR' },
              { url: 'https://s/4.srt', name: 'Turkish subs' }, { url: 'https://s/5.srt', language: 'Klingon' }, { url: 'https://s/6.srt' }
            ] }] };
        """.trimIndent()
        val playable = (run(code).toStreamResult(StreamSource(1, "A")) as StreamResult.Found).streams.single()
        assertEquals(listOf("tur", "en", "pt-BR", "tr", null, null), playable.subtitles.map { it.language })
    }
}
