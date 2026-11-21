package com.kaan.play.ui.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteClockTest {
    @Test fun formatsLikeTheDesign() {
        assertEquals("0:00", clock(0))
        assertEquals("17:44", clock(1_064_000))
        assertEquals("1:17:53", clock(4_673_000))
        assertEquals("0:00", clock(-5))
    }
}
