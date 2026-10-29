package com.kaan.play.data.plugin

import com.kaan.play.data.addon.ManifestFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

data class RepoManagerEntry(
    val id: String,
    val name: String,
    val url: String,
    val category: String,
    val sourceType: SourceType,
)

sealed interface CatalogInstallOutcome {
    val url: String

    data class Success(override val url: String, val result: AddResult) : CatalogInstallOutcome
    data class Failure(override val url: String, val message: String) : CatalogInstallOutcome
}

class CatalogInstallCoordinator(
    private val appScope: CoroutineScope,
    private val installer: SourceInstaller,
) {
    private val _installingUrl = MutableStateFlow<String?>(null)
    val installingUrl: StateFlow<String?> = _installingUrl.asStateFlow()

    private val _outcome = MutableStateFlow<CatalogInstallOutcome?>(null)
    val outcome: StateFlow<CatalogInstallOutcome?> = _outcome.asStateFlow()

    fun install(url: String, sourceType: SourceType): Boolean {
        if (_installingUrl.value != null) return false
        _installingUrl.value = url
        _outcome.value = null
        appScope.launch {
            try {
                val res = installer.add(url, sourceType)
                _outcome.value = CatalogInstallOutcome.Success(url, res)
            } catch (e: Exception) {
                _outcome.value = CatalogInstallOutcome.Failure(url, e.message ?: "Kurulum başarısız")
            } finally {
                _installingUrl.value = null
            }
        }
        return true
    }

    fun clearOutcome() {
        _outcome.value = null
    }
}

class RepoManagerCatalog(private val fetcher: ManifestFetcher) {

    suspend fun refresh(): List<RepoManagerEntry> {
        val raw = fetcher.fetch(CATALOG_URL)
        return parse(raw)
    }

    companion object {
        const val CATALOG_URL =
            "https://raw.githubusercontent.com/liberta09/CloudStreamRepoManager/main/central_repos.json"
        const val MAX_ENTRIES = 200

        fun parse(raw: String): List<RepoManagerEntry> {
            val element = Json.parseToJsonElement(raw)
            val jsonObject = element.jsonObject
            val repos = jsonObject["repos"]?.jsonArray ?: return emptyList()
            val seenUrls = HashSet<String>()

            return repos.asSequence()
                .mapNotNull { it as? JsonObject }
                .mapNotNull { row ->
                    val urlStr = string(row, "url") ?: return@mapNotNull null
                    val httpUrl = urlStr.toHttpUrlOrNull() ?: return@mapNotNull null
                    if (!httpUrl.isHttps) return@mapNotNull null
                    val canonicalUrl = httpUrl.toString()

                    val typeStr = string(row, "type")?.lowercase(Locale.ROOT).orEmpty()
                    val platformStr = string(row, "platform")?.lowercase(Locale.ROOT).orEmpty()
                    val nuvioKind = string(row, "nuvioKind")?.lowercase(Locale.ROOT).orEmpty()

                    val sourceType = when {
                        typeStr == "cloudstream" || platformStr == "cloudstream" -> SourceType.CLOUDSTREAM
                        typeStr == "nuvio" || platformStr == "nuvio" -> {
                            if (nuvioKind == "plugin" || typeStr == "plugin") SourceType.NUVIO_PLUGIN
                            else SourceType.NUVIO_REPO
                        }
                        typeStr == "plugin" -> SourceType.NUVIO_PLUGIN
                        typeStr == "addon" -> SourceType.NUVIO_REPO
                        else -> SourceType.CLOUDSTREAM
                    }

                    val id = string(row, "id") ?: canonicalUrl
                    val name = string(row, "name") ?: id
                    val category = string(row, "category") ?: "Diğer"

                    RepoManagerEntry(
                        id = id,
                        name = name,
                        url = canonicalUrl,
                        category = category,
                        sourceType = sourceType,
                    )
                }
                .filter { entry ->
                    val normalized = entry.url.lowercase(Locale.ROOT).trimEnd('/')
                    seenUrls.add(normalized)
                }
                .take(MAX_ENTRIES)
                .toList()
        }

        private fun string(obj: JsonObject, key: String): String? {
            return obj[key]?.jsonPrimitive?.content?.trim()?.ifBlank { null }
        }
    }
}
