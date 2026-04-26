# Emulator Boot Timeout Audit (600s → 1200s)

**Trigger:** Carried-forward Phase F blocker from PR #823's pre-Phase F
mega-audit closeout. PR #817 triaged the connected-tests AVD boot
failures (`adb failed with exit code 1` + `[EmulatorConsole]: Failed to
start Emulator console for 5554`) and concluded the
`emulator-boot-timeout` default of 600s is insufficient.

This audit operationalizes that recommendation.

---

## Phase 1 — Audit

### 1. Premise verification

**Premise correct, with a clarification.** The audit prompt assumed the
config exists and is set to 600s. Reality: **no `emulator-boot-timeout`
key exists in either workflow invocation** — both rely on the
`reactivecircus/android-emulator-runner@v2` action's documented default
of 600 seconds.

Both invocations live in `.github/workflows/android-integration.yml`:

- **`integration-tests` job** (line 117-122): `Run instrumented tests against AVD + Firebase emulator`. Action params: `api-level: 34`, `target: google_apis`, `arch: x86_64`, `profile: pixel_6`. No `emulator-boot-timeout`.
- **`cross-device-tests` job** (line 265-270): `Run medication cross-device tests with one retry`. Same action param shape. Same omission.

Effective timeout for both: **600s (action default).**

### 2. Drive-by-fix check

`git log -p -S 'emulator-boot-timeout' --since='2026-04-25' --all` returned
**only PR #823's Phase 3 closeout doc** (which mentions the term in the
post-merge summary text, not in any workflow file). No PR has shipped a
workflow change touching this config.

### 3. PR #817 verification

PR #817 (`d41555e6`) shipped `docs/audits/CONNECTED_TESTS_FLAKE_TRIAGE_2026-04-26.md`
(+194 LOC, doc-only). It identified the AVD boot failure pattern,
classified the connected-tests cancellation surge as bootstrap failure
(not test failure), and **recommended** the timeout bump — but did NOT
ship the workflow change. The fix was deliberately scoped out as a
follow-up workflow PR (this one).

### 4. Recommendation

**PROCEED.** Add `emulator-boot-timeout: 1200` to both action invocations.
Single workflow file edit, no test coverage required (workflow config has
no unit tests; the proof is the next AVD bootstrap not failing).

---

## Phase 2 — Workflow PR

Branch: `ci/emulator-boot-timeout-1200s`.

Edits:
1. `.github/workflows/android-integration.yml` line ~123 — add `emulator-boot-timeout: 1200` to the `integration-tests` job's action `with:` block.
2. `.github/workflows/android-integration.yml` line ~271 — same for the `cross-device-tests` job.
3. `CHANGELOG.md` — `## Unreleased` → `### Changed` entry pointing at this audit doc.

No `[skip ci]` (memory entry #18). Trailing newline on CHANGELOG.

---

## Phase 3 — Post-merge note

To be appended after PR merges with: PR number, SHA, confirmation the
bump landed in main. Memory entries #21 + #25 already cover the
detection-and-debug pattern; this PR makes that pattern's recipe
operational.
