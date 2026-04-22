package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "study_logs",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_pick"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = AssignmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignment_pick"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("date", unique = true),
        Index("course_pick"),
        Index("assignment_pick"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class StudyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "course_pick") val coursePick: Long? = null,
    @ColumnInfo(name = "study_done") val studyDone: Boolean = false,
    @ColumnInfo(name = "assignment_pick") val assignmentPick: Long? = null,
    @ColumnInfo(name = "assignment_done") val assignmentDone: Boolean = false,
    @ColumnInfo(name = "started_at") val startedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "cloud_id") val cloudId: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0L
)
