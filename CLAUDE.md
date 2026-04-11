# CLAUDE.md

## Project Overview

**PrismTask** (`com.averycorp.prismtask`) is a native Android todo list app built with Kotlin and Jetpack Compose. v1.3.0 includes full task management, projects, subtasks, tags, recurrence, reminders, notifications, NLP quick-add, voice input (speech-to-task, voice commands, TTS, hands-free mode), accessibility (TalkBack, font scaling, high-contrast, keyboard nav, reduced motion), Today focus screen (compact header, collapsible sections, customizable layout), tabbed task editor (Details/Schedule/Organize), week/month/timeline views, urgency scoring with user-configurable weights, smart suggestions, drag-to-reorder with custom sort, quick reschedule, duplicate task, bulk edit (priority/date/tags/project), configurable swipe actions, flagged tasks, task templates with built-ins and NLP shortcuts, project and habit templates, saved filter presets, advanced recurrence (weekday/biweekly/custom month days/after-completion), notification profiles with quiet hours and daily digest, three-tier pricing (Free/Pro/Premium) with Google Play Billing, Firebase cloud sync, Google Sign-In, JSON/CSV data export/import, Google Drive backup/restore, habit tracking with streaks/analytics, bookable habits, productivity dashboard with burndown charts and heatmap, time tracking per task, 7 home-screen widgets (Today, Habit Streak, Quick-Add, Calendar, Productivity, Timer, Upcoming) with per-instance config, Gmail/Slack/Calendar/Zapier integrations, app self-update, and a FastAPI web backend with Claude Haiku-powered NLP parsing.

**v1.4.0 (in progress):** Work-Life Balance Engine phase 1 (V1) adds a `LifeCategory` enum per task (Work/Personal/Self-Care/Health/Uncategorized), a keyword-based `LifeCategoryClassifier`, `BalanceTracker` for ratio/overload computation, a Today-screen balance bar section, Organize-tab life-category chips, NLP category tags (`#work`, `#self-care`, `#personal`, `#health`), filter-panel category multi-select, and a Settings section with target-ratio sliders, auto-classify toggle, balance-bar toggle, and overload-threshold slider. Room migration 32 → 33 adds `tasks.life_category`.

## Tech Stack

