package com.averycorp.prismtask.notifications

import com.averycorp.prismtask.domain.model.notifications.QuietHoursWindow
import com.averycorp.prismtask.domain.model.notifications.UrgencyTier
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Exercises the pure [ReminderScheduler.applyQuietHours] helper so we
 * don't need an alarm manager or Android runtime to verify the
 * integration between the scheduler and [QuietHoursDeferrer].
 */
class ReminderSchedulerQuietHoursTest {
    private val zone: ZoneId = ZoneId.of("UTC")

    private fun zdt(hour: Int, minute: Int = 0, day: DayOfWeek = DayOfWeek.MONDAY): Long {
        // Monday 2024-01-01 UTC base; pick the first week containing that weekday
        var base = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, zone)
        while (base.dayOfWeek != day) {
            base = base.plusDays(1)
        }
        return base.plusHours(hour.toLong()).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
    }

    @Test
    fun `disabled window returns the original trigger`() {
        val trigger = zdt(hour = 23)
        val result = ReminderScheduler.applyQuietHours(
            trigger = trigger,
            window = QuietHoursWindow.DISABLED,
            urgencyTier = UrgencyTier.MEDIUM,
            zone = zone
        )
        assertEquals(trigger, result)
    }

    @Test
    fun `trigger outside quiet window is unchanged`() {
        val window = QuietHoursWindow(
            enabled = true,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
            priorityOverrideTiers = emptySet()
        )
        val trigger = zdt(hour = 12) // noon — outside window
        val result = ReminderScheduler.applyQuietHours(
            trigger = trigger,
            window = window,
            urgencyTier = UrgencyTier.MEDIUM,
            zone = zone
        )
        assertEquals(trigger, result)
    }

    @Test
    fun `trigger inside overnight window defers to end time`() {
        val window = QuietHoursWindow(
            enabled = true,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY),
            priorityOverrideTiers = emptySet()
        )
        val trigger = zdt(hour = 23) // Monday 11pm — in window
        val result = ReminderScheduler.applyQuietHours(
            trigger = trigger,
            window = window,
            urgencyTier = UrgencyTier.MEDIUM,
            zone = zone
        )
        // Should be Tuesday 7:00
        assertEquals(zdt(hour = 7, day = DayOfWeek.TUESDAY), result)
    }

    @Test
    fun `critical tier breaks through the allowlist unchanged`() {
        val window = QuietHoursWindow(
            enabled = true,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.MONDAY),
            priorityOverrideTiers = setOf(UrgencyTier.CRITICAL)
        )
        val trigger = zdt(hour = 23) // Monday 11pm
        val result = ReminderScheduler.applyQuietHours(
            trigger = trigger,
            window = window,
            urgencyTier = UrgencyTier.CRITICAL,
            zone = zone
        )
        assertEquals(trigger, result)
    }

    @Test
    fun `window on non-matching day does not apply`() {
        val window = QuietHoursWindow(
            enabled = true,
            start = LocalTime.of(22, 0),
            end = LocalTime.of(7, 0),
            days = setOf(DayOfWeek.SATURDAY), // only Saturdays
            priorityOverrideTiers = emptySet()
        )
        val trigger = zdt(hour = 23, day = DayOfWeek.MONDAY)
        val result = ReminderScheduler.applyQuietHours(
            trigger = trigger,
            window = window,
            urgencyTier = UrgencyTier.MEDIUM,
            zone = zone
        )
        assertEquals(trigger, result)
    }
}
