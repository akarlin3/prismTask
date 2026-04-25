# Phase D Bundle — Pre-Implementation Audit

**Date:** 2026-04-25
**Bundle owner:** akarlin3
**Pattern:** Audit-first, fan-out PRs (mirrors PRs #775–#779)
**Working-tree state at audit time:** local `main` is **8 commits behind `origin/main`** at `f248bdd9`. Local changes: `M app/build.gradle.kts`, `?? scripts/build-release-aab.sh`. **Implementation work in Phase 2 must `git pull --rebase origin main` first** to pick up PR #774, PR #776, PR #777 et al — many citations below reference origin/main rather than the local working tree.

**One global correction up front:** CLAUDE.md states `CURRENT_DB_VERSION = 57` (and that v54→v55 / v55→v56 / v56→v57 are the most recent migrations). This is **stale**. On origin/main: `CURRENT_DB_VERSION = 63` (`Migrations.kt:1843`, `PrismTaskDatabase.kt:129`), `versionName = "1.6.0"` (`app/build.gradle.kts:26`). `MIGRATION_62_63` shipped the `medication_marks` table referenced in Item 3. CLAUDE.md should be refreshed in a separate housekeeping PR.

---

## Item 1 — Promote `cross-device-tests` to required-status check

### 1.1 Premise verification

Job `cross-device-tests` exists at `.github/workflows/android-integration.yml:179` (origin/main), with:
- `if:` (lines 180-182): `github.event_name != 'pull_request' || contains(github.event.pull_request.labels.*.name, 'ci:integration')`
- No `needs:`; runs after the existing `connected-tests` job in parallel.
- The job-level name surfaced to GitHub's check-runs API is exactly `cross-device-tests` (no display-name override).

Git history:
- PR #773 merge `11cd349d` ("feat(meds): migration instrumentation safety net (Phase B)") added the job (~+103 lines).
- PR #776 merge `591a9d77` ("fix(ci): cross-device-tests shell-escape — replace \\-continuation chain with function") replaced the line-continuation chain with a function (`run_cross_device_tests`) called twice as `run_cross_device_tests || run_cross_device_tests --rerun-tasks` (line 254 on origin/main).

Branch protection — `scripts/setup-branch-protection.sh:35-40`:
```bash
REQUIRED_CHECKS=(
  "lint-and-test"       # Android CI
  "connected-tests"     # Android Integration CI — matches PR label auto-apply
  "test"                # Backend CI
  "web-lint-and-test"   # Web CI
)
```
Live live config (`gh api repos/akarlin3/PrismTask/branches/main/protection`) returns the same four contexts. **`cross-device-tests` is absent in both the script and the live ruleset.**

### 1.2 Current state — recent run health

20-run sample of `Android Integration CI`. Filtering to `branch=main` since PR #773 merged at 2026-04-25T07:51Z gives 4 runs:

| run id | conclusion | `cross-device-tests` | `connected-tests` |
|---|---|---|---|
| 24938962202 | failure | **failure** | success |
| 24938943423 | cancelled | (cancelled) | (cancelled) |
| 24937612065 | failure | **failure** | success |
| 24926119046 | failure | **failure** | failure |

**`cross-device-tests` is failing 3/3 (100%) of completed main runs since it landed.** Logs from run 24938962202 confirm both retry attempts run, but the failure is the AVD never coming up (`The process '/usr/local/lib/android/sdk/platform-tools/adb' failed with exit code 1` repeated three times before `##[error]The process '/usr/bin/sh' failed with exit code 2`). The shell-level retry-once is wired correctly; **it cannot recover from emulator-runner setup crashes** because `android-emulator-runner@v2` runs the script only once per invocation — both attempts share the same emulator instance.

### 1.3 Gap

The mechanical change is trivial: insert `"cross-device-tests"` into the `REQUIRED_CHECKS` array in `scripts/setup-branch-protection.sh:35-40` and re-run the script. **But the gap is not mechanical — it's stability.** Promoting a job that fails 100% of the time on main would block every PR merge.

### 1.4 Wrong-premise check

🛑 **STOP — WRONG PREMISE.**

The premise that "the job has been running cleanly with the retry-once installed by PR #776" is incorrect. Three signals:

1. **100% failure rate on main** since the job landed (3/3 runs).
2. **Retry-once is firing but cannot help** — the failure is in emulator-runner action setup, before the wrapped script even gets a chance to run a second time.
3. `capture-failure-log.needs: [connected-tests]` (`.github/workflows/android-integration.yml:276`) does **not** include `cross-device-tests`, so cross-device failures aren't being archived to the `ci-logs` branch (so the failure pattern is invisible until you query `gh run` directly).

### 1.5 Proposed PR shape — STOP-driven

**No promotion PR right now.** Required prerequisite: a stabilization PR that fixes the AVD-boot pattern (likely needs `disable-animations: false` removal, an `emulator-boot-timeout` bump, a different api-level/target combination, or `force-avd-creation: false`). Single-file diff in `.github/workflows/android-integration.yml`, ~5 lines. Verification gate: **5 consecutive green main runs of `cross-device-tests`** before any promotion PR is opened. CHANGELOG (`## Unreleased` → `### Fixed`): "ci(android-integration): stabilize `cross-device-tests` AVD boot (was failing 3/3 main runs since #773)."

The follow-up promotion PR — once stable — edits `scripts/setup-branch-protection.sh:35-40` to add `"cross-device-tests"`, runs the script, and updates `capture-failure-log.needs` to include the new job. ~6-line diff. CHANGELOG (`## Unreleased` → `### Changed`): "ci: require `cross-device-tests` for merge to main."

### 1.6 Risk + dependencies

- `setup-branch-protection.sh:50` uses `strict: true`. Compounding `strict` with a flaky required check forces rebase/retry queues.
- `capture-failure-log` job's `needs:` array must be updated in the promotion PR or cross-device failures won't be archived.
- Working copy is 8 commits behind origin/main — must `git pull --rebase` before any work on this item.
- Repo admin permission required to re-run the protection script (per the script header).

---

## Item 2 — Sweep-test coverage audit (PR #707 follow-up)

### 2.1 Premise verification

PR #707 commit: `feb43fb4` — `fix(test): make BatchUndoLogDaoTest 'expired' row actually expired (#707)`.

Test file: `app/src/androidTest/java/com/averycorp/prismtask/BatchUndoLogDaoTest.kt:142` — method `sweep_dropsExpiredAndStaleUndone_keepsRecent`. The fix changed one entry: `expiresAt = 1L + DAY_MILLIS` (≈86_400_001) → `expiresAt = now - 1L` so the predicate `expires_at < :now` (now=100_000) actually matched.

Sweep DAO predicate — `app/src/main/java/com/averycorp/prismtask/data/local/dao/BatchUndoLogDao.kt:73-80`:
```sql
DELETE FROM batch_undo_log
WHERE (expires_at < :now AND undone_at IS NULL)
   OR (undone_at IS NOT NULL AND undone_at < :undoneCutoff)
```
**Both predicates are strict `<`** (not `<=`). A row with `expires_at == now` survives. Caller: `BatchUndoSweepWorker.kt:44` (daily periodic; `undoneCutoff = now - 7d`).

### 2.2 Current state — every sweep entry point in the codebase

| # | DAO method | Location | Predicate | Test? | Caller |
|---|---|---|---|---|---|
| 1 | `BatchUndoLogDao.sweep(now, undoneCutoff)` | `BatchUndoLogDao.kt:73-80` | `expires_at < :now AND undone_at IS NULL OR (undone_at IS NOT NULL AND undone_at < :undoneCutoff)` | yes — `BatchUndoLogDaoTest.kt:142` | `BatchUndoSweepWorker.kt:44` |
| 2 | `FocusReleaseLogDao.deleteOlderThan(beforeTimestamp)` | `FocusReleaseLogDao.kt:20-21` | `created_at < :beforeTimestamp` | **none** | **none** (orphan DAO method per `DEAD_CODE_AUDIT.md:125`) |
| 3 | `TaskDao.archiveCompletedBefore(cutoffDate, now)` | `TaskDao.kt:151-152` | `is_completed=1 AND completed_at < :cutoffDate AND archived_at IS NULL` (UPDATE-style sweep) | only via repository fakes; no DAO-level boundary test | `AutoArchiveWorker` via `TaskRepository.kt:450` |
| 4 | `TaskDao.clearExpiredPlans` | `TaskDao.kt:218` | `<` (planner reset) | **none** | various ViewModels |

### 2.3 Gap

| Entry point | Classification | Justification |
|---|---|---|
| `BatchUndoLogDao.sweep` — `expires_at` arm | **Boundary gap (mild)** | Existing test covers `expiresAt = now - 1L` (clearly past). No row tests `expiresAt == now` (the strict-`<` boundary) or `expiresAt == now + 1` (just-future). PR #707 specifically fixed an "off-anchor" mistake — the same hazard exists in any future regression. |
| `BatchUndoLogDao.sweep` — `undone_at` arm | **Boundary gap (mild)** | Test covers `undoneAt = now - 14d` and `undoneAt = now - 30s`. No row tests `undoneAt == undoneCutoff` exactly. |
| `BatchUndoLogDao.sweep` — concurrency | **Theoretical only** | Worker is the only DELETE writer; INSERT is append-only. Single-writer pattern → not worth a test. |
| `BatchUndoLogDao.sweep` — timezone | **Not applicable** | Predicate uses raw epoch-millis, no `strftime`/local-day truncation. |
| `FocusReleaseLogDao.deleteOlderThan` | **Dead code** | Already classified in `DEAD_CODE_AUDIT.md:125`. Adding sweep tests is the wrong fix. |
| `TaskDao.archiveCompletedBefore` | **Boundary gap, but out of scope** | Different test file (`TaskDaoTest`); separate PR if pursued. |
| `TaskDao.clearExpiredPlans` | **No coverage, but out of scope** | Same shape; planner reset, not a sweep. |

### 2.4 Wrong-premise check

✅ **PROCEED WITH MODIFIED SCOPE.** The premise holds — gaps exist — but they're modest. The originally-feared categories (timezone edge cases, local-day truncation) **don't apply** to any sweep in this codebase: all time predicates compare raw `Long` epoch-millis. Concurrency/race is theoretical (single-writer worker model).

Modified scope: **scope this PR to `BatchUndoLogDaoTest` only.** `FocusReleaseLogDao.deleteOlderThan` is a separate dead-code triage; `TaskDao.archiveCompletedBefore` belongs in a `TaskDaoTest` PR.

### 2.5 Proposed PR shape

Single test file edit: `app/src/androidTest/java/com/averycorp/prismtask/BatchUndoLogDaoTest.kt`. Add 3 new test methods (~50–70 LOC including helpers):

1. `sweep_atExactExpiryBoundary_keepsRow` — insert row with `expiresAt = now`, assert sweep returns 0 and row remains. Documents the strict `<` semantics.
2. `sweep_oneMillisPastExpiry_dropsRow` — `expiresAt = now - 1L`, asserts deletion (regression-pin for PR #707's fix).
3. `sweep_undoneAtExactCutoff_keepsRow` — `undoneAt = undoneCutoff` exactly, asserts row survives the strict-`<` undone-cutoff.

Each pins behavior — would fail if anyone "fixes" the predicate to `<=`.

CHANGELOG entry (`## Unreleased`):
> - test(batch-undo): pin BatchUndoLogDao.sweep boundary semantics (expires_at and undone_at use strict `<`); follow-up to #707.

### 2.6 Risk + dependencies

- **No flakiness risk.** Tests use a hardcoded `now = 100_000L` constant; no clock, no `System.currentTimeMillis()`, no Robolectric fake-time needed.
- **No `TestClock` needed.** Predicate is a simple long comparison, fully deterministic.
- **No cross-PR dependencies.**
- Run cost: instrumented test — runs in `connectedDebugAndroidTest` only.

---

## Item 3 — Per-medication mark UX (long-press time editor)

### 3.1 Premise verification — schema state

- DB version: `CURRENT_DB_VERSION = 63` at `Migrations.kt:1843`. `@Database(version = CURRENT_DB_VERSION)` at `PrismTaskDatabase.kt:129`. **CLAUDE.md is stale at 57.**
- `versionName = "1.6.0"` at `app/build.gradle.kts:26`.
- `medication_marks` table created in `MIGRATION_62_63` at `Migrations.kt:1778-1828`. Schema:
  ```
  id PK auto, cloud_id TEXT (unique), medication_id INTEGER NOT NULL,
  medication_tier_state_id INTEGER NOT NULL, intended_time INTEGER (nullable),
  logged_at INTEGER NOT NULL, marked_taken INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL, FK CASCADE on medication_id + medication_tier_state_id
  ```
  Entity at `MedicationMarkEntity.kt:25-72`. DAO at `MedicationMarkDao.kt` has `observeForSlot`, `getForPair`, `insert`, `update`, `delete`, `setCloudId`.

### 3.2 Current state — the orphan problem

**`medication_marks` is provisioned but never written by any production write path.** Comprehensive grep:

- Only consumers of `MedicationMarkDao`: `BackendSyncService.kt:320` (read for push) and `CloudIdOrphanHealer.kt:404` (cloud-id healing).
- **No** `medicationMarkDao.insert(...)` or `update(...)` is invoked anywhere outside its own tests.
- `MedicationRepository.kt` has `logDose / unlogDose / updateDose / logSyntheticSkipDose` — all writing to **`medication_doses`**, not `medication_marks`.
- `MedicationViewModel.setIntendedTimeForSlot()` at `MedicationViewModel.kt:216-232` writes `intended_time` to **`medication_tier_states`** rows (one per `(med, slot, date)`), via `slotRepository.setTierStateIntendedTime(...)`.
- The comment at `MedicationViewModel.kt:132-134` is explicit: *"Intended/logged times are recorded per-(med, slot, date) but the user edits them at slot granularity, so all per-slot rows carry the same value."*

**The "per-medication intended_time" already lives on `medication_tier_states`, not on `medication_marks`.** The mark table is dead-on-arrival from PR #743 (PR2 of 4); the UI half (PR #744) ended up using the tier-state row instead, leaving `medication_marks` provisioned-but-unused.

### 3.3 Current state — what PR #744 actually shipped

Commit `a7274ffb feat(meds): long-press time editor + backlogged indicator (PR3 of 4) (#744)`:
- `MedicationScreen.kt:96-97, 170, 228-238` — `timeEditingSlotState` state + `onLongPressTier` wired to a per-SLOT long-press on the tier chip.
- `MedicationScreen.kt:305-313, 335-383` — `TierChip` uses `Modifier.combinedClickable(onClick, onLongClick)` on the **tier chip** itself, not on per-medication rows.
- `components/MedicationTimeEditSheet.kt` — the time-picker sheet.
- Per-medication checkbox rows already exist (`MedicationDoseRow` at `MedicationScreen.kt:386-455`). They have `clickable(onClick = onToggle)` only — **no `combinedClickable`, no per-medication long-press.**

PR #745 web parity ships the same pattern: `web/src/features/medication/MedicationScreen.tsx:313-324` wraps `MedicationTierPicker` in `TierPickerWithLongPress`. Writes to `tierStates` via `setTierStateIntendedTime`. **Per-medication rows do not exist on web at all** — the web list shows slot summaries with `medLabels` strings, not per-med checkboxes.

### 3.4 Surface mapping

| Surface | Per-medication mark UI | Per-medication time edit | Per-slot time edit |
|---|---|---|---|
| Android `MedicationScreen.kt` | YES — `MedicationDoseRow:386-455` (`clickable` checkbox toggling `medication_doses`) | NO | YES — `TierChip` long-press, line 311; opens `MedicationTimeEditSheet` |
| Web `MedicationScreen.tsx` | NO (only `medLabels` text + slot-level Mark Taken/Not Taken at line 334-340) | NO | YES — `TierPickerWithLongPress:382-419`; opens `MedicationTimeEditModal` |

### 3.5 Gap (and why it's the wrong gap)

The literal "deliverable" the prompt sketches — adding `combinedClickable(onLongClick)` to `MedicationDoseRow` and routing through a new `MedicationViewModel.updateMarkTime()` → `MedicationRepository.updateMarkTime()` → `MedicationMarkDao.update()` — would require:

1. New write path through `medication_marks` (none exists today; no `MedicationMarkRepository` exists).
2. A new `markedTaken` write semantic that overlaps with `medication_doses` (which already records taken state per `(medication_id, slot_key, date)`).
3. A new "per-med intended_time" concept that conflicts with the existing per-slot one stored on `medication_tier_states.intended_time` via `setTierStateIntendedTime`.

That's not a UI gap — it's a **data-model fork**. Two tables would carry per-(med, slot, day) state with overlapping semantics: `medication_doses` (the taken row, with timestamp `loggedAt`) plus `medication_tier_states` (the achieved-tier row with `intended_time`/`logged_at`) plus the proposed-active `medication_marks`.

### 3.6 Wrong-premise check

🛑 **STOP — WRONG PREMISE** on two compounding axes:

- **(a) "Schema NOT actually ready" — partially true.** Columns exist, but no repository writes to `medication_marks`, no Hilt-injected `MedicationMarkRepository` exists, and the per-slot intended-time path uses `medication_tier_states` instead. Wiring `medication_marks` requires designing a write path, not just adding UI.
- **(b) "Per-dose covers this" — strongest finding.** The per-medication checkbox row IS already there (`MedicationDoseRow`). The slot-level long-press IS already there. The only "missing" piece is per-medication granularity on time edits, which would fragment the model: `MedicationViewModel.kt:132` is explicit that *"the user edits them at slot granularity"* — that was a deliberate design call in PR #744. **Per-medication time editing is a feature redesign, not a UI patch.**

### 3.7 Recommended next action — three options

This bundle item should be **rejected or reframed before any code work**:

1. **Do nothing.** PR #744 already ships slot-level long-press, which the design doc treats as the per-medication editor (because all per-(med, slot, date) tier-state rows share the same intended_time). Web has parity via PR #745. Add a CHANGELOG note clarifying this if the spec language is causing confusion.
2. **Garbage-collect `medication_marks`.** If slot-granularity is final, schedule a follow-up migration to drop the orphan table + sync mappers + DAO. Eliminates a sync-protocol footgun.
3. **Genuine feature redesign.** If per-medication time editing IS a distinct desired feature, that's a brainstorm-level decision: data-model fork, sync semantics, UI conflict between row-tap-toggle and row-long-press-edit on a 8-dp-padded row. Not a Phase D drive-by.

### 3.8 Risk + dependencies

- **Sync footgun**: `medication_marks.cloud_id` + `updated_at` are present and sync mappers are wired (`BackendSyncMappers.kt:307, 326-339`, `MedicationSyncMapper.kt:234`). If any future client (web, backend, future Android) starts writing `medication_marks` while Android keeps writing `medication_tier_states`, both round-trip independently and conflict will be invisible to LWW because they're different tables.
- **Gesture conflict** (in option 3): per-row `combinedClickable` competes with the slot-level `TierChip` long-press in screen-reader contexts (both expose `onLongClickLabel = "Edit time"`). TalkBack disambiguation cost.
- **Web parity cost** (in option 3): web has no per-medication checkbox row at all (`MedicationScreen.tsx:275-279` shows `medLabels.join(', ')` as plain text). Web-side per-medication marking is a much larger lift — entirely new component, not a long-press handler addition.

---

## Item 4 — Web tier enum casing drift cleanup

### 4.1 Premise verification — Android side

- `MedicationTier` (3 values: `ESSENTIAL`, `PRESCRIPTION`, `COMPLETE`) is the per-medication classification — `app/src/main/java/.../domain/model/medication/MedicationTier.kt:14-17`. Stored as lowercase via `name.lowercase()` (`:20`).
- `AchievedTier` (4 values: `SKIPPED`, `ESSENTIAL`, `PRESCRIPTION`, `COMPLETE`) is the per-slot/day **computed state** that web's `MedicationTier` mirrors — `AchievedTier.kt:12-16, 18`. Stored as lowercase via `name.lowercase()`.
- Android Firestore mapper writes lowercase — `MedicationSyncMapper.kt:31, 73, 199-200, 224-225`.
- PR #762 = commit `44149da4` (Apr 24 2026): replaced web's `'SKIPPED' | 'PARTIAL' | 'COMPLETE'` with the lowercase 4-value type, added `normalizeTier()` legacy fallback, rewrote picker UI, added 13 vitest cases. +260/-13 across 5 files.

**Note for the audit doc reader:** the Phase D prompt says "Android canonical is `MedicationTier` with values `skipped/essential/prescription/complete`." That's a slight mislabel — Android has TWO enums; web's `MedicationTier` actually mirrors Android's `AchievedTier` (the 4-value computed state). This is acknowledged in PR #762's body. Doesn't affect the verdict.

### 4.2 Premise verification — web side

Tier definition at `web/src/api/firestore/medicationSlots.ts:46`:
```ts
export type MedicationTier = 'skipped' | 'essential' | 'prescription' | 'complete';
```
**Matches Android `AchievedTier` exactly** — lowercase, 4-tier, same order. Reads route through `normalizeTier()` (`medicationSlots.ts:65-79`), which folds legacy uppercase docs and warns. Picker (`MedicationTierPicker.tsx:4-9`) renders all four lowercase values. Screen handler (`MedicationScreen.tsx:140`) is type-safe.

### 4.3 Drift inventory

**Zero drift in production code.** All non-canonical "tier" string occurrences in `web/src/`:

- `web/src/types/auth.ts:7` — `tier: 'FREE' | 'PRO'` (Pro-billing tier, **not medication**)
- `web/src/hooks/useProFeature.ts:16-17` — same Pro-billing tier
- `web/src/features/batch/{BatchPreviewScreen,batchApplier}.tsx`, `web/src/types/batch.ts:16` — `'COMPLETE'` is a batch `mutation_type`, **not a tier**
- `medicationSlots.ts:68-70` — uppercase strings appear as legacy-detection branches inside `normalizeTier()` (correct, intentional)
- `medicationSlots.test.ts:55-75`, `medicationSlots.ts:42, 58-59` — uppercase strings appear in test fixtures/comments documenting the legacy values (correct)

### 4.4 Wrong-premise check

🛑 **STOP — NO WORK NEEDED.** PR #762 is comprehensively complete.

Additional findings:
- **Backend stores tier as free-form `String(20)`** with no validation — `backend/app/models.py:593` (`tier = Column(String(20), nullable=False)`). Whatever the client sends round-trips. Confirms PR #762's commit message ("Backend has no medication tier validation — confirmed clean").
- **Sync isn't actually wired** for medication tier-states cross-platform: PR #762 body explicitly notes web Firestore tier-states live at a different doc path than Android's. Aligning the enum was a forward-looking guard, not an active bug fix — and that guard is in place.

### 4.5 Proposed PR shape

**No PR needed.** A redundant verification PR would add 0 production lines + one new vitest case asserting `Object.values<MedicationTier>` exactly equals `['skipped','essential','prescription','complete']`. That's gold-plating; **recommend skipping**.

### 4.6 Risk + dependencies

- **Persisted-data risk: already mitigated.** `normalizeTier()` (`medicationSlots.ts:65-79`) handles any pre-v1.5.3 Firestore doc still carrying `SKIPPED`/`PARTIAL`/`COMPLETE`. localStorage doesn't store tier values.
- **No dependencies on Items 3 or 5.**

---

## Item 5 — Web-side in-app account deletion flow

### 5.1 Premise verification — backend

> ⚠️ Local `main` is at `f248bdd9` (8 commits behind `origin/main`). PR #774 (`72659e33`) is on origin but not in the local working tree. Citations below use `git show 72659e33:<path>` against origin/main.

**4 endpoints under `/api/v1/auth/me/deletion` + `/me/purge`** — `backend/app/routers/auth.py`:

- `:211` — `GET /me/deletion` → `get_deletion_status(current_user = Depends(get_current_user))`. Returns `DeletionStatusResponse`. Auth: `get_current_user` (intentionally NOT `get_active_user`, so pending-deletion users can still inspect status).
- `:221` — `POST /me/deletion` → `request_deletion(body: DeletionRequest, current_user = Depends(get_current_user))`. Body: `{ initiated_from: "android"|"web"|"email" }`. Idempotent.
- `:256` — `DELETE /me/deletion` → `cancel_deletion(...)`. Idempotent.
- `:277` — `POST /me/purge` → 204 No Content. Refuses if not pending or grace window unexpired (409).

**`get_active_user`** — `backend/app/middleware/auth.py:78-91`:
```python
async def get_active_user(current_user: User = Depends(get_current_user)) -> User:
    if current_user.deletion_pending_at is not None:
        raise HTTPException(status_code=status.HTTP_410_GONE,
                            detail="Account is pending deletion")
    return current_user
```
**Returns HTTP 410 Gone when pending.** Applied to mutation endpoints.

PR #774 stat: `backend/app/middleware/auth.py` (+16), `backend/app/routers/auth.py` (+149), `backend/app/schemas/auth.py` (+19), `backend/app/services/auth.py` (+20), `backend/alembic/versions/020_add_user_deletion_fields.py` (+51), `backend/tests/test_auth_deletion.py` (+298).

### 5.2 Premise verification — Android idiom is typed-DELETE

`app/src/main/java/com/averycorp/prismtask/ui/screens/settings/sections/DeleteAccountSection.kt:46-105` (`DeleteAccountSection` composable + inner `DeleteAccountDialog`) uses a two-step state machine:

```kotlin
private enum class DeleteDialogStep { EXPLAIN, CONFIRM }
var step by remember { mutableStateOf(DeleteDialogStep.EXPLAIN) }
var confirmText by remember { mutableStateOf("") }
```

- **EXPLAIN step**: lists what gets deleted (sign-out / 30-day scheduled deletion / local data wipe). "Continue" → CONFIRM; "Cancel" closes.
- **CONFIRM step**: `OutlinedTextField` requiring user to type literal `"DELETE"` (`.trim()`-tolerant). Destructive button disabled until match; error-styled.

Trigger button (`:78-100`): outlined button with `BorderStroke(1.dp, error)`, shows `CircularProgressIndicator` + "Deleting…" while in flight. Visual idiom mirrors existing `ResetAppDataDialog` (RESET → DELETE substitution).

### 5.3 Premise verification — web current state (disabled, no-op)

`web/src/features/settings/SettingsScreen.tsx:811-822`:
```tsx
<Button variant="danger" disabled
        title="Account deletion is currently only available in the PrismTask Android app">
  Delete Account
</Button>
<p className="mt-2 text-xs text-[var(--color-text-secondary)]">
  To delete your account, open the PrismTask Android app and go to{' '}
  <span className="font-medium">Settings → Account &amp; Sync → Delete Account</span>.
  ...
  A web-side delete is on the roadmap.
</p>
```

Plus comment at `:191-194`: *"Account deletion is currently Android-only … intentionally disabled until we wire the same /api/v1/auth/me/deletion endpoint into the web flow."*

**No partial implementation**: `web/` has zero matches for `DeleteAccountModal`, `useDeleteAccount`, `accountDeletion`. Pre-PR-774's `deleteAccountOpen` state and `ConfirmDialog` were intentionally removed (they were a privacy lie — toasted success without calling backend).

### 5.4 Wrong-premise check

✅ **PROCEED AS WRITTEN.**

- Backend endpoints exist (4) on origin/main.
- `get_active_user` returns 410 Gone — matches premise.
- Android idiom IS typed-DELETE (not biometric/email-code).
- Web button is disabled with helper text — matches premise exactly.

One nuance: local `main` is 8 commits behind. **Implementation must `git pull --rebase origin main` first.**

### 5.5 Gap — proposed web flow

State machine (in `DeleteAccountModal`):
```
idle → explain → confirming(typedText) → submitting → submitted → signing-out → signed-out
                                              ↓ (error)
                                          error(retryable)
```

Component tree:
```
SettingsScreen.tsx (replace lines 811-822)
  └ <DeleteAccountSection /> (new — colocate with logout)
      └ <DeleteAccountModal /> (new — multi-step)
            └ uses useAuthStore() for sign-out
            └ uses authApi.requestAccountDeletion() (new)
```

API additions — `web/src/api/auth.ts` (extend `authApi`):
```ts
getDeletionStatus(): Promise<DeletionStatus> { ... },
requestAccountDeletion(initiatedFrom: 'web' = 'web'): Promise<DeletionStatus> { ... },
cancelAccountDeletion(): Promise<DeletionStatus> { ... },
```
Add `DeletionStatus` type to `web/src/types/auth.ts`.

Existing API client: `web/src/api/client.ts` (axios singleton). Auth header is auto-attached from `localStorage.prismtask_access_token`. **Note**: response interceptor handles 401/403/429 but **NOT 410** — see Risk.

Sign-out wiring: `useAuthStore.logout()` already exists at `web/src/stores/authStore.ts`.

### 5.6 Proposed PR shape

Files touched (5–6):
- `web/src/api/auth.ts` (+~25 lines: 3 new methods)
- `web/src/types/auth.ts` (+~10 lines: `DeletionStatus` interface)
- `web/src/features/settings/sections/DeleteAccountSection.tsx` (new, ~60 lines)
- `web/src/features/settings/sections/DeleteAccountModal.tsx` (new, ~180 lines: state machine + 2 steps)
- `web/src/features/settings/SettingsScreen.tsx` (replace lines 811-822 with `<DeleteAccountSection />` and remove the comment block at :191-194; -15/+5)
- `web/src/api/client.ts` (+~6 lines: add `status === 410` branch in response interceptor → force logout)

Total: **~280–320 net LOC.**

Vitest cases (new `DeleteAccountModal.test.tsx` + `auth.test.ts`):
1. Modal opens in `explain` step; "Cancel" closes; "Continue" advances to `confirming`.
2. Submit button disabled until typed text trimmed equals `"DELETE"` (test `"delete"`, `" DELETE "`, `"DELETE"`, `""`).
3. Successful `POST /me/deletion` transitions submitting → submitted → signing-out, fires `useAuthStore.logout()` mock.
4. API failure surfaces error state with retry; modal stays open.
5. `authApi.requestAccountDeletion()` posts `{ initiated_from: 'web' }` to `/auth/me/deletion`.
6. 410 interceptor branch: any post-deletion API call clears tokens + triggers logout (regression test).

CHANGELOG entry (`## Unreleased` → `### Added`):
> - Web-side in-app account deletion: Settings → Delete Account now opens a two-step typed-DELETE confirmation that calls `/api/v1/auth/me/deletion` with `initiated_from="web"` and signs the user out on success. Mirrors the Android flow shipped in #774; closes the privacy gap from PR4b.

### 5.7 Risk + dependencies

- **410 handling gap (must-fix in this PR)**: `web/src/api/client.ts` response interceptor handles 401/403/429 but **NOT 410**. After deletion request, all subsequent mutation API calls return 410 from `get_active_user`. Need a `status === 410` branch that mirrors the 401-no-refresh-token path: clear tokens, call `useAuthStore.logout()`, redirect to login. Prevents zombie web sessions.
- **Session revocation**: backend doesn't blacklist the JWT — token remains technically valid until expiry. Mitigation: in `signing-out` state, immediately call `useAuthStore.logout()` (clears `localStorage`) AND `firebaseAuth.signOut()`. The 410 interceptor is the safety net.
- **Cross-device behavior — recommended expansion**: after web request, Firestore `users/{uid}` deletion fields are NOT marked from web (only Android does that — see `AccountDeletionService.kt`). On next Android sign-in, Android's `checkDeletionStatus()` reads from Firestore (which is unmarked), so Android won't detect a web-initiated deletion. **Recommend** web also writes Firestore fields via `updateDoc(doc(db, 'users', uid), { deletion_pending_at, deletion_scheduled_for, deletion_initiated_from: 'web' })`. Adds ~15 LOC.
- **No data-destruction risk**: web flow is "request" only. `POST /me/deletion` is idempotent + reversible for 30 days. Permanent purge is server-driven on next Android sign-in or admin sweep — web client never calls `/me/purge`.
- **No dependency on Item 4** (web tier enum cleanup is orthogonal).

---

## Recommendations summary

| # | Item | Verdict | Reason |
|---|---|---|---|
| 1 | `cross-device-tests` → required | 🛑 **STOP — WRONG PREMISE** | Job is failing 100% of main runs (3/3 since PR #773 merged). Retry-once cannot recover from emulator-runner setup crashes. Promoting it would block all merges. **Need stabilization PR first; then ≥5 consecutive green main runs before promotion.** |
| 2 | Sweep-test audit | ✅ **PROCEED WITH MODIFIED SCOPE** | Real but small gaps in `BatchUndoLogDao.sweep` boundary. Single test file edit (~60 LOC) pinning strict-`<` semantics. Other DAOs out of scope. |
| 3 | Per-medication mark UX | 🛑 **STOP — WRONG PREMISE** | `medication_marks` table is a provisioned orphan with no write path. Per-medication-row long-press would fork the data model (3 tables for the same per-(med, slot, day) state). Slot-level long-press from PR #744 IS the per-medication editor by design. **Reject or reframe.** |
| 4 | Web tier enum drift | 🛑 **STOP — NO WORK NEEDED** | PR #762 is comprehensively complete. Zero drift in production code. `normalizeTier()` handles legacy Firestore docs. |
| 5 | Web in-app delete flow | ✅ **PROCEED AS WRITTEN** (with one expansion) | Backend endpoints exist (4), 410 middleware in place, web button is verifiably disabled. ~280–320 LOC. Adds 410-interceptor branch + recommended Firestore-mark for cross-device parity with Android sign-in guard. |

**Net Phase 2 work, if all "PROCEED" items go ahead:** 2 PRs (Items 2 and 5).

**Recommended user decisions (for the prompt's "STOP and report" pattern):**

- **Item 1**: Approve a stabilization PR (single-file workflow tweak) as a substitute, OR drop entirely.
- **Item 3**: Pick option 1 (do nothing — close as completed-by-design), 2 (garbage-collect orphan table), or 3 (genuine feature redesign in a separate brainstorm).
- **Item 4**: Confirm closure as "completed by PR #762"; no work.

Items 2 and 5 can proceed once 1/3/4 decisions are recorded.
