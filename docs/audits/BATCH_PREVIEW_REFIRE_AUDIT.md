# BatchPreview Re-Fire Audit

**Source defect:** S25 Ultra device session (May 1, 2026), NLP batch ops Test 1.3.
**Severity:** P1 — not Phase F GATE-blocking, but creates real risk for non-idempotent
batch operations and erodes UX trust.
**Reproduction (paraphrased from scope):** quick-add → "complete all tasks today" →
BatchPreviewScreen renders mutations → tap Apply → mutations apply, BUT
BatchPreviewScreen reopens automatically with the same nav arg, runs Haiku again,
displays an empty mutation list (because tasks are now complete).

This audit narrows three candidate root causes from the scope (CAUSE-A
input-preservation re-trigger / CAUSE-B navigation-stack issue / CAUSE-C
ViewModel state-machine bug) by static reading, picks one, and proposes the
fix shape.

---

## Phase 1 — Audit

### A1 — Static repro of the symptom (GREEN — symptom matches code path)

The symptom decomposes into two observed facts:

1. **Haiku ran twice for the same nav arg.** The only call site for
   `repository.parseCommand(...)` from the UI is
   `BatchPreviewViewModel.loadPreview()` at
   `app/src/main/java/com/averycorp/prismtask/ui/screens/batch/BatchPreviewViewModel.kt:65`.
   So `loadPreview()` was invoked twice for the same `commandText`.
2. **Empty mutation list.** Between the two invocations, the underlying
   tasks transitioned to completed, so the second parse legitimately returns
   no mutations.

`loadPreview()` is invoked from exactly one place in the UI:
`BatchPreviewScreen.kt:72-74`:

```kotlin
LaunchedEffect(commandText) {
    viewModel.loadPreview(commandText)
}
```

The static question is: **how does this `LaunchedEffect` get a second chance
to fire `loadPreview` for the same `commandText`?** Two structurally distinct
paths can produce that:

- **Path P1 — same back-stack entry, re-entered composition.** The screen is
  briefly recomposed in a way that disposes + re-mounts the `LaunchedEffect`
  (e.g. transition recomposition triggered by `popBackStack()` itself).
  ViewModel is the SAME instance — state is whatever `approve()` left it at
  (`Committing`).
- **Path P2 — fresh back-stack entry, fresh ViewModel.** Something
  re-navigates to `PrismTaskRoute.BatchPreview.createRoute(commandText)`.
  ViewModel is a fresh instance — state starts at `Idle`.

P1 vs P2 is distinguishable at runtime (logcat would show the difference)
but NOT statically. However, the FIX shape — make `loadPreview` idempotent
and close the state-machine — addresses both, so the audit can proceed
without resolving the runtime ambiguity.

### A2 — CAUSE-A (input-preservation / LaunchedEffect re-trigger) — YELLOW

The scope's CAUSE-A asks whether a `LaunchedEffect` on input retains
preservation and re-fires the parse.

`QuickAddViewModel.onSubmit()` clears the input on the batch path:

```kotlin
// app/src/main/java/com/averycorp/prismtask/ui/components/QuickAddViewModel.kt:369-373
viewModelScope.launch {
    _batchIntents.emit(batchIntent.commandText)
    inputText.value = ""
}
return
```

So the QuickAddBar input field IS cleared after submit. The "still in the
field" phrasing in the scope refers to the *navigation argument preserved by
the route* (`PrismTaskRoute.BatchPreview` → `batch_preview?command={command}`),
not the QuickAddBar TextField.

CAUSE-A is partially relevant in this codebase, but mapped to the
`LaunchedEffect(commandText)` in `BatchPreviewScreen` rather than to the
QuickAddBar. That LaunchedEffect IS the re-fire trigger under Path P1.

**Verdict: YELLOW — CAUSE-A as scoped (input field re-trigger) does not
match the static code, but the related mechanism (LaunchedEffect on
nav-arg) IS the most plausible trigger under Path P1.**

