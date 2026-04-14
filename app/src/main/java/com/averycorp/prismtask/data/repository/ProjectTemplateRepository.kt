package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.ProjectTemplateDao
import com.averycorp.prismtask.data.local.entity.ProjectTemplateEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectTemplateRepository
    @Inject
    constructor(
        private val dao: ProjectTemplateDao
    ) {
        private val gson = Gson()

        /** One inline task definition within a project template. */
        data class InlineTask(
            val title: String,
            val description: String? = null,
            val priority: Int = 0,
            val durationMinutes: Int? = null,
            val recurrenceJson: String? = null,
            val dayOffset: Int = 0
        )

        fun getAll(): Flow<List<ProjectTemplateEntity>> = dao.getAll()

        suspend fun getById(id: Long): ProjectTemplateEntity? = dao.getById(id)

        suspend fun insert(template: ProjectTemplateEntity): Long = dao.insert(template)

        suspend fun update(template: ProjectTemplateEntity) = dao.update(template)

        suspend fun delete(template: ProjectTemplateEntity) = dao.delete(template)

        suspend fun incrementUsage(id: Long) = dao.incrementUsage(id)

        fun decodeTasks(template: ProjectTemplateEntity): List<InlineTask> =
            try {
                val type = com.google.gson.reflect.TypeToken
                    .getParameterized(List::class.java, InlineTask::class.java)
                    .type
                gson.fromJson(template.taskTemplatesJson, type) ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

        fun encodeTasks(tasks: List<InlineTask>): String = gson.toJson(tasks)

        suspend fun seedBuiltInsIfEmpty() {
            if (dao.count() > 0) return
            val now = System.currentTimeMillis()
            BUILT_INS.forEach { builtIn ->
                dao.insert(
                    ProjectTemplateEntity(
                        name = builtIn.name,
                        color = builtIn.color,
                        iconEmoji = builtIn.iconEmoji,
                        category = builtIn.category,
                        taskTemplatesJson = encodeTasks(builtIn.tasks),
                        isBuiltIn = true,
                        createdAt = now
                    )
                )
            }
        }

        data class BuiltIn(
            val name: String,
            val color: String?,
            val iconEmoji: String?,
            val category: String,
            val tasks: List<InlineTask>
        )

        companion object {
            val BUILT_INS = listOf(
                BuiltIn(
                    name = "Sprint",
                    color = "#2563EB",
                    iconEmoji = "\uD83C\uDFC3",
                    category = "Work",
                    tasks = listOf(
                        InlineTask("Planning", priority = 2, dayOffset = 0),
                        InlineTask("Daily Standup", priority = 1, dayOffset = 1),
                        InlineTask("Sprint Review", priority = 2, dayOffset = 14),
                        InlineTask("Retrospective", priority = 2, dayOffset = 14)
                    )
                ),
                BuiltIn(
                    name = "Event Planning",
                    color = "#E91E63",
                    iconEmoji = "\uD83C\uDF89",
                    category = "Personal",
                    tasks = listOf(
                        InlineTask("Venue", priority = 3, dayOffset = 0),
                        InlineTask("Guest List", priority = 2, dayOffset = 1),
                        InlineTask("Invitations", priority = 2, dayOffset = 3),
                        InlineTask("Catering", priority = 2, dayOffset = 7),
                        InlineTask("Day-of Setup", priority = 3, dayOffset = 14),
                        InlineTask("Cleanup", priority = 1, dayOffset = 15)
                    )
                ),
                BuiltIn(
                    name = "Course",
                    color = "#4CAF50",
                    iconEmoji = "\uD83D\uDCDA",
                    category = "Learning",
                    tasks = listOf(
                        InlineTask("Syllabus Review", priority = 2, dayOffset = 0),
                        InlineTask("Weekly Reading", priority = 1, dayOffset = 7),
                        InlineTask("Midterm Prep", priority = 3, dayOffset = 30),
                        InlineTask("Final Prep", priority = 4, dayOffset = 60),
                        InlineTask("Final Project", priority = 4, dayOffset = 70)
                    )
                )
            )
        }
    }
