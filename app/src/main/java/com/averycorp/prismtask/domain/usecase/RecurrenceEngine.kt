package com.averycorp.prismtask.domain.usecase

import com.averycorp.prismtask.domain.model.RecurrenceRule
import com.averycorp.prismtask.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object RecurrenceEngine {

    fun shouldRecur(rule: RecurrenceRule): Boolean {
        if (rule.maxOccurrences != null && rule.occurrenceCount >= rule.maxOccurrences) {
            return false
        }
        return true
    }

    fun calculateNextDueDate(currentDueDate: Long, rule: RecurrenceRule): Long? {
        if (!shouldRecur(rule)) return null

        val current = Instant.ofEpochMilli(currentDueDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val next = when (rule.type) {
            RecurrenceType.DAILY, RecurrenceType.CUSTOM -> calculateDaily(current, rule)
            RecurrenceType.WEEKLY -> calculateWeekly(current, rule)
            RecurrenceType.MONTHLY -> calculateMonthly(current, rule)
            RecurrenceType.YEARLY -> calculateYearly(current, rule)
        }

        val nextMillis = next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (rule.endDate != null && nextMillis > rule.endDate) return null

        return nextMillis
    }

    private fun calculateDaily(current: LocalDate, rule: RecurrenceRule): LocalDate {
        var next = current.plusDays(rule.interval.toLong())
        if (rule.skipWeekends) {
            while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
                next = next.plusDays(1)
            }
        }
        return next
    }

    private fun calculateWeekly(current: LocalDate, rule: RecurrenceRule): LocalDate {
        val days = rule.daysOfWeek
        if (days.isNullOrEmpty()) {
            return current.plusWeeks(rule.interval.toLong())
        }

        // daysOfWeek uses 1=Mon..7=Sun; DayOfWeek.getValue() also uses 1=Mon..7=Sun
        val sorted = days.sorted()
        val currentDow = current.dayOfWeek.value

        // Find the next day in the current week after today
        val nextInWeek = sorted.firstOrNull { it > currentDow }
        if (nextInWeek != null) {
            return current.plusDays((nextInWeek - currentDow).toLong())
        }

        // Wrap to the first day in the list, advancing by interval weeks
        val daysUntilEndOfWeek = 7 - currentDow
        val daysIntoNextCycle = sorted.first()
        return current.plusDays((daysUntilEndOfWeek + ((rule.interval - 1) * 7) + daysIntoNextCycle).toLong())
    }

    private fun calculateMonthly(current: LocalDate, rule: RecurrenceRule): LocalDate {
        val targetDay = rule.dayOfMonth ?: current.dayOfMonth
        val nextMonth = current.plusMonths(rule.interval.toLong())
        val clampedDay = targetDay.coerceAtMost(nextMonth.lengthOfMonth())
        return nextMonth.withDayOfMonth(clampedDay)
    }

    private fun calculateYearly(current: LocalDate, rule: RecurrenceRule): LocalDate {
        val nextYear = current.plusYears(rule.interval.toLong())
        // Handle leap day: Feb 29 in a non-leap year becomes Feb 28
        return if (current.monthValue == 2 && current.dayOfMonth == 29 && !nextYear.isLeapYear) {
            nextYear.withDayOfMonth(28)
        } else {
            nextYear
        }
    }
}
