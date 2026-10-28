package com.kaan.play.data.tmdb

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tür çipine göre doğru TMDB arama uç noktası; dil parametresi ve boş sorgu kuralları aynı. Gerçek anahtar yok. */
class SearchTypeTest {

    private lateinit var server: MockWebServer
    private val seen = mutableListOf<HttpUrl>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request.url
                val body = when (request.url.encodedPath.removePrefix("/3/")) {
                    // search/movie ve search/tv sonuçlarında media_type YOKTUR: tür uç noktadan gelir.
                    "search/movie" -> """{"page":2,"total_pages":4,"results":[{"id":1,"title":"Film"}]}"""
                    "search/tv" -> """{"page":1,"total_pages":1,"results":[{"id":2,"name":"Dizi"}]}"""
                    "search/multi" -> """{"page":1,"total_pages":3,"results":[
                        {"id":1,"media_type":"movie","title":"Film"},{"id":2,"media_type":"tv","name":"Dizi"},{"id":3,"media_type":"person","name":"Kişi"}]}"""
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().code(200).body(body).build()
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun repo() = TmdbRepository(
        client = OkHttpClient(),
        json = PlayJson,
        apiKey = "test-key-123",
        language = { UiLanguage.Turkish },
        baseUrl = server.url("/3/"),
    )

    @Test
    fun allUsesMultiAndDropsPeople() = runTest {
        val page = repo().search("kara", 1, null)
        assertEquals("search/multi", seen.single().encodedPath.removePrefix("/3/"))
        assertEquals(listOf(MediaType.Movie, MediaType.Tv), page.items.map { it.type })
        assertEquals(3, page.totalPages)
    }

    @Test
    fun moviesUseSearchMovieAndTypeComesFromEndpoint() = runTest {
        val page = repo().search("kara", 2, MediaType.Movie)
        val url = seen.single()
        assertEquals("search/movie", url.encodedPath.removePrefix("/3/"))
        assertEquals("kara", url.queryParameter("query"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("tr-TR", url.queryParameter("language"))
        assertEquals("false", url.queryParameter("include_adult"))
        assertEquals(listOf(MediaType.Movie), page.items.map { it.type })
        assertEquals("Film", page.items.single().title)
        assertTrue("sayfalama TMDB'den gelir", page.hasMore)
    }

    @Test
    fun seriesUseSearchTv() = runTest {
        val page = repo().search("kara", 1, MediaType.Tv)
        assertEquals("search/tv", seen.single().encodedPath.removePrefix("/3/"))
        assertEquals(listOf(MediaType.Tv), page.items.map { it.type })
        assertEquals("Dizi", page.items.single().title)
    }

    @Test
    fun blankQueryMakesNoRequest() = runTest {
        for (type in listOf(null, MediaType.Movie, MediaType.Tv)) {
            assertTrue(repo().search("   ", 1, type).items.isEmpty())
        }
        assertTrue(seen.isEmpty())
    }
}
