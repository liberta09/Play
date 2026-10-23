package com.kaan.play.data.addon

import com.kaan.play.data.provider.CatalogRow

/**
 * Nuvio addon kataloglarının görünürlük kuralları (Android'den bağımsız, birim testli).
 * DataStore anahtarı: `addonId:type:catalogId`
 * Varsayılan: tüm kataloglar AÇIKTIR (disabledKeys kümesinde olmayanlar açıktır).
 */
object CatalogVisibility {

    fun key(addonId: Long, type: String, catalogId: String): String = "$addonId:$type:$catalogId"

    fun key(addonId: String, type: String, catalogId: String): String = "$addonId:$type:$catalogId"

    /**
     * Varsayılan hepsi AÇIK (true).
     * Sadece disabledKeys içinde yer alan kataloglar kapalı (false) döner.
     */
    fun isVisible(addonId: Long, type: String, catalogId: String, disabledKeys: Set<String>): Boolean =
        key(addonId, type, catalogId) !in disabledKeys

    fun isVisible(addonId: String, type: String, catalogId: String, disabledKeys: Set<String>): Boolean =
        key(addonId, type, catalogId) !in disabledKeys

    fun isVisible(key: String, disabledKeys: Set<String>): Boolean =
        key !in disabledKeys

    /**
     * Aç/kapat durumuna göre devre dışı anahtar kümesini günceller.
     * enabled = true -> disabledKeys içinden çıkarılır.
     * enabled = false -> disabledKeys içine eklenir.
     */
    fun updateDisabled(currentDisabled: Set<String>, key: String, enabled: Boolean): Set<String> =
        if (enabled) currentDisabled - key else currentDisabled + key

    /**
     * Addon silindiğinde o addon'a ait tüm anahtarları temizler.
     */
    fun cleanForAddon(currentDisabled: Set<String>, addonId: Long): Set<String> {
        val prefix = "$addonId:"
        return currentDisabled.filterNot { it.startsWith(prefix) }.toSet()
    }

    fun cleanForAddon(currentDisabled: Set<String>, addonId: String): Set<String> {
        val prefix = "$addonId:"
        return currentDisabled.filterNot { it.startsWith(prefix) }.toSet()
    }

    /**
     * Katalog satırları listesini görünürlük kurallarına göre filtreler.
     * Nuvio addon satırları için providerId "nuvio_addon:{addonId}" ve row.id "{type}:{catalogId}" formatındadır.
     */
    fun filterRows(rows: List<CatalogRow>, disabledKeys: Set<String>): List<CatalogRow> {
        if (disabledKeys.isEmpty()) return rows
        return rows.filter { row ->
            if (row.providerId.startsWith("nuvio_addon:")) {
                val addonId = row.providerId.removePrefix("nuvio_addon:")
                val colonIdx = row.id.indexOf(':')
                if (colonIdx > 0) {
                    val type = row.id.substring(0, colonIdx)
                    val catId = row.id.substring(colonIdx + 1)
                    val k = key(addonId, type, catId)
                    k !in disabledKeys
                } else {
                    true
                }
            } else {
                true
            }
        }
    }
}
