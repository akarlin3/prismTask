# Web Parity Gap Analysis

**Date:** 2026-04-23 (audit); updated 2026-04-23 after slices 1+2 shipped.
**Android version:** v1.5.2 (build 689, Room DB v58 per recent migrations)
**Web app:** deployed to https://app.prismtask.app (Railway-hosted static build)
**Purpose:** Audit the PrismTask web client against the Android client to identify
the highest-leverage parity slices to ship now (pre–Phase G). Phase G currently
budgets 4 weeks for "Desktop web app — feature parity with Android"; this
document is the input that lets us shrink that budget.

## Shipped in this pass

- ✅ **Slice 1 — NLP batch ops** ([PR #711](https://github.com/akarlin3/prismTask/pull/711)): QuickAddBar intent detector + `/batch/preview` screen + 30s Sonner undo + 24h Settings → Recent Batch Commands. Covers TASK RESCHEDULE/DELETE/COMPLETE/PRIORITY_CHANGE/PROJECT_MOVE + HABIT COMPLETE/SKIP/ARCHIVE/DELETE + PROJECT ARCHIVE/DELETE. TAG_CHANGE + MEDICATION deferred at apply time (same as Android).
- ✅ **Slice 2 — Onboarding + 4 named themes** ([PR #712](https://github.com/akarlin3/prismTask/pull/712)): Cyberpunk / Synthwave / Matrix / Void color-token sets, theme migration for existing users (default VOID), 9-page onboarding wizard, per-account `users/{uid}.onboardingCompletedAt` Firestore gate.
- ✅ **Slice 3 — Daily briefing + weekly planner** ([PR #714](https://github.com/akarlin3/prismTask/pull/714)): `/briefing` wires `POST /ai/daily-briefing`; `/planner` wires `POST /ai/weekly-plan` with preferences drawer (work days, focus hours, front-loading). Both Pro-gated, 429 rate-limit surfaced with readable toast.
- ✅ **Slice 4 — Analytics dashboard** ([PR #715](https://github.com/akarlin3/prismTask/pull/715)): `/analytics` wires `GET /analytics/summary` + `productivity-score` + `time-tracking` + `habit-correlations`. Summary tiles, productivity area chart, time-tracking bar chart with accuracy coloring, habit-correlations list. Uses `Promise.allSettled` for graceful per-endpoint failure. Skips `/analytics/project-progress` (backend expects integer project_id; web has Firestore string IDs — needs backend change).
- ✅ **Slice 5 — Conversation extraction** ([PR #717](https://github.com/akarlin3/prismTask/pull/717)): new `/extract` route wires `POST /ai/tasks/extract-from-text`. Textarea paste (10k chars), candidate rows with Apply/Skip toggles + due date / priority / project / confidence pills, commit via Firestore.
- ✅ **Slice 6 — Pomodoro+ coaching** ([PR #718](https://github.com/akarlin3/prismTask/pull/718)): `PomodoroCoachPanel` mounts on `PomodoroScreen` and wires `POST /ai/pomodoro-coaching` across all three triggers (pre_session / break_activity / session_recap). Trigger inferred from existing `SessionPhase` so no refactor of the existing flow.
- ✅ **Slice 7 — Eisenhower classify_text** ([PR #720](https://github.com/akarlin3/prismTask/pull/720)): text-only variant via "Classify Text" button + modal on EisenhowerScreen.
- ✅ **Slice 8 — Task editor schedule-tab parity** ([PR #721](https://github.com/akarlin3/prismTask/pull/721)): wired weekday selector, biweekly/weekdays types, after-completion flag, three-way end condition, planned-date field, wired reminder dropdown.
- ✅ **Slice 9 — Today polish** ([PR #722](https://github.com/akarlin3/prismTask/pull/722)): DayBoundary utility + configurable Start-of-Day hour, Today briefing card teaser.
- ✅ **Slice 10 — Medication screen** ([PR #723](https://github.com/akarlin3/prismTask/pull/723)): dedicated `/medication` route with per-day navigation. Tier picker + slot CRUD out of scope (need backend).
- ✅ **Slice 11 — Templates parity (habits + projects)** ([PR #724](https://github.com/akarlin3/prismTask/pull/724)): tabbed UI on TemplateListScreen + curated starter library (6 habits, 4 project blueprints). Custom authoring needs backend.
- ✅ **Slice 12 — Settings sections bundle** ([PR #725](https://github.com/akarlin3/prismTask/pull/725)): Accessibility, Help & Feedback, Maintenance, About — all pure-client.
- ✅ **Slice 13 — Theme typography** ([PR #726](https://github.com/akarlin3/prismTask/pull/726)): per-theme Google Fonts + `--prism-font-body/display/mono` + `.prism-display` utility.
- ✅ **Slice 14 — Theme shape + decorative** ([PR #727](https://github.com/akarlin3/prismTask/pull/727)): per-theme shape / density / glow / personality; opt-in `.prism-card` + per-personality pseudo-element treatments (brackets/terminal/editorial/sunset).
- ✅ **Slice 15 — TAG_CHANGE batch + tag persistence** ([PR #728](https://github.com/akarlin3/prismTask/pull/728)): Firestore `tagIds` array + `setTagsForTask` + applier branch flipped to apply with name→ID resolution.

With fifteen slices merged, the parity gap matrix below has been revised —
see the DONE markers and remaining-gaps section at the end.

**Not shipped — backend-blocked:** Analytics `/project-progress` (backend
takes int `project_id`; web has Firestore string IDs). Requires either a
backend change to accept string IDs or a resolver mapping. Flagged for
Phase G.

---

## 1. Repo structure inventory

| Item | Finding |
|------|---------|
| Subdirectory | `web/` (top-level, sibling of `app/` and `backend/`) |
| Build system | Vite 8 + TypeScript 6 + React 19; scripts in `web/package.json` |
| Deployment | Dockerfile + nginx.conf — suggests container deploy. `.firebaserc` at repo root + `firebase.json` → **probably Firebase Hosting or a static Railway service**. Live URL (app.prismtask.app) confirms it is deployed; exact host is **not obvious from the repo** and should be confirmed with Avery. |
| Dev URL | `http://localhost:5173` via `npm run dev`; proxies `/api` → `https://averytask-production.up.railway.app` |
| Prod backend | `https://averytask-production.up.railway.app/api/v1` (Railway FastAPI) — same backend Android uses |
| Testing | Vitest unit tests (`src/**/__tests__`), Playwright E2E (`e2e/`, 3 spec files, 86 LOC total — **very thin**) |
| CI | `.github/workflows/web-ci.yml` — lint + vitest + build, Playwright in a second job |

Environment vars the web needs (`web/.env.example`):
`VITE_API_BASE_URL`, `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`,
`VITE_FIREBASE_PROJECT_ID`, `VITE_FIREBASE_STORAGE_BUCKET`,
`VITE_FIREBASE_MESSAGING_SENDER_ID`, `VITE_FIREBASE_APP_ID`.

### Data-access model (important nuance)

The web app uses a **split data path** that is not explicitly documented anywhere:

- **Firestore-direct** (via Firebase Web SDK) for `tasks`, `projects`, `tags`, `habits`
  — see `web/src/api/firestore/*.ts` (~2.6K LOC). Stores (`useTaskStore`, etc.)
  subscribe to Firestore snapshots in real time. This is the path Android's
  dual-write sync populates.
- **Backend REST** (Axios + JWT with refresh) for: auth, dashboard summary,
  daily-essentials medication slots, AI features, search, export, sync triggers,
  admin logs, syllabus import, goals, templates, parse. Clients live in
  `web/src/api/*.ts`.

Side effects worth flagging:
- `web/src/api/tasks.ts` (backend REST task CRUD, 47 LOC) **exists but is not
  consumed by any store** — all task mutations go through Firestore directly.
  Likely dormant / superseded.
- `firestore.rules` in this repo is a stub that allows any authenticated user
  full access. The real production rules live only in the Firebase console
  (`docs/FIREBASE_EMULATOR.md` confirms this). Any web-first slice that
  introduces a new Firestore collection will need the production rules updated
  out-of-band; that is explicitly off-limits for this prompt.

---

## 2. Web app current feature inventory

All routes live under `ProtectedRoute` unless noted. Completeness is an honest
estimate based on LOC, imports, and whether the feature talks to live data.

| Route | File | LOC | Purpose | Data source | Auth | Completeness |
|-------|------|----:|---------|-------------|------|-------------:|
| `/login` | LoginScreen.tsx | 176 | Email/password + Google sign-in | Backend `/auth`, Firebase Auth | Public | 90% |
| `/register` | RegisterScreen.tsx | 197 | Register new user | Backend `/auth`, Firebase Auth | Public | 85% |
| `/` | TodayScreen.tsx | 679 | Today focus: overdue/today/upcoming sections, habit row, medication slots, inline quick-add, auto-refresh 60s, collapse state | Firestore (tasks, habits, projects) + `/dashboard/summary` + `/daily-essentials/slots` | Authed | 75% |
| `/tasks` | TaskListScreen.tsx | 1025 | All tasks, filter by project/tag, status toggle, bulk select, drag reorder | Firestore | Authed | 80% |
| `/tasks/:id` | TaskDetailScreen.tsx | 58 | Task detail (small — likely just a redirect into TaskEditor modal) | Firestore | Authed | 30% |
| (modal) | TaskEditor.tsx | 925 | Task create/edit: title, description, subtasks, tags, project, priority, due date, Eisenhower, notes, recurrence | Firestore | Authed | 75% |
| `/projects` | ProjectListScreen.tsx | 568 | Project list with progress, CRUD | Firestore | Authed | 75% |
| `/projects/:id` | ProjectDetailScreen.tsx | 369 | Project detail + milestone tracking | Firestore | Authed | 60% |
| `/habits` | HabitListScreen.tsx | 416 | Habit list, streak badges, complete toggle | Firestore + `/habits/*` | Authed | 70% |
| `/habits/:id/analytics` | HabitAnalyticsScreen.tsx | 734 | Habit analytics (contribution grid, trends) | Firestore | Authed | 70% |
| (modal) | HabitModal.tsx | 288 | Habit create/edit | Firestore | Authed | 70% |
| `/calendar` | CalendarRedirect.tsx | 7 | → `/calendar/week` | — | Authed | 100% |
| `/calendar/week` | WeekViewScreen.tsx | 532 | Week view of scheduled tasks | Firestore | Authed | 70% |
| `/calendar/month` | MonthViewScreen.tsx | 402 | Month view | Firestore | Authed | 70% |
| `/calendar/timeline` | TimelineScreen.tsx | 594 | Timeline / day view with scheduled blocks | Firestore | Authed | 65% |
| `/eisenhower` | EisenhowerScreen.tsx | 690 | Eisenhower matrix, auto-classify button | Firestore + `/ai/eisenhower` | Authed | 75% |
| `/pomodoro` | PomodoroScreen.tsx | 656 | Pomodoro timer + Pomodoro planner | Firestore + `/ai/pomodoro-plan` | Authed | 70% |
| `/timeblock` | TimeBlockScreen.tsx | 295 | Auto-block my day / time blocking | Firestore + `/ai/time-block` | Authed | 65% |
| `/weekly-review` | WeeklyReviewScreen.tsx | 451 | Weekly review generator (schema v2 hybrid) | Firestore + `/ai/weekly-review` | Authed | 75% |
| `/templates` | TemplateListScreen.tsx | 330 | Task template list | Firestore + `/templates/*` | Authed | 60% |
| (modal) | TemplateEditorModal.tsx | 471 | Task template editor | Firestore | Authed | 60% |
| `/schoolwork` | SchoolworkScreen.tsx | 41 | Schoolwork entry (web-only feature: syllabus import) | `/syllabus/*` | Authed | 50% |
| (panel) | SyllabusImport.tsx / SyllabusReviewPanel.tsx | 574 | Syllabus parse → review → commit | Backend | Authed | 60% |
| `/archive` | ArchiveScreen.tsx | 473 | Archived tasks | Firestore | Authed | 70% |
| `/settings` | SettingsScreen.tsx | 944 | Theme (light/dark + 12 accent colors), notifications permission, Pro tier, account, keyboard shortcuts, export/import (JSON & CSV), delete data, change password, delete account | Mixed | Authed | 45%* |
| `/admin/logs` | AdminLogsScreen.tsx | 57 | Admin shell; tabs → ActivityLogsPanel + DebugLogsPanel | Backend `/admin/*` | Authed | 75% |

\* Settings completeness is low because **Android has ~30 settings sections**
(see `app/.../ui/screens/settings/sections/`) and the web has ~8.

**Shared components the web already provides**: `TaskRow`, `NLPInput` (single-line
parser), `PriorityBadge`, `SearchModal`, `TagChip`, `ProjectPill`,
`QuickReschedule`, `SortableTaskList`, `KeyboardShortcutsModal`,
`ProUpgradeModal`, `DueDateLabel`, `StatusBadge`, `SplashScreen`,
`OfflineBanner`, `ErrorBoundary`, `SkeletonLoader`. UI primitives: Button,
Modal, ConfirmDialog, Drawer, Dropdown, EmptyState, Input, ProgressBar, Select,
Spinner, Tabs, Toggle, Tooltip, Avatar, Badge, Chip, Checkbox.

---

## 3. Android canonical feature list

From `CLAUDE.md` + a source survey of
`app/src/main/java/com/averycorp/prismtask/ui/screens/`, user-facing surfaces:

### Core

| Surface | Android state | Pro-gated? | Data |
|---------|---------------|------------|------|
| Auth (email/password, Google, sign-out, delete) | Done | Free | Firebase Auth → JWT |
| Onboarding (9-page flow incl. ThemePickerPage) | Done | Free | Local prefs |
| Today screen | Done | Free | Local/Room + sync |
| Task list (filters, search, sort, drag, bulk) | Done | Free | Room |
| Task create/edit (tabbed: Details/Schedule/Organize) | Done | Free | Room |
| Subtasks, tags, projects | Done | Free | Room |
| Recurrence (daily / weekly / biweekly / custom month days / after-completion) | Done | Free | Room |
| Habit tracking (forgiveness streaks, analytics) | Done | Mixed | Room |
| Project detail + milestones | Done | Free | Room |
| Templates (task + habit + project) | Done | Mixed | Room |
| Export/import (JSON + CSV) | Done | Free | Room |
| Search + archive | Done | Free | Room |
| Week / Month / Timeline views | Done | Free | Room |
| Settings (~30 sections) | Done | Mixed | DataStore |

### AI endpoints (6 primary ones on backend)

| Endpoint | Android wired | Web wired | Route |
|----------|---------------|-----------|-------|
| `/ai/eisenhower` + `/ai/eisenhower/classify_text` | ✅ | ✅ (base), ❌ classify_text | Eisenhower |
| `/ai/pomodoro-plan` | ✅ | ✅ | Pomodoro |
| `/ai/pomodoro-coaching` | ✅ | ❌ | Pomodoro |
| `/ai/daily-briefing` | ✅ | ❌ | Briefing |
| `/ai/weekly-plan` | ✅ | ❌ | Planner |
| `/ai/time-block` | ✅ | ✅ | TimeBlock |
| `/ai/weekly-review` | ✅ | ✅ | WeeklyReview |
| `/ai/tasks/extract-from-text` | ✅ | ❌ | Extract |
| `/ai/batch-parse` | ✅ | ❌ | QuickAddBar + BatchPreview |

### v1.4.x / v1.5.x additions (the "new on Android" column)

| Android feature | Backend-ready? | Web state |
|-----------------|----------------|-----------|
| 4 named themes (`themesets/THEME_SPEC.md`; 6 stylistic axes) | N/A (client-only) | ❌ web has light/dark + 12 accent colors |
| Medication slot system (slots + overrides + tier picker + MedicationScreen + Settings editor) | ✅ `/daily-essentials/slots` | Partial — Today has `MedicationSlotList` + detail modal, no dedicated screen, no Settings editor, no tier picker |
| NLP batch ops (QuickAddBar auto-detect + `BatchPreviewScreen` + 30s Snackbar undo + Settings history) | ✅ `/ai/batch-parse` + batch_undo_log | ❌ (missing entirely) |
| Work-Life balance (LifeCategory on every task, `BalanceTracker`, Today balance bar, `WeeklyBalanceReportScreen`) | Partial (`ai.py` schemas mention `life_category`; task schema does not) | ❌ |
| Mood & energy tracking + correlation engine | ❌ no `mood_energy_logs` endpoint | ❌ |
| Morning check-in + streak | ❌ no `check_in_logs` endpoint | ❌ |
| Weekly review storage (separate from `/ai/weekly-review`) | ❌ `weekly_reviews` entity not exposed | Partial (ai synthesizer only) |
| Boundaries + `BoundaryEnforcer` + `BurnoutScorer` + `ProfileAutoSwitcher` | ❌ | ❌ |
| Focus Release + ND-friendly modes + Brain Mode + Shake-to-capture | Partial (`/nd-preferences` get/update) | ❌ |
| Medication refills + `ClinicalReportGenerator` | ❌ | ❌ |
| Conversation extraction (`PasteConversationScreen`) | ✅ `/ai/tasks/extract-from-text` | ❌ |
| Notification profiles + custom sounds + escalation | ❌ no profile/sound tables on backend | ❌ (web uses Notification permission only) |
| Daily Essentials settings editor (housework/schoolwork habit picker) | ✅ prefs on Android, no backend equivalent | ❌ |
| NLP shortcuts (`nlp_shortcuts` table) | ❌ | ❌ |
| Saved filters (`saved_filters` table) | ❌ | ❌ |
| Widgets (8 Glance widgets) | N/A (Android-only) | N/A |
| Reminders / AlarmManager | N/A (Android-only) | Partial (web has `Notification` permission request only; no scheduling UI) |

### Task analytics (contribution grid etc.)

Backend has `/analytics/productivity-score`, `/time-tracking`,
`/project-progress`, `/habit-correlations`, `/summary`. **Web has no Analytics
screen and does not consume any of these endpoints.**

---

## 4. Parity gap matrix

Scoring notes:
- **Gap size** — S (< 300 LOC), M (300–1000 LOC), L (1000+ LOC or multiple screens)
- **Web complexity** — Low / Medium / High (platform-specific work, new APIs, new data model)
- **Leverage** — 1–10 for this session; combines user value, coverage, prerequisite unlocks, and whether backend is ready

| Android surface | Android | Web | Gap | Web complexity | Leverage | Notes |
|-----------------|:-------:|:---:|:---:|:--------------:|:--------:|-------|
| Auth + session | Done | Done | — | — | — | At parity |
| Today screen | Done | Partial | S | Low | 3 | Covers ~75% already; small polish (SoD prompt, layout options) |
| Task list / editor / CRUD | Done | Done | S | Low | 2 | Parity; missing some niceties (tabbed editor Schedule/Organize) |
| Projects + milestones | Done | Partial | S | Low | 3 | Milestone UI is the gap |
| Habits | Done | Partial | S | Low | 3 | Core at parity; analytics screen exists |
| Templates | Done | Partial | M | Medium | 4 | Task templates only; habit + project templates missing |
| Calendar views | Done | Partial | S | Low | 2 | At parity structurally |
| Eisenhower | Done | Done | — | — | — | At parity (missing classify_text for text-only classification) |
| Pomodoro (plan) | Done | Done | — | — | — | At parity |
| **Pomodoro+ coaching** | Done | ✅ Done (PR #718) | — | — | — | All 3 triggers wired via single panel |
| **Daily briefing + weekly plan** | Done | ✅ Done (PR #714) | — | — | — | Both endpoints wired in one slice |
| **Analytics dashboard** | Done | ✅ Done (PR #715) | S | Medium | 3 | 4/5 endpoints wired; project-progress blocked on int/str ID mismatch |
| Time block | Done | Done | — | — | — | At parity |
| Weekly review | Done | Done | — | — | — | At parity (synthesizer) |
| **Daily briefing** | Done | ✅ Done (PR #714) | — | — | — | Shipped in slice 3 |
| **Weekly plan / planner** | Done | ✅ Done (PR #714) | — | — | — | Shipped in slice 3 |
| **NLP batch ops + preview + undo + history** | Done | ✅ Done (PR #711) | — | — | — | TAG_CHANGE + MEDICATION deferred at apply time |
| **Conversation extraction (/extract)** | Done | ✅ Done (PR #717) | — | — | — | Paste + preview + Firestore commit |
| **Onboarding (9-page)** | Done | ✅ Done (PR #712) | — | — | — | Per-account via `users/{uid}.onboardingCompletedAt` |
| **4 named themes (PrismThemeSection)** | Done | ✅ Done, colors only (PR #712) | S | Medium | 4 | Typography, shape, decorative flags deferred |
| Settings — Accessibility | Done | Partial | S | Low | 4 | |
| Settings — 25+ other sections | Done | Missing | L | Medium | 5 | Breaks into many small slices |
| Archive + Search | Done | Partial | S | Low | 3 | |
| Export / Import | Done | Done | — | — | — | |
| Medication slot UI (Today row) | Done | Done | — | — | — | Already wired |
| **Medication dedicated screen + tier picker + settings editor** | Done | Missing | M | Medium | 6 | Backend ready |
| **Medication refills** | Done | Missing | M | Medium | 4 | Needs backend additions |
| Analytics / productivity dashboard | Done | Missing | L | High | 5 | 5 endpoints ready; chart-heavy |
| Work-Life balance (LifeCategory + bar + report) | Done | Missing | L | High | 4 | Backend schema not clean; needs design |
| Mood & energy tracking | Done | Missing | L | High | 2 | **Backend endpoints missing — out of scope** |
| Morning check-in + streak | Done | Missing | M | Medium | 2 | **Backend endpoints missing — out of scope** |
| Boundaries + `BoundaryEnforcer` | Done | Missing | M | High | 1 | **Backend missing — out of scope** |
| Focus Release / Brain Mode / ND prefs | Done | Missing | L | High | 3 | Only `/nd-preferences` backed |
| Notification profiles + custom sounds + escalation | Done | Missing | L | High | 1 | **Backend missing; also platform-specific (Web Push is not a substitute for scheduled local notifications)** |
| NLP shortcuts | Done | Missing | S | Low | 2 | **Backend missing** |
| Saved filters | Done | Missing | S | Low | 2 | **Backend missing** |
| Clinical report generator | Done | Missing | M | Medium | 2 | Needs `/medications` + `/medication-refills` backend |
| Widgets | Done | N/A | — | — | — | Not applicable |
| Reminders / scheduled local notifications | Done | N/A | — | Platform | — | Web Push is push-only; out of scope |

### Summary of parity

- **At parity:** Auth, Task CRUD, Projects core, Habits core, Calendar, Eisenhower, Pomodoro plan, Time block, Weekly review synthesizer, Export/Import, core medication slot row on Today, Archive core, Search core.
- **Backend-ready but unwired on web (shippable now):** Eisenhower classify_text, ND preferences, Medication dedicated screen. (Shipped so far: NLP batch-parse, Daily briefing, Weekly plan, Analytics dashboard, Conversation extraction, Pomodoro+ coaching.)
- **Client-only and shippable:** 4 named themes, 9-page onboarding, more Settings sections, Today polish (SoD prompt, layout options).
- **Blocked by backend work (out of scope):** Mood tracking, Morning check-in, Boundaries/BoundaryEnforcer, Notification profiles/custom sounds, NLP shortcuts, Saved filters, Clinical report + med refills, Work-Life balance (needs life_category on task schema cleanly exposed).

---

## 5. Two-slice proposal

Both slices below are chosen so they (a) ship value Android users already have,
(b) require **zero backend work** and **zero Android work**, (c) unlock
follow-on slices, and (d) cover different axes (AI productivity + new-user UX),
so a single PR failure does not waste the whole session.

---

### SLICE 1 — NLP Batch Operations (QuickAddBar auto-detect → BatchPreview → 30s Undo → Settings history)

**Rationale:** Highest-leverage single feature on the Android v1.5.x slate that
is fully backend-ready (`/ai/batch-parse` + batch_undo_log) but has zero web
presence. Ships one of the most-visible AI features at parity. Introduces the
snackbar-undo pattern that future AI slices (extract-from-text, weekly-plan)
will reuse.

**In scope:**
- New `src/api/nlp.ts` (or extend existing `parse.ts`): typed wrapper for
  `POST /ai/batch-parse` → `BatchParseResponse`.
- Enhance `components/shared/NLPInput.tsx` (or build new `QuickAddBar.tsx`): when
  the user presses Enter on a multi-line paste or a string matching batch syntax
  (multi-line, "then"/"and" separators, or explicit `/batch` prefix —
  behavior to be matched with Android's detection), switch to batch mode and
  open BatchPreview.
- New `features/batch/BatchPreviewScreen.tsx`: full-screen modal listing each
  parsed task with title, due date, tags, project, priority. Allow per-row
  toggle-off before commit. Commit = bulk Firestore write.
- New `stores/batchUndoStore.ts`: keeps the last N (10?) committed batches with
  cloud IDs. Reuses Sonner toast + action button for 30s snackbar undo. On
  undo, deletes the Firestore docs created.
- New `features/settings/sections/BatchHistorySection.tsx` mounted inside
  SettingsScreen: list recent batches with "Undo" (if within window) and
  "Commit again" (re-run). Stored client-side first (localStorage/IndexedDB);
  if future backend sync of batch history is desired, it lands in a follow-up.
- Vitest coverage for: batch-command detection heuristic, store reducer for
  commit/undo, API wrapper happy path + error path.
- One Playwright happy-path spec: enter multi-line paste → preview opens → commit → tasks appear on Today.

**Not in scope:**
- No backend changes.
- No cross-device batch history sync.
- No batch-editing (bulk patch) of existing tasks — distinct Android feature.
- No voice/speech integration.

**Data integration path:** Call `/ai/batch-parse`, render preview locally,
write committed tasks to Firestore via the existing `firestoreTasks.create`.
Undo window keeps Firestore doc IDs in memory, deletes on tap.

**Estimated LOC + components:** ~1,100–1,400 LOC across ~7 new files + 2–3
edits. Components: BatchPreviewScreen, BatchPreviewRow, BatchUndoSnackbar,
BatchHistorySection, QuickAddBar enhancement.

**Risk:** Medium. The undo UX requires care (what happens if the user logs out
mid-window? What if a Firestore write fails halfway through a batch?). Answer
those two with: (a) undo window closes on unmount / logout, (b) use
Firestore `writeBatch` so the whole batch commits atomically.

**Unlocks:** The snackbar-undo + preview pattern can be reused for Slice-2
extraction and for future `/ai/daily-briefing` "apply these recommendations"
flows.

---

### SLICE 2 — Onboarding 9-page flow + 4 named themes

**Rationale:** The web app has **no onboarding whatsoever** — new users land on
`/login` and then straight into Today with no orientation. Android's 9-page
flow includes the ThemePickerPage, which on Android picks between **four named
themes** (Minimalist, Prism, Retro, Editorial — from `themesets/THEME_SPEC.md`,
six stylistic axes). Web's current `themeStore` is light/dark + 12 hex accents;
this is not at parity.

Bundling the two is efficient because the onboarding flow already needs a
theme-picker screen, so rewriting themes as named-themes lets both ship from
one coherent PR.

**In scope:**
- **Theme migration:** rewrite `src/stores/themeStore.ts` to carry a
  `themeKey` (Minimalist | Prism | Retro | Editorial) + effective light/dark.
  Build theme-tokens file under `src/theme/themes.ts` with the six-axis schema
  from `themesets/THEME_SPEC.md` (colors, typography, shape, density,
  decorative treatments, behavior flags). Apply via CSS custom properties on
  `<html>`.
- **Rewrite `SettingsScreen` appearance section** (`PrismThemeSection`
  equivalent) to show the 4 theme cards with preview swatches; keep the
  existing accent-color picker as a sub-option within each theme only if the
  spec allows it (the spec implies each theme fixes its palette, so the accent
  picker likely **goes away** or becomes theme-scoped).
- **New `features/onboarding/` module:**
  - `OnboardingScreen.tsx` (route-level container with page indicator + nav).
  - 9 page components matching Android: Welcome, WhatIsPrismTask, CoreConcepts
    (tasks/projects/tags), QuickCapture (NLP intro), SchedulePreview (calendar
    views), HabitsOverview, NotificationsAsk, **ThemePickerPage**,
    ProUpgradeTeaser. (Exact page titles to confirm against Android source
    during implementation.)
- **Route wiring:** new `/onboarding` route. Gate via
  `onboarding_complete` flag in `settingsStore` (localStorage). After login,
  if flag is absent, redirect to `/onboarding` instead of `/`. "Skip" button
  writes the flag without going through all pages.
- **Vitest:** theme reducer tests, page-navigation reducer tests.
- **Playwright:** one happy-path spec (login → onboarding → complete → lands on Today).

**Not in scope:**
- No re-onboarding on upgrade (Android does this; can be a follow-up).
- No deferred-persona flows (Android's ND-mode pick happens in a different place; not onboarding).
- No migration of existing users — everyone already signed in keeps their light/dark + accent mapped to the closest named theme on first load; actual remap can be a small settings prompt.

**Data integration path:** Entirely client-side. No backend, no Firestore.

**Estimated LOC + components:** ~1,300–1,600 LOC across ~11 new files + 3
edits. Components: OnboardingScreen + 9 page components + ThemeCard + theme
tokens module + settings section rewrite.

**Risk:** Low. Mostly presentation. Theme migration has a small risk of
regressing visual parity on already-styled screens; mitigate by keeping the
CSS custom-property names unchanged and just varying the values.

**Unlocks:** Once a six-axis theme system is on web, future Settings sections
that reference theme-aware tokens (Accessibility, Appearance sub-panels)
stop fighting the current light/dark-only model. Also, onboarding is the
natural place to add future ND-mode intro or Pro teaser when those land.

---

## 6. Remaining-gaps preview for Phase G

**Update (2026-04-23 — slices 1–15 shipped — PRs #711, #712, #714, #715,
#717, #718, #720, #721, #722, #723, #724, #725, #726, #727, #728):** Phase G
scope has collapsed to essentially one backend-blocked item plus polish
follow-ups on slices 10 (tier picker + slot CRUD) and 11 (custom habit /
project template authoring).

### What's left (backend-ready web work)

**Polish / follow-ups on existing slices:**
- Medication tier picker + slot CRUD — **deferred on slice 10** because
  `/daily-essentials/*` is slot-toggle only; needs backend additions for
  tier states and slot CRUD.
- Custom habit + project template authoring — **deferred on slice 11**
  because Android's `HabitTemplateEntity` / `ProjectTemplateEntity` are
  Room-local; needs backend tables + endpoints.
- Component migration to `.prism-card` + `.prism-display` utilities
  — shipped in slices 13/14 as opt-in. Existing cards work fine but
  won't carry per-theme shape/decorative treatments until migrated.
  (~S–M, ~1–3 days across all components.)

### Backend-blocked (requires a separate prompt)

- **Analytics `/project-progress`** — backend takes int `project_id`, web
  projects have Firestore string IDs. One of: backend accepts string IDs,
  or a cross-reference resolver, or a separate Firestore-native endpoint.
- **Medication tier states + slot CRUD** — needs new tables + endpoints.
- **Custom habit + project templates** — needs new tables + endpoints.
- **Wellness cluster** (mood, check-in, boundaries, focus release) — same
  as before: no backend endpoints exist.
- **Notification profiles** (custom sounds, escalation) — needs tables +
  Web Push strategy.

Sub-total: roughly **1–3 working days** of polish + component migrations
on web (was 6–10 before slices 7–15 shipped); backend-blocked work is
still the ~8–12 days flagged earlier.

### Blocked by backend / Android source-of-truth work (requires a separate prompt)

- Mood tracking + correlation engine (new `mood_energy_logs` endpoint)
- Morning check-in + streak (new `check_in_logs` endpoint)
- Boundaries + BoundaryEnforcer (new `boundary_rules` endpoint)
- Notification profiles + custom sounds + escalation (new tables + need a Web Push story)
- NLP shortcuts + Saved filters (new endpoints)
- Medication refills + Clinical report (new `medications`/`medication_refills` endpoints)
- Work-Life balance (needs `life_category` on task schema exposed cleanly on backend, plus Android source-of-truth for classifier rules)
- Focus Release / Brain Mode / Shake-to-capture (ND prefs are backed; the rest needs thought and platform primitives that don't map cleanly to web)

Sub-total: roughly **8–12 working days** of combined backend + web work once
the backend endpoints exist.

### Phase G budget implication

- Current Phase G budget: 4 weeks (~20 working days).
- With slices 1–15 shipped: remaining backend-ready web work is ~1–3 days
  of polish (opt-in utility migration, small tidy-ups), plus ~8–12 days of
  backend-blocked work that needs endpoints first.
- Realistic new estimate: **1–3 days** of web-only work, **vs. 4 weeks
  budgeted**. The web parity push has effectively closed the visible Phase G
  gap. Remaining capability gaps are all backend-blocked: wellness cluster
  (mood / check-in / boundaries / focus release), notification profiles,
  medication tier/CRUD, custom habit/project template authoring, analytics
  project-progress ID mismatch.

### New surprises discovered while implementing

Flagged here so the Phase G prompt can plan around them:

1. **Firestore rules for `users/{uid}` root doc.** Slice 2 is the first web
   write to the user doc at the root (not a subcollection). The repo's
   `firestore.rules` is a stub; production rules live in the Firebase
   console. If production rules do not permit writes to `users/{uid}`, the
   onboarding completion write fails silently and the user sees the flow
   again on next sign-in. Recommend confirming or updating production rules
   before the next user-doc write lands (e.g. syncing theme pick or other
   cross-device preferences).
2. **Web tag persistence gap.** The web's Firestore task layer never maps
   tags on/off tasks (task doc's `tags` field is hard-coded `[]` on
   `docToTask`). Any batch TAG_CHANGE — and any future single-task tag
   write — first needs a task-tag persistence decision (embed tag IDs on
   the task doc vs. maintain a separate tag-cross-ref collection). Until
   then slice 1 surfaces TAG_CHANGE rows in the preview but skips them at
   apply time.
3. **Android onboarding completion is local-only.** `OnboardingPreferences`
   (Android) uses DataStore. A user who completes onboarding on web will
   still see it on Android, and vice versa. For real cross-platform parity
   on the "once per account" guarantee, Android needs to read/write the
   same `users/{uid}.onboardingCompletedAt` field. Explicitly out of scope
   for this web-only prompt.
4. **`src/api/tasks.ts` is dormant.** The backend-REST task client exists
   but no store consumes it — all task writes go through Firestore direct.
   A future Phase G slice that wants to move tasks to backend-REST would
   need to adopt that file, but current parity work can treat it as dead
   weight.

---

## 7. Open questions for Avery

1. **Slice choice confirmation.** Do slices 1 (NLP Batch Ops) + 2 (Onboarding + 4 themes) look right, or would you rather swap either for Conversation extraction or Daily Briefing + Weekly Plan? (Those are the next tier down on leverage but each a tighter single PR.)
2. **Theme migration policy for existing users.** On first launch post-migration, should an already-signed-in user (a) be auto-mapped to "Minimalist" (the closest to current light/dark + indigo), (b) see a one-time prompt to pick, or (c) keep their accent color inside a light/dark "Classic" preset that is a fifth theme?
3. **Onboarding gating.** Should new users **required** to complete onboarding (hard gate, with "Skip" that still marks it complete), or let them bypass entirely if they press Esc?
4. **Batch undo persistence.** Android has a `batch_undo_log` table. For the web MVP I'm proposing local-only undo history (in-memory + recent list in localStorage). Acceptable, or do you want cross-device undo history via Firestore from day one?
5. **Firestore rules.** The repo's `firestore.rules` is a stub; the real rules are in the Firebase console. Slices 1+2 do NOT introduce new collections (batch history is local-only, onboarding flags are local-only), so no rules change is needed. Please confirm.
6. **Hosting.** `web/Dockerfile` + `nginx.conf` exist but it is not obvious whether the live site is Railway-hosted, Firebase-hosted, or Vercel. Where does app.prismtask.app actually serve from, and do I need to do anything at PR time to get a deploy preview URL?
7. **Branch strategy.** Auditing consumed this branch (`feature/web-parity-audit`). Should the audit doc land as its own PR to main before slice 1, or is it fine to stack the doc onto the slice-1 PR?

---

## Checkpoint 1 — STOP

Awaiting approval of two slices before writing any implementation code.
