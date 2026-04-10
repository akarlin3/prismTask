package com.averycorp.averytask.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.averycorp.averytask.ui.screens.auth.AuthScreen
import com.averycorp.averytask.ui.screens.auth.AuthViewModel
import com.averycorp.averytask.ui.screens.addedittask.AddEditTaskScreen
import com.averycorp.averytask.ui.screens.archive.ArchiveScreen
import com.averycorp.averytask.ui.screens.habits.AddEditHabitScreen
import com.averycorp.averytask.ui.screens.habits.HabitAnalyticsScreen
import com.averycorp.averytask.ui.screens.projects.AddEditProjectScreen
import com.averycorp.averytask.ui.screens.search.SearchScreen
import com.averycorp.averytask.ui.screens.tags.TagManagementScreen
import com.averycorp.averytask.ui.screens.monthview.MonthViewScreen
import com.averycorp.averytask.ui.screens.timeline.TimelineScreen
import com.averycorp.averytask.ui.screens.weekview.WeekViewScreen
import com.averycorp.averytask.ui.screens.today.TodayScreen
import com.averycorp.averytask.ui.screens.tasklist.TaskListScreen
import com.averycorp.averytask.ui.screens.projects.ProjectListScreen
import com.averycorp.averytask.ui.screens.habits.HabitListScreen
import com.averycorp.averytask.ui.screens.leisure.LeisureScreen
import com.averycorp.averytask.ui.screens.schoolwork.AddEditCourseScreen
import com.averycorp.averytask.ui.screens.schoolwork.SchoolworkScreen
import com.averycorp.averytask.ui.screens.medication.MedicationLogScreen
import com.averycorp.averytask.ui.screens.medication.MedicationScreen
import com.averycorp.averytask.ui.screens.selfcare.SelfCareScreen
import com.averycorp.averytask.ui.screens.settings.SettingsScreen
import com.averycorp.averytask.ui.screens.eisenhower.EisenhowerScreen
import com.averycorp.averytask.ui.screens.pomodoro.SmartPomodoroScreen
import com.averycorp.averytask.ui.screens.templates.AddEditTemplateScreen
import com.averycorp.averytask.ui.screens.templates.TemplateListScreen
import com.averycorp.averytask.ui.screens.timer.TimerScreen
import kotlinx.coroutines.launch

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
    data object Timer : AveryTaskRoute("timer")
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
    data object MedicationLog : AveryTaskRoute("medication_log")
    data object Leisure : AveryTaskRoute("leisure")
    data object Schoolwork : AveryTaskRoute("schoolwork")
    data object AddEditCourse : AveryTaskRoute("add_edit_course?courseId={courseId}") {
        fun createRoute(courseId: Long? = null): String =
            if (courseId != null) "add_edit_course?courseId=$courseId" else "add_edit_course"
    }
    data object EisenhowerMatrix : AveryTaskRoute("eisenhower_matrix")
    data object SmartPomodoro : AveryTaskRoute("smart_pomodoro")
    data object TemplateList : AveryTaskRoute("templates")
    data object AddEditTemplate : AveryTaskRoute("templates/edit?templateId={templateId}") {
        fun createRoute(templateId: Long? = null): String =
            if (templateId != null) "templates/edit?templateId=$templateId" else "templates/edit"
    }
    data object MainTabs : AveryTaskRoute("main_tabs")
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
    BottomNavItem(AveryTaskRoute.Timer.route, "Timer", Icons.Filled.Timer, Icons.Outlined.Timer),
    BottomNavItem(AveryTaskRoute.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private const val NAV_ANIM_DURATION = 300

@Composable
fun AveryTaskNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    tabOrder: List<String> = ALL_BOTTOM_NAV_ITEMS.map { it.route },
    hiddenTabs: Set<String> = emptySet(),
    initialLaunchAction: String? = null
) {
    // Handle deep-link intents from the QuickAdd widget: "open_templates"
    // routes straight to the Template List screen. Other launch actions
    // fall through to the default start destination.
    LaunchedEffect(initialLaunchAction) {
        if (initialLaunchAction == com.averycorp.averytask.MainActivity.ACTION_OPEN_TEMPLATES) {
            navController.navigate(AveryTaskRoute.TemplateList.route)
        }
    }
    // Append any tabs that aren't yet in the saved order (e.g. new tabs added in an update).
    val effectiveOrder = tabOrder + ALL_BOTTOM_NAV_ITEMS.map { it.route }.filter { it !in tabOrder }
    val bottomNavItems = effectiveOrder
        .mapNotNull { route -> ALL_BOTTOM_NAV_ITEMS.find { it.route == route } }
        .filter { it.route !in hiddenTabs }
        .ifEmpty { ALL_BOTTOM_NAV_ITEMS.take(2) }

    val pagerState = rememberPagerState(pageCount = { bottomNavItems.size })
    val coroutineScope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute == null || currentRoute == AveryTaskRoute.MainTabs.route

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEachIndexed { index, item ->
                        val selected = pagerState.currentPage == index

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
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
            startDestination = AveryTaskRoute.MainTabs.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Main tab screens — swipeable via HorizontalPager
            composable(
                route = AveryTaskRoute.MainTabs.route,
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { bottomNavItems[it].route },
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (bottomNavItems[page].route) {
                        AveryTaskRoute.Today.route -> TodayScreen(navController)
                        AveryTaskRoute.TaskList.route -> TaskListScreen(navController)
                        AveryTaskRoute.HabitList.route -> HabitListScreen(navController)
                        AveryTaskRoute.Timer.route -> TimerScreen(navController)
                        AveryTaskRoute.Settings.route -> SettingsScreen(navController)
                    }
                }
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
                route = AveryTaskRoute.MedicationLog.route,
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
                MedicationLogScreen(navController)
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

            composable(
                route = AveryTaskRoute.EisenhowerMatrix.route,
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
                EisenhowerScreen(navController)
            }

            composable(
                route = AveryTaskRoute.SmartPomodoro.route,
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
                SmartPomodoroScreen(navController)
            }

            composable(
                route = AveryTaskRoute.TemplateList.route,
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
                TemplateListScreen(navController)
            }

            composable(
                route = AveryTaskRoute.AddEditTemplate.route,
                arguments = listOf(
                    navArgument("templateId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                ),
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                AddEditTemplateScreen(navController)
            }
        }
    }
}
