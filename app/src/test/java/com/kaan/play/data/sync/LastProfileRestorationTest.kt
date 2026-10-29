package com.kaan.play.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastProfileRestorationTest {
    private val admin = LocalProfileSummary(id = "admin", name = "Admin", syncCode = "123456", isAdmin = true)
    private val second = LocalProfileSummary(id = "second", name = "Second", syncCode = "654321")

    @Test
    fun restoresSavedProfileOnlyWhenEnabledAndSetupIsComplete() {
        assertEquals(second, LastProfileRestoration.candidate(true, true, "second", listOf(admin, second)))
        assertNull(LastProfileRestoration.candidate(false, true, "second", listOf(admin, second)))
        assertNull(LastProfileRestoration.candidate(true, false, "second", listOf(admin, second)))
    }

    @Test
    fun doesNotRestoreMissingOrHiddenProfile() {
        assertNull(LastProfileRestoration.candidate(true, true, "gone", listOf(admin, second)))
        val hidden = second.copy(isHidden = true)
        assertNull(LastProfileRestoration.candidate(true, true, "second", listOf(admin, hidden)))
    }

    @Test
    fun pinProtectedProfileIsReturnedForTheUiToRequestPin() {
        val protected = second.copy(pinHash = "hash")
        assertEquals(protected, LastProfileRestoration.candidate(true, true, "second", listOf(admin, protected)))
    }
}
