package com.averycorp.averytask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidTest
class TodayScreenSmokeTest : SmokeTestBase() {

    @Test
    fun todayScreen_launches_withCompactHeader() {
        composeRule.waitForIdle()

        // Compact header shows "Today" title
        findByText("Today").assertIsDisplayed()

        // Date label is visible (format: "Thursday, April 10")
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        val todayDateStr = dateFormat.format(Date())
        findByText(todayDateStr).assertIsDisplayed()

        // Progress counter is visible (e.g. "0/5")
        // We check that a node with "/" exists in the header area
        composeRule.onNode(hasText("/", substring = true)).assertIsDisplayed()
    }

    @Test
    fun todayScreen_sections_collapseAndExpand() {
        composeRule.waitForIdle()

        // "Today Tasks" section should be visible since we have tasks due today
        findByText("Today Tasks").assertIsDisplayed()

        // Find and click the section header to collapse it
        findByText("Today Tasks").performClick()
        composeRule.waitForIdle()

        // After collapsing, today tasks should still have the header but
        // individual task titles may be hidden. Click again to expand.
        findByText("Today Tasks").performClick()
        composeRule.waitForIdle()

        // A task due today should be visible again
        findByText("Review pull requests").assertIsDisplayed()
    }

    @Test
    fun todayScreen_overdueSection_isVisible() {
        composeRule.waitForIdle()

        // We seeded an overdue task ("Overdue report"), so the overdue section
        // should appear with a count badge.
        findByText("Overdue report").assertIsDisplayed()
    }

    @Test
    fun todayScreen_habits_showInSection() {
        composeRule.waitForIdle()

        // The Habits section should be visible with our seeded habits
        findByText("Habits").assertIsDisplayed()
    }

    @Test
    fun todayScreen_quickAddBar_isVisible() {
        composeRule.waitForIdle()

        // The quick-add bar is rendered as the bottomBar and should show
        // its prompt text when collapsed
        composeRule.onNode(
            hasText("Add task", substring = true)
        ).assertIsDisplayed()
    }

    @Test
    fun todayScreen_fabVisible() {
        composeRule.waitForIdle()

        // FAB with "New Task" content description should be visible
        findByContentDescription("New Task").assertIsDisplayed()
    }

    @Test
    fun todayScreen_swipeToComplete_showsUndo() {
        composeRule.waitForIdle()

        // Find a today task and swipe right to complete
        val taskNode = findByText("Review pull requests")
        taskNode.performScrollTo()
        taskNode.performTouchInput { swipeRight() }

        composeRule.waitForIdle()

        // Undo snackbar should appear
        composeRule.onNode(hasText("Undo", substring = true)).assertIsDisplayed()
    }

    @Test
    fun todayScreen_settingsGearIcon_navigatesToSettings() {
        composeRule.waitForIdle()

        // Tap the settings gear icon
        findByContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        // Settings screen should be visible
        findByText("Settings").assertIsDisplayed()
    }
}
