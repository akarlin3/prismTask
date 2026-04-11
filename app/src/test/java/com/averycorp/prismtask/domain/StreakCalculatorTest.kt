package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.domain.usecase.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dailyHabit(target: Int = 1) = HabitEntity(
        id = 1,
        name = "Test",
        targetFrequency = target,
        frequencyPeriod = "daily"
    )

    private fun weeklyHabit(target: Int = 3, activeDays: String? = null) = HabitEntity(
        id = 1,
        name = "Test",
        targetFrequency = target,
        frequencyPeriod = "weekly",
        activeDays = activeDays
    )

    private fun completion(habitId: Long = 1, date: LocalDate) = HabitCompletionEntity(
        habitId = habitId,
        completedDate = date.toMillis(),
        completedAt = date.toMillis()
    )

    // --- Current Streak ---

    @Test
    fun test_currentStreak_3days() {
        val today = LocalDate.of(2025, 6, 10) // Tuesday
        val completions = listOf(
            completion(date = today),
            completion(date = today.minusDays(1)),
            completion(date = today.minusDays(2))
        )
        val streak = StreakCalculator.calculateCurrentStreak(completions, dailyHabit(), today)
        assertEquals(3, streak)
    }

    @Test
    fun test_currentStreak_brokenByGap() {
        val today = LocalDate.of(2025, 6, 10)
        val completions = listOf(
            completion(date = today),
            completion(date = today.minusDays(2)) // missing yesterday
        )
        val streak = StreakCalculator.calculateCurrentStreak(completions, dailyHabit(), today)
        assertEquals(1, streak)
    }

    @Test
    fun test_currentStreak_todayNotDoneYet() {
        val today = LocalDate.of(2025, 6, 10)
        val completions = listOf(
            completion(date = today.minusDays(1)),
            completion(date = today.minusDays(2))
        )
        val streak = StreakCalculator.calculateCurrentStreak(completions, dailyHabit(), today)
        assertEquals(2, streak)
    }

    @Test
    fun test_currentStreak_empty() {
        val streak = StreakCalculator.calculateCurrentStreak(emptyList(), dailyHabit())
        assertEquals(0, streak)
    }

    @Test
    fun test_currentStreak_noRecentCompletions() {
        val today = LocalDate.of(2025, 6, 10)
        val completions = listOf(
            completion(date = today.minusDays(5)),
            completion(date = today.minusDays(6))
        )
        val streak = StreakCalculator.calculateCurrentStreak(completions, dailyHabit(), today)
        assertEquals(0, streak)
    }

    // --- Longest Streak ---

    @Test
    fun test_longestStreak() {
        val today = LocalDate.of(2025, 6, 10)
        // Past streak of 5 days
        val pastStreak = (0L until 5).map { completion(date = today.minusDays(20 - it)) }
        // Current streak of 3 days
        val currentStreak = (0L until 3).map { completion(date = today.minusDays(it)) }
        val completions = pastStreak + currentStreak

        val longest = StreakCalculator.calculateLongestStreak(completions, dailyHabit(), today)
        assertEquals(5, longest)
    }

    @Test
    fun test_longestStreak_currentIsLongest() {
        val today = LocalDate.of(2025, 6, 10)
        val completions = (0L until 10).map { completion(date = today.minusDays(it)) }
        val longest = StreakCalculator.calculateLongestStreak(completions, dailyHabit(), today)
        assertEquals(10, longest)
    }

    // --- Completion Rate ---

    @Test
    fun test_completionRate() {
        val today = LocalDate.of(2025, 6, 10)
        val completions = (0L until 5).map { completion(date = today.minusDays(it)) }
        val rate = StreakCalculator.calculateCompletionRate(completions, dailyHabit(), 7, today)
        assertEquals(5f / 7f, rate, 0.01f)
    }

    @Test
    fun test_completionRate_perfect() {
        val today = LocalDate.of(2025, 6, 10)
        val completions = (0L until 7).map { completion(date = today.minusDays(it)) }
        val rate = StreakCalculator.calculateCompletionRate(completions, dailyHabit(), 7, today)
        assertEquals(1.0f, rate, 0.01f)
    }

    // --- Weekly Streak ---

    @Test
    fun test_weeklyStreak() {
        // 4 consecutive weeks with 3+ completions each
        val today = LocalDate.of(2025, 6, 9) // Monday
        val completions = mutableListOf<HabitCompletionEntity>()
        for (week in 0 until 4) {
            val weekStart = today.minusWeeks(week.toLong())
            completions.add(completion(date = weekStart))
            completions.add(completion(date = weekStart.plusDays(1)))
            completions.add(completion(date = weekStart.plusDays(2)))
        }
        val streak = StreakCalculator.calculateCurrentStreak(completions, weeklyHabit(target = 3), today)
        assertEquals(4, streak)
    }

    // --- Active Days ---

    @Test
    fun test_activeDays_completionRate() {
        // Mon/Wed/Fri habit (values 1,3,5)
        val today = LocalDate.of(2025, 6, 13) // Friday
        val habit = weeklyHabit(target = 3, activeDays = "[1,3,5]")
        // Complete Mon, Wed, Fri of this week
        val monday = LocalDate.of(2025, 6, 9)
        val completions = listOf(
            completion(date = monday),
            completion(date = monday.plusDays(2)), // Wed
            completion(date = monday.plusDays(4))  // Fri
        )
        val rate = StreakCalculator.calculateCompletionRate(completions, habit, 5, today)
        assertEquals(1.0f, rate, 0.01f)
    }

    // --- Best/Worst Day ---

    @Test
    fun test_bestDay() {
        val completions = listOf(
            completion(date = LocalDate.of(2025, 6, 9)),  // Monday
            completion(date = LocalDate.of(2025, 6, 9)),  // Monday (2nd)
            completion(date = LocalDate.of(2025, 6, 10)), // Tuesday
        )
        val best = StreakCalculator.getBestDay(completions)
        assertEquals(DayOfWeek.MONDAY, best)
    }

    @Test
    fun test_worstDay() {
        val completions = listOf(
            completion(date = LocalDate.of(2025, 6, 9)),  // Monday
            completion(date = LocalDate.of(2025, 6, 10)), // Tuesday
        )
        val worst = StreakCalculator.getWorstDay(completions)
        assertNotNull(worst)
        // Worst should be one of the days with 0 completions (Wed-Sun)
        assertEquals(0, completions.count { it.completedDate.let { ts ->
            java.time.Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().dayOfWeek
        } == worst })
    }

    // --- Additional Edge Cases ---

    @Test
    fun test_getCompletionsByDay_returnsAllDaysInRange() {
        val start = LocalDate.of(2025, 6, 9) // Monday
        val end = LocalDate.of(2025, 6, 15) // Sunday
        val completions = listOf(
            completion(date = LocalDate.of(2025, 6, 10)),
            completion(date = LocalDate.of(2025, 6, 10)), // double completion
            completion(date = LocalDate.of(2025, 6, 13))
        )
        val byDay = StreakCalculator.getCompletionsByDay(completions, start, end)
        assertEquals(7, byDay.size)
        assertEquals(0, byDay[LocalDate.of(2025, 6, 9)])
        assertEquals(2, byDay[LocalDate.of(2025, 6, 10)])
        assertEquals(1, byDay[LocalDate.of(2025, 6, 13)])
        assertEquals(0, byDay[LocalDate.of(2025, 6, 15)])
    }

    @Test
    fun test_getBestDay_empty_returnsNull() {
        val result = StreakCalculator.getBestDay(emptyList())
        assertEquals(null, result)
    }

    @Test
    fun test_getWorstDay_empty_returnsNull() {
        val result = StreakCalculator.getWorstDay(emptyList())
        assertEquals(null, result)
    }

    @Test
    fun test_completionRate_zeroDays_returnsZero() {
        val rate = StreakCalculator.calculateCompletionRate(emptyList(), dailyHabit(), 0)
        assertEquals(0f, rate, 0.001f)
    }

    @Test
    fun test_longestStreak_empty_returnsZero() {
        val longest = StreakCalculator.calculateLongestStreak(emptyList(), dailyHabit())
        assertEquals(0, longest)
    }

    @Test
    fun test_currentStreak_onlyToday() {
        val today = LocalDate.of(2025, 6, 10)
        val completions = listOf(completion(date = today))
        val streak = StreakCalculator.calculateCurrentStreak(completions, dailyHabit(), today)
        assertEquals(1, streak)
    }

    @Test
    fun test_currentStreak_multiTarget_perDay() {
        val today = LocalDate.of(2025, 6, 10)
        // Habit requires 2 per day, but only 1 completion per day
        val completions = listOf(
            completion(date = today),
            completion(date = today.minusDays(1))
        )
        val streak = StreakCalculator.calculateCurrentStreak(completions, dailyHabit(target = 2), today)
        assertEquals(0, streak) // not meeting target
    }

    @Test
    fun test_currentStreak_multiTarget_met() {
        val today = LocalDate.of(2025, 6, 10)
        // Habit requires 2 per day, and we have 2 completions per day
        val completions = listOf(
            completion(date = today), completion(date = today),
            completion(date = today.minusDays(1)), completion(date = today.minusDays(1))
        )
        val streak = StreakCalculator.calculateCurrentStreak(completions, dailyHabit(target = 2), today)
        assertEquals(2, streak)
    }

    // --- Streak Grace Period (maxMissedDays) ---

    @Test
    fun test_currentStreak_graceOfTwo_survivesSingleMissedDay() {
        val today = LocalDate.of(2025, 6, 10)
        // Missed yesterday, but done today, day-before-yesterday, and back 3 more days.
        val completions = listOf(
            completion(date = today),
            completion(date = today.minusDays(2)),
            completion(date = today.minusDays(3)),
            completion(date = today.minusDays(4))
        )
        // Grace of 2 means a single missed day is forgiven.
        val streak = StreakCalculator.calculateCurrentStreak(
            completions, dailyHabit(), today, maxMissedDays = 2
        )
        assertEquals(4, streak)
    }

    @Test
    fun test_currentStreak_graceOfTwo_brokenByTwoConsecutiveMisses() {
        val today = LocalDate.of(2025, 6, 10)
        // Missed both yesterday and the day before — streak should end at today.
        val completions = listOf(
            completion(date = today),
            completion(date = today.minusDays(3)),
            completion(date = today.minusDays(4))
        )
        val streak = StreakCalculator.calculateCurrentStreak(
            completions, dailyHabit(), today, maxMissedDays = 2
        )
        assertEquals(1, streak)
    }

    @Test
    fun test_currentStreak_graceOfOne_matchesOriginalBehavior() {
        // Grace of 1 is the original semantics: any miss breaks the streak.
        val today = LocalDate.of(2025, 6, 10)
        val completions = listOf(
            completion(date = today),
            completion(date = today.minusDays(2))
        )
        val streak = StreakCalculator.calculateCurrentStreak(
            completions, dailyHabit(), today, maxMissedDays = 1
        )
        assertEquals(1, streak)
    }

    @Test
    fun test_longestStreak_graceOfTwo_spansSingleGap() {
        val today = LocalDate.of(2025, 6, 10)
        // 3 completions, a single miss, then 4 more completions.
        val completions = listOf(
            completion(date = today.minusDays(8)),
            completion(date = today.minusDays(7)),
            completion(date = today.minusDays(6)),
            // missed day 5
            completion(date = today.minusDays(4)),
            completion(date = today.minusDays(3)),
            completion(date = today.minusDays(2)),
            completion(date = today.minusDays(1))
        )
        val longest = StreakCalculator.calculateLongestStreak(
            completions, dailyHabit(), today, maxMissedDays = 2
        )
        // With one forgiven gap, the run should stitch together into 7 completions.
        assertEquals(7, longest)
    }
}
