package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class BugReportSmokeTest : SmokeTestBase() {
    @Test
    fun settingsHelpSection_showsReportBugOption() {
        composeRule.waitForIdle()

        // Navigate to Settings tab
        findByText("Settings").performClick()
        composeRule.waitForIdle()

        // Scroll to find Help & Feedback section
        findByText("Report a Bug").assertIsDisplayed()
    }

    @Test
    fun bugReportScreen_opensFromSettings() {
        composeRule.waitForIdle()

        findByText("Settings").performClick()
        composeRule.waitForIdle()

        findByText("Report a Bug").performClick()
        composeRule.waitForIdle()

        // Bug report screen should be displayed
        findByText("Report a Bug").assertIsDisplayed()
        findByText("Help us improve PrismTask").assertIsDisplayed()
    }

    @Test
    fun bugReportScreen_sendDisabledUntilDescriptionValid() {
        composeRule.waitForIdle()

        findByText("Settings").performClick()
        composeRule.waitForIdle()

        findByText("Report a Bug").performClick()
        composeRule.waitForIdle()

        // Send button should be disabled initially
        composeRule.onNodeWithText("Send Report").assertIsNotEnabled()

        // Type a short description (< 10 chars)
        composeRule.onNodeWithText("Describe what went wrong...").performTextInput("Short")
        composeRule.onNodeWithText("Send Report").assertIsNotEnabled()

        // Type enough to meet minimum
        composeRule.onNodeWithText("Short").performTextInput(" enough text")
        composeRule.onNodeWithText("Send Report").assertIsEnabled()
    }

    @Test
    fun bugReportScreen_autoContextExpandable() {
        composeRule.waitForIdle()

        findByText("Settings").performClick()
        composeRule.waitForIdle()

        findByText("Report a Bug").performClick()
        composeRule.waitForIdle()

        // Auto-collected context section should be visible
        findByText("Auto-Collected Context").assertIsDisplayed()

        // Tap to expand
        findByText("Auto-Collected Context").performClick()
        composeRule.waitForIdle()

        // Device info should now be visible
        findByText("Device").assertIsDisplayed()
    }
}
