# CLAUDE.md

## Project Overview

**PrismTask** (`com.averycorp.prismtask`) is a native Android todo list app built with Kotlin and Jetpack Compose. v1.3.0 includes full task management, projects, subtasks, tags, recurrence, reminders, notifications, NLP quick-add, voice input (speech-to-task, voice commands, TTS, hands-free mode), accessibility (TalkBack, font scaling, high-contrast, keyboard nav, reduced motion), Today focus screen (compact header, collapsible sections, customizable layout), tabbed task editor (Details/Schedule/Organize), week/month/timeline views, urgency scoring with user-configurable weights, smart suggestions, drag-to-reorder with custom sort, quick reschedule, duplicate task, bulk edit (priority/date/tags/project), configurable swipe actions, flagged tasks, task templates with built-ins and NLP shortcuts, project and habit templates, saved filter presets, advanced recurrence (weekday/biweekly/custom month days/after-completion), notification profiles with quiet hours and daily digest, three-tier pricing (Free/Pro/Premium) with Google Play Billing, Firebase cloud sync, Google Sign-In, JSON/CSV data export/import, Google Drive backup/restore, habit tracking with streaks/analytics, bookable habits, productivity dashboard with burndown charts and heatmap, time tracking per task, 7 home-screen widgets (Today, Habit Streak, Quick-Add, Calendar, Productivity, Timer, Upcoming) with per-instance config, Gmail/Slack/Calendar/Zapier integrations, app self-update, and a FastAPI web backend with Claude Haiku-powered NLP parsing.

**v1.4.0 (in progress):** The release expands PrismTask into a wellness-aware productivity layer on top of the v1.3 core:

- **Work-Life Balance Engine (V1)**: `LifeCategory` enum per task (Work/Personal/Self-Care/Health/Uncategorized), keyword-based `LifeCategoryClassifier`, `BalanceTracker` for ratio/overload computation, a Today-screen balance bar section, Organize-tab life-category chips, NLP category tags (`#work`, `#self-care`, `#personal`, `#health`), filter-panel category multi-select, a dedicated `WeeklyBalanceReportScreen`, and a Settings section with target-ratio sliders, auto-classify toggle, balance-bar toggle, and overload-threshold slider. Room migration 32 → 33 adds `tasks.life_category`; `OverloadCheckWorker` runs periodic overload checks.
- **Mood & energy tracking**: `MoodEnergyLogEntity` + `MoodCorrelationEngine` power a dedicated Mood Analytics screen that correlates mood/energy with task completion, habits, and life categories.
- **Morning check-in & weekly review**: `CheckInLogEntity`, `MorningCheckInResolver`, and `WeeklyReviewAggregator` drive guided daily check-ins and end-of-week reflections, surfaced via new `checkin/` and `review/` feature modules and a Check-In Streak settings section.
- **Boundaries & overload protection**: `BoundaryRuleEntity` + `BoundaryRuleParser` + `BoundaryEnforcer` let users declare work-hours / category limits; `BurnoutScorer` and `ProfileAutoSwitcher` auto-adjust notification profiles when overload is detected.
- **Focus Release & ND-friendly modes**: `FocusReleaseLogEntity`, `GoodEnoughTimerManager`, `ParalysisBreaker`, `EnergyAwarePomodoro`, and `ShipItCelebrationManager` provide neurodivergence-friendly focus flows; `NdPreferences` + `NdFeatureGate` gate these features, with Brain Mode / UI Complexity / Forgiveness-Streak / Shake-to-capture settings sections.
- **Medication refills, clinical report, conversation extraction**: `MedicationRefillEntity` + `RefillCalculator` project refill dates; `ClinicalReportGenerator` exports a therapist-friendly summary; `ConversationTaskExtractor` pulls tasks out of chat transcripts (new `extract/` screen).
- **Custom notification sounds + escalation**: `CustomSoundEntity`, `SoundResolver`, `EscalationScheduler`, and `VibrationAdapter` power per-profile custom sounds, vibration patterns, and escalation chains; `ReminderProfile*` was renamed to `NotificationProfile*` and moved under `domain/model/notifications/`.
- **Database**: Current Room version is **42** with 41 cumulative migrations (`MIGRATION_1_2` through `MIGRATION_41_42`) wired into `PrismTaskDatabase`.

