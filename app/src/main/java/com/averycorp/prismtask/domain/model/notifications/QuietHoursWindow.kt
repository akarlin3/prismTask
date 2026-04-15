package com.averycorp.prismtask.domain.model.notifications

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * User-configured quiet-hours window.
 *
 * [start] and [end] are stored as local wall-clock times; [days] is the
 * set of weekdays the window applies to. Overnight windows (end <= start)
 * are supported and handled by
 * [com.averycorp.prismtask.domain.usecase.QuietHoursDeferrer].
 *
 * [priorityOverrideTiers] is the set of urgency tiers that are *allowed
 * through* the quiet window without being deferred — e.g. CRITICAL tasks
 * still fire at 2am even if quiet hours are active.
 */
data class QuietHoursWindow(
    val enabled: Boolean = false,
    val start: LocalTime = LocalTime.of(22, 0),
    val end: LocalTime = LocalTime.of(7, 0),
    val days: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val priorityOverrideTiers: Set<UrgencyTier> = setOf(UrgencyTier.CRITICAL)
) {
    fun appliesOn(day: DayOfWeek): Boolean = enabled && day in days

    fun canBreakThrough(tier: UrgencyTier): Boolean = tier in priorityOverrideTiers

    companion object {
        val DISABLED = QuietHoursWindow(enabled = false)

        /** Sunday 22:00 → Monday 07:00 applied Mon-Fri. Nothing on weekends. */
        val WEEKNIGHTS = QuietHoursWindow(
            enabled = true,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            ),
            priorityOverrideTiers = setOf(UrgencyTier.CRITICAL)
        )
    }
}
