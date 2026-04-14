package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.HabitTemplateDao
import com.averycorp.prismtask.data.local.entity.HabitTemplateEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitTemplateRepository
    @Inject
    constructor(
        private val dao: HabitTemplateDao
    ) {
        fun getAll(): Flow<List<HabitTemplateEntity>> = dao.getAll()

        suspend fun getById(id: Long): HabitTemplateEntity? = dao.getById(id)

        suspend fun insert(template: HabitTemplateEntity): Long = dao.insert(template)

        suspend fun update(template: HabitTemplateEntity) = dao.update(template)

        suspend fun delete(template: HabitTemplateEntity) = dao.delete(template)

        suspend fun incrementUsage(id: Long) = dao.incrementUsage(id)

        suspend fun seedBuiltInsIfEmpty() {
            if (dao.count() > 0) return
            val now = System.currentTimeMillis()
            BUILT_INS.forEach { b ->
                dao.insert(
                    HabitTemplateEntity(
                        name = b.name,
                        iconEmoji = b.iconEmoji,
                        color = b.color,
                        category = b.category,
                        frequency = b.frequency,
                        targetCount = 1,
                        activeDaysCsv = b.activeDaysCsv,
                        isBuiltIn = true,
                        createdAt = now
                    )
                )
            }
        }

        data class BuiltIn(
            val name: String,
            val iconEmoji: String,
            val color: String,
            val category: String,
            val frequency: String,
            val activeDaysCsv: String
        )

        companion object {
            val BUILT_INS = listOf(
                BuiltIn(
                    name = "Exercise",
                    iconEmoji = "\uD83D\uDCAA",
                    color = "#E53935",
                    category = "Health",
                    frequency = "DAILY",
                    activeDaysCsv = ""
                ),
                BuiltIn(
                    name = "Reading",
                    iconEmoji = "\uD83D\uDCD6",
                    color = "#1E88E5",
                    category = "Learning",
                    frequency = "DAILY",
                    activeDaysCsv = ""
                ),
                BuiltIn(
                    name = "Meditation",
                    iconEmoji = "\uD83E\uDDD8",
                    color = "#8E24AA",
                    category = "Wellness",
                    frequency = "DAILY",
                    activeDaysCsv = ""
                ),
                BuiltIn(
                    name = "Weekly Review",
                    iconEmoji = "\uD83D\uDCCA",
                    color = "#43A047",
                    category = "Productivity",
                    frequency = "WEEKLY",
                    activeDaysCsv = "7" // Sunday
                )
            )
        }
    }
