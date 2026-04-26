# Legacy `util.DayBoundary` sweep — per-caller triage

**Trigger:** PR #798 (Apr 26) fixed the MedicationScreen SoD-boundary bug by
introducing `core.time.LocalDateFlow` (Android) + `useLogicalToday` (web). PR
#798's audit doc parked 7 legacy callers that still use
`util.DayBoundary.currentLocalDateString(hour)` (or sister helpers) in
patterns that may exhibit the same snapshot-anti-pattern bug.

**Goal:** classify each of the 7 callers as HAS-THE-BUG / NO / DEFER, then
ship per-caller migration PRs for the HAS-THE-BUG ones. Mega-PR forbidden.

**Process:** audit-first. No migration code in Phase 1. Phase 2 only proceeds
on user approval of the per-caller triage below.

**Bug shape recap:** A `Flow`/`StateFlow` exposing "today's date" computed once
per upstream emission, with no clock-tick subscription to refresh on wall-clock
crossing. Concretely the patterns to watch for:

```kotlin
// SHAPE A — direct StateFlow snapshot
.getDayStartHour().map { DayBoundary.startOfCurrentDay(it) }.stateIn(...)

// SHAPE B — flatMapLatest with snapshotted locals
.getDayStartHour().flatMapLatest { hour ->
    val todayLocal = DayBoundary.currentLocalDateString(hour)  // snapshot
    combine(...) { ... uses todayLocal ... }
}

// SHAPE C — combine lambda that recomputes only on its source emissions
combine(prefA, prefB, repoFlow) { a, b, repo ->
    val todayStart = DayBoundary.startOfCurrentDay(...)  // refreshes on
                                                         // upstream emit, not
                                                         // on wall-clock
    ...
}
```

**Not the bug shape:**

```kotlin
// One-shot at user-action time — the snapshot IS the contract.
suspend fun onUserTap() {
    val todayStart = DayBoundary.startOfCurrentDay(...)
    repo.write(date = todayStart)
}

// One-shot at scheduling time — alarm fires on wall-clock, not on Flow.
suspend fun reschedule() {
    val todayLocal = DayBoundary.currentLocalDateString(...)
    val countToday = dao.countOnce(todayLocal)
    if (countToday < target) alarmManager.setExact(...)
}

// Worker render — widget rendering is stateless; Flow has nowhere to live.
suspend fun renderWidget() {
    val startOfDay = DayBoundary.startOfCurrentDay(...)
    return WidgetData(startOfDay, ...)
}
```

The distinguishing question for each call site: **is the date used as a
reactive value the UI observes over time, or as a snapshot at the moment of an
event?** Snapshot semantics are correct in many places; the bug shape is
specifically the reactive-Flow shape that doesn't tick on wall-clock crossing.

---

## Triage results — summary

| # | Caller | Verdict | Severity | Migration |
|---|--------|---------|----------|-----------|
| 1 | `TodayViewModel` | HAS THE BUG (×2) | **CRITICAL** | PROCEED |
| 2 | `WidgetDataProvider` | NO (architectural — STOP) | n/a | STOP |
| 3 | `TaskListViewModel` | HAS THE BUG | HIGH | PROCEED |
| 4 | `MorningCheckInViewModel` | NO — STOP | n/a | STOP |
| 5 | `DailyEssentialsUseCase` | HAS THE BUG | HIGH | PROCEED |
| 6a | `HabitReminderScheduler` | NO — STOP | n/a | STOP |
| 6b | `MedicationReminderScheduler` | NO — STOP | n/a | STOP |
| 6c | `MedicationIntervalRescheduler` | NO — no usage | n/a | STOP |
| 7 | `NaturalLanguageParser` | NO — already on canonical | n/a | STOP |

**3 PROCEED, 6 STOP.** All STOP rationales documented per-caller below so
future audits don't re-flag them.

