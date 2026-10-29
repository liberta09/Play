package com.kaan.play.data.match

import com.kaan.play.data.match.ContentDurationCheck.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentDurationCheckTest {
    private val min = 60_000L

    @Test
    fun movie_33MinuteVideoForA178MinuteFilm_isMismatch() {
        assertEquals(Result.Mismatch, ContentDurationCheck.check(33 * min + 25_000, 178, ContentKind.Movie))
    }

    @Test
    fun movie_normalAndExtendedEditionsAreOk() {
        assertEquals(Result.Ok, ContentDurationCheck.check(178 * min, 178, ContentKind.Movie))
        assertEquals(Result.Ok, ContentDurationCheck.check(200 * min, 178, ContentKind.Movie)) // extended
        assertEquals(Result.Ok, ContentDurationCheck.check(228 * min, 179, ContentKind.Movie)) // LOTR extended
        assertEquals(Result.Ok, ContentDurationCheck.check(95 * min, 178, ContentKind.Movie)) // %53
    }

    @Test
    fun movie_boundaries() {
        assertEquals(Result.Mismatch, ContentDurationCheck.check(89 * min, 180, ContentKind.Movie)) // %49,4
        assertEquals(Result.Ok, ContentDurationCheck.check(90 * min, 180, ContentKind.Movie)) // %50
        assertEquals(Result.Ok, ContentDurationCheck.check(324 * min, 180, ContentKind.Movie)) // %180
        assertEquals(Result.Mismatch, ContentDurationCheck.check(325 * min, 180, ContentKind.Movie))
        assertEquals(Result.Mismatch, ContentDurationCheck.check(600 * min, 100, ContentKind.Movie))
    }

    @Test
    fun unknownDurationOrRuntime_neverEliminates() {
        assertEquals(Result.Unknown, ContentDurationCheck.check(0, 178, ContentKind.Movie)) // canli/bilinmeyen
        assertEquals(Result.Unknown, ContentDurationCheck.check(-1, 178, ContentKind.Movie))
        assertEquals(Result.Unknown, ContentDurationCheck.check(10 * min, null, ContentKind.Movie))
        assertEquals(Result.Unknown, ContentDurationCheck.check(10 * min, 0, ContentKind.Tv))
        assertEquals(Result.Unknown, ContentDurationCheck.check(2 * min, 12, ContentKind.Movie)) // kisa film: oran guvenilmez
    }

    @Test
    fun episode_halfLengthWrongSeriesAndExtremeDurationsAreEliminated() {
        assertEquals(Result.Ok, ContentDurationCheck.check(42 * min, 45, ContentKind.Tv))
        assertEquals(Result.Ok, ContentDurationCheck.check(30 * min, 45, ContentKind.Tv)) // %66.7 alt sınırın içinde
        assertEquals(Result.Ok, ContentDurationCheck.check(67 * min, 45, ContentKind.Tv)) // yaklaşık %149
        assertEquals(Result.Mismatch, ContentDurationCheck.check(20 * min, 45, ContentKind.Tv)) // Lupin gibi yarı süreli yanlış bölüm
        assertEquals(Result.Mismatch, ContentDurationCheck.check(80 * min, 45, ContentKind.Tv)) // > 1.5 kat
        assertEquals(Result.Mismatch, ContentDurationCheck.check(14 * min, 45, ContentKind.Tv)) // < %65
        assertEquals(Result.Mismatch, ContentDurationCheck.check(180 * min, 22, ContentKind.Tv)) // film uzunlugunda video
    }
}
