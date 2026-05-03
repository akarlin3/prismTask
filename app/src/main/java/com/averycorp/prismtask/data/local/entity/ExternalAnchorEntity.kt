package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An external anchor on a project (and optionally a specific phase
 * within that project). The polymorphic
 * [com.averycorp.prismtask.domain.model.ExternalAnchor] payload is
 * stored as JSON in [anchorJson] via [com.averycorp.prismtask.data.remote.adapter.ExternalAnchorJsonAdapter],
 * mirroring the pattern AutomationEngine uses for its sealed
 * trigger / condition / action hierarchies (PR #1056).
 *
 * Phase deletion does not cascade — the anchor stays attached to its
 * project with [phaseId] reset to NULL via FK SET_NULL. Project
 * deletion DOES cascade because anchors only make sense in the
 * context of a parent project.
 *
 * Added in v1.8.x as part of the PrismTask-timeline-class scope (PR-3).
 */
@Entity(
    tableName = "external_anchors",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectPhaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["phase_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("project_id"),
        Index("phase_id"),
        Index(value = ["cloud_id"], unique = true)
    ]
)
data class ExternalAnchorEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "cloud_id")
    val cloudId: String? = null,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "phase_id")
    val phaseId: Long? = null,
    val label: String,
    /** [com.averycorp.prismtask.domain.model.ExternalAnchor] payload as JSON. */
    @ColumnInfo(name = "anchor_json")
    val anchorJson: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
