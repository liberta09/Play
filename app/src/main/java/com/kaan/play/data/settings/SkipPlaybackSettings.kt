package com.kaan.play.data.settings

/** Community/chapter segment categories controlled by the skip button settings. */
enum class SkipCategory {
    Intro,
    Recap,
    Credits,
    Preview,
}

data class SkipPlaybackSettings(
    val showButtons: Boolean = true,
    val autoSkip: Boolean = false,
    val enabledCategories: Set<SkipCategory> = SkipCategory.entries.toSet(),
) {
    fun isEnabled(category: SkipCategory): Boolean = category in enabledCategories

    companion object {
        val Default = SkipPlaybackSettings()
    }
}
