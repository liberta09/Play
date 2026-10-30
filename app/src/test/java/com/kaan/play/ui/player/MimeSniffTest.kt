package com.kaan.play.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MimeSniffTest {
    private fun sniff(text: String) = text.toByteArray(Charsets.ISO_8859_1).let { MimeSniff.fromHead(it, it.size) }

    @Test
    fun `HLS listesi taninir`() {
        assertEquals(MimeSniff.HLS, sniff("#EXTM3U\n#EXT-X-VERSION:3"))
        assertEquals(MimeSniff.HLS, sniff("﻿".let { "\n  #extm3u\n" }))
    }

    @Test
    fun `BOM li HLS taninir`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "#EXTM3U".toByteArray()
        assertEquals(MimeSniff.HLS, MimeSniff.fromHead(bytes, bytes.size))
    }

    @Test
    fun `DASH manifest taninir`() {
        assertEquals(MimeSniff.DASH, sniff("<MPD xmlns=\"urn:mpeg:dash:schema:mpd:2011\">"))
        assertEquals(MimeSniff.DASH, sniff("<?xml version=\"1.0\"?><MPD>"))
    }

    @Test
    fun `HTML hata sayfasi ve JSON medya sayilmaz`() {
        assertNull(sniff("<!DOCTYPE html><html>"))
        assertNull(sniff("{\"error\":\"nope\"}"))
        assertNull(sniff("<?xml version=\"1.0\"?><Error>"))
    }

    @Test
    fun `bos yanit karar vermez`() {
        assertNull(MimeSniff.fromHead(ByteArray(0), 0))
        assertNull(sniff("   \n\r\n"))
    }

    @Test
    fun `ikili medya duz dosya sayilir`() {
        val mp4 = byteArrayOf(0, 0, 0, 0x18, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        assertEquals(MimeSniff.PROGRESSIVE, MimeSniff.fromHead(mp4, mp4.size))
        val mkv = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        assertEquals(MimeSniff.PROGRESSIVE, MimeSniff.fromHead(mkv, mkv.size))
    }

    @Test
    fun `yalnizca ayristirma kodlari yoklanir`() {
        assertTrue(MimeSniff.isParsingErrorCode(3001))
        assertTrue(MimeSniff.isParsingErrorCode(3002))
        assertTrue(MimeSniff.isParsingErrorCode(3003))
        assertTrue(MimeSniff.isParsingErrorCode(3004))
        assertFalse(MimeSniff.isParsingErrorCode(2004))
        assertFalse(MimeSniff.isParsingErrorCode(4001))
    }
}
