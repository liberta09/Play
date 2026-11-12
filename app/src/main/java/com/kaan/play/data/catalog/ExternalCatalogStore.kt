package com.kaan.play.data.catalog

import com.kaan.play.data.addon.AddonManifest
import com.kaan.play.data.addon.ManifestCatalog
import com.kaan.play.data.addon.ManifestResource
import com.kaan.play.data.db.AddonDao
import com.kaan.play.data.db.AddonEntity
import com.kaan.play.data.db.AddonType
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ExternalCatalogStore(
    private val filesDir: File,
    private val addonDao: AddonDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val catalogDir: File
        get() = File(filesDir, "external-catalogs").apply { if (!exists()) mkdirs() }

    fun getCatalogFile(uuid: String): File = File(catalogDir, "$uuid.json")

    @Throws(ExternalCatalogException::class)
    suspend fun importCatalog(rawJson: String, byteSize: Long = rawJson.toByteArray(Charsets.UTF_8).size.toLong()): AddonEntity = withContext(Dispatchers.IO) {
        val parsed = ExternalCatalogParser.parse(rawJson, byteSize)
        val uuid = UUID.randomUUID().toString()
        val file = getCatalogFile(uuid)
        file.writeText(rawJson, Charsets.UTF_8)

        val manifest = AddonManifest(
            id = "local-catalog:$uuid",
            name = parsed.name,
            version = "1.0.0",
            description = null,
            catalogs = listOf(
                ManifestCatalog(
                    type = "movie",
                    id = "local-catalog-$uuid",
                    name = parsed.name,
                ),
            ),
            resources = listOf(ManifestResource("catalog")),
            types = listOf("movie", "series"),
        )

        val entity = AddonEntity(
            type = AddonType.NUVIO_ADDON,
            sourceUrl = "local-catalog:$uuid",
            addonId = "local-catalog:$uuid",
            name = parsed.name,
            version = "1.0.0",
            description = null,
            logoUrl = null,
            enabled = true,
            manifestJson = PlayJson.encodeToString(AddonManifest.serializer(), manifest),
            addedAt = clock(),
        )

        val rowId = addonDao.insert(entity)
        entity.copy(id = rowId)
    }

    suspend fun readCatalog(uuid: String): ExternalCatalog? = withContext(Dispatchers.IO) {
        val file = getCatalogFile(uuid)
        if (!file.exists()) return@withContext null
        try {
            val text = file.readText(Charsets.UTF_8)
            ExternalCatalogParser.parse(text)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun deleteCatalogFile(uuid: String): Boolean = withContext(Dispatchers.IO) {
        val file = getCatalogFile(uuid)
        if (file.exists()) file.delete() else true
    }

    suspend fun deleteBySourceUrl(sourceUrl: String): Boolean {
        if (!sourceUrl.startsWith(PREFIX)) return false
        val uuid = sourceUrl.removePrefix(PREFIX)
        return deleteCatalogFile(uuid)
    }

    companion object {
        const val PREFIX = "local-catalog:"
    }
}
