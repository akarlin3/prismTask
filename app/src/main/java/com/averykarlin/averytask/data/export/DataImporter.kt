package com.averykarlin.averytask.data.export

import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.averykarlin.averytask.data.local.entity.ProjectEntity
import com.averykarlin.averytask.data.local.entity.TagEntity
import com.averykarlin.averytask.data.local.entity.TaskEntity
import com.averykarlin.averytask.data.local.entity.TaskTagCrossRef
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class ImportMode { MERGE, REPLACE }

data class ImportResult(
    val tasksImported: Int = 0,
    val projectsImported: Int = 0,
    val tagsImported: Int = 0,
    val duplicatesSkipped: Int = 0,
    val errors: List<String> = emptyList()
)

@Singleton
class DataImporter @Inject constructor(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao
) {
    suspend fun importFromJson(jsonString: String, mode: ImportMode): ImportResult {
        val errors = mutableListOf<String>()
        var tasksImported = 0
        var projectsImported = 0
        var tagsImported = 0
        var duplicatesSkipped = 0

        try {
            val root = JsonParser.parseString(jsonString).asJsonObject
            val gson = Gson()

            if (mode == ImportMode.REPLACE) {
                // Delete everything
                taskDao.getAllTasksOnce().forEach { taskDao.deleteById(it.id) }
                projectDao.getAllProjectsOnce().forEach { projectDao.delete(it) }
                tagDao.getAllTagsOnce().forEach { tagDao.delete(it) }
            }

            val existingProjects = projectDao.getAllProjectsOnce()
            val existingTags = tagDao.getAllTagsOnce()
            val existingTasks = taskDao.getAllTasksOnce()

            // Import projects
            val projectNameToId = mutableMapOf<String, Long>()
            existingProjects.forEach { projectNameToId[it.name.lowercase()] = it.id }

            root.getAsJsonArray("projects")?.forEach { elem ->
                val obj = elem.asJsonObject
                val name = obj.get("name")?.asString ?: return@forEach
                if (mode == ImportMode.MERGE && name.lowercase() in projectNameToId) {
                    duplicatesSkipped++
                } else {
                    val project = ProjectEntity(
                        name = name,
                        color = obj.get("color")?.asString ?: "#4A90D9",
                        icon = obj.get("icon")?.asString ?: "\uD83D\uDCC1",
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    val id = projectDao.insert(project)
                    projectNameToId[name.lowercase()] = id
                    projectsImported++
                }
            }

            // Import tags
            val tagNameToId = mutableMapOf<String, Long>()
            existingTags.forEach { tagNameToId[it.name.lowercase()] = it.id }

            root.getAsJsonArray("tags")?.forEach { elem ->
                val obj = elem.asJsonObject
                val name = obj.get("name")?.asString ?: return@forEach
                if (mode == ImportMode.MERGE && name.lowercase() in tagNameToId) {
                    duplicatesSkipped++
                } else {
                    val tag = TagEntity(
                        name = name,
                        color = obj.get("color")?.asString ?: "#6B7280",
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis()
                    )
                    val id = tagDao.insert(tag)
                    tagNameToId[name.lowercase()] = id
                    tagsImported++
                }
            }

            // Import tasks
            root.getAsJsonArray("tasks")?.forEach { elem ->
                try {
                    val obj = elem.asJsonObject
                    val title = obj.get("title")?.asString ?: return@forEach
                    if (title.isBlank()) return@forEach

                    // Dedup check
                    if (mode == ImportMode.MERGE) {
                        val createdAt = obj.get("createdAt")?.asLong ?: 0
                        val isDup = existingTasks.any { it.title == title && abs(it.createdAt - createdAt) < 60000 }
                        if (isDup) { duplicatesSkipped++; return@forEach }
                    }

                    val projectName = obj.get("project")?.asString
                    val projectId = projectName?.let { projectNameToId[it.lowercase()] }

                    val task = TaskEntity(
                        title = title,
                        description = obj.get("description")?.takeIf { !it.isJsonNull }?.asString,
                        notes = obj.get("notes")?.takeIf { !it.isJsonNull }?.asString,
                        dueDate = obj.get("dueDate")?.takeIf { !it.isJsonNull }?.asLong,
                        dueTime = obj.get("dueTime")?.takeIf { !it.isJsonNull }?.asLong,
                        priority = obj.get("priority")?.asInt ?: 0,
                        isCompleted = obj.get("isCompleted")?.asBoolean ?: false,
                        projectId = projectId,
                        recurrenceRule = obj.get("recurrenceRule")?.takeIf { !it.isJsonNull }?.asString,
                        reminderOffset = obj.get("reminderOffset")?.takeIf { !it.isJsonNull }?.asLong,
                        plannedDate = obj.get("plannedDate")?.takeIf { !it.isJsonNull }?.asLong,
                        estimatedDuration = obj.get("estimatedDuration")?.takeIf { !it.isJsonNull }?.asInt,
                        scheduledStartTime = obj.get("scheduledStartTime")?.takeIf { !it.isJsonNull }?.asLong,
                        createdAt = obj.get("createdAt")?.asLong ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        completedAt = obj.get("completedAt")?.takeIf { !it.isJsonNull }?.asLong,
                        archivedAt = obj.get("archivedAt")?.takeIf { !it.isJsonNull }?.asLong
                    )
                    val taskId = taskDao.insert(task)

                    // Assign tags
                    obj.getAsJsonArray("tags")?.forEach { tagElem ->
                        val tagName = tagElem.asString
                        val tagId = tagNameToId[tagName.lowercase()]
                        if (tagId != null) {
                            tagDao.addTagToTask(TaskTagCrossRef(taskId = taskId, tagId = tagId))
                        }
                    }

                    tasksImported++
                } catch (e: Exception) {
                    errors.add("Failed to import task: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("Import failed: ${e.message}")
        }

        return ImportResult(tasksImported, projectsImported, tagsImported, duplicatesSkipped, errors)
    }
}
