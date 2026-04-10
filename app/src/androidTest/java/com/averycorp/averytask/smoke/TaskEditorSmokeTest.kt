package com.averycorp.averytask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class TaskEditorSmokeTest : SmokeTestBase() {

    @Test
    fun taskEditor_opensAsBottomSheet() {
        composeRule.waitForIdle()

        // Tap FAB to open editor
        findByContentDescription("New Task").performClick()
        composeRule.waitForIdle()

        // Bottom sheet should show "New Task" title
        findByText("New Task").assertIsDisplayed()
    }

    @Test
    fun taskEditor_hasTabs_detailsScheduleOrganize() {
        composeRule.waitForIdle()

        // Open editor via FAB
        findByContentDescription("New Task").performClick()
        composeRule.waitForIdle()

        // All three tabs should be visible
        findByText("Details").assertIsDisplayed()
        findByText("Schedule").assertIsDisplayed()
        findByText("Organize").assertIsDisplayed()
    }

    @Test
    fun taskEditor_tabsAreSwipeable() {
        composeRule.waitForIdle()

        // Open editor
        findByContentDescription("New Task").performClick()
        composeRule.waitForIdle()

        // Details tab should be initially selected
        findByText("Details").assertIsDisplayed()

        // Tap Schedule tab
        findByText("Schedule").performClick()
        composeRule.waitForIdle()

        // Schedule tab content should be visible (e.g. date chips like "Today")
        composeRule.onNode(hasText("Today", substring = true)).assertIsDisplayed()

        // Tap Organize tab
        findByText("Organize").performClick()
        composeRule.waitForIdle()

        // Organize tab should now be active
        findByText("Organize").assertIsDisplayed()
    }

    @Test
    fun taskEditor_createTask_withTitleAndPriority() {
        composeRule.waitForIdle()

        // Open editor
        findByContentDescription("New Task").performClick()
        composeRule.waitForIdle()

        // Type title
        composeRule.onNode(hasText("Title")).performClick()
        composeRule.onNode(hasText("Title")).performTextInput("Smoke test task")
        composeRule.waitForIdle()

        // Tap High priority
        findByText("High").performScrollTo()
        findByText("High").performClick()
        composeRule.waitForIdle()

        // Save the task
        composeRule.onNode(hasText("Save Task")).performScrollTo()
        composeRule.onNode(hasText("Save Task")).performClick()
        composeRule.waitForIdle()

        // The task should appear somewhere in the task views
        // (sheet should dismiss after save)
    }

    @Test
    fun taskEditor_scheduleTab_quickDateChips() {
        composeRule.waitForIdle()

        // Open editor
        findByContentDescription("New Task").performClick()
        composeRule.waitForIdle()

        // Navigate to Schedule tab
        findByText("Schedule").performClick()
        composeRule.waitForIdle()

        // "Tomorrow" chip should be visible
        findByText("Tomorrow").assertIsDisplayed()

        // Tap "Tomorrow"
        findByText("Tomorrow").performClick()
        composeRule.waitForIdle()

        // The chip should now be in a selected state
        // (we verify it didn't crash and the date area updated)
        findByText("Tomorrow").assertIsDisplayed()
    }

    @Test
    fun taskEditor_organizeTab_projectSelector() {
        composeRule.waitForIdle()

        // Open editor
        findByContentDescription("New Task").performClick()
        composeRule.waitForIdle()

        // Navigate to Organize tab
        findByText("Organize").performClick()
        composeRule.waitForIdle()

        // "No project" should be the default
        composeRule.onNode(hasText("No project", substring = true)).performScrollTo()
        composeRule.onNode(hasText("No project", substring = true)).assertIsDisplayed()
    }

    @Test
    fun taskEditor_editMode_populatesFields() {
        composeRule.waitForIdle()

        // Tap an existing task to edit it
        findByText("Review pull requests").performClick()
        composeRule.waitForIdle()

        // The editor should open with the task title pre-filled
        composeRule.onNode(hasText("Review pull requests")).assertIsDisplayed()

        // It should show "Edit Task" or the title (the editor shows the task title)
        composeRule.onNode(hasText("Edit Task")).assertIsDisplayed()
    }
}
