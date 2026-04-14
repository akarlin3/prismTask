package com.averycorp.prismtask.domain.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * What a boundary rule does when a task crosses its window.
 *
 * @property BLOCK_CATEGORY Show a warning dialog and refuse to create/edit
 *                          a task in the blocked category during the window.
 *                          User can override with "Create Anyway".
 * @property SUGGEST_CATEGORY Pre-select the suggested category in the
 *                            editor when creating during the window.
 * @property REMIND Fire a notification at the start time as a soft nudge.
 */
enum class BoundaryRuleType {
    BLOCK_CATEGORY,
    SUGGEST_CATEGORY,
    REMIND
}

/**
 * A single boundary rule (v1.4.0 V3).
 *
 * Runtime model — the Room entity is [com.averycorp.prismtask.data.local.entity.BoundaryRuleEntity]
 * which serializes `activeDays` as a CSV and `startTime` / `endTime` as
 * HH:mm strings. This class uses proper Java-time types so the enforcer
 * can reason about days and times directly.
 */
data class BoundaryRule(
    val id: Long = 0,
    val name: String,
    val ruleType: BoundaryRuleType,
    val category: LifeCategory,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val activeDays: Set<DayOfWeek>,
    val isEnabled: Boolean = true
) {
    /** True if this rule's window currently contains [time] on [day]. */
    fun containsNow(time: LocalTime, day: DayOfWeek): Boolean {
        if (!isEnabled) return false
        if (day !in activeDays) return false
        // Windows may straddle midnight (e.g. 20:00 – 07:00).
        return if (endTime >= startTime) {
            !time.isBefore(startTime) && time.isBefore(endTime)
        } else {
            !time.isBefore(startTime) || time.isBefore(endTime)
        }
    }

    companion object {
        fun parseTime(value: String): LocalTime =
            try {
                LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: DateTimeParseException) {
                LocalTime.MIDNIGHT
            }

        fun formatTime(time: LocalTime): String =
            time.format(DateTimeFormatter.ofPattern("HH:mm"))

        fun parseDays(csv: String): Set<DayOfWeek> {
            if (csv.isBlank()) return emptySet()
            return csv
                .split(",")
                .mapNotNull { entry ->
                    entry.trim().toIntOrNull()?.let { num ->
                        runCatching { DayOfWeek.of(num) }.getOrNull()
                    }
                }.toSet()
        }

        fun formatDays(days: Set<DayOfWeek>): String =
            days.map { it.value }.sorted().joinToString(",")

        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.values().toSet()
    }
}
