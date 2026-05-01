# "Couldn't Update Task" Snackbar Audit

**Scope.** User-reported: tapping the checkbox on a task in the Task List
screen surfaces the snackbar **"Couldn't update task"**. The exception
itself is swallowed to logcat with a generic `"Failed to toggle complete"`
message — there is no diagnostic surface that lets us identify which
exception fires in the field.

**Date.** 2026-05-01
**Author.** Audit-first sweep
**Pre-audit baseline.** `main` @ `50205ef` (v1.8.15 / build 813)

---

## TL;DR

The snackbar string `"Couldn't update task"` is unique to
`TaskListViewModel.onToggleComplete:679`. It fires from a
`catch (e: Exception)` around the two-call branch
`taskRepository.completeTask(taskId)` /
`taskRepository.uncompleteTask(taskId)`. The exception is logged via
`Log.e("TaskListVM", "Failed to toggle complete", e)` — **no exception
type, message, or task id is included in any user-visible surface**, and
the project ships no Crashlytics-equivalent for non-fatal exceptions, so
field reports of this snackbar are un-debuggable today.

Three structural problems make this report tractable to fix even without
a stack trace:

1. **Observability gap (Item 1, RED, PROCEED).** Every Task List
   handler swallows exceptions to a generic logcat tag. Surface the
   exception class + message in the log line + a structured
   `DiagnosticsLogger` event so the next time a user reports this we
   have something actionable.
2. **Post-transaction side effects fan out even when `completeTask`
   short-circuits (Item 2, YELLOW, PROCEED).** `TaskRepository.kt:386-388`
   runs `syncTracker.trackUpdate` + `calendarPushDispatcher
   .enqueueDeleteTaskEvent` + `widgetUpdateManager.updateTaskWidgets`
   unconditionally, even when the inner transaction returned `null`
   because the row was already completed or got deleted between the
   outer read and the transaction. Idempotent, but: queues spurious
   work and obscures whether a no-op call hit the snackbar branch or
   not.
3. **Stale `isCurrentlyCompleted` flag (Item 3, GREEN, no work).**
   The checkbox passes `task.isCompleted` from the rendered row. If a
   notification "Complete" action raced the UI, the wrong branch is
   taken — `completeTask` for an already-completed task short-circuits
   silently inside the transaction; `uncompleteTask` on a
   never-completed row markIncomplete-no-ops. Neither path throws.
   Worth a noted invariant, no fix.

Verdicts:

- **Item 1 — Observability gap on `onToggleComplete` catch (RED, PROCEED).**
- **Item 2 — Post-transaction side effects always fire (YELLOW, PROCEED).**
- **Item 3 — Stale `isCurrentlyCompleted` UI parameter (GREEN, no work).**
- **Item 4 — Same swallow-and-snackbar pattern across 13 sibling handlers (YELLOW, PROCEED bundled with Item 1).**
- **Item 5 — `tagDao.getTagsForTask(id).first()` could throw on empty Flow (GREEN, no work — Room invariant).**

Improvements 1 + 4 are bundled into a single PR (same file, same
pattern). Improvement 2 lands separately because it touches
`TaskRepository` semantics rather than UI plumbing. Item 5 is logged for
the record but does not need work — Room flows always emit at least
once on first collect, so `.first()` returns the current query result
(empty list if no tags) rather than throwing.

---

## Architectural recap

There is exactly one production path that emits this snackbar:

```kotlin
// app/src/main/java/com/averycorp/prismtask/ui/screens/tasklist/TaskListViewModel.kt:669
fun onToggleComplete(taskId: Long, isCurrentlyCompleted: Boolean) {
    viewModelScope.launch {
        try {
            if (isCurrentlyCompleted) {
                taskRepository.uncompleteTask(taskId)
            } else {
                taskRepository.completeTask(taskId)
            }
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to toggle complete", e)
            snackbarHostState.showSnackbar("Couldn't update task")
        }
    }
}
```

The Today screen's equivalent (`TodayViewModel.onToggleComplete:772`) is
**not** symmetric — it logs but does **not** show a snackbar. So the
user's report localises to the Task List screen, the `onToggleSubtaskComplete`
sibling (which says "Couldn't update subtask"), or the bulk handlers
(Item 4).

Inside the repo:

