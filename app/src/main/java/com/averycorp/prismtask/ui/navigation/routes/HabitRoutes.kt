package com.averycorp.prismtask.ui.navigation.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.habits.AddEditHabitScreen
import com.averycorp.prismtask.ui.screens.habits.HabitAnalyticsScreen
import com.averycorp.prismtask.ui.screens.habits.HabitDetailScreen

/**
 * Habit-related route definitions: add/edit, analytics, and detail.
 */
internal fun NavGraphBuilder.habitRoutes(navController: NavHostController) {
    horizontalSlideComposable(
        route = PrismTaskRoute.AddEditHabit.route,
        arguments = listOf(
            navArgument("habitId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        AddEditHabitScreen(navController)
    }

    horizontalSlideComposable(
        route = PrismTaskRoute.HabitAnalytics.route,
        arguments = listOf(
            navArgument("habitId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        HabitAnalyticsScreen(navController)
    }

    horizontalSlideComposable(
        route = PrismTaskRoute.HabitDetail.route,
        arguments = listOf(
            navArgument("habitId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        HabitDetailScreen(navController)
    }
}
