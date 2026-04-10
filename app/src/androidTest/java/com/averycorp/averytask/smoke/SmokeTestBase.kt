package com.averycorp.averytask.smoke

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.averycorp.averytask.MainActivity
import com.averycorp.averytask.data.local.database.AveryTaskDatabase
import dagger.hilt.android.testing.HiltAndroidRule
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
    lateinit var database: AveryTaskDatabase

    protected lateinit var seededIds: TestDataSeeder.SeededIds

    @Before
    fun baseSetUp() {
        hiltRule.inject()
        runTest {
            seededIds = TestDataSeeder.seed(database)
        }
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

    protected fun waitForIdle() = composeRule.waitForIdle()
}
