package com.kaan.play.data.sync.local

import com.kaan.play.data.sync.DeletedFavoriteItem
import com.kaan.play.data.sync.DeletedHistoryItem
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.serialization.json.Json

/** Bir cihazın birleştirmeden sonra yazacakları. */
data class LocalSyncPlan(
    /** Birleşik profil listesi (PIN'ler yerelde kalır, karşıdan gelen profil PIN'siz gelir). */
    val profiles: List<LocalProfileSummary>,
    /** Profil başına birleşik geçmiş + favoriler. */
    val libraries: Map<String, LocalProfileBackup>,
    /** Karşıda olup burada olmayan kaynaklar: kendiliğinden eklenmez, kullanıcıya sorulur. */
    val missingSources: List<SourceTransferItem>,
    /** İki cihazda da olan kaynakların uygulanacak durumları (yalnızca karşı taraf esas alınıyorsa dolu). */
    val sourceStates: List<SourceTransferItem>,
    /** Tekilleştirilen Varsayılan profillerin eski kimlik -> korunan kimlik haritası. */
    val remappedProfileIds: Map<String, String> = emptyMap(),
    /** Birleştirmeden sonra uygulanacak aktif profil kimliği (atılan kimlikteyse korunana geçer). */
    val activeProfileId: String? = null,
    /** İki tarafın birleşik ve budanmış geçmiş mezar taşları. */
    val deletedHistory: List<DeletedHistoryItem> = emptyList(),
    /** İki tarafın birleşik ve budanmış favori mezar taşları. */
    val deletedFavorites: List<DeletedFavoriteItem> = emptyList(),
) {
    val historyCount: Int get() = libraries.values.sumOf { it.history.size }
}

/** Tek cihazda ya da birleştirmede profil tekilleştirme sonucu. */
data class ProfileConsolidationResult(
    val profiles: List<LocalProfileSummary>,
    val libraries: Map<String, LocalProfileBackup>,
    val remappedIds: Map<String, String> = emptyMap(),
    val activeProfileId: String? = null,
)

/**
 * Wi-Fi senkronizasyonunun birleştirme kuralları (Android'den bağımsız, birim testli). İki cihaz birbirinin anlık
 * görüntüsünü alır ve aynı kurallarla birleştirir; böylece sonunda aynı veriye sahip olurlar:
 *
 * - **Profiller:** kimliğe göre birleşim. PIN hiçbir zaman aktarılmaz ([forTransfer]); yerel profil PIN'ini korur.
 *   PIN'siz ve adı tam olarak [DEFAULT_NAME] olan profiller tekilleştirilir; daha eski/sabit kimlik ([DEFAULT_ID] ya da
 *   deterministik en küçük kimlik) korunur, atılan kimliğin kitaplığı (geçmiş + favori) korunana eklenir.
 * - **Geçmiş / kaldığı yer:** aynı içerikte (film ya da sezon+bölüm) en yeni `lastWatchedAt` kazanır; eşitlikte daha ileri konum.
 * - **Favoriler:** birleşim.
 * - **Kaynaklar:** yalnızca karşıda olanlar [LocalSyncPlan.missingSources] olur (onay sorulur). İki tarafta da olanlarda
 *   açık/kapalı, eklenti ayarları ve katalog anahtarları **kodu gösteren cihazınki** esas alınır: bağlanan (istemci)
 *   cihaz `remoteIsAuthority = true` ile çağırır ve bunları uygular, sunucu kendi durumunu korur.
 */
object LocalSyncMerge {

    const val DEFAULT_NAME = "Varsayılan"
    const val DEFAULT_ID = "default"
    const val MAX_DELETED_HISTORY_PER_PROFILE = 500
    const val DELETED_HISTORY_RETENTION_MS = 90L * 24 * 60 * 60 * 1000L // 90 gün

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Yalnızca adı tam olarak DEFAULT_NAME olan ve PIN'i olmayan profiller tekilleştirme adayıdır. */
    fun isEligibleDefault(profile: LocalProfileSummary): Boolean =
        profile.name == DEFAULT_NAME && !profile.hasPin

    /** Korunacak kimlik: varsa sabit DEFAULT_ID ("default"), yoksa deterministik alfabetik en küçük kimlik. */
    fun chooseKeptDefaultId(candidateIds: Collection<String>): String? {
        if (candidateIds.isEmpty()) return null
        return if (candidateIds.contains(DEFAULT_ID)) {
            DEFAULT_ID
        } else {
            candidateIds.minOrNull()
        }
    }

