package com.averycorp.prismtask.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    /** Legacy hex color field; kept dual-written alongside [themeColorKey] for back-compat. */
    val color: String = "#4A90D9",
    val icon: String = "\uD83D\uDCC1",
    /**
     * v1.4.0 Projects feature: a token key resolved against the active theme
     * rather than a raw hex. Null on rows created before the feature existed;
     * callers fall back to [color] in that case.
     */
    @ColumnInfo(name = "theme_color_key")
    val themeColorKey: String? = null,
    /** [com.averycorp.prismtask.domain.model.ProjectStatus] name. Defaults to "ACTIVE" via the migration. */
    @ColumnInfo(name = "status", defaultValue = "ACTIVE")
    val status: String = "ACTIVE",
    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,
    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
