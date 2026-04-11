package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.averycorp.prismtask.domain.model.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tests for v1.3.0 P13 advanced recurrence types: WEEKDAY, BIWEEKLY,
 * CUSTOM_DAYS, AFTER_COMPLETION.
 */
class AdvancedRecurrenceTest {

    private fun toMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun toDate(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    @Test
    fun `weekday from monday advances to tuesday`() {
        val mon = LocalDate.of(2026, 4, 6) // Monday
        val rule = RecurrenceRule(RecurrenceType.WEEKDAY)
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(mon), rule)
        assertEquals(LocalDate.of(2026, 4, 7), toDate(next!!))
    }

    @Test
    fun `weekday from friday skips to monday`() {
        val fri = LocalDate.of(2026, 4, 10)
        val rule = RecurrenceRule(RecurrenceType.WEEKDAY)
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(fri), rule)
        assertEquals(LocalDate.of(2026, 4, 13), toDate(next!!))
    }

    @Test
    fun `weekday from saturday lands on monday`() {
        val sat = LocalDate.of(2026, 4, 11)
        val rule = RecurrenceRule(RecurrenceType.WEEKDAY)
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(sat), rule)
        assertEquals(LocalDate.of(2026, 4, 13), toDate(next!!))
    }

    @Test
    fun `biweekly advances exactly 14 days`() {
        val d = LocalDate.of(2026, 4, 1)
        val rule = RecurrenceRule(RecurrenceType.BIWEEKLY)
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(d), rule)
        assertEquals(LocalDate.of(2026, 4, 15), toDate(next!!))
    }

    @Test
    fun `custom days finds next day in same month`() {
        val d = LocalDate.of(2026, 4, 5)
        val rule = RecurrenceRule(RecurrenceType.CUSTOM_DAYS, monthDays = listOf(1, 15))
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(d), rule)
        assertEquals(LocalDate.of(2026, 4, 15), toDate(next!!))
    }

    @Test
    fun `custom days wraps to next month when past last day in set`() {
        val d = LocalDate.of(2026, 4, 20)
        val rule = RecurrenceRule(RecurrenceType.CUSTOM_DAYS, monthDays = listOf(1, 15))
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(d), rule)
        assertEquals(LocalDate.of(2026, 5, 1), toDate(next!!))
    }

    @Test
    fun `custom days handles february with day 31`() {
        val d = LocalDate.of(2026, 1, 31)
        val rule = RecurrenceRule(RecurrenceType.CUSTOM_DAYS, monthDays = listOf(31))
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(d), rule)!!
        // February has no 31st; engine must skip to March 31.
        assertEquals(LocalDate.of(2026, 3, 31), toDate(next))
    }

    @Test
    fun `after completion with days unit adds days from completion time`() {
        val due = LocalDate.of(2026, 4, 1)
        val completed = LocalDate.of(2026, 4, 5) // late by 4 days
        val rule = RecurrenceRule(
            RecurrenceType.AFTER_COMPLETION,
            afterCompletionInterval = 3,
            afterCompletionUnit = "days"
        )
        val next = RecurrenceEngine.calculateNextDueDate(
            currentDueDate = toMillis(due),
            rule = rule,
            completedAt = toMillis(completed)
        )
        assertEquals(LocalDate.of(2026, 4, 8), toDate(next!!))
    }

    @Test
    fun `after completion with weeks unit adds weeks`() {
        val due = LocalDate.of(2026, 4, 1)
        val completed = LocalDate.of(2026, 4, 1)
        val rule = RecurrenceRule(
            RecurrenceType.AFTER_COMPLETION,
            afterCompletionInterval = 2,
            afterCompletionUnit = "weeks"
        )
        val next = RecurrenceEngine.calculateNextDueDate(
            toMillis(due), rule, completedAt = toMillis(completed)
        )
        assertEquals(LocalDate.of(2026, 4, 15), toDate(next!!))
    }

    @Test
    fun `after completion falls back to due date when completedAt is null`() {
        val due = LocalDate.of(2026, 4, 1)
        val rule = RecurrenceRule(
            RecurrenceType.AFTER_COMPLETION,
            afterCompletionInterval = 1,
            afterCompletionUnit = "days"
        )
        val next = RecurrenceEngine.calculateNextDueDate(toMillis(due), rule)!!
        assertEquals(LocalDate.of(2026, 4, 2), toDate(next))
    }

    @Test
    fun `json backward compatibility nullable fields default to null`() {
        // Build a minimal RecurrenceRule and verify the new fields default to null.
        val rule = RecurrenceRule(RecurrenceType.DAILY)
        assertEquals(null, rule.monthDays)
        assertEquals(null, rule.afterCompletionInterval)
        assertEquals(null, rule.afterCompletionUnit)
        val next = RecurrenceEngine.calculateNextDueDate(
            toMillis(LocalDate.of(2026, 4, 1)), rule
        )
        assertNotNull(next)
    }
}
