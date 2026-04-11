package com.averycorp.prismtask.ui.navigation

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
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.scale
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
import com.averycorp.prismtask.ui.screens.auth.AuthScreen
import com.averycorp.prismtask.ui.screens.auth.AuthViewModel
import com.averycorp.prismtask.ui.screens.onboarding.OnboardingScreen
import com.averycorp.prismtask.ui.screens.onboarding.OnboardingViewModel
import com.averycorp.prismtask.ui.screens.addedittask.AddEditTaskScreen
import com.averycorp.prismtask.ui.screens.archive.ArchiveScreen
import com.averycorp.prismtask.ui.screens.habits.AddEditHabitScreen
import com.averycorp.prismtask.ui.screens.habits.HabitAnalyticsScreen
import com.averycorp.prismtask.ui.screens.habits.HabitDetailScreen
import com.averycorp.prismtask.ui.screens.projects.AddEditProjectScreen
import com.averycorp.prismtask.ui.screens.search.SearchScreen
import com.averycorp.prismtask.ui.screens.tags.TagManagementScreen
import com.averycorp.prismtask.ui.screens.monthview.MonthViewScreen
import com.averycorp.prismtask.ui.screens.timeline.TimelineScreen
import com.averycorp.prismtask.ui.screens.weekview.WeekViewScreen
import com.averycorp.prismtask.ui.screens.today.TodayScreen
import com.averycorp.prismtask.ui.screens.tasklist.TaskListScreen
import com.averycorp.prismtask.ui.screens.projects.ProjectListScreen
import com.averycorp.prismtask.ui.screens.habits.HabitListScreen
import com.averycorp.prismtask.ui.screens.leisure.LeisureScreen
import com.averycorp.prismtask.ui.screens.schoolwork.AddEditCourseScreen
import com.averycorp.prismtask.ui.screens.schoolwork.SchoolworkScreen
import com.averycorp.prismtask.ui.screens.medication.MedicationLogScreen
import com.averycorp.prismtask.ui.screens.medication.MedicationScreen
import com.averycorp.prismtask.ui.screens.selfcare.SelfCareScreen
import com.averycorp.prismtask.ui.screens.settings.SettingsScreen
import com.averycorp.prismtask.ui.screens.briefing.DailyBriefingScreen
import com.averycorp.prismtask.ui.screens.eisenhower.EisenhowerScreen
import com.averycorp.prismtask.ui.screens.planner.WeeklyPlannerScreen
import com.averycorp.prismtask.ui.screens.pomodoro.SmartPomodoroScreen
import com.averycorp.prismtask.ui.screens.templates.AddEditTemplateScreen
import com.averycorp.prismtask.ui.screens.chat.ChatScreen
import com.averycorp.prismtask.ui.screens.templates.TemplateListScreen
import com.averycorp.prismtask.ui.screens.timer.TimerScreen
import kotlinx.coroutines.launch

