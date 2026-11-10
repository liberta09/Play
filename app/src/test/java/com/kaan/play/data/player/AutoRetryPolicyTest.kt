package com.kaan.play.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRetryPolicyTest {

    @Test
    fun optionsContainThreeFiveTenAndUnlimited() {
        assertEquals(listOf(3, 5, 10, AutoRetryPolicy.UNLIMITED), AutoRetryPolicy.OPTIONS)
    }

    @Test
    fun normalizeFallsBackToUnlimitedForUnknownValues() {
        assertEquals(3, AutoRetryPolicy.normalize(3))
        assertEquals(5, AutoRetryPolicy.normalize(5))
        assertEquals(10, AutoRetryPolicy.normalize(10))
        assertEquals(AutoRetryPolicy.UNLIMITED, AutoRetryPolicy.normalize(0))
        assertEquals(AutoRetryPolicy.UNLIMITED, AutoRetryPolicy.normalize(null))
        assertEquals(AutoRetryPolicy.UNLIMITED, AutoRetryPolicy.normalize(7))
        assertEquals(AutoRetryPolicy.UNLIMITED, AutoRetryPolicy.normalize(-1))
    }

    @Test
    fun limitOrNullMapsUnlimitedToNullAndPositiveLimitsToValue() {
        assertNull(AutoRetryPolicy.limitOrNull(AutoRetryPolicy.UNLIMITED))
        assertNull(AutoRetryPolicy.limitOrNull(99))
        assertEquals(3, AutoRetryPolicy.limitOrNull(3))
        assertEquals(5, AutoRetryPolicy.limitOrNull(5))
        assertEquals(10, AutoRetryPolicy.limitOrNull(10))
    }

    @Test
    fun canTryAnotherStopsAtPositiveLimitAndAllowsAnyWhenUnlimited() {
        assertTrue(AutoRetryPolicy.canTryAnother(0, 3))
        assertTrue(AutoRetryPolicy.canTryAnother(2, 3))
        assertFalse(AutoRetryPolicy.canTryAnother(3, 3))
        assertTrue(AutoRetryPolicy.hasReachedLimit(3, 3))
        assertTrue(AutoRetryPolicy.hasReachedLimit(5, 3))

        assertTrue(AutoRetryPolicy.canTryAnother(100, null))
        assertFalse(AutoRetryPolicy.hasReachedLimit(100, null))
        assertTrue(AutoRetryPolicy.canTryAnother(100, AutoRetryPolicy.UNLIMITED))
    }
}
