package com.kaan.play.data.plugin

import com.kaan.play.data.addon.ManifestFetcher
import com.kaan.play.data.net.PlayJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Tek katalog kartı: merkezi CloudStream Repo Manager kataloğundan alınır. */
data class RepoManagerEntry(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val sourceType: SourceType,
)

/** Merkezi listeyi her çağrıda ağdan alır; yeni katalog kayıtları uygulama güncellemesi gerektirmez. */
class RepoManagerCatalog(private val fetcher: ManifestFetcher) {
    suspend fun refresh(): List<RepoManagerEntry> = parse(fetcher.fetch(CATALOG_URL))

    companion object {
        const val CATALOG_URL = "https://raw.githubusercontent.com/liberta09/CloudStreamRepoManager/main/central_repos.json"
        const val MAX_ENTRIES = 1_000

        fun parse(raw: String): List<RepoManagerEntry> {
            val root = runCatching { PlayJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return emptyList()
            val repos = root["repos"] as? JsonArray ?: return emptyList()
            val seen = HashSet<String>()
            return repos.asSequence()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { row ->
                    val url = row.string("url")?.toHttpUrlOrNull()
                        ?.takeIf { it.isHttps }
                        ?.toString()
                        ?: return@mapNotNull null
                    val type = row.string("type")?.lowercase()
                    val platform = row.string("platform")?.lowercase()
                    val kind = row.string("nuvioKind")?.lowercase()
                    val sourceType = when {
                        platform == "cloudstream" || type == "cloudstream" -> SourceType.CLOUDSTREAM
                        (platform == "nuvio" || type == "nuvio") && kind == "plugin" -> SourceType.NUVIO_PLUGIN
                        (platform == "nuvio" || type == "nuvio") && kind == "addon" -> SourceType.NUVIO_REPO
                        else -> return@mapNotNull null
                    }
                    RepoManagerEntry(
                        id = row.string("id")?.takeIf { it.isNotBlank() } ?: url,
                        name = row.string("name")?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/'),
                        url = url,
                        category = row.string("category")?.takeIf { it.isNotBlank() } ?: "Diğer",
                        sourceType = sourceType,
                    )
                }
                .filter { seen.add(it.url.trimEnd('/').lowercase()) }
                .take(MAX_ENTRIES)
                .toList()
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    }
}
