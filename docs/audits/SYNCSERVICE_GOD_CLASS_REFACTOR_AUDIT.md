# SYNCSERVICE_GOD_CLASS_REFACTOR_AUDIT

**Scope:** F.6 architectural cleanup track. Refactor `SyncService.kt` from
god-class to per-entity dispatch composition (per `cc_syncservice_god_class_refactor_audit_first.md`).

**Phase 1 only.** Phase 2 deferred — see Operator Decision below.

**Audit-first protocol:** single-pass, no checkpoints, ≤500 lines.

---

## Operator decision (locked) — Option C: Audit-only Phase 1

The prompt asked for one of:
- **Option A:** single-large PR, all entities migrated
- **Option B:** staged refactor across multiple PRs
- **Option C:** audit-only Phase 1 + sequencing plan, defer Phase 2

**Verdict: Option C.** Two refactor axes are in active competition (per-entity
vs. surface-axis), one is **explicitly rejected by in-source author intent**,
and operator must reconcile before any Phase 2 implementation. Premise
corrections below explain. Default recommendation in the prompt was already
Option C; recon evidence reinforces it.

---

## Recon findings (Phase 1.A)

### A.1 Drive-by detection (GREEN — no prior extraction shipped)

`git log --all --oneline -- '**/SyncService.kt'` returns standard feature-add
commits only. No "split", "extract", "refactor" landed:
- `31972dd` PR #1085 — projects timeline-class (added entities)
- `d4023d2` PR #1077 — natural-key dedup (additive)
- `57f9784` PR #1070 — automation_rule routing (additive)
- `36767d8` — task_timings cross-device sync (additive)
- `b6cfc14` PR #934 — medication natural-key dedup (additive)

No `SyncEntityHandler`, `EntityPusher`, `EntityPuller`, or `EntitySyncHandler`
identifiers exist anywhere in `app/` or `docs/`. Confirmed via grep.

### A.2 Parked branches (GREEN)

`git branch -r | grep -iE "syncservice|sync-refactor|per-entity|sync.*split"`
returns only `origin/claude/syncservice-god-class-audit-q71FP` (this branch).
No prior parked work to rebase onto.

### A.3 Shape-grep — current SyncService.kt structure

**File: `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt`**
**Total: 3,839 lines.** (Premise drift — see D.1.)

Top-level method map:

| Line  | Method                                           | LOC          |
|-------|--------------------------------------------------|-------------:|
| 68    | `class SyncService` (constructor: 49 DAOs)       |              |
| 133   | `suspend fun initialUpload`                      | 73           |
| 206   | `private suspend fun doInitialUpload`            | **522**      |
| 728   | `private suspend fun uploadFocusReleaseLogs`     | 28           |
| 756   | `private suspend fun uploadAssignments`          | 28           |
| 784   | `private suspend fun uploadAttachments`          | 28           |
| 812   | `private suspend fun uploadStudyLogs`            | 38           |
| 850   | `private suspend fun <T> uploadRoomConfigFamily` | 58           |
| 908   | `private suspend fun maybeRunEntityBackfill`     | 27           |
| 935-1190| 7× `runXBackfillIfNeeded` methods              | ~256 total   |
| 1191  | `fun launchInitialUpload`                        | 25           |
| 1216  | `suspend fun pushLocalChanges`                   | 61           |
| 1277  | `private fun collectionNameFor`                  | 38           |
| 1320  | `private suspend fun pushCreate`                 | **230**      |
| 1550  | `private suspend fun pushUpdate`                 | **224**      |
| 1774  | `private suspend fun pushDelete`                 | 17           |
| 1791  | `suspend fun pullRemoteChanges`                  | **1,455**    |
| 3246  | `private suspend fun pullCollection`             | 30           |
| 3305  | `private suspend fun pullRoomConfigFamily`       | 51           |
| 3356  | `suspend fun fullSync`                           | 94           |
| 3450  | `private suspend fun restoreCloudIdFromMetadata` | 62           |
| 3512  | `fun startAutoSync`                              | 124          |
| 3636  | `fun startRealtimeListeners`                     | 70           |
| 3706  | `private suspend fun processRemoteDeletions`     | 118          |
| 3824  | `fun stopRealtimeListeners`                      | 15           |

