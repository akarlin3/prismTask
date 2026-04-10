package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object StreakCalculator {

    private fun Long.toLocalDate(): LocalDate =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
