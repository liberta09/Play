package com.kaan.play.data.tmdb

import com.kaan.play.data.model.CastMember
import com.kaan.play.data.model.Episode
import com.kaan.play.data.model.HeroItem
import com.kaan.play.data.model.MediaDetail
import com.kaan.play.data.model.MediaSummary
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.model.Page
import com.kaan.play.data.model.SeasonDetail
import com.kaan.play.data.model.SeasonInfo
import com.kaan.play.data.sync.AgeRatingPolicy
import com.kaan.play.i18n.UiLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Uygulamanın TMDB kapısı: DTO'ları alan modellerine çevirir ve dil yedeğini uygular.
 *
 * Dil: her istek [language]'ın TMDB etiketiyle (`tr-TR` / `en-US`) gider; her çağrıda yeniden okunur,
 * yani Ayarlar'da dil değişince yeni istekler hemen yeni dilde çıkar.
 * Yedek: Türkçe özet (bölümde de) boşsa aynı içerik `en-US` ile bir kez daha istenip boşluk doldurulur.
 * Listelerde yedek yok: kartlar başlık+poster gösterir, özet gerekmez.
 */
class TmdbRepository(
    client: OkHttpClient,
    json: Json,
    apiKey: String,
    private val language: () -> UiLanguage,
    baseUrl: HttpUrl = TmdbService.DEFAULT_BASE_URL.toHttpUrl(),
    private val childMode: suspend () -> Boolean = { false },
) {
    private val service = TmdbService(client, json, apiKey, baseUrl)

    suspend fun trending(page: Int = 1): Page<MediaSummary> =
        service.trending(lang(), page).toPage().filterForChildMode()

    suspend fun popular(type: MediaType, page: Int = 1): Page<MediaSummary> =
        service.popular(type.tmdbPath, lang(), page).toPage(defaultType = type).filterForChildMode()

    /** Yalnızca film ya da yalnızca dizi için bu haftanın trendleri. */
    suspend fun trending(type: MediaType, page: Int = 1): Page<MediaSummary> =
        service.trendingOf(type.tmdbPath, lang(), page).toPage(defaultType = type).filterForChildMode()

    /**
     * Ana sayfa kapak karuseli: bu haftanın trendlerinden arka plan görseli olan ilk [limit] içerik; tür adları ve
     * (varsa) başlık logosuyla. Tür listesi ya da logo alınamazsa o alan boş kalır, karusel yine çıkar.
     */
    suspend fun hero(limit: Int = HERO_COUNT): List<HeroItem> {
        val items = trending().items.filter { !it.backdropPath.isNullOrBlank() }.take(limit)
        val names = bestEffort { genreNames() }.orEmpty()
        return coroutineScope {
            items.map { media ->
                async {
                    val logo = bestEffort { service.images(media.type.tmdbPath, media.id, lang(), imageLanguages()) }
                        ?.logos?.let { pickLogo(it, language().iso) }
                    HeroItem(media, media.genreIds.mapNotNull(names::get).take(2), logo)
                }
            }.awaitAll()
        }
    }

    private var genreCache: Pair<UiLanguage, Map<Int, String>>? = null

    private suspend fun genreNames(): Map<Int, String> {
        val current = language()
        genreCache?.takeIf { it.first == current }?.let { return it.second }
        val merged = buildMap {
            MediaType.entries.forEach { type ->
                service.genres(type.tmdbPath, current.tmdbTag).genres.forEach { put(it.id, it.name) }
            }
        }
        genreCache = current to merged
        return merged
    }

    /** `tr,en,null`: arayüz dili, İngilizce, dilsiz logolar (sırayla tercih edilir, bkz. [pickLogo]). */
    private fun imageLanguages(): String = imageLanguagesFor(language())

    /** Boş sorgu ağa gitmeden boş sonuç verir. Kişi sonuçları ayıklanır. */
    suspend fun search(query: String, page: Int = 1): Page<MediaSummary> {
        val q = query.trim()
        if (q.isEmpty()) return Page(emptyList(), 1, 1)
        return service.search(q, lang(), page).toPage().filterForChildMode()
    }

    /** IMDb kimliğine göre TMDB'de ara. Bulunursa (MediaType, tmdbId), yoksa null döner. */
    suspend fun findByImdb(imdbId: String): Pair<MediaType, Int>? {
        val result = bestEffort { service.findByImdb(imdbId, lang()) } ?: return null
        val movie = result.movieResults.firstOrNull { it.id > 0 }
        if (movie != null && isAllowedForCurrentProfile(MediaType.Movie, movie.id)) return MediaType.Movie to movie.id
        val tv = result.tvResults.firstOrNull { it.id > 0 }
        if (tv != null && isAllowedForCurrentProfile(MediaType.Tv, tv.id)) return MediaType.Tv to tv.id
        return null
    }

    suspend fun detail(type: MediaType, id: Int): MediaDetail {
        val primary = service.detail(type.tmdbPath, id, lang(), imageLanguages())
        val merged = if (needsFallback() && primary.overview.isNullOrBlank()) {
            // En-US yedeği en iyi çabadır: başarısız olursa Türkçe sonuç boş özetle yine de döner.
            val english = bestEffort { service.detail(type.tmdbPath, id, UiLanguage.English.tmdbTag) }
            if (english == null) primary else primary.fillFrom(english)
        } else {
            primary
        }
        val keywordIds = merged.keywords?.all().orEmpty().map { it.id }.filter { it > 0 }.distinct()
        val discovered = if (keywordIds.isNotEmpty()) {
            bestEffort {
                service.discoverRelated(
                    type = type.tmdbPath,
                    language = lang(),
                    keywordIds = keywordIds,
                    genreIds = merged.genres.map { it.id },
                ).toPage(defaultType = type).items
            }.orEmpty()
        } else {
            emptyList()
        }
        val mapped = merged.toDetail(type, language(), discovered)
        return if (childMode()) mapped.copy(similar = mapped.similar.filter { AgeRatingPolicy.allowsChildGenres(it.type, it.genreIds, it.adult) }) else mapped
    }

    /** Son güvenlik kapısı: çocuk modunda derecelendirmesi bilinmeyen içerik de oynatılamaz. */
    suspend fun isAllowedForCurrentProfile(type: MediaType, id: Int): Boolean {
        if (!childMode()) return true
        return runCatching {
            val candidate = detail(type, id)
            AgeRatingPolicy.allowsChildGenres(type, candidate.summary.genreIds, candidate.summary.adult) &&
                AgeRatingPolicy.allowsChild(candidate.certification)
        }.getOrDefault(false)
    }

    private suspend fun Page<MediaSummary>.filterForChildMode(): Page<MediaSummary> =
        if (!childMode()) this else copy(items = items.filter { AgeRatingPolicy.allowsChildGenres(it.type, it.genreIds, it.adult) })

    suspend fun season(tvId: Int, season: Int): SeasonDetail {
        val primary = service.season(tvId, season, lang())
        val merged = if (needsFallback() && primary.episodes.any { it.overview.isNullOrBlank() }) {
            val english = bestEffort { service.season(tvId, season, UiLanguage.English.tmdbTag) }
            if (english == null) primary else primary.fillFrom(english)
        } else {
            primary
        }
        return SeasonDetail(
            number = merged.seasonNumber,
            episodes = merged.episodes.map {
                Episode(
                    number = it.episodeNumber,
                    name = it.name.orEmpty(),
                    overview = it.overview.orEmpty(),
                    stillPath = it.stillPath,
                    runtimeMinutes = it.runtime,
                    airDate = it.airDate?.takeIf(String::isNotBlank),
                )
            },
        )
    }

    private fun lang(): String = language().tmdbTag

    private fun needsFallback(): Boolean = language() != UiLanguage.English
}

