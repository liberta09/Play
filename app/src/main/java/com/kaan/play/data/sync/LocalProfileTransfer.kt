package com.kaan.play.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class PluginStateTransfer(
    val pluginId: String,
    val enabled: Boolean,
)

@Serializable
data class SourceTransferItem(
    val type: String,
    val sourceUrl: String,
    val enabled: Boolean = true,
    val plugins: List<PluginStateTransfer> = emptyList(),
)

@Serializable
data class LocalProfileTransfer(
    val profiles: List<LocalProfileSummary> = emptyList(),
    val backups: Map<String, String> = emptyMap(), // profileId -> JSON of LocalProfileBackup
    val sources: List<SourceTransferItem> = emptyList(),
    val warnings: List<String> = emptyList(),
)
