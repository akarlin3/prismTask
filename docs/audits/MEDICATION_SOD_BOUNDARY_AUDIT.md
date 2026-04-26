# Medication SoD Boundary Audit

**Bug:** `MedicationScreen` (Android, main slot-today view) shows medication
state that doesn't reset at the user's configured Start-of-Day (SoD), so logs
from yesterday's logical day appear as "today's" doses, and the screen flips
state at the calendar-midnight boundary instead of at the SoD boundary.

**Severity:** P0 — blocks Phase F (May 15 launch).

**Process:** Repro-first, audit-first. This document is built up phase by phase
per the launch prompt; do not act on any later section before the earlier ones
are signed off.

**Repo state at start of audit:** branch `fix/medication-sod-boundary` cut from
`main` @ `ea03b751`.

---

## Phase 1 — Reproduction

### Section 1.1 — Baseline

#### Where is SoD persisted?

`com.averycorp.prismtask.data.preferences.TaskBehaviorPreferences`
(`app/src/main/java/com/averycorp/prismtask/data/preferences/TaskBehaviorPreferences.kt`)

- Keys: `day_start_hour` (Int 0–23, default `0`), `day_start_minute` (Int 0–59,
  default `0`), `has_set_start_of_day` (Bool, default `false`).
- Hour-only flow: `getDayStartHour(): Flow<Int>` (line 101).
- Minute-only flow: `getDayStartMinute(): Flow<Int>` (line 105).
- Combined snapshot: `getStartOfDay(): Flow<StartOfDay>` (line 113).
- Atomic setter: `setStartOfDay(hour, minute)` flips `has_set_start_of_day = true`
  in the same edit (line 162).

> ⚠️ The launch prompt assumed SoD lived in `UserPreferencesDataStore`. It does
> not — it lives in `TaskBehaviorPreferences` (a sibling DataStore at
> `task_behavior_prefs`). Documented here so Phase 2 traces don't waste time.

#### Canonical "logical day" helpers

There are **two** SoD-aware helpers in the codebase, kept in parallel:

1. **`com.averycorp.prismtask.core.time.DayBoundary`** —
   `app/src/main/java/com/averycorp/prismtask/core/time/DayBoundary.kt`
   - The canonical v1.4+ API. `Instant`-based, takes explicit `sodHour`,
     `sodMinute`, `zone` arguments (no static `now()` reads inside the helper).
   - Paired with `core.time.TimeProvider` (interface + `SystemTimeProvider`
     `@Singleton` Hilt binding) so callers can be tested without monkey-patching
     `Instant.now()`.
   - Existing test coverage: `app/src/test/java/com/averycorp/prismtask/core/time/DayBoundaryTest.kt`
     (DST cases, exact-on-SoD, minute precision, ambiguous-time resolution).

2. **`com.averycorp.prismtask.util.DayBoundary`** —
   `app/src/main/java/com/averycorp/prismtask/util/DayBoundary.kt`
   - The legacy `Long`-millis API. `now: Long = System.currentTimeMillis()`
     defaulted at the parameter level — the helper itself is pure, but its
     callers can (and routinely do) skip passing `now`, which silently captures
     the wall-clock at the moment of the call.
   - Has the convenience helpers `currentLocalDate(...)` and
     `currentLocalDateString(...)` that the medication code uses.
   - The KDoc on `core.time.DayBoundary` (line 17) explicitly states the legacy
     util "is kept for back-compat with hour-only callers and will be migrated
     incrementally."

#### Who uses each helper?

`grep -r "DayBoundary"` (33 matches across Kotlin, web, docs):

| File | Helper | Notes |
|------|--------|-------|
| `data/repository/HabitRepository.kt` | `util` | habit completions |
| `data/repository/TaskRepository.kt` | `util` | today filter |
| `data/repository/SchoolworkRepository.kt` | `util` | course/assignment day grouping |
| `data/repository/SelfCareRepository.kt` | `util` | self-care logs |
| `data/repository/LeisureRepository.kt` | `util` | leisure logs |
| `data/repository/MedicationRepository.kt` | `util` | **dose write-side** (correct — passes `takenAt`) |
| `domain/usecase/MedicationStatusUseCase.kt` | `util` | **dose read-side** (broken — see §1.3) |
| `domain/usecase/DailyEssentialsUseCase.kt` | `util` | daily essentials grouping |
| `domain/usecase/NaturalLanguageParser.kt` | `util` | NLP date resolution |
| `notifications/HabitReminderScheduler.kt` | `util` | next-fire computation |
| `notifications/MedicationReminderScheduler.kt` | `util` | next-fire computation |
| `widget/WidgetDataProvider.kt` | `util` | widget today queries |
| `workers/DailyResetWorker.kt` | `util` | daily reset cron |
| `ui/screens/today/TodayViewModel.kt` | `util` | Today screen filter |
| `ui/screens/tasklist/TaskListViewModel.kt` | `util` | task list filter |
| `ui/screens/checkin/MorningCheckInViewModel.kt` | `util` | morning check-in window |
| **`ui/screens/medication/MedicationViewModel.kt`** | `util` | **main MedicationScreen — the buggy reader** |