    /** Aktif profil atılan kimlikteyse korunana geçer; değilse mevcut profil listede kalıyorsa o, yoksa ilk profil. */
    fun resolveActiveProfileId(
        currentActiveId: String?,
        profiles: List<LocalProfileSummary>,
        remappedIds: Map<String, String> = emptyMap(),
    ): String? {
        val target = remappedIds[currentActiveId] ?: currentActiveId
        return if (target != null && profiles.any { it.id == target }) {
            target
        } else {
            profiles.firstOrNull()?.id
        }
    }

    /** Tek cihazda (ya da birleştirme sonrasında) çoğalmış PIN'siz Varsayılan profilleri tekilleştirir, kitaplıkları birleştirir. */
    fun consolidateProfiles(
        profiles: List<LocalProfileSummary>,
        libraries: Map<String, LocalProfileBackup> = emptyMap(),
        activeProfileId: String? = null,
    ): ProfileConsolidationResult {
        val eligible = profiles.filter(::isEligibleDefault)
        if (eligible.size <= 1) {
            return ProfileConsolidationResult(
                profiles = profiles,
                libraries = libraries,
                remappedIds = emptyMap(),
                activeProfileId = activeProfileId,
            )
        }
        val keptId = chooseKeptDefaultId(eligible.map { it.id }) ?: return ProfileConsolidationResult(profiles, libraries, emptyMap(), activeProfileId)
        val discardedIds = eligible.map { it.id }.filter { it != keptId }.toSet()
        val remapped = discardedIds.associateWith { keptId }

        val newProfiles = profiles.filter { it.id !in discardedIds }

        var mergedLib = libraries[keptId] ?: LocalProfileBackup()
        for (dId in discardedIds) {
            val lib = libraries[dId]
            if (lib != null) {
                mergedLib = mergeLibrary(mergedLib, lib)
            }
        }
        val newLibraries = libraries.filterKeys { it !in discardedIds } + (keptId to mergedLib)
        val newActiveId = resolveActiveProfileId(activeProfileId, newProfiles, remapped)

        return ProfileConsolidationResult(
            profiles = newProfiles,
            libraries = newLibraries,
            remappedIds = remapped,
            activeProfileId = newActiveId,
        )
    }

    /** Aktarım için: PIN ve bulut kodu çıkarılır. */
    fun forTransfer(profile: LocalProfileSummary): LocalProfileSummary = profile.copy(pinHash = null, syncCode = null)

    /**
     * Bir cihazın anlık görüntüsü (Wi-Fi ve bulut aynı yükü kullanır). PIN ve kod hiçbir zaman girmez; kaynaklarda
     * yalnızca adresler ve durumlar vardır (eklenti JS/DEX dosyaları taşınmaz, karşı cihaz kendisi indirir).
     */
    fun snapshot(
        profiles: List<LocalProfileSummary>,
        libraries: Map<String, LocalProfileBackup>,
        sources: List<SourceTransferItem>,
        deviceName: String,
        deletedHistory: List<DeletedHistoryItem> = emptyList(),
        deletedFavorites: List<DeletedFavoriteItem> = emptyList(),
    ): LocalProfileTransfer = LocalProfileTransfer(
        profiles = profiles.map(::forTransfer),
        backups = libraries.mapValues { encodeBackup(it.value) },
        sources = sources,
        deviceName = deviceName,
        deletedHistory = deletedHistory,
        deletedFavorites = deletedFavorites,
    )

    /**
     * Buluta yazılacak yük: bu cihazın birleştirmeden sonraki görüntüsü + henüz onaylanmamış (karşıdan gelen) kaynaklar.
     * Böylece onay bekleyen kaynak buluttan silinmez; öteki cihazlar onu görmeye devam eder.
     */
    fun withPending(local: LocalProfileTransfer, pending: List<SourceTransferItem>): LocalProfileTransfer {
        val known = local.sources.mapTo(HashSet()) { urlKey(it.sourceUrl) }
        return local.copy(sources = local.sources + pending.filter { urlKey(it.sourceUrl) !in known })
    }

