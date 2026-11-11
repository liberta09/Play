package com.kaan.play.data.party

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoenixFrameTest {

    @Test
    fun `serialize and parse heartbeat frame vsn 1_0_0 JSON object`() {
        val frame = PhoenixFrame.heartbeat(ref = "42")
        val serialized = frame.serialize()

        // vsn=1.0.0 Phoenix JSON object format
        val json = Json.parseToJsonElement(serialized).jsonObject
        assertEquals("phoenix", json["topic"]?.jsonPrimitive?.content)
        assertEquals("heartbeat", json["event"]?.jsonPrimitive?.content)
        assertEquals("42", json["ref"]?.jsonPrimitive?.content)

        val parsed = PhoenixFrame.parse(serialized)
        assertNotNull(parsed)
        assertEquals("42", parsed!!.ref)
        assertEquals("phoenix", parsed.topic)
        assertEquals("heartbeat", parsed.event)
        assertNull(parsed.joinRef)
    }

    @Test
    fun `join frame contains private true and access_token in payload`() {
        val frame = PhoenixFrame.join(
            topic = "realtime:wp:room-123",
            ref = "1",
            jwt = "mock-jwt-token",
            userId = "user-abc-456",
            joinRef = "1",
        )
        val serialized = frame.serialize()

        val parsed = PhoenixFrame.parse(serialized)
        assertNotNull(parsed)
        assertEquals("realtime:wp:room-123", parsed!!.topic)
        assertEquals("phx_join", parsed.event)
        assertEquals("1", parsed.ref)
        assertEquals("1", parsed.joinRef)

        val payload = parsed.payload.jsonObject
        // 1) access_token is present in join payload
        assertEquals("mock-jwt-token", payload["access_token"]?.jsonPrimitive?.content)

        // 2) config has private: true and presence key
        val config = payload["config"]?.jsonObject
        assertNotNull(config)
        assertTrue(config!!["private"]?.jsonPrimitive?.boolean == true)

        val presence = config["presence"]?.jsonObject
        assertNotNull(presence)
        assertEquals("user-abc-456", presence!!["key"]?.jsonPrimitive?.content)

        val broadcast = config["broadcast"]?.jsonObject
        assertNotNull(broadcast)
        assertFalse(broadcast!!["self"]?.jsonPrimitive?.boolean == true)
        assertFalse(broadcast["ack"]?.jsonPrimitive?.boolean == true)
    }

    @Test
    fun `accessToken frame sends new token on channel`() {
        val frame = PhoenixFrame.accessToken(
            topic = "realtime:wp:room-123",
            ref = "2",
            jwt = "refreshed-jwt-token",
        )
        val parsed = PhoenixFrame.parse(frame.serialize())
        assertNotNull(parsed)
        assertEquals("access_token", parsed!!.event)
        assertEquals("refreshed-jwt-token", parsed.payload.jsonObject["access_token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `serialize and extract broadcast control message`() {
        val data = buildJsonObject {
            put("seq", JsonPrimitive(10))
            put("positionMs", JsonPrimitive(5000L))
            put("playing", JsonPrimitive(true))
            put("speed", JsonPrimitive(1.0f))
            put("sentAtMs", JsonPrimitive(12345678L))
        }
        val broadcastFrame = PhoenixFrame.broadcast(
            topic = "realtime:wp:room-abc",
            ref = "3",
            event = "control",
            data = data,
        )
        val serialized = broadcastFrame.serialize()

        val parsed = PhoenixFrame.parse(serialized)
        assertNotNull(parsed)
        assertEquals("broadcast", parsed!!.event)

        val extracted = PhoenixFrame.extractBroadcast(parsed)
        assertNotNull(extracted)
        val (eventName, payload) = extracted!!
        assertEquals("control", eventName)
        assertEquals(10L, payload.jsonObject["seq"]?.jsonPrimitive?.content?.toLong())
        assertEquals(true, payload.jsonObject["playing"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `presence track frame contains user_id display_name and role`() {
        val frame = PhoenixFrame.presenceTrack(
            topic = "realtime:wp:room-xyz",
            ref = "4",
            userId = "uid-1",
            displayName = "Kaan",
            role = "host",
        )
        val parsed = PhoenixFrame.parse(frame.serialize())
        assertNotNull(parsed)
        assertEquals("presence", parsed!!.event)
        val payload = parsed.payload.jsonObject
        assertEquals("track", payload["event"]?.jsonPrimitive?.content)
        val trackPayload = payload["payload"]?.jsonObject
        assertNotNull(trackPayload)
        assertEquals("uid-1", trackPayload!!["user_id"]?.jsonPrimitive?.content)
        assertEquals("Kaan", trackPayload["display_name"]?.jsonPrimitive?.content)
        assertEquals("host", trackPayload["role"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parse invalid frame returns null`() {
        assertNull(PhoenixFrame.parse("not json"))
        assertNull(PhoenixFrame.parse("{}")) // missing topic/event
        assertNull(PhoenixFrame.parse("[]")) // array format
    }
}
