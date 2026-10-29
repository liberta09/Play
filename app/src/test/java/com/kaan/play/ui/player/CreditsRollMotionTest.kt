package com.kaan.play.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditsRollMotionTest {
    private val motion = CreditsRollMotion()
    private val credits = listOf(
        CreditTextLine("Directed by Jane Doe", 0.24f),
        CreditTextLine("Produced by Alex Smith", 0.35f),
        CreditTextLine("Music by Sam Lee", 0.46f),
        CreditTextLine("Cinematography Pat Kim", 0.57f),
        CreditTextLine("Written by Taylor Jones", 0.68f),
    )

    @Test
    fun requiresSeveralFramesOfConsistentUpwardTextMotion() {
        assertFalse(motion.observe(credits))
        assertFalse(motion.observe(credits.shiftUp(0.02f)))
        assertTrue(motion.observe(credits.shiftUp(0.04f)))
    }

    @Test
    fun staticTextDoesNotLookLikeRollingCredits() {
        assertFalse(motion.observe(credits))
        assertFalse(motion.observe(credits))
        assertFalse(motion.observe(credits))
    }

    @Test
    fun oneOrTwoSubtitleLinesCannotTriggerCreditsDetection() {
        val subtitle = listOf(
            CreditTextLine("We need to leave now", 0.84f),
            CreditTextLine("The train is coming", 0.90f),
        )
        assertFalse(motion.observe(subtitle))
        assertFalse(motion.observe(subtitle.map { it.copy(centerY = it.centerY - 0.02f) }))
        assertFalse(motion.observe(subtitle.map { it.copy(centerY = it.centerY - 0.04f) }))
    }

    @Test
    fun downwardOrInconsistentMotionDoesNotTriggerCreditsDetection() {
        assertFalse(motion.observe(credits))
        assertFalse(motion.observe(credits.shiftDown(0.02f)))
        assertFalse(motion.observe(credits.shiftDown(0.04f)))
        motion.reset()
        assertFalse(motion.observe(credits))
        assertFalse(motion.observe(credits.shiftUp(0.02f).mapIndexed { index, line ->
            if (index < 2) line.copy(centerY = line.centerY + 0.2f) else line
        }))
        assertFalse(motion.observe(credits.shiftUp(0.04f).mapIndexed { index, line ->
            if (index < 2) line.copy(centerY = line.centerY + 0.2f) else line
        }))
    }

    @Test
    fun lostFrameResetsConsecutiveMotionConfirmation() {
        assertFalse(motion.observe(credits))
        assertFalse(motion.observe(credits.shiftUp(0.02f)))
        assertFalse(motion.observe(emptyList()))
        assertFalse(motion.observe(credits.shiftUp(0.06f)))
        assertFalse(motion.observe(credits.shiftUp(0.08f)))
    }

    private fun List<CreditTextLine>.shiftUp(amount: Float) = map { it.copy(centerY = it.centerY - amount) }
    private fun List<CreditTextLine>.shiftDown(amount: Float) = map { it.copy(centerY = it.centerY + amount) }
}
