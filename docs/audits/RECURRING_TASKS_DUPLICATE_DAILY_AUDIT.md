# Recurring tasks duplicate each day audit

**Scope.** User-reported: "Repeating tasks duplicate each day." Daily-recurring
tasks ship one extra incomplete copy per day, growing the next-day backlog
linearly with each affected task.

**Date.** 2026-04-30
**Author.** Audit-first sweep
**Pre-audit baseline.** `main` @ `d6c1090e`

---

## TL;DR

`TaskRepository.completeTask` is non-idempotent on recurring rows and the
"Undo" path is non-reversible. Each *additional* completion of the same
recurring task spawns another copy of the **next** occurrence, with no
guard on `isCompleted` and no rollback of the spawned child when the user
taps UNDO. Two real-world flows trip this:

1. **Undo + redo** (every Today / TaskList swipe-complete uses it). UNDO
   calls `uncompleteTask`, which only flips `is_completed = 0` on the
   parent and leaves the spawned next-instance in the database. The second
   `completeTask` call sees a recurring task with a due date and spawns
   yet another next-instance. Net effect: one extra duplicate per
   undo-redo cycle, indefinitely.
2. **Rapid double-tap or chained UI events** (checkbox + swipe both
   firing). Each tap is a separate `viewModelScope.launch`. The only
   intra-call atomicity is `transactionRunner.withTransaction { ... }`,
   which does NOT short-circuit when the row is already completed.

Verdicts:

