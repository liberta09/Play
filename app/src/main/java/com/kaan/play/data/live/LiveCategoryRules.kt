package com.kaan.play.data.live

import com.kaan.play.R
import java.text.Normalizer

enum class LiveCategory(
    val id: String,
    val titleRes: Int,
    val keywords: List<String>,
) {
    BeIn(
        id = "bein",
        titleRes = R.string.live_category_bein,
        keywords = listOf("bein"),
    ),
    Tabii(
        id = "tabii",
        titleRes = R.string.live_category_tabii,
        keywords = listOf("tabii"),
    ),
    Tivibu(
        id = "tivibu",
        titleRes = R.string.live_category_tivibu,
        keywords = listOf("tivibu"),
    ),
    Exxen(
        id = "exxen",
        titleRes = R.string.live_category_exxen,
        keywords = listOf("exxen"),
    ),
    SSport(
        id = "s_sport",
        titleRes = R.string.live_category_s_sport,
        keywords = listOf("s sport"),
    ),
    OtherSports(
        id = "other_sports",
        titleRes = R.string.live_category_other_sports,
        keywords = listOf("spor", "sport", "eurosport", "a spor", "trt spor", "nba", "tjk", "fight"),
    ),
    News(
        id = "news",
        titleRes = R.string.live_category_news,
        keywords = listOf("haber", "news", "cnn", "ntv", "trt haber", "a haber", "tv100", "halk tv", "sozcu", "tgrt haber", "bloomberg", "euronews"),
    ),
    Kids(
        id = "kids",
        titleRes = R.string.live_category_kids,
        keywords = listOf("cocuk", "kids", "cartoon", "minika", "disney", "nick", "baby", "trt cocuk"),
    ),
    Documentary(
        id = "documentary",
        titleRes = R.string.live_category_documentary,
        keywords = listOf("belgesel", "documentary", "nat geo", "national geographic", "discovery", "history", "bbc earth", "animal planet", "yaban"),
    ),
    Yesilcam(
        id = "yesilcam",
        titleRes = R.string.live_category_yesilcam,
        keywords = listOf("yesilcam"),
    ),
    Cinema(
        id = "cinema",
        titleRes = R.string.live_category_cinema,
        keywords = listOf("sinema", "cinema", "movie", "film", "hbo", "cinemax", "sinema tv"),
    ),
    Music(
        id = "music",
        titleRes = R.string.live_category_music,
        keywords = listOf("muzik", "music", "mtv", "kral", "power", "number1", "dream"),
    ),
    Local(
        id = "local",
        titleRes = R.string.live_category_local,
        keywords = listOf("yerel", "local", "belediye"),
    ),
    National(
        id = "national",
        titleRes = R.string.live_category_national,
        keywords = listOf("trt 1", "kanal d", "show tv", "star tv", "atv", "fox", "tv8", "kanal 7", "now"),
    ),
    Other(
        id = "other",
        titleRes = R.string.live_category_other,
        keywords = emptyList(),
    );
}

object LiveCategoryRules {

    /**
     * Kanal adını küçük harfe ve Türkçe karakter/aksan katlamaya tabi tutar.
     * Boş/null metin için boş dize döner.
     */
    fun fold(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val sb = StringBuilder(text.length)
        for (ch in text.trim()) {
            when (ch) {
                'ı', 'I', 'i', 'İ' -> sb.append('i')
                'ğ', 'Ğ' -> sb.append('g')
                'ü', 'Ü' -> sb.append('u')
                'ş', 'Ş' -> sb.append('s')
                'ö', 'Ö' -> sb.append('o')
                'ç', 'Ç' -> sb.append('c')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        val decomposed = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    /**
     * Kanal adını sıralı ilk eşleşme mantığıyla 15 kategoriden birine sınıflandırır.
     */
    fun classify(channelName: String?): LiveCategory {
        val normalized = fold(channelName)
        if (normalized.isBlank()) return LiveCategory.Other

        for (category in LiveCategory.entries) {
            if (category == LiveCategory.Other) continue
            if (category.keywords.any { normalized.contains(it) }) {
                return category
            }
        }
        return LiveCategory.Other
    }
}