// ---- DTO -> alan modeli ----

private fun ListDto.toPage(defaultType: MediaType? = null): Page<MediaSummary> = Page(
    items = results.mapNotNull { it.toSummary(defaultType) },
    page = page,
    totalPages = totalPages,
)

/** Film/dizi değilse (ör. kişi) null döner. */
private fun ItemDto.toSummary(defaultType: MediaType?): MediaSummary? {
    val type = MediaType.fromTmdb(mediaType) ?: defaultType ?: return null
    return MediaSummary(
        id = id,
        type = type,
        title = (title ?: name).orEmpty(),
        overview = overview.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = yearOf(releaseDate ?: firstAirDate),
        rating = voteAverage?.takeIf { it > 0.0 },
        genreIds = genreIds,
        popularity = popularity?.takeIf { it > 0.0 },
        adult = adult,
    )
}

private fun DetailDto.toDetail(
    type: MediaType,
    language: UiLanguage,
    discovered: List<MediaSummary>,
): MediaDetail = MediaDetail(
    summary = MediaSummary(
        id = id,
        type = type,
        title = (title ?: name).orEmpty(),
        overview = overview.orEmpty(),
        posterPath = posterPath,
        backdropPath = backdropPath,
        year = yearOf(releaseDate ?: firstAirDate),
        rating = voteAverage?.takeIf { it > 0.0 },
        genreIds = genres.map { it.id }.filter { it > 0 },
        adult = adult,
    ),
    imdbId = (externalIds?.imdbId ?: imdbId)?.takeIf { it.startsWith("tt") },
    tagline = tagline.orEmpty(),
    genres = genres.map { it.name }.filter { it.isNotBlank() },
    runtimeMinutes = (runtime ?: episodeRunTime.firstOrNull())?.takeIf { it > 0 },
    // Sezon 0 ("Özel bölümler") addon'larda nadiren karşılık bulur; sade tutmak için dışarıda.
    seasons = seasons.filter { it.seasonNumber > 0 }.map {
        SeasonInfo(
            number = it.seasonNumber,
            name = it.name.orEmpty(),
            episodeCount = it.episodeCount,
            posterPath = it.posterPath,
        )
    },
    logoPath = images?.logos?.let { pickLogo(it, language.iso) },
    certification = certificationFor(type, language),
    directors = credits?.crew.orEmpty().filter { it.job == "Director" }.names(),
    writers = credits?.crew.orEmpty().filter { it.job in WRITER_JOBS }.names(),
    creators = createdBy.mapNotNull { it.name }.names(),
    cast = credits?.cast.orEmpty()
        .filter { !it.name.isNullOrBlank() }
        .sortedBy { it.order }
        .take(CAST_COUNT)
        .map { CastMember(it.id, it.name.orEmpty(), it.character.orEmpty().trim(), it.profilePath) },
    originalTitle = (originalTitle ?: originalName)?.takeIf { it.isNotBlank() },
    alternativeTitles = alternativeTitleList(),
    similar = RelatedMediaRanking.rank(
        type = type,
        sourceId = id,
        sourceTitle = (title ?: name).orEmpty(),
        sourceOverview = overview.orEmpty(),
        sourceGenreIds = genres.map { it.id }.filter { it > 0 },
        discovered = discovered,
        recommendations = recommendations?.results.orEmpty().mapNotNull { it.toSummary(type) },
        similar = similar?.results.orEmpty().mapNotNull { it.toSummary(type) },
    ),
    trailerKey = videos?.results.orEmpty()
        .filter { it.site.equals("YouTube", ignoreCase = true) && !it.key.isNullOrBlank() }
        .sortedWith(compareByDescending<VideoDto> { it.type.equals("Trailer", ignoreCase = true) }
            .thenByDescending { it.official == true })
        .firstOrNull()?.key,
)

