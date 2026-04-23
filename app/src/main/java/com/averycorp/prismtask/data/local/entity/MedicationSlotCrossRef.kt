package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Many-to-many link between [MedicationEntity] and [MedicationSlotEntity].
 * Mirrors the [TaskTagCrossRef] pattern (composite primary key, no
 * standalone id / cloud_id). The link set for a medication is synced by
 * serializing the slot cloud-ids onto the parent `MedicationEntity`
 * Firestore document — junction rows themselves are rebuilt on pull rather
 * than independently tracked.
 *
 * `CASCADE` on both sides keeps the junction consistent when either parent
 * is deleted.
 */
@Entity(
    tableName = "medication_medication_slots",
    primaryKeys = ["medication_id", "slot_id"],
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
        Index(value = ["slot_id"])
    ]
)
data class MedicationSlotCrossRef(
    @ColumnInfo(name = "medication_id")
    val medicationId: Long,
    @ColumnInfo(name = "slot_id")
    val slotId: Long
)
