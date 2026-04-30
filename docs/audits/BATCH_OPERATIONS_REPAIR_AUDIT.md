# Batch Operations Repair Audit

**Scope.** User report: "batch operations are not working." Sweep the
end-to-end batch path — NLP-driven AI batch ops (QuickAdd → BatchPreview →
apply → undo) and multi-select bulk edit — and identify what is actually
broken on `main` (currently `feature/customizable-settings-ui-advanced`,
forked from `main` at `2efbb605`).

Audit-first per `CLAUDE.md` "Audit doc length" — Phase 1 is read-only.
Phase 2 fan-out auto-fires.

## Phase 1 — Findings

### Item 1 · QuickAddBar batch routing missing on TaskListScreen (RED)

**Finding.** `app/src/main/java/com/averycorp/prismtask/ui/screens/tasklist/TaskListScreen.kt:721`
mounts `QuickAddBar(onMultiCreate = …)` and omits `onBatchCommand`. The
default in `QuickAddBar.kt:70` is `{}`. The collector at
`QuickAddBar.kt:106-108` still drains the `batchIntents` SharedFlow
(invoking the no-op lambda), and `QuickAddViewModel.onSubmit` line
`362-366` clears `inputText` *before* emitting. Net effect for a Pro user
on the Tasks tab:

1. Type "Reschedule all overdue tasks to tomorrow"
2. `BatchIntentDetector` matches (QUANTIFIER + TIME_RANGE +
   BULK_VERB_AND_PLURAL — see `BatchIntentDetectorTest.kt:88-95`)
3. `proFeatureGate.hasAccess(AI_BATCH_OPS)` returns true →
   `_batchIntents.emit(commandText)` + `inputText.value = ""`
4. Host's `onBatchCommand({})` runs — no navigation
5. User sees their input vanish. Nothing else happens.

This is the load-bearing user-visible breakage behind the report. Today
(via `FloatingQuickAddBar` → `TodayQuickAddBar` → `QuickAddBar` at
`TodayScreen.kt:227-232`) is the *only* surface that wires the parameter
through, so batch ops only "work" if the user happens to be on Today.

**Risk.** RED — feature is shipped behind Pro paywall but only functional
on one of three QuickAddBar hosts. Recovered input is lost (it gets
cleared by `onSubmit` before any host can navigate), so retrying on Today
requires the user to retype.

**Recommendation.** PROCEED. Wire `onBatchCommand` in TaskListScreen with
the same `navController.navigate(PrismTaskRoute.BatchPreview.createRoute(
commandText))` shape Today uses. Also mount a `BatchUndoListenerViewModel`
on TaskList so the post-approve "Undo" Snackbar offer fires when the user
pops back from BatchPreview to Tasks (without this, batch on Tasks works
but the 30-second Undo affordance is silently dropped — a partial fix).

### Item 2 · QuickAddBar batch routing missing on PlanForTodaySheet (RED)

**Finding.** `app/src/main/java/com/averycorp/prismtask/ui/screens/today/components/PlanForTodaySheet.kt:228`
also omits `onBatchCommand`. Identical symptom to Item 1 inside the
"Plan for today" bottom sheet. Note: `onMultiCreate` is wired (line 233),
which makes the gap stand out — it's a forgot-to-thread-it bug, not a
deliberate omission.

The hosting Today screen *does* mount a `BatchUndoListenerViewModel`
(`TodayScreen.kt:144`), so once we route the navigation, the undo
Snackbar will already fire on pop-back. Only the navigation hop is
missing.

