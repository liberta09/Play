package com.kaan.play.ui.detail

/**
 * Bölüm satırı başlığı: "1. <ad>". TMDB adı boşsa ya da yalnızca numaradan ibaretse ("1. Bölüm", "Bölüm 1",
 * "Episode 1") ad yok sayılır ve arayüz "1. Bölüm" yazar; ad numarayla başlıyorsa ("1. Pilot") numara bir kez kalır.
 * Böylece "1. 1. Bölüm" asla çıkmaz.
 */
object EpisodeTitles {

    private val GENERIC = Regex("""^(?:\d+\s*[.)\-:]?\s*)?(?:bölüm|bolum|episode|ep\.?|chapter)\s*\d*\.?$""", RegexOption.IGNORE_CASE)

    /** Gösterilecek ad (numarasız); null ise yalnızca numaralı yedek metin gösterilir. */
    fun displayName(number: Int, name: String): String? {
        var n = name.trim()
        // Ad bölüm numarasıyla başlıyorsa ("1. Pilot", "1 - Pilot") numara ayrıca yazıldığı için düşürülür.
        Regex("""^$number\s*[.)\-:]\s*""").find(n)?.let { n = n.substring(it.range.last + 1).trim() }
        if (n.isEmpty() || GENERIC.matches(n)) return null
        return n
    }
}
