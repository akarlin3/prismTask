package com.averycorp.prismtask.util

import java.util.Calendar

/**
 * Utility for computing day boundaries based on a configurable "day start hour".
 *
 * The user can choose what time of day a new day begins (e.g. 4 AM). All "today"
 * windows for tasks, habits, and widgets should flow through here so that the
 * day rolls over at the configured time instead of midnight.
 */
object DayBoundary {
    /**
     * Returns the timestamp of the start of the current day, taking [dayStartHour]
     * into account. If the current wall-clock hour is before [dayStartHour], the
     * "current day" is considered to have started yesterday.
     */
    fun startOfCurrentDay(dayStartHour: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        cal.set(Calendar.HOUR_OF_DAY, dayStartHour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (hour < dayStartHour) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return cal.timeInMillis
    }

    /** End of the current day (exclusive upper bound). */
    fun endOfCurrentDay(dayStartHour: Int, now: Long = System.currentTimeMillis()): Long =
        startOfCurrentDay(dayStartHour, now) + DAY_MILLIS

    /**
     * Calendar midnight of the "effective current day" — i.e. the same calendar
     * date that [startOfCurrentDay] resolves to, but snapped back to 00:00 local
     * instead of [dayStartHour].
     *
     * Intended for UI filters where timeless events (tasks/habits whose dueDate,
     * plannedDate, or bookedDate is stored at local midnight because the user
     * tapped "Today"/"Tomorrow") should be assumed to fall after the day-start
     * hour for their own calendar date. Pair with [calendarMidnightOfNextDay]
     * to get a `[start, end)` window that contains every timeless event dated
     * to the current day, and only those events.
     */
    fun calendarMidnightOfCurrentDay(dayStartHour: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfCurrentDay(dayStartHour, now) }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Calendar midnight of the calendar day *after* [calendarMidnightOfCurrentDay]. */
    fun calendarMidnightOfNextDay(dayStartHour: Int, now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = calendarMidnightOfCurrentDay(dayStartHour, now)
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /**
     * Normalizes any timestamp to the day-start of the day it falls into,
     * given the configured [dayStartHour]. Used by habit completions so that a
     * completion logged at 2 AM (with a 4 AM day start) is recorded against the
     * previous calendar day.
     */
    fun normalizeToDayStart(timestamp: Long, dayStartHour: Int): Long =
        startOfCurrentDay(dayStartHour, timestamp)

    /**
     * Returns the next time the configured day boundary will be crossed.
     * Used by [com.averycorp.prismtask.workers.DailyResetWorker] to schedule
     * itself.
     */
    fun nextBoundary(dayStartHour: Int, now: Long = System.currentTimeMillis()): Long {
        val start = startOfCurrentDay(dayStartHour, now)
        return start + DAY_MILLIS
    }

    const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
}
