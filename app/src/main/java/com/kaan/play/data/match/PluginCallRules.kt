package com.kaan.play.data.match

/**
 * Nuvio JS eklentisine giden çağrının doğrulaması (docs/nuvio-plugin-format.md §2.1; Android'den bağımsız). Eklentiye başlık/yıl gitmez,
 * yalnızca kimlik ve tür gider; bu yüzden kimliğin kendisi doğru ve tutarlı olmalı: TMDB kimliği pozitif sayı, tür filmse `movie` ve
 * sezon/bölüm yok, dizi ise `tv`/`series` ve ikisi de var. Tutarsız çağrı eklentiye gönderilmez (yanlış içerik getirmesin).
 */
object PluginCallRules {
    /** @return null: çağrı geçerli; aksi hâlde sebep. */
    fun problem(tmdbId: String, mediaType: String, season: Int?, episode: Int?, kind: ContentKind): String? {
        val id = tmdbId.toLongOrNull()
        if (id == null || id <= 0) return "geçersiz TMDB kimliği"
        return when (kind) {
            ContentKind.Movie -> when {
                mediaType != "movie" -> "film için tür movie olmalı ($mediaType)"
                season != null || episode != null -> "filmde sezon/bölüm olmamalı"
                else -> null
            }
            ContentKind.Tv -> when {
                mediaType != "tv" && mediaType != "series" -> "dizi için tür tv/series olmalı ($mediaType)"
                season == null || episode == null || season < 0 || episode < 1 -> "dizide geçerli sezon ve bölüm gerekli"
                else -> null
            }
        }
    }
}