**Drive-by-fix check:** Ran `git log -p -S 'util.DayBoundary' --since='2026-04-26' -- <file>`
for each PROCEED candidate. NONE of the 3 has-the-bug callers were touched by
intervening PRs. PRs #800-#809 (the auto-bump CI chain) didn't touch any
caller-relevant Kotlin. Confirmed all 3 still have the bug.

---

## 1. `TodayViewModel` — HAS THE BUG (×2), CRITICAL

**File:** `app/src/main/java/com/averycorp/prismtask/ui/screens/today/TodayViewModel.kt`

### Premise verification

`util.DayBoundary` imported at line 48. Eight call sites total (lines 170,
372, 376, 381, 385, 414, 417, 1120, 1153). Two of them are the bug shape;
the rest are correct point-of-use snapshots.

### Drive-by-fix check

Empty — no `util.DayBoundary` change in this file since 2026-04-26. Bug stands.

### Snapshot anti-pattern classification — bug instance #1

`dayStart` and `dayEnd` `StateFlow<Long>` (lines 370-386) — **SHAPE A**:

```kotlin
private val dayStart: StateFlow<Long> = taskBehaviorPreferences
    .getDayStartHour()
    .map { DayBoundary.calendarMidnightOfCurrentDay(it) }       // ← snapshot
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DayBoundary.calendarMidnightOfCurrentDay(0)              // ← SoD=0
    )

private val dayEnd: StateFlow<Long> = taskBehaviorPreferences
    .getDayStartHour()
    .map { DayBoundary.calendarMidnightOfNextDay(it) }
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DayBoundary.calendarMidnightOfNextDay(0)
    )
```

Identical shape to PR #798's `MedicationViewModel.todayDate` (and identical
`SoD=0` initial-value defect at lines 376 and 385). The `.map` evaluates
once per `getDayStartHour()` emission; nothing refreshes the value when the
wall-clock crosses the user's SoD. `dayStart` and `dayEnd` are then consumed
by overdue/today/tomorrow filtering elsewhere in the VM (line 296+), so the
entire Today-screen day-rendering pipeline is keyed off stale boundaries.

### Snapshot anti-pattern classification — bug instance #2

Morning-check-in banner reactive pipeline (lines 162-201) — **SHAPE C**:

```kotlin
combine(
    morningCheckInPreferences.featureEnabled(),
    morningCheckInPreferences.bannerDismissedDate(),
    checkInLogRepository.observeAll()
) { enabled, dismissedDate, logs ->
    val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
    val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)   // ← snapshot
    val todayIso = java.time.LocalDate.now().format(...)            // ← snapshot
    val alreadyCheckedInToday = logs.any { it.date >= todayStart }
    val hour = java.util.Calendar.getInstance().get(...HOUR_OF_DAY) // ← snapshot
    val beforePromptHour = hour < 11
    val dismissedToday = dismissedDate == todayIso
    enabled && beforePromptHour && !alreadyCheckedInToday && !dismissedToday
}.distinctUntilChanged().collect { … }
```

The lambda only re-fires when one of `featureEnabled` / `bannerDismissedDate` /
`observeAll()` emits. None of those re-emit on wall-clock crossing. So a user
who opens Today at 11pm, leaves it open across SoD, sees a banner state frozen
at 11pm's `todayStart` / `todayIso` / `hour`. `beforePromptHour` is computed
from a stale `Calendar.getInstance()` reading too — separate bug not in scope
for this audit but worth noting.

### User-facing surface

- The Today screen's overdue / today / tomorrow / planned task sections
  (driven by `dayStart` / `dayEnd`) — render the wrong logical day's tasks
  past SoD.
- The morning check-in banner — fails to appear correctly across logical-day
  rollover. Affects users who keep the app open overnight (rare but not zero).

### Risk classification

**CRITICAL.** Today screen is the app's launch surface. Phase F testers
opening the app each morning would see yesterday's tasks under "Today's
Tasks" until the app fully cold-starts (Compose state cleared) — exactly
the failure shape PR #798 fixed for medication.

