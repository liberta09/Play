package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBufferConfigTest {

    private val mb16 = 16 * 1024 * 1024
    private val mb64 = 64 * 1024 * 1024

    private val gb1 = 1L * 1024 * 1024 * 1024
    private val gb2 = 2L * 1024 * 1024 * 1024
    private val gb4 = 4L * 1024 * 1024 * 1024
    private val gb8 = 8L * 1024 * 1024 * 1024
    private val gb16 = 16L * 1024 * 1024 * 1024

    @Test
    fun tvDeviceAlwaysKeeps16MbToPreventOom() {
        val bytesHighMemTv = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = true,
            memoryClass = 256,
            totalMemBytes = gb16,
        )
        assertEquals(mb16, bytesHighMemTv)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = true, memoryClass = 256, totalMemBytes = gb16))

        val bytesLowMemTv = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = true,
            isTv = true,
            memoryClass = 128,
            totalMemBytes = gb2,
        )
        assertEquals(mb16, bytesLowMemTv)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = true, isTv = true, memoryClass = 128, totalMemBytes = gb2))
    }

    @Test
    fun lowRamDeviceKeeps16Mb() {
        val bytes = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = true,
            isTv = false,
            memoryClass = 256,
            totalMemBytes = gb16,
        )
        assertEquals(mb16, bytes)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = true, isTv = false, memoryClass = 256, totalMemBytes = gb16))
    }

    @Test
    fun lowMemoryClassKeeps16MbEvenWithHighTotalRam() {
        // memoryClass = 128 (largeMemoryClass 256 olsa dahi dikkate alınmaz) => 16 MB
        val bytes = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 128,
            totalMemBytes = gb8,
        )
        assertEquals(mb16, bytes)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false, memoryClass = 128, totalMemBytes = gb8))
    }

    @Test
    fun lowTotalRamBoxWithoutLeanbackKeeps16Mb() {
        // 1-2 GB RAM'li, leanback bildirmeyen TV kutusu: memoryClass >= 256 olsa dahi totalMem < 4 GB => 16 MB
        val bytes1Gb = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 256,
            totalMemBytes = gb1,
        )
        assertEquals(mb16, bytes1Gb)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false, memoryClass = 256, totalMemBytes = gb1))

        val bytes2Gb = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 256,
            totalMemBytes = gb2,
        )
        assertEquals(mb16, bytes2Gb)
        assertFalse(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false, memoryClass = 256, totalMemBytes = gb2))
    }

    @Test
    fun modernPhoneWithHighMemoryClassAndAtLeast4GbRamUses64Mb() {
        // 4 GB RAM ve memoryClass 256 => 64 MB
        val bytes4Gb = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 256,
            totalMemBytes = gb4,
        )
        assertEquals(mb64, bytes4Gb)
        assertTrue(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false, memoryClass = 256, totalMemBytes = gb4))

        // OnePlus 12R: 16 GB RAM ve memoryClass 256 => 64 MB
        val bytesOnePlus = PlayerBufferConfig.computeTargetBufferBytes(
            isLowRam = false,
            isTv = false,
            memoryClass = 256,
            totalMemBytes = gb16,
        )
        assertEquals(mb64, bytesOnePlus)
        assertTrue(PlayerBufferConfig.shouldPrioritizeTimeOverSize(isLowRam = false, isTv = false, memoryClass = 256, totalMemBytes = gb16))
    }
}
