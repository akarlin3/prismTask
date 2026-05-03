package com.averycorp.prismtask.ui.navigation.routes

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.addedittask.AddEditTaskScreen
import com.averycorp.prismtask.ui.screens.analytics.TaskAnalyticsScreen
import com.averycorp.prismtask.ui.screens.archive.ArchiveScreen
import com.averycorp.prismtask.ui.screens.monthview.MonthViewScreen
import com.averycorp.prismtask.ui.screens.projects.AddEditProjectScreen
import com.averycorp.prismtask.ui.screens.projects.ProjectDetailScreen
import com.averycorp.prismtask.ui.screens.projects.ProjectListScreen
import com.averycorp.prismtask.ui.screens.projects.roadmap.ProjectRoadmapScreen
import com.averycorp.prismtask.ui.screens.search.SearchScreen
import com.averycorp.prismtask.ui.screens.tags.TagManagementScreen
import com.averycorp.prismtask.ui.screens.timeline.TimelineScreen
import com.averycorp.prismtask.ui.screens.weekview.WeekViewScreen

/**
 * Task- and project-related route definitions: list, add/edit, views
 * (week/month/timeline), archive, search, tags, and analytics.
 */
internal fun NavGraphBuilder.taskRoutes(navController: NavHostController) {
    horizontalSlideComposable(PrismTaskRoute.ProjectList.route) {
        ProjectListScreen(navController)
    }

    horizontalSlideComposable(
        route = PrismTaskRoute.AddEditTask.route,
        arguments = listOf(
            navArgument("taskId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        AddEditTaskScreen(navController)
    }

    horizontalSlideComposable(
        route = PrismTaskRoute.AddEditProject.route,
        arguments = listOf(
            navArgument("projectId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        AddEditProjectScreen(navController)
    }

    horizontalSlideComposable(
        route = PrismTaskRoute.ProjectDetail.route,
        arguments = listOf(
            navArgument("projectId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        ProjectDetailScreen(navController)
    }

    horizontalSlideComposable(
        route = PrismTaskRoute.ProjectRoadmap.route,
        arguments = listOf(
            navArgument("projectId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        ProjectRoadmapScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.TagManagement.route) {
        TagManagementScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.Search.route) {
        SearchScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.WeekView.route) {
        WeekViewScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.MonthView.route) {
        MonthViewScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.Timeline.route) {
        TimelineScreen(navController)
    }

    horizontalSlideComposable(PrismTaskRoute.Archive.route) {
        ArchiveScreen(navController)
    }

    horizontalSlideComposable(
        route = PrismTaskRoute.TaskAnalytics.route,
        arguments = listOf(
            navArgument("projectId") {
                type = NavType.LongType
                defaultValue = -1L
            }
        )
    ) {
        TaskAnalyticsScreen(navController)
    }
}