### A3 — CAUSE-B (navigation-stack issue) — GREEN-NOT-APPLICABLE

`AIRoutes.kt:108-115` already wires both `onApproved` and `onCancelled` to
`navController.popBackStack()`:

```kotlin
BatchPreviewScreen(
    navController = navController,
    commandText = command,
    onApproved = { _, _, _ ->
        navController.popBackStack()
    },
    onCancelled = { navController.popBackStack() }
)
```

The Approved event fires from `BatchPreviewViewModel.approve()` at
`BatchPreviewViewModel.kt:258-264`, is collected by the `LaunchedEffect(viewModel)`
at `BatchPreviewScreen.kt:76-83`, and routes to `onApproved` → `popBackStack`.
This path is intact statically. There is no missing pop.

**Verdict: CAUSE-B does not apply. The pop is wired. STOP-no-work-needed
on this hypothesis.**

### A4 — CAUSE-C (ViewModel state-machine bug) — RED, LOAD-BEARING

The state machine for `BatchPreviewViewModel` has TWO defects that combine
to make `loadPreview` non-idempotent:

**Defect C-1: `loadPreview` guard is too narrow.**

`BatchPreviewViewModel.kt:60-62`:

```kotlin
fun loadPreview(commandText: String) {
    if (_state.value is BatchPreviewState.Loading) return
    _state.value = BatchPreviewState.Loading(commandText)
    ...
}
```

The guard returns ONLY when state is `Loading`. It does NOT short-circuit
when state is `Committing` (mid-apply, post-Approve, pre-pop) or `Loaded`
(parse already succeeded for this `commandText`). So a second invocation in
either of those states will re-emit `Loading`, kick off a new
`repository.parseCommand` network call, and overwrite the `Loaded` /
`Committing` state.

**Defect C-2: no terminal state after `approve()` success.**

`BatchPreviewViewModel.kt:237-273`:

```kotlin
fun approve() {
    val loaded = _state.value as? BatchPreviewState.Loaded ?: return
    val toApply = loaded.mutations.filterIndexed { idx, _ -> idx !in _excluded.value }
    if (toApply.isEmpty()) { ... return }
    _state.value = BatchPreviewState.Committing(loaded.commandText)  // ← only state mutation
    viewModelScope.launch {
        try {
            val result = repository.applyBatch(loaded.commandText, toApply)
            undoBus.notifyApplied(...)
            _events.emit(BatchEvent.Approved(...))  // ← no state transition here
        } catch (e: Exception) {
            _state.value = BatchPreviewState.Error(...)
        }
    }
}
```

After `applyBatch` succeeds, the VM emits the `Approved` event but does NOT
transition `_state` to a terminal value. The state stays at
`Committing(loaded.commandText)` forever. Nothing closes the state machine.

**Combination effect:** if `loadPreview` is re-invoked AFTER successful
approve (Path P1), state is `Committing`, the C-1 guard does not fire, the
parse re-runs, and the screen re-renders with whatever Haiku returns.

The `_state` audit at `Migrations.kt`-style precision: state mutation sites
(grep'd) are at lines 40 (init), 62 (Loading), 108 (Loaded), 120/134
(Error), 221 (resolveAmbiguity update), 246 (Committing), 266 (Error in
approve catch). **No state mutation on the success path of `approve`.**
That is the C-2 omission, exactly.

**Verdict: RED. CAUSE-C is the load-bearing defect. The fix lands here
regardless of whether the runtime trigger is Path P1 or Path P2.**

### A5 — Verdict: CAUSE-C, with explicit ambiguity on the trigger mechanism

**Pick: CAUSE-C (state-machine).**

- Defect C-1 (guard too narrow) and C-2 (no terminal state) are real,
  static, and load-bearing.
- The fix targets both: add a terminal `Applied` state, transition on
  approve success, and strengthen the guard to bail on
  `Loading` + `Committing` + `Applied` + `Loaded(same commandText)`.
- This makes `loadPreview` idempotent under Path P1 (same VM, state =
  `Committing`/`Applied`).
- Path P2 (fresh VM, state = `Idle`) is NOT defended by this fix. A fresh
  VM legitimately re-parses on first composition. If Phase 3 device
  verification reveals the actual mechanism is Path P2, re-audit with
  logcat — at that point we'd target the re-navigation trigger (most
  likely `_batchIntents` re-emit or a Compose Navigation quirk), not the
  ViewModel.