    /**
     * Bulut: iki tarafta da olan kaynakların durumunda "daha yeni kazanır". [remoteUpdatedAt] buluttaki kaydın
     * `updated_at`'i, [localStamp] bu cihazın buluta son yazdığında sunucunun verdiği `updated_at` (yerel durumun
     * bilinen son eşitlenme anı). Bulut kaydı damgasızsa yerel korunur; bu cihaz hiç yazmamışsa damgalı bulut kazanır.
     */
    fun remoteIsNewer(remoteUpdatedAt: String?, localStamp: String?): Boolean {
        val remote = remoteUpdatedAt?.let(CloudStamp::parseMicros) ?: return false
        val local = localStamp?.let(CloudStamp::parseMicros) ?: return true
        return remote > local
    }

    fun plan(
        local: LocalProfileTransfer,
        remote: LocalProfileTransfer,
        remoteIsAuthority: Boolean,
        localActiveProfileId: String? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): LocalSyncPlan {
        val profiles = mergeProfiles(local.profiles, remote.profiles, remoteIsAuthority)

        // Tekilleştirilen Varsayılan profilleri tespit et
        val allEligibleDefaults = (local.profiles + remote.profiles).filter(::isEligibleDefault)
        val candidateDefaultIds = allEligibleDefaults.map { it.id }.distinct()
        val keptDefaultId = if (candidateDefaultIds.size > 1) chooseKeptDefaultId(candidateDefaultIds) else null
        val discardedDefaultIds = if (keptDefaultId != null) candidateDefaultIds.filter { it != keptDefaultId }.toSet() else emptySet()

        val remappedIds = if (keptDefaultId != null) {
            discardedDefaultIds.associateWith { keptDefaultId }
        } else {
            emptyMap()
        }

        val mergedTombstones = mergeDeletedHistory(local.deletedHistory, remote.deletedHistory, remappedIds, nowMs)
        val mergedFavoriteTombstones = mergeDeletedFavorites(local.deletedFavorites, remote.deletedFavorites, remappedIds, nowMs)

        val libraries = profiles.associate { p ->
            val profileTombstones = mergedTombstones.filter { it.profileId == p.id }
            val profileFavTombstones = mergedFavoriteTombstones.filter { it.profileId == p.id }
            val base = mergeLibrary(
                local = decode(local.backups[p.id]),
                remote = decode(remote.backups[p.id]),
                tombstones = profileTombstones,
                favoriteTombstones = profileFavTombstones,
            )
            val finalLib = if (p.id == keptDefaultId) {
                discardedDefaultIds.fold(base) { acc, dId ->
                    val withLocal = mergeLibrary(acc, decode(local.backups[dId]), profileTombstones, profileFavTombstones)
                    mergeLibrary(withLocal, decode(remote.backups[dId]), profileTombstones, profileFavTombstones)
                }
            } else {
                base
            }
            p.id to finalLib
        }

        val activeId = resolveActiveProfileId(localActiveProfileId, profiles, remappedIds)

        val localByUrl = local.sources.associateBy { urlKey(it.sourceUrl) }
        val missing = remote.sources.filter { urlKey(it.sourceUrl) !in localByUrl }.distinctBy { urlKey(it.sourceUrl) }
        val states = if (remoteIsAuthority) remote.sources.filter { urlKey(it.sourceUrl) in localByUrl } else emptyList()
        return LocalSyncPlan(
            profiles = profiles,
            libraries = libraries,
            missingSources = missing,
            sourceStates = states,
            remappedProfileIds = remappedIds,
            activeProfileId = activeId,
            deletedHistory = mergedTombstones,
            deletedFavorites = mergedFavoriteTombstones,
        )
    }

    fun mergeProfiles(
        local: List<LocalProfileSummary>,
        remote: List<LocalProfileSummary>,
        remoteIsAuthority: Boolean,
    ): List<LocalProfileSummary> {
        val remoteById = remote.associateBy { it.id }
        val merged = local.map { mine ->
            val theirs = remoteById[mine.id]
            if (theirs != null && remoteIsAuthority) {
                forTransfer(theirs).copy(pinHash = mine.pinHash, syncCode = mine.syncCode)
            } else {
                mine
            }
        }
        val known = local.mapTo(HashSet()) { it.id }
        val allMerged = merged + remote.filter { it.id !in known }.distinctBy { it.id }.map(::forTransfer)

        val eligibleDefaults = allMerged.filter(::isEligibleDefault)
        if (eligibleDefaults.size <= 1) return allMerged

        val keptId = chooseKeptDefaultId(eligibleDefaults.map { it.id }) ?: return allMerged
        val discardedIds = eligibleDefaults.map { it.id }.filter { it != keptId }.toSet()

        return allMerged.filter { it.id !in discardedIds }
    }

