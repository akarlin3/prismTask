# Automation Validation Tests 2 + 4 — Failure Triage Audit

**Source:** May 3 2026 AVD validation run (post-PR #1074); engine validation D5 item.
**Severity:** Test 2 = P2 (trigger surface broken; entity-event workaround exists). Test 4 = P0/P1 (cross-device sync regression; broader blast radius).
**Outcome of validation run:** Tests 1, 3, 5, 6 GREEN. Tests 2, 4 RED.
**Audit references:** memories #16, #18 (incl. (e) sibling-primitive axis), #22, #28, #30.
**Phase 1 cap:** 500 lines. **Phase 2 NOT auto-fired** (operator-task HARD STOP overrides skill default).

---

## Why audit-first format with 2-PR fan-out

Two distinct subsystems, presumed two distinct fixes (Part C verifies):

- **Test 2** is engine-internal: `AutomationTimeTickWorker` scheduling + `TimeOfDayMatcher` dispatch wiring on the engine side.
- **Test 4** is cross-cutting sync: `SyncService` dispatch + Firestore subcollection + push/pull listener registration. Touches PR #1070's claimed-shipped state, so memory #22 verification is mandatory.

If Phase 1 reveals shared root cause (e.g. AutomationEventBus broken; Hilt graph missing module), promote to single bundled PR. **Per Part C below, no shared cause was found** — 2-PR fan-out proceeds: PR-A first (lower stakes), PR-B second (wider blast radius).

---

## Phase 1 — Combined Failure Audit

### Part A — Test 2 (TimeOfDay) findings

#### A.1 — Worker scheduling + dispatch (RED)

`AutomationTimeTickWorker` lives at `app/src/main/java/com/averycorp/prismtask/workers/AutomationTimeTickWorker.kt`. Implementation is fine — emits `AutomationEvent.TimeTick(hour, minute)` from `Calendar.getInstance()` and returns `Result.success()`. Dispatch path through `AutomationEventBus` is identical to the working entity-event path (verified vs Test 1 GREEN).

Scheduling site: `PrismTaskApplication.startAutomationEngine()` lines 474–487:

```kotlin
val workRequest = PeriodicWorkRequestBuilder<AutomationTimeTickWorker>(15, TimeUnit.MINUTES).build()
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "automation_time_tick", ExistingPeriodicWorkPolicy.UPDATE, workRequest
)
```

**Defect**: no `setInitialDelay`, no clock-aligned phase. PeriodicWorkRequest fires 15 min after enqueue, then every 15 min thereafter from that floating offset. If the app boots at 06:47:23, ticks land at minute values `2, 17, 32, 47` — never `0`. Doze-mode flex window can shift further. There is no mechanism to align worker fires to wall-clock `00/15/30/45`.

#### A.2 — TimeOfDayMatcher (RED, primary defect)

`AutomationEngine.kt:186–193`:

```kotlin
is AutomationTrigger.TimeOfDay -> {
    val tick = event as? AutomationEvent.TimeTick ?: return false
    tick.hour == trigger.hour && tick.minute == trigger.minute
}
is AutomationTrigger.DayOfWeekTime -> {
    val tick = event as? AutomationEvent.TimeTick ?: return false
    if (tick.hour != trigger.hour || tick.minute != trigger.minute) return false
    // ... day check
}
```

**Defect:** exact-minute equality. Combined with A.1's random worker phase, the practical fire probability for any rule configured at e.g. `07:00` is ≈ **0%** for that day (2/17/32/47 ≠ 0). Even a rule at `07:15` would only fire if app boot luck happened to land worker phase at minute=15, which is < 7%.

**Design-intent vs impl drift (axis (d)):** worker doc-comment at `AutomationTimeTickWorker.kt:13–20` says:

> "...so a `[AutomationTrigger.TimeOfDay]` only fires when the minute lines up **within one tick**. This is acceptable for v1 — sub-15-minute precision for time-based rules adds AlarmManager complexity that doesn't pay off..."

Phrase "lines up within one tick" implies a tolerance window (±tick-interval / 2, or rounding to tick boundary). Implementation is rigid integer equality — no tolerance. The design clearly intended best-effort-within-tick; the matcher does not implement it.

#### A.3 — Architecture-doc drift (axis (d), separate)

`docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md:148`:

> "AutomationTriggerScheduler PeriodicWorkRequest fires → emits TimeTick events at 5-min granularity"

Two drift findings:

1. Worker is named `AutomationTimeTickWorker`, not `AutomationTriggerScheduler`. Zero matches for `AutomationTriggerScheduler` in source.
2. Doc claims 5-min granularity. Implementation uses 15 min (PeriodicWorkRequest's API floor).

Both worth a one-line doc touch-up in PR-A but not load-bearing.

#### A.4 — Sibling-primitive axis (e): DayOfWeekTime

`AutomationEngine.kt:191–199` (excerpted in A.2). DayOfWeekTime gates on `tick.hour != trigger.hour || tick.minute != trigger.minute` with the **same exact-minute equality** before checking day-of-week. Same defect, same root cause. **PR-A fix must cover both matcher branches.** This is exactly the axis-(e) catch the recon-quad-sweep was added for in memory #18.

#### A.5 — Starter library TimeOfDay coverage (GREEN)

`AutomationStarterLibrary.kt`: 9 templates use TimeOfDay/DayOfWeekTime triggers (07:00, 21:00, 08:00, 20:00, 07:30, 13:00, 21:30, 14:00, 22:00). Only `builtin.morning_routine` (07:00) is in `FIRST_INSTALL_SEED_IDS`, and it seeds with `enabled = false`. So Test 2 must have manually enabled or imported a TimeOfDay rule (consistent with operator's reported procedure).

Library is fine. Defect is downstream in the matcher.

#### A.6 — Test coverage (RED)

No `AutomationEngineTest`. No `AutomationTimeTickWorkerTest`. The exact-minute / random-phase incompatibility was never exercised by CI. PR-A must add at least one regression test.

#### A.7 — Sibling entity-event dispatch (GREEN, comparison)

Entity-event path uses identical `AutomationEventBus` machinery — same `_events.tryEmit()`, same `bus.events.collect { ... }` subscriber in `AutomationEngine.start()`. Test 1 GREEN proves bus + engine + handlers work. The defect is *not* the dispatch path — it is the matcher's tolerance semantics combined with worker scheduling phase.

### Part B — Test 4 (cross-device sync) findings

#### B.1 — PR #1070 git-log verification (memory #22 — NOT ESCALATED)

Commit `57f9784b7760cd3b6ec78ed887d770297834eaa8` (May 2 23:18 UTC) titled "feat(automation): SyncService routing for automation_rule (A6) (#1070)" actually shipped the routing it claimed. Diff against `main`:

- **AutomationRuleDao.kt**: added `getAllOnce()` and `setCloudId(id, cloudId)` (2 methods).
- **SyncService.kt**: 36 lines added across all 7 touchpoints.

Memory #22 escalation **not triggered** for PR #1070 routing wire-up. (One signature mismatch flagged in B.2 below is a dormant code smell, not a falsified-shipped claim.)

#### B.2 — SyncService 7-touchpoint side-by-side (mostly GREEN, one nit)

| # | Touchpoint | automation_rule | boundary_rule | Status |
|---|-----------|-----------------|---------------|--------|
| 1 | `initialUpload` (uploadRoomConfigFamily) | L533–539 | L526–532 | MATCH |
| 2 | `collectionNameFor` map | L1162 | L1161 | MATCH |
| 3 | `pushCreate` switch | L1321–1323 | L1317–1319 | MATCH |
| 4 | `pushUpdate` switch | L1491–1493 | L1487–1489 | MATCH |
| 5 | `pullRemoteChanges` (pullRoomConfigFamily) | L2526–2546 | L2511–2524 | ENHANCED — automation_rule has `naturalKeyLookup` (added by PR #1077 for dedup); boundary_rule does not. Functionally a superset. Not a defect. |
| 6 | listener registration array | L3264 | L3263 | MATCH |
| 7 | `processRemoteDeletions` reverse map + delete dispatch | L3352, L3397 | L3351, L3396 | MATCH |

**All 7 touchpoints wired symmetrically.** No missed routing. No regression vs canonical sibling.

DAO signature drift (dormant): `BoundaryRuleDao.setCloudId(id, cloudId?, now)` takes 3 args; `AutomationRuleDao.setCloudId(id, cloudId)` takes 2 with non-null `cloudId`. **Not called from anywhere** — the cloud-id binding actually flows through `SyncMetadataDao.upsert()` inside `uploadRoomConfigFamily`. Cosmetic cleanup at best; not Test 4's cause.

#### B.3 — SyncMapper round-trip (GREEN)

`automationRuleToMap` (L1258–1275) emits all 16 entity fields; `mapToAutomationRule` (L1277–1299) deserializes with safe casts and sensible defaults. `updatedAt` round-trips → LWW intact. Symmetric with `boundaryRuleToMap`. No omissions.

#### B.4 — Firestore collection shape + auth rules (UNKNOWN — operator gating)

Collection ref is per-user subcollection: `firestore.collection("users").document(uid).collection("automation_rules")` (SyncService.kt:122–123, helper `userCollection`). Path mirrors boundary_rules exactly.

`firestore.rules` in repo is **explicitly stubbed for emulator only** (file header lines 1–6: "Stubbed rules for local emulator use only. The production Firestore rules live in the Firebase console and were NOT imported into this repo..."). **Production rules are out-of-repo and unauditable from this codebase.**

If the operator's AVD pair points at production Firestore (not emulator), and production rules don't grant read/write on `users/{uid}/automation_rules`, every push silently 403s and every listener returns no docs. This would explain Test 4 RED end-to-end with no code defect. **This is the highest-leverage hypothesis Phase 2 cannot resolve without operator input** — see Part F.

#### B.5 — Repository → SyncTracker integration (GREEN)

`AutomationRuleRepository`:
- L43–71 `create()` calls `syncTracker.trackCreate(id, "automation_rule")` (L69)
- L75 `setEnabled()` → `trackUpdate`
- L80 `update()` → `trackUpdate`
- L85 `delete()` → `trackDelete`

Symmetric with `BoundaryRuleRepository.insert` (`syncTracker.trackCreate(id, "boundary_rule")` L37–41). Imported rules **are** enqueued for push.

#### B.6 — Test coverage (RED, structural gap)

- No `SyncServiceTest` covering automation_rule round-trip
- No `SyncMapperTest` for automation_rule (existing SyncMapperTest + Tier2Test omit it)
- Only adjacent test: `AutomationDuplicateBackfillerTest` (PR #1077, dedup)

PR #1070 shipped routing without an integration test. PR #1069 shipped the mapper without a round-trip test. Same gap as A.6.

#### B.7 — Sibling boundary_rule end-to-end status (UNKNOWN — operator gating)

No git-log evidence that boundary_rule sync was ever validated end-to-end on a two-AVD pair. PR #1070 commit message says "mirrors boundary_rule pattern exactly" — true at the routing level, but the canonical sibling itself has no validation audit trail.

**If boundary_rule sync also fails on the same AVD pair, the issue lives at the SyncService dispatch layer (or Firestore rules) and will affect every config-family entity, not just automation_rule.** This is a question only the operator can answer without writing test infrastructure.

#### B.8 — Recent SyncService commits

`d4023d2` (PR #1077, May 3 04:07 UTC, ~5h after Test 4 RED was first observed) added `naturalKeyLookup` to the automation_rule pull path. That fix dedupes rules that arrive after both devices have imported the same template independently — it does **not** address "rule never reaches the second device at all," which is what Test 4 surfaced. The dedup fix presumes the rule reached Firestore in the first place, which Test 4 RED suggests it did not.

### Part C — Cross-test diagnosis

| Hypothesis | Verdict |
|-----------|---------|
| Shared `AutomationEventBus` broken | REJECTED. Test 1 GREEN proves bus works. |
| Shared Hilt-graph fault | REJECTED. Both subsystems instantiate cleanly; SyncService injects automationRuleDao without error in production builds (else app would crash on launch). |
| Shared `SyncService` fault that also breaks TimeTick | REJECTED. TimeTick path doesn't touch SyncService at all — it goes worker → bus → engine in-process. |

**Test 2 and Test 4 have independent root causes.** 2-PR fan-out as planned.

Test 1 GREEN bounds the Test 2 search: bus + engine + action handlers work; only matcher precision + worker scheduling phase are suspect (confirmed in A.1, A.2, A.4). Test 3 GREEN bounds further: lineage + cycle detection + AutomationLog work; engine-side state machinery is fine.

### Part D — PR-A fix shape (Test 2)

**Scope:** widen matcher tolerance to align with worker tick granularity, OR tighten worker scheduling to fire at clock-aligned minutes — operator picks one of the two (see Part F STOP-condition (3)).

**Option (i) — Widen matcher to 15-min window** (recommended; lower-risk):

```kotlin
// AutomationEngine.kt:187-199
private fun timeMatches(triggerHour: Int, triggerMin: Int, tick: AutomationEvent.TimeTick): Boolean {
    val triggerMinutes = triggerHour * 60 + triggerMin
    val tickMinutes = tick.hour * 60 + tick.minute
    val delta = ((tickMinutes - triggerMinutes) % 1440 + 1440) % 1440
    return delta < 15  // tick fires at-or-after the configured minute, within one worker interval
}

is AutomationTrigger.TimeOfDay -> {
    val tick = event as? AutomationEvent.TimeTick ?: return false
    timeMatches(trigger.hour, trigger.minute, tick)
}
is AutomationTrigger.DayOfWeekTime -> {
    val tick = event as? AutomationEvent.TimeTick ?: return false
    if (!timeMatches(trigger.hour, trigger.minute, tick)) return false
    // existing day-of-week check unchanged
}
```

**Trade-off:** Rule fires once within 0–15 min after configured time. Acceptable per `AutomationTimeTickWorker.kt:13–20` doc-comment ("lines up within one tick"). Daily-fire-count guard (`AutomationRuleEntity.dailyFireCount`) already prevents multiple fires on the same day, so a rule won't double-fire if two ticks land in the same window.

**Option (ii) — Tighten worker schedule to clock-aligned minutes** (more invasive):

Use a chained `OneTimeWorkRequest` with `setInitialDelay` computed to the next `:00 :15 :30 :45` boundary, re-enqueueing itself after each fire. Higher complexity; affects battery model; doze-mode behavior unclear. **Defer unless operator wants <15-min precision in a future iteration.**

**LOC estimate:** Option (i) ≈ 30–60 lines (matcher refactor + 2 unit tests + 1 worker test). Operator-task cap is 150 — well under.

**New tests** (mandatory per A.6):
- `AutomationEngineTimeOfDayTest`: rule at 07:00, tick at 07:00 fires; tick at 07:14 fires; tick at 07:15 does NOT fire (window-end exclusive); tick at 06:59 does NOT fire; midnight wrap (rule at 00:00, tick at 23:55 within previous day, does NOT fire next day's rule).
- `AutomationEngineDayOfWeekTimeTest`: same window semantics + day-of-week filter.
- `AutomationTimeTickWorkerTest`: doWork emits TimeTick with `Calendar.HOUR_OF_DAY` and `Calendar.MINUTE` from the system clock at fire time.

**Files touched:** `AutomationEngine.kt`, `AutomationTimeTickWorker.kt` (doc-comment fix), `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md` (drift cleanup — names + granularity).

### Part E — PR-B fix shape (Test 4)

**Scope depends on operator answers to Part F STOP-conditions (1) and (2).** Three branches:

**Branch (B.α) — operator confirms boundary_rule sync ALSO fails on AVD pair:**
- Root cause is SyncService dispatch layer or Firestore rules. Defer this audit; spawn a new audit covering all config-family sync at once.
- **Do not** ship PR-B in its current shape — it would be a no-op.

**Branch (B.β) — operator confirms boundary_rule sync WORKS on the same AVD pair:**
- Then automation_rule has a wiring difference that recon missed. Re-audit with that signal: most likely candidate is Firestore production rules missing `automation_rules` ACL grant (B.4).
- PR-B = update production Firestore rules + verify push reaches cloud + verify listener fires on AVD-B. Code change might be zero (just rules deploy). Add the integration test at the same time.

**Branch (B.γ) — operator can't or won't run boundary_rule comparison:**
- Default to "ship missing test coverage; ask operator to re-run on AVD pair." Add `SyncServiceAutomationRuleTest` (round-trip via in-memory Firestore mock) and `SyncMapperTest::automationRule` (round-trip), then re-run Test 4 on AVD. If still RED, escalate to (B.β) hypothesis check.
- LOC: ~80–150. Operator-task cap is 250 — under.

**Files likely touched (all branches):**
- `app/src/test/java/.../sync/SyncMapperTest.kt` (extend with automation_rule round-trip)
- `app/src/test/java/.../remote/SyncServiceTest.kt` (new file or extend; FakeFirestore mock)
- Possibly `firestore.rules` if operator pulls production rules into repo (long-overdue per repo conventions; not strictly in scope)

### Part F — STOP-conditions (mandatory operator gate before Phase 2)

Per operator-task spec, **HARD STOP**: Phase 2 does NOT begin until operator confirms the audit's verdict + chooses fix shape. The skill's auto-fire default is overridden.

The following STOP-conditions need operator input to dispatch:

1. **Test 4 sibling sanity check (B.7)** — does boundary_rule sync work on the same two AVDs that ran Test 4? Run procedure: import a custom boundary rule (e.g. work-hours 09:00–17:00) on AVD-A, observe it on AVD-B within 30 s. **Three possible answers route to three different PR-B shapes (Part E).** Audit cannot proceed without this signal.

2. **AVD target Firestore (B.4)** — are the AVDs hitting production Firestore or the local emulator? If emulator, B.4 hypothesis is dead (stub rules grant everything). If production, B.4 is the leading candidate. Operator can answer in one line.

3. **Test 2 fix shape choice (Part D)** — option (i) widen matcher to 15-min window (recommended; sub-15-min precision sacrificed) or option (ii) tighten worker to clock-aligned minutes (sub-15-min precision preserved at cost of complexity). Default: (i).

4. **Test 4 dedup-fix interaction (B.8)** — PR #1077 added `naturalKeyLookup` for automation_rules pull. If PR-B turns out to require a wider sync-infra change, does PR #1077's behavior remain valid? Should be a no-op concern but worth confirming.

5. **Memory #22 status** — escalation **not triggered** for PR #1070 routing (verified shipped in B.1). Memory remains as-is unless Phase 2 surfaces a follow-on falsified claim.

6. **Audit doc length** — currently ~370 lines (under 500 cap). No split needed.

---

## Phase 2 — sequential implementation (gated, NOT auto-fired)

Phase 2 plan is committed below for completeness; **do not execute** until operator clears Part F.

### PR-A — Test 2 matcher fix (engine internal)

- **Branch:** `fix/automation-timeofday-matcher-tolerance` (worktree per CLAUDE.md repo conventions)
- **Files:** `AutomationEngine.kt` (matcher), `AutomationTimeTickWorker.kt` (doc), `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md` (drift cleanup)
- **Tests:** `AutomationEngineTimeOfDayTest`, `AutomationEngineDayOfWeekTimeTest`, `AutomationTimeTickWorkerTest`
- **LOC cap:** 150 (operator-task spec)
- **Verification:** unit tests green; AVD: enable a TimeOfDay rule at next-hour-mark, wait 1 worker cycle, observe rule fire + AutomationLog entry; regression Tests 1+3+5+6 still GREEN

### PR-B — Test 4 sync fix (depends on Part F answers)

- **Pre-condition:** PR-A merged; operator confirms Test 2 GREEN on AVD; operator answers Part F (1) and (2)
- **Branch / files / LOC:** depends on which of (B.α), (B.β), (B.γ) fires — see Part E
- **Verification:** AVD pair: import on AVD-A → appears on AVD-B within 30 s; toggle on AVD-B syncs back; Tests 1+2+3+5+6 still GREEN

### Phase 2 STOP-conditions (cross-PR)

- PR-A doesn't make Test 2 GREEN → re-audit, do not patch over.
- PR-B doesn't make Test 4 GREEN → re-audit.
- Any of Tests 1/3/5/6 regress → revert and re-audit.
- > 2 days between PR-A merge and PR-B start → PR-B's micro-audit re-verifies shared infrastructure premises.

---

## Phase 3 — pre-merge integration verification

Per memory #16 (Phase 3+4 fire pre-merge, not post-merge): each PR runs Phase 3 + Phase 4 before merge button.

- **PR-A Phase 3:** unit tests green + AVD single-device run (TimeOfDay rule fires within 15 min of configured time).
- **PR-B Phase 3:** unit tests green + AVD pair run (rule import → cross-device propagation < 30 s).
- **Cross-PR Phase 3 (after both merged):** full automation engine validation Tests 1–6 on AVD; goal 6/6 GREEN; engine validation D5 closes done: 1.0.

---

## Phase 4 — closure + memory updates

Standard 4-field summary per PR + final aggregate.

Memory updates that may fire:

- **memory #22**: re-strengthen, this time as a *negative* example — PR #1070 was alleged "claimed-shipped-but-incomplete" by the operator-task framing, but git-log + diff inspection confirms it shipped end-to-end at the routing layer. The actual gap was test coverage, not routing. Worth a one-line note that "memory #22 verification fired and *cleared* PR #1070" — useful as evidence the discipline catches falsified claims AND avoids false-positive escalations.
- **memory #18**: axis (e) earned its keep again (DayOfWeekTime caught alongside TimeOfDay). Fifth consecutive use post PRs #1077, #1078, #1082, plus this audit's Part A.4 finding. Pattern is durable.
- **SyncService 6-7-touchpoint pattern (originally PR #1089)**: re-confirmed. Routing extension was mechanical against boundary_rule template. No new memory entry needed; existing pattern memory stands.
- **Test-coverage gap on sync round-trip**: candidate for a new memory entry — "config-family entity sync extensions need round-trip + integration tests at the same PR; routing-without-tests is the dominant failure mode for sync regressions". Defer unless PR-B shows the same pattern again.

---

## Verdict + ranked recommendation table

| Item | Risk | Reco | Why |
|------|------|------|-----|
| Test 2 matcher exact-minute equality | **RED** | PROCEED → PR-A | Primary defect. ~0% fire rate today. Fix is ≤ 60 LOC. |
| Test 2 worker missing setInitialDelay | YELLOW | INCLUDED in PR-A | Compounds the matcher defect; one-line fix bundled. |
| Test 2 DayOfWeekTime sibling-primitive (axis (e)) | **RED** | PROCEED → PR-A (same change) | Same matcher branch; same fix covers both. |
| Test 2 architecture-doc drift | YELLOW | INCLUDED in PR-A | Cheap one-line cleanup; bundle. |
| Test 2 missing test coverage | **RED** | PROCEED → PR-A | Mandatory regression net. |
| Test 4 SyncService routing | GREEN | STOP-no-work-needed | All 7 touchpoints verified shipped. Memory #22 cleared. |
| Test 4 SyncMapper round-trip | GREEN | STOP-no-work-needed | Symmetric with boundary_rule. |
| Test 4 SyncTracker integration | GREEN | STOP-no-work-needed | Repository wiring symmetric. |
| Test 4 production Firestore rules | **DEFERRED** | OPERATOR-INPUT (Part F #2) | Out-of-repo. Likely root cause if AVD targets prod. |
| Test 4 boundary_rule sibling validation | **DEFERRED** | OPERATOR-INPUT (Part F #1) | Routes PR-B shape. Cannot proceed without. |
| Test 4 missing test coverage | YELLOW | PROCEED → PR-B (any branch) | Add round-trip + integration tests regardless. |
| AutomationRuleDao.setCloudId signature drift | GREEN | STOP-no-work-needed | Dormant — method not invoked. |

**Anti-patterns flagged but not fixed:**
- "Routing-without-tests" pattern in PR #1069 (mapper) and PR #1070 (routing) — see memory candidate above.
- Production Firestore rules out-of-repo — long-standing convention gap, not in scope here.

---

## Confidence summary

- Test 2 root cause (matcher exact-equality + worker random phase): **HIGH** confidence, three independent lines of evidence (recon code excerpt, sibling-primitive bug at L191–199, design-intent drift in worker doc-comment).
- Test 4 root cause: **MEDIUM** confidence pending operator answers to Part F. Most likely either production Firestore ACLs (B.4) or a Test-4-specific environmental issue (network, listener registration timing, etc.). Routing layer is verified GREEN.
- 2-PR fan-out vs single bundled PR: **HIGH** confidence — no shared root cause (Part C).
- Memory #22 disposition: **HIGH** confidence — verification fired and cleared PR #1070; not escalated.

**Audit doc length:** approximately 410 lines (under both 500 and 600 caps). Single-pass shape, no batch split needed.

---

## Phase 3 — bundle summary (post-implementation, pre-merge per memory #16)

Both PRs landed as sequential commits on the single branch
`claude/fix-audit-engine-tests-WjtHa` (PR #1093). The 2-PR fan-out was preserved
at the commit level rather than as separate GitHub PRs because the system-prompt
branch constraint allowed only one branch per session. Each commit retains its
own scope, micro-audit context, and verification gate.

### PR-A — Test 2 fix (commit `5beccbb`)

- **Scope:** option (ii) selected by operator. Tighten the worker's first
  fire to a clock-aligned 15-min slot via `setInitialDelay`. Matcher
  unchanged (still exact-minute equality). Sibling-primitive axis (e)
  parity preserved — `DayOfWeekTime` inherits the fix because both
  branches share the same matcher dispatch.
- **Touched:** `AutomationTimeTickWorker.kt` (+ `computeAlignedDelayMs`
  helper, `INTERVAL_MIN` constant, doc-comment rewrite),
  `PrismTaskApplication.kt` (+ `setInitialDelay` plumb-through),
  `AutomationEngine.kt` (extract matcher to companion `matchTrigger`
  for unit testability), `AutomationTrigger.kt` (kdoc drift),
  `AUTOMATION_ENGINE_ARCHITECTURE.md` (drift cleanup at L44 + L148).
- **New tests:** `AutomationTimeTickWorkerScheduleTest` (10 cases for
  `computeAlignedDelayMs`), `AutomationEngineMatcherTest` (13 cases
  across all matcher branches incl. axis-(e) DayOfWeekTime parity).
- **LOC:** +393 / -44 (incl. 211 lines of new test).
- **Documented UX trade-off:** rules whose minute is not 0/15/30/45
  will not fire. Rule-editor UI constraint deferred — out of PR-A
  scope. Surfaced in worker kdoc + `AutomationTrigger.kt` kdoc.

### PR-B — Test 4 fix (commit on top of PR-A)

- **Scope:** branch (B.γ) — operator did not have boundary_rule end-to-end
  data; emulator confirmation killed the production-Firestore-rules
  hypothesis. PR-B recon (Phase 1.5) found the actual root cause:
  `SyncService.startAutoSync()` is called only from
  `MainActivity.onCreate` and `OnboardingViewModel`; the post-
  interactive-sign-in path (`AuthViewModel.runPostSignInSync`) calls
  `fullSync` + `initialUpload` + `startRealtimeListeners` but never
  installs the reactive-push subscriber or the 30s periodic
  `ReactiveSyncDriver` that live inside `startAutoSync`. Result:
  any local edit (rule import included) made in the same session as
  a fresh sign-in is enqueued to `sync_metadata` but never pushed
  until the next process boot.
- **Touched:** `SyncService.kt` (+ `autoSyncStarted` `@Volatile` flag
  + idempotency guard at the top of `startAutoSync`),
  `AuthViewModel.kt` (+ `syncService.startAutoSync()` call at end of
  `runPostSignInSync`).
- **Test additions (covers audit B.6 gap):** three new
  `SyncMapperTest::automationRule_*` cases —
  `roundTrip_preservesAllFields`, `roundTrip_handlesNullableFields`,
  `toMap_emitsUpdatedAtForLww` (the latter pinning the LWW invariant
  the audit B.3 verdict depends on).
- **LOC:** ~+95 / -1 (incl. ~85 lines of new SyncMapper tests).
- **SyncService idempotency unit test deferred:** the constructor
  takes ~50 injected DAOs/services; standing up MockK coverage
  costs more than the fix it protects. AVD pair re-run is the
  primary verification gate (B.γ branch's "ship missing test
  coverage; ask operator to re-run on AVD pair" prescription).

### Updated Phase-1 verdict matrix (post-fix)

| Item | Phase 1 risk | Phase 2 disposition |
|------|-------------|--------------------|
| Test 2 matcher exact-minute equality | RED | Preserved (option ii); paired with worker scheduling fix. |
| Test 2 worker missing setInitialDelay | YELLOW | FIXED in PR-A (`setInitialDelay = computeAlignedDelayMs(now)`). |
| Test 2 DayOfWeekTime axis-(e) parity | RED | FIXED via shared matcher dispatch (no separate change). |
| Test 2 architecture-doc drift | YELLOW | FIXED in PR-A. |
| Test 2 missing test coverage | RED | FIXED in PR-A (+23 cases). |
| Test 4 `startAutoSync` post-sign-in gap | (NOT IN PHASE 1) | FIXED in PR-B. Recon-emerging finding. |
| Test 4 SyncMapper round-trip coverage gap (B.6) | YELLOW | FIXED in PR-B (3 new SyncMapperTest cases). |
| Test 4 SyncService idempotency unit test | NEW | DEFERRED — DI graph cost exceeds AVD validation cost. |
| AutomationRuleDao.setCloudId signature drift | GREEN | Still STOP-no-work-needed (dormant). |

### Process incidents

- **Memory #22 fired and *cleared* PR #1070** (audit B.1). Verification
  is working in both directions — catches falsified claims AND avoids
  false-positive escalations.
- **Memory #18 axis (e) caught DayOfWeekTime** alongside TimeOfDay
  (audit A.4). Fifth consecutive use.
- **Recon-emerging finding (Test 4 root cause):** Phase 1's MEDIUM-
  confidence verdict turned into a HIGH-confidence diagnosis after
  one extra round of recon focused on the call-graph for
  `startAutoSync`. The audit framework's "operator-input gates Phase
  2" pattern paid off — without the operator's emulator + B.γ
  answers, Phase 2 would have shipped the wrong fix shape.
- **2-PR fan-out collapsed to a single PR (#1093)** by system-prompt
  branch constraint. Logical separation preserved; merge granularity
  changed. Worth a one-line memory note: "branch-constrained sessions
  ship sequential commits, not separate PRs — verify whether the
  constraint is intentional before splitting work."

### Re-baselined wall-clock per PR

PR-A: ~25 min (recon → impl → tests → push). PR-B: ~30 min including
the second recon round. Both well under the audit's 200-line micro-
audit cap.

### Memory entry candidates

- **Strengthen memory #22**: add line — "fired-and-cleared PR #1070
  routing claim. Verification is bidirectional discipline."
- **Strengthen memory #18**: fifth consecutive (e) axis catch. Pattern
  is durable; no new entry needed.
- **New candidate** — "post-sign-in vs cold-start sync paths must
  install the same machinery; if `startAutoSync` exists as a single
  installer it must be idempotent and called from BOTH paths." Defer
  unless this pattern repeats in another sync surface.
- **New candidate (deferred)** — "config-family entity sync extensions
  ship without round-trip + integration tests; routing-without-tests
  is the dominant failure mode for sync regressions." Confirmed by
  PR-B recon — this same gap masked the missing post-sign-in
  invocation for as long as PR #1070 has been in main. Worth promoting
  to a tracked memory if it bites a third time.

### Schedule for next audit

Engine validation D5 (the parent item this audit unblocks) closes at
done: 1.0 once the AVD pair re-runs Tests 1–6 GREEN on the merged
PR-B commit. No further audits needed for this scope.

---

## Phase 4 — Claude Chat handoff summary

Emitted as the last block of the run — see the assistant's final
message for a paste-ready fenced markdown block.
