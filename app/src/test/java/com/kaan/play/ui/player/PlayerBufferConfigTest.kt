package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBufferConfigTest {

    private val mb16 = 16 * 1024 * 1024
    private val mb48 = 48 * 1024 * 1024
    private val mb64 = 64 * 1024 * 1024

    @Test
    fun tvDeviceAlwaysKeeps16MbAndPrioritizesSizeToPreventOom() {
        val bytesHighMemTv = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = true,
            memoryClass = 256,
            largeMemoryClass = 512,
        )
        assertEquals(mb16, bytesHighMemTv)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = true))

        val bytesLowMemTv = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = true,
            isTv = true,
            memoryClass = 128,
            largeMemoryClass = 128,
        )
        assertEquals(mb16, bytesLowMemTv)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = true, isTv = true))
    }

    @Test
    fun lowRamDeviceKeeps16MbAndPrioritizesSize() {
        val bytes = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = true,
            isTv = false,
            memoryClass = 256,
            largeMemoryClass = 512,
        )
        assertEquals(mb16, bytes)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = true, isTv = false))
    }

    @Test
    fun budgetPhoneBelow192MbUses16MbWithTimePriority() {
        val bytes = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 128,
            largeMemoryClass = 128,
        )
        assertEquals(mb16, bytes)
        assertTrue(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false))
    }

    @Test
    fun standardPhoneBetween192And256MbUses48Mb() {
        val bytes = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 192,
            largeMemoryClass = 192,
        )
        assertEquals(mb48, bytes)
        assertTrue(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false))
    }

    @Test
    fun highEndPhoneWithLargeMemoryUses64Mb() {
        // OnePlus 12R: memoryClass=256, largeMemoryClass=512
        val bytes = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 256,
            largeMemoryClass = 512,
        )
        assertEquals(mb64, bytes)
        assertTrue(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false))
    }

    @Test
    fun largeMemoryClassElevatesTargetBufferBytes() {
        val bytes = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 128,
            largeMemoryClass = 256,
        )
        assertEquals(mb64, bytes)
        assertTrue(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false))
    }
}
