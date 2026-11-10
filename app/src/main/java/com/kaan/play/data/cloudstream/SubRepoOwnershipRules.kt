package com.kaan.play.data.cloudstream

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaan.play.data.net.PlayJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/**
 * CloudStream üst depo -> alt depo sahiplik anlık görüntüsü (Android'den bağımsız).
 * Tüm adresler [PluginRepoRequestRules.normalizeUrl] ile normalleştirilmiş tutulur.
 */
@Serializable
data class SubRepoOwnershipState(
    val parentToChildren: Map<String, Set<String>> = emptyMap(),
    val manualUrls: Set<String> = emptySet(),
)

/**
 * CloudStream alt depo sahiplenme ve güvenli basamaklı silme kuralları (saf Kotlin).
 * - Kullanıcının kendisinin doğrudan eklediği (`manualUrls`) bir depo ASLA silinmez.
 * - Başka bir kurulu üst deponun da bildirdiği/kullandığı alt depo ASLA silinmez.
 * - Döngülerde (`A -> B`, `B -> A`) sonsuz döngüye girmez.
 */
object SubRepoOwnershipRules {

    fun normalize(url: String): String = PluginRepoRequestRules.normalizeUrl(url)

    fun recordManual(state: SubRepoOwnershipState, repoUrl: String): SubRepoOwnershipState {
        val norm = normalize(repoUrl)
        if (norm.isEmpty()) return state
        return state.copy(manualUrls = state.manualUrls + norm)
    }

    fun recordManualAll(state: SubRepoOwnershipState, repoUrls: Collection<String>): SubRepoOwnershipState {
        val normUrls = repoUrls.mapNotNull {
            val n = normalize(it)
            if (n.isNotEmpty()) n else null
        }
        if (normUrls.isEmpty()) return state
        return state.copy(manualUrls = state.manualUrls + normUrls)
    }

    fun recordSubRepos(
        state: SubRepoOwnershipState,
        parentUrl: String,
        childUrls: Iterable<String>,
    ): SubRepoOwnershipState {
        val normParent = normalize(parentUrl)
        if (normParent.isEmpty()) return state
        val validChildren = childUrls
            .map(::normalize)
            .filter { it.isNotEmpty() && it != normParent }
            .toSet()
        if (validChildren.isEmpty()) return state

        val updatedMap = state.parentToChildren.toMutableMap()
        val existing = updatedMap[normParent].orEmpty()
        updatedMap[normParent] = existing + validChildren
        return state.copy(parentToChildren = updatedMap)
    }

    /**
     * [parentUrl] kaldırılırken güvenle kaldırılabilecek alt depo URL'lerini (normalleştirilmiş) döner.
     */
    fun removableChildren(
        state: SubRepoOwnershipState,
        parentUrl: String,
        installedRepoUrls: Collection<String>,
    ): Set<String> {
        val normParent = normalize(parentUrl)
        if (normParent.isEmpty()) return emptySet()

        val installedNorm = installedRepoUrls.mapTo(HashSet(), ::normalize)
        val children = state.parentToChildren[normParent].orEmpty()
        if (children.isEmpty()) return emptySet()

        val usedByOtherInstalledParents = state.parentToChildren
            .asSequence()
            .filter { (parent, _) -> parent != normParent && parent in installedNorm }
            .flatMap { (_, list) -> list.asSequence() }
            .toSet()

        return children.filterTo(LinkedHashSet()) { child ->
            child in installedNorm &&
                child != normParent &&
                child !in state.manualUrls &&
                child !in usedByOtherInstalledParents
        }
    }

