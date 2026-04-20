# PrismTask — Architecture & Data Model

## 1. System Overview

```
┌─────────────────────────────────────────────────────┐
│                  Android Device                      │
│  ┌───────────────────────────────────────────────┐  │
│  │        Kotlin / Jetpack Compose                │  │
│  │  ┌─────────┐ ┌──────────┐ ┌───────────────┐  │  │
│  │  │ Screens │ │ ViewModels│ │    Room DB    │  │  │
│  │  │ & Nav   │ │ (StateFlow)│ │  (SQLite)    │  │  │
│  │  └─────────┘ └──────────┘ └───────────────┘  │  │
│  │  ┌───────────────────────────────────────────┐│  │
│  │  │  Firebase Auth + Firestore Sync + Hilt DI ││  │
│  │  └───────────────────┬───────────────────────┘│  │
│  └──────────────────────┼────────────────────────┘  │
└─────────────────────────┼───────────────────────────┘
                          │ HTTPS
                          ▼
┌─────────────────────────────────────────────────────┐
│                     Browser                          │
│  ┌───────────────────────────────────────────────┐  │
│  │       React + TypeScript + Vite                │  │
│  │  ┌─────────┐ ┌──────────┐ ┌───────────────┐  │  │
│  │  │ Screens │ │  State   │ │  API Client   │  │  │
│  │  │ (Router)│ │ (Zustand)│ │  (Axios)      │  │  │
│  │  └─────────┘ └──────────┘ └───────┬───────┘  │  │
│  └────────────────────────────────────┼──────────┘  │
└───────────────────────────────────────┼─────────────┘
                                        │ HTTPS
                                        ▼
┌─────────────────────────────────────────────────────┐
│              Railway / Render                        │
│  ┌───────────────────────────────────────────────┐  │
│  │              FastAPI Server                    │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────┐  │  │
│  │  │  Auth    │ │  CRUD    │ │  NLP Parser  │  │  │
│  │  │  (JWT)   │ │  Routes  │ │  (Claude)    │  │  │
│  │  └──────────┘ └────┬─────┘ └──────────────┘  │  │
│  │                     │                          │  │
│  │  ┌──────────────────▼─────────────────────┐   │  │
│  │  │         SQLAlchemy ORM + Alembic       │   │  │
│  │  └──────────────────┬─────────────────────┘   │  │
│  └─────────────────────┼─────────────────────────┘  │
│                        │                             │
│  ┌─────────────────────▼─────────────────────────┐  │
│  │              PostgreSQL                        │  │
│  └────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### Tech Stack Summary

| Layer        | Technology             | Why                                                    |
|--------------|------------------------|--------------------------------------------------------|
| Android      | Kotlin + Jetpack Compose | Native performance, Material 3, offline-first with Room |
| Web          | React 19 + TypeScript + Vite | Fast iteration, shared API, responsive SPA             |
| Web Styling  | TailwindCSS 4          | Utility-first, rapid prototyping, consistent design     |
| Web State    | Zustand 5              | Lightweight, no boilerplate (Redux is overkill for MVP) |
| HTTP Client  | Axios (web) / Retrofit (Android) | Interceptors for auth tokens, clean error handling |
| Backend      | FastAPI (Python 3.11+) | Auto-docs, async, type hints — reinforces Python resume |
| ORM          | SQLAlchemy 2.0         | Industry standard, pairs with Alembic for migrations    |
| Migrations   | Alembic                | Schema versioning — shows production discipline         |
| Database     | PostgreSQL             | Production-grade, free tier on Railway/Render           |
| Auth         | JWT (python-jose)      | Stateless, simple, well-understood                      |
| NLP Feature  | Anthropic API (Haiku)  | Fast, cheap, high-quality parsing                       |
| Deployment   | Docker + Railway       | Simple, free/cheap tier, auto-deploy from GitHub        |
| Web Tests    | Vitest + Playwright    | Unit tests and E2E browser automation                   |
| CI           | GitHub Actions         | Auto-test on push, free for public repos                |

---

## 2. Backend Data Model (FastAPI / PostgreSQL)

The FastAPI backend uses SQLAlchemy 2.0 with a hierarchical
`User → Goal → Project → Task` model. Priority uses the Todoist/Linear
convention (1 = Urgent, 4 = Low), which is the inverse of the Android
app convention (0 = None, 4 = Urgent) — mapping happens in the API client.

### Entity Relationship Diagram

```
┌──────────┐
│   User   │
│──────────│
│ id (PK)  │
│ email    │
│ password │
│ name     │
│ created  │
└────┬─────┘
     │ 1:many
     ▼
