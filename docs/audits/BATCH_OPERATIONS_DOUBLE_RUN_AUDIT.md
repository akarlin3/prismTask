# Batch Operations Double-Run Audit

**Scope.** User report: *"All batch operations seem to try and run twice.
Sometimes it offers different options in the second go."* Audit the entry
points and execution paths of every batch surface in the app to identify
which one(s) double-fire and why.

**Why now.** PR #1049 (`fix(batch): close BatchPreview re-fire after Apply`,
commit `92d404e`) closed Path P1 of the prior re-fire audit
(`docs/audits/BATCH_PREVIEW_REFIRE_AUDIT.md`) by adding terminal
`Applied` state + widening the `loadPreview` re-entry guard. That audit
explicitly flagged **Path P2** (fresh VM from a re-navigation triggered
by a `_batchIntents` re-emit upstream of `BatchPreviewViewModel`) as
*not defended* by the fix and named it as the next-most-likely
mechanism to investigate. The user's symptom — *"different options in
the second go"* — uniquely indicts a **non-deterministic Haiku
re-parse**, not idempotent local mutations. That collapses the search
space onto Path P2.

**Investigation method.** (1) Map every batch operation surface (DAO,
repository, ViewModel, UI) — done via Explore-agent sweep. (2) For each
surface, classify whether double-execution is *possible* (no re-entry
guard, no idempotency, no debouncing) vs. *defended*. (3) Trace the
`_batchIntents` emit-to-navigate chain end-to-end to confirm or rule
out the user's specific symptom (different options on the second go).

---

## A1 — Batch operation surface map (GREEN, no work)

Inventory of every code path that mutates >1 entity from a single user
action. Source: subagent sweep + manual verification of cited line
ranges.

**DAO layer** (all properly `@Transaction`-wrapped, single SQL `UPDATE …
WHERE id IN (:ids)`; cannot double-execute internally):

- `TaskDao.batchUpdatePriority` (`app/src/main/java/com/averycorp/prismtask/data/local/dao/TaskDao.kt:335`)
- `TaskDao.batchReschedule` (`TaskDao.kt:345`)
- `TaskDao.batchMoveToProject` (`TaskDao.kt:355`)
- `TaskDao.batchAddTag` / `batchRemoveTag` (`TaskDao.kt:366`, `:375`)
- `BatchUndoLogDao.insertAll` (`BatchUndoLogDao.kt`)

**Repository layer** (all wrap one DAO call; reminder-loop in
`batchReschedule` is `FLAG_UPDATE_CURRENT`-idempotent at the alarm
layer):

- `TaskRepository.batchUpdatePriority` (`TaskRepository.kt:574`)
- `TaskRepository.batchReschedule` (`TaskRepository.kt:609`) — loops
  `reminderScheduler.scheduleReminder` per task; safe because
  `PendingIntent.FLAG_UPDATE_CURRENT` is set in `ReminderScheduler.kt:78–82`.
- `TaskRepository.batchMoveToProject` (`TaskRepository.kt:635`)
- `TaskRepository.batchAddTag` / `batchRemoveTag` (`TaskRepository.kt:652`)
- `BatchOperationsRepository.applyBatch` (`BatchOperationsRepository.kt:176`) —
  AI batch apply, runs inside `database.withTransaction` but **generates
  a fresh `UUID.randomUUID()` per call** (`:182`); no idempotency on
  `(commandText, mutations)`. Two calls = two distinct `batch_id`s in
  `batch_undo_log`.

**ViewModel layer — multi-select bulk edits** (TaskListViewModelBulk):

- `onBulkComplete`, `onBulkDelete`, `onBulkSetPriority`, `onBulkReschedule`,
  `onBulkApplyTags` (`TaskListViewModelBulk.kt:17–196`). All run inside
  `viewModelScope.launch`. None have an explicit re-entry guard, but
  the underlying ops are idempotent at the DAO/alarm level (priority
  set, reminder reschedule with `FLAG_UPDATE_CURRENT`, tag add/remove).
  A double-tap would produce no observable double-effect — and crucially
  **could not produce "different options the second time"** because
  there is no re-parse in the loop.

**ViewModel layer — AI batch (the prime suspect)**:

- `BatchPreviewViewModel.loadPreview` / `approve`
  (`BatchPreviewViewModel.kt:60`, `:252`). Re-entry guards added in
  PR #1049: bail in `Loading` / `Committing` / `Applied`, bail in
  `Loaded` when `commandText` matches. Approve transitions to terminal
  `Applied`. **Within a single VM instance, this is closed.**