The canonical `core.time.DayBoundary` is used by `Test`, `Test`, and… nothing
else in production. Migration to the canonical helper has not actually
started; only the helper itself + its test suite landed.

#### Medication read-side architecture

```
TaskBehaviorPreferences.getDayStartHour(): Flow<Int>
        │
        ▼
MedicationViewModel.todayDate: StateFlow<String>          ← stale (BUG)
        │
        ├──► flatMapLatest { date ->
        │       MedicationRepository.observeDosesForDate(date)   ← Room Flow
        │    }
        │
        └──► flatMapLatest { date ->
                MedicationSlotRepository.observeTierStatesForDate(date)
             }                                                    ← Room Flow

slotTodayStates = combine(activeSlots, medications, todaysDoses, todaysTierStates)
        │
        ▼
MedicationScreen.SlotTodayCard  ← renders takenTimeLabel(state)
```

`todayDate` is the single source of "what day are we querying for." Both
downstream `flatMapLatest` chains re-key off it. If `todayDate` is stale, the
DAO queries are pinned to a stale date and the entire screen represents the
wrong logical day.

`MedicationViewModel.todayDate` (`MedicationViewModel.kt:91-100`):

```kotlin
val todayDate: StateFlow<String> = taskBehaviorPreferences
    .getDayStartHour()
    .flatMapLatest { hour ->
        MutableStateFlow(DayBoundary.currentLocalDateString(hour))   // ① snapshot
    }
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        DayBoundary.currentLocalDateString(0)                         // ② SoD=0
    )
```

Two structural defects already visible from this snippet — formalised in §1.3
once the repro confirms them:

- ① `MutableStateFlow(currentLocalDateString(hour))` evaluates the helper once
  at the moment `flatMapLatest`'s lambda fires (i.e. once per upstream emission
  of `getDayStartHour()`), then completes. The flow has no clock-tick, no
  `tickerFlow`, no broadcast-receiver hook for `ACTION_TIME_TICK` /
  `ACTION_DATE_CHANGED`. After construction the value is locked.
- ② The `stateIn` initial value hard-codes `dayStartHour = 0` — calendar
  midnight — regardless of the user's configured SoD.

Sister defect at `MedicationStatusUseCase.kt:78-88`:

```kotlin
fun observeDueDosesToday(): Flow<List<MedicationDose>> =
    taskBehaviorPreferences.getDayStartHour().flatMapLatest { dayStartHour ->
        val todayLocal = DayBoundary.currentLocalDateString(dayStartHour)   // ③ snapshot
        combine(
            medicationDao.getActive(),
            medicationDoseDao.getForDate(todayLocal)
        ) { meds, doses ->
            expandMedicationsToDoses(meds, doses).filterNot { it.takenToday }
        }
    }
```

Same shape: ③ `todayLocal` is captured once when `flatMapLatest`'s lambda runs;
the `combine` underneath happily re-emits whenever meds/doses change but always
keys off the stale date.

#### Write-side: correct

`MedicationRepository.logDose` (line 102) and `logSyntheticSkipDose` (line 133):

```kotlin
val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
val dateLocal = DayBoundary.currentLocalDateString(dayStartHour, takenAt)
```

These pass `takenAt` (or `intendedAt`) explicitly to `currentLocalDateString`,
so the persisted `takenDateLocal` reflects the actual logical day of the dose.
**The bug is purely on the read/display side.** This rules out classification
3b (persistence-side) — there is no migration to write.

#### Hidden secondary defect: write paths that key off `todayDate.value`

These VM methods read the stale `todayDate.value` and pass it back into write
calls (`MedicationViewModel.kt:197, 224, 246, 259, 416`):

- `setSkippedForSlot` — passes `todayDate.value` to `slotRepository.upsertTierState`.
- `setIntendedTimeForSlot` — passes `todayDate.value` to `slotRepository.setTierStateIntendedTime`.
- `clearUserOverrideForSlot` — filters by `todayDate.value` (read).
- `refreshTierState` — passes `todayDate.value` to `slotRepository.upsertTierState`.
- `bulkMarkInternal` — embeds `todayDate.value` into the `proposedNewValues`
  payload that `BatchOperationsRepository.applyBatch` re-hydrates downstream.

Note `logSyntheticSkipDose` and `logDose` re-derive the date from
`takenAt`/`intendedAt`, so the `medication_doses` rows are correct; but the
`medication_tier_states` rows written from these paths use the stale
`todayDate.value` directly, so a user marking a slot SKIPPED at 5 a.m. with
SoD = 4 a.m. (after a screen that was opened at 11 p.m. the night before) would
write the tier-state row under yesterday's `date` column. Today's tier chip
stays empty until the screen is re-collected from scratch.

This is downstream of the same root cause; the fix to `todayDate` resolves it
without separate code changes, but the audit needs to remember it for Phase 2's
test plan.

### Section 1.2 — Repro path

The launch prompt suggests three repro paths in order: A) emulator clock,
B) app debug-build now-provider hook, C) patient observation.

- **Repro A — emulator clock.** Not attempted. No Android emulator is running
  in this environment (`adb devices` would show none from the host shell), and
  spinning one up to demonstrate a structural bug whose root cause is already
  visible from inspection is poor ROI. Documented as skipped, not failed.
