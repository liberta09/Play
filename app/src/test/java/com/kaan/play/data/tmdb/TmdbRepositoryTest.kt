package com.kaan.play.data.tmdb

import com.kaan.play.data.match.toContentIdentity
import com.kaan.play.data.model.MediaType
import com.kaan.play.data.net.PlayJson
import com.kaan.play.i18n.UiLanguage
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Sahte TMDB yanıtlarıyla parser, dil parametresi, dil yedeği ve hata eşlemeleri. Gerçek anahtar kullanılmaz. */
class TmdbRepositoryTest {

    private val fakeKey = "test-key-123"
    private lateinit var server: MockWebServer
    private val seen = mutableListOf<HttpUrl>()

    /** "yol|dil" -> yanıt gövdesi. Bulunamazsa 404. */
    private val fixtures = mutableMapOf<String, String>()
    private var forcedCode: Int? = null
    private var language = UiLanguage.Turkish

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request.url
                forcedCode?.let { return MockResponse.Builder().code(it).build() }
                val path = request.url.encodedPath.removePrefix("/3/")
                val body = fixtures["$path|${request.url.queryParameter("language")}"]
                    ?: return MockResponse.Builder().code(404).build()
                return MockResponse.Builder().code(200).body(body).build()
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repo(key: String = fakeKey) = TmdbRepository(
        client = OkHttpClient(),
        json = PlayJson,
        apiKey = key,
        language = { language },
        baseUrl = server.url("/3/"),
    )

    // ---- listeler ----

    @Test
    fun trendingParsesAndDropsPeople() = runTest {
        fixtures["trending/all/week|tr-TR"] = """
            {"page":1,"total_pages":5,"results":[
              {"id":10,"media_type":"movie","title":"Başlangıç","overview":"Rüya","poster_path":"/a.jpg","release_date":"2010-07-16","vote_average":8.3},
              {"id":20,"media_type":"tv","name":"Dizi Adı","first_air_date":"2019-03-01","vote_average":0},
              {"id":30,"media_type":"person","name":"Bir Oyuncu"}
            ],"unknown_field":true}
        """
        val page = repo().trending()

        assertEquals(2, page.items.size)
        assertTrue(page.hasMore)
        val movie = page.items[0]
        assertEquals(MediaType.Movie, movie.type)
        assertEquals("Başlangıç", movie.title)
        assertEquals(2010, movie.year)
        assertEquals(8.3, movie.rating!!, 0.001)
        val tv = page.items[1]
        assertEquals(MediaType.Tv, tv.type)
        assertEquals("Dizi Adı", tv.title)
        assertEquals(2019, tv.year)
        assertNull(tv.rating)
    }

    @Test
    fun popularUsesRequestedTypeWhenItemsLackMediaType() = runTest {
        fixtures["tv/popular|tr-TR"] =
            """{"page":2,"total_pages":2,"results":[{"id":7,"name":"X","first_air_date":"2021-01-01"}]}"""
        val page = repo().popular(MediaType.Tv, page = 2)

        assertEquals(MediaType.Tv, page.items.single().type)
        assertFalse(page.hasMore)
        assertEquals("2", seen.single().queryParameter("page"))
    }

    @Test
    fun searchSendsQueryAndSkipsNetworkForBlank() = runTest {
        fixtures["search/multi|tr-TR"] =
            """{"page":1,"total_pages":1,"results":[{"id":1,"media_type":"movie","title":"Film"}]}"""

        assertTrue(repo().search("   ").items.isEmpty())
        assertTrue(seen.isEmpty())

        val result = repo().search("  kara şövalye ")
        assertEquals("Film", result.items.single().title)
        assertEquals("kara şövalye", seen.single().queryParameter("query"))
        assertEquals("false", seen.single().queryParameter("include_adult"))
    }

    // ---- dil ----

    @Test
    fun requestsCarryLanguageAndKeyAsQueryParameters() = runTest {
        fixtures["tv/popular|tr-TR"] = """{"results":[]}"""
        fixtures["tv/popular|en-US"] = """{"results":[]}"""

        repo().popular(MediaType.Tv)
        language = UiLanguage.English // Dil değişince sonraki istek yeni dille gitmeli.
        repo().popular(MediaType.Tv)

        assertEquals(listOf("tr-TR", "en-US"), seen.map { it.queryParameter("language") })
        assertTrue(seen.all { it.queryParameter("api_key") == fakeKey })
    }