- **Language**: Kotlin 2.2.10 (JVM target 21)
- **UI**: Jetpack Compose with Material 3 (BOM 2024.12.01)
- **DI**: Hilt (Dagger) 2.59.2
- **Database**: Room 2.8.4 with KSP
- **Navigation**: Jetpack Navigation Compose 2.8.5
- **Serialization**: Gson 2.11.0 (for RecurrenceRule JSON)
- **Cloud**: Firebase Auth + Firestore + Storage (BOM 33.6.0), Google Drive API v3
- **Auth**: Credential Manager + Google Identity
- **Drag-to-Reorder**: sh.calvin.reorderable 2.4.3
- **Widgets**: Glance for Compose 1.1.0
- **Billing**: Google Play Billing 7.1.1
- **Testing**: JUnit 4.13.2, kotlinx-coroutines-test 1.9.0, Turbine 1.1.0, MockK 1.13.13, Robolectric 4.13, Hilt Testing 2.59.2
- **Build**: Gradle 8.13 with Kotlin DSL
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
│   │   │   ├── NlpShortcutDao.kt, SavedFilterDao.kt, ReminderProfileDao.kt
│   │   │   ├── SelfCareDao.kt, LeisureDao.kt, SchoolworkDao.kt
│   │   ├── database/
│   │   │   ├── PrismTaskDatabase.kt    # Room DB with migrations
│   │   │   └── Migrations.kt           # Grouped migration definitions
│   │   └── entity/                     # Room entities
│   │       ├── TaskEntity.kt, ProjectEntity.kt, TagEntity.kt
│   │       ├── TaskTagCrossRef.kt, TaskWithTags.kt, AttachmentEntity.kt
│   │       ├── UsageLogEntity.kt, SyncMetadataEntity.kt, CalendarSyncEntity.kt
│   │       ├── HabitEntity.kt, HabitCompletionEntity.kt, HabitLogEntity.kt (bookable)
│   │       ├── HabitTemplateEntity.kt, TaskTemplateEntity.kt, ProjectTemplateEntity.kt
│   │       ├── NlpShortcutEntity.kt, SavedFilterEntity.kt, ReminderProfileEntity.kt
│   │       ├── SelfCareLogEntity.kt, SelfCareStepEntity.kt, StudyLogEntity.kt
│   │       ├── LeisureLogEntity.kt, CourseEntity.kt, AssignmentEntity.kt, CourseCompletionEntity.kt
│   ├── preferences/                    # DataStore preferences
│   │   ├── UserPreferencesDataStore.kt # Centralized customization settings
│   │   ├── ThemePreferences.kt, ArchivePreferences.kt, SortPreferences.kt
│   │   ├── DashboardPreferences.kt, ProStatusPreferences.kt, HabitListPreferences.kt
│   │   ├── TaskBehaviorPreferences.kt, TemplatePreferences.kt, TimerPreferences.kt
│   │   ├── VoicePreferences.kt, A11yPreferences.kt, OnboardingPreferences.kt
│   │   ├── TabPreferences.kt, LeisurePreferences.kt, MedicationPreferences.kt
│   │   ├── CalendarPreferences.kt, BackendSyncPreferences.kt, CoachingPreferences.kt
│   │   ├── ApiPreferences.kt, AuthTokenPreferences.kt
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
│   │   ├── HabitRepository.kt, HabitTemplateRepository.kt, TaskTemplateRepository.kt
│   │   ├── ProjectTemplateRepository.kt, SavedFilterRepository.kt, NlpShortcutRepository.kt
│   │   ├── ReminderProfileRepository.kt, ChatRepository.kt, CoachingRepository.kt
│   │   ├── SelfCareRepository.kt, LeisureRepository.kt, SchoolworkRepository.kt
│   └── seed/                           # Built-in content seeders
├── di/
│   ├── DatabaseModule.kt, BillingModule.kt (+ additional Hilt modules)
├── domain/
│   ├── model/
│   │   ├── RecurrenceRule.kt, TaskFilter.kt
│   │   ├── TodayLayoutResolver.kt, TaskCardDisplayConfig.kt, TaskMenuAction.kt
│   └── usecase/
│       ├── RecurrenceEngine.kt, NaturalLanguageParser.kt, ParsedTaskResolver.kt
│       ├── UrgencyScorer.kt, SuggestionEngine.kt, StreakCalculator.kt
│       ├── ProFeatureGate.kt           # Three-tier access control
│       ├── VoiceInputManager.kt, VoiceCommandParser.kt, TextToSpeechManager.kt
│       ├── SmartDefaultsEngine.kt, NlpShortcutExpander.kt, QuietHoursDeferrer.kt
│       ├── ChecklistParser.kt, TodoListParser.kt, DateShortcuts.kt
├── notifications/
│   ├── NotificationHelper.kt, ReminderScheduler.kt, ReminderBroadcastReceiver.kt
│   ├── CompleteTaskReceiver.kt, BootReceiver.kt
│   ├── WeeklyHabitSummary.kt, WeeklySummaryWorker.kt
│   ├── BriefingNotificationWorker.kt, EveningSummaryWorker.kt, ReengagementWorker.kt
│   ├── MedicationReminderScheduler.kt, MedicationReminderReceiver.kt
│   ├── MedStepReminderReceiver.kt, LogMedicationReceiver.kt
├── widget/                             # 7 Glance widgets with per-instance config
│   ├── TodayWidget.kt, HabitStreakWidget.kt, QuickAddWidget.kt
│   ├── CalendarWidget.kt, ProductivityWidget.kt, TimerWidget.kt, UpcomingWidget.kt
│   ├── WidgetActions.kt, WidgetConfigDataStore.kt
│   ├── WidgetDataProvider.kt, WidgetUpdateManager.kt
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
    │   ├── settings/sections/          # 22 extracted settings sections (Accessibility,
    │   │                               #   SwipeActions, Voice, TaskDefaults, DebugTier,
    │   │                               #   Subscription, Appearance, AI, etc.)
    │   ├── habits/components/, templates/components/
    │   ├── leisure/, leisure/components/
    │   ├── selfcare/, selfcare/components/
    │   ├── medication/, medication/components/
    │   ├── schoolwork/, briefing/, chat/, coaching/
    │   ├── eisenhower/, pomodoro/, planner/, timer/, onboarding/
    └── theme/
        ├── Color.kt, Theme.kt, Type.kt, PriorityColors.kt
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
- **Notification Profiles**: `ReminderProfileRepository` supports multi-reminder bundles with escalation; `QuietHoursDeferrer` defers notifications during quiet hours; daily digest notification
- **Analytics**: Productivity dashboard with daily/weekly/monthly views, burndown charts, habit-productivity correlation, heatmap visualization, per-task time tracking
- **Integrations**: Gmail starred-email sync, Slack message-to-task, Google Calendar prep-task generation, webhook/Zapier endpoint; a suggestion inbox reviews auto-created tasks
- **Bookable Habits**: Habit logs carry booking state via `HabitLogEntity` for activity history

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

