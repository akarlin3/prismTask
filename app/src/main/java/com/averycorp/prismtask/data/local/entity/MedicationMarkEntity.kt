package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-medication mark within a tier-state slot. Where
 * [MedicationTierStateEntity] aggregates the achieved tier across every
 * medication in a slot, this row records the individual checkbox per
 * medication — including its own [intendedTime] (user-claimed) vs
 * [loggedAt] (database-write) stamps so a user can backdate a single
 * medication without affecting the slot-level intended time.
 *
 * `(medication_id, medication_tier_state_id)` is unique — one mark per
 * medication-within-slot. [markedTaken] discriminates affirmative marks
 * from explicit unmarks (the row is preserved on unmark so the user's
 * audit trail stays continuous).
 *
 * Both FKs CASCADE — removing a medication or its tier-state slot
 * scrubs the marks for it.
 */
@Entity(
    tableName = "medication_marks",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicationTierStateEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_tier_state_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cloud_id"], unique = true),
        Index(value = ["medication_id", "medication_tier_state_id"], unique = true),
        Index(value = ["medication_tier_state_id"]),
        Index(value = ["medication_id"])
    ]
)
data class MedicationMarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
    @ColumnInfo(name = "medication_tier_state_id")
    val medicationTierStateId: Long,
    /** User-claimed wall-clock epoch millis. NULL inherits from the slot. */
    @ColumnInfo(name = "intended_time")
    val intendedTime: Long? = null,
    /** Database-write epoch millis. NOT NULL. */
    @ColumnInfo(name = "logged_at")
    val loggedAt: Long = System.currentTimeMillis(),
    /**
     * `true` when the user marked this med taken in the slot; `false`
     * when the user explicitly unmarked. Rows are preserved on unmark
     * so the time-stamp history stays intact.
     */
    @ColumnInfo(name = "marked_taken", defaultValue = "1")
    val markedTaken: Boolean = true,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
