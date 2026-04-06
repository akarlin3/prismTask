# CLAUDE.md

## Project Overview

**AveryTask** (`com.averykarlin.averytask`) is a native Android todo list app built with Kotlin and Jetpack Compose. v0.6.0 includes full task management, projects, subtasks, tags, recurrence, reminders, notifications, NLP quick-add, Today focus screen, week/month/timeline views, urgency scoring, smart suggestions, Firebase cloud sync, Google Sign-In, JSON/CSV data export/import, habit tracking with streaks/analytics, and home screen widgets.

## Tech Stack

- **Language**: Kotlin 2.2.10 (JVM target 17)
- **UI**: Jetpack Compose with Material 3 (BOM 2024.12.01)
- **DI**: Hilt (Dagger) 2.59.2
- **Database**: Room 2.8.4 with KSP
- **Navigation**: Jetpack Navigation Compose 2.9.7
- **Serialization**: Gson 2.11.0 (for RecurrenceRule JSON)
- **Cloud**: Firebase Auth + Firestore + Storage (BOM 33.7.0)
- **Auth**: Credential Manager + Google Identity
- **Widgets**: Glance for Compose 1.1.1
- **Build**: Gradle 8.13 with Kotlin DSL
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 35 (Android 15)

## Project Structure

```
app/src/main/java/com/averykarlin/averytask/
├── MainActivity.kt                     # Single-activity entry point, notification permission
├── AveryTaskApplication.kt             # @HiltAndroidApp
├── data/
│   ├── local/
│   │   ├── converter/
│   │   │   └── RecurrenceConverter.kt  # Gson JSON ↔ RecurrenceRule
│   │   ├── dao/
│   │   │   ├── TaskDao.kt             # Room DAO with Flow queries (today, overdue, planned)
│   │   │   ├── ProjectDao.kt          # Room DAO with task count join
│   │   │   ├── TagDao.kt              # Tag CRUD + task-tag relations
│   │   │   ├── AttachmentDao.kt       # Attachment CRUD
│   │   │   ├── UsageLogDao.kt         # Usage analytics for smart suggestions
│   │   │   ├── HabitDao.kt            # Habit CRUD + active/archive filtering
│   │   │   └── HabitCompletionDao.kt  # Habit completions: date queries, range, toggle
│   │   ├── database/
│   │   │   └── AveryTaskDatabase.kt   # Room DB (v7, migrations 1→7)
│   │   └── entity/
│   │       ├── TaskEntity.kt          # Tasks table with plannedDate, FKs, indices
│   │       ├── ProjectEntity.kt       # Projects table
│   │       ├── TagEntity.kt           # Tags table
│   │       ├── TaskTagCrossRef.kt     # Task-tag junction table
│   │       ├── TaskWithTags.kt        # Room relation
│   │       ├── AttachmentEntity.kt    # File attachments
│   │       ├── UsageLogEntity.kt      # Usage logs for suggestion engine
│   │       ├── SyncMetadataEntity.kt  # Cloud sync local↔remote ID mapping
│   │       ├── CalendarSyncEntity.kt  # Task↔Google Calendar event mapping
│   │       ├── HabitEntity.kt         # Habits: name, frequency, color, icon, category
│   │       └── HabitCompletionEntity.kt # Habit completions with FK to habits
│   ├── remote/
│   │   ├── AuthManager.kt            # Firebase Auth + Google Sign-In
│   │   ├── SyncService.kt            # Firestore push/pull/real-time sync
│   │   └── mapper/
│   │       └── SyncMapper.kt         # Entity ↔ Firestore document mapping
│   ├── export/
│   │   ├── DataExporter.kt           # JSON + CSV export
│   │   └── DataImporter.kt           # JSON import with merge/replace modes
│   ├── repository/
│   │   ├── TaskRepository.kt          # Task CRUD, recurrence completion, date grouping
│   │   ├── ProjectRepository.kt       # Project CRUD
│   │   ├── TagRepository.kt           # Tag CRUD + task assignment
│   │   ├── AttachmentRepository.kt    # Attachment CRUD
│   │   └── HabitRepository.kt         # Habit CRUD, completions, streak calc, HabitWithStatus
│   └── preferences/
│       ├── ThemePreferences.kt        # Theme mode + accent color DataStore
│       ├── ArchivePreferences.kt      # Auto-archive settings
│       └── DashboardPreferences.kt    # Dashboard section order + visibility
├── di/
│   └── DatabaseModule.kt              # Hilt module: Room DB, DAOs (incl. Habit DAOs)
├── domain/
│   ├── model/
│   │   ├── RecurrenceRule.kt          # RecurrenceRule data class + RecurrenceType enum
│   │   └── TaskFilter.kt             # Filter model for task list
│   └── usecase/
│       ├── RecurrenceEngine.kt        # Next-date calculation for all recurrence types
│       ├── NaturalLanguageParser.kt   # NLP parser: extracts dates, tags, priority from text
│       ├── ParsedTaskResolver.kt      # Resolves parsed NLP data against existing entities
│       ├── UrgencyScorer.kt           # Urgency scoring (0-1) based on due date, priority, age
│       ├── SuggestionEngine.kt        # Smart tag/project suggestions based on usage patterns
│       └── StreakCalculator.kt        # Habit streak calculation (current, longest, rates, by-day)
├── notifications/
│   ├── NotificationHelper.kt          # Channel creation, notification builder
│   ├── ReminderScheduler.kt           # AlarmManager scheduling
│   ├── ReminderBroadcastReceiver.kt   # Fires notification on alarm
│   ├── CompleteTaskReceiver.kt        # Marks task complete from notification action
│   ├── BootReceiver.kt               # Reschedules reminders after reboot
│   ├── WeeklyHabitSummary.kt         # Weekly habit summary generator + notification
│   └── WeeklySummaryWorker.kt        # WorkManager worker for weekly summary
├── widget/
│   ├── TodayWidget.kt                # Glance today widget with progress + task list
│   ├── HabitStreakWidget.kt           # Glance habit streak widget
│   ├── QuickAddWidget.kt             # Glance quick-add widget
│   ├── WidgetDataProvider.kt          # Room queries for widget data
│   └── WidgetUpdateManager.kt         # Triggers widget updates
└── ui/
    ├── components/
    │   ├── SubtaskSection.kt          # Expandable subtask list with inline add
    │   ├── RecurrenceSelector.kt      # Recurrence config dialog
    │   ├── EmptyState.kt             # Reusable empty state composable
    │   ├── FilterPanel.kt            # Advanced filter bottom sheet
    │   ├── HighlightedText.kt        # Search result highlighting
    │   ├── TagSelector.kt            # Tag selection component
    │   ├── QuickAddBar.kt            # NLP-powered quick task creation bar
    │   ├── QuickAddViewModel.kt      # Quick-add logic: parse → resolve → create
    │   ├── StreakBadge.kt            # Fire emoji streak badge with pulse animation
    │   ├── ContributionGrid.kt       # GitHub-style 12-week completion grid
    │   └── WeeklyProgressDots.kt     # 7-dot Mon-Sun weekly progress indicator
    ├── navigation/
    │   └── NavGraph.kt               # NavHost with bottom nav (Today, Tasks, Projects, Settings)
    ├── screens/
    │   ├── auth/
    │   │   ├── AuthScreen.kt         # Google Sign-In screen
    │   │   └── AuthViewModel.kt      # Auth state management
    │   ├── today/
    │   │   ├── TodayScreen.kt        # Today focus: progress ring, overdue, planned, completed
    │   │   └── TodayViewModel.kt     # Today state, plan-for-today, rollover
    │   ├── tasklist/
    │   │   ├── TaskListScreen.kt      # Main screen: grouped/list views, swipe, filter, multi-select
    │   │   └── TaskListViewModel.kt   # Sort (incl. urgency), filter, group, subtask map, undo
    │   ├── addedittask/
    │   │   ├── AddEditTaskScreen.kt   # Task form with date/time/priority/recurrence/reminder
    │   │   └── AddEditTaskViewModel.kt
    │   ├── projects/
    │   │   ├── ProjectListScreen.kt
    │   │   ├── ProjectListViewModel.kt
    │   │   ├── AddEditProjectScreen.kt
    │   │   └── AddEditProjectViewModel.kt
    │   ├── weekview/
    │   │   ├── WeekViewScreen.kt      # 7-day column view with task cards
    │   │   └── WeekViewModel.kt       # Week navigation, task grouping by day
    │   ├── monthview/
    │   │   ├── MonthViewScreen.kt     # Calendar grid with density dots, day detail
    │   │   └── MonthViewModel.kt      # Month navigation, day info aggregation
    │   ├── timeline/
    │   │   ├── TimelineScreen.kt      # Daily timeline with scheduled blocks
    │   │   └── TimelineViewModel.kt   # Timeline state, scheduling
    │   ├── search/
    │   │   ├── SearchScreen.kt
    │   │   └── SearchViewModel.kt
    │   ├── archive/
    │   │   ├── ArchiveScreen.kt
    │   │   └── ArchiveViewModel.kt
    │   ├── settings/
    │   │   ├── SettingsScreen.kt
    │   │   └── SettingsViewModel.kt
    │   ├── habits/
    │   │   ├── HabitListScreen.kt        # Habit list with streak badges, checkboxes, dots
    │   │   ├── HabitListViewModel.kt     # Habit list state + toggle/delete/reorder
    │   │   ├── AddEditHabitScreen.kt     # Habit form: icon, color, frequency, category
    │   │   ├── AddEditHabitViewModel.kt  # Habit creation/editing
    │   │   ├── HabitAnalyticsScreen.kt   # Stats, contribution grid, charts
    │   │   └── HabitAnalyticsViewModel.kt # Analytics state computation
    │   └── tags/
    │       ├── TagManagementScreen.kt
    │       └── TagManagementViewModel.kt
    └── theme/
        ├── Color.kt                   # Material 3 color tokens
        ├── Theme.kt                   # Dynamic color theme (dark mode forced)
        ├── Type.kt                    # Typography scale
        └── PriorityColors.kt         # Centralized priority color definitions
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
- **Export/Import**: JSON full backup + CSV tasks export; JSON import with merge/replace modes
- **Habits**: Habit tracking with daily/weekly frequency, streaks, analytics, contribution grid, weekly summary notification
- **Widgets**: Glance-based home screen widgets (Today, Habit Streaks, Quick-Add)
- **Dashboard**: Customizable Today section order via DashboardPreferences DataStore

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

- **Theme**: Use `AveryTaskTheme` as the root composable wrapper
- **No XML layouts**: All UI must be Jetpack Compose
- **JVM target**: 17 — do not change without updating both `compileOptions` and `kotlinOptions`
- **Entity fields**: Use `@ColumnInfo` with snake_case column names
- **Recurrence**: Stored as JSON string in `TaskEntity.recurrenceRule`, parsed via `RecurrenceConverter` (Gson)
- **Reminders**: `reminderOffset` is millis before due date; scheduling handled by `ReminderScheduler`
- **Priority levels**: 0=None, 1=Low, 2=Medium, 3=High, 4=Urgent; colors in `PriorityColors`
- **Error handling**: ViewModels catch exceptions and surface via `SnackbarHostState` or `SharedFlow<String>`

## Important Files

- `build.gradle.kts` — Root build file with plugin versions (AGP 9.1.0, Kotlin 2.2.10)
- `app/build.gradle.kts` — App module dependencies, build config, ProGuard/R8 settings
- `app/proguard-rules.pro` — Keep rules for Room, Gson, domain models
- `app/src/main/AndroidManifest.xml` — Activity, receivers, permissions
- `app/google-services.json` — Firebase config (placeholder — replace with actual)
- `app/src/test/` — RecurrenceEngine (18), NaturalLanguageParser (32), UrgencyScorer (10), StreakCalculator (10) unit tests
- `app/src/androidTest/` — DAO + recurrence integration tests
