package com.kaan.play.data.stream

import com.kaan.play.data.model.MediaType
import com.kaan.play.data.tmdb.TmdbRepository
import kotlinx.coroutines.CancellationException

data class ResolvedIdentity(
    val cleanTitle: String,
    val year: Int?,
    val tmdbId: Int?,
    val imdbId: String?,
)

object MediaIdentityResolver {

    /**
     * Yanlış eşleşme, kimliği olmamaktan kötüdür (yanlış filmin altyazısı, karışan geçmiş). Bu yüzden:
     * yıl biliniyorsa yalnızca ±1 yıl tutan aday (başlığı da tutan önde); yıl bilinmiyorsa yalnızca başlığı
     * birebir tutan aday. Hiçbiri yoksa eşleşme yok (null).
     */
    internal fun pickCandidate(candidates: List<com.kaan.play.data.model.MediaSummary>, cleanTitle: String, year: Int?): com.kaan.play.data.model.MediaSummary? {
        val wanted = normalize(cleanTitle)
        fun titleOk(c: com.kaan.play.data.model.MediaSummary) = wanted.isNotEmpty() && normalize(c.title) == wanted
        return if (year != null) {
            val yearOk = candidates.filter { c -> c.year != null && kotlin.math.abs(c.year - year) <= 1 }
            yearOk.firstOrNull(::titleOk) ?: yearOk.firstOrNull()
        } else {
            candidates.firstOrNull(::titleOk)
        }
    }

    private fun normalize(s: String): String = s.lowercase(java.util.Locale.ROOT)
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u').replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
        .filter { it.isLetterOrDigit() }

    /**
     * Kimlik çözümleme sırası:
     * 1. Eklentinin LoadResponse'undaki imdb/tmdb kimliği (syncData içinde)
     * 2. Yoksa temizlenmiş başlık + yıl ile TMDB araması -> eşleşen kaydın detail'inden external_ids.imdbId
     * 3. O da yoksa null (SubDL daha sonra başlıkla arayacak)
     */
    suspend fun resolveIdentity(
        rawTitle: String,
        rawYear: Int? = null,
        syncData: Map<String, String>? = null,
        type: MediaType = MediaType.Movie,
        tmdb: TmdbRepository,
    ): ResolvedIdentity {
        val (cleanTitle, extractedYear) = TitleCleaner.cleanTitleAndYear(rawTitle)
        val year = rawYear ?: extractedYear

        // 1. syncData kontrolü
        var foundImdb: String? = null
        var foundTmdb: Int? = null

        if (!syncData.isNullOrEmpty()) {
            for ((k, v) in syncData) {
                val key = k.lowercase()
                val value = v.trim()
                if (key.contains("imdb") && value.startsWith("tt")) {
                    foundImdb = value
                } else if (value.matches(Regex("""^tt\d+$"""))) {
                    foundImdb = value
                }
                if (key.contains("tmdb")) {
                    value.toIntOrNull()?.let { foundTmdb = it }
                }
            }
        }

        // Eğer tmdbId var ama imdbId yoksa TMDB detail ile çek
        val currentTmdb = foundTmdb
        if (currentTmdb != null && foundImdb == null) {
            try {
                foundImdb = tmdb.detail(type, currentTmdb).imdbId?.takeIf { it.startsWith("tt") }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        // Eğer imdbId var ama tmdbId yoksa findByImdb ile TMDB id'yi bul
        if (foundImdb != null && foundTmdb == null) {
            try {
                foundTmdb = tmdb.findByImdb(foundImdb)?.second
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        if (foundImdb != null) {
            return ResolvedIdentity(
                cleanTitle = cleanTitle,
                year = year,
                tmdbId = foundTmdb,
                imdbId = foundImdb,
            )
        }

        // 2. TMDB araması (temizlenmiş başlık + yıl ile)
        try {
            val page = tmdb.search(cleanTitle)
            val candidates = page.items.filter { it.type == type }
            val matched = pickCandidate(candidates, cleanTitle, year)

            if (matched != null) {
                val matchedTmdbId = matched.id
                val detail = try {
                    tmdb.detail(type, matchedTmdbId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                val resolvedImdb = detail?.imdbId?.takeIf { it.startsWith("tt") }
                return ResolvedIdentity(
                    cleanTitle = cleanTitle,
                    year = year ?: matched.year,
                    tmdbId = matchedTmdbId,
                    imdbId = resolvedImdb,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        // 3. Bulunamadı: temiz başlık ve yılı koru, kimlikler null
        return ResolvedIdentity(
            cleanTitle = cleanTitle,
            year = year,
            tmdbId = foundTmdb,
            imdbId = null,
        )
    }
}
