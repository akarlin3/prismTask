package com.averycorp.prismtask.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.averycorp.prismtask.ui.screens.addedittask.AddEditTaskScreen
import com.averycorp.prismtask.ui.screens.analytics.TaskAnalyticsScreen
import com.averycorp.prismtask.ui.screens.archive.ArchiveScreen
import com.averycorp.prismtask.ui.screens.auth.AuthScreen
import com.averycorp.prismtask.ui.screens.auth.AuthViewModel
import com.averycorp.prismtask.ui.screens.balance.WeeklyBalanceReportScreen
import com.averycorp.prismtask.ui.screens.briefing.DailyBriefingScreen
import com.averycorp.prismtask.ui.screens.chat.ChatScreen
import com.averycorp.prismtask.ui.screens.checkin.MorningCheckInScreen
import com.averycorp.prismtask.ui.screens.debug.DebugLogScreen
import com.averycorp.prismtask.ui.screens.eisenhower.EisenhowerScreen
import com.averycorp.prismtask.ui.screens.extract.PasteConversationScreen
import com.averycorp.prismtask.ui.screens.feedback.BugReportScreen
import com.averycorp.prismtask.ui.screens.feedback.BugReportViewModel
import com.averycorp.prismtask.ui.screens.habits.AddEditHabitScreen
import com.averycorp.prismtask.ui.screens.habits.HabitAnalyticsScreen
import com.averycorp.prismtask.ui.screens.habits.HabitDetailScreen
import com.averycorp.prismtask.ui.screens.leisure.LeisureScreen
import com.averycorp.prismtask.ui.screens.medication.MedicationLogScreen
import com.averycorp.prismtask.ui.screens.medication.MedicationRefillScreen
import com.averycorp.prismtask.ui.screens.medication.MedicationScreen
import com.averycorp.prismtask.ui.screens.monthview.MonthViewScreen
import com.averycorp.prismtask.ui.screens.mood.MoodAnalyticsScreen
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
import com.averycorp.prismtask.ui.screens.planner.WeeklyPlannerScreen
import com.averycorp.prismtask.ui.screens.pomodoro.SmartPomodoroScreen
import com.averycorp.prismtask.ui.screens.projects.AddEditProjectScreen
import com.averycorp.prismtask.ui.screens.projects.ProjectListScreen
import com.averycorp.prismtask.ui.screens.review.WeeklyReviewScreen
import com.averycorp.prismtask.ui.screens.schoolwork.AddEditCourseScreen
import com.averycorp.prismtask.ui.screens.schoolwork.SchoolworkScreen
import com.averycorp.prismtask.ui.screens.search.SearchScreen
import com.averycorp.prismtask.ui.screens.selfcare.SelfCareScreen
import com.averycorp.prismtask.ui.screens.settings.AccessibilityScreen
import com.averycorp.prismtask.ui.screens.settings.AccountSyncScreen
import com.averycorp.prismtask.ui.screens.settings.AiFeaturesScreen
import com.averycorp.prismtask.ui.screens.settings.AppearanceScreen
import com.averycorp.prismtask.ui.screens.settings.BrainModeScreen
import com.averycorp.prismtask.ui.screens.settings.CalendarScreen
import com.averycorp.prismtask.ui.screens.settings.DataBackupScreen
import com.averycorp.prismtask.ui.screens.settings.FocusTimerScreen
import com.averycorp.prismtask.ui.screens.settings.HabitsStreaksScreen
import com.averycorp.prismtask.ui.screens.settings.LayoutScreen
import com.averycorp.prismtask.ui.screens.settings.LifeModesScreen
import com.averycorp.prismtask.ui.screens.settings.NotificationsScreen
import com.averycorp.prismtask.ui.screens.settings.SubscriptionScreen
import com.averycorp.prismtask.ui.screens.settings.TaskDefaultsScreen
import com.averycorp.prismtask.ui.screens.settings.WellbeingScreen
import com.averycorp.prismtask.ui.screens.tags.TagManagementScreen
import com.averycorp.prismtask.ui.screens.templates.AddEditTemplateScreen
import com.averycorp.prismtask.ui.screens.templates.TemplateListScreen
import com.averycorp.prismtask.ui.screens.timeline.TimelineScreen
import com.averycorp.prismtask.ui.screens.weekview.WeekViewScreen

private const val NAV_ANIM_DURATION = 300

/**
 * Feature route definitions extracted from PrismTaskNavGraph.
 * Every destination here closes only over [navController]; no other
 * NavGraph-local state is required.
 */
