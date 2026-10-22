package com.kaan.play.data.match

import java.text.Normalizer
import java.util.Locale

/**
 * Başlık metni işleme (Android'den bağımsız, birim testli). Eşleştirme kuralları ([TitleMatch], [CsMatchRules]) bunun üstüne kurulur.
 *
 * Karşılaştırma için başlık "belirteç"lere (token) bölünür: Türkçe harfler ve aksanlar katlanır (ı→i, ş→s, é→e), noktalama atılır,
 * siteler başlığa eklediği gürültü sözcükleri ("izle", "full", "hd", "dublaj", "extended" …) ve baştaki "the" düşer, Romen ve yazıyla
 * yazılmış devam numaraları ("Part II", "Part Two", "İki" değil yalnızca "part/bölüm/chapter" işaretinden sonra) rakama çevrilir,
 * böylece "Dune Part Two" = "Dune: Part 2" = "Dune 2" olur. Yıl ("(2019)" ya da sondaki 1900–2035 arası) belirteçlerden ayrılır.
 */
object TitleText {
    private val TURKISH: Locale = Locale.forLanguageTag("tr")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    private val MARKS = Regex("\\p{Mn}+")
    private val PAREN_YEAR = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")
    private val SEGMENT_SPLIT = Regex("""\s+[-–—|/]\s+""")

    /** Sitelerin başlığa eklediği, içerik kimliği taşımayan sözcükler (katlanmış biçimde). */
    private val NOISE = setOf(
        "izle", "full", "hd", "fullhd", "uhd", "4k", "1080p", "720p", "480p", "turkce", "dublaj", "dublajli", "altyazili", "altyazi",
        "film", "filmi", "filmini", "seyret", "online", "bedava", "indir", "extended", "uncut", "imax", "remastered", "restored",
        "edition", "cut", "directors", "bluray", "webdl", "tr", "en",
    )

    /** "part/bölüm/chapter" gibi devam işaretleri: kendileri düşer, ardından gelen sayı kalır. */
    private val PART_MARKERS = setOf("part", "pt", "bolum", "chapter", "volume", "vol", "kisim", "episode")

    private val ROMAN = mapOf("ii" to "2", "iii" to "3", "iv" to "4", "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9")
    private val AFTER_MARKER_WORDS = mapOf(
        "i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6",
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5", "six" to "6",
        "bir" to "1", "iki" to "2", "uc" to "3", "dort" to "4", "bes" to "5", "alti" to "6",
    )

    /** Karşılaştırma biçimi: küçük harf (Türkçe kurallı), aksan/Türkçe harf katlama, noktalama → boşluk, tek boşluk. */
    fun fold(text: String): String {
        val lower = text.lowercase(TURKISH).replace('ı', 'i')
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(MARKS, "")
        return decomposed.replace(NON_WORD, " ").trim()
    }

    /** Başlıktaki yıl: önce "(2019)" / "[2019]", yoksa sondaki 4 haneli yıl; yoksa null. */
    fun yearIn(name: String): Int? {
        PAREN_YEAR.find(name)?.let { return it.groupValues[1].toInt() }
        val last = fold(name).split(' ').lastOrNull()?.takeIf { it.length == 4 && it.all(Char::isDigit) }?.toInt()
        return last?.takeIf { it in 1900..2035 && fold(name).split(' ').size > 1 }
    }

    /** Karşılaştırılacak belirteçler: yıl, gürültü, baştaki "the" ve devam işaretleri ayıklanmış; Romen/yazıyla sayılar rakama çevrilmiş. */
    fun tokens(name: String): List<String> {
        val withoutParenYear = name.replace(PAREN_YEAR, " ")
        val raw = fold(withoutParenYear).split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>(raw.size)
        var afterMarker = false
        for (token in raw) {
            if (token in PART_MARKERS) {
                afterMarker = true
                continue
            }
            val mapped = when {
                afterMarker && token in AFTER_MARKER_WORDS -> AFTER_MARKER_WORDS.getValue(token)
                token in ROMAN -> ROMAN.getValue(token)
                else -> token
            }
            afterMarker = false
            if (mapped in NOISE) continue
            out += mapped
        }
        if (out.size > 1 && out.first() == "the") out.removeAt(0)
        // Sondaki yıl ("Dune 2021") belirteç değildir; ama başlığın kendisi yılsa ("1917", "2012") ya da yıl olamayacak sayıysa ("2049") kalır.
        if (out.size > 1) {
            val last = out.last()
            if (last.length == 4 && last.all(Char::isDigit) && last.toInt() in 1900..2035) out.removeAt(out.lastIndex)
        }
        return out
    }

    fun isNumber(token: String): Boolean = token.isNotEmpty() && token.all(Char::isDigit)

    /** "A - B", "A | B", "A / B" biçimli sitelerin başlığını parçalara ayırır (yerel ad - özgün ad gibi). Tüm başlık da listede. */
    fun segments(name: String): List<String> {
        val parts = name.split(SEGMENT_SPLIT).map(String::trim).filter { it.isNotEmpty() }
        return (listOf(name.trim()) + parts).distinct()
    }

    /** Noktalamasız arama sorgusu (":" "-" gibi karakterlerde boş dönen site aramaları için). */
    fun plainQuery(text: String): String = text.replace(NON_WORD, " ").trim().replace(Regex("\\s+"), " ")
}
