package com.averycorp.prismtask.ui.navigation.routes

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.admin.AdminBugReportsScreen
import com.averycorp.prismtask.ui.screens.debug.DebugLogScreen
import com.averycorp.prismtask.ui.screens.feedback.BugReportScreen
import com.averycorp.prismtask.ui.screens.feedback.BugReportViewModel

/**
 * Feedback, bug-report, feature-request, and debug-log route
 * definitions. Bug / feature-request use a vertical-slide transition
 * since they behave like full-screen dialogs.
 */
internal fun NavGraphBuilder.feedbackRoutes(navController: NavHostController) {
    composable(
        route = PrismTaskRoute.BugReport.route,
        arguments = listOf(
            navArgument("fromScreen") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("screenshotUri") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        ),
        enterTransition = verticalSlideEnter,
        exitTransition = fadeExitOnly,
        popEnterTransition = fadeEnterOnly,
        popExitTransition = verticalSlidePopExit
    ) {
        BugReportScreen(navController)
    }

    composable(
        route = PrismTaskRoute.FeatureRequest.route,
        enterTransition = verticalSlideEnter,
        exitTransition = fadeExitOnly,
        popEnterTransition = fadeEnterOnly,
        popExitTransition = verticalSlidePopExit
    ) {
        val viewModel: BugReportViewModel = hiltViewModel()
        LaunchedEffect(Unit) {
            viewModel.setIsFeatureRequest(true)
        }
        BugReportScreen(navController, viewModel)
    }

    horizontalSlideComposable(PrismTaskRoute.DebugLog.route) {
        DebugLogScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.AdminBugReports.route) {
        AdminBugReportsScreen(navController)
    }
}
