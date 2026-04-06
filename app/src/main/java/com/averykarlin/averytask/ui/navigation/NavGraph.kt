package com.averykarlin.averytask.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.averykarlin.averytask.ui.screens.auth.AuthScreen
import com.averykarlin.averytask.ui.screens.auth.AuthViewModel
import com.averykarlin.averytask.ui.screens.addedittask.AddEditTaskScreen
import com.averykarlin.averytask.ui.screens.archive.ArchiveScreen
import com.averykarlin.averytask.ui.screens.habits.AddEditHabitScreen
import com.averykarlin.averytask.ui.screens.habits.HabitAnalyticsScreen
import com.averykarlin.averytask.ui.screens.projects.AddEditProjectScreen
import com.averykarlin.averytask.ui.screens.search.SearchScreen
import com.averykarlin.averytask.ui.screens.tags.TagManagementScreen
import com.averykarlin.averytask.ui.screens.monthview.MonthViewScreen
import com.averykarlin.averytask.ui.screens.timeline.TimelineScreen
import com.averykarlin.averytask.ui.screens.weekview.WeekViewScreen
import com.averykarlin.averytask.ui.screens.today.TodayScreen
import com.averykarlin.averytask.ui.screens.tasklist.TaskListScreen
import com.averykarlin.averytask.ui.screens.projects.ProjectListScreen
import com.averykarlin.averytask.ui.screens.habits.HabitListScreen
import com.averykarlin.averytask.ui.screens.leisure.LeisureScreen
import com.averykarlin.averytask.ui.screens.schoolwork.AddEditCourseScreen
import com.averykarlin.averytask.ui.screens.schoolwork.SchoolworkScreen
import com.averykarlin.averytask.ui.screens.medication.MedicationScreen
import com.averykarlin.averytask.ui.screens.selfcare.SelfCareScreen
import com.averykarlin.averytask.ui.screens.settings.SettingsScreen

