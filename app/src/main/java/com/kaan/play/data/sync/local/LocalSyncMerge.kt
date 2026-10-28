package com.kaan.play.data.sync.local

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
) {
    val historyCount: Int get() = libraries.values.sumOf { it.history.size }
}

/**
 * Wi-Fi senkronizasyonunun birleştirme kuralları (Android'den bağımsız, birim testli). İki cihaz birbirinin anlık
 * görüntüsünü alır ve aynı kurallarla birleştirir; böylece sonunda aynı veriye sahip olurlar:
 *
 * - **Profiller:** kimliğe göre birleşim. PIN hiçbir zaman aktarılmaz ([forTransfer]); yerel profil PIN'ini korur.
 * - **Geçmiş / kaldığı yer:** aynı içerikte (film ya da sezon+bölüm) en yeni `lastWatchedAt` kazanır; eşitlikte daha ileri konum.
 * - **Favoriler:** birleşim.
 * - **Kaynaklar:** yalnızca karşıda olanlar [LocalSyncPlan.missingSources] olur (onay sorulur). İki tarafta da olanlarda
 *   açık/kapalı, eklenti ayarları ve katalog anahtarları **kodu gösteren cihazınki** esas alınır: bağlanan (istemci)
 *   cihaz `remoteIsAuthority = true` ile çağırır ve bunları uygular, sunucu kendi durumunu korur.
 */
object LocalSyncMerge {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
    ): LocalProfileTransfer = LocalProfileTransfer(
        profiles = profiles.map(::forTransfer),
        backups = libraries.mapValues { encodeBackup(it.value) },
        sources = sources,
        deviceName = deviceName,
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

    fun plan(local: LocalProfileTransfer, remote: LocalProfileTransfer, remoteIsAuthority: Boolean): LocalSyncPlan {
        val profiles = mergeProfiles(local.profiles, remote.profiles, remoteIsAuthority)
        val libraries = profiles.associate { p ->
            p.id to mergeLibrary(decode(local.backups[p.id]), decode(remote.backups[p.id]))
        }
        val localByUrl = local.sources.associateBy { urlKey(it.sourceUrl) }
        val missing = remote.sources.filter { urlKey(it.sourceUrl) !in localByUrl }.distinctBy { urlKey(it.sourceUrl) }
        val states = if (remoteIsAuthority) remote.sources.filter { urlKey(it.sourceUrl) in localByUrl } else emptyList()
        return LocalSyncPlan(profiles, libraries, missing, states)
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
        return merged + remote.filter { it.id !in known }.distinctBy { it.id }.map(::forTransfer)
    }

    fun mergeLibrary(local: LocalProfileBackup?, remote: LocalProfileBackup?): LocalProfileBackup {
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
        return base.copy(
            history = history.values.sortedByDescending { it.lastWatchedAt },
            favorites = favorites.values.sortedByDescending { it.addedAt },
        )
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
