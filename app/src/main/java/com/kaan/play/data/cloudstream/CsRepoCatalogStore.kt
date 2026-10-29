package com.kaan.play.data.cloudstream

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.kaan.play.data.net.PlayJson

/** Keşfedilmiş, henüz kullanıcının kurmadığı CloudStream alt depoları. */
class CsRepoCatalogStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("cs_repo_catalog", Context.MODE_PRIVATE)
    private val lock = Any()
    private val mutableEntries = MutableStateFlow(readEntries())
    private val mutableOwnership = MutableStateFlow(readOwnership())
    val entries: StateFlow<List<CsRepoReference>> = mutableEntries.asStateFlow()

    suspend fun merge(references: List<CsRepoReference>) = withContext(Dispatchers.IO) {
        if (references.isEmpty()) return@withContext
        synchronized(lock) {
            val mergedByUrl = linkedMapOf<String, CsRepoReference>()
            (mutableEntries.value + references)
                .filter { it.url.startsWith("https://", ignoreCase = true) }
                .forEach { ref ->
                    val key = canonicalUrl(ref.url)
                    val normalized = ref.copy(name = ref.name.ifBlank { readableName(ref.url) })
                    val previous = mergedByUrl[key]
                    mergedByUrl[key] = if (previous == null) normalized else previous.copy(
                        name = previous.name.ifBlank { normalized.name },
                        description = previous.description ?: normalized.description,
                        discoveredFrom = (previous.discoveredFrom + normalized.discoveredFrom).distinct(),
                    )
                }
            writeEntries(mergedByUrl.values.take(MAX_ENTRIES))

            var graph = mutableOwnership.value
            references.forEach { ref ->
                ref.discoveredFrom.forEach { owner -> graph = CsRepoOwnershipGraph.add(graph, owner, ref.url) }
            }
            writeOwnership(graph)
        }
    }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            writeEntries(mutableEntries.value.filterNot { canonicalUrl(it.url) == canonicalUrl(url) })
        }
    }

    /**
     * Üst depo silinirken yalnızca ona ait, başka hiçbir kurulu kaynağın sahiplenmediği alt dalları döndürür.
     * Paylaşılan alt depoların diğer sahiplik bağlantıları ve kurulumları korunur.
     */
    suspend fun removeOwnedDescendants(parentUrl: String): List<String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val plan = CsRepoOwnershipGraph.removeOwnedDescendants(mutableOwnership.value, parentUrl)
            writeOwnership(plan.remainingEdges)
            val removedKeys = plan.descendants.mapTo(mutableSetOf(), ::canonicalUrl) + canonicalUrl(parentUrl)
            writeEntries(mutableEntries.value.filterNot { canonicalUrl(it.url) in removedKeys })
            plan.descendants
        }
    }

    private fun readEntries(): List<CsRepoReference> = runCatching {
        val raw = preferences.getString(KEY_ENTRIES, null) ?: return emptyList()
        PlayJson.decodeFromString(ListSerializer(CsRepoReference.serializer()), raw)
            .filter { it.url.startsWith("https://", ignoreCase = true) }
            .distinctBy { canonicalUrl(it.url) }
            .take(MAX_ENTRIES)
            .map { it.copy(name = it.name.ifBlank { readableName(it.url) }) }
    }.getOrDefault(emptyList())

    private fun writeEntries(items: List<CsRepoReference>) {
        val raw = PlayJson.encodeToString(ListSerializer(CsRepoReference.serializer()), items)
        preferences.edit().putString(KEY_ENTRIES, raw).commit()
        mutableEntries.value = items
    }

    private fun readOwnership(): Map<String, List<String>> = runCatching {
        val raw = preferences.getString(KEY_OWNERSHIP, null) ?: return emptyMap()
        PlayJson.decodeFromString(OwnershipSerializer, raw)
            .mapValues { (_, children) -> children.distinctBy(::canonicalUrl) }
    }.getOrDefault(emptyMap())

    private fun writeOwnership(edges: Map<String, List<String>>) {
        val normalized = edges.mapValues { (_, children) -> children.distinctBy(::canonicalUrl) }
            .filterValues { it.isNotEmpty() }
        val raw = PlayJson.encodeToString(OwnershipSerializer, normalized)
        preferences.edit().putString(KEY_OWNERSHIP, raw).commit()
        mutableOwnership.value = normalized
    }

    private fun canonicalUrl(url: String): String = url.trim().removeSuffix("/").lowercase()

    private fun readableName(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url.substringAfterLast('/').ifBlank { url }
        val segments = parsed.pathSegments.filter { it.isNotBlank() }
        val name = when {
            parsed.host.equals("raw.githubusercontent.com", ignoreCase = true) && segments.size >= 2 ->
                "${segments[0]} / ${segments[1]}"
            segments.isNotEmpty() -> segments.last().removeSuffix(".json").removeSuffix(".JSON")
            else -> parsed.host
        }
        return name.ifBlank { parsed.host }
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val KEY_OWNERSHIP = "repository_ownership"
        const val MAX_ENTRIES = 500
        val OwnershipSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    }
}

internal data class CsRepoRemovalPlan(
    val descendants: List<String>,
    val remainingEdges: Map<String, List<String>>,
)

/** Pure ownership graph operations, kept separate so shared/recursive deletion rules can be unit tested. */
internal object CsRepoOwnershipGraph {
    fun add(edges: Map<String, List<String>>, parentUrl: String, childUrl: String): Map<String, List<String>> {
        val parent = canonical(parentUrl)
        val children = edges[parent].orEmpty()
        if (children.any { canonical(it) == canonical(childUrl) }) return edges
        return edges + (parent to (children + childUrl))
    }

    fun removeOwnedDescendants(edges: Map<String, List<String>>, parentUrl: String): CsRepoRemovalPlan {
        val remaining = edges.mapValuesTo(linkedMapOf()) { (_, children) -> children.toMutableList() }
        val root = canonical(parentUrl)
        val descendants = linkedMapOf<String, String>()
        val visiting = mutableSetOf<String>()

        // An explicit deletion also removes incoming references to the deleted root.
        remaining.remove(root)
        remaining.keys.toList().forEach { owner ->
            remaining[owner]?.removeAll { canonical(it) == root }
            if (remaining[owner].isNullOrEmpty()) remaining.remove(owner)
        }

        fun visit(parent: String) {
            if (!visiting.add(parent)) return
            val children = remaining.remove(parent).orEmpty()
            children.forEach { childUrl ->
                val child = canonical(childUrl)
                if (child == root || child in visiting) return@forEach
                val stillOwned = remaining.any { (_, ownedChildren) -> ownedChildren.any { canonical(it) == child } }
                if (!stillOwned) {
                    if (child !in descendants) {
                        descendants[child] = childUrl
                        visit(child)
                    }
                }
            }
            visiting.remove(parent)
        }

        // The root's outgoing edges have already been removed above; visit them from the original graph.
        val rootChildren = edges.entries.firstOrNull { canonical(it.key) == root }?.value.orEmpty()
        rootChildren.forEach { childUrl ->
            val child = canonical(childUrl)
            if (child == root || child in visiting) return@forEach
            val stillOwned = remaining.any { (_, ownedChildren) -> ownedChildren.any { canonical(it) == child } }
            if (!stillOwned && child !in descendants) {
                descendants[child] = childUrl
                visit(child)
            }
        }

        return CsRepoRemovalPlan(descendants.values.toList(), remaining.mapValues { (_, children) -> children.toList() })
    }

    private fun canonical(url: String): String = url.trim().removeSuffix("/").lowercase()
}