    // ---- detay ----

    private val movieDetailTr = """
        {"id":27205,"title":"Başlangıç","overview":"Bir hırsız rüyalara girer.","tagline":"Rüya içinde rüya",
         "poster_path":"/p.jpg","backdrop_path":"/b.jpg","release_date":"2010-07-15","vote_average":8.4,"runtime":148,
         "genres":[{"id":1,"name":"Bilim Kurgu"},{"id":2,"name":"Aksiyon"}],
         "external_ids":{"imdb_id":"tt1375666"}}
    """

    @Test
    fun movieDetailDerivesImdbIdFromExternalIds() = runTest {
        fixtures["movie/27205|tr-TR"] = movieDetailTr
        val d = repo().detail(MediaType.Movie, 27205)

        assertEquals("tt1375666", d.imdbId)
        assertEquals(148, d.runtimeMinutes)
        assertEquals(listOf("Bilim Kurgu", "Aksiyon"), d.genres)
        assertEquals("Başlangıç", d.summary.title)
        assertEquals("external_ids,credits,images,alternative_titles,similar,recommendations,keywords,videos,release_dates", seen.single().queryParameter("append_to_response"))
        assertEquals("tr,en,null", seen.single().queryParameter("include_image_language"))
        assertEquals(1, seen.size) // Özet dolu: yedek isteği yok.
    }

    @Test
    fun movieRelatedUsesKeywordDiscoveryAndDropsPopularButUnrelatedTitle() = runTest {
        fixtures["movie/100|tr-TR"] = """
            {"id":100,"title":"Rüya Hırsızı","overview":"Bir hırsız rüya zihin plan.",
             "genres":[{"id":878,"name":"Bilim Kurgu"},{"id":28,"name":"Aksiyon"}],
             "keywords":{"keywords":[{"id":501,"name":"dream"},{"id":502,"name":"heist"}]},
             "recommendations":{"results":[
               {"id":101,"title":"Aynı tema","overview":"Hırsız rüya zihin plan.","genre_ids":[878,28],"vote_average":7.0,"popularity":10},
               {"id":102,"title":"Alakasız ve popüler","overview":"Bir köy sanatçısı ailesiyle yeni bir şehre taşınır.","genre_ids":[28],"vote_average":9.8,"popularity":900}
             ]},
             "similar":{"results":[{"id":100,"title":"Kaynağın kendisi","overview":"Hırsız rüya zihin plan.","genre_ids":[878,28]}]}}
        """
        fixtures["discover/movie|tr-TR"] = """
            {"results":[
              {"id":103,"title":"Anahtar kelime eşleşmesi","overview":"Rüya dünyasında bir plan.","genre_ids":[878,28],"popularity":5},
              {"id":104,"media_type":"tv","name":"Başka tür","overview":"Rüya plan.","genre_ids":[878,28]}
            ]}
        """

        val detail = repo().detail(MediaType.Movie, 100)

        assertEquals(listOf(878, 28), detail.summary.genreIds)
        assertEquals(listOf(101, 103), detail.similar.map { it.id })
        assertEquals(10.0, detail.similar.first().popularity!!, 0.001) // Yüksek popülerliğe rağmen alakasız içerik elenir.
        val discover = seen.single { it.encodedPath.endsWith("/discover/movie") }
        assertEquals("501|502", discover.queryParameter("with_keywords"))
        assertEquals("878|28", discover.queryParameter("with_genres"))
        assertEquals("false", discover.queryParameter("include_adult"))
        assertEquals(
            "external_ids,credits,images,alternative_titles,similar,recommendations,keywords,videos,release_dates",
            seen.first().queryParameter("append_to_response"),
        )
    }

