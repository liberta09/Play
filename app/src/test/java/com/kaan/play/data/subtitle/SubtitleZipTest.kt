package com.kaan.play.data.subtitle

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SubtitleZipEpisodeTest {

    private fun createZip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private val sampleSrt1 = "1\n00:00:01,000 --> 00:00:04,000\nEpisode 1".toByteArray(Charsets.UTF_8)
    private val sampleSrt21 = "1\n00:00:01,000 --> 00:00:04,000\nEpisode 21 Correct".toByteArray(Charsets.UTF_8)
    private val sampleSrt22 = "1\n00:00:01,000 --> 00:00:04,000\nEpisode 22".toByteArray(Charsets.UTF_8)

    @Test
    fun isZipIdentifiesZipHeader() {
        val validZip = createZip(mapOf("test.srt" to sampleSrt1))
        assertTrue(SubtitleZip.isZip(validZip))
        assertFalse(SubtitleZip.isZip(byteArrayOf(1, 2, 3)))
        assertFalse(SubtitleZip.isZip("Not a zip".toByteArray()))
        assertFalse(SubtitleZip.isZip(ByteArray(0)))
    }

    @Test
    fun rank1MatchesSeasonAndEpisodeFormat() {
        val zip = createZip(
            mapOf(
                "Criminal.Minds.S01E01.HDTV.srt" to sampleSrt1,
                "Criminal.Minds.S01E21.720p.HDTV.srt" to sampleSrt21,
                "Criminal.Minds.S01E22.HDTV.srt" to sampleSrt22,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 21)
        assertNotNull(extracted)
        assertArrayEquals(sampleSrt21, extracted)
    }

    @Test
    fun rank1Matches1x21Format() {
        val zip = createZip(
            mapOf(
                "Criminal.Minds.1x01.srt" to sampleSrt1,
                "Criminal Minds - 1x21 - Secrets.srt" to sampleSrt21,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 21)
        assertNotNull(extracted)
        assertArrayEquals(sampleSrt21, extracted)
    }

    @Test
    fun rank2MatchesE21AndEp21WithoutConflictingSeason() {
        val zip = createZip(
            mapOf(
                "Episode_01.srt" to sampleSrt1,
                "ep21.srt" to sampleSrt21,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 21)
        assertNotNull(extracted)
        assertArrayEquals(sampleSrt21, extracted)
    }

    @Test
    fun rank2RejectsConflictingSeason() {
        val s02e21 = "Season 2 Episode 21".toByteArray(Charsets.UTF_8)
        val zip = createZip(
            mapOf(
                "Criminal.Minds.S02E21.srt" to s02e21,
                "Criminal.Minds.E01.srt" to sampleSrt1,
                "Criminal.Minds.S01E21.srt" to sampleSrt21,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 21)
        assertNotNull(extracted)
        assertArrayEquals(sampleSrt21, extracted)
    }

    @Test
    fun rank3MatchesStandaloneEpisodeNumber() {
        val zip = createZip(
            mapOf(
                "Criminal Minds - 01.srt" to sampleSrt1,
                "Criminal Minds - 21.srt" to sampleSrt21,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 21)
        assertNotNull(extracted)
        assertArrayEquals(sampleSrt21, extracted)
    }

    @Test
    fun doesNotFalseMatchYearAsEpisodeNumber() {
        val movie2021 = "Movie from 2021".toByteArray(Charsets.UTF_8)
        val zip = createZip(
            mapOf(
                "Some.Movie.2021.720p.BluRay.srt" to movie2021,
                "Episode 21.srt" to sampleSrt21,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 21)
        assertNotNull(extracted)
        assertArrayEquals(sampleSrt21, extracted)
    }

    @Test
    fun fallbackPicksLargestSrtWhenNoEpisodeMatch() {
        val smallSrt = ByteArray(100) { 1 }
        val largeSrt = ByteArray(500) { 2 }
        val zip = createZip(
            mapOf(
                "notes.srt" to smallSrt,
                "complete_movie.srt" to largeSrt,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 99)
        assertNotNull(extracted)
        assertEquals(500, extracted!!.size)
    }

    @Test
    fun fallbackPicksLargestSrtForMovieWithoutEpisode() {
        val smallSrt = ByteArray(200) { 1 }
        val largeSrt = ByteArray(800) { 2 }
        val zip = createZip(
            mapOf(
                "Joker.sample.srt" to smallSrt,
                "Joker.2019.1080p.WEBRip.srt" to largeSrt,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = null, episode = null)
        assertNotNull(extracted)
        assertEquals(800, extracted!!.size)
    }

    @Test
    fun ignoresMacOsMetadataAndDirectories() {
        val zip = createZip(
            mapOf(
                "__MACOSX/._sub.srt" to ByteArray(50) { 0 },
                "subfolder/video.srt" to sampleSrt21,
            )
        )
        val extracted = SubtitleZip.extract(zip, 2 * 1024 * 1024L, season = 1, episode = 21)
        assertNotNull(extracted)
        assertArrayEquals(sampleSrt21, extracted)
    }

    @Test
    fun returnsNullWhenNoSubtitleFoundOrNonZip() {
        val zip = createZip(
            mapOf(
                "image.png" to ByteArray(100),
                "readme.txt" to ByteArray(50),
            )
        )
        assertNull(SubtitleZip.extract(zip, 2 * 1024 * 1024L))
        assertNull(SubtitleZip.extract(ByteArray(10), 2 * 1024 * 1024L))
    }

    @Test
    fun skipsEntryExceedingMaxEntryBytes() {
        val hugeSrt = ByteArray(1024 * 1024) // 1 MB
        val normalSrt = ByteArray(100) { 5 }
        val zip = createZip(
            mapOf(
                "huge.srt" to hugeSrt,
                "normal.srt" to normalSrt,
            )
        )
        // Limit to 500 KB per entry
        val extracted = SubtitleZip.extract(zip, 500 * 1024L)
        assertNotNull(extracted)
        assertEquals(100, extracted!!.size)
    }
}
