# Automation Minute Cadence — Phase 1 Audit (HARD STOP)

**Source:** operator-task `cc_automation_minute_cadence_audit_first_mega.md` (2026-05-04).
**Scope:** move time-based automation triggers (`TimeOfDay` + `DayOfWeekTime`) from a 15-min slot-aligned cadence to per-minute cadence (Path C), accepting the battery + Doze cost.
**Outcome:** **STOP-A fires.** Operator's pre-selection of Path C was based on a false premise about the current code state. With the corrected premise, **Path A is strictly better** on every dimension Phase 1 measured. Phase 2 does **not** auto-fire — operator re-pick required before any implementation begins.
**Audit doc length:** ~430 lines, under the 500-line cap.

---

## TL;DR for operator (read this first)

The prompt assumes "PR #1093 PR-A (May 4) shipped option (ii) — `AutomationTimeTickWorker.computeAlignedDelayMs` + `setInitialDelay`". That code does not exist. PR #1093 is a docs-only audit (`AUTOMATION_VALIDATION_T2_T4_AUDIT.md`); the implementation PR-A has not landed. The current state is the **original buggy baseline** described in that audit:

- `AutomationTimeTickWorker.kt` — plain `PeriodicWorkRequest(15, MINUTES)` enqueued in `PrismTaskApplication.kt:475–483`. No `setInitialDelay`. Random worker phase from process start.
- `AutomationEngine.kt:187–209` — `triggerMatches` (private instance method, **not** a companion `matchTrigger`) still uses exact-minute equality at lines 189 and 193.

**The bug today is not "rules at 8:23 silently no-op." The bug is "rules at *almost any* minute silently no-op."** Only the four (random, install-dependent) minute values that happen to coincide with the worker's floating phase have any chance of firing.

Implication: the bug is broader, the fix surface is narrower, and the merged audit's recommended fix (Path A — widen matcher to 15-min window) addresses the entire bug at ~30–60 LOC. Path C delivers a stronger guarantee at 5–10× the cost, and the "any-minute" promise is hollow under Doze rate-limiting on real devices in deep sleep.

**Recommended action:** re-pick Path A (or document why Path C still wins despite the corrected premise). If operator confirms Path C anyway, this audit doc reframes the implementation plan and Phase 2 can fire with eyes open.

---

## Operator decision (as filed)

Path C (1-min cadence) selected over Path A (matcher tolerance widening) and Path B (UI constraint). Operator accepted battery cost.

**Filed rationale:** "users can set rules for any minute (e.g., 8:23) without silent no-op."

**Why this audit re-questions it:** the rationale assumed (i) the 15-min slot-aligned cadence had shipped (it has not) and (ii) the only failure mode was non-aligned minutes (the actual failure mode is broader and Path A solves it).

---

## Phase 1 — Recon findings

### A.1 Drive-by detection (memory #18 axis (a))

`git log --all --oneline -- app/src/main/java/com/averycorp/prismtask/workers/AutomationTimeTickWorker.kt`:

```
cc382eb feat(automation): IFTTT-style rules engine — storage, evaluator, executor, safety, minimal UI (#1056)
```

Single commit. Worker has not been touched since PR #1056. No subsequent commit added `computeAlignedDelayMs` / `setInitialDelay`.

`git log --all --oneline --grep="cadence\|computeAligned\|setInitialDelay\|TimeTickWorker"`:

```
d5c77b1 docs(audits): Phase 1 — Automation validation T2 + T4 failure triage (#1093)
cc382eb feat(automation): IFTTT-style rules engine — storage, evaluator, executor, safety, minimal UI (#1056)
50113e2 chore(release): bump to v1.7.88 — sync on reconnect + 30 s periodic floor (#945)
d3b3336 fix(sync): trigger sync on reconnect + 30 s periodic floor (#944)
```

PR #1093 is the only reference and it is docs-only — diff confirms it touches `docs/audits/AUTOMATION_VALIDATION_T2_T4_AUDIT.md` and nothing else.

### A.2 Parked-branch sweep (memory #18 axis (b))

`git branch -r`:

