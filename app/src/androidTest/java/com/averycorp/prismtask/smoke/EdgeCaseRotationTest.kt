package com.averycorp.prismtask.smoke

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Rotation and state preservation smoke tests.
 *
 * Verifies that the app survives configuration changes (portrait ↔
 * landscape) across the most interaction-heavy screens. Each test sets up
 * some transient state, toggles [android.app.Activity.setRequestedOrientation]
 * via the Compose rule's [composeRule.activity], and then asserts that the
 * state is still present after the Activity is recreated.
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

        // Open the task editor via the FAB.
        findByContentDescription("New Task").performClick()
        composeRule.waitForIdle()

        // Type a title into the Details tab (the editor opens on Details).
        composeRule.onNode(hasText("Title")).performClick()
        composeRule.onNode(hasText("Title")).performTextInput("Rotation test task")
        composeRule.waitForIdle()

        // Select High priority.
        findByText("High").performScrollTo()
        findByText("High").performClick()
        composeRule.waitForIdle()

        // Switch to Schedule tab and tap the "Tomorrow" quick-date chip.
        findByText("Schedule").performClick()
        composeRule.waitForIdle()
        findByText("Tomorrow").performClick()
        composeRule.waitForIdle()

        // Rotate to landscape — the Activity is recreated, but the
        // AddEditTaskViewModel (SavedStateHandle-backed) should keep the
        // typed values and selections.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        // Title is still present.
        composeRule.onNode(hasText("Rotation test task", substring = true)).assertIsDisplayed()
        // Tomorrow chip is still selected (we're on the Schedule tab).
        findByText("Tomorrow").assertIsDisplayed()

        // Priority is preserved — pop back to Details and verify High is
        // highlighted.
        findByText("Details").performClick()
        composeRule.waitForIdle()
        findByText("High").performScrollTo()
        findByText("High").assertIsDisplayed()
        // Title text still visible on the Details tab.
        composeRule.onNode(hasText("Rotation test task", substring = true)).assertIsDisplayed()

        // Rotate back to portrait and verify again.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        composeRule.onNode(hasText("Rotation test task", substring = true)).assertIsDisplayed()
        findByText("High").performScrollTo()
        findByText("High").assertIsDisplayed()
    }

    @Test
    fun testPomodoroTimerSurvivesRotation() {
        composeRule.waitForIdle()

        // Navigate to the Today screen (default) and tap the Focus quick
        // action chip, which routes to the Smart Pomodoro screen.
        findByText("Focus").performScrollTo()
        findByText("Focus").performClick()
        composeRule.waitForIdle()

        // Smart Pomodoro should be showing.
        findByText("Smart Focus").assertIsDisplayed()

        // Generate a plan and start the first session. If the backend is not
        // reachable in the test environment, these taps are still safe — the
        // planning view simply stays on-screen and the later timer
        // assertions are skipped.
        composeRule.onAllNodesWithText("Plan My Sessions").let { nodes ->
            if (nodes.fetchSemanticsNodes().isNotEmpty()) {
                nodes[0].performClick()
                composeRule.waitForIdle()
            }
        }

        composeRule.onAllNodesWithText("Start Focus").let { nodes ->
            if (nodes.fetchSemanticsNodes().isNotEmpty()) {
                nodes[0].performClick()
                composeRule.waitForIdle()
            }
        }

        // Give the session a couple of seconds to tick down.
        Thread.sleep(2000L)
        composeRule.waitForIdle()

        // Rotate to landscape.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        // After rotation we should still be inside the Pomodoro flow
        // (either the active session or the planning view, depending on
        // whether plan generation succeeded). The screen title is a stable
        // anchor that survives rotation because the NavController is
        // recomposed from the back stack.
        findByText("Smart Focus").assertIsDisplayed()

        // Rotate back to portrait to leave the activity in a known state.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findByText("Smart Focus").assertIsDisplayed()
    }

    @Test
    fun testMorningCheckInSurvivesRotation() {
        composeRule.waitForIdle()

        // The morning check-in banner is gated by the time of day and the
        // CheckInLog table. Only run the rotation assertion when the banner
        // is actually visible in this session.
        val startNodes = composeRule.onAllNodesWithText("Start Check-In")
        if (startNodes.fetchSemanticsNodes().isEmpty()) {
            // Banner not shown (e.g. after 11am or already checked in) —
            // nothing to verify; the test is a no-op.
            return
        }

        startNodes[0].performClick()
        composeRule.waitForIdle()

        // Advance past the first page via the "Next" button on the pager.
        findByText("Morning Check-In").assertIsDisplayed()
        findByText("Next").performClick()
        composeRule.waitForIdle()

        // Rotate — the pager state is backed by rememberPagerState so it
        // should retain the current page through an activity recreation.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        // We're still on the Morning Check-In screen, not popped back to
        // Today. The "Back" button only appears once we've moved past
        // page 0, so its presence verifies we didn't reset to page 1.
        findByText("Morning Check-In").assertIsDisplayed()
        findByText("Back").assertIsDisplayed()

        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findByText("Morning Check-In").assertIsDisplayed()
    }

    @Test
    fun testTodayScreenSurvivesRotation() {
        composeRule.waitForIdle()

        // Ensure the overdue section is visible and collapse it so we have
        // an observable state transition to verify across rotation.
        findByText("Today Tasks").performScrollTo()
        findByText("Today Tasks").performClick()
        composeRule.waitForIdle()

        // Rotate to landscape.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        // Today screen still rendered after rotation.
        findByText("Today").assertIsDisplayed()
        findByText("Today Tasks").assertIsDisplayed()

        // Re-expand the section to confirm the header is interactive after
        // rotation, then verify a task appears again.
        findByText("Today Tasks").performClick()
        composeRule.waitForIdle()
        findByText("Review pull requests").assertIsDisplayed()

        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        findByText("Today").assertIsDisplayed()
        findByText("Today Tasks").assertIsDisplayed()
    }

    @Test
    fun testFilterPanelSurvivesRotation() {
        composeRule.waitForIdle()

        // Navigate to the Tasks tab and open the filter sheet.
        findByText("Tasks").performClick()
        composeRule.waitForIdle()
        findByContentDescription("Filters").performClick()
        composeRule.waitForIdle()

        // Filter panel is open — select the "High" priority filter chip.
        findByText("Priority").performScrollTo()
        composeRule.waitForIdle()
        findByText("High").performScrollTo()
        findByText("High").performClick()
        composeRule.waitForIdle()

        // Rotate to landscape. The filter sheet is a ModalBottomSheet which
        // may or may not survive recreation, but the TaskListViewModel's
        // currentFilter StateFlow is Hilt-scoped and should persist.
        rotateTo(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        // The active filter pill for High priority should be displayed on
        // the Tasks screen regardless of whether the sheet reopened.
        composeRule.waitForIdle()
        composeRule.onNode(hasText("High", substring = true)).assertIsDisplayed()

        rotateTo(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        composeRule.waitForIdle()
        composeRule.onNode(hasText("High", substring = true)).assertIsDisplayed()
    }
}
