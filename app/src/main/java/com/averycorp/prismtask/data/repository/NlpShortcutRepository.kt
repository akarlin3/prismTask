package com.averycorp.prismtask.data.repository

import com.averycorp.prismtask.data.local.dao.NlpShortcutDao
import com.averycorp.prismtask.data.local.entity.NlpShortcutEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NlpShortcutRepository @Inject constructor(
    private val dao: NlpShortcutDao
) {
    fun getAll(): Flow<List<NlpShortcutEntity>> = dao.getAll()

    suspend fun getAllOnce(): List<NlpShortcutEntity> = dao.getAllOnce()

    suspend fun getByTrigger(trigger: String): NlpShortcutEntity? = dao.getByTrigger(trigger)

    suspend fun insert(shortcut: NlpShortcutEntity): Long = dao.insert(shortcut)

    suspend fun update(shortcut: NlpShortcutEntity) = dao.update(shortcut)

    suspend fun delete(shortcut: NlpShortcutEntity) = dao.delete(shortcut)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    /**
     * Seeds the four built-in example shortcuts if the table is empty. Safe to
     * call multiple times: subsequent calls are no-ops once the user has at
     * least one shortcut.
     */
    suspend fun seedBuiltInsIfEmpty() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        BUILT_IN_SHORTCUTS.forEachIndexed { index, (trigger, expansion) ->
            dao.insert(
                NlpShortcutEntity(
                    trigger = trigger,
                    expansion = expansion,
                    sortOrder = index,
                    createdAt = now
                )
            )
        }
    }

    companion object {
        val BUILT_IN_SHORTCUTS = listOf(
            "hw" to "Homework assignment @School !medium",
            "mtg" to "Meeting #work !high",
            "bug" to "Fix bug #dev !urgent",
            "errand" to "Errand #personal"
        )

        private val TRIGGER_REGEX = Regex("^[a-zA-Z0-9]{2,10}$")

        /** Validates a trigger: 2-10 alphanumeric chars. */
        fun isValidTrigger(trigger: String): Boolean = TRIGGER_REGEX.matches(trigger)
    }
}