    @Test
    fun tvRelatedReadsTheResultsKeywordShape() = runTest {
        fixtures["tv/200|tr-TR"] = """
            {"id":200,"name":"Zaman Dizisi","overview":"zaman yolculuğu aile sırrı",
             "genres":[{"id":10765,"name":"Bilim Kurgu"}],
             "keywords":{"results":[{"id":601,"name":"time travel"}]}}
        """
        fixtures["discover/tv|tr-TR"] = """
            {"results":[{"id":201,"name":"Benzer tema","overview":"zaman yolculuğu aile sırrı","genre_ids":[10765]}]}
        """

        val detail = repo().detail(MediaType.Tv, 200)

        assertEquals(listOf(201), detail.similar.map { it.id })
        assertEquals("601", seen.single { it.encodedPath.endsWith("/discover/tv") }.queryParameter("with_keywords"))
    }

    @Test
    fun detailCarriesOriginalAndAlternativeTitlesForMatching() = runTest {
        fixtures["movie/120|tr-TR"] = """
            {"id":120,"title":"Yüzüklerin Efendisi: Yüzük Kardeşliği","original_title":"The Lord of the Rings: The Fellowship of the Ring",
             "overview":"Özet","release_date":"2001-12-18","runtime":179,"external_ids":{"imdb_id":"tt0120737"},
             "alternative_titles":{"titles":[{"iso_3166_1":"DE","title":"Der Herr der Ringe"},{"iso_3166_1":"US","title":"LOTR 1"},{"iso_3166_1":"TR","title":" "}]}}
        """
        val d = repo().detail(MediaType.Movie, 120)
        assertEquals("The Lord of the Rings: The Fellowship of the Ring", d.originalTitle)
        assertEquals(listOf("LOTR 1", "Der Herr der Ringe"), d.alternativeTitles) // TR/US/GB önce, boş başlık atılır
        assertEquals(2001, d.summary.year)

        val identity = d.toContentIdentity()
        assertEquals(120, identity.tmdbId)
        assertEquals("tt0120737", identity.imdbId)
        assertEquals(179, identity.runtimeMinutes)
        assertEquals("The Lord of the Rings: The Fellowship of the Ring", identity.searchQueries()[1])
    }

    @Test
    fun tvDetailAlternativeTitlesComeFromResults() = runTest {
        fixtures["tv/1399|tr-TR"] = """
            {"id":1399,"name":"Taht Oyunları","original_name":"Game of Thrones","overview":"Özet","first_air_date":"2011-04-17",
             "alternative_titles":{"results":[{"iso_3166_1":"US","title":"GoT"}]}}
        """
        val d = repo().detail(MediaType.Tv, 1399)
        assertEquals("Game of Thrones", d.originalTitle)
        assertEquals(listOf("GoT"), d.alternativeTitles)
    }

    @Test
    fun tvDetailListsSeasonsWithoutSpecialsAndUsesEpisodeRuntime() = runTest {
        fixtures["tv/1399|tr-TR"] = """
            {"id":1399,"name":"Dizi","overview":"Özet","first_air_date":"2011-04-17","episode_run_time":[57],
             "seasons":[{"season_number":0,"name":"Özel","episode_count":3},
                        {"season_number":1,"name":"Sezon 1","episode_count":10,"poster_path":"/s1.jpg"},
                        {"season_number":2,"name":"Sezon 2","episode_count":8}],
             "external_ids":{"imdb_id":"tt0944947"}}
        """
        val d = repo().detail(MediaType.Tv, 1399)

        assertEquals("tt0944947", d.imdbId)
        assertEquals(57, d.runtimeMinutes)
        assertEquals(listOf(1, 2), d.seasons.map { it.number })
        assertEquals(10, d.seasons.first().episodeCount)
        assertEquals(MediaType.Tv, d.summary.type)
    }

    @Test
    fun invalidImdbIdIsDropped() = runTest {
        fixtures["movie/1|tr-TR"] = """{"id":1,"title":"A","overview":"x","external_ids":{"imdb_id":"nm0000001"}}"""
        assertNull(repo().detail(MediaType.Movie, 1).imdbId)
    }

