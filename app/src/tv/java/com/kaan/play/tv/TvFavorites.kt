package com.kaan.play.tv

import android.content.Context
import com.kaan.play.PlayApp
import com.kaan.play.data.library.ProviderContentId
import com.kaan.play.data.library.ProviderFavoriteRefs
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * TV favorileri = Play'in favorileri (tek liste): kayıt Play'in `favorites` tablosundadır, Wi-Fi ve bulut eşitlemesi
 * telefona taşır; telefonda eklenenler burada görünür. Kimlik Play'deki gibidir: TMDB içeriğinde TMDB kimliği, eklenti
 * içeriğinde detayın çözdüğü kimlik (TMDB ya da [ProviderContentId] negatif kimliği) + tür.
 *
 * Play'in satırı eklentiyi ve içerik adresini tutmaz; TV'de eklenen eklenti içeriğinin açılış bilgisi ([DetailRef]) bu
 * cihazda ayrıca saklanır ("kimlik:tür" → DetailRef). Bilgisi olmayan negatif kimlikli favori, aynı kimlikli izleme
 * geçmişi satırının eklentisi + adresiyle açılır (Play'in PermanentIdMap kuralı), o da yoksa Play'in ortak açılış kaydıyla
 * ([ProviderFavoriteRefs]: telefonda eklenip Wi-Fi eşitlemesiyle gelen, bu TV'nin eklentisine çevrilmiş bilgi); hiçbiri
 * yoksa açılamaz ve listede gösterilmez. TV'de eklenenlerin bilgisi de o ortak kayda yazılır ki Wi-Fi telefona taşısın.
 * Eski TV listesi (SharedPreferences, profil başına) o profil ilk etkin olduğunda bir kez Play tablosuna taşınır; eski
 * metin silinmez, `backup:` anahtarıyla kalır.
 */
class TvFavorites private constructor(context: Context) {
    private val container = (context as PlayApp).container
    private val prefs = context.getSharedPreferences("play_tv_favorites", Context.MODE_PRIVATE)
    private val library get() = container.library
    private val refs = MutableStateFlow(readRefs())
    private val shared = ProviderFavoriteRefs(context)

    /** Etkin profilin favorileri, en yeni önde; açılabilen her biri TV detay adresiyle. */
    val items: StateFlow<List<DetailRef>> =
        combine(container.library.allFavorites, refs, container.library.watchHistory) { favs, map, history ->
            favs.mapNotNull { f ->
                val image = TmdbImages.url(f.posterPath, TmdbImages.Size.Poster)
                map[key(f.tmdbId, f.type)]?.let { return@mapNotNull it.copy(title = f.title.ifBlank { it.title }, imageUrl = it.imageUrl ?: image) }
                if (f.tmdbId > 0) return@mapNotNull DetailRef(TMDB_PROVIDER, "${f.type.tmdbPath}:${f.tmdbId}", true, f.title, image)
                history.firstOrNull { it.tmdbId == f.tmdbId && it.type == f.type && it.pluginId?.startsWith("cs:") == true && it.contentUrl != null }
                    ?.let { return@mapNotNull DetailRef(it.pluginId!!, it.contentUrl!!, false, f.title, image ?: it.posterPath) }
                shared.get(f.type, f.tmdbId)?.let { (providerId, url) -> DetailRef(providerId, url, false, f.title, image) }
            }
        }.stateIn(container.appScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Etkin profilde favori olan "kimlik:tür" anahtarları (detaydaki kalp düğmesi). */
    val keys: StateFlow<Set<String>> = container.library.allFavorites.map { list -> list.map { key(it.tmdbId, it.type) }.toSet() }
        .stateIn(container.appScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        // Bu sürümden önce TV'de eklenen eklenti favorilerinin bilgisi ortak kayda bir kez kopyalanır (Wi-Fi telefona taşısın).
        refs.value.forEach { (k, r) -> copyToShared(k, r) }
        container.appScope.launch(Dispatchers.IO) {
            container.localProfile.libraryScope.distinctUntilChanged().collect { (_, profileId) ->
                // Profil henüz kesinleşmemişken ("legacy-profile") eski liste taşınmaz; etkin profil gelince taşınır.
                if (profileId != "legacy-profile") runCatching { migrate(profileId) }
                    .onFailure { android.util.Log.w("PlayTv", "eski TV favorileri taşınamadı: ${it.javaClass.simpleName}") }
            }
        }
    }

    /** Detaydaki içerik favori mi: Play kimliğiyle ya da (eski listeden başka kimlikle taşınmışsa) aynı eklenti + adresle. */
    fun isFavorite(ref: DetailRef, tmdbId: Int, type: MediaType, keys: Set<String>): Boolean =
        key(tmdbId, type) in keys || refs.value.any { (k, r) -> k in keys && r.providerId == ref.providerId && r.url == ref.url }

    /** Favoriye ekler ya da çıkarır (etkin profil). [posterPath]: Play satırının afişi (TMDB yolu ya da tam adres). */
    fun toggle(ref: DetailRef, tmdbId: Int, type: MediaType, posterPath: String?) {
        container.appScope.launch(Dispatchers.IO) {
            val k = key(tmdbId, type)
            val sameRef = refs.value.filterValues { it.providerId == ref.providerId && it.url == ref.url }.keys
            val existing = library.allFavorites.first().filter { key(it.tmdbId, it.type).let { x -> x == k || x in sameRef } }
            if (existing.isNotEmpty()) {
                existing.forEach { library.removeFavorite(it.type, it.tmdbId) }
            } else {
                if (ref.providerId.startsWith("cs:")) putRef(k, ref)
                library.setFavorite(summary(tmdbId, type, ref.title, posterPath), true)
            }
        }
    }

    private fun summary(tmdbId: Int, type: MediaType, title: String, posterPath: String?) =
        MediaSummary(id = tmdbId, type = type, title = title, overview = "", posterPath = posterPath, backdropPath = null, year = null, rating = null)

    /**
     * Eski TV listesi → Play favorileri (etkin profil); sıra korunur (en yeni önde: en eskisi önce yazılır, eklenme anı
     * artsın diye aralarında kısa bekleme). Eklenti içeriğinin kimliği geçmişten, yoksa adresten.
     */
    private suspend fun migrate(profileId: String) {
        val k = "$KEY_PREFIX$profileId"
        val raws = listOfNotNull(prefs.getString(k, null), prefs.getString(LEGACY_KEY, null))
        if (raws.isEmpty()) return
        val old = raws.flatMap(::parse).distinctBy { it.providerId to it.url }
        val history = library.watchHistory.first()
        for (ref in old.asReversed()) {
            val (tmdbId, type) = identityOf(ref, history) ?: continue
            if (ref.providerId.startsWith("cs:")) putRef(key(tmdbId, type), ref)
            library.setFavorite(summary(tmdbId, type, ref.title, ref.imageUrl), true)
            delay(2)
        }
        val edit = prefs.edit()
        prefs.getString(k, null)?.let { edit.putString("backup:$k", it).remove(k) }
        prefs.getString(LEGACY_KEY, null)?.let { edit.putString("backup:$LEGACY_KEY", it).remove(LEGACY_KEY) }
        edit.apply()
        android.util.Log.i("PlayTv", "eski TV favorileri Play favorilerine taşındı: ${old.size}")
    }

    private fun identityOf(ref: DetailRef, history: List<com.kaan.play.data.db.WatchHistoryEntity>): Pair<Int, MediaType>? {
        if (ref.providerId == TMDB_PROVIDER) {
            val (path, id) = ref.url.split(":").let { it.getOrNull(0) to it.getOrNull(1)?.toIntOrNull() }
            val type = MediaType.entries.firstOrNull { it.tmdbPath == path } ?: return null
            return (id ?: return null) to type
        }
        if (!ref.providerId.startsWith("cs:")) return null
        history.firstOrNull { it.contentUrl == ref.url && it.pluginId?.startsWith("cs:") == true }?.let { return it.tmdbId to it.type }
        // İzlenmemiş eklenti içeriği: kalıcı kimlik; tür bilinmez, dramalar dizi olduğu için dizi.
        return ProviderContentId.of(ref.providerId, ref.url) to MediaType.Tv
    }

    /** "kimlik:TÜR" anahtarlı eklenti bilgisini Play'in ortak kaydına yazar (orada yoksa). */
    private fun copyToShared(k: String, ref: DetailRef) {
        if (!ref.providerId.startsWith("cs:")) return
        val id = k.substringBefore(':').toIntOrNull() ?: return
        val type = MediaType.entries.firstOrNull { it.name == k.substringAfter(':') } ?: return
        if (id < 0 && shared.get(type, id) == null) shared.put(type, id, ref.providerId, ref.url)
    }

    private fun putRef(k: String, ref: DetailRef) {
        copyToShared(k, ref)
        val now = refs.value + (k to ref.copy(landscape = false))
        refs.value = now
        val obj = JSONObject()
        now.forEach { (key, r) -> obj.put(key, r.toJson()) }
        prefs.edit().putString(REFS_KEY, obj.toString()).apply()
    }

    private fun readRefs(): Map<String, DetailRef> = runCatching {
        val obj = JSONObject(prefs.getString(REFS_KEY, "{}")!!)
        obj.keys().asSequence().associateWith { obj.getJSONObject(it).toRef() }
    }.getOrDefault(emptyMap())

    private fun parse(raw: String): List<DetailRef> = runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getJSONObject(it).toRef() }
    }.getOrDefault(emptyList())

    private fun DetailRef.toJson() = JSONObject().put("p", providerId).put("u", url).put("l", landscape).put("t", title).put("i", imageUrl)

    private fun JSONObject.toRef() = DetailRef(getString("p"), getString("u"), optBoolean("l"), optString("t"), optString("i").ifBlank { null })

    companion object {
        private const val LEGACY_KEY = "items"
        private const val KEY_PREFIX = "items:"
        private const val REFS_KEY = "refs"

        internal fun key(tmdbId: Int, type: MediaType) = "$tmdbId:${type.name}"

        @Volatile private var instance: TvFavorites? = null
        fun get(context: Context): TvFavorites =
            instance ?: synchronized(this) { instance ?: TvFavorites(context.applicationContext).also { instance = it } }
    }
}
