package com.kaan.play.data.stream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSyncReportStoreTest {

    private class MemoryPreferencesStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun incrementsPerPluginWithoutResettingOtherPlugins() = runBlocking {
        val store = AudioSyncReportStore(MemoryPreferencesStore())
        val a = StreamSource(5L, "AddonA")
        val b = StreamSource(0L, "CustomPlugin")

        assertEquals(1, store.increment(a))
        assertEquals(2, store.increment(a))
        assertEquals(1, store.increment(b))
        assertEquals(2, store.countFor(a))
        assertEquals(1, store.countFor(b))
    }
}
