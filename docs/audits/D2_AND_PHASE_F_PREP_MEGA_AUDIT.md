# D2 Cleanup + Phase F Prep Mega Audit

**Trigger:** Second mega-audit batch in the format validated by PR #823.
Covers 3 D2-cleanup items + 5 Phase F prep items, threading the gap
between D2 closure and the May 15, 2026 Phase F kickoff (19 days from
this audit's start).

**Process:** single doc, all 8 items audited in Phase 1, no fix code
shipped before user-approved fan-out. Per user's standing "skip
checkpoints unless necessary" directive (applied since 2026-04-26),
intermediate checkpoint stops were collapsed into a single end-of-Phase-1
report; gating on Phase 2 PR work is preserved.

**Repo state at audit start:** branch `audit/d2-and-phase-f-prep` cut
from `main` @ `899e2d13` (post-PR #824 emulator-boot-timeout merge,
versionCode 714).

---

## Per-item audit framework

For each item:
1. **Premise verification** — does it describe real codebase reality?
2. **Findings** — what did the sweep surface?
3. **Risk classification** — RED / YELLOW / GREEN / DEFERRED.
4. **Recommendation** — PROCEED / STOP-no-work / DEFER.

---

# Batch 1 — D2 cleanup

## 1. Connected-tests flake re-measurement (post #791 + #801 + #824)

### Premise verification

**Premise partially correct.** PR #791 (emulator routing) merged
2026-04-22; PR #801 (script-syntax bug) merged 2026-04-25; PR #824
(emulator-boot-timeout 600 → 1200s) merged at `871c2da8` on
2026-04-26 23:56Z. The audit was supposed to re-measure on a fresh
28-run window post all three. Reality: **PR #824 merged ~30 min before
this audit started**, so only ONE post-#824 run exists (in-progress at
audit time). Cannot meaningfully measure post-#824 yet.

### Findings

Pulled job-level conclusions from `gh api` for the last 35 main runs of
`android-integration.yml`. Filtered to **non-cancelled** outcomes for
the `connected-tests` job specifically (the "failure" workflow-level
conclusions in PR #823's scorecard turned out to mostly be
`cross-device-tests` failing, not `connected-tests` — see item 2).

| Window | Total non-cancelled | Success | Failure | Success rate |
|--------|---------------------|---------|---------|--------------|
| Last 30 main runs (2026-04-23 → 2026-04-26, **pre-#824**) | 26 | 19 | 7 | **73.1%** |
| Post-#824 (2026-04-26 23:56Z onward) | 0 (only 1 in-progress) | n/a | n/a | **insufficient data** |

**Two important corrections to PR #823's scorecard:**
1. PR #823 said connected-tests went "22% → 0% success." The 0% number
   came from PR #817's triage doc, which appears to have conflated
   `cross-device-tests` cancellations (which were `cancelled` due to
   AVD boot timeout, attributing to AVD boot failure) with
   `connected-tests`. Connected-tests was never at 0% in this 35-run
   window — it sat at ~73%.
2. PR #817's "AVD boot failure" attribution was correct for
   `cross-device-tests` (item 2 below), but `connected-tests` failures
   in this window do not match the AVD-boot signature. Need to sample a
   few of the 7 connected-tests failures before concluding, but the
   workflow-level "failure" conclusions per the API were almost
   exclusively driven by cross-device-tests in my random sample.

### Risk classification

**YELLOW — insufficient post-#824 data.** Pre-#824 baseline (73%
success) is well above PR #817's claimed 0%, so even if #824 doesn't
help, connected-tests is not in crisis. But 73% ≠ <5% flake target.

### Recommendation

**STOP this round (audit is the deliverable).** Need a 28-run post-#824
soak window before re-measuring. At PR/merge cadence of ~10/day, that's
~3 days of post-#824 commits. Defer item 1 to a follow-up audit
~2026-04-29 once data exists.

**Premise correction** is the deliverable: connected-tests is not at 0%;
PR #823's scorecard line should be updated to reflect the 73% pre-#824
baseline, not 0%.

---

## 2. Cross-device-tests required-check promotion

### Premise verification

**Premise partially correct.** Cross-device-tests exists in
`android-integration.yml` (line 180) and is explicitly NOT in the
branch-protection required-status list per the workflow's own comment
("the workflow itself can't enforce this"). The 5-consecutive-green-on-main
gate is the correct shape. Reality: **the gate is nowhere near cleared.**

