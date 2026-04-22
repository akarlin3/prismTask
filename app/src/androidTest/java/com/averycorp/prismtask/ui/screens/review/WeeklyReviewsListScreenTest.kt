package com.averycorp.prismtask.ui.screens.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.dao.WeeklyReviewDao
import com.averycorp.prismtask.data.remote.SyncTracker
import com.averycorp.prismtask.data.repository.WeeklyReviewRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [WeeklyReviewsListScreen] renders its empty-state copy when
 * the repo emits an empty history. This is the path a fresh user hits
 * before the Sunday worker has fired once, so the copy ("No Reviews Yet"
 * + explainer) doubles as a "what to expect next" affordance.
 *
 * Instantiating the ViewModel manually and passing it in keeps the test
 * Hilt-free — the composable's default `hiltViewModel()` is bypassed.
 */
@RunWith(AndroidJUnit4::class)
class WeeklyReviewsListScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun empty_history_renders_empty_state_copy() {
        val dao = mockk<WeeklyReviewDao>()
        val syncTracker = mockk<SyncTracker>(relaxed = true)
        every { dao.observeAll() } returns flowOf(emptyList())
        val repository = WeeklyReviewRepository(dao, syncTracker)
        val viewModel = WeeklyReviewsListViewModel(repository)

        composeRule.setContent {
            WeeklyReviewsListScreen(
                navController = rememberNavController(),
                viewModel = viewModel
            )
        }

        composeRule.onNodeWithText("No Reviews Yet").assertIsDisplayed()
        composeRule
            .onNode(hasText("Your first weekly review will generate this Sunday.", substring = true))
            .assertIsDisplayed()
    }
}
