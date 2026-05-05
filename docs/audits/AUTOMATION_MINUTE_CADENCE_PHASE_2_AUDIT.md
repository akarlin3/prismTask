# Automation Minute Cadence — Phase 2 Audit

Phase 1 of the audit-first workflow for moving time-based automation
triggers (`TimeOfDay` + `DayOfWeekTime`) from `PeriodicWork(15min)`
random-phase scheduling with exact-minute equality matching to
**per-minute cadence** (Path C in the operator's path-A/B/C framing).

No code changes in Phase 1. This doc enumerates verdicts on the v2 prompt's
B.1–B.7 hypothesis points + STOP-A through STOP-F evaluations, then scopes
Phase 2 implementation.

## Operator decision (locked)

Path C selected with full knowledge of trade-offs. Confirmed:

- ~290 LOC estimate (not "~10 LOC" as v1 prompt assumed).
- 15× wakeup multiplier vs current `PeriodicWork(15min)`.
- Doze rate-limiting collapses A-vs-C latency delta on sleeping devices
  (~9 min light Doze, hours deep Doze). Path C delivers <1-min latency
  only on **awake** devices.
- Path A (widen matcher to 15-min window, simpler) is the audit's
  GREEN-RECOMMENDED fix per `AUTOMATION_VALIDATION_T2_T4_AUDIT.md` (PR
  #1093). Operator chose Path C anyway for awake-device responsiveness.

## Pre-conditions note — PR #1095 absence

The v2 prompt cites `docs/audits/AUTOMATION_MINUTE_CADENCE_AUDIT.md` as
"PR #1095, 254 lines, merged May 4 evening." That file does NOT exist in
`docs/audits/` and there is no PR #1095 in `git log` (last merged PR is
#1094 → `7060a2f F.7 architectural cleanup batch`). The substantive
recon claims the prompt cites (alarm namespace map, current
worker-scheduling shape, matcher line numbers, sibling primitive
inventory) all verify against current `main` source — see § Premise
re-verification below. Treat the prompt's "PR #1095" as the v2
*operator's working notes* rather than a merged audit doc; this Phase 1
doc replaces it as the canonical recon for the cadence change.

## Recon findings

### A.1 Drive-by detection — clean

`git log --all --oneline -S "scheduleNext"` and `... -S
"computeNextMinuteBoundary"` against `app/src/main/java/com/averycorp/prismtask/`:
both return zero results. `AutomationTimeTickWorker.kt` has no
`setInitialDelay` and no `OneTimeWorkRequestBuilder`. No prior PR has
shipped Path C in any form — STOP-A does not fire.

### A.2 Parked-branch sweep — clean

`git branch -r | grep -iE "automation|cadence|minute"` returns only
`origin/claude/automation-minute-cadence-6zCEN` (this branch). No orphan
implementation to rescue.

### A.3 Shape-grep — confirms premises hold

- `grep -n "computeAlignedDelayMs\|setInitialDelay" app/src/main/java/com/averycorp/prismtask/automation/`
  → directory does not exist. `AutomationTimeTickWorker` lives at
  `app/src/main/java/com/averycorp/prismtask/workers/`, not under
  `automation/`. No setInitialDelay anywhere.
- `grep -n "PeriodicWork.*15\|15.*MINUTES"` → confirms
  `PrismTaskApplication.kt:475–483` schedules
  `PeriodicWorkRequestBuilder<AutomationTimeTickWorker>(15, MINUTES)` with
  `ExistingPeriodicWorkPolicy.UPDATE` under unique name
  `"automation_time_tick"`.
- Alarm namespace audit (full enumeration below) confirms `1_000_000+`
  is free.

### A.4 Sibling-primitive (e) axis — no expansion

OneTimeWork self-rescheduling siblings already in tree:

- `workers/DailyResetWorker.kt` (canonical reference, used by Phase 2)
- `notifications/OverloadCheckWorker.kt`
- `notifications/CognitiveLoadOverloadCheckWorker.kt`
- `notifications/BatchUndoSweepWorker.kt`
- `notifications/BriefingNotificationWorker.kt`
- `notifications/EveningSummaryWorker.kt`
- `notifications/ReengagementWorker.kt`
- `notifications/WeeklyAnalyticsWorker.kt`
- `notifications/WeeklyHabitSummaryWorker.kt`
- `notifications/WeeklyReviewWorker.kt`
- `notifications/WeeklyTaskSummaryWorker.kt`

Pattern is well-established and idiomatic — Phase 2 reuses, not
establishes.

Other periodic workers (intentionally NOT in scope):

- `WidgetUpdateManager` — 15-min refresh cadence, intentional, separate
  scope.
- `AutoArchiveWorker` — 24h cadence, separate domain.

No sibling automation worker should change cadence with this work. STOP-F
does not fire.

## Premise re-verification (vs v2 prompt's "PR #1095" claims)

| # | Premise | Verdict | Source |
|---|---------|---------|--------|
| D.1 | `AutomationTimeTickWorker.kt` is plain `PeriodicWork(15min)` random-phase, no `setInitialDelay`, no `computeAlignedDelayMs` | ✅ confirmed | `app/.../workers/AutomationTimeTickWorker.kt:23-37`; `PrismTaskApplication.kt:475-483` |
| D.2 | Matcher is private instance `triggerMatches`, exact-minute equality at lines 189 (TimeOfDay) and 193 (DayOfWeekTime) | ✅ confirmed | `app/.../domain/automation/AutomationEngine.kt:181-209` |
| D.3 | Alarm namespace 1_000_000+ is free | ✅ confirmed | full enumeration below |
| D.4 | OneTimeWork self-rescheduling pattern present in `DailyResetWorker` + sibling notification workers | ✅ confirmed | `app/.../workers/DailyResetWorker.kt:113-132` plus 10 sibling notification workers |
| D.5 | No prior PR has shipped the cadence change | ✅ confirmed | A.1 + A.2 sweep clean |

**Alarm namespace map (verified):**

| Range | Owner | File |
|-------|-------|------|
| `400_000+` | Medication legacy per-med | `notifications/MedicationReminderScheduler.kt:254` |
| `500_000+` | Medication slot INTERVAL | `notifications/MedicationIntervalRescheduler.kt:215` |
| `600_000+` | Medication per-med INTERVAL override | `notifications/MedicationIntervalRescheduler.kt:216` |
| `700_000+` | Habit follow-up + Medication slot CLOCK | `notifications/HabitFollowUpReceiver.kt:91`, `notifications/MedicationClockRescheduler.kt:321` |
| `800_000+` | Habit follow-up dismiss + Medication per-(med, slot) CLOCK override | `notifications/HabitFollowUpReceiver.kt:92`, `notifications/MedicationClockRescheduler.kt:322` |
| `900_000+` | Notification escalation chains | `notifications/EscalationScheduler.kt:96` |
| **`1_000_000+`** | **FREE** | — (not used by Phase 2 either; primitive choice is WorkManager, not AlarmManager) |

Note: Phase 2 uses `WorkManager.OneTimeWork` (primitive choice C.1a), so
the alarm namespace question is moot — flagged here for completeness so
future cadence-bound work can pick up the namespace map without re-doing
recon.

## Implementation hypothesis verdicts

### B.1 Scheduling primitive — C.1a OneTimeWork self-rescheduling (confirmed)

Reuse, not establish. WorkManager is the established background-work
framework; AlarmManager-based primitives (C.1b/c) would introduce new
namespace pressure and Doze allowance complexity (`setExactAndAllowWhileIdle`)
without buying anything Path C asks for. 11 sibling workers already use
this pattern.

### B.2 Implementation shape — mirror DailyResetWorker

Reference: `DailyResetWorker.kt:113-132` (canonical). Phase 2 transcribes
the pattern with these specifics:

```kotlin
// AutomationTimeTickWorker.doWork():
override suspend fun doWork(): Result {
    val cal = Calendar.getInstance()
    bus.emit(AutomationEvent.TimeTick(
        hour = cal.get(Calendar.HOUR_OF_DAY),
        minute = cal.get(Calendar.MINUTE)
    ))
    schedule(applicationContext)   // self-reschedule
    return Result.success()
}

// companion:
fun computeNextMinuteBoundaryDelayMs(now: Long = System.currentTimeMillis()): Long {
    val msIntoMinute = now % 60_000L
    val msUntilNext = 60_000L - msIntoMinute
    return msUntilNext.coerceAtLeast(0L)
}

fun schedule(context: Context, now: Long = System.currentTimeMillis()) {
    val delay = computeNextMinuteBoundaryDelayMs(now)
    val request = OneTimeWorkRequestBuilder<AutomationTimeTickWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        UNIQUE_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request
    )
}
```

`UNIQUE_WORK_NAME = "automation_time_tick"` is reused intentionally — see
B.3 migration note.

### B.3 Migration from `PeriodicWorkRequest(15min)` (confirmed safe)

App-launch path in `PrismTaskApplication.kt:474-486` swaps from
`enqueueUniquePeriodicWork(name, UPDATE, ...)` to
`enqueueUniqueWork(name, REPLACE, ...)`. Same unique name
`"automation_time_tick"` collapses the previous `PeriodicWork`
registration into the new `OneTimeWork` chain on first launch after
update — WorkManager treats unique names as the dedup key regardless of
the underlying request type. No explicit `cancelUniqueWork` needed
because `REPLACE` policy supersedes anything previously registered under
that name.

Race against in-flight `PeriodicWork` execution at app-launch:
`REPLACE` cancels any pending work before enqueuing the replacement; if
a `PeriodicWork` is currently `RUNNING`, it completes before the
replacement takes effect (per WorkManager semantics). Since the existing
`AutomationTimeTickWorker.doWork()` is a single `bus.emit()` call
(idempotent — duplicate ticks at the same minute have no observable
effect because the matcher is hour+minute equality, not "fire once per
event"), even a brief overlap is harmless.

### B.4 Matcher behavior — preserved unchanged (confirmed)

`AutomationEngine.triggerMatches` lines 187-200 stay byte-for-byte
identical. Cadence is the only fix; the worker now fires every minute,
so any minute the user sets is hit by the matcher within ~1 minute on an
awake device.

Regression-gate: existing rules at "aligned" minutes (e.g. 9:00, 14:30)
continue to fire as they do today. There is no behavior change for any
rule whose minute happens to coincide with the worker's previous random
phase — they keep working, they just stop being the only minutes that
work.

### B.5 User-facing copy — minimal, deferred

Operator default per v2 prompt: minimal copy update only. Phase 2
recommends NO copy change in this PR. Rationale:

- Doze is OS-wide; users with battery-optimization on already understand
  background work has no real-time guarantees.
- Adding rule-editor copy ("may fire up to ~9 minutes late if device is
  asleep") risks signaling a regression where there's an upgrade — most
  rules will fire faster, not slower.
- If field telemetry post-merge surfaces user confusion about Doze
  behavior, file follow-on with copy updates targeted at observed
  questions.

If operator wants copy in this PR, propose adding a one-line subtitle
under the rule-editor TimePicker: "Fires within ~1 minute of this time
when device is awake." Open question for operator — see § Open
questions.

### B.6 Rate-limiter interaction — no change needed (confirmed)

Math walk-through:

- `TimeOfDay` matcher is exact-minute equality (`tick.hour ==
  trigger.hour && tick.minute == trigger.minute`). At per-minute cadence
  the worker fires 1,440 times/day, but each unique `TimeOfDay` rule
  matches AT MOST ONCE per day (the one minute the rule's hour:minute
  matches the wall clock).
- `DayOfWeekTime` similarly matches at most once per day, and only on
  selected days.
- Per-rule daily cap of 100 (`AutomationRateLimiter.kt:62`) is
  comfortably above 1 fire/day for time-based rules.
- Global hourly cap of 500 is unaffected — fires/hour can't exceed the
  number of distinct rules whose minute lines up with the current
  minute, which is bounded by the user's rule count.

Edge case: if `REPLACE` policy or Doze deferral causes the worker to
fire twice within the same wall-clock minute, a `TimeOfDay` rule could
fire twice in one minute (once per worker run). Daily cap of 100 still
generous (this would require ~50 same-minute double-fires per day, far
beyond what Doze irregularity could plausibly cause). Not a STOP, but
flagged for potential future tightening if telemetry shows real
duplicate-fire incidents.

STOP-D does not fire.

### B.7 Test coverage shape

Phase 2 adds:

- **`AutomationTimeTickWorkerScheduleTest.kt`** (pure JVM, mirrors
  `DailyResetWorkerScheduleTest.kt` pattern) — covers
  `computeNextMinuteBoundaryDelayMs` boundary math:
  - mid-minute → time until next minute
  - exactly at second 0 of a minute → 60_000ms (full minute, not 0)
  - second 59.999 of a minute → ~1ms
  - delay never negative
  - consecutive-call no-drift over 5 minutes
- **`AutomationEngineMatcherTest.kt`** *(if not already present)* —
  regression-gate that `TimeOfDay` matcher fires on hour+minute
  equality. *Note: matcher is currently a `private` instance method;
  adding a test may require @VisibleForTesting + internal visibility
  bump. Phase 2 will assess and either (a) test via the public bus API
  or (b) bump visibility.* Decision deferred to implementation time.
- **Migration smoke test** — Robolectric or instrumentation: register
  `PeriodicWork(15min)` under `"automation_time_tick"`, call
  `AutomationTimeTickWorker.schedule(context)`, assert
  `WorkManager.getWorkInfosForUniqueWork("automation_time_tick")`
  returns exactly one `OneTimeWork` (not the previous Periodic).
- **Doze test — SKIPPED** per memory note (Cowork sandbox cannot
  manipulate non-rooted AVD clocks). Documented as deferred manual S25
  verification, deferred to D6 device session bundle.
- **Rate-limiter interaction test — NOT added.** Per B.6 verdict, no
  rate-limit math changes; existing `AutomationRateLimiterTest.kt`
  coverage is unaffected.

## STOP-conditions evaluated

| STOP | Condition | Fired? | Notes |
|------|-----------|--------|-------|
| A | Path C already shipped in main since "PR #1095" | ❌ | Drive-by + parked-branch sweep clean. v2 prompt's "PR #1095" doesn't exist as a merged audit; flagged in pre-conditions. |
| B | "PR #1095" premises shifted | ❌ | All 5 substantive premises (D.1-D.5) verify against current `main`. The doc-numbering inconsistency does not invalidate the underlying recon. |
| C | OneTimeWork self-rescheduling has hidden cost | ❌ | 11 sibling workers use this pattern in production. No known reliability issue. `REPLACE` policy is the standard idiom (`DailyResetWorker.kt:129`). |
| D | Rate-limiter math broken at per-minute cadence | ❌ | Walked through in B.6 — exact-minute matcher caps natural fire rate at 1×/day for `TimeOfDay`. |
| E | Doze makes Path C deliver no awake-device improvement over Path A | ❌ | Path A doesn't address the bug at all for non-aligned minutes (e.g. 8:23) — random-phase `PeriodicWork(15min)` would still miss most rules even with widened matcher window unless the worker is also phase-aligned. Path C is a strict improvement on awake devices and a wash on Doze-sleeping devices. |
| F | (e)-axis surfaces sibling primitive that should change | ❌ | All siblings are intentional cadences (daily, weekly, 15-min widget refresh). No expansion. |

**No STOP fires. Phase 2 proceeds.**

## Phase 2 scope

### Files touched

| File | Change shape | LOC est |
|------|-------------|---------|
| `app/.../workers/AutomationTimeTickWorker.kt` | Add `companion object` with `UNIQUE_WORK_NAME`, `computeNextMinuteBoundaryDelayMs()`, `schedule()`. Call `schedule(applicationContext)` at end of `doWork()`. Update kdoc. | ~50 |
| `app/.../PrismTaskApplication.kt` | Replace `PeriodicWorkRequestBuilder<AutomationTimeTickWorker>(15, MINUTES)` + `enqueueUniquePeriodicWork(name, UPDATE, ...)` with `AutomationTimeTickWorker.schedule(this)`. Drop unused `PeriodicWorkRequestBuilder` / `ExistingPeriodicWorkPolicy` imports if no other call site uses them (`AutoArchiveWorker` still does — keep imports). | ~10 |
| `app/.../domain/automation/AutomationTrigger.kt` | Update stale kdoc comments: "5-minute granularity" → "1-minute granularity" on `TimeOfDay` and `DayOfWeekTime` docstrings. | ~4 |
| `app/src/test/.../workers/AutomationTimeTickWorkerScheduleTest.kt` | NEW. Mirror `DailyResetWorkerScheduleTest.kt` shape: TimeZone-pinned, hand-built `now` instants, boundary cases. | ~120 |
| `app/src/test/.../workers/AutomationTimeTickWorkerMigrationTest.kt` | NEW (Robolectric). Smoke test for `PeriodicWork → OneTimeWork` swap under same unique name. | ~50 |

**Estimated total: ~234 LOC.** (Lower than v2 prompt's ~290 estimate
because matcher is unchanged and rate-limiter needs no edits.)

### Test scope

- `./gradlew :app:testDebugUnitTest` GREEN (existing + new pure-JVM
  tests).
- Robolectric migration test GREEN.
- `./gradlew :app:assembleDebugAndroidTest` GREEN (catches DAO-gap
  pattern per memory #20 — no DAO changes here, low risk).
- Existing `AutomationEngine` / `AutomationRateLimiter` tests
  unaffected.

### Out-of-scope (deferred, NOT auto-filed per memory #30)

- Battery instrumentation for WorkManager wakeup tags. Re-trigger:
  post-Path-C merge if Phase E telemetry watch surfaces battery
  regression in the 15× wakeup multiplier.
- Doze deep-sleep manual S25 device test. Defer to D6 device session
  bundle.
- Duplicate-fire suppression within same wall-clock minute (B.6 edge
  case). Re-trigger: telemetry showing real incidents post-merge.
- Path A fallback (matcher widening). Path C is strictly more capable;
  Path A only re-enters scope if Path C's wakeup cost shows
  unacceptable battery impact in field telemetry.

### Open questions for operator

1. **B.5 user-facing copy.** Default in this audit: ship NO copy change
   in Phase 2. If operator wants the rule-editor TimePicker subtitle
   ("Fires within ~1 minute of this time when device is awake"),
   surface as an explicit ask before Phase 2 implementation begins.
   Otherwise Phase 2 proceeds copy-clean.
2. **B.7 matcher visibility for testing.** `triggerMatches` is private.
   Phase 2 can either test via public bus API (preferred — black-box
   regression gate) or bump visibility with `@VisibleForTesting`.
   Default: test via bus API. Open if operator prefers white-box.

## Improvement table

| # | Item | Wall-clock savings | Implementation cost | Ratio |
|---|------|--------------------|--|--|
| 1 | Cadence change (Path C, this PR) | ~7 min average latency improvement on awake-device time-based rules (was 0–15 min, will be 0–1 min) | ~234 LOC, single PR | high |

(Single-item table — this audit is scoped to one improvement.)

## Anti-patterns (flag, do not auto-fix)

- **Stale kdoc in AutomationTrigger.kt** mentions "5-minute granularity"
  for both `TimeOfDay` and `DayOfWeekTime`, but actual cadence is 15
  min. Phase 2 corrects to "1-minute granularity" to match Path C
  reality.
- **kdoc on AutomationTimeTickWorker** says "15 minutes (the WorkManager
  minimum periodic interval)." After Phase 2 the worker is a
  self-rescheduling OneTimeWork chain, not periodic at all — kdoc rewrite
  is part of Phase 2.

## Phase 3 + Phase 4 (pre-merge per CLAUDE.md)

Phase 3 verification gates and Phase 4 Claude Chat handoff block fire
**before merge**, appended to the implementation PR description as soon
as it opens — not after CI green or merge. Per CLAUDE.md Repo
conventions § "Audit-first Phase 3 + 4 fire pre-merge."
