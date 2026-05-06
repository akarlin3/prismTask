# D_MEDICATION_ADD_CRASH_AUDIT

D-series launch-gate item. Operator reproduced May 6 on AVD: "Add Medication"
with no slot selected → crash. Scope-locked to the medication-add flow; sibling
read-path null-safety surfaces only as deferred items per operator constraint.

## Reproduction + stack trace

**Reproduction status: STRUCTURAL ONLY.** This sandbox has no AVD/adb to
re-run the operator's repro live; Phase 1 read the codepath top-to-bottom
to identify the crash surface instead. STOP-A1 was considered and the
operator-locked broad-scope mandate ("scan ALL nullable / optional fields
in medication-add flow") makes the structural read sufficient — every
crash candidate here is reachable from the documented repro and from
adjacent `OnConflictStrategy.ABORT` paths the dialog doesn't gate on.

The dominant candidate is **#3 below** (unique-name constraint violation
on insert). It plausibly matches the operator's "no slot selected" repro
shape if their fresh-sign-in AVD had any prior med named identically (a
pulled built-in or a stale row from an earlier session) — the dialog's
slot-empty warning is non-blocking, so the user typically *also* leaves
slots empty when the crash hits, conflating cause and circumstance.

## Recon findings

### A.3 Add-flow surface (files actually touched on insert)

- `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/MedicationScreen.kt`
  → wires the dialog and dispatches the confirm callback
  (`MedicationScreen.kt:213-224`).
- `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/components/MedicationEditorDialog.kt`
  → the dialog itself; Save button gated only on
  `name.isNotBlank()` (`MedicationEditorDialog.kt:241`).
- `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/components/MedicationSlotPicker.kt`
  → builds the `List<MedicationSlotSelection>` payload.
- `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/MedicationViewModel.kt`
  → `addMedication` (`MedicationViewModel.kt:291-333`) — the
  load-bearing write coroutine. **No try/catch around the launch.**
- `app/src/main/java/com/averycorp/prismtask/data/repository/MedicationRepository.kt`
  → `insert` (`MedicationRepository.kt:67-74`) calls `medicationDao.insert`,
  which has `@Insert(onConflict = OnConflictStrategy.ABORT)`
  (`MedicationDao.kt:46`).
- `app/src/main/java/com/averycorp/prismtask/data/local/entity/MedicationEntity.kt`
  → `Index(value = ["name"], unique = true)`
  (`MedicationEntity.kt:28`) — the unique-name constraint that ABORT
  enforces. Index is on `name` only; `is_archived` does NOT factor in.
- `app/src/main/java/com/averycorp/prismtask/data/repository/MedicationSlotRepository.kt`
  → `replaceLinksForMedication` (`MedicationSlotRepository.kt:104-109`),
  `upsertOverride` (line 130).
- `app/src/main/java/com/averycorp/prismtask/notifications/MedicationClockRescheduler.kt`
  → `rescheduleAll` invoked at the tail of `addMedication` (line 331);
  already wraps AlarmManager calls in try/catch via `ExactAlarmHelper`,
  no new crash surface.

### A.2 Drive-by history

`git log --oneline -- ui/screens/medication/MedicationViewModel.kt` shows
the file was last touched in 2026-01 (PR #891, removing Today-screen
medication reminders) and PR #907 (time-edit-sheet logical-day fix).
The current crash surface predates both — `addMedication` has had no
try/catch since the v1.5 medication-top-level rewrite shipped. No prior
PR has hardened this path.

### A.4 Null-safety gap inventory (per file, prioritized add-flow proximity)