    @Test
    fun blankTurkishOverviewFallsBackToEnglish() = runTest {
        fixtures["movie/27205|tr-TR"] =
            """{"id":27205,"title":"Başlangıç","overview":"","tagline":"","external_ids":{"imdb_id":"tt1375666"}}"""
        fixtures["movie/27205|en-US"] =
            """{"id":27205,"title":"Inception","overview":"A thief enters dreams.","tagline":"Your mind is the scene of the crime"}"""

        val d = repo().detail(MediaType.Movie, 27205)

        assertEquals("A thief enters dreams.", d.summary.overview)
        assertEquals("Your mind is the scene of the crime", d.tagline)
        assertEquals("Başlangıç", d.summary.title) // Türkçe başlık korunur.
        assertEquals("tt1375666", d.imdbId)
        assertEquals(listOf("tr-TR", "en-US"), seen.map { it.queryParameter("language") })
    }

    @Test
    fun englishFallbackFailureStillReturnsTurkishResult() = runTest {
        fixtures["movie/5|tr-TR"] = """{"id":5,"title":"Film","overview":""}"""
        // en-US için fixture yok -> 404

        val d = repo().detail(MediaType.Movie, 5)

        assertEquals("Film", d.summary.title)
        assertEquals("", d.summary.overview)
    }

    @Test
    fun noFallbackWhenUiIsEnglish() = runTest {
        language = UiLanguage.English
        fixtures["movie/5|en-US"] = """{"id":5,"title":"Film","overview":""}"""

        repo().detail(MediaType.Movie, 5)

        assertEquals(1, seen.size)
    }

    // ---- sezon ----

    @Test
    fun seasonFillsBlankEpisodeOverviewsFromEnglish() = runTest {
        fixtures["tv/9/season/1|tr-TR"] = """
            {"season_number":1,"episodes":[
              {"episode_number":1,"name":"Pilot","overview":"Türkçe özet","runtime":50,"air_date":"2020-01-01","still_path":"/e1.jpg"},
              {"episode_number":2,"name":"","overview":"","air_date":""}]}
        """
        fixtures["tv/9/season/1|en-US"] = """
            {"season_number":1,"episodes":[
              {"episode_number":1,"name":"Pilot","overview":"English one"},
              {"episode_number":2,"name":"Second","overview":"English two"}]}
        """

        val s = repo().season(9, 1)

        assertEquals(listOf(1, 2), s.episodes.map { it.number })
        assertEquals("Türkçe özet", s.episodes[0].overview)
        assertEquals("English two", s.episodes[1].overview)
        assertEquals("Second", s.episodes[1].name)
        assertEquals(50, s.episodes[0].runtimeMinutes)
        assertNull(s.episodes[1].airDate)
    }

    // ---- hatalar ----

    private suspend fun expectKind(kind: TmdbException.Kind, block: suspend () -> Unit) {
        try {
            block()
            fail("TmdbException bekleniyordu")
        } catch (e: TmdbException) {
            assertEquals(kind, e.kind)
            // Anahtar sorgu parametresindedir; hata mesajında asla geçmemeli.
            assertFalse(e.message!!.contains(fakeKey))
            assertFalse(e.message!!.contains("api_key"))
        }
    }

    @Test
    fun missingKeyFailsWithoutNetworkCall() = runTest {
        expectKind(TmdbException.Kind.MissingApiKey) { repo(key = "").trending() }
        assertTrue(seen.isEmpty())
    }

    @Test
    fun httpStatusesMapToKinds() = runTest {
        forcedCode = 401
        expectKind(TmdbException.Kind.InvalidApiKey) { repo().trending() }
        forcedCode = 404
        expectKind(TmdbException.Kind.NotFound) { repo().detail(MediaType.Movie, 1) }
        forcedCode = 429
        expectKind(TmdbException.Kind.RateLimited) { repo().trending() }
        forcedCode = 500
        expectKind(TmdbException.Kind.Http) { repo().trending() }
    }

    @Test
    fun malformedJsonIsParseError() = runTest {
        fixtures["trending/all/week|tr-TR"] = "{ not json"
        expectKind(TmdbException.Kind.Parse) { repo().trending() }
    }

    @Test
    fun unreachableServerIsNetworkError() = runTest {
        val r = repo()
        server.close()
        expectKind(TmdbException.Kind.Network) { r.trending() }
    }

    // ---- görseller ----

