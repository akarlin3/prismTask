package com.averycorp.prismtask.domain.usecase

import java.util.Calendar

/**
 * Pure date math used by the quick-reschedule popup. All functions are
 * deterministic given a "now" so they can be unit-tested without touching the
 * system clock.
 */
object DateShortcuts {
    /** Returns the epoch millis at midnight for the day [now] falls into. */
    fun startOfDay(now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Today (at midnight). */
    fun today(now: Long = System.currentTimeMillis()): Long = startOfDay(now)

    /** Tomorrow (at midnight). */
    fun tomorrow(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /**
     * Returns the next Monday strictly *after* today. If today is Monday the
     * result is 7 days from now — callers that want "this Monday or today"
     * should use a different helper. Independent of the user's locale.
     */
    fun nextMonday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        // Calendar.MONDAY == 2, Calendar.SUNDAY == 1
        val currentDow = cal.get(Calendar.DAY_OF_WEEK)
        val daysUntilMonday = when (currentDow) {
            Calendar.MONDAY -> 7
            Calendar.TUESDAY -> 6
            Calendar.WEDNESDAY -> 5
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 3
            Calendar.SATURDAY -> 2
            Calendar.SUNDAY -> 1
            else -> 7
        }
        cal.add(Calendar.DAY_OF_YEAR, daysUntilMonday)
        return cal.timeInMillis
    }

    /** Today + 7 days (at midnight). */
    fun nextWeek(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        cal.add(Calendar.DAY_OF_YEAR, 7)
        return cal.timeInMillis
    }
}