### Migration approach

- Inject `LocalDateFlow` into `TodayViewModel` constructor.
- Replace `dayStart` / `dayEnd` StateFlows with derived projections of
  `localDateFlow.observe(...)` — convert `LocalDate` → `startOfCurrentDay
  ` epoch via `core.time.DayBoundary` Instant helpers, OR (cleaner) introduce
  a `dayStartMillisFlow(sodSource): Flow<Long>` helper on `LocalDateFlow`
  that returns `logicalDayStart(...).toEpochMilli()`.
- Replace the morning-check-in banner `combine` upstream to include the
  `localDateFlow.observeIsoString()` so the lambda re-fires on wall-clock
  crossing.
- Suspend helpers `currentStartOfToday()` / `currentEndOfToday()` (lines
  411-417) stay as-is — they're called from write paths and snapshot
  semantics are correct there.
- Estimate: ~40-80 LOC (medium) — touches the most consumers of any caller
  in this sweep.

### Wrong-premise check

Premise correct: `TodayViewModel` has the bug, in two distinct instances,
both surfacing user-visible date mis-rendering.

### Recommendation

**PROCEED** — ship as `fix/today-vm-localdateflow`. Two bug instances ship
in one PR (same VM, same scope) but each has its own RED→GREEN regression
test.

---

## 2. `WidgetDataProvider` — STOP, NO

**File:** `app/src/main/java/com/averycorp/prismtask/widget/WidgetDataProvider.kt`

### Premise verification

`util.DayBoundary` imported at line 10. Multiple call sites (lines 146, 151,
186, 228, 232, 237, 238, 248, 252, 266, 294, 352).

### Drive-by-fix check

Not applicable — STOP verdict.

### Snapshot anti-pattern classification

Every call site is inside a `suspend fun getXxxData(context: Context)` method
that is invoked **per widget refresh** by the Glance framework. Each
invocation reads `dayStartHour` / `dayStartMinute` from preferences and
computes `startOfDay = DayBoundary.startOfCurrentDay(...)` fresh.

This is **NOT** the bug shape. Widgets are stateless render surfaces — there
is no `Flow` to observe and no `StateFlow` to be stale against. Glance's
contract is "the framework calls your provider, you return data, the
framework renders." Each render is a one-shot snapshot.

### Architectural caveat (separate concern, not this audit)

The widget framework refreshes widgets via:
- `WidgetUpdateManager.updateAllWidgets()` — debounced explicit refreshes on
  data mutations (covers in-app changes).
