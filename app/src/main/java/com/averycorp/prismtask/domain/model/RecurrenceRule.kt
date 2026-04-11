package com.averycorp.prismtask.domain.model

data class RecurrenceRule(
    val type: RecurrenceType,
    val interval: Int = 1,
    val daysOfWeek: List<Int>? = null,    // 1=Mon..7=Sun
    val dayOfMonth: Int? = null,
    val endDate: Long? = null,
    val maxOccurrences: Int? = null,
    val occurrenceCount: Int = 0,
    val skipWeekends: Boolean = false,

    // v1.3.0 P13: advanced recurrence fields (all nullable for backward compat)

    /** CUSTOM_DAYS: specific days of the month (1-31), e.g. [1, 15] for 1st & 15th. */
    val monthDays: List<Int>? = null,

    /** AFTER_COMPLETION: how many units after the task is marked complete. */
    val afterCompletionInterval: Int? = null,

    /** AFTER_COMPLETION: "days" or "weeks". */
    val afterCompletionUnit: String? = null
)

enum class RecurrenceType {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM,
    // v1.3.0 P13
    WEEKDAY,
    BIWEEKLY,
    CUSTOM_DAYS,
    AFTER_COMPLETION
}