- `build.gradle.kts` — Root build file with plugin versions (AGP 9.1.0, Kotlin 2.2.10)
- `app/build.gradle.kts` — App module dependencies, build config, ProGuard/R8 settings
- `app/proguard-rules.pro` — Keep rules for Room, Gson, domain models
- `app/src/main/AndroidManifest.xml` — Activity, receivers, permissions
- `app/google-services.json` — Firebase config (placeholder — replace with actual)
- `app/src/test/` — ~490 unit tests spanning NaturalLanguageParser, AppUpdater, StreakCalculator, RecurrenceEngine, TaskFilter, SyncMapper, TaskTemplateRepository, UrgencyScorer (+ weights), EntityJsonMerger, SuggestionEngine, RecurrenceConverter, DateShortcuts, DuplicateTask, HabitRepositoryHelpers, DataExporter, DataImporter, SortPreferences, ProFeatureGate, MoveToProject, TemplateSeeder, ProStatusCache, repository tests (Task, Habit, Project, Tag, Coaching, ReminderProfile, SavedFilter, MedLogReconcile), use case tests (ParsedTaskResolver, ChecklistParser, TodoListParser, VoiceCommandParser, SmartDefaults, NlpShortcutExpander, QuietHoursDeferrer, AdvancedRecurrence, TimeBlock, WeeklyPlanner, DailyBriefing, Eisenhower, SmartPomodoro, BookableHabit), DataStore preferences tests (ThemePreferences, ThemePreferencesRecentColors, UserPreferencesDataStore, DashboardPreferences, ArchivePreferences, SortPreferences), notification/reminder scheduling tests, ViewModel tests (Today, AddEditTask, TaskList, HabitList, Eisenhower, Onboarding, SmartPomodoro), TaskCardDisplayConfig/TaskMenuAction/TodayLayoutResolver model tests, widget data and config-defaults tests, accessibility and theme tests, calendar manager + sync preferences tests
- `app/src/androidTest/` — ~100 instrumentation tests: Task/Project/Habit/Tag DAO tests, recurrence integration, and smoke suites for Navigation, QoL features, Task editor, Templates, Today screen, Data export/import, Views, Search/archive, Tags/projects, Settings, Recurrence, Multi-select/bulk edit, Habits, and Offline edge cases
- `backend/tests/` — ~60+ pytest suites for dashboard, export, search, app_update, projects routers; recurrence/urgency/NLP edge-case services; and end-to-end integration workflows and stress tests
- **Total:** ~654 tests across the repo
