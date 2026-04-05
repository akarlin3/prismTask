package com.averykarlin.averytask.data.export

import com.averykarlin.averytask.data.local.dao.ProjectDao
import com.averykarlin.averytask.data.local.dao.TagDao
import com.averykarlin.averytask.data.local.dao.TaskDao
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExporter @Inject constructor(
    private val taskDao: TaskDao,
    private val projectDao: ProjectDao,
    private val tagDao: TagDao
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportToJson(): String {
        val root = JsonObject()
        root.addProperty("version", 1)
        root.addProperty("exportedAt", System.currentTimeMillis())
        root.addProperty("appVersion", "0.4.0")

        val tasks = taskDao.getAllTasksOnce()
        val projects = projectDao.getAllProjectsOnce()
        val tags = tagDao.getAllTagsOnce()

        root.add("tasks", gson.toJsonTree(tasks.map { task ->
            val taskTags = tagDao.getTagIdsForTaskOnce(task.id)
            val tagNames = taskTags.mapNotNull { id -> tags.find { it.id == id }?.name }
            val projectName = task.projectId?.let { pid -> projects.find { it.id == pid }?.name }
            mapOf(
                "title" to task.title,
                "description" to task.description,
                "notes" to task.notes,
                "dueDate" to task.dueDate,
                "dueTime" to task.dueTime,
                "priority" to task.priority,
                "isCompleted" to task.isCompleted,
                "project" to projectName,
                "tags" to tagNames,
                "recurrenceRule" to task.recurrenceRule,
                "reminderOffset" to task.reminderOffset,
                "plannedDate" to task.plannedDate,
                "estimatedDuration" to task.estimatedDuration,
                "scheduledStartTime" to task.scheduledStartTime,
                "createdAt" to task.createdAt,
                "updatedAt" to task.updatedAt,
                "completedAt" to task.completedAt,
                "archivedAt" to task.archivedAt
            )
        }))

        root.add("projects", gson.toJsonTree(projects.map {
            mapOf("name" to it.name, "color" to it.color, "icon" to it.icon, "createdAt" to it.createdAt)
        }))

        root.add("tags", gson.toJsonTree(tags.map {
            mapOf("name" to it.name, "color" to it.color, "createdAt" to it.createdAt)
        }))

        return gson.toJson(root)
    }

    suspend fun exportToCsv(): String {
        val tasks = taskDao.getAllTasksOnce()
        val projects = projectDao.getAllProjectsOnce()
        val tags = tagDao.getAllTagsOnce()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Title,Description,Due Date,Due Time,Priority,Project,Tags,Status,Created,Completed")

        for (task in tasks) {
            val tagNames = tagDao.getTagIdsForTaskOnce(task.id)
                .mapNotNull { id -> tags.find { it.id == id }?.name }
                .joinToString("; ")
            val projectName = task.projectId?.let { pid -> projects.find { it.id == pid }?.name } ?: ""
            val status = if (task.isCompleted) "Completed" else "Incomplete"

            sb.appendLine(listOf(
                csvEscape(task.title),
                csvEscape(task.description ?: ""),
                task.dueDate?.let { dateFormat.format(Date(it)) } ?: "",
                task.dueTime?.let { dateFormat.format(Date(it)) } ?: "",
                task.priority.toString(),
                csvEscape(projectName),
                csvEscape(tagNames),
                status,
                dateFormat.format(Date(task.createdAt)),
                task.completedAt?.let { dateFormat.format(Date(it)) } ?: ""
            ).joinToString(","))
        }

        return sb.toString()
    }

    private fun csvEscape(value: String): String =
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            "\"${value.replace("\"", "\"\"")}\""
        else value
}
