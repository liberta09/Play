package com.kaan.play.data.net

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SlowConnectionRulesTest {

    @Test
    fun scalesTimeouts3xWhenEnabledAndLeavesUnchangedWhenOff() {
        assertEquals(10_000, SlowConnectionRules.scaledTimeoutMs(10_000, slowMode = false))
        assertEquals(30_000, SlowConnectionRules.scaledTimeoutMs(10_000, slowMode = true))
        assertEquals(45_000, SlowConnectionRules.scaledTimeoutMs(15_000, slowMode = true))
    }

    @Test
    fun getAndHeadRetryOnceAfterNetworkErrorWhilePostNeverRetries() {
        val io = IOException("reset")
        assertTrue(SlowConnectionRules.shouldRetryAfterFailure("GET", slowMode = true, retryAttempted = 0, failure = io))
        assertTrue(SlowConnectionRules.shouldRetryAfterFailure("HEAD", slowMode = true, retryAttempted = 0, failure = io))
        assertFalse(SlowConnectionRules.shouldRetryAfterFailure("GET", slowMode = true, retryAttempted = 1, failure = io))
        assertFalse(SlowConnectionRules.shouldRetryAfterFailure("POST", slowMode = true, retryAttempted = 0, failure = io))
        assertFalse(SlowConnectionRules.shouldRetryAfterFailure("PUT", slowMode = true, retryAttempted = 0, failure = io))
        assertFalse(SlowConnectionRules.shouldRetryAfterFailure("GET", slowMode = false, retryAttempted = 0, failure = io))
        assertFalse(
            SlowConnectionRules.shouldRetryAfterFailure(
                "GET",
                slowMode = true,
                retryAttempted = 0,
                failure = com.kaan.play.data.cloudstream.BlockedAddressException("blocked"),
            ),
        )
    }

    @Test
    fun okHttpClientVerifies3xTimeoutsGetHeadSingleRetryAndPostNeverRetries() {
        val observedConnectMs = AtomicInteger(0)
        val observedReadMs = AtomicInteger(0)
        val calls = AtomicInteger(0)
        val failNextCall = AtomicInteger(0)

        val terminal = Interceptor { chain ->
            observedConnectMs.set(chain.connectTimeoutMillis())
            observedReadMs.set(chain.readTimeoutMillis())
            calls.incrementAndGet()
            if (failNextCall.getAndDecrement() > 0) {
                throw IOException("simulated network failure")
            }
            val code = if (chain.request().method == "HEAD") 204 else 200
            val body = if (code == 204) "".toResponseBody(null) else "ok".toResponseBody("text/plain".toMediaType())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(body)
                .build()
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(2_000, TimeUnit.MILLISECONDS)
            .readTimeout(3_000, TimeUnit.MILLISECONDS)
            .addInterceptor(SlowConnectionInterceptor { true })
            .addInterceptor(terminal)
            .build()

        // 1) GET ilk ağ hatasında 1 kez yenilenir, 3x timeout gözlenir.
        failNextCall.set(1)
        client.newCall(Request.Builder().url("https://example.com/get").get().build()).execute().use { resp ->
            assertEquals(200, resp.code)
            assertEquals("ok", resp.body.string())
        }
        assertEquals(6_000, observedConnectMs.get())
        assertEquals(9_000, observedReadMs.get())
        assertEquals(2, calls.get())

        // 2) HEAD ilk ağ hatasında 1 kez yenilenir.
        failNextCall.set(1)
        client.newCall(Request.Builder().url("https://example.com/head").head().build()).execute().use { resp ->
            assertEquals(204, resp.code)
        }
        assertEquals(4, calls.get())

        // 3) POST ağ hatasında ASLA yeniden denenmez (tek çağrı = 5).
        failNextCall.set(2)
        val postFailed = runCatching {
            client.newCall(Request.Builder().url("https://example.com/post").post("x=1".toRequestBody()).build()).execute()
        }.isFailure
        assertTrue(postFailed)
        assertEquals(5, calls.get())
    }

    @Test
    fun promptRuleTriggersOnlyForContinuous15sOnlineNonTorrentBufferingOncePerSession() {
        assertTrue(
            SlowConnectionPromptRules.shouldShowPrompt(
                suggestEnabled = true,
                slowModeEnabled = false,
                promptedThisSession = false,
                isOffline = false,
                isTorrent = false,
                firstFrameRendered = true,
                isSeeking = false,
                currentBufferingStartMs = 1_000L,
                nowMs = 16_000L,
            ),
        )
        assertFalse(
            SlowConnectionPromptRules.shouldShowPrompt(
                suggestEnabled = true,
                slowModeEnabled = false,
                promptedThisSession = false,
                isOffline = false,
                isTorrent = false,
                firstFrameRendered = true,
                isSeeking = false,
                currentBufferingStartMs = 1_000L,
                nowMs = 15_900L,
            ),
        )
        assertFalse(
            SlowConnectionPromptRules.shouldShowPrompt(
                suggestEnabled = true,
                slowModeEnabled = false,
                promptedThisSession = false,
                isOffline = true,
                isTorrent = false,
                firstFrameRendered = true,
                isSeeking = false,
                currentBufferingStartMs = 1_000L,
                nowMs = 20_000L,
            ),
        )
        assertFalse(
            SlowConnectionPromptRules.shouldShowPrompt(
                suggestEnabled = true,
                slowModeEnabled = false,
                promptedThisSession = false,
                isOffline = false,
                isTorrent = true,
                firstFrameRendered = true,
                isSeeking = false,
                currentBufferingStartMs = 1_000L,
                nowMs = 20_000L,
            ),
        )
        assertFalse(
            SlowConnectionPromptRules.shouldShowPrompt(
                suggestEnabled = true,
                slowModeEnabled = true,
                promptedThisSession = false,
                isOffline = false,
                isTorrent = false,
                firstFrameRendered = true,
                isSeeking = false,
                currentBufferingStartMs = 1_000L,
                nowMs = 20_000L,
            ),
        )
        assertFalse(
            SlowConnectionPromptRules.shouldShowPrompt(
                suggestEnabled = true,
                slowModeEnabled = false,
                promptedThisSession = true,
                isOffline = false,
                isTorrent = false,
                firstFrameRendered = true,
                isSeeking = false,
                currentBufferingStartMs = 1_000L,
                nowMs = 20_000L,
            ),
        )
    }
}
