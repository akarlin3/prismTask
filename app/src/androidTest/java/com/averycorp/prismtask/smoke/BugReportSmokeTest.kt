package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Smoke tests for the Bug Report feature. In v1.4 the Bug Report screen
 * is reached either via shake-to-report (Shake feature) or via the debug
 * floating feedback button (BuildConfig.DEBUG only — present in all
 * androidTest runs). `HelpFeedbackSection` in Settings still defines a
 * "Report a Bug" row but it's not wired into the main SettingsScreen —
 * the only integrated entry points are the ones listed above.
 */
@HiltAndroidTest
class BugReportSmokeTest : SmokeTestBase() {

    @Test
    fun floatingFeedbackButton_isDisplayedOnMainScreens() {
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Report a Bug").onFirst().assertIsDisplayed()
    }

    @Test
    fun floatingFeedbackButton_opensBugReportScreen() {
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Report a Bug")
            .onFirst()
            .performClick()
        composeRule.waitForIdle()

        // BugReportScreen renders with the title node — but since "Report a
        // Bug" text can also match the FAB's contentDescription, scope to
        // the onFirst() which is the top-of-stack composition.
        composeRule.onAllNodesWithText("Report a Bug").onFirst().assertIsDisplayed()
    }

    @Test
    fun bugReportScreen_rendersDescriptionField() {
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Report a Bug")
            .onFirst()
            .performClick()
        composeRule.waitForIdle()

        // The description prompt is a TextField placeholder; its presence
        // confirms the form composed. BugReportViewModel unit tests cover
        // the validator (min-length, debouncing) which is timing-sensitive
        // on real emulators.
        composeRule.onAllNodesWithText("Describe", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun bugReportScreen_opensWithoutCrashing() {
        composeRule.waitForIdle()

        composeRule.onAllNodesWithContentDescription("Report a Bug")
            .onFirst()
            .performClick()
        composeRule.waitForIdle()
        // Smoke intent: reaching the bug-report screen doesn't throw.
    }
}
