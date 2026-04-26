# Phase D Fan-Out 2 — Post-Implementation Summary

**Date:** 2026-04-26
**Bundle owner:** akarlin3
**Companion doc:** [`PHASE_D_FANOUT_2_AUDIT.md`](./PHASE_D_FANOUT_2_AUDIT.md)

This document captures Phase 2 investigation findings and what (if anything) shipped. Pattern mirrors `PHASE_D_BUNDLE_SUMMARY.md` from PRs #780–#786.

## TL;DR

**Net production work: zero PRs.** Audit doc (PR #799) and this summary (TBD) are the only deliverables. Both code-PR candidates from the audit's replacement scope died on Phase 2 inspection — one was already fixed by an unrelated PR drive-by, the other was misdiagnosed in the audit phase.

## Per-item outcomes

| # | Item | Audit verdict | What shipped | PR | Diff |
|---|---|---|---|---|---|
| 1 | HiltTestRunner.onStart sweep | 🛑 STOP — no work needed (audit IS the deliverable) | Audit doc only. Optional sentinel test deferred per Q1 = "skip Phase 2 entirely". | [#799](https://github.com/akarlin3/prismTask/pull/799) | +291/0, 1 file (audit doc) |
| 2 | Connected-tests flake stabilization | 🛑 STOP — wrong premise; replacement scope was 3 PRs (PR 1 + PR 2 + PR 3) | **All 3 replacement PRs dropped.** PR 1 already fixed by #774, PR 2 deferred to bundle-owner per Q2, PR 3 misdiagnosed (AVD failures, not Compose race). | — (this summary) | — |

## Phase 2 investigation findings — both PRs died on inspection

### PR 1 — Migration59To60Test fix: ALREADY FIXED, no PR needed

**What the audit found:** `Migration59To60Test.malformedTiersByTimeJson_backfillsNothing` failed 3 consecutive runs since PR #773 merged — flagged as a "real (non-flake) test failure."

**What Phase 2 investigation found:** the audit was correct that the test was failing, but missed that **PR #774 (Account deletion UI) included a drive-by fix to `MIGRATION_59_60`** that resolved it.

Evidence:
- `git log -p -S 'recognizable tier token is present' -- Migrations.kt` shows commit `72659e33` (PR #774, merged 2026-04-25 18:25 UTC) added the OR-tier-token filter (`Migrations.kt:1685-1690` on origin/main).
- All 3 failing runs in the audit window pre-date PR #774:
  - run 24926119046 — 2026-04-25T07:51Z, on main at PR #773 merge SHA `11cd349d`
  - run 24936169864 — 2026-04-25T17:11Z, on `feature/medication-batch-mutations` (forked pre-#774)
  - run 24936420297 — 2026-04-25T17:24Z, on PR #774's own branch BEFORE its self-merge added the fix
- All 3 most recent main `connected-tests` runs (post-#774) show **success**:
  - 24937612065 (PR #774 merge): `connected-tests: success`, only `cross-device-tests` failed (AVD)
  - 24945995404 (PR #782 merge): `connected-tests: success`, only `cross-device-tests` failed (AVD)
  - 24947340575 (PR #787 merge): `connected-tests: success`, only `cross-device-tests` failed (AVD)

**Why the audit missed this:** the research agent saw the 3 failures clustered AFTER PR #773 but didn't check whether subsequent commits had quietly fixed the migration WHERE clause. `git log --grep` for "Migration_59_60" or "tiers_by_time" would have surfaced PR #774's drive-by; the agent searched for explicit "Migration59To60" mentions, not for migration body changes.

### PR 2 — AVD stabilization: deferred per Q2 = option 2

Per the user's Q2 = "approve PR 1 + PR 3, defer PR 2 to bundle-owner coordination," PR 2 was not opened. The bundle audit's `PHASE_D_BUNDLE_AUDIT.md` Item 1.5 already flagged the same fix; PR #780 from that bundle attempted a fix but addressed a different symptom (script-syntax shell escaping vs. AVD boot reliability). The 100% cross-device-tests failure rate persists across recent main runs — bundle owner needs a follow-up fix, separate from this fan-out.

### PR 3 — HabitSmokeTest waitUntil: MISDIAGNOSED, no PR needed

**What the audit said:** Two `HabitSmokeTest` failures (`habitsTab_showsSeededHabits`, `habitList_tappingHabitDoesNotCrash`) attributed to a "Compose seed-data race" — `composeRule.waitForIdle()` returning before the built-in habit seeder finishes.

**What Phase 2 investigation found:** the failures are **AVD boot failures**, not Compose timing races. Same cluster as the cross-device-tests AVD issue.

Evidence from `gh run view <id> --log-failed`:
- run 24919842285 (audit attributed `habitsTab_showsSeededHabits`):
  - `WARNING | Failed to process .ini file /home/runner/.android/emu-update-last-check.ini for reading` (×2)
  - `The process '/usr/local/lib/android/sdk/platform-tools/adb' failed with exit code 1` (×5)
  - `[EmulatorConsole]: Failed to start Emulator console for 5554`
- run 24919540542 (audit attributed `habitList_tappingHabitDoesNotCrash`):
  - Same shape: `adb failed with exit code 1` (×3) + `[EmulatorConsole]: Failed to start Emulator console for 5554`

In both cases the emulator never booted; the test framework's "test failed" attribution is misleading because the AVD itself crashed. **A `waitUntil` change inside the test would not have prevented either failure.**

**Why the audit missed this:** the research agent grouped failures by test name from the gradle output, but didn't read the surrounding emulator/adb logs to verify the test actually ran. A two-line check (`grep -B 50 "<test name>" log | grep -E "adb failed|EmulatorConsole"`) would have caught the AVD crash precursor.

## Scope-vs-audit deviations

### Item 1 — held exactly as predicted

The audit's "STOP — NO WORK NEEDED" verdict survived Phase 2. The 17-hook inventory was complete; nothing surfaced after the audit that would change the classification. Optional sentinel test was deferred per user Q1.

### Item 2 — replacement scope was MORE wrong than the audit thought

The audit caught the prompt's wrong premise (named hotspots aren't actually flaking). What the audit didn't catch:
- **PR 1 was already fixed** — this required a `git log -p -S` search across recent commits, not just looking at the failing test code
- **PR 3 was misdiagnosed** — this required reading the actual emulator boot logs, not just the test failure attribution

The deeper lesson: when the audit identifies a "real test failure not flake" cluster, the audit phase needs to (a) check whether intervening PRs have shipped a drive-by fix, and (b) confirm the test actually executed by checking the emulator/runtime logs, not just the gradle test summary.

## Process notes (for next bundle)

1. **Audit-first paid off again, more strongly than expected.** 5 of 5 substantive PRs across Phase 1 and Phase 2 ended up STOP/NOT-NEEDED. If the original prompt had been executed without audit-first, the work would have been: 4 sentinel-class PRs against PrismTaskApplication.onCreate hooks (3 of which would have created cross-test pollution and been reverted), plus 3 flake fixes targeting tests that aren't flaking (zero impact), plus rewrites of `EdgeCaseRotationTest` and `SyncTestHarnessSmokeTest` (both already passing). Net cost saved: probably 3–5 PRs of dead-end work + the cross-test pollution remediation.

2. **The audit's failure-mode classification can itself be wrong.** Same lesson as the Phase D bundle's Item 1 — the `gh run list` summary is not enough; you have to read `gh run view --log-failed` and look at the **lines around** the test failure, not just the test name. This time it surfaced as misattribution (AVD boot failure → "Compose race"); in the bundle audit it surfaced as wrong-cause attribution (script-syntax → "AVD boot"). **Recommendation for next bundle template:** for any CI-stabilization audit item, include a "verified via raw log inspection" checkbox per failing test, with the specific log lines copied into the audit doc.

3. **Drive-by fixes from unrelated PRs are easy to miss.** PR #774's primary purpose was account deletion UI, but it included a one-paragraph migration-WHERE-clause fix that quietly resolved Item 2 PR 1. The audit phase should `git log -p -S '<key SQL clause or function name>'` for the failing area to catch these. **Recommendation:** add this to the audit checklist.

4. **The "fan-out per item" template held up.** Even when both items collapsed to docs-only, the structure (per-item premise verification → wrong-premise check → recommendation → STOP) made it easy to confirm "no actually, still STOP" at Phase 2. The cost of writing the audit was paid back by being able to abort cleanly.

## Memory entry candidates

Flagging for the user — none auto-saved:

1. **PR-#774-style drive-by migration fixes are common.** When a non-trivial PR touches `Migrations.kt`, audit-phase research should check whether the change resolves any open test failures in the area. Worth a feedback memory if this pattern recurs across more bundles.

2. **`adb failed with exit code 1` in connected-tests is an AVD boot failure, not a test failure.** Even when the gradle output names a specific test class as "FAILED", check 50 lines above for `EmulatorConsole` or `adb` errors before classifying. This applies to every connected-tests post-mortem.

3. **The audit-first pattern's failure mode is "the audit was wrong but plausible-looking."** Both bundles now have an example. Worth elevating from process note to durable feedback memory: "audit-phase agent reports require a Phase-2 spot-check before code work begins, even when the audit recommends PROCEED."

## Phase D timeline impact

This bundle moved zero items from open to done in the strict sense — the Phase D backlog was already correct on these items, and the audit confirmed it. What it DID accomplish:

- ✅ **Verified HiltTestRunner.onStart is in good shape post-#791.** No further sweeps needed.
- ✅ **Verified `connected-tests` lane is already green on main post-#774.** The "22% flake rate" cited in PR #761 is no longer current; the actual test-flake rate post-#774 is ~0% on the connected-tests lane.
- ✅ **Confirmed the cross-device-tests AVD reliability issue is the only remaining obstacle** to promoting connected-tests to required-status (which is its own follow-up, owned by the bundle).

Remaining open Phase D work (not touched by this fan-out):
- The `cross-device-tests` AVD boot stabilization (bundle Item 1.5 follow-up).
- The `connected-tests` promotion-to-required-status PR, which can land the moment cross-device-tests is stable (or independently if the bundle decides to ship them separately).

## What this fan-out actually shipped

- 1 audit doc PR (#799, merged)
- 1 summary doc PR (this PR)
- 0 code PRs
- 0 reverts needed
- 0 cross-test pollution introduced

The audit-first discipline is the deliverable.
