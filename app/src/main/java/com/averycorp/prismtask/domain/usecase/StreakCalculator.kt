package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Configuration for "forgiveness-first" streak calculation.
 *
 * The core idea (v1.4.0 V5): a single missed day shouldn't destroy a streak
 * the way a classic habit tracker would. Instead, the user is allowed
 * [allowedMisses] misses inside a rolling [gracePeriodDays] window before
 * the streak hard-resets. This lines up with how people actually work —
 * life happens, and a forgiving system encourages long-term consistency
 * without punishing an off day.
 *
 * When [enabled] is false the calculator reverts to classic strict-streak
 * behavior (resilientStreak == strictStreak). The user can opt out via
 * the "Forgiveness-first streaks" toggle in Settings.
 */
data class ForgivenessConfig(
    val enabled: Boolean = true,
    val gracePeriodDays: Int = 7,
    val allowedMisses: Int = 1
) {
    companion object {
        val STRICT = ForgivenessConfig(enabled = false)
        val DEFAULT = ForgivenessConfig()
    }
}

/**
 * Result bundle for forgiveness-aware streak calculations.
 *
 * @property strictStreak The classic "consecutive days without a miss" count.
 *                        Matches the old [StreakCalculator.calculateCurrentStreak]
 *                        return value exactly.
 * @property resilientStreak The forgiving count: consecutive *plus* the miss
 *                           days that the grace window tolerated. This is
 *                           the number the UI should display as the primary
 *                           streak when forgiveness is on.
 * @property missesInWindow How many missed days fall inside the current
 *                          rolling grace window.
 * @property gracePeriodRemaining How many more misses the user can afford
 *                                before the streak hard-resets
 *                                (`allowedMisses - missesInWindow`).
 * @property forgivenDates Dates that were missed but stayed within the
 *                         resilient streak so the UI can render them as a
 *                         "forgiven miss" instead of an empty slot.
 */
data class StreakResult(
    val strictStreak: Int,
    val resilientStreak: Int,
    val missesInWindow: Int,
    val gracePeriodRemaining: Int,
    val forgivenDates: List<LocalDate>
) {
    companion object {
        val EMPTY = StreakResult(0, 0, 0, 0, emptyList())
    }
}

