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

    fun calculateNextDueDate(currentDueDate: Long, rule: RecurrenceRule): Long? =
        calculateNextDueDate(currentDueDate, rule, completedAt = null)

    /**
     * @param completedAt milliseconds at which the task was marked complete.
     *        Required for [RecurrenceType.AFTER_COMPLETION]; ignored otherwise.
     */
    fun calculateNextDueDate(currentDueDate: Long, rule: RecurrenceRule, completedAt: Long?): Long? {
        if (!shouldRecur(rule)) return null

        val current = Instant.ofEpochMilli(currentDueDate)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val next = when (rule.type) {
            RecurrenceType.DAILY, RecurrenceType.CUSTOM -> calculateDaily(current, rule)
            RecurrenceType.WEEKLY -> calculateWeekly(current, rule)
            RecurrenceType.MONTHLY -> calculateMonthly(current, rule)
            RecurrenceType.YEARLY -> calculateYearly(current, rule)
            RecurrenceType.WEEKDAY -> calculateWeekday(current)
            RecurrenceType.BIWEEKLY -> current.plusWeeks(2)
            RecurrenceType.CUSTOM_DAYS -> calculateCustomDays(current, rule)
            RecurrenceType.AFTER_COMPLETION -> {
                val basis = completedAt ?: currentDueDate
                val base = Instant.ofEpochMilli(basis).atZone(ZoneId.systemDefault()).toLocalDate()
                val interval = rule.afterCompletionInterval ?: 1
                when (rule.afterCompletionUnit?.lowercase()) {
                    "weeks" -> base.plusWeeks(interval.toLong())
                    else -> base.plusDays(interval.toLong())
                }
            }
        }

        val nextMillis = next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        if (rule.endDate != null && nextMillis > rule.endDate) return null

        return nextMillis
    }

    private fun calculateWeekday(current: LocalDate): LocalDate {
        var next = current.plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
        }
        return next
    }

    private fun calculateCustomDays(current: LocalDate, rule: RecurrenceRule): LocalDate {
        val days = rule.monthDays?.filter { it in 1..31 }?.sorted().orEmpty()
        if (days.isEmpty()) return current.plusMonths(1)

        // Find the next valid day in the current month after today, or advance to the
        // first valid day in the next month (and keep advancing if that month doesn't
        // have the requested day, e.g. day 31 in February).
        val currentDay = current.dayOfMonth
        val nextInThisMonth = days.firstOrNull { it > currentDay && it <= current.lengthOfMonth() }
        if (nextInThisMonth != null) {
            return current.withDayOfMonth(nextInThisMonth)
        }
        var candidate = current.plusMonths(1).withDayOfMonth(1)
        var safety = 0
        while (safety < 12) {
            val firstValid = days.firstOrNull { it <= candidate.lengthOfMonth() }
            if (firstValid != null) return candidate.withDayOfMonth(firstValid)
            candidate = candidate.plusMonths(1)
            safety++
        }
        // Should never happen, but fall back gracefully.
        return current.plusMonths(1)
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