- **Repro B — debug-build now-provider hook.** Adopted with a twist. The
  legacy `util.DayBoundary.currentLocalDateString` already takes `now: Long`
  as a parameter — only the call sites in `MedicationViewModel` and
  `MedicationStatusUseCase` skip it and let the default `System.currentTimeMillis()`
  bind. So a unit test can drive the helper across the SoD boundary by passing
  explicit `now` values, and reconstruct the *flow shape* of `todayDate` to
  prove the StateFlow snapshot pattern locks the value. This is enough to
  eliminate any "the helper is wrong" hypothesis (it isn't) and pin the bug to
  the flow plumbing.
- **Repro C — patient observation.** Not attempted. Rejected as too slow given
  Repro B succeeded.

#### Test file

`app/src/test/java/com/averycorp/prismtask/ui/screens/medication/MedicationTodayDateRefreshTest.kt`
— a new pure-JVM test that:

1. Sanity-checks the helper itself across an SoD boundary (one assertion).
2. Reconstructs the exact `MutableStateFlow(currentLocalDateString(hour))`
   shape from `MedicationViewModel.kt:91-100` and proves the value stays
   locked to the construction-time evaluation across a simulated 6-hour
   wall-clock advance (one assertion).
3. Demonstrates the `stateIn` initial value hard-codes `dayStartHour = 0`,
   producing a wrong-by-one-day date for custom-SoD users at the time-of-day
   when SoD > calendar midnight (one assertion).

Run command:

```
./gradlew :app:testDebugUnitTest --tests "com.averycorp.prismtask.ui.screens.medication.MedicationTodayDateRefreshTest"
```

### Section 1.3 — Repro outcome

**Repro: confirmed.** `MedicationTodayDateRefreshTest` runs in 7 ms and all
three assertions pass on `main` @ `ea03b751`:

```
:app:testDebugUnitTest --tests "...MedicationTodayDateRefreshTest"

testsuite name="...MedicationTodayDateRefreshTest" tests="3"
                  skipped="0" failures="0" errors="0" time="0.007"
  testcase mutableStateFlowSnapshot_doesNotRefreshAcrossWallClockAdvance
  testcase helper_returnsDifferentDatesAcrossSoDBoundary
  testcase stateInInitialValue_ignoresUserSoD
```

Each test is written so that **passing means the bug exists** — the
assertions encode the broken contract. Fixing the bug must invert the
relevant assertions so a regression would re-fail them.

Test report:
`app/build/test-results/testDebugUnitTest/TEST-com.averycorp.prismtask.ui.screens.medication.MedicationTodayDateRefreshTest.xml`

#### Defects confirmed

1. **`MutableStateFlow` snapshot defect** — `MedicationViewModel.kt:93-95`.
   Wrapping `DayBoundary.currentLocalDateString(hour)` in `MutableStateFlow(...)`
   captures the helper return value once per upstream emission and never
   refreshes thereafter. There is no clock-tick subscription anywhere in the
   chain. `todayDate.value` is locked to the wall-clock moment of the last
   `getDayStartHour()` emission.
2. **`stateIn` initial-value defect** — `MedicationViewModel.kt:99`. The
   initial `DayBoundary.currentLocalDateString(0)` argument hard-codes
   `dayStartHour = 0`, ignoring the user's SoD. For a custom-SoD user whose
   wall-clock is between calendar midnight and SoD, the initial value reports
   a date one day ahead of their logical date for the brief window before the
   upstream lands.
3. **Sister defect (not directly tested)** —
   `MedicationStatusUseCase.kt:80`. Same shape: the `todayLocal` snapshot
   inside `flatMapLatest { dayStartHour -> … }` captures once per upstream
   emission. The `combine` underneath re-emits whenever
   `medicationDoseDao.getForDate(todayLocal)` returns a new row set, but
   always for the stale `todayLocal`. Confirmed by code inspection; same
   repro mechanism as defect 1, so a single fix to the helper / flow shape
   resolves both.

#### Symptom mapping (reported → confirmed cause)

| User-reported symptom | Root cause |
|-----------------------|------------|
| "Logs from yesterday show as today's logs" after the SoD has rolled over | Defect 1: `todayDate.value` is still yesterday's logical date because nothing refreshes it on a wall-clock advance, so the DAO query is pinned to yesterday's date and yesterday's dose rows render in today's slot cards. |
| "Day doesn't reset at SoD" | Defect 1 again: the only way the value advances is a fresh upstream emission from `getDayStartHour()` (preference change) or a fresh re-subscription of the StateFlow (screen torn down and recollected from scratch). Neither is triggered by the wall-clock crossing SoD. |
| "Today's logs disappear at midnight (calendar boundary), not at SoD" | Defect 2: when `WhileSubscribed(5_000L)` collapses the flow and the user re-enters the screen, the `stateIn` initial value uses `dayStartHour = 0` and reports the new calendar date. Doses with `takenDateLocal = yesterday's logical date` aren't returned by `getForDate(newCalendarDate)`, so the cards briefly empty out — reads to the user as "logs disappeared at midnight." When the upstream re-emits the actual `dayStartHour`, the helper resolves back to yesterday's logical date and the doses reappear. The same flicker happens at every fresh subscription, so it can present at midnight even when no actual day-change occurred. |

#### Hidden secondary findings (downstream of the same root cause)

These were uncovered while tracing the read path and are noted here so Phase 2
doesn't have to rediscover them:

- `MedicationViewModel` write methods that read `todayDate.value` —
  `setSkippedForSlot` (line 197), `setIntendedTimeForSlot` (line 224),
  `clearUserOverrideForSlot` (line 246), `refreshTierState` (line 259),
  `bulkMarkInternal` (line 416) — propagate the stale date into
  `medication_tier_states` writes. A user marking a slot SKIPPED post-SoD
  with a stale `todayDate` will write the tier-state row under yesterday's
  `date` column, never visible on today's card. (`logDose` and
  `logSyntheticSkipDose` re-derive the date from `takenAt`/`intendedAt`
  internally, so the `medication_doses` rows are written correctly.)
