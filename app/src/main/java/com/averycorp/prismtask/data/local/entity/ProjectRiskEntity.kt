package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A risk register entry on a [ProjectEntity]. Risks express known
 * project hazards with a severity level and optional mitigation note.
 *
 * Stored as a simple flat list per project; ordering is by [createdAt]
 * unless the UI sorts otherwise. Resolved risks carry [resolvedAt] but
 * remain in the table for historical context.
 *
 * Severity is stored as the [com.averycorp.prismtask.domain.model.RiskLevel]
 * enum name ("LOW" / "MEDIUM" / "HIGH"). Unknown or null values are
 * treated as `MEDIUM` by the domain layer.
 *
 * Added in v1.8.x as part of the PrismTask-timeline-class scope (PR-1).
 */
@Entity(
    tableName = "project_risks",
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
data class ProjectRiskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    val title: String,
    @ColumnInfo(name = "level", defaultValue = "MEDIUM")
    val level: String = "MEDIUM",
    val mitigation: String? = null,
    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
