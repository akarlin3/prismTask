package com.averycorp.prismtask.smoke

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Smoke tests for the batch edit flows that power the multi-select bottom
 * bar. These hit the DAO directly to exercise the atomic UPDATE statements
 * rather than the Compose UI, since the UI path relies on long-press
 * gesture simulation which is very flaky in a smoke suite.
 */
@HiltAndroidTest
class MultiSelectBulkEditSmokeTest : SmokeTestBase() {

    @Test
    fun batchUpdatePriority_updatesEveryTaskInBatch() = runBlocking {
        val dao = database.taskDao()
        val ids = listOf(seededIds.todayTask1Id, seededIds.todayTask2Id, seededIds.tomorrowTaskId)
        dao.batchUpdatePriority(ids, priority = 4)

        ids.forEach { id ->
            val task = dao.getTaskByIdOnce(id)
            assert(task?.priority == 4) { "Task $id should have priority 4 after batch update" }
        }
    }

    @Test
    fun batchReschedule_setsDueDateOnAllTasks() = runBlocking {
        val dao = database.taskDao()
        val newDue = 1_900_000_000_000L
        dao.batchReschedule(listOf(seededIds.todayTask1Id, seededIds.tomorrowTaskId), newDue)

        assert(dao.getTaskByIdOnce(seededIds.todayTask1Id)?.dueDate == newDue)
        assert(dao.getTaskByIdOnce(seededIds.tomorrowTaskId)?.dueDate == newDue)
    }

    @Test
    fun batchReschedule_nullDueDateClearsField() = runBlocking {
        val dao = database.taskDao()
        dao.batchReschedule(listOf(seededIds.todayTask1Id), null)
        assert(dao.getTaskByIdOnce(seededIds.todayTask1Id)?.dueDate == null)
    }

    @Test
    fun batchMoveToProject_updatesProjectOnAllTasks() = runBlocking {
        val dao = database.taskDao()
        val targetProject = seededIds.projectPersonalId

        dao.batchMoveToProject(
            listOf(seededIds.todayTask1Id, seededIds.parentTaskId),
            targetProject
        )

        assert(dao.getTaskByIdOnce(seededIds.todayTask1Id)?.projectId == targetProject)
        assert(dao.getTaskByIdOnce(seededIds.parentTaskId)?.projectId == targetProject)
    }

    @Test
    fun batchMoveToProject_nullProjectClearsAssociation() = runBlocking {
        val dao = database.taskDao()
        dao.batchMoveToProject(listOf(seededIds.todayTask1Id), null)
        assert(dao.getTaskByIdOnce(seededIds.todayTask1Id)?.projectId == null)
    }

    @Test
    fun batchAddTag_attachesTagToAllTasks() = runBlocking {
        val dao = database.taskDao()
        val tagDao = database.tagDao()
        val tasks = listOf(seededIds.todayTask2Id, seededIds.tomorrowTaskId)

        dao.batchAddTag(tasks, seededIds.tagCodeId)

        tasks.forEach { id ->
            assert(seededIds.tagCodeId in tagDao.getTagIdsForTaskOnce(id)) {
                "Task $id should have the code tag after batchAddTag"
            }
        }
    }

    @Test
    fun batchRemoveTag_detachesTagFromAllTasks() = runBlocking {
        val dao = database.taskDao()
        val tagDao = database.tagDao()

        // overdueTaskId already carries the urgent tag from seed.
        dao.batchRemoveTag(listOf(seededIds.overdueTaskId), seededIds.tagUrgentId)

        val tagIds = tagDao.getTagIdsForTaskOnce(seededIds.overdueTaskId)
        assert(seededIds.tagUrgentId !in tagIds) {
            "batchRemoveTag should drop the urgent tag"
        }
    }
}
