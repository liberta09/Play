package com.kaan.play.data.subtitle

import com.kaan.play.data.db.SubtitleDao
import com.kaan.play.data.db.SubtitleLanguageEntity
import com.kaan.play.data.db.SubtitleOffsetEntity

/** Room'un REPLACE / birincil anahtar davranışını taklit eden bellek içi altyazı DAO'su (gerçek SQLite sınaması DaoTest'te). */
class FakeSubtitleDao : SubtitleDao {
    val languages = LinkedHashMap<String, SubtitleLanguageEntity>()
    val offsets = LinkedHashMap<Pair<String, String>, SubtitleOffsetEntity>()
    var writes = 0
        private set

    override suspend fun language(contentKey: String): String? = languages[contentKey]?.language

    override suspend fun putLanguage(entity: SubtitleLanguageEntity) {
        writes++
        languages[entity.contentKey] = entity
    }

    override suspend fun offset(contentKey: String, subtitleKey: String): Long? = offsets[contentKey to subtitleKey]?.offsetMs

    override suspend fun putOffset(entity: SubtitleOffsetEntity) {
        writes++
        offsets[entity.contentKey to entity.subtitleKey] = entity
    }

    override suspend fun clearOffset(contentKey: String, subtitleKey: String) {
        writes++
        offsets.remove(contentKey to subtitleKey)
    }

    override suspend fun pruneOffsets(keep: Int) {
        val newest = offsets.values.sortedByDescending { it.updatedAt }.take(keep).map { it.contentKey to it.subtitleKey }.toSet()
        offsets.keys.retainAll(newest)
    }

    override suspend fun pruneLanguages(keep: Int) {
        val newest = languages.values.sortedByDescending { it.updatedAt }.take(keep).map { it.contentKey }.toSet()
        languages.keys.retainAll(newest)
    }

    override suspend fun clearOffsets() {
        writes++
        offsets.clear()
    }

    override suspend fun clearLanguages() {
        writes++
        languages.clear()
    }

    override suspend fun clearAll() {
        writes++
        offsets.clear()
        languages.clear()
    }

    override suspend fun deleteAll() {
        clearAll()
    }
}
