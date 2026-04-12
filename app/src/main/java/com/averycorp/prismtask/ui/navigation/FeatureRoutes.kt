package com.averycorp.prismtask.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

import com.averycorp.prismtask.ui.screens.auth.AuthScreen
import com.averycorp.prismtask.ui.screens.auth.AuthViewModel
import com.averycorp.prismtask.ui.screens.onboarding.OnboardingScreen
import com.averycorp.prismtask.ui.screens.onboarding.OnboardingViewModel
import com.averycorp.prismtask.ui.screens.addedittask.AddEditTaskScreen
import com.averycorp.prismtask.ui.screens.archive.ArchiveScreen
import com.averycorp.prismtask.ui.screens.habits.AddEditHabitScreen
import com.averycorp.prismtask.ui.screens.analytics.TaskAnalyticsScreen
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
import com.averycorp.prismtask.ui.screens.balance.WeeklyBalanceReportScreen
import com.averycorp.prismtask.ui.screens.briefing.DailyBriefingScreen
import com.averycorp.prismtask.ui.screens.checkin.MorningCheckInScreen
import com.averycorp.prismtask.ui.screens.extract.PasteConversationScreen
import com.averycorp.prismtask.ui.screens.medication.MedicationRefillScreen
import com.averycorp.prismtask.ui.screens.mood.MoodAnalyticsScreen
import com.averycorp.prismtask.ui.screens.review.WeeklyReviewScreen
import com.averycorp.prismtask.ui.screens.eisenhower.EisenhowerScreen
import com.averycorp.prismtask.ui.screens.planner.WeeklyPlannerScreen
import com.averycorp.prismtask.ui.screens.pomodoro.SmartPomodoroScreen
import com.averycorp.prismtask.ui.screens.templates.AddEditTemplateScreen
import com.averycorp.prismtask.ui.screens.chat.ChatScreen
import com.averycorp.prismtask.ui.screens.feedback.BugReportScreen
import com.averycorp.prismtask.ui.screens.feedback.BugReportViewModel
import com.averycorp.prismtask.ui.screens.feedback.MyReportsScreen
import com.averycorp.prismtask.ui.screens.templates.TemplateListScreen
import com.averycorp.prismtask.ui.screens.timer.TimerScreen

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
                route = PrismTaskRoute.MyReports.route,
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
                MyReportsScreen(navController)
            }
}
