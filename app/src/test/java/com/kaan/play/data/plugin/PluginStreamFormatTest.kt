package com.kaan.play.data.plugin

import com.kaan.play.data.plugin.runtime.PluginStream
import com.kaan.play.data.plugin.runtime.toPlayable
import com.kaan.play.data.stream.StreamSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Eklentinin `format` alanı: uzantısız HLS adresleri bu ipucuyla oynatılır (Dragon/Imu/Garling: ExoPlayer 3003 hatası vardı). */
class PluginStreamFormatTest {
    private val source = StreamSource(-1, "Dragon")

    private fun parse(json: String) = PluginStream.fromJson(Json.parseToJsonElement(json).jsonObject)!!

    @Test
    fun m3u8FormatSetsHlsMimeTypeForExtensionlessUrl() {
        val s = parse("""{"url":"https://cdn.example/play?id=1","name":"Dragon","format":"m3u8","quality":"1080p"}""")
        assertEquals("m3u8", s.format)
        assertEquals("application/x-mpegURL", s.toPlayable(source).mimeType)
    }

    @Test
    fun hlsAndDashAliases() {
        assertEquals("application/x-mpegURL", parse("""{"url":"https://c.example/x","format":"HLS"}""").toPlayable(source).mimeType)
        assertEquals("application/dash+xml", parse("""{"url":"https://c.example/x","format":"mpd"}""").toPlayable(source).mimeType)
        assertEquals("application/dash+xml", parse("""{"url":"https://c.example/x","format":"dash"}""").toPlayable(source).mimeType)
    }

    @Test
    fun unknownOrMissingFormatLeavesTypeToTheUrl() {
        assertNull(parse("""{"url":"https://c.example/video.mp4","format":"mp4"}""").toPlayable(source).mimeType)
        assertNull(parse("""{"url":"https://c.example/video.mp4"}""").toPlayable(source).mimeType)
    }
}
