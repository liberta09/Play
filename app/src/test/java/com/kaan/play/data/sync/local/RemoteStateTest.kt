package com.kaan.play.data.sync.local

import com.kaan.play.data.net.PlayJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteStateTest {
    @Test fun oldTvErrorReplyIsNotAState() {
        // Eski TV durum sorusuna `{"error":"bad_request"}` döner; v = 0 → kumanda yok sayar.
        val s = PlayJson.decodeFromString(RemoteState.serializer(), """{"error":"bad_request"}""")
        assertEquals(0, s.v)
    }

    @Test fun stateRoundTrips() {
        val s = RemoteState(v = 1, title = "A", subtitle = "S1 · B1", posterUrl = "https://x/y.jpg", playing = true,
            positionMs = 1_000, durationMs = 60_000, volume = 7, maxVolume = 15, muted = false)
        assertEquals(s, PlayJson.decodeFromString(RemoteState.serializer(), PlayJson.encodeToString(RemoteState.serializer(), s)))
    }

    @Test fun stateQueryAndOldInputsDecode() {
        val q = PlayJson.decodeFromString(RemoteInput.serializer(), PlayJson.encodeToString(RemoteInput.serializer(), RemoteInput(state = true)))
        assertTrue(q.state)
        // Eski telefonun gönderdiği tuş (state alanı yok) aynen çözülür.
        val old = PlayJson.decodeFromString(RemoteInput.serializer(), """{"key":"Ok"}""")
        assertEquals(RemoteKey.Ok, old.key)
        assertFalse(old.state)
        assertEquals(RemoteKey.VolumeUp, PlayJson.decodeFromString(RemoteInput.serializer(), """{"key":"VolumeUp"}""").key)
    }
}