- A periodic `WorkManager` schedule (per CLAUDE.md "8 home-screen widgets …
  with per-instance config" — typical periodicity ~15 min).
- OS-driven re-renders on device state changes.

There's no scheduled refresh AT the user's SoD boundary specifically.
Confirmed via grep: `DAY_CHANGE | ACTION_DATE_CHANGED | setOnNextDay |
scheduleNextWidgetUpdate | setExactAndAllowWhileIdle | setAlarmClock` find
no SoD-boundary alarm scheduling in `WidgetUpdateManager.kt` or
`WidgetRefreshWorker.kt`.

This means the widget DOES exhibit a SoD-boundary staleness window of up to
~15 min (until the next periodic refresh fires). But that is **not** the
snapshot anti-pattern fix's domain — migrating to `LocalDateFlow` would
not help, because the widget can't keep a long-lived Flow subscription.
The right fix for widget SoD staleness (if it's a problem worth fixing) is
in `WidgetUpdateManager` — schedule a one-shot exact alarm at the next
logical-day boundary that calls `updateAllWidgets()` when it fires.

### Risk classification

n/a — STOP. The bug-shape audit doesn't apply. The separate widget-refresh
audit (if pursued) is its own scope.

### Wrong-premise check

Premise was **incorrect**: `WidgetDataProvider` does not have the
LocalDateFlow-shaped bug. Snapshot is the architecturally correct pattern
for widget data providers. Document so future audits don't re-flag.

### Recommendation

**STOP.** Do not migrate. If widget SoD staleness becomes a Phase F bug
report, open a separate audit for `WidgetUpdateManager` to add an
SoD-boundary alarm — that's a different fix shape entirely.

---

## 3. `TaskListViewModel` — HAS THE BUG, HIGH

**File:** `app/src/main/java/com/averycorp/prismtask/ui/screens/tasklist/TaskListViewModel.kt`

### Premise verification

`util.DayBoundary` imported at line 27. Five call sites (lines 293, 294, 912,
913, 914).

### Drive-by-fix check

Empty. Bug stands.

### Snapshot anti-pattern classification

`dayStartFlow` (lines 291-294) — **SHAPE A**:

```kotlin
private val dayStartFlow: StateFlow<Long> = taskBehaviorPreferences
    .getDayStartHour()
    .map { DayBoundary.startOfCurrentDay(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
             DayBoundary.startOfCurrentDay(0))
```

Identical shape to PR #798's bug. SoD=0 initial-value defect too (line 294).

Consumed by:
- `overdueCount` (line 296-298) — counts tasks with `dueDate < startOfToday`.
  Stale `startOfToday` → wrong overdue count.
- `groupByDate(...)` (line 910-914) — groups by `startOfToday`,
  `startOfTomorrow`, `startOfDayAfterTomorrow`, `endOfWeek`. Stale
  `startOfToday` → wrong "Today" / "Tomorrow" groups.

### User-facing surface

Task list screen — main task management surface alongside Today. Tasks bucket
incorrectly past SoD ("Today" group shows yesterday's tasks; "Tomorrow"
shows today's). Overdue badge is wrong.

### Risk classification

**HIGH.** User-facing main-tab surface. Less prominent than Today
(sectioned by date but the user navigates here intentionally), but still
ships the same wrong-day class of bug.

### Migration approach

- Inject `LocalDateFlow`.
- Replace `dayStartFlow` with `localDateFlow.observe(getStartOfDay()).map { logicalDate -> ... epochMillis }`.
- Initial value: use `LocalDate.now().toString()` → epoch (same as
  `MedicationViewModel.todayDate`'s pattern from PR #798 — initial value is
  one-frame fallback, the inner flow emits SoD-correct value on subscription).
- `groupByDate(...)` reads `dayStartFlow.value` — that becomes the new
  flow's `.value`; no behavioral change at the call site.
- Estimate: ~25-40 LOC.

### Wrong-premise check

Premise correct.

### Recommendation

**PROCEED** — ship as `fix/tasklist-vm-localdateflow`.

---

## 4. `MorningCheckInViewModel` — STOP, NO

**File:** `app/src/main/java/com/averycorp/prismtask/ui/screens/checkin/MorningCheckInViewModel.kt`

### Premise verification

`util.DayBoundary` imported at line 31. Five call sites (lines 154, 179, 200,
233, 246).

### Drive-by-fix check

Not applicable — STOP verdict.

### Snapshot anti-pattern classification

Every call site is inside a `viewModelScope.launch { ... }` block in one of:

- `init` (line 154) — runs once at VM construction. Computes the morning
  check-in plan. One-shot.
- `loadCalendarEvents()` (line 179) — runs when `calendarManager.isCalendarConnected`
  fires. One-shot per invocation.
- `logMoodEnergy(...)` (line 200) — user-action handler. One-shot at tap.
- `toggleHabit(...)` (line 233) — user-action handler. One-shot at tap.
- `finalize()` (line 246) — user-action handler (final submit). One-shot.

No `StateFlow` exposing "today's date." All usage is **point-of-event**:
either VM init or explicit user action. The morning check-in flow is
designed as a brief once-per-day interaction — open, fill out, submit. It
does not span logical-day boundaries.

### Edge case considered

If the user opens the morning check-in screen at 11pm and leaves it open
through SoD without submitting, the `init`-computed plan reflects yesterday's
date. The user finishes, taps Finalize, and the `finalize()` writes a
`CheckInLog` with `date = todayStart` recomputed AT finalize time, which
correctly reflects the new logical day. So the persistence is correct; only
the displayed plan is from yesterday.

This is an edge case, not the bug class PR #798 fixed. The user explicitly
opened the morning check-in for "today" (yesterday-relative-to-now) and
left it open — the displayed plan reflecting that intent is arguably
correct UX. Documenting as a non-bug.

### Wrong-premise check

Premise was **incorrect**: `MorningCheckInViewModel` does not have the
reactive snapshot anti-pattern. All usage is one-shot at point-of-event.
Document.

### Recommendation

**STOP.** Snapshot semantics are correct for an event-driven flow. Do not
migrate.

---

## 5. `DailyEssentialsUseCase` — HAS THE BUG, HIGH

**File:** `app/src/main/java/com/averycorp/prismtask/domain/usecase/DailyEssentialsUseCase.kt`

### Premise verification

`util.DayBoundary` imported at line 20. Four call sites (lines 160-163), all
inside `observeToday()`.

### Drive-by-fix check

Empty. PR #798 fixed only the `medicationStatusUseCase.observeDueDosesToday()`
leaf inside this combine; the rest of the snapshot block (lines 160-163) was
left as-is per PR #798's own audit doc ("Out of scope").

### Snapshot anti-pattern classification

`observeToday()` (lines 158-178) — **SHAPE B**:

```kotlin
fun observeToday(): Flow<DailyEssentialsUiState> =
    taskBehaviorPreferences.getDayStartHour().flatMapLatest { dayStartHour ->
        val todayStart = DayBoundary.startOfCurrentDay(dayStartHour)            // snapshot
        val todayLocal = DayBoundary.currentLocalDateString(dayStartHour)       // snapshot
        val windowStart = DayBoundary.calendarMidnightOfCurrentDay(dayStartHour) // snapshot
        val windowEnd = DayBoundary.calendarMidnightOfNextDay(dayStartHour)     // snapshot

        combine(
            observeRoutineCard("morning", "Morning Routine", todayStart),
            observeRoutineCard("bedtime", "Bedtime Routine", todayStart),
            observeHouseworkCard(todayLocal),
            observeRoutineCard("housework", "Housework", todayStart),
            observeSchoolworkCard(todayLocal, windowStart, windowEnd),
            leisureRepository.getTodayLog(),
            medicationStatusUseCase.observeDueDosesToday(),    // ← only this
            slotCompletionDao.observeForDate(todayStart),       //   leaf was
            dailyEssentialsPreferences.hasSeenHint,             //   fixed by
            leisurePreferences.getSlotConfig(LeisureSlotId.MUSIC),  //  PR #798
            leisurePreferences.getSlotConfig(LeisureSlotId.FLEX)
        ) { args -> combineDailyEssentials(args) }
    }
```

The four locals (`todayStart`, `todayLocal`, `windowStart`, `windowEnd`) are
captured ONCE per `getDayStartHour()` emission. The `combine` underneath
re-emits whenever any of its 11 sources changes, but always with the same
stale window values for routines, housework, schoolwork, slot completion,
etc.

The `medicationStatusUseCase.observeDueDosesToday()` leaf at line 172 was
fixed by PR #798 (it now ticks via `LocalDateFlow` internally). So
medication doses correctly refresh at SoD; everything else inside the
combine stays stale. This is an inconsistent-state bug — past SoD the
medication card shows the right day, the routine cards show the wrong day.

### User-facing surface

Today screen's "Daily Essentials" section. Past SoD: morning/bedtime
routine cards, housework habit card, schoolwork card, leisure log all show
yesterday's logical day; medication card shows today's. Visible inconsistency.

### Risk classification

**HIGH.** Same severity tier as `TaskListViewModel` — high-visibility user
surface (Today screen), but the bug presents as inconsistency rather than
"today's screen is just wrong."

### Migration approach

- Inject `LocalDateFlow` into `DailyEssentialsUseCase`.
- Replace `taskBehaviorPreferences.getDayStartHour().flatMapLatest { hour -> ... }`
  with `localDateFlow.observeIsoString(taskBehaviorPreferences.getStartOfDay()).flatMapLatest { todayLocal -> ... }`.
- Re-derive `todayStart` / `windowStart` / `windowEnd` from the
  `LocalDate` value inside the lambda (need explicit zone — get from
  `TimeProvider`).
- Estimate: ~30-50 LOC.

### Wrong-premise check

Premise correct.

### Recommendation

**PROCEED** — ship as `fix/daily-essentials-localdateflow`.

---

## 6a. `HabitReminderScheduler` — STOP, NO

**File:** `app/src/main/java/com/averycorp/prismtask/notifications/HabitReminderScheduler.kt`

### Premise verification

`util.DayBoundary` imported at line 17. One call site at line 235.

### Drive-by-fix check

Not applicable — STOP verdict.

### Snapshot anti-pattern classification

```kotlin
val habits = habitDao.getHabitsWithIntervalReminder()
val todayLocal = DayBoundary.currentLocalDateString(taskBehaviorPreferences.getDayStartHour().first())
for (habit in habits) {
    val interval = habit.reminderIntervalMillis ?: continue
    val timesPerDay = habit.reminderTimesPerDay
    val todayCount = completionDao.getCompletionCountForDateLocalOnce(habit.id, todayLocal)
    if (todayCount >= timesPerDay) continue
    // ... schedule next AlarmManager fire ...
}
```

This is inside a method that runs **at scheduling time** (e.g., app startup,
after a habit edit). It computes "what is today" once, queries "how many
completions today," and decides whether to schedule another reminder.

The output is `AlarmManager` triggers at concrete wall-clock times. Those
alarms fire on the OS clock, not on a Flow. Migrating to `LocalDateFlow`
would not help — the scheduler is one-shot and doesn't observe a stream.

### Wrong-premise check

Premise was **incorrect**: snapshot is correct for one-shot scheduling logic.

### Recommendation

**STOP.** Document so future audits don't re-flag.

---

## 6b. `MedicationReminderScheduler` — STOP, NO

**File:** `app/src/main/java/com/averycorp/prismtask/notifications/MedicationReminderScheduler.kt`

### Premise verification

`util.DayBoundary` imported at line 11. One call site at line 221.

### Snapshot anti-pattern classification

```kotlin
/** Surface only to drive non-Android unit tests of the day-hour helper. */
internal suspend fun currentLogicalDateString(): String {
    val dayStartHour = taskBehaviorPreferences.getDayStartHour().first()
    return DayBoundary.currentLocalDateString(dayStartHour)
}
```

A test helper. One-shot suspending function. Used only by `*Test.kt` files
to verify the helper itself behaves. Not consumed by production code as a
reactive value.

### Wrong-premise check

Premise was **incorrect**: not a bug-shaped call site at all. Test surface.

### Recommendation

**STOP.** No migration warranted. (If the helper became reactive in the
future, that would be the bug shape — flag it then.)

---

## 6c. `MedicationIntervalRescheduler` — STOP, no usage

**File:** `app/src/main/java/com/averycorp/prismtask/notifications/MedicationIntervalRescheduler.kt`

### Premise verification

`grep -n DayBoundary` returns **zero matches**. The file does not use
`util.DayBoundary` at all (verified — the launch prompt's enumeration was
out of date for this caller).

### Recommendation

**STOP — no usage.** Document for future audits.

---

## 7. `NaturalLanguageParser` — STOP, already on canonical

**File:** `app/src/main/java/com/averycorp/prismtask/domain/usecase/NaturalLanguageParser.kt`

### Premise verification

The only `DayBoundary` import in this file is:

```kotlin
import com.averycorp.prismtask.core.time.DayBoundary as LogicalDayBoundary
```

That is the **canonical** `core.time.DayBoundary`, not the legacy
`util.DayBoundary`. Used at lines 141 and 466 to call `resolveAmbiguousTime(...)`
for "remind me at 2 AM" style NLP inputs — anchors a parsed time to the
user's logical day correctly.

### Snapshot anti-pattern classification

`resolveAmbiguousTime(...)` is a pure function called once per parse. NLP
parsing is event-driven (user types or speaks input → parser runs once).
No `Flow`, no `StateFlow`, no observed value.

### Wrong-premise check

Premise was **incorrect**: this caller never used `util.DayBoundary` (or has
already been migrated — the alias-import suggests intentional use of the
canonical helper). Both options mean nothing to do here.

### Recommendation

**STOP — already on canonical.** Document.

---

## Recommendations summary

### PROCEED — 3 callers, 3 separate PRs

| Caller | Branch name | Estimated LOC | Risk |
|--------|-------------|---------------|------|
| `TodayViewModel` | `fix/today-vm-localdateflow` | ~40-80 | CRITICAL |
| `TaskListViewModel` | `fix/tasklist-vm-localdateflow` | ~25-40 | HIGH |
| `DailyEssentialsUseCase` | `fix/daily-essentials-localdateflow` | ~30-50 | HIGH |

Each PR follows PR #798's 5-commit shape (RED test → impl → wiring → web
parity if applicable → CHANGELOG → GREEN test inversion). Total ~95-170 LOC
production change across 3 PRs.

**Web parity check:** `TodayViewModel` and `TaskListViewModel` have web
equivalents that PR #798 already migrated to `useLogicalToday` (the
broadened web-parity scope from PR #798 covered `TodayScreen.tsx`,
`MorningCheckInCard.tsx`, etc.). `DailyEssentialsUseCase` is Android-only
(Phase G is bringing daily-essentials to web; not yet shipped). So no web
parity work in any of the 3 PROCEED PRs.

### STOP — 6 callers, no migration

| Caller | STOP rationale |
|--------|---------------|
| `WidgetDataProvider` | Stateless render surface; snapshot is architecturally correct. SoD-boundary widget-refresh is a separate concern in `WidgetUpdateManager`. |
| `MorningCheckInViewModel` | Event-driven (init + user actions); snapshot at point-of-event is correct. |
| `HabitReminderScheduler` | Schedules concrete `AlarmManager` triggers; one-shot at scheduling time. |
| `MedicationReminderScheduler` | Test surface only; not a reactive consumer. |
| `MedicationIntervalRescheduler` | No `util.DayBoundary` usage at all. |
| `NaturalLanguageParser` | Already on canonical `core.time.DayBoundary`. |

Document each STOP reason here so future sweeps don't re-flag these callers.

### DEFER — none

No HAS-THE-BUG callers were judged too invasive to migrate this sweep. All
3 PROCEED migrations are template-fits.

---

## Phase 1 deliverable — gated

This document is the Phase 1 deliverable. **Stopping here for sign-off
before Phase 2.**

Per the launch prompt's audit-first gate: do not start any of the 3
migration PRs without explicit approval of:

1. The 3 PROCEED classifications (TodayViewModel, TaskListViewModel,
   DailyEssentialsUseCase).
2. The 6 STOP classifications (WidgetDataProvider, MorningCheckInViewModel,
   HabitReminderScheduler, MedicationReminderScheduler,
   MedicationIntervalRescheduler, NaturalLanguageParser) — specifically
   that future audits should not re-flag these.
3. The PR ordering (recommend CRITICAL first: `TodayViewModel` → then HIGH
   in either order).
