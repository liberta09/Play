package com.kaan.play.data.party

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchPartyDisplayNameRulesTest {

    @Test
    fun `resolve uses candidate name when provided`() {
        val result = WatchPartyDisplayNameRules.resolve(
            candidateName = "Kaan",
            fallbackGuestName = "Misafir",
        )
        assertEquals("Kaan", result)
    }

    @Test
    fun `resolve trims leading and trailing whitespace from candidate name`() {
        val result = WatchPartyDisplayNameRules.resolve(
            candidateName = "   Kaan Okan   ",
            fallbackGuestName = "Misafir",
        )
        assertEquals("Kaan Okan", result)
    }

    @Test
    fun `resolve truncates candidate name to at most 40 characters`() {
        val longName = "A".repeat(50)
        val result = WatchPartyDisplayNameRules.resolve(
            candidateName = longName,
            fallbackGuestName = "Misafir",
        )
        assertEquals(40, result.length)
        assertEquals("A".repeat(40), result)
    }

    @Test
    fun `resolve falls back to guest name when candidate is null`() {
        val result = WatchPartyDisplayNameRules.resolve(
            candidateName = null,
            fallbackGuestName = "Misafir",
        )
        assertEquals("Misafir", result)
    }

    @Test
    fun `resolve falls back to guest name when candidate is empty or only whitespace`() {
        val emptyResult = WatchPartyDisplayNameRules.resolve(
            candidateName = "",
            fallbackGuestName = "Guest",
        )
        assertEquals("Guest", emptyResult)

        val blankResult = WatchPartyDisplayNameRules.resolve(
            candidateName = "    ",
            fallbackGuestName = "Guest",
        )
        assertEquals("Guest", blankResult)
    }

    @Test
    fun `resolve trims fallback guest name and enforces max length`() {
        val longGuest = "   " + "G".repeat(50) + "   "
        val result = WatchPartyDisplayNameRules.resolve(
            candidateName = null,
            fallbackGuestName = longGuest,
        )
        assertEquals(40, result.length)
        assertEquals("G".repeat(40), result)
    }

    @Test
    fun `email is never used as fallback when profile name is missing`() {
        // Explicit check: When profile name is absent, display name must be the localized guest fallback,
        // and never an email or email prefix.
        val fakeAccountEmail = "kaan.okan.test@example.com"
        val emailPrefix = fakeAccountEmail.substringBefore('@')

        val resolved = WatchPartyDisplayNameRules.resolve(
            candidateName = null,
            fallbackGuestName = "Misafir",
        )

        assertEquals("Misafir", resolved)
        org.junit.Assert.assertNotEquals(emailPrefix, resolved)
    }
}