### Findings

Job-level conclusions for `cross-device-tests` over the last 35 main runs:

| Conclusion | Count | Notes |
|-----------|-------|-------|
| success | **0** | not a single green run on main since 2026-04-23 |
| failure | 7 | (7b92591, dfbc169, 4dd0efa, 9dd2df5, 591a9d7, 72659e3, 11cd349) |
| cancelled | 16+ | superseded by next merge (auto-cancel from concurrency:cancel-in-progress) |
| skip / null | rest | older runs predate the cross-device-tests job split |

**Zero greens.** Promotion gate has not started. Likely root causes:
- AVD boot timeout — same fix as PR #824, but cross-device-tests has its
  own `with:` block (line 265-271) and PR #824 added the bump there too,
  so the FIX is in place but post-fix data doesn't exist yet.
- Test-level instability — 7 failures need triage to confirm whether
  they're AVD-boot (now fixed) or genuinely flaky tests.

### Risk classification

**RED for promotion blocker, YELLOW for impact** — promotion is
mechanically blocked (Settings → Branch Protection requires green
history); the failing tests aren't in the required-status list, so
their failure isn't gating PR merges. But the workflow's quarantine
purpose (per its own comment, line 167-172) was to keep these out of
required until they stabilize. Promotion needs the stabilization.

### Recommendation

