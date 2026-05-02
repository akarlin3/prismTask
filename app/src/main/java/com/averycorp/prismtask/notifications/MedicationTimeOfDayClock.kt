package com.averycorp.prismtask.notifications

/**
 * Wall-clock fallbacks for the four legacy time-of-day buckets used by
 * `MedicationEntity.timesOfDay`. Read from both
 * [MedicationReminderScheduler] (when arming legacy alarms) and
 * `NotificationProjector` (when projecting legacy phantom rows for
 * the today/upcoming surfaces). Keep the two in sync by reading from
 * here rather than duplicating the map.
 *
 * Independent of the user's start-of-day setting — medication timing
 * is wall-clock, not logical-day-anchored.
 */
internal val MEDICATION_TIME_OF_DAY_CLOCK: Map<String, String> = mapOf(
    "morning" to "08:00",
    "afternoon" to "13:00",
    "evening" to "18:00",
    "night" to "21:00"
)
