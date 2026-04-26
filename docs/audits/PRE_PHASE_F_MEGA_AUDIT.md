# Pre-Phase F Mega Audit

**Trigger:** Phase F kickoff is 2026-05-15 (19 days from this audit's start).
This is the last large derisking + hygiene + planning sweep before launch
prep dominates the schedule.

**Process:** audit-first, single doc, three checkpoint stops. Phase 1 below
covers all 9 items in three batches. Phase 2 ships fan-out PRs only for
items the user explicitly approves. Mega-PR is forbidden.

**Scope:** 9 items across 3 batches.
- **Batch 1 — derisking:** backlog audit, per-feature readiness, privacy drift.
- **Batch 2 — hygiene:** stale TODOs, dead code, doc drift.
- **Batch 3 — planning:** CHANGELOG + release notes, Phase G scope, test infra.

**Repo state at start of audit:** branch `docs/audits/pre-phase-f-mega` cut
from `main` @ `1c15ea2b` (post-LocalDateFlow sweep close-out, versionCode
706).

---

## Phase 1 — Audit (no fix code)

Each item below uses this framework:
1. **Premise verification.** Does the item describe real codebase reality?
2. **Findings.** What did the sweep surface? Citations.
3. **Risk classification.** RED / YELLOW / GREEN / DEFERRED.
4. **Recommendation.** PROCEED, STOP-no-work-needed, or DEFER-to-G.0+.

---

# Batch 1 — Pre-Phase F Derisking

## 1. Backlog audit

### Premise verification

**Premise correct.** Multiple audit docs from prior batches contain
"Out of scope" / "Follow-up backlog" / "Deferred" sections, and the
interval since those audits saw mostly SoD-boundary + CI-pipeline work
(`#798`, `#803`-`#809`, `#810`-`#814`) rather than backlog burn-down. Items
parked in older audits are still likely active.

### Findings

Single sweep across `docs/audits/*.md`, `CLAUDE.md`, repo-side memory,
and recent merged-PR descriptions. **Findings are stratified by
confidence**: items in audits ≤7 days old are HIGH-confidence (still
current); items in audits >2 weeks old are MEDIUM-confidence (may have
intervening fixes I didn't reverify). Verification cost is per-item.

#### High-confidence — fresh-audit deferrals (≤7 days old)

| Source | Item | Owner |
|--------|------|-------|
| `MEDICATION_SOD_BOUNDARY_AUDIT.md` § 4 (PR #798 audit) | Web-side equivalent for `MedicationStatusUseCase` (no current consumer; web `useLogicalToday` covers four other surfaces but not this leaf path) | DEFER-G.0 |
| `UTIL_DAYBOUNDARY_SWEEP_AUDIT.md` § "Follow-up backlog" | `WidgetUpdateManager` SoD-boundary alarm scheduling — widgets refresh on ~15 min cadence + on data mutations, but NOT at user's SoD; widgets can show "yesterday's content" for up to ~15 min past SoD | DEFER-G.0 (separate audit) |
| `UTIL_DAYBOUNDARY_SWEEP_AUDIT.md` § "Follow-up backlog" | `DailyEssentialsUseCase`'s `combine` source count (now 11) is near Kotlin's `combine` arity overloads. Adding a 12th source requires `combine(vararg)` or restructuring | DEFER (informational) |
| `MEDICATION_SOD_BOUNDARY_AUDIT.md` § Phase 4 closeout | Migrating remaining `util.DayBoundary` callers (Habit/Tasklist/Today) to canonical `core.time.DayBoundary` + `TimeProvider` — partially done by sweep PRs #811-#813, but other repos still on snapshot | DEFER-G.0 |

These four items are all DEFER-able past Phase F. None block launch.

#### Medium-confidence — older-audit findings that may still apply

These items came from audit docs 2-4 weeks old. The agent's sweep
surfaced them but recent merges (since 2026-04-22) have NOT touched the
relevant code paths, so the findings are likely still real. **Recommend
a verification pass per item before any fix work.**

| Source audit | Item | If still real |
|--------------|------|---------------|
| `DATA_INTEGRITY_AUDIT.md` (older) | `resetAppData()` clears habits/tasks but **not** `sync_metadata` → dangling pending actions; sign-out path same → cross-account sync contamination | RED if user can trigger resetAppData on a populated account |
| `DATA_INTEGRITY_AUDIT.md` (older) | Calendar sync **pull** is a stub — `findByState()` defined but never called; etag never used for conflict detection. (Backend endpoint exists at `CalendarBackendApi.kt:42` `/calendar/sync/pull` — may be wired now or still stub on Android side) | YELLOW (one-way sync ships acceptably for v2.0) |
| `CRASH_STABILITY_AUDIT.md` (older) | `TaskRepository.deleteTask()` / `permanentlyDeleteTask()` don't cancel pending reminder alarms → stale alarms fire for deleted tasks | YELLOW (visible to user — null-titled notification) |
| `CRASH_STABILITY_AUDIT.md` (older) | Multiple BroadcastReceivers use `GlobalScope.launch` without `goAsync()` (5 HIGH-severity async/threading issues) | YELLOW (rare crash path) |
| `DATA_INTEGRITY_AUDIT.md` (older) | 8 unbounded tables (`usage_logs` 50k+ rows, `task_completions` orphans, etc.) — no purge worker | YELLOW (DB bloat over time) |
| `DEAD_CODE_AUDIT.md` | 4 unused repositories + `ParalysisBreaker` (defined but never invoked); `NdPreferences` ADHD/calm flags written but never read | LOW (cleanup, not blocker) |
| `DEAD_CODE_AUDIT.md` | `/mobile/` Expo React Native scaffolding from abandoned Phase 2 — orphaned, not documented | LOW (cleanup) |
| `PII_EGRESS_AUDIT.md` (PR #788 era) | Med-name → Anthropic Haiku disclosure; **resolved by PR #790** Option C (verified in Item 3 below) | ✅ DONE |

**Verification cost** for the medium-confidence batch: ~30 min per item to
confirm-or-disconfirm. The 8 items above would cost ~4h to fully
re-verify. **Recommendation: don't blindly inherit. Pick the top 1-3 by
launch impact and verify those; defer the rest with explicit "verify
before fix" labels.**

#### Cross-source patterns (high signal — surfaced in 2+ audits)

1. **Sync metadata cleanup on sign-out / resetAppData** — `DATA_INTEGRITY` + `CRASH_STABILITY`. Cross-account contamination risk if both items are real. **Top verification candidate.**
2. **Reminder alarm cancellation on task delete** — multiple `CRASH_STABILITY` references. User-visible. **Second verification candidate.**
3. **LocalDateFlow adoption backlog** — `MEDICATION_SOD_BOUNDARY` + `UTIL_DAYBOUNDARY_SWEEP`. Architectural follow-up, no user impact. Defer cleanly.

### Risk classification

- **YELLOW** — should verify the top 2-3 medium-confidence items before
  Phase F.
- The high-confidence fresh items are all DEFER-G.0+ (correctly parked by
  recent audits).

### Recommendation

**PROCEED — but as a verification PR, not a fix PR.** The right output here
is a small audit doc that re-checks `resetAppData()` + reminder alarm
cancellation on current `main` (e.g., 30 min each), and flips them to RED
or GREEN definitively. Don't fan out fixes for medium-confidence items
without verification — risk is shipping a "fix" for a bug that no longer
exists, OR missing one that's genuinely there.

Suggested PR shape:
- Branch: `audit/pre-f-backlog-verify`
- New file: `docs/audits/PRE_PHASE_F_BACKLOG_VERIFY.md`
- Per item: read the named files on current `main`, quote the relevant
  lines, classify GREEN-fixed-since / YELLOW-still-real / RED-still-real-and-Phase-F-blocker.
- Estimate: ~3-4 items verified, ~250 LOC of doc.

---

## 2. Per-feature Phase F readiness scorecard

### Premise verification

**Premise correct.** All 8 surfaces have substantial recent work, varying
test depth, and the SoD-boundary sweep (PRs #811-#813 just landed)
touched several of them indirectly.

### Findings

| # | Surface | Tests | TODOs | Last-2wk PRs | Risk |
|---|---------|-------|-------|--------------|------|
| 1 | Medication tracking | 6 unit + 2 sync-integration | 0 active | #798, #811-#813 (SoD sweep — addressed, not regression) | **GREEN** |
| 2 | Habits + streaks | 9 unit + 3 notification | 0 active in core | #813 (downstream) | **GREEN** |
| 3 | Tasks + AI quick-add | 8 unit | 0 active | none in 2 weeks | **GREEN** |
| 4 | Cross-device sync | 2 unit + 8 scenario tests + 4 misc | 3 in `SyncService.kt` (lines 56, 1131, 1318 — refactoring notes, not bugs) | #782, #753, #743 (3+ weeks) | **YELLOW** — schema churn + delete-wins regression history |
| 5 | Batch operations | 2 unit (medication-specific is new) | 0 | #772 (10 days), #761 (~2 weeks) | **YELLOW** — minimal coverage on new medication batch surface |
| 6 | Pomodoro+ focus | 6 unit | 0 | #664 (~2 weeks; AI coaching, all default-on, 2s timeout) | **GREEN** |
| 7 | Weekly review | 4 unit + 2 worker | 1 (`WeeklyReviewViewModel.kt:221` — TaskEntity PK ID-mapping mid-refactor) | #665 (~2 weeks; auto-gen WorkManager Sun 8pm) | **YELLOW** — unresolved TODO + new auto-gen still in shake-out |
| 8 | Settings + AI gate | 2 unit (`UserPreferencesDataStoreTest`, `SettingsScreenTest`) | 0 | #790 (11 days; the Phase F unblocker) | **YELLOW** — minimal coverage for the critical Phase F privacy gate |

### Risk classification

- **3 GREEN:** Medication, Habits, Pomodoro+ — fully derisked.
- **3 YELLOW:** Sync, Batch ops, Weekly review, Settings/AI gate — should
  add targeted regression tests before Phase F, not necessarily fixes.
  Settings/AI gate is the sharpest concern (only 2 test files for the
  privacy invariant that gates the entire launch).

### Recommendation

**PROCEED with targeted YELLOW-tier test additions, not fixes.** The
right work is:

1. **Settings/AI gate** — add an integration test that exercises the
   `AiFeatureGateInterceptor` against a mocked `OkHttp` chain, asserting
   the synthetic 451 response when the toggle is off and that the request
   never reaches the network layer. Highest priority.
2. **Batch ops (medication)** — add 4 unit tests covering apply / undo /
   partial-failure / idempotent re-apply for the new medication
   STATE_CHANGE / SKIP / COMPLETE / DELETE mutations (per
   `BULK_MEDICATION_MARK_AUDIT.md` § Vitest follow-up).
3. **Weekly review** — resolve the `WeeklyReviewViewModel.kt:221` TODO
   (TaskEntity PK ID mapping for backend submission) — or document
   why it's a non-blocker if the backend now accepts Long IDs.
4. **Sync** — no specific action (the 3 SyncService TODOs are
   refactoring notes, not bugs). Schema churn is concerning but the
   existing 14-test integration coverage is the right gate.

Suggested PR shape:
- Branch: `audit/pre-f-yellow-tests` OR three smaller fan-out branches
  (one per surface)
- ~3 test files added, ~150-250 LOC of test code
- No production-code changes

### Notable observation

The **Settings/AI gate** is uniquely under-tested for its launch criticality.
PR #790's commit message explicitly anchors it to the 2026-05-15 launch.
2 test files is below the project's typical "critical-path" test density.
Flag as the **single most-bang-per-buck Phase F readiness investment.**

---

## 3. Privacy + Data Safety form drift audit

### Premise verification

**Premise correct.** PR #790 (privacy) and #774 (account deletion) are
both on `main`. Their docs (`docs/privacy/index.md`,
`docs/store-listing/compliance/data-safety-form.md`) exist. The audit
question is whether code reality matches doc claims.

### Findings — all four verifications: **NO DELTA**

#### Verification 1: AI feature gate actually gates

- **Doc claim** (`privacy/index.md:51`): "You can turn off all AI processing
  in **Settings → AI Features → Use Claude AI for advanced features**;
  when disabled, no PrismTask data is sent to Anthropic."
- **Code reality**:
  - `AiFeatureGateInterceptor` wired as the first OkHttp interceptor
    (`NetworkModule.kt:71`).
  - Short-circuits with synthetic HTTP 451 when toggle off
    (`AiFeatureGateInterceptor.kt:41-62`).
  - Toggle persisted in `UserPreferencesDataStore` (`KEY_AI_FEATURES_ENABLED`,
    lines 228-229).
  - UI in `AiFeaturesScreen.kt:64`.
- **DELTA**: NONE. ✅

#### Verification 2: No new third-party SDK since PR #790

- `git log -p --since='2026-04-25' app/build.gradle.kts build.gradle.kts`
  shows only `versionCode`/`versionName` bumps post-#790. No dependency
  additions or modifications.
- **DELTA**: NONE. ✅

#### Verification 3: Medication-name disclosure language is accurate

- **Doc claim** (`privacy/index.md:71`): "for NLP batch commands — your
  medication names (id + name only; not dosage, frequency, or prescriber)"
- **Code reality**:
  - `BatchMedicationContext` (`ApiModels.kt:423-426`) contains only `id`
    and `name`. No dosage / frequency / prescriber.
  - `ClaudeParserService.kt:37` (the import/paste path) sends raw text
    only — no medication context.
  - Med names are transmitted only via `/ai/batch/parse` for NLP batch
    commands, which is the disclosed surface.
- **DELTA**: NONE. ✅

#### Verification 4: Account deletion flow (PR #774) documented correctly

- **Doc claim** (`privacy/index.md:82-84` + `data-safety-form.md:22`):
  immediate sign-out + on-device wipe + 30-day reversible grace + permanent
  Postgres + Firebase Auth deletion via Admin SDK after expiry.
- **Code reality**:
  - UI: `DeleteAccountSection.kt:81` ("Danger Zone" → typed-confirmation
    dialog).
  - Service: `AccountDeletionService.kt:86-108` (`requestAccountDeletion()`
    marks Firestore deletion-pending, POSTs to `/api/v1/auth/me/deletion`,
    calls `cleanLocalState()`).
  - Grace: `GRACE_DAYS = 30` (line 287); `restoreAccount()` lines
    148-173.
  - Permanent purge: `executePermanentPurge()` lines 188-203
    (`api.purgeAccount()` → backend Postgres CASCADE + Firebase Auth Admin).
- **DELTA**: NONE. ✅

### Risk classification

**GREEN.** Code and docs match across all four verifications. Premise that
"there might be drift" was correct to check, but no drift exists.

### Recommendation

**STOP — no work needed.** Document this STOP rationale durably so future
audits don't re-do the work.

**Memory entry candidate** (flag, don't auto-add):

> "Privacy / Data Safety form audit (Apr 26): no drift between
> `docs/privacy/index.md`, `docs/store-listing/compliance/data-safety-form.md`,
> and code. AI feature gate (PR #790) wired correctly via OkHttp
> interceptor with HTTP 451 short-circuit; medication-name disclosure
> accurate (id+name only via `/ai/batch/parse`); account deletion (PR #774)
> 30-day grace + Postgres+Firebase Auth purge as documented. No new
> third-party SDKs added post-#790. Re-verify only if a new external
> integration ships."

---

# Phase 1 Batch 1 deliverable — STOP for checkpoint review

**Per the launch prompt's checkpoint protocol, stopping here.** Items 4-9
will be filled in only after batch 1 sign-off.

### Batch 1 summary

| # | Item | Verdict | Action |
|---|------|---------|--------|
| 1 | Backlog audit | **PROCEED — verification PR**, not fix PR. ~3-4 medium-confidence items from older audits need re-checking. | Approve `audit/pre-f-backlog-verify` doc-only PR |
| 2 | Per-feature readiness | **PROCEED — targeted YELLOW-tier test additions**. Settings/AI gate is sharpest concern (under-tested for its Phase F criticality). | Approve 1-3 test PRs (recommend Settings/AI gate first) |
| 3 | Privacy drift | **STOP — no drift, no work.** ✅ All 4 verifications pass. | None — record STOP rationale in memory |

### Wrong premises caught

- **Item 3** premise: "PR #790 + #774 docs match current code" was the
  question. Answer: yes, fully match. The premise was correct in the
  sense it was worth checking, but the predicted "STOP if no drift"
  outcome held. This is a STOP, not a wrong premise.

### Unexpected scope

- **Item 1** larger than expected: 47 items surfaced across 8 audit docs.
  Most were stratified down to ~12 actionable items, then to ~4 for
  PROCEED-as-verification. Audit fan-out vs. fix fan-out is the right
  scoping call for this item.
- **Item 2** sharpest finding (Settings/AI gate test density) was not
  predicted by the launch prompt but is the highest-value Phase F
  derisking action surfaced in this batch.

### Continue / course-correct?

Recommended: **continue to batch 2 (items 4-6: TODOs, dead code, doc
drift)**. Batch 1 surfaced no signal that warrants restructuring the
mega-audit's plan.

Awaiting your signal to proceed.