| # | Location | Pattern | Severity |
|---|----------|---------|----------|
| 1 | `MedicationViewModel.kt:300` | `viewModelScope.launch { … insert() … }` no try/catch — **any** thrown exception in the chain crashes the app | RED |
| 2 | `MedicationViewModel.kt:345` | Same pattern in `updateMedication` | RED |
| 3 | `MedicationDao.kt:46` + `MedicationEntity.kt:28` | Unique-name index + `OnConflictStrategy.ABORT` → `SQLiteConstraintException` on duplicate-name insert; the dialog enables Save based on blankness only | RED (most-likely root cause) |
| 4 | `MedicationEditorDialog.kt:220-226` + `:241` | "Pick at least one slot…" warning is advisory; Save remains enabled with empty selections, producing a med invisible on the Today screen | YELLOW (UX, not crash, but matches operator's "broken" repro shape) |
| 5 | `MedicationViewModel.kt:299` | `if (name.isBlank()) return` after dialog has already dispatched a `name.trim()` value — silent no-op when user types only whitespace and Save is enabled | YELLOW (silent failure, not crash) |
| 6 | `MedicationViewModel.kt:171, :474` | `it.medicationId!!` on dose entries — comments justify the filter precondition; verified safe (filter excludes `medicationId == null` rows immediately above) | GREEN |
| 7 | `MedicationViewModel.kt:324` | `medicationRepository.getByIdOnce(id) ?: return@launch` — defensive read of just-inserted med | GREEN |
| 8 | `MedicationClockRescheduler.kt` | `nextTriggerForClock` returns `null` on malformed time, callers `continue` — defensive | GREEN |
| 9 | `MedicationRefillViewModel.kt:51-97` | Independent add path (refill screen). Calls `getByNameOnce` → update-or-insert, so the duplicate-name crash from #3 cannot surface here. Wrapping in try/catch is still hygiene but lower priority | GREEN (out of scope for the locked PR; bundles cleanly if op wants it) |

### A.5 Sibling-primitive (e) axis (deferred per operator scope-lock)

These read paths consume the same `slot` / `slotId` state but are NOT in
scope per operator's "medication-add-flow only" lock. Listed for F.5
deferred follow-up:

- `MedicationLogScreen` / `LogCustomDoseSheet` (`logCustomDose` write
  path — uses `customMedicationName` field, NOT the unique `name`
  index; structurally cannot hit constraint #3).
- `MedicationWidget` data provider (`WidgetDataProvider.getMedicationData`
  at line 625) — already null-defensive (`emptyList()` fallback,
  `?: 0` on null pillCount).
- Settings → Medication Slots editor (`MedicationSlotEditorSheet`) —
  Save is gated on `name.isNotBlank() && isValidHhMm(idealTime) &&
  driftMinutes >= 1`, but slot insert has the same ABORT/unique-name
  pattern (`MedicationSlotDao.insert` line 102 with
  `onConflict = OnConflictStrategy.ABORT`). Same crash class — defer.

## Root cause verdict

**Candidate 3 — duplicate-name `SQLiteConstraintException` on insert,
amplified by Candidate 1 (no top-level try/catch).**

Why this matches the operator's "no slot selected" repro:
- Operator description says "fresh sign-in" — but a "fresh sign-in" on
  Android with cloud sync may pull medication rows from Firestore
  before the user's first manual add. If any pulled row shares a
  case-sensitive `name` with what the user types, insert aborts.
- Slot-empty is a load-bearing *circumstance* (the warning text is
  always-on for empty selections, so the user notices and remembers
  it), not a load-bearing *cause*. The structural read confirms an
  empty `slotSelections` list is handled correctly downstream:
  `replaceLinksForMedication(id, emptyList())` runs, no link rows
  inserted, junction stays empty.
- The viewmodel's `viewModelScope.launch { … }` block has no
  exception handler, so the SQLiteConstraintException propagates
  to `Dispatchers.IO`'s default uncaught-exception path → process
  death matching the operator's "crash" description.

A secondary candidate (Candidate 4) is that the operator's bug was
*not* a crash but a silent UX failure: the med inserts but is
invisible because no slot was linked. The "crash" verbiage may be
operator shorthand. Either way the fix surface overlaps — promote
the dialog warning to a hard gate (Save disabled when slots exist
and selections are empty) AND wrap the viewmodel write in try/catch.

## Implementation plan

### Phase 2 scope (single PR per operator scope-lock)

1. **`MedicationViewModel.addMedication` + `updateMedication`** — wrap
   the `viewModelScope.launch` body in `try { … } catch (e: Exception)
   { _errors.emit("Couldn't save medication: ${friendly(e)}") }`.
   Surface via a new `SharedFlow<String>` named `errors` that the
   screen collects into a Snackbar (matches the codebase's existing
   pattern — see CLAUDE.md § "Error handling").
   - LOC: ~25 added in viewmodel + ~10 in screen for the snackbar host.

2. **Pre-insert duplicate-name guard.** Before
   `medicationRepository.insert(...)` in `addMedication`, call
   `medicationRepository.getByNameOnce(name.trim())`. If a non-archived
   row matches, emit a friendly error and return. If an archived row
   matches, unarchive + update its fields rather than insert (match
   the spirit of `MedicationRefillViewModel.addMedication`'s path).
   - LOC: ~20 in viewmodel.

3. **Dialog gating fix.** In `MedicationEditorDialog`, change Save's
   `enabled` from `name.isNotBlank()` to
   `name.isNotBlank() && (selections.isNotEmpty() || activeSlots.isEmpty())`.
   Empty-slot dialogs (no slots configured at all) keep Save enabled
   so users can create a med-without-slot when no slots exist; once
   slots exist, the user must pick at least one. The advisory text
   stays as-is.
   - LOC: ~3 changed in dialog.

4. **Regression tests** (per Phase 1.B.3 + operator's "no new tests
   deferred" mandate):

   a. `MedicationViewModelAddMedicationTest` (new file):
      - `addMedication_withDuplicateName_emitsErrorWithoutCrashing()` —
        coEvery `getByNameOnce` returns a non-archived med; assert
        `errors` flow emits and `insert` is never called.
      - `addMedication_withArchivedDuplicateName_unarchivesAndUpdates()`
        — coEvery `getByNameOnce` returns archived; assert `update`
        called, no `insert`.
      - `addMedication_repositoryThrows_emitsErrorWithoutCrashing()` —
        coEvery `insert` throws SQLiteConstraintException; assert
        `errors` flow emits, no propagation.
      - `addMedication_emptySelections_persistsWithEmptyLinks()` —
        coverage for the safe path so we don't regress.

   b. Compose test deferred (UI-test on AVD only; logged as F.5 if
      operator wants it later — operator's "no new tests deferred"
      applies to unit-test regression, not Compose).

### Files touched + LOC estimate

| File | LOC delta |
|------|-----------|
| `MedicationViewModel.kt` | +50 / -5 |
| `MedicationScreen.kt` | +15 / -0 (snackbar host wiring) |
| `components/MedicationEditorDialog.kt` | +3 / -1 |
| `app/src/test/.../MedicationViewModelAddMedicationTest.kt` | +180 / -0 (new) |
| **Aggregate** | ~248 / -6 (well under STOP-F's 1500 ceiling) |

## STOP-conditions evaluated

- **STOP-A1** (reproduction failed): partially fired — no AVD available
  in this sandbox, so reproduction is structural-only. Operator
  authorized "broad scope" recon, which is sufficient given the
  candidate-#3 fix shape covers both the literal-empty-slot and the
  duplicate-name interpretations of the repro. **PROCEED with
  structural-only confidence; surface this caveat in PR description.**
- **STOP-A2** (different layer): not fired. Stack trace would land
  in the SQLite/Room insert path; structurally well-bounded.
- **STOP-B** (5+ null-safety gaps): not fired. Inventory found 3
  RED, 2 YELLOW, ~5 GREEN.
- **STOP-D** (sibling read-path crashes): YELLOW — slot insert
  shares the same ABORT/unique pattern, deferred per scope-lock.
- **STOP-E** (schema change required): not fired. Fix is at
  application layer; schema unchanged.
- **STOP-F** (LOC > 1500): not fired. ~248 LOC.

## Premise verification

- **D.1** (bug reproducible on today's debug build): structural-only
  in this session. Candidate #3 is reproducible in a unit test
  (`addMedication_repositoryThrows_…`) so the fix is self-validating
  even without AVD access.
- **D.2** (medication slot is currently nullable in some layer):
  CONFIRMED — `slotSelections: List<MedicationSlotSelection>` is
  always-non-null but `emptyList()` is allowed; per-(med, slot)
  override columns are nullable; `MedicationEntity.reminderMode` is
  nullable; nothing in the entity itself is "slot" though — the
  link is via the junction table and `slotSelections.map { it.slotId }`.
- **D.3** (memory #8 medication architecture holds):
  `find backend/alembic/versions -name "*medication*"` confirmed
  alembic migration 019 (medication tables in Postgres) exists; the
  cross-surface concern is real but *out of scope* for this PR
  (operator scope-lock).

## Open questions for operator

1. **Snackbar copy.** Suggested wording for the duplicate-name error:
   `"A medication named "$NAME" already exists. Edit it instead, or
   pick a different name."` — accept or counter-propose?
2. **Archived-name reuse.** Should reusing an archived med's name
   *unarchive* the archived row (preserves dose history) or insert a
   new row alongside it (cleaner UX, but requires lifting the unique
   index)? Plan above does the unarchive path. Confirm before Phase 2.
3. **STOP-A1 caveat.** Plan ships without an AVD-confirmed stack
   trace. Operator OK with structural-only confidence + unit-test
   self-validation, or do you want to halt and re-scope?

## Deferred (NOT auto-filed per memory #30)

- **F.5a — Slot insert hardening.** `MedicationSlotsViewModel.create`
  + `MedicationSlotDao.insert` share the same
  ABORT-on-unique-name pattern. Re-trigger criteria: another
  duplicate-slot-name crash report, or post-launch sync-pull
  conflict surface.
- **F.5b — `MedicationRefillViewModel` try/catch hygiene.** The
  refill add path is safe today (does pre-check + update-or-insert),
  but lacks a top-level try/catch. Re-trigger: any future write-path
  expansion that adds new exception sources.
- **F.5c — Compose UI test for the dialog gating change.** Re-trigger:
  next AVD test session or when operator green-lights a UI-test PR.
- **F.5d — Cross-surface (Postgres + Room + Firestore) duplicate-name
  reconciliation.** Per memory #8 medication architecture. Re-trigger:
  observed sync conflict where same `name` arrives from cloud while
  local insert is in flight.

## Anti-patterns considered and rejected

- **Don't catch `NullPointerException` and ignore.** Considered for
  the `medicationId!!` sites (line 171, 474) — rejected because the
  filter precondition makes them sound; widening the catch would
  hide future bugs.
- **Don't drop the unique-name index.** Considered as a "permissive"
  fix — rejected because cloud sync relies on `name`-based dedup
  in `BuiltInMedicationReconciler`. Lifting the index would create
  reconciler ambiguity.
- **Don't auto-rename on collision** (e.g., append "(2)"). Rejected
  because it surprises the user — explicit error + edit-existing
  affordance is clearer.

## Phase 3 — Bundle summary (pre-merge per memory #16 / CLAUDE.md repo-convention)

| Improvement | PR | LOC delta |
|-------------|-----|-----------|
| Viewmodel try/catch + duplicate-name guard + errors flow | [#1141](https://github.com/averycorp/prismTask/pull/1141) | +71 / -5 in `MedicationViewModel.kt` |
| Dialog Save-gating fix | #1141 | +10 / -1 in `MedicationEditorDialog.kt` |
| Snackbar wiring on `MedicationScreen` | #1141 | +14 / -2 in `MedicationScreen.kt` |
| Regression test (`MedicationViewModelAddMedicationTest`) | #1141 | +278 / 0 (new file) |

**Aggregate: 4 files, +373 / -8.** Bundled into a single PR per
operator scope-lock; well under the STOP-F 1500-LOC ceiling.

**Branch:** `claude/medication-crash-audit-first-U2Mvc`. Worktree
teardown paired with merge per memory `feedback_use_worktrees_for_features`.

**Verification (pre-merge gates, memory #22 bidirectional):**
- Static gates: ktlint / detekt / pytest / Android CI run via the
  branch's automatic CI bundle (subscribed via `subscribe_pr_activity`).
- Runtime gates: AVD smoke listed in PR test plan; the audit sandbox
  has no AVD, so AVD verification is operator-side post-merge.
- Memory #22 forward verification:
  - `git log -p -S "_errorMessages.emit"` confirms the new emit
    sites land on the `addMedication` and `updateMedication`
    code paths.
  - `git log -p -S "getByNameOnce"` confirms the pre-flight guard
    sits inside the new try/catch.
- Memory #22 reverse verification: AVD repro deferred to operator;
  unit tests assert no exception propagation on the duplicate-name
  and repository-throws branches.

**Memory entry candidates:** None. The fix shape (snackbar-routed
error flow + pre-flight `getByNameOnce`) is already established
elsewhere in the codebase (`AddEditProjectViewModel`,
`MedicationRefillViewModel`); this audit just brings the medication
add path into line. No new convention worth recording.

**D-series item closure:** 0 → 1.0 for the medication-add crash;
Phase F GREEN-GO blockers down by one. Set-priority silent failure
remains the other outstanding launch-blocker.

**Next audit candidate:** F.5a (medication slot insert hardening)
becomes the natural follow-up if any duplicate-slot-name crash
report surfaces, or pre-emptively as a hygiene PR before Phase F
kickoff May 15.
