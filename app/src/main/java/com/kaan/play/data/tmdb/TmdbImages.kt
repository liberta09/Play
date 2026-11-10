package com.kaan.play.data.tmdb

/** TMDB görsel adresleri. Yol (`/abc.jpg`) boş/null ise null döner; arayüz yer tutucu gösterir. */
object TmdbImages {
    private const val BASE = "https://image.tmdb.org/t/p/"

    enum class Size(val segment: String) {
        PosterSmall("w185"),
        Poster("w342"),
        PosterLarge("w500"),
        Backdrop("w780"),
        BackdropLarge("w1280"),
        Still("w300"),
        Logo("w500"),
        Profile("w185"),
    }

    fun posterSize(smaller: Boolean): Size = if (smaller) Size.PosterSmall else Size.Poster

    fun backdropSize(smaller: Boolean): Size = if (smaller) Size.Backdrop else Size.BackdropLarge

    fun url(path: String?, size: Size): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> BASE + size.segment + path
        else -> null
    }
}
