package com.kaan.play.data.party

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchPartyErrorTest {

    @Test
    fun `maps server message to typed error`() {
        assertEquals(WatchPartyError.NotAuthenticated, WatchPartyError.fromServerMessage("not_authenticated"))
        assertEquals(WatchPartyError.InvalidInput, WatchPartyError.fromServerMessage("invalid_input"))
        assertEquals(WatchPartyError.RoomNotFound, WatchPartyError.fromServerMessage("room_not_found"))
        assertEquals(WatchPartyError.RoomFull, WatchPartyError.fromServerMessage("room_full"))
        assertEquals(WatchPartyError.NotHostOrClosed, WatchPartyError.fromServerMessage("not_host_or_closed"))
        assertEquals(WatchPartyError.CodeGenerationFailed, WatchPartyError.fromServerMessage("code_generation_failed"))
        assertEquals(WatchPartyError.ServerUnavailable, WatchPartyError.fromServerMessage("PGRST202"))
        assertEquals(WatchPartyError.ServerUnavailable, WatchPartyError.fromServerMessage("Could not find the function wp_join_room"))
        assertEquals(WatchPartyError.ServerUnavailable, WatchPartyError.fromServerMessage("server_unavailable"))

        val unknown = WatchPartyError.fromServerMessage("unexpected DB error 500")
        assertTrue(unknown is WatchPartyError.Unknown)
        assertEquals("unexpected DB error 500", (unknown as WatchPartyError.Unknown).message)
    }

    @Test
    fun `WatchPartyException carries typed error`() {
        val ex = WatchPartyException(WatchPartyError.RoomNotFound)
        assertEquals(WatchPartyError.RoomNotFound, ex.error)
    }
}
