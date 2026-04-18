# Projects Feature (v1.4.0)

This document describes the Projects feature that landed across v1.4.0 PRs
`Phase 1` → `Phase 5`. It covers the data model, streak semantics, the
Tasks-tab toggle state flow, Firestore sync shape, and intentional
deviations from the habits pattern with their reasons.

## Scope

Projects in v1.4.0 are **first-class project-management entities** with:
- Lifecycle status (`ACTIVE` / `COMPLETED` / `ARCHIVED`)
- Optional description, start date, end date
- A theme color (hex today, token-ready)
- A child `milestones` table
- A forgiveness-first streak driven by task completions + milestone completions
- A home-screen widget that tracks one user-picked project
- NLP intents in quick-add for create / complete / add milestone
- Firestore round-trip

**Out of scope for v1**: project templates with milestones (the existing
`ProjectTemplateEntity` is a pre-v1.4 scaffold for spawning a
project-with-tasks bundle and is intentionally untouched — see
[§ Naming overlap](#naming-overlap)), sharing / collaboration, Gantt or
timeline visualizations, project-specific notification profiles, Pomodoro
time contributing to the streak, and any migration that moves existing
tasks into a default project.

## Data model

### `projects` table

`app/src/main/java/com/averycorp/prismtask/data/local/entity/ProjectEntity.kt`

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER` PK, autogen | Local Long PK; Firestore doc IDs live in `sync_metadata` |
| `name` | `TEXT` NOT NULL | |
| `description` | `TEXT` nullable | Added in v47→v48 |
| `color` | `TEXT` NOT NULL, default `#4A90D9` | Legacy hex; kept dual-written for back-compat |
| `icon` | `TEXT` NOT NULL, default `📁` | |
| `theme_color_key` | `TEXT` nullable | v1.4 token key; today the picker writes the same hex as `color`, leaving headroom for a future token-based palette without a data migration |
| `status` | `TEXT` NOT NULL, default `ACTIVE` | Stored as `ProjectStatus` enum name |
| `start_date`, `end_date` | `INTEGER` nullable | Epoch millis |
| `completed_at`, `archived_at` | `INTEGER` nullable | Stamped by `completeProject` / `archiveProject` |
| `created_at`, `updated_at` | `INTEGER` NOT NULL | |

Migration `MIGRATION_47_48` adds all v1.4 columns additively. Existing
rows default to `status='ACTIVE'` with null for everything else — no
backfill, no data shape coercion.

### `milestones` table

`app/src/main/java/com/averycorp/prismtask/data/local/entity/MilestoneEntity.kt`

| Column | Type | Notes |
|---|---|---|
| `id` | `INTEGER` PK, autogen | |
| `project_id` | `INTEGER` NOT NULL | FK → `projects.id`, `ON DELETE CASCADE` |
| `title` | `TEXT` NOT NULL | |
| `is_completed` | `INTEGER` NOT NULL, default 0 | |
| `completed_at` | `INTEGER` nullable | Stamped when `is_completed` flips true |
| `order_index` | `INTEGER` NOT NULL, default 0 | User-controlled ordering |
| `created_at`, `updated_at` | `INTEGER` NOT NULL | |

### `tasks.project_id`

`TaskEntity.projectId` predates this feature and is unchanged.
`SET_NULL` on project delete continues to be the behavior.

## Streak semantics

Projects and habits share `domain/usecase/DailyForgivenessStreakCore.kt`,
extracted during Phase 1. The habit `StreakCalculator.calculateResilientDailyStreak`
now delegates into the core too.

### What counts as activity on a project

For a given day, a project has **activity** if **any** of these happened:
1. A task with `task.projectId == project.id` was completed that day.
2. A subtask whose parent task is on the project was completed that day
   (read-time inheritance — we do **not** denormalize `projectId` onto the
   subtask row).
3. A milestone on the project was marked completed that day.

### What does NOT count

- Pomodoro time on project-linked tasks. **Not in v1.** Candidate for v1.1
  once the Pomodoro service's persistence story is stable.
- Project metadata edits (renaming, description changes, status flips).
  Only the task/milestone *completion* event counts.

### Reopening does not retroactively decrement

Activity is an **event log, not derived state**. If the user completed
"email Dr. Aliotta" yesterday and reopens it today (to attach a forgotten
file), yesterday's activity day still counts. This matches the forgiveness
philosophy — the app never retroactively punishes a correction.

Implementation: `TaskCompletionEntity` rows are never deleted on reopen
(the existing `TaskCompletion*` pipeline already behaves this way).

### Subtask inheritance: read time, not write time

When a subtask has no explicit `projectId` but its parent does, we inherit
at read time via the SQL join in `ProjectDao.getTaskActivityDates`:

```sql
SELECT DISTINCT tc.completed_date
FROM task_completions tc
INNER JOIN tasks t ON t.id = tc.task_id
WHERE t.project_id = :projectId
   OR t.parent_task_id IN (SELECT id FROM tasks WHERE project_id = :projectId)
```

This lets the parent task's project change without orphaning historical
subtask activity — a subtask always inherits whatever its current parent's
projectId is.

## Tasks-tab toggle state flow

`ui/screens/tasklist/TaskListScreen.kt`

- A `SingleChoiceSegmentedButtonRow` with `[Tasks | Projects]` sits above
  the body and hides when multi-select mode is on.
- Side persists via `rememberSaveable { mutableStateOf(PANE_TASKS) }` —
  saved-instance-state handles process-death restoration with no custom
  `SavedStateHandle` plumbing on the parent ViewModel.
- Projects pane is an inline composable (`ProjectsPane`) backed by its own
  `ProjectsPaneViewModel`. Its status-filter selection *also* persists via
  `SavedStateHandle` under key `projects_pane_status_filter` so switching
  sides and coming back restores the filter the user left on.
- Scaffold FAB cluster hides when the pane is on Projects — the pane's
  own FAB (+ new project) owns the primary action so only one FAB is
  ever visible.

### Existing "Projects" bottom-nav tab

CLAUDE.md's list of bottom-nav tabs mentioned Projects, but in practice it
was never a bottom-nav tab — it was a detail screen reached via a "Manage
Projects" link inside TaskListScreen. The Phase 2 plan initially called
for removing that nav tab; the actual implementation kept the legacy
`ProjectList` route reachable (no removal needed) and simply added the
segmented toggle as the primary entry point.

## NLP intents (Phase 4)

`domain/usecase/ProjectIntent.kt` + `ProjectIntentParser.kt`

`QuickAddViewModel.onSubmit()` runs the intent parser as a pre-pass before
the existing `NaturalLanguageParser`. Regex patterns cover:

| Intent | Example | Behavior |
|---|---|---|
| `CreateProject` | "start a project called AAPM abstract" | Creates ACTIVE project |
| `CompleteProject` | "mark the pancData paper done" | Finds project by name; calls `completeProject` |
| `AddMilestone` | "add milestone 'finish draft' to AAPM project" | Find-or-create project, then `addMilestone` |
| `CreateTask` w/ hint | "draft intro for the AAPM project" | Falls through to task creation, pre-binding `projectId` |

`CompleteProject` is gated by a heuristic that requires either the literal
word "project" or project-shaped words (`paper`, `draft`, `abstract`,
`report`, `thesis`, `proposal`) — "finish the laundry" therefore falls
through to task creation rather than hijacking into a project completion.

**Not yet:** LLM-routed intents through the backend `/api/v1/tasks/parse`
endpoint. The spec allowed regex-only for `create_project` and "graceful
failure" (fall through to task creation) for the others; the deployed
code does exactly that. A future revision can extend the backend prompt
+ `ParsedTaskResponse` with an `intent` slot and a `schema_version` for
graceful client fallback on unknown intents.

## Firestore sync (Phase 5)

`data/remote/SyncService.kt`, `data/remote/mapper/SyncMapper.kt`

### Collections

Under `users/{userId}/`:
- `projects/` — one doc per project
- `milestones/` — one doc per milestone, references its parent project's
  **cloud ID** (not the local Long id) via `projectCloudId`

### Instance

`FirebaseFirestore.getInstance()` uses the `(default)` instance. Named
instances were avoided per an explicit earlier-project learning — don't
change this without coordinating with the Firebase Console config.

### Conflict resolution

Last-writer-wins by `updatedAt`, matching the habit sync path. When
`SyncService`'s pull listener detects that the local row's `updated_at` is
already ≥ the remote's, the remote write is skipped. This is the same
pattern used for projects pre-v1.4; the Phase 5 extension simply widens
the serialized field set.

### Initial upload order

`SyncService.initialUpload()` runs on the durable
`CoroutineScope(SupervisorJob() + Dispatchers.IO)` (**not `viewModelScope`**)
so a user navigating away doesn't orphan the upload. Order:

1. Projects (needed first so milestones can reference cloud IDs)
2. **Milestones** (new in Phase 5 — depends on project cloud IDs)
3. Tags
4. Habits / habit completions / habit logs
5. Tasks (existing)

### Missing-field safety

`SyncMapper.mapToProject` defaults any post-v1.3-only field to a safe
null/empty when the doc was written before the feature existed:

```kotlin
status = (data["status"] as? String) ?: "ACTIVE"
description = data["description"] as? String
themeColorKey = data["themeColorKey"] as? String
// ...
```

### Security rules — deployment TODO

Firestore security rules live outside this repo in the Firebase Console.
Before enabling project/milestone sync in production, mirror the habits
rules for the new collections — specifically:

```
match /users/{userId}/projects/{projectId} {
  allow read, write: if request.auth.uid == userId;
}
match /users/{userId}/milestones/{milestoneId} {
  allow read, write: if request.auth.uid == userId;
}
```

(Adjust for whatever schema the existing habits rules use — this is the
shape, not the exact verbatim rule set.)

## Widget (Phase 3)

`widget/ProjectWidget.kt` + `ProjectWidgetConfigActivity.kt`

One Glance widget per placed instance, tracking a single user-picked
project (picked at placement time via the config activity). Layout mirrors
`ProjectCard` — theme color stripe, name, progress bar, streak, "Next:
{milestone or next-due task}", task counts, "N days idle" badge when > 3.

Refresh path:
- The existing 15-min `WidgetRefreshWorker` schedule calls
  `WidgetUpdateManager.updateAllWidgets()` which now includes
  `ProjectWidget().updateAll()`.
- `WidgetUpdateManager.updateProjectWidget()` is available for finer-
  grained pushes from project / milestone mutations; wiring the call
  sites is a polish item (periodic refresh covers the semantics for v1).

## Naming overlap: ProjectTemplate

`data/local/entity/ProjectTemplateEntity.kt` (v1.3.0 P15) is **orthogonal**
to the v1.4 Projects feature despite the name. It's a JSON blueprint that
spawns a project-with-tasks bundle when the user picks it from the
templates screen — a scaffold, not a project-management entity. The
entity KDoc carries a note clarifying this. Renaming it to something
less overloaded (`TaskBundleTemplate`, `ProjectScaffold`, …) was
considered and rejected for Phase 1: it would touch a schema rename on
`project_templates`, collide with the already-taken `TaskTemplate*`
name, and force another migration for cosmetic benefit.

## Key deviations from the habits pattern

1. **No per-instance widget config flow for habits.** Habits ship
   without a config activity; projects require one (the widget is
   one-project-per-instance, so the user MUST pick). This is a
   behavioral difference, not a code pattern difference.
2. **Streak activity is compound.** Habits count only their own
   completions; projects union task completions + milestone completions
   + subtask-inherited completions. The core walk is the same; the
   input set is richer.
3. **No "target frequency".** A habit has `targetFrequency` meaning "how
   many times per period to count as met." Projects don't — any
   activity on the day counts. `DailyForgivenessStreakCore` accepts a
   `Set<LocalDate>` so callers with or without target-frequency logic
   can plug in.
4. **Cloud identity via `sync_metadata`, not a `cloud_id` column.** The
   original Phase 0 proposal had a dedicated `cloud_id` column on
   `projects`. Habits already use `sync_metadata` for this, so Phase 1
   followed that pattern instead — no new column, no new migration
   constraint.

## Testing

Phase 1: `DailyForgivenessStreakCoreTest`, `ProjectRepositoryTest`,
`ProjectDaoTest` (extended), `Migration47To48Test`.
Phase 2: `ProjectsPaneViewModelTest`.
Phase 4: `ProjectIntentParserTest`.

Manual E2E (to run on a device): create project → add milestone → add
task with projectId → complete task → verify the streak increments on
the ProjectCard and ProjectDetail → verify Firestore mirrors state on
next push → kill app, reopen, verify state restored.
