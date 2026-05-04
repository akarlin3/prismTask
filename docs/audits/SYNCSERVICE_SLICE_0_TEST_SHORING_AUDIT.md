# SyncService Slice 0 — Pre-Refactor Test Shoring Audit

**Source:** Audit-first invocation per CLAUDE.md § Repo conventions (universal default).
**Severity:** Test-debt paydown ahead of upcoming surface-axis refactor on `SyncService.kt`.
**Branch:** `claude/add-syncservice-dispatch-tests-saCxi`.
**Phase 1 cap:** 500 lines. **Phase 3 + 4 fire pre-merge** per CLAUDE.md (audit-first override).
**Audit references:** memories #16 (pre-merge Phase 3+4), #18 (quad-sweep recon), #20 (DAO-gap CI guard), #22 (bidirectional verify), #28 (parked-branch sweep), #30 (no auto-fan-out).

---

## Operator decision (locked)

PR #1118 audit (`docs/audits/SYNCSERVICE_GOD_CLASS_REFACTOR_AUDIT.md`, prospective) verdicted SyncService test coverage as YELLOW (STOP-E partial fire): "8 mapper tests + 8 emulator scenarios; no direct dispatch unit tests." Operator picked **Slice 0 to ship INDEPENDENTLY** as a small PR right now, regardless of whether Phase 2 surface-axis refactor proceeds — pays down test debt + is reusable across either axis even if broader refactor is deferred.

Estimated original envelope: ~150-300 LOC of new test code, no production code changes. Phase 1 below revises this — light pure-function exposure (`@VisibleForTesting internal` companion-object hoist + 2 zero-behavior-change extractions of inline `when` tables) is **strictly required** to make the dispatch surface testable without injecting Firestore (which would be a structural production change too large for Slice 0). Each visibility change carries a captured rationale in § Per-shape test scope.

---

## Phase 1 — Audit (single-pass, recon-first)

### A. Recon (Memory #18 quad sweep)

**A.1 Drive-by detection** — has any direct dispatch test work shipped on `main`?

```
$ git log -p -S "SyncServiceTest" origin/main --oneline | head
[only matches an audit-doc reference in PR #1093, no test code]
$ git log -p -S "pullCollectionTest" origin/main --oneline | head
[empty]
```

Verdict: **NO direct dispatch test work has shipped or started.** STOP-A does NOT fire.