```
origin/claude/audit-minute-cadence-TG1tS  (this branch)
origin/main
```

No parked automation/cadence/minute branches. STOP-E does not fire.

### A.3 Shape-grep (memory #18 axis (c))

- `grep -rn "computeAlignedDelayMs\|setInitialDelay" app/`: 12 matches across `WeeklyAnalyticsWorker`, `OverloadCheckWorker`, `WeeklyReviewWorker`, `BatchUndoSweepWorker`, `EveningSummaryWorker`, `BriefingNotificationWorker`, `WeeklyTaskSummaryWorker`, `WeeklyHabitSummaryWorker`, `CognitiveLoadOverloadCheckWorker`, `ReengagementWorker`, `DailyResetWorker`. **Zero matches in `AutomationTimeTickWorker.kt`.** Premise D.1 falsified.
- `grep -n "PeriodicWorkRequest" app/src/main/java/com/averycorp/prismtask/PrismTaskApplication.kt`: scheduling at line 475 with `15, TimeUnit.MINUTES` and no chained `setInitialDelay`.
- No other site assumes the automation worker fires at slot-aligned minutes (checked all 14 PeriodicWorkRequest usages).

### A.4 Sibling-primitive sweep (memory #18 axis (e))

Other workers / schedulers in the time-trigger family:

| Component | Cadence primitive | Aligned? | Bundle candidate? |
|-----------|-------------------|----------|-------------------|
| `AutomationTimeTickWorker` | `PeriodicWork(15min)` | ❌ random phase (the bug) | YES — primary scope |
| `WidgetRefreshWorker` | `PeriodicWork(15–240min)` user-configurable | n/a — refresh-frequency, not trigger-fire-time | NO |
| `WeeklyAnalyticsWorker` | `OneTimeWork` + `setInitialDelay` to next Monday | YES (week-aligned) | NO — different use case |
| `OverloadCheckWorker` / `CognitiveLoadOverloadCheckWorker` | `OneTimeWork` self-rescheduling | YES (precomputed delay) | NO — different cadence semantics |
| `MedicationReminderScheduler` | `AlarmManager.setExactAndAllowWhileIdle` | per-slot exact | NO — separate domain, separate namespace |
| `MedicationClockRescheduler` | `AlarmManager.setExactAndAllowWhileIdle` | per-(med, slot) exact | NO |
| `EscalationScheduler` | `AlarmManager.setExactAndAllowWhileIdle` (base 900_000) | per-fire exact | NO |
| `DailyResetWorker` | `OneTimeWork` self-rescheduling at next user start-of-day | YES | NO |

**Finding:** the codebase already has a working `OneTimeWork` self-rescheduling pattern (`DailyResetWorker`, `WeeklyAnalyticsWorker`, `OverloadCheckWorker`). Path C's C.1a primitive choice would be reusing an established pattern, **not** establishing a new one. Premise D.3 is therefore wrong in the conservative direction — the pattern exists.

**Finding:** alarm namespace `900_000+` is **not free** — `EscalationScheduler.kt:96` (`BASE_REQUEST_CODE = 900_000`) and `MedicationMigrationRunner.kt:200` ("daily-time (+900_000) alarms") both occupy it. Path C using AlarmManager would need `1_000_000+`. Premise D.4 falsified.

**Bundle pre-approval check:** none of the siblings need the same change. No expansion.

### B. Path verdicts (with corrected premise)

#### Path A — Widen matcher tolerance to 15-min window — **GREEN, RECOMMENDED**

Implementation shape (already specified in `AUTOMATION_VALIDATION_T2_T4_AUDIT.md` Part D, Option (i)):

```kotlin
private fun timeMatches(triggerHour: Int, triggerMin: Int, tick: AutomationEvent.TimeTick): Boolean {
    val triggerMinutes = triggerHour * 60 + triggerMin
    val tickMinutes = tick.hour * 60 + tick.minute
    val delta = ((tickMinutes - triggerMinutes) % 1440 + 1440) % 1440
    return delta < 15
}
```