Constructor takes **49 injected DAOs/services** (lines 68-123) — DI surface is
already a smell; any per-entity extraction must redistribute these.

### A.4 Entity enumeration (definitive count)

The "30+ synced entity families" claim is correct; actual count by registry:

- **`collectionNameFor` (lines 1277-1314):** 36 explicit `entityType → collection`
  mappings + 1 fallback (`else -> entityType + "s"`).
- **`pullCollection` invocations (28):** projects, tags, habits, tasks,
  task_completions, task_timings, habit_completions, habit_logs, milestones,
  project_phases, project_risks, task_dependencies, external_anchors,
  task_templates, courses, course_completions, leisure_logs, self_care_steps,
  self_care_logs, medication_slots, medications, medication_doses,
  medication_slot_overrides, medication_tier_states, focus_release_logs,
  assignments, attachments, study_logs.
- **`pullRoomConfigFamily` invocations (13):** notification_profiles,
  custom_sounds, saved_filters, nlp_shortcuts, habit_templates, project_templates,
  boundary_rules, automation_rules, check_in_logs, mood_energy_logs,
  medication_refills, weekly_reviews, daily_essential_slot_completions.
- **`uploadRoomConfigFamily` calls in `doInitialUpload` (13):** mirrors above.
- **`startRealtimeListeners` registry (lines 3639-3650):** 40 collections.
- **Bespoke initial-upload methods (4):** `uploadFocusReleaseLogs`,
  `uploadAssignments`, `uploadAttachments`, `uploadStudyLogs` — entities that
  do NOT use the `uploadRoomConfigFamily` helper.
- **Bespoke backfill methods (7):** courses, course_completions, leisure_logs,
  self_care_steps, self_care_logs, medications, medication_doses.

**Distinct sync-shape categories: 4+** (uniform `pullCollection` cloud-id
keying; `pullRoomConfigFamily` with optional `naturalKeyLookup`; bespoke
upload paths; backfill-once paths). Non-uniform — see B.1 verdict.

### A.5 Sibling-primitive (e) axis (DEFERRED — surface, do not bundle)

Other `data/remote/*.kt` classes that touch sync but are NOT god-class
candidates from this brief scan:

- `GenericPreferenceSyncService.kt` (206 lines) — DataStore-backed user prefs.
  4 lifecycle methods (`startPushObserver` / `ensurePullListener` /
  `startAfterSignIn` / `stopAfterSignOut`).
- `ThemePreferencesSyncService.kt` (173 lines) — same shape, theme-specific.
- `SortPreferencesSyncService.kt` (224 lines) — same shape, sort-specific.
- `BackendSyncService.kt` (720 lines) under `sync/` — backend (FastAPI)
  sync, separate from Firestore SyncService.

These are **prior-extracted preference handlers**, not entity-table sync.
Their existence does NOT support the per-entity hypothesis in the prompt;
they handle a different concern (Firestore-backed user preferences via
single-document upserts, not entity-table batch sync). Do not conflate.

Other reconcile/heal/backfill helpers in `data/remote/`:
`BuiltInHabitReconciler`, `BuiltInTaskTemplateReconciler`,
`BuiltInTaskTemplateBackfiller`, `BuiltInMedicationReconciler`,
`CloudIdOrphanHealer`, `MedicationMigrationRunner`,
`AutomationDuplicateBackfiller`, `LifeCategoryBackfiller`. All are
single-purpose, already-extracted, and small. None are god-class candidates.