- **Item 1 — `completeTask` non-idempotence (RED, root cause, PROCEED).**
- **Item 2 — `uncompleteTask` doesn't roll back the spawned child (RED, PROCEED with Item 1).**
- **Item 3 — `EisenhowerViewModel.completeTask` bypasses the repository (YELLOW, DEFER).**
- **Item 4 — Multi-device / sync re-entry duplicates (GREEN, no work).**
- **Item 5 — `DailyResetWorker` auto-spawns recurring instances (GREEN, no work — it doesn't).**

Improvement table at the bottom is sorted by wall-clock-savings ÷
implementation cost. Items 1+2 are bundled into a single PR — fan-out
would be churn (same file, intertwined invariants).

---

## Architectural recap

There is exactly one production path that creates the next occurrence of
a recurring task: `TaskRepository.completeTask`
(`app/src/main/java/com/averycorp/prismtask/data/repository/TaskRepository.kt:299`).
The relevant block:

```kotlin
suspend fun completeTask(id: Long) {
    val now = System.currentTimeMillis()
    val task = taskDao.getTaskById(id).firstOrNull()
    val tags = if (task != null) tagDao.getTagsForTask(id).first() else emptyList()

    reminderScheduler.cancelReminder(id)

    val nextRecurrenceId = transactionRunner.withTransaction {
        if (task != null) {
            taskCompletionRepository.recordCompletion(task, tags)
        }
        val nextId = if (task?.recurrenceRule != null && task.dueDate != null) {
            val rule = RecurrenceConverter.fromJson(task.recurrenceRule)
            val nextDueDate = rule?.let { RecurrenceEngine.calculateNextDueDate(task.dueDate, it) }
            if (rule != null && nextDueDate != null) {
                val updatedRule = rule.copy(occurrenceCount = rule.occurrenceCount + 1)
                val nextDraft = task.copy(
                    id = 0,
                    isCompleted = false,
                    dueDate = nextDueDate,
                    recurrenceRule = RecurrenceConverter.toJson(updatedRule),
                    completedAt = null,
                    createdAt = now,
                    updatedAt = now
                )
                val nextTask = nextDraft.copy(lifeCategory = resolveLifeCategoryForInsert(nextDraft))
                taskDao.insert(nextTask)
            } else null
        } else null
        taskDao.markCompleted(id, now)
        nextId
    }
    // …schedule reminder + sync push for nextRecurrenceId…
}
```

Three properties of this block matter for duplication:

- The spawn predicate is `task.recurrenceRule != null && task.dueDate != null`.
  It does NOT include `!task.isCompleted`. The `task` snapshot is read once at
  the top of the function, **before** the transaction, and is NOT re-read
  inside the transaction.
- The original task's `recurrenceRule` and `dueDate` columns are never
  cleared — `markCompleted` only sets `is_completed = 1` /
  `completed_at = now`. So a re-read of the row (after a previous complete
  succeeded) still satisfies the spawn predicate.
- `uncompleteTask` is purely a reverse-mark — it does not delete the
  child that `completeTask` may have inserted, and it does not know the
  child's id.

The "Undo" scaffolding in `TodayViewModel.onCompleteWithUndo` (`:786`),
`TaskListViewModel.onCompleteTaskWithUndo` (`:710`), and
`TaskListViewModelBulk.onBulkComplete` (`:17`) treats the snackbar UNDO
as `uncompleteTask(taskId)` — a one-line reverse — with no awareness that
recurrence forks a child row.

---

## Item 1 — `completeTask` non-idempotence (RED, root cause, PROCEED)

**Findings.**

- `TaskRepository.completeTask:299` reads `task` once via
  `getTaskById(id).firstOrNull()`, then enters
  `transactionRunner.withTransaction { ... }`. Within the transaction
  the spawn check is `task?.recurrenceRule != null && task.dueDate != null`
  (`:316`). There is no guard on `task.isCompleted`. Calling `completeTask`
  twice on the same row produces two `taskDao.insert(nextTask)` rows
  with the same computed `dueDate`.
- This is reachable from at least three documented user paths:
  - **Undo + redo cycle.** See Item 2.
  - **Concurrent rapid taps.** UI completion entry points wrap each tap
    in its own `viewModelScope.launch` (e.g.
    `TodayViewModel.onToggleComplete:772`,
    `TaskListViewModel.onToggleComplete:669`). Coroutines run on a shared
    dispatcher, and the second launch can begin before the first's
    transaction commits — but even after the first commits, the second
    re-reads the same row outside its transaction, sees
    `recurrenceRule != null && dueDate != null` (still both set), and
    inserts another child.
  - **Sync re-entry.** Backend pull does NOT call `completeTask` (it
    writes raw rows via `taskDao.insert`), so this specific path is not
    a vector. But any future code that re-invokes `completeTask` on an
    already-completed recurring task would duplicate. (See Item 4.)
- Existing tests cover the happy-path single-call:
  `RecurrenceIntegrationTest.test_completeRecurringTask_createsNextOccurrence`
  (`app/src/androidTest/java/com/averycorp/prismtask/RecurrenceIntegrationTest.kt:69`),
  `RecurrenceSmokeTest.completingDailyRecurringTask_createsNextOccurrence`
  (`app/src/androidTest/java/com/averycorp/prismtask/smoke/RecurrenceSmokeTest.kt:48`).
  Neither asserts that a *second* `completeTask` call is a no-op. The
  word "twice" / "double" / "idempot" appears nowhere in the recurrence
  test files.

**Risk classification.** RED. The bug grows the user's backlog by one
spurious task per affected daily-recurring task per affected
undo/double-tap event. For a user with 5 recurring daily tasks, even one
mistaken UNDO per task per day produces 35 extra tasks in a week.

**Recommendation.** PROCEED. Inside the transaction, re-read the row
fresh (or check `task.isCompleted` upfront with an explicit early
return) and short-circuit when the row is already complete:

```kotlin
val nextRecurrenceId = transactionRunner.withTransaction {
    val fresh = taskDao.getTaskByIdOnce(id) ?: return@withTransaction null
    if (fresh.isCompleted) return@withTransaction null   // idempotent guard
    // …existing spawn block, using `fresh` instead of the pre-transaction `task`…
}
```

Re-reading inside the transaction also closes the rapid-tap concurrency
window: a second invocation that races the first will observe
`isCompleted = true` once the first transaction commits and bail out
without spawning.

The pre-transaction `task` read should still feed `recordCompletion`
side data (tags) and the cancel-reminder call, but the spawn / mark
must use the freshly-read row to be safe.

---

## Item 2 — `uncompleteTask` doesn't roll back the spawned child (RED, PROCEED with Item 1)

**Findings.**

- `TaskRepository.uncompleteTask:367` only calls
  `taskDao.markIncomplete(id, now)` and re-arms the reminder. It has no
  knowledge of the spawned next-instance.
- The spawned next-instance survives an UNDO. Subsequent
  `completeTask(id)` re-invocations spawn a *new* sibling next-instance,
  duplicating the row.
- Repro path (single device, no rapid taps, completely standard UI):
  1. Open Today, swipe-complete a daily-recurring task.
  2. Tap UNDO on the snackbar (within the snackbar's `Short` duration).
  3. Swipe-complete the same task again.
  4. Tomorrow's view now shows the daily task **twice**.

**Risk classification.** RED. This is the highest-volume duplicate
vector because the snackbar UNDO is an explicit, advertised user gesture
in three different screens (Today, TaskList, BulkComplete). Most users
have hit it.

**Recommendation.** PROCEED, bundled with Item 1. Two complementary
fixes:

1. **Capture the spawned id in the UNDO scaffolding.** Have
   `completeTask` return the spawned `nextRecurrenceId?` to its callers.
   (It already computes it; today the callers ignore it.) Then
   `onCompleteWithUndo` / `onCompleteTaskWithUndo` / `onBulkComplete`
   can call a new `uncompleteTask(taskId, spawnedChildId = next)`
   variant that deletes the child row before flipping the parent
   incomplete.
2. **Item 1 idempotence guard** as a defensive backstop, so even if a
   future caller path forgets to pass the spawned id, double-completion
   is still safe.

Both should land together — Item 1 alone leaves the user with an
already-spawned orphan after UNDO; Item 2 alone leaves any non-UNDO
re-invocation duplicating.

---

## Item 3 — `EisenhowerViewModel.completeTask` bypasses the repository (YELLOW, DEFER)

**Findings.**

- `EisenhowerViewModel.completeTask:179` calls
  `taskDao.markCompleted(taskId, System.currentTimeMillis())` directly,
  bypassing `TaskRepository.completeTask`. Side effects skipped:
  - **Recurrence next-instance is NOT spawned.** A daily-recurring task
    completed from the Eisenhower screen vanishes for good — no
    "tomorrow" instance appears. This is the *opposite* of the
    duplication bug, but it's the same broken path.
  - Reminder cancel is skipped (a stale alarm can fire for the
    completed task).
  - Sync tracking, calendar push, widget refresh are all skipped.
- This is independent of the duplicate-each-day complaint and could be
  its own audit. Logging here so it doesn't get lost.

**Risk classification.** YELLOW. User-visible (recurring tasks
disappear from Eisenhower without re-spawning) but a distinct
incident-class from the audit's stated scope.

**Recommendation.** DEFER to a follow-up audit / PR. Trivial fix
(replace direct DAO call with `taskRepository.completeTask`), but
needs its own connected-test coverage and shouldn't bloat the
duplicate-fix bundle.

---

## Item 4 — Multi-device / sync re-entry duplicates (GREEN, no work)

**Findings.**

- `BackendSyncService.applyTaskChanges:406` writes pulled rows via
  `taskDao.insert(task)` keyed on the server-provided `clientId`
  (the local primary key). Last-write-wins on `updatedAt`. There is
  no path that calls `TaskRepository.completeTask` from the sync
  applier — so a pulled-down "this task is now completed" change does
  NOT spawn a next-instance on the receiver. Whoever first marked
  the task complete already spawned the child locally and pushed both
  the parent's mark-complete AND the spawned child as separate sync
  changes.
- The risk pattern would be: device A completes, spawns child A1.
  Device B completes the same task before pulling A's changes — spawns
  child B1. Both A1 and B1 land on the server with distinct clientIds
  and are pulled by the other device. **This produces a duplicate.**
  But it requires concurrent offline edits on two devices, not the
  single-device "every day" pattern the user described, and it's also a
  property of any last-write-wins spawn-on-complete model. Out of scope
  for this audit; flag as a known multi-device edge case.

**Risk classification.** GREEN for the reported scope.

**Recommendation.** No work. (If a follow-up multi-device dedupe
audit fires, it should look at deterministic child-id derivation —
e.g. `next_id = parent_id + occurrence_index` — so concurrent spawns
collapse on the server.)

---

## Item 5 — `DailyResetWorker` auto-spawns recurring instances (GREEN, no work — it doesn't)

**Findings.**

- `DailyResetWorker.kt:43` only refreshes widgets and re-schedules
  itself. It does NOT call `RecurrenceEngine` or insert any rows. No
  other worker or scheduler calls `RecurrenceEngine.calculateNextDueDate`
  — pickaxe across `app/src/main`:
  ```
  TaskRepository.kt:318  ← only production caller
  ```
- Hypothesis "a daily worker is creating today's recurring tasks
  alongside the completion-driven flow" is therefore false.

**Risk classification.** GREEN.

**Recommendation.** No work.

---

## Improvement table

Sorted by **wall-clock-savings ÷ implementation cost** (impact per
hour of work). One bundled PR — see Item 2 rationale.

| Rank | Improvement | Items | Wall-clock savings | Cost | Ratio |
|-----:|-------------|-------|--------------------|------|-------|
| 1 | Idempotent `completeTask` + `uncompleteTask` rolls back spawned child + capture-spawned-id in UNDO scaffolding | 1 + 2 | High — eliminates the user-reported duplicate-each-day bug end-to-end and its multi-device near-cousin | ~2-3h (one repository, three viewmodels, two new connected tests for double-complete + undo-redo) | **~5-6×** |
| 2 | Route `EisenhowerViewModel.completeTask` through `TaskRepository.completeTask` | 3 | Medium — restores recurrence + reminder cancel + sync on Eisenhower complete | ~1h (one viewmodel, one connected test) | ~3× |

## Anti-patterns flagged (worth noting, not fixing)

- **Pre-transaction reads feeding inside-transaction writes.**
  `TaskRepository.completeTask` reads `task` outside the transaction
  and never re-reads inside. This is the proximate enabler for the
  rapid-tap race in Item 1. There are similar shapes in
  `completeTask`/`duplicateTask`/`moveTask` — worth flagging in a
  data-integrity sweep.
- **No-arg `uncompleteTask` paired with a spawning `completeTask`.**
  Whenever a "complete" mutation has a side effect (spawning a child,
  writing a completion-history row, …), the matching "uncomplete" must
  either be wired with the side-effect handles or explicitly document
  that it's a partial reverse. Today neither is true.
- **DAO calls from ViewModel layer that duplicate repository methods.**
  `EisenhowerViewModel.completeTask:179` is the cleanest example, but
  pickaxe finds a few more `taskDao.markCompleted` direct calls in
  test/seeder code. ViewModels should never bypass repositories for
  mutating operations — repositories are where reminder, sync, widget,
  and recurrence orchestration lives.
