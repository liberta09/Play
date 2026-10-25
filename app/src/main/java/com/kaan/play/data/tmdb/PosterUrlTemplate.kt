package com.kaan.play.data.tmdb

import java.util.Locale

/** Kullanıcının IMDb kimliğinden poster URL'si üreten düzenlenebilir şablon. */
object PosterUrlTemplate {
    const val PLACEHOLDER = "{imdb_id}"
    // Kullanıcının mevcut varsayılan özel poster adresi korunur.
    const val DEFAULT = "https://btttr.cc/poster-qa/imdb/poster-default/{imdb_id}.jpg?lang=tr"
    private val imdbIdPattern = Regex("tt\\d{5,12}", RegexOption.IGNORE_CASE)
    private val tmdbIdPattern = Regex("(?:^|[^a-z0-9])tmdb(?::|%3a|/|%2f)+([0-9]{1,10})(?![0-9])", RegexOption.IGNORE_CASE)

    fun isValid(template: String): Boolean =
        template.startsWith("https://") && template.contains(PLACEHOLDER)

    /** IMDb ID'sini çıplak kimlikten veya katalog poster/kaynak URL'si içindeki bir yoldan bulur. */
    fun findImdbId(vararg references: String?): String? = references.asSequence()
        .filterNotNull()
        .mapNotNull { imdbIdPattern.find(it)?.value?.lowercase(Locale.ROOT) }
        .firstOrNull()

    /** Nuvio sağlayıcıları sıklıkla TMDB ID'sini `tmdb:12345` biçiminde verir; bu kimlik TMDB->IMDb için kullanılır. */
    fun findTmdbId(vararg references: String?): Int? = references.asSequence()
        .filterNotNull()
        .mapNotNull { tmdbIdPattern.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        .firstOrNull()

    fun build(template: String, imdbId: String?): String? {
        val id = findImdbId(imdbId) ?: return null
        return if (isValid(template)) template.replace(PLACEHOLDER, id) else null
    }
}