- The legacy `util.DayBoundary` is still the only helper in production use.
  The canonical `core.time.DayBoundary` + `TimeProvider` stack is built and
  unit-tested but no production caller exists yet. Phase 2 should decide
  whether to migrate medication to the canonical stack as part of the fix
  or keep it on the legacy helper and patch the flow shape only.

#### What Phase 2 must NOT do

- **Do not** propose 3b (persistence-side fix). The write path is correct.
- **Do not** propose 3d (timezone confusion). Tests pass cleanly under a
  pinned UTC default; symptoms described by the user are pure SoD-boundary
  behaviour, not DST/zone behaviour.
- **Do not** sweep more than the medication surface unless the same flow
  shape is found elsewhere — this audit doesn't claim the other
  `util.DayBoundary` callers (Today, Habit, Tasklist, Widget) have the same
  bug. They use different flow shapes (e.g. `combine` with the upstream
  re-keying off DB Flows) and need their own audit before any sweep.

---

## Phase 1 deliverable — gated

Phase 1 is complete. **Stopping here for sign-off before Phase 2.**

Repro confirmed via `MedicationTodayDateRefreshTest`. Root cause is
read-side flow plumbing in `MedicationViewModel.todayDate` (and the
identical shape in `MedicationStatusUseCase.observeDueDosesToday()`). Three
symptoms map to two structural defects + one fresh-subscription flicker.
Persistence is correct; no migration needed.

Per the launch prompt's Phase 1 gate, do not proceed to Phase 2 (root cause
classification + fix proposal) without explicit approval.

---

## Phase 2 — Root cause audit

Phase 1 was approved on 2026-04-26.

### Section 2 — Code path tracing

#### Where does the medication surface ask "what time is it now"?

| Callsite | Pattern | Notes |
|----------|---------|-------|
| `MedicationViewModel.kt:94` (inside `flatMapLatest`) | `DayBoundary.currentLocalDateString(hour)` — defaulted `now = System.currentTimeMillis()` | reads wall-clock at flatMap-lambda time |
| `MedicationViewModel.kt:99` (initial value of `stateIn`) | `DayBoundary.currentLocalDateString(0)` — defaulted `now`, hard-coded SoD=0 | reads wall-clock at VM construction time |
| `MedicationViewModel.kt:199` (`setSkippedForSlot`) | `System.currentTimeMillis()` — passed to `logSyntheticSkipDose(intendedAt = ...)` | dose-write side, correct |
| `MedicationStatusUseCase.kt:80` (inside `flatMapLatest`) | `DayBoundary.currentLocalDateString(dayStartHour)` — defaulted `now` | reads wall-clock at flatMap-lambda time |
| `MedicationRepository.kt:99,103` (`logDose`) | `System.currentTimeMillis()` (default) → `DayBoundary.currentLocalDateString(dayStartHour, takenAt)` | dose-write side, correct |
| `MedicationRepository.kt:131,134` (`logSyntheticSkipDose`) | same as above | correct |

The TimeProvider abstraction (`core/time/TimeProvider.kt`) **exists** as a Hilt
`@Singleton` but is not wired into MedicationViewModel or MedicationStatusUseCase.
Every "what time is it now" read in the buggy paths is a direct
`System.currentTimeMillis()` call.

#### Where does it ask "what day are we logging for"?

There IS a SoD-aware helper — actually two, kept in parallel:

- `core.time.DayBoundary` — canonical v1.4+ API (`Instant`-based, takes
  `sodHour`, `sodMinute`, `zone` arguments, no static now reads). Designed to
  pair with `TimeProvider`. **Not in production use.**
- `util.DayBoundary` — legacy (`Long` millis, defaulted-`now` parameters).
  Pure function; the helper itself is correct. **Used by every caller in
  production today.**

