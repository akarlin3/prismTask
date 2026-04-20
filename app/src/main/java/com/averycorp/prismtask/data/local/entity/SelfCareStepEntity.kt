package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "self_care_steps")
data class SelfCareStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "step_id") val stepId: String,
    @ColumnInfo(name = "routine_type") val routineType: String,
    val label: String,
    val duration: String,
    val tier: String,
    val note: String = "",
    val phase: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "reminder_delay_millis") val reminderDelayMillis: Long? = null,
    @ColumnInfo(name = "time_of_day") val timeOfDay: String = "morning",
    /**
     * Optional link to a [MedicationRefillEntity] by exact name match
     * (v1.4.0 V10 follow-up). When non-null, the medication self-care
     * routine can surface "X days of pills left" next to this step and
     * a successful step completion can decrement the refill pill count
     * via [com.averycorp.prismtask.domain.usecase.RefillCalculator.applyDailyDose].
     */
    @ColumnInfo(name = "medication_name")
    val medicationName: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L
)
