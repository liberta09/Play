package com.kaan.play.data.collection

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Dışarıdan aktarılan koleksiyon dosyasındaki kaynak türü. Yalnızca TMDB kaynakları desteklenir. */
enum class CollectionSourceKind {
    List,
    Collection,
    Discover,
    Single,
}

@Serializable
data class CollectionSource(
    val kind: CollectionSourceKind,
    val mediaType: MediaType? = null,
    val tmdbId: Int? = null,
    val sortBy: String? = null,
    val filters: Map<String, String> = emptyMap(),
)

@Serializable
data class CollectionFolder(
    val id: String,
    val title: String,
    val sources: List<CollectionSource>,
)

@Serializable
data class ImportedCollection(
    val id: String,
    val title: String,
    val folders: List<CollectionFolder>,
    val importedAt: Long,
)

data class CollectionFolderShelf(
    val collectionId: String,
    val collectionTitle: String,
    val folder: CollectionFolder,
)

data class ParsedCollection(
    val title: String,
    val folders: List<CollectionFolder>,
    val unsupportedSourcesCount: Int,
)

data class CollectionImportResult(
    val collection: ImportedCollection?,
    val foldersAdded: Int,
    val unsupportedSourcesCount: Int,
)

sealed class CollectionImportException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidJson(cause: Throwable? = null) : CollectionImportException("Invalid collection JSON", cause)
    class FileTooLarge : CollectionImportException("Collection file exceeds max size")
    class StorageFull : CollectionImportException("Collection storage limit reached")
}

/**
 * Esnek JSON koleksiyon ayrıştırıcısı (Android'den bağımsız).
 * Kök: `title`/`name`, `folders[]` (her klasörde `title`/`name` + `sources[]`/`catalogSources[]`).
 * Yalnızca `provider == "tmdb"` için `list`, `collection`, `discover` (izinli filtrelerle) ve tekil `tmdbId` desteklenir.
 */
object CollectionJsonParser {
    const val MAX_IMPORT_BYTES = 512 * 1024
    const val MAX_STORED_BYTES = 256 * 1024
    const val MAX_FOLDERS = 50
    const val MAX_SOURCES_PER_FOLDER = 25

    val ALLOWED_DISCOVER_FILTERS: Set<String> = setOf(
        "with_genres",
        "with_original_language",
        "primary_release_year",
        "first_air_date_year",
        "vote_average.gte",
        "with_companies",
        "with_networks",
    )

    private val SAFE_SORT_BY = Regex("^[A-Za-z0-9_.]+$")

    fun parse(raw: String, fallbackTitle: String = ""): ParsedCollection {
        val root = try {
            PlayJson.parseToJsonElement(raw)
        } catch (e: SerializationException) {
            throw CollectionImportException.InvalidJson(e)
        } catch (e: IllegalArgumentException) {
            throw CollectionImportException.InvalidJson(e)
        }
        if (root !is JsonObject) throw CollectionImportException.InvalidJson()

        val foldersElement = root["folders"] as? JsonArray ?: throw CollectionImportException.InvalidJson()
        val rootTitle = root.firstText("title", "name")
            ?: fallbackTitle.trim()

        var unsupportedCount = 0
        val folders = mutableListOf<CollectionFolder>()

        for ((index, folderEl) in foldersElement.withIndex()) {
            if (folders.size >= MAX_FOLDERS) break
            val folderObj = folderEl as? JsonObject ?: continue
            val sourcesArray = (folderObj["sources"] ?: folderObj["catalogSources"]) as? JsonArray ?: continue
            val folderTitle = folderObj.firstText("title", "name")

            val validSources = mutableListOf<CollectionSource>()
            for (srcEl in sourcesArray) {
                if (validSources.size >= MAX_SOURCES_PER_FOLDER) {
                    unsupportedCount++
                    continue
                }
                val parsed = parseSource(srcEl)
                if (parsed != null) {
                    validSources += parsed
                } else {
                    unsupportedCount++
                }
            }

            if (!folderTitle.isNullOrBlank() && validSources.isNotEmpty()) {
                folders += CollectionFolder(
                    id = "f_$index",
                    title = folderTitle,
                    sources = validSources,
                )
            }
        }

        return ParsedCollection(
            title = rootTitle,
            folders = folders,
            unsupportedSourcesCount = unsupportedCount,
        )
    }

    private fun parseSource(element: JsonElement): CollectionSource? {
        val obj = element as? JsonObject ?: return null
        val provider = obj["provider"].textOrNull()?.lowercase() ?: return null
        if (provider != "tmdb") return null

        val hasRawType = obj.containsKey("mediaType") && obj["mediaType"] !is JsonNull ||
            obj.containsKey("type") && obj["type"] !is JsonNull
        val rawMediaType = obj.firstText("mediaType", "type")?.lowercase()
        val mediaType = when (rawMediaType) {
            null -> if (hasRawType) return null else null
            "movie" -> MediaType.Movie
            "tv", "series" -> MediaType.Tv
            else -> return null
        }

        val sourceType = obj["tmdbSourceType"].textOrNull()?.lowercase()
        val tmdbId = obj["tmdbId"].positiveIntOrNull()

        return when (sourceType) {
            "list" -> {
                if (tmdbId == null) return null
                CollectionSource(
                    kind = CollectionSourceKind.List,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                )
            }
            "collection" -> {
                if (tmdbId == null) return null
                CollectionSource(
                    kind = CollectionSourceKind.Collection,
                    mediaType = mediaType ?: MediaType.Movie,
                    tmdbId = tmdbId,
                )
            }
            "discover" -> {
                val targetType = mediaType ?: return null
                val sortBy = obj["sortBy"].textOrNull()?.takeIf { SAFE_SORT_BY.matches(it) }
                val filters = parseFilters(obj["filters"] as? JsonObject)
                CollectionSource(
                    kind = CollectionSourceKind.Discover,
                    mediaType = targetType,
                    sortBy = sortBy,
                    filters = filters,
                )
            }
            null, "single", "item" -> {
                if (tmdbId == null || mediaType == null) return null
                CollectionSource(
                    kind = CollectionSourceKind.Single,
                    mediaType = mediaType,
                    tmdbId = tmdbId,
                )
            }
            else -> null
        }
    }

    private fun parseFilters(filtersObj: JsonObject?): Map<String, String> {
        if (filtersObj == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        for ((key, value) in filtersObj) {
            if (key !in ALLOWED_DISCOVER_FILTERS) continue
            val text = value.scalarTextOrNull() ?: continue
            result[key] = text
        }
        return result
    }

    private fun JsonObject.firstText(vararg keys: String): String? {
        for (key in keys) {
            val found = this[key].textOrNull()
            if (found != null) return found
        }
        return null
    }

    private fun JsonElement?.textOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (!primitive.isString) return null
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }

    private fun JsonElement?.scalarTextOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }

    private fun JsonElement?.positiveIntOrNull(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        val n = primitive.content.trim().toIntOrNull() ?: return null
        return n.takeIf { it > 0 }
    }
}
