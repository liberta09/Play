package com.kaan.play.ui.settings

/**
 * Ayarlar ekranının gruplu düzeni (Android'den bağımsız saf Kotlin katalog kuralları).
 */
enum class SettingsGroupId {
    Appearance,
    Playback,
    AddonsAndSources,
    ProfileAndSync,
    ConnectionAndData,
    About,
}

enum class SettingsItemId {
    // 1. Görünüm ve kişiselleştirme
    Theme,
    Language,
    NavBarPosition,
    PerformanceMode,
    PosterTemplate,
    HeroAutoAdvance,
    HeroTrailer,

    // 2. Oynatma
    PlayerSettings,
    EpisodeNotifications,

    // 3. Eklentiler ve kaynaklar
    Addons,
    Collections,
    WebSites,
    SourceHealth,

    // 4. Profil ve senkronizasyon
    ProfileSelection,
    RememberLastProfile,
    CloudSync,
    WifiSync,
    WatchParty,

    // 5. Bağlantı ve veri
    NetworkAndInternet,
    SpeedTest,
    ClearCache,
    ClearHistory,
    ClearFavorites,
    ResetAllSettings,

    // 6. Hakkında
    About,
}

data class SettingsGroup(
    val id: SettingsGroupId,
    val items: List<SettingsItemId>,
)

object SettingsCatalogRules {
    val GROUPS: List<SettingsGroup> = listOf(
        SettingsGroup(
            id = SettingsGroupId.Appearance,
            items = listOf(
                SettingsItemId.Theme,
                SettingsItemId.Language,
                SettingsItemId.NavBarPosition,
                SettingsItemId.PerformanceMode,
                SettingsItemId.PosterTemplate,
                SettingsItemId.HeroAutoAdvance,
                SettingsItemId.HeroTrailer,
            ),
        ),
        SettingsGroup(
            id = SettingsGroupId.Playback,
            items = listOf(
                SettingsItemId.PlayerSettings,
                SettingsItemId.EpisodeNotifications,
            ),
        ),
        SettingsGroup(
            id = SettingsGroupId.AddonsAndSources,
            items = listOf(
                SettingsItemId.Addons,
                SettingsItemId.Collections,
                SettingsItemId.WebSites,
                SettingsItemId.SourceHealth,
            ),
        ),
        SettingsGroup(
            id = SettingsGroupId.ProfileAndSync,
            items = listOf(
                SettingsItemId.ProfileSelection,
                SettingsItemId.RememberLastProfile,
                SettingsItemId.CloudSync,
                SettingsItemId.WifiSync,
                SettingsItemId.WatchParty,
            ),
        ),
        SettingsGroup(
            id = SettingsGroupId.ConnectionAndData,
            items = listOf(
                SettingsItemId.NetworkAndInternet,
                SettingsItemId.SpeedTest,
                SettingsItemId.ClearCache,
                SettingsItemId.ClearHistory,
                SettingsItemId.ClearFavorites,
                SettingsItemId.ResetAllSettings,
            ),
        ),
        SettingsGroup(
            id = SettingsGroupId.About,
            items = listOf(
                SettingsItemId.About,
            ),
        ),
    )

    val ALL_ITEMS: List<SettingsItemId> = GROUPS.flatMap { it.items }

    fun groupOf(item: SettingsItemId): SettingsGroupId? =
        GROUPS.firstOrNull { it.items.contains(item) }?.id
}
