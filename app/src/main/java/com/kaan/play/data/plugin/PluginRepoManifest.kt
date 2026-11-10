package com.kaan.play.data.plugin

import com.kaan.play.data.addon.AddonException
import com.kaan.play.data.addon.AddonException.Kind
import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Nuvio eklenti deposunun (JS kazıyıcı deposu) kendi modelimizle kodlanmış manifest'i. Biçim `docs/nuvio-plugin-format.md`
 * §1'de belgelidir; yalnızca Play'in kullandığı alanlar tutulur, bilinmeyenler yok sayılır.
 */
@Serializable
data class PluginRepoManifest(
    val name: String,
    val version: String,
    val description: String? = null,
    val scrapers: List<PluginScraperEntry>,
)

@Serializable
data class PluginScraperEntry(
    val id: String,
    val name: String,
    val version: String,
    /** Manifest'teki ham değer (göreli ya da mutlak). */
    val filename: String,
    /** İndirme adresi: `filename` mutlaksa kendisi, değilse manifest adresine göre çözülmüş hali. */
    val scriptUrl: String,
    val description: String? = null,
    /** Ham değerler (`movie`, `tv`, `series`...); eşleme için [normalizedTypes]. */
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val contentLanguage: List<String> = emptyList(),
    val hasSettings: Boolean = false,
) {
    /** `series/show/other` -> `tv` (Nuvio ile aynı normalleştirme). */
    val normalizedTypes: Set<String>
        get() = supportedTypes.map { normalizeType(it) }.toSet()

    companion object {
        fun normalizeType(value: String): String = when (val v = value.trim().lowercase()) {
            "series", "show", "other" -> "tv"
            else -> v
        }
    }
}

/** İçeriğine göre manifest türü. Etikete ya da adrese değil, yalnızca alanlara bakılır. */
enum class ManifestKind {
    /** Stremio protokollü addon: `resources` ve zorunlu `id` var, `scrapers` yok. */
    Addon,

    /** Nuvio JS kazıyıcı deposu: `scrapers` nesne dizisi var (`resources`/`types` bunu değiştirmez). */
    PluginRepo,

    /** CloudStream tarzı harici depo (`pluginLists` veya `manifestVersion` + `plugins`). */
    External,

    /** Hiçbirine uymuyor; addon ayrıştırıcısı kesin hatayı verir. */
    Unknown;

