package com.averycorp.prismtask.data.remote.mapper

import com.averycorp.prismtask.data.local.entity.HabitCompletionEntity
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity

object SyncMapper {

    fun taskToMap(task: TaskEntity, tagIds: List<String> = emptyList()): Map<String, Any?> = mapOf(
        "localId" to task.id,
        "title" to task.title,
        "description" to task.description,
        "dueDate" to task.dueDate,
        "dueTime" to task.dueTime,
        "priority" to task.priority,
        "isCompleted" to task.isCompleted,
        "projectId" to task.projectId?.toString(),
        "parentTaskId" to task.parentTaskId?.toString(),
        "recurrenceRule" to task.recurrenceRule,
        "reminderOffset" to task.reminderOffset,
        "tags" to tagIds,
        "plannedDate" to task.plannedDate,
        "estimatedDuration" to task.estimatedDuration,
        "scheduledStartTime" to task.scheduledStartTime,
        "notes" to task.notes,
        "eisenhowerQuadrant" to task.eisenhowerQuadrant,
        "eisenhowerUpdatedAt" to task.eisenhowerUpdatedAt,
        "eisenhowerReason" to task.eisenhowerReason,
        "sortOrder" to task.sortOrder,
        "createdAt" to task.createdAt,
        "updatedAt" to task.updatedAt,
        "completedAt" to task.completedAt,
        "archivedAt" to task.archivedAt
    )

    fun mapToTask(data: Map<String, Any?>, localId: Long = 0): TaskEntity = TaskEntity(
        id = localId,
        title = data["title"] as? String ?: "",
        description = data["description"] as? String,
        dueDate = (data["dueDate"] as? Number)?.toLong(),
        dueTime = (data["dueTime"] as? Number)?.toLong(),
        priority = (data["priority"] as? Number)?.toInt() ?: 0,
        isCompleted = data["isCompleted"] as? Boolean ?: false,
        projectId = (data["projectId"] as? String)?.toLongOrNull(),
        parentTaskId = (data["parentTaskId"] as? String)?.toLongOrNull(),
        recurrenceRule = data["recurrenceRule"] as? String,
        reminderOffset = (data["reminderOffset"] as? Number)?.toLong(),
        plannedDate = (data["plannedDate"] as? Number)?.toLong(),
        estimatedDuration = (data["estimatedDuration"] as? Number)?.toInt(),
        scheduledStartTime = (data["scheduledStartTime"] as? Number)?.toLong(),
        notes = data["notes"] as? String,
        eisenhowerQuadrant = data["eisenhowerQuadrant"] as? String,
        eisenhowerUpdatedAt = (data["eisenhowerUpdatedAt"] as? Number)?.toLong(),
        eisenhowerReason = data["eisenhowerReason"] as? String,
        sortOrder = (data["sortOrder"] as? Number)?.toInt() ?: 0,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        completedAt = (data["completedAt"] as? Number)?.toLong(),
        archivedAt = (data["archivedAt"] as? Number)?.toLong()
    )

    fun projectToMap(project: ProjectEntity): Map<String, Any?> = mapOf(
        "localId" to project.id,
        "name" to project.name,
        "color" to project.color,
        "icon" to project.icon,
        "createdAt" to project.createdAt,
        "updatedAt" to project.updatedAt
    )

