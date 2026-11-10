package com.kaan.play.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogRulesTest {

    @Test
    fun verifiesExactSixGroupsInOrder() {
        val expectedGroups = listOf(
            SettingsGroupId.Appearance,
            SettingsGroupId.Playback,
            SettingsGroupId.AddonsAndSources,
            SettingsGroupId.ProfileAndSync,
            SettingsGroupId.ConnectionAndData,
            SettingsGroupId.About,
        )
        assertEquals(expectedGroups, SettingsCatalogRules.GROUPS.map { it.id })
    }

    @Test
    fun verifiesExactTwentyTwoItemsAndNoDuplicates() {
        assertEquals(22, SettingsCatalogRules.ALL_ITEMS.size)
        assertEquals(22, SettingsCatalogRules.ALL_ITEMS.toSet().size)
        assertEquals(SettingsItemId.entries.size, SettingsCatalogRules.ALL_ITEMS.size)
    }

    @Test
    fun verifiesGroupItemCountsAndOrder() {
        val appearance = SettingsCatalogRules.GROUPS[0]
        assertEquals(
            listOf(
                SettingsItemId.Theme,
                SettingsItemId.Language,
                SettingsItemId.NavBarPosition,
                SettingsItemId.PerformanceMode,
                SettingsItemId.PosterTemplate,
            ),
            appearance.items,
        )

        val playback = SettingsCatalogRules.GROUPS[1]
        assertEquals(
            listOf(
                SettingsItemId.PlayerSettings,
                SettingsItemId.EpisodeNotifications,
            ),
            playback.items,
        )

        val addons = SettingsCatalogRules.GROUPS[2]
        assertEquals(
            listOf(
                SettingsItemId.Addons,
                SettingsItemId.Collections,
                SettingsItemId.WebSites,
                SettingsItemId.SourceHealth,
            ),
            addons.items,
        )

        val profile = SettingsCatalogRules.GROUPS[3]
        assertEquals(
            listOf(
                SettingsItemId.ProfileSelection,
                SettingsItemId.RememberLastProfile,
                SettingsItemId.CloudSync,
                SettingsItemId.WifiSync,
            ),
            profile.items,
        )

        val connectionData = SettingsCatalogRules.GROUPS[4]
        assertEquals(
            listOf(
                SettingsItemId.NetworkAndInternet,
                SettingsItemId.SpeedTest,
                SettingsItemId.ClearCache,
                SettingsItemId.ClearHistory,
                SettingsItemId.ClearFavorites,
                SettingsItemId.ResetAllSettings,
            ),
            connectionData.items,
        )

        val about = SettingsCatalogRules.GROUPS[5]
        assertEquals(
            listOf(SettingsItemId.About),
            about.items,
        )
    }

    @Test
    fun verifiesGroupLookup() {
        assertEquals(SettingsGroupId.Appearance, SettingsCatalogRules.groupOf(SettingsItemId.Theme))
        assertEquals(SettingsGroupId.Playback, SettingsCatalogRules.groupOf(SettingsItemId.PlayerSettings))
        assertEquals(SettingsGroupId.AddonsAndSources, SettingsCatalogRules.groupOf(SettingsItemId.WebSites))
        assertEquals(SettingsGroupId.ProfileAndSync, SettingsCatalogRules.groupOf(SettingsItemId.WifiSync))
        assertEquals(SettingsGroupId.ConnectionAndData, SettingsCatalogRules.groupOf(SettingsItemId.ResetAllSettings))
        assertEquals(SettingsGroupId.About, SettingsCatalogRules.groupOf(SettingsItemId.About))
    }
}