internal fun NavGraphBuilder.featureRoutes(
    navController: NavHostController,
    initialSharedText: String? = null
) {
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
        route = PrismTaskRoute.TaskAnalytics.route,
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
        TaskAnalyticsScreen(navController)
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
        route = PrismTaskRoute.MorningCheckIn.route,
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
        MorningCheckInScreen(navController)
    }

    composable(route = PrismTaskRoute.MoodAnalytics.route) {
        MoodAnalyticsScreen(navController)
    }

    composable(route = PrismTaskRoute.WeeklyBalanceReport.route) {
        WeeklyBalanceReportScreen(navController)
    }

    composable(route = PrismTaskRoute.PasteConversation.route) {
        PasteConversationScreen(
            navController = navController,
            sharedText = initialSharedText
        )
    }

    composable(route = PrismTaskRoute.MedicationRefill.route) {
        MedicationRefillScreen(navController)
    }

    composable(route = PrismTaskRoute.WeeklyReview.route) {
        WeeklyReviewScreen(navController)
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

    composable(
        route = PrismTaskRoute.BugReport.route,
        arguments = listOf(
            navArgument("fromScreen") {
                type = NavType.StringType
                defaultValue = ""
            }
        ),
        enterTransition = {
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
        popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
        popExitTransition = {
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
        }
    ) {
        BugReportScreen(navController)
    }

    composable(
        route = PrismTaskRoute.FeatureRequest.route,
        enterTransition = {
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
        popEnterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
        popExitTransition = {
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(NAV_ANIM_DURATION)) +
                fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
        }
    ) {
        val viewModel: BugReportViewModel = hiltViewModel()
        androidx.compose.runtime.LaunchedEffect(Unit) {
            viewModel.setIsFeatureRequest(true)
        }
        BugReportScreen(navController, viewModel)
    }

    composable(
        route = PrismTaskRoute.DebugLog.route,
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
        DebugLogScreen(navController)
    }

    // ------------------------------------------------------------------
    // Settings sub-screens — reorganized flat list into category screens.
    // Each row on SettingsScreen routes into one of these.
    // ------------------------------------------------------------------
    settingsSubScreenRoutes(navController)

    // ------------------------------------------------------------------
    // v1.4.0 Notifications Overhaul
    //
    // Hub + 14 sub-screens. They all share the same simple horizontal
    // slide animation — the hub acts as the "home" and each sub-screen
    // is a push off the stack.
    // ------------------------------------------------------------------
    notificationRoutes(navController)
}

/**
 * Settings category sub-screens. Extracted so the transition lambdas
 * infer the correct receiver and the main [featureRoutes] stays compact.
 */
private fun NavGraphBuilder.settingsSubScreenRoutes(navController: NavHostController) {
    val durationTween: androidx.compose.animation.core.TweenSpec<androidx.compose.ui.unit.IntOffset> =
        tween(NAV_ANIM_DURATION)

    listOf<Pair<String, @androidx.compose.runtime.Composable () -> Unit>>(
        "settings/account_sync" to { AccountSyncScreen(navController) },
        "settings/subscription" to { SubscriptionScreen(navController) },
        "settings/appearance" to { AppearanceScreen(navController) },
        "settings/layout" to { LayoutScreen(navController) },
        "settings/task_defaults" to { TaskDefaultsScreen(navController) },
        "settings/habits_streaks" to { HabitsStreaksScreen(navController) },
        "settings/life_modes" to { LifeModesScreen(navController) },
        "settings/focus_timer" to { FocusTimerScreen(navController) },
        "settings/ai_features" to { AiFeaturesScreen(navController) },
        "settings/brain_mode" to { BrainModeScreen(navController) },
        "settings/wellbeing" to { WellbeingScreen(navController) },
        "settings/calendar" to { CalendarScreen(navController) },
        "settings/notifications" to { NotificationsScreen(navController) },
        "settings/accessibility" to { AccessibilityScreen(navController) },
        "settings/data_backup" to { DataBackupScreen(navController) }
    ).forEach { (route, content) ->
        composable(
            route = route,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = durationTween) +
                    fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = durationTween) +
                    fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = durationTween) +
                    fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = durationTween) +
                    fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
            }
        ) { content() }
    }
}

/**
 * Notification hub + 14 sub-screen routes. Extracted into its own
 * extension so the transition lambdas infer the correct
 * `AnimatedContentTransitionScope<NavBackStackEntry>` receiver, and so
 * adding another sub-screen doesn't grow [featureRoutes] any further.
 */
private fun NavGraphBuilder.notificationRoutes(navController: NavHostController) {
    // Shared transition spec — kept local so it closes over NAV_ANIM_DURATION.
    val durationTween: androidx.compose.animation.core.TweenSpec<androidx.compose.ui.unit.IntOffset> =
        tween(NAV_ANIM_DURATION)

    composable(
        route = PrismTaskRoute.NotificationsHub.route,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = durationTween) +
                fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = durationTween) +
                fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = durationTween) +
                fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = durationTween) +
                fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
        }
    ) { NotificationsHubScreen(navController) }

    listOf<Pair<String, @androidx.compose.runtime.Composable () -> Unit>>(
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
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = durationTween) +
                    fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = durationTween) +
                    fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = durationTween) +
                    fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = durationTween) +
                    fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
            }
        ) { content() }
    }
}
