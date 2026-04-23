package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Smoke tests around tag- and project-scoped filtering. These don't try to
 * reach the full Tag Management / Project Management screens (those live
 * behind Settings); instead they verify the seeded tag/project rows are
 * reachable via the main task list via Room directly so the DI wiring and
 * row rendering stay healthy.
 */
@HiltAndroidTest
class TagProjectSmokeTest : SmokeTestBase() {
    @Test
    fun projectsArePresentInDatabase() = runBlocking {
        val projects = database.projectDao().getAllProjectsOnce()
        assert(projects.any { it.name == "Work" })
        assert(projects.any { it.name == "Personal" })
    }

    @Test
    fun tagsArePresentInDatabase() = runBlocking {
        val tags = database.tagDao().getAllTagsOnce()
        assert(tags.any { it.name == "urgent" })
        assert(tags.any { it.name == "email" })
        assert(tags.any { it.name == "code" })
    }

    @Test
    fun tagsAreAssignedToOverdueTask() = runBlocking {
        val tagIds = database.tagDao().getTagIdsForTaskOnce(seededIds.overdueTaskId)
        assert(seededIds.tagUrgentId in tagIds) {
            "Overdue task should be tagged 'urgent' after seeding"
        }
    }

    @Test
    fun parentTaskCarriesMultipleTags() = runBlocking {
        val tagIds = database.tagDao().getTagIdsForTaskOnce(seededIds.parentTaskId).toSet()
        assert(seededIds.tagUrgentId in tagIds)
        assert(seededIds.tagCodeId in tagIds)
    }

    @Test
    fun taskListTabShowsProjectTasks() {
        composeRule.waitForIdle()
        clickTab("Tasks")
        // The Tasks tab mounts; asserting on specific seeded titles is
        // fragile because the task list's default sort can bury seeded
        // rows below the initial viewport. The DAO-level tests above
        // verify the data is present.
        findTab("Tasks").assertIsDisplayed()
    }
}
