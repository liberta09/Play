package com.kaan.play.data.sync.local

import com.kaan.play.data.db.FavoriteDao
import com.kaan.play.data.db.WatchHistoryDao
import com.kaan.play.data.db.WatchHistoryEntity
import com.kaan.play.data.library.PermanentIdMap
import com.kaan.play.data.library.ProviderContentId
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.sync.DeletedHistoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * İzleme geçmişindeki CloudStream eklenti numarası (`cs:N`, eklenti satırının kimliği) cihaza özeldir. Wi-Fi eşitlemesi
 * kaydı karşı cihazın numarasıyla getirdiğinde bu cihazda başka bir eklentiyi gösterebilir (10 Kasım 2026: telefonda 718 =
 * Dramacix, TV'de 718 = kapalı DiziMom; "Devam et" yanlış eklentiyi açıyordu).
 *
 * Onarım: kaydın içerik adresinin alan adı, numaradaki eklentinin site adreslerinden biri değilse ve bu alan adı yalnız tek
 * bir etkin eklentinin site adresiyse kayıt o eklentiye taşınır. Aynı anahtarda (tmdbId/tür/sezon/bölüm) başka kayıt varsa
 * daha yeni izlenen kalır. Karar her cihazda yalnız kendi eklentilerine bakarak verilir; karşı cihazın numarasına güvenilmez.
 *
 * Kimlik geçişi ([migrateIds]): TMDB'de bulunamayan eklenti içeriğinin eski kimliği numaradan türetiliyordu
 * ([ProviderContentId.legacy]); kayıtlar kalıcı kimliğe ([ProviderContentId.of]) taşınır. Eklenti yüklemez, ucuzdur.
 */
class HistoryPluginRepair(
    private val history: WatchHistoryDao,
    /** Etkin CloudStream eklentisi (satır kimliği) → site adreslerinin alan adları (`www.` atılmış, küçük harf). */
    private val pluginHosts: suspend () -> Map<Long, Set<String>>,
    /**
     * Etkin eklenti satırı → eklentinin iç adı (internalName). Aynı eklenti iki depodan kurulmuşsa (ör. iki
     * depoda Dramacix) adaylar aynı eklentinin kopyasıdır; belirsiz sayılmaz.
     */
    private val pluginNames: suspend () -> Map<Long, String> = { emptyMap() },
    /** Her taşıma ve her başarısızlık için tek satır (eski → yeni numara, alan adı; başlık yazılmaz). */
    private val log: (String) -> Unit = {},
    /** Kimlik geçişinde aynı içeriğin favorisi de yeni kimliğe taşınır; null: favorilere dokunulmaz (testler). */
    private val favorites: FavoriteDao? = null,
    /** Kayıtlı mezar taşları (geçiş artığı temizliği için); boş: temizlik yok (testler). */
    private val tombstones: suspend () -> List<DeletedHistoryItem> = { emptyList() },
    /** Verilen mezar taşlarını siler. */
    private val dropTombstones: suspend (List<DeletedHistoryItem>) -> Unit = {},
) {
    private val lock = Mutex()

    /**
     * Eşitleme ve açılış yolunun çağırdığı biçim: aynı anda tek onarım (TV açılışı, Wi-Fi birleştirmesi ve kaynak onayı üst
     * üste gelebilir). Hata eşitlemeyi bozmaz: eklenti kodundan gelen Error dahil yakalanır, satıra
     * yazılır (sürekli başarısızlık "onarılacak kayıt yok"tan ayrılsın) ve 0 döner. İptal yutulmaz.
     */
    suspend fun repairSafely(userId: String, profileIds: Collection<String>): Int = lock.withLock {
        try {
            repair(userId, profileIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("geçmiş eklenti onarımı başarısız: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            0
        }
    }

    /** [migrateIds]'in eşitleme/açılış biçimi: [repairSafely] ile aynı kilit, hata 0 + log. */
    suspend fun migrateIdsSafely(userId: String, profileIds: Collection<String>): Int = lock.withLock {
        try {
            migrateIds(userId, profileIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log("içerik kimliği geçişi başarısız: ${e.javaClass.simpleName}")
            0
        }
    }

    /**
     * Eski (eklenti numaralı) kimlikli satırları kalıcı kimliğe taşır; taşınan satır sayısı. Yalnız kimliği tam olarak eski
     * hesapla üretilmiş satıra dokunur (TMDB kimliği ya da başka eklentinin kimliği değişmez). Aynı içeriğin favorisi de taşınır.
     *
     * Eski kimliğe mezar taşı BIRAKILMAZ: bulut ve Wi-Fi'den gelen eski kimlikli kopyalar birleştirmede kalıcı kimliğe
     * çevrilip aynı satıra katılır (LocalSyncMerge.withPermanentIds). Mezar taşı, güncellenmemiş bir cihazın (aynı cs:N)
     * henüz taşınmamış bölümünü iki cihazdan da siliyordu. 1.1.52/0.2.14 test derlemesinin bıraktığı
     * bu tür mezar taşları ([staleTombstones]) temizlenir.
     */
    suspend fun migrateIds(userId: String, profileIds: Collection<String>): Int {
        var moved = 0
        for (profileId in profileIds) {
            var list = history.all(userId, profileId)
            val renamed = mutableMapOf<Pair<Int, MediaType>, Int>()
            var rows = 0
            for (id in list.map { it.id }) {
                val row = list.firstOrNull { it.id == id } ?: continue
                val plugin = row.pluginId ?: continue
                val url = row.contentUrl ?: continue
                if (row.tmdbId != ProviderContentId.legacy(plugin, url)) continue
                val newId = ProviderContentId.of(plugin, url)
                if (newId == row.tmdbId) continue
                rekey(userId, profileId, list, row, row.copy(tmdbId = newId))
                renamed[row.tmdbId to row.type] = newId
                list = history.all(userId, profileId)
                rows++
            }
            // Adressiz satırlar (daha önce buluttan gelmiş kopyalar): bu cihazın adresli satırlarından öğrenilen eşlemeyle
            // (birleştirmeyle aynı kural) kalıcı kimliğe katılır, eklenti + adres de alınır. Eşitlemede çevrilip yazılan kopyanın
            // yanında veritabanında kalan eski kopya da böylece aynı satıra katlanır.
            val map = PermanentIdMap.from(list.asSequence().map { PermanentIdMap.Row(it.tmdbId, it.pluginId, it.contentUrl, it.title, it.type.name) })
            if (!map.isEmpty) {
                for (id in list.filter { it.contentUrl == null }.map { it.id }) {
                    val row = list.firstOrNull { it.id == id } ?: continue
                    val target = map.target(row.tmdbId, row.title, row.type.name) ?: continue
                    val src = map.source(target)
                    rekey(userId, profileId, list, row, row.copy(tmdbId = target, pluginId = src.pluginId, contentUrl = src.contentUrl))
                    renamed[row.tmdbId to row.type] = target
                    list = history.all(userId, profileId)
                    rows++
                }
            }
            moved += rows
            // Favoriler: bu geçişte taşınan içeriklerin ve eşlenebilen bütün eski kimlikli favoriler.
            var favs = 0
            favorites?.let { dao ->
                for (fav in dao.all(userId, profileId)) {
                    val newId = renamed[fav.tmdbId to fav.type] ?: map.target(fav.tmdbId, fav.title, fav.type.name) ?: continue
                    if (newId == fav.tmdbId) continue
                    dao.add(fav.copy(tmdbId = newId)) // önce yenisi: arada favori kaybolmaz (aynısı varsa yerine yazılır)
                    dao.remove(userId, profileId, fav.tmdbId, fav.type)
                    favs++
                }
            }
            if (renamed.isNotEmpty() || favs > 0) log("içerik kimliği geçişi: ${renamed.size} içerik, $rows satır, $favs favori")
            val stale = staleTombstones(profileId, list, tombstones())
            if (stale.isNotEmpty()) {
                dropTombstones(stale)
                log("içerik kimliği geçişi: ${stale.size} eski mezar taşı temizlendi")
            }
        }
        return moved
    }

    /**
     * Verilen profillerin geçmişini onarır; düzeltilen kayıt sayısı. Hiç `cs:` kaydı yoksa eklentiler yüklenmez. Site haritası
     * her çağrıda yeniden okunur (önbellek yok: onarım yalnız Wi-Fi eşitlemesinde ve TV açılışında çalışır, eklenti değişikliği
     * hemen görülür).
     */
    suspend fun repair(userId: String, profileIds: Collection<String>): Int {
        if (profileIds.none { p -> history.all(userId, p).any { it.pluginId?.startsWith("cs:") == true && it.contentUrl != null } }) return 0
        val hosts = pluginHosts()
        val names = pluginNames()
        var fixed = 0
        for (profileId in profileIds) {
            // Eklentiler yüklendikten SONRA okunur: yükleme sürerken kaydedilen izleme ilerlemesi eski kopyayla ezilmez
            //. Her taşımadan sonra güncel liste: çakışma denetimi önceki taşımaları da görür.
            var list = history.all(userId, profileId)
            for (id in list.map { it.id }) {
                val row = list.firstOrNull { it.id == id } ?: continue
                val target = target(row.pluginId, row.contentUrl, hosts, names) ?: continue
                val url = row.contentUrl ?: continue
                val plugin = row.pluginId!!
                // Kimlik eklentiden türetilmişse (eski ya da kalıcı biçim) yeni eklentiyle yeniden hesaplanır; kalıcı biçimde
                // eklenti türü aynı kaldığından değişmez. TMDB kimliği olduğu gibi kalır.
                val derived = row.tmdbId == ProviderContentId.legacy(plugin, url) || row.tmdbId == ProviderContentId.of(plugin, url)
                val tmdbId = if (derived) ProviderContentId.of(target, url) else row.tmdbId
                log("geçmiş eklenti onarımı: $plugin -> $target (${host(url)})")
                rekey(userId, profileId, list, row, row.copy(pluginId = target, tmdbId = tmdbId))
                list = history.all(userId, profileId)
                fixed++
            }
        }
        return fixed
    }

    /**
     * [row]'u [replacement] anahtarına taşır. Aynı anahtarda (tmdbId/tür/sezon/bölüm) başka satır varsa daha yeni izlenen kalır.
     * Tam eşitlikte adresini taşıyan satır kalır. Aynı satır kimliğiyle REPLACE tek adımdır (eski satır yerinde değişir, arada kayıp olmaz); çakışan eski satırı Room
     * benzersiz indeks yüzünden zaten siler, açıkça silinmesi niyeti gösterir.
     */
    private suspend fun rekey(
        userId: String,
        profileId: String,
        list: List<WatchHistoryEntity>,
        row: WatchHistoryEntity,
        replacement: WatchHistoryEntity,
    ) {
        val clash = list.firstOrNull {
            it.id != row.id && it.tmdbId == replacement.tmdbId && it.type == row.type && it.season == row.season && it.episode == row.episode
        }
        // Tam eşitlikte adresini taşıyan satır kalır.
        val keepClash = clash != null && (clash.lastWatchedAt > row.lastWatchedAt ||
            clash.lastWatchedAt == row.lastWatchedAt && (clash.contentUrl != null || replacement.contentUrl == null))
        if (keepClash) {
            history.delete(userId, profileId, row.id)
        } else {
            if (clash != null) history.delete(userId, profileId, clash.id)
            history.upsert(replacement)
        }
    }

    companion object {
        /**
         * Geçiş artığı mezar taşları: eski kimlikte, ama o içeriğin satırları bu cihazda hâlâ duruyor ve hepsi mezar taşından
         * eski. Gerçek bir silmede o satırlar silinmiş olurdu; kalmışlarsa mezar taşı silme değil, kimlik taşımanın izidir
         * (1.1.52/0.2.14 test derlemesi). Bırakılırsa güncellenmemiş cihazlara gidip oradaki eski kimlikli satırları siler.
         */
        fun staleTombstones(
            profileId: String,
            rows: List<WatchHistoryEntity>,
            tombstones: List<DeletedHistoryItem>,
        ): List<DeletedHistoryItem> = tombstones.filter { t ->
            if (t.profileId != profileId || t.tmdbId >= 0) return@filter false
            val content = rows.filter { r ->
                val plugin = r.pluginId
                val url = r.contentUrl
                plugin != null && url != null && r.type.name.equals(t.type, ignoreCase = true) &&
                    ProviderContentId.legacy(plugin, url) == t.tmdbId
            }
            content.isNotEmpty() && content.all { it.lastWatchedAt <= t.deletedAt }
        }

        fun host(url: String?): String? =
            runCatching { java.net.URI(url ?: return null).host?.lowercase()?.removePrefix("www.") }.getOrNull()

        /** Kaydın taşınacağı eklenti (`cs:M`); numara doğruysa, alan adı bilinmiyorsa ya da aday tek değilse null. */
        fun target(pluginId: String?, contentUrl: String?, hosts: Map<Long, Set<String>>, names: Map<Long, String> = emptyMap()): String? {
            val id = pluginId?.takeIf { it.startsWith("cs:") }?.removePrefix("cs:")?.toLongOrNull() ?: return null
            val host = host(contentUrl) ?: return null
            val own = hosts[id]
            if (own?.contains(host) == true) return null
            // Kendi eklentisi etkin ama o an yüklenemediyse (site listesi boş) kayıt doğru olabilir; dokunulmaz.
            if (own != null && own.isEmpty()) return null
            val candidates = hosts.filter { (other, set) -> other != id && host in set }.keys
            if (candidates.isEmpty()) return null
            // Birden çok aday yalnız hepsi aynı eklentinin kopyasıysa çözülür (en küçük numara, iki cihazda da kararlı).
            val sameCode = candidates.map { names[it] }.distinct().singleOrNull() != null
            return if (candidates.size == 1 || sameCode) "cs:${candidates.min()}" else null
        }
    }
}