    companion object {
        /** @throws AddonException [Kind.NotJson] */
        fun classify(raw: String): ManifestKind {
            val root = try {
                PlayJson.parseToJsonElement(raw)
            } catch (e: SerializationException) {
                throw AddonException(Kind.NotJson, cause = e)
            } catch (e: IllegalArgumentException) {
                throw AddonException(Kind.NotJson, cause = e)
            }
            if (root !is JsonObject) throw AddonException(Kind.NotJson)

            // Kural 1 (Nuvio mantığı): `scrapers` nesne dizisi varsa KESİNLİKLE PLUGIN DEPOSU.
            // `resources` veya `types` olması bunu değiştirmez (örn. jasoelys, hans-mega, bronzecloud).
            val scrapers = root["scrapers"]
            if (scrapers is JsonArray && (scrapers.any { it is JsonObject } || (scrapers.isEmpty() && root["resources"] == null))) {
                return PluginRepo
            }

            // Kural 2: CloudStream deposu (pluginLists, manifestVersion+plugins veya alt depo listesi)
            val pluginLists = root["pluginLists"]
            if (pluginLists is JsonArray && pluginLists.isNotEmpty()) {
                return External
            }
            if (root["manifestVersion"] != null && root["plugins"] is JsonArray) {
                return External
            }
            val subRepoKeys = listOf("repositories", "repos", "subRepos", "includedRepos", "includedRepositories")
            if (subRepoKeys.any { key -> (root[key] as? JsonArray)?.isNotEmpty() == true }) {
                return External
            }

            // Kural 3: Addon için zorunlu `id` ve `resources` dizisi gerekir; `scrapers` olmamalıdır
            val id = (root["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            val resources = root["resources"]
            if (!id.isNullOrEmpty() && resources is JsonArray && scrapers == null) {
                return Addon
            }

            // Yedek toleranslar
            if (scrapers is JsonArray && scrapers.isNotEmpty()) {
                return PluginRepo
            }
            if (resources is JsonArray && !id.isNullOrEmpty()) {
                return Addon
            }
            if (pluginLists is JsonArray) {
                return External
            }

            return Unknown
        }
    }
}

object PluginRepoManifestParser {

    /** Bir depodan alınacak en çok kazıyıcı sayısı (gerçek depolar 6–43). */
    const val MAX_SCRAPERS = 200

    /**
     * Depo manifest'ini doğrular. Eksik/bozuk girişler (kimlik ya da dosya adı yok, adres çözülemiyor, http(s) değil,
     * yinelenen kimlik) atlanır; hiç kullanılabilir giriş kalmazsa [Kind.NoPlugins].
     * @param manifestUrl göreli `filename` değerlerinin çözüleceği adres.
     */
    fun parse(raw: String, manifestUrl: String): PluginRepoManifest {
        val root = try {
            PlayJson.parseToJsonElement(raw)
        } catch (e: SerializationException) {
            throw AddonException(Kind.NotJson, cause = e)
        } catch (e: IllegalArgumentException) {
            throw AddonException(Kind.NotJson, cause = e)
        }
        if (root !is JsonObject) throw AddonException(Kind.NotJson)

        val name = root.string("name") ?: throw AddonException(Kind.MissingField, "name")
        val array = root["scrapers"] as? JsonArray ?: throw AddonException(Kind.MissingField, "scrapers")
        val base = manifestUrl.toHttpUrlOrNull()

        val seen = HashSet<String>()
        val entries = array.asSequence()
            .mapNotNull { (it as? JsonObject)?.let { obj -> entry(obj, base) } }
            .filter { seen.add(it.id) }
            .take(MAX_SCRAPERS)
            .toList()
        if (entries.isEmpty()) throw AddonException(Kind.NoPlugins)

        return PluginRepoManifest(
            name = name,
            version = root.string("version") ?: "0",
            description = root.string("description"),
            scrapers = entries,
        )
    }

    private fun entry(obj: JsonObject, base: HttpUrl?): PluginScraperEntry? {
        val id = obj.string("id") ?: return null
        val filename = obj.string("filename") ?: return null
        val url = resolve(filename, base) ?: return null
        return PluginScraperEntry(
            id = id,
            name = obj.string("name") ?: id,
            version = obj.string("version") ?: "0",
            filename = filename,
            scriptUrl = url,
            description = obj.string("description"),
            supportedTypes = obj.strings("supportedTypes").ifEmpty { listOf("movie", "tv") },
            contentLanguage = obj.strings("contentLanguage"),
            hasSettings = when (val p = obj["hasSettings"]) {
                is JsonPrimitive -> p.content.equals("true", ignoreCase = true)
                else -> false
            },
        )
    }

    /** Mutlak http(s) adresi olduğu gibi, göreli olan manifest adresine göre çözülür; başka şema reddedilir. */
    internal fun resolve(filename: String, base: HttpUrl?): String? {
        val absolute = filename.toHttpUrlOrNull()
        if (absolute != null) return absolute.toString()
        // `ftp://`, `file:`, `javascript:` gibi başka şemalar resolve() içinde null döner.
        return base?.resolve(filename)?.toString()
    }

    private fun JsonObject.string(field: String): String? {
        val v = this[field] as? JsonPrimitive ?: return null
        if (!v.isString) return v.content.takeIf { it != "null" && it.isNotBlank() }?.trim()
        return v.content.trim().ifEmpty { null }
    }

    private fun JsonObject.strings(field: String): List<String> {
        val v: JsonElement = this[field] ?: return emptyList()
        if (v is JsonNull) return emptyList()
        if (v is JsonArray) {
            return v.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim()?.ifEmpty { null } }
        }
        if (v is JsonPrimitive && v.isString) {
            val content = v.content.trim()
            if (content.isEmpty()) return emptyList()
            return content.split(',', ' ', ';', '\t').map { it.trim() }.filter { it.isNotEmpty() }
        }
        return emptyList()
    }
}

/** Sürüm karşılaştırması: nokta ayrımlı sayılar sayısal, başka her şey eşitlik testi. */
object VersionCompare {
    /** @return `>0` ise [remote] daha yeni; `0` aynı; `<0` yerel daha yeni. Sayısal olmayanlarda farklıysa `1`. */
    fun compare(local: String, remote: String): Int {
        if (local == remote) return 0
        val a = parts(local)
        val b = parts(remote)
        if (a == null || b == null) return 1
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y.compareTo(x)
        }
        return 0
    }

    private fun parts(v: String): List<Int>? {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        val pieces = cleaned.split('.')
        val numbers = pieces.map { it.toIntOrNull() ?: return null }
        return numbers.takeIf { it.isNotEmpty() }
    }
}
