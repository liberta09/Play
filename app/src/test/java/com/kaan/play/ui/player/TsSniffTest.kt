package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TsSniffTest {

    // Yapı olarak HDFilmCehennemi tarzı bir ana liste (adresler uydurma): ses grupları "GROUP-ID"/"LANGUAGE" içerir.
    private val master = """
        #EXTM3U
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="tur",NAME="Turkce",DEFAULT=YES,URI="a1.txt?s=abc"
        #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",LANGUAGE="eng",NAME="English",DEFAULT=NO,URI="a2.txt?s=abc"
        #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080,AUDIO="aud"
        v1080.txt?s=abc
    """.trimIndent().toByteArray()

    @Test
    fun `eski kural ana listeyi bir G harfinden kesiyordu (3002 kök nedeni)`() {
        val cut = TsSniff.syncOffset(master, master.size)
        assertTrue("liste içinde uygun bir 'G' bulunmalı", cut > 0)
        val after = String(master, cut, master.size - cut)
        assertFalse(after.startsWith("#EXTM3U"))
    }

    @Test
    fun `oynatma listesi asla kirpilmaz`() {
        assertEquals(0, TsSniff.trimFor(master, master.size, null))
        assertEquals(0, TsSniff.trimFor(master, master.size, "text/plain"))
        assertEquals(0, TsSniff.trimFor(master, master.size, "application/vnd.apple.mpegurl"))
    }

    @Test
    fun `BOM ve bosluklu liste de taninir`() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), '\n'.code.toByte()) + master
        assertTrue(TsSniff.isPlaylistOrManifest(withBom, withBom.size, null))
    }

    @Test
    fun `icerik turu mpegurl ise ilk baytlara bakilmadan dokunulmaz`() {
        val junk = ByteArray(400) { 0x10 }.also { it[50] = TsSniff.SYNC; it[238] = TsSniff.SYNC }
        assertEquals(0, TsSniff.trimFor(junk, junk.size, "application/x-mpegURL"))
    }

    @Test
    fun `DASH manifest kirpilmaz`() {
        val mpd = "<?xml version=\"1.0\"?><MPD>GG</MPD>".toByteArray()
        assertEquals(0, TsSniff.trimFor(mpd, mpd.size, null))
    }

    @Test
    fun `sahte PNG basligi olan TS parcasi eskisi gibi kirpilir`() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()) // 'G' tek başına eşitleme sayılmamalı
        val ts = ByteArray(188 * 3) { 0x00 }.also { for (p in 0 until 3) it[p * 188] = TsSniff.SYNC }
        val data = png + ByteArray(60) { 0x01 } + ts
        val skip = TsSniff.trimFor(data, data.size, "image/png")
        assertEquals(png.size + 60, skip)
        assertEquals(TsSniff.SYNC, data[skip])
    }

    @Test
    fun `zaten TS ile baslayan parca degismez`() {
        val ts = ByteArray(188 * 2) { 0x00 }.also { it[0] = TsSniff.SYNC; it[188] = TsSniff.SYNC }
        assertEquals(0, TsSniff.trimFor(ts, ts.size, "video/mp2t"))
    }
}
