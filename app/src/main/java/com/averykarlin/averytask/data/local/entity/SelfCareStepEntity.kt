package com.averykarlin.averytask.data.local.entity

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
    @ColumnInfo(name = "reminder_delay_millis") val reminderDelayMillis: Long? = null
)