**Risk.** RED — same user-visible symptom as Item 1. Smaller blast radius
because the sheet is a secondary entry (Today's "Plan for today" CTA),
but cheap to fix in the same PR.

**Recommendation.** PROCEED. Add an `onBatchCommand` parameter to
`PlanForTodaySheet`, thread it from `TodayScreen` (which already has the
navController), and pass to the inner `QuickAddBar`. Bundle with Item 1.

### Item 3 · Repository / undo / history / sweep paths (GREEN)

**Premise verification.** Verified by reading and cross-referencing:

- `BatchOperationsRepository.kt` — `applyBatch` runs in a single
  `database.withTransaction`; per-mutation snapshots feed
  `batch_undo_log`; `undoBatch` reverses in reverse insertion order.
  Skipped mutations are surfaced via `SkippedMutation` so the host can
  show partial-application feedback (`BatchPreviewViewModel.kt:97`,
  `TodayScreen.kt:147-151`).
- `BatchUndoLogDao.kt` — `observeBatchIds()` is Room-Flow-backed, so
  `markBatchUndone` UPDATEs trigger a re-emit and the History screen
  reflects undone state on the next observation tick (no stale-card bug).
- `BatchUndoSweepWorker.kt` — daily periodic worker scheduled in
  `PrismTaskApplication.onCreate` line 220, sweeps `expires_at < now AND
  undone_at IS NULL` plus already-undone-rows past `tailDays` (configurable
  via `AdvancedTuningPreferences`).
- `BatchHistoryViewModel.kt` — `flatMapLatest` over `observeBatchIds` is
  the correct shape; `dao.getEntriesForBatchOnce(id)` re-fires on every
  table change (Room observer semantics).
- `BatchPreviewViewModel.kt` — `loadPreview` → `parseCommand` → `Loaded`
  state; `approve` → `applyBatch` → `undoBus.notifyApplied` →
  `BatchEvent.Approved`. Empty mutation list is gated at the bottom bar
  (`approveEnabled = state.mutations.isNotEmpty()`).
- Backend `app/routers/ai.py:737-778` — stateless, validates, rate-limits
  via `batch_parse_rate_limiter` + `daily_ai_rate_limiter`, returns
  `BatchParseResponse`.
- Tests: `BatchIntentDetectorTest`, `BatchOperationsRepositoryTagChangeTest`,
  `BatchOperationsRepositoryMedicationTest`, `BatchUndoLogDaoTest`,
  backend `test_ai_batch_parse.py`. All present, last touched in landing
  PRs (#697, #700, #761, #772, #781).

**Risk.** GREEN — no broken seams in the apply / undo / history / sweep
chain. Bug surface is purely the QuickAddBar wiring documented in
Items 1-2.

**Recommendation.** STOP-no-work-needed for the data path itself.

### Item 4 · Multi-select bulk edit on TaskList (GREEN)

**Premise verification.** This is the *non-AI* batch surface — the
multi-select bottom bar with Complete / Delete / Set Priority / Reschedule
/ Move-to-Project / Tag actions. Backed by `TaskListViewModelBulk.kt`
extension functions on `TaskListViewModel`.

- DAO atoms (`TaskDao.kt:287-361`) wrap each operation in
  `@Transaction` — `batchUpdatePriority`, `batchReschedule`,
  `batchMoveToProject`, `batchAddTag`, `batchRemoveTag`.
- Repo wrappers (`TaskRepository.kt:488-576`) call `syncTracker.trackUpdate`
  per task, refresh widgets, and reschedule reminders for `batchReschedule`.
- ViewModel handlers capture pre-state, dispatch the batch, show the
  Snackbar with an UNDO action, and group-by-old-value to dispatch
  ungrouped reverse calls on undo.
- Smoke tests (`MultiSelectBulkEditSmokeTest.kt`) cover all six DAO
  ops directly.

**Risk.** GREEN — independent of the AI batch path; no evidence of
breakage. The user-reported "batch operations not working" almost
certainly refers to AI batch ops (Items 1-2), not multi-select.

**Recommendation.** STOP-no-work-needed.

### Item 5 · Cross-host Undo Snackbar listener coverage (YELLOW)

**Finding.** `BatchUndoListenerViewModel` is mounted only on Today
(`TodayScreen.kt:144`). After we wire `onBatchCommand` on TaskList
(Item 1), the navigation will pop back to TaskList — and the
`BatchUndoEventBus.notifyApplied` event needs a TaskList-side collector
to surface the "Undo" Snackbar. Without it, the batch lands silently.

This is mitigated by Item 1's recommendation (mount the listener on
TaskList too). Calling it out here so it's not lost during fan-out.

**Risk.** YELLOW — partial-fix hazard, not an independent bug. Becomes
a real issue only after Item 1 ships without its listener half.

**Recommendation.** PROCEED — bundle into Item 1's PR rather than
shipping a half-fix.

### Item 6 · BatchUndoEventBus replay semantics during nav transition (DEFERRED)

**Premise.** `BatchUndoEventBus` is a `SharedFlow` with `replay = 0` and
`extraBufferCapacity = 4`. After approve, `BatchPreviewScreen` pops
*itself* (line 112-115 of `AIRoutes.kt`); the host (Today) recomposes,
its `LaunchedEffect(batchUndoListener)` re-launches, and the collector
re-subscribes. Between `notifyApplied` (called inside the still-alive
preview VM scope, which is `viewModelScope` from `BatchPreviewViewModel`)
and the host's re-subscribe, there's a millisecond-scale gap where a
fresh subscriber wouldn't see the value (replay=0).

In practice: `notifyApplied` is `tryEmit` and runs *before* the
`BatchEvent.Approved` emit triggers `popBackStack`. In the Today host's
case, the host composable was *paused* (still in backstack) but its
`LaunchedEffect` was not cancelled because Compose Navigation keeps the
backstack composition alive when the destination is re-entered via
`popBackStack`. So in the happy path, the collector is still subscribed
and the event is delivered.

The risk is theoretical: if Compose Navigation ever changes its
backstack-disposal semantics, or a different host disposes its
LaunchedEffect during BatchPreview, the Snackbar offer would be lost.
The bus already buffers 4 entries — switching to `replay = 1` would
make this fully race-proof.

**Risk.** DEFERRED — works on current Compose Navigation semantics; no
user-visible bug today. Worth flagging in the test net but not worth a
PR by itself. If we touch the bus again, opportunistic upgrade.

**Recommendation.** DEFER. Note in the anti-patterns list below.

### Item 7 · BatchIntentDetector "two-signal" threshold (DEFERRED)

**Finding.** The detector's design (`BatchIntentDetector.kt:25,112`) is
intentionally conservative — must show 2+ distinct signal categories.
This rejects valid-feeling commands like "Cancel Thursday afternoon"
(only TIME_RANGE matches, "afternoon" requires the multiword phrase
"this afternoon" — see `BatchIntentDetectorTest.kt:56-71`). The test
file *documents* this trade-off and the user is expected to rephrase.

Not a bug — it's a deliberate false-negative bias to keep regular task
creation off the heavier batch path. Tuning the threshold is a separate
product decision, not a fix.

**Risk.** DEFERRED — design choice with an explicit test pinning the
behavior.

**Recommendation.** DEFER unless the user reports specific commands they
expect to be detected; that's a separate ticket.

## Improvement table — ranked by wall-clock-savings ÷ implementation-cost

| Rank | Item | Why it ranks here |
|------|------|-------------------|
| 1 | Item 1 + Item 2 + Item 5 (one bundled PR) | Fixes the user-reported breakage on two host surfaces, plus listener parity, in one coherent diff (~3 small wirings). High savings (feature moves from 33% functional to 100%); minutes of cost. |
| 2 | Item 6 (replay = 1 on BatchUndoEventBus) | Pure defensiveness; no user-visible bug today; ship if we touch the bus, otherwise defer. |
| 3 | Item 7 (detector threshold tuning) | Product call, not a bug. |

## Anti-pattern callouts (flag, not necessarily fix)

- **No-op-default-lambdas-on-load-bearing-callbacks.** `onBatchCommand`
  defaulted to `{}` is what let this regression sit silently across the
  TaskList and PlanForToday surfaces. The detector emits, the input
  clears, the lambda runs, nothing visible happens — three out of four
  steps "succeed." Consider a `requireNotNull` or removing the default
  for parameters whose absence corrupts UX.
- **SharedFlow `replay=0` for "X just happened, please react" events.**
  `BatchUndoEventBus` is a "fire-and-be-heard" channel; a single-replay
  buffer would give every host a 5-second-window grace period to
  subscribe. Cheap upgrade.
- **Documented detector false-negatives without a UI hint.** When
  `BatchIntentDetector` says "NotABatch" but the user clearly meant a
  batch (e.g. "Clear Thursday afternoon"), there's no in-app affordance
  pointing at "did you mean to run a batch command?" That's the kind of
  follow-up tuning that makes detection feel reliable instead of
  capricious.

---

## Phase 2 — Implementation

(auto-fires after Phase 1 commit; no checkpoint)

Single bundled PR `fix/batch-operations-repair`:

1. Wire `onBatchCommand` on `TaskListScreen` QuickAddBar; mount
   `BatchUndoListenerViewModel` and route its events into the existing
   `viewModel.snackbarHostState`.
2. Add `onBatchCommand` parameter to `PlanForTodaySheet`; thread from
   `TodayScreen` (existing navController).
3. (Optional, low cost) Bump `BatchUndoEventBus` replay to 1 to harden
   Item 6.

Required CI green; no `[skip ci]`; squash-merge auto-merge.
