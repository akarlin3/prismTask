package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A reusable project blueprint. Child task definitions are stored as a JSON
 * blob so the task schema can evolve without a Room migration.
 *
 * Added in v1.3.0 (P15).
 */
@Entity(tableName = "project_templates")
data class ProjectTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val color: String? = null,
    @ColumnInfo(name = "icon_emoji")
    val iconEmoji: String? = null,
    val category: String? = null,
    /** JSON array of inline task definitions (title/description/priority/etc). */
    @ColumnInfo(name = "task_templates_json")
    val taskTemplatesJson: String,
    @ColumnInfo(name = "is_built_in", defaultValue = "0")
    val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "usage_count", defaultValue = "0")
    val usageCount: Int = 0,
    @ColumnInfo(name = "last_used_at")
    val lastUsedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
