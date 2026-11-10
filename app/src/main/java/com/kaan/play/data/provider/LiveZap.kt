package com.kaan.play.data.provider

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.stream.StreamRequest
import java.util.UUID

/** Oynatıcı içi kanal geçişi (zap) için hafif ve Android'den bağımsız kanal öğesi. */
data class LiveZapChannel(
    val channelKey: String,
    val title: String,
    val logoUrl: String? = null,
    val routes: List<LiveSourceRoute>,
    val isAvailable: Boolean = true,
)

/** Oynatıcının kanal listesini hangi kaynaktan aldığını gösterir. */
enum class LiveZapListSource {
    VisibleList,
    Catalog,
    Unavailable,
}

/** Ardışık zap isteklerinde yalnızca en güncel isteğin uygulanmasını sağlayan sıra bileti. */
data class LiveZapTicket(
    val generation: Long,
    val requestedIndex: Int,
)

/** Canlı TV kanal geçişi (zap) ve kaynak listesi belirleme kuralları. */
object LiveZapRules {

    /**
     * [channels] listesinde [currentIndex]'ten [direction] (<0 geri, >0 ileri) yönünde adım adım
     * liste uçlarından sararak ilerler; `isAvailable == true` ve `routes` boş olmayan ilk kanalın
     * indeksini döndürür. Tek/boş liste, 0 yön veya uygun kanal yoksa null.
     */
    fun nextAvailableIndex(
        channels: List<LiveZapChannel>,
        currentIndex: Int,
        direction: Int,
    ): Int? {
        if (channels.size <= 1 || direction == 0) return null
        if (currentIndex !in channels.indices) return null
        val step = if (direction < 0) -1 else 1
        val n = channels.size
        for (offset in 1 until n) {
            val raw = currentIndex + step * offset
            val candidate = ((raw % n) + n) % n
            val channel = channels[candidate]
            if (channel.isAvailable && channel.routes.isNotEmpty()) {
                return candidate
            }
        }
        return null
    }

    /** Katalog kimliği varsa [LiveZapListSource.Catalog], yoksa bellekte liste varsa [VisibleList], ikisi de yoksa [Unavailable]. */
    fun sourceFor(catalogId: String?, holderHasList: Boolean): LiveZapListSource = when {
        !catalogId.isNullOrBlank() -> LiveZapListSource.Catalog
        holderHasList -> LiveZapListSource.VisibleList
        else -> LiveZapListSource.Unavailable
    }

    /** [LiveSourceRoute]'u oynatıcı akış isteğine dönüştürür. */
    fun toStreamRequest(route: LiveSourceRoute, title: String): StreamRequest = StreamRequest(
        tmdbId = 0,
        imdbId = null,
        type = MediaType.Tv,
        pluginId = route.pluginId,
        addonType = route.addonType,
        addonContentId = route.contentId,
        dataUrl = route.dataUrl,
        title = title,
    )
}

/**
 * Canlı TV kanal listesini Intent yerine bellek içinde tutan, iş parçacığı güvenli geçici depo.
 * En fazla [MAX_CAPACITY] (12) liste saklar; aşılınca en eski eklenen silinir.
 */
object LiveZapListHolder {
    const val MAX_CAPACITY = 12

    private val lock = Any()
    private val entries = LinkedHashMap<String, List<LiveZapChannel>>(MAX_CAPACITY, 0.75f, false)

    fun put(channels: List<LiveZapChannel>): String {
        val key = UUID.randomUUID().toString()
        val snapshot = channels.toList()
        synchronized(lock) {
            entries[key] = snapshot
            while (entries.size > MAX_CAPACITY) {
                val eldestKey = entries.entries.first().key
                entries.remove(eldestKey)
            }
        }
        return key
    }

    fun get(key: String?): List<LiveZapChannel>? {
        if (key.isNullOrBlank()) return null
        return synchronized(lock) { entries[key] }
    }

    fun remove(key: String?) {
        if (key.isNullOrBlank()) return
        synchronized(lock) { entries.remove(key) }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }
}

/**
 * Hızlı ardışık kanal değiştirme basışlarında eski asenkron sonuçların yeni seçimi ezmesini önler.
 */
class LiveZapRequestSequencer {
    private val lock = Any()
    private var generation = 0L

    fun issue(requestedIndex: Int): LiveZapTicket = synchronized(lock) {
        generation += 1L
        LiveZapTicket(generation = generation, requestedIndex = requestedIndex)
    }

    fun isLatest(ticket: LiveZapTicket): Boolean = synchronized(lock) {
        ticket.generation > 0L && ticket.generation == generation
    }
}
