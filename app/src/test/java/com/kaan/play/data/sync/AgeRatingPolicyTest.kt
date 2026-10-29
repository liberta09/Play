package com.kaan.play.data.sync

import com.kaan.play.data.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeRatingPolicyTest {
    @Test fun onlyChildAndFamilyGenresAreAccepted() {
        assertTrue(AgeRatingPolicy.allowsChildGenres(MediaType.Movie, listOf(16)))
        assertTrue(AgeRatingPolicy.allowsChildGenres(MediaType.Movie, listOf(10751)))
        assertTrue(AgeRatingPolicy.allowsChildGenres(MediaType.Tv, listOf(10762)))
        assertFalse(AgeRatingPolicy.allowsChildGenres(MediaType.Movie, listOf(28)))
        assertFalse(AgeRatingPolicy.allowsChildGenres(MediaType.Tv, listOf(10759)))
        assertFalse(AgeRatingPolicy.allowsChildGenres(MediaType.Movie, listOf(16), adult = true))
        assertFalse(AgeRatingPolicy.allowsChildGenres(MediaType.Movie, emptyList()))
    }

    @Test fun unknownOrOverTwelveRatingsFailClosed() {
        assertTrue(AgeRatingPolicy.allowsChild("G"))
        assertTrue(AgeRatingPolicy.allowsChild("PG"))
        assertTrue(AgeRatingPolicy.allowsChild("12"))
        assertFalse(AgeRatingPolicy.allowsChild("PG-13"))
        assertFalse(AgeRatingPolicy.allowsChild("18+"))
        assertFalse(AgeRatingPolicy.allowsChild(null))
        assertFalse(AgeRatingPolicy.allowsChild("NR"))
    }
}