```kotlin
// app/src/main/java/com/averycorp/prismtask/data/repository/TaskRepository.kt:314
suspend fun completeTask(id: Long): Long? {
    val now = System.currentTimeMillis()
    val task = taskDao.getTaskById(id).firstOrNull() ?: return null
    val tags = tagDao.getTagsForTask(id).first()
    reminderScheduler.cancelReminder(id)

    val nextRecurrenceId = transactionRunner.withTransaction {
        val fresh = taskDao.getTaskByIdOnce(id) ?: return@withTransaction null
        if (fresh.isCompleted) return@withTransaction null
        // ... spawn next-instance, recordCompletion, markCompleted ...
        nextId
    }

    if (nextRecurrenceId != null) {
        syncTracker.trackCreate(nextRecurrenceId, "task")
        calendarPushDispatcher.enqueuePushTask(nextRecurrenceId)
        // reminder for next instance
    }
    syncTracker.trackUpdate(id, "task")              // <-- always runs
    calendarPushDispatcher.enqueueDeleteTaskEvent(id) // <-- always runs
    widgetUpdateManager.updateTaskWidgets()           // <-- always runs
    return nextRecurrenceId
}
```

`uncompleteTask` (`TaskRepository.kt:408`) is symmetric: it also runs
`syncTracker.trackUpdate` + reminder rebind + widget refresh
unconditionally.

Neither method declares throws. The code paths that *can* propagate an
exception out of the catch:

- `taskDao.getTaskById(id).firstOrNull()` — Room flow, throws only if
  the connection is dead (DB closed mid-call)
- `tagDao.getTagsForTask(id).first()` — same; Room flows always emit
  (Item 5, GREEN)
- `transactionRunner.withTransaction { ... }` — propagates SQLite
  exceptions (FK violation, constraint failure, etc.)
- `taskCompletionRepository.recordCompletion(...)` →
  `taskCompletionDao.insert(completion)` — `task_completions.task_id`
  has a `ON DELETE SET NULL` FK (`TaskCompletionEntity.kt:16`), so a
  task deletion between read and insert downgrades to a NULL `task_id`
  rather than throwing. **This is not a likely culprit.**
- `taskDao.markCompleted(id, now)` — UPDATE statement; only throws on
  schema-level errors (e.g. missing column post-migration).

**Most plausible field cause** is a partial-migration state where
`task_completions.spawned_recurrence_id` (added in `MIGRATION_66_67`,
`Migrations.kt:1946`) is missing on a device whose v66 → v67 migration
ran but committed mid-step, or where some other migration in the chain
between the user's installed version and `CURRENT_DB_VERSION = 69`
threw. Without the exception class in the log, we can't confirm.

---

## Item 1 — Observability gap on `onToggleComplete` catch (RED, PROCEED)

**Findings.** The catch block at
`TaskListViewModel.kt:677-680` does this:

```kotlin
} catch (e: Exception) {
    Log.e("TaskListVM", "Failed to toggle complete", e)
    snackbarHostState.showSnackbar("Couldn't update task")
}
```

`Log.e(tag, msg, throwable)` does include the stack trace in logcat —
but field users do not have logcat. The project ships
`diagnostics/DiagnosticsLogger` (used at the SyncTracker layer to log
queue-track events) but it is **not** wired into the Task List
ViewModels. There is no Crashlytics integration for non-fatal exceptions;
the only crash-equivalent surface is the existing
`MigrationInstrumentor` for migration failures.

Net effect: user reports "Couldn't update task" → engineer searches the
codebase → finds the line → cannot identify the exception → has to ask
the user for an `adb bugreport`, which most users can't produce.

**Risk classification.** RED — this is the load-bearing diagnostic gap.
Every "Couldn't update task" report is currently a coin-flip between
"FK violation", "schema mismatch", "Room thread eviction", or "code path
upstream that we don't suspect yet". Any one of those would be obvious
from the exception class.

**Recommendation.** PROCEED. Three-line fix: log the exception class +
message + task id alongside the existing `Log.e`, and pipe the same
fields into `DiagnosticsLogger` if it's available (skip if not Hilt-injected
in this VM). Phase 2 PR keeps this surgical — no behaviour change, no
new error UI, just logging.

---

## Item 2 — Post-transaction side effects always fire (YELLOW, PROCEED)

**Findings.** `TaskRepository.completeTask` (lines 386-388) runs three
side-effect calls outside the transaction:

```kotlin
syncTracker.trackUpdate(id, "task")
calendarPushDispatcher.enqueueDeleteTaskEvent(id)
widgetUpdateManager.updateTaskWidgets()
return nextRecurrenceId
```

These run unconditionally even when:

