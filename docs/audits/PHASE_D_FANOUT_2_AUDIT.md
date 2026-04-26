# Phase D Fan-Out 2 — Pre-Implementation Audit

**Date:** 2026-04-26
**Bundle owner:** akarlin3
**Pattern:** Audit-first, fan-out PRs (mirrors Phase D bundle PRs #780–#786)
**Worktree state at audit time:** branched from `origin/main` at `6a677ea5` (post PR #797 v1.7.0 bump). `cb12afe3..6a677ea5` is the delta past local `main` at audit start.

**Scope:** Two items shipped as a fan-out CC session:

1. HiltTestRunner.onStart audit — surfaced by PR #791 (Firebase emulator routing). Sweep the rest of `PrismTaskApplication.onCreate` for parity gaps.
2. Connected-tests flake stabilization — PR #761 cited ~22% flake rate. Blocks Option 3 lockdown CI hardening (needs <5%/20-run benchmark).

**TL;DR — both items STOP, but for different reasons:**
- **Item 1: STOP — NO WORK NEEDED.** Of 17 hooks in `PrismTaskApplication.onCreate`, the one that mattered (Firebase emulator routing) is already mirrored via PR #791. The remaining 14 should explicitly NOT be mirrored (cross-test pollution risk), 2 are masked by per-test seeders, and the rest are covered via Hilt module DI which runs identically under both Application classes. Optional: a sentinel-style audit test (~80 LoC) to catch future drift.
- **Item 2: STOP — WRONG PREMISE.** 2 of 3 named hotspots have **zero failures** in the measured 28-run window. The actual flake-rate driver is a real (non-flake) test failure in `Migration59To60Test` shipped by PR #773 (3 consecutive failures), plus the `cross-device-tests` AVD boot reliability issue already flagged in `PHASE_D_BUNDLE_AUDIT.md` Item 1. Fan-out as prompted would not move the flake rate.

---

## Item 1 — HiltTestRunner.onStart sweep

### 1.1 Premise verification

Premise as stated: "@HiltAndroidTest substitutes HiltTestApplication for PrismTaskApplication, so anything in `PrismTaskApplication.onCreate` must be mirrored in `HiltTestRunner.onStart` or it silently doesn't run under instrumented tests."

The premise is **mechanically correct** but the implication that "many other gaps must exist beyond the Firebase emulator routing PR #791 caught" is **wrong**. Most `PrismTaskApplication.onCreate` work falls into three categories that should NOT be mirrored.

Files audited (read end-to-end):
- `app/src/main/java/com/averycorp/prismtask/PrismTaskApplication.kt` (17 hooks)
- `app/src/androidTest/java/com/averycorp/prismtask/HiltTestRunner.kt` (5 actions)
- `app/src/main/java/com/averycorp/prismtask/data/diagnostics/MigrationInstrumentor.kt` (Hilt-DI-injected, fires from both Application classes)
- `app/src/androidTest/java/com/averycorp/prismtask/smoke/TestDatabaseModule.kt:14-19` (replaces `DatabaseModule` with in-memory Room)
- `app/src/androidTest/java/com/averycorp/prismtask/smoke/TestNetworkModule.kt:26-30,45-48` (replaces `NetworkModule` with `FakePrismTaskApi` + mocked `CalendarBackendApi`)
- `app/src/androidTest/java/com/averycorp/prismtask/sync/SyncTestHarness.kt:243,255,267` (only place tests touch `FirebaseApp.initializeApp` — for the secondary "deviceB" instance)
- `app/src/main/java/com/averycorp/prismtask/notifications/BootReceiver.kt:14-63` (where `HabitReminderScheduler.rescheduleAll`, `MedicationReminderScheduler.rescheduleAll`, `MedicationIntervalRescheduler.rescheduleAll`, `ReminderScheduler.rescheduleAllReminders` actually fire — NOT from `Application.onCreate`)
- `app/src/main/java/com/averycorp/prismtask/MainActivity.kt:160-166` (where `NotificationHelper.createNotificationChannel` actually fires — NOT from `Application.onCreate`)

### 1.2 Current state — full inventory

#### `PrismTaskApplication.onCreate()` — 17 hooks

| # | Hook | Line(s) | Notes |
|---|---|---|---|
| 1 | `super.onCreate()` | 87 | Standard Application init |
| 2 | `configureFirebaseEmulator()` — routes Firestore + Auth to local emulator when `BuildConfig.USE_FIREBASE_EMULATOR`; disables Firestore persistence | 88, 156-180 | **Mirrored in HiltTestRunner via PR #791** |
| 3 | `configureCrashlytics()` — `FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)` | 89, 133-141 | Production-only telemetry |
| 4 | `MigrationInstrumentor.flushPending(this)` — drains buffered Room migration events to Firebase Analytics + Crashlytics custom keys | 95-99 (call), `MigrationInstrumentor.kt:115-122` (impl) | Belt-and-braces drain; primary flush is via `RoomDatabase.Callback.onOpen` injected through Hilt |
| 5 | `scheduleAutoArchive()` — `WorkManager.enqueueUniquePeriodicWork("auto_archive", KEEP, AutoArchiveWorker)` every 24 h | 101, 357-368 | Periodic worker |
| 6 | `scheduleDailyReset()` — observes `taskBehaviorPreferences.getDayStartHour()` and re-schedules `DailyResetWorker` whenever the hour changes (long-lived `appScope.launch { ...collectLatest }`) | 102, 242-248 | Continuous flow collection |
| 7 | `scheduleNotificationWorkers()` → `notificationWorkerScheduler.applyAll()` — applies user toggles to daily briefing, evening summary, weekly summary, overload check, re-engagement workers (UPDATE policy inside scheduler) | 103, 202-210 | Periodic workers |
| 8 | `scheduleWidgetRefresh()` — explicitly cancels `widget_refresh_periodic` | 104, 314-318 | No-op in tests |
| 9 | `scheduleCalendarSync()` → `calendarSyncScheduler.applyPreferences()` — UPDATE periodic worker | 105, 328-336 | Periodic worker |
| 10 | `scheduleBatchUndoSweep()` → `BatchUndoSweepWorker.schedule(this)` — daily sweep of `batch_undo_log` | 106, 218-224 | Periodic worker |
| 11 | `seedStructuralHabits()` — `schoolworkRepository.ensureHabitExists()` + `leisureRepository.ensureHabitExists()` | 116, 307-312 | Data-state seed |
| 12 | `seedBuiltInTemplates()` — `templateSeeder.seedIfNeeded()` (one-shot via `templates_seeded` DataStore flag) | 117, 231-235 | Data-state seed |
| 13 | `runBuiltInBackfill()` — `builtInHabitReconciler.runBackfillIfNeeded()` | 118, 256-260 | One-shot data migration |
| 14 | `runDriftCleanup()` — `builtInHabitReconciler.runDriftCleanupIfNeeded()` | 119, 262-266 | One-shot data migration |
| 15 | `runLifeCategoryBackfill()` — one-shot life-category classifier pass | 120, 273-277 | One-shot data migration |
| 16 | `runMedicationMigrationPasses()` — `recordPostV54LaunchIfApplicable()` + `preserveScheduleIfNeeded()` + `backfillDosesIfNeeded()` | 121, 290-296 | Post v53→v54 medication refactor |
| 17 | `startMedicationIntervalRescheduler()` — `rescheduleAll()` then `start(appScope)` Flow observer | 122, 346-355 | Long-lived; re-anchors INTERVAL alarms |
| (`workManagerConfiguration` provider, NOT in onCreate) | sets `HiltWorkerFactory` | 80-84 | Mirrored via `WorkManagerTestInitHelper` in HiltTestRunner |

Hooks **NOT present** that the prompt speculated about:
- No `FirebaseApp.initializeApp` (implicit via `google-services.json` provider)
- No `FirebaseAppCheck.installAppCheckProviderFactory`
- No `FirebaseAnalytics.getInstance(...)` (used lazily inside `MigrationInstrumentor.kt:261-263`)
- No `StrictMode` policy
- No Timber / logging plant
- No `NotificationHelper.createNotificationChannel(...)` (lives in `MainActivity.onCreate`, not `Application.onCreate`)
- No direct `HabitReminderScheduler.rescheduleAll()` / `MedicationReminderScheduler.rescheduleAll()` / `EscalationScheduler.*` calls — these only fire from `BootReceiver.onReceive`
- No `CloudIdOrphanHealer` invocation — runs from inside `SyncService.healOrphans()` post-pull (`SyncService.kt:2566-2568`)

#### `HiltTestRunner.onStart()` — 5 actions

| # | Action | Line(s) |
|---|---|---|
| 1 | `WorkManagerTestInitHelper.initializeTestWorkManager(targetContext, Configuration.Builder().build())` — replaces production `Configuration.Provider` since `HiltTestApplication` doesn't implement it | 45-48 |
| 2 | `configureFirebaseEmulator()` — Firestore + Auth `useEmulator(host, port)` + `setPersistenceEnabled(false)` when `BuildConfig.USE_FIREBASE_EMULATOR` | 49, 64-87 |
| 3 | `preGrantRuntimePermissions()` — `pm grant <pkg> POST_NOTIFICATIONS / READ_CALENDAR / WRITE_CALENDAR` via `uiAutomation.executeShellCommand` | 50, 117-134 |
| 4 | `super.onStart()` | 51 |
| 5 | (override) `newApplication(...)` — substitutes `HiltTestApplication` for `PrismTaskApplication` | 15-19 |

#### Hilt module-driven init (covers both Application + HiltTestApplication)

These initialize via DI and run identically under either Application class:

- `DatabaseModule` (replaced by `TestDatabaseModule` in androidTest with an in-memory Room DB)
- `NetworkModule` (replaced by `TestNetworkModule` with `FakePrismTaskApi`)
- `PreferencesModule`, `PreferenceSyncModule`, `BillingModule`, `CalendarModule`, `TimeModule` — production providers run as-is in tests
- `WidgetDataProvider` `@InstallIn(SingletonComponent::class)`
- The Room `RoomDatabase.Callback.onOpen` `flushPending` hook for `MigrationInstrumentor` is wired through `DatabaseModule`, so it fires whenever the DB opens regardless of Application class

### 1.3 Gap — diff with risk classification

Of the 17 `Application.onCreate` hooks, only **#2** (Firebase emulator routing) is mirrored in `HiltTestRunner.onStart`. The other 15:

| # | Hook | Classification | Risk if NOT mirrored | Risk if mirrored |
|---|---|---|---|---|
| 3 | `configureCrashlytics` | **Should explicitly NOT mirror** | None — disabled in DEBUG anyway | Phoning home from CI |
| 4 | `MigrationInstrumentor.flushPending` | **Already covered via Hilt DI** | None — in-memory DB has no migrations to flush | None |
| 5 | `scheduleAutoArchive` | **Should explicitly NOT mirror** | None — tests don't depend on it | **High** — would fire `AutoArchiveWorker` against test DB |
| 6 | `scheduleDailyReset` | **Should explicitly NOT mirror** | None | **High** — long-lived flow collector leaks across tests |
| 7 | `scheduleNotificationWorkers` | **Should explicitly NOT mirror** | None | **High** — would enqueue 5 periodic workers |
| 8 | `scheduleWidgetRefresh` (cancel) | **Mirror or skip — no-op either way** | None | None |
| 9 | `scheduleCalendarSync` | **Should explicitly NOT mirror** | None | **High** — fires against mocked `CalendarBackendApi` |
| 10 | `scheduleBatchUndoSweep` | **Should explicitly NOT mirror** | None | **Medium** — periodic worker pollution |
| 11 | `seedStructuralHabits` | **Should mirror** | **Medium** — masked by per-test `TestDataSeeder` today; fragile |
| 12 | `seedBuiltInTemplates` | **Should mirror** | **Medium** — same shape |
| 13 | `runBuiltInBackfill` | **Already covered + irrelevant** | None — fresh in-memory DB has nothing to dedupe | None |
| 14 | `runDriftCleanup` | **Already covered + irrelevant** | None | None |
| 15 | `runLifeCategoryBackfill` | **Already covered + irrelevant** | None — no rows to backfill in in-memory DB | None |
| 16 | `runMedicationMigrationPasses` | **Already covered + irrelevant** | None — pre-v54 state doesn't exist in test DB | None |
| 17 | `startMedicationIntervalRescheduler` | **Should explicitly NOT mirror** | Real but bounded — only matters for tests exercising medication interval reminders, which construct the rescheduler explicitly | **High** — registers real AlarmManager alarms + leaks Flow observer across tests |

**Summary:** 14 of 17 hooks should NOT be mirrored. 2 are arguable Medium gaps masked by per-test seeders. 1 (Firebase emulator routing) is correctly already mirrored.

### 1.4 Wrong-premise check

🛑 **STOP — NO WORK NEEDED.**

Justification:
- No "Critical" risk gaps: instrumented tests do not silently pass while production is broken because of a missing onCreate mirror. PR #791 was the only such gap; it has landed.
- The two "Medium" gaps (`seedStructuralHabits`, `seedBuiltInTemplates`) are currently masked by per-test seeders. Mirroring them at the runner level could mask **future** bugs in test setup (tests legitimately wanting an empty DB would no longer get one), so the fix is double-edged.
- Most missing hooks are deliberately omitted because mirroring them would create cross-test pollution (high risk in the OTHER direction — periodic workers fire during test runs and contaminate other tests' state).

PR #791 was the **one** initialization that genuinely needed mirroring because:
- It must run before any Firestore/Auth client is touched.
- It is a routing decision (emulator vs prod), not a data-state side effect.
- HiltTestApplication never runs `Application.onCreate`, so the routing was silently skipped.

The audit confirms there is no second PR #791 hiding in the codebase.

### 1.5 Proposed PR shape — STOP-driven

**No fix PR needed.** This audit document is the deliverable.

**Optional sentinel-style protection** (recommended but not required) — would catch future drift:

- New file: `app/src/androidTest/java/com/averycorp/prismtask/HiltTestRunnerParitySentinelTest.kt` (~80 LoC)
- Reflectively scan `PrismTaskApplication.kt` source for new private methods invoked from `onCreate()`. Assert each one is either (a) listed in an explicit `KNOWN_TEST_EXEMPT` allow-list with a justification comment, or (b) referenced by name from `HiltTestRunner.onStart()`.
- Fails CI when a new onCreate hook lands without a deliberate mirror/exempt decision.
- Pattern reference: PR #773's PII sentinel — "this should never happen, fail loudly if it does."

CHANGELOG (`## Unreleased` → `### Changed`): "test(infra): added HiltTestRunner parity sentinel test that fails when a new `Application.onCreate` hook lands without an explicit mirror/exempt decision."

### 1.6 Risk + dependencies

- The two "Medium" gaps (`seedStructuralHabits`, `seedBuiltInTemplates`) could become Critical in the future if the per-test `TestDataSeeder` pattern is dropped or a new test author forgets it. The sentinel test would catch this; otherwise we're relying on test-author discipline.
- If a future PR adds a `Application.onCreate` hook that genuinely needs mirroring (next PR #791), there is no automated detection. The sentinel test addresses this.
- No code dependencies — Phase 2 for Item 1 is either zero work or a single sentinel test PR.

---

## Item 2 — Connected-tests flake stabilization

### 2.1 Premise verification

Premise as stated: "Flake rate ~22% per PR #761 evidence. Stabilization order: top 10 flakiest first (Pareto), then `EdgeCaseRotationTest` + `HabitSmokeTest` + `SyncTestHarnessSmokeTest` individually since those hit during PR #761."

The premise has **two factual problems**:

1. **The named hotspots are mostly NOT flaking** in the current 28-run window:
   - `EdgeCaseRotationTest` — **0 failures** out of 28 runs
   - `SyncTestHarnessSmokeTest.harness_waitForReturnsAsSoonAsPredicateIsTrue` — **0 failures** (the prompt typo'd this as `SyllabusTestHarnessSmokeTest`, which doesn't exist)
   - `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` — **1 failure** (genuine but rare)
2. **The 22% conclusion-failure rate is not driven by flakes.** Of the 8 `connected-tests` failures in the window, 3 are infrastructure (git-checkout, Firebase emulator port timeout, compile error), 3 are a single **real (non-flake) test failure** in `Migration59To60Test` repeating across 3 consecutive runs since PR #773 merged, and 2 are genuine `HabitSmokeTest` Compose-timing flakes.

### 2.2 Current state — measured flake rate

**Window:** 28 real (non-skipped, non-cancelled) `android-integration.yml` runs from 2026-04-25T01:19Z through 2026-04-26T03:32Z.

**Note:** PR #791 merged at 2026-04-26T04:15:48Z — **after** the audit window. Its effect on `cross-device-tests` AVD reliability is not yet measured. PR #791 is a Firestore-routing fix, not an AVD-boot fix; it likely does not change the connected-tests numbers at all.

| Job | Real runs | Failures | Failure rate |
|---|---|---|---|
| `connected-tests` | 28 | 8 | **28.6%** |
| `cross-device-tests` | 16 (only post-04-25T07:51Z) | **16** | **100%** |

Of the 8 `connected-tests` failures:
- **3 infrastructure (not test code):** git-checkout fail (run 24938939904), Firebase emulator port-not-ready (run 24921274091), `compileDebugAndroidTestKotlin` compile error (run 24936201319). Not addressable by test stabilization.
- **5 real test failures:** see Pareto below.

**Test-flake rate (excluding infra):** 5 / 28 = **17.9%**. Still well above the 5% target.

Run IDs informing the calculation: 24947340575, 24945995404, 24945691272, 24943329684, 24943217820, 24942962248, 24942695133, 24942365939, 24938962202, 24938939904, 24937612065, 24937267539, 24936420297, 24936201319, 24936169864, 24926119046, 24925898805, 24922199291, 24921981464, 24921701817, 24921693199, 24921360343, 24921274091, 24920242399, 24919880641, 24919842285, 24919540542, 24919116665.

### 2.3 Top 10 flakiest tests (Pareto)

The window only has 3 distinct failing tests across 5 occurrences — this is not a long-tail flake distribution.

| Rank | Test | Failures | Diagnosis |
|---|---|---|---|
| 1 | `Migration59To60Test.malformedTiersByTimeJson_backfillsNothing` | **3 / 28 (10.7%)** | Same test failing on 3 consecutive runs after PR #773 merged. **Real bug or assertion drift, not a flake.** |
| 2 | `HabitSmokeTest.habitsTab_showsSeededHabits` | 1 / 28 | Compose `onAllNodesWithText("Exercise")` did not find seeded habit. Likely seeder-vs-`waitForIdle` race. |
| 3 | `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` | 1 / 28 | Same seed-data race as #2 (taps the first "Exercise" node). |

`cross-device-tests` lane: 16/16 failures, all single test `MedicationCrossDeviceConvergenceTest`, ALL with the same root cause: AVD `adb: device offline` after emulator boot — never reaches the test class. **Same finding as `PHASE_D_BUNDLE_AUDIT.md` Item 1**, which already recommended a stabilization PR; we should not double-write that fix.

### 2.4 Named hotspots verification

| Hotspot (per prompt) | File | Status |
|---|---|---|
| `EdgeCaseRotationTest` | `app/src/androidTest/java/com/averycorp/prismtask/smoke/EdgeCaseRotationTest.kt` | EXISTS. **NOT flaking** in the 28-run window. |
| `HabitSmokeTest.habitList_tappingHabitDoesNotCrash` | `app/src/androidTest/java/com/averycorp/prismtask/smoke/HabitSmokeTest.kt:29` | EXISTS. Flaked once (run 24919540542). Real seed-data race. |
| `SyllabusTestHarnessSmokeTest.harness_waitForReturnsAsSoonAsPredicateIsTrue` | NO SUCH FILE — typo in prompt | Actual file is `app/src/androidTest/java/com/averycorp/prismtask/sync/SyncTestHarnessSmokeTest.kt:133`. **NOT flaking** in window. Test polls a counter under 50 ms with a 5s ceiling — structurally robust. |

### 2.5 Root cause clustering

| Cluster | Tests | Hypothesis | Single fix cascades? |
|---|---|---|---|
| **AVD boot failure** (`adb: device offline`) | All 16 `cross-device-tests` failures + (probably) connected-tests run 24921274091 + indirectly 24919842285 | `reactivecircus/android-emulator-runner@v2` AVD boots flakily under `ubuntu-24.04 / 20260413.86.1` with `api-level: 34 / google_apis / x86_64 / pixel_6 / -no-snapshot`. Every cross-device run failed the same way; PR #780 (script-syntax fix) and PR #791 (Firestore routing) addressed *different* symptoms. Same root cause as `PHASE_D_BUNDLE_AUDIT.md` Item 1. | **YES** — pin emulator-runner action SHA, add AVD snapshot caching (`actions/cache` with AVD path), and/or downgrade to `api-level: 33`. One PR fixes ALL cross-device runs. |
| **Real test failure (not flake)** | `Migration59To60Test.malformedTiersByTimeJson_backfillsNothing` (3 runs) | Failing on 3 consecutive runs after PR #773 (medication migration safety net) merged. Real test/code defect. | N/A — needs targeted fix. |
| **Compose seed-data race** | `HabitSmokeTest.habitsTab_showsSeededHabits` + `habitList_tappingHabitDoesNotCrash` | `composeRule.waitForIdle()` returns before built-in habit seeder + `BuiltInHabitReconciler` finish on first launch. | **YES** — replace `waitForIdle()` with `composeRule.waitUntil { onAllNodesWithText("Exercise").fetchSemanticsNodes().isNotEmpty() }`. One pattern fix in `SmokeTestBase` cascades. |
| **Pre-test infra** | git-checkout fail (24938939904), emulator port timeout (24921274091), `compileDebugAndroidTestKotlin` compile fail (24936201319) | Transient runner / dependency issues. Not test-code. | Not addressable by test changes. Workflow-level retry-on-failure for setup steps would mitigate. |

### 2.6 Wrong-premise check

🛑 **STOP — WRONG PREMISE.**

Two of the three named hotspots (`EdgeCaseRotationTest`, `SyncTestHarnessSmokeTest.harness_waitForReturnsAsSoonAsPredicateIsTrue`) have **zero failures** in the measured window. Spending cycles "stabilizing" them produces no measurable improvement, since their failure rate is already zero.

The "~22% flake rate" cited from PR #761 matches the measured 28.6% conclusion-failure rate, but **dissecting** that 28.6% reveals:
- ~10.7% is one **real test failure** (`Migration59To60Test`), not flake.
- ~10.7% is **infra** (git, emulator port, compile) — not addressable by stabilizing test code.
- Only ~7.1% (2 runs) is true Compose-timing flake, all in `HabitSmokeTest`.

Fan-out as written would produce three PRs that don't move the flake rate.

### 2.7 Proposed PR shape — replacement scope

**Order of execution:** 1 → 2 → 3, each landing before next so flake-rate measurement is attributable.

#### PR 1 — fix(test): repair `Migration59To60Test.malformedTiersByTimeJson_backfillsNothing`

- **Title:** `fix(test): Migration59To60Test.malformedTiersByTimeJson_backfillsNothing — verify actual schema state`
- **Files:**
  - `app/src/androidTest/java/com/averycorp/prismtask/Migration59To60Test.kt` (~10–30 line change once root cause is identified)
  - Possibly `app/src/main/java/com/averycorp/prismtask/data/local/database/Migrations.kt` (if the migration itself has a bug)
- **Tests:** the failing test IS the regression test — fix it to assert correctly (or fix the migration). No new test needed.
- **CHANGELOG (`## Unreleased` → `### Fixed`):** "Migration 59→60 backfill: corrected assertion for malformed-JSON case in instrumentation test (CI failure since #773)."

#### PR 2 — ci(android-integration): pin emulator-runner + cache AVD snapshot for `cross-device-tests`

- **Coordination note:** This is the SAME fix flagged in `PHASE_D_BUNDLE_AUDIT.md` Item 1.5 ("ci(android-integration): stabilize `cross-device-tests` AVD boot"). Confirm with bundle owner whether that PR has been opened; if so, this PR is redundant. If not, it should be opened as part of the bundle's Item 1 follow-up, not as a Phase D Fan-Out 2 deliverable.
- **Title:** `ci(android-integration): cache AVD snapshot + pin emulator-runner to fix 100% cross-device-tests AVD-boot fail rate`
- **Files:**
  - `.github/workflows/android-integration.yml` (~15–25 line change: pin `reactivecircus/android-emulator-runner@v2` to a specific SHA in BOTH jobs; add `actions/cache@v4` step to cache `~/.android/avd` keyed on api-level + arch + profile; add `force-avd-creation: false` after the cache hit is verified)
- **Tests:** regression assertion is the workflow itself — the next 5 runs on main must show `cross-device-tests` reaching the test phase.
- **CHANGELOG (`## Unreleased` → `### Changed`):** "CI: pinned Android emulator runner SHA and added AVD-snapshot caching for the cross-device-tests lane (eliminates 'adb: device offline' boot failures)."

#### PR 3 — test(smoke): replace `waitForIdle` with `waitUntil` for seed-data dependent assertions

- **Title:** `test(smoke): waitUntil for seeded-habit assertions to fix HabitSmokeTest race`
- **Files:**
  - `app/src/androidTest/java/com/averycorp/prismtask/smoke/SmokeTestBase.kt` (add `waitForText(text: String, timeoutMs: Long = 5_000)` helper)
  - `app/src/androidTest/java/com/averycorp/prismtask/smoke/HabitSmokeTest.kt` (~5-line change: replace `composeRule.onAllNodesWithText("Exercise").onFirst().…` with a `waitForText("Exercise")` precursor)
- **Tests:** the `HabitSmokeTest` methods themselves are the regression — re-run the workflow 10× post-merge to confirm 0/10 flakes.
- **CHANGELOG (`## Unreleased` → `### Changed`):** "Smoke tests: replaced `waitForIdle()` with explicit `waitUntil(text-present)` before tapping seeded habits, fixing CI flake."

#### What NOT to do (explicit rejections of the prompt fan-out)

- Do NOT spend cycles "fixing" `EdgeCaseRotationTest` — 0 failures, rewriting cannot lower an already-zero failure rate.
- Do NOT spend cycles on `SyncTestHarnessSmokeTest.harness_waitForReturnsAsSoonAsPredicateIsTrue` — also 0 failures, structurally robust polling test.
- Do NOT add per-test Firestore Emulator clear hooks as a "blanket cascade fix" — there's no evidence Firestore state leakage is causing any of the 5 measured failures. `SyncTestHarnessSmokeTest` already calls `harness.cleanupFirestoreUser()` in `@Before`/`@After`.

After PR 1 + PR 2 + PR 3 land, re-measure on the next 20 runs. If `connected-tests` failure rate drops below 5%, file the trivial promote-to-required PR (single edit to `scripts/setup-branch-protection.sh`).

### 2.8 Risk + dependencies

- PR 2 overlaps with `PHASE_D_BUNDLE_AUDIT.md` Item 1's recommended stabilization PR. **Risk: double-write.** Coordinate with bundle owner before opening.
- PR 1 requires reading the actual `Migration59To60Test` source + run logs to identify the real failure mode — out of audit scope. The fix could end up in either the test or the migration; can't predict without inspection.
- No runs yet exist post-PR #791 for `connected-tests`. PR #791 is a `cross-device-tests` Firestore-routing fix; it's unlikely to change the connected-tests flake rate, but the next ~5 runs should be observed before opening PR 2 in case the AVD picture shifts.
- Working tree was 2 commits behind `origin/main` at audit time (cb12afe3..6a677ea5). Worktree was branched from `origin/main` so Phase 2 work starts from current tip.

---

## Recommendations

| Item | Verdict | Rationale |
|---|---|---|
| Item 1 — HiltTestRunner.onStart sweep | **STOP — NO WORK NEEDED** | 14 of 17 onCreate hooks should NOT be mirrored (cross-test pollution risk). 2 are masked by per-test seeders. PR #791 was the one genuine gap and has landed. **Audit IS the deliverable.** Optional sentinel test (~80 LoC) recommended for future-proofing but not required. |
| Item 2 — Connected-tests flake stabilization | **STOP — WRONG PREMISE** | 2 of 3 named hotspots have **zero failures** in the 28-run window. Real flake-rate drivers are: (a) one real (non-flake) test failure in `Migration59To60Test`, (b) `cross-device-tests` AVD boot reliability already flagged in `PHASE_D_BUNDLE_AUDIT.md` Item 1, (c) one real Compose seed-data race in `HabitSmokeTest`. **Replacement scope is 3 different PRs** — PR 1 (Migration59To60 fix) and PR 3 (HabitSmokeTest waitUntil) are net-new; PR 2 is the SAME AVD fix already flagged in the bundle audit and may be redundant. |

### Phase 2 ask

Per the prompt's hard rule "STOP and report on wrong premises. Do not silently reframe." — **stopping for explicit Phase 2 approval** before any code work.

Decision points the user needs to weigh in on:

1. **Item 1**: ship the optional sentinel test PR, or accept the audit doc as the only deliverable?
2. **Item 2**: approve the replacement scope (PR 1 + PR 3, with PR 2 deferred to bundle-owner coordination), or pursue the original fan-out anyway (which the data does not support)?
3. **Item 2 PR 2 coordination**: is the AVD stabilization PR already in flight from the Phase D bundle? If yes, drop it from this fan-out.
