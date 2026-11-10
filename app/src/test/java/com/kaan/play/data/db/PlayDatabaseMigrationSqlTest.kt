package com.kaan.play.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

class PlayDatabaseMigrationSqlTest {

    private fun schemaFile(version: Int): File {
        val rel = "schemas/com.kaan.play.data.db.PlayDatabase/$version.json"
        return listOf(File(rel), File("app/$rel")).firstOrNull { it.isFile }
            ?: error("Schema dosyası bulunamadı: $rel")
    }

    @Test
    fun version7PreservesAllVersion6EntitiesAndMigrationMatchesSchema() {
        val v6 = Json.parseToJsonElement(schemaFile(6).readText()).jsonObject["database"]!!.jsonObject
        val v7 = Json.parseToJsonElement(schemaFile(7).readText()).jsonObject["database"]!!.jsonObject

        val v6Entities = v6["entities"]!!.jsonArray.associateBy { it.jsonObject["tableName"]!!.jsonPrimitive.content }
        val v7Entities = v7["entities"]!!.jsonArray.associateBy { it.jsonObject["tableName"]!!.jsonPrimitive.content }

        for ((tableName, entity) in v6Entities) {
            assertEquals("v6 tablosu v7'de değişmemeli: $tableName", entity, v7Entities[tableName])
        }

        val expectedActivitySql = v7Entities["live_channel_activity"]!!.jsonObject["createSql"]!!
            .jsonPrimitive.content.replace("\${TABLE_NAME}", "live_channel_activity")
        val expectedOrderSql = v7Entities["live_channel_order"]!!.jsonObject["createSql"]!!
            .jsonPrimitive.content.replace("\${TABLE_NAME}", "live_channel_order")

        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    executedSql += args[0] as String
                    null
                }
                else -> error("Beklenmeyen SupportSQLiteDatabase çağrısı: ${method.name}")
            }
        } as SupportSQLiteDatabase

        assertEquals(6, PlayDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, PlayDatabase.MIGRATION_6_7.endVersion)
        PlayDatabase.MIGRATION_6_7.migrate(db)

        assertEquals(listOf(expectedActivitySql, expectedOrderSql), executedSql)
        for (sql in executedSql) {
            val upper = sql.uppercase()
            assertFalse("Veri silen göç SQL'i yasak: $sql", upper.contains("DROP "))
            assertFalse("Veri silen göç SQL'i yasak: $sql", upper.contains("DELETE "))
            assertFalse("Veri silen göç SQL'i yasak: $sql", upper.contains("TRUNCATE "))
            assertTrue(upper.startsWith("CREATE TABLE IF NOT EXISTS"))
        }
    }

    @Test
    fun version8PreservesAllVersion7EntitiesAndMigration7To8MatchesSchema() {
        val v7 = Json.parseToJsonElement(schemaFile(7).readText()).jsonObject["database"]!!.jsonObject
        val v8 = Json.parseToJsonElement(schemaFile(8).readText()).jsonObject["database"]!!.jsonObject

        val v7Entities = v7["entities"]!!.jsonArray.associateBy { it.jsonObject["tableName"]!!.jsonPrimitive.content }
        val v8Entities = v8["entities"]!!.jsonArray.associateBy { it.jsonObject["tableName"]!!.jsonPrimitive.content }

        for ((tableName, entity) in v7Entities) {
            assertEquals("v7 tablosu v8'de değişmemeli: $tableName", entity, v8Entities[tableName])
        }

        val expectedCatalogSql = v8Entities["live_catalog"]!!.jsonObject["createSql"]!!
            .jsonPrimitive.content.replace("\${TABLE_NAME}", "live_catalog")
        val catalogChannelObj = v8Entities["live_catalog_channel"]!!.jsonObject
        val expectedChannelSql = catalogChannelObj["createSql"]!!
            .jsonPrimitive.content.replace("\${TABLE_NAME}", "live_catalog_channel")
        val expectedIndexSql = catalogChannelObj["indices"]!!.jsonArray.single().jsonObject["createSql"]!!
            .jsonPrimitive.content.replace("\${TABLE_NAME}", "live_catalog_channel")

        val executedSql = mutableListOf<String>()
        val db = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            when (method.name) {
                "execSQL" -> {
                    executedSql += args[0] as String
                    null
                }
                else -> error("Beklenmeyen SupportSQLiteDatabase çağrısı: ${method.name}")
            }
        } as SupportSQLiteDatabase

        assertEquals(7, PlayDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, PlayDatabase.MIGRATION_7_8.endVersion)
        PlayDatabase.MIGRATION_7_8.migrate(db)

        assertEquals(listOf(expectedCatalogSql, expectedChannelSql, expectedIndexSql), executedSql)
        for (sql in executedSql) {
            val upper = sql.uppercase()
            assertFalse("Veri silen göç SQL'i yasak: $sql", upper.contains("DROP "))
            assertFalse("Veri silen göç SQL'i yasak: $sql", upper.contains("DELETE FROM"))
            assertFalse("Veri silen göç SQL'i yasak: $sql", upper.contains("TRUNCATE "))
            assertTrue(
                upper.startsWith("CREATE TABLE IF NOT EXISTS") || upper.startsWith("CREATE INDEX IF NOT EXISTS"),
            )
        }
    }
}