/** Alternatif başlıklar: önce Türkiye/ABD/Birleşik Krallık kayıtları, sonra diğerleri; boşlar atılır, yinelenenler [com.kaan.play.data.match.ContentIdentity] eler. */
private fun DetailDto.alternativeTitleList(): List<String> {
    val all = alternativeTitles?.let { it.titles + it.results }.orEmpty()
    val preferred = setOf("TR", "US", "GB")
    return all.filter { it.country in preferred }.plus(all.filter { it.country !in preferred })
        .mapNotNull { it.title?.trim()?.takeIf(String::isNotEmpty) }
}

private val WRITER_JOBS = setOf("Screenplay", "Writer", "Story", "Novel")
private const val CAST_COUNT = 12
private const val NAMES_COUNT = 2
private const val HERO_COUNT = 5

@JvmName("crewNames")
private fun List<CrewDto>.names(): List<String> = mapNotNull { it.name }.names()

private fun List<String>.names(): List<String> = filter { it.isNotBlank() }.distinct().take(NAMES_COUNT)

/**
 * Yaş sınırı: arayüz dilinin ülkesi (TR / US), yoksa ABD; boş değerler atlanır. Filmde `release_dates`,
 * dizide `content_ratings` okunur.
 */
private fun DetailDto.certificationFor(type: MediaType, language: UiLanguage): String? {
    val regions = listOf(language.region, UiLanguage.English.region).distinct()
    val byRegion: Map<String, String> = when (type) {
        MediaType.Movie -> releaseDates?.results.orEmpty().mapNotNull { c ->
            val cert = c.releases.firstNotNullOfOrNull { it.certification?.trim()?.takeIf(String::isNotEmpty) }
            if (c.country != null && cert != null) c.country to cert else null
        }
        MediaType.Tv -> contentRatings?.results.orEmpty().mapNotNull { c ->
            val cert = c.rating?.trim()?.takeIf(String::isNotEmpty)
            if (c.country != null && cert != null) c.country to cert else null
        }
    }.toMap()
    return regions.firstNotNullOfOrNull { byRegion[it] }
}

