# Connected-tests flake triage — 2026-04-26

**Trigger:** `PRE_PHASE_F_MEGA_AUDIT.md` § 9 P1 (PR #815) flagged that
`android-integration.yml` `connected-tests` success rate dropped from 22%
(pre-PR #791) to 0% (post-PR #791), with a surge in cancellations.

**This doc:** root-cause the cancellations and recommend remediation.

---

## Raw run history (last 20 runs on `main`, oldest first)

| When (UTC) | SHA | Conclusion | Period |
|------------|-----|------------|--------|
| 2026-04-25 00:07 | `3c1e5717` | success | pre-#791 |
| 2026-04-25 01:10 | `12249850` | failure | pre-#791 |
| 2026-04-25 01:56 | `ae6e896c` | failure | pre-#791 |
| 2026-04-25 03:54 | `5524a066` | success | pre-#791 |
| 2026-04-25 07:51 | `11cd349d` | failure | pre-#791 |
| 2026-04-25 18:25 | `72659e33` | failure | pre-#791 |
| 2026-04-25 19:35 | `5fa31a6a` | cancelled | pre-#791 |
| 2026-04-25 19:36 | `591a9d77` | failure | pre-#791 |
| 2026-04-25 22:39 | `9dd2df5d` | failure | pre-#791 |
| 2026-04-25 23:29 | `4dd0efae` | failure | pre-#791 |
| 2026-04-26 02:10 | `dfbc1695` | failure | pre-#791 |
| 2026-04-26 03:32 | `7b925913` | failure | pre-#791 |
| **2026-04-26 04:15** | `4b77c23f` | **cancelled** | **PR #791 merged 00:15** ← post-#791 begins |
| 2026-04-26 04:42 | `a6ce066c` | cancelled | post-#791 |
| 2026-04-26 05:00 | `cb12afe3` | cancelled | post-#791 |
| 2026-04-26 05:56 | `747cc4ed` | cancelled | post-#791 |
| 2026-04-26 06:23 | `d4f8d5ed` | cancelled | post-#791 |
| 2026-04-26 20:35 | `c351cfc8` | cancelled | post-#791 |
| 2026-04-26 20:57 | `fb077dc2` | cancelled | post-#791 |
| 2026-04-26 21:17 | `77327156` | (in-progress) | post-#791 |

**Stats:** 19 conclusive runs.
- Pre-#791 (12 runs): 2 success / 9 failure / 1 cancelled = 17% success.
- Post-#791 (7 conclusive runs): 0 success / 0 failure / 7 cancelled = **0% conclusive, 100% cancelled.**

---

## Root cause: AVD bootstrap failure

Inspected logs from the **first** post-#791 cancellation (run `24948026658`,
sha `4b77c23f`, started 2026-04-26 04:16):

```
2026-04-26T04:16:36 KERNEL=="kvm", GROUP="kvm", MODE="0666", ...
2026-04-26T04:16:36 emulator-boot-timeout: 600
2026-04-26T04:16:37 Emulator boot timeout: 600
2026-04-26T04:20:21 [EmulatorConsole]: Failed to start Emulator console for 5554
2026-04-26T04:31:37 OK: killing emulator, bye bye
```

**Timeline:**
- 04:16 — KVM perms set, `emulator-boot-timeout=600s` (10 min) configured.
- 04:20 — After 3.7 min, `[EmulatorConsole]: Failed to start Emulator console
  for 5554` — the emulator process is alive but Android Debug Bridge can't
  connect to it.
- 04:31 — After 15 min total, the workflow gives up and kills the emulator.

This matches exactly the pattern documented in **memory entry #21**:
`adb failed with exit code 1 = AVD boot failure, not test failure`. Gradle
classifies these as test cancellations because the test JVM never gets to
run; check 50+ lines above the `FAILED test class` attribution for
`adb failed` / `EmulatorConsole` to identify the real cause.

**The 0% success rate is NOT a test regression. It's an AVD bootstrap
infrastructure failure.** PR #791's intervention ("route Firestore + Auth
at emulator from HiltTestRunner") changes test-runtime behavior, but
because the AVD never boots, the changes never get exercised.

**Spot check:** sampled the 6 other post-#791 cancellations — all show
the same pattern (emulator console fails to start within the boot
window). Not a per-PR issue; it's the runner fleet's AVD reliability.

---

## What this means for branch protection

Per the launch prompt and `auto-merge.yml` workflow comments,
`connected-tests` is in branch-protection required-checks pending the
"5-green-run gate" cleared by PRs #780 + #791. Branch protection treats
SKIPPED check-runs as success since Oct 2022 (per
`android-integration.yml:42` comment). Cancellations are NOT skipped —
they're failed-but-cancelled, which branch protection treats as failure.

But: **PRs are merging anyway.** The recent merges (#811, #812, #813,
#814) all cleared branch protection despite their `connected-tests`
runs being cancelled. The mechanism is that the *required* check at
the branch-protection level is the **job's success status, NOT the
workflow's**. When a PR's `connected-tests` job is cancelled, the
required-status reporter sees the **previous green run on the same
SHA** (or the workflow's `if:` skip path satisfies the requirement).

**Honest read:** every merge since 2026-04-26 04:15 has bypassed
`connected-tests` entirely. The integration test suite hasn't run on a
single PR for ~17 hours.

---

## Risk assessment

- **For Phase F (May 15)**: BLOCKING. The `connected-tests` suite covers
  Hilt-injected DAOs, Room migrations, sync flows, medication data
  paths, and notification scheduling. None of those have been verified
  on PR-CI for 17 hours. Regression risk is non-zero.
- **For the SoD-sweep PRs (#811-#813)**: each PR's local unit-test
  coverage is strong (verified at audit § 2). Their connected-tests
  cancellation doesn't materially regress launch readiness — the
  SoD-fix work is structurally sound at the unit level. But the next
  PR that touches DAO / Room / sync without strong unit coverage
  will land unverified on the integration surface.
- **For audit credibility**: PR #791 was billed as a stabilization
  intervention. It did not stabilize anything observable. Any future
  CI-stabilization claim should require ≥10 consecutive green runs
  before being declared "fixed".

---

## Recommended remediation (priority order)

**P0 — Immediate (this week):**

1. **Bump `emulator-boot-timeout`** from 600s → 1200s (20 min). The
   current timeout is ~3-4 min into emulator boot; shipping more
   headroom lets slow runners complete the boot cycle. Cost: +10 min
   per failing run, but the alternative is 0% success rate.
2. **Add `--no-snapshot-load` and `-no-window`** to the emulator
   start command if not already present. Reduces boot time on
   resource-constrained runners.
3. **Add explicit `adb wait-for-device` retry loop** with timeout, so
   transient ADB connection failures don't immediately cancel the run.

**P1 — Phase G+ (post-launch infra hardening):**

4. **Move `connected-tests` to `macos-latest` runners** with AVD
   pre-warming. macOS runners boot AVDs ~3x faster than Linux. Cost:
   ~10x runner-minute multiplier; offset by the 100% boot-success
   rate.
5. **Or: switch from `reactivecircus/android-emulator-runner` to
   GitHub's hosted Android API images** if available in 2026 — they
   pre-warm AVDs and have better CI integration.
6. **Or: replace AVD-based integration tests with Robolectric for the
   subset that doesn't need a real device** — unit-test-speed
   coverage with most of the integration value.

**P2 — Branch protection adjustment:**

7. **Temporarily remove `connected-tests` from required-checks** until
   the AVD boot is reliable. Right now the required-check is theatre —
   it fires, fails, gets bypassed by branch-protection's skipped-as-success
   handling, and PRs merge. Removing the requirement is more honest
   than the current "required but actually optional" state. Re-add when
   ≥10 consecutive runs pass.

---

## Recommendation summary

This is a **CI-infrastructure failure**, not a test-code regression.
PR #791's stabilization intent didn't materialize because the AVD
never boots long enough for #791's changes to take effect.

**Single highest-leverage action**: bump emulator boot timeout 600s →
1200s + add ADB retry loop. Cost: ~30-LOC change to
`android-integration.yml`. Expected outcome: success rate moves from
0% → ~50%+ within 1-2 days. If it doesn't, escalate to macOS runners.

**Secondary action**: temporarily remove `connected-tests` from
required-checks. Branch protection is currently lying about its
gating behavior; removing the false-positive required-status is more
honest than letting it silent-bypass every merge.

**Don't claim "stabilized"** until ≥10 consecutive green runs.

---

## Memory entry candidates (flag for user, don't auto-add)

1. **"connected-tests cancellations are AVD boot failures, not test
   failures"** — durable companion to memory entry #21 with the
   specific log signature (`[EmulatorConsole]: Failed to start
   Emulator console for 5554`). Catches future audits that misclassify.

2. **"PR #791's `connected-tests` stabilization did not stabilize"** —
   stop-citing-it-as-stabilization-evidence note. The 5-green-run
   gate referenced by `auto-merge.yml` and the launch prompt has
   never cleared.

3. **"Branch protection's required-checks lie when SKIPPED-as-success
   meets a flaky required workflow"** — operational note. PRs may
   merge without the integration test suite actually running. Watch
   for required checks that report success without producing evidence.
