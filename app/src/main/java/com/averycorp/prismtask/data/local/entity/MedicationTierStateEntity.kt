package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-day achieved tier for a given `(medication, slot)` pair. Replaces
 * the legacy `self_care_logs.tiers_by_time` JSON column as the canonical
 * store of "how did this slot land on this day".
 *
 * - [tier] is stored as a lowercase token (see `AchievedTier.toStorage()`).
 * - [tierSource] discriminates auto-computed values from user overrides so
 *   the UI can decide whether to refresh on dose changes.
 * - `(medication_id, log_date, slot_id)` is unique — one row per
 *   slot-day-med triple.
 *
 * `CASCADE` on both FKs: removing a medication or a slot scrubs its tier
 * history. Historical preservation when a slot is retired is handled via
 * the `is_active = 0` soft-delete flag on `medication_slots` (rows remain,
 * slot row remains, FK remains valid).
 */
@Entity(
    tableName = "medication_tier_states",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicationSlotEntity::class,
            parentColumns = ["id"],
            childColumns = ["slot_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cloud_id"], unique = true),
        Index(value = ["medication_id", "log_date", "slot_id"], unique = true),
        Index(value = ["log_date"]),
        Index(value = ["slot_id"])
    ]
)
data class MedicationTierStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
    @ColumnInfo(name = "slot_id")
    val slotId: Long,
    /** ISO LocalDate in device timezone — mirrors `medication_doses.taken_date_local`. */
    @ColumnInfo(name = "log_date")
    val logDate: String,
    /** Lowercase `AchievedTier` token: `skipped` / `essential` / `prescription` / `complete`. */
    @ColumnInfo(name = "tier")
    val tier: String,
    /** Lowercase `TierSource` token: `computed` or `user_set`. */
    @ColumnInfo(name = "tier_source", defaultValue = "computed")
    val tierSource: String = "computed",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
