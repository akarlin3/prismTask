package com.averykarlin.averytask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_completions",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("habit_id"),
        Index("completed_date")
    ]
)
data class HabitCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "habit_id")
    val habitId: Long,

    @ColumnInfo(name = "completed_date")
    val completedDate: Long,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long = System.currentTimeMillis(),

    val notes: String? = null
)
