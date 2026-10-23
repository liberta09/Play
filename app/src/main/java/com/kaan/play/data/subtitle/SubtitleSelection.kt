package com.kaan.play.data.subtitle

/**
 * Altyazı menüsündeki tek seçenek. Sıra anlamlıdır: videonun içindekiler önce, sonra akışın kendi altyazıları, sonra
 * altyazı addon'ları (aynı dilde ilk gelen varsayılan seçilir).
 */
sealed interface SubtitleItem {
    /** [SubtitleLanguages.normalize] ile iki harfli temel dil; bilinmiyorsa null. */
    val language: String?

    /**
     * Videonun kendi (Media3'ün okuduğu) altyazı parçası; [index] oynatıcının parça listesindeki sıradır, [label] parçanın
     * kendi adı (ör. "Forced"), yoksa null.
     */
    data class Embedded(val index: Int, override val language: String?, val label: String? = null) : SubtitleItem

    /** Adresten indirilen altyazı (akışın, altyazı addon'unun ya da eklentinin verdiği). */
    data class External(val track: SubtitleTrack) : SubtitleItem {
        override val language: String? get() = track.language
    }
}

/** Menüde bir dilin altındaki seçenekler. [language] null: dili bilinmeyenler. */
data class SubtitleGroup(val language: String?, val name: String, val items: List<SubtitleItem>)

object SubtitleSelection {

    const val MAX_PER_ADDON_LANGUAGE = 5

