package com.kaan.play.data.settings

/** Atlama düğmesi ayarlarının denetlediği bölüm türleri (chapter işaretleri ve TheIntroDB zamanları). */
enum class SkipCategory {
    Intro,
    Recap,
    Credits,
    Preview,
}

/** Ayarlar > Oynatıcı: düğmeler varsayılan açık, otomatik atlama varsayılan kapalı, dört tür ayrı ayrı açılıp kapanır. */
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
