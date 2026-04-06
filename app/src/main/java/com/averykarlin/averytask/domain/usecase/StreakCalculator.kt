package com.averykarlin.averytask.domain.usecase

import com.averykarlin.averytask.data.local.entity.HabitCompletionEntity
import com.averykarlin.averytask.data.local.entity.HabitEntity
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

        return if (habit.frequencyPeriod == "weekly") {
            calculateWeeklyStreak(completions, habit, today, longest = false)
        } else {
            calculateDailyStreak(completions, habit, today, longest = false)
        }
    }

    fun calculateLongestStreak(
        completions: List<HabitCompletionEntity>,
        habit: HabitEntity,
        today: LocalDate = LocalDate.now()
    ): Int {
        if (completions.isEmpty()) return 0

        return if (habit.frequencyPeriod == "weekly") {
            calculateWeeklyStreak(completions, habit, today, longest = true)
        } else {
            calculateDailyStreak(completions, habit, today, longest = true)
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

    private fun parseActiveDays(json: String?): Set<Int> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            json.trim('[', ']').split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
