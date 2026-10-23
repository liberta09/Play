package com.kaan.play.data.addon

import com.kaan.play.data.provider.CatalogItem
import com.kaan.play.data.provider.CatalogRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogVisibilityTest {

    @Test
    fun defaultAllCatalogsAreVisible() {
        val emptyDisabled = emptySet<String>()
        assertTrue(CatalogVisibility.isVisible(1L, "tv", "wiospor", emptyDisabled))
        assertTrue(CatalogVisibility.isVisible("1", "movie", "top", emptyDisabled))
        assertTrue(CatalogVisibility.isVisible("1:series:top", emptyDisabled))
    }

    @Test
    fun disabledCatalogIsNotVisible() {
        val key = CatalogVisibility.key(1L, "tv", "wiospor")
        assertEquals("1:tv:wiospor", key)
        val disabled = setOf(key)
        assertFalse(CatalogVisibility.isVisible(1L, "tv", "wiospor", disabled))
        assertFalse(CatalogVisibility.isVisible("1", "tv", "wiospor", disabled))
        // Diğer kataloglar hâlâ açık
        assertTrue(CatalogVisibility.isVisible(1L, "tv", "other", disabled))
        assertTrue(CatalogVisibility.isVisible(2L, "tv", "wiospor", disabled))
    }

    @Test
    fun updateDisabledTogglesProperly() {
        var disabled = emptySet<String>()
        val key = CatalogVisibility.key(5L, "movie", "pop")

        // Kapat (enabled = false)
        disabled = CatalogVisibility.updateDisabled(disabled, key, false)
        assertEquals(setOf(key), disabled)
        assertFalse(CatalogVisibility.isVisible(key, disabled))

        // Aç (enabled = true)
        disabled = CatalogVisibility.updateDisabled(disabled, key, true)
        assertTrue(disabled.isEmpty())
        assertTrue(CatalogVisibility.isVisible(key, disabled))
    }

    @Test
    fun cleanForAddonRemovesOnlyTargetAddonKeys() {
        val disabled = setOf(
            "1:tv:ch1",
            "1:movie:m1",
            "2:tv:ch1",
            "10:series:s1",
        )
        val cleaned = CatalogVisibility.cleanForAddon(disabled, 1L)
        assertEquals(setOf("2:tv:ch1", "10:series:s1"), cleaned)
    }

    @Test
    fun filterRowsHidesDisabledNuvioCatalogsAndPreservesOthers() {
        val disabled = setOf("1:tv:hidden")
        val r1 = CatalogRow(id = "tv:hidden", title = "Hidden Channel", items = emptyList(), providerId = "nuvio_addon:1", providerName = "Addon1")
        val r2 = CatalogRow(id = "tv:visible", title = "Visible Channel", items = emptyList(), providerId = "nuvio_addon:1", providerName = "Addon1")
        val r3 = CatalogRow(id = "cs:all", title = "CS Catalog", items = emptyList(), providerId = "cs:42", providerName = "CS")

        val filtered = CatalogVisibility.filterRows(listOf(r1, r2, r3), disabled)
        assertEquals(listOf(r2, r3), filtered)
    }
}
