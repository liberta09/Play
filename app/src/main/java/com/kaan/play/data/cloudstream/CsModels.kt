package com.kaan.play.data.cloudstream

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * CloudStream depo kök dosyası (repo.json).
 * [pluginLists]: `plugins.json` adreslerinin listesi; her biri [CsPluginList] döner.
 */
@Serializable
data class CsRepoRoot(
    val name: String,
    val description: String? = null,
    val manifestVersion: Int = 1,
    @SerialName("pluginLists")
    val pluginLists: List<String>,
    val subRepositories: List<CsRepoReference> = emptyList(),
)

@Serializable
data class CsRepoReference(
    val url: String,
    val name: String = "",
    val description: String? = null,
    val discoveredFrom: List<String> = emptyList(),
)

/**
 * Tek bir `plugins.json` içindeki eklenti listesi.
 * CS deposu `plugins.json` üst düzey alanı altında [SitePlugin] dizisi tutar.
 */
@Serializable
data class CsPluginList(
    val plugins: List<SitePlugin> = emptyList(),
)

/**
 * Bir CS eklentisinin (`.cs3`) üst bilgisi. [internalName], DEX içinde yüklenecek sınıfın adıdır.
 * [url]: `.cs3` dosyasının adresi (mutlak ya da göreli). [fileHash]: SHA-256 hex özeti.
 */
@Serializable
data class SitePlugin(
    val name: String,
    val internalName: String,
    val url: String,
    val version: Int = 1,
    val apiVersion: Int = 1,
    val status: Int = 1,
    val fileHash: String = "",
    val fileSize: Long = 0,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val repositoryUrl: String? = null,
    val iconUrl: String? = null,
    val language: String = "en",
    val tvTypes: List<String> = listOf("Movie", "TvSeries"),
)

/** Play'in iç temsili: repo manifest → ayrıştırılmış ve çözünmüş eklenti listesi. */
data class CsRepoManifest(
    val name: String,
    val description: String? = null,
    val plugins: List<CsPluginEntry>,
)

data class CsPluginEntry(
    val internalName: String,
    val name: String,
    /** İndirme adresi (mutlak). */
    val url: String,
    val version: Int,
    val apiVersion: Int,
    val fileHash: String,
    val fileSize: Long,
    val authors: List<String>,
    val description: String? = null,
    val language: String,
    val tvTypes: List<String>,
)

// ---------------------------------------------------------------------------
// Ayrıştırıcı
// ---------------------------------------------------------------------------

object CsRepoManifestParser {

    const val MAX_PLUGINS = 200

    /**
     * Kullanıcının girdiği adreste `repo.json` beklenir; ayrıştırılıp [CsRepoManifest] döner.
     * Her `pluginLists` adresi indirilip birleştirilir (paralel değil; hata tek tek atlanır).
     * @throws AddonException
     */
    fun parseRoot(raw: String, baseUrl: String? = null): CsRepoRoot {
        val root = try {
            PlayJson.decodeFromString<JsonElement>(raw)
        } catch (e: SerializationException) {
            throw AddonException(Kind.NotJson, cause = e)
        } catch (e: IllegalArgumentException) {
            throw AddonException(Kind.NotJson, cause = e)
        }
        if (root !is JsonObject) throw AddonException(Kind.NotJson)

        // pluginLists dizisi zorunludur
        val listsEl = root["pluginLists"] as? JsonArray ?: throw AddonException(Kind.MissingField, "pluginLists")
        val base = baseUrl?.toHttpUrlOrNull()
        val pluginLists = listsEl.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim() }
            .mapNotNull { resolveUrl(it, base) }
        if (pluginLists.isEmpty()) throw AddonException(Kind.NoPlugins)

        val name = (root["name"] as? JsonPrimitive)?.content?.trim() ?: "CloudStream Repo"
        val description = (root["description"] as? JsonPrimitive)?.content?.trim()
        val subRepositories = listOf("subRepositories", "repositories", "repos", "repoLists")
            .flatMap { key -> parseRepoReferences(root[key], base) }
            .distinctBy { it.url }
            .map { it.copy(discoveredFrom = listOfNotNull(baseUrl)) }