    fun mergeLibrary(
        local: LocalProfileBackup?,
        remote: LocalProfileBackup?,
        tombstones: List<DeletedHistoryItem> = emptyList(),
        favoriteTombstones: List<DeletedFavoriteItem> = emptyList(),
    ): LocalProfileBackup {
        val base = local ?: remote ?: return LocalProfileBackup()
        val history = LinkedHashMap<String, HistoryBackup>()
        for (h in (local?.history.orEmpty() + remote?.history.orEmpty())) {
            val key = historyKey(h)
            val current = history[key]
            if (current == null || newer(h, current)) history[key] = h
        }
        val favorites = LinkedHashMap<String, FavoriteBackup>()
        for (f in (local?.favorites.orEmpty() + remote?.favorites.orEmpty())) {
            val key = "${f.tmdbId}:${f.type.lowercase()}"
            val current = favorites[key]
            if (current == null || f.addedAt > current.addedAt) favorites[key] = f
        }
        val filteredHistory = filterHistoryWithTombstones(history.values.toList(), tombstones)
        val filteredFavorites = filterFavoritesWithTombstones(favorites.values.toList(), favoriteTombstones)
        return base.copy(
            history = filteredHistory.sortedByDescending { it.lastWatchedAt },
            favorites = filteredFavorites.sortedByDescending { it.addedAt },
        )
    }

    private inline fun <T> pruneTombstones(
        items: List<T>,
        nowMs: Long,
        maxCount: Int,
        retentionMs: Long,
        crossinline profileOf: (T) -> String,
        crossinline mediaKeyOf: (T) -> String,
        crossinline deletedAtOf: (T) -> Long,
    ): List<T> {
        val cutoff = nowMs - retentionMs
        return items
            .filter { deletedAtOf(it) >= cutoff }
            .groupBy { "${profileOf(it)}:${mediaKeyOf(it)}" }
            .values
            .map { group -> group.maxByOrNull { deletedAtOf(it) }!! }
            .groupBy { profileOf(it) }
            .values
            .flatMap { profileGroup ->
                profileGroup.sortedByDescending { deletedAtOf(it) }.take(maxCount)
            }
            .sortedByDescending { deletedAtOf(it) }
    }

    /** Mezar taşlarını profil başına en çok [maxCount] (500) ve [retentionMs] (90 gün) sınırlarıyla budar. */
    fun pruneDeletedHistory(
        items: List<DeletedHistoryItem>,
        nowMs: Long = System.currentTimeMillis(),
        maxCount: Int = MAX_DELETED_HISTORY_PER_PROFILE,
        retentionMs: Long = DELETED_HISTORY_RETENTION_MS,
    ): List<DeletedHistoryItem> = pruneTombstones(
        items = items,
        nowMs = nowMs,
        maxCount = maxCount,
        retentionMs = retentionMs,
        profileOf = { it.profileId },
        mediaKeyOf = { "${it.tmdbId}:${it.type.lowercase()}" },
        deletedAtOf = { it.deletedAt },
    )

    /** Favori mezar taşlarını profil başına en çok [maxCount] (500) ve [retentionMs] (90 gün) sınırlarıyla budar. */
    fun pruneDeletedFavorites(
        items: List<DeletedFavoriteItem>,
        nowMs: Long = System.currentTimeMillis(),
        maxCount: Int = MAX_DELETED_HISTORY_PER_PROFILE,
        retentionMs: Long = DELETED_HISTORY_RETENTION_MS,
    ): List<DeletedFavoriteItem> = pruneTombstones(
        items = items,
        nowMs = nowMs,
        maxCount = maxCount,
        retentionMs = retentionMs,
        profileOf = { it.profileId },
        mediaKeyOf = { "${it.tmdbId}:${it.type.lowercase()}" },
        deletedAtOf = { it.deletedAt },
    )

    /** İki tarafın mezar taşlarını birleştirir (aynı içerik için en yeni deletedAt), remapped profil kimliklerini taşır ve budar. */
    fun mergeDeletedHistory(
        local: List<DeletedHistoryItem>,
        remote: List<DeletedHistoryItem>,
        remappedProfileIds: Map<String, String> = emptyMap(),
        nowMs: Long = System.currentTimeMillis(),
    ): List<DeletedHistoryItem> {
        val combined = (local + remote).map { item ->
            val targetProfId = remappedProfileIds[item.profileId] ?: item.profileId
            if (targetProfId != item.profileId) item.copy(profileId = targetProfId) else item
        }
        return pruneDeletedHistory(combined, nowMs)
    }

