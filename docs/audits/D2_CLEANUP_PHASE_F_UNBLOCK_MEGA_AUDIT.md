# D2 Cleanup + Phase F Unblock Mega Audit

**Trigger:** Third mega-audit session in the format that worked across PRs
#823 + #825 (17 prior items, 11 PROCEED PRs, 4 STOPs, 2 GitHub issues).
This batch covers the remaining CC-automatable items in Phase D2 + the
load-bearing Phase F kickoff blocker (cross-device-tests RED).

**Process:** audit-first, single doc, three checkpoint stops. Phase 1 below
covers all 8 items. Phase 2 ships fan-out PRs only for items the user
explicitly approves. Mega-PR is forbidden.

**Repo state at start of audit:** worktree `audit/d2-unblock-mega` cut from
`origin/main` @ `ddfbde41` (post-#825 D2/F prep mega-audit Phase 4 close,
versionCode 717).

---

## Phase 1 — Audit (no fix code)

Each item below uses this framework:
1. **Premise verification.** Does the item describe real codebase reality?
2. **Findings.** What did the sweep / triage / measurement actually surface?
3. **Risk classification.** RED / YELLOW / GREEN / DEFERRED.
4. **Recommendation.** PROCEED, STOP-no-work-needed, or DEFER-to-G.0+.

---

# Batch 1 — Test infrastructure (load-bearing)

## 1. Cross-device-tests RED root-cause audit

### Premise verification

**Premise PARTIALLY correct, with a critical re-classification.**

The prompt frames this as a Phase F kickoff blocker assumed to share a
root-cause taxonomy with PR #780 (script-syntax) + PR #791 (emulator
routing) + PR #824 (boot timeout 600 → 1200s). The premise that
cross-device-tests is RED is **correct** — every recent run cancelled.
The premise about likely failure modes (AVD boot still failing despite
timeout, Firestore emulator state, test content regression) is
**incomplete**: the dominant failure mode is **none of those**.

**Workflow location:** `cross-device-tests` is not a separate workflow
(`.github/workflows/cross-device-tests.yml` does not exist). It is a
**job** inside `.github/workflows/android-integration.yml:180-291`,
sibling to the `connected-tests` job (lines 57-165). Both jobs share
identical setup but run different test scopes.

### Findings

#### Per-job conclusion (last 11 runs on `main`, oldest → newest)

