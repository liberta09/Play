package com.lagradost.cloudstream3

import okhttp3.OkHttpClient
import okhttp3.Request
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Az bellekli cihazda eklentinin tek yanıtı sınırlanır: sınır altı aynen gelir, üstü IOException (eklenti hata görür). */
class ResponseSizeLimitInterceptorTest {
    private val server = MockWebServer().also { it.start() }
    private val client = OkHttpClient.Builder().addInterceptor(ResponseSizeLimitInterceptor(maxBytes = 1024)).build()

    @After fun tearDown() = server.close()

    private fun get() = client.newCall(Request.Builder().url(server.url("/")).build()).execute()

    @Test
    fun `sinir altindaki yanit aynen okunur`() {
        server.enqueue(MockResponse.Builder().body("a".repeat(1000)).build())
        assertEquals(1000, get().body!!.string().length)
    }

    @Test
    fun `bildirilen boyut sinirdan buyukse yanit okunmaz`() {
        server.enqueue(MockResponse.Builder().body("a".repeat(5000)).build())
        val error = runCatching { get() }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun `boyutu bildirilmeyen yanit sinirda kesilir`() {
        server.enqueue(MockResponse.Builder().chunkedBody(Buffer().writeUtf8("a".repeat(5000)), 256).build())
        val error = runCatching { get().use { it.body!!.string() } }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
