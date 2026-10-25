package com.kaan.play.data.cloudstream

import com.kaan.play.data.stream.StreamSource
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CsLivePlaybackTest {

    @Test
    fun resolvesCatalogUrlToLiveDataUrlAndKeepsLegacyFallback(): Unit = runBlocking {
        val seenData = mutableListOf<String>()
        val api = object : MainAPI() {
            override var name = "Test Live API"
            override var mainUrl = "https://live.example"

            override suspend fun load(url: String): LoadResponse =
                newLiveStreamLoadResponse("Test channel", url, "plugin-live-data") {}

            override suspend fun loadLinks(
                data: String,
                isCasting: Boolean,
                subtitleCallback: (SubtitleFile) -> Unit,
                callback: (ExtractorLink) -> Unit,
            ): Boolean {
                seenData += data
                return true
            }
        }

        val links = CsRuntime().loadLiveLinks(
            api = api,
            catalogUrl = "https://live.example/channel/arda",
            source = StreamSource(addonId = 7, addonName = "Test live"),
            timeoutMs = 1_000L,
        )

        assertEquals(emptyList<Any>(), links)
        assertEquals(listOf("plugin-live-data", "https://live.example/channel/arda"), seenData)
    }
}
