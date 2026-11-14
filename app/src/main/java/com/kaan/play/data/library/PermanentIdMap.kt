package com.kaan.play.data.library

/**
 * Adres taşımayan eklenti içeriği satırını (bulut kopyası, eski kimlikli favori) kalıcı kimliğe ([ProviderContentId.of])
 * çevirmek için eşleme. Adresini taşıyan satırlardan öğrenilir: "eski kimlik → kalıcı kimlik" (eski hesabı satırın kendi
 * eklenti + adresinden) ve "başlık + tür → kalıcı kimlik". Önce eski kimlik, o yoksa başlık tek bir kalıcı kimliğe gidiyorsa
 * başlık kullanılır; belirsiz başlığa dokunulmaz. Birleştirme (LocalSyncMerge.withPermanentIds) ve cihazdaki geçiş
 * (HistoryPluginRepair.migrateIds) aynı kuralı kullanır.
 */
class PermanentIdMap private constructor(
    private val sources: Map<Int, Source>,
    private val byLegacy: Map<String, Int>,
    private val byTitle: Map<String, Set<Int>>,
) {
    /** Kalıcı kimliğin eklentisi ve içerik adresi (çevrilen satır bunları alır, kart açılabilir olur). */
    data class Source(val pluginId: String, val contentUrl: String)

    /** Eşlemenin girdisi; [type] "tv"/"movie" (büyük/küçük harf fark etmez). */
    class Row(val tmdbId: Int, val pluginId: String?, val contentUrl: String?, val title: String, val type: String)

    val isEmpty: Boolean get() = sources.isEmpty()

    /** Çevrilecek kalıcı kimlik; null: dokunma (TMDB kimliği, zaten bilinen kalıcı kimlik, eşleme yok ya da başlık belirsiz). */
    fun target(tmdbId: Int, title: String, type: String): Int? {
        if (tmdbId >= 0 || tmdbId in sources) return null
        return byLegacy[legacyKey(tmdbId, type)] ?: byTitle[titleKey(title, type)]?.singleOrNull()
    }

    fun source(permanentId: Int): Source = sources.getValue(permanentId)

    companion object {
        /** [rows] içinde ilk gelen kaynak öncelikli: yerel taraf önce verilmeli (eklenti numarası bu cihazınki olsun). */
        fun from(rows: Sequence<Row>): PermanentIdMap {
            val sources = LinkedHashMap<Int, Source>()
            val byLegacy = HashMap<String, Int>()
            val byTitle = HashMap<String, MutableSet<Int>>()
            for (r in rows) {
                val plugin = r.pluginId ?: continue
                val url = r.contentUrl ?: continue
                if (r.tmdbId >= 0) continue
                val legacy = ProviderContentId.legacy(plugin, url)
                val permanent = ProviderContentId.of(plugin, url)
                if (r.tmdbId != legacy && r.tmdbId != permanent) continue // eklentiden türetilmemiş kimlik
                sources.putIfAbsent(permanent, Source(plugin, url))
                byLegacy.putIfAbsent(legacyKey(legacy, r.type), permanent)
                byTitle.getOrPut(titleKey(r.title, r.type)) { mutableSetOf() } += permanent
            }
            return PermanentIdMap(sources, byLegacy, byTitle)
        }

        private fun legacyKey(tmdbId: Int, type: String) = "$tmdbId:${type.lowercase()}"

        private fun titleKey(title: String, type: String) = "${title.trim().lowercase()}:${type.lowercase()}"
    }
}
