package com.kaan.play.data.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PluginStateTransfer(
    val pluginId: String,
    val enabled: Boolean,
    /** Eklentinin `hasSettings` değerleri (Wi-Fi senkronizasyonu). */
    val settings: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class SourceTransferItem(
    val type: String,
    val sourceUrl: String,
    val enabled: Boolean = true,
    val plugins: List<PluginStateTransfer> = emptyList(),
    /** Görünen ad ("Şunlar eklensin mi?" listesi ve web siteleri için). */
    val name: String = "",
    /** Addon'un kapalı katalogları: `tür:katalogKimliği` (satır kimliği cihaza özel olduğu için yazılmaz). */
    val disabledCatalogs: List<String> = emptyList(),
)

@Serializable
data class LocalProfileTransfer(
    val profiles: List<LocalProfileSummary> = emptyList(),
    val backups: Map<String, String> = emptyMap(), // profileId -> JSON of LocalProfileBackup
    val sources: List<SourceTransferItem> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** Gönderen cihazın adı (Wi-Fi senkronizasyonu). */
    val deviceName: String = "",
)
