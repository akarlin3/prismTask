package com.averycorp.prismtask.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Repeat
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.averycorp.prismtask.ui.screens.habits.HabitListScreen
import com.averycorp.prismtask.ui.screens.onboarding.OnboardingScreen
import com.averycorp.prismtask.ui.screens.onboarding.OnboardingViewModel
import com.averycorp.prismtask.ui.screens.settings.SettingsScreen
import com.averycorp.prismtask.ui.screens.tasklist.TaskListScreen
import com.averycorp.prismtask.ui.screens.timer.TimerScreen
import com.averycorp.prismtask.ui.screens.today.TodayScreen
import kotlinx.coroutines.launch

sealed class PrismTaskRoute(
    val route: String
) {
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

    data object HabitsRecurring : PrismTaskRoute("habits_recurring")

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

    data object MorningCheckIn : PrismTaskRoute("morning_check_in")

    data object MoodAnalytics : PrismTaskRoute("mood_analytics")

    data object WeeklyBalanceReport : PrismTaskRoute("weekly_balance_report")

    data object PasteConversation : PrismTaskRoute("paste_conversation")

    data object WeeklyReview : PrismTaskRoute("weekly_review")

    data object MedicationRefill : PrismTaskRoute("medication_refill")

    data object BugReport : PrismTaskRoute("bug_report?fromScreen={fromScreen}") {
        fun createRoute(fromScreen: String = ""): String =
            "bug_report?fromScreen=$fromScreen"
    }

    data object FeatureRequest : PrismTaskRoute("feature_request")

    data object DebugLog : PrismTaskRoute("debug_log")

    data object TaskAnalytics : PrismTaskRoute("task_analytics?projectId={projectId}") {
        fun createRoute(projectId: Long? = null): String =
            if (projectId != null) "task_analytics?projectId=$projectId" else "task_analytics"
    }

    // v1.4.0 Notifications Overhaul: top-level hub + per-domain sub-screens
    data object NotificationsHub : PrismTaskRoute("notifications_hub")

    data object NotificationProfiles : PrismTaskRoute("notifications_profiles")

    data object NotificationTypes : PrismTaskRoute("notifications_types")

    data object NotificationBriefing : PrismTaskRoute("notifications_briefing")

    data object NotificationStreak : PrismTaskRoute("notifications_streak")

    data object NotificationCollaborator : PrismTaskRoute("notifications_collab")

    data object NotificationSound : PrismTaskRoute("notifications_sound")

    data object NotificationVibration : PrismTaskRoute("notifications_vibration")

    data object NotificationVisual : PrismTaskRoute("notifications_visual")

    data object NotificationLockScreen : PrismTaskRoute("notifications_lockscreen")

    data object NotificationQuietHours : PrismTaskRoute("notifications_quiet_hours")

    data object NotificationSnooze : PrismTaskRoute("notifications_snooze")

    data object NotificationEscalation : PrismTaskRoute("notifications_escalation")

    data object NotificationWatch : PrismTaskRoute("notifications_watch")

    data object NotificationTester : PrismTaskRoute("notifications_tester")
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val ALL_BOTTOM_NAV_ITEMS = listOf(
    BottomNavItem(PrismTaskRoute.Today.route, "Today", Icons.Filled.Today, Icons.Outlined.Today),
    BottomNavItem(
        PrismTaskRoute.TaskList.route,
        "Tasks",
        Icons.AutoMirrored.Filled.FormatListBulleted,
        Icons.AutoMirrored.Outlined.FormatListBulleted
    ),
    BottomNavItem(PrismTaskRoute.HabitList.route, "Daily", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
    BottomNavItem(PrismTaskRoute.HabitsRecurring.route, "Recurring", Icons.Filled.Repeat, Icons.Outlined.Repeat),
    BottomNavItem(PrismTaskRoute.Timer.route, "Timer", Icons.Filled.Timer, Icons.Outlined.Timer),
    BottomNavItem(PrismTaskRoute.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
)

private const val NAV_ANIM_DURATION = 300

@Composable
fun PrismTaskNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    tabOrder: List<String> = ALL_BOTTOM_NAV_ITEMS.map { it.route },
    hiddenTabs: Set<String> = emptySet(),
    initialLaunchAction: String? = null,
    initialSharedText: String? = null,
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
    // v1.4.0 V9: route incoming shared text into the Paste Conversation
    // screen with a pre-filled input. The screen observes its
    // SavedStateHandle for the "shared_text" arg and forwards it to
    // PasteConversationViewModel on first composition.
    // Track the shared text in mutable state so the NavHost can clear it
    // after the destination consumes it. Null when there's nothing to
    // forward.
    var pendingSharedText by androidx.compose.runtime.remember(initialSharedText) {
        androidx.compose.runtime.mutableStateOf(initialSharedText)
    }
    LaunchedEffect(initialSharedText) {
        if (!initialSharedText.isNullOrBlank()) {
            navController.navigate(PrismTaskRoute.PasteConversation.route)
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
    val focusRequester = androidx.compose.runtime.remember {
        androidx.compose.ui.focus
            .FocusRequester()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
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
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    true
                }
                androidx.compose.ui.input.key.Key.Two -> {
                    if (bottomNavItems.size > 1) {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    }
                    true
                }
                androidx.compose.ui.input.key.Key.Three -> {
                    if (bottomNavItems.size > 2) {
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    }
                    true
                }
                androidx.compose.ui.input.key.Key.Four -> {
                    if (bottomNavItems.size > 3) {
                        coroutineScope.launch { pagerState.animateScrollToPage(3) }
                    }
                    true
                }
                androidx.compose.ui.input.key.Key.Escape -> {
                    navController.popBackStack()
                    true
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
                            label = {
                                Text(
                                    text = item.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            },
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
                            onVoiceAutoStartConsumed = { autoStartVoice.value = false },
                            onNavigateToHabits = {
                                val habitIndex = bottomNavItems.indexOfFirst {
                                    it.route == PrismTaskRoute.HabitList.route
                                }
                                if (habitIndex >= 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(habitIndex)
                                    }
                                }
                            }
                        )
                        PrismTaskRoute.TaskList.route -> TaskListScreen(navController)
                        PrismTaskRoute.HabitList.route -> HabitListScreen(navController, filter = "daily")
                        PrismTaskRoute.HabitsRecurring.route -> HabitListScreen(navController, filter = "recurring")
                        PrismTaskRoute.Timer.route -> TimerScreen(navController)
                        PrismTaskRoute.Settings.route -> SettingsScreen(navController)
                    }
                }
            }

            featureRoutes(navController, initialSharedText = pendingSharedText)
        }
    }
}
