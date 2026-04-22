package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Medications are a top-level entity in v1.4 (spec:
 * `docs/SPEC_MEDICATIONS_TOP_LEVEL.md`). Each row represents one user-tracked
 * medication with its schedule and optional refill/pharmacy metadata.
 *
 * The old representation scattered medication data across four surfaces
 * (the built-in `"Medication"` habit row, `self_care_steps` with
 * `routine_type='medication'`, `self_care_logs`, and `MedicationPreferences`
 * DataStore); the v53 → v54 migration collapses all of those into this table
 * plus [MedicationDoseEntity]. Source rows are quarantined for 2+ weeks
 * before a Phase 2 cleanup migration drops them.
 *
 * Schedule semantics: exactly one of [timesOfDay], [specificTimes], or
 * [intervalMillis] is populated for any given row; [scheduleMode]
 * disambiguates which. `AS_NEEDED` leaves all three null.
 */
@Entity(
    tableName = "medications",
    indices = [
        Index(value = ["cloud_id"], unique = true),
        Index(value = ["name"], unique = true)
    ]
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "display_label")
    val displayLabel: String? = null,
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "tier", defaultValue = "essential")
    val tier: String = "essential",
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    /**
     * One of `"TIMES_OF_DAY"`, `"SPECIFIC_TIMES"`, `"INTERVAL"`,
     * `"AS_NEEDED"`. Stored as a string (not a typed enum) to match the
     * existing repo convention for schedule-discriminator fields
     * (`HabitEntity.frequencyPeriod`, `routine_type`). Domain-layer parsing
     * defaults unknown values to `TIMES_OF_DAY`.
     */
    @ColumnInfo(name = "schedule_mode", defaultValue = "TIMES_OF_DAY")
    val scheduleMode: String = "TIMES_OF_DAY",
    /** Comma-separated subset of `"morning,afternoon,evening,night"`. */
    @ColumnInfo(name = "times_of_day")
    val timesOfDay: String? = null,
    /** Comma-separated `"HH:mm"` strings, e.g. `"08:00,14:30,21:00"`. */
    @ColumnInfo(name = "specific_times")
    val specificTimes: String? = null,
    @ColumnInfo(name = "interval_millis")
    val intervalMillis: Long? = null,
    @ColumnInfo(name = "doses_per_day", defaultValue = "1")
    val dosesPerDay: Int = 1,
    @ColumnInfo(name = "pill_count")
    val pillCount: Int? = null,
    @ColumnInfo(name = "pills_per_dose", defaultValue = "1")
    val pillsPerDose: Int = 1,
    @ColumnInfo(name = "last_refill_date")
    val lastRefillDate: Long? = null,
    @ColumnInfo(name = "pharmacy_name")
    val pharmacyName: String? = null,
    @ColumnInfo(name = "pharmacy_phone")
    val pharmacyPhone: String? = null,
    @ColumnInfo(name = "reminder_days_before", defaultValue = "3")
    val reminderDaysBefore: Int = 3,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
