package com.averycorp.prismtask.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyForgivenessStreakCoreTest {

    private val today = LocalDate.of(2026, 4, 18)

    @Test
    fun emptySet_returnsEmpty() {
        val result = DailyForgivenessStreakCore.calculate(emptySet(), today)
        assertEquals(StreakResult.EMPTY, result)
    }

    @Test
    fun consecutiveRun_fromToday_countsAllAsStrictAndResilient() {
        val dates = daysBackwards(today, 5)
        val result = DailyForgivenessStreakCore.calculate(dates, today)
        assertEquals(5, result.strictStreak)
        assertEquals(5, result.resilientStreak)
        assertTrue(result.forgivenDates.isEmpty())
    }

    @Test
    fun todayMissing_yesterdayMet_strictStreakIsOne_fromYesterday() {
        // Mid-day rule: if today isn't logged yet, start from yesterday.
        val dates = daysBackwards(today.minusDays(1), 3)
        val result = DailyForgivenessStreakCore.calculate(dates, today)
        assertEquals(3, result.strictStreak)
        assertEquals(3, result.resilientStreak)
    }

    @Test
    fun todayAndYesterdayBothMissing_hardResetsResilientToZero() {
        // Even if the user had a great 10-day run ending 3 days ago, two
        // consecutive misses at the cursor end the current resilient run.
        val dates = daysBackwards(today.minusDays(3), 10)
        val result = DailyForgivenessStreakCore.calculate(dates, today)
        assertEquals(0, result.resilientStreak)
        // Strict walks from yesterday and immediately breaks (yesterday is a miss).
        assertEquals(0, result.strictStreak)
    }

    @Test
    fun singleMissWithinGrace_isAbsorbedIntoResilient() {
        // Today met, yesterday miss, two days ago met, three days ago met.
        val dates = setOf(today, today.minusDays(2), today.minusDays(3))
        val result = DailyForgivenessStreakCore.calculate(
            dates,
            today,
            ForgivenessConfig(enabled = true, gracePeriodDays = 7, allowedMisses = 1)
        )
        // Strict: today is met (1), yesterday miss → strict stops at 1.
        assertEquals(1, result.strictStreak)
        // Resilient: absorbs yesterday's miss → 1 (today) + 1 (forgiven) + 2 (prior met) = 4.
        assertEquals(4, result.resilientStreak)
        assertEquals(listOf(today.minusDays(1)), result.forgivenDates)
        assertEquals(1, result.missesInWindow)
        assertEquals(0, result.gracePeriodRemaining)
    }

    @Test
    fun twoMissesInsideWindow_breaksWhenGraceExhausted() {
        // Today met, yesterday miss, -2 miss, -3 met, -4 met. allowedMisses=1
        // tolerates the first miss but not the second.
        val dates = setOf(today, today.minusDays(3), today.minusDays(4))
        val result = DailyForgivenessStreakCore.calculate(
            dates,
            today,
            ForgivenessConfig(enabled = true, gracePeriodDays = 7, allowedMisses = 1)
        )
        assertEquals(1, result.strictStreak)
        // Absorbs yesterday (1 miss), hits -2 miss — rolling window (yesterday
        // through 5 days ahead of -2) already has yesterday miss, so -2 can't
        // be absorbed → walk terminates before counting -2, -3, -4.
        assertEquals(2, result.resilientStreak) // today + forgiven yesterday
        assertEquals(1, result.missesInWindow)
    }

    @Test
    fun missesOutsideGraceWindow_eachConsumesGraceSeparately() {
        // With allowedMisses=2 and gracePeriodDays=7: two misses inside the
        // window are tolerated; three misses in a 7-day span terminates.
        val dates = setOf(
            today,
            today.minusDays(2),
            today.minusDays(4),
            today.minusDays(5),
            today.minusDays(6)
        )
        val result = DailyForgivenessStreakCore.calculate(
            dates,
            today,
            ForgivenessConfig(enabled = true, gracePeriodDays = 7, allowedMisses = 2)
        )
        // Walk absorbs yesterday miss (1) and -3 miss (2), then the walk
        // continues through -4, -5, -6. Total = today + 1 + -2 + 1 + -4 + -5 + -6 = 7.
        // misses = 2, metDays = 5.
        assertEquals(7, result.resilientStreak)
        assertEquals(2, result.missesInWindow)
        assertEquals(0, result.gracePeriodRemaining)
        assertEquals(setOf(today.minusDays(1), today.minusDays(3)), result.forgivenDates.toSet())
    }

    @Test
    fun forgivenessDisabled_resilientCollapsesToStrict() {
        val dates = setOf(today, today.minusDays(2))
        val result = DailyForgivenessStreakCore.calculate(
            dates,
            today,
            ForgivenessConfig.STRICT
        )
        assertEquals(1, result.strictStreak)
        assertEquals(1, result.resilientStreak)
        assertTrue(result.forgivenDates.isEmpty())
        assertEquals(0, result.missesInWindow)
        assertEquals(0, result.gracePeriodRemaining)
    }

    @Test
    fun walkStopsAtEarliestKnownActivity_noPunishmentForPreHistory() {
        val dates = setOf(today, today.minusDays(1), today.minusDays(2))
        val result = DailyForgivenessStreakCore.calculate(
            dates,
            today,
            ForgivenessConfig(enabled = true, gracePeriodDays = 7, allowedMisses = 1)
        )
        // Walk hits today-2 (met), then next cursor today-3 is before earliest
        // known → stop. No phantom miss on today-3.
        assertEquals(3, result.resilientStreak)
        assertEquals(3, result.strictStreak)
        assertTrue(result.forgivenDates.isEmpty())
    }

    @Test
    fun strictWalk_ignoresForgivenessConfig() {
        // Two misses in the middle of the history; strict should still be just
        // the prefix of consecutive met days from today.
        val dates = setOf(today, today.minusDays(1), today.minusDays(4))
        val result = DailyForgivenessStreakCore.calculate(
            dates,
            today,
            ForgivenessConfig(enabled = true, gracePeriodDays = 7, allowedMisses = 3)
        )
        assertEquals(2, result.strictStreak) // today + yesterday, break at -2
    }

    @Test
    fun singleDayActivity_today_returnsOne() {
        val dates = setOf(today)
        val result = DailyForgivenessStreakCore.calculate(dates, today)
        assertEquals(1, result.strictStreak)
        assertEquals(1, result.resilientStreak)
    }

    private fun daysBackwards(start: LocalDate, count: Int): Set<LocalDate> =
        (0 until count).map { start.minusDays(it.toLong()) }.toSet()
}
