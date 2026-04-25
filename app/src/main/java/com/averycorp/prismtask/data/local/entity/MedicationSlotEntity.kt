package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User-defined medication time slot (e.g. "Morning", "After dinner"). Slots
 * are a global per-user collection; a medication is linked to one or more
 * slots via [MedicationSlotCrossRef] and may override the slot's default
 * time via [MedicationSlotOverrideEntity].
 *
 * [idealTime] is stored as a wall-clock `"HH:mm"` string; [driftMinutes]
 * is the ± acceptable window around that time for reminder scheduling.
 * [isActive] acts as a soft-delete flag so historical tier-state rows
 * remain interpretable after a slot is retired.
 *
 * [reminderMode] selects between wall-clock reminders ("CLOCK") and
 * interval-based reminders ("INTERVAL", fire N minutes after the most
 * recent dose). Null means "inherit the user's global default."
 * [reminderIntervalMinutes] is only meaningful when the resolved mode is
 * INTERVAL. The full precedence chain (medication → slot → global) lives
 * in `MedicationReminderModeResolver` (PR2).
 *
 * Tenancy mirrors every other synced entity in the project: no `user_id`
 * column — Firestore document-path scoping (`users/{uid}/medication_slots`)
 * enforces per-user isolation.
 */
@Entity(
    tableName = "medication_slots",
    indices = [
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class MedicationSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "name")
    val name: String,
    /** Wall-clock `"HH:mm"` string (e.g. `"09:00"`). */
    @ColumnInfo(name = "ideal_time")
    val idealTime: String,
    @ColumnInfo(name = "drift_minutes", defaultValue = "180")
    val driftMinutes: Int = 180,
    @ColumnInfo(name = "sort_order", defaultValue = "0")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "is_active", defaultValue = "1")
    val isActive: Boolean = true,
    /** "CLOCK" | "INTERVAL" | null (inherit global default). */
    @ColumnInfo(name = "reminder_mode")
    val reminderMode: String? = null,
    /** Minutes between interval-mode reminders. Meaningful only when [reminderMode] resolves to INTERVAL. */
    @ColumnInfo(name = "reminder_interval_minutes")
    val reminderIntervalMinutes: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