sealed class AveryTaskRoute(val route: String) {
    data object Today : AveryTaskRoute("today")
    data object TaskList : AveryTaskRoute("task_list")
    data object AddEditTask : AveryTaskRoute("add_edit_task?taskId={taskId}") {
        fun createRoute(taskId: Long? = null): String =
            if (taskId != null) "add_edit_task?taskId=$taskId" else "add_edit_task"
    }
    data object ProjectList : AveryTaskRoute("project_list")
    data object AddEditProject : AveryTaskRoute("add_edit_project?projectId={projectId}") {
        fun createRoute(projectId: Long? = null): String =
            if (projectId != null) "add_edit_project?projectId=$projectId" else "add_edit_project"
    }
    data object Settings : AveryTaskRoute("settings")
    data object TagManagement : AveryTaskRoute("tag_management")
    data object Search : AveryTaskRoute("search")
    data object Archive : AveryTaskRoute("archive")
    data object Auth : AveryTaskRoute("auth")
    data object WeekView : AveryTaskRoute("week_view")
    data object MonthView : AveryTaskRoute("month_view")
    data object Timeline : AveryTaskRoute("timeline")
    data object HabitList : AveryTaskRoute("habit_list")
    data object AddEditHabit : AveryTaskRoute("add_edit_habit?habitId={habitId}") {
        fun createRoute(habitId: Long? = null): String =
            if (habitId != null) "add_edit_habit?habitId=$habitId" else "add_edit_habit"
    }
    data object HabitAnalytics : AveryTaskRoute("habit_analytics?habitId={habitId}") {
        fun createRoute(habitId: Long): String = "habit_analytics?habitId=$habitId"
    }
    data object SelfCare : AveryTaskRoute("self_care?routineType={routineType}") {
        fun createRoute(routineType: String = "morning"): String = "self_care?routineType=$routineType"
    }
    data object Medication : AveryTaskRoute("medication")
    data object Leisure : AveryTaskRoute("leisure")
    data object Schoolwork : AveryTaskRoute("schoolwork")
    data object AddEditCourse : AveryTaskRoute("add_edit_course?courseId={courseId}") {
        fun createRoute(courseId: Long? = null): String =
            if (courseId != null) "add_edit_course?courseId=$courseId" else "add_edit_course"
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val ALL_BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(AveryTaskRoute.Today.route, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    BottomNavItem(AveryTaskRoute.TaskList.route, "Tasks", Icons.AutoMirrored.Filled.FormatListBulleted, Icons.AutoMirrored.Outlined.FormatListBulleted),
    BottomNavItem(AveryTaskRoute.HabitList.route, "Habits", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
)

private const val NAV_ANIM_DURATION = 300

@Composable
fun AveryTaskNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    tabOrder: List<String> = ALL_BOTTOM_NAV_ITEMS.map { it.route },
    hiddenTabs: Set<String> = emptySet()
) {
    val bottomNavItems = tabOrder
        .mapNotNull { route -> ALL_BOTTOM_NAV_ITEMS.find { it.route == route } }
        .filter { it.route !in hiddenTabs }
        .ifEmpty { ALL_BOTTOM_NAV_ITEMS.take(2) }
    val mainRoutes = bottomNavItems.map { it.route }.toSet()
    val startRoute = bottomNavItems.firstOrNull()?.route ?: AveryTaskRoute.Today.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in mainRoutes

    Scaffold(
        modifier = modifier,
        topBar = {
            if (showBottomBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(end = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            navController.navigate(AveryTaskRoute.Settings.route) {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = navBackStackEntry?.destination?.hierarchy
                            ?.any { it.route == item.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Main tab screens — native Compose
            composable(
                route = AveryTaskRoute.Today.route,
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                TodayScreen(navController)
            }

            composable(
                route = AveryTaskRoute.TaskList.route,
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                TaskListScreen(navController)
            }

            composable(
                route = AveryTaskRoute.ProjectList.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                ProjectListScreen(navController)
            }

            composable(
                route = AveryTaskRoute.HabitList.route,
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                HabitListScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Leisure.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                LeisureScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Schoolwork.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                SchoolworkScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Settings.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                SettingsScreen(navController)
            }

            // Detail screens — remain native Compose, slide transitions
            composable(
                route = AveryTaskRoute.AddEditTask.route,
                arguments = listOf(
                    navArgument("taskId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                AddEditTaskScreen(navController)
            }

            composable(
                route = AveryTaskRoute.AddEditProject.route,
                arguments = listOf(
                    navArgument("projectId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                AddEditProjectScreen(navController)
            }

            composable(
                route = AveryTaskRoute.TagManagement.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                TagManagementScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Search.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                SearchScreen(navController)
            }

            composable(
                route = AveryTaskRoute.WeekView.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                WeekViewScreen(navController)
            }

            composable(
                route = AveryTaskRoute.MonthView.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                MonthViewScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Timeline.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                TimelineScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Archive.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                ArchiveScreen(navController)
            }

            composable(
                route = AveryTaskRoute.AddEditHabit.route,
                arguments = listOf(
                    navArgument("habitId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                AddEditHabitScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Auth.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                val authViewModel: AuthViewModel = hiltViewModel()
                AuthScreen(
                    viewModel = authViewModel,
                    onContinue = { navController.popBackStack() }
                )
            }

            composable(
                route = AveryTaskRoute.HabitAnalytics.route,
                arguments = listOf(
                    navArgument("habitId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                HabitAnalyticsScreen(navController)
            }

            composable(
                route = AveryTaskRoute.SelfCare.route,
                arguments = listOf(
                    navArgument("routineType") {
                        type = NavType.StringType
                        defaultValue = "morning"
                    }
                ),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                SelfCareScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Medication.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                MedicationScreen(navController)
            }

            composable(
                route = AveryTaskRoute.AddEditCourse.route,
                arguments = listOf(
                    navArgument("courseId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                AddEditCourseScreen(navController)
            }
        }
    }
}
