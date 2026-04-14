package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Daily morning check-in completion row (v1.4.0 V4).
 *
 * One row per calendar date — the check-in streak analytics card counts
 * consecutive rows. The `steps_completed_csv` column is a comma-separated
 * list of [com.averycorp.prismtask.domain.usecase.CheckInStep] enum names
 * so the history view can show which sections the user actually walked
 * through ("Skipped balance" etc.).
 */
@Entity(
    tableName = "check_in_logs",
    indices = [Index(value = ["date"], unique = true)]
)
data class CheckInLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Midnight-normalized date in millis. */
    @ColumnInfo(name = "date")
    val date: Long,
    @ColumnInfo(name = "steps_completed_csv")
    val stepsCompletedCsv: String,
    @ColumnInfo(name = "medications_confirmed", defaultValue = "0")
    val medicationsConfirmed: Int = 0,
    @ColumnInfo(name = "tasks_reviewed", defaultValue = "0")
    val tasksReviewed: Int = 0,
    @ColumnInfo(name = "habits_completed", defaultValue = "0")
    val habitsCompleted: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