    @Test
    fun imageUrlsAreBuiltOnlyForValidPaths() {
        assertEquals("https://image.tmdb.org/t/p/w342/a.jpg", TmdbImages.url("/a.jpg", TmdbImages.Size.Poster))
        assertNull(TmdbImages.url(null, TmdbImages.Size.Poster))
        assertNull(TmdbImages.url("", TmdbImages.Size.Poster))
    }

    // ---- 10. adım: logo, yaş sınırı, oyuncular, kapak karuseli ----

    private val movieRich = """
        {"id":27205,"title":"Başlangıç","overview":"Özet","release_date":"2010-07-15","vote_average":8.4,"runtime":148,
         "genres":[{"id":28,"name":"Aksiyon"}],"external_ids":{"imdb_id":"tt1375666"},
         "credits":{
           "cast":[{"id":2,"name":"İkinci","character":"Arthur","order":1,"profile_path":"/c2.jpg"},
                   {"id":1,"name":"Birinci","character":"Cobb","order":0,"profile_path":null},
                   {"id":3,"name":"","character":"X","order":2}],
           "crew":[{"name":"Christopher Nolan","job":"Director"},{"name":"Christopher Nolan","job":"Screenplay"},
                   {"name":"Emma Thomas","job":"Producer"},{"name":"Jonathan Nolan","job":"Story"},{"name":"Üçüncü Yazar","job":"Writer"}]},
         "images":{"logos":[
           {"file_path":"/en-low.png","iso_639_1":"en","vote_average":3.0},
           {"file_path":"/en-high.png","iso_639_1":"en","vote_average":5.5},
           {"file_path":"/tr.png","iso_639_1":"tr","vote_average":1.0},
           {"file_path":"/vec.svg","iso_639_1":"tr","vote_average":9.0}]},
         "release_dates":{"results":[
           {"iso_3166_1":"US","release_dates":[{"certification":""},{"certification":"PG-13"}]},
           {"iso_3166_1":"TR","release_dates":[{"certification":"13+"}]}]}}
    """

    @Test
    fun detailCarriesCastCrewLogoAndTurkishCertification() = runTest {
        fixtures["movie/27205|tr-TR"] = movieRich
        val d = repo().detail(MediaType.Movie, 27205)

        assertEquals("/tr.png", d.logoPath) // arayüz dili önce; SVG atlanır
        assertEquals("13+", d.certification)
        assertEquals(listOf("Christopher Nolan"), d.directors)
        assertEquals(listOf("Christopher Nolan", "Jonathan Nolan"), d.writers) // yinelenen ad bir kez, en çok iki
        assertEquals(listOf("Birinci", "İkinci"), d.cast.map { it.name }) // sıraya göre, adsız oyuncu yok
        assertEquals("Arthur", d.cast[1].role)
        assertEquals("/c2.jpg", d.cast[1].profilePath)
        assertNull(d.cast[0].profilePath)
    }

    @Test
    fun englishUiPicksEnglishLogoWithBestVoteAndUsCertification() = runTest {
        language = UiLanguage.English
        fixtures["movie/27205|en-US"] = movieRich
        val d = repo().detail(MediaType.Movie, 27205)

        assertEquals("/en-high.png", d.logoPath)
        assertEquals("PG-13", d.certification)
        assertEquals("en,null", seen.single().queryParameter("include_image_language"))
    }

    @Test
    fun certificationFallsBackToUsAndMissingExtrasAreEmpty() = runTest {
        fixtures["movie/1|tr-TR"] = """
            {"id":1,"title":"A","overview":"x","release_dates":{"results":[{"iso_3166_1":"US","release_dates":[{"certification":"R"}]}]}}
        """
        val d = repo().detail(MediaType.Movie, 1)
        assertEquals("R", d.certification)
        assertNull(d.logoPath)
        assertTrue(d.cast.isEmpty() && d.directors.isEmpty() && d.writers.isEmpty() && d.creators.isEmpty())

        fixtures["movie/2|tr-TR"] = """{"id":2,"title":"B","overview":"x"}"""
        assertNull(repo().detail(MediaType.Movie, 2).certification)
    }

