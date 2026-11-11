package com.kaan.play.ui.home

import com.kaan.play.data.cloudstream.PlayNetworkGuard
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(PlayNetworkGuard.interceptor)
        .build(),
) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val url = request.url()
        val httpMethod = request.httpMethod()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val body = if (dataToSend != null && dataToSend.isNotEmpty()) {
            dataToSend.toRequestBody(null)
        } else if (httpMethod.equals("POST", ignoreCase = true) || httpMethod.equals("PUT", ignoreCase = true)) {
            ByteArray(0).toRequestBody(null)
        } else {
            null
        }

        val reqBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, body)

        for ((name, values) in headers) {
            for (value in values) {
                reqBuilder.addHeader(name, value)
            }
        }

        client.newCall(reqBuilder.build()).execute().use { okResponse ->
            val responseBody = okResponse.body?.string() ?: ""
            val responseCode = okResponse.code
            val responseMessage = okResponse.message
            val latestUrl = okResponse.request.url.toString()

            val responseHeaders = mutableMapOf<String, List<String>>()
            val okHeaders = okResponse.headers
            for (name in okHeaders.names()) {
                responseHeaders[name] = okHeaders.values(name)
            }

            return Response(responseCode, responseMessage, responseHeaders, responseBody, latestUrl)
        }
    }
}
