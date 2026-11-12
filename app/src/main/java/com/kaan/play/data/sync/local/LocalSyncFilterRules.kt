package com.kaan.play.data.sync.local

object LocalSyncFilterRules {
    const val LOCAL_CATALOG_PREFIX = "local-catalog:"

    fun isSyncableSource(sourceUrl: String, addonId: String? = null): Boolean {
        if (sourceUrl.startsWith(LOCAL_CATALOG_PREFIX)) return false
        if (addonId?.startsWith(LOCAL_CATALOG_PREFIX) == true) return false
        return true
    }
}
