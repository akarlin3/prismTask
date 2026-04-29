package com.averycorp.prismtask.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.averycorp.prismtask.data.billing.BillingManager
import com.averycorp.prismtask.data.billing.UserTier
import com.averycorp.prismtask.data.remote.api.ExtractFromTextResponse
import com.averycorp.prismtask.data.remote.api.ExtractedTaskCandidateResponse
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import javax.inject.Inject

/**
 * End-to-end smokes for the multi-task QuickAddBar flow (Phase B / PR-C
 * of the multi-task creation audit). Both navigate via the real
 * QuickAddBar → MultiCreateDetector → onMultiCreate emit → NavGraph
 * route path so the integration glue is exercised in-process.
 *
 * The detector + parser + ViewModel internals are exhaustively unit
 * tested elsewhere; these smokes verify only that the bottom sheet
 * route renders with both API-success and API-failure inputs without
 * crashing the harness.
 */
@HiltAndroidTest
class MultiCreateSmokeTest : SmokeTestBase() {
    @Inject
    lateinit var fakeApi: FakePrismTaskApi

    @Inject
    lateinit var billingManager: BillingManager

    /**
     * Pro tier required: the multi-create pre-pass in QuickAddViewModel
     * is gated on AI_NLP. setDebugTier is a no-op for non-admin
     * production builds but allowed in DEBUG / androidTest builds.
     */
    private fun forcePro() {
        billingManager.setDebugTier(UserTier.PRO)
        composeRule.waitForIdle()
    }

    /**
     * Type [text] into the QuickAddBar's collapsed-then-expanded text
     * field and tap Submit. Mirrors the user's manual flow: tap Quick
     * Add → enter text → tap send icon.
     */
    private fun submitViaQuickAdd(text: String) {
        composeRule.onAllNodesWithText("Quick Add", substring = false)
            .onFirst()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Add task", substring = true)
            .onFirst()
            .performTextInput(text)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Submit task")
            .onFirst()
            .performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun multiCreate_apiSuccess_rendersCandidates() {
        forcePro()
        fakeApi.extractTasksFromTextResult = ExtractFromTextResponse(
            tasks = listOf(
                ExtractedTaskCandidateResponse(
                    title = "Email Bob",
                    suggestedDueDate = null,
                    suggestedPriority = 0,
                    suggestedProject = null,
                    confidence = 0.9f
                ),
                ExtractedTaskCandidateResponse(
                    title = "Call Mary",
                    suggestedDueDate = null,
                    suggestedPriority = 0,
                    suggestedProject = null,
                    confidence = 0.8f
                )
            )
        )

        // Rule (b) input: 3 comma segments, all carrying time markers
        // (today, tomorrow, by Friday) → MultiCreateDetector emits.
        submitViaQuickAdd(
            "email Bob today, call Mary tomorrow, finish report by Friday"
        )

        composeRule.onAllNodesWithText("Email Bob")
            .onFirst()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Add Multiple Tasks")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun multiCreate_apiFailure_fallsBackToRegex_thenRendersEmptyState() {
        forcePro()
        // No canned response → FakePrismTaskApi.extractTasksFromText
        // throws. NaturalLanguageParser.extractFromText catches and
        // routes to ConversationTaskExtractor. The rule-(b) input
        // below has no "TODO:" / "I'll" / "Action item:" markers, so
        // the regex extractor returns 0 candidates and the sheet
        // renders its "no tasks recognized" empty state — verifying
        // the failure path does not crash the harness.
        fakeApi.extractTasksFromTextResult = null

        submitViaQuickAdd(
            "email Bob today, call Mary tomorrow, finish report by Friday"
        )

        composeRule.onAllNodesWithText("Add Multiple Tasks")
            .onFirst()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("No tasks recognized", substring = true)
            .onFirst()
            .assertIsDisplayed()
    }
}