/** TMDB `include_image_language` değeri: arayüz dili, İngilizce, dilsiz. */
internal fun imageLanguagesFor(language: UiLanguage): String =
    listOf(language.iso, UiLanguage.English.iso, "null").distinct().joinToString(",")

/**
 * Başlık logosu: arayüz dilindekiler önce, sonra İngilizce, sonra dilsiz; kendi içinde en yüksek oy. SVG atlanır
 * (görsel yükleyici yalnızca raster çözer).
 */
internal fun pickLogo(logos: List<LogoDto>, uiIso: String): String? {
    val usable = logos.filter { !it.filePath.isNullOrBlank() && !it.filePath.endsWith(".svg", ignoreCase = true) }
    val order = listOf(uiIso, UiLanguage.English.iso, null).distinct()
    for (iso in order) {
        usable.filter { it.language == iso || (iso == null && it.language.isNullOrBlank()) }
            .maxByOrNull { it.voteAverage }?.let { return it.filePath }
    }
    return null
}

private fun DetailDto.fillFrom(en: DetailDto): DetailDto = copy(
    title = title.takeUnless { it.isNullOrBlank() } ?: en.title,
    name = name.takeUnless { it.isNullOrBlank() } ?: en.name,
    overview = overview.takeUnless { it.isNullOrBlank() } ?: en.overview,
    tagline = tagline.takeUnless { it.isNullOrBlank() } ?: en.tagline,
    keywords = keywords?.takeIf { it.all().isNotEmpty() } ?: en.keywords,
)

private fun SeasonDetailDto.fillFrom(en: SeasonDetailDto): SeasonDetailDto {
    val byNumber = en.episodes.associateBy { it.episodeNumber }
    return copy(
        episodes = episodes.map { ep ->
            val fallback = byNumber[ep.episodeNumber] ?: return@map ep
            ep.copy(
                name = ep.name.takeUnless { it.isNullOrBlank() } ?: fallback.name,
                overview = ep.overview.takeUnless { it.isNullOrBlank() } ?: fallback.overview,
            )
        },
    )
}

private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()

/** Yedek istekler için: yalnızca TMDB hatasını yutar, iptal gibi diğer istisnalar yukarı çıkar. */
private inline fun <T> bestEffort(block: () -> T): T? =
    try { block() } catch (_: TmdbException) { null }