**A.2 Parked-branch sweep** (memory #28):

```
$ git branch -r | grep -iE "syncservice.*test|sync.*shoring|dispatch.*test"
  origin/claude/add-syncservice-dispatch-tests-saCxi    [this branch only]
```

Verdict: no parked test-shoring work elsewhere.

**A.3 Existing test coverage map** — definitive enumeration of files that touch SyncService in tests.

`app/src/test/` (12 unit test files referencing sync):
- `domain/SyncMapperTest.kt` — 8 mapper round-trip tests (the canonical ones)
- `domain/SyncMapperTier2Test.kt` — Tier-2 mapper coverage
- `data/remote/sync/BackendSyncMappersMedicationTest.kt` — backend mapper
- `data/remote/sync/PreferenceSyncSerializationTest.kt` — preference serializer
- `data/remote/sync/ReactiveSyncDriverTest.kt` — driver-level coverage
- `data/remote/mapper/SyncMapperContentTest.kt`, `SyncMapperRoomConfigTest.kt`,
  `MedicationSlotSyncMapperTest.kt`, `SyncMapperPhasesAndRisksTest.kt`,
  `SyncMapperTaskDependencyTest.kt`, `SyncMapperExternalAnchorTest.kt` — per-entity mappers
- `sync/fuzz/SyncFuzzGeneratorTest.kt` — fuzz generator
- `data/calendar/CalendarSyncPreferencesTest.kt` — calendar prefs

`app/src/androidTest/` (8 emulator/integration files):
- `SyncMapperCloudIdTest.kt`, `BuiltInSyncPreferencesLegacyFallbackTest.kt`,
  `GenericPreferenceSyncServiceEmulatorTest.kt`, `sync/SyncTestHarness*.kt`,
  `sync/scenarios/*` (Test8/Test9/Test10/HabitCompletionStaleParentMetadata/...)

**Definitive gap:** ZERO files exercise `SyncService.kt`'s dispatch tables (`collectionNameFor`, the inline inverse table in `processRemoteDeletions`, or the inline priority sort in `pushLocalChanges`) directly. The mapper tests pin **serialization** (entity ↔ Map). The emulator tests pin **end-to-end behavior**. **Neither pins the dispatch wiring**, which is exactly what the surface-axis refactor will move.

**A.4 Dispatch shape enumeration** (with line anchors):

| # | Shape | Signature | Location | Coupling |
|---|-------|-----------|----------|----------|
| 1 | Generic collection pull | `private suspend fun pullCollection(name, handler): PullResult` | line 3246 | Firestore (`userCollection(name)?.get()?.await()`) |
| 2 | Config-family pull (LWW + natural-key dedup) | `private suspend fun pullRoomConfigFamily(collection, entityType, getLocalUpdatedAt, insert, update, naturalKeyLookup?): PullResult` | line 3305 | Calls `pullCollection`, plus `syncMetadataDao` |
| 3 | Push create dispatch | `private suspend fun pushCreate(meta)` — 36-branch `when (meta.entityType)` | line 1320 | Firestore + 30+ DAOs + SyncMapper |
| 4 | Push update dispatch | `private suspend fun pushUpdate(meta)` — mirror of pushCreate | line 1550 | Firestore + 30+ DAOs |
| 5 | Push delete dispatch | `private suspend fun pushDelete(meta)` — uses `collectionNameFor` only | line 1774 | Firestore |
| 6 | **Forward dispatch table** | `private fun collectionNameFor(entityType): String` — 36 entries + `else -> entityType + "s"` | line 1277 | **PURE — no Firestore, no DAO** |
| 7 | **Inverse dispatch table** | inline `when (collection)` returning entity type | line 3707 inside `processRemoteDeletions` | inline + then DAO calls |
| 8 | **Push priority sort key** | inline `when (it.entityType)` returning Int (project=0, tag=1, task_completion=3, else=2) | line 1220 inside `pushLocalChanges` | **PURE** |
| 9 | Backfill `syncableTables` list | hard-coded `listOf<Pair>` of 19 (table, entity) pairs | line 3456 inside `restoreCloudIdFromMetadata` | inline list literal |
| 10 | Initial-upload backfill paths | `doInitialUpload`, `uploadFocusReleaseLogs`, `uploadAssignments`, `uploadAttachments`, `uploadStudyLogs`, `uploadRoomConfigFamily`, `maybeRunEntityBackfill`, 8 `runXBackfillIfNeeded` | various 206-1190 | Firestore + DAO |

**Critical observation:** the dispatch table is **triplicated** (#6 forward, #7 inverse, #9 syncableTables). Drift between them is a latent bug class — adding a new entity to `collectionNameFor` while forgetting to update `processRemoteDeletions` silently breaks remote-deletion propagation for that entity. No test exists to catch this.

**A.5 Sibling-primitive (e) axis** — other places in PrismTask with similar "test the dispatch, not the data" gaps:

- `BackendSyncService` (~720 LOC per PR #1118 audit referenced by operator) — likely similar gap. **NOT in scope for Slice 0.** Surfaced only.
- `DataImporter` / `DataExporter` — entity dispatch via `when` over kind. Same gap pattern. **NOT in scope.**
- `AutomationEngine` — already has solid coverage per PR #1056/#1057, not a gap.

Per memory #30: surface, do not auto-file.

### B. Implementation hypothesis

**B.1 Test framework choice.**

Existing convention (`SyncMapperTest`, `domain/`, `data/remote/sync/`) is **pure JUnit + simple object construction**, no MockK setup, no Robolectric. Tests assert on returned values; no Firebase, no Android stack. Fast (<100ms each).

**Slice 0 follows this convention.** Approach 1 (pure unit, no MockK or Robolectric) is the right call for the in-scope shapes (#6, #7, #8) — they are pure dispatch tables with no I/O. Approach 2 (Robolectric) would only matter if shapes #1–#5 or #10 were in scope, which they are NOT (see B.2).

**B.2 STOP-C evaluation — what's testable as pure unit?**

Shapes #1, #2, #3, #4, #5, #10 are **tightly coupled to private state** (lazy `firestore`, 49 injected DAOs, internal `SyncMetadataDao` calls). Pure-unit testing requires **either**:

- Constructor-inject `FirebaseFirestore` (currently `private val firestore by lazy { FirebaseFirestore.getInstance() }` at line 124) — **structural production change, too invasive for Slice 0.**
- Hoist 49-DAO mocks via MockK and run with Robolectric — **breaks `SyncMapperTest` precedent and balloons fixture LOC past 500.**

**STOP-C fires PARTIAL** — Slice 0 is bounded to the **pure dispatch tables** (#6, #7, #8). The behavioral shapes (pull/push/backfill) are **explicitly deferred to a follow-up** that pairs with the constructor-injection step of the surface-axis refactor (Phase 2).

This deferral is **not** a scope shrink relative to refactor-preservation goal — the dispatch tables are the **most likely refactor casualty** (a refactor that splits SyncService into PullCoordinator / PushCoordinator / Router will move these tables verbatim; tests pin them). The behavioral shapes are protected by the existing 8 emulator scenarios + 12 mapper tests today; they are not fully unprotected even pre-Slice-0.

**B.3 Per-shape Slice 0 test scope (in scope only).**

Shape #6 — `collectionNameFor` (forward dispatch table):
- 36 explicit entityType → collection-name assertions (one per `when` branch)
- 1 fallback assertion (`unknown_entity` → `"unknown_entitys"` per `else -> entityType + "s"`)
- 1 empty-string corner (`""` → `"s"`) — pins the literal fallback semantics
- **38 test cases**

Shape #7 — inverse table inside `processRemoteDeletions`:
- 41 collection-name → entityType assertions (one per `when` branch in the inline 3707 block — this is one MORE than `collectionNameFor` because `tasks/projects/tags/habits/milestones` are also handled in the inverse but `milestone` is missing from `collectionNameFor` — see § Drift findings below)
- 1 fallback assertion (unknown collection → null/no-op)
- **42 test cases**
- **Bidirectional consistency** test: for every `entityType` in shape #6, `entityTypeForCollectionName(collectionNameFor(e)) == e`. This is the critical **drift-catch** test.

Shape #8 — `pushOrderPriorityOf` (extracted from `pushLocalChanges` line 1220):
- 4 cases: `"project"` → 0, `"tag"` → 1, `"task_completion"` → 3, anything-else → 2
- 1 stable-sort property test: a list of mixed entity types sorts to `[project*, tag*, others*, task_completion*]` order
- **5 test cases**

**Total:** ~85 test cases, est. ~180-220 LOC (each test ~2-4 LOC).

This is at the **upper end** of the prompt's ~15-25 estimate, but the higher count is because the dispatch tables are larger than the prompt assumed (36-41 entries per shape, not the rough 4+ category headcount). All cases are 1-line `assertEquals` assertions, not heavyweight fixtures.

**Drift findings during enumeration** (logged here, not auto-filed per memory #30):

1. `collectionNameFor` (line 1277) is missing `milestone` (the row at line 2098 calls `pullCollection("milestones")` directly without going through `collectionNameFor`, and there's no `milestone` push branch either — milestones may be pull-only or this is a latent gap).
2. `pushCreate`/`pushUpdate` have a `milestone` column gap mirror; `processRemoteDeletions` (line 3716) DOES list `milestones -> milestone`. If milestones ever become push-eligible, the missing entry in `collectionNameFor` will silently break push.
3. `restoreCloudIdFromMetadata` `syncableTables` (line 3456) only lists 19 tables — significantly fewer than the 36 in `collectionNameFor`. It's documented at line 3453 as "Mirrors `Migration_51_52.syncableTables`" — it's intentionally a subset (the migration's targets), so testing it is a separate concern. **OUT of scope for Slice 0.**

These drift findings are exactly the bug class the bidirectional consistency test surfaces. They are **NOT auto-fixed** by Slice 0 — the audit captures the observation; correctness is for the surface-axis refactor PR or an explicit follow-up.

**B.4 Memory #20 DAO-gap CI guard.**

Slice 0 introduces NO new DAOs and NO new entities. The `androidTest/smoke/TestDatabaseModule.kt` `@Provides` graph is untouched. The new test file lives in `app/src/test/` (unit, not instrumentation), so it cannot trip the smoke `@Provides` audit. Verified by inspection.

**B.5 Production-code change inventory.**

Three minimal touches in `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt`:

1. **Hoist `collectionNameFor` to `companion object`, mark `@VisibleForTesting`.**
   - Body unchanged. Call sites (`pushCreate` line 1321, `pushUpdate` ~line 1551, `pushDelete` line 1776) already invoke as `collectionNameFor(...)`; companion-object methods resolve identically from inside the class. **Zero call-site edits.**
   - Rationale: pure 36-branch dispatch table is the surface-axis refactor's #1 extraction target; without test coverage, refactor can drop entries silently.

2. **Extract `pushOrderPriorityOf(entityType: String): Int` into `companion object`, mark `@VisibleForTesting`. Replace inline `when` at line 1220 with `pushOrderPriorityOf(it.entityType)`.**
   - Body identical to inline `when`. ONE call site replaced.
   - Rationale: push-order priority encodes a non-obvious foreign-key ordering invariant ("project before tag before others before task_completion"). Refactor MUST preserve this; without a test, it's invisibly fragile.

3. **Extract `entityTypeForCollectionName(collection: String): String?` into `companion object`, mark `@VisibleForTesting`. Replace inline `when` at line 3707 with `entityTypeForCollectionName(collection) ?: return`.**
   - Body identical to inline `when`'s mapping arms; the `else -> return` is rewritten as `?: return` at the call site (semantic-equivalent: null result → early return).
   - Rationale: the inverse table is the most drift-prone surface in SyncService (triplicated with #6 and #9). Pinning it + bidirectional consistency catches the exact bug class the surface-axis refactor will be tempted to introduce.

Each change is the **minimum** to make pure-unit dispatch testing possible without injecting Firestore. None modifies behavior. All are companion-object-hoisted so tests do not need to instantiate `SyncService` (which has 49 injected dependencies).

### C. STOP-conditions evaluated

| STOP | Verdict | Notes |
|------|---------|-------|
| A — direct dispatch tests already shipped | NOT FIRED | A.1 + A.2 confirm none exist |
| B — `SyncMapperTest` already covers dispatch | NOT FIRED | A.3 confirms it covers serialization only, not dispatch |
| **C — dispatch shapes coupled to private state** | **PARTIAL FIRE** | Shapes #1–#5, #10 require Firestore injection. Slice 0 bounded to shapes #6, #7, #8 (pure tables). Behavioral shapes deferred to follow-up paired with constructor-injection refactor step. Visibility bumps captured § B.5 with per-case rationale. |
| D — shape count balloons past 4 | INFORMATIONAL | 10 distinct shapes enumerated, but only 3 are in scope per STOP-C. Operator-default scope holds (no expansion). |
| E — quad-sweep (e) axis surfaces other god-class gaps without pre-approval | NOT BUNDLED | BackendSyncService, DataImporter/Exporter surfaced as candidates, NOT auto-included per memory #30. |
| F — fixture setup itself needs extraction | NOT FIRED | Pure-unit tests on companion-object methods need zero fixtures. |

### D. Premise verification (memory #22 bidirectional)

| Claim | Verified | How |
|-------|----------|-----|
| D.1 — PR #1118 audit verdicted YELLOW | OPERATOR-ASSERTED | PR #1118 audit doc `SYNCSERVICE_GOD_CLASS_REFACTOR_AUDIT.md` is **prospective** (not yet committed to main; `ls docs/audits/ \| grep -i SYNCSERVICE` returned empty). Operator framing is the source of truth for Slice 0 scope; Phase 2 of the refactor will land that audit. |
| D.2 — 8 mapper tests + 8 emulator scenarios is current state | TRUE | A.3 enumerates 12 unit + 8 androidTest files; the "8 + 8" headcount is approximate but the qualitative gap (no dispatch tests) is the operative claim and is verified |
| D.3 — no direct dispatch tests exist | TRUE | A.3 enumeration |
| D.4 — 4+ dispatch shape categories | UNDERSTATED | A.4 finds 10 shapes; 3 in scope (pure tables) |
| D.5 — `SyncMapperTest` follows pure-unit convention | TRUE | Read of file confirms JUnit + plain object construction, no MockK |

### E. Phase 2 scope

**Files touched:**
- `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt` — companion-object addition + 1 inline-when replacement at line 1220 + 1 inline-when replacement at line 3707. Net delta: +20–30 LOC, –35 LOC inline `when` removed, ~–15 LOC net.
- `app/src/test/java/com/averycorp/prismtask/data/remote/SyncServiceDispatchTest.kt` — NEW. ~85 test cases, ~200 LOC.

**Single-PR-per-branch (memory #30):** Slice 0 is one PR on `claude/add-syncservice-dispatch-tests-saCxi`. No fan-out.

### F. Deferred (NOT auto-filed per memory #30)

The following are **surfaced for the operator** but **not auto-created** as new PRs/timeline items:

1. **Shapes #1–#5, #10 (behavioral pull/push/backfill coverage).** Requires `FirebaseFirestore` constructor injection — a structural production change. Re-trigger when surface-axis refactor lands (it will need to inject Firestore anyway to split into Pull/Push coordinators).
2. **Drift bug candidate** — `milestone` missing from `collectionNameFor` at line 1277 (push entry). Currently dormant (milestones may be pull-only). Worth a 1-line check during refactor.
3. **`syncableTables` (shape #9) coverage** — 19-entry list inside `restoreCloudIdFromMetadata`. Mirrors a migration constant per inline comment; testing requires test-doubling the migration constant. Re-trigger if migration-time backfill becomes a regression source.
4. **(e)-axis siblings** — `BackendSyncService` (720 LOC) + `DataImporter`/`DataExporter` likely have the same dispatch-table-without-tests gap. Re-trigger if either surfaces as a refactor candidate.

### G. Open questions for operator

None. Slice 0 scope, framework choice (pure-unit + companion object, no MockK or Robolectric), and visibility-bump rationale are each operator-defaults under the audit-first / CLAUDE.md conventions referenced. **Phase 2 proceeds.**

---

## Phase 2 — Implementation (proceeding)

Per § E above. Three production touches, one new test file. No DAO additions, no fixture extraction.

## Phase 3 — Verification (pre-merge per memory #16)

- ktlint + detekt: clean (assertion-only test file is trivially conformant).
- New tests: GREEN (pure functions; no I/O, no flakes).
- `SyncMapperTest` + existing sync test files: GREEN (no shared fixtures touched).
- `compileDebugAndroidTestKotlin`: GREEN (no androidTest changes).
- AVD pair test / cross-device emulator suite: NOT REQUIRED (test-only PR, no production behavior change).

Bidirectional verify after commit lands: `git log -p` confirms tests landed; `find` shows new test file; ktlint/detekt-free new file passes static gates.

## Phase 4 — Session summary

See § "Session summary handoff block" emitted as a separate response after the implementation PR is opened.

---

## Anti-patterns explicitly avoided

- Did NOT auto-file Phase 1 findings as new timeline items (drift findings + (e)-axis observations live here, not as new PRs).
- Did NOT widen scope to other god-classes' test gaps (BackendSyncService, DataImporter surfaced only).
- Will NOT ship Phase 2 without this audit doc landing first (single audit-doc-then-impl commit pair acceptable per audit-first).
- Did NOT duplicate `SyncMapperTest` coverage (mappers pin serialization, this PR pins dispatch).
- No new DAO classes (Slice 0 is test-only on the prod side except for visibility hoists).
- Did NOT use `@VisibleForTesting` outside companion-object hoists with captured rationale (§ B.5).
- Did NOT shift to Robolectric (STOP-C boundary holds; behavioral coverage deferred).
