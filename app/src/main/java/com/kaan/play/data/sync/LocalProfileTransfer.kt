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
    val enabled: Boolean,
    val plugins: List<PluginStateTransfer> = emptyList(),
)

@Serializable
data class LocalProfileTransfer(
    val profiles: List<LocalProfileSummary>,
    val backups: Map<String, String>,
    /** Yeni aktarım formatı: Nuvio addon + Nuvio JS + CloudStream depoları. */
    val sources: List<SourceTransferItem> = emptyList(),
    /** Karşı cihazda içe aktarılamayan eklenti depoları için tanılama mesajları. */
    val warnings: List<String> = emptyList(),
)
