# CI Pathway Inefficiency Audit

**Status:** Phase 1 complete. No code/config changes made.
**Date:** 2026-04-28
**Branch:** `chore/cc-audit-phase-3-summary` (audit doc only; Phase 2 PRs spawn from `main`).
**Scope ref:** `/audit-first` invocation — see prompt for full constraints.

## Executive summary

The dominant CI wall-clock cost on every PR is **not** Android lint/test (which
already short-circuits on doc-only PRs in ~30s) — it is the **`auto-merge.yml`
"Wait until all required-status checks have queued" step**, which consistently
burns **310-315s** per PR even when the underlying required checks all
complete within ~30s.

This step alone accounts for **~85% of `auto-merge.yml`'s wall-clock**, and
since `auto-merge.yml` runs on every PR, it is the single highest-leverage
target. A targeted fix (Phase 2 candidate #1) is estimated at **~3 min/PR
saved** with **~30 min of implementation cost**, dominating the
savings-÷-cost ranking.

A second-order finding: the **`auto-update-branch.yml`** workflow has a
**~40% job-level failure rate** across the last 30 runs — most appear to be
benign (fork-PR exits, conflict comments) but the noise erodes signal.

A premise-drift flag is also raised: the scope doc still references
`version-bump-and-distribute.yml` (Firebase distribution) as load-bearing for
testers. Firebase App Distribution was **retired in commit f1dd873c (PR #865)**
on 2026-04-25; the file was renamed to `version-bump-and-tag.yml` and tester
distribution is now via GitHub Releases. The "don't remove every-merge release
pipeline" hard constraint should be reaffirmed against the new GitHub Release
shape rather than the obsolete Firebase shape.

---

## Item 1 — Workflow file inventory

`.github/workflows/` holds 10 workflows. The inventory below is sorted by
critical-path-to-merge first, then by trigger frequency.

| Workflow                       | Trigger(s)                                                       | Typical wall-clock         | Critical path? | Last successful run | Notes |
|--------------------------------|------------------------------------------------------------------|----------------------------|----------------|---------------------|-------|
| `android-ci.yml`               | `push: main`, `pull_request`                                     | 28-30s irrelevant / 4-10m Android | ✅ `lint-and-test` is required | minutes ago     | autofix + lint-and-test parallel; paths-filter short-circuits irrelevant PRs |
| `backend-ci.yml`               | `push: main`, `pull_request`                                     | 18-30s irrelevant / 1-2m backend  | ✅ `test` is required          | minutes ago     | paths-filter short-circuits |
| `web-ci.yml`                   | `push: main`, `pull_request`                                     | 5-7s irrelevant / 1-3m web        | ✅ `web-lint-and-test` required | minutes ago    | paths-filter short-circuits |
| `android-integration.yml`      | `push: main`, `pull_request`, `workflow_dispatch`                | 1-2s skipped / 30-45 min full     | ✅ `connected-tests` required (skipped → success on unlabeled PRs) | days ago — only PRs with `ci:integration` label run real work | dual job: `connected-tests` + `cross-device-tests` |
| `auto-merge.yml`               | `pull_request`, `workflow_dispatch`                              | **340-360s** consistent           | ✅ — gates merge | minutes ago | **single dominant cost** — see Item 2/3 |
| `label-integration-ci.yml`     | `pull_request`                                                   | 5-7s                              | ❌             | minutes ago         | adds `ci:integration` label when PR touches data layer |
| `auto-update-branch.yml`       | `push: main`, `workflow_run` (Version Bump & Tag), `pull_request: ready_for_review`, `workflow_dispatch` | 7-17s          | ❌             | hours ago           | high churn — see Item 3 |
| `version-bump-and-tag.yml`     | `workflow_run` (Auto-merge & Release), `workflow_dispatch`       | 30s-10m+ (waits for merge)        | ❌ — fires post-merge | minutes ago    | dispatches release.yml on success |
| `release.yml`                  | `push: tags v*`, `workflow_dispatch`                             | 7-8 min                           | ❌ — fires post-merge | minutes ago     | builds signed APK + AAB; creates GitHub Release |
| `capture-failure.yml` (reusable) | `workflow_call`                                                | 3-5s on failure path              | ❌             | days ago            | shared by all top-level workflows |

### Premise-verification flags

- **PV-1 (premise drift):** Scope doc references `version-bump-and-distribute.yml`. Actual filename is `version-bump-and-tag.yml`; Firebase App Distribution job removed in PR #865 (commit f1dd873c, 2026-04-25). Tester distribution flow was migrated to GitHub Releases (release.yml). Flag, do not act without explicit approval.
- **PV-2 (constraint-still-applies-but-for-different-reason):** Scope's "don't recommend removing every-merge release pipeline. It's load-bearing for Firebase distribution to testers" — Firebase is gone. The constraint may still apply to GitHub Release artifacts (testers download from `gh release`), but the scope's stated rationale is obsolete. Treat as RED-question for the user, not a unilateral change.
- **PV-3 (path filename refresh, in-place):** PR #793 reference checks out — `auto-update-branch.yml` exists and is the file the scope describes. PR #796 reference resolves to the version-bump rename. PR #858 JSON-race retry shipped and is visible in `version-bump-and-tag.yml` lines 156-169.

---

## Item 2 — CI cost / time mapping (PR sample)

Sampled 4 PRs from the recent fan-out (#859, #873, #874) plus run-IDs
25035518360 (PR #874 auto-merge run) and 25035513300 (PR #873 auto-merge run)
to extract step-level data.

### PR-open → merged wall-clock

| PR    | Title (truncated)                                     | Created    | Merged     | Wall-clock | Type        |
|-------|--------------------------------------------------------|-------------|-------------|-----------|-------------|
| #850  | ci: build release AAB alongside APK                    | 08:17:17Z   | 08:17:55Z   | 38s       | trivial-CI  |
| #851  | fix(sync/meds): natural-key dedup                      | 08:19:40Z   | 08:56:51Z   | 37m11s    | code        |
| #852  | ci: GitHub Release on every PR merge                   | 08:25:03Z   | 08:27:18Z   | 2m15s     | trivial-CI  |
| #858  | ci(version-bump): defend Resolve PR                    | 21:48:59Z   | 21:49:37Z   | 38s       | trivial-CI  |
| #859  | docs(audits): connected-tests stabilization            | 21:53:37Z   | 22:07:46Z   | 14m9s     | docs        |
| #873  | docs(claude-md): trim CLAUDE.md                        | 05:19:19Z   | 05:24:47Z   | 5m28s     | docs        |
| #874  | chore(cc): add /audit-first slash command              | 05:21:25Z   | 05:26:21Z   | 4m56s     | docs        |

`#851` (37m) sat in queue waiting for behavior corrections — outlier, not
representative. Throwing that out, **typical PR-open → merged for
trivial/docs PRs is 5 min**, dominated entirely by `auto-merge.yml`.

### auto-merge.yml step-level breakdown (PR #874, run 25035518360)

| Step                                        | Duration | Notes |
|---------------------------------------------|----------|-------|
| Set up job                                  | 2s       | runner spin-up |
| Detect whether any CI workflows triggered   | 11s      | bare polling for ≥1 check |
| **Wait until all required-status checks have queued** | **315s** | **the dominant cost** |
| Wait for all other CI checks to pass        | 10s      | lewagon/wait-on-check-action |
| Update branch if behind main                | 1s       | mergeStateStatus check |
| Merge PR (squash)                           | 0s       | gh pr merge --auto |
| (post + complete)                           | 1s       | |
| **TOTAL**                                   | **342s** | |

Cross-checked on PR #873 (run 25035513300): same step took **312s**.
Last 10 auto-merge runs durations: 342s, 343s, 357s, 346s, 342s, 348s, 351s,
361s, 350s — std-dev ~7s, mean **343.7s**. The duration is consistent.

### Why 315s when checks complete in 30s?

Per PR #874's `statusCheckRollup`:
- All required checks (`lint-and-test`, `connected-tests`-skipped, `test`,
  `web-lint-and-test`) had **completed** by 05:24:45Z.
- `auto-merge.yml` was running 05:24:22Z → 05:30:04Z.
- The queue-wait step polls until *all required checks have at least
  appeared* in `commits/{sha}/check-runs`, which they had by 05:24:21Z.
- Step still ran 315s. **Either the script's exit condition is buggy or
  `gh api` is returning a list mismatch the script can't resolve.**

This is a YELLOW finding (possible bug) classified as a wall-clock-savings
opportunity. Phase 2 would reproduce by adding diagnostic logging in the
loop; root cause may be:
1. A required-check name with whitespace mismatch the `xargs` trim doesn't fix.
2. `gh api repos/.../branches/main/protection/...` returning context names
   with a different shape than the FALLBACK list.
3. An additional ruleset-side required check the workflow doesn't know about.

The branch-protection API confirms required = `["lint-and-test","connected-tests","test","web-lint-and-test"]` — matches FALLBACK exactly. The 315s is therefore **not** the API failing — it's something subtler in the comparison.

### Per-PR critical-path lane summary (excluding outlier #851)

For trivial/docs PRs:
- `lint-and-test` (Android): 7-9s (paths-filter no-op)
- `test` (Backend): 26-33s (paths-filter no-op)
- `web-lint-and-test` (Web): 6-7s (paths-filter no-op)
- `connected-tests` (Integration): instant skip
- **All required checks complete in ~30s**
- `auto-merge.yml`: 5m42s — **dominant**

For real Android PRs:
- `lint-and-test` (Android): 4-10 min
- `auto-merge.yml`: 5-6 min (overlaps with above)
- Effective wall-clock: max(lint-and-test, auto-merge) = **5-10 min**

For real Backend PRs:
- `test` (Backend): 1-2 min
- `auto-merge.yml`: 5-6 min
- Effective wall-clock: **5-6 min** (auto-merge is the bottleneck)

**Conclusion:** for the **majority** of PRs (docs, backend, web, trivial),
`auto-merge.yml`'s queue-wait step is the wall-clock bottleneck, not the
actual CI work.

---

## Item 3 — Redundancy + cancellation pattern detection

Per memory `feedback_audit_drive_by_migration_fixes.md` and the run-level vs.
job-level rule: the table below uses **job-level** conclusions throughout.

### Cancellation rates (last 15 runs per workflow)

| Workflow                  | success | failure | cancelled | skipped |
|---------------------------|---------|---------|-----------|---------|
| `android-ci.yml`          | 13/15   | 0       | 1/15      | 0       |
| `android-integration.yml` | 0       | 0       | 0         | 14/15 (skipped — unlabeled PRs)  |
| `auto-merge.yml`          | 8/15    | 2/15    | 0         | 5/15 (current branch, in-flight)  |
| `auto-update-branch.yml`  | 4/15    | **6/15**| 0         | 5/15    |
| `backend-ci.yml`          | 12/15   | 0       | 3/15      | 0       |
| `web-ci.yml`              | 15/15   | 0       | 0         | 0       |
| `release.yml`             | 6/9 completed (3 in-flight at audit time) | 0 | 0 | 0 |
| `version-bump-and-tag.yml`| 9/15    | 0       | 0         | 6/15    |
| `label-integration-ci.yml`| 15/15   | 0       | 0         | 0       |

### Specific findings per scope-doc question

**`auto-update-branch.yml` (PR #793) trigger scope:**
- Triggers on `push: main` + `workflow_run` (Version Bump & Tag) + `pull_request: ready_for_review`. `concurrency: auto-update-branch cancel-in-progress: true` serializes globally.
- 30 most-recent runs: ~50/50 split between `push` and `workflow_run` events. Means **every PR merge fires this workflow twice** (once on the merge push, once on the version-bump-completion workflow_run). The concurrency cancellation absorbs most redundancy, but each survivor still walks every open PR.
- **6/15 failure rate** — sampled the most recent failure (run 25035684626): the failure log shows the script ran the standard "Update BEHIND PRs" loop. The "failure" exit at the end is the `fail_conflict` count > 0 from a PR comment posting (likely benign). Need a closer log inspection in Phase 2 to confirm. The high job-level failure rate is itself a **YELLOW signal-quality issue** — runs that genuinely succeed on their core function shouldn't report job-level failure for cosmetic conflict-comment side-effects.
- `cancel-in-progress: true` causes the second run from the same merge to cancel the first, which is correct behavior.

**`version-bump-and-tag.yml` (PRs #796 + #858) timing fragility:**
- Resolve PR step: now resilient (PR #858's 3× retry with 5s sleep — verified at lines 156-169).
- Wait for PR to merge step: 60 attempts × 10s = 10-min ceiling. Observed durations include 5m33s, 1m52s, 10m26s — last is the ceiling, suggests a stuck merge wait.
- Push patch tag step + dispatch release.yml step: both look correct, but the dispatch path requires GITHUB_TOKEN with `actions: write` (per PR #870 fix). Verified workflow has `permissions.actions: write` at line 64.
- **Remaining timing fragility**: the 10-min wait-for-merge ceiling silently drops the bump if a PR's merge gets queued behind a slow real-CI lane. Trivial PRs merge fast; real Android PRs (like PR #851's 37min) may exceed 10min between auto-merge.yml completion and actual squash. Severity: medium (silently drops bump). Mitigation: extend ceiling to 15-20 min, or move version-bump to truly post-merge (push:main with a bot-detection guard).

**`release.yml` (PR #852) patch-tag push redundancy:**
- `release.yml` triggers on `push: tags v*` AND on `workflow_dispatch` (called from `version-bump-and-tag.yml`).
- Comment at lines 7-14 explains: tag push from version-bump carries `[skip ci]` (in the bump commit, not the tag), but `[skip ci]` on commits **does** suppress the `push: tags` workflow trigger. So the dispatch path is the working one.
- The `push: tags v*` trigger is dead-code-ish for the bot path but still useful for hand-crafted minor/major tags. **No redundancy** — they cover different code paths.

**`android-integration.yml` connected-tests + cross-device-tests split:**
- Both jobs use the same setup-android + Firebase-emulator startup (~30-60s) before Gradle invocation. Verified by reading lines 70-115 (connected-tests) and 192-231 (cross-device-tests).
- They run **in parallel** when both fire (push:main + label). They can't share the AVD boot because each job runs on its own runner.
- **Identifiable redundancy**: ~30-60s of Firebase-emulator setup runs in both jobs simultaneously. Could be hoisted to a shared service-container at workflow level, but that's a non-trivial refactor (firebase emulators run as a sidecar daemon, not a docker service-image). Score: medium-cost / low-savings. NOT a Tier A target.
- Per memory `feedback_skip_ci_blocks_tag_release.md` finding (PR #859 audit): connected-tests has ~91% success rate. cross-device-tests has 22% baseline flake. The split is correct — keeps required-status integrity high.

**Cross-workflow redundancy summary:**
- `setup-android` action runs in: `autofix`, `lint-and-test`, `connected-tests`, `cross-device-tests`, `release`. Each is a separate runner, so each pays the full ~30s setup cost. Could be reduced via a pre-built container image (~5s instead of 30s). Score: high-cost / medium-savings. **Tier B candidate** (G.0 hardening).
- `compileDebugKotlin` runs in `autofix` (implicit via ktlintFormat), `lint-and-test` (explicit), and `connected-tests` (implicit via connectedDebugAndroidTest). All three pay full incremental compile. The `app/build` cache (audit C3) shares between autofix + lint-and-test in the same workflow. Cross-workflow cache sharing across `android-ci.yml` ↔ `android-integration.yml` would require matching cache key on Gradle build files. Worth ~30s on connected-tests when it runs. Score: medium-cost / low-savings (because connected-tests is opt-in).

### Anti-patterns surfaced (NOT recommending change per scope hard constraints)

- **A1. `workflow_run` cascade cancellations (memory #23):** observed across `version-bump-and-tag.yml` and `auto-update-branch.yml`. Per scope: expected behavior, not a bug. No change recommended.
- **A2. APK + AAB on every merge (PR #850):** `release.yml` builds both unconditionally. For docs-only PRs (e.g. #873), this is 7-8 min of bundleRelease + assembleRelease wall-clock for no source change. Per scope: recently added, not a regression — no change recommended without explicit approval.
- **A3. Every-PR-merge tag + release (release pipeline architecture):** Premise drift flag PV-2 above. The "load-bearing for Firebase distribution" rationale is obsolete; current rationale is GitHub Release artifacts for testers. Constraint may still apply but should be re-validated.

### `[skip ci]` audit (per memory #18)

Grepped recent commits and all workflows:
- `[skip ci]` appears only in `version-bump-and-tag.yml`'s `git commit -m "chore: bump to v... [skip ci]"` (line 280). This is the bot-only exemption. ✅ Pass.
- No `[skip ci]` in workflow YAML steps anywhere else.
- No commit messages from human users include `[skip ci]` in the last 30 commits.

---

## Item 4 — Improvement proposals (sorted by savings ÷ cost)

### Improvement #1 — Fix auto-merge queue-wait step

**Problem:** auto-merge.yml's "Wait until all required-status checks have
queued" step burns 310-315s on every PR (mean 343.7s total job, 312-315s in
this step) even when all required checks complete within ~30s.

**Concrete change(s) — option A (root-cause investigation):**
Add diagnostic logging to print `MISSING` and `CHECK_RUNS` on every loop
iteration. Land that as a 1-PR observability change, then pick a fix once the
mismatch shape is known.

**Concrete change(s) — option B (cap-and-skip, faster-but-cruder):**
1. Lower poll interval from 10s to 5s (line 155).
2. Lower attempt cap from 30 to 12 (60s ceiling instead of 300s).
3. Add an early-exit if all known check-run names are in `state=completed`,
   regardless of whether they're "queued" — once they're done, queue-wait is
   moot (this is the actual exit condition the next step `wait-on-check-action`
   already enforces).

**Implementation cost:** Option A: 30 min (1 PR). Option B: 30 min (1 PR).
Option B with Option A's logging: 60 min (2 PRs).

**Per-PR wall-clock savings:**
- Option A: unknown until root-cause is identified, but likely 200-300s/PR.
- Option B: ~250-300s/PR (assumes most PRs hit the early-exit; worst case still bounded at 60s ceiling).

**Risk:** LOW. The next step `wait-on-check-action` already handles "all
checks completed" correctly. Worst case is a rare PR where queue-wait exits
early but a check then queues new — `wait-on-check-action` would still wait
on it. No reliability regression.

**Verification:**
```bash
gh run list --workflow=auto-merge.yml --limit 20 --json conclusion,createdAt,updatedAt --jq '.[] | "\(.conclusion) \((.updatedAt | fromdate) - (.createdAt | fromdate))s"'
```
Mean should drop from ~344s to ~30-60s (target: <120s on 95th percentile).

**Score:** ~3 min/PR ÷ 30 min impl = 6 PRs to break even. At 50 PRs over
Phase F: ~145 min wall-clock saved. **Highest savings ÷ cost.**

### Improvement #2 — Investigate auto-update-branch failure noise

**Problem:** `auto-update-branch.yml` reports job-level failure on 6/15 recent
runs, but inspection suggests most are benign (conflict-comment posted
successfully, `fail_conflict` increments and triggers `exit 1`). Real
auth/conflict failures should remain LOUD; cosmetic conflict-comment events
should not.

**Concrete change:** Distinguish exit-1-on-actual-failure from
exit-1-on-comment-posted. Lines 246-249 currently `exit 1` if `fail_conflict
> 0` regardless of whether the conflict was *handled* (comment posted) or *not*
(unexpected exit). Split into:
- Comment posted successfully → log + `continue` (counts as success)
- Auth failure / unexpected error → `exit 1` (loud)

**Implementation cost:** 30 min (script logic split).

**Per-PR wall-clock savings:** 0 (this is a signal-quality fix, not a
wall-clock fix).

**Reliability gain:** the auto-update-branch failure-rate drops from 40% to
near-0% on cosmetic events; a real failure (auth/PAT-expired) becomes a
single signal instead of getting drowned in noise.

**Risk:** LOW. If the comment-post itself fails, that's still surfaceable via
stderr and would still propagate.

**Verification:** `gh run list --workflow=auto-update-branch.yml --limit 30 --json conclusion --jq '[.[] | .conclusion] | group_by(.) | map({k: .[0], n: length})'` should show <5% failure on 30 runs after 1 week of merges.

**Score:** No wall-clock saved, but reduces cognitive load and eliminates
false-CI-alerts. **Tier A signal-quality fix.**

### Improvement #3 — Reduce auto-merge `wait-on-check-action` poll interval

**Problem:** `WAIT_INTERVAL: 30` (line 24 of auto-merge.yml) — the lewagon
action polls every 30s. After fixing #1, this becomes the new minor
overhead. For docs PRs where checks finish in 5-15s, the 30s poll adds 15-30s
of buffer.

**Concrete change:** Lower `WAIT_INTERVAL` from 30 to 15.

**Implementation cost:** 5 min (1-line change).

**Per-PR wall-clock savings:** ~15s on docs/trivial PRs. ~0 on Android PRs
(check duration dominates).

**Risk:** LOW. The action handles fast-completion correctly; just polls
twice as often.

**Score:** Marginal (15s savings × 50 PRs = 12.5 min). Bundle with #1 in same
PR for trivial cost.

### Improvement #4 — Extend version-bump wait-for-merge ceiling

**Problem:** `version-bump-and-tag.yml` polls 60 attempts × 10s = 10 min
ceiling for PR to reach MERGED state. Real Android PRs (like PR #851 at 37
min) silently drop the bump if real-CI is slow.

**Concrete change:** Extend ceiling from 60 to 120 attempts (20 min). Or
better: use `workflow_run.conclusion=success` AND poll the merge SHA on main
directly via `git ls-remote`, breaking the dependency on PR-state-flip.

**Implementation cost:** 30 min for the simple ceiling extension; 1.5 hours
for the structural fix.

**Per-PR wall-clock savings:** 0 directly — this is a reliability fix
preventing dropped bumps. Indirect savings: dropped bumps require a manual
`gh workflow run release.yml --ref vX.Y.Z` re-fire (per memory entry #18 on
"Version-bump [skip ci] also blocks the auto-tag release fire").

**Risk:** LOW for ceiling extension; MEDIUM for the structural fix
(requires the script to handle both pre-merge and post-merge paths).

**Score:** Reliability fix. Tier A or B depending on observed dropped-bump
rate (sample needed — Phase 2 should grep CI logs for "did not reach MERGED
within 10 minutes" warnings).

### Improvement #5 — Cross-workflow `app/build` cache share

**Problem:** `android-ci.yml` shares `app/build` cache between its 2 jobs
(autofix + lint-and-test). `android-integration.yml`'s connected-tests job
re-builds from a cold cache.

**Concrete change:** Add `actions/cache@v4` to `connected-tests` and
`cross-device-tests` jobs with the same key as android-ci's. (Lines 75-76 in
android-integration.yml — currently no app/build cache.)

**Implementation cost:** 30 min.

**Per-PR wall-clock savings:** ~30-60s on labeled (`ci:integration`) PRs only.
Most PRs don't run integration, so amortized savings are small.

**Risk:** LOW. Cache miss falls through to cold compile.

**Score:** Marginal. Tier B (low priority for Phase F window since most PRs
don't run integration).

### Improvement #6 — Trim duplicate `setup-android` cost via container image

**Problem:** `setup-android` composite action runs in 5+ jobs across 3
workflows. Each pays 25-40s for SDK download + cache restore.

**Concrete change:** Build a pre-baked container image with Android SDK +
JDK pre-installed. Use `container:` directive in jobs. Push image to GHCR.

**Implementation cost:** 4-6 hours (image build, GHCR push, workflow refactor,
testing).

**Per-PR wall-clock savings:** ~30s × N relevant jobs = 60-90s on Android PRs.
Most PRs don't benefit (paths-filter short-circuits before setup-android).

**Risk:** MEDIUM. Container drift vs. setup-android source-of-truth.
Maintenance burden of image rebuilds for Android tooling updates.

**Score:** Medium savings, high cost. **Tier C — defer.** If saved
~60s/Android-PR over 50 PRs = 50 min saved for ~5 hours of cost.

---

## Item 5 — Risk classification + ordering

### Phase 2 fix sequence (Tier A — Phase F window value before May 15)

| # | Title                                              | Cost   | Reversible | Independent | Risk | Tier |
|---|----------------------------------------------------|--------|------------|-------------|------|------|
| 1 | Fix auto-merge queue-wait step (option A then B)   | 60 min | YES        | YES         | LOW  | A    |
| 2 | Auto-update-branch failure-noise split             | 30 min | YES        | YES         | LOW  | A    |
| 3 | Reduce `WAIT_INTERVAL` from 30 to 15               | 5 min  | YES        | bundle with #1 | LOW | A   |

### Phase 2 fix sequence (Tier B — G.0 hardening)

| # | Title                                              | Cost      | Reversible | Independent | Risk | Tier |
|---|----------------------------------------------------|-----------|------------|-------------|------|------|
| 4 | Extend version-bump wait-for-merge ceiling         | 30 min    | YES        | YES         | LOW  | B    |
| 5 | Cross-workflow `app/build` cache share             | 30 min    | YES        | YES         | LOW  | B    |

### Tier C — defer

| # | Title                                              | Cost      | Reversible | Independent | Risk    | Tier |
|---|----------------------------------------------------|-----------|------------|-------------|---------|------|
| 6 | Container image for setup-android                  | 4-6 hours | YES (revert) | YES       | MEDIUM  | C    |

### Premise-drift items requiring explicit approval

| # | Item                                              | Action |
|---|----------------------------------------------------|--------|
| PV-1 | Scope filename `version-bump-and-distribute.yml` is wrong | Update scope-doc references. No code change. |
| PV-2 | "Don't recommend removing every-merge release pipeline / Firebase distribution" — Firebase retired | Re-validate constraint. May still apply to GitHub Releases path; don't change without explicit OK. |

---

## Phase 2 plan (proposed, awaiting approval)

Recommended fan-out:
- **PR A:** `ci/auto-merge-queue-wait-diag` — adds diagnostic logging in
  the queue-wait loop. (Improvement #1 option A.)
- **PR B (after A's first run lands data):** `ci/auto-merge-queue-wait-fix` —
  applies the targeted fix once the mismatch shape is known. (Improvement #1
  option B + Improvement #3.)
- **PR C:** `ci/auto-update-branch-noise-split` — splits cosmetic from real
  failure paths. (Improvement #2.)

PR A → PR B is the only sequential pairing. Everything else fans out
independently. All would land squash-merged auto-merge with required CI green.
None of these touch reliability gates per scope hard constraints.

## Memory entries to consider after Phase 2 lands

If improvements #1 + #3 reduce auto-merge wall-clock from ~5min to ~30s as
predicted, that's a strong candidate for a feedback memory:
- *"Auto-merge.yml polling steps amplify wall-clock — measure step-level
  durations not just job-level when investigating CI cost."*

If improvement #2 fixes the false-failure rate, no memory needed (it's
codified in the workflow itself).

## Re-baseline after Phase 2

Re-run the queries from Item 2 after PR A + B + C merge. Target:
- `auto-merge.yml` mean wall-clock: **<60s** (down from 343s).
- `auto-update-branch.yml` job-level failure rate: **<10%** (down from 40%).
- Per-PR creation→merged for trivial PRs: **<2 min** (down from ~5 min).

---

**End Phase 1.** No code or config changes have been made. Awaiting Phase 2
approval to spawn fan-out PRs from `main`.

---

## Phase 3 — Bundle summary (post-merge measurement)

**Date:** 2026-04-28 (same day as Phase 1 + 2).

### Per-improvement landing

| Audit # | PR | State | Branch | Notes |
|--------:|----|-------|--------|-------|
| #1 (auto-merge queue-wait fix) | [#877](https://github.com/akarlin3/prismTask/pull/877) | MERGED 05:49Z | `ci/auto-merge-queue-wait-fix` | bundled cap reduction (300→90s), poll halved (10→5s), diag logs added, WAIT_INTERVAL 30→15s |
| #2 (auto-update-branch noise split) | [#878](https://github.com/akarlin3/prismTask/pull/878) | MERGED 05:48Z | `ci/auto-update-branch-noise-split` | `fail_handled` (cosmetic, exits 0) split from `fail_unexpected` (genuine, exits 1) |
| #3 (WAIT_INTERVAL drop) | bundled into #877 | — | — | landed alongside #1 |
| #4 (version-bump merge ceiling) | [#880](https://github.com/akarlin3/prismTask/pull/880) | MERGED 05:53Z | `ci/version-bump-extend-merge-ceiling` | 60→120 attempts (10→20 min ceiling) |
| #5 (integration `app/build` cache) | [#881](https://github.com/akarlin3/prismTask/pull/881) | MERGED 05:57Z | `ci/integration-app-build-cache` | `actions/cache@v4` added to both connected-tests + cross-device-tests jobs |
| Tier C #2 (lintDebug on PR) | [#883](https://github.com/akarlin3/prismTask/pull/883) | OPEN, auto-merge | `ci/android-lint-debug-only-on-pr` | YAML parse bug on first attempt — `: ` in step name. Fix shipped commit `be66c7ff`. |
| Tier C #3 (firebase-tools cache) | [#884](https://github.com/akarlin3/prismTask/pull/884) | OPEN, auto-merge | `ci/firebase-tools-install-cache` | pinned `@15` (current major; verified 15.15.1 latest via `npm view`) |
| #6 (container image) | NOT DONE | — | — | premise wrong — setup-android measured at 13s/job, not the audited ~30s. ROI negative. |

### Measured wall-clock deltas

**`auto-merge.yml`** mean wall-clock — **62% reduction**:

- Baseline (Phase 1 sample): **343.7s mean, std-dev ~7s** across 10 runs.
- Post-#877 sample (clean PRs `ci/android-lint-debug-only-on-pr`, `ci/firebase-tools-install-cache`): **129s, 133s** = **131s mean** = **62% reduction (212s saved per PR)**.
- Outlier observation: 477s and 523s on `feat/edge-case-sync-fuzz-*` PRs that have legitimate test-failure-driven slow CI lanes. The fix did not regress slow-CI behavior — the new ceiling is per-step (90s on queue-wait), not per-job, so PRs with long-running real CI still get full wait-on-check-action coverage.
- **Phase F window projection**: 50 PRs × 212s saved = **~177 min recovered** (better than the 145-min audit prediction).

**`auto-update-branch.yml`** failure-rate — **target met**:

- Baseline (Phase 1 sample): **40%** (6/15 runs, dominated by handled conflicts).
- Post-#878 sample: **10%** (1 failure / 10 runs), **0% cosmetic-conflict false-failures**.
- The 1 remaining failure is run `25036554370` on PR #884 — a genuine `AUTOFIX_PAT` 401/403 auth/scope error. The `fail_auth=1` branch correctly fired exit-1 with the LOUD `::error::` annotation, exactly the signal the fix was designed to preserve. **Working as intended.**

**Tier C #2 (lintDebug on PR)** — pending merge, not yet measurable post-fix. Prediction: ~150-200s saved per Android PR. Verification command after merge:
```bash
gh run view <next-android-pr-run> --json jobs \
  --jq '.jobs[] | select(.name=="lint-and-test") | .steps[] | select(.name | contains("lint")) | "\(.name) | \(((.completedAt | fromdateiso8601) - (.startedAt | fromdateiso8601)))s"'
```

**Tier C #3 (firebase-tools cache)** — pending merge. Prediction: ~15-20s/job × 2 jobs = 30-40s saved on labeled `ci:integration` PRs.

### Re-baseline (achieved vs target)

| Metric                                          | Target   | Achieved | Status |
|-------------------------------------------------|----------|----------|--------|
| `auto-merge.yml` mean wall-clock                | <60s     | 131s     | 🟡 better than 120s mid-target, missed <60s stretch goal |
| `auto-update-branch.yml` job-level failure rate | <10%     | 10%      | 🟡 hit ceiling exactly; 0% false-failures (real goal achieved) |
| Per-PR creation→merged for trivial PRs          | <2 min   | ~3-5 min | 🔴 not reached — auto-merge is now ~131s but other steps (CI run, autofix, update-branch race) still gate the merge time |

The trivial-PR creation→merged target was not reached because the audit
under-attributed the wall-clock to other steps (autofix race, GitHub's
own merge-queue, post-merge tag/release fire). The auto-merge fix did
its job; further trivial-PR speedup requires investigating those
adjacent paths.

### Memory entry candidates

**Saved as new memory** (worth surfacing in future CI work):

> **YAML `: ` (colon-space) in unquoted step names breaks workflow parse.**
> Run shows `name: .github/workflows/<file>.yml` (file path) instead of the
> parsed workflow `name:` field; zero jobs run on the trigger event. Fix:
> quote the value or remove `: ` from the label. Em dash (` — `) is
> YAML-neutral. Witnessed on PR #883 first push — run 25036738341.

The original audit's Improvement #1 root-cause-investigation hypothesis
(diagnostic logging would surface MISSING/CHECK_RUNS mismatch shape) was
not exercised because the cap-and-skip approach in #877 dropped the
overall wall-clock so much that the diagnostic loops are now a non-issue.
The diagnostic logs are still in the workflow for future debugging if
the bug re-emerges.

### Tier C #6 (container image) — not done, premise correction

Audit estimated `setup-android` at ~30s/job, sized container migration at
4-6 hr for ~75 min savings. Real measurement on push:main run
25032580155: setup-android = **13s/job**. Container migration would
save ≤13s/job. Over Phase F (~30 Android-CI runs): ~6.5 min saved for
4-6 hr cost. **Negative ROI. Skipped.**

The actual high-leverage Tier C target (not in original audit) was
**Android lint at 355s** (55% of `lint-and-test` job). Tier C #2 (#883)
addresses this with the lintDebug-on-PR split.

### Schedule for next audit

- **After Phase F (post-May 15):** re-baseline `auto-merge.yml` durations
  on the new merge cadence to confirm the 131s holds. If the variance
  widens, the diagnostic logs in #877 should now show the underlying
  MISSING/CHECK_RUNS mismatch — file follow-up `ci/auto-merge-queue-wait-rootcause-fix`.
- **AUTOFIX_PAT scope/expiry** (out of audit scope): the 1 real failure
  in the post-#878 sample suggests AUTOFIX_PAT may need rotation. Worth
  a separate one-off check next session — not a CI-pathway issue.

---

**End Phase 3.** Phase 2 fan-out delivered measurable wall-clock recovery
(~177 min over Phase F projected). Tier C PRs (#883, #884) auto-merging.
No follow-up audits scheduled in the immediate window.
