# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased — v1.4.0 Vision Deck Rollout

### Added — Work-Life Balance Engine (V1)
- New `LifeCategory` enum (WORK / PERSONAL / SELF_CARE / HEALTH / UNCATEGORIZED)
  with a `life_category` column on `tasks` (migration 32 → 33).
- `LifeCategoryClassifier` — fast, offline keyword-based classifier with
  configurable word lists per category, used as the default auto-classification
  path for new tasks.
- `BalanceTracker` — computes current week + 4-week rolling category ratios,
  overload detection, and dominant category from a task pool.
- `WorkLifeBalancePrefs` in `UserPreferencesDataStore` — target ratios, auto-
  classify toggle, balance bar toggle, overload threshold (5–25%).
- Organize tab: "Life Category" chip selector with Auto + 4 colored category
  chips. Editing a task now persists the life category to Room + Firestore
  sync + JSON export/import.
- Today screen: new compact `TodayBalanceSection` stacked bar above the task
  list showing the week's distribution with an overload warning badge when
  work exceeds target.
- Settings: new "Work-Life Balance" section with auto-classify toggle, balance
  bar toggle, per-category target sliders (with live sum validation), and
  overload threshold slider.
- NLP quick-add: `#work`, `#personal`, `#health`, `#self-care` (and
  `#selfcare`) set `lifeCategory` in addition to being added as regular tags.
  The hyphenated `#self-care` form is handled explicitly so the dash isn't
  dropped.
- QuickAddViewModel now falls back to `LifeCategoryClassifier` for tasks
  created without a manual category tag, so Today's balance bar stays live
  without extra user effort.
- Filter panel: "Life Category" multi-select filter with colored chips; the
  `TaskFilter` model and task-list filtering pipeline both respect it.
- Added 26 unit tests: `LifeCategoryClassifierTest` (11), `BalanceTrackerTest`
  (10), `NaturalLanguageParserTest` life-category additions (5).

### Added — Forgiveness-First Streak System (V5)
- `ForgivenessConfig` + `StreakResult` data classes exposing `strictStreak`,
  `resilientStreak`, `missesInWindow`, `gracePeriodRemaining`, and the list
  of dates that were forgiven so the UI can render them as partial hits.
