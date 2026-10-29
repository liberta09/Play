package com.kaan.play.data.sync

/** Startup rule for the optional "remember last profile" preference. PIN validation remains in the UI. */
object LastProfileRestoration {
    fun candidate(
        enabled: Boolean,
        setupComplete: Boolean,
        savedProfileId: String,
        profiles: List<LocalProfileSummary>,
    ): LocalProfileSummary? {
        if (!enabled || !setupComplete || savedProfileId.isBlank()) return null
        return profiles.firstOrNull { it.id == savedProfileId && !it.isHidden }
    }
}
