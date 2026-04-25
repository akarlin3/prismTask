# Phase D Bundle — Post-Implementation Summary

**Date:** 2026-04-25 (PRs opened the same day as the audit)
**Bundle pattern:** Audit-first, fan-out PRs (mirrors PRs #775–#779)
**Companion doc:** [`PHASE_D_BUNDLE_AUDIT.md`](./PHASE_D_BUNDLE_AUDIT.md)

This document captures what actually happened after the Phase 1 audit finalized the per-item verdicts. It's written so a future bundle-author can scan the decisions and the deviations without re-reading the audit.

## Per-item outcomes

| # | Item | Audit verdict | What shipped | PR | Diff |
|---|---|---|---|---|---|
| 1 | Promote `cross-device-tests` to required | 🛑 STOP — wrong premise; 100% failure rate | **Substituted with stabilization PR** — the *real* failure was per-line `sh -c` execution by `android-emulator-runner@v2`, breaking multi-line shell constructs (NOT the audit's first guess of AVD boot failure). Wrote runner to a temp file, invoked as one bash command. **No promotion until ≥5 consecutive green main runs.** | [#780](https://github.com/akarlin3/prismTask/pull/780) | +53/−20, 2 files |
| 2 | Sweep boundary tests | ✅ PROCEED with modified scope | Three boundary regression tests in `BatchUndoLogDaoTest` pinning the strict-`<` semantics on both arms of the sweep predicate. | [#781](https://github.com/akarlin3/prismTask/pull/781) | +69/0, 2 files |
| 3 | Per-medication mark UX | 🛑 STOP — wrong premise | **A + B applied:** A) close as completed-by-design (PR #744 slot-level long-press IS the per-medication editor by design — `MedicationViewModel.kt:132`); B) garbage-collected the orphan `medication_marks` table everywhere — Android Room (DB v63→v64), backend Alembic (021), SQLAlchemy model, sync mappers, DAO, orphan-healer plumbing, all related tests. | [#782](https://github.com/akarlin3/prismTask/pull/782) | +355/−461, 25 files |
| 4 | Web tier enum drift | 🛑 STOP — no work needed | Closed; PR #762 was comprehensively complete. No PR opened. | — | — |
| 5 | Web in-app delete flow | ✅ PROCEED as written, with one expansion | Two-step typed-DELETE modal mirroring Android's `DeleteAccountSection.kt`, plus the audit-recommended Firestore mark for cross-device parity, plus a 410-Gone interceptor branch on the axios client to prevent zombie web sessions. | [#783](https://github.com/akarlin3/prismTask/pull/783) | +583/−13, 10 files |
| housekeeping | Audit doc + this summary | — | This PR. | TBD | this PR |

**Net production work: 4 PRs (1 stabilization, 1 test, 1 cleanup, 1 feature) + 1 docs PR.** The original prompt envisioned 5 feature/cleanup PRs; the audit-first pattern collapsed two of them (Item 4 to nothing, Item 3 from feature → cleanup).

## Scope-vs-audit deviations

### Item 1 (CI stabilization) — root cause was different than the audit guessed

The audit and the investigation agent both initially attributed the cross-device-tests failure to the AVD never coming up (`adb failed with exit code 1`). On closer reading of the actual run log, the AVD does boot ("Boot completed in 40414 ms", "Emulator booted."), and the failure is the action's per-line `sh -c` execution model breaking the multi-line `run_cross_device_tests() { … }` function definition. PR #776's "fix" of swapping `\`-continuations for a function form was a fix for the wrong shape — both forms are broken under per-line execution.

**Lesson:** when a CI failure has a clean-looking root cause but the same symptom keeps recurring after a "fix", read the actual log line-by-line before trusting the symptom name. The fix here would have been the same regardless (write the script to a file), but the audit's framing of "stabilize the AVD boot" was misleading. The PR body and commit message are scoped to the real cause.

### Item 3 (orphan table) — predicted ~20+ files, actual 25

Predicted 17–20 files; actual was 25, with 5 surprises:
- 4 backend test files needed surgery (`test_medication_sync_audit.py`, `test_audit_emit_telemetry.py`) — the audit didn't predict these because the agent's read-only audit didn't dive into backend test contents.
- `docs/MEDICATION_TIME_LOGGING.md` needed a "historical note" since the table was a documented part of the feature.
- The `MedicationLogEvent` docstring needed updating (it referenced the now-removed mark family).
- `BackendSyncMappersMedicationTest.kt` and `MedicationSlotSyncMapperTest.kt` (unit tests) needed cuts — the audit named them but didn't predict the test-method count.

**Lesson:** when the audit identifies a table to drop, the read-only audit should also grep for tests that mention the entity name + sync mappers + region comments. "It's only used in two production paths" doesn't mean "there are only two test files." Worth adding to the next bundle's per-item template.

### Item 5 (web delete flow) — predicted ~280–320 LOC, actual 583

Larger than predicted because:
- Test files alone are ~250 LOC (the audit predicted vitest cases but didn't size them).
- Local test runs uncovered that jsdom doesn't implement `matchMedia`, requiring a one-line polyfill in the shared `setup.ts`. Future tests touching `Modal` / `useIsMobile` benefit; backwards-compatible.
- The modal's state machine grew from the audit's predicted 4 states to 5 (explicit `error` state with retry path) — this was a real product decision that improves UX, not scope creep.

**Lesson:** the audit's LOC estimate was for production code; tests were a meaningful unaccounted-for addition. The next bundle's audit template should call out test LOC separately.

## Process notes (for next bundle)

1. **Audit-first paid off again.** 3 of 5 items got STOP verdicts. Items 1 and 3 would have been wasted multi-day cleanup loops if shipped as the prompt described. Item 4 would have been pure churn. Skipping the audit phase to "save time" would have cost ~4–6× the time it took to write the audit.

2. **The audit's failure-mode analysis can itself be wrong.** Item 1's *correct* root cause didn't surface in the audit phase — the agent only had `gh run list` summary state, not the actual log. The PR work surfaced the real cause. **Recommendation:** for any CI-stabilization audit item, fetch the failed-step log content (`gh run view <id> --log-failed`) into the audit phase, not just the run-list summary.

3. **Two backend test surgeries weren't anticipated.** The audit's read-only investigation listed file paths but didn't read the test bodies. **Recommendation:** when the audit verdict is "drop entity X", the audit should also grep `tests/*` for `entity_type = "X"` and similar string literals, not just imports + DAO references.

4. **Local working tree was 8 commits behind origin/main.** Caught early because the SessionStart fetch hook surfaced ahead/behind state. The user's WIP changes (`versionCode 690→694`, `scripts/build-release-aab.sh`) were stashed cleanly before rebase. **Recommendation:** the next bundle prompt could explicitly call out the rebase-first step instead of relying on the audit-time observation.

5. **Working tree state didn't break anything.** All 4 PR worktrees were created off a freshly-fetched `origin/main`, never the 8-commits-behind local working tree. The discipline to always branch from `origin/main` rather than the local checkout (per the worktree convention) is what makes this safe.

## Memory entry candidates

Flagging for the user — none are auto-saved:

1. **`reactivecircus/android-emulator-runner@v2` runs `script:` lines individually via `sh -c`.** This is a non-obvious gotcha: any multi-line shell construct (function, if-block, heredoc) silently fails because each `sh -c` is a fresh shell. The fix is always to write the script to a file in a previous step. Worth a memory entry under feedback or reference, since both PR #773 (broke the workflow) and PR #776 (failed to fix it) tripped on this.

2. **`reactivecircus/android-emulator-runner@v2` retry-once must wrap a single command, not a function-call chain.** Closely related to #1 — the retry shape only works if the wrapped invocation is a single shell command, not a function call resolved across multiple `sh -c` shells.

3. **jsdom doesn't implement `matchMedia`; tests touching `Modal` / `useIsMobile` need a polyfill.** Not bundle-specific — hits any future test that goes through these primitives. The polyfill in `web/src/test/setup.ts` shipped in PR #783 covers all future tests.

4. **Audit-first prevents fragmenting orthogonal data models.** Item 3 would have shipped 3 tables (`medication_doses`, `medication_tier_states`, `medication_marks`) all carrying overlapping per-(med, slot, day) state. The audit caught this as a data-model fork before it reached implementation. The pattern (audit reads design docs + comments + ViewModel decisions before sketching the gap) is worth keeping.

5. **CLAUDE.md drifts faster than expected.** It claimed `CURRENT_DB_VERSION = 57`; reality on origin/main was **63** (with v62→v63 having shipped the very table at the heart of Item 3). A separate housekeeping pass on CLAUDE.md is warranted — flagging for a future routine.

## Phase D timeline impact

This bundle moved the following items from the Phase D backlog to done:

- ✅ **Item 1 (CI stabilization)** — substituted for the original "promote to required" task; the promotion remains pending ≥5 consecutive green main runs.
- ✅ **Item 2 (sweep test coverage)** — closed. PR #707 follow-up gap eliminated.
- ✅ **Item 3 (medication_marks orphan)** — closed via cleanup PR. The "per-medication mark UX" work item is closed-as-completed-by-design (PR #744 ships it at slot granularity by deliberate decision).
- ✅ **Item 4 (web tier enum drift)** — closed. PR #762 was already complete.
- ✅ **Item 5 (web delete flow)** — closed via PR #783.

Remaining open Phase D work (not touched by this bundle):
- The required-status promotion of `cross-device-tests` once stability is confirmed.
- Anything else still in the Phase D backlog elsewhere.
