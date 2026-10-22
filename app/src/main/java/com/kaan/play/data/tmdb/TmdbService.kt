package com.kaan.play.data.tmdb

import com.kaan.play.data.net.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * TMDB v3 için ince HTTP katmanı. Yalnızca DTO döndürür; eşleme ve dil yedeği [TmdbRepository]'dedir.
 * Anahtar `api_key` sorgu parametresiyle gider; URL hiçbir yere loglanmaz ve hatalara girmez.
 */
internal class TmdbService(
    private val client: OkHttpClient,
    private val json: Json,
    private val apiKey: String,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {

    suspend fun trending(language: String, page: Int): ListDto =
        get("trending/all/week", ListDto.serializer(), language, "page" to page.toString())

    suspend fun trendingOf(type: String, language: String, page: Int): ListDto =
        get("trending/$type/week", ListDto.serializer(), language, "page" to page.toString())

    suspend fun popular(type: String, language: String, page: Int): ListDto =
        get("$type/popular", ListDto.serializer(), language, "page" to page.toString())

    /** Tür kimliği -> ad listesi (`genre/movie/list`, `genre/tv/list`), istenen dilde. */
    suspend fun genres(type: String, language: String): GenreListDto =
        get("genre/$type/list", GenreListDto.serializer(), language)

    /** Yalnızca başlık logoları; [imageLanguages]: `tr,en,null` gibi (TMDB `include_image_language`). */
    suspend fun images(type: String, id: Int, language: String, imageLanguages: String): ImagesDto =
        get("$type/$id/images", ImagesDto.serializer(), language, "include_image_language" to imageLanguages)

    suspend fun search(query: String, language: String, page: Int): ListDto =
        get(
            "search/multi", ListDto.serializer(), language,
            "query" to query, "page" to page.toString(), "include_adult" to "false",
        )

    suspend fun findByImdb(imdbId: String, language: String): FindResultDto =
        get("find/$imdbId", FindResultDto.serializer(), language, "external_source" to "imdb_id")

    /**
     * Tek istekte: `external_ids` (IMDb kimliği), `credits` (oyuncu/ekip), `images` (başlık logosu) ve yaş sınırı
     * (filmde `release_dates`, dizide `content_ratings`).
     */
    suspend fun detail(type: String, id: Int, language: String, imageLanguages: String = "en,null"): DetailDto {
        val rating = if (type == "tv") "content_ratings" else "release_dates"
        return get(
            "$type/$id", DetailDto.serializer(), language,
            "append_to_response" to "external_ids,credits,images,alternative_titles,$rating",
            "include_image_language" to imageLanguages,
        )
    }

    suspend fun season(tvId: Int, season: Int, language: String): SeasonDetailDto =
        get("tv/$tvId/season/$season", SeasonDetailDto.serializer(), language)

    private suspend fun <T> get(
        path: String,
        serializer: KSerializer<T>,
        language: String,
        vararg params: Pair<String, String>,
    ): T {
        if (apiKey.isBlank()) throw TmdbException(TmdbException.Kind.MissingApiKey, path = path)

        val url = baseUrl.newBuilder().apply {
            addPathSegments(path)
            addQueryParameter("api_key", apiKey)
            addQueryParameter("language", language)
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).get().build()).await().use { response ->
                    if (!response.isSuccessful) {
                        throw TmdbException(kindFor(response.code), response.code, path)
                    }
                    json.decodeFromString(serializer, response.body.string())
                }
            } catch (e: IOException) {
                throw TmdbException(TmdbException.Kind.Network, path = path, cause = e)
            } catch (e: SerializationException) {
                throw TmdbException(TmdbException.Kind.Parse, path = path, cause = e)
            }
        }
    }

    private fun kindFor(code: Int) = when (code) {
        401 -> TmdbException.Kind.InvalidApiKey
        404 -> TmdbException.Kind.NotFound
        429 -> TmdbException.Kind.RateLimited
        else -> TmdbException.Kind.Http
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.themoviedb.org/3/"
    }
}