    @Test
    fun tvDetailUsesContentRatingsAndCreators() = runTest {
        fixtures["tv/9|tr-TR"] = """
            {"id":9,"name":"Dizi","overview":"x","created_by":[{"name":"Yaratıcı A"},{"name":"Yaratıcı B"},{"name":"Yaratıcı C"}],
             "content_ratings":{"results":[{"iso_3166_1":"US","rating":"TV-MA"},{"iso_3166_1":"TR","rating":"18+"}]}}
        """
        val d = repo().detail(MediaType.Tv, 9)

        assertEquals("18+", d.certification)
        assertEquals(listOf("Yaratıcı A", "Yaratıcı B"), d.creators)
        assertEquals("external_ids,credits,images,alternative_titles,similar,recommendations,keywords,videos,content_ratings", seen.single().queryParameter("append_to_response"))
    }

    @Test
    fun trendingByTypeUsesTypedPathAndKeepsGenreIds() = runTest {
        fixtures["trending/tv/week|tr-TR"] =
            """{"page":1,"total_pages":1,"results":[{"id":7,"name":"X","genre_ids":[18,80]}]}"""
        val item = repo().trending(MediaType.Tv).items.single()

        assertEquals(MediaType.Tv, item.type)
        assertEquals(listOf(18, 80), item.genreIds)
    }

    @Test
    fun heroKeepsOnlyBackdropItemsWithGenreNamesAndLogos() = runTest {
        fixtures["trending/all/week|tr-TR"] = """
            {"results":[
              {"id":1,"media_type":"movie","title":"Film Bir","backdrop_path":"/b1.jpg","release_date":"2020-01-01","genre_ids":[28,12,35]},
              {"id":2,"media_type":"tv","name":"Dizi İki","backdrop_path":null,"genre_ids":[18]},
              {"id":3,"media_type":"tv","name":"Dizi Üç","backdrop_path":"/b3.jpg","genre_ids":[18]}]}
        """
        fixtures["genre/movie/list|tr-TR"] = """{"genres":[{"id":28,"name":"Aksiyon"},{"id":12,"name":"Macera"},{"id":35,"name":"Komedi"}]}"""
        fixtures["genre/tv/list|tr-TR"] = """{"genres":[{"id":18,"name":"Dram"}]}"""
        fixtures["movie/1/images|tr-TR"] = """{"logos":[{"file_path":"/l1.png","iso_639_1":"tr","vote_average":1}]}"""
        // dizi üç için images fixture'ı yok (404): logo olmadan da kapak çıkar.

        val hero = repo().hero()

        assertEquals(listOf("Film Bir", "Dizi Üç"), hero.map { it.media.title })
        assertEquals(listOf("Aksiyon", "Macera"), hero[0].genres) // en çok iki tür
        assertEquals("/l1.png", hero[0].logoPath)
        assertEquals(listOf("Dram"), hero[1].genres)
        assertNull(hero[1].logoPath)
    }

    @Test
    fun heroSurvivesGenreListFailure() = runTest {
        fixtures["trending/all/week|tr-TR"] =
            """{"results":[{"id":1,"media_type":"movie","title":"Film","backdrop_path":"/b.jpg","genre_ids":[28]}]}"""
        val hero = repo().hero()
        assertEquals(1, hero.size)
        assertTrue(hero[0].genres.isEmpty())
    }

    @Test
    fun findByImdbReturnsMovieWhenFound() = runTest {
        fixtures["find/tt0111161|tr-TR"] = """{"movie_results":[{"id":278,"title":"Esaretin Bedeli"}],"tv_results":[]}"""
        val found = repo().findByImdb("tt0111161")
        assertEquals(MediaType.Movie to 278, found)
    }

    @Test
    fun findByImdbReturnsTvWhenFound() = runTest {
        fixtures["find/tt0944947|tr-TR"] = """{"movie_results":[],"tv_results":[{"id":1399,"name":"Game of Thrones"}]}"""
        val found = repo().findByImdb("tt0944947")
        assertEquals(MediaType.Tv to 1399, found)
    }

    @Test
    fun findByImdbReturnsNullWhenNotFound() = runTest {
        fixtures["find/tt0000000|tr-TR"] = """{"movie_results":[],"tv_results":[]}"""
        val found = repo().findByImdb("tt0000000")
        assertNull(found)
    }
}