## Tech Stack

- **Language**: Kotlin 2.3.20 (JVM target 21)
- **UI**: Jetpack Compose with Material 3 (BOM 2024.12.01)
- **DI**: Hilt (Dagger) 2.59.2
- **Database**: Room 2.8.4 with KSP 2.3.6
- **Navigation**: Jetpack Navigation Compose 2.8.5
- **Serialization**: Gson 2.11.0 (for RecurrenceRule JSON)
- **Cloud**: Firebase Auth + Firestore + Storage (BOM 33.12.0), Google Drive API v3
- **Auth**: Credential Manager + Google Identity
- **Drag-to-Reorder**: sh.calvin.reorderable 2.4.3
- **Widgets**: Glance for Compose 1.1.0
- **Billing**: Google Play Billing 7.1.1
- **Testing**: JUnit 4.13.2, kotlinx-coroutines-test 1.9.0, Turbine 1.1.0, MockK 1.13.13, Robolectric 4.13, Hilt Testing 2.59.2
- **Build**: Gradle 9.3.1 with Kotlin DSL, AGP 9.1.0
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 35 (Android 15)

## Project Structure

```
app/src/main/java/com/averycorp/prismtask/
├── MainActivity.kt                     # Single-activity entry point, notification permission
├── PrismTaskApplication.kt             # @HiltAndroidApp
├── data/
│   ├── billing/
│   │   └── BillingManager.kt           # Google Play Billing: three-tier purchase flow, restore, cached status
│   ├── calendar/
│   │   ├── CalendarManager.kt          # Device calendar provider wrapper
│   │   └── CalendarSyncPreferences.kt
│   ├── export/
│   │   ├── DataExporter.kt             # Full JSON export (all entities + config) + CSV
│   │   ├── DataImporter.kt             # Full JSON import with merge/replace
│   │   └── EntityJsonMerger.kt         # Entity-level merge helper
│   ├── local/
│   │   ├── converter/
│   │   │   └── RecurrenceConverter.kt  # Gson JSON ↔ RecurrenceRule
│   │   ├── dao/                       # Room DAOs
│   │   │   ├── TaskDao.kt, ProjectDao.kt, TagDao.kt, AttachmentDao.kt
│   │   │   ├── UsageLogDao.kt, SyncMetadataDao.kt, CalendarSyncDao.kt
│   │   │   ├── HabitDao.kt, HabitCompletionDao.kt, HabitLogDao.kt
│   │   │   ├── HabitTemplateDao.kt, TaskTemplateDao.kt, ProjectTemplateDao.kt
│   │   │   ├── NlpShortcutDao.kt, SavedFilterDao.kt, NotificationProfileDao.kt
│   │   │   ├── SelfCareDao.kt, LeisureDao.kt, SchoolworkDao.kt
│   │   │   ├── TaskCompletionDao.kt        # Task completion history queries
│   │   │   ├── BoundaryRuleDao.kt, CheckInLogDao.kt, CustomSoundDao.kt
│   │   │   ├── FocusReleaseLogDao.kt, MedicationRefillDao.kt
│   │   │   ├── MoodEnergyLogDao.kt, WeeklyReviewDao.kt
│   │   ├── database/
│   │   │   ├── PrismTaskDatabase.kt    # Room DB (@Database version = 42)
│   │   │   └── Migrations.kt           # MIGRATION_1_2 … MIGRATION_41_42
│   │   └── entity/                     # Room entities
│   │       ├── TaskEntity.kt, ProjectEntity.kt, TagEntity.kt
│   │       ├── TaskTagCrossRef.kt, TaskWithTags.kt, AttachmentEntity.kt
│   │       ├── UsageLogEntity.kt, SyncMetadataEntity.kt, CalendarSyncEntity.kt
│   │       ├── HabitEntity.kt, HabitCompletionEntity.kt, HabitLogEntity.kt (bookable)
│   │       ├── HabitTemplateEntity.kt, TaskTemplateEntity.kt, ProjectTemplateEntity.kt
│   │       ├── NlpShortcutEntity.kt, SavedFilterEntity.kt, NotificationProfileEntity.kt
│   │       ├── SelfCareLogEntity.kt, SelfCareStepEntity.kt, StudyLogEntity.kt
│   │       ├── TaskCompletionEntity.kt     # Task completion history record
│   │       ├── LeisureLogEntity.kt, CourseEntity.kt, AssignmentEntity.kt, CourseCompletionEntity.kt
│   │       ├── BoundaryRuleEntity.kt, CheckInLogEntity.kt, CustomSoundEntity.kt
│   │       ├── FocusReleaseLogEntity.kt, MedicationRefillEntity.kt
│   │       ├── MoodEnergyLogEntity.kt, WeeklyReviewEntity.kt
│   ├── preferences/                    # DataStore preferences
│   │   ├── UserPreferencesDataStore.kt # Centralized customization settings
│   │   ├── ThemePreferences.kt, ArchivePreferences.kt, SortPreferences.kt
│   │   ├── DashboardPreferences.kt, ProStatusPreferences.kt, HabitListPreferences.kt
│   │   ├── TaskBehaviorPreferences.kt, TemplatePreferences.kt, TimerPreferences.kt
│   │   ├── VoicePreferences.kt, A11yPreferences.kt, OnboardingPreferences.kt
│   │   ├── TabPreferences.kt, LeisurePreferences.kt, MedicationPreferences.kt
│   │   ├── CalendarPreferences.kt, BackendSyncPreferences.kt, CoachingPreferences.kt
│   │   ├── AuthTokenPreferences.kt, NotificationPreferences.kt
│   │   ├── MorningCheckInPreferences.kt, ShakePreferences.kt
│   │   ├── FocusReleaseEnums.kt, NdPreferences.kt, NdPreferencesDataStore.kt, NdFeatureGate.kt
│   ├── remote/
│   │   ├── AuthManager.kt              # Firebase Auth + Google Sign-In
│   │   ├── GoogleDriveService.kt       # Drive backup/restore
│   │   ├── SyncService.kt              # Firestore push/pull/real-time
│   │   ├── CalendarSyncService.kt      # Google Calendar two-way sync
│   │   ├── ClaudeParserService.kt      # Backend NLP parse HTTP client
│   │   ├── AppUpdater.kt, UpdateChecker.kt, SyncTracker.kt
│   │   ├── api/                        # Retrofit backend client
│   │   │   ├── ApiClient.kt, ApiModels.kt, PrismTaskApi.kt
│   │   ├── mapper/
│   │   │   └── SyncMapper.kt           # Entity ↔ Firestore docs
│   │   └── sync/                       # Backend sync split
│   │       ├── BackendSyncService.kt, BackendSyncMappers.kt, SyncModels.kt
│   ├── repository/                     # All repositories
│   │   ├── TaskRepository.kt, ProjectRepository.kt, TagRepository.kt, AttachmentRepository.kt
│   │   ├── TaskCompletionRepository.kt     # Task completion recording + analytics stats
│   │   ├── HabitRepository.kt, HabitTemplateRepository.kt, TaskTemplateRepository.kt
│   │   ├── ProjectTemplateRepository.kt, SavedFilterRepository.kt, NlpShortcutRepository.kt
│   │   ├── NotificationProfileRepository.kt, ChatRepository.kt, CoachingRepository.kt
│   │   ├── SelfCareRepository.kt, LeisureRepository.kt, SchoolworkRepository.kt
│   │   ├── BoundaryRuleRepository.kt, CheckInLogRepository.kt, CustomSoundRepository.kt
│   │   ├── MedicationRefillRepository.kt, MoodEnergyRepository.kt
│   │   ├── SyllabusRepository.kt, WeeklyReviewRepository.kt
│   └── seed/                           # Built-in content seeders
├── di/
│   ├── DatabaseModule.kt, BillingModule.kt, NetworkModule.kt, PreferencesModule.kt
├── diagnostics/                        # Crash/event diagnostics helpers
├── domain/
│   ├── model/
│   │   ├── RecurrenceRule.kt, TaskFilter.kt, LifeCategory.kt, BoundaryRule.kt
│   │   ├── TaskCardDisplayConfig.kt, TaskMenuAction.kt, TodaySection.kt
│   │   ├── SelfCareRoutine.kt, BugReport.kt, UiComplexityTier.kt, UserPreferenceEnums.kt
│   │   └── notifications/              # NotificationProfile, EscalationChain,
│   │                                   #   QuietHoursWindow, BuiltInSound, VibrationPatterns
│   └── usecase/
│       ├── RecurrenceEngine.kt, NaturalLanguageParser.kt, ParsedTaskResolver.kt
│       ├── UrgencyScorer.kt, SuggestionEngine.kt, StreakCalculator.kt
│       ├── ProFeatureGate.kt           # Three-tier access control
│       ├── VoiceInputManager.kt, VoiceCommandParser.kt, TextToSpeechManager.kt
│       ├── SmartDefaultsEngine.kt, NlpShortcutExpander.kt, QuietHoursDeferrer.kt
│       ├── ChecklistParser.kt, TodoListParser.kt, DateShortcuts.kt
│       ├── NotificationProfileResolver.kt, AntiReworkGuard.kt
│       ├── LifeCategoryClassifier.kt, BalanceTracker.kt, BurnoutScorer.kt
│       ├── BoundaryEnforcer.kt, BoundaryRuleParser.kt, ProfileAutoSwitcher.kt
│       ├── MoodCorrelationEngine.kt, MorningCheckInResolver.kt, WeeklyReviewAggregator.kt
│       ├── EnergyAwarePomodoro.kt, GoodEnoughTimerManager.kt, ParalysisBreaker.kt
│       ├── ShipItCelebrationManager.kt, SelfCareNudgeEngine.kt
│       ├── ConversationTaskExtractor.kt, DuplicateCleanupPlanner.kt
│       ├── RefillCalculator.kt, ClinicalReportGenerator.kt
│       ├── ScreenshotCapture.kt, ShakeDetector.kt
├── notifications/
│   ├── NotificationHelper.kt, ReminderScheduler.kt, ReminderBroadcastReceiver.kt
│   ├── EscalationScheduler.kt, EscalationBroadcastReceiver.kt
│   ├── SoundResolver.kt, VibrationAdapter.kt, ExactAlarmHelper.kt, NotificationTester.kt
│   ├── CompleteTaskReceiver.kt, BootReceiver.kt, OverloadCheckWorker.kt
│   ├── WeeklyHabitSummary.kt, WeeklySummaryWorker.kt, HabitNotificationUtils.kt
│   ├── HabitFollowUpReceiver.kt, HabitFollowUpDismissReceiver.kt
│   ├── BriefingNotificationWorker.kt, EveningSummaryWorker.kt, ReengagementWorker.kt
│   ├── MedicationReminderScheduler.kt, MedicationReminderReceiver.kt
│   ├── MedStepReminderReceiver.kt, LogMedicationReceiver.kt, PomodoroTimerService.kt
├── widget/                             # 7 Glance widgets with per-instance config
│   ├── TodayWidget.kt, HabitStreakWidget.kt, QuickAddWidget.kt
│   ├── CalendarWidget.kt, ProductivityWidget.kt, TimerWidget.kt, UpcomingWidget.kt
│   ├── WidgetActions.kt, WidgetColors.kt, WidgetTextStyles.kt, WidgetEmptyState.kt
│   ├── WidgetConfigDataStore.kt, WidgetDataProvider.kt, WidgetUpdateManager.kt
│   ├── WidgetRefreshWorker.kt, TimerStateDataStore.kt
├── workers/                            # Background WorkManager workers
├── util/, utils/                       # Shared helpers
└── ui/
    ├── a11y/                           # Accessibility helpers (TalkBack, font scaling, contrast)
    ├── components/                     # Shared composables
    │   ├── SubtaskSection.kt, RecurrenceSelector.kt, EmptyState.kt, FilterPanel.kt
    │   ├── HighlightedText.kt, TagSelector.kt, QuickAddBar.kt, QuickAddViewModel.kt
    │   ├── ProBadge.kt, ProUpgradePrompt.kt, StreakBadge.kt
    │   ├── ContributionGrid.kt, WeeklyProgressDots.kt, QuickReschedulePopup.kt
    │   └── settings/                   # Shared settings-screen composables
    ├── navigation/
    │   ├── NavGraph.kt                 # Top-level NavHost
    │   └── FeatureRoutes.kt            # Feature group route definitions
    ├── screens/
    │   ├── auth/, today/, tasklist/, addedittask/, projects/
    │   ├── weekview/, monthview/, timeline/, search/, archive/
    │   ├── tags/, templates/, habits/, settings/
    │   ├── today/components/           # PlanForTodaySheet + TodayComponents
    │   ├── tasklist/components/        # Extracted task list components
    │   ├── addedittask/tabs/           # DetailsTab, ScheduleTab, OrganizeTab
    │   ├── settings/sections/          # 35 extracted settings sections (Accessibility,
    │   │                               #   SwipeActions, Voice, TaskDefaults, DebugTier,
    │   │                               #   Subscription, Appearance, AI, WorkLifeBalance,
    │   │                               #   Boundaries, Modes, BrainMode, CheckInStreak,
    │   │                               #   ClinicalReport, ForgivenessStreak, FocusRelease,
    │   │                               #   Shake, UiComplexity, DebugLogAdmin, etc.)
    │   ├── habits/components/, templates/components/
    │   ├── leisure/, leisure/components/
    │   ├── selfcare/, selfcare/components/
    │   ├── medication/, medication/components/
    │   ├── schoolwork/, briefing/, chat/, coaching/
    │   ├── eisenhower/, pomodoro/, planner/, timer/, onboarding/
    │   ├── analytics/                  # TaskAnalyticsScreen + TaskAnalyticsViewModel
    │   ├── balance/                    # WeeklyBalanceReportScreen + life-category visualizations
    │   ├── mood/                       # MoodAnalyticsScreen + mood/energy correlation views
    │   ├── checkin/                    # MorningCheckInScreen + check-in streak UI
    │   ├── review/                     # Weekly review flow screens
    │   ├── extract/                    # ConversationTaskExtractor inbox
    │   ├── notifications/              # Notification profile editor, escalation, custom sounds
    │   ├── feedback/, debug/
    └── theme/
        ├── Color.kt, Theme.kt, Type.kt, PriorityColors.kt, LifeCategoryColors.kt
```

