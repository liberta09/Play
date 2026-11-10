package com.kaan.play.data.collection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * İçe aktarılan JSON koleksiyonlarını DataStore'da saklar (en fazla [CollectionJsonParser.MAX_STORED_BYTES]).
 * Android'den bağımsızdır.
 */
class CollectionRepository(
    private val store: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    val collections: Flow<List<ImportedCollection>> = store.data.map { prefs ->
        decodeList(prefs[IMPORTED_COLLECTIONS])
    }.distinctUntilChanged()

    val folderShelves: Flow<List<CollectionFolderShelf>> = collections.map { list ->
        list.flatMap { collection ->
            collection.folders.map { folder ->
                CollectionFolderShelf(
                    collectionId = collection.id,
                    collectionTitle = collection.title,
                    folder = folder,
                )
            }
        }
    }

    suspend fun importBytes(bytes: ByteArray, fallbackTitle: String = ""): CollectionImportResult {
        if (bytes.size > CollectionJsonParser.MAX_IMPORT_BYTES) {
            throw CollectionImportException.FileTooLarge()
        }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            throw CollectionImportException.InvalidJson(e)
        }
        return importJson(text, fallbackTitle)
    }

    suspend fun importJson(raw: String, fallbackTitle: String = ""): CollectionImportResult {
        if (raw.toByteArray(StandardCharsets.UTF_8).size > CollectionJsonParser.MAX_IMPORT_BYTES) {
            throw CollectionImportException.FileTooLarge()
        }
        val parsed = CollectionJsonParser.parse(raw, fallbackTitle)
        if (parsed.folders.isEmpty()) {
            return CollectionImportResult(
                collection = null,
                foldersAdded = 0,
                unsupportedSourcesCount = parsed.unsupportedSourcesCount,
            )
        }

        val item = ImportedCollection(
            id = idGenerator(),
            title = parsed.title.ifBlank { fallbackTitle.trim() },
            folders = parsed.folders,
            importedAt = clock(),
        )

        store.edit { prefs ->
            val current = decodeList(prefs[IMPORTED_COLLECTIONS])
            val updated = current + item
            val encoded = PlayJson.encodeToString(LIST_SERIALIZER, updated)
            if (encoded.toByteArray(StandardCharsets.UTF_8).size > CollectionJsonParser.MAX_STORED_BYTES) {
                throw CollectionImportException.StorageFull()
            }
            prefs[IMPORTED_COLLECTIONS] = encoded
        }

        return CollectionImportResult(
            collection = item,
            foldersAdded = parsed.folders.size,
            unsupportedSourcesCount = parsed.unsupportedSourcesCount,
        )
    }

    suspend fun removeCollection(id: String) {
        store.edit { prefs ->
            val updated = decodeList(prefs[IMPORTED_COLLECTIONS]).filterNot { it.id == id }
            if (updated.isEmpty()) {
                prefs.remove(IMPORTED_COLLECTIONS)
            } else {
                prefs[IMPORTED_COLLECTIONS] = PlayJson.encodeToString(LIST_SERIALIZER, updated)
            }
        }
    }

    private fun decodeList(raw: String?): List<ImportedCollection> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { PlayJson.decodeFromString(LIST_SERIALIZER, raw) }.getOrDefault(emptyList())
    }

    companion object {
        private val IMPORTED_COLLECTIONS = stringPreferencesKey("imported_collections")
        private val LIST_SERIALIZER = ListSerializer(ImportedCollection.serializer())
    }
}
