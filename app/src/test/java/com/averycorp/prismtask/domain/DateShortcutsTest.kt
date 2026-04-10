package com.averycorp.prismtask.domain

import com.averycorp.prismtask.domain.usecase.DateShortcuts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for the pure date-math helpers used by the quick-reschedule
 * popup. All inputs are pinned via [buildDate] so results are deterministic
 * across time zones.
 */
class DateShortcutsTest {

    /**
     * Builds a "now" anchored to a specific [dayOfWeek] (e.g. [Calendar.MONDAY])
     * so assertions don't depend on hard-coded calendar dates that might not
     * match the intended day.
     */
    private fun noonOnDayOfWeek(dayOfWeek: Int): Long {
        // Start from a fixed reference date then advance until we hit the
        // requested day of week. Using 2026-01-01 (a Thursday) as the anchor.
        val cal = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.JANUARY, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun dayOfWeekOf(millis: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    private fun daysBetween(a: Long, b: Long): Int {
        val diff = b - a
        return Math.round(diff.toDouble() / (24L * 60L * 60L * 1000L)).toInt()
    }

    @Test
    fun nextMonday_fromMonday_skipsAFullWeek() {
        val monday = noonOnDayOfWeek(Calendar.MONDAY)
        val result = DateShortcuts.nextMonday(monday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        // Should jump a full 7 days instead of returning "today"
        assertEquals(7, daysBetween(DateShortcuts.today(monday), result))
    }

    @Test
    fun nextMonday_fromWednesday_landsOnNextMonday() {
        val wednesday = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val result = DateShortcuts.nextMonday(wednesday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        assertEquals(5, daysBetween(DateShortcuts.today(wednesday), result))
    }

    @Test
    fun nextMonday_fromSunday_returnsTomorrow() {
        val sunday = noonOnDayOfWeek(Calendar.SUNDAY)
        val result = DateShortcuts.nextMonday(sunday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        assertEquals(1, daysBetween(DateShortcuts.today(sunday), result))
    }

    @Test
    fun nextMonday_fromSaturday_landsTwoDaysLater() {
        val saturday = noonOnDayOfWeek(Calendar.SATURDAY)
        val result = DateShortcuts.nextMonday(saturday)
        assertEquals(Calendar.MONDAY, dayOfWeekOf(result))
        assertEquals(2, daysBetween(DateShortcuts.today(saturday), result))
    }

    @Test
    fun today_andTomorrow_differByExactlyOneDay() {
        val now = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val today = DateShortcuts.today(now)
        val tomorrow = DateShortcuts.tomorrow(now)
        assertEquals(1, daysBetween(today, tomorrow))
    }

    @Test
    fun nextWeek_isSevenDaysFromToday() {
        val now = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val today = DateShortcuts.today(now)
        val plus7 = DateShortcuts.nextWeek(now)
        assertEquals(7, daysBetween(today, plus7))
    }

    @Test
    fun startOfDay_zerosTimeComponents() {
        val noon = noonOnDayOfWeek(Calendar.WEDNESDAY)
        val start = DateShortcuts.startOfDay(noon)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }
}
