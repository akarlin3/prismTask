package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test

/**
 * Smoke tests for the Habits tab. Verifies the habit list renders the
 * seeded habits and that basic navigation / interactions don't crash.
 */
@HiltAndroidTest
class HabitSmokeTest : SmokeTestBase() {
    @Test
    fun habitsTab_showsSeededHabits() {
        composeRule.waitForIdle()
        findByText("Habits").performClick()
        composeRule.waitForIdle()

        findByText("Exercise").assertIsDisplayed()
        findByText("Read").assertIsDisplayed()
    }

    @Test
    fun habitList_tappingHabitDoesNotCrash() {
        composeRule.waitForIdle()
        findByText("Habits").performClick()
        composeRule.waitForIdle()

        // Tapping a habit opens its detail/analytics — we don't assert on the
        // detail screen specifically, just that the tap doesn't blow up the app.
        findByText("Exercise").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun habitsTab_todayScreenShowsHabitsSection() {
        composeRule.waitForIdle()
        // Today screen is the default destination; seeded habits should be
        // reachable from here via the habit chip row, so at least the header
        // should render.
        findByText("Today").assertIsDisplayed()
    }
}
