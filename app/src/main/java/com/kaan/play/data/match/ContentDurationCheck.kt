package com.kaan.play.data.match

/**
 * Oynatılan videonun süresi, istenen içeriğin TMDB süresiyle bağdaşıyor mu? (Android'den bağımsız, birim testli.)
 *
 * Başlığı/kimliği olmayan kaynaklar (Nuvio JS eklentileri, düz adresler) için tek nesnel doğrulama: 33 dakikalık bir video
 * 3 saatlik bir filmin yerine oynatılamaz. Eşikler geniştir ki uzatılmış sürümler (extended) elenmesin.
 *
 * - **Film:** süre TMDB süresinin %50'sinden kısa ya da %180'inden uzunsa [Result.Mismatch].
 * - **Dizi bölümü:** bölüm süreleri çok değişkendir; yalnızca belirgin uyumsuzlukta (3 kattan fazla uzun / 3 kattan fazla kısa).
 * - Video süresi bilinmiyorsa (canlı yayın, belirsiz) ya da beklenen süre bilinmiyorsa karar verilmez ([Result.Unknown]): kaynak ELENMEZ.
 * - **Canlı Yayın:** Canlı TV yayınında sonlu ve kısa süre (<= 30 dk) bir sahte/engel video göstergesidir.
 */
object ContentDurationCheck {
    enum class Result { Ok, Mismatch, Unknown }

    /** Beklenen süre bundan kısaysa (kısa film) oran güvenilmez: karar verilmez. */
    private const val MIN_RELIABLE_MOVIE_MINUTES = 40

    const val MOVIE_MIN_RATIO = 0.5
    const val MOVIE_MAX_RATIO = 1.8
    const val EPISODE_RATIO = 3.0

    /** Canlı yayında (TV/kanal) sonlu ve kısa dosya süresi (<= 30 dk) sahte/engel/yer tutucu video kabul edilir. */
    fun isFakeLiveStream(durationMs: Long, isLive: Boolean): Boolean {
        if (!isLive) return false
        return durationMs in 1L..(30 * 60 * 1000L)
    }

    fun check(durationMs: Long, expectedMinutes: Int?, kind: ContentKind): Result {
        if (durationMs <= 0 || expectedMinutes == null || expectedMinutes <= 0) return Result.Unknown
        val ratio = (durationMs / 60_000.0) / expectedMinutes
        return when (kind) {
            ContentKind.Movie -> when {
                expectedMinutes < MIN_RELIABLE_MOVIE_MINUTES -> Result.Unknown
                ratio < MOVIE_MIN_RATIO || ratio > MOVIE_MAX_RATIO -> Result.Mismatch
                else -> Result.Ok
            }
            ContentKind.Tv ->
                if (ratio > EPISODE_RATIO || ratio < 1.0 / EPISODE_RATIO) Result.Mismatch else Result.Ok
        }
    }
}
