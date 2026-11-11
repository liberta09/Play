package com.kaan.play.data.party

object WatchPartyDisplayNameRules {
    const val MAX_LENGTH = 40

    /**
     * Resolves display name for Watch Party:
     * 1. Uses active profile name (or candidate name) if not blank.
     * 2. Otherwise falls back to localized guest name.
     * Email prefix is NEVER used.
     * Whitespace is trimmed, and the result is limited to at most 40 characters.
     */
    fun resolve(candidateName: String?, fallbackGuestName: String): String {
        val cleanCandidate = candidateName?.trim().orEmpty()
        val chosen = if (cleanCandidate.isNotEmpty()) {
            cleanCandidate
        } else {
            fallbackGuestName.trim()
        }
        return chosen.take(MAX_LENGTH)
    }
}
