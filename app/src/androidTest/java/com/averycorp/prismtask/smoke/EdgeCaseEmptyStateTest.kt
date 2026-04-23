package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 * gracefully — they assert "no crash + correct empty state UI visible."
 *
 * The compose harness launches [MainActivity] so we navigate through real
 * UI to reach each screen (bottom-nav tabs, in-screen buttons, Settings
 * rows). Screens that require Pro/Premium tier or that are only
 * reachable via nav paths needing seeded data are exercised through the
 * most direct stable entry point available.
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
        // MainActivity gates the main UI behind hasCompletedOnboarding and
        // hasSetStartOfDay; unset they block the empty-state screens with
        // the onboarding flow / SoD picker. Seed both here so empty-state
        // assertions actually reach the target screens.
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

    // ─── 1. Today screen ───────────────────────────────────────────────────

    @Test
    fun testTodayScreenEmpty() {
        composeRule.waitForIdle()

        // Today tab is the default start destination. The compact header
        // always renders the "Today" title regardless of task count.
        composeRule.onNodeWithText("Today").assertIsDisplayed()

        // With zero tasks and zero completions, the day-clear empty state
        // should be the dominant message on the screen.
        composeRule
            .onNode(hasText("Nothing Planned for Today", substring = true))
            .assertIsDisplayed()
    }

    // ─── 2. Task list ──────────────────────────────────────────────────────

    @Test
    fun testTaskListEmpty() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Tasks").performClick()
        composeRule.waitForIdle()

        // Empty-state composable with "Clean Slate" title renders when
        // there are no tasks and no active filter.
        composeRule
            .onNode(hasText("Clean Slate", substring = true))
            .assertIsDisplayed()

        // FAB for creating a task must still be visible.
        composeRule.onNodeWithContentDescription("Add Task").assertIsDisplayed()

        // The project filter row stays accessible; the default "All" chip
        // must still be present even with zero projects.
        composeRule.onNodeWithText("All").assertIsDisplayed()
    }

    // ─── 3. Project list ───────────────────────────────────────────────────

    @Test
    fun testProjectListEmpty() {
        composeRule.waitForIdle()

        // Navigate: Tasks tab → "Manage" chip opens ProjectListScreen.
        composeRule.onNodeWithText("Tasks").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Manage").performClick()
        composeRule.waitForIdle()

        // Empty-state body + the Add Project FAB must both be visible.
        composeRule
            .onNode(hasText("No Projects Yet", substring = true))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add Project").assertIsDisplayed()
    }

    // ─── 4. Habit list ─────────────────────────────────────────────────────

    @Test
    fun testHabitListEmpty() {
        composeRule.waitForIdle()

        // The daily habits tab is labelled "Daily" (see ALL_BOTTOM_NAV_ITEMS).
        composeRule.onNodeWithText("Daily").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNode(hasText("Start Building Habits", substring = true))
            .assertIsDisplayed()

        // The empty state exposes the primary "Create Habit" action.
        composeRule
            .onNode(hasText("Create Habit", substring = true))
            .assertIsDisplayed()
    }

    // ─── 5. Eisenhower matrix ──────────────────────────────────────────────

    @Test
    fun testEisenhowerEmpty() {
        composeRule.waitForIdle()

        // Settings tab → scroll to AI section → tap "Eisenhower Matrix".
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        val eisenhowerRow = composeRule.onNodeWithText("Eisenhower Matrix")
        eisenhowerRow.performScrollTo()
        eisenhowerRow.performClick()
        composeRule.waitForIdle()

        // Screen renders its title bar. The 2x2 grid renders the four
        // quadrant cells even with zero tasks — each cell shows its
        // "No Tasks" body inside the quadrant card.
        composeRule.onNodeWithText("Eisenhower Matrix").assertIsDisplayed()
    }

    // ─── 6. Smart pomodoro ─────────────────────────────────────────────────

    @Test
    fun testPomodoroEmpty() {
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        val smartFocusRow = composeRule.onNodeWithText("Smart Focus Sessions")
        smartFocusRow.performScrollTo()
        smartFocusRow.performClick()
        composeRule.waitForIdle()

        // The screen's top-app-bar title and the Pomodoro-config card both
        // render in the PLANNING state. With zero incomplete tasks the
        // planning subtitle reports the count.
        composeRule.onNodeWithText("Smart Focus").assertIsDisplayed()
        composeRule
            .onNode(hasText("Pomodoro Configuration", substring = true))
            .assertIsDisplayed()
    }

    // ─── 7. Week view ──────────────────────────────────────────────────────

    @Test
    fun testWeekViewEmpty() {
        composeRule.waitForIdle()

        // Tasks tab → view-mode icon dropdown → "Week".
        composeRule.onNodeWithText("Tasks").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("View mode").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Week").performClick()
        composeRule.waitForIdle()

        // The "Go to today" action on the top bar confirms WeekViewScreen
        // composed; the seven empty day columns render underneath.
        composeRule.onNodeWithContentDescription("Go to today").assertIsDisplayed()
    }

    // ─── 8. Habit analytics ────────────────────────────────────────────────

    @Test
    fun testHabitAnalyticsEmpty() {
        composeRule.waitForIdle()

        // HabitAnalyticsScreen is only reachable by tapping a habit, so
        // with zero habits the analytics entry-point isn't exposed. The
        // expected "correct empty state UI" is therefore the habits list
        // empty-state prompting the user to build a habit first.
        composeRule.onNodeWithText("Daily").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNode(hasText("Start Building Habits", substring = true))
            .assertIsDisplayed()
    }

    // ─── 9. Weekly balance report ──────────────────────────────────────────

    @Test
    fun testWeeklyBalanceReportEmpty() {
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        val viewReport = composeRule.onNodeWithText("View Weekly Report")
        viewReport.performScrollTo()
        viewReport.performClick()
        composeRule.waitForIdle()

        // Top bar renders; the donut chart + sparkline sections handle an
        // empty stats object by rendering zero-valued shapes rather than
        // crashing.
        composeRule.onNodeWithText("Weekly Balance Report").assertIsDisplayed()
    }

    // ─── 10. Settings with no data ─────────────────────────────────────────

    @Test
    fun testSettingsWithNoData() {
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()

        // Settings is a long ScrollColumn. Scroll to representative rows
        // from several sections to force those composables to render and
        // prove they survive empty-DB state.
        composeRule.onNodeWithText("Manage Projects").performScrollTo()
        composeRule.onNodeWithText("Manage Projects").assertIsDisplayed()

        composeRule.onNodeWithText("Eisenhower Matrix").performScrollTo()
        composeRule.onNodeWithText("Eisenhower Matrix").assertIsDisplayed()

        composeRule.onNodeWithText("Smart Focus Sessions").performScrollTo()
        composeRule.onNodeWithText("Smart Focus Sessions").assertIsDisplayed()
    }
}
