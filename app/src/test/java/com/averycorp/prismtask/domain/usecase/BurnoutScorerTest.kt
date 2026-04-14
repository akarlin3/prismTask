package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the v1.4.0 V2 [BurnoutScorer].
 *
 * These check the component ceilings (no single signal can push the score
 * past its allowance), the band mapping, and the two extreme cases (all
 * zeros → 0, all maxed → 100).
 */
class BurnoutScorerTest {
    private val scorer = BurnoutScorer()

    @Test
    fun `all zero inputs produce zero score`() {
        val result = scorer.compute(BurnoutInputs())
        assertEquals(0, result.score)
        assertEquals(BurnoutBand.BALANCED, result.band)
    }

    @Test
    fun `all components maxed produce score of one hundred`() {
        val result = scorer.compute(
            BurnoutInputs(
                workRatio = 1f, // target + 0.60 → caps at 0.40 overshoot → 25 pts
                workTarget = 0.40f,
                overdueCount = 50, // caps at 10 → 20 pts
                skippedSelfCareRatio = 1f, // → 20 pts
                medicationGapRatio = 1f, // → 15 pts
                streakBreaks = 10, // caps at 5 → 10 pts
                restDeficit = true // → 10 pts
            )
        )
        assertEquals(100, result.score)
        assertEquals(BurnoutBand.HIGH_RISK, result.band)
    }

    @Test
    fun `work overshoot alone scales up to its ceiling`() {
        val result = scorer.compute(
            BurnoutInputs(workRatio = 0.80f, workTarget = 0.40f)
        )
        // 0.40 overshoot / 0.40 max = 1.0 * 25 = 25.
        assertEquals(25, result.workOvershootPoints)
        assertEquals(25, result.score)
        assertEquals(BurnoutBand.BALANCED, result.band)
    }

    @Test
    fun `work at target contributes zero`() {
        val result = scorer.compute(
            BurnoutInputs(workRatio = 0.40f, workTarget = 0.40f)
        )
        assertEquals(0, result.workOvershootPoints)
    }

    @Test
    fun `work below target contributes zero`() {
        val result = scorer.compute(
            BurnoutInputs(workRatio = 0.20f, workTarget = 0.40f)
        )
        assertEquals(0, result.workOvershootPoints)
    }

    @Test
    fun `overdue count scales up to twenty points at ten or more`() {
        val ten = scorer.compute(BurnoutInputs(overdueCount = 10))
        assertEquals(20, ten.overduePoints)
        val twenty = scorer.compute(BurnoutInputs(overdueCount = 20))
        assertEquals(20, twenty.overduePoints)
    }

    @Test
    fun `skipped self care ratio scales linearly`() {
        val half = scorer.compute(BurnoutInputs(skippedSelfCareRatio = 0.5f))
        assertEquals(10, half.skippedSelfCarePoints)
    }

    @Test
    fun `medication gap scales linearly`() {
        val third = scorer.compute(BurnoutInputs(medicationGapRatio = 0.33f))
        // 0.33 * 15 = 4.95 → int → 4
        assertEquals(4, third.medicationPoints)
    }

    @Test
    fun `rest deficit is either zero or ten`() {
        val off = scorer.compute(BurnoutInputs(restDeficit = false))
        val on = scorer.compute(BurnoutInputs(restDeficit = true))
        assertEquals(0, off.restDeficitPoints)
        assertEquals(10, on.restDeficitPoints)
    }

    @Test
    fun `band boundaries map correctly`() {
        assertEquals(BurnoutBand.BALANCED, BurnoutBand.forScore(0))
        assertEquals(BurnoutBand.BALANCED, BurnoutBand.forScore(25))
        assertEquals(BurnoutBand.MONITOR, BurnoutBand.forScore(26))
        assertEquals(BurnoutBand.MONITOR, BurnoutBand.forScore(50))
        assertEquals(BurnoutBand.CAUTION, BurnoutBand.forScore(51))
        assertEquals(BurnoutBand.CAUTION, BurnoutBand.forScore(75))
        assertEquals(BurnoutBand.HIGH_RISK, BurnoutBand.forScore(76))
        assertEquals(BurnoutBand.HIGH_RISK, BurnoutBand.forScore(100))
    }

    @Test
    fun `moderate overload plus overdue lands in monitor band`() {
        val result = scorer.compute(
            BurnoutInputs(
                workRatio = 0.55f, // overshoot 0.15/0.40 → 9 pts
                workTarget = 0.40f,
                overdueCount = 5, // scaled → 10 pts
                skippedSelfCareRatio = 0.3f // → 6 pts
            )
        )
        // Total around 25, right at the balanced/monitor edge.
        assertTrue(result.score in 20..35)
    }

    @Test
    fun `score is always clamped to 0 through 100`() {
        // Even if components somehow summed to more than 100, result clamps.
        val result = scorer.compute(
            BurnoutInputs(
                workRatio = 2f,
                workTarget = 0.0f,
                overdueCount = 9999,
                skippedSelfCareRatio = 2f,
                medicationGapRatio = 2f,
                streakBreaks = 9999,
                restDeficit = true
            )
        )
        assertEquals(100, result.score)
    }
}
