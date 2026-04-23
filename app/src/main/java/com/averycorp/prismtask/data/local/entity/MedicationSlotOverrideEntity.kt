package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-medication override of a [MedicationSlotEntity]'s time / drift for
 * a single link in [MedicationSlotCrossRef]. Exists only when a medication
 * deviates from the slot's global [MedicationSlotEntity.idealTime] /
 * `driftMinutes` — absence of an override row means the medication uses
 * the slot's values as-is.
 *
 * `CASCADE` on both FKs: deleting the medication or the slot drops any
 * overrides keyed to them. The unique `(medication_id, slot_id)` index
 * enforces one override per `(medication, slot)` pair.
 */
@Entity(
    tableName = "medication_slot_overrides",
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
        Index(value = ["medication_id", "slot_id"], unique = true),
        Index(value = ["slot_id"])
    ]
)
data class MedicationSlotOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
    @ColumnInfo(name = "slot_id")
    val slotId: Long,
    /** Nullable: `null` means "inherit slot's idealTime". */
    @ColumnInfo(name = "override_ideal_time")
    val overrideIdealTime: String? = null,
    /** Nullable: `null` means "inherit slot's driftMinutes". */
    @ColumnInfo(name = "override_drift_minutes")
    val overrideDriftMinutes: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
