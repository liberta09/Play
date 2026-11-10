package com.kaan.play.data.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppExitDiagnosticsTest {

    @Test
    fun excludesNormalExitsAndClassifiesAppVsSystemExits() {
        assertNull(AppExitClassifier.classify(AppExitClassifier.REASON_EXIT_SELF, 100L))
        assertNull(AppExitClassifier.classify(AppExitClassifier.REASON_USER_REQUESTED, 200L))
        assertNull(AppExitClassifier.classify(AppExitClassifier.REASON_USER_STOPPED, 300L))
        assertNull(AppExitClassifier.classify(AppExitClassifier.REASON_PACKAGE_UPDATED, 400L))
        assertNull(AppExitClassifier.classify(AppExitClassifier.REASON_PACKAGE_STATE_CHANGE, 500L))
        assertNull(AppExitClassifier.classify(AppExitClassifier.REASON_PERMISSION_CHANGE, 600L))

        val anr = AppExitClassifier.classify(AppExitClassifier.REASON_ANR, 1_000L)!!
        assertEquals(ExitCategory.Anr, anr.category)
        assertEquals(ExitAttribution.App, anr.attribution)

        val crash = AppExitClassifier.classify(AppExitClassifier.REASON_CRASH, 2_000L)!!
        assertEquals(ExitCategory.Crash, crash.category)
        assertEquals(ExitAttribution.App, crash.attribution)

        val nativeCrash = AppExitClassifier.classify(AppExitClassifier.REASON_CRASH_NATIVE, 3_000L)!!
        assertEquals(ExitCategory.NativeCrash, nativeCrash.category)
        assertEquals(ExitAttribution.App, nativeCrash.attribution)

        val excessive = AppExitClassifier.classify(AppExitClassifier.REASON_EXCESSIVE_RESOURCE_USAGE, 4_000L)!!
        assertEquals(ExitCategory.ExcessiveResource, excessive.category)
        assertEquals(ExitAttribution.App, excessive.attribution)

        val lowMem = AppExitClassifier.classify(AppExitClassifier.REASON_LOW_MEMORY, 5_000L)!!
        assertEquals(ExitCategory.LowMemory, lowMem.category)
        assertEquals(ExitAttribution.System, lowMem.attribution)

        val signaled = AppExitClassifier.classify(AppExitClassifier.REASON_SIGNALED, 6_000L)!!
        assertEquals(ExitCategory.SystemOrOther, signaled.category)
        assertEquals(ExitAttribution.System, signaled.attribution)
    }

    @Test
    fun filtersNormalExitsAndKeepsLatestFiveAbnormalNewestFirst() {
        val raw = listOf(
            AppExitClassifier.REASON_USER_REQUESTED to 9_000L,
            AppExitClassifier.REASON_CRASH to 8_000L,
            AppExitClassifier.REASON_PACKAGE_UPDATED to 7_000L,
            AppExitClassifier.REASON_ANR to 6_000L,
            AppExitClassifier.REASON_LOW_MEMORY to 5_000L,
            AppExitClassifier.REASON_CRASH_NATIVE to 4_000L,
            AppExitClassifier.REASON_EXCESSIVE_RESOURCE_USAGE to 3_000L,
            AppExitClassifier.REASON_OTHER to 2_000L,
            AppExitClassifier.REASON_USER_STOPPED to 1_000L,
        )
        val filtered = AppExitClassifier.filterRecentAbnormal(raw)
        assertEquals(5, filtered.size)
        assertEquals(
            listOf(8_000L, 6_000L, 5_000L, 4_000L, 3_000L),
            filtered.map { it.timestampMs },
        )
    }

    @Test
    fun sdkGateMatchesAndroid11Api30() {
        assertFalse(AppExitDiagnostics.isSupported(29))
        assertTrue(AppExitDiagnostics.isSupported(30))
        assertTrue(AppExitDiagnostics.isSupported(34))
    }
}
