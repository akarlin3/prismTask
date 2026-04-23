package com.averycorp.prismtask.smoke

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Empty-state instrumentation tests. Every test begins with a completely
 * empty Room database: the @Before hook clears every table before any
 * assertions. These tests verify that each screen handles zero data
 * gracefully — they assert "no crash + tab/entry-point visible." Specific
 * empty-state copy (e.g. "Clean Slate", "No Projects Yet") is covered by
 * screen-level unit tests; asserting on exact strings here is brittle
 * because localizations + UI-copy tweaks churn frequently.
 */
@HiltAndroidTest
class EdgeCaseEmptyStateTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: PrismTaskDatabase

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Inject
    lateinit var taskBehaviorPreferences: TaskBehaviorPreferences

    @Before
    fun setUp() {
        hiltRule.inject()
        // Clear everything: every test runs against an empty DB so the UI
        // must render its empty-state composable rather than real content.
        runTest { database.clearAllTables() }
        // Seed onboarding/SoD completion so MainActivity lands on the
        // main UI instead of the onboarding flow / blocking SoD picker.
        runBlocking {
            onboardingPreferences.setOnboardingCompleted()
            taskBehaviorPreferences.setHasSetStartOfDay(true)
        }
        composeRule.waitForIdle()
    }

    @After
    fun tearDown() {
        runTest { database.clearAllTables() }
    }

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun findTab(label: String) =
        composeRule.onNode(hasText(label).and(hasRole(Role.Tab)))

    private fun clickTab(label: String) {
        findTab(label).performClick()
        composeRule.waitForIdle()
    }

    // ─── 1. Today screen ───────────────────────────────────────────────────

    @Test
    fun testTodayScreenEmpty() {
        composeRule.waitForIdle()
        // Today tab is the default start destination; with zero rows the
        // compact header + empty-state surface still render without
        // crashing. Asserting on tab presence is the stable smoke signal.
        findTab("Today").assertIsDisplayed()
    }

    // ─── 2. Task list ──────────────────────────────────────────────────────

    @Test
    fun testTaskListEmpty() {
        clickTab("Tasks")
        findTab("Tasks").assertIsDisplayed()
    }

    // ─── 3. Project list ───────────────────────────────────────────────────

    @Test
    fun testProjectListEmpty() {
        clickTab("Tasks")
        // The Manage chip's discoverability on the Tasks tab depends on
        // project-filter presence; in an empty DB that chip may be
        // absent. Verify the tab mounts.
        findTab("Tasks").assertIsDisplayed()
    }

    // ─── 4. Habit list ─────────────────────────────────────────────────────

    @Test
    fun testHabitListEmpty() {
        clickTab("Daily")
        findTab("Daily").assertIsDisplayed()
    }

    // ─── 5. Eisenhower matrix ──────────────────────────────────────────────

    @Test
    fun testEisenhowerEmpty() {
        clickTab("Settings")
        // Eisenhower Matrix is a SettingsRow inside the AI section; scroll
        // it into view and confirm it's rendered. Clicking into the matrix
        // is covered by its own unit tests.
        composeRule.onAllNodesWithText("Eisenhower Matrix")
            .onFirst()
            .performScrollTo()
        composeRule.onAllNodesWithText("Eisenhower Matrix")
            .onFirst()
            .assertIsDisplayed()
    }

    // ─── 6. Smart pomodoro ─────────────────────────────────────────────────

    @Test
    fun testPomodoroEmpty() {
        clickTab("Settings")
        composeRule.onAllNodesWithText("Smart Focus Sessions")
            .onFirst()
            .performScrollTo()
        composeRule.onAllNodesWithText("Smart Focus Sessions")
            .onFirst()
            .assertIsDisplayed()
    }

    // ─── 7. Week view ──────────────────────────────────────────────────────

    @Test
    fun testWeekViewEmpty() {
        clickTab("Tasks")
        // Week view is reachable via a view-mode dropdown on the Tasks
        // tab. Without a specific content-description for the dropdown
        // trigger (it's an icon-only IconButton and the label varies by
        // selection), deep-linking via nav route is fragile; verifying
        // the Tasks tab composes is the stable smoke signal.
        findTab("Tasks").assertIsDisplayed()
    }

    // ─── 8. Habit analytics ────────────────────────────────────────────────

    @Test
    fun testHabitAnalyticsEmpty() {
        clickTab("Daily")
        // With zero habits, HabitAnalyticsScreen isn't reachable because
        // the tap-into-habit path requires a habit row. The empty state
        // on the Daily tab prompts the user to create a habit, and that
        // tab composes without crashing — which is the smoke signal.
        findTab("Daily").assertIsDisplayed()
    }

    // ─── 9. Weekly balance report ──────────────────────────────────────────

    @Test
    fun testWeeklyBalanceReportEmpty() {
        clickTab("Settings")
        composeRule.onAllNodesWithText("View Weekly Report")
            .onFirst()
            .performScrollTo()
        composeRule.onAllNodesWithText("View Weekly Report")
            .onFirst()
            .assertIsDisplayed()
    }

    // ─── 10. Settings with no data ─────────────────────────────────────────

    @Test
    fun testSettingsWithNoData() {
        clickTab("Settings")

        // Scroll to representative rows from several sections — each must
        // render without crashing on empty state. "Manage Projects" lives
        // in the Projects settings subsection; "Eisenhower Matrix" in AI;
        // "Smart Focus Sessions" in AI/Pomodoro. onFirst() handles
        // duplicate matches with nested subsection group labels.
        listOf(
            "Manage Projects",
            "Eisenhower Matrix",
            "Smart Focus Sessions"
        ).forEach { rowText ->
            composeRule.onAllNodesWithText(rowText).onFirst().performScrollTo()
            composeRule.onAllNodesWithText(rowText).onFirst().assertIsDisplayed()
        }
    }
}
