package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.data.local.entity.HabitEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Decides whether a habit should be hidden from the Today screen because the
 * user has configured a "skip window" that the habit currently falls inside.
 *
 * Two independent windows:
 *  - **After completion**: hide for N days after the last completion (e.g. a
 *    daily vitamin habit configured with N=2 would not show on Today the day
 *    after it was logged).
 *  - **Before next schedule**: hide if the habit's next scheduled occurrence
 *    is within N days. Applies to weekly habits via [HabitEntity.activeDays]
 *    (next weekday in the set) and to bookable habits via
 *    [HabitEntity.bookedDate]. Daily habits with no `activeDays` always have a
 *    "next occurrence == today" and are never affected by this window.
 *
 * Both windows resolve a per-habit override against a global default:
 * [HabitEntity.todaySkipAfterCompleteDays] / [HabitEntity.todaySkipBeforeScheduleDays]
 * are -1 to inherit, 0 to explicitly disable, and >=1 to use that value.
 */
class HabitTodayVisibilityResolver {
    fun resolveSkipAfterCompleteDays(habit: HabitEntity, globalDays: Int): Int =
        habit.todaySkipAfterCompleteDays.takeIf { it >= 0 } ?: globalDays

    fun resolveSkipBeforeScheduleDays(habit: HabitEntity, globalDays: Int): Int =
        habit.todaySkipBeforeScheduleDays.takeIf { it >= 0 } ?: globalDays

    /**
     * Returns true when [habit] should be hidden from the Today screen given
     * the user's most recent completion timestamp ([lastCompletionDate], or
     * null if the habit has never been completed) and the resolved windows.
     *
     * [now] is injected to keep the function deterministic in tests.
     */
    fun isHidden(
        habit: HabitEntity,
        lastCompletionDate: Long?,
        skipAfterCompleteDays: Int,
        skipBeforeScheduleDays: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (skipAfterCompleteDays > 0 && lastCompletionDate != null) {
            val days = TimeUnit.MILLISECONDS.toDays(now - lastCompletionDate)
            // Hide for the configured window starting the day of completion.
            // days < N means we're still inside the [completion, completion+N) range.
            if (days in 0 until skipAfterCompleteDays.toLong()) return true
        }
        if (skipBeforeScheduleDays > 0) {
            val nextScheduled = nextScheduledDate(habit, today) ?: return false
            // The habit's own scheduled day (delta == 0) shouldn't be hidden;
            // we only suppress when the habit appears today *and* the next
            // occurrence is in the [1, N] day range.
            val delta = today.until(nextScheduled, java.time.temporal.ChronoUnit.DAYS)
            if (delta in 1..skipBeforeScheduleDays.toLong()) return true
        }
        return false
    }

    /**
     * Computes the next scheduled date for [habit] from [today] (inclusive).
     * Returns null when the habit has no future-looking schedule we can use
     * (e.g. plain daily habits without `activeDays` and without a booked
     * date); those habits are never hidden by the schedule window.
     */
    fun nextScheduledDate(habit: HabitEntity, today: LocalDate): LocalDate? {
        // Bookable habit with an explicit booked date wins.
        habit.bookedDate?.let { booked ->
            val bookedLocal = java.time.Instant.ofEpochMilli(booked)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            if (!bookedLocal.isBefore(today)) return bookedLocal
        }
        val days = parseActiveDays(habit.activeDays)
        if (days.isEmpty()) return null
        // Walk forward from tomorrow until we find an active weekday.
        // The habit appears on Today separately; we want "next occurrence
        // after today" so the window 1..N applies cleanly.
        for (offset in 1..7) {
            val candidate = today.plusDays(offset.toLong())
            if (candidate.dayOfWeek in days) return candidate
        }
        return null
    }

    private fun parseActiveDays(json: String?): Set<DayOfWeek> {
        if (json.isNullOrBlank()) return emptySet()
        return try {
            json.trim('[', ']')
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .mapNotNull { v -> runCatching { DayOfWeek.of(v) }.getOrNull() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
