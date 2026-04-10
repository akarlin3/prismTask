package com.averycorp.prismtask.domain

import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.averycorp.prismtask.domain.model.RecurrenceType
import com.averycorp.prismtask.domain.usecase.RecurrenceEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurrenceEngineTest {

    private fun LocalDate.toMillis(): Long =
        atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun Long.toLocalDate(): LocalDate =
        java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

    // --- Daily ---

    @Test
    fun daily_basic() {
        val date = LocalDate.of(2025, 1, 6) // Monday
        val rule = RecurrenceRule(type = RecurrenceType.DAILY)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 7), next)
    }

    @Test
    fun daily_interval3() {
        val date = LocalDate.of(2025, 1, 6)
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, interval = 3)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 9), next)
    }

    @Test
    fun daily_skipWeekends_fromFriday() {
        val date = LocalDate.of(2025, 1, 3) // Friday
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, skipWeekends = true)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 6), next) // Monday
    }

    @Test
    fun daily_skipWeekends_fromSaturday() {
        // interval=1 from Saturday: +1=Sunday (weekend), +1=Monday
        val date = LocalDate.of(2025, 1, 4) // Saturday
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, skipWeekends = true)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 6), next) // Monday
    }

    // --- Weekly ---

    @Test
    fun weekly_sameDay() {
        val date = LocalDate.of(2025, 1, 6) // Monday
        val rule = RecurrenceRule(type = RecurrenceType.WEEKLY)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 13), next)
    }

    @Test
    fun weekly_multipleDays_monWedFri() {
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            daysOfWeek = listOf(1, 3, 5) // Mon, Wed, Fri
        )

        // From Monday -> next is Wednesday
        val fromMon = LocalDate.of(2025, 1, 6)
        val next1 = RecurrenceEngine.calculateNextDueDate(fromMon.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 8), next1) // Wednesday

        // From Wednesday -> next is Friday
        val fromWed = LocalDate.of(2025, 1, 8)
        val next2 = RecurrenceEngine.calculateNextDueDate(fromWed.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 10), next2) // Friday

        // From Friday -> next is Monday (next week)
        val fromFri = LocalDate.of(2025, 1, 10)
        val next3 = RecurrenceEngine.calculateNextDueDate(fromFri.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 13), next3) // Monday
    }

    @Test
    fun weekly_interval2() {
        val date = LocalDate.of(2025, 1, 6) // Monday
        val rule = RecurrenceRule(type = RecurrenceType.WEEKLY, interval = 2)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 20), next) // two weeks later
    }

    @Test
    fun weekly_noDaysSpecified() {
        val date = LocalDate.of(2025, 1, 8) // Wednesday
        val rule = RecurrenceRule(type = RecurrenceType.WEEKLY)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 15), next)
    }

    // --- Monthly ---

    @Test
    fun monthly_basic() {
        val date = LocalDate.of(2025, 1, 15)
        val rule = RecurrenceRule(type = RecurrenceType.MONTHLY)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 2, 15), next)
    }

    @Test
    fun monthly_day31_inFeb() {
        val date = LocalDate.of(2025, 1, 31)
        val rule = RecurrenceRule(type = RecurrenceType.MONTHLY, dayOfMonth = 31)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 2, 28), next) // clamped
    }

    @Test
    fun monthly_day31_inApr() {
        val date = LocalDate.of(2025, 3, 31)
        val rule = RecurrenceRule(type = RecurrenceType.MONTHLY, dayOfMonth = 31)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 4, 30), next) // clamped
    }

    @Test
    fun monthly_interval3() {
        val date = LocalDate.of(2025, 1, 10)
        val rule = RecurrenceRule(type = RecurrenceType.MONTHLY, interval = 3)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 4, 10), next)
    }

    // --- Yearly ---

    @Test
    fun yearly_basic() {
        val date = LocalDate.of(2025, 3, 15)
        val rule = RecurrenceRule(type = RecurrenceType.YEARLY)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2026, 3, 15), next)
    }

    @Test
    fun yearly_leapDay() {
        val date = LocalDate.of(2024, 2, 29) // leap year
        val rule = RecurrenceRule(type = RecurrenceType.YEARLY)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)!!.toLocalDate()
        assertEquals(LocalDate.of(2025, 2, 28), next) // non-leap year
    }

    // --- End conditions ---

    @Test
    fun endDate_notReached() {
        val date = LocalDate.of(2025, 1, 6)
        val endDate = LocalDate.of(2025, 12, 31).toMillis()
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, endDate = endDate)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)
        assertNotNull(next)
    }

    @Test
    fun endDate_passed() {
        val date = LocalDate.of(2025, 12, 31)
        val endDate = LocalDate.of(2025, 12, 31).toMillis()
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, endDate = endDate)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)
        assertNull(next) // next would be Jan 1 2026, past end date
    }

    @Test
    fun maxOccurrences_notReached() {
        val date = LocalDate.of(2025, 1, 6)
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, maxOccurrences = 5, occurrenceCount = 3)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)
        assertNotNull(next)
    }

    @Test
    fun maxOccurrences_reached() {
        val date = LocalDate.of(2025, 1, 6)
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, maxOccurrences = 5, occurrenceCount = 5)
        val next = RecurrenceEngine.calculateNextDueDate(date.toMillis(), rule)
        assertNull(next)
    }
}