- `fresh == null` inside the transaction (task deleted between the
  outer `getTaskById` read and the transaction start) → `withTransaction`
  returns `null`, but the post-transaction code continues.
- `fresh.isCompleted == true` inside the transaction (Phase-1 fix from
  PR #1021 made `completeTask` idempotent on already-completed rows) →
  same: `withTransaction` returns `null`, post-transaction code continues.

In both no-op cases:

- `syncTracker.trackUpdate(id, "task")` — upserts a
  `pendingAction = "update"` even though nothing changed. Idempotent
  on the server side, but bloats the sync queue and bumps `updatedAt`.
- `calendarPushDispatcher.enqueueDeleteTaskEvent(id)` — enqueues a
  WorkManager `OP_DELETE` for an already-cancelled or never-existed
  Calendar event. Idempotent on the backend (`/api/v1/calendar/sync/push`
  no-ops on already-deleted events), but burns network + a worker run.
- `widgetUpdateManager.updateTaskWidgets()` — refreshes all task widgets.
  Idempotent, but spurious.

**Why this matters for the user's report:** symmetry. If the user
double-taps a checkbox (rapid tap, accidental swipe + tap), the second
call sees `fresh.isCompleted == true`, returns `null` from the
transaction, and runs the three side effects. None of them throws —
but if **any** of them throws in some edge case (e.g.
`WorkManager.getInstance(context)` throws because the WorkManager
singleton hasn't been initialised because of a Hilt eager-init race),
the exception bubbles out of `completeTask`, into the catch block in
`onToggleComplete`, and the snackbar fires for what was already a
no-op. The user's mental model — "I tapped to complete, the task is
already complete" — is then completely disconnected from the snackbar.

**Risk classification.** YELLOW — not a corruption bug, but a structural
symmetry break that lets the snackbar lie about whether a real
operation happened. The fix gates the post-transaction work on a
`didComplete: Boolean` returned alongside `nextRecurrenceId`.

**Recommendation.** PROCEED. Refactor the transaction block to return
a sealed result type or a `Pair<Boolean, Long?>`:

```kotlin
val (didComplete, nextRecurrenceId) = transactionRunner.withTransaction {
    val fresh = taskDao.getTaskByIdOnce(id) ?: return@withTransaction (false to null)
    if (fresh.isCompleted) return@withTransaction (false to null)
    // ... real completion work ...
    true to nextId
}

if (!didComplete) return null

syncTracker.trackUpdate(id, "task")
calendarPushDispatcher.enqueueDeleteTaskEvent(id)
widgetUpdateManager.updateTaskWidgets()
```

Same fix for `uncompleteTask` — currently it always runs side effects
even when the row wasn't actually completed (e.g. toggle-uncomplete
on a never-completed task with `latestCompletionId == null`).

---

## Item 3 — Stale `isCurrentlyCompleted` UI parameter (GREEN, no work)

**Findings.** `TaskListItemScopes.kt:125, 325, 508` all wire the
checkbox to `viewModel.onToggleComplete(task.id, task.isCompleted)`.
The `task.isCompleted` value is read from the rendered list item — a
snapshot of the StateFlow at composition time.

Race scenario: notification "Complete" action fires
(`CompleteTaskReceiver.kt`) → `taskRepository.completeTask(id)` runs
on a background thread → DB updates `is_completed = 1` → UI hasn't yet
recomposed → user taps checkbox → `onToggleComplete(id, isCompleted = false)`
→ calls `completeTask(id)` again → inside the transaction
`fresh.isCompleted == true` → short-circuits (Item 2 path). No
exception, no snackbar fires today.

**Risk classification.** GREEN — the idempotence guard from PR #1021
(`TaskRepository.kt:333`) makes this race safe. No fix needed.

**Recommendation.** No work. Document the invariant in a comment near
the catch block in Item 1's PR so future readers don't re-introduce
the race assumption.

---

## Item 4 — Same swallow-and-snackbar pattern across 13 sibling handlers (YELLOW, PROCEED bundled)

**Findings.** Inside `TaskListViewModel.kt` alone, the pattern
`catch (e) { Log.e(tag, msg, e); snackbarHostState.showSnackbar("Couldn't ...") }`
appears 13 times:

| Line | Action | Snackbar copy |
|-----:|--------|---------------|
| 426 | onMoveToProject | `"Couldn't move task"` |
| 443 | onCreateProject | `"Couldn't create project"` |
| 585 | onAddTask | `"Couldn't add task"` |
| 596 | onAddSubtask | `"Couldn't add subtask"` |
| 616 | onDeleteSubtask | `"Couldn't delete subtask"` |
| 627 | onReorderSubtasks | `"Couldn't reorder subtasks"` |
| 663 | onReorderTasks | `"Couldn't reorder tasks"` |
| 679 | **onToggleComplete** | **`"Couldn't update task"`** |
| 694 | onToggleSubtaskComplete | `"Couldn't update subtask"` |
| 705 | onDeleteTask | `"Couldn't delete task"` |
| 723 | onCompleteTaskWithUndo | `"Couldn't complete task"` |
| 743 | onDeleteTaskWithUndo | `"Couldn't delete task"` |
| 765 | onMoveToTomorrow | `"Couldn't move to tomorrow"` |

Plus `TaskListViewModelBulk.kt:38, 88, 193` for bulk handlers. Every
one of them has the same diagnostic gap: exception swallowed to logcat,
no exception class in the log message, no structured event.

**Risk classification.** YELLOW — Item 1 is the user-reported one;
the others are siblings of the same antipattern that will become
the next un-debuggable report.

**Recommendation.** PROCEED, bundled into Item 1's PR. Extract a tiny
helper that takes a `tag`, `msg`, `throwable`, and snackbar text, and
applies the same diagnostic pattern uniformly. Single-file diff stays
under ~50 lines net.

---

## Item 5 — `tagDao.getTagsForTask(id).first()` could throw on empty Flow (GREEN, no work)

**Findings.** `TaskRepository.completeTask:317` calls
`tagDao.getTagsForTask(id).first()` outside the transaction.
`getTagsForTask` is a `@Query` Room method returning `Flow<List<TagEntity>>`.
Per Room's contract, the flow emits exactly once on first collection
with the current result of the query, then continues to re-emit on
invalidation. `.first()` returns immediately with the first emission.

If a task has no tags, the JOIN returns an empty result set → empty
list → `.first()` returns `emptyList()`. Does **not** throw
`NoSuchElementException`.

**Risk classification.** GREEN — Room invariant holds. Not a candidate
root cause.

**Recommendation.** No work.

---

## Improvement table (sorted by wall-clock-savings ÷ implementation-cost)

| Rank | Item | Verdict | Bundle | Est. PR size | Wall-clock saved |
|-----:|------|---------|--------|--------------|------------------|
| 1 | **Item 1 + 4** — diagnostic logging on Task List catch blocks | RED + YELLOW → PROCEED | bundled PR | ~50 LOC, 1 file | every future "Couldn't ..." report becomes self-diagnostic; eliminates ~2-4h of back-and-forth per report |
| 2 | **Item 2** — gate post-transaction side effects on actual completion | YELLOW → PROCEED | separate PR | ~30 LOC, 1 file + 1 test | removes spurious sync/calendar/widget churn on no-op calls; ~1-2h per future regression-hunt where snackbar lies about whether work happened |
| - | Item 3 — stale `isCurrentlyCompleted` parameter | GREEN | no work | — | — |
| - | Item 5 — `getTagsForTask(id).first()` empty-flow concern | GREEN | no work | — | — |

---

## Anti-pattern flag (worth noting, no PR scoped here)

**Pre-transaction-read anti-pattern recurrence.** `TaskRepository.completeTask`
reads `task` and `tags` outside the transaction (lines 316-317), then
re-reads `fresh` inside the transaction. This was specifically called
out as an anti-pattern in `RECURRING_TASKS_DUPLICATE_DAILY_AUDIT.md` Phase 3
(`docs/audits/RECURRING_TASKS_DUPLICATE_DAILY_AUDIT.md:391-400`). The
recurring-tasks audit deferred the broader sweep ("data-integrity audit"
follow-up); it's still open. The `tags` read in particular is **not**
re-read inside the transaction, so a tag added/removed between the
outer read and `recordCompletion` won't be reflected in the
`task_completions.tags` snapshot. Low impact (analytics-only field) but
a cousin of the recurring-tasks Item 1 bug. Worth a follow-up sweep.

**Inconsistent error UX between Today and Task List.** `TodayViewModel.onToggleComplete:772`
has the same try/catch, logs identically, but does **not** show a
snackbar. So a user who reports "Couldn't update task" definitely
tapped from Task List, not Today. This asymmetry should be normalised
either way (both should snackbar, or neither should), but that's a
copy/UX decision, not a bug.
