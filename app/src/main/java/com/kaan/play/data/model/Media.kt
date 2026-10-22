package com.kaan.play.data.model

/** [addonType]: addon protokolündeki tür adı (`movie` / `series`). */
enum class MediaType(val tmdbPath: String, val addonType: String) {
    Movie("movie", "movie"),
    Tv("tv", "series");

    companion object {
        fun fromTmdb(value: String?): MediaType? = entries.firstOrNull { it.tmdbPath == value }
    }
}

/** Liste/kart için yeterli özet. Birincil kimlik TMDB id'dir. */
data class MediaSummary(
    val id: Int,
    val type: MediaType,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val year: Int?,
    val rating: Double?,
    /** TMDB tür kimlikleri (listelerde gelir); adlara [com.kaan.play.data.tmdb.TmdbRepository] çevirir. */
    val genreIds: List<Int> = emptyList(),
)

/** Ana sayfa kapak karuselinin bir sayfası: arka plan görseli olan içerik + tür adları + (varsa) başlık logosu. */
data class HeroItem(
    val media: MediaSummary,
    val genres: List<String>,
    /** TMDB başlık logosu yolu; yoksa arayüz metin başlık gösterir. */
    val logoPath: String?,
)

data class CastMember(
    val id: Int,
    val name: String,
    val role: String,
    val profilePath: String?,
)

data class MediaDetail(
    val summary: MediaSummary,
    /** Addon'lar IMDb "tt..." kimliği ister; TMDB external_ids'ten türetilir. Yoksa addon sorgulanamaz. */
    val imdbId: String?,
    val tagline: String,
    val genres: List<String>,
    val runtimeMinutes: Int?,
    /** Yalnızca diziler için; "Özel bölümler" (sezon 0) hariç. */
    val seasons: List<SeasonInfo>,
    /** TMDB başlık logosu (arayüz diline göre, yoksa İngilizce/dilsiz); yoksa metin başlık gösterilir. */
    val logoPath: String? = null,
    /** Yaş sınırı (ör. "13+", "PG-13"); bulunamazsa null. */
    val certification: String? = null,
    val directors: List<String> = emptyList(),
    val writers: List<String> = emptyList(),
    /** Yalnızca diziler: yaratıcılar. */
    val creators: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    /** TMDB özgün başlığı (örn. İngilizce); eklenti eşleştirmesi için. Arayüzde gösterilmez. */
    val originalTitle: String? = null,
    /** TMDB alternatif başlıkları (ülke/dil farkları); eklenti eşleştirmesi için. Arayüzde gösterilmez. */
    val alternativeTitles: List<String> = emptyList(),
)

data class SeasonInfo(
    val number: Int,
    val name: String,
    val episodeCount: Int,
    val posterPath: String?,
)

data class SeasonDetail(
    val number: Int,
    val episodes: List<Episode>,
)

data class Episode(
    val number: Int,
    val name: String,
    val overview: String,
    val stillPath: String?,
    val runtimeMinutes: Int?,
    val airDate: String?,
)

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
) {
    val hasMore: Boolean get() = page < totalPages
}