- LOC: ~30–60 including 3 unit tests (`AutomationEngineTimeOfDayTest`, `AutomationEngineDayOfWeekTimeTest`, `AutomationTimeTickWorkerTest`).
- Risk: low. `dailyFireCount` guard in `AutomationRuleEntity` already prevents double-fire.
- Doze interaction: irrelevant. The 15-min worker is already Doze-throttled; the matcher widening doesn't change scheduling.
- User-visible behavior: rules fire within 0–15 min after configured wall-clock time. Acceptable per worker doc-comment intent ("lines up within one tick").
- Battery cost: zero delta vs current.
- Test infrastructure: works cleanly under Robolectric / JVM unit test (no AlarmManager / Doze involvement).

#### Path B — Constrain UI to {0, 15, 30, 45} — **YELLOW, NOT RECOMMENDED ALONE**

Implementation shape:

- `AutomationRuleEditViewModel.kt:127` change `coerceIn(0, 59)` to `coerceIn(0, 59).let { (it / 15) * 15 }` (or use a dropdown).
- `AutomationRuleEditScreen.kt:159–166, 177–184` change minute `OutlinedTextField` to `DropdownPicker` over {0, 15, 30, 45}.
- LOC: ~30–80.

**Why YELLOW alone:** restricts user choice rather than fixing the bug. Even after Path B, the worker still has random phase from process start, so seeded :00/:15/:30/:45 rules would not fire reliably either — Path B without Path A or option (ii) bundled doesn't make any rule fire reliably. Path B + option (ii) (slot-align worker) bundled is functionally equivalent to Path A but worse UX (4-value picker).

**Why YELLOW not RED:** Path B is a valid future polish layer on top of Path A — once Path A makes any-minute "fire within 15 min" work, the editor could still constrain to nice values for cleaner UX. Filed as a deferred F.5 follow-on per memory #30 defer-minimization.

#### Path C — Switch to per-minute cadence — **YELLOW, NOT RECOMMENDED**

Implementation shape after corrected premise:

- **C.1 primitive:** `OneTimeWorkRequest` chain (C.1a) — `AlarmManager` (C.1b/c) is needlessly heavy for a 1-min cadence and the 900_000 namespace is taken (would need 1_000_000+ or refactor). C.1a reuses the established pattern from `DailyResetWorker` / `OverloadCheckWorker` — verified in A.4.
- **C.2 Doze:** explicit asterisk. `OneTimeWorkRequest` chains under Doze can defer to the next maintenance window (~9 min on light Doze, hours on deep Doze). The "any-minute" promise is **hollow when device is asleep**. User-facing copy must reflect "fires within 1 min when device is awake; subject to Android Doze when asleep." Compare with Path A's worse-case 15-min window which is also Doze-deferred — so the actual end-user-visible delta between A and C is "0–1 min" vs "0–15 min" *when the device is awake*, and roughly equivalent under Doze.
- **C.3 rate-limiter:** at 1-min cadence the worker fires 15× more often. Per-rule 100/day cap (`AutomationRateLimiter.kt`) is hit in 100 minutes (1h40m) if a rule's matcher would fire every minute. The matcher only fires when `tick.hour == trigger.hour && tick.minute == trigger.minute`, so per-rule cap is fine for time-triggered rules (1 fire per matched minute = 1/day max). Global 500/hr cap is fine. Net rate-limiter risk: minimal. ✅
- **C.4 battery:** 60 wakeups/hour vs 4 — 15× multiplier. No existing Crashlytics / Firebase Performance visibility on `WorkManager` wakeup count by tag (would need a new instrumentation hook). Post-launch we cannot answer "did this regress battery." Filed as deferred F.6 follow-on, NOT in scope per memory #30.
- **C.5 migration:** cancel old `automation_time_tick` `PeriodicWorkRequest`, register new chain. `WorkManager.cancelUniqueWork("automation_time_tick")` then enqueue new `OneTimeWorkRequest` with `enqueueUniqueWork` and `ExistingWorkPolicy.REPLACE`. Brief overlap window possible (~1 min) but not a correctness issue (matcher is idempotent per-minute).
- **C.6 tests:** unit (matcher unchanged regression-gate), unit (chain self-rescheduling), Robolectric (worker emits + reschedules). Doze tests SKIP per memory note (sandbox cannot manipulate non-rooted AVD clock).