    /** URL normalizasyonu (boşluklar, fragment ve gereksiz / temizliği). */
    fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        val noFrag = trimmed.substringBefore('#')
        return if (!noFrag.contains('?') && noFrag.endsWith('/')) noFrag.dropLast(1) else noFrag
    }

    /** Altyazı dosya/sürüm etiketini temizler ve en çok 24 karaktere kırpar. */
    fun cleanLabel(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var cleaned = raw.trim()
        for (ext in listOf(".srt", ".vtt", ".sub", ".ass", ".ssa")) {
            if (cleaned.endsWith(ext, ignoreCase = true)) {
                cleaned = cleaned.dropLast(ext.length).trim()
            }
        }
        return if (cleaned.length > 24) cleaned.take(24).trimEnd() + "…" else cleaned
    }

    /** Aynı adres tekrarlarını ayıklar (ilk görülen kalır); videonun içindekiler adresi olmadığı için elenmez. */
    fun dedupe(items: List<SubtitleItem>): List<SubtitleItem> {
        val seen = HashSet<String>()
        return items.filter { it !is SubtitleItem.External || seen.add(normalizeUrl(it.track.url)) }
    }

    /**
     * (a) Gerçekten aynı içerik/adres olanları tek satıra indirir.
     * (b) Aynı addon + aynı dilde en çok [maxPerAddonLang] (varsayılan 5) satır gösterir (fazlası listeye girmez).
     * (c) Aynı addon + aynı dilde birden fazla FARKLI altyazı kalırsa satırları ayırt edilebilir yapar:
     *     kaynak adı yanında kısa bir ayırt edici (dosya/sürüm adı varsa kırpılmış hâli, yoksa "1", "2", "3" sırası).
     */
    fun dedupeAndDisambiguate(
        items: List<SubtitleItem>,
        maxPerAddonLang: Int = MAX_PER_ADDON_LANGUAGE,
    ): List<SubtitleItem> {
        val deduped = dedupe(items)
        val result = mutableListOf<SubtitleItem>()

        // Dile göre grupla
        val byLang = deduped.groupBy { it.language }
        for ((_, langItems) in byLang) {
            val embedded = langItems.filterIsInstance<SubtitleItem.Embedded>()
            val external = langItems.filterIsInstance<SubtitleItem.External>()

            // Harici altyazıları kaynak/addon (origin) bazında sınırla ve ayırt edici etiket ver
            val byOrigin = external.groupBy { it.track.origin }
            val disambiguatedExternal = mutableListOf<SubtitleItem.External>()

            for ((_, originItems) in byOrigin) {
                val kept = originItems.take(maxPerAddonLang)
                if (kept.size <= 1) {
                    disambiguatedExternal.addAll(kept)
                } else {
                    // Birden fazla parça var: ayırt edici ekle
                    val cleanedLabels = kept.map { cleanLabel(it.track.label) }
                    val allDistinct = cleanedLabels.all { !it.isNullOrBlank() } && cleanedLabels.distinct().size == kept.size

                    kept.forEachIndexed { index, item ->
                        val badge = if (allDistinct) {
                            cleanedLabels[index]
                        } else {
                            (index + 1).toString()
                        }
                        val updatedTrack = item.track.copy(label = badge)
                        disambiguatedExternal.add(SubtitleItem.External(updatedTrack))
                    }
                }
            }

            // Orijinal öncelik sırasını koru (video içi > akış > dosya > addon > çevrimiçi)
            val keptUrls = disambiguatedExternal.associateBy { normalizeUrl(it.track.url) }
            for (item in langItems) {
                when (item) {
                    is SubtitleItem.Embedded -> result.add(item)
                    is SubtitleItem.External -> {
                        keptUrls[normalizeUrl(item.track.url)]?.let {
                            result.add(it)
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Dile göre gruplar. Sıra: [preferred] sırasıyla (genelde arayüz dili, İngilizce), sonra kalan diller görünen adına göre
     * alfabetik, en sonda dili bilinmeyenler. Grup içi sıra [items] sırasıdır. [nameOf]: dil kodu -> görünen ad (arayüz dilinde);
     * [unknownName]: dili bilinmeyen grubun adı.
     */
    fun groups(
        items: List<SubtitleItem>,
        preferred: List<String>,
        nameOf: (String) -> String,
        unknownName: String,
    ): List<SubtitleGroup> {
        val processed = dedupeAndDisambiguate(items)
        val byLanguage = processed.groupBy { it.language }
        val known = byLanguage.keys.filterNotNull()
        val ordered = preferred.filter { it in known } +
            known.filter { it !in preferred }.sortedBy { nameOf(it).lowercase() }
        return buildList {
            for (language in ordered) add(SubtitleGroup(language, nameOf(language), byLanguage.getValue(language)))
            byLanguage[null]?.let { add(SubtitleGroup(null, unknownName, it)) }
        }
    }

    /**
     * Otomatik seçim için dil sırası. [remembered]: bu dizide/filmde daha önce seçilen dil ([REMEMBERED_OFF]: kullanıcı
     * altyazıyı kapattıysa hiçbir şey seçilmez). Hatırlanan dil yoksa ya da videoda bulunmuyorsa uygulama dili, sonra İngilizce.
     */
    fun preferredLanguages(remembered: String?, appLanguage: String): List<String> = when (remembered) {
        REMEMBERED_OFF -> emptyList()
        null -> listOf(appLanguage, "en").distinct()
        else -> listOf(remembered, appLanguage, "en").distinct()
    }

    /** Kullanıcı bu içerikte altyazıyı bilerek kapattı (dil olarak saklanır). */
    const val REMEMBERED_OFF = "off"

    /** [language]'in tercih sırasındaki yeri (0 en iyi); listede yoksa null. */
    fun rank(language: String?, preferred: List<String>): Int? =
        language?.let { preferred.indexOf(it).takeIf { i -> i >= 0 } }

    /**
     * Varsayılan seçim: tercih sırasındaki en iyi dilin ilk seçeneği; hiçbiri yoksa null (altyazı kapalı kalır).
     * Aynı dilde videonun içindeki parça, akışın ve addon'ların önüne geçer (sıra [items] sırasıdır).
     */
    fun pickDefault(items: List<SubtitleItem>, preferred: List<String>): SubtitleItem? {
        var best: SubtitleItem? = null
        var bestRank = Int.MAX_VALUE
        for (item in dedupe(items)) {
            val r = rank(item.language, preferred) ?: continue
            if (r < bestRank) {
                best = item
                bestRank = r
            }
        }
        return best
    }
}

/** Bir altyazının satırlarını oynatma konumuna göre bulur. [SubtitleCue] zamanları kaydırılmaz; kaydırma sorguda uygulanır. */
class SubtitleTimeline(cues: List<SubtitleCue>) {
    private val cues: List<SubtitleCue> = cues.sortedBy { it.startMs }
    private val starts = LongArray(this.cues.size) { this.cues[it].startMs }

    /** En uzun satır süresi: geriye bakarken nerede duracağımızı sınırlar (üst üste binen satırlar için). */
    private val longest: Long = minOf(this.cues.maxOfOrNull { it.endMs - it.startMs } ?: 0L, MAX_REASONABLE_DURATION_MS)

    val size: Int get() = cues.size

    private companion object {
        const val MAX_LOOKBACK = 32
        const val MAX_REASONABLE_DURATION_MS = 30_000L
    }

    /**
     * [positionMs] anında görünen satırlar (başlangıç sırasıyla). [offsetMs] > 0: altyazı o kadar GEÇ görünür, < 0: erken.
     * Yani satır, oynatma konumu `başlangıç + offset` ile `bitiş + offset` arasındayken gösterilir.
     */
    fun activeAt(positionMs: Long, offsetMs: Long = 0L): List<SubtitleCue> {
        val t = positionMs - offsetMs
        // t'den büyük başlangıcı olmayan son satırı bul.
        var lo = 0
        var hi = starts.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (starts[mid] <= t) lo = mid + 1 else hi = mid
        }
        val active = ArrayList<SubtitleCue>(2)
        var i = lo - 1
        var scanned = 0
        // Her satırın sonunu da denetler ama sonsuza dek geriye gitmez: uç değerli (bölüm boyu süren) bir satır taramayı uzatmasın.
        while (i >= 0 && starts[i] > t - longest && scanned++ < MAX_LOOKBACK) {
            if (cues[i].endMs > t) active += cues[i]
            i--
        }
        active.reverse()
        return active
    }
}

/** Elle senkron kaydırması (milisaniye; + altyazıyı geciktirir, − öne alır). */
object SubtitleOffset {
    const val COARSE_MS = 500L
    const val FINE_MS = 100L

    /** ±10 dakika: farklı kesim/kare hızı farkları için yeter, yanlışlıkla uçmasın. */
    const val LIMIT_MS = 600_000L

    fun step(current: Long, deltaMs: Long): Long = (current + deltaMs).coerceIn(-LIMIT_MS, LIMIT_MS)
}
