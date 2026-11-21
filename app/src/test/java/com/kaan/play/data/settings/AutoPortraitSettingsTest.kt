package com.kaan.play.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

private class PortraitMemoryStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = transform(state.value).also { state.value = it }
}
class AutoPortraitSettingsTest {
    @Test fun defaultsOnAndUserChoicePersistsWithoutChangingLandscapeResize() = runTest {
        val store = PortraitMemoryStore(); val settings = SettingsRepository(store)
        settings.setResizeMode(ResizeMode.Ratio16x9)
        assertTrue(settings.autoPortraitVideos.first())
        settings.setAutoPortraitVideos(false)
        assertFalse(SettingsRepository(store).autoPortraitVideos.first())
        assertEquals(ResizeMode.Ratio16x9, settings.resizeMode.first())
        settings.setAutoPortraitVideos(true)
        assertTrue(settings.autoPortraitVideos.first())
        assertEquals(ResizeMode.Ratio16x9, settings.resizeMode.first())
    }
}