    /** İki tarafın favori mezar taşlarını birleştirir, remapped profil kimliklerini taşır ve budar. */
    fun mergeDeletedFavorites(
        local: List<DeletedFavoriteItem>,
        remote: List<DeletedFavoriteItem>,
        remappedProfileIds: Map<String, String> = emptyMap(),
        nowMs: Long = System.currentTimeMillis(),
    ): List<DeletedFavoriteItem> {
        val combined = (local + remote).map { item ->
            val targetProfId = remappedProfileIds[item.profileId] ?: item.profileId
            if (targetProfId != item.profileId) item.copy(profileId = targetProfId) else item
        }
        return pruneDeletedFavorites(combined, nowMs)
    }

    /**
     * Mezar taşlarına göre favorileri filtreler: favori ancak `addedAt > deletedAt` ise kalır.
     */
    fun filterFavoritesWithTombstones(
        favorites: List<FavoriteBackup>,
        tombstones: List<DeletedFavoriteItem>,
    ): List<FavoriteBackup> {
        if (favorites.isEmpty() || tombstones.isEmpty()) return favorites
        val newestDeleteByKey = tombstones
            .groupBy { "${it.tmdbId}:${it.type.lowercase()}" }
            .mapValues { (_, items) -> items.maxOf { it.deletedAt } }
        return favorites.filterNot { f ->
            val key = "${f.tmdbId}:${f.type.lowercase()}"
            val deletedAt = newestDeleteByKey[key]
            deletedAt != null && deletedAt >= f.addedAt
        }
    }

    /**
     * Mezar taşlarına göre geçmişi filtreler: aynı içerik için deletedAt >= lastWatchedAt ise elenir;
     * lastWatchedAt > deletedAt ise (silindikten sonra tekrar izlenmiş) korunur.
     * Dizilerde mezar taşı (type=tv) tüm bölümleri kapsar.
     */
    fun filterHistoryWithTombstones(
        history: List<HistoryBackup>,
        tombstones: List<DeletedHistoryItem>,
    ): List<HistoryBackup> {
        if (history.isEmpty() || tombstones.isEmpty()) return history
        val tombstoneMap = tombstones.associateBy { "${it.tmdbId}:${it.type.lowercase()}" }
        return history.filterNot { h ->
            val key = "${h.tmdbId}:${h.type.lowercase()}"
            val t = tombstoneMap[key]
            t != null && t.deletedAt >= h.lastWatchedAt
        }
    }

    /** Aynı içerik: film ya da dizinin aynı sezon+bölümü. */
    fun historyKey(h: HistoryBackup): String = "${h.tmdbId}:${h.type.lowercase()}:${h.season}:${h.episode}"

    private fun newer(candidate: HistoryBackup, current: HistoryBackup): Boolean =
        candidate.lastWatchedAt > current.lastWatchedAt ||
            (candidate.lastWatchedAt == current.lastWatchedAt && candidate.positionMs > current.positionMs)

    /** Adres karşılaştırması: baştaki/sondaki boşluk ve sondaki "/" yok sayılır; şema ve alan adı küçük harfe iner. */
    fun urlKey(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        val schemeEnd = trimmed.indexOf("://")
        if (schemeEnd < 0) return trimmed
        val hostEnd = trimmed.indexOf('/', schemeEnd + 3).let { if (it < 0) trimmed.length else it }
        return trimmed.substring(0, hostEnd).lowercase() + trimmed.substring(hostEnd)
    }

    fun encodeBackup(backup: LocalProfileBackup): String = json.encodeToString(LocalProfileBackup.serializer(), backup)

    fun decodeBackup(raw: String?): LocalProfileBackup? = decode(raw)

    private fun decode(raw: String?): LocalProfileBackup? =
        raw?.takeIf { it.isNotBlank() }?.let {
            try {
                json.decodeFromString(LocalProfileBackup.serializer(), it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    fun encode(transfer: LocalProfileTransfer): String = json.encodeToString(LocalProfileTransfer.serializer(), transfer)

    /** Bozuk/uyumsuz gövde: [IllegalArgumentException]. */
    fun decodeTransfer(raw: String): LocalProfileTransfer = json.decodeFromString(LocalProfileTransfer.serializer(), raw)
}