- `StreakCalculator.calculateResilientDailyStreak` walks backwards from
  today (or yesterday if today isn't complete yet), tolerating up to
  `allowedMisses` misses inside a rolling `gracePeriodDays` window. A run
  of consecutive misses that starts today + yesterday is treated as a
  hard reset. The walk also stops at the earliest known completion so
  pre-habit history doesn't unfairly count as misses.
- `StreakCalculator.calculateResilientStreak` dispatches by frequency:
  daily habits get the full forgiving walk, other frequencies (weekly,
  monthly, bimonthly, quarterly, fortnightly) fall back to strict streaks
  as a follow-up pass.
- `ForgivenessPrefs` in `UserPreferencesDataStore` — enable/disable the
  system, grace period (1–30 days), allowed misses (0–5).
- Settings screen: new "Forgiveness-First Streaks" section with a
  primary toggle plus grace-window and allowed-misses sliders that reveal
  only when the toggle is on.
- 10 new unit tests in `ForgivenessStreakTest` covering the spec's core
  cases (5-on 1-miss 3-on, grace exhausted, classic mode, yesterday-only
  completion, empty history, pre-history truncation, non-daily fallback,
  zero allowance).

## v1.3.0 — Voice, Widgets, Accessibility, Analytics, Integrations & Three-Tier Pricing (April 2026)

Skips the v1.2.0 tag and ships everything developed since v1.1.0 together.

### Added — Voice Input & Accessibility
- Speech-to-task creation via Android SpeechRecognizer
- Voice commands for hands-free task management
- Text-to-speech readback of tasks and briefings
- Hands-free mode with continuous listening
- TalkBack/screen reader support throughout all screens
- Dynamic font scaling respecting system accessibility settings
- High-contrast mode support
- Keyboard navigation for all interactive elements
- Reduced motion option for animations

### Added — Widget Overhaul
- Redesigned Today, Habit Streak, and Quick-Add widgets
- 4 new widgets: Calendar, Productivity, Timer, Upcoming
- Per-instance widget configuration activities
- Widget background opacity and section toggles

### Added — Advanced Analytics & Time Tracking
- Productivity dashboard with daily/weekly/monthly views
- Task completion burndown charts
- Habit-productivity correlation analysis
- Time tracking per task with start/stop logging
- Heatmap visualization for activity patterns
- Time-tracked badge on task cards (configurable)

### Added — API Integrations
- Gmail integration: auto-create tasks from starred emails
- Slack integration: create tasks from Slack messages
- Google Calendar prep tasks: auto-generate prep tasks before meetings
- Webhook/Zapier endpoint for external automations
- Suggestion inbox for reviewing auto-created tasks

### Added — Customization & Personalization
- Centralized UserPreferencesDataStore for all customization settings
- Configurable swipe actions (7 options per direction: complete, delete,
  reschedule, archive, move to project, flag, none)
- Flagged task system with filter support
- Custom accent color picker with hex input and recent colors
- Compact mode and configurable card corner radius
- Customizable task card display fields (12 toggleable metadata fields)
- Minimal card style option
- User-configurable urgency scoring weights with live preview
- Configurable task defaults and smart defaults engine
- Custom NLP shortcuts/aliases with quick-add suggestion chips
- Saved filter presets with quick-apply chips
- Customizable long-press context menu (reorderable, toggleable actions)
- Customizable Today screen section order and visibility
- Advanced recurrence patterns: weekday, biweekly, custom month days,
  after-completion
- Notification profiles with multi-reminder bundles and escalation
- Quiet hours with deferred reminders
- Daily digest notification
- Project and habit template systems with built-in templates

### Added — Three-Tier Pricing
- Free: core tasks, habits, templates (local), calendar sync, widgets, all views
- Pro ($3.99/mo): + cloud sync, template sync, AI Eisenhower, AI Pomodoro,
  basic analytics, time tracking, smart defaults, notification profiles,
  unlimited saved filters, custom templates
- Premium ($7.99/mo): + AI briefing/planner/time blocking, collaboration,
  integrations, full analytics, Drive backup
- Debug tier override in Settings (debug builds only)

### Added — Bookable Habits
- Booking status tracking for habit logs
- Activity history with booking state

### Changed — Code Quality
- Refactored SettingsScreen.kt from ~2,800 to ~300 lines (extracted into
  section composables)
- Refactored TodayScreen.kt from ~2,290 to ~300 lines (section composables)
- Refactored AddEditTaskSheet.kt from ~2,275 to ~250 lines (tab composables)
- Refactored TaskListScreen.kt from ~2,250 to ~350 lines (component extraction)
- Refactored TaskListViewModel.kt into sort/filter/multiselect/grouping helpers
- Refactored SettingsViewModel.kt into focused delegates
- Split NavGraph.kt into navigation group extensions
- Split HabitListScreen, MedicationScreen, SelfCareScreen, DataImporter,
  BackendSyncService, and 8 additional 800+ line files into components
- Extracted Room migrations into grouped files
- No file in the codebase exceeds ~500 lines

### Added — Testing
- Repository unit tests: Task, Habit, Project, Tag (~43 tests)
- Use case tests: ParsedTaskResolver, ChecklistParser, TodoListParser (~30 tests)
- DataImporter unit tests with merge/replace/edge cases (~15 tests)
- Notification/reminder scheduling tests (~22 tests)
- DataStore preferences tests (~24 tests)
- ViewModel unit tests: Today, AddEditTask, TaskList, HabitList, Eisenhower,
  Settings, SmartPomodoro (~76 tests)
- Smoke tests: habits, search/archive, tags/projects, views, settings,
  multi-select, edge cases, offline, recurrence, export/import (~62 tests)
- DAO instrumentation tests: Habit, Tag, Template, Attachment (~24 tests)
- Backend router tests: dashboard, export, search, app_update, projects (~34 tests)
- Backend service tests: recurrence, urgency, NLP edge cases (~28 tests)
- Backend integration workflows and stress tests (~16 tests)

### Infrastructure
- Google Play Billing library for three-tier subscription
- BillingManager singleton with purchase flow and status caching
- ProFeatureGate updated for three-tier access control
- Release signing configuration and App Bundle support
- GitHub Actions release workflow for AAB builds
- kotlinx-coroutines-test, Turbine, and MockK test dependencies

## v1.1.0 — PrismTask Rebrand, AI Productivity, Freemium & Play Store (April 2026)

### Changed — Rebrand
- App renamed from AveryTask to PrismTask
- Package renamed from com.averycorp.averytask to com.averycorp.prismtask
- All UI strings, documentation, and metadata updated

### Added — AI Productivity
- Eisenhower Matrix view: 2x2 grid with AI auto-categorization via Claude Haiku
- Smart Pomodoro: AI-planned focus sessions with configurable work style
- AI settings: auto-categorize toggle, default focus style, Eisenhower badges
- Eisenhower quadrant badges on task cards
- Focus quick-start button on Today screen

### Added — Play Store & Freemium
- Google Play Billing integration for Pro subscription ($3.99/month)
- Freemium feature gating: AI, cloud sync, and collaboration are Pro
- Pro upgrade prompts with feature descriptions and free trial
- Subscription management in Settings
- Privacy Policy and Terms of Service
- Play Store listing metadata and asset specifications
- Release signing configuration and App Bundle support
- Release checklist documentation

### Added — Testing
- 28 automated Android UI smoke tests (Compose testing)
- 28 backend API integration tests
- Test data seeding infrastructure

### Infrastructure
- Google Play Billing library integration
- BillingManager singleton with purchase flow and status caching
- ProFeatureGate for consistent feature access control
- ProGuard rules for Play Billing, Calendar API, and AI models
- Release build with R8 optimization and resource shrinking
- GitHub Actions release workflow for AAB builds

## v1.0.0 — Stable Release (April 2026)

- Bump version to 1.0.0 stable release

## v0.9.0 — UX Overhaul, QoL Features & Task Templates (April 2026)

### Added — UX Overhaul
- Today screen: compact progress header bar replacing large circular ring
- Collapsible sections with remembered expand/collapse state (DataStore-persisted)
- Overdue section visual urgency: red tint background and accent bar
- Habits displayed as horizontal scrollable chips with tap-to-complete
- Floating quick-add bar pinned above navigation (always accessible)
- "All Caught Up" celebration state when all tasks are completed
- Plan-for-Today sheet: inline quick-add, search filter, batch planning, sort options

### Added — Task Editor Redesign
- Task editor converted to full-screen modal bottom sheet
- Three-tab layout: Details / Schedule / Organize
- Title and priority selector always visible in sheet header
- Details tab: inline subtask add, expandable description/notes fields
- Schedule tab: quick date chips, conditional time/reminder visibility, duration presets
- Organize tab: project card selector, inline tag toggle chips, smart context defaults
- Subtask drag-to-reorder with smooth animations
- Subtask swipe-to-complete and swipe-to-delete gestures
- Unsaved changes detection with discard confirmation dialog

### Added — Quality of Life
- Sort preference memory: each screen remembers its last sort mode (DataStore-persisted)
- Drag-to-reorder tasks in "Custom" sort mode with persistent order
- Quick reschedule: long-press any task card for date shortcuts (Today, Tomorrow, Next Week, Pick Date)
- Duplicate task from context menu or editor with optional subtask copying
- Bulk edit extensions: batch change priority, due date, tags for multi-selected tasks
- Move tasks between projects via long-press menu and drag in grouped-by-project view
- "Custom" sort mode added to all sort pickers

### Added — Task Templates
- Template system: save reusable task blueprints with pre-filled fields
- Template CRUD: create, edit, delete templates with icon, category, and all task fields
- Create task from template with pre-filled editor (user can adjust before saving)
- Quick-use: tap template in list to create task instantly
- Save existing task as template from editor overflow menu
- NLP shortcut: type "/templatename" in quick-add bar to use a template
- 6 built-in templates: Morning Routine, Weekly Review, Meeting Prep, Grocery Run, Assignment, Deep Clean
- Template usage tracking (count and last used date)
- Template categories with filter chips

### Added — Backend (Task Templates)
- Template CRUD endpoints (GET/POST/PATCH/DELETE /api/v1/templates)
- Use template endpoint (POST /api/v1/templates/{id}/use)
- Create template from task endpoint (POST /api/v1/templates/from-task/{task_id})
- Templates included in sync push/pull
- Alembic migration for task_templates table

### Changed
- Room database version upgraded (sortOrder column, task_templates table)
- Multi-select bottom action bar redesigned with 6 operation icons
- Task card long-press shows quick reschedule popup

### Infrastructure
- New DataStore preferences: SortPreferences, EditorPreferences, DashboardPreferences
- New Room entities: TaskTemplateEntity
- New screens: TemplateListScreen, AddEditTemplateScreen
- New components: QuickReschedulePopup, collapsible section headers, horizontal habit chips

## v0.8.0 — Backend Integration (April 2026)

### Added

- FastAPI backend with PostgreSQL (deployed on Railway)
- Claude Haiku-powered NLP task parsing via backend API
- Backend sync (push/pull) with manual trigger in Settings
- Cloud export/import (JSON) via backend
- JWT authentication with token refresh
- Self-update system via Firebase App Distribution
- Backend CI pipeline (GitHub Actions)
- API documentation (auto-generated Swagger UI)

### Changed

- NLP parser now tries Claude API first, falls back to local regex when offline
- README updated with backend docs, screenshots, architecture diagram, CI badges

## [0.7.0] - 2026-04-06

### Added

- Housework as built-in habit with tiered routine tracking (quick, standard, deep clean)
- Recurring habits tab with weekly, fortnightly, and monthly frequency options
- Flexible medication scheduling with interval-based or specific times-of-day modes
- Self-care and medication habits displayed on Today screen
- Custom habit category support with user-defined categories
- Self-care habit categories (skincare, grooming, dental, etc.)
- Settings toggles for self-care, medication, school, and leisure modes
- App self-update system: Settings button checks GitHub for new APK and installs updates
- Update detection comparing commit time against app install time
- FastAPI web backend with PostgreSQL, JWT auth, full CRUD, NLP parsing, and search endpoints
- Backend API: tags, habits, sync, and export endpoints with complete data model
- Debug APK CI workflow triggered on pull requests

### Fixed

- Google Sign-In "no credentials available" error
- GitHub API 403 errors by adding required User-Agent header
- Debug build failures from missing debug package in google-services.json
- Debug APK install conflict via `.debug` applicationId suffix
- Update button conflict with unique filenames and ContentResolver cleanup
- Package conflict on update by falling back to debug signing config
- Kotlin compiler failures (DataStore property name clash, missing import)
- Release APK CI curl error by adding signing config
- KeytoolException for tag numbers over 30

### Infrastructure

- Optimized CI performance across all workflows
- Build workflow runs only on pushes to main
- PR-only debug CI workflow for update button tests
- 154 unit tests across 11 test files (up from 137)
- AppUpdater test suite (11 tests)

## [0.6.0] - 2026-04-05

### Added

- Schoolwork tracking: UC Boulder coursework checklists (CSCA 5424, CSCA 5454)
- Self-care and medication tracking modes
- Leisure tracker with daily music practice and flexible activity tracking
- Custom app icon
- GitHub Actions CI workflow for automated debug APK builds

### Infrastructure

- Restored native Compose screens (removed experimental React/WebView layer)
- CI fixes for JDK, Gradle daemon, and SDK configuration

## [0.5.0] - 2026-04-05

### Added

- Habit tracking system: daily/weekly frequency, color, icon, category
- Streak engine: current streak, longest streak, completion rates (7/30/90 day), best/worst day
- Habit analytics screen: GitHub-style contribution grid, weekly trend chart, day-of-week bar chart
- Habits integrated into Today screen with combined progress ring
- Weekly habit summary notification via WorkManager (Sunday 7PM)
- Habit streak and quick-add home screen widgets (Glance for Compose)
- Customizable dashboard section ordering and visibility via DataStore
- Firestore sync for habits and habit completions

### Infrastructure

- Room database v7 with habit entities and migrations 6-7

## [0.4.0] - 2026-04-05

### Added

- Firebase Authentication with Google Sign-In via Credential Manager
- Firestore bidirectional sync for tasks, projects, and tags
- Offline queue with pending action tracking and retry logic
- Real-time snapshot listeners for cross-device updates
- JSON export (full backup) and CSV export (tasks only)
- JSON import with merge (skip duplicates) or replace (delete-all-first) modes
- Sync status indicator component

### Infrastructure

- Room database v6 with sync metadata and calendar sync entities
- ProGuard rules for Firebase and sync models

## [0.3.0] - 2026-04-05

### Added

- Today focus screen: progress ring, overdue/today/planned/completed sections, plan-for-today sheet
- NLP quick-add bar: parse dates, tags (#), projects (@), priority (!), recurrence from text
- Smart suggestions for tags and projects based on usage keyword matching
- Urgency scoring (0-1) based on due date proximity, priority, age, and subtask progress
- Week view: 7-day column layout with task cards and navigation
- Month view: calendar grid with density dots and day detail panel
- Usage logging for suggestion engine

### Infrastructure

- NaturalLanguageParser, ParsedTaskResolver, UrgencyScorer, SuggestionEngine use cases
- Room database v5 with usage log entity

## [0.2.0] - 2026-04-05

### Added

- Tags system: entity, many-to-many relations, management screen, tag selector
- Filter panel with advanced filtering (tags, priorities, projects, date range)
- Full-text search with highlighted results
- Dark/light theming with 12 accent color options and Settings screen
- Task notes and image/link attachments
- Archive system with auto-archive worker, archive screen, and settings
- Accessibility improvements and PriorityColors consistency

### Infrastructure

- Room database v3 with tag and attachment entities, migrations 1-3
- ProGuard rules for new models

## [0.1.0] - 2026-04-17

### Added

- Task management with create, edit, delete, and completion tracking
- Project organization with custom colors and emoji icons
- Subtask support with inline add, nested display, and completion counts
- Recurring tasks (daily, weekly, monthly, yearly) with configurable intervals, day-of-week selection, end conditions, and automatic next-occurrence creation on completion
- Task reminders via Android notifications with quick-select offsets (at due time, 15 min, 30 min, 1 hour, 1 day before)
- Notification actions: tap to open app, "Complete" button to mark done from the notification
- Boot persistence for scheduled reminders
- Upcoming grouped view (Overdue / Today / Tomorrow / This Week / Later / No Date)
- Flat list view with sorting (due date, priority, date created, alphabetical)
- Project filtering via horizontal chip row
- Overdue detection with red card styling, left border, and badge count in the top bar
- Quick-select date chips (Today, Tomorrow, +1 Week, Pick Date) in the task editor
- Smart date labels (Today / Tomorrow / Overdue + formatted date)
- Swipe-to-complete (right, green) and swipe-to-delete (left, red) with undo snackbars
- Priority system (None / Low / Medium / High / Urgent) with colored dots and centralized PriorityColors theme
- Material 3 DatePicker and TimePicker dialogs
- Recurrence selector UI with type chips, interval picker, day-of-week multi-select, and end condition options
- Reminder picker dialog with preset offset options
- Reusable EmptyState component across task list, project list, and filtered views
- Slide + fade navigation transitions (300ms)
- Custom typography scale
- animateContentSize on subtask sections
- Hilt dependency injection throughout
- Room database with TaskEntity, ProjectEntity, TaskDao, ProjectDao
- Foreign keys: task-to-project (SET_NULL), task-to-parent (CASCADE delete)
- Indices on projectId, parentTaskId, dueDate, isCompleted, priority
- POST_NOTIFICATIONS permission request on Android 13+
- SCHEDULE_EXACT_ALARM and RECEIVE_BOOT_COMPLETED permissions
- ProGuard/R8 rules for Room, Gson, and domain models
- Release build with minification and resource shrinking enabled
- Unit tests for RecurrenceEngine (18 tests)
- Integration tests for DAO operations and recurrence completion flow

### Infrastructure

- Single-activity Compose architecture with Jetpack Navigation
- MVVM with ViewModels, Repositories, and Room DAOs
- Material 3 theming with dynamic color support (Android 12+)
- Edge-to-edge display
- Kotlin 2.2.10, Compose BOM 2024.12.01, Gradle 8.13
- Min SDK 26 (Android 8.0), Target SDK 35 (Android 15)

[0.7.0]: https://github.com/akarlin3/prismTask/releases/tag/v0.7.0
[0.6.0]: https://github.com/akarlin3/prismTask/releases/tag/v0.6.0
[0.5.0]: https://github.com/akarlin3/prismTask/releases/tag/v0.5.0
[0.4.0]: https://github.com/akarlin3/prismTask/releases/tag/v0.4.0
[0.3.0]: https://github.com/akarlin3/prismTask/releases/tag/v0.3.0
[0.2.0]: https://github.com/akarlin3/prismTask/releases/tag/v0.2.0
[0.1.0]: https://github.com/akarlin3/prismTask/releases/tag/v0.1.0-mvp
