package com.averycorp.prismtask.domain.model

data class RecurrenceRule(
    val type: RecurrenceType,
    val interval: Int = 1,
    val daysOfWeek: List<Int>? = null,    // 1=Mon..7=Sun
    val dayOfMonth: Int? = null,
    val endDate: Long? = null,
    val maxOccurrences: Int? = null,
    val occurrenceCount: Int = 0,
    val skipWeekends: Boolean = false
)

enum class RecurrenceType { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }
