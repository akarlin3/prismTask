package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Smoke tests for the Settings screen. These verify the settings entry
 * is reachable from every main tab and that opening it doesn't crash the
 * harness. Individual settings sections have their own ViewModel-level
 * unit coverage.
 */
@HiltAndroidTest
class SettingsSmokeTest : SmokeTestBase() {
    @Test
    fun settingsGear_isReachableFromTodayTab() {
        composeRule.waitForIdle()
        findByContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsGear_isReachableFromTasksTab() {
        composeRule.waitForIdle()
        findByText("Tasks").performClick()
        composeRule.waitForIdle()
        findByContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsGear_isReachableFromHabitsTab() {
        composeRule.waitForIdle()
        findByText("Habits").performClick()
        composeRule.waitForIdle()
        findByContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsGear_clickDoesNotCrashApp() {
        composeRule.waitForIdle()
        findByContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        // If we got this far without an exception, the Settings route at
        // least composes for an initial frame.
    }
}