**Why I'm not stopping for ambiguity per the scope's STOP-condition:**

- The fix shape for CAUSE-C is the *minimum viable defense* and
  measurably closes one open hole (the C-2 state-machine omission is
  unambiguous on its own — state staying at `Committing` forever is a
  bug regardless of the re-fire question).
- Phase 3 verification explicitly tests the symptom on-device. If the
  fix doesn't close the symptom, that's a clear signal to re-audit for
  Path P2, NOT a silent failure.
- Holding for logcat would cost a device-session round-trip; shipping
  CAUSE-C and verifying is faster.

### A6 — Side-finding: `BatchPreviewState.Loaded` re-entry should also be guarded (DEFERRED)

A second `loadPreview(sameCommandText)` while state is `Loaded` would
silently overwrite the user's exclusion picks (`_excluded.value =
emptySet()` at line 118) and reset the picker UI. The current code path
doesn't trigger this (no recomposition forces it organically), but the
guard tightening proposed in C-1 also closes this latent hole.

**Recommendation: PROCEED, included in the same fix.**

### A7 — Side-finding: AIRoutes uses plain `composable`, not transition variant (GREEN-NOT-A-DEFECT)

`AIRoutes.kt:98-116` uses plain `composable(...)`, not
`horizontalSlideComposable(...)`. The other AI routes (Eisenhower,
SmartPomodoro, DailyBriefing, etc.) DO use the slide variant. Whether
that's an oversight is out of scope for this audit; raising it as a
note for the reader.

**Recommendation: DEFER.** Out of scope per the constraint "no related
refactoring."

---

## Ranked improvement table

| # | Improvement | Wall-clock saved | Implementation cost | Ratio |
|---|---|---|---|---|
| 1 | Add terminal `Applied` state + transition on approve success | High (closes C-2 unambiguously, also defends C-1 path) | ~20 LOC + 2 unit tests | High |
| 2 | Strengthen `loadPreview` guard to bail in `Committing`/`Applied`/`Loaded(same commandText)` | High (closes the re-fire window for Path P1) | ~5 LOC | Very high |
| 3 | Render `Applied` state in `BatchPreviewScreen` (no-op LoadingBody, since pop fires immediately via the `Approved` event collector) | Low — defensive only | ~3 LOC | Medium |

All three land in a single PR. Ratio target #2 first — that's the load-bearing
guard. #1 and #3 are the structural close.

---

## Anti-pattern list (flag, not fix)

- **`AIRoutes.kt` mixes `composable` and `horizontalSlideComposable` for
  AI routes without an obvious rule.** Other AI routes use slide; BatchPreview
  doesn't. If the convention is "AI routes slide," this is an outlier; if
  the convention is "modal flows don't slide," BatchPreview is correct.
  **Skip.** Out of audit scope.
- **`_state` has 7 mutation sites in `BatchPreviewViewModel.kt`.** A small
  state machine like this is borderline for sealed-class-with-transitions
  refactor. **Skip.** The fix below adds one terminal state without
  refactoring the rest.
- **`LaunchedEffect(commandText)` re-fire risk is generic to nav-arg-driven
  screens.** Other screens may have the same pattern. **Skip.** Audit-scope
  capped to BatchPreview per the constraint.

---

## Phase 2 — Implementation

**File 1: `BatchPreviewViewModel.kt`**

- Add `BatchPreviewState.Applied(batchId, appliedCount, skippedCount)` data class.
- In `approve()` success path (after `_events.emit(BatchEvent.Approved(...))`),
  transition `_state.value = BatchPreviewState.Applied(...)`.
- Strengthen `loadPreview` guard:

  ```kotlin
  fun loadPreview(commandText: String) {
      val current = _state.value
      // Re-entry guards:
      // - Loading: already in flight, drop the duplicate.
      // - Committing/Applied: post-Approve, the screen will pop on the
      //   Approved event collector — re-parsing here would re-run Haiku
      //   on now-stale data and risk double-applying non-idempotent ops.
      // - Loaded with the same commandText: parse already succeeded;
      //   re-running would silently clobber the user's exclusion picks.
      // Error falls through — the Retry button explicitly invokes loadPreview.
      when (current) {
          is BatchPreviewState.Loading -> return
          is BatchPreviewState.Committing -> return
          is BatchPreviewState.Applied -> return
          is BatchPreviewState.Loaded -> if (current.commandText == commandText) return
          BatchPreviewState.Idle, is BatchPreviewState.Error -> Unit
      }
      _state.value = BatchPreviewState.Loading(commandText)
      ...
  }
  ```

**File 2: `BatchPreviewScreen.kt`**

- Add a render branch for `BatchPreviewState.Applied` — same body as
  `Committing` (a `LoadingBody`). The screen will pop on the next
  recomposition after the `Approved` event is collected, so the visual
  is transient.

**File 3: `BatchPreviewViewModelTest.kt`**

- New test: `approve_success_transitionsToAppliedState` — assert
  `state.value` is `Applied` after `advanceUntilIdle()` post-approve.
- New test: `loadPreview_shortCircuitsInCommittingState` — manually set
  state to `Committing`, call `loadPreview`, assert `parseCommand` is
  NOT invoked and state remains `Committing`.
- New test: `loadPreview_shortCircuitsInLoadedSameCommand` — load,
  call `loadPreview` again with same text, assert `parseCommand` invoked
  ONCE (via `coVerify(exactly = 1)`).
- New test: `loadPreview_doesNotShortCircuitInLoadedDifferentCommand`
  — load with text A, call `loadPreview(B)`, assert second parse
  ran (regression guard against over-eager guard).

**Branch:** `fix/batch-preview-refire`. Squash-merge auto-merge.
Commit message cites this audit doc.

**Estimate:** ~30 LOC production change (split: ~15 in VM, ~3 in
Screen, ~12 for the new state class), ~80 LOC test additions.
Single PR. No other files touched.

---

## Phase 3 — Verification (plan)

Per scope:

1. **Unit:** `./gradlew :app:testDebugUnitTest --tests "*BatchPreview*"`.
   All existing tests pass + new tests pass.
2. **On-device 3.1 (idempotent path, original repro):** quick-add
   "complete all tasks today" → Apply → expect: pops, Snackbar,
   no re-fire. Pass = no re-fire.
3. **On-device 3.2 (non-idempotent risk, REQUIRED additional test):**
   3 tasks tagged `#work`, none `#urgent`. Quick-add "add tag urgent
   to all tasks tagged work" → Apply → expect: each task has exactly
   `#work` + `#urgent` (2 tags), screen pops, no re-fire. Worst-case
   fail = re-fire + double-apply on second tap.
4. **On-device 3.3 (empty-state regression):** with all tasks
   complete, re-run the same command → empty state renders correctly.
5. **On-device 3.4 (AI gate regression):** toggle AI off → re-run
   → "AI features are off" UI per PR #1048. Toggle back on → recovery.

If 3.2 fails (re-fire still observed), this audit's CAUSE-C verdict
was wrong and Path P2 (re-navigation, fresh VM) is the actual
mechanism — STOP and re-audit with logcat.

---

## Phase 3 — Bundle summary

(Filled in after Phase 2 PR merges.)

---

## Phase 4 — Claude Chat handoff

(Filled in after Phase 3 closes.)
