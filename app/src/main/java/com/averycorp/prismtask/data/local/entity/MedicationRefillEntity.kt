package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-medication refill metadata (v1.4.0 V10).
 *
 * This is a separate table from `self_care_steps` — the existing medication
 * feature models each dose as a self-care step, which works for "is it time
 * for your meds" but isn't a good place to hang pharmacy/refill data. This
 * table stores one row per named medication; the name is the link from the
 * self-care-step side.
 */
@Entity(
    tableName = "medication_refills",
    indices = [
        Index(value = ["medication_name"], unique = true),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class MedicationRefillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "medication_name")
    val medicationName: String,
    /** Current pill count on hand. */
    @ColumnInfo(name = "pill_count")
    val pillCount: Int,
    /** Number of pills per dose (e.g. 2 tablets). */
    @ColumnInfo(name = "pills_per_dose", defaultValue = "1")
    val pillsPerDose: Int = 1,
    /** Number of doses taken per day. */
    @ColumnInfo(name = "doses_per_day", defaultValue = "1")
    val dosesPerDay: Int = 1,
    /** Date (millis) of the last refill — anchors the calculated refill date. */
    @ColumnInfo(name = "last_refill_date")
    val lastRefillDate: Long? = null,
    @ColumnInfo(name = "pharmacy_name")
    val pharmacyName: String? = null,
    @ColumnInfo(name = "pharmacy_phone")
    val pharmacyPhone: String? = null,
    /**
     * How many days before the calculated refill date to schedule the
     * reminder. Defaults to 3 — "three days heads-up."
     */
    @ColumnInfo(name = "reminder_days_before", defaultValue = "3")
    val reminderDaysBefore: Int = 3,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null
)