        return CsRepoRoot(name = name, description = description, pluginLists = pluginLists, subRepositories = subRepositories)
    }

    /** MegaRepo repos-db.json biçimi: URL dizisi veya `{url, verified}` nesneleri. */
    fun parseRepoIndex(raw: String, baseUrl: String? = null): List<CsRepoReference> {
        val parsed = runCatching { PlayJson.decodeFromString<JsonElement>(raw) }.getOrNull() as? JsonArray
            ?: return emptyList()
        val base = baseUrl?.toHttpUrlOrNull()
        return parsed.mapNotNull { item ->
            val (rawUrl, name, description) = when (item) {
                is JsonPrimitive -> if (item.isString) Triple(item.content.trim(), "", null) else return@mapNotNull null
                is JsonObject -> Triple(
                    item.str("url") ?: item.str("repoUrl") ?: item.str("repositoryUrl") ?: return@mapNotNull null,
                    item.str("name").orEmpty(),
                    item.str("description"),
                )
                else -> return@mapNotNull null
            }
            resolveUrl(rawUrl, base)?.let { CsRepoReference(it, name, description) }
        }.distinctBy { it.url }
    }

    private fun parseRepoReferences(element: JsonElement?, base: okhttp3.HttpUrl?): List<CsRepoReference> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val (rawUrl, name, description) = when (item) {
                is JsonPrimitive -> if (item.isString) Triple(item.content.trim(), "", null) else return@mapNotNull null
                is JsonObject -> Triple(
                    item.str("url") ?: item.str("repoUrl") ?: item.str("repositoryUrl") ?: return@mapNotNull null,
                    item.str("name").orEmpty(),
                    item.str("description"),
                )
                else -> return@mapNotNull null
            }
            resolveUrl(rawUrl, base)?.let { CsRepoReference(it, name, description) }
        }
    }

    /** `plugins.json` içeriğini eklenti listesine dönüştürür; bozuk girişler atlanır. */
    fun parsePluginList(raw: String, baseUrl: String): List<CsPluginEntry> {
        val el = try { PlayJson.decodeFromString<JsonElement>(raw) } catch (_: Exception) { return emptyList() }

        // İki biçim desteklenir:
        // 1. {"plugins":[...]}  (resmi CS biçimi)
        // 2. [{...}, ...]       (bazı depolar düz dizi kullanır)
        val arr: JsonArray? = when {
            el is JsonObject -> el["plugins"] as? JsonArray
            el is JsonArray -> el
            else -> null
        }
        if (arr == null) return emptyList()

        val base = baseUrl.toHttpUrlOrNull()
        val seen = HashSet<String>()
        return arr.asSequence()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { obj -> entryFrom(obj, base) }
            .filter { seen.add(it.internalName) }
            .take(MAX_PLUGINS)
            .toList()
    }

    private fun entryFrom(obj: JsonObject, base: okhttp3.HttpUrl?): CsPluginEntry? {
        val name = obj.str("name") ?: return null
        val internalName = obj.str("internalName") ?: return null
        val rawUrl = obj.str("url") ?: return null
        val url = resolveUrl(rawUrl, base) ?: return null

        return CsPluginEntry(
            name = name,
            internalName = internalName,
            url = url,
            version = obj.int("version") ?: 1,
            apiVersion = obj.int("apiVersion") ?: 1,
            fileHash = obj.str("fileHash") ?: "",
            fileSize = obj.long("fileSize") ?: 0L,
            authors = obj.strList("authors"),
            description = obj.str("description"),
            language = obj.str("language") ?: "en",
            tvTypes = obj.strList("tvTypes").ifEmpty { listOf("Movie", "TvSeries") },
        )
    }

    private fun resolveUrl(raw: String, base: okhttp3.HttpUrl?): String? {
        raw.toHttpUrlOrNull()?.let { return it.toString() }
        return base?.resolve(raw)?.toString()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null }

    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
    private fun JsonObject.strList(key: String): List<String> {
        val v = this[key] ?: return emptyList()
        return when (v) {
            is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.ifEmpty { null } }
            is JsonPrimitive -> listOf(v.content)
            else -> emptyList()
        }
    }
}