So the bug is not "no helper" or "wrong helper" — both helpers are correct in
isolation. The bug is in how the helper return value is wired into a flow:
the medication surface evaluates `DayBoundary.currentLocalDateString(hour)`
**once per upstream `getDayStartHour()` emission**, then wraps the return
value in `MutableStateFlow(...)` (or assigns it to a local `val` inside
`flatMapLatest`'s lambda), giving the value no path to refresh on a wall-clock
advance.

#### Where do logged medication times get persisted?

- `medication_doses` table — one row per dose, with `taken_at` (epoch millis,
  the wall-clock moment of the dose) AND `taken_date_local` (ISO string of
  the user's logical day at write time). Per CLAUDE.md the column is timezone-
  neutral.
- `medication_tier_states` table — one row per `(medication_id, slot_id, log_date)`,
  with `log_date` (ISO string) + the achieved tier + `intended_time` +
  `logged_at`. Per CLAUDE.md, `intended_time` was added in `MIGRATION_62_63`
  and `medication_marks` was added then dropped in `MIGRATION_63_64` (orphan
  table, no production write path).

Write paths:

- `MedicationRepository.logDose` (line 96-117): re-derives `dateLocal` from the
  passed-in `takenAt`, NOT from any cached date. **Correct.**
- `MedicationRepository.logSyntheticSkipDose` (line 128-149): same shape.
  **Correct.**
- `MedicationViewModel.setSkippedForSlot` (line 195-215): calls
  `slotRepository.upsertTierState(date = todayDate.value, ...)` — uses the
  stale `todayDate.value`. **Bug downstream of root cause.**
- `MedicationViewModel.setIntendedTimeForSlot` (line 222-238): same.
- `MedicationViewModel.clearUserOverrideForSlot` (line 243-250): same.
- `MedicationViewModel.refreshTierState` (line 258-279): same.
- `MedicationViewModel.bulkMarkInternal` (line 411-462): embeds
  `todayDate.value` into `proposedNewValues["date"]`. Same.

So the `medication_doses` table is durable-correct (every dose row carries the
right logical-day key derived from its own `taken_at`), but
`medication_tier_states` rows written from `MedicationViewModel` paths inherit
the stale `todayDate.value` and land under yesterday's `log_date`. Fixing
`todayDate` resolves both — there is no migration debt.

#### Where do persisted times get loaded for display?

```
MedicationScreen (Compose)
  └─ collectAsStateWithLifecycle(viewModel.slotTodayStates)
       └─ slotTodayStates = combine(activeSlots, medications, todaysDoses, todaysTierStates)
           ├─ todaysDoses     = todayDate.flatMapLatest { observeDosesForDate(it) }
           │    └─ MedicationRepository.observeDosesForDate(date)
           │         └─ MedicationDoseDao.getForDate(date) ─ Room Flow ✓ reactive on DB change
           │
           └─ todaysTierStates = todayDate.flatMapLatest { observeTierStatesForDate(it) }
                └─ MedicationSlotRepository.observeTierStatesForDate(date)
                     └─ MedicationTierStateDao.observeForDate(date) ─ Room Flow ✓ reactive on DB change
```

Both DAO leaves are reactive Room Flows that re-emit when their query result
changes. The bug is **not** at the DAO leaf or in the `combine` upstream —
it's at the root, where `todayDate` snapshots once and pins the date passed
into `flatMapLatest`. The Room Flows happily stream rows for whatever date
they were last keyed off, so the cards keep showing yesterday's rows
indefinitely.

#### Sister flow propagation

`MedicationStatusUseCase.observeDueDosesToday()` (the broken sister flow) is
consumed by `DailyEssentialsUseCase.kt:172` — the Today-screen "essentials"
card. So the same bug surfaces on the Today screen as "the medication card
shows yesterday's untaken doses as today's." Fixing
`MedicationStatusUseCase` fixes both surfaces; no Today-screen change needed.

### Section 3 — Root cause classification

**Classification: 3a (display-side day boundary).** Specifically a 3a-prime
sub-shape: the day-boundary helper itself is correct (and SoD-aware), but the
flow plumbing wraps the helper return value in a `MutableStateFlow(...)` that
captures the wall-clock once and offers no refresh hook. There is no
clock-tick subscription, no `tickerFlow`, no broadcast-receiver hook for
`Intent.ACTION_DATE_CHANGED` / `ACTION_TIME_TICK`, no scheduled re-emission
at the next logical-day boundary.

**Why not 3b (persistence-side):** `medication_doses.taken_date_local` is
written from the dose's own `taken_at`, not from a cached date. The column is
correct. (`medication_tier_states.log_date` is downstream-stale via the same
read-side bug, but is not durable-wrong — fixing `todayDate` corrects future
writes.)

**Why not 3c (SoD setting read once at app start):** The SoD setting IS read
reactively via `taskBehaviorPreferences.getDayStartHour(): Flow<Int>`. The
upstream Flow re-emits on preference change. The defect is downstream of the
SoD read, in the snapshot of "what day is it" given that SoD value.

**Why not 3d (timezone confusion):** Reproduction tests pass under a pinned
UTC default with no DST involvement. The user reports symptoms on plain
SoD-boundary timing, not DST or zone-change timing. The canonical
`core.time.DayBoundaryTest` has DST coverage — neither helper is
zone-confused.

**Why not 3e:** No additional unaccounted-for surface emerged during repro.
Three reported symptoms all map cleanly to the two structural defects already
identified.

### Section 4 — Fix proposal

#### Approach

Introduce a **shared, reactive `LocalDateFlow`** in `core/time/` that combines
the SoD setting Flow with a wall-clock ticker. The ticker emits the current
logical date on subscription, then suspends until the next logical-day
boundary, then re-emits. Backed by `TimeProvider` so tests can drive virtual
time.

Both buggy callers (`MedicationViewModel`, `MedicationStatusUseCase`) consume
this helper and drop their bespoke snapshot logic. The legacy
`util.DayBoundary` and the canonical `core.time.DayBoundary` both stay where
they are — the new helper is a flow-shape wrapper, not another date library.
Other broken-shape callers (Today, Habit, Tasklist, Widget) are out of scope
for this PR but can adopt the helper later in a dedicated migration audit.

#### Files touched

| File | Change | LOC |
|------|--------|-----|
| `core/time/LocalDateFlow.kt` | NEW — `@Singleton` Hilt-injectable helper. Public API: `observe(): Flow<LocalDate>`, `observeIsoString(): Flow<String>`, `current(): LocalDate` (suspending one-shot). Internally combines `taskBehaviorPreferences.getStartOfDay()` with a `flow { … delay(timeUntilNextLogicalDayStart) }` body. Deduped via `distinctUntilChanged()`. | ~60 |
| `ui/screens/medication/MedicationViewModel.kt` | EDIT — add `private val localDateFlow: LocalDateFlow` constructor param. Replace lines 91-100 with `val todayDate = localDateFlow.observeIsoString().stateIn(viewModelScope, WhileSubscribed(5_000L), LocalDate.now().toString())`. | ~10 delta |
| `domain/usecase/MedicationStatusUseCase.kt` | EDIT — add `private val localDateFlow: LocalDateFlow` constructor param. Replace lines 79-88 with `localDateFlow.observeIsoString().flatMapLatest { todayLocal -> combine(meds, doses) { … } }`. | ~10 delta |
| `core/time/LocalDateFlowTest.kt` | NEW — uses `runTest` virtual scheduler + a `FakeTimeProvider` driven by `TestScope.currentTime`. Cases: subscription-time emission, boundary re-emission, no-emission for sub-boundary advance, SoD change re-keying, DST spring-forward (NY zone), zero-day-no-SoD baseline. | ~120 |
| `ui/screens/medication/MedicationTodayDateRefreshTest.kt` | REWRITE — keep the file name, flip the assertions so passing now means the bug is FIXED (`todayDate` advances when the wall-clock crosses SoD; initial value respects user SoD; sister `MedicationStatusUseCase` re-emits across boundaries). Test must FAIL on `main`, PASS after the fix. Acts as the regression gate. | ~80 (replaces existing 130) |
| `domain/usecase/MedicationStatusUseCaseTest.kt` | EDIT — add a flow test covering the corrected reactive behavior (the existing file only covers `dedupByName`, so this is purely additive; no existing test breaks). | ~50 added |
| `web/src/utils/useLogicalToday.ts` | NEW — React hook returning a stable `string` that advances on wall-clock crossing of `endOfLogicalDayMs`. Internally schedules a one-shot `setTimeout` to the next boundary, re-schedules on fire. Cleans up on unmount and on `startOfDayHour` change. | ~40 |
| `web/src/features/medication/MedicationScreen.tsx` | EDIT — replace `const todayIso = logicalToday(Date.now(), startOfDayHour);` (line 79) with `const todayIso = useLogicalToday(startOfDayHour);`. | 2-line delta |
| `web/src/utils/__tests__/useLogicalToday.test.ts` | NEW — Vitest with `vi.useFakeTimers()`. Cases: initial value matches `logicalToday(Date.now(), sod)`; advancing past `endOfLogicalDayMs` produces a re-render with the new ISO; advancing within a logical day does NOT re-render; `startOfDayHour` prop change re-keys correctly. | ~80 |
| `CHANGELOG.md` | EDIT — add `Unreleased ▸ Fixed` entry covering the Android + web fix. Trailing newline. | ~10 added |

**Total: ~470 LOC across 10 files**, with ~290 LOC in new tests and ~180 LOC
in production code. Bigger than the launch prompt's 30-80 LOC estimate
because we're (a) introducing a shared helper rather than patching in place
twice, (b) covering both Android and web, and (c) adding test coverage that
includes the boundary-crossing case the existing suite has no equivalent for.
The shared helper is the strongest defense against the same bug shape
recurring in the next refactor.

#### Web parity scope decision (broadened on 2026-04-26)

Initial Phase 2 plan scoped the web fix to the medication site only, in line
with the launch prompt's narrow phrasing. On Phase 2 sign-off, scope was
broadened to all 4 web sites carrying the same `const todayIso = logicalToday(Date.now(), startOfDayHour);`
construction-time-snapshot shape:

| Web site | Shape | Action |
|----------|-------|--------|
| `web/src/features/medication/MedicationScreen.tsx:79` | identical | adopt `useLogicalToday` (in scope per bug report) |
| `web/src/features/today/TodayScreen.tsx:75` | identical (bundled into a `settingsStartOfDay` object) | adopt `useLogicalToday` (broadened) |
| `web/src/features/mood/MoodLogModal.tsx:46` | identical | adopt `useLogicalToday` (broadened) |
| `web/src/features/checkin/MorningCheckInCard.tsx:26` | identical | adopt `useLogicalToday` (broadened) |

The hook is small, the swap is one line per call site, and the latent bug
exists today on all four — fixing them now prevents the same shape from
shipping with the May 15 launch.

#### Out of scope (deferred follow-ups)

Documented here so they don't get sneaked into this PR:

- **Android Today / Habit / Tasklist / Widget** consumers of `util.DayBoundary`
  — same likely pattern but each has a different flow shape (combining with
  DB Flows that re-key off other inputs). Need a dedicated migration audit
  before sweeping.
- **`DailyEssentialsUseCase.kt:158-178`** — same snapshot shape for routine
  cards, schoolwork window, etc. The medication leaf inside the same
  `combine(...)` is fixed by this PR (because `MedicationStatusUseCase` is
  fixed at the source), but the other leaves stay snapshot-stale until a
  separate audit. Pre-existing, not a regression.
- **Migrating `util.DayBoundary` callers to canonical `core.time.DayBoundary`
  + `TimeProvider`.** Mechanical migration but requires `TimeProvider`
  injection across ~8 repositories; out of scope for a P0 bug fix.

#### Test approach

- **Unit:** `LocalDateFlowTest` covers the helper itself with virtual time.
- **Unit:** `MedicationTodayDateRefreshTest` covers the integration of
  `LocalDateFlow` into the `MedicationViewModel.todayDate` shape — same file
  name as the Phase 1 repro, rewritten to gate the regression. The Phase 1
  repro file is kept (renamed/repurposed) rather than deleted so the bug's
  shape is documented in the test suite forever.
- **Unit:** `MedicationStatusUseCaseTest` flow-test for `observeDueDosesToday`.
- **Unit (web):** `useLogicalToday.test.ts` with `vi.useFakeTimers()`.
- **Instrumentation:** No new androidTest. Existing medication androidTests
  don't cover boundary-crossing (no time-travel infrastructure in place);
  unit-level virtual-time tests are the right granularity for this fix.
- **Manual gate:** Before opening PR, re-run the Phase 1 repro test against
  the fixed build — must invert (the test file's assertions were originally
  designed so passing = bug exists; rewritten file's assertions are designed
  so passing = bug fixed). This is the "verify by re-running the repro"
  gate from the launch prompt.

#### Web parity decision

**Yes, ship in the same PR.** The medication site has the identical bug
shape. The other 3 web sites (Today, Mood, MorningCheckIn) share the broken
pattern but are outside the bug scope — defer to a follow-up. The web hook
(`useLogicalToday`) is small and self-contained.

#### CHANGELOG entry text

Under `## Unreleased` → `### Fixed`:

```markdown
- **Medication screen day boundary now respects Start-of-Day on Android +
  web.** `MedicationViewModel.todayDate` (Android) and `MedicationScreen`
  (web) previously snapshotted the logical date at flow / component
  construction time and never refreshed when the wall-clock crossed the
  user's configured SoD boundary. Doses logged before SoD reset would
  linger on today's slot cards; new doses after SoD would land under
  yesterday's `medication_tier_states.log_date`; the screen would briefly
  empty out at calendar midnight (instead of at SoD) on every fresh
  subscription because the StateFlow's initial value hard-coded `SoD=0`.
  A new `core.time.LocalDateFlow` helper (Android) + `useLogicalToday`
  React hook (web) wires the logical date to a wall-clock ticker that
  re-emits at every logical-day boundary. The same fix corrects
  `MedicationStatusUseCase.observeDueDosesToday()` and the Today screen's
  Daily Essentials card downstream of it. Persisted dose timestamps were
  always correct; no migration. See
  `docs/audits/MEDICATION_SOD_BOUNDARY_AUDIT.md`.
```

---

## Phase 2 deliverable — gated

Phase 2 complete. Sections 2-4 written. **Stopping here for Section 4
sign-off before Phase 3 (implementation).**

Per the launch prompt's Phase 2 gate, do not write any production / test
code without explicit approval of the fix proposal above. Specific
decisions you may want to override:

- The shared helper approach (vs. patching in place twice).
- Web parity scope (medication only vs. all 4 surfaces).
- Test scope (unit only vs. add an instrumentation test).
- Whether to gate v1.6.1 release on this fix or carve a v1.6.0.x patch.

---

## Phase 3 — Implementation

Phase 2 was approved on 2026-04-26 with web parity scope **broadened to
all 4 surfaces** (option 3). Implementation landed in five commits on
branch `fix/medication-sod-boundary`:

| Commit | Subject | Files | Tests |
|--------|---------|-------|-------|
| `bc956b45` | test(medication): gate SoD-boundary fix with rewritten regression test | LocalDateFlow stub + rewritten `MedicationTodayDateRefreshTest` + audit scope update | 3 RED |
| `c0ff95c9` | feat(time): LocalDateFlow ticker for SoD-aware logical-day Flow | `LocalDateFlow.kt` impl + `LocalDateFlowTest.kt` | 7 GREEN |
| `67427a1a` | fix(medication): wire MedicationViewModel + MedicationStatusUseCase to LocalDateFlow | wiring + additive flow test in `MedicationStatusUseCaseTest` | full :app:testDebugUnitTest GREEN |
| `00236dfc` | fix(web): useLogicalToday hook + adopt at all 4 affected sites | `useLogicalToday.ts` + 4 site swaps + Vitest | 320/320 GREEN, `tsc -b` clean |
| _(this commit)_ | docs(changelog): medication SoD boundary fix entry | CHANGELOG `Unreleased ▸ Fixed` + Phase 3 closeout | n/a |

### Verification before opening PR

- `:app:testDebugUnitTest` — full app suite passes locally on the final
  wiring commit.
- `vitest run` — 320/320 tests pass on the final web commit.
- `tsc -b` — clean.
- Phase 1 reproduction inverted: `MedicationTodayDateRefreshTest`
  (originally designed so passing meant the bug existed) was rewritten
  to assert the FIXED contract; tests fail on `bc956b45` (RED gate),
  pass on `c0ff95c9` and onward.

### Scope deviations from Phase 2

- **Web parity widened to 4 sites** (medication + Today + Mood +
  MorningCheckIn) per Phase 2 sign-off option 3. Each adoption is a
  single-line swap; total web-side delta ~150 LOC including the new
  hook + its tests.
- **No instrumentation test added.** Phase 2 already noted this — the
  unit-virtual-time tests cover boundary crossings at the right
  granularity; androidTest has no time-travel infrastructure.
- **Total LOC** landed at ~520, slightly above the Phase 2 estimate of
  ~470 because the `MedicationStatusUseCaseTest` flow test came in
  fuller than budgeted (MockK-based DAO fakes carried more setup).

## Phase 4 — Post-merge summary

PR #798 squashed to `main` on 2026-04-26 at 05:56:37Z as commit
`747cc4ed`. Five branch commits (`bc956b45` → `2fc91893`) collapsed
into a single squash commit on `main`.

### Final scope deviation

- **Web parity widened from 1 site to 4** per Phase 2 sign-off
  option 3 — covered medication + Today + Mood + MorningCheckIn in a
  single PR rather than carrying the latent bug into next PR's
  blast radius.
- **Total LOC** landed at ~520 (Phase 2 estimate ~470). The overrun
  came from the `MedicationStatusUseCaseTest` flow test being
  fuller than budgeted (MockK DAO scaffolding) and the audit doc
  itself growing as the trace turned up the sister
  `DailyEssentialsUseCase` propagation path. Both worth the spend.
- **No deviations from the test-first commit pattern.** RED gate
  landed in `bc956b45` with 3 failing assertions; flipped to GREEN
  on `c0ff95c9` (LocalDateFlow impl) and stayed GREEN through the
  rest of the chain.
- **Branch was rebased twice during CI** by the auto-update-branch
  workflow as PRs #800 and #801 landed on `main` mid-cycle. Each
  re-triggered a fresh CI run — no interventions needed; the
  pattern handled it.

### Repro after fix — NEGATIVE (the bug doesn't reproduce)

On `main` @ `747cc4ed`:

```
:app:testDebugUnitTest \
  --tests com.averycorp.prismtask.core.time.LocalDateFlowTest \
  --tests com.averycorp.prismtask.ui.screens.medication.MedicationTodayDateRefreshTest \
  --tests com.averycorp.prismtask.domain.usecase.MedicationStatusUseCaseTest

→ BUILD SUCCESSFUL
```

`MedicationTodayDateRefreshTest` is the inverted Phase 1 repro — the
file's assertions originally proved the bug existed (passing meant
broken); the rewrite asserts the FIXED contract (passing means
fixed). All 3 assertions pass on `main`. The `LocalDateFlow`
boundary-crossing tests pass. The new `MedicationStatusUseCase`
flow-integration test passes. Web `useLogicalToday.test.ts` 4/4
passes via `vitest run`.

### Memory entries persisted

Two entries added to project auto-memory (`~/.claude/projects/.../memory/`):

1. `feedback_localdateflow_for_logical_day_flows.md` — architectural
   note: when a Compose surface needs "what day are we on" reactively,
   reach for `core.time.LocalDateFlow` (or web's `useLogicalToday`
   hook). The snapshot-once `MutableStateFlow(currentLocalDateString(...))`
   pattern is exactly the bug this audit fixed; future copy/paste
   should be flagged.
2. `feedback_repro_first_for_time_boundary_bugs.md` — process note:
   for any time-boundary bug (SoD, midnight, DST, timezone) the
   Phase 1 repro is non-negotiable, because the symptom only
   re-surfaces at boundary crossings — a fix shipped without a
   confirmed repro is a fix that probably doesn't fix.

### Follow-up backlog (deferred, not in this PR)

Recorded in §4 "Out of scope" but worth re-stating here:

- Sweep audit of `util.DayBoundary` callers in
  `TodayViewModel`, `TaskListViewModel`, `MorningCheckInViewModel`,
  `WidgetDataProvider`, the schedulers, the repositories, and
  `NaturalLanguageParser` — same shape suspected, different flow
  plumbing in each.
- `DailyEssentialsUseCase.kt:158-178` — same snapshot for routine
  cards / schoolwork / windows. Medication leaf got fixed by this
  PR; the rest stay snapshot-stale.
- Migration audit for moving the legacy `util.DayBoundary` callers
  to canonical `core.time.DayBoundary` + `TimeProvider`.

`LocalDateFlow` is the first production caller of canonical
`core.time.DayBoundary`. The migration path is open.