object StreakCalculator {

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * Forgiveness-aware streak calculation for daily habits.
     *
     * Walks backwards from today building a consecutive "met" run. When a
     * miss is encountered, checks whether it fits inside the rolling grace
     * window ([ForgivenessConfig.gracePeriodDays]) against the previously
     * tolerated misses. If yes, the miss is absorbed into [StreakResult.resilientStreak]
     * and the walk continues; if no, the walk terminates.
     *
     * The strict streak is the number of consecutive met days *before* the
     * first miss — equivalent to the classic [calculateCurrentStreak] path.
     *
     * Currently only daily habits are supported. Weekly, monthly, and the
     * other period variants fall back to strict streaks (see
     * [calculateResilientStreak]). Those can be wired up in a follow-up pass.
     */
    fun calculateResilientDailyStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate = LocalDate.now(),
        config: ForgivenessConfig = ForgivenessConfig.DEFAULT
    ): StreakResult {
        val target = habit.targetFrequency
        val completionsByDate = completions.groupBy { it.completedDate.toLocalDate() }
            .mapValues { it.value.size }

        // Classic strict streak: the prefix of consecutive met days.
        val strict = calculateDailyStreak(completions, habit, today, longest = false)
        if (!config.enabled) {
            return StreakResult(
                strictStreak = strict,
                resilientStreak = strict,
                missesInWindow = 0,
                gracePeriodRemaining = 0,
                forgivenDates = emptyList()
            )
        }

        val allowed = config.allowedMisses.coerceAtLeast(0)
        val window = config.gracePeriodDays.coerceAtLeast(1)

        // Determine the starting cursor: if today isn't complete yet, count
        // from yesterday so the user isn't penalized mid-day.
        val start = if ((completionsByDate[today] ?: 0) >= target) today else today.minusDays(1)

        // Hard reset: if the starting cursor itself is a miss (meaning
        // today AND yesterday are both missed), the user's current run has
        // already broken — return EMPTY regardless of strict streak value.
        if ((completionsByDate[start] ?: 0) < target) {
            return StreakResult(
                strictStreak = strict,
                resilientStreak = 0,
                missesInWindow = 0,
                gracePeriodRemaining = allowed,
                forgivenDates = emptyList()
            )
        }

        // Don't walk past the earliest known completion — we have no data
        // before then, so counting those days as "misses" would penalize
        // the user for the period before the habit existed.
        val earliestCompletion = completionsByDate.keys.minOrNull()

        var cursor = start
        var metDays = 0
        val missDates = mutableListOf<LocalDate>()

        // Walk backwards. At each step, decide whether the day's miss is
        // tolerable under the rolling window rule: a miss is tolerable iff
        // the number of misses already inside the last [window] days
        // (relative to the cursor) is less than [allowed].
        while (true) {
            val met = (completionsByDate[cursor] ?: 0) >= target
            if (met) {
                metDays++
            } else {
                // Count existing misses that would still be in the window
                // if we were to "absorb" this one. The window is anchored
                // to `cursor`, so a previous miss counts if its date is
                // within `window - 1` days ahead of the cursor.
                val windowStart = cursor
                val windowEnd = cursor.plusDays((window - 1).toLong())
                val priorMissesInWindow = missDates.count { !it.isBefore(windowStart) && !it.isAfter(windowEnd) }
                if (priorMissesInWindow >= allowed) {
                    // No more grace left — stop here.
                    break
                }
                missDates.add(cursor)
            }
            // Guard against absurdly long histories in case of bad data.
            if (metDays + missDates.size > 10_000) break
            cursor = cursor.minusDays(1)
            // Stop once we go past the earliest known completion — we have
            // no observation data before that point.
            if (earliestCompletion != null && cursor.isBefore(earliestCompletion)) break
        }

        val resilient = metDays + missDates.size
        val remaining = (allowed - missDates.size).coerceAtLeast(0)
        return StreakResult(
            strictStreak = strict,
            resilientStreak = resilient,
            missesInWindow = missDates.size,
            gracePeriodRemaining = remaining,
            forgivenDates = missDates.toList()
        )
    }

    /**
     * Forgiveness-aware streak for any habit frequency. Daily habits get the
     * full forgiving walk; other frequencies fall back to the classic strict
     * streak (`resilientStreak == strictStreak`, no forgiven dates).
     */
    fun calculateResilientStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate = LocalDate.now(),
        config: ForgivenessConfig = ForgivenessConfig.DEFAULT
    ): StreakResult {
        if (habit.frequencyPeriod == "daily" || habit.frequencyPeriod.isBlank()) {
            return calculateResilientDailyStreak(completions, habit, today, config)
        }
        val strict = calculateCurrentStreak(completions, habit, today)
        return StreakResult(
            strictStreak = strict,
            resilientStreak = strict,
            missesInWindow = 0,
            gracePeriodRemaining = config.allowedMisses.coerceAtLeast(0),
            forgivenDates = emptyList()
        )
    }

    fun calculateCurrentStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (completions.isEmpty()) return 0

        return when (habit.frequencyPeriod) {
            "weekly" -> calculateWeeklyStreak(completions, habit, today, longest = false)
            "fortnightly" -> calculateFortnightlyStreak(completions, habit, today, longest = false)
            "monthly" -> calculateMonthlyStreak(completions, habit, today, longest = false)
            "bimonthly" -> calculateBimonthlyStreak(completions, habit, today, longest = false)
            "quarterly" -> calculateQuarterlyStreak(completions, habit, today, longest = false)
            else -> calculateDailyStreak(completions, habit, today, longest = false)
        }
    }

    fun calculateLongestStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (completions.isEmpty()) return 0

        return when (habit.frequencyPeriod) {
            "weekly" -> calculateWeeklyStreak(completions, habit, today, longest = true)
            "fortnightly" -> calculateFortnightlyStreak(completions, habit, today, longest = true)
            "monthly" -> calculateMonthlyStreak(completions, habit, today, longest = true)
            "bimonthly" -> calculateBimonthlyStreak(completions, habit, today, longest = true)
            "quarterly" -> calculateQuarterlyStreak(completions, habit, today, longest = true)
            else -> calculateDailyStreak(completions, habit, today, longest = true)
        }
    }

    fun calculateCompletionRate(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        days: Int,
        today: LocalDate = LocalDate.now()
    ): Float {
        if (days <= 0) return 0f
        val startDate = today.minusDays(days.toLong() - 1)
        val activeDays = parseActiveDays(habit.activeDays)

        var totalExpected = 0
        var totalCompleted = 0
        val completionDates = completions.map { it.completedDate.toLocalDate() }

        var date = startDate
        while (!date.isAfter(today)) {
            if (activeDays.isEmpty() || date.dayOfWeek.value in activeDays) {
                totalExpected++
                if (date in completionDates) totalCompleted++
            }
            date = date.plusDays(1)
        }

        return if (totalExpected == 0) 0f else totalCompleted.toFloat() / totalExpected
    }

    fun getCompletionsByDay(
        completions: List<HabitCompletionEntity>,
        startDate: LocalDate,
        endDate: LocalDate
    ): Map<LocalDate, Int> {
        val result = mutableMapOf<LocalDate, Int>()
        val grouped = completions.groupBy { it.completedDate.toLocalDate() }

        var date = startDate
        while (!date.isAfter(endDate)) {
            result[date] = grouped[date]?.size ?: 0
            date = date.plusDays(1)
        }
        return result
    }

    fun getBestDay(completions: List<HabitCompletionEntity>): DayOfWeek? {
        if (completions.isEmpty()) return null
        return completions
            .groupBy { it.completedDate.toLocalDate().dayOfWeek }
            .maxByOrNull { it.value.size }
            ?.key
    }

    fun getWorstDay(completions: List<HabitCompletionEntity>): DayOfWeek? {
        if (completions.isEmpty()) return null
        val grouped = completions.groupBy { it.completedDate.toLocalDate().dayOfWeek }
        return DayOfWeek.entries.minByOrNull { grouped[it]?.size ?: 0 }
    }

    private fun calculateDailyStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate,
        longest: Boolean
    ): Int {
        val target = habit.targetFrequency
        val completionsByDate = completions.groupBy { it.completedDate.toLocalDate() }
            .mapValues { it.value.size }

        if (longest) {
            val dates = completionsByDate.keys.sorted()
            if (dates.isEmpty()) return 0

            var maxStreak = 0
            var currentStreak = 0
            var expectedDate = dates.first()

            for (date in dates) {
                while (expectedDate.isBefore(date)) {
                    if ((completionsByDate[expectedDate] ?: 0) >= target) {
                        currentStreak++
                    } else {
                        maxStreak = maxOf(maxStreak, currentStreak)
                        currentStreak = 0
                    }
                    expectedDate = expectedDate.plusDays(1)
                }
                if ((completionsByDate[date] ?: 0) >= target) {
                    currentStreak++
                } else {
                    maxStreak = maxOf(maxStreak, currentStreak)
                    currentStreak = 0
                }
                expectedDate = date.plusDays(1)
            }
            return maxOf(maxStreak, currentStreak)
        }

        // Current streak: count backward from today/yesterday
        var streak = 0
        var checkDate = today

        // If today isn't completed yet, start from yesterday
        if ((completionsByDate[today] ?: 0) < target) {
            checkDate = today.minusDays(1)
        }

        while ((completionsByDate[checkDate] ?: 0) >= target) {
            streak++
            checkDate = checkDate.minusDays(1)
        }

        return streak
    }

    private fun calculateWeeklyStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate,
        longest: Boolean
    ): Int {
        val target = habit.targetFrequency
        val activeDays = parseActiveDays(habit.activeDays)

        // Group completions by week (Mon-Sun)
        val completionsByWeek = completions.groupBy { completion ->
            val date = completion.completedDate.toLocalDate()
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        }.mapValues { entry ->
            if (activeDays.isEmpty()) {
                entry.value.size
            } else {
                entry.value.count { it.completedDate.toLocalDate().dayOfWeek.value in activeDays }
            }
        }

        if (longest) {
            val weeks = completionsByWeek.keys.sorted()
            if (weeks.isEmpty()) return 0

            var maxStreak = 0
            var currentStreak = 0
            var expectedWeek = weeks.first()

            for (week in weeks) {
                while (expectedWeek.isBefore(week)) {
                    if ((completionsByWeek[expectedWeek] ?: 0) >= target) {
                        currentStreak++
                    } else {
                        maxStreak = maxOf(maxStreak, currentStreak)
                        currentStreak = 0
                    }
                    expectedWeek = expectedWeek.plusWeeks(1)
                }
                if ((completionsByWeek[week] ?: 0) >= target) {
                    currentStreak++
                } else {
                    maxStreak = maxOf(maxStreak, currentStreak)
                    currentStreak = 0
                }
                expectedWeek = week.plusWeeks(1)
            }
            return maxOf(maxStreak, currentStreak)
        }

        // Current weekly streak
        var streak = 0
        var checkWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        // If current week target isn't met yet, start from previous week
        if ((completionsByWeek[checkWeekStart] ?: 0) < target) {
            checkWeekStart = checkWeekStart.minusWeeks(1)
        }

        while ((completionsByWeek[checkWeekStart] ?: 0) >= target) {
            streak++
            checkWeekStart = checkWeekStart.minusWeeks(1)
        }

        return streak
    }

    private fun getFortnightStart(date: LocalDate): LocalDate {
        val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        // Align fortnights using ISO week number: odd weeks start a fortnight
        val weekNum = weekStart.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return if (weekNum % 2 == 0) weekStart.minusWeeks(1) else weekStart
    }

    private fun calculateFortnightlyStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate,
        longest: Boolean
    ): Int {
        val target = habit.targetFrequency

        // Group completions by fortnight start
        val completionsByFortnight = completions.groupBy { completion ->
            val date = completion.completedDate.toLocalDate()
            getFortnightStart(date)
        }.mapValues { it.value.groupBy { c -> c.completedDate.toLocalDate() }.size }

        if (longest) {
            val fortnights = completionsByFortnight.keys.sorted()
            if (fortnights.isEmpty()) return 0

            var maxStreak = 0
            var currentStreak = 0
            var expected = fortnights.first()

            for (fn in fortnights) {
                while (expected.isBefore(fn)) {
                    if ((completionsByFortnight[expected] ?: 0) >= target) currentStreak++
                    else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                    expected = expected.plusWeeks(2)
                }
                if ((completionsByFortnight[fn] ?: 0) >= target) currentStreak++
                else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                expected = fn.plusWeeks(2)
            }
            return maxOf(maxStreak, currentStreak)
        }

        var streak = 0
        var checkStart = getFortnightStart(today)
        if ((completionsByFortnight[checkStart] ?: 0) < target) {
            checkStart = checkStart.minusWeeks(2)
        }
        while ((completionsByFortnight[checkStart] ?: 0) >= target) {
            streak++
            checkStart = checkStart.minusWeeks(2)
        }
        return streak
    }

    private fun calculateMonthlyStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate,
        longest: Boolean
    ): Int {
        val target = habit.targetFrequency

        // Group completions by month start
        val completionsByMonth = completions.groupBy { completion ->
            val date = completion.completedDate.toLocalDate()
            date.withDayOfMonth(1)
        }.mapValues { it.value.groupBy { c -> c.completedDate.toLocalDate() }.size }

        if (longest) {
            val months = completionsByMonth.keys.sorted()
            if (months.isEmpty()) return 0

            var maxStreak = 0
            var currentStreak = 0
            var expected = months.first()

            for (month in months) {
                while (expected.isBefore(month)) {
                    if ((completionsByMonth[expected] ?: 0) >= target) currentStreak++
                    else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                    expected = expected.plusMonths(1)
                }
                if ((completionsByMonth[month] ?: 0) >= target) currentStreak++
                else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                expected = month.plusMonths(1)
            }
            return maxOf(maxStreak, currentStreak)
        }

        var streak = 0
        var checkMonth = today.withDayOfMonth(1)
        if ((completionsByMonth[checkMonth] ?: 0) < target) {
            checkMonth = checkMonth.minusMonths(1)
        }
        while ((completionsByMonth[checkMonth] ?: 0) >= target) {
            streak++
            checkMonth = checkMonth.minusMonths(1)
        }
        return streak
    }

    private fun getBimonthStart(date: LocalDate): LocalDate {
        // Align to 2-month periods starting from January
        val month = date.monthValue
        val startMonth = if (month % 2 == 0) month - 1 else month
        return date.withMonth(startMonth).withDayOfMonth(1)
    }

    private fun calculateBimonthlyStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate,
        longest: Boolean
    ): Int {
        val target = habit.targetFrequency

        // Group completions by bimonth start (Jan-Feb, Mar-Apr, etc.)
        val completionsByBimonth = completions.groupBy { completion ->
            val date = completion.completedDate.toLocalDate()
            getBimonthStart(date)
        }.mapValues { it.value.groupBy { c -> c.completedDate.toLocalDate() }.size }

        if (longest) {
            val bimonths = completionsByBimonth.keys.sorted()
            if (bimonths.isEmpty()) return 0

            var maxStreak = 0
            var currentStreak = 0
            var expected = bimonths.first()

            for (bm in bimonths) {
                while (expected.isBefore(bm)) {
                    if ((completionsByBimonth[expected] ?: 0) >= target) currentStreak++
                    else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                    expected = expected.plusMonths(2)
                }
                if ((completionsByBimonth[bm] ?: 0) >= target) currentStreak++
                else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                expected = bm.plusMonths(2)
            }
            return maxOf(maxStreak, currentStreak)
        }

        var streak = 0
        var checkStart = getBimonthStart(today)
        if ((completionsByBimonth[checkStart] ?: 0) < target) {
            checkStart = checkStart.minusMonths(2)
        }
        while ((completionsByBimonth[checkStart] ?: 0) >= target) {
            streak++
            checkStart = checkStart.minusMonths(2)
        }
        return streak
    }

    private fun getQuarterStart(date: LocalDate): LocalDate {
        // Align to 3-month periods starting from January (Q1=Jan-Mar, Q2=Apr-Jun, etc.)
        val month = date.monthValue
        val startMonth = ((month - 1) / 3) * 3 + 1
        return date.withMonth(startMonth).withDayOfMonth(1)
    }

    private fun calculateQuarterlyStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate,
        longest: Boolean
    ): Int {
        val target = habit.targetFrequency

        // Group completions by quarter start
        val completionsByQuarter = completions.groupBy { completion ->
            val date = completion.completedDate.toLocalDate()
            getQuarterStart(date)
        }.mapValues { it.value.groupBy { c -> c.completedDate.toLocalDate() }.size }

        if (longest) {
            val quarters = completionsByQuarter.keys.sorted()
            if (quarters.isEmpty()) return 0

            var maxStreak = 0
            var currentStreak = 0
            var expected = quarters.first()

            for (q in quarters) {
                while (expected.isBefore(q)) {
                    if ((completionsByQuarter[expected] ?: 0) >= target) currentStreak++
                    else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                    expected = expected.plusMonths(3)
                }
                if ((completionsByQuarter[q] ?: 0) >= target) currentStreak++
                else { maxStreak = maxOf(maxStreak, currentStreak); currentStreak = 0 }
                expected = q.plusMonths(3)
            }
            return maxOf(maxStreak, currentStreak)
        }

        var streak = 0
        var checkStart = getQuarterStart(today)
        if ((completionsByQuarter[checkStart] ?: 0) < target) {
            checkStart = checkStart.minusMonths(3)
        }
        while ((completionsByQuarter[checkStart] ?: 0) >= target) {
            streak++
            checkStart = checkStart.minusMonths(3)
        }
        return streak
    }

    private fun parseActiveDays(json: String?): Set<Int> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            json.trim('[', ']').split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