    /**
     * Üst depo (ve istenirse seçilen alt depolar) kaldırıldıktan sonra sahiplik durumunu temizler.
     */
    fun onRepoRemoved(
        state: SubRepoOwnershipState,
        removedRepoUrl: String,
        deletedChildUrls: Set<String> = emptySet(),
    ): SubRepoOwnershipState {
        val normRemoved = normalize(removedRepoUrl)
        val normDeletedChildren = deletedChildUrls.mapTo(HashSet(), ::normalize)
        val allRemoved = normDeletedChildren + normRemoved

        val updatedParents = LinkedHashMap<String, Set<String>>()
        for ((parent, children) in state.parentToChildren) {
            if (parent in allRemoved) continue
            val remainingChildren = children.filterNotTo(LinkedHashSet()) { it in allRemoved }
            if (remainingChildren.isNotEmpty()) {
                updatedParents[parent] = remainingChildren
            }
        }
        val updatedManual = state.manualUrls.filterNotTo(LinkedHashSet()) { it in allRemoved }
        return SubRepoOwnershipState(
            parentToChildren = updatedParents,
            manualUrls = updatedManual,
        )
    }
}

class SubRepoOwnershipStore(private val dataStore: DataStore<Preferences>) {

    val state: Flow<SubRepoOwnershipState> = dataStore.data.map { prefs -> decode(prefs[OWNERSHIP_KEY]) }

    suspend fun currentState(): SubRepoOwnershipState = decode(dataStore.data.first()[OWNERSHIP_KEY])

    suspend fun recordManual(repoUrl: String) {
        update { SubRepoOwnershipRules.recordManual(it, repoUrl) }
    }

    suspend fun recordManualAll(repoUrls: Collection<String>) {
        update { SubRepoOwnershipRules.recordManualAll(it, repoUrls) }
    }

    /**
     * İlk açılışta (bir kereye mahsus) o an veritabanında var olan tüm CloudStream depolarını
     * kullanıcının kendi deposu (manual) olarak işaretler; üst depo silinmelerinde silinmelerini engeller.
     */
    suspend fun seedInitialManualReposIfFirstTime(installedRepoUrls: Collection<String>) {
        dataStore.edit { prefs ->
            if (prefs[SEEDED_KEY] == true) return@edit
            val current = decode(prefs[OWNERSHIP_KEY])
            val next = SubRepoOwnershipRules.recordManualAll(current, installedRepoUrls)
            if (next.parentToChildren.isEmpty() && next.manualUrls.isEmpty()) {
                prefs.remove(OWNERSHIP_KEY)
            } else {
                prefs[OWNERSHIP_KEY] = PlayJson.encodeToString(SubRepoOwnershipState.serializer(), next)
            }
            prefs[SEEDED_KEY] = true
        }
    }

    suspend fun recordSubRepos(parentUrl: String, childUrls: Iterable<String>) {
        update { SubRepoOwnershipRules.recordSubRepos(it, parentUrl, childUrls) }
    }

    suspend fun removableChildren(parentUrl: String, installedRepoUrls: Collection<String>): Set<String> =
        SubRepoOwnershipRules.removableChildren(currentState(), parentUrl, installedRepoUrls)

    suspend fun onRepoRemoved(removedRepoUrl: String, deletedChildUrls: Set<String> = emptySet()) {
        update { SubRepoOwnershipRules.onRepoRemoved(it, removedRepoUrl, deletedChildUrls) }
    }

    private suspend fun update(transform: (SubRepoOwnershipState) -> SubRepoOwnershipState) {
        dataStore.edit { prefs ->
            val current = decode(prefs[OWNERSHIP_KEY])
            val next = transform(current)
            if (next.parentToChildren.isEmpty() && next.manualUrls.isEmpty()) {
                prefs.remove(OWNERSHIP_KEY)
            } else {
                prefs[OWNERSHIP_KEY] = PlayJson.encodeToString(SubRepoOwnershipState.serializer(), next)
            }
        }
    }

    private fun decode(raw: String?): SubRepoOwnershipState {
        if (raw.isNullOrBlank()) return SubRepoOwnershipState()
        return runCatching { PlayJson.decodeFromString(SubRepoOwnershipState.serializer(), raw) }
            .getOrDefault(SubRepoOwnershipState())
    }

    companion object {
        private val OWNERSHIP_KEY = stringPreferencesKey("cs_sub_repo_ownership")
        val SEEDED_KEY = booleanPreferencesKey("cs_sub_repos_manual_seeded")
    }
}
