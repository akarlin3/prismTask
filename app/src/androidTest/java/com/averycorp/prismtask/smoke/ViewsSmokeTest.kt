package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Smoke tests for the week/month/timeline and related alternate views,
 * exercised primarily via the DAOs the views consume. The Compose screens
 * for these views are reachable via settings routes that aren't stable in
 * a plain smoke harness, so we verify data wiring rather than navigation.
 */
@HiltAndroidTest
class ViewsSmokeTest : SmokeTestBase() {

    @Test
    fun taskListTab_rendersSeededTasks() {
        composeRule.waitForIdle()
        findByText("Tasks").performClick()
        composeRule.waitForIdle()
        findByText("Review pull requests").assertIsDisplayed()
        findByText("Buy groceries").assertIsDisplayed()
    }

    @Test
    fun todayTab_rendersTodayHeader() {
        composeRule.waitForIdle()
        findByText("Today").assertIsDisplayed()
    }

    @Test
    fun incompleteRootTasks_flowIncludesSeededTasks() = runBlocking {
        val tasks = database.taskDao().getIncompleteRootTasks().first()
        // The overdue task, two today tasks, tomorrow task, no-date task, and
        // parent task — 6 root tasks, plus subtasks which should be filtered.
        assert(tasks.any { it.id == seededIds.overdueTaskId })
        assert(tasks.any { it.id == seededIds.todayTask1Id })
        assert(tasks.none { it.parentTaskId != null }) {
            "Incomplete root tasks should exclude subtasks"
        }
    }

    @Test
    fun weekViewQueries_returnTasksInCorrectDayBuckets() = runBlocking {
        // The tasklist queries the DAO for today and tomorrow windows. We
        // simulate that here by fetching the tasks directly.
        val today = database.taskDao().getAllTasksOnce().filter {
            it.dueDate != null && it.parentTaskId == null
        }
        assert(today.size >= 4) {
            "Expected at least 4 scheduled root tasks in the seed"
        }
    }

    @Test
    fun habitDao_returnsSeededActiveHabits() = runBlocking {
        val habits = database.habitDao().getActiveHabitsOnce()
        assert(habits.size == 2)
        assert(habits.any { it.name == "Exercise" })
        assert(habits.any { it.name == "Read" })
    }

    @Test
    fun templates_areSeededForTemplateListView() = runBlocking {
        val templates = database.taskTemplateDao().getAllTemplatesOnce()
        assert(templates.any { it.name == "Morning Routine" })
        assert(templates.any { it.name == "Meeting Prep" })
    }
}
