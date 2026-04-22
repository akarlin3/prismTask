package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Daily mood + energy check-in entry (v1.4.0 V7).
 *
 * One row per day is the common case, but an evening check-in can produce a
 * second row on the same calendar date — that's why the unique index is on
 * `(date, time_of_day)` rather than `date` alone. [timeOfDay] is either
 * "morning" or "evening"; more granular slots can be added later.
 *
 * [mood] and [energy] are stored as integers 1..5 (1 = low / unwell, 5 = peak).
 * [notes] is optional free text.
 */
@Entity(
    tableName = "mood_energy_logs",
    indices = [
        Index("date"),
        Index(value = ["date", "time_of_day"], unique = true),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class MoodEnergyLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Midnight-normalized date in millis. */
    @ColumnInfo(name = "date")
    val date: Long,
    @ColumnInfo(name = "mood")
    val mood: Int,
    @ColumnInfo(name = "energy")
    val energy: Int,
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "time_of_day", defaultValue = "'morning'")
    val timeOfDay: String = "morning",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
