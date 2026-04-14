package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A named bundle of reminder offsets. Replaces the single-offset reminder on
 * a task with a profile that can fire multiple reminders and optionally
 * escalate at a fixed interval until the task is completed.
 *
 * Offsets are stored as a comma-separated list of Long milliseconds (0 = at
 * due time, positive = before due time, negative = after due time).
 *
 * Added in v1.3.0 (P14).
 */
@Entity(tableName = "reminder_profiles")
data class ReminderProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    /** Comma-separated list of offset millis. */
    @ColumnInfo(name = "offsets_csv")
    val offsetsCsv: String,
    /** If true, keep sending reminders at escalationIntervalMinutes until completed. */
    @ColumnInfo(name = "escalation", defaultValue = "0")
    val escalation: Boolean = false,
    @ColumnInfo(name = "escalation_interval_minutes")
    val escalationIntervalMinutes: Int? = null,
    @ColumnInfo(name = "is_built_in", defaultValue = "0")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Decodes [offsetsCsv] into a list of Long millis. */
    fun offsets(): List<Long> =
        offsetsCsv.split(",").mapNotNull { it.trim().toLongOrNull() }

    companion object {
        fun encodeOffsets(offsets: List<Long>): String = offsets.joinToString(",")
    }
}
