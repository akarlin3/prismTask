package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_completions",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["task_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("completed_date"),
        Index("project_id"),
        Index("task_id")
    ]
)
data class TaskCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "task_id")
    val taskId: Long? = null,

    @ColumnInfo(name = "project_id")
    val projectId: Long? = null,

    @ColumnInfo(name = "completed_date")
    val completedDate: Long,

    @ColumnInfo(name = "completed_at_time")
    val completedAtTime: Long = System.currentTimeMillis(),

    val priority: Int = 0,

    @ColumnInfo(name = "was_overdue")
    val wasOverdue: Boolean = false,

    @ColumnInfo(name = "days_to_complete")
    val daysToComplete: Int? = null,

    val tags: String? = null
)