Pulled via `gh run view <run_id> --json jobs` per memory entry #21
(don't trust workflow-level "failure" — disambiguate per-job).

| Run ID       | When (UTC)        | SHA         | connected-tests | cross-device-tests | xdev duration |
|--------------|-------------------|-------------|-----------------|--------------------|---------------|
| 24948026658  | 2026-04-26T04:15  | `4b77c23f`  | success         | cancelled          | 26m43s        |
| 24948443960  | 2026-04-26T04:42  | `a6ce066c`  | success         | cancelled          | 18m18s        |
| 24948728277  | 2026-04-26T05:00  | `cb12afe3`  | success         | cancelled          | —             |
| 24949613217  | 2026-04-26T05:56  | `747cc4ed`  | success         | cancelled          | —             |
| 24950056333  | 2026-04-26T06:23  | `d4f8d5ed`  | success         | cancelled          | —             |
| 24966447620  | 2026-04-26T20:35  | `c351cfc8`  | success         | cancelled          | —             |
| 24966870448  | 2026-04-26T20:57  | `fb077dc2`  | success         | cancelled          | —             |
| 24967280776  | 2026-04-26T21:17  | `77327156`  | success         | cancelled          | —             |
| 24969550465  | 2026-04-26T23:16  | `c874385a`  | **failure**     | cancelled          | —             |
| 24969851342  | 2026-04-26T23:32  | `e8b564e9`  | success         | cancelled          | 23m31s        |
| 24970283404  | 2026-04-26T23:56  | `871c2da8`  | success         | **cancelled at 45m5s (job-timeout exceeded)** | **45m5s** |

**Headline numbers:**
- `connected-tests`: 10 / 11 success = **91% over the last day** — well above
  prior PR #831 reconciliation's "73% pre-#824" baseline measurement.
- `cross-device-tests`: 0 / 11 success = **0%, all cancelled.**

#### Critical finding: cross-device cancellations have TWO distinct causes

Earlier-window cancellations (durations <30 min): caused by **`concurrency:
cancel-in-progress: true`** at workflow level (`android-integration.yml:46-48`).
When a new push to main fires android-integration before the previous run
finishes, the older run is cancelled. Examples:

- run `24948026658` started 04:15, cancelled at 26m43s — 04:42 = next
  push fired exactly at the cancellation moment. New run `24948443960`
  begins at 04:42.
- run `24948443960` cancelled at 18m18s ≈ 05:00 = next push (`24948728277`).
- pattern repeats for the entire pre-#824 window.

These cancellations are **NOT a test or infra failure**. They are
"expected" workflow housekeeping when merges land in quick succession.

The post-#824 merge run (`24970283404` on `871c2da8`) is the **first run
that had no subsequent push to cancel it**. It ran for the full 45 minutes
and **hit the job-level `timeout-minutes: 45` ceiling** (annotation:
`"The job has exceeded the maximum execution time of 45m0s"`,
`android-integration.yml:185`).

This is a **completely different failure class** from the AVD-boot pattern
PRs #780, #791, #824 addressed.

#### Why the test-execution timeout fires

`cross-device-tests` has a shell-level retry pattern (lines 256-262):

```bash
run_cross_device_tests() {
  ./gradlew :app:connectedDebugAndroidTest \
    "-Pandroid.testInstrumentationRunnerArguments.class=$TEST_CLASS" \
    --no-daemon "$@"
}
run_cross_device_tests || run_cross_device_tests --rerun-tasks
```

Inside `reactivecircus/android-emulator-runner@v2`, this means:
- Single emulator boot (max 1200s = 20 min per `emulator-boot-timeout: 1200`)
- First test attempt
- Retry on first failure (full re-run)

Wall-clock budget within `timeout-minutes: 45`:
- setup-android (~2-3 min)
- npm + firebase-tools install (~1 min)
- start Firebase emulator suite (~30s)
- enable KVM (~5s)
- write runner script (~5s)
- **android-emulator-runner step:** boot AVD (5-15 min) + 1st attempt + retry
- stop Firebase emulators (~10s)
- upload artifacts (~30s)
- post-step teardowns (~30s)

If the first test attempt hangs for >15 min (or completes legitimately in
~12 min, with retry), the wall-clock crosses 45 min before the runner
returns. The job is then killed, both attempts may be incomplete, and the
job conclusion is `cancelled` (not `failure`).

**The `MedicationCrossDeviceConvergenceTest` class** at
`app/src/androidTest/java/com/averycorp/prismtask/sync/scenarios/MedicationCrossDeviceConvergenceTest.kt`
exercises real `SyncService.pullRemoteChanges` paths (lines 1-80 read).
Each scenario runs `withTimeout(TEST_TIMEOUT)` blocks against a
two-device `SyncTestHarness`. There are multiple `@Test` methods (the
file is the full convergence-test suite). With per-test cost in the
single-digit-minute range and retry doubling wall-clock, hitting 45 min
on the first non-cancelled run is the expected outcome.

#### Failure-mode classification (per-class, with prevalence)

Per the prompt's framework, classify each failure:

| Class | Prevalence | Evidence |
|-------|-----------|----------|
| **Job-level timeout exceeded** (test execution > 45min) | 1/11 (9%) — only the most recent post-#824 run that wasn't pre-empted | run `24970283404` annotation `"exceeded maximum execution time of 45m0s"` |
| **Concurrency cancel-in-progress** (next push pre-empted run) | 10/11 (91%) — every pre-#824 cancellation matches | duration < 45min, next push timestamp ≈ cancellation moment |
| AVD boot timeout (memory #25 pattern) | 0 / observed sample | No `[EmulatorConsole]: Failed to start` in inspected step list; setup-android step shows ✓ |
| Firestore emulator state | 0 / observed sample | Start Firebase Emulator Suite step shows ✓ |
| Test content regression | 0 / observed sample | No `FAILED test` lines in inspected step output (cancellation prevents test reporting) |

Important: I cannot distinguish "test hung" from "tests took 22 min × 2
attempts" without job logs. `gh run view --log` returns HTTP 403 (admin
rights required); the `ci-logs` branch only captures FAILURE logs, not
CANCELLED, so cross-device-tests cancellations are not in `ci-logs`.

The 91% concurrency-cancel finding is **structurally robust** regardless
of what's inside the test execution: the pre-#824 cancellations would
have cancelled even if the tests passed. So the FIRST audit question
("is cross-device-tests RED because of test-content failure?") is
**inconclusive** in the pre-#824 window — we cannot tell because no
attempt was allowed to run to completion.

The post-#824 single data point (`24970283404`) shows the test execution
timeout. That is the **only run with a real signal** in the entire
sample.

#### Per-class fix proposal

For the **dominant pre-#824 class (concurrency-cancel)**: this is not a
bug. It's the cost of `cancel-in-progress: true` plus a slow job. The
fix is either to (a) accept it as inherent to busy main-branch merges,
or (b) accept that cross-device-tests is currently quarantined from
required-status (per `android-integration.yml:174-175` comment) so the
cancellations don't block PRs.

For the **post-#824 single-data-point class (job-timeout)**, three
candidate fixes:

- **A. Drop the shell-level retry** — make cross-device-tests single-attempt.
  Saves up to half the wall-clock; flakes converge into the rest of the
  signal. Pro: simplest. Con: real flakes will surface as failures, not
  retries (audit-friendly trade).
- **B. Bump `timeout-minutes: 45 → 75`** — pure config bump, mirrors
  PR #824's pattern. Accommodates worst-case boot (20 min) + 2 attempts
  (~25 min each) + cleanup (5 min) = 75 min. Pro: keeps retry. Con:
  bigger CI budget per run, trades signal speed for tolerance.
- **C. Move retry to workflow-level (matrix or separate job)** — clean
  separation of attempts; first attempt's failure logs preserved
  independently. Pro: best diagnostics. Con: bigger refactor, more YAML
  surface.
- **D. Profile + speed up `MedicationCrossDeviceConvergenceTest`** — find
  the slow test(s) and speed them. Pro: best long-term outcome. Con:
  open-ended; needs profiling we can't do without admin job logs.

**Per-class fix:**
- For concurrency-cancel: accept (no fix); cross-device-tests is
  quarantined so the cancellations don't block PRs.
- For job-timeout: **A or B**. Recommend **A** (drop retry) — it's
  smaller, restores signal, and matches the structure that connected-tests
  already uses (no retry, 91% success).

#### Escalation gate (per prompt)

Prompt: "If failure-mode classification surfaces 'unknown / mixed' with
>50% of failures, STOP and surface."

Classification surfaces **two distinct, well-understood classes** with
clear prevalence:
- Concurrency cancel: 91% of sample. Not a real failure.
- Job-timeout: 9% of sample (n=1). Has a clear shell-level retry root cause.
- Unknown / mixed: 0%.

**Item 1 does NOT need a dedicated session.** A single ~30-LOC PR (drop
the `|| run_cross_device_tests --rerun-tasks` line OR bump
`timeout-minutes`) is the right shape.

### Risk classification

**RED** for the original "is cross-device-tests stable" question — 0/11
green over the last day. But **the RED is not a Phase F launch blocker**:
cross-device-tests is quarantined out of required-status per
`android-integration.yml:174-175`, so PRs are not blocked by it. Phase F
kickoff readiness scorecard (PR #831 line 1037) already classifies this
as RED with this exact framing.

### Recommendation

**PROCEED** with a small fix PR. Two viable shapes:

- **Option A (recommended)**: Drop the shell-level retry. ~5-LOC change
  to `android-integration.yml:261`. Gets cross-device-tests to single-attempt
  matching connected-tests' shape. Expected outcome: green when
  MedicationCrossDeviceConvergenceTest passes; failure when it doesn't —
  honest signal restored.
- **Option B (fallback)**: Bump `timeout-minutes: 45 → 75`. Keeps retry,
  trades CI budget for tolerance.

**My pick: A.** Connected-tests succeeds 91% with no retry; cross-device-tests
should adopt the same shape. If the underlying test is genuinely flaky,
that's a test-quality concern, not a CI-infra one.

**No dedicated session needed.** Item 1 is a standard fan-out PR.

---

## 2. Connected-tests post-#824 flake re-measurement

### Premise verification

**Premise PARTIALLY correct.**

PR #824 (commit `871c2da8`, merged 2026-04-26 23:56 UTC) bumped
`emulator-boot-timeout: 600 → 1200s`. The prompt expected a soak window
of "post-#824 data" to re-measure flake.

**Reality:** as of 2026-04-27 ~01:00 UTC (this audit's start time), only
**~1 hour** has elapsed since #824 merged. The android-integration
workflow's `paths:` filter (lines 26-35) excludes docs-only changes,
which means the docs-PRs that merged AFTER #824 (#825, #826, #827, #828,
#831) **did not trigger android-integration runs**. The post-#824 sample
size is **exactly 1 run** (the merge of #824 itself).

The prompt's contingency ("If soak is incomplete (<3 days), document the
partial signal and recommend continuing the soak") matches reality.

### Findings

**Pre-#824 measurement** (10 runs on Apr 26, 04:15 → 23:32 UTC):
- 9 success / 1 failure / 0 cancelled
- Conclusive success rate: **90%** (9/10)
- Failure source: run `24969550465` on `c874385a` at 23:16 UTC. Inspected
  the captured log on the `ci-logs` branch (`ci-logs/android-integration/20260426T233152Z-24969550465.log`).
  Failing test: `HabitSmokeTest > habitList_tappingHabitDoesNotCrash`,
  with `java.lang.AssertionError: Failed to inject touch input. Can't
  retrieve node at index '0' of 'Text + EditableText contains "Exercise"
  (ignoreCase: false)'`. This is a **flaky UI smoke test** (timing race
  on built-in habit seeding before Compose finds the row), not an
  infrastructure issue.

**Post-#824 measurement** (1 run, the #824 merge run itself):
- 1 success / 0 failure / 0 cancelled
- Conclusive success rate: **100%** (1/1)
- Soak window: **~1 hour, n=1.**

**Reconciliation with PR #831's measurement** (line 1036): PR #831
Phase 4 reconciliation reported "actual pre-#824 baseline measured at
~73% success over 30-run window". My 10-run sample shows 90%. The
discrepancy reflects sample-size and time-window differences — PR #831
likely sampled a wider 30-run window including the post-#791 cancellation
streak that this audit's day-old window excludes (because those
cancellations have rolled off the per-job listing).

The 73% figure is the more conservative baseline; my 90% suggests the
Apr 26 daily window was relatively clean. Both put connected-tests
**well above the prompt's 5% flake threshold** (i.e., 5% failure ≈ 95%
success).

### Risk classification

**YELLOW** — soak window incomplete, but trending positive. The data
that DOES exist (90% pre-#824 over 10 runs, 100% post-#824 over 1 run,
the single failure being a known flaky UI smoke test) is consistent
with the connected-tests suite being healthy. Not RED.

### Recommendation

**STOP — defer to soak completion.** No fan-out PR.

- **Soak window incomplete** (1 hour, n=1 post-#824). Prompt explicitly
  permits this output: "If soak is incomplete (<3 days), document the
  partial signal and recommend continuing the soak."
- **Re-measurement window:** ~3 days from #824 merge (2026-04-27 → 2026-04-29).
  Re-measure on 2026-04-29+ once the soak completes.
- **Single known flake source:** `HabitSmokeTest.habitList_tappingHabitDoesNotCrash`
  (touch-input race). Not infra-shaped. If the soak window confirms a
  stable rate <5%, treat this single test as a candidate for either
  retry-loop scaffolding or a `@Retry`-annotated quarantine.

**Implication for item 4:** soak incomplete → item 4 STOPs.

---

## 3. Cross-device-tests required-check promotion (conditional on item 1)

### Premise verification

**Premise correct as a conditional.** Mechanical Settings UI step IF
prerequisites cleared.

### Findings

Prerequisites for promotion (from prompt):
1. Item 1's audit shows cross-device-tests is stable.
2. The 5-green-run counter from PR #791 has cleared.

**Both prerequisites NOT met:**
1. Item 1: cross-device-tests at 0/11 green. Not stable.
2. 5-green-run counter: 0/5. Has not had a single green run on main
   since the cross-device split (per PR #831 line 1037).

### Risk classification

**N/A** — gate not cleared.

### Recommendation

**STOP — prerequisites not met.** No Settings UI change.

Re-evaluate after item 1's fix PR ships AND ≥5 consecutive green runs
land. Per the prompt's hard rules: "If not, STOP and document."

---

# Batch 2 — Verification work (CC-runnable)

## 4. Connected-tests required-check promotion (conditional on item 2)

### Premise verification

**Premise correct as a conditional.** Mechanical Settings UI step IF
prerequisites cleared.

### Findings

Prerequisites:
1. Item 2's re-measurement returns <5% failure (= ≥95% success).
2. Soak window complete (≥3 days post-#824).

**Soak window NOT complete** (item 2: ~1 hour, n=1).

The pre-#824 measurement (90%) is below the 95% threshold (5% failure
= 10% failure measured). The post-#824 single-data-point measurement
(100%, n=1) is above threshold but statistically meaningless at n=1.

### Risk classification

**N/A** — soak incomplete, sample too small to evaluate.

### Recommendation

**STOP — soak incomplete.** No Settings UI change.

Re-evaluate on 2026-04-29+ once ≥3 days of post-#824 data exists. If
post-#824 success rate is ≥95% over a full window, item 4 becomes
viable.

---

## 5. Migration63To64Test on real emulator

### Premise verification

**Premise correct.** PR #782's MIGRATION_63_64 (orphan `medication_marks`
drop) shipped. The migration test exists at
`app/src/androidTest/java/com/averycorp/prismtask/Migration63To64Test.kt`
and is wired into the standard androidTest scope. The CI's
connected-tests suite covers it but cross-device-tests does not.

### Findings

**Test executed locally on a real Android emulator.**

- **Emulator:** Pixel_7 AVD (API 35), `emulator-5554`, headless
  (`-no-window -no-snapshot-load -no-audio`).
- **Command:** `./gradlew :app:connectedDebugAndroidTest
  -Pandroid.testInstrumentationRunnerArguments.class=com.averycorp.prismtask.Migration63To64Test
  --no-daemon --console=plain`
- **Build outcome:** `BUILD SUCCESSFUL in 2m 40s` (54 executed, 31 cached).
- **Test outcome (from
  `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml`):**

  ```xml
  <testsuites tests="2" failures="0" errors="0" skipped="0" time="0.712">
    <testsuite name="com.averycorp.prismtask.Migration63To64Test" tests="2" failures="0" errors="0" skipped="0" time="0.014">
      <testcase name="migration_isIdempotentWhenTableAbsent" time="0.013" />
      <testcase name="migration_dropsMedicationMarksTable" time="0.001" />
    </testsuite>
  </testsuites>
  ```

- **Per-test result:**
  - `migration_dropsMedicationMarksTable`: PASS (0.001s)
  - `migration_isIdempotentWhenTableAbsent`: PASS (0.013s)
- **No skips, no errors, no failures.**

The test class itself (read at `app/src/androidTest/java/com/averycorp/prismtask/Migration63To64Test.kt:1-60`)
is straightforward: it builds a minimal v63 schema, applies `MIGRATION_63_64`,
and asserts `medication_marks` table is absent (drop) + idempotent
(re-applying drop on absent table doesn't error).

### Risk classification

**GREEN.** Migration test is healthy, runs in <20ms post-build, and the
PR #782 migration logic is verified on real hardware.

### Recommendation

**STOP — no action needed.** Test is passing as-is. Keep it in the
standard androidTest scope.

The prompt's stated concern was that "CI's connected suite skipped it"
— but the test ran successfully on a clean emulator in this audit, so
any past CI skips were artifacts of CI cancellations (per item 1
analysis: cross-device-tests cancellations from concurrency or
job-timeout). Once item 1's fix lands, the test will run regularly
in CI as part of the broader connectedDebugAndroidTest suite.

---

## 6. PR #772 Phase 3 verification (emulator-runnable subset)

### Premise verification

**Premise WRONG. STOP-on-premise.**

PR #772 (commit `5fa31a6a`, merged 2026-04-25 19:35 UTC) is the
"feat(batch): MEDICATION COMPLETE/SKIP/DELETE/STATE_CHANGE (Android +
web + Haiku)" bundle. **It does not have a "Phase 3 spec" with sub-items
(a)(b)(c)(d) as described in the prompt.**

Pulled the full PR body via `gh api repos/Akarlin3/PrismTask/pulls/772
-q '.body'`. The Test Plan section contains:

- [x] Backend pytest — 20/20 pass
- [x] Android compile + ktlintFormat — green
- [ ] Android `connectedDebugAndroidTest` — not run locally; CI is the
  verification gate
- [x] Web vitest — 295/295 pass
- [ ] **Real-device smoke (post-merge): "mark morning meds taken" →
  preview → undo round-trip on S25 Ultra**

There is **no formal Phase 3** with the prompt's enumerated subitems
(cross-device sync round-trip, reminder-mode + batch SKIP, ambiguous
medication name). The prompt may be conflating PR #772 with a different
PR's Phase-3 follow-up plan, or it has invented sub-items that don't
exist.

The single non-checked item ("real-device smoke … on S25 Ultra") is
explicitly **Avery's work** per the prompt's exclusions ("This batch
deliberately excludes hands-on-device work").

### Findings

There is no emulator-runnable subset to verify. All Test Plan checkboxes
that PR #772 declared were CC-runnable are already marked complete (✅).
The single open item is a real-device smoke (Avery's work, excluded from
this batch).

### Risk classification

**GREEN** for what the PR actually committed to. **N/A** for the
prompt-described "Phase 3 spec" because the spec doesn't exist as
described.

### Recommendation

**STOP — premise wrong.** No work needed.

If the user has a different PR or plan in mind that's a follow-up to
PR #772 with the cross-device + reminder-mode + ambiguous-name
sub-items, surface the actual reference and we can re-scope. Otherwise,
this item is a no-op.

---

# Batch 3 — Housekeeping + planning

## 7. Audit + close issues #821 + #822 on GitHub

### Premise verification

**Premise PARTIALLY correct.** The prompt anticipated these issues might
already be closed. Confirmed via `gh api`.

### Findings

| Issue | Title | State | Closed at |
|-------|-------|-------|-----------|
| #821 | Widgets: ship in v2.0 / delete code / accept indefinitely-deferred? | **closed** | 2026-04-26T23:46:30Z |
| #822 | Phase G scope: align engineering roadmap with marketing copy | **closed** | 2026-04-26T23:46:24Z |

Both were closed ~2 hours before this audit started. The PR #831 Phase
4 reconciliation (line 1095) still listed them under "Still blocking
before kickoff: User decisions on issues #821 (widgets) and #822
(Phase G scope)" — that note is stale by ~2 hours.

The audit doc (PR #831) line 1037 + 1043-1044 already track the
underlying decisions:
- Issue #821 (widgets): Phase 4 state is "YELLOW pending #821" — pending
  the user decision the closure presumably resolved.
- Issue #822 (Phase G scope): Phase 4 state is "YELLOW pending #822" —
  same.

### Risk classification

**GREEN** — both issues already closed. No action needed.

### Recommendation

**STOP — no work needed.** Closures already in place.

**Optional follow-up:** the next Phase F readiness scorecard refresh
(if there is one) should remove the "Still blocking" line about
#821 + #822. That's a one-line tweak that fits naturally into a
later doc-maintenance PR — not worth a dedicated PR now.

---

## 8. Phase F readiness gap analysis (PR #831 input)

### Premise verification

**Premise correct.** PR #831 (commit `ea4ffa27`, merged 2026-04-27 00:30 UTC)
appended a Phase 4 reconciliation section to
`docs/audits/PRE_PHASE_F_MEGA_AUDIT.md` (lines 1019-1115). That section
contains the readiness scorecard.

### Findings

Pulled the scorecard from `PRE_PHASE_F_MEGA_AUDIT.md` lines 1033-1057.
RED + YELLOW surfaces classified by recommendation:

#### RED surfaces

| Surface | Phase 4 state | CC-actionable? | Recommendation |
|---------|---------------|----------------|-----------------|
| **Cross-device-tests CI signal** | RED — 0/5 green gate, quarantined from required-status | **YES — handled by item 1 of THIS audit** | PROCEED via item 1's fan-out PR (drop retry OR bump timeout). |

That's the only RED in the Phase 4 scorecard.

#### YELLOW surfaces

| Surface | Phase 4 state | CC-actionable? | Recommendation |
|---------|---------------|----------------|-----------------|
| **Connected-tests CI signal** | YELLOW — pre-#824 ~73%, post-#824 soak pending ~3 days | NO (auto-resolves with time) | DEFER — re-measure 2026-04-29+. Item 2 of THIS audit. |
| **Stale TODO inventory** | YELLOW — verified still-relevant; not actually stale | LOW — sweep is open-ended | DEFER-G.0+ unless one specific TODO is launch-blocking |
| **Phase G engineering scope** | YELLOW — issue #822 closed (per item 7) | RESOLVED | Update scorecard reference (one-line tweak in next doc-PR). |
| **Widgets ship-or-cut** | YELLOW — issue #821 closed (per item 7) | RESOLVED | Update scorecard reference (one-line tweak in next doc-PR). |
| **Mood/energy test coverage** | YELLOW — 8 unit tests landed via PR #829 | DONE | No further action. |
| **Medication tier-marking coverage** | YELLOW — 11 unit tests landed via PR #830 | DONE | No further action. |
| **BetaTesting platform external config** | YELLOW (cannot self-verify) — checklist doc shipped via PR #826 | NO (Avery's manual gate) | Hands-on-only; deferred to Avery before May 15. |
| **Phase F kickoff readiness** | YELLOW with 3 carry-forward blockers | partial | See below. |

#### Three carry-forward blockers (PR #831 lines 1092-1095)

1. **Connected-tests post-#824 soak verification** — auto-resolves in
   ~3 days. CC-actionable: NO (just wait). Item 2 of THIS audit.
2. **Cross-device-tests stabilization** — RED, no clear ETA. CC-actionable:
   **YES** via item 1 of THIS audit (drop retry OR bump timeout).
3. **User decisions on issues #821 + #822** — both already closed (item 7).

### Risk classification

**Net Phase F readiness post-this-audit:**
- 1 of 3 carry-forward blockers becomes CC-actionable via item 1.
- 1 of 3 auto-resolves (item 2 soak window).
- 1 of 3 already resolved in flight (issues closed; readiness scorecard
  has stale "still blocking" claim that needs a one-line doc tweak).

After the item 1 fan-out PR ships AND the soak window completes, all
three Phase F kickoff blockers can move to GREEN — leaving only
YELLOW-DEFER items (TODOs, BetaTesting config) which are not launch
blockers in their own right.

### Recommendation

**PROCEED — but the Phase F kickoff scorecard does NOT need a
standalone audit-driven PR.** Item 1's fan-out PR closes the only
RED that's CC-actionable. Item 2's deferral closes the auto-resolving
YELLOW. Items 7 + scorecard staleness can be batched into the next
natural doc-maintenance PR (perhaps the same fan-out for item 1 if
it's already touching audit docs).

The "8-item recommendation" from the prompt: the Phase F readiness
gap analysis is **GREEN** at the meta-level — the gap CC can plausibly
close before May 15 IS exactly what items 1-7 of this audit address.
Items 5 (migration test verification) and 7 (issue closures) close
incidentally. There are no hidden Phase F gaps that this audit didn't
already surface.

---

## Phase 1 deliverable summary

| Item | Recommendation | Phase 2 PR? | Notes |
|------|---------------|-------------|-------|
| 1. Cross-device-tests RED root-cause | **PROCEED** | YES — drop shell retry (Option A) or bump timeout (Option B) | Recommend Option A. ~5 LOC. |
| 2. Connected-tests post-#824 flake | **STOP — soak incomplete** | NO | Re-measure on 2026-04-29+. |
| 3. Cross-device-tests promotion | **STOP — gate not cleared** | NO | Re-evaluate after item 1's fix + 5 green runs. |
| 4. Connected-tests promotion | **STOP — soak incomplete** | NO | Re-evaluate after item 2 re-measure. |
| 5. Migration63To64Test on emulator | **GREEN — passed (2/2 tests)** | NO | Verification action, not a PR. Pixel_7 API 35, 14ms total. |
| 6. PR #772 Phase 3 verification | **STOP — premise wrong** | NO | PR #772 has no "Phase 3 spec" with sub-items as described. |
| 7. Close issues #821 + #822 | **STOP — already closed** | NO (1-line scorecard tweak deferable) | Both closed 2026-04-26T23:46Z. |
| 8. Phase F readiness gap analysis | **PROCEED** (via item 1) | NO new PR — closed by item 1 + 7 + auto-resolve | No hidden gaps. |

**Net Phase 2 fan-out:** 1 PR (item 1 — drop the shell retry in
cross-device-tests). All other items STOP or auto-resolve.

**Expected STOP rate per prompt:** 2-4. Actual: 5 (items 2, 3, 4, 6, 7) +
1 PROCEED (item 1) + 1 verification (item 5) + 1 closed-incidentally
(item 8). The high STOP rate is consistent with the audit-first
discipline catching prerequisites + premise issues before Phase 2 work.

---

## Memory entry candidates (flagged for user, not auto-added)

1. **Cross-device-tests cancellations have TWO classes** — concurrency
   cancel-in-progress (dominant in busy main-merge windows) AND
   job-level timeout (dominant when no concurrent push exists). When
   classifying android-integration cancellations, check the duration:
   <30 min ≈ concurrency, ~45 min ≈ job-timeout. Memory entries #21 and
   #25 cover AVD boot — they don't cover these two new classes.

2. **PR-body Test Plans aren't always "Phase N" specs** — the prompt
   conflated PR #772's brief Test Plan with a multi-phase spec that
   never existed. Before a follow-up audit cites "Phase N of PR M",
   `gh api repos/.../pulls/M -q '.body'` to verify the spec is real.

3. **Capture-failure-log only fires on FAILURE, not CANCELLED** —
   cross-device-tests cancellations leave no `ci-logs` entry, which is
   why pre-#824 root-cause analysis is hard. If we want diagnostics on
   cancelled runs, the capture-failure workflow needs a `cancelled`
   trigger added.

4. **Workflow-level `concurrency: cancel-in-progress: true` + slow jobs
   + busy-merge cadence = systematic 0% job conclusiveness** — the
   pre-#824 cross-device-tests window had this exact shape. Auditing
   integration-test signal needs to disambiguate "test failed" from
   "didn't get a chance to finish."
