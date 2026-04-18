package com.averycorp.prismtask.domain.model

/**
 * Lifecycle status of a [com.averycorp.prismtask.data.local.entity.ProjectEntity].
 *
 * Stored on the row as the enum name (e.g. "ACTIVE"). Unknown/legacy values
 * are treated as [ACTIVE] — see [fromStorageOrActive].
 */
enum class ProjectStatus {
    ACTIVE,
    COMPLETED,
    ARCHIVED;

    companion object {
        fun fromStorageOrActive(raw: String?): ProjectStatus =
            raw?.let { value -> entries.firstOrNull { it.name == value } } ?: ACTIVE
    }
}