sealed class PrismTaskRoute(val route: String) {
    data object Today : PrismTaskRoute("today")
    data object TaskList : PrismTaskRoute("task_list")
    data object AddEditTask : PrismTaskRoute("add_edit_task?taskId={taskId}") {
        fun createRoute(taskId: Long? = null): String =
            if (taskId != null) "add_edit_task?taskId=$taskId" else "add_edit_task"
    }
    data object ProjectList : PrismTaskRoute("project_list")
    data object AddEditProject : PrismTaskRoute("add_edit_project?projectId={projectId}") {
        fun createRoute(projectId: Long? = null): String =
            if (projectId != null) "add_edit_project?projectId=$projectId" else "add_edit_project"
    }
    data object Settings : PrismTaskRoute("settings")
    data object TagManagement : PrismTaskRoute("tag_management")
    data object Search : PrismTaskRoute("search")
    data object Archive : PrismTaskRoute("archive")
    data object Auth : PrismTaskRoute("auth")
    data object WeekView : PrismTaskRoute("week_view")
    data object MonthView : PrismTaskRoute("month_view")
    data object Timeline : PrismTaskRoute("timeline")
    data object HabitList : PrismTaskRoute("habit_list")
    data object Timer : PrismTaskRoute("timer")
    data object AddEditHabit : PrismTaskRoute("add_edit_habit?habitId={habitId}") {
        fun createRoute(habitId: Long? = null): String =
            if (habitId != null) "add_edit_habit?habitId=$habitId" else "add_edit_habit"
    }
    data object HabitAnalytics : PrismTaskRoute("habit_analytics?habitId={habitId}") {
        fun createRoute(habitId: Long): String = "habit_analytics?habitId=$habitId"
    }
    data object HabitDetail : PrismTaskRoute("habit_detail?habitId={habitId}") {
        fun createRoute(habitId: Long): String = "habit_detail?habitId=$habitId"
    }
    data object SelfCare : PrismTaskRoute("self_care?routineType={routineType}") {
        fun createRoute(routineType: String = "morning"): String = "self_care?routineType=$routineType"
    }
    data object Medication : PrismTaskRoute("medication")
    data object MedicationLog : PrismTaskRoute("medication_log")
    data object Leisure : PrismTaskRoute("leisure")
    data object Schoolwork : PrismTaskRoute("schoolwork")
    data object AddEditCourse : PrismTaskRoute("add_edit_course?courseId={courseId}") {
        fun createRoute(courseId: Long? = null): String =
            if (courseId != null) "add_edit_course?courseId=$courseId" else "add_edit_course"
    }
    data object EisenhowerMatrix : PrismTaskRoute("eisenhower_matrix")
    data object SmartPomodoro : PrismTaskRoute("smart_pomodoro")
    data object DailyBriefing : PrismTaskRoute("daily_briefing")
    data object WeeklyPlanner : PrismTaskRoute("weekly_planner")
    data object TemplateList : PrismTaskRoute("templates")
    data object AddEditTemplate : PrismTaskRoute("templates/edit?templateId={templateId}") {
        fun createRoute(templateId: Long? = null): String =
            if (templateId != null) "templates/edit?templateId=$templateId" else "templates/edit"
    }
    data object AiChat : PrismTaskRoute("ai_chat?taskId={taskId}") {
        fun createRoute(taskId: Long? = null): String =
            if (taskId != null) "ai_chat?taskId=$taskId" else "ai_chat"
    }
    data object MainTabs : PrismTaskRoute("main_tabs")
    data object Onboarding : PrismTaskRoute("onboarding")
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val ALL_BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(PrismTaskRoute.Today.route, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    BottomNavItem(PrismTaskRoute.TaskList.route, "Tasks", Icons.AutoMirrored.Filled.FormatListBulleted, Icons.AutoMirrored.Outlined.FormatListBulleted),
    BottomNavItem(PrismTaskRoute.HabitList.route, "Habits", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    BottomNavItem(PrismTaskRoute.Timer.route, "Timer", Icons.Filled.Timer, Icons.Outlined.Timer),
    BottomNavItem(PrismTaskRoute.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

private const val NAV_ANIM_DURATION = 300

@Composable
fun PrismTaskNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    tabOrder: List<String> = ALL_BOTTOM_NAV_ITEMS.map { it.route },
    hiddenTabs: Set<String> = emptySet(),
    initialLaunchAction: String? = null,
    hasCompletedOnboarding: Boolean = true
) {
    // Handle deep-link intents from the QuickAdd widget: "open_templates"
    // routes straight to the Template List screen. "voice_input" keeps the
    // user on Today and auto-starts speech recognition.
    val autoStartVoice = androidx.compose.runtime.remember(initialLaunchAction) {
        androidx.compose.runtime.mutableStateOf(
            initialLaunchAction == com.averycorp.prismtask.MainActivity.ACTION_VOICE_INPUT
        )
    }
    LaunchedEffect(initialLaunchAction) {
        if (initialLaunchAction == com.averycorp.prismtask.MainActivity.ACTION_OPEN_TEMPLATES) {
            navController.navigate(PrismTaskRoute.TemplateList.route)
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
    val showBottomBar = currentRoute == null || currentRoute == PrismTaskRoute.MainTabs.route

    // Keyboard shortcuts: Ctrl+1..4 switches tabs, Ctrl+N opens quick add,
    // Ctrl+F focuses search, Escape pops the backstack. These are best-effort
    // — the hosting Activity decides whether a hardware keyboard is present.
    val focusRequester = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }
    val shortcutModifier = modifier
        .focusRequester(focusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (!event.isCtrlPressed && event.key != androidx.compose.ui.input.key.Key.Escape) {
                return@onPreviewKeyEvent false
            }
            when (event.key) {
                androidx.compose.ui.input.key.Key.N -> {
                    navController.navigate(PrismTaskRoute.AddEditTask.createRoute())
                    true
                }
                androidx.compose.ui.input.key.Key.F -> {
                    navController.navigate(PrismTaskRoute.Search.route)
                    true
                }
                androidx.compose.ui.input.key.Key.One -> {
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }; true
                }
                androidx.compose.ui.input.key.Key.Two -> {
                    if (bottomNavItems.size > 1)
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    true
                }
                androidx.compose.ui.input.key.Key.Three -> {
                    if (bottomNavItems.size > 2)
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    true
                }
                androidx.compose.ui.input.key.Key.Four -> {
                    if (bottomNavItems.size > 3)
                        coroutineScope.launch { pagerState.animateScrollToPage(3) }
                    true
                }
                androidx.compose.ui.input.key.Key.Escape -> {
                    navController.popBackStack(); true
                }
                else -> false
            }
        }

    Scaffold(
        modifier = shortcutModifier,
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
                                val iconScale by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (selected) 1.1f else 1f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy
                                    ),
                                    label = "nav_icon_scale"
                                )
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    modifier = Modifier.scale(iconScale)
                                )
                            },
                            label = { Text(item.label) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val startDest = if (hasCompletedOnboarding) PrismTaskRoute.MainTabs.route else PrismTaskRoute.Onboarding.route

        NavHost(
            navController = navController,
            startDestination = startDest,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Onboarding screen
            composable(
                route = PrismTaskRoute.Onboarding.route,
                enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onComplete = {
                        navController.navigate(PrismTaskRoute.MainTabs.route) {
                            popUpTo(PrismTaskRoute.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            // Main tab screens — swipeable via HorizontalPager
            composable(
                route = PrismTaskRoute.MainTabs.route,
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
                        PrismTaskRoute.Today.route -> TodayScreen(
                            navController = navController,
                            autoStartVoice = autoStartVoice.value,
                            onVoiceAutoStartConsumed = { autoStartVoice.value = false }
                        )
                        PrismTaskRoute.TaskList.route -> TaskListScreen(navController)
                        PrismTaskRoute.HabitList.route -> HabitListScreen(navController)
                        PrismTaskRoute.Timer.route -> TimerScreen(navController)
                        PrismTaskRoute.Settings.route -> SettingsScreen(navController)
                    }
                }
            }

            composable(
                route = PrismTaskRoute.ProjectList.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.Leisure.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                LeisureScreen(navController)
            }

            composable(
                route = PrismTaskRoute.Schoolwork.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(NAV_ANIM_DURATION)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) }
            ) {
                SchoolworkScreen(navController)
            }

            // Detail screens — remain native Compose, slide transitions
            composable(
                route = PrismTaskRoute.AddEditTask.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.AddEditProject.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.TagManagement.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.Search.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.WeekView.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.MonthView.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.Timeline.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.Archive.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.AddEditHabit.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.Auth.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.HabitAnalytics.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.HabitDetail.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                HabitDetailScreen(navController)
            }

            composable(
                route = PrismTaskRoute.SelfCare.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.Medication.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.MedicationLog.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.AddEditCourse.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.EisenhowerMatrix.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.SmartPomodoro.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.DailyBriefing.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                DailyBriefingScreen(navController)
            }

            composable(
                route = PrismTaskRoute.WeeklyPlanner.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                WeeklyPlannerScreen(navController)
            }

            composable(
                route = PrismTaskRoute.TemplateList.route,
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
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
                route = PrismTaskRoute.AddEditTemplate.route,
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

            composable(
                route = PrismTaskRoute.AiChat.route,
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
                    slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                            fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            ) {
                ChatScreen(navController)
            }
        }
    }
}
