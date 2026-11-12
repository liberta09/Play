package com.kaan.play.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewEpisodeRulesTest {

    @Test
    fun firstCheckRecordsBaselineSilentlyWithoutNotification() {
        val latest = AiredEpisode(season = 2, episode = 5, airDate = "2026-11-09")
        val decision = NewEpisodeRules.evaluateSeries(
            previous = null,
            latest = latest,
            todayIso = "2026-11-10",
        )
        assertEquals(EpisodeCheckDecision.RecordBaseline(2, 5), decision)
    }

    @Test
    fun newerEpisodeSameSeasonOrSeasonJumpNotifiesOnce() {
        val prev = StoredEpisodeState(season = 1, episode = 10)
        assertEquals(
            EpisodeCheckDecision.NoChange,
            NewEpisodeRules.evaluateSeries(prev, AiredEpisode(1, 10, "2026-11-10"), "2026-11-10"),
        )
        assertEquals(
            EpisodeCheckDecision.NoChange,
            NewEpisodeRules.evaluateSeries(prev, AiredEpisode(1, 9, "2026-11-01"), "2026-11-10"),
        )
        assertEquals(
            EpisodeCheckDecision.NotifyNewEpisode(1, 11),
            NewEpisodeRules.evaluateSeries(prev, AiredEpisode(1, 11, "2026-11-10"), "2026-11-10"),
        )
        assertEquals(
            EpisodeCheckDecision.NotifyNewEpisode(2, 1),
            NewEpisodeRules.evaluateSeries(prev, AiredEpisode(2, 1, "2026-11-10"), "2026-11-10"),
        )
    }

    @Test
    fun futureOrInvalidAirDateDoesNotNotify() {
        val prev = StoredEpisodeState(season = 2, episode = 1)
        assertFalse(NewEpisodeRules.isReleasedOnOrBefore("2026-11-11", "2026-11-10"))
        assertTrue(NewEpisodeRules.isReleasedOnOrBefore("2026-11-10", "2026-11-10"))
        assertFalse(NewEpisodeRules.isReleasedOnOrBefore(null, "2026-11-10"))

        assertEquals(
            EpisodeCheckDecision.NoChange,
            NewEpisodeRules.evaluateSeries(prev, AiredEpisode(2, 2, "2026-11-11"), "2026-11-10"),
        )
        assertEquals(
            EpisodeCheckDecision.NoChange,
            NewEpisodeRules.evaluateSeries(prev, AiredEpisode(2, 2, ""), "2026-11-10"),
        )
    }

    @Test
    fun stateEncodingAndDecodingResilientToMalformedEntries() {
        val encoded = NewEpisodeRules.encodeState(
            mapOf(
                37799 to StoredEpisodeState(2, 5),
                1402 to StoredEpisodeState(11, 24),
            ),
        )
        assertEquals(
            mapOf(
                1402 to StoredEpisodeState(11, 24),
                37799 to StoredEpisodeState(2, 5),
            ),
            NewEpisodeRules.decodeState(encoded),
        )
        assertEquals(
            mapOf(42 to StoredEpisodeState(3, 1)),
            NewEpisodeRules.decodeState("bad,42:3:1,-1:1:1,99:x:2"),
        )
    }
}
