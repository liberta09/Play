package com.kaan.play

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class CrashReporterTest {

    @Before
    fun setup() {
        CrashReporter.resetDedupState()
    }

    @Test
    fun testSanitizeTextReplacesUrlsAndMagnets() {
        val input = "Crash happened at https://sub.domain.com/path?key=val&other=123 " +
                "also checked http://insecure.test/sample. " +
                "Torrent magnet: magnet:?xt=urn:btih:3b80b3d6874dc5d050587640c31c7757eebeffbc&dn=Ubuntu and done."

        val sanitized = CrashReporter.sanitizeText(input)

        assertFalse("HTTP/HTTPS URLs must not be present in sanitized text", sanitized.contains("https://"))
        assertFalse("HTTP/HTTPS URLs must not be present in sanitized text", sanitized.contains("http://"))
        assertFalse("Magnet links must not be present in sanitized text", sanitized.contains("magnet:?"))
        assertTrue("URL placeholder must be present", sanitized.contains("<url>"))
        assertTrue("Magnet placeholder must be present", sanitized.contains("<magnet>"))
    }

    @Test
    fun testSanitizeTextTruncatesTo3500Characters() {
        val largeText = "A".repeat(5000)
        val sanitized = CrashReporter.sanitizeText(largeText)

        assertEquals("Sanitized text must not exceed 3500 characters", 3500, sanitized.length)
    }

    @Test
    fun testPayloadContainsRequiredFieldsAndNoSecrets() {
        val version = "1.2.6"
        val versionCode = 33
        val device = "Google Pixel (Android 14)"
        val threadName = "DefaultDispatcher-worker-1"
        val message = "NullPointerException at https://secret-url.com"
        val stackTrace = "java.lang.NullPointerException\n\tat com.kaan.play.Test.run(Test.kt:15)"

        val payload = CrashReporter.buildReportPayload(
            version = version,
            versionCode = versionCode,
            device = device,
            threadName = threadName,
            message = message,
            stackTrace = stackTrace
        )

        // Doğrulanması gereken alanlar: version, versionCode, device, text
        assertEquals(version, payload.getString("version"))
        assertEquals(versionCode, payload.getInt("versionCode"))
        assertEquals(device, payload.getString("device"))
        assertTrue(payload.has("text"))

        val text = payload.getString("text")
        assertTrue(text.contains("Thread: DefaultDispatcher-worker-1"))
        assertTrue(text.contains("<url>"))
        assertFalse(text.contains("https://secret-url.com"))

        // Gövdede asla bot anahtarı, sohbet kimliği veya gizli alan bulunmamalı
        assertFalse("Payload must not contain TELEGRAM_BOT_TOKEN", payload.has("TELEGRAM_BOT_TOKEN"))
        assertFalse("Payload must not contain TELEGRAM_CHAT_ID", payload.has("TELEGRAM_CHAT_ID"))
        assertFalse("Payload must not contain bot_token", payload.has("bot_token"))
        assertFalse("Payload must not contain chat_id", payload.has("chat_id"))
        assertFalse("Payload must not contain token", payload.has("token"))
        assertFalse("Payload must not contain apikey", payload.has("apikey"))
        assertFalse("Payload must not contain Authorization", payload.has("Authorization"))
    }

    @Test
    fun testDedupPreventsDuplicateReports() {
        val message1 = "IllegalStateException: bad state"
        val trace1 = "com.kaan.play.Test.call(Test.kt:20)\n\tat line 1\n\tat line 2\n\tat line 3"

        val t0 = 10_000L
        // İlk rapor gönderilmeli
        assertTrue("İlk rapor gönderilebilmeli", CrashReporter.shouldSendReport(message1, trace1, t0))

        // Aynı hata cooldown (60 sn) içinde tekrar gönderilmemeli
        val t1 = t0 + 1_000L
        assertFalse("Aynı hata 60 sn içinde tekrar gönderilmemeli (dedup)", CrashReporter.shouldSendReport(message1, trace1, t1))

        // Farklı hata fakat asgari mesaj aralığı (3 sn) dolmadan denenirse engellenmeli (rate-limit)
        val message2 = "IOException: timeout"
        val trace2 = "com.kaan.play.Net.call(Net.kt:10)"
        val t2 = t0 + 2_000L
        assertFalse("İki mesaj arasında 3 sn dolmadan gönderilmemeli", CrashReporter.shouldSendReport(message2, trace2, t2))

        // Asgari mesaj aralığı (3 sn) geçtikten sonra farklı hata gönderilmeli
        val t3 = t0 + 4_000L
        assertTrue("3 sn sonra farklı hata gönderilebilmeli", CrashReporter.shouldSendReport(message2, trace2, t3))

        // 60 sn cooldown geçtikten sonra ilk hata tekrar gönderilebilmeli
        val t4 = t0 + 65_000L
        assertTrue("60 sn cooldown sonrası hata tekrar gönderilebilmeli", CrashReporter.shouldSendReport(message1, trace1, t4))
    }

    @Test
    fun testSendReportHttpHandlesNetworkErrorGracefullyWithoutThrowing() {
        val errorClient = OkHttpClient.Builder()
            .addInterceptor {
                throw IOException("Simulated network connection reset")
            }
            .build()

        val payload = JSONObject().apply {
            put("version", "1.2.6")
            put("versionCode", 33)
            put("device", "TestDevice")
            put("text", "Test error")
        }

        // Hata sessizce yakalanmalı, false dönmeli ve uygulama asla çökmemeli
        val result = CrashReporter.sendReportHttp(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "test-key",
            payload = payload,
            client = errorClient
        )

        assertFalse("Ağ hatasında false dönmeli", result)
    }

    @Test
    fun testSendReportHttpHandlesServerErrorGracefullyWithoutThrowing() {
        val serverErrorClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("Internal Server Error".toResponseBody("text/plain".toMediaType()))
                    .build()
            }
            .build()

        val payload = JSONObject().apply {
            put("version", "1.2.6")
            put("versionCode", 33)
            put("device", "TestDevice")
            put("text", "Test error")
        }

        val result = CrashReporter.sendReportHttp(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "test-key",
            payload = payload,
            client = serverErrorClient
        )

        assertFalse("Sunucu hatasında (500) false dönmeli", result)
    }

    @Test
    fun testSendReportHttpHandlesBlankConfigurationSilently() {
        val payload = JSONObject()

        val result1 = CrashReporter.sendReportHttp("", "key", payload)
        assertFalse("Boş Supabase URL'de false dönmeli", result1)

        val result2 = CrashReporter.sendReportHttp("https://example.supabase.co", "", payload)
        assertFalse("Boş Supabase Key'de false dönmeli", result2)
    }

    @Test
    fun testSendReportHttpReturnsTrueOnSuccess() {
        val successClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val payload = JSONObject().apply {
            put("version", "1.2.6")
            put("versionCode", 33)
            put("device", "TestDevice")
            put("text", "Test error")
        }

        val result = CrashReporter.sendReportHttp(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "test-key",
            payload = payload,
            client = successClient
        )

        assertTrue("204 No Content başarılı yanıtında true dönmeli", result)
    }
}