## Architecture

- **Single Activity**: `MainActivity` with `@AndroidEntryPoint`, notification permission request
- **MVVM**: ViewModels → Repositories → Room DAOs, all connected via Hilt
- **Compose-only UI**: No XML layouts; entire UI is Jetpack Compose
- **Material 3 theming**: Dynamic colors on Android 12+, static light/dark fallback
- **Edge-to-edge**: Uses `enableEdgeToEdge()`
- **Reactive data**: Room returns `Flow<T>`, ViewModels expose `StateFlow<T>` via `stateIn()`
- **Recurrence**: On task completion, `RecurrenceEngine` calculates next due date; a new task is inserted automatically
- **Reminders**: `AlarmManager` schedules `BroadcastReceiver` triggers; notifications have "Complete" action
- **NLP Quick-Add**: `NaturalLanguageParser` extracts dates, tags (#), projects (@), priority (!), recurrence from text
- **Bottom Navigation**: 5 tabs (Today, Tasks, Projects, Habits, Settings); detail screens hide nav bar
- **Today Focus**: Progress ring, overdue/today/planned sections, plan-for-today sheet
- **Urgency Scoring**: `UrgencyScorer` computes 0–1 score from due date, priority, age, subtask progress
- **Smart Suggestions**: `SuggestionEngine` suggests tags/projects based on usage log keyword matching
- **Cloud Sync**: Firebase Firestore for cross-device sync, `SyncService` with push/pull/real-time listeners
- **Auth**: Google Sign-In via Credential Manager, optional (local-only mode supported)
- **Timeline**: Daily view with scheduled time blocks, duration management, current time indicator
- **Export/Import**: JSON full backup (tasks, habits, habit completions, self-care logs/steps, leisure logs, courses, assignments, course completions, all preferences/config) + CSV tasks export; JSON import with merge/replace modes; Google Drive backup/restore via Drive API v3
- **Habits**: Habit tracking with daily/weekly frequency, streaks, analytics, contribution grid, weekly summary notification
- **Widgets**: 7 Glance-based home screen widgets (Today, Habit Streak, Quick-Add, Calendar, Productivity, Timer, Upcoming) with per-instance configuration
- **Dashboard**: Customizable Today section order and visibility via DashboardPreferences DataStore
- **Task Templates**: Reusable blueprints with backend sync and NLP shortcut (`/templatename`); also project and habit templates
- **Tabbed Editor**: Bottom sheet with Details/Schedule/Organize tabs (extracted into `addedittask/tabs/`)
- **Sort Memory**: Per-screen sort preferences via DataStore
- **Drag-to-Reorder**: Custom sort mode with persistent task order
- **Three-Tier Pricing**: ProFeatureGate checks BillingManager tier (Free/Pro $3.99/Premium $7.99); Free gets core features, Pro unlocks AI Eisenhower/Pomodoro + cloud sync + analytics, Premium adds briefing/planner/integrations/collaboration
- **Billing**: Google Play Billing via BillingManager singleton; tier cached in DataStore for offline access; debug tier override in Settings
- **Voice Input**: `VoiceInputManager` wraps Android SpeechRecognizer for dictation and continuous hands-free mode; `VoiceCommandParser` parses command grammar; `TextToSpeechManager` reads tasks and briefings
- **Accessibility**: `ui/a11y/` helpers expose TalkBack labels, dynamic font scaling, high-contrast mode, keyboard focus traversal, and reduced-motion animation gates
- **Customization**: `UserPreferencesDataStore` centralizes configurable swipe actions, urgency weights, task card fields, accent colors, card corner radius, compact mode, NLP shortcuts, saved filters, context menu ordering, and Today-screen layout
- **Notification Profiles**: `NotificationProfileRepository` supports multi-reminder bundles with escalation chains (`EscalationScheduler`), custom per-profile sounds (`CustomSoundEntity` + `SoundResolver`), and vibration patterns (`VibrationAdapter`); `QuietHoursDeferrer` defers notifications during quiet hours; `ProfileAutoSwitcher` rotates active profile based on burnout signals; daily digest notification
- **Analytics**: Productivity dashboard with daily/weekly/monthly views, burndown charts, habit-productivity correlation, heatmap visualization, per-task time tracking
- **Task Analytics**: Contribution grid, streak tracking, day-of-week/hour-of-day distributions, completion rate, on-time rate, and per-project filtering for completed tasks via `TaskCompletionEntity` history table (added in migration 37→38 with backfill; DB is currently at version 42)
- **Integrations**: Gmail starred-email sync, Slack message-to-task, Google Calendar prep-task generation, webhook/Zapier endpoint; a suggestion inbox reviews auto-created tasks
- **Bookable Habits**: Habit logs carry booking state via `HabitLogEntity` for activity history
- **Work-Life Balance**: `LifeCategory` enum on every task; `LifeCategoryClassifier` auto-tags tasks from keywords; `BalanceTracker` computes category ratios and detects overload; `OverloadCheckWorker` runs periodic checks; dedicated Today balance bar and `WeeklyBalanceReportScreen`
- **Mood / Check-In / Review**: `MoodEnergyLogEntity` + `MoodCorrelationEngine` power Mood Analytics; `CheckInLogEntity` + `MorningCheckInResolver` drive morning check-ins with streaks; `WeeklyReviewEntity` + `WeeklyReviewAggregator` drive guided weekly reviews
- **Boundaries**: `BoundaryRuleEntity` + `BoundaryRuleParser` + `BoundaryEnforcer` enforce user-declared work-hours / category limits; `BurnoutScorer` surfaces risk scores
- **ND-Friendly Modes**: `NdFeatureGate` + `NdPreferences` gate Brain Mode, UI Complexity, Forgiveness Streak, Focus Release (`FocusReleaseLogEntity`, `GoodEnoughTimerManager`, `ParalysisBreaker`, `EnergyAwarePomodoro`, `ShipItCelebrationManager`), and Shake-to-capture (`ShakeDetector` + `ScreenshotCapture`)
- **Medication Refills + Clinical Report**: `MedicationRefillEntity` + `RefillCalculator` project refill dates; `ClinicalReportGenerator` exports a therapist-friendly summary
- **Conversation Extraction**: `ConversationTaskExtractor` pulls tasks from chat transcripts into a dedicated review inbox

## CI Failure Logs

Workflow failures are auto-committed to the `ci-logs` orphan branch of this public repo. Fetch the relevant log directly (no auth needed) instead of asking the user to paste CI output:

- Android:    https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/android-ci/latest.log
- Backend:    https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/backend-ci/latest.log
- Web:        https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/web-ci/latest.log
- Release:    https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/release/latest.log
- Auto-merge: https://raw.githubusercontent.com/Akarlin3/PrismTask/ci-logs/ci-logs/auto-merge/latest.log

Historical failures: `ci-logs/<workflow-slug>/<timestamp>-<run-id>.log` on the same branch. See [`CI_LOGS.md`](CI_LOGS.md) for details.

## Build Commands

**Note:** The Android SDK is not available in the Claude Code environment. Do not attempt local builds or tests. Instead, push your changes and wait for GitHub CI to build and report results.

```bash
# Debug build
./gradlew assembleDebug

# Release build (R8 minification + resource shrinking enabled)
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumentation tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Clean
./gradlew clean
```

## Key Conventions

- **Theme**: Use `PrismTaskTheme` as the root composable wrapper
- **No XML layouts**: All UI must be Jetpack Compose
- **JVM target**: 21 — do not change without updating both `compileOptions` and `kotlinOptions`
- **Entity fields**: Use `@ColumnInfo` with snake_case column names
- **Recurrence**: Stored as JSON string in `TaskEntity.recurrenceRule`, parsed via `RecurrenceConverter` (Gson)
- **Reminders**: `reminderOffset` is millis before due date; scheduling handled by `ReminderScheduler`
- **Priority levels**: 0=None, 1=Low, 2=Medium, 3=High, 4=Urgent; colors in `PriorityColors`
- **Error handling**: ViewModels catch exceptions and surface via `SnackbarHostState` or `SharedFlow<String>`
- **Capitalization**: Use Title Capitalization in all user-facing strings throughout the app (screen titles, tab labels, button labels, section headers, menu items, dialog titles, empty states, notifications, etc.). Capitalize the first letter of each major word.

## Important Files

- `build.gradle.kts` — Root build file with plugin versions (AGP 9.1.0, Kotlin 2.3.20, KSP 2.3.6, Hilt 2.59.2)
- `app/build.gradle.kts` — App module dependencies, build config, ProGuard/R8 settings
- `app/proguard-rules.pro` — Keep rules for Room, Gson, domain models
- `app/src/main/AndroidManifest.xml` — Activity, receivers, permissions
- `app/google-services.json` — Firebase config (placeholder — replace with actual)
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 9.3.1
- `app/src/test/` — 121 unit test files covering NLP, recurrence, urgency, suggestion, streak, export/import, repositories (Task, Habit, Project, Tag, Coaching, NotificationProfile, SavedFilter, MedLogReconcile, TaskCompletion), use cases (ParsedTaskResolver, ChecklistParser, TodoListParser, VoiceCommandParser, SmartDefaults, NlpShortcutExpander, QuietHoursDeferrer, AdvancedRecurrence, TimeBlock, WeeklyPlanner, DailyBriefing, Eisenhower, SmartPomodoro, BookableHabit, BalanceTracker, LifeCategoryClassifier, BurnoutScorer, BoundaryEnforcer, MoodCorrelationEngine, WeeklyReviewAggregator, RefillCalculator, ConversationTaskExtractor, ShakeDetector), DataStore preferences, notification/reminder scheduling, ViewModels (Today, AddEditTask, TaskList, HabitList, Eisenhower, Onboarding, SmartPomodoro, Mood, CheckIn, Balance), TaskCardDisplayConfig/TaskMenuAction model tests, widget data/config-defaults, accessibility, theme, and calendar manager
- `app/src/androidTest/` — 28 instrumentation test files: Task/Project/Habit/Tag DAO tests, recurrence integration, and smoke suites for Navigation, QoL features, Task editor, Templates, Today screen, Data export/import, Views, Search/archive, Tags/projects, Settings, Recurrence, Multi-select/bulk edit, Habits, and Offline edge cases
- `backend/tests/` — 25 pytest files covering dashboard, export, search, app_update, projects routers; recurrence/urgency/NLP edge-case services; and end-to-end integration workflows and stress tests
