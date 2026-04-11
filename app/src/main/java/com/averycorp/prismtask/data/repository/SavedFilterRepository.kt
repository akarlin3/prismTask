package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.SavedFilterDao
import com.averycorp.prismtask.data.local.entity.SavedFilterEntity
import com.averycorp.prismtask.domain.model.DateRange
import com.averycorp.prismtask.domain.model.TaskFilter
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedFilterRepository @Inject constructor(
    private val dao: SavedFilterDao
) {
    private val gson = Gson()

    fun getAll(): Flow<List<SavedFilterEntity>> = dao.getAll()

    suspend fun getAllOnce(): List<SavedFilterEntity> = dao.getAllOnce()

    /**
     * Saves a new filter preset. If the name collides with an existing preset,
     * appends a unique suffix (" 2", " 3", ...) to the name.
     */
    suspend fun savePreset(
        name: String,
        filter: TaskFilter,
        iconEmoji: String? = null
    ): Long {
        val finalName = uniqueName(name)
        val entity = SavedFilterEntity(
            name = finalName,
            filterJson = gson.toJson(filter),
            iconEmoji = iconEmoji,
            sortOrder = dao.count(),
            createdAt = System.currentTimeMillis()
        )
        return dao.insert(entity)
    }

    suspend fun update(preset: SavedFilterEntity) = dao.update(preset)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /** Deserializes the JSON filter body back into a TaskFilter. */
    fun decodeFilter(entity: SavedFilterEntity): TaskFilter =
        try {
            gson.fromJson(entity.filterJson, TaskFilter::class.java) ?: TaskFilter()
        } catch (_: Exception) {
            TaskFilter()
        }

    /**
     * Seeds the three built-in presets ("High Priority", "This Week", "Flagged")
     * if the table is empty. Idempotent.
     */
    suspend fun seedBuiltInsIfEmpty() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        val weekMs = 7 * 24L * 60 * 60 * 1000
        val builtIns = listOf(
            Triple(
                "High Priority",
                "\uD83D\uDD34",
                TaskFilter(selectedPriorities = listOf(3, 4))
            ),
            Triple(
                "This Week",
                "\uD83D\uDCC5",
                TaskFilter(dateRange = DateRange(now, now + weekMs))
            ),
            Triple(
                "Flagged",
                "\uD83D\uDCCC",
                TaskFilter(showFlaggedOnly = true)
            )
        )
        builtIns.forEachIndexed { index, (name, emoji, filter) ->
            dao.insert(
                SavedFilterEntity(
                    name = name,
                    filterJson = gson.toJson(filter),
                    iconEmoji = emoji,
                    sortOrder = index,
                    createdAt = now
                )
            )
        }
    }

    /** Returns a name that does not collide with any existing preset. */
    private suspend fun uniqueName(requested: String): String {
        if (dao.getByName(requested) == null) return requested
        var suffix = 2
        while (dao.getByName("$requested $suffix") != null) suffix++
        return "$requested $suffix"
    }
}
