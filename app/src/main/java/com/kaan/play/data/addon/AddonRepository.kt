package com.kaan.play.data.addon

import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

data class Addon(
    val id: Long,
    val type: AddonType,
    val sourceUrl: String,
    val manifest: AddonManifest,
    val enabled: Boolean,
    val addedAt: Long,
) {
    /** Addon uç noktaları bunun altındadır: `{baseUrl}/stream/movie/tt123.json`. */
    val baseUrl: String get() = AddonUrl.baseOf(sourceUrl)
}

/** Addon yönetiminin tek kapısı. Faz 1'de yalnızca [AddonType.NUVIO_ADDON] eklenebilir. */
class AddonRepository(
    private val dao: AddonDao,
    private val fetcher: ManifestFetcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onAddonRemoved: (suspend (Long) -> Unit)? = null,
) {

    /** Yalnızca Nuvio addon satırları: diğer türlerin (Faz 2/3) depo kayıtları farklı biçimdedir. */
    val addons: Flow<List<Addon>> = dao.observeOfType(AddonType.NUVIO_ADDON).map { rows -> rows.map { it.toAddon() } }

    /** Kaynak toplama için: aktif Nuvio addon'ları (eklenme sırasıyla). */
    suspend fun enabledAddons(): List<Addon> =
        dao.enabledOfType(AddonType.NUVIO_ADDON).map { it.toAddon() }

    /**
     * Girilen bağlantıyı normalleştirir, manifest'i indirip doğrular ve kaydeder.
     * Hata durumunda hiçbir şey kaydedilmez.
     * @throws AddonException
     */
    suspend fun add(input: String, type: AddonType = AddonType.NUVIO_ADDON): Addon {
        if (type != AddonType.NUVIO_ADDON) throw AddonException(Kind.UnsupportedKind)

        val url = AddonUrl.normalize(input)
        if (isKnown(url)) throw AddonException(Kind.AlreadyAdded)
        return addFetched(url, fetcher.fetch(url))
    }

    /** Bu adres (her türde) zaten kayıtlı mı? */
    suspend fun isKnown(url: String): Boolean = dao.countBySourceUrl(url) > 0

    /**
     * Zaten indirilmiş [raw] manifest'i addon olarak doğrular ve kaydeder (içerik türü ayrımı yukarıda yapılmıştır).
     * @throws AddonException
     */
    suspend fun addFetched(url: String, raw: String, type: AddonType = AddonType.NUVIO_ADDON): Addon {
        if (dao.countBySourceUrl(url) > 0) throw AddonException(Kind.AlreadyAdded)
        val manifest = AddonManifestParser.parse(raw)

        val entity = AddonEntity(
            type = type,
            sourceUrl = url,
            addonId = manifest.id,
            name = manifest.name,
            version = manifest.version,
            description = manifest.description,
            logoUrl = manifest.logo,
            enabled = true,
            manifestJson = PlayJson.encodeToString(AddonManifest.serializer(), manifest),
            addedAt = clock(),
        )
        val rowId = dao.insert(entity)
        // İndirme sürerken aynı adres başka yoldan eklenmişse IGNORE -1 döner.
        if (rowId == -1L) throw AddonException(Kind.AlreadyAdded)
        return entity.copy(id = rowId).toAddon()
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
    suspend fun setAllEnabled(enabled: Boolean) = dao.setAllEnabledOfType(AddonType.NUVIO_ADDON, enabled)

    suspend fun remove(id: Long) {
        dao.delete(id)
        onAddonRemoved?.invoke(id)
    }

    suspend fun byId(id: Long): Addon? = dao.byId(id)?.toAddon()

    suspend fun exportTransfer(): List<AddonTransferItem> = addons.first().map { AddonTransferItem(it.sourceUrl, it.enabled) }

    suspend fun importTransfer(items: List<AddonTransferItem>) {
        items.forEach { item ->
            val existing = addons.first().firstOrNull { it.sourceUrl == item.sourceUrl }
            val addon = existing ?: runCatching { add(item.sourceUrl) }.getOrNull()
            addon?.let { setEnabled(it.id, item.enabled) }
        }
    }

    private fun AddonEntity.toAddon() = Addon(
        id = id,
        type = type,
        sourceUrl = sourceUrl,
        manifest = PlayJson.decodeFromString(AddonManifest.serializer(), manifestJson),
        enabled = enabled,
        addedAt = addedAt,
    )
}
