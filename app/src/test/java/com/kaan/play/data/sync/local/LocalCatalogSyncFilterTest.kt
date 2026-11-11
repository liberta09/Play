package com.kaan.play.data.sync.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCatalogSyncFilterTest {

    @Test
    fun testIsSyncableSource_filtersOutLocalCatalog() {
        // Normal addon ve depolar senkronize edilir
        assertTrue(LocalSyncFilterRules.isSyncableSource("https://example.com/manifest.json", "org.example.addon"))
        assertTrue(LocalSyncFilterRules.isSyncableSource("https://cs.example/repo.json", null))

        // local-catalog: önekli kayıtlar senkronizasyondan süzülür
        assertFalse(LocalSyncFilterRules.isSyncableSource("local-catalog:1234-uuid", "local-catalog:1234-uuid"))
        assertFalse(LocalSyncFilterRules.isSyncableSource("local-catalog:5678", "normal-addon"))
        assertFalse(LocalSyncFilterRules.isSyncableSource("https://example.com/manifest.json", "local-catalog:9999"))
    }
}
