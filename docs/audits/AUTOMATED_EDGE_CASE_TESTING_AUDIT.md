# Automated Edge-Case Testing Infrastructure — Audit

**Status:** Phase 1 complete (single-pass per memory `feedback_skip_audit_checkpoints.md`).
**Scope:** Three test infrastructure approaches (property-based / snapshot / clock simulation) × four edge-case classes (sync / date-time / UI state / background tasks) = 12-cell leverage matrix, compressed to a Tier A/B/C roadmap for Phase F (May 15 – Jun 4) and post-launch G.0.
**Motivation:** Two recent P0 episodes traced to happy-path-only coverage — sync constraint fan-out (#851/#853) and SoD boundary regression (#798).
**Author:** audit-first agent, 2026-04-28.

## TL;DR — audit-first reframe (matches "Less likely 20%" outcome)

The matrix collapses harder than the prompt expected. Two of the three "approaches" are **already partially implemented in main**; the audit's job is to extend coverage of existing infrastructure rather than wire new frameworks:

- **Clock simulation harness** is **substantially EXISTS**: `core/time/TimeProvider` is Hilt-bound (`SystemTimeProvider`) and used in 7 unit tests; `LocalDateFlowTest` already drives DST + boundary cases via `TestScope.currentTime`. The legacy `util/DayBoundary` (millis-based) is the remaining gap.
- **Sync harness** is **substantially EXISTS**: `SyncTestHarness` (two-FirebaseApp split, real Firebase emulator) already powers 9 instrumented scenario tests including concurrent-delete, last-write-wins, FK integrity, multi-device streaks, cloud-id orphan healing. Adversarial paths are hand-picked, not state-machine-fuzzed.
- **Snapshot UI testing** is **NOT PRESENT**. Paparazzi / Roborazzi do not appear in any `build.gradle.kts`. Compose UI is exercised today only by interaction-shaped smoke tests under `androidx.compose.ui:ui-test-junit4`.
- **Property-based / fuzz testing** is **NOT PRESENT**. No Kotest property, no Hedgehog, no `@ParameterizedTest`, no `kotlin-faker`, no `TestParameterInjector`.

Tier A (Phase F leverage): **2 cells** — sync state-machine fuzz on top of `SyncTestHarness`, and `util/DayBoundary` migration to `TimeProvider`. Both are extensions of existing infrastructure, neither requires a new test framework. Tier C (post-launch hardening): Paparazzi UI snapshot setup as the only candidate for net-new framework adoption.

Estimated Phase 2 fan-out: **3 PRs** (1 setup + 2 first-batch), **~6 days wall-clock**, all complete by May 8 — comfortably inside the May 15 Phase F kickoff.

---

## Item 1 — Inventory of existing test infrastructure

### Frameworks present in `app/build.gradle.kts`

| Library | Version | Scope | Notes |
|---|---|---|---|
| `junit:junit` | 4.13.2 | test | core test runner |
| `kotlinx-coroutines-test` | 1.9.0 | test + androidTest | virtual time scheduler |
| `app.cash.turbine` | 1.1.0 | test | Flow assertion |
| `io.mockk:mockk` / `mockk-android` | 1.13.13 | both | mock framework |
| `org.robolectric:robolectric` | 4.13 | test | JVM Android |
| `androidx.work:work-testing` | 2.9.1 | both | WorkManager test driver |
| `androidx.room:room-testing` | 2.8.4 | both | migration test helpers |
| `androidx.compose.ui:ui-test-junit4` | BOM 2024.12.01 | androidTest | Compose interaction tests |
| `dagger.hilt-android-testing` | 2.59.2 | androidTest | Hilt graph in instrumented |

### Frameworks NOT present (verified by grep across all `*.kt` and `*.gradle*`)

- `io.kotest:kotest-property` / `kotest-runner-junit5` — **not present**
- `app.cash.paparazzi` / `com.airbnb.android:paparazzi` — **not present** (zero matches in `build.gradle*`)
- `dev.zacsweers.metro:roborazzi` / `com.github.takahirom.roborazzi` — **not present**
- `qa.hedgehog:hedgehog` — **not present**
- `kotlinx-datetime` (any artifact) — **not present**; the codebase uses `java.time` exclusively
- `com.google.testparameterinjector:test-parameter-injector` — **not present**
- `io.github.serpro69:kotlin-faker` — **not present**

### Clock injection pattern — **SUBSTANTIALLY EXISTS**

`app/src/main/java/com/averycorp/prismtask/core/time/TimeProvider.kt`:

```kotlin
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
```

Bound via `di/TimeModule.kt`. Production callers using injection today:

| Caller | File |
|---|---|
| `LocalDateFlow` | `core/time/LocalDateFlow.kt` |
| `core/time/DayBoundary` (object, no DI; helper takes `ZoneId` arg) | `core/time/DayBoundary.kt` |
| `NaturalLanguageParser` (defaults to `SystemTimeProvider()` + accepts override) | `domain/usecase/NaturalLanguageParser.kt:57` |
| `ProfileAutoSwitcher` | `domain/usecase/ProfileAutoSwitcher.kt:14` |

Tests using `TimeProvider` (substituting an anonymous object backed by `TestScope.currentTime`):

1. `core/time/LocalDateFlowTest.kt` — DST spring-forward + boundary re-emission (anchor pattern)
2. `core/time/DayBoundaryTest.kt`
3. `domain/usecase/MedicationStatusUseCaseTest.kt`
4. `domain/usecase/DailyEssentialsDayBoundaryFlowTest.kt`
5. `ui/screens/today/TodayDayBoundaryFlowTest.kt`
6. `ui/screens/tasklist/TaskListDayBoundaryFlowTest.kt`
7. `ui/screens/medication/MedicationTodayDateRefreshTest.kt`

The `LocalDateFlowTest.virtualClock(scope, base)` helper is the canonical pattern: an anonymous `TimeProvider` whose `now()` returns `base.plusMillis(scope.testScheduler.currentTime)`. `advanceTimeBy` advances both the suspended `delay(...)` inside the flow body and the provider's `now()` reading coherently.

### Sync test harness — **SUBSTANTIALLY EXISTS**

`app/src/androidTest/java/com/averycorp/prismtask/sync/SyncTestHarness.kt` (~282 lines).

- Two-FirebaseApp model: default app (Hilt-visible, what production `SyncService` uses) plus a named `"deviceB"` app, both routed at the same Firebase Emulator Suite
- `signInBothDevicesAsSharedUser`, `setDeviceAOffline/Online`, `writeAsDeviceB`, `deleteAsDeviceB`, `firestoreCount`, `firestoreAllDocs`, `waitFor(predicate)`
- 11 known subcollections cleaned between tests
- Gated by `Assume.assumeTrue(BuildConfig.USE_FIREBASE_EMULATOR)` — runs only under `.github/workflows/android-integration.yml`

Scenario tests built on top of the harness via `SyncScenarioTestBase` (`@HiltAndroidTest` + injected `database`, `syncService`, `authManager`, `taskRepository`, `habitRepository`, `projectRepository`):

| Test | What it exercises |
|---|---|
| `Test7OfflineEditReconnectTest` | offline edit reconnect resolution |
| `Test8MultiDeviceStreakSyncTest` | habit streak convergence |
| `Test9ConcurrentEditLastWriteWinsTest` | concurrent edit LWW |
| `Test10ConcurrentDeleteTest` | concurrent delete vs. offline edit (delete wins) |
| `Test11OfflineDuringRemoteWriteTest` | A offline while B writes |
| `Test14RapidCreateDeleteNoOrphanTest` | rapid op sequences (closest to property-based shape) |
| `HabitCompletionStaleParentMetadataTest` | stale parent FK shape |
| `MedicationCrossDeviceConvergenceTest` | medication LWW + slot convergence (the #851/#853 surface) |
| `SyncMapperCloudIdTest`, `CloudIdOrphanHealer*Test` (4 files) | cloud-id orphan healing |

### Compose UI test surface

`androidTest/.../smoke/`: 16 smoke files including `NavigationSmokeTest`, `TodayScreenSmokeTest`, `TaskEditorSmokeTest`, `TemplatesSmokeTest`, `ViewsSmokeTest`, `EdgeCaseEmptyStateTest`, `EdgeCaseRotationTest`, `EdgeCaseOfflineTest`, `OfflineEdgeCaseSmokeTest`, `BugReportSmokeTest`. All interaction-shaped (`onNodeWithText`, `performClick`); none capture pixel snapshots.

`captureToImage` / Paparazzi-style usage: zero matches outside `data/diagnostics/ScreenshotCapture.kt` (production bug-report feature, unrelated to test infra).

### Background-task test surface

`work-testing 2.9.1` is in `dependencies { … }` of both `test` and `androidTest`. Workers tested today:

- `WeeklyHabitSummaryWorkerTest` (test/, JVM)
- `EscalationSchedulerTest` (test/)
- `ReminderSchedulerQuietHoursTest` (test/)
- `ReminderSchedulerAlarmInstrumentedTest` (androidTest/)
- `ReminderBroadcastInstrumentedTest` (androidTest/)
- `NotificationPermissionInstrumentedTest`, `NotificationChannelsInstrumentedTest`, `HabitSuppressionPersistenceInstrumentedTest` (androidTest/)

### Parameterized / property-test patterns

Zero matches for `@ParameterizedTest`, `@TestFactory`, `forAll`, `forall`, `Parameterized::class`. `MedicationSlotEntityTest` (PR #830) is a plain JUnit 4 class with 11 individual `@Test` methods — table-driven by hand, not framework-driven.

---

## Item 2 — Test surface gap analysis per edge-case class

### Sync layer

**Strong baseline.** 9 hand-rolled scenarios cover: concurrent delete, offline edits, FK integrity (`HabitCompletionStaleParentMetadataTest`), last-write-wins on the medication PULL surface, cloud-id orphan healing, rapid create/delete. `Test14RapidCreateDeleteNoOrphanTest` is the closest existing analog to a property-based shape.

**Gaps:**

- No state-machine fuzzer — every scenario hand-picks an op sequence. The #851/#853 P0 was a constraint violation under a specific op-ordering combination that no scenario was written for; a fuzzer that walks random sequences over `(insert / update / delete) × (medications / slots / doses / habit_completions)` and asserts FK + UNIQUE + cross-device convergence holds at every step would have surfaced it pre-merge.
- `Test14` covers rapid op sequences for a single entity type only; cross-entity op sequences (e.g., delete medication while a slot edit is in-flight from B, then a dose insert from A targeting the soon-to-be-orphan slot) are not covered.
- All scenarios are `runBlocking` with explicit `pushLocalChanges()` / `pullRemoteChanges()` — real-time-listener interleavings between push and pull are not exercised by design (memory `feedback_firestore_doc_iteration_order.md` documents why).

**Severity:** HIGH. Two of the last three sync-layer P0s (#851 + #853) match exactly the shape a state-machine fuzzer catches and a hand-picked scenario misses.

### Date / time boundaries

**Strong baseline.** `core/time/DayBoundary` + `LocalDateFlow` are fully unit-tested with virtual time. DST spring-forward exists (`LocalDateFlowTest.observe_dstSpringForward_handlesBoundaryWithoutFlapping`). Per-screen day-boundary flow tests exist for Today, TaskList, DailyEssentials, Medication. `QuietHoursDstTest` covers the notification side.

**Gaps:**

- **`util/DayBoundary` (legacy millis-based) does not use `TimeProvider`.** Its `now: Long = System.currentTimeMillis()` default means call sites (widgets, `DailyResetWorker`, several repositories per the Item 1 grep results) cannot be deterministically tested without monkey-patching the system clock. The `core/time/DayBoundary` KDoc says "kept for back-compat with hour-only callers and will be migrated incrementally" — the migration is the unfinished half.
- DST fall-back (the ambiguous-hour case where `02:30` happens twice on the same date) is not covered. Spring-forward is — that's the easier direction.
- Leap-day (`Feb 29`) and year-boundary (`Dec 31 23:59 → Jan 1 04:00 SoD`) cases are not exercised. Low likelihood, but the SoD shape makes year boundaries non-trivial.
- Timezone *changes mid-session* (user flies, OS updates `ZoneId.systemDefault()`) are not exercised.

**Severity:** MEDIUM. PR #798 closed the most expensive class. Remaining gaps are second-order — the legacy util/DayBoundary call sites are the ones most likely to harbor PR-#798-shaped bugs.

### UI state combinations

**Smoke baseline.** 16 smoke tests cover happy-path interactions. Empty-state, rotation, offline edge cases have dedicated smoke files.

**Gaps:**

- No pixel-stable visual regression check. A theme-token refactor or accent-color preference change can shift rendering without breaking a single `onNodeWithText` assertion.
- "Rare data shapes that break rendering" are unenumerated: e.g. 100+ medication slots in MedicationScreen, a 200-character medication name, a project with zero tasks vs. 10,000, a habit with a 365-day streak rendered in the contribution grid, NLP-derived recurrence rules with all 7 weekdays plus a long "ends after N occurrences" tail.
- Loading-state and error-state composables are not snapshot-asserted; smoke tests skip them (`waitUntil` for the post-loading state).

**Severity:** LOW-to-MEDIUM. No P0 traces to UI-state-combination regressions; smoke tests are sufficient for Phase F's scope. Snapshot value is mostly Phase G post-launch.

### Background tasks

**Adequate baseline.** `WorkManager.work-testing` driver is in dependencies. Reminder scheduling, escalation chains, quiet-hours deferral, and weekly habit summary are unit-tested. Notification channels and permission flows have instrumented tests.

**Gaps:**

- `OverloadCheckWorker`, `DailyResetWorker`, the AI batch worker (if any), and widget `WidgetUpdateManager` periodic refresh are not exercised under simulated time advances. The `work-testing` `TestDriver.setAllConstraintsMet()` + `setPeriodDelayMet()` API would let us assert "after 24h, the worker fires and Today rolls over" deterministically.
- Widget update path (`WidgetDataProvider` → Glance state) is unit-tested for data shape but not under "Glance pinned to old state across SoD boundary" simulations.

**Severity:** LOW. No P0 traces to background-task regression.

---

## Item 3 — Approach × class leverage matrix

Per cell: **GREEN** (high leverage, low cost) / **YELLOW** (leverage exists but cost or framework mismatch) / **RED** (does not apply or dominated by another approach) / **EXISTS** (already covered).

|                      | **Sync**                                          | **Date / Time**                                   | **UI state**                                      | **Background**                                    |
|----------------------|---------------------------------------------------|---------------------------------------------------|---------------------------------------------------|---------------------------------------------------|
| **Property / fuzz**  | **GREEN** — state-machine over op sequences hits the #851/#853 shape directly | YELLOW — table-driven boundary cases beat property tests for time | YELLOW — Compose state fuzzing exists (compose-test-state) but cost > value | YELLOW — WM events are not state-machine-shaped |
| **Snapshot**         | RED — sync isn't render-shaped                    | RED — time isn't render-shaped                    | **GREEN** — Paparazzi pins themed/scaled rendering | RED — workers don't render                         |
| **Clock harness**    | YELLOW — useful for "23:59:59 push" but subsumed by sync property-based generators that pick timestamps | **EXISTS** — `TimeProvider` already wired; remaining work is migrate `util/DayBoundary` call sites | YELLOW — subsumed by snapshot once snapshots exist | YELLOW — `WorkManager.TestDriver` already exists; expand coverage |

### Per-cell rationale

- **Property × Sync (GREEN):** State-machine generator over `(insert / update / delete) × (entity-type) × (device A / device B)` runs N random sequences, asserting FK integrity, UNIQUE invariants, and cross-device convergence after each step. Builds on `SyncTestHarness`; no new test framework needed beyond a small generator helper. Hits the exact P0 shape from #851/#853.
- **Property × Time (YELLOW):** DST/leap-day/SoD boundaries are a small finite set of named cases. Table-driven beats randomly-generated for time because the failure modes are at specific named instants, not in the generic distribution.
- **Property × UI (YELLOW):** Compose state-fuzzing (semantics-tree walks, random gesture sequences) is a research-tier investment with low predictable Phase F leverage.
- **Property × Background (YELLOW):** WorkManager event sequences are constraint-driven, not state-machine-driven. Existing `TestDriver` coverage gaps are better closed by hand-picked tests.
- **Snapshot × Sync/Time/Background (RED):** Not render-shaped.
- **Snapshot × UI (GREEN, but Tier C):** Paparazzi pins Compose UI rendering. Cheap to wire (one Gradle plugin, headless renders, golden PNGs in repo). Phase F leverage is low because testers exercise interaction shapes that Paparazzi doesn't capture.
- **Clock × Time (EXISTS):** `TimeProvider` is wired, 7 tests already use the virtual-clock pattern. Gap is `util/DayBoundary` legacy call sites.
- **Clock × Sync (YELLOW):** Sync timestamps are `System.currentTimeMillis()` in places; threading `TimeProvider` through `SyncMetadata` would be a non-trivial refactor for marginal value.
- **Clock × UI (YELLOW):** Clock is needed for date-rendering snapshots, but only after Snapshot×UI lands; until then, the existing flow tests cover the surface.
- **Clock × Background (YELLOW):** `WorkManager.TestDriver` is the right tool here, not `TimeProvider`.

### Compressed matrix

Of 12 cells, **two** are GREEN-and-NEW for Phase F:

1. **Property × Sync** — state-machine fuzz over `SyncTestHarness`
2. **Clock × Time** (extension) — migrate `util/DayBoundary` call sites onto `TimeProvider`, write deterministic widget/worker boundary tests

One cell is GREEN-but-Tier-C:

3. **Snapshot × UI** — Paparazzi setup, defer to G.0

Remaining 9 cells: existing infrastructure, table-driven cases, or low-leverage.

---

## Item 4 — Tier A / B / C prioritization

### Tier A — Phase F leverage (catches regressions in May-Jun code)

| # | Item | Library | Setup time | Tests added |
|---|---|---|---|---|
| A1 | **Sync state-machine fuzzer** on `SyncTestHarness` | None (hand-rolled generator + JUnit 4 `@Test` with seeded `Random`) | 1 day | 5–10 fuzz scenarios, 100–1000 ops each, deterministic seeds |
| A2 | **Migrate `util/DayBoundary` to `TimeProvider`** | None (existing infra) | 1 day | 10–15 widget/worker boundary tests under virtual time |

**Why no new framework for A1:** Kotest-property's value is generators + shrinking. Shrinking is hard to retrofit onto Firebase emulator I/O — the harness's `cleanupFirestoreUser()` + `clearAllTables()` is already the "minimal failing case" workflow. A hand-rolled generator with a seeded `Random` and `assertEachStep()` after every op gives 80% of the leverage at 10% of the integration risk.

**Why A2 is Tier A despite "EXISTS":** the `TimeProvider` is wired, but ~6 production call sites on `util/DayBoundary` (per the Item 1 grep) currently pass `System.currentTimeMillis()` and are untestable without monkey-patching. Phase F testers will hit widget rollover and `DailyResetWorker` boundary bugs unless these call sites move to the injected clock. This is *finishing* the SoD boundary sweep that PR #798 started — not new infrastructure.

### Tier B — surfaces latent bugs in pre-existing code (medium value, no Phase F guarantee)

| # | Item |
|---|---|
| B1 | Table-driven `core/time/DayBoundary` cases for DST fall-back, leap day, year boundary |
| B2 | Cross-entity sync fuzzer (medication + slot + dose + habit-completion in same run) — extension of A1 |
| B3 | `WorkManager.TestDriver` coverage for `OverloadCheckWorker`, `DailyResetWorker`, widget refresh |

### Tier C — post-launch hardening (G.0)

| # | Item |
|---|---|
| C1 | Paparazzi setup + golden-image baseline for 10 hot screens (Today, MedicationScreen, HabitsList, EisenhowerMatrix, BalanceBar, …) |
| C2 | Compose state-fuzz exploration (research; no commitment) |
| C3 | `kotlinx-datetime` migration (from `java.time`) — non-trivial, no Phase F leverage |

### Implementation sequence

**Sprint 1 (May 1–4):** A1 setup PR — generator helper + 1 trivial fuzz scenario, gated CI workflow stays green.
**Sprint 2 (May 5–6):** A1 first-batch — 5–10 fuzz scenarios on the medication + slot surface that motivated #851/#853.
**Sprint 3 (May 7–8):** A2 — migrate `util/DayBoundary` call sites + write the deterministic boundary tests.

All complete by May 8, well before the May 15 Phase F kickoff. Tier B + C deferred to G.0.

---

## Item 5 — Risk classification per Tier A item

| Item | Implementation risk | Maintenance risk | False-positive risk | Lock-in risk | Mitigation |
|---|---|---|---|---|---|
| **A1: Sync fuzzer** | MEDIUM — Firebase emulator startup is slow (~30s); fuzz scenarios with 1000 ops × 30s emulator init = CI time pressure | MEDIUM — every new sync entity (when added) must extend the generator's op-type set, or it goes uncovered | LOW-MEDIUM — Firestore iteration order is non-deterministic (memory `feedback_firestore_doc_iteration_order.md`); assertions must be on convergence shape, not doc order | LOW — hand-rolled generator has no library lock-in; if it underperforms, swap in Kotest-property without re-doing the harness layer | Cap fuzz length at ~100 ops/test, run 5–10 scenarios in the existing `android-integration` workflow; document the "convergence-shape only" assertion rule in the test base class |
| **A2: Legacy DayBoundary migration** | LOW — call sites are well-localized (~6 files per Item 1); existing `core/time/DayBoundary` is the migration target | LOW — strictly subtractive after migration; deletes the legacy util once last call site moves | LOW — virtual-clock tests are deterministic | LOW — no library involved | Land call-site migration in a single PR with the deterministic tests; do not delete `util/DayBoundary` immediately, mark `@Deprecated` for one release cycle |

**Demotions:** none. Both Tier A items are risk-acceptable. The fuzzer's main risk is CI duration; this is mitigated by capping fuzz length, not by demoting.

**Excluded from Tier A:** Paparazzi (C1) was considered for Tier A given low setup cost. Demoted because (a) Phase F testers will hit interaction-shaped issues a snapshot wouldn't catch, and (b) golden-image baselines are noisy with font rendering / OS rendering differences across emulator versions, which would compete for triage attention against real Phase F bugs.

---

## Item 6 — Phase 2 fan-out proposal

**3 PRs total. ~6 days wall-clock. Squash-merge with auto-merge per `feedback_push_without_asking.md`.**

### PR-A1-setup — Sync fuzzer harness

- Branch: `feat/edge-case-sync-fuzz-setup`
- Worktree: `../prismTask-edge-case-sync-fuzz-setup`
- Files: `app/src/androidTest/java/com/averycorp/prismtask/sync/fuzz/SyncFuzzGenerator.kt` (new, ~150 lines), `app/src/androidTest/java/com/averycorp/prismtask/sync/fuzz/SyncFuzzScenarioBase.kt` (new, ~80 lines), `app/src/androidTest/java/com/averycorp/prismtask/sync/fuzz/Fuzz01TaskOpSequenceTest.kt` (one trivial fuzz scenario, ~50 lines, seeded `Random(42)`)
- CI: gated by existing `USE_FIREBASE_EMULATOR=true` in `.github/workflows/android-integration.yml`; no new workflow
- Verification: emulator-side green run + a fixed-seed assertion that the generator produces the same op sequence twice (regression-gate)

### PR-A1-batch — Medication / slot fuzz scenarios

- Branch: `feat/edge-case-sync-fuzz-medication`
- Worktree: `../prismTask-edge-case-sync-fuzz-medication`
- Files: 5–8 new fuzz scenario files under `sync/fuzz/`, each ~60–80 lines, each anchored to a distinct seed
- Targets: `(insert med + slot + dose) × (delete med while slot in-flight) × (concurrent edit)` op-set, derived from #851/#853 motivation
- Verification: every scenario must converge inside the `waitFor` timeout under emulator network conditions

### PR-A2 — `util/DayBoundary` migration to `TimeProvider`

- Branch: `feat/edge-case-clock-util-day-boundary-migration`
- Worktree: `../prismTask-edge-case-clock-util-day-boundary-migration`
- Files: ~6 `app/src/main/.../*.kt` call-site updates (widget data provider, `DailyResetWorker`, repositories using `currentLocalDateString`), `util/DayBoundary` marked `@Deprecated`, ~10–15 new unit tests under `app/src/test/.../widget/` and `app/src/test/.../workers/`
- Per memory `feedback_repro_first_for_time_boundary_bugs.md`: write the structural repro test FIRST, then migrate
- Verification: existing `DayBoundaryTest` stays green; new tests use `virtualClock(scope, base)` pattern from `LocalDateFlowTest`

### Bundling decision

Per `feedback_audit_drive_by_migration_fixes.md` + the fan-out bundling rule: 3 PRs not 1 bundle. Setup vs. first-batch is a clean split (the setup PR is verifiable in isolation against a green CI before fuzz scenarios pile on). A2 is unrelated scope (time vs. sync) and reviews differently.

### Per-PR hygiene

- No `[skip ci]` in commit messages (`feedback_skip_ci_in_commit_message.md`)
- Trailing newline on `CHANGELOG.md` if touched
- Branch from latest `main` with fresh `git pull --rebase`
- Worktree teardown paired with merge (`feedback_use_worktrees_for_features.md`)
- `gh pr merge --auto --squash` per PR
- After A2 lands, schedule a `/loop` follow-up to check that no `util.DayBoundary` import survives (regression-gate via `grep`)

---

## Improvement table — wall-clock-savings ÷ implementation-cost

Sorted by leverage:

| Rank | Item | Wall-clock savings (Phase F bug-day cost avoided) | Cost (engineer-days) | Ratio | Tier |
|---|---|---|---|---|---|
| 1 | A1 setup + first-batch (sync fuzzer) | ~3 days (one P0-shape regression caught pre-merge) | 3 | 1.0 | A |
| 2 | A2 (`util/DayBoundary` migration) | ~1.5 days (one widget/worker boundary regression caught) | 1.5 | 1.0 | A |
| 3 | B1 (table-driven DST fall-back, leap day, year boundary) | ~0.5 day (low likelihood, big blast radius) | 1 | 0.5 | B |
| 4 | B3 (`WorkManager.TestDriver` coverage) | ~0.3 day | 1 | 0.3 | B |
| 5 | C1 (Paparazzi setup) | ~0 in Phase F; ~3 days/year in G.0 | 2 (setup) + 0.5/screen × 10 = 7 | n/a Phase F; 0.4/year G.0 | C |
| 6 | C2 (Compose state-fuzz) | unknown | 5+ research | unranked | C |

Tier A items dominate Phase F leverage. The audit confirms the prompt's "Less likely 20%" expected outcome: **the matrix is more compressed than expected because TimeProvider exists, and the highest-leverage move is to extend existing infrastructure rather than wire a new framework.**

---

## Anti-pattern list — flagged but not necessarily fixed

- **Two `DayBoundary` types in two packages** (`core/time/DayBoundary` + `util/DayBoundary`) — confusing for new contributors. A2 deprecates the legacy one; full deletion is one release cycle later.
- **`NaturalLanguageParser` defaults `timeProvider = SystemTimeProvider()`** — this is a constructor-arg default, not a Hilt binding. Slightly fragile (forgetting the override in tests is silently allowed) but acceptable for now.
- **Sync scenario tests are runBlocking with explicit push/pull calls** — by-design (memory `feedback_firestore_doc_iteration_order.md`), but means real-time-listener-interleaved bugs are not exercised. Out of scope for this audit; would require a different harness.
- **No CI gate on `util.DayBoundary` import count** — once A2 lands, a one-line grep regression-gate would prevent reintroducing legacy callers. Add to A2 PR.

---

## Phase F leverage tier summary

- **Tier A (high Phase F leverage, this audit's recommendation):** A1 + A2, 3 PRs, ~6 days wall-clock, complete by May 8.
- **Tier B (medium value, deferred to G.0):** B1, B2, B3.
- **Tier C (post-launch hardening, no Phase F leverage):** C1 (Paparazzi), C2 (state-fuzz research), C3 (kotlinx-datetime migration).

Audit-first track record stays at 13 of 14 if this audit's reframe lands cleanly: the matrix compressed from 12 cells to 2 GREEN-and-NEW cells, both extensions of existing infrastructure. No new test framework is recommended for Phase F.

---

## References

- **PR #798** — Medication SoD boundary fix + `LocalDateFlow` / `useLogicalToday` (the regression-gate template this audit's A2 extends)
- **PR #830** — Medication slot system + 11 hand-table-driven unit tests (existing param-test shape)
- **PR #851 / #853 / #855** — Sync constraint fan-out (the bugs that motivate A1)
- **PR #859** — Connected-tests stabilization audit, single-pass shape (this audit follows the same shape)
- **`docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md`** — `LocalDateFlow` / `TimeProvider` rollout history (cited in `LocalDateFlow.kt` KDoc)
- **`feedback_skip_audit_checkpoints.md`** — single-pass audit per memory rule
- **`feedback_firestore_doc_iteration_order.md`** — sync assertion shape constraint (governs A1 design)
- **`feedback_repro_first_for_time_boundary_bugs.md`** — A2 must write the repro test first
- **`feedback_use_worktrees_for_features.md`** — every Phase 2 PR uses a worktree, paired teardown
- **`feedback_skip_ci_in_commit_message.md`** — no `[skip ci]` in commit titles or bodies
