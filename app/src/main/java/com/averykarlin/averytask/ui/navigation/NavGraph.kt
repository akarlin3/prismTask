package com.averykarlin.averytask.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    data object Leisure : AveryTaskRoute("leisure")
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(AveryTaskRoute.Today.route, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    BottomNavItem(AveryTaskRoute.TaskList.route, "Tasks", Icons.AutoMirrored.Filled.FormatListBulleted, Icons.AutoMirrored.Outlined.FormatListBulleted),
    BottomNavItem(AveryTaskRoute.ProjectList.route, "Projects", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy),
    BottomNavItem(AveryTaskRoute.HabitList.route, "Habits", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    BottomNavItem(AveryTaskRoute.Leisure.route, "Leisure", Icons.Filled.SelfImprovement, Icons.Outlined.SelfImprovement),
    BottomNavItem(AveryTaskRoute.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private val mainRoutes = bottomNavItems.map { it.route }.toSet()

private const val NAV_ANIM_DURATION = 300

@Composable
fun AveryTaskNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in mainRoutes

    Scaffold(
        modifier = modifier,
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
            startDestination = AveryTaskRoute.Today.route,
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
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
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
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                LeisureScreen(navController)
            }

            composable(
                route = AveryTaskRoute.Settings.route,
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
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
        }
    }
}
