package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

@HiltAndroidTest
class TemplatesSmokeTest : SmokeTestBase() {

    private fun navigateToTemplates() {
        composeRule.waitForIdle()

        // Navigate to Settings first
        findByContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        // Then navigate to Templates from Settings
        // or use direct navigation if available
        findByText("Templates").performScrollTo()
        findByText("Templates").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun templates_seededTemplatesExist() {
        navigateToTemplates()

        // Verify our seeded templates are present
        findByText("Morning Routine").assertIsDisplayed()
        findByText("Meeting Prep").performScrollTo()
        findByText("Meeting Prep").assertIsDisplayed()
    }

    @Test
    fun templates_quickUse_createsTask() {
        navigateToTemplates()

        // Tap a template card to quick-use it
        findByText("Morning Routine").performClick()
        composeRule.waitForIdle()

        // Snackbar should confirm task creation
        composeRule.onNode(
            hasText("Created", substring = true)
        ).assertIsDisplayed()
    }

    @Test
    fun templates_newTemplateButton_isVisible() {
        navigateToTemplates()

        // FAB for creating new template should be visible
        findByContentDescription("New Template").assertIsDisplayed()
    }

    @Test
    fun templates_categoryFilter_isVisible() {
        navigateToTemplates()

        // Category filter chips should be available
        // "All" is always the first filter option
        findByText("All").assertIsDisplayed()
    }
}
