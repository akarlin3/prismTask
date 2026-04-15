package com.averycorp.prismtask.ui.navigation.routes

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.notifications.NotificationBriefingScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationCollaboratorScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationEscalationScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationLockScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationProfilesScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationQuietHoursScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationSnoozeScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationSoundScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationStreakScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationTesterScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationTypesScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationVibrationScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationVisualScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationWatchScreen
import com.averycorp.prismtask.ui.screens.notifications.NotificationsHubScreen

/**
 * v1.4.0 Notifications Overhaul — Hub + 14 sub-screens. The hub acts
 * as the "home" and each sub-screen is a push onto the stack using
 * the standard horizontal slide transition.
 */
internal fun NavGraphBuilder.notificationRoutes(navController: NavHostController) {
    horizontalSlideComposable(PrismTaskRoute.NotificationsHub.route) {
        NotificationsHubScreen(navController)
    }

    listOf<Pair<String, @Composable () -> Unit>>(
        PrismTaskRoute.NotificationProfiles.route to { NotificationProfilesScreen(navController) },
        PrismTaskRoute.NotificationTypes.route to { NotificationTypesScreen(navController) },
        PrismTaskRoute.NotificationBriefing.route to { NotificationBriefingScreen(navController) },
        PrismTaskRoute.NotificationStreak.route to { NotificationStreakScreen(navController) },
        PrismTaskRoute.NotificationCollaborator.route to { NotificationCollaboratorScreen(navController) },
        PrismTaskRoute.NotificationSound.route to { NotificationSoundScreen(navController) },
        PrismTaskRoute.NotificationVibration.route to { NotificationVibrationScreen(navController) },
        PrismTaskRoute.NotificationVisual.route to { NotificationVisualScreen(navController) },
        PrismTaskRoute.NotificationLockScreen.route to { NotificationLockScreen(navController) },
        PrismTaskRoute.NotificationQuietHours.route to { NotificationQuietHoursScreen(navController) },
        PrismTaskRoute.NotificationSnooze.route to { NotificationSnoozeScreen(navController) },
        PrismTaskRoute.NotificationEscalation.route to { NotificationEscalationScreen(navController) },
        PrismTaskRoute.NotificationWatch.route to { NotificationWatchScreen(navController) },
        PrismTaskRoute.NotificationTester.route to { NotificationTesterScreen(navController) }
    ).forEach { (route, content) ->
        composable(
            route = route,
            enterTransition = horizontalSlideEnter,
            exitTransition = horizontalSlideExit,
            popEnterTransition = horizontalSlidePopEnter,
            popExitTransition = horizontalSlidePopExit
        ) { content() }
    }
}
