package com.kaan.play.data.sync.local

import com.kaan.play.data.db.PluginEntity
import com.kaan.play.data.library.PermanentIdMap
import com.kaan.play.data.library.ProviderContentId
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.sync.DeletedFavoriteItem
import com.kaan.play.data.sync.DeletedHistoryItem
import com.kaan.play.data.sync.DeletedLibraryItem
import com.kaan.play.data.sync.FavoriteSourceTransfer
import com.kaan.play.data.sync.FavoriteBackup
import com.kaan.play.data.sync.HistoryBackup
import com.kaan.play.data.sync.LocalProfileBackup
import com.kaan.play.data.sync.LocalProfileSummary
import com.kaan.play.data.sync.LocalProfileTransfer
import com.kaan.play.data.sync.SourceTransferItem
import kotlinx.serialization.json.Json

/** Bir cihazın birleştirmeden sonra yazacakları. */
data class LocalSyncPlan(
    /** Birleşik profil listesi (PIN: [LocalSyncMerge.mergeProfiles] kuralı). */
    val profiles: List<LocalProfileSummary>,
    /** Profil başına birleşik geçmiş + favoriler. */
    val libraries: Map<String, LocalProfileBackup>,
    /** Karşıda olup burada olmayan kaynaklar: kendiliğinden eklenmez, kullanıcıya sorulur. */
    val missingSources: List<SourceTransferItem>,
    /** İki cihazda da olan kaynakların uygulanacak durumları (yalnızca karşı taraf esas alınıyorsa dolu). */
    val sourceStates: List<SourceTransferItem>,
    /** İki cihazda da olan kaynakların karşıdaki hali (her yönde dolu): karşıda açık olup burada kurulu olmayan eklentiler için. */
    val sharedSources: List<SourceTransferItem> = emptyList(),
    /** Tekilleştirilen Varsayılan profillerin eski kimlik -> korunan kimlik haritası. */
    val remappedProfileIds: Map<String, String> = emptyMap(),
    /** Birleştirmeden sonra uygulanacak aktif profil kimliği (atılan kimlikteyse korunana geçer). */
    val activeProfileId: String? = null,
    /** İki tarafın birleşik ve budanmış geçmiş mezar taşları. */
    val deletedHistory: List<DeletedHistoryItem> = emptyList(),
    /** İki tarafın birleşik ve budanmış Wi-Fi silinenler kaydı (bölüm, geçmişi temizle, favori). */
    val deletedItems: List<DeletedLibraryItem> = emptyList(),
    /** Karşı cihazın eklenti favorisi açılış bilgileri (yalnız Wi-Fi; bulutta hep boş). */
    val favoriteSources: List<FavoriteSourceTransfer> = emptyList(),
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
 * - **Profiller:** kimliğe göre birleşim. PIN özeti yalnız Wi-Fi'da (şifreli kanal) aktarılır, buluta hiç girmez ([forTransfer]);
 *   iki tarafta da olan profilde PIN [pinWinner] kuralıyla seçilir (karşıdan gelen değişiklik yerel PIN'i kaldıramaz/değiştiremez).
 *   PIN'siz ve adı tam olarak [DEFAULT_NAME] olan profiller tekilleştirilir; daha eski/sabit kimlik ([DEFAULT_ID] ya da
 *   deterministik en küçük kimlik) korunur, atılan kimliğin kitaplığı (geçmiş + favori) korunana eklenir.
 * - **Geçmiş / kaldığı yer:** aynı içerikte (film ya da sezon+bölüm) en yeni `lastWatchedAt` kazanır; eşitlikte daha ileri konum.
 * - **Favoriler:** birleşim.
 * - **Silinenler:** içerik düzeyindeki mezar taşları ([DeletedHistoryItem], bulutta da) ve yalnız Wi-Fi'de taşınan bölüm /
 *   "geçmişi temizle" / favori kayıtları ([DeletedLibraryItem]) birleşimden önce süzer; silme anından eski kopya geri gelmez.
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

    /** Bulut aktarımı için: PIN, PIN damgası ve bulut kodu çıkarılır. */
    fun forTransfer(profile: LocalProfileSummary): LocalProfileSummary = profile.copy(pinHash = null, syncCode = null, pinUpdatedAt = 0L)

    /** Wi-Fi aktarımı için: yalnız bulut kodu çıkarılır; PIN özeti (PBKDF2) ve damgası şifreli yerel kanalda gider. */
    fun forWifi(profile: LocalProfileSummary): LocalProfileSummary = profile.copy(syncCode = null)

    /**
     * İki cihazda da olan profilde geçerli PIN: karşıdan gelen değişiklik yerel PIN'i **kaldıramaz ve değiştiremez** (ebeveyn
     * kilidi atlatılamasın). Yerelde PIN varsa yerel kalır; yalnız yerelde PIN yokken karşının PIN'i alınır (daha kısıtlı
     * olan kazanır). Damga gelecekten gelemez: karşının [LocalProfileSummary.pinUpdatedAt] değeri [nowMs]'e kırpılır.
     * [remoteIsAuthority] PIN kararında rol oynamaz (eski imza korunur).
     */
    @Suppress("UNUSED_PARAMETER")
    fun pinWinner(
        mine: LocalProfileSummary,
        theirs: LocalProfileSummary,
        remoteIsAuthority: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): LocalProfileSummary = when {
        mine.pinHash != null -> mine
        theirs.pinHash != null -> theirs.copy(pinUpdatedAt = theirs.pinUpdatedAt.coerceIn(0L, nowMs))
        else -> mine
    }

    /**
     * Bir cihazın anlık görüntüsü (Wi-Fi ve bulut aynı yükü kullanır). Bulut kodu hiç, PIN yalnız [includePins] (Wi-Fi) ise girer; kaynaklarda
     * yalnızca adresler ve durumlar vardır (eklenti JS/DEX dosyaları taşınmaz, karşı cihaz kendisi indirir).
     */
    fun snapshot(
        profiles: List<LocalProfileSummary>,
        libraries: Map<String, LocalProfileBackup>,
        sources: List<SourceTransferItem>,
        deviceName: String,
        deletedHistory: List<DeletedHistoryItem> = emptyList(),
        includePins: Boolean = false,
        deletedItems: List<DeletedLibraryItem> = emptyList(),
        favoriteSources: List<FavoriteSourceTransfer> = emptyList(),
        deletedFavorites: List<DeletedFavoriteItem> = emptyList(),
    ): LocalProfileTransfer = LocalProfileTransfer(
        profiles = profiles.map(if (includePins) ::forWifi else ::forTransfer),
        backups = libraries.mapValues { encodeBackup(it.value) },
        sources = sources,
        deviceName = deviceName,
        deletedHistory = deletedHistory,
        deletedItems = deletedItems,
        favoriteSources = favoriteSources,
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
        val mergedItems = mergeDeletedItems(local.deletedItems, remote.deletedItems, remappedIds, nowMs)
        val mergedFavoriteTombstones = mergeDeletedFavorites(local.deletedFavorites, remote.deletedFavorites, remappedIds, nowMs)

        val libraries = profiles.associate { p ->
            val profileTombstones = mergedTombstones.filter { it.profileId == p.id }
            val profileItems = mergedItems.filter { it.profileId == p.id }
            val profileFavTombstones = mergedFavoriteTombstones.filter { it.profileId == p.id }
            val base = mergeLibrary(
                localRaw = decode(local.backups[p.id]),
                remoteRaw = decode(remote.backups[p.id]),
                tombstones = profileTombstones,
                deletedItems = profileItems,
                favoriteTombstones = profileFavTombstones,
            )
            val finalLib = if (p.id == keptDefaultId) {
                discardedDefaultIds.fold(base) { acc, dId ->
                    val withLocal = mergeLibrary(acc, decode(local.backups[dId]), profileTombstones, profileItems, profileFavTombstones)
                    mergeLibrary(withLocal, decode(remote.backups[dId]), profileTombstones, profileItems, profileFavTombstones)
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
            sharedSources = remote.sources.filter { urlKey(it.sourceUrl) in localByUrl }.distinctBy { urlKey(it.sourceUrl) },
            remappedProfileIds = remappedIds,
            activeProfileId = activeId,
            deletedHistory = mergedTombstones,
            deletedItems = mergedItems,
            favoriteSources = remote.favoriteSources,
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
            val theirs = remoteById[mine.id] ?: return@map mine
            val base = if (remoteIsAuthority) forWifi(theirs).copy(syncCode = mine.syncCode) else mine
            val pin = pinWinner(mine, theirs, remoteIsAuthority)
            base.copy(pinHash = pin.pinHash, pinUpdatedAt = pin.pinUpdatedAt)
        }
        val known = local.mapTo(HashSet()) { it.id }
        val now = System.currentTimeMillis()
        val allMerged = merged + remote.filter { it.id !in known }.distinctBy { it.id }.map(::forWifi)
            .map { it.copy(pinUpdatedAt = it.pinUpdatedAt.coerceIn(0L, now)) }

        val eligibleDefaults = allMerged.filter(::isEligibleDefault)
        if (eligibleDefaults.size <= 1) return allMerged

        val keptId = chooseKeptDefaultId(eligibleDefaults.map { it.id }) ?: return allMerged
        val discardedIds = eligibleDefaults.map { it.id }.filter { it != keptId }.toSet()

        return allMerged.filter { it.id !in discardedIds }
    }

    fun mergeLibrary(
        localRaw: LocalProfileBackup?,
        remoteRaw: LocalProfileBackup?,
        tombstones: List<DeletedHistoryItem> = emptyList(),
        deletedItems: List<DeletedLibraryItem> = emptyList(),
        favoriteTombstones: List<DeletedFavoriteItem> = emptyList(),
    ): LocalProfileBackup {
        val (local, remote) = withPermanentIds(localRaw, remoteRaw)
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
            .filterNot { isHistoryDeleted(it.tmdbId, it.type, it.season, it.episode, it.lastWatchedAt, deletedItems) }
        val filteredFavorites = filterFavoritesWithTombstones(favorites.values.toList(), favoriteTombstones)
        return base.copy(
            history = filteredHistory.sortedByDescending { it.lastWatchedAt },
            favorites = filteredFavorites.filterNot { isFavoriteDeleted(it.tmdbId, it.type, it.addedAt, deletedItems) }
                .sortedByDescending { it.addedAt },
        )
    }

    /**
     * Eklenti içeriğinin eski (cihaza özel cs:N ile türetilmiş) kimliklerini birleştirmeden ve mezar taşı süzmesinden ÖNCE
     * kalıcı kimliğe ([ProviderContentId.of]) çevirir; iki tarafa birlikte bakar.
     *
     * 1. Adresini taşıyan satır doğrudan çevrilir (eski kimliği birebir eski hesapsa).
     * 2. Adres taşımayan satır (bulut kopyası) ve favori: adresli satırlardan öğrenilen "eski kimlik → kalıcı kimlik"
     *    eşlemesiyle, o yoksa aynı başlık + tür tek bir kalıcı kimliğe gidiyorsa onunla çevrilir; eklenti ve içerik adresi de
     *    o satırdan alınır (kart açılabilir olur). Başlık birden çok kalıcı kimliğe gidiyorsa dokunulmaz.
     *
     * Böylece güncellenmemiş bir cihazın eski kimlikli bölümü silinmeden aynı içeriğe katılır,
     * taşınmış favorinin eski kopyası ayrı favori olarak geri gelmez. Eşleme yalnız bu iki yedekten öğrenilir.
     */
    fun withPermanentIds(
        local: LocalProfileBackup?,
        remote: LocalProfileBackup?,
    ): Pair<LocalProfileBackup?, LocalProfileBackup?> {
        val sides = listOfNotNull(local, remote)
        if (sides.none { b -> b.history.any { it.tmdbId < 0 } || b.favorites.any { it.tmdbId < 0 } }) return local to remote
        // Yerel taraf önce: çevrilen satır bu cihazın eklenti numarasını alsın.
        val map = PermanentIdMap.from(
            sides.asSequence().flatMap { it.history.asSequence() }
                .map { PermanentIdMap.Row(it.tmdbId, it.pluginId, it.contentUrl, it.title, it.type) },
        )
        if (map.isEmpty) return local to remote
        fun convert(b: LocalProfileBackup?): LocalProfileBackup? {
            b ?: return null
            val history = b.history.map { h ->
                val plugin = h.pluginId
                val url = h.contentUrl
                if (plugin != null && url != null) {
                    return@map if (h.tmdbId < 0 && h.tmdbId == ProviderContentId.legacy(plugin, url)) {
                        h.copy(tmdbId = ProviderContentId.of(plugin, url))
                    } else h
                }
                val id = map.target(h.tmdbId, h.title, h.type) ?: return@map h
                val src = map.source(id)
                h.copy(tmdbId = id, pluginId = src.pluginId, contentUrl = src.contentUrl)
            }
            val favorites = b.favorites.map { f -> map.target(f.tmdbId, f.title, f.type)?.let { f.copy(tmdbId = it) } ?: f }
            return b.copy(history = history, favorites = favorites)
        }
        return convert(local) to convert(remote)
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
     * Wi-Fi silinenler kaydını budar: aynı kayıt (profil + tür + içerik + bölüm) için en yeni silme; [retentionMs] (90 gün)
     * ve profil başına her türden en çok [maxCount] (500) kayıt. "Geçmişi temizle" kaydı profil başına tektir (en yenisi).
     */
    fun pruneDeletedItems(
        items: List<DeletedLibraryItem>,
        nowMs: Long = System.currentTimeMillis(),
        maxCount: Int = MAX_DELETED_HISTORY_PER_PROFILE,
        retentionMs: Long = DELETED_HISTORY_RETENTION_MS,
    ): List<DeletedLibraryItem> {
        val cutoff = nowMs - retentionMs
        return items
            .filter { it.deletedAt >= cutoff && it.kind in KNOWN_KINDS }
            .groupBy { "${it.profileId}:${it.kind}:${it.tmdbId}:${it.type.lowercase()}:${it.season}:${it.episode}" }
            .values
            .map { group -> group.maxBy { it.deletedAt } }
            .groupBy { it.profileId to it.kind }
            .values
            .flatMap { group -> group.sortedByDescending { it.deletedAt }.take(maxCount) }
            .sortedByDescending { it.deletedAt }
    }

    /** İki tarafın Wi-Fi silinenler kaydını birleştirir, tekilleştirilen profil kimliklerini taşır ve budar. */
    fun mergeDeletedItems(
        local: List<DeletedLibraryItem>,
        remote: List<DeletedLibraryItem>,
        remappedProfileIds: Map<String, String> = emptyMap(),
        nowMs: Long = System.currentTimeMillis(),
    ): List<DeletedLibraryItem> = pruneDeletedItems(
        (local + remote).map { item -> remappedProfileIds[item.profileId]?.let { item.copy(profileId = it) } ?: item },
        nowMs,
    )

    /**
     * Geçmiş satırı Wi-Fi silinenler kaydıyla silinmiş mi: aynı bölüm (ya da film) kaydı ya da profilin "geçmişi temizle"
     * kaydı, satırın son izlenme anından yeni ya da eşitse. [items] tek profile ait olmalı.
     */
    fun isHistoryDeleted(tmdbId: Int, type: String, season: Int, episode: Int, lastWatchedAt: Long, items: List<DeletedLibraryItem>): Boolean =
        items.any { d ->
            d.deletedAt >= lastWatchedAt && when (d.kind) {
                DeletedLibraryItem.KIND_CLEAR -> true
                DeletedLibraryItem.KIND_EPISODE -> d.tmdbId == tmdbId && d.type.equals(type, ignoreCase = true) &&
                    d.season == season && d.episode == episode
                else -> false
            }
        }

    /** Favori Wi-Fi silinenler kaydıyla çıkarılmış mı (çıkarma anından önce eklenmişse). [items] tek profile ait olmalı. */
    fun isFavoriteDeleted(tmdbId: Int, type: String, addedAt: Long, items: List<DeletedLibraryItem>): Boolean =
        items.any { d ->
            d.kind == DeletedLibraryItem.KIND_FAVORITE && d.tmdbId == tmdbId && d.type.equals(type, ignoreCase = true) && d.deletedAt >= addedAt
        }

    /**
     * Gönderilecek eklenti favorisi açılış bilgileri: yalnız bir profilde favori olan negatif kimlikli içerik, CS eklentisi
     * (`cs:N`) ve bu cihazda adı bilinen eklenti. [pluginNames]: eklenti satırı → iç adı (CS `internalName`).
     */
    fun outgoingFavoriteSources(
        refs: Map<Pair<MediaType, Int>, Pair<String, String>>,
        favorites: Set<Pair<MediaType, Int>>,
        pluginNames: Map<Long, String>,
    ): List<FavoriteSourceTransfer> = refs.mapNotNull { (key, ref) ->
        val (type, id) = key
        if (id >= 0 || key !in favorites) return@mapNotNull null
        val row = ref.first.takeIf { it.startsWith("cs:") }?.removePrefix("cs:")?.toLongOrNull() ?: return@mapNotNull null
        val name = pluginNames[row] ?: return@mapNotNull null
        FavoriteSourceTransfer(id, type.tmdbPath, name, ref.second)
    }.sortedBy { it.tmdbId }

    /**
     * Karşıdan gelen açılış bilgisinin bu cihazdaki karşılığı (tür, `cs:N`): aynı iç adlı eklenti (açık olan önce, sonra en
     * küçük numara; TV'nin telefondan oynatma çevirisiyle aynı kural). Kimlik adresle tutmuyorsa, adres http(s) değilse ya
     * da eklenti bu cihazda yoksa null (sonraki eşitlemede yeniden denenir).
     */
    fun favoriteSourceTarget(item: FavoriteSourceTransfer, plugins: List<PluginEntity>): Pair<MediaType, String>? {
        val type = MediaType.fromTmdb(item.type) ?: return null
        val url = item.contentUrl
        if (item.tmdbId >= 0 || url.length > 2048 || !url.matches(Regex("^https?://\\S+$", RegexOption.IGNORE_CASE))) return null
        val plugin = plugins.filter { it.pluginId == item.pluginName }.sortedWith(compareBy({ !it.enabled }, { it.id })).firstOrNull()
            ?: return null
        val providerId = "cs:${plugin.id}"
        if (ProviderContentId.of(providerId, url) != item.tmdbId) return null
        return type to providerId
    }

    private val KNOWN_KINDS = setOf(DeletedLibraryItem.KIND_EPISODE, DeletedLibraryItem.KIND_CLEAR, DeletedLibraryItem.KIND_FAVORITE)

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

    /** Tam eşitlikte adresini taşıyan satır kazanır (adressiz bulut kopyası eklenti/adres/oynatma verisini silmesin). */
    private fun newer(candidate: HistoryBackup, current: HistoryBackup): Boolean =
        candidate.lastWatchedAt > current.lastWatchedAt ||
            (candidate.lastWatchedAt == current.lastWatchedAt && candidate.positionMs > current.positionMs) ||
            (candidate.lastWatchedAt == current.lastWatchedAt && candidate.positionMs == current.positionMs &&
                current.contentUrl == null && candidate.contentUrl != null)

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