**Memory #18 quad-sweep (e) axis verdict:** No bundled refactor candidates
surfaced. Other classes (`DataExporter`, `DataImporter`) may be dispatch-heavy
but are out of scope for this audit.

---

## Refactor hypothesis verdicts (Phase 1.B)

### B.1 Handler interface shape (RED — explicitly rejected by in-source intent)

The prompt's hypothesis (B.1): "each entity family becomes a class
implementing a `SyncEntityHandler<T>` interface with the 6 touchpoints
(initialUpload, collectionNameFor, pushCreate, pushUpdate, pullRemoteChanges,
processRemoteDeletions, startRealtimeListeners)."

**Two contradictions in the source itself:**

1. **`SyncService.kt:1316-1319`** (above `pushCreate`):
   ```
   // Dispatch across every synced entityType — splitting the `when` is not
   // worth the indirection since each branch is only a DAO lookup + mapper
   // call. TODO: refactor pushCreate to reduce early return statements.
   ```
2. **`SyncService.kt:1547-1549`** (above `pushUpdate`):
   ```
   // Dispatch across every synced entityType — see pushCreate for the same
   // trade-off. TODO: refactor pushUpdate to reduce early return statements.
   ```

The author has already considered per-entity dispatch and **explicitly
rejected** it — the only TODO is "reduce early return statements" within the
existing dispatch.

**Plus a competing in-source TODO at `SyncService.kt:63-65`:**
```
// TODO(sync-refactor): split SyncService — separate push, pull, listener,
// and initial-upload surfaces. Each PR that touches this file widens the
// file further; the next refactor should land before the next feature.
```

This recommends a **surface-axis split** (push / pull / listener / initial-upload),
NOT a per-entity-axis split.

**Plus non-uniform entity sync shapes (4+ categories per A.4)** mean the
"uniform 6-touchpoint handler interface" assumption breaks before
implementation:
- 28 entities use `pullCollection` (cloud-id-keyed only)
- 13 entities use `pullRoomConfigFamily` (with optional `naturalKeyLookup`)
- 4 entities have bespoke `uploadX` paths
- 7 entities have bespoke `runXBackfillIfNeeded` paths
- A handful (tasks, projects, habits, medication_slots) have inline
  natural-key dedup that doesn't fit either helper

A uniform interface would either need to be a kitchen-sink with 6+ optional
hooks (defeating the abstraction) or fragmented into 4+ sub-interfaces
(defeating the simplification).

**Verdict: RED.** The interface-shape hypothesis must be reconciled with the
in-source author's documented preference + with non-uniform entity shapes
before any Phase 2 work. Recommend operator pivots to surface-axis split.

### B.2 Migration approach (DEFERRED — gated on B.1 reconciliation)

Strangler Fig vs. Big Bang is moot until B.1 is settled. If operator pivots
to surface-axis split (matching the in-source TODO at line 63):

- **`SyncPusher`** would absorb `pushCreate` + `pushUpdate` + `pushDelete` +
  `pushLocalChanges` (~530 LOC).
- **`SyncPuller`** would absorb `pullRemoteChanges` + `pullCollection` +
  `pullRoomConfigFamily` (~1,540 LOC).
- **`SyncListenerManager`** would absorb `startRealtimeListeners` +
  `stopRealtimeListeners` + listener-triggered `processRemoteDeletions` (~200 LOC).
- **`SyncInitialUploader`** would absorb `initialUpload` + `doInitialUpload` +
  `uploadXxx` helpers + `uploadRoomConfigFamily` + `runXxxBackfillIfNeeded` (~1,000 LOC).
- **`SyncService`** stays as orchestrator: holds `startAutoSync`, `fullSync`,
  `restoreCloudIdFromMetadata`, `collectionNameFor` (~250 LOC).

