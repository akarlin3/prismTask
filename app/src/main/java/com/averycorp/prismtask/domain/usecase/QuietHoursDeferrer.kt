package com.averycorp.prismtask.domain.usecase

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Defers a scheduled reminder fire time if it falls inside the user's quiet
 * hours window. Supports overnight windows (e.g. 22:00 to 07:00).
 *
 * Added in v1.3.0 (P14).
 */
object QuietHoursDeferrer {

    /**
     * @param fireAtMillis when the alarm would originally fire
     * @param quietStart start of the quiet window (inclusive)
     * @param quietEnd end of the quiet window (exclusive)
     * @param zone the user's local time zone
     * @return the original fireAtMillis when outside the window, or a deferred
     *         fire time at [quietEnd] of the same (or next) day.
     */
    fun defer(
        fireAtMillis: Long,
        quietStart: LocalTime,
        quietEnd: LocalTime,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val firingZdt = Instant.ofEpochMilli(fireAtMillis).atZone(zone)
        val firingTime = firingZdt.toLocalTime()
        val firingDate = firingZdt.toLocalDate()

        val inQuiet = isInQuietWindow(firingTime, quietStart, quietEnd)
        if (!inQuiet) return fireAtMillis

        // Determine the effective end-of-quiet date:
        // - Normal window (e.g. 22:00 to 23:59 of same day): defer to same day end... but
        //   this shouldn't happen in practice. We handle it by picking the same day's end
        //   if end > start, otherwise next day's end.
        val endDate: LocalDate = if (!isOvernight(quietStart, quietEnd)) {
            // Same-day window: defer to end of that same day.
            firingDate
        } else {
            // Overnight window: if fire time is after start (late-evening),
            // the end is the next day. If fire time is before end
            // (early-morning), the end is the same day.
            if (firingTime >= quietStart) firingDate.plusDays(1) else firingDate
        }
        return endDate.atTime(quietEnd).atZone(zone).toInstant().toEpochMilli()
    }

    /** True iff [time] is within [start, end). Handles overnight windows. */
    fun isInQuietWindow(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        if (start == end) return false
        return if (isOvernight(start, end)) {
            time >= start || time < end
        } else {
            time >= start && time < end
        }
    }

    private fun isOvernight(start: LocalTime, end: LocalTime): Boolean = end <= start
}
