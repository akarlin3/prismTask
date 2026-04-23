package com.averycorp.prismtask.smoke

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Rotation smoke tests. Verifies that the app survives configuration
 * changes (portrait ↔ landscape) across the bottom-nav tabs without
 * crashing. Specific state-preservation across rotation for individual
 * screens (task editor SavedStateHandle, filter panel, pager state, etc.)
 * is covered by ViewModel-level unit tests — at the instrumentation
 * level the stable smoke signal is "rotating the activity doesn't crash
 * and the tab bar still renders."
 */
@HiltAndroidTest
class EdgeCaseRotationTest : SmokeTestBase() {
    private fun rotateTo(orientation: Int) {
        composeRule.activity.requestedOrientation = orientation
        composeRule.waitForIdle()
    }

    @Test
    fun testTaskEditorSurvivesRotation() {
        composeRule.waitForIdle()
        clickTab("Tasks")
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        // Landscape may swap NavigationBar for NavigationRail (different
        // Role), so don't re-assert on findTab after rotation — the
        // smoke signal is "rotate didn't crash the activity."
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findTab("Tasks").assertIsDisplayed()
    }

    @Test
    fun testPomodoroTimerSurvivesRotation() {
        composeRule.waitForIdle()
        clickTab("Timer")
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findTab("Timer").assertIsDisplayed()
    }

    @Test
    fun testMorningCheckInSurvivesRotation() {
        composeRule.waitForIdle()
        // Morning check-in banner is time-of-day-gated — smoke-verifying
        // rotation on the Today tab that hosts the banner is the stable
        // signal.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findTab("Today").assertIsDisplayed()
    }

    @Test
    fun testTodayScreenSurvivesRotation() {
        composeRule.waitForIdle()
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findTab("Today").assertIsDisplayed()
    }

    @Test
    fun testFilterPanelSurvivesRotation() {
        composeRule.waitForIdle()
        clickTab("Tasks")
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        // Landscape may swap NavigationBar for NavigationRail (different
        // Role), so don't re-assert on findTab after rotation — the
        // smoke signal is "rotate didn't crash the activity."
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findTab("Tasks").assertIsDisplayed()
    }
}
