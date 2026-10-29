package com.kaan.play.data.sync

import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.db.PluginDao
import com.kaan.play.data.plugin.SourceInstaller
import com.kaan.play.data.plugin.SourceType
import kotlinx.coroutines.flow.first

/** Telefon-TV arasında üç kaynak türünü de yeniden kurup durumları eşitleyen aktarım kapısı. */
class LocalSourceTransfer(
    private val addons: AddonDao,
    private val plugins: PluginDao,
    private val installer: SourceInstaller,
) {
    suspend fun exportTransfer(): List<SourceTransferItem> = buildList {
        AddonType.entries
            .filter { it == AddonType.NUVIO_ADDON || it == AddonType.NUVIO_PLUGIN_REPO || it == AddonType.CLOUDSTREAM_REPO }
            .forEach { type ->
                addons.observeOfType(type).first().forEach { source ->
                    add(
                        SourceTransferItem(
                            type = type.name,
                            sourceUrl = source.sourceUrl,
                            enabled = source.enabled,
                            plugins = plugins.ofRepo(source.id).map { PluginStateTransfer(it.pluginId, it.enabled) },
                        ),
                    )
                }
            }
    }

    suspend fun importTransfer(items: List<SourceTransferItem>): List<String> {
        val warnings = mutableListOf<String>()
        items.forEach { item ->
            val type = runCatching { AddonType.valueOf(item.type) }.getOrNull()
            if (type == null) {
                warnings += "Bilinmeyen eklenti türü: ${item.type}"
                return@forEach
            }
            val expected = when (type) {
                AddonType.NUVIO_ADDON -> SourceType.NUVIO_REPO
                AddonType.NUVIO_PLUGIN_REPO -> SourceType.NUVIO_PLUGIN
                AddonType.CLOUDSTREAM_REPO -> SourceType.CLOUDSTREAM
                else -> return@forEach
            }
            val existing = findBySourceUrl(item.sourceUrl)
            if (existing == null) {
                runCatching { installer.add(item.sourceUrl, expected) }
                    .onFailure { warnings += "${item.sourceUrl}: ${it.message ?: "eklenti deposu eklenemedi"}" }
            }
            val row = findBySourceUrl(item.sourceUrl)
            if (row == null) {
                if (existing != null) warnings += "${item.sourceUrl}: eklenti deposu bulunamadı"
                return@forEach
            }
            addons.setEnabled(row.id, item.enabled)
            val installed = plugins.ofRepo(row.id).associateBy { it.pluginId }
            item.plugins.forEach { state ->
                val plugin = installed[state.pluginId]
                if (plugin == null) warnings += "${item.sourceUrl}: ${state.pluginId} eklentisi kurulamadı"
                else plugins.setEnabled(plugin.id, state.enabled)
            }
        }
        return warnings
    }

    private suspend fun findBySourceUrl(url: String): AddonEntity? {
        for (type in AddonType.entries) {
            if (type == AddonType.WEB_SITE) continue
            addons.observeOfType(type).first().firstOrNull { it.sourceUrl == url }?.let { return it }
        }
        return null
    }
}