    fun mapToProject(data: Map<String, Any?>, localId: Long = 0): ProjectEntity = ProjectEntity(
        id = localId,
        name = data["name"] as? String ?: "",
        color = data["color"] as? String ?: "#4A90D9",
        icon = data["icon"] as? String ?: "\uD83D\uDCC1",
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun tagToMap(tag: TagEntity): Map<String, Any?> = mapOf(
        "localId" to tag.id,
        "name" to tag.name,
        "color" to tag.color,
        "createdAt" to tag.createdAt
    )

    fun mapToTag(data: Map<String, Any?>, localId: Long = 0): TagEntity = TagEntity(
        id = localId,
        name = data["name"] as? String ?: "",
        color = data["color"] as? String ?: "#6B7280",
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun habitToMap(habit: HabitEntity): Map<String, Any?> = mapOf(
        "localId" to habit.id,
        "name" to habit.name,
        "description" to habit.description,
        "targetFrequency" to habit.targetFrequency,
        "frequencyPeriod" to habit.frequencyPeriod,
        "activeDays" to habit.activeDays,
        "color" to habit.color,
        "icon" to habit.icon,
        "reminderTime" to habit.reminderTime,
        "sortOrder" to habit.sortOrder,
        "isArchived" to habit.isArchived,
        "category" to habit.category,
        "createDailyTask" to habit.createDailyTask,
        "reminderIntervalMillis" to habit.reminderIntervalMillis,
        "reminderTimesPerDay" to habit.reminderTimesPerDay,
        "hasLogging" to habit.hasLogging,
        "trackBooking" to habit.trackBooking,
        "trackPreviousPeriod" to habit.trackPreviousPeriod,
        "createdAt" to habit.createdAt,
        "updatedAt" to habit.updatedAt
    )

    fun mapToHabit(data: Map<String, Any?>, localId: Long = 0): HabitEntity = HabitEntity(
        id = localId,
        name = data["name"] as? String ?: "",
        description = data["description"] as? String,
        targetFrequency = (data["targetFrequency"] as? Number)?.toInt() ?: 1,
        frequencyPeriod = data["frequencyPeriod"] as? String ?: "daily",
        activeDays = data["activeDays"] as? String,
        color = data["color"] as? String ?: "#4A90D9",
        icon = data["icon"] as? String ?: "\u2B50",
        reminderTime = (data["reminderTime"] as? Number)?.toLong(),
        sortOrder = (data["sortOrder"] as? Number)?.toInt() ?: 0,
        isArchived = data["isArchived"] as? Boolean ?: false,
        category = data["category"] as? String,
        createDailyTask = data["createDailyTask"] as? Boolean ?: false,
        reminderIntervalMillis = (data["reminderIntervalMillis"] as? Number)?.toLong(),
        reminderTimesPerDay = (data["reminderTimesPerDay"] as? Number)?.toInt() ?: 1,
        hasLogging = data["hasLogging"] as? Boolean ?: false,
        trackBooking = data["trackBooking"] as? Boolean ?: false,
        trackPreviousPeriod = data["trackPreviousPeriod"] as? Boolean ?: false,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    fun habitCompletionToMap(completion: HabitCompletionEntity, habitCloudId: String): Map<String, Any?> = mapOf(
        "localId" to completion.id,
        "habitCloudId" to habitCloudId,
        "completedDate" to completion.completedDate,
        "completedAt" to completion.completedAt,
        "notes" to completion.notes
    )

    fun mapToHabitCompletion(data: Map<String, Any?>, localId: Long = 0, habitLocalId: Long = 0): HabitCompletionEntity =
        HabitCompletionEntity(
            id = localId,
            habitId = habitLocalId,
            completedDate = (data["completedDate"] as? Number)?.toLong() ?: 0,
            completedAt = (data["completedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            notes = data["notes"] as? String
        )

    fun taskTemplateToMap(template: TaskTemplateEntity): Map<String, Any?> = mapOf(
        "localId" to template.id,
        "userId" to template.userId,
        "remoteId" to template.remoteId,
        "name" to template.name,
        "description" to template.description,
        "icon" to template.icon,
        "category" to template.category,
        "templateTitle" to template.templateTitle,
        "templateDescription" to template.templateDescription,
        "templatePriority" to template.templatePriority,
        "templateProjectId" to template.templateProjectId?.toString(),
        "templateTagsJson" to template.templateTagsJson,
        "templateRecurrenceJson" to template.templateRecurrenceJson,
        "templateDuration" to template.templateDuration,
        "templateSubtasksJson" to template.templateSubtasksJson,
        "isBuiltIn" to template.isBuiltIn,
        "usageCount" to template.usageCount,
        "lastUsedAt" to template.lastUsedAt,
        "createdAt" to template.createdAt,
        "updatedAt" to template.updatedAt
    )

    fun mapToTaskTemplate(data: Map<String, Any?>, localId: Long = 0): TaskTemplateEntity = TaskTemplateEntity(
        id = localId,
        userId = data["userId"] as? String,
        remoteId = (data["remoteId"] as? Number)?.toInt(),
        name = data["name"] as? String ?: "",
        description = data["description"] as? String,
        icon = data["icon"] as? String,
        category = data["category"] as? String,
        templateTitle = data["templateTitle"] as? String,
        templateDescription = data["templateDescription"] as? String,
        templatePriority = (data["templatePriority"] as? Number)?.toInt(),
        templateProjectId = (data["templateProjectId"] as? String)?.toLongOrNull()
            ?: (data["templateProjectId"] as? Number)?.toLong(),
        templateTagsJson = data["templateTagsJson"] as? String,
        templateRecurrenceJson = data["templateRecurrenceJson"] as? String,
        templateDuration = (data["templateDuration"] as? Number)?.toInt(),
        templateSubtasksJson = data["templateSubtasksJson"] as? String,
        isBuiltIn = data["isBuiltIn"] as? Boolean ?: false,
        usageCount = (data["usageCount"] as? Number)?.toInt() ?: 0,
        lastUsedAt = (data["lastUsedAt"] as? Number)?.toLong(),
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}
