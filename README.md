# AveryTask

[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.115-009688.svg)](https://fastapi.tiangolo.com)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)](https://postgresql.org)
[![Android CI](https://github.com/akarlin3/averyTask/actions/workflows/android-ci.yml/badge.svg)](https://github.com/akarlin3/averyTask/actions/workflows/android-ci.yml)
[![Backend CI](https://github.com/akarlin3/averyTask/actions/workflows/ci.yml/badge.svg)](https://github.com/akarlin3/averyTask/actions/workflows/ci.yml)

A native Android task manager with a Python API backend featuring AI-powered natural language processing. Built with Kotlin/Jetpack Compose for the client and FastAPI/PostgreSQL for the server.

## Screenshots

<p align="center">
  <img src="screenshots/today-view.jpg" width="250" alt="Today View" />
  &nbsp;&nbsp;
  <img src="screenshots/habits-view.jpg" width="250" alt="Habits" />
  &nbsp;&nbsp;
  <img src="screenshots/timer-view.jpg" width="250" alt="Focus Timer" />
</p>

## Features

### Task Management
- Create, edit, and delete tasks with titles, descriptions, due dates, times, and priority levels (None/Low/Medium/High/Urgent)
- Organize tasks into projects with custom colors and emoji icons
- Nested subtasks with completion tracking and cascade delete
- Flexible tagging system (many-to-many) with color-coded tags
- Notes and file/link attachments per task
- Swipe-to-complete and swipe-to-delete gestures with undo snackbars
- Multi-select mode with batch complete, delete, and move operations
- Bulk edit: batch change priority, due date, tags, and move to project for multi-selected tasks
- Drag-to-reorder tasks in "Custom" sort mode with persistent order
- Quick reschedule via long-press popup (Today, Tomorrow, Next Week, Pick Date)
- Duplicate task from context menu or editor with optional subtask copying
- Move tasks between projects via long-press menu and drag in grouped-by-project view
- Per-screen sort preference memory (DataStore-persisted)
- Urgency scoring (0-1) based on due date proximity, priority, age, and subtask progress

### Recurrence
- Daily, weekly (multi-day), monthly, and yearly patterns with configurable intervals
- Skip-weekends option for daily recurrence
- End conditions: max occurrences or end date
- Completing a recurring task auto-creates the next occurrence

### Reminders and Notifications
- Per-task reminders with configurable offset before due date
- AlarmManager scheduling with BroadcastReceiver delivery
- "Complete" action directly from the notification
- Reminders re-scheduled after device reboot

### NLP Quick-Add
- Natural language task creation from a single text input
- Extracts dates ("today", "tomorrow", "next Monday", "in 3 days", "Jan 15", "5/20", "2026-05-15"), times ("at 3pm", "at 15:00", "at noon"), tags (#work), projects (@home), priority (!urgent, !!), and recurrence hints (daily, weekly)
- Parsed results resolved against existing tags and projects with unmatched item feedback
- Smart suggestions for tags and projects based on usage keyword matching

### Views
- **Today** -- Focus screen with compact progress header bar, collapsible sections (expand/collapse state persisted), overdue section with red urgency tint, horizontal habit chips with tap-to-complete, "All Caught Up" celebration state, "Plan for Today" bottom sheet with inline quick-add, search filter, batch planning, and sort options
- **Task Editor** -- Full-screen modal bottom sheet with three-tab layout (Details / Schedule / Organize), title and priority always visible in header, subtask drag-to-reorder, swipe-to-complete/delete gestures, unsaved changes detection
- **Task List** -- Grouped or flat list with sorting (priority, date, urgency, alphabetical, custom), advanced filtering (tags, priorities, projects, date range), search with highlighted results
- **Week View** -- 7-day column layout with task cards per day and week navigation
- **Month View** -- Calendar grid with density dots and day detail panel
- **Timeline** -- Daily scheduled view with time blocks, duration management, and current-time indicator
- **Projects** -- Project list with task counts and full CRUD
- **Habits** -- Habit list with streak badges, weekly progress dots, and circular completion checkboxes
- **Archive** -- Archived tasks with search and restore/permanent-delete options

### Habit Tracking
- Create habits with name, description, icon (16 emoji options), color (12 options), and category
- Daily or weekly frequency with configurable target count and active day selection
- Streak engine: current streak, longest streak, completion rates (7/30/90 day), best/worst day analysis
- Analytics screen: GitHub-style contribution grid (12 weeks), weekly trend line chart, day-of-week bar chart, stat cards
- Habits integrated into Today screen with combined progress ring
- Optional daily reminder and auto-create-task features
- Weekly habit summary notification via WorkManager (Sunday 7PM)

### Task Templates
- Create reusable task blueprints with pre-filled title, description, priority, project, tags, subtasks, recurrence, and duration
- 6 built-in templates (Morning Routine, Weekly Review, Meeting Prep, Grocery Run, Assignment, Deep Clean)
- Quick-use from template list or NLP shortcut (`/templatename`)
- Save any existing task as a template from editor overflow menu
- Template categories, search, and usage tracking (count and last used date)

### Cloud Sync
- Firebase Authentication with Google Sign-In via Credential Manager
- Firestore bidirectional sync for tasks, projects, tags, habits, and habit completions
- Offline queue with pending action tracking and retry logic
- Real-time snapshot listeners for cross-device updates

### Home Screen Widgets
- **Today Widget** -- Combined progress count + top task names + habit completion count
- **Habit Streak Widget** -- Up to 6 habits with icons and completion status
- **Quick-Add Widget** -- Minimal tap-to-launch bar for fast task creation
- Built with Glance for Compose

### Data Management
- JSON export (full backup: tasks with tag/project names, projects, tags)
- CSV export (tasks only with proper escaping)
- JSON import with merge (skip duplicates) or replace (delete-all-first) modes
- Customizable dashboard section ordering and visibility

### Theming
- Material 3 with dynamic colors on Android 12+
- Light, Dark, and System theme modes with 12 accent color options
- Edge-to-edge display

## Architecture Overview

```
┌─────────────────────────┐         ┌──────────────────────────┐
│   Android App (Kotlin)  │  HTTPS  │   FastAPI Backend         │
│   Jetpack Compose        │◄───────►│   Python 3.12            │
│   Room + Firebase        │         │   SQLAlchemy + Alembic   │
│   Glance Widgets         │         │   JWT Auth               │
└─────────────────────────┘         └──────────┬───────────────┘
                                               │
                                    ┌──────────▼───────────────┐
                                    │   PostgreSQL 16          │
                                    └──────────────────────────┘
                                               │
                                    ┌──────────▼───────────────┐
                                    │   Claude Haiku (NLP)     │
                                    └──────────────────────────┘
```

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Navigation | Jetpack Navigation Compose | 2.8.5 |
| DI | Hilt (Dagger) + KSP | 2.59.2 |
| Database | Room + KSP | 2.8.4 |
| Preferences | DataStore | 1.1.1 |
| Background | WorkManager | 2.9.1 |
| Cloud | Firebase Auth + Firestore + Storage | BOM 33.6.0 |
| Auth | Credential Manager + Google Identity | 1.3.0 / 1.1.1 |
| Drag-to-Reorder | sh.calvin.reorderable | 2.4.3 |
| Widgets | Glance for Compose | 1.1.0 |
| Serialization | Gson | 2.11.0 |
| Async | Kotlin Coroutines | 1.10.2 |
| Build | Gradle (Kotlin DSL) | 8.13 |

**Target:** Android 8.0+ (API 26) through Android 15 (API 35)

## Backend API

The FastAPI backend provides REST endpoints for cross-device sync, AI-powered task parsing, and self-updating.

**Live API docs:** https://averytask-production.up.railway.app/docs

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | FastAPI | 0.115.6 |
| Database | PostgreSQL + SQLAlchemy | 16 / 2.0 |
| Migrations | Alembic | - |
| Auth | JWT (python-jose) + Firebase token bridge | - |
| NLP | Anthropic Claude Haiku API | - |
| Deployment | Railway + Docker | - |
| CI | GitHub Actions | - |

### Key Endpoints
- `POST /api/v1/auth/register` — Create account
- `POST /api/v1/auth/login` — JWT authentication
- `POST /api/v1/tasks/parse` — AI-powered natural language task parsing
- `GET /api/v1/dashboard/summary` — Dashboard statistics
- `POST /api/v1/sync/push` / `GET /api/v1/sync/pull` — Cross-device sync
- `GET /api/v1/app/version` — Self-update check

## Requirements

- Android Studio Ladybug (2024.2.1) or later
- JDK 17
- Android SDK 35
- Device or emulator running Android 8.0+ (API 26)

## Getting Started

### Android

```bash
# Clone the repository
git clone https://github.com/akarlin3/averyTask.git
cd averyTask

# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug
```

Replace `app/google-services.json` with your Firebase project configuration for cloud sync and authentication features. The app works fully offline without Firebase.

### Backend (optional — app works fully offline)

```bash
cd backend
cp .env.example .env  # Edit with your settings
docker compose up -d
# API docs at http://localhost:8000/docs
```

## Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build (R8 minification + resource shrinking)
./gradlew assembleRelease

# Run unit tests (216 tests)
./gradlew testDebugUnitTest

# Run instrumentation tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Clean
./gradlew clean
```

## Architecture

Single-activity MVVM with Hilt dependency injection:

- **UI layer**: Jetpack Compose screens with Material 3, connected to ViewModels via `hiltViewModel()`
- **ViewModel layer**: Exposes `StateFlow` from repositories via `stateIn(WhileSubscribed)`, handles user actions in `viewModelScope`
- **Repository layer**: Single source of truth wrapping Room DAOs with business logic (recurrence completion, date grouping, streak calculation, duplicate prevention)
- **Data layer**: Room database (v24, 18 entities) with reactive `Flow` queries, Firebase Firestore for cloud sync, DataStore for preferences
- **Domain layer**: Pure use-case objects -- RecurrenceEngine, NaturalLanguageParser, UrgencyScorer, StreakCalculator, SuggestionEngine, ParsedTaskResolver
- **Notifications**: AlarmManager + BroadcastReceiver for task reminders, WorkManager for weekly habit summaries
- **Widgets**: Glance for Compose with direct Room queries via WidgetDataProvider

```
UI (Compose Screens + ViewModels)
        |
        v
  Repositories
        |
   +---------+---------+
   |         |         |
Room DAOs  SyncService  DataStore
   |         |
SQLite    Firestore
```

## Test Coverage

**216 unit tests** across 18 test files:

| Test File | Tests | Covers |
|-----------|-------|--------|
| NaturalLanguageParserTest | 38 | Tags, projects, priority, dates, times, recurrence, edge cases |
| AppUpdaterTest | 24 | GitHub API parsing, version comparison, download, install triggers |
| StreakCalculatorTest | 21 | Current/longest streak, completion rate, weekly, by-day, multi-target |
| RecurrenceEngineTest | 18 | Daily/weekly/monthly/yearly, intervals, skip weekends, end conditions |
| TaskFilterTest | 13 | Filter activation, counting, defaults, all 7 filter types |
| SyncMapperTest | 13 | Round-trip for tasks, projects, tags, habits, completions, defaults |
| TaskTemplateRepositoryTest | 11 | Template CRUD, create-from-template, built-in seeding, usage tracking |
| UrgencyScorerTest | 10 | Due date, priority, age, subtasks, urgency levels, clamping |
| EntityJsonMergerTest | 9 | Merge vs. replace import paths for tasks, projects, habits |
| SuggestionEngineTest | 8 | Keyword extraction, stop words, short words, casing, empty input |
| RecurrenceConverterTest | 8 | JSON round-trip, invalid input, partial data, all recurrence types |
| DateShortcutsTest | 7 | Quick reschedule date calculations (today, tomorrow, next week, etc.) |
| DuplicateTaskTest | 7 | Task duplication with/without subtasks, field copying, ID isolation |
| HabitRepositoryHelpersTest | 7 | Date normalization, week boundaries, idempotency |
| DataExporterTest | 7 | CSV escaping: commas, quotes, newlines, combined, empty |
| SortPreferencesTest | 6 | Per-screen sort mode persistence, defaults, screen isolation |
| MoveToProjectTest | 5 | Move task between projects, null project, batch move |
| TemplateSeederTest | 4 | Built-in template seeding, idempotency, field validation |

**15 instrumentation tests** across 3 test files:

| Test File | Tests | Covers |
|-----------|-------|--------|
| TaskDaoTest | 7 | CRUD, completion, project/subtask queries, date queries |
| ProjectDaoTest | 4 | CRUD, task count aggregation |
| RecurrenceIntegrationTest | 4 | Recurring task completion flow, max occurrences |

## Database

Room database `averytask.db` at version 24 with 18 entities:

| Table | Purpose |
|-------|---------|
| `tasks` | Core task data with FKs to projects and parent tasks, sortOrder column |
| `projects` | Project grouping with color and icon |
| `tags` | Tag definitions with color |
| `task_tags` | Many-to-many junction (task-tag) |
| `attachments` | File and link attachments per task |
| `usage_logs` | Keyword-entity frequency for smart suggestions |
| `sync_metadata` | Local-to-cloud ID mapping with pending action queue |
| `calendar_sync` | Task-to-Google Calendar event mapping |
| `habits` | Habit definitions: frequency, color, icon, category |
| `habit_completions` | Per-day habit completion records |
| `leisure_logs` | Leisure activity tracking |
| `courses` | Schoolwork course definitions |
| `assignments` | Course assignment tracking |
| `study_logs` | Study session logs |
| `course_completions` | Course completion records |
| `self_care_logs` | Self-care activity logs |
| `self_care_steps` | Self-care routine step tracking |
| `task_templates` | Reusable task blueprints with category, icon, and usage stats |

Migrations: 1→24 covering tags, notes/attachments, planned date, usage logs, duration/sync, habits, leisure, schoolwork, self-care, calendar sync, task templates, sort order, and more.

## Project Structure

```
averyTask/
├── app/                                    # Android app (Kotlin / Jetpack Compose)
│   └── src/main/java/com/averycorp/averytask/
│       ├── MainActivity.kt                 # Single-activity entry point
│       ├── AveryTaskApplication.kt         # @HiltAndroidApp
│       ├── data/
│       │   ├── local/                      # Room entities (18), DAOs (13), database, converters
│       │   ├── remote/                     # Firebase auth, sync service, entity mappers
│       │   ├── export/                     # JSON/CSV export and JSON import
│       │   ├── preferences/                # DataStore: theme, archive, dashboard, sort, templates
│       │   └── repository/                 # Task, Project, Tag, Habit, Attachment, TaskTemplate
│       ├── di/                             # Hilt DatabaseModule
│       ├── domain/
│       │   ├── model/                      # RecurrenceRule, TaskFilter
│       │   └── usecase/                    # RecurrenceEngine, NLP Parser, UrgencyScorer,
│       │                                     StreakCalculator, SuggestionEngine, ParsedTaskResolver
│       ├── notifications/                  # Reminders, receivers, weekly summary worker
│       ├── widget/                         # Glance widgets: Today, HabitStreak, QuickAdd
│       └── ui/
│           ├── components/                 # Reusable composables (10+)
│           ├── navigation/                 # NavGraph with 5-tab bottom nav
│           ├── screens/                    # 19 screen packages (incl. templates)
│           └── theme/                      # Color, Theme, Type, PriorityColors
└── backend/                                # FastAPI backend (Python 3.12)
    ├── app/                                # FastAPI application
    │   ├── api/                            # REST routers (auth, tasks, sync, dashboard)
    │   ├── core/                           # Config, security, JWT
    │   ├── db/                             # SQLAlchemy models, session
    │   ├── schemas/                        # Pydantic request/response models
    │   └── services/                       # Claude Haiku NLP, sync logic
    ├── alembic/                            # Database migrations
    ├── tests/                              # Pytest suite
    ├── Dockerfile
    └── requirements.txt
```

## Deployment

- **Android:** Firebase App Distribution via GitHub Actions CI
- **Backend:** Railway (Docker) with auto-deploy from main branch
- **Database:** Railway PostgreSQL

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code conventions, and pull request guidelines.

## Security

See [SECURITY.md](SECURITY.md) for security considerations and how to report vulnerabilities.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).

---

## AveryTask Web Backend + React Native App

In addition to the native Android app, AveryTask includes a full-stack web backend and cross-platform React Native mobile app.

### Why I Built This

I wanted a hierarchical task management system that maps how I actually think about work — career goals broken into projects, projects broken into tasks. Most task apps are flat lists. AveryTask gives me Goal → Project → Task hierarchy with an NLP parser powered by Claude that lets me create tasks from natural language.

### Web Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Backend | FastAPI (Python 3.11+) | Async REST API with auto-docs |
| ORM | SQLAlchemy 2.0 (async) | Models + migrations via Alembic |
| Database | PostgreSQL | Production-grade relational DB |
| Auth | JWT (PyJWT + bcrypt) | Stateless auth with refresh tokens |
| NLP | Claude Haiku (Anthropic API) | Natural language task parsing |
| Mobile | React Native (Expo) | Cross-platform with file-based routing |
| State | Zustand | Lightweight state management |
| CI | GitHub Actions | Automated tests + linting |
| Deploy | Docker + Railway | Containerized deployment |

### Architecture

```
Android/iOS Device (React Native + Expo)
        │ HTTPS
        ▼
FastAPI Server (Railway)
├── Auth (JWT)
├── CRUD Routes (Goals → Projects → Tasks)
├── NLP Parser (Claude Haiku)
└── SQLAlchemy ORM + Alembic
        │
        ▼
    PostgreSQL
```

### Backend API Endpoints

All endpoints under `/api/v1`:

- **Auth**: POST `/auth/register`, `/auth/login`, `/auth/refresh`
- **Goals**: GET/POST `/goals`, GET/PATCH/DELETE `/goals/{id}`
- **Projects**: GET/POST `/goals/{id}/projects`, GET/PATCH/DELETE `/projects/{id}`
- **Tasks**: GET/POST `/projects/{id}/tasks`, GET/PATCH/DELETE `/tasks/{id}`, POST `/tasks/{id}/subtasks`
- **Dashboard**: GET `/tasks/today`, `/tasks/overdue`, `/tasks/upcoming`, `/dashboard/summary`
- **NLP**: POST `/tasks/parse` — natural language → structured task suggestion
- **Search**: GET `/search?q=query` — full-text search across tasks

### Getting Started (Backend)

```bash
# Prerequisites: Docker, Node.js 18+

# Start backend + PostgreSQL
docker compose up -d

# API docs
open http://localhost:8000/docs

# Run tests
docker compose exec backend pytest -v

# Start mobile app
cd mobile && npm install && npx expo start
```

### Environment Variables

Copy `backend/.env.example` to `backend/.env`:

```env
DATABASE_URL=postgresql+asyncpg://averytask:averytask@localhost:5432/averytask
JWT_SECRET_KEY=change-me-in-production
JWT_ALGORITHM=HS256
ANTHROPIC_API_KEY=sk-ant-your-key-here
ENVIRONMENT=dev
```

### Backend Test Coverage

21+ tests covering auth, goals CRUD, tasks/subtasks, depth constraints, and NLP parsing.

## Author

Avery Karlin
