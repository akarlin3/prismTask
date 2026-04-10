package com.averycorp.averytask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class NavigationSmokeTest : SmokeTestBase() {

    @Test
    fun bottomNav_hasExpectedTabs() {
        composeRule.waitForIdle()

        // Bottom nav should have Today, Tasks, Habits, Timer tabs
        findByText("Today").assertIsDisplayed()
        findByText("Tasks").assertIsDisplayed()
        findByText("Habits").assertIsDisplayed()
        findByText("Timer").assertIsDisplayed()
    }

    @Test
    fun bottomNav_switchesBetweenScreens() {
        composeRule.waitForIdle()

        // Tap "Tasks" tab — task list should appear
        findByText("Tasks").performClick()
        composeRule.waitForIdle()
        // Task list shows our seeded tasks
        findByText("Review pull requests").assertIsDisplayed()

        // Tap "Habits" tab — habit list should appear
        findByText("Habits").performClick()
        composeRule.waitForIdle()
        findByText("Exercise").assertIsDisplayed()

        // Tap "Today" tab — today screen should reappear
        findByText("Today").performClick()
        composeRule.waitForIdle()
        // Today header should be visible
        findByText("Today").assertIsDisplayed()
    }

    @Test
    fun settingsGear_isOnMainScreens() {
        composeRule.waitForIdle()

        // Settings gear should be on Today screen
        findByContentDescription("Settings").assertIsDisplayed()

        // Navigate to Tasks tab
        findByText("Tasks").performClick()
        composeRule.waitForIdle()
        findByContentDescription("Settings").assertIsDisplayed()

        // Navigate to Habits tab
        findByText("Habits").performClick()
        composeRule.waitForIdle()
        findByContentDescription("Settings").assertIsDisplayed()
    }
}