**LOC estimate:** ~150–300 (refined from prompt's "~10 LOC" — 10 LOC is wrong by an order of magnitude). Operator-task spec's 150 cap would be exceeded.

**Why YELLOW not GREEN:**
- Path A delivers the user-visible bug fix (rules-don't-fire-at-all → rules-fire-within-15-min) at 1/5 the cost of Path C.
- Path C's incremental benefit (15-min latency → 1-min latency) is hollow under Doze, which is the deep-sleep state where most morning/evening rules are scheduled to fire.
- 15× wakeup multiplier on a feature that's not yet validated post-launch is premature optimization for a precision the platform cannot deliver.
- No existing battery instrumentation. We would ship a 15× wakeup regression with no dashboard to detect it.

**Why YELLOW not RED:** Path C is implementable and would deliver the minute-precision promise *for awake devices*. If operator's user research says "users care about 8:23 vs 8:24 specifically and accept the battery hit," Path C is technically tractable. The verdict is YELLOW because the cost/benefit ratio is bad given the corrected premise, not because the work is impossible.

### C. STOP-conditions evaluated

| STOP | Fires? | Why |
|------|--------|-----|
| **STOP-A** — Path A strictly better | **YES, FIRES** | Trivial fix, no double-fire (dailyFireCount guard), no Doze asterisk, already audit-recommended in PR #1093, user-visible behavior delta vs Path C is negligible under Doze. **Recommend re-pick.** |
| STOP-B — Path B strictly better given engine-change blast radius | NO | Path B alone doesn't fix the bug (worker still has random phase). Path A is simpler still. |
| STOP-C — Doze makes Path C deliver no improvement over status quo | PARTIAL | Doze makes Path C deliver ~no improvement *over Path A* in the device-asleep case (both subject to Doze). Awake-device improvement is real (15 min → 1 min latency). Folds into STOP-A's "Path A close enough." |
| STOP-D — sibling primitive needs the same change | NO | Quad-sweep (e) found no sibling that should change with this work. |
| STOP-E — cadence change attempted/parked in prior branch | NO | Single branch, no orphans. |

**Premise-failure HARD STOP** (audit-first universal rule, supersedes per-item STOPs): premises D.1 and D.4 are false. STOP-and-report fires regardless of per-Path verdicts.

### D. Premise verification table (memory #22 bidirectional)

| # | Premise | Verification command | Outcome |
|---|---------|----------------------|---------|
| D.1 | PR #1093 PR-A is current cadence baseline (shipped option ii) | `git log -p` on `AutomationTimeTickWorker.kt`; grep for `computeAlignedDelayMs` | **FALSE.** Worker only touched in PR #1056. Symbol does not exist. PR #1093 is docs-only. |
| D.2 | `matchTrigger` exact-minute equality at `AutomationEngine.kt:191–199` | Read `AutomationEngine.kt` | **PARTIALLY CORRECT.** Method is `triggerMatches` (private instance), not `matchTrigger` (companion). Exact-minute equality confirmed at lines 189 (TimeOfDay) and 193 (DayOfWeekTime). Line drift acceptable. |
| D.3 | No existing `OneTimeWorkRequest` self-rescheduling pattern | grep `OneTimeWorkRequest` + `setInitialDelay` chain | **FALSE in conservative direction.** Pattern exists in `DailyResetWorker`, `WeeklyAnalyticsWorker`, `OverloadCheckWorker`. Path C would reuse, not establish. |
| D.4 | Alarm namespace 900_000+ free | grep `900_000` | **FALSE.** `EscalationScheduler.BASE_REQUEST_CODE = 900_000`; medication daily-time alarms also at +900_000. Path C with AlarmManager would need `1_000_000+`. |
| D.5 | `WeeklyAnalyticsWorker.schedule` reads `WeeklySummarySchedule` from prefs | Read `WeeklyAnalyticsWorker.kt` | CORRECT — slider-driven cadence pattern exists; useful precedent for Path C if pursued. |
| D.6 | No seeded built-in template uses non-aligned minutes | `grep "timeOfDay(" AutomationStarterLibrary.kt` | CORRECT. All 9 seeded times are :00 or :30 (`07:00, 21:00, 08:00, 20:00, 14:00, 07:30, 13:00, 21:30, 22:00`). No behavior change for seeded rules under Path A or Path C. |

### E. Operator-prompt scope drift items (axis (d), memory #18)

The operator prompt also referenced "shared `AutomationEngine.matchTrigger` companion." That companion does not exist in the current code; the matcher is a private instance method `triggerMatches` (at lines 181–209). This is cosmetic — the matcher is implemented, just under a different name and shape than the prompt described. Likely the prompt was authored against a planned PR-A shape from the merged audit's Part D, where Option (i)'s recommended refactor extracted a shared `timeMatches` helper. That extraction has also not shipped.

### F. Doze rate-limiting reality check (load-bearing)

Android's Doze rate-limit reference (per Android developers documentation, well-known constraint):
- Light Doze: ~9 min minimum window between background work fires when device is idle but awake-ish.
- Deep Doze (after ~1 hr stationary + screen off): maintenance windows extend to hours.

For automation rules typically configured at "morning" / "evening" / "weekly" wall-clock times — the device is in deep Doze at exactly those times. Path A's 15-min window matches Doze's natural granularity. Path C's 1-min cadence is *more often* dropped by Doze than fired by it. The end-user-visible promise of Path C ("rule at 8:23 fires at 8:23") is therefore **unreliable** in the realistic case (phone on bedside table, screen off since 23:00).

This is not a hypothetical — it's the documented Android platform behavior. STOP-A subsumes the spirit of STOP-C: Path A delivers within-Doze-granularity correctness, Path C promises sub-Doze precision the platform doesn't deliver.

---

## Phase 2 — BLOCKED

Per audit-first hard rule ("STOP-and-report on wrong premises is the one real halt"), Phase 2 does **not** auto-fire. Premises D.1 and D.4 are false; the operator's selection of Path C was based on the false premise that the 15-min slot-aligned worker had shipped and was the source of the silent no-op. The actual current-state bug is broader, and Path A (which the merged audit recommended) addresses it at trivial cost.

**Required operator input before Phase 2:**

1. **Re-pick** with corrected premise. Default recommendation: Path A.
2. If operator still selects Path C with eyes open (corrected premise + accepted Doze hollow-promise + accepted 15× wakeup multiplier without battery instrumentation), confirm and Phase 2 fires under the implementation plan in section "Path C implementation plan (if operator confirms)" below.
3. If operator selects Path A, this audit doc closes the F.5 follow-on (UI minute-picker constraint) as STOPPED-superseded, and a fresh Phase 2 fires for Path A. The merged audit `AUTOMATION_VALIDATION_T2_T4_AUDIT.md` Part D Option (i) is the implementation plan; this audit doc would not need to re-derive it.

### Path C implementation plan (filed for completeness, gated behind operator re-confirm)

If operator re-confirms Path C with corrected premise:

- **Files touched (estimate):**
  - `AutomationTimeTickWorker.kt` — switch from `CoroutineWorker` body emitting once to body that emits + chains next fire via `WorkManager.enqueueUniqueWork(REPLACE)`. Compute next-minute-boundary delay in `doWork()` end. (~30 LOC)
  - `PrismTaskApplication.kt:474–487` — replace `PeriodicWorkRequestBuilder<…>(15, MINUTES)` with `OneTimeWorkRequestBuilder<…>().setInitialDelay(nextMinuteBoundary, MILLISECONDS)`. Add `WorkManager.cancelUniqueWork("automation_time_tick")` migration call before re-enqueue. (~20 LOC)
  - `AutomationEngine.kt:181–209` — no change to matcher (exact-minute semantics preserved; cadence change makes any minute reachable). (0 LOC)
  - `AutomationRuleEditScreen.kt` — copy refresh: "Fires at the configured time when device is awake. Android battery optimization may delay rules by several minutes when device is asleep." (~5 LOC copy; will need string resource entries)
  - `app/src/test/java/.../workers/AutomationTimeTickWorkerTest.kt` — new file: verify `doWork()` emits TimeTick with current `Calendar` hour/minute, verify chain re-enqueue fires with `ExistingWorkPolicy.REPLACE`. (~80 LOC)
  - `app/src/test/java/.../domain/automation/AutomationEngineTest.kt` — new file: regression-gate exact-minute matcher behavior (TimeOfDay + DayOfWeekTime + Manual + Composed + EntityEvent). (~150 LOC)
  - `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md` — drift fix: doc says "5-min granularity"; correct to "1-min granularity (fires every minute when device is awake; subject to Android Doze when asleep)." (~3 LOC)

- **Total LOC:** ~290 (vs prompt's ~10 LOC estimate, vs Path A's ~60 LOC).

- **Migration safety:** brief overlap window between old `PeriodicWork` cancellation and new `OneTimeWork` first fire. Worst case: one missed minute or one duplicate minute — matcher's `dailyFireCount` guard absorbs duplicates; missed-minute is acceptable for a one-time install upgrade.

- **Test scope deferred:** Doze interaction. AVD cannot reliably simulate Doze (no privileged time manipulation in the cowork sandbox). Manual verification on physical device deferred to post-merge D6.

- **Memory edit candidate (memory #30):** if Path C ships at ~290 LOC vs prompt's ~10 LOC estimate, that's a 30× delta worth a memory entry — pattern: "operator-approved 'simple cadence change' is rarely simple when the change crosses scheduling primitives." Not auto-filed pending Phase 2.

---

## Phase 3 — Bundle summary

**No PRs merged in this audit cycle.** Phase 2 is gated. Bundle summary fills in once operator re-picks and Phase 2 lands. Placeholder structure:

| Item | Risk | Reco | Why |
|------|------|------|-----|
| Premise D.1 falsified (PR #1093 PR-A did not ship) | **RED** | STOP-and-report | Hard halt per audit-first protocol. |
| Premise D.4 falsified (alarm namespace 900_000 taken) | YELLOW | Inform Path C plan | If operator re-picks Path C with AlarmManager, must use 1_000_000+. |
| Path A — widen matcher tolerance | **GREEN** | RECOMMEND | ~60 LOC, low risk, audit-recommended in PR #1093. |
| Path B — UI minute constraint | YELLOW | DEFER | Polish layer; needs Path A or option (ii) bundled to actually work. |
| Path C — 1-min cadence | YELLOW | DEFER unless operator confirms with eyes open | ~290 LOC, 15× battery multiplier, Doze hollow-promise. |
| F.5 follow-on (UI minute-picker constraint) | DEFERRED | Closes if Path A ships (constraint moot) | — |
| F.6 follow-on (battery instrumentation) | DEFERRED | Filed only if Path C ships | Re-trigger: post-Path-C merge. |

**Anti-patterns flagged:**
- "Operator-task framing assumes a code state that didn't ship." This audit's primary finding. Any future operator prompt that claims "PR #X shipped Y" should either include the SHA or note "claimed-shipped — verify." Memory candidate.
- "10 LOC estimate for cadence change" — see Path C implementation plan above; actual is ~290 LOC. Documented in operator-prompt's own anti-pattern note ("if Path C's actual LOC is dramatically different from the ~10 LOC initial estimate, that's worth capturing"). Memory candidate.

---

## Phase 4 — Claude Chat handoff

Emitted as the last block of this run, in the conversation outside this doc, per audit-first protocol.

---

## Open questions for operator

1. **Re-pick decision:** Path A (default, ~60 LOC, audit-recommended) or Path C with corrected premise (~290 LOC, 15× wakeup, Doze hollow-promise)?
2. **F.5 closure:** if Path A picked, confirm F.5 follow-on closes as STOPPED-superseded.
3. **Doze user-facing copy:** if Path C picked, confirm rule editor copy reflects Doze constraint ("fires within 1 min when device awake; subject to Android battery optimization when asleep").
4. **Battery instrumentation scope:** if Path C picked, file F.6 follow-on for `WorkManager` wakeup-tag visibility, or accept post-launch blind?
