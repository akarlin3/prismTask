# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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

[0.7.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.7.0
[0.6.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.6.0
[0.5.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.5.0
[0.4.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.4.0
[0.3.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.3.0
[0.2.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.2.0
[0.1.0]: https://github.com/akarlin3/averyTask/releases/tag/v0.1.0-mvp