┌───────────┐
│   Goal    │
│───────────│
│ id (PK)   │
│ user_id   │──→ User
│ title     │
│ description│
│ status    │  (active / achieved / archived)
│ target_date│
│ color     │  (hex, for UI grouping)
│ sort_order│
│ created   │
│ updated   │
└────┬──────┘
     │ 1:many
     ▼
┌────────────┐
│  Project   │
│────────────│
│ id (PK)    │
│ goal_id    │──→ Goal
│ user_id    │──→ User
│ title      │
│ description│
│ status     │  (active / completed / on_hold / archived)
│ due_date   │
│ sort_order │
│ created    │
│ updated    │
└────┬───────┘
     │ 1:many
     ▼
┌──────────────┐
│    Task      │
│──────────────│
│ id (PK)      │
│ project_id   │──→ Project
│ user_id      │──→ User
│ parent_id    │──→ Task (nullable, self-referential for subtasks)
│ title        │
│ description  │
│ status       │  (todo / in_progress / done / cancelled)
│ priority     │  (1=urgent, 2=high, 3=medium, 4=low)
│ due_date     │
│ completed_at │
│ sort_order   │
│ depth        │  (0=task, 1=subtask — max depth 1)
│ created      │
│ updated      │
└──────────────┘
```

The backend has expanded beyond this core model to support task templates,
NLP parsing, calendar integration, export/import, and search. See the
`backend/app/` directory for current SQLAlchemy models and Alembic
migrations.

---

## 2b. Android Local Database (Room / SQLite)

The Android app maintains a local Room database that is significantly richer
than the backend model. The Android app is the primary data store;
Firebase Firestore provides cross-device cloud sync for core entities.

**Current schema version: 50** (49 cumulative migrations,
`MIGRATION_1_2` through `MIGRATION_49_50`)

### Entity Groups

**Core Tasks & Projects**

| Table | Key columns | Notes |
|---|---|---|
| `tasks` | title, priority (0–4), due_date, life_category, recurrence_rule (JSON), project_id (SET NULL), parent_task_id (CASCADE) | Priority 0=None…4=Urgent |
| `task_completions` | task_id (CASCADE), completed_date | Completion history; added migration 37→38 with backfill |
| `projects` | name, color, icon, description†, status†, start_date†, end_date†, theme_color_key†, completed_at†, archived_at† | †Added migration 47→48 |
| `milestones` | project_id (CASCADE), title, is_completed, order_index | Added migration 47→48 |
| `tags` | name, color | |
| `task_tag_cross_ref` | task_id (CASCADE), tag_id (CASCADE) | Many-to-many |
| `attachments` | task_id, uri, type | File/link attachments |

**Habits**

| Table | Notes |
|---|---|
| `habits` | Daily/weekly frequency, color, icon, category, target_frequency, is_built_in†, template_key† | †Added migration 48→49 |
| `habit_completions` | Daily check-off records; completed_date_local† (migration 49→50) | †Timezone-neutral local date string |
| `habit_logs` | Bookable activity history |

**Wellness & Work-Life Balance**

| Table | Notes |
|---|---|
| `mood_energy_logs` | (date, time_of_day) unique index; mood 1–5, energy 1–5. Migration 33→34 |
| `check_in_logs` | Morning check-in history |
| `weekly_reviews` | Guided weekly review records |
| `boundary_rules` | Work-hours / category limit rule definitions |
| `focus_release_logs` | Focus session history; task_id SET NULL on delete (migration 44→45) |

**Notifications**

| Table | Notes |
|---|---|
| `reminder_profiles` | `NotificationProfileEntity` — offsets_csv, sound, vibration, display, escalation chain, quiet hours, auto-switch rules |
| `custom_sounds` | User-uploaded audio metadata (≤10 MB, ≤30 s) |

**Medication & Self-Care**

| Table | Notes |
|---|---|
| `medication_refills` | Pill count, dosage, pharmacy, refill forecast. Migration 34→35 |
| `self_care_logs` | Self-care routine tracking |
| `self_care_steps` | Individual steps within a self-care routine |

**Learning, Leisure & Daily Essentials**

| Table | Notes |
|---|---|
| `study_logs` | Schoolwork tracking; course_pick and assignment_pick SET NULL (migration 44→45) |
| `courses` | Course definitions |
| `assignments` | Assignment records |
| `course_completions` | Course completion records |
| `leisure_logs` | Music + flex leisure tracking; custom_sections_state added migration 46→47 |
| `daily_essential_slot_completions` | Seven virtual Today-screen cards. Migration 45→46 |

**Templates & NLP**

| Table | Notes |
|---|---|
| `task_templates` | Reusable task blueprints |
| `habit_templates` | Reusable habit blueprints |
| `project_templates` | JSON blueprint for spawning project+task bundles (orthogonal to v1.4 Projects feature) |
| `nlp_shortcuts` | Custom quick-add aliases |
| `saved_filters` | Filter preset bookmarks |

**Sync & Analytics**

| Table | Notes |
|---|---|
| `sync_metadata` | Local ↔ Firestore cloud ID mapping |
| `calendar_sync` | Google Calendar event sync records |
| `usage_logs` | Keyword-based suggestion engine input |

### Migration History (selected)

| Migration | What changed |
|---|---|
| 32→33 | `tasks.life_category` (Work-Life Balance Engine) |
| 33→34 | `mood_energy_logs` table |
| 34→35 | `medication_refills` table |
| 37→38 | `task_completions` table with historical backfill |
| 44→45 | Data-integrity hardening: `ON DELETE SET NULL` for `study_logs.course_pick`, `study_logs.assignment_pick`, `focus_release_logs.task_id` |
| 45→46 | `daily_essential_slot_completions` table |
| 46→47 | `leisure_logs.custom_sections_state` column |
| 47→48 | `projects` lifecycle columns + `milestones` table (Projects Phase 1) |
| 48→49 | `habits.is_built_in` + `habits.template_key`; backfills 6 built-in habit names |
| 49→50 | `habit_completions.completed_date_local` TEXT + index; strftime backfill for timezone-neutral day queries |

---

## 3. API Design

### Base URL
```
https://averytask-api.up.railway.app/api/v1
```

### Authentication

| Endpoint          | Method | Description              |
|-------------------|--------|--------------------------|
| `/auth/register`  | POST   | Create account           |
| `/auth/login`     | POST   | Get JWT access + refresh |
| `/auth/refresh`   | POST   | Refresh access token     |

All other endpoints require `Authorization: Bearer <token>` header.

### Goals

| Endpoint           | Method | Description          |
|--------------------|--------|----------------------|
| `/goals`           | GET    | List all goals       |
| `/goals`           | POST   | Create a goal        |
| `/goals/{id}`      | GET    | Get goal + projects  |
| `/goals/{id}`      | PATCH  | Update a goal        |
| `/goals/{id}`      | DELETE | Delete goal (cascade)|

### Projects

| Endpoint                      | Method | Description               |
|-------------------------------|--------|---------------------------|
| `/goals/{goal_id}/projects`   | GET    | List projects under goal  |
| `/goals/{goal_id}/projects`   | POST   | Create project under goal |
| `/projects/{id}`              | GET    | Get project + tasks       |
| `/projects/{id}`              | PATCH  | Update a project          |
| `/projects/{id}`              | DELETE | Delete project (cascade)  |

### Tasks

| Endpoint                          | Method | Description                |
|-----------------------------------|--------|----------------------------|
| `/projects/{project_id}/tasks`    | GET    | List tasks under project   |
| `/projects/{project_id}/tasks`    | POST   | Create task under project  |
| `/tasks/{id}`                     | GET    | Get task + subtasks        |
| `/tasks/{id}`                     | PATCH  | Update a task              |
| `/tasks/{id}`                     | DELETE | Delete task (cascade)      |
| `/tasks/{id}/subtasks`            | POST   | Create subtask             |

### Cross-Cutting Queries (Dashboard)

| Endpoint                | Method | Description                          |
|-------------------------|--------|--------------------------------------|
| `/tasks/today`          | GET    | All tasks due today                  |
| `/tasks/overdue`        | GET    | All overdue tasks                    |
| `/tasks/upcoming?days=7`| GET    | Tasks due within N days              |
| `/dashboard/summary`    | GET    | Counts: overdue, today, this week, completed this week |

### NLP Endpoint

| Endpoint       | Method | Description                                        |
|----------------|--------|----------------------------------------------------|
| `/tasks/parse` | POST   | NL input → structured task (project, due date, priority) |

**Request:**
```json
{
  "text": "finish pancData3 paper by Friday, high priority"
}
```

**Response:**
```json
{
  "parsed": {
    "title": "Finish pancData3 paper",
    "project_suggestion": "pancData3",
    "due_date": "2026-04-10",
    "priority": 2
  },
  "confidence": 0.92,
  "needs_confirmation": true
}
```

The client always shows a confirmation screen before creating — the NLP endpoint suggests, the user confirms.

---

## 4. NLP Task Parser Design

### How It Works

1. User types or speaks a natural language string
2. Client sends string to `/tasks/parse`
3. Backend calls Claude Haiku with a structured prompt
4. Claude returns JSON with parsed fields
5. Backend validates and returns to client
6. Client shows pre-filled task creation form for user confirmation

### Prompt Template

```python
PARSE_PROMPT = """You are a task parser for a hierarchical task management app.

The user's existing projects are:
{project_list}

Parse the following natural language input into a structured task.
Return ONLY valid JSON with these fields:
- title: string (cleaned up task title)
- project_suggestion: string or null (best matching project name from the list above)
- due_date: string or null (ISO date, interpreting relative dates from today: {today})
- priority: int or null (1=urgent, 2=high, 3=medium, 4=low)
- parent_task_suggestion: string or null (if this sounds like a subtask of something)

Input: "{user_input}"
"""
```

### Why Claude Haiku?
- ~$0.25 per million input tokens / ~$1.25 per million output tokens
- At ~200 tokens per parse call, even 100 tasks/day = ~$0.006/day
- Sub-second latency — feels instant on mobile
- Handles ambiguity well ("Friday" → next Friday, "EOD" → today, "ASAP" → priority 1)

---

## 5. Auth Design

### Flow
1. User registers with email + password
2. Password hashed with bcrypt
3. Login returns JWT access token (15 min) + refresh token (7 days)
4. Access token sent in header for all API calls
5. Refresh token stored securely (Android Keystore on mobile, httpOnly cookie or localStorage on web)
6. On 401, client auto-refreshes and retries

### JWT Payload
```json
{
  "sub": 1,
  "email": "avery@example.com",
  "exp": 1717200000
}
```

---

## 6. Project Structure

```
prismTask/
├── backend/
│   ├── app/
│   │   ├── main.py                  # FastAPI app, CORS, lifespan
│   │   ├── config.py                # Settings (env vars)
│   │   ├── database.py              # Engine, session factory
│   │   ├── models.py                # SQLAlchemy models
│   │   ├── schemas/                 # Pydantic request/response models
│   │   │   ├── auth.py, goal.py, project.py, task.py
│   │   │   ├── dashboard.py, template.py, nlp.py
│   │   ├── routers/                 # API route handlers
│   │   │   ├── auth.py, goals.py, projects.py, tasks.py
│   │   │   ├── dashboard.py, export.py, search.py
│   │   │   ├── app_update.py, calendar.py, ai.py
│   │   │   └── integrations/        # Gmail, Slack, webhook handlers
│   │   ├── services/                # Business logic
│   │   │   ├── auth.py, task_service.py, recurrence.py
│   │   │   ├── urgency.py, nlp_parser.py  # Claude Haiku integration
│   │   │   └── integrations/        # calendar_integration.py, gmail_integration.py
│   │   └── middleware/
│   │       └── auth.py              # JWT dependency
│   ├── alembic/                     # Database migrations
│   ├── tests/                       # 25 pytest files
│   │   ├── routers/                 # dashboard, export, search, app_update, projects
│   │   ├── services/                # recurrence, urgency, NLP edge cases
│   │   └── integration/             # end-to-end workflows + stress tests
│   ├── Dockerfile
│   ├── requirements.txt
│   └── alembic.ini
│
├── app/                             # Android app module (Kotlin 2.3.20 / Jetpack Compose)
│   ├── src/main/java/com/averycorp/prismtask/
│   │   ├── MainActivity.kt          # Single-activity entry point
│   │   ├── PrismTaskApplication.kt  # @HiltAndroidApp
│   │   ├── data/
│   │   │   ├── billing/             # BillingManager — two-tier Free/Pro
│   │   │   ├── calendar/            # CalendarManager, CalendarSyncPreferences
│   │   │   ├── export/              # DataExporter (JSON v5 + CSV), DataImporter
│   │   │   ├── local/
│   │   │   │   ├── dao/             # 25+ Room DAOs
│   │   │   │   ├── database/        # PrismTaskDatabase (v50), Migrations.kt
│   │   │   │   └── entity/          # 32 Room entities
│   │   │   ├── preferences/         # 25+ DataStore preference files
│   │   │   ├── remote/              # Firebase Auth/Firestore, Google Drive,
│   │   │   │                        #   ClaudeParserService, BackendSyncService
│   │   │   └── repository/          # 20+ repositories
│   │   ├── di/                      # Hilt modules (Database, Billing, Network, Prefs)
│   │   ├── domain/
│   │   │   ├── model/               # RecurrenceRule, TaskFilter, LifeCategory,
│   │   │   │                        #   BoundaryRule, NotificationProfile, etc.
│   │   │   └── usecase/             # 35+ use cases (NLP, urgency, streak,
│   │   │                            #   balance, mood, burnout, pomodoro, etc.)
│   │   ├── notifications/           # NotificationHelper, ReminderScheduler,
│   │   │                            #   EscalationScheduler, SoundResolver,
│   │   │                            #   WorkManager workers, BroadcastReceivers
│   │   ├── widget/                  # 7 Glance widgets with per-instance config
│   │   │                            #   (Today, HabitStreak, QuickAdd, Calendar,
│   │   │                            #    Productivity, Timer, Upcoming, Project)
│   │   ├── workers/                 # Background WorkManager workers
│   │   ├── util/, utils/            # Shared helpers
│   │   └── ui/
│   │       ├── a11y/                # TalkBack, font scaling, contrast helpers
│   │       ├── components/          # Shared composables + settings sections
│   │       ├── navigation/          # NavGraph.kt, FeatureRoutes.kt
│   │       ├── screens/             # 40+ feature screens:
│   │       │   ├── today/, tasklist/, addedittask/, projects/
│   │       │   ├── habits/, settings/, templates/, tags/
│   │       │   ├── weekview/, monthview/, timeline/, search/, archive/
│   │       │   ├── analytics/, balance/, mood/, checkin/, review/
│   │       │   ├── notifications/, extract/, pomodoro/, eisenhower/
│   │       │   ├── leisure/, selfcare/, medication/, schoolwork/
│   │       │   ├── briefing/, chat/, coaching/, onboarding/
│   │       │   └── auth/, feedback/, debug/
│   │       └── theme/               # Color, Type, PriorityColors, LifeCategoryColors
│   ├── src/test/                    # 150+ unit test files (see CLAUDE.md)
│   └── src/androidTest/             # 28 instrumentation test files
│
├── web/                             # Web client (React 19 + TypeScript + Vite)
│   ├── src/
│   │   ├── api/                     # Axios API client modules
│   │   ├── components/              # Layout, shared UI primitives
│   │   ├── features/                # Feature screens (auth, tasks, projects,
│   │   │                            #   habits, calendar, eisenhower, etc.)
│   │   ├── hooks/                   # Custom React hooks
│   │   ├── routes/                  # React Router definitions
│   │   ├── stores/                  # Zustand 5 state stores
│   │   ├── types/                   # TypeScript type definitions
│   │   └── utils/                   # Helpers and utility tests
│   ├── package.json
│   ├── vite.config.ts
│   └── playwright.config.ts         # E2E test config
│
├── docs/                            # Design and architecture docs
│   ├── ARCHITECTURE.md              # This document
│   ├── NOTIFICATIONS_DESIGN.md      # Cross-platform notification system
│   ├── ADR-calendar-sync.md         # Architecture decision: backend-mediated calendar sync
│   ├── projects-feature.md          # Projects Phase 1 deep-dive
│   ├── export_import_audit_*.md     # Export/import audit snapshots
│   ├── PRIVACY_POLICY.md / TERMS_OF_SERVICE.md
│   └── RELEASE.md                   # Release checklist
│
├── store/listing/                   # Play Store assets + data safety / content rating
│
├── .github/
│   └── workflows/
│       ├── android-ci.yml           # Android build + unit tests
│       ├── backend-ci.yml           # Backend pytest + lint
│       ├── web-ci.yml               # Web lint + Vitest + Playwright
│       └── release.yml              # Release AAB build
│
├── docker-compose.yml               # Local dev (backend + postgres)
├── README.md
├── CHANGELOG.md
├── CONTRIBUTING.md
├── SECURITY.md
└── CLAUDE.md                        # AI-assistant codebase guide
```

---

## 7. Deployment Architecture

### Local Development
```
docker-compose up
# Starts: FastAPI (hot reload) + PostgreSQL