**Worker / dispatcher layer**:

- `DefaultCalendarPushDispatcher.enqueuePushTask`
  (`DefaultCalendarPushDispatcher.kt:30–58`) uses `WorkManager.enqueue()`,
  not `enqueueUniqueWork`. Each call adds a fresh worker. (YELLOW —
  see A8.)
- `BatchUndoSweepWorker` uses `enqueueUniquePeriodicWork` with
  `ExistingPeriodicWorkPolicy.UPDATE` (`BatchUndoSweepWorker.kt:68–89`).
  Safe.
- `ReminderScheduler.scheduleReminder` uses `FLAG_UPDATE_CURRENT`
  (`ReminderScheduler.kt:78–82`). Safe.

---

## A2 — `BatchPreviewViewModel` re-entry (GREEN, already closed by PR #1049)

`loadPreview` short-circuits on `Loading` / `Committing` / `Applied` /
`Loaded(sameCommand)`. `approve` writes terminal `Applied` synchronously
*before* launching the apply coroutine
(`BatchPreviewViewModel.kt:261–293`), so a second `approve()` tap reads
state as `Committing`/`Applied` and returns at line 253 (`as? Loaded`
returns null).

Verified: tests `BatchPreviewViewModelTest` (PR #1049) cover the
in-VM re-fire matrix.

---

## A3 — `BatchPreviewScreen.LaunchedEffect(commandText)` (GREEN)

`LaunchedEffect(commandText)` (`BatchPreviewScreen.kt:72–74`) re-fires
only when `commandText` changes. Within one screen instance, the route
arg is stable — the LaunchedEffect runs once per VM lifetime. The VM's
re-entry guard catches any spurious re-run.

**However:** a *new* `BatchPreviewScreen` composable from a *new*
`NavBackStackEntry` gets a *fresh* `BatchPreviewViewModel`
(per-back-stack-entry scoping via `hiltViewModel()`), and that VM is
in `Idle` state — so its `LaunchedEffect` legitimately re-parses. This
is exactly the Path P2 the prior audit flagged. Whether Path P2 actually
triggers depends on whether anything upstream emits twice.

---

## A4 — `QuickAddViewModel.onSubmit` batch-path re-entry race (RED → PROCEED)

**This is the bug.**

`onSubmit` (`QuickAddViewModel.kt:346–537`) has no re-entry guard at the
top. The batch path (`:359–374`) emits to `_batchIntents` inside
`viewModelScope.launch` *without* toggling `_isSubmitting`:

```kotlin
// QuickAddViewModel.kt:359-374
val batchIntent = batchIntentDetector.detect(text)
if (batchIntent is BatchIntentDetector.Result.Batch) {
    if (!proFeatureGate.hasAccess(ProFeatureGate.AI_BATCH_OPS)) {
        viewModelScope.launch {
            _voiceMessages.emit(
                "Batch commands are a Pro feature — upgrade to use them."
            )
        }
        return
    }
    viewModelScope.launch {
        _batchIntents.emit(batchIntent.commandText)
        inputText.value = ""
    }
    return
}
```

The Send button (`QuickAddBar.kt:269–278`) is gated by
`enabled = inputText.isNotBlank() && !isSubmitting`, but `isSubmitting`
is **never set** for the batch path, so the button stays enabled. The
IME `Done` action (`QuickAddBar.kt:281–286`) only checks `isNotBlank()`
— it does not check `isSubmitting` at all.

**Race window.** `viewModelScope.launch` returns immediately. Compose
dispatches click events serially on the main thread, but a second tap
that lands *after* `onSubmit` returns synchronously and *before* the
launched coroutine sets any guard will see `inputText` still populated
(it's only cleared inside the coroutine, after the suspending `emit`)
*and* `isSubmitting == false`. Both taps detect batch intent. Both
emit. Both navigations fire.

**Downstream effect.** Two `navController.navigate(BatchPreview.create(commandText))`
calls (`AIRoutes.kt:102–120`) push two separate `NavBackStackEntry`
instances. Each gets its own `BatchPreviewViewModel` (Path P2).
Each VM's `LaunchedEffect(commandText)` runs `loadPreview` against
`Idle` state — both call `BatchOperationsRepository.parseCommand`
(`:90`), which round-trips to Haiku. **Haiku is non-deterministic** —
the two responses can differ in `mutations`, `confidence`,
`ambiguous_entities`. The user sees one parse, dismisses or approves,
pops back to the second screen with **"different options"**.

This exactly matches the user's reported symptom and is precisely the
mechanism the prior audit named:

> Path P2 (fresh VM, state = `Idle`) is NOT defended by this fix. […]
> If Phase 3 device verification reveals the actual mechanism is Path P2,
> re-audit […] at that point we'd target the re-navigation trigger
> (most likely `_batchIntents` re-emit […]).
> — `BATCH_PREVIEW_REFIRE_AUDIT.md:189–193`

**Fix.** Synchronously claim the submit slot at the top of `onSubmit`
*before* any work, and release in the appropriate finally:

```kotlin
fun onSubmit(plannedDateOverride: Long? = null) {
    if (_isSubmitting.value) return       // re-entry guard
    val text = inputText.value.trim()
    if (text.isBlank()) return
    _isSubmitting.value = true            // claim slot synchronously
    // … each branch's launch{} wraps in try/finally that clears the flag
}
```

For the batch path:

```kotlin
viewModelScope.launch {
    try {
        _batchIntents.emit(batchIntent.commandText)
        inputText.value = ""
    } finally {
        _isSubmitting.value = false
    }
}
```

Same shape for the multi-create path (A5) and the paywall short-circuit
(`!proFeatureGate.hasAccess(...)`). The template path and the regular
task-create path already use try/finally on `_isSubmitting`, so the
upstream sync set is a no-op for them.

**Why a synchronous set, not just an in-coroutine set.** The race is
*between* taps — Compose dispatches the click handlers on the main
thread serially, so the synchronous `if` + assignment runs to
completion before the next click handler can run. A second tap reads
`isSubmitting == true` and returns. An in-coroutine `_isSubmitting.value
= true` (which is what the regular task-create path does today) leaves
a window between `viewModelScope.launch{ }` returning and the coroutine
body actually running, during which a second tap can sneak through.

**Risk of fix:** very low. `_isSubmitting` already exists, is already
observed by the Send button's `enabled`, and is already toggled by
three of the four code paths. The fix is to make the toggle
synchronous + add it to the two paths that lack it.

---

## A5 — `QuickAddViewModel.onSubmit` multi-create-path race (RED → PROCEED)

Same shape as A4. Lines 384–395:

```kotlin
val multiCreate = multiCreateDetector.detect(text)
if (multiCreate is MultiCreateDetector.Result.MultiCreate) {
    if (proFeatureGate.hasAccess(ProFeatureGate.AI_NLP)) {
        viewModelScope.launch {
            _multiCreateIntents.emit(multiCreate.rawText)
            inputText.value = ""
        }
    } else {
        createTasksLocallyFromSegments(multiCreate.segments, plannedDateOverride)
    }
    return
}
```

The Pro branch emits `_multiCreateIntents` without `_isSubmitting`,
so a double-tap on a comma-list paste produces two
`MultiCreateBottomSheet` navigations (`AIRoutes.kt:126–140`), each
triggering its own Haiku extraction. Same "different options" failure
mode. Free branch already manages `_isSubmitting` inside
`createTasksLocallyFromSegments` (`:551–602`) but is itself unguarded
upstream, so two rapid taps could in principle queue two coroutines
before the first one starts. Bundle the fix with A4.

---

## A6 — Regular task-create path race (YELLOW → bundle the upstream guard)

The regular task-create path (`:440–537`) sets `_isSubmitting.value =
true` *inside* `viewModelScope.launch`, leaving the same launch-window
race that A4 exploits. The symptom for double-creating a single task
would be a duplicate row, not "different options" — and the user did
not report duplicate single-task creation, suggesting the race is
narrow enough to rarely fire in practice. The fix from A4 (sync set
at the top of `onSubmit`) closes this path *for free* without further
code changes, since the inner `_isSubmitting.value = true` becomes a
no-op.

---

## A7 — `QuickAddBar` IME `Done` skips `!isSubmitting` (YELLOW → bundle)

```kotlin
// QuickAddBar.kt:281-286
keyboardActions = KeyboardActions(onDone = {
    if (inputText.isNotBlank()) {           // ← only blank check
        viewModel.onSubmit(plannedDateOverride)
        onTaskCreated()
    }
})
```

The Send `IconButton` (`:269–278`) checks `enabled = inputText.isNotBlank() && !isSubmitting`. The IME `Done` does not. Without A4's
upstream guard, a Send-tap-then-Enter combo on a hardware keyboard or
a fast soft-keyboard double-tap could race exactly the way A4
describes. With A4 in place, this becomes belt-and-suspenders, but
still worth fixing for symmetry — add `&& !isSubmitting` to the
`onDone` predicate.

---

## A8 — `DefaultCalendarPushDispatcher.enqueue` not unique (YELLOW → DEFER)

`enqueue()` instead of `enqueueUniqueWork` means a per-task-id worker
deduplication does not exist. If `batchReschedule` ever did fire twice
upstream (which A4 closes), each affected task would queue two calendar
push workers. The push payload itself is idempotent against the
calendar API (Google Calendar `update` with the same `iCalUID` is a
no-op on identical content), so the observable effect is wasted
WorkManager runs and one wasted network round-trip per duplicate.

DEFER until there's a measurable user complaint or telemetry showing
duplicate calendar event spam — A4's upstream fix removes the only
known trigger. If we later need to harden, swap to
`enqueueUniqueWork(uniqueName = "calendar-push-task-$taskId", policy =
ExistingWorkPolicy.REPLACE, request)`.

---

## A9 — `TaskListViewModelBulk` re-entry guards (GREEN → no work)

Multi-select bulk-edit handlers (complete, delete, set-priority,
reschedule, apply-tags) launch a single `viewModelScope.launch` and
call into the repository's `batch*` methods. None have explicit
re-entry guards. However:

- The underlying ops are idempotent (set-priority, set-due-date,
  add/remove tag).
- Re-running `batchAddTag` with the same tag is a Room insert that
  hits the unique constraint and is silently skipped.
- The "different options" symptom cannot occur here — there is no
  re-parse.

A double-tap would produce wasted work but no user-visible double
effect. Not in scope for the user's report. If we later see telemetry
showing repeated mutations on the same selection, revisit.

---

## Ranked improvement table

Sorted by **wall-clock-savings ÷ implementation-cost** (savings = how
much user-facing pain this removes; cost = LOC + test surface).

| Rank | Item | Risk | Action | Cost | Savings |
|------|------|------|--------|------|---------|
| 1 | A4: `onSubmit` batch-path race | RED | PROCEED | ~30 LOC + 1 test | Closes the user's reported symptom; removes a wasted Haiku call per duplicate (real $) |
| 2 | A5: `onSubmit` multi-create-path race | RED | PROCEED (bundle with A4) | shared with A4 | Closes the second instance of the same race; same fix shape |
| 3 | A7: IME `Done` skips `!isSubmitting` | YELLOW | PROCEED (bundle with A4) | 1-line edit | Belt-and-suspenders; symmetric with the Send button |
| 4 | A6: regular task-create launch-window race | YELLOW | covered for free by A4's sync set | 0 | Removes a latent dup-task bug |
| 5 | A8: `CalendarPushDispatcher` `enqueue` not unique | YELLOW | DEFER | ~15 LOC | No current trigger after A4; swap to unique-work if future telemetry shows it |
| 6 | A9: `TaskListViewModelBulk` re-entry guards | GREEN | DEFER | ~40 LOC across 5 handlers | No observable user-facing effect; idempotent at DAO |

---

## Anti-patterns surfaced

Worth noting for memory but not necessarily fixing now:

- **Setting submit-locks inside `viewModelScope.launch{}` for click
  handlers.** This pattern shows up in three of the four `onSubmit`
  branches and is a recurring source of Compose double-tap races —
  the launch returns synchronously, leaving a window for a second
  click handler to run before the coroutine sets the flag. Always
  set the lock *before* the launch, in the synchronous prelude.
- **`MutableSharedFlow<T>(extraBufferCapacity = 1)` for navigation
  triggers.** This buffers exactly one extra value beyond replay, so
  a two-emit storm where the collector is slow will deliver both.
  Combined with the launch-window race above, it makes
  one-emit-equals-one-navigate ambiguous. Either gate the upstream
  emitter (what A4 does) or use `MutableSharedFlow(replay = 0,
  extraBufferCapacity = 0, onBufferOverflow = DROP_OLDEST)` — a
  dedicated single-shot navigation channel. The former is cheaper
  here.
- **Per-`NavBackStackEntry` `hiltViewModel()` + `LaunchedEffect(arg)`
  for one-shot remote work.** This is correct Compose but means *any*
  duplicate navigation = duplicate remote call. The defense has to
  live at the navigation trigger, not the screen. The prior audit
  (BATCH_PREVIEW_REFIRE_AUDIT) defended this within one VM via the
  `loadPreview` re-entry guard; but that guard is per-VM and cannot
  see a sibling VM. Upstream-guard is the only correct fix.
