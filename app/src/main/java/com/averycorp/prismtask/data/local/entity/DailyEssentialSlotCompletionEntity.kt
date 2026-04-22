package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Materialized completion record for a Daily Essentials medication time slot.
 *
 * Slots are normally *virtual* — derived on the fly from the user's medication
 * schedule (habits, self-care steps, and [data.preferences.MedicationPreferences]
 * specific times). The first time the user interacts with a slot on a given
 * day, a row is inserted here; subsequent reads/writes flow through this row.
 *
 * ``slotKey`` is either a ``"HH:mm"`` wall-clock time or the literal string
 * ``"anytime"`` for interval-based doses that have no fixed clock time.
 * ``medIdsJson`` carries a JSON array of synthetic dose keys (``source:name``)
 * that belonged to the slot at materialization time. ``takenAt`` is non-null
 * only while the user has the slot checked.
 *
 * No foreign key to the parent medication entities — dose keys are synthetic
 * strings (``"specific_time:lipitor"``, etc.) that survive refill renames and
 * leave stale keys harmlessly orphaned.
 */
@Entity(
    tableName = "daily_essential_slot_completions",
    indices = [
        Index(value = ["date", "slot_key"], unique = true),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class DailyEssentialSlotCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "slot_key") val slotKey: String,
    @ColumnInfo(name = "med_ids_json") val medIdsJson: String = "[]",
    @ColumnInfo(name = "taken_at") val takenAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "cloud_id") val cloudId: String? = null
)
