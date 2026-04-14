package com.averycorp.prismtask.domain

import com.averycorp.prismtask.data.repository.HabitRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class HabitRepositoryHelpersTest {
    @Test
    fun normalizeToMidnight_zeroesTimes() {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JUNE, 15, 14, 30, 45)
        cal.set(Calendar.MILLISECOND, 500)
        val normalized = HabitRepository.normalizeToMidnight(cal.timeInMillis)

        val result = Calendar.getInstance()
        result.timeInMillis = normalized
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
        assertEquals(0, result.get(Calendar.SECOND))
        assertEquals(0, result.get(Calendar.MILLISECOND))
    }

    @Test
    fun normalizeToMidnight_preservesDate() {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.MARCH, 20, 23, 59, 59)
        val normalized = HabitRepository.normalizeToMidnight(cal.timeInMillis)

        val result = Calendar.getInstance()
        result.timeInMillis = normalized
        assertEquals(2025, result.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, result.get(Calendar.MONTH))
        assertEquals(20, result.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun normalizeToMidnight_idempotent() {
        val first = HabitRepository.normalizeToMidnight(System.currentTimeMillis())
        val second = HabitRepository.normalizeToMidnight(first)
        assertEquals(first, second)
    }

    @Test
    fun getWeekStart_isMonday() {
        // 2025-06-15 is a Sunday
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JUNE, 15, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = HabitRepository.normalizeToMidnight(cal.timeInMillis)
        val weekStart = HabitRepository.getWeekStart(today)

        val result = Calendar.getInstance()
        result.timeInMillis = weekStart
        assertEquals(Calendar.MONDAY, result.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun getWeekEnd_isSunday() {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JUNE, 10, 0, 0, 0) // Tuesday
        cal.set(Calendar.MILLISECOND, 0)
        val today = HabitRepository.normalizeToMidnight(cal.timeInMillis)
        val weekEnd = HabitRepository.getWeekEnd(today)

        val result = Calendar.getInstance()
        result.timeInMillis = weekEnd
        assertEquals(Calendar.SUNDAY, result.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun getWeekEnd_isAfterWeekStart() {
        // Use a known Wednesday to avoid locale-dependent edge cases
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JUNE, 11, 0, 0, 0) // Wednesday
        cal.set(Calendar.MILLISECOND, 0)
        val today = HabitRepository.normalizeToMidnight(cal.timeInMillis)
        val weekStart = HabitRepository.getWeekStart(today)
        val weekEnd = HabitRepository.getWeekEnd(today)
        assertTrue("weekEnd ($weekEnd) should be > weekStart ($weekStart)", weekEnd > weekStart)
    }

    @Test
    fun weekBoundaries_spanSevenDays() {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JUNE, 12, 0, 0, 0) // Thursday
        cal.set(Calendar.MILLISECOND, 0)
        val today = HabitRepository.normalizeToMidnight(cal.timeInMillis)
        val weekStart = HabitRepository.getWeekStart(today)
        val weekEnd = HabitRepository.getWeekEnd(today)

        // Week end should be roughly 7 days after week start
        val daysBetween = (weekEnd - weekStart) / (24 * 60 * 60 * 1000)
        assertTrue(daysBetween in 6..7)
    }
}
