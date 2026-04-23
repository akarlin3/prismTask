package com.averycorp.prismtask.smoke

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.averycorp.prismtask.MainActivity
import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import com.averycorp.prismtask.data.preferences.OnboardingPreferences
import com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import javax.inject.Inject

/**
 * Base class for smoke tests that need a running Activity with Hilt DI
 * and a seeded in-memory database.
 *
 * Subclasses get:
 *  - [composeRule] for Compose UI testing
 *  - [hiltRule] for Hilt injection
 *  - [database] for direct DB access
 *  - [seededIds] with all inserted entity IDs
 *  - Helper extensions: [findByText], [findByContentDescription]
 */
abstract class SmokeTestBase {
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

    protected lateinit var seededIds: TestDataSeeder.SeededIds

    @Before
    fun baseSetUp() {
        hiltRule.inject()
        // MainActivity gates the main UI behind hasCompletedOnboarding; a
        // fresh test install has that unset so the rule launches straight
        // into the onboarding flow, and compose assertions for "Today" /
        // "Tasks" / etc. fail because those labels only appear in the main
        // tab bar. Same story for hasSetStartOfDay — without this, a
        // blocking StartOfDayPickerDialog covers the main UI. Seed both
        // flags before any assertion runs; runBlocking guarantees the
        // DataStore write completes before we return control to the test.
        runBlocking {
            onboardingPreferences.setOnboardingCompleted()
            taskBehaviorPreferences.setHasSetStartOfDay(true)
        }
        runTest {
            seededIds = TestDataSeeder.seed(database)
        }
        composeRule.waitForIdle()
    }

    @After
    fun baseTearDown() {
        runTest {
            TestDataSeeder.clear(database)
        }
    }

    // ---- helpers ----

    protected fun findByText(text: String) = composeRule.onNodeWithText(text)

    protected fun findByContentDescription(description: String) =
        composeRule.onNodeWithContentDescription(description)

    /**
     * Find a bottom-nav tab by its label. Nav tabs carry semantics Role = Tab;
     * filtering on that rules out duplicate text matches from screen titles,
     * filter chips, task titles, etc. Use this instead of `findByText("Tasks")`
     * whenever the intent is "click the bottom-nav Tasks tab."
     */
    protected fun findTab(label: String) =
        composeRule.onNode(hasText(label).and(hasRole(Role.Tab)))

    /**
     * Click the bottom-nav tab with the given label and wait for the target
     * screen to settle. Hides the common "Tasks text matches 3 nodes" /
     * "Today matches 5 nodes" breakage that every pre-#683 smoke test hit.
     */
    protected fun clickTab(label: String) {
        findTab(label).performClick()
        composeRule.waitForIdle()
    }

    protected fun waitForIdle() = composeRule.waitForIdle()

    private fun hasRole(role: Role): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, role)
}
