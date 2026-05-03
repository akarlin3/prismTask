package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A phase within a [ProjectEntity]. Phases group tasks under a project
 * with date ranges and version anchors so PrismTask itself can be modeled
 * as a project (e.g. "Phase F: Apr 25–May 9, ends at v1.9.0").
 *
 * Ordering within a project is user-controlled via [orderIndex]; lower
 * values come first.
 *
 * Phase membership on a task is represented by `tasks.phase_id` (nullable
 * — legacy tasks have no phase). When a phase is deleted, member tasks
 * have `phase_id` set to NULL via FK SET_NULL on the tasks side.
 *
 * Added in v1.8.x as part of the PrismTask-timeline-class scope (PR-1).
 */
@Entity(
    tableName = "project_phases",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("project_id"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class ProjectPhaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    val title: String,
    val description: String? = null,
    /** Token key resolved against the active theme palette (mirrors `projects.theme_color_key`). */
    @ColumnInfo(name = "color_key")
    val colorKey: String? = null,
    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,
    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,
    /** Free-form version anchor at phase end (e.g. "v1.9.0"). */
    @ColumnInfo(name = "version_anchor")
    val versionAnchor: String? = null,
    /** Optional release-note style summary tied to [versionAnchor]. */
    @ColumnInfo(name = "version_note")
    val versionNote: String? = null,
    @ColumnInfo(name = "order_index", defaultValue = "0")
    val orderIndex: Int = 0,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
