package com.kaan.play.data.party

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PhoenixFrameTest {

    @Test
    fun `serialize and parse heartbeat frame`() {
        val frame = PhoenixFrame.heartbeat(ref = "42")
        val serialized = frame.serialize()

        assertEquals("[null,\"42\",\"phoenix\",\"heartbeat\",{}]", serialized)

        val parsed = PhoenixFrame.parse(serialized)
        assertNotNull(parsed)
        assertNull(parsed!!.joinRef)
        assertEquals("42", parsed.ref)
        assertEquals("phoenix", parsed.topic)
        assertEquals("heartbeat", parsed.event)
    }

    @Test
    fun `serialize and parse join frame`() {
        val frame = PhoenixFrame.join(topic = "realtime:wp:room-123", ref = "1", joinRef = "1")
        val serialized = frame.serialize()

        assertEquals("[\"1\",\"1\",\"realtime:wp:room-123\",\"phx_join\",{}]", serialized)

        val parsed = PhoenixFrame.parse(serialized)
        assertNotNull(parsed)
        assertEquals("1", parsed!!.joinRef)
        assertEquals("1", parsed.ref)
        assertEquals("realtime:wp:room-123", parsed.topic)
        assertEquals("phx_join", parsed.event)
    }

    @Test
    fun `serialize and parse leave frame`() {
        val frame = PhoenixFrame.leave(topic = "realtime:wp:room-123", ref = "2", joinRef = "1")
        val serialized = frame.serialize()

        assertEquals("[\"1\",\"2\",\"realtime:wp:room-123\",\"phx_leave\",{}]", serialized)

        val parsed = PhoenixFrame.parse(serialized)
        assertNotNull(parsed)
        assertEquals("1", parsed!!.joinRef)
        assertEquals("2", parsed.ref)
        assertEquals("realtime:wp:room-123", parsed.topic)
        assertEquals("phx_leave", parsed.event)
    }

    @Test
    fun `serialize and extract broadcast message`() {
        val data = buildJsonObject {
            put("seq", JsonPrimitive(10))
            put("is_playing", JsonPrimitive(true))
        }
        val broadcastFrame = PhoenixFrame.broadcast(
            topic = "realtime:wp:room-abc",
            ref = "3",
            event = "sync",
            data = data,
        )
        val serialized = broadcastFrame.serialize()

        val parsed = PhoenixFrame.parse(serialized)
        assertNotNull(parsed)
        assertEquals("broadcast", parsed!!.event)

        val extracted = PhoenixFrame.extractBroadcast(parsed)
        assertNotNull(extracted)
        val (eventName, payload) = extracted!!
        assertEquals("sync", eventName)
        assertEquals("{\"seq\":10,\"is_playing\":true}", payload.toString())
    }

    @Test
    fun `parse invalid frame returns null`() {
        assertNull(PhoenixFrame.parse("not json"))
        assertNull(PhoenixFrame.parse("{}"))
        assertNull(PhoenixFrame.parse("[\"1\",\"2\"]")) // less than 5 elements
    }
}
