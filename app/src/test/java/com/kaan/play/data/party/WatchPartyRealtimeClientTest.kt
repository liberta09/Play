package com.kaan.play.data.party

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchPartyRealtimeClientTest {

    private class FakeWebSocket : WebSocket {
        val sentMessages = mutableListOf<String>()
        var closedCode: Int? = null
        var closedReason: String? = null

        override fun cancel() {}

        override fun close(code: Int, reason: String?): Boolean {
            closedCode = code
            closedReason = reason
            return true
        }

        override fun queueSize(): Long = 0L

        override fun request(): Request = Request.Builder().url("http://localhost").build()

        override fun send(text: String): Boolean {
            sentMessages.add(text)
            return true
        }

        override fun send(bytes: ByteString): Boolean = true
    }

    @Test
    fun `buildWsUrl contains apikey and vsn 1_0_0 and NO token`() {
        val projectUrl = "https://example-project.supabase.co"
        val apiKey = "anon-key-12345"

        val url = WatchPartyRealtimeClient.buildWsUrl(projectUrl, apiKey)

        // 1) Contains apikey and vsn=1.0.0
        assertTrue("URL must contain apikey", url.contains("apikey=anon-key-12345"))
        assertTrue("URL must contain vsn=1.0.0", url.contains("vsn=1.0.0"))
        assertTrue("URL must start with wss://", url.startsWith("wss://example-project.supabase.co/realtime/v1/websocket"))

        // 2) NEVER contains access token in URL
        assertFalse("URL must NOT contain access_token", url.contains("access_token"))
        assertFalse("URL must NOT contain token=", url.contains("token="))
        assertFalse("URL must NOT contain bearer", url.contains("bearer", ignoreCase = true))
    }

    @Test
    fun `buildWsUrl handles http scheme`() {
        val url = WatchPartyRealtimeClient.buildWsUrl("http://localhost:54321", "local-key")
        assertTrue(url.startsWith("ws://localhost:54321/realtime/v1/websocket"))
    }

    @Test
    fun `guest cannot broadcast control or content or closed`() = runTest {
        val testScope = TestScope()
        val mockHttpClient = OkHttpClient()
        val client = WatchPartyRealtimeClient(mockHttpClient, testScope)

        val fakeWs = FakeWebSocket()

        // Reflection to inject webSocket, currentRole as GUEST, activeRoomId
        val wsField = WatchPartyRealtimeClient::class.java.getDeclaredField("webSocket").apply { isAccessible = true }
        wsField.set(client, fakeWs)

        val roleField = WatchPartyRealtimeClient::class.java.getDeclaredField("currentRole").apply { isAccessible = true }
        roleField.set(client, PartyRole.GUEST)

        val roomField = WatchPartyRealtimeClient::class.java.getDeclaredField("activeRoomId").apply { isAccessible = true }
        roomField.set(client, "room-guest-1")

        // Guest attempts to broadcast control
        client.broadcastControl(PartyControlPacket(seq = 1, positionMs = 1000L, playing = true, speed = 1f, sentAtMs = 100L))
        // Guest attempts to broadcast content
        client.broadcastContent(PartyContentPacket(tmdbId = 123, mediaType = "movie", season = null, episode = null, title = "Film"))
        // Guest attempts to broadcast closed
        client.broadcastClosed()

        // Verify WebSocket.send was NEVER called!
        assertEquals(0, fakeWs.sentMessages.size)
    }

    @Test
    fun `phx_reply with error status stops reconnection and sets error state`() = runTest {
        val testScope = TestScope()
        val mockHttpClient = OkHttpClient()
        val client = WatchPartyRealtimeClient(mockHttpClient, testScope)

        val fakeWs = FakeWebSocket()

        // Set up client state
        val wsField = WatchPartyRealtimeClient::class.java.getDeclaredField("webSocket").apply { isAccessible = true }
        wsField.set(client, fakeWs)

        val joinRefField = WatchPartyRealtimeClient::class.java.getDeclaredField("currentJoinRef").apply { isAccessible = true }
        joinRefField.set(client, "join-ref-1")

        // Deliver phx_reply with status=error
        val replyPayload = buildJsonObject {
            put("status", JsonPrimitive("error"))
            put("response", buildJsonObject {
                put("message", JsonPrimitive("Unauthorized"))
            })
        }
        val replyFrame = PhoenixFrame(
            joinRef = "join-ref-1",
            ref = "join-ref-1",
            topic = "realtime:wp:room-1",
            event = "phx_reply",
            payload = replyPayload,
        )

        val handleMethod = WatchPartyRealtimeClient::class.java.getDeclaredMethod(
            "handleIncomingMessage",
            WebSocket::class.java,
            String::class.java,
        ).apply { isAccessible = true }

        handleMethod.invoke(client, fakeWs, replyFrame.serialize())

        // Verify state is Error
        val state = client.connectionState.value
        assertTrue("State must be Error", state is PartyConnectionState.Error)

        // Verify socket was closed with code 1000
        assertEquals(1000, fakeWs.closedCode)
        assertEquals("Auth error", fakeWs.closedReason)

        // Verify isManuallyClosed is true (which prevents scheduleReconnect)
        val closedField = WatchPartyRealtimeClient::class.java.getDeclaredField("isManuallyClosed").apply { isAccessible = true }
        assertTrue(closedField.get(client) == true)
    }
}