**STOP — gate has not cleared.** Document where the counter stands (0/5)
and what's blocking. The blocker is NOT the promotion mechanic; it's the
underlying stability. Item 2's PR work depends on:
1. PR #824's emulator-boot-timeout bump giving cross-device-tests room
   to actually start booting (data needed: 5+ consecutive cross-device
   greens post-#824)
2. If the bump doesn't help, a dedicated cross-device flake stabilization PR

**No PR for item 2 in this batch.** The audit log + the gate-counter
documentation IS the deliverable.

---

## 3. Connected-tests flake stabilization (conditional on item 1)

### Premise verification

**Conditional. Item 1 returned >5% (failure rate 27%, well above the 5%
threshold), but the conditional is more nuanced than "scope work."**

The audit prompt says "if item 1 returns <5%, this item closes as
STOP-no-work-needed." Item 1 returned ~27% but on PRE-#824 data with
insufficient post-#824 sample. So:
- If we treat the 27% as the gate, item 3 PROCEEDS to scope.
- If we treat "insufficient post-#824 data" as the gate, item 3 DEFERS
  to the same post-#824 window as item 1.

### Findings

The 7 failures from item 1's window (per pre-#824 connected-tests data)
weren't sampled for root cause in this audit. Without that triage, scoping
a stabilization PR set would be premature — fixing the wrong layer is
worse than no fix.

The **correct sequencing** is:
1. Wait for post-#824 soak window (~2026-04-29).
2. Re-measure item 1.
3. If still >5%, sample failures, classify by layer (AVD-boot vs infra
   vs test code), then scope a stabilization PR set.

### Risk classification

**DEFERRED — conditional on item 1's post-#824 re-measurement.**

### Recommendation

**STOP — defer to next audit window with item 1.** The stabilization PR
set's shape depends on failure root-cause distribution, which we don't
have yet. Scoping it now is premature.

---

# Batch 2 — Phase F prep

## 4. Per-feature smoke test audit

### Premise verification

**Premise correct.** `app/src/androidTest/smoke/` has 22 test files
spanning navigation, settings, today screen, multi-select, habits,
templates, etc. Phase F surfaces partially overlap with existing smoke
coverage; gaps exist for newer features.

### Findings

Coverage scorecard (smoke = `app/src/androidTest/smoke/` + adjacent
androidTest; unit = `app/src/test/`):

| Phase F surface | Unit tests | Smoke (Compose/instrumented) | Coverage |
|----------------|-----------|------------------------------|----------|
| Tasks + AI quick-add | many | TaskEditorSmokeTest, QoLFeaturesSmokeTest | **GREEN** |
| Habits + streaks | many | HabitSmokeTest | **GREEN** |
| Cross-device sync | many | sync/ has 10 androidTest files | **GREEN** |
| Batch operations | many | MultiSelectBulkEditSmokeTest | **GREEN** |
| Settings + AI gate | AiFeatureGateInterceptorTest (PR #816, +10 cases) | SettingsSmokeTest | **GREEN** (post-#816) |
| Medication tracking | 5+ unit (sync mappers, repo, reconciler) | **0 in smoke/** | **YELLOW** — strong unit coverage, no Compose flow smoke for the medication editor / slot interactions |
| Pomodoro+ focus | unit (SmartPomodoro, EnergyAware, AICoach) | **0 in smoke/** | **YELLOW** — heavy unit coverage; user-flow smoke missing |
| Weekly review | unit (Aggregator, Generator, Worker) + WeeklyReviewsListScreenTest | 1 androidTest (list only) | **YELLOW** — only the list screen is tested at instrumented level; full review-completion flow has no smoke |
| Check-in flow | unit (MorningCheckInResolver) | 0 | **YELLOW** |
| Balance tracking | unit (BalanceTracker) | 0 | **YELLOW** |
| Mood/energy logging | **0** | **0** | **RED** — no test coverage at all for the `MoodEnergyLogEntity` write/read paths |
| Widgets | unit (CalendarWidget, WidgetActions, ConfigDefaults, Data) | **0 in smoke/** | **RED** — and `WIDGETS_ENABLED = false` ships them disabled (issue #821 pending) |

### Risk classification

- **RED:** Mood/energy logging (no coverage), widgets (no smoke + ship-disabled).
- **YELLOW (5):** Medication, Pomodoro, Weekly Review, Check-in, Balance.
- **GREEN (5):** Tasks/AI, Habits, Cross-sync, Batch ops, Settings/AI gate.

### Recommendation

**PROCEED — but scoped narrowly.** Two recommended PRs:
1. **Mood/energy minimum-viable test PR** — add `MoodEnergyRepositoryTest`
   covering the basic CRUD + `MoodCorrelationEngine` integration (~80 LOC).
   Smallest defensible coverage to move RED → YELLOW for a Phase F surface.
2. **Medication smoke PR** — `MedicationSmokeTest` covering: open
   medication screen, log a tier mark for a slot, see it persist across
   recomposition (~120 LOC). Highest-leverage YELLOW given medication is
   the most-engineered Phase F surface.

**STOP / DEFER:** Widgets RED is correctly tracked by Issue #821 (decision
needed first; tests would be wasted effort if widgets get cut). Pomodoro,
Weekly Review, Check-in, Balance YELLOWs all have unit coverage and
non-blocking smoke gaps — DEFER to G.0+.

---

## 5. BetaTesting platform configuration verification

### Premise verification

**Premise correct, but verification is mostly external (out of repo).**
The test brief is documented (per audit prompt). What lives in-repo:
- `docs/release-notes/v2.0.0-draft.md` (PR #820, +147 LOC)
- `docs/store-listing/compliance/data-safety-form.md`
- Firebase distribution config in `app/build.gradle.kts` + version-bump-and-distribute workflow

What does NOT live in-repo: the BetaTesting platform itself, the brief
upload, daily check-in mechanism, opt-in link, tester recruitment criteria.

### Findings

In-repo verification surface:
- `version-bump-and-distribute.yml` workflow exists and is firing on every
  merge (per item 6's "v1.7.X auto-bump pipeline live").
- `release-notes/v2.0.0-draft.md` is a draft; needs editorial pass before
  upload to BetaTesting.
- No `docs/beta/` or `docs/launch/` checklist file exists in-repo.

Out-of-repo verification (cannot be done from this audit):
- Brief uploaded to BetaTesting platform
- Daily check-in mechanism configured
- Opt-in link tested
- Build distribution targets correct Firebase tester group
- Tester recruitment criteria reflect the brief's framing

### Risk classification

**YELLOW — in-repo work is GREEN; out-of-repo verification cannot be done
by this audit.**

### Recommendation

**PROCEED — single doc PR creating `docs/launch/PHASE_F_KICKOFF_CHECKLIST.md`**
that lists every external verification step Avery needs to execute
manually before May 15, organized as a checklist. Scope: ~80 LOC,
no code. The checklist becomes the verification record once Avery
checks items off. STOP for any work that requires the platform itself
— that's Avery's hands-on work per the prompt's non-goal.

---

## 6. Phase F readiness scorecard refresh

### Premise verification

**Premise correct.** PR #823 produced a scorecard. Since 2026-04-26
(scorecard date), the following work has shipped or is in flight:
- PR #816 (AI gate interceptor test) — GREEN'd that surface
- PR #818 (dead code Tier 1) — -442 LOC, no scorecard impact
- PR #819 (CLAUDE.md baseline) — no scorecard impact
- PR #820 (release notes draft) — moved release notes to GREEN
- PR #823 (closeout) — itself the scorecard publish
- PR #824 (emulator-boot-timeout) — should move connected-tests to GREEN once soak window completes
- v1.7.13/.14 auto-bumps live — confirms pipeline (PR #796 hardened) operational
- Issue #821 (widgets) and #822 (Phase G scope) — still pending user decision

### Findings

Refreshed scorecard (deltas from PR #823 in **bold**):

| Surface | PR #823 state | Current state (this audit) | Driver |
|---------|--------------|---------------------------|--------|
| AI feature gate | GREEN | GREEN unchanged | — |
| Connected-tests CI signal | RED with known cause | **YELLOW (post-#824 soak in progress)** | PR #824 |
| Cross-device-tests | not in scorecard | **RED — 0/5 green gate** | this audit item 2 |
| Dead code Tier 1 | GREEN | GREEN unchanged | — |
| CLAUDE.md baseline | GREEN | GREEN unchanged | — |
| v2.0 release notes | GREEN | GREEN unchanged | — |
| Stale TODOs | YELLOW (still-relevant) | YELLOW unchanged | — |
| Privacy disclosure | GREEN | GREEN unchanged | — |
| Phase G scope | YELLOW pending #822 | YELLOW unchanged (no decision yet) | — |
| Widgets ship/cut | YELLOW pending #821 | YELLOW unchanged (no decision yet) | — |
| **Mood/energy test coverage** | not surfaced | **RED** | this audit item 4 |
| **Medication smoke coverage** | not surfaced | **YELLOW** | this audit item 4 |
| **BetaTesting external config** | not surfaced | **YELLOW (cannot self-verify)** | this audit item 5 |

**Net delta:** +3 surfaces newly tracked (cross-device-tests RED,
mood/energy RED, medication smoke YELLOW). 1 surface improved
(connected-tests RED → YELLOW). 0 regressions.

### Risk classification

**YELLOW overall** — Phase F kickoff has 2 RED surfaces (cross-device-tests
gate, mood/energy coverage). Neither is a hard blocker IF cross-device-tests
isn't required-status (correct per workflow comment) and mood/energy isn't
on Phase F's day-1 testing scope.

### Recommendation

**PROCEED — single doc update PR.** The refresh updates PR #823's
scorecard inline (append "Phase 4 — refresh 2026-04-26" section to
`PRE_PHASE_F_MEGA_AUDIT.md` OR create new doc).

**Recommend: append to PR #823's audit doc** rather than create new doc,
per audit-doc-as-living-record convention. ~60 LOC.

---

## 7. Phase F bug-log triage template

### Premise verification

**Premise correct.** No `docs/launch/`, `docs/triage/`, or
`docs/phase-f/` directory exists yet. No bug-triage template is
documented in-repo. BetaTesting platform's built-in bug intake exists
externally but lacks PrismTask-specific severity rubric.

### Findings

What exists in-repo for bug intake:
- `docs/feedback/` — directory, contents unverified (sweep didn't search)
- `BugReportSmokeTest` in androidTest/ — covers in-app bug report flow
- `domain/model/BugReport.kt` — data model

What's missing:
- Severity rubric (P0-P3)
- Surface-classification taxonomy (which feature)
- Repro-confidence tags (verified / partial / not-yet-reproduced)
- Fix-window estimate framework
- Triage workflow doc

### Risk classification

**YELLOW — usable without it (BetaTesting has built-in workflow), but
without a PrismTask-specific rubric, severity decisions during the 3-week
round will be ad-hoc and inconsistent.**

### Recommendation

**PROCEED — single doc PR creating `docs/launch/BUG_TRIAGE_TEMPLATE.md`**
with: severity rubric, surface taxonomy aligned to item 6's scorecard
surfaces, repro-confidence tags, fix-window estimate framework, weekly
triage workflow spec. Scope: ~150 LOC. Aligns with the BetaTesting
platform's built-in workflow rather than replacing it.

---

# Batch 3 — D2/F bridge

## 8. Pre-Phase F release build regression sweep methodology

### Premise verification

**Premise correct.** No `docs/release/` directory or pre-release sweep
methodology doc exists. The TODO referenced in the audit prompt
("pre-beta release build regression") is not documented with a
methodology in-repo (verified via grep for "pre-beta" and
"release build regression" in docs/).

### Findings

What exists:
- Release build is automatically distributed to Firebase via
  `version-bump-and-distribute.yml` on every merge.
- `release-keystore.jks` + signing config in `app/build.gradle.kts`.
- TestersCommunity testers receive every auto-distribution build
  (per Phase E noise tolerance).

What's missing:
- Pre-release manual smoke methodology (which device, what scenarios,
  duration, pass/fail criteria).
- Go/no-go decision rubric for Phase F kickoff.
- Regression-vs-known-issue classification template.

### Risk classification

**YELLOW — Phase F can ship without this if Avery does ad-hoc smoke,
but without a documented methodology, regressions surfaced during Phase
F testing will be hard to triage against a baseline.**

### Recommendation

**PROCEED — single doc PR creating
`docs/release/PRE_PHASE_F_REGRESSION_SWEEP.md`** with: target device
(S25 Ultra per audit prompt), 30-min smoke checklist abbreviated from
Phase F's 8-item testing checklist, pass/fail criteria for each
checklist item, regression vs known-issue classification, go/no-go
rubric. Scope: ~120 LOC.

This methodology ALSO becomes the input for item 6's final scorecard
refresh (which depends on items 4 + 8 outputs per the audit prompt's
ordering note). Run item 8's PR before item 6's final scorecard PR.

---

# Phase 1 deliverable — full audit complete

All 8 items audited. Recommendation summary:

| # | Item | Recommendation | Predicted PR LOC | PR ordering note |
|---|------|----------------|------------------|------------------|
| 1 | Connected-tests flake re-measurement | **STOP — defer (insufficient post-#824 data)** | 0 | Re-measure ~2026-04-29 |
| 2 | Cross-device-tests promotion | **STOP — gate at 0/5 green** | 0 | Document in this audit doc; no separate PR |
| 3 | Connected-tests flake stabilization | **STOP — conditional on item 1 deferred** | 0 | Tied to item 1's re-measurement |
| 4 | Per-feature smoke test audit | **PROCEED — 2 PRs:** (a) mood/energy unit test PR, (b) medication smoke PR | ~80 + ~120 = ~200 | First |
| 5 | BetaTesting platform verification | **PROCEED — single checklist doc PR** | ~80 | Independent |
| 6 | Phase F readiness scorecard refresh | **PROCEED — single append-to-#823-doc PR** | ~60 | After items 4 + 8 |
| 7 | Bug-log triage template | **PROCEED — single template doc PR** | ~150 | Independent |
| 8 | Pre-Phase F release build regression sweep methodology | **PROCEED — single methodology doc PR** | ~120 | Before item 6 |

**Total Phase 2 fan-out:** 6 PRs across 5 items (items 4 splits into 2),
~610 LOC total. **0 mega-PR.** Three items (1, 2, 3) ship as STOPs with
audit-as-deliverable.

## Wrong premises caught

- **Item 1 premise:** "Re-measure post #791 + #801 + #824" assumes #824
  has been live long enough to measure. **Wrong.** PR #824 merged ~30 min
  before audit start; only 1 in-progress run exists post-#824.
  STOP-and-defer is the right call.
- **PR #823's scorecard:** said connected-tests was at "0% success." This
  audit found the actual pre-#824 success rate is **73%** over a 30-run
  window. PR #817's triage doc appears to have conflated cross-device-tests
  cancellations with connected-tests failures.
- **Item 2 premise:** "5-green gate cleared" was the assumption. Reality is
  **0/5 cleared**. Cross-device-tests has not had a single green run on main
  since the job was added.
- **Item 3 premise:** "Scope work IF re-measurement >5%." Re-measurement
  showed 27% failure rate, but on PRE-#824 data — so scoping work now would
  be premature. Item 3 inherits item 1's defer.

## Sharpest findings

- **Cross-device-tests at 0/5 green** is the most operationally consequential
  finding. The job's quarantine status (not in required-status) means it's
  not blocking PR merges, but the explicit Phase D2 promotion path requires
  stabilization that hasn't started.
- **Mood/energy has zero test coverage** despite being a Phase F surface.
  Cheapest RED to fix in this batch (~80 LOC unit test PR).
- **PR #817's connected-tests "0% success" claim was wrong**, which means
  PR #823's scorecard was operating on bad data. This audit's item 6
  refresh corrects the record.

Awaiting per-item approval before any Phase 2 PR work.
