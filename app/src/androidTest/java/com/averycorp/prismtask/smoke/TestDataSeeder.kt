package com.averycorp.prismtask.smoke

import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.local.entity.HabitEntity
import com.averycorp.prismtask.data.local.entity.ProjectEntity
import com.averycorp.prismtask.data.local.entity.TagEntity
import com.averycorp.prismtask.data.local.entity.TaskEntity
import com.averycorp.prismtask.data.local.entity.TaskTagCrossRef
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import java.util.Calendar

/**
 * Seeds the test database with consistent sample data for smoke tests.
 *
 * Creates:
 * - 2 projects: "Work" (blue) and "Personal" (green)
 * - 3 tags: "urgent", "email", "code"
 * - 5 tasks: 1 overdue (yesterday, high priority), 2 today, 1 tomorrow, 1 no date
 * - 1 task with 3 subtasks
 * - 2 habits: "Exercise" (daily), "Read" (daily)
 * - 2 templates: "Morning Routine" and "Meeting Prep"
 * - Tag/project assignments across tasks
 */
object TestDataSeeder {
    data class SeededIds(
        val projectWorkId: Long,
        val projectPersonalId: Long,
        val tagUrgentId: Long,
        val tagEmailId: Long,
        val tagCodeId: Long,
        val overdueTaskId: Long,
        val todayTask1Id: Long,
        val todayTask2Id: Long,
        val tomorrowTaskId: Long,
        val noDateTaskId: Long,
        val parentTaskId: Long,
        val subtask1Id: Long,
        val subtask2Id: Long,
        val subtask3Id: Long,
        val habitExerciseId: Long,
        val habitReadId: Long,
        val templateMorningId: Long,
        val templateMeetingId: Long
    )

    suspend fun seed(database: PrismTaskDatabase): SeededIds {
        val taskDao = database.taskDao()
        val projectDao = database.projectDao()
        val tagDao = database.tagDao()
        val habitDao = database.habitDao()
        val templateDao = database.taskTemplateDao()

        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()

        // Yesterday (overdue)
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, -1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val yesterday = cal.timeInMillis

        // Today
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis

        // Tomorrow
        cal.timeInMillis = now
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val tomorrow = cal.timeInMillis

        // Projects
        val projectWorkId = projectDao.insert(
            ProjectEntity(name = "Work", color = "#4A90D9", icon = "\uD83D\uDCBC")
        )
        val projectPersonalId = projectDao.insert(
            ProjectEntity(name = "Personal", color = "#4CAF50", icon = "\uD83C\uDFE0")
        )

        // Tags
        val tagUrgentId = tagDao.insert(TagEntity(name = "urgent", color = "#E53935"))
        val tagEmailId = tagDao.insert(TagEntity(name = "email", color = "#1E88E5"))
        val tagCodeId = tagDao.insert(TagEntity(name = "code", color = "#43A047"))

        // Tasks
        val overdueTaskId = taskDao.insert(
            TaskEntity(
                title = "Overdue report",
                dueDate = yesterday,
                priority = 3,
                projectId = projectWorkId
            )
        )
        val todayTask1Id = taskDao.insert(
            TaskEntity(
                title = "Review pull requests",
                dueDate = today,
                priority = 2,
                projectId = projectWorkId
            )
        )
        val todayTask2Id = taskDao.insert(
            TaskEntity(
                title = "Buy groceries",
                dueDate = today,
                priority = 1,
                projectId = projectPersonalId
            )
        )
        val tomorrowTaskId = taskDao.insert(
            TaskEntity(
                title = "Plan weekend trip",
                dueDate = tomorrow,
                priority = 0,
                projectId = projectPersonalId
            )
        )
        val noDateTaskId = taskDao.insert(
            TaskEntity(
                title = "Read architecture docs",
                priority = 1,
                projectId = projectWorkId
            )
        )

        // Parent task with subtasks
        val parentTaskId = taskDao.insert(
            TaskEntity(
                title = "Prepare presentation",
                dueDate = today,
                priority = 2,
                projectId = projectWorkId
            )
        )
        val subtask1Id = taskDao.insert(
            TaskEntity(
                title = "Gather data",
                parentTaskId = parentTaskId,
                projectId = projectWorkId
            )
        )
        val subtask2Id = taskDao.insert(
            TaskEntity(
                title = "Create slides",
                parentTaskId = parentTaskId,
                projectId = projectWorkId
            )
        )
        val subtask3Id = taskDao.insert(
            TaskEntity(
                title = "Practice delivery",
                parentTaskId = parentTaskId,
                projectId = projectWorkId
            )
        )

        // Tag assignments
        tagDao.addTagToTask(TaskTagCrossRef(overdueTaskId, tagUrgentId))
        tagDao.addTagToTask(TaskTagCrossRef(todayTask1Id, tagCodeId))
        tagDao.addTagToTask(TaskTagCrossRef(todayTask2Id, tagEmailId))
        tagDao.addTagToTask(TaskTagCrossRef(parentTaskId, tagUrgentId))
        tagDao.addTagToTask(TaskTagCrossRef(parentTaskId, tagCodeId))

        // Habits
        val habitExerciseId = habitDao.insert(
            HabitEntity(name = "Exercise", icon = "\uD83D\uDCAA", color = "#FF5733")
        )
        val habitReadId = habitDao.insert(
            HabitEntity(name = "Read", icon = "\uD83D\uDCDA", color = "#3F51B5")
        )

        // Templates
        val templateMorningId = templateDao.insert(
            TaskTemplateEntity(
                name = "Morning Routine",
                icon = "\u2600\uFE0F",
                category = "Routines",
                templateTitle = "Complete Morning Routine",
                templatePriority = 2,
                templateSubtasksJson = "[\"Stretch\",\"Meditate\",\"Journal\"]",
                isBuiltIn = true
            )
        )
        val templateMeetingId = templateDao.insert(
            TaskTemplateEntity(
                name = "Meeting Prep",
                icon = "\uD83D\uDCCB",
                category = "Work",
                templateTitle = "Prepare for Meeting",
                templatePriority = 2,
                templateProjectId = projectWorkId,
                templateSubtasksJson = "[\"Gather notes\",\"Draft agenda\",\"Send invite\"]",
                isBuiltIn = true
            )
        )

        return SeededIds(
            projectWorkId = projectWorkId,
            projectPersonalId = projectPersonalId,
            tagUrgentId = tagUrgentId,
            tagEmailId = tagEmailId,
            tagCodeId = tagCodeId,
            overdueTaskId = overdueTaskId,
            todayTask1Id = todayTask1Id,
            todayTask2Id = todayTask2Id,
            tomorrowTaskId = tomorrowTaskId,
            noDateTaskId = noDateTaskId,
            parentTaskId = parentTaskId,
            subtask1Id = subtask1Id,
            subtask2Id = subtask2Id,
            subtask3Id = subtask3Id,
            habitExerciseId = habitExerciseId,
            habitReadId = habitReadId,
            templateMorningId = templateMorningId,
            templateMeetingId = templateMeetingId
        )
    }

    suspend fun clear(database: PrismTaskDatabase) {
        database.clearAllTables()
    }
}
