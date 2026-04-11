package com.averycorp.prismtask.smoke

import com.averycorp.prismtask.data.local.entity.TaskEntity
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Offline-first smoke tests. None of these exercise network code — they
 * verify the local Room layer behaves correctly after a sequence of
 * rapid-fire writes, deletions, and rereads, which is the "offline path"
 * the app falls back to when the backend is unreachable.
 */
@HiltAndroidTest
class OfflineEdgeCaseSmokeTest : SmokeTestBase() {

    @Test
    fun createTaskOffline_isVisibleAfterRefetch() = runBlocking {
        val dao = database.taskDao()
        val id = dao.insert(TaskEntity(title = "Offline draft"))
        val refetched = dao.getTaskByIdOnce(id)
        assert(refetched?.title == "Offline draft")
    }

    @Test
    fun editTaskOffline_persistsChanges() = runBlocking {
        val dao = database.taskDao()
        val id = dao.insert(TaskEntity(title = "Before"))
        dao.update(dao.getTaskByIdOnce(id)!!.copy(title = "After"))
        assert(dao.getTaskByIdOnce(id)?.title == "After")
    }

    @Test
    fun completeTaskOffline_isReflectedInCompletionQueries() = runBlocking {
        val dao = database.taskDao()
        val id = dao.insert(TaskEntity(title = "Do it", dueDate = System.currentTimeMillis()))
        dao.markCompleted(id, System.currentTimeMillis())
        val task = dao.getTaskByIdOnce(id)
        assert(task?.isCompleted == true)
        assert(task?.completedAt != null)
    }

    @Test
    fun deleteTaskOffline_removesTaskFromQueries() = runBlocking {
        val dao = database.taskDao()
        val id = dao.insert(TaskEntity(title = "Ephemeral"))
        dao.deleteById(id)
        assert(dao.getTaskByIdOnce(id) == null)
    }

    @Test
    fun rapidCreate_fiveTasksAllLand() = runBlocking {
        val dao = database.taskDao()
        val ids = (1..5).map { idx ->
            dao.insert(TaskEntity(title = "Rapid $idx"))
        }
        val all = dao.getAllTasksOnce()
        ids.forEach { id -> assert(all.any { it.id == id }) }
    }

    @Test
    fun cascadeDeleteSubtasksWhenParentIsDeleted() = runBlocking {
        val dao = database.taskDao()
        val parentId = dao.insert(TaskEntity(title = "Parent"))
        val subId = dao.insert(TaskEntity(title = "Child", parentTaskId = parentId))

        dao.deleteById(parentId)

        assert(dao.getTaskByIdOnce(parentId) == null)
        assert(dao.getTaskByIdOnce(subId) == null) {
            "Room FK cascade should remove subtasks when the parent is deleted"
        }
    }

    @Test
    fun sortOrderIsPreservedAcrossInserts() = runBlocking {
        val dao = database.taskDao()
        dao.insert(TaskEntity(title = "Alpha", sortOrder = 0))
        dao.insert(TaskEntity(title = "Bravo", sortOrder = 1))
        dao.insert(TaskEntity(title = "Charlie", sortOrder = 2))

        val all = dao.getAllTasksByCustomOrder().first().filter {
            it.title in listOf("Alpha", "Bravo", "Charlie")
        }
        assert(all.map { it.title } == listOf("Alpha", "Bravo", "Charlie"))
    }
}
