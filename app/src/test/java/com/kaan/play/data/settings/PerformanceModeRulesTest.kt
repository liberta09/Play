package com.kaan.play.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceModeRulesTest {

    @Test
    fun standardAndReducedPoliciesDifferWhereExpected() {
        val standard = PerformancePolicy.forMode(false)
        assertFalse(standard.enabled)
        assertTrue(standard.heroAutoAdvanceEnabled)
        assertTrue(standard.focusScaleAnimationEnabled)
        assertTrue(standard.focusGlowEnabled)
        assertFalse(standard.useSmallerPosters)
        assertEquals(3, standard.maxConcurrentProviderLoads)
        assertEquals(Int.MAX_VALUE, standard.providerShelfBatchSize)
        assertTrue(standard.shadowsAndEffectsEnabled)

        val reduced = PerformancePolicy.forMode(true)
        assertTrue(reduced.enabled)
        assertFalse(reduced.heroAutoAdvanceEnabled)
        assertFalse(reduced.focusScaleAnimationEnabled)
        assertFalse(reduced.focusGlowEnabled)
        assertTrue(reduced.useSmallerPosters)
        assertEquals(1, reduced.maxConcurrentProviderLoads)
        assertEquals(4, reduced.providerShelfBatchSize)
        assertFalse(reduced.shadowsAndEffectsEnabled)
    }

    @Test
    fun batchingUnlocksFourShelvesAtATime() {
        assertEquals(0, PerformanceBatchRules.visibleCount(0, 0, 4))
        assertEquals(4, PerformanceBatchRules.visibleCount(10, 0, 4))
        assertEquals(4, PerformanceBatchRules.visibleCount(10, 3, 4))
        assertEquals(8, PerformanceBatchRules.visibleCount(10, 4, 4))
        assertEquals(8, PerformanceBatchRules.visibleCount(10, 7, 4))
        assertEquals(10, PerformanceBatchRules.visibleCount(10, 8, 4))
        assertEquals(10, PerformanceBatchRules.visibleCount(10, 0, Int.MAX_VALUE))
    }
}