# Web dev server (separate terminal):
cd web && npm run dev
# Opens at http://localhost:5173, proxies /api to backend

# Android: open in Android Studio and run on device/emulator
```

### Production
- **Backend:** Docker container on Railway (free tier: 500 hrs/month, more than enough)
- **Database:** Railway PostgreSQL (free tier: 1GB, plenty for single-user)
- **Android:** Built via Gradle, distributed through Google Play Store
- **Web:** Static build (`npm run build`) deployable to any CDN/hosting
- **CI:** GitHub Actions runs three pipelines — Android CI, Backend CI (pytest + lint), Web CI (Vitest + Playwright)

### Environment Variables
```env
DATABASE_URL=postgresql://user:pass@host:5432/averytask
JWT_SECRET_KEY=<random-256-bit>
JWT_ALGORITHM=HS256
ANTHROPIC_API_KEY=<your-key>
```

---

## 8. Build History

The original MVP roadmap targeted an Expo / React Native mobile client. That
plan was abandoned in favor of a native Kotlin / Jetpack Compose Android
app plus a React / TypeScript / Vite web app. Both clients share the FastAPI
+ PostgreSQL backend. See the [README](README.md) and [CHANGELOG](CHANGELOG.md)
for the shipping feature set.

---

## 9. Key Technical Decisions Log

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Android UI | Jetpack Compose (Kotlin) | Native performance, Material 3, offline-first with Room |
| Web UI | React + TypeScript + Vite | Fast iteration, large ecosystem, shared backend API |
| Web styling | TailwindCSS | Utility-first, rapid prototyping, small CSS bundles |
| Web state | Zustand | Minimal boilerplate, hooks-native, sufficient for SPA |
| ORM vs raw SQL | SQLAlchemy | Portfolio signal, migration support, type safety |
| JWT vs session auth | JWT | Stateless, standard for mobile + web APIs |
| PostgreSQL vs SQLite (server) | PostgreSQL | Production-grade, shows DB skills, free on Railway |
| Monorepo vs separate repos | Monorepo | Single GitHub link for portfolio, easier to review |
| REST vs GraphQL | REST | Simpler, FastAPI auto-docs, sufficient for this data model |
| Subtask depth limit | Max 1 | Prevents UI complexity explosion, matches most task apps |
| Web bundler | Vite | Fast HMR, native ESM, optimized production builds |
| Web E2E testing | Playwright | Cross-browser support, reliable CI execution |
