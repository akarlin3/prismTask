package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single dose-taken event for a [MedicationEntity]. Parallels
 * `habit_completions` (matching the [takenDateLocal] timezone-neutrality
 * pattern from `MIGRATION_49_50`).
 *
 * [slotKey] identifies which schedule slot the dose satisfied — either a
 * wall-clock `"HH:mm"` (for specific-time schedules), a time-of-day bucket
 * (`"morning"`, `"afternoon"`, `"evening"`, `"night"`), or `"anytime"` for
 * interval / as-needed doses.
 *
 * `CASCADE` on delete mirrors `milestones`-from-`projects`: deleting a
 * medication removes its history. UI primary affordance should be
 * `archive`, not `delete`, for anything the user is actively tracking.
 * `daily_essential_slot_completions` has NO FK to this table — it keeps
 * its synthetic `source:name` dose keys and survives medication renames
 * independently.
 */
@Entity(
    tableName = "medication_doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cloud_id"], unique = true),
        Index(value = ["medication_id", "taken_date_local"]),
        Index(value = ["taken_date_local"])
    ]
)
data class MedicationDoseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
    @ColumnInfo(name = "slot_key")
    val slotKey: String,
    @ColumnInfo(name = "taken_at")
    val takenAt: Long,
    /** ISO LocalDate in device timezone — matches `habit_completions.completed_date_local`. */
    @ColumnInfo(name = "taken_date_local")
    val takenDateLocal: String,
    @ColumnInfo(name = "note", defaultValue = "")
    val note: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