Surface-axis split keeps the per-entity `when` dispatches intact (preserving
the author's stated trade-off) while eliminating the 3,839-line file. Each
new class is ≤1,540 LOC — still large, but no longer god-class.

Strangler Fig still preferred for either axis: each new class can absorb its
dispatch in one PR while the old `SyncService` delegates pass-through, then
the old code is deleted in a final cleanup PR. This is N+1 PRs but each one
is independently revertable.

### B.3 Behavioral preservation gates (enumerated)

If a refactor proceeds, the following must hold:

1. **SyncMapper round-trip tests stay GREEN.** 8 unit-test files cover the
   mapper layer — these would not move during refactor and must not regress.
2. **Cross-device sync tests stay GREEN.** 8 instrumentation scenario tests
   under `app/src/androidTest/java/.../sync/scenarios/` (Test7-Test14 +
   `MedicationCrossDeviceConvergenceTest`) plus 4 fuzz tests under
   `app/src/androidTest/java/.../sync/fuzz/`.
3. **DAO-gap CI guard preserved** (CLAUDE.md "current DB version" rule, memory
   #20). Refactor must not add infra that bypasses the production-DB / test-DB
   parity check.
4. **`startAutoSync` multi-subscriber-installer behavior preserved.** Lines
   3512-3633 install three subscribers (realtime listeners; reactive push
   observer on `syncMetadataDao.observePending`; `ReactiveSyncDriver` for
   reconnect + periodic backstop). Whatever orchestrator survives must keep
   this exact wiring.
5. **Natural-key dedup at lines 1681-1693 (habit_completions) preserved.**
   Plus the `naturalKeyLookup` callbacks at lines 2850, 2919, 2942, 2964,
   2992, 3014, 3040 — all medication / mood / refill / nlp_shortcut paths.
6. **`naturalKeyLookup` parameter on `pullRoomConfigFamily` preserved.** PR #1077
   pattern; lines 3290-3354.
7. **Conflict resolution (LWW on `updatedAt`) preserved exactly.** Lines
   3321-3324 + 3346-3351.
8. **`isSyncing` flag semantics preserved.** Re-entrancy guards at lines 150,
   3357-3365, 3577-3580, 3670 are load-bearing for sign-in serialization.
9. **One-shot `initialUpload` guard preserved.** Lines 140-143 — the
   `builtInSyncPreferences.isInitialUploadDone()` check is the duplication-spiral
   fix.
10. **Post-`initialUpload` pull preserved.** Lines 188-205 — defense for the
    `isSyncing`-held window.

### B.4 Test coverage (YELLOW — STOP-E partial; mapper-heavy, dispatch-thin)

Existing coverage:
- **Mapper layer (8 unit tests):** `SyncMapperTest`, `SyncMapperTier2Test`,
  `SyncMapperContentTest`, `SyncMapperRoomConfigTest`,
  `MedicationSlotSyncMapperTest`, `SyncMapperPhasesAndRisksTest`,
  `SyncMapperTaskDependencyTest`, `SyncMapperExternalAnchorTest`.
- **Driver layer (1 unit test):** `ReactiveSyncDriverTest`.
- **Preference sync (1 unit test + 1 emulator):** `PreferenceSyncSerializationTest`,
  `GenericPreferenceSyncServiceEmulatorTest`.
- **Cross-device scenarios (8 instrumentation):** Test7-Test14 +
  `MedicationCrossDeviceConvergenceTest`.
- **Fuzz (4 instrumentation):** `Fuzz02`, `Fuzz04`, `SyncFuzzScenarioBase`,
  `SyncFuzzGeneratorTest`.
- **`SyncTestHarness.kt`** (319 LOC) provides cross-device emulator scaffold.

**Coverage gap:** No `SyncServiceTest.kt` in `src/test`. `pushCreate` /
`pushUpdate` / `pullRemoteChanges` / `processRemoteDeletions` dispatch logic
is covered only transitively via instrumentation scenarios. A behavior-preserving
refactor would lean entirely on the slow-CI emulator + fuzz suites for
regression detection.

**Verdict: YELLOW.** Pre-refactor test-shoring would be prudent. Adding direct
`SyncService` dispatch unit tests (one per entity-shape category) would cost
~300-500 LOC of test code but anchor the refactor at unit-test speed instead
of emulator speed.

---

## STOP-conditions evaluated (Phase 1.C)

- **STOP-A** (prior work shipped/started): **GREEN — does not fire.** A.1 + A.2
  came back empty. Sibling preference services (A.5) are orthogonal, not
  prior extraction of entity dispatch.
- **STOP-B** (size drift): **FIRES — material.** File is 3,839 LOC, not 2,930.
  31% larger than the prompt's baseline. Implementation cost estimate must
  be re-anchored. Two recent feature PRs (#1085, #1094) added entity types
  and 700+ LOC since the prompt was drafted.
- **STOP-C** (sibling god-classes): **DEFERRED.** No bundling. Other large
  classes (DataImporter, DataExporter, BackendSyncService at 720 LOC) may
  warrant follow-up audits but **MUST NOT** ride this PR.
- **STOP-D** (non-uniform sync semantics): **PARTIALLY FIRES.** A.4 finds at
  least 4 distinct sync-shape categories. The "uniform handler interface"
  assumption breaks. Reinforces Option C (audit-only) — operator must
  pivot to a smaller interface + composition, OR accept the in-source
  author's surface-axis recommendation.
- **STOP-E** (insufficient test coverage): **YELLOW.** Coverage is mapper-heavy
  but missing direct dispatch unit tests. Recommend test-shoring PR before
  any Phase 2 implementation. See B.4.
- **STOP-F** (size-based, >2000 LOC delta): **FIRES — almost certainly.**
  Big Bang refactor of either axis would touch 3,000+ LOC across the moved
  code; surface-axis split alone moves ~3,500 LOC into 4 new files. Single-PR
  exception required from operator regardless of axis. Strangler Fig N+1 PRs
  is the only way to keep individual PRs under the implicit threshold.
- **STOP-G** (in-flight PRs): **GREEN — does not fire.** Last touch was
  PR #1094 (merged). No active branches besides this audit branch.

---

## Premise verification (Phase 1.D)

- **D.1** SyncService.kt at `data/remote/SyncService.kt` (NOT `sync/SyncService.kt`
  as the prompt states). Size **3,839 LOC**, not 2,930. **Premise WRONG on
  both path and size.**
- **D.2** PR #753 reference: no commit `gh search` hits matched "god class
  SyncService refactor". The closest landed audit is
  `docs/audits/ANDROID_WEB_PARITY_AUDIT.md`. The prompt's PR #753 reference
  is not verifiable from this branch's git history. **Premise UNVERIFIED.**
- **D.3** "30+ synced entity families" — confirmed (36 in `collectionNameFor`,
  ~40 in realtime listener registry). **Premise CORRECT.**
- **D.4** No prior PR has shipped or started this refactor. **Premise CORRECT.**
- **D.5** Recent SyncService PRs (#1056, #1070, #1077, #1085, #1094)
  reviewed — all are additive (new entities, new dispatch branches), none
  started extraction. **Premise CORRECT.**

---

## Phase 2 scope (skipped — Option C)

Phase 2 is **deferred** until operator decides between two reconciliation paths:

- **Path 1 — Pivot to surface-axis split** (matches in-source line 63 TODO):
  - PR-1: Test-shoring — add `SyncServiceDispatchTest` covering pushCreate /
    pushUpdate per entity-shape category. ~400 LOC of tests, no production
    change.
  - PR-2: Extract `SyncPuller` (largest, ~1,540 LOC).
  - PR-3: Extract `SyncInitialUploader` (~1,000 LOC).
  - PR-4: Extract `SyncPusher` (~530 LOC).
  - PR-5: Extract `SyncListenerManager` (~200 LOC).
  - PR-6: Cleanup pass — remove pass-through delegates, finalize coordinator.

- **Path 2 — Override the in-source author** and proceed with per-entity
  handlers as the prompt proposed. Requires operator to explicitly accept
  the contradiction with lines 1316-1319, 1547-1549. NOT recommended without
  written rationale, since the author's reasoning ("each branch is only a
  DAO lookup + mapper call") still holds.

- **Path 3 — Defer entirely.** F.6 closure stays at 0/1. Add a comment to
  the in-source TODO at line 63 noting the audit doc landed.

---

## Audit-only deliverable (Option C)

This audit doc IS the Phase 1 deliverable.

**Sequencing plan if Path 1 is picked** (suggested per-PR scope):

| Slice | New file                        | LOC moved | Old SyncService LOC removed | Risk |
|------:|---------------------------------|----------:|---------------------------:|------|
| 0     | `SyncServiceDispatchTest.kt`    |     ~400  |                          0 | LOW  |
| 1     | `sync/SyncPuller.kt`            |    ~1540  |                      ~1540 | HIGH |
| 2     | `sync/SyncInitialUploader.kt`   |    ~1000  |                      ~1000 | MED  |
| 3     | `sync/SyncPusher.kt`            |     ~530  |                       ~530 | MED  |
| 4     | `sync/SyncListenerManager.kt`   |     ~200  |                       ~200 | LOW  |
| 5     | Cleanup (delete delegates)      |       0   |                       ~250 | LOW  |

Final shape: `SyncService.kt` ≈ 250-300 LOC orchestrator. Each new file is
≤1,540 LOC — still large, but no longer god-class. Each PR is independently
revertable. Total Phase 2 wall-clock estimate: **3-5 sessions** at typical
rate.

---

## Deferred (NOT auto-filed per memory #30)

- **Other large `data/remote` classes:** `BackendSyncService.kt` (720 LOC),
  `DataExporter.kt`, `DataImporter.kt` — re-trigger if any of these grow past
  1,500 LOC or develop similar in-source `TODO(refactor)` markers.
- **Constructor DI surface:** `SyncService` takes 49 injected dependencies.
  Surface-axis split would naturally redistribute these (each new class
  needs only its slice's DAOs). Re-trigger if any single new class still
  takes >25 dependencies after refactor.
- **`pullRemoteChanges` decomposition (1,455 LOC alone):** Even within the
  surface-axis split, `SyncPuller` would still be the largest file. A
  follow-up audit could decompose `pullRemoteChanges` along its 28+13 entity
  blocks, but this is not blocking.
- **Test-shoring PR (Slice 0):** Could ship independently as a F.6
  test-coverage item, even if the refactor is deferred indefinitely. Adds
  regression-gate without behavior change.

---

## Open questions for operator

1. **Refactor axis decision: per-entity vs. surface-axis?** In-source author
   intent at `SyncService.kt:63` and `:1316-1319` recommends surface-axis;
   the prompt assumes per-entity. Operator must pick before Phase 2.
2. **PR #753 reference traceability.** The prompt cites "surfaced during PR
   #753 audit"; no audit doc on disk matches. Operator can confirm where this
   reference originated (timeline notes? memory #22? a different audit?).
3. **Single-PR convention exception.** STOP-F fires regardless of axis.
   Strangler-Fig N+1 PRs is the only path that respects per-PR-size norms;
   operator pre-confirms this is acceptable F.6 framing.
4. **Test-shoring PR sequencing.** Should Slice 0 (dispatch unit tests) ship
   even if the broader refactor is deferred? It pays down test-coverage debt
   independently and is reusable across both axis choices.

---

## Anti-patterns to avoid (flagged, not necessarily fixed)

- **Do not** add a `SyncEntityHandler<T>` interface without first reconciling
  the in-source rejection at lines 1316-1319.
- **Do not** bundle other god-class refactors (BackendSyncService, DataImporter,
  DataExporter) into this PR — separate scopes, separate audits.
- **Do not** ship Phase 2 without a behavior-preserving test gate. STOP-E
  exists for this; Slice 0 must land first regardless of axis chosen.
- **Do not** paraphrase existing dispatch logic. Behavioral preservation
  means exact semantic preservation: `naturalKeyLookup`, LWW on `updatedAt`,
  `isSyncing` re-entrancy guards, one-shot `initialUpload` guard,
  `multi-subscriber-installer` orchestration — all eight gates in B.3
  must be byte-identical post-refactor.
- **Do not** assume the constructor's 49 DAO dependencies are evenly
  redistributable. Several DAOs (`syncMetadataDao`, `taskDao`, `projectDao`,
  `tagDao`) are touched by every dispatch — they will be passed to multiple
  new classes, not partitioned cleanly.

---

## Ranked improvement table (wall-clock-savings ÷ implementation-cost)

| Rank | Action                                     | Cost    | Savings        | Ratio |
|-----:|--------------------------------------------|--------:|----------------|------:|
| 1    | Append note to in-source TODO at line 63   | 5 min   | clarifies axis | high  |
| 2    | Slice 0: SyncServiceDispatchTest           | 1 sess  | unblocks 1-5   | high  |
| 3    | Path 1 Strangler Fig (slices 1-5)          | 3-5 sess| F.6 closure    | med   |
| 4    | Path 3 (defer entirely, accept god-class)  | 0       | nothing breaks | n/a   |
| 5    | Path 2 (per-entity, override author)       | 5+ sess | F.6 closure    | low   |

Top-1 (annotate the in-source TODO with audit-doc reference) is the only
zero-risk action that ships value immediately. Every other path requires
operator decision first.

---

## Phase 3 — Bundle summary (pre-merge per CLAUDE.md)

**PR shipped:** [#1118](https://github.com/averycorp/prismtask/pull/1118)
— `docs(audits): SyncService god-class refactor — Phase 1 (Option C)`.
Branch `claude/syncservice-god-class-audit-q71FP`, commit `c12c2c1`.

**Per-improvement table:**

| Item                            | PR     | Status     | Measured impact            |
|---------------------------------|-------:|------------|----------------------------|
| Phase 1 audit doc               | #1118  | open       | doc-only; no behavior change |
| Path-1 surface-axis split       | —      | DEFERRED   | gated on operator decision |
| Path-2 per-entity handlers      | —      | NOT REC.   | contradicts in-source author |
| Path-3 defer entirely           | —      | DEFERRED   | F.6 stays at 0/1            |
| Top-1 annotate line-63 TODO     | —      | DEFERRED   | trivial, can ship anytime  |

**Re-baselined wall-clock-per-PR estimate:** Path-1 Strangler Fig at
3-5 sessions across 6 PRs (Slice 0 test-shoring + Slices 1-5). Original
prompt under-anchored (assumed 2,930 LOC; actual 3,839 LOC).

**Memory entry candidates (only if surprising / non-obvious):**
- *Candidate:* When recon finds a competing in-source `TODO(refactor)`
  with explicit axis preference, surface that contradiction as the FIRST
  premise correction in the audit doc — operator's prompt may be working
  from a stale or different mental model. (3-of-3 audits this week have
  surfaced source-of-truth contradictions; pattern is durable.)
- *Skip:* The "31% size drift since prompt was written" finding is
  not surprising for a long-lived prompt; STOP-B exists for exactly
  this and is already documented.

**Schedule for next audit:** After operator picks Path 1 / 2 / 3.
If Path 1 is picked, the next audit is Slice 0 (test-shoring PR scoping).
If Path 3 is picked, no next audit required.

---

## Phase 4 — Claude Chat handoff

See the handoff block emitted at the end of the session-summary message
(it is not duplicated here to keep the audit doc canonical-source for the
verdicts and out of paste-block formatting).

