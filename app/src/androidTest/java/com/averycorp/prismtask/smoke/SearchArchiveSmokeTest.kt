package com.averycorp.prismtask.smoke

import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Smoke tests for search and archive functionality exercised via the DAO
 * directly. The full Compose UI for search/archive is wired through Settings
 * routes which are flaky to reach in an instrumentation run; these tests
 * focus on the data-layer wiring that backs those screens.
 */
@HiltAndroidTest
class SearchArchiveSmokeTest : SmokeTestBase() {
    @Test
    fun searchTasks_matchesOnTitle() = runBlocking {
        val results = database.taskDao().searchTasks("Review").first()
        assert(results.any { it.title.contains("Review", ignoreCase = true) }) {
            "searchTasks should return tasks matching the query"
        }
    }

    @Test
    fun searchTasks_caseInsensitiveSubstring() = runBlocking {
        val results = database.taskDao().searchTasks("groceries").first()
        assert(results.isNotEmpty()) { "searchTasks should be case-insensitive" }
    }

    @Test
    fun archiveTask_movesTaskOutOfActiveList() = runBlocking {
        val dao = database.taskDao()
        val taskId = seededIds.todayTask1Id

        dao.archiveTask(taskId, System.currentTimeMillis())
        val archived = dao.getArchivedTasks().first()
        assert(archived.any { it.id == taskId }) {
            "Archived task should appear in getArchivedTasks()"
        }
    }

    @Test
    fun unarchiveTask_clearsArchivedAtColumn() = runBlocking {
        val dao = database.taskDao()
        val taskId = seededIds.todayTask1Id
        dao.archiveTask(taskId, System.currentTimeMillis())
        dao.unarchiveTask(taskId, System.currentTimeMillis())
        val refreshed = dao.getTaskByIdOnce(taskId)
        assert(refreshed?.archivedAt == null) {
            "unarchiveTask should clear archivedAt"
        }
    }

    @Test
    fun searchArchivedTasks_onlyReturnsArchivedMatches() = runBlocking {
        val dao = database.taskDao()
        val taskId = seededIds.todayTask1Id
        dao.archiveTask(taskId, System.currentTimeMillis())

        val archivedHits = dao.searchArchivedTasks("Review").first()
        val activeHits = dao.searchArchivedTasks("Buy").first()
        assert(archivedHits.any { it.id == taskId })
        assert(activeHits.none { it.title.contains("Buy") }) {
            "searchArchivedTasks should ignore still-active matches"
        }
    }
}
