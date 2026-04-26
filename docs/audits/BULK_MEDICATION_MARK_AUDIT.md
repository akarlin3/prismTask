# Bulk Medication Tier Marking — Pre-Implementation Audit

**Date:** 2026-04-25
**Bundle owner:** akarlin3
**Pattern:** Audit-first, single PR (Android + web parity)
**Companion infrastructure:** PR #772 (batch mutations: STATE_CHANGE verb, BatchEntityType.MEDICATION, batch_undo_log)

---

## Working-tree state

Local `main` is current at `91452743` (PR #786 just merged). The Phase D bundle (#780–#786) has settled. DB version is **64**, `medication_marks` table dropped, web in-app delete flow live.

## Premise verification snapshot

| Premise from prompt | Verified? | Note |
|---|---|---|
| `medication_tier_states` schema doc-string composite key | ⚠️ partial | Web doc id IS `{date}__{slot}` (slot-aggregate per day). **But Android Room key is `(medication_id, log_date, slot_id)` — per-(med, slot, date)**, NOT 1:1 with web. This is a real architectural difference, not just a casing issue. See Item 1 + the Decision-points block. |
| Tier enum lowercase 4-tier | ✅ on disk | `AchievedTier` (4-tier per-slot computed) and `MedicationTier` (3-tier per-med classification) are both UPPERCASE Kotlin enums but serialize lowercase via `toStorage()`. Web `MedicationTier` type is lowercase 4-tier on the wire. |
| PR #772 STATE_CHANGE accepts multi-target payloads | ❌ NO | Single-target only. `entity_id` is one Long; `pre_state_json` is one flat Map. Apply path takes `(entityId, mutation)` scalars. **3a infeasible.** |
| Multi-doc Firestore writes are an existing pattern on web | ❌ NO | Zero `writeBatch` usage anywhere in `web/src/`. **Bulk web write is N independent setDoc calls — partial-write/torn-state risk on network failure.** |
| `MedicationViewModel` has flows materializing "all-at-slot-X" | ✅ | `slotTodayStates: StateFlow<List<MedicationSlotTodayState>>` already shapes that. No tier-keyed flow exists. |
| PR #744 slot-level long-press is on tier chip, must be preserved | ✅ | `TierChip.combinedClickable(onClick, onLongClick)` at `MedicationScreen.kt:350-354`. Bulk-mark must NOT collide with this gesture. |
| `BatchPreviewScreen` renders STATE_CHANGE rows | ✅ | One row per target. Multi-target bulk action would render N rows. **No native multi-target row template.** |

---

## Item 1 — `medication_tier_states` schema (post v63→v64)

### Android Room entity

`app/src/main/java/com/averycorp/prismtask/data/local/entity/MedicationTierStateEntity.kt:25-84`

| Column | Type | Nullability | Default |
|---|---|---|---|
| `id` | INTEGER PK auto | NOT NULL | autogen |
| `cloud_id` | TEXT | nullable | NULL |
| `medication_id` | INTEGER (FK→medications.id, CASCADE) | NOT NULL | — |
| `slot_id` | INTEGER (FK→medication_slots.id, CASCADE) | NOT NULL | — |
| `log_date` | TEXT (ISO LocalDate) | NOT NULL | — |
| `tier` | TEXT (lowercase token) | NOT NULL | — |
| `tier_source` | TEXT (`computed` \| `user_set`) | NOT NULL | `"computed"` |
| `intended_time` | INTEGER (epoch ms) | nullable | NULL |
| `logged_at` | INTEGER (epoch ms) | NOT NULL | 0 (then backfilled) |
| `created_at`, `updated_at` | INTEGER | NOT NULL | now() |

Unique index: `(medication_id, log_date, slot_id)` — note the column order. Cross-system handle: `cloud_id`.

### DAO query coverage

`app/src/main/java/com/averycorp/prismtask/data/local/dao/MedicationTierStateDao.kt:13-58`

The bulk-mark feature needs "all tier-states at (slot, date)" — `getForSlotDateOnce(slotId, date): List<MedicationTierStateEntity>` already exists. No new DAO query needed for the slot-scope case. There's no tier-keyed query (e.g., "all tier-states where current tier = essential"); the tier-scope feature would need to either filter `getForDateOnce(date)` in memory or add a new `@Query`.

### Web Firestore doc shape

`web/src/api/firestore/medicationSlots.ts:223-241, 285-311`

```
users/{uid}/medication_tier_states/{dateIso}__{slotKey}
```

Doc id is deterministic. **Doc body shape:** `{slotKey, dateIso, tier, source, loggedAt, updatedAt}` plus optional `intendedTime`. Tier enum: `'skipped' | 'essential' | 'prescription' | 'complete'`.

### ⚠️ Schema asymmetry (call out)

**Android stores per-(medication, slot, date) — one row per medication for each slot+day.** **Web stores per-(slot, date) — one doc per slot+day, aggregated across medications.**

Concretely: a slot 8am with 4 medications has **4 Android rows** (one per med) but **1 web doc** ("the achieved tier for slot 8am on 2026-04-25 is `complete`"). This is by design — the web has never tracked per-medication tier state, only slot-aggregate.

This means **"mark slot 8am complete"** is:
- **Android**: 4 row writes (one STATE_CHANGE per medication in the slot)
- **Web**: 1 doc write (one tier-state for the slot)

Same semantic outcome, different write fan-outs. The bulk-mark UI behaves identically; the underlying batch shape differs by platform. This is fine — but the audit must surface it because it directly drives the test scale and the per-platform implementation cost.

---

## Item 2 — PR #772 Android infrastructure

### Apply path

`BatchOperationsRepository.applyMedicationMutation` at `BatchOperationsRepository.kt:434-578` is **strictly single-target**:

```kotlin
private suspend fun applyMedicationMutation(
    batchId: String, commandText: String, expiresAt: Long, now: Long,
    mutationType: BatchMutationType,
    entityId: Long,                       // <-- ONE medication id
    mutation: ProposedMutationResponse    // <-- ONE slot_key + date + tier
): MutationOutcome
```

`pre_state_json` (built by `snapshot(...)` at line 858-883) is a flat `Map<String, Any?>` — one entity, one snapshot blob. No list-of-objects in the on-disk shape.

### Reverse path

`reverseMedication` at `BatchOperationsRepository.kt:747-825`. Driven from `reverseOne` (line 620), iterates per-row: `entry.entityId ?: return false` (line 624). Undo is always single-target per row. `undoBatch` aggregates by `batch_id` and reverses each row independently.

### `batch_undo_log` schema

`BatchUndoLogEntry.kt:49-50, 59-60`:
- `entity_id` is `Long?` (nullable, single)
- `pre_state_json` is `String NOT NULL` (one flat JSON object)
- No list-style column anywhere in the schema

### Existing test coverage

`BatchOperationsRepositoryMedicationTest.kt` — 11 tests (apply 6 + undo 5), **all single-target**. Every `mutations = listOf(...)` is size 1.

### Tier enum confirmation

- `AchievedTier`: `SKIPPED, ESSENTIAL, PRESCRIPTION, COMPLETE` (UPPERCASE Kotlin), serialized lowercase via `toStorage()`
- `MedicationTier`: `ESSENTIAL, PRESCRIPTION, COMPLETE` (UPPERCASE Kotlin), serialized lowercase
- **`applyMedicationMutation:542,553,561` writes `tier` raw without `toStorage()` — input MUST already be lowercase.** Bulk-mark inputs follow suit.

### Feasibility verdict

| Option | Verdict | Reason |
|---|---|---|
| **3a** — multi-target STATE_CHANGE row | ❌ **NO** | Schema is single-entity. Consumers (`reverseOne`, every `reverseMedication` branch) read scalar keys. Repackaging would require rewriting both paths + a new `applyMedicationMutationBulk` function — that IS a parallel apply/reverse path, not "no schema change." |
| **3b** — fan-out under shared batch_id | ✅ **YES** | `applyBatch` at `BatchOperationsRepository.kt:130-160` already loops `for (mutation in mutations)` and assigns one shared `batchId = UUID.randomUUID()` (line 126) to every row. Five "mark slot 8am complete" mutations passed as a `List<ProposedMutationResponse>` produce 5 `BatchUndoLogEntry` rows under one batch_id, all in one `withTransaction`. `undoBatch(batchId)` already reverses all of them as a unit. **Zero schema changes, zero apply-path changes, zero undo-path changes.** |
| **3c** — new BULK_STATE_CHANGE verb | ❌ **SHOULD-NOT-USE** | 3b is feasible today with zero changes. A new verb forces matching changes in `BatchMutationType`, Haiku prompt + Pydantic regex, web `MUTATION_LABELS`, web `batchApplier`, plus a new DB-level multi-target apply/undo. PR #772 explicitly named the verb *"entity-agnostic"* in its commit message. |

### Recommended payload shape

Bulk caller resolves the slot's medication list at intent time (`MedicationDao.getAllForSlotOnce(slotId)`) and emits one `ProposedMutationResponse` per medication, each with `mutationType = "STATE_CHANGE"`, `entityType = "MEDICATION"`, `entityId = "<medId>"`, `proposedNewValues = {"slot_key": "8am", "date": "2026-04-25", "tier": "complete"}`. Pass the full list as `mutations` to one `applyBatch` call so all rows share a `batch_id` and undo as one unit.

---

## Item 3 — PR #772 web infrastructure

### Apply path signature

`web/src/features/batch/batchApplier.ts`:
```ts
async function applyMedicationMutation(
  uid: string,
  entityId: string,
  mutationType: BatchMutationType,
  values: Record<string, unknown>,
): Promise<ApplyOutcome>
```

`ProposedMutation` shape (`web/src/types/batch.ts`): `{ entity_type: 'MEDICATION', entity_id: string, mutation_type: BatchMutationType, proposed_new_values: Record<string, unknown>, human_readable_description: string }`.

### Firestore apply path

Doc path `users/{uid}/medication_tier_states/{dateIso}__{slotKey}` (deterministic id from `tierStateId(dateIso, slotKey)`). Writes via `setTierState(uid, dateIso, slotKey, tier, 'user_set')` using `setDoc(ref, payload, { merge: true })`. Each call is its own single-doc write.

### ⚠️ Multi-doc strategy: NONE

Grep for `writeBatch|batch.commit|firestore.batch` across `web/src/` returns **zero matches**. `batchStore.commit` loops `applyMutation` sequentially with `await` — one Firestore round-trip per mutation, no atomic batch.

**Risk:** a network blip during a 4-slot bulk-mark leaves a torn write — some slots updated, others stale. Android pull-side LWW converges eventually (each doc has its own `updatedAt`), but the user sees half-applied state until retry. **This is a decision point for the user — see Decision Points below.**

### Undo storage on web

Web does NOT sit on Android's `batch_undo_log`. Undo is in `localStorage` per Firebase uid:
- Key: `prismtask_batch_history_${uid}`
- 24h `UNDO_WINDOW_MS`, capped at `MAX_HISTORY_ENTRIES = 25`
- `BatchHistoryRecord.entries: BatchUndoLogEntry[]` carries `pre_state` per entry
- `undoEntry` calls `setTierState(uid, dateIso, slotKey, prior_tier, prior_source)` if `prior_existed`, else `clearTierState`

**Durability: per-browser, per-uid, localStorage. Survives reload but NOT cross-device.** Different from Android's Room-backed durable log.

### Existing test coverage

8 vitest cases in `batchApplier.medication.test.ts`. All single-target. Bulk follow-up should add ~4 multi-target cases (apply both, partial-failure, undo reverses both, idempotent re-apply).

### Tier picker reuse

`MedicationTierPicker` (`web/src/features/medication/MedicationTierPicker.tsx`) is a 4-button chip row. Reusable verbatim for "destination tier" in the bulk-mark dialog: pass `value={selectedTier}`, drop `isUserSet`/`onClear`.

### Feasibility verdict

| Option | Verdict | Reason |
|---|---|---|
| **3a** — multi-target STATE_CHANGE | ❌ **NO** | `ProposedMutation.entity_id` is a single string; backend regex `_BATCH_MUTATION_TYPE_PATTERN` validates on the existing single-entity shape. Adding `targets: [...]` diverges. |
| **3b** — fan-out at store layer | ✅ **YES** | `batchStore.commit` already takes `mutations: ProposedMutation[]`, loops, groups under one `batch_id` with shared `created_at`/`expires_at`. Bulk-mark UI builds N `ProposedMutation` rows and calls `useBatchStore.commit(commandText, mutations)`. |
| **3c** — new verb | ❌ **SHOULD-NOT-USE** | Backend regex change + applier branch + breaks Android parity. |

---

## Item 4 — UI invocation surfaces

### Android — `MedicationScreen.kt`

Single-screen LazyColumn (`MedicationScreen.kt:142-194`):
- TopAppBar with History + Edit icons (lines 108-140)
- One `SlotTodayCard` per slot (lines 158-172)
- "All Medications" edit list (edit mode only)
- 3 modal layers: `MedicationEditorDialog`, edit-dialog, `MedicationTimeEditSheet`, archive `AlertDialog`

Composables: `MedicationScreen` (83), `SlotTodayCard` (266), `TierChip` (337), `MedicationDoseRow` (386), `MedicationEditRow` (458).

### PR #744 long-press to preserve

`TierChip.kt:350-354` wires `combinedClickable(onClick = tierCycle, onLongClick = openTimeEditSheet, onLongClickLabel = "Edit time")`. Caller in `SlotTodayCard:170-171, 305-312` passes `onLongPressTier = { timeEditingSlotState = state }`. **Per-slot long-press already opens the time-edit sheet — bulk-mark must use a DIFFERENT gesture surface.** Recommendation: a TopAppBar action icon (PlaylistAddCheck / DoneAll) at line 111-118.

### `MedicationViewModel` state available

`slotTodayStates: StateFlow<List<MedicationSlotTodayState>>` (`MedicationViewModel.kt:110-146`) already materializes "all meds at slot X for today" with: linked meds, takenMedicationIds, achievedTier, isUserSet. **No tier-keyed flow exists** — a tier-scope multi-target mark would scan `slotTodayStates.value` in memory.

Existing single-target STATE_CHANGE path: `slotRepository.upsertTierState(...)` invoked per medication inside `setSkippedForSlot` (`MedicationViewModel.kt:189-209`). Simplest VM addition: a single `markSlots(targets: List<(medicationId, slotId, date)>, tier: AchievedTier)` looping the existing call.

### `BatchPreviewScreen` multi-target rendering

`MutationRow` (`BatchPreviewScreen.kt:239-292`) is strictly per-row-per-target. Medication STATE_CHANGE rendering at `BatchPreviewScreen.kt:280-288` with `MedicationTierChip` showing `tier (slotKey)` per row.

**Recommendation: do NOT extend the row template.** A direct in-app bulk-mark (no AI parse) bypasses BatchPreview entirely. Surfacing N rows in BatchPreview already works for the AI path. For the new menu path, ship a pre-confirmation step inside the bulk-mark dialog (one-line summary: "Mark 4 medications in slot 8am as complete") — that's the bulk-equivalent of BatchPreview, and it's a more natural fit for an explicit user action vs an AI-parsed batch.

### Tier + slot enumeration

- `MedicationTier`: `MedicationTier.kt:14-17, 27` (LADDER ordering)
- `AchievedTier`: `AchievedTier.kt:12-17`
- Slot order: `MedicationSlotDao.kt:15` `SELECT * FROM medication_slots WHERE is_active = 1 ORDER BY sort_order ASC, ideal_time ASC, id ASC` — already ordered for UI consumption

### Web — `MedicationScreen.tsx`

Header bar (`MedicationScreen.tsx:206-223`) with day-shift cluster is the only common bulk-action surface. Slot tiles (`<ul>` grid, lines 226-345) wrap `TierPickerWithLongPress` (lines 313-324) which owns the existing PR #745 long-press for time-edit (parity with Android PR #744). **Don't disrupt that.**

Recommendation: **TopAppBar / header-bar button on both platforms.** Lowest gesture-collision risk.

---

## Item 5 — UX shape recommendation

**Pick: Option A — Single menu with scope picker.**

### Why A, not B or C

- Adding three separate entry points (B) means inventing new chrome per slot tile and per tier chip. Android's per-slot long-press is already taken (PR #744); web's per-slot long-press is already taken (PR #745). No room without collision.
- The screen has only one global affordance today (TopAppBar Edit icon on Android, header bar on web). One bulk-mark icon next to it doesn't crowd the screen.
- `slotTodayStates` is a flat per-slot list and the tier picker is already a 4-button component. Composing them into one dialog is structurally trivial.

### What changes

| Surface | Change | Preserves |
|---|---|---|
| Android `MedicationScreen.kt` | New TopAppBar action icon (PlaylistAddCheck / DoneAll) → opens `BulkMarkDialog` composable | All existing PR #744 long-press wiring |
| Android `MedicationViewModel.kt` | New method `bulkMark(scope, target, tier)` | All existing single-target methods |
| Web `MedicationScreen.tsx` | New header-bar button → opens `BulkMarkDialog` component | All existing PR #745 long-press wiring + per-slot Mark Taken / Not Taken buttons |

### `BulkMarkDialog` shape (per platform)

```
┌──────────────────────────────────┐
│ Bulk mark medications     [X]    │
│                                  │
│ Scope:  ( ) This slot            │
│         ( ) All today at tier T  │  ← see Decision Point #2
│         ( ) Full day (all slots) │
│                                  │
│ [target picker, depends on scope]│
│                                  │
│ Set to: [skipped][essential]     │
│         [prescription][complete] │
│                                  │
│ [Cancel]            [Mark]       │
│                                  │
│ "This will update 4 medications  │
│  in slot 8am for today."         │
└──────────────────────────────────┘
```

The summary line shows the action's blast radius before the user commits. That's the bulk-equivalent of BatchPreview, scoped to a direct (non-AI) action.

---

## Item 6 — Edge cases

| Case | Recommendation | Evidence |
|---|---|---|
| **Already-set targets** | **No-op (write-through).** Bulk-marking 4 medications where 2 are already at COMPLETE produces 2 idempotent updates. LWW handles. Optional polish: count `existingSource == USER_SET && existing.tier == newTier` rows pre-write and surface in a Snackbar — but that's polish, not correctness. | `MedicationSlotRepository.kt:182-219` — `upsertTierState` only short-circuits when existing is `USER_SET` and new write is `COMPUTED`; for two USER_SET writes (the bulk case), it overwrites unconditionally. |
| **Empty selection** | **Disable the confirm button when target produces zero `(med, slot)` pairs.** Same pattern as the existing "No medications linked to this slot yet" empty state (`MedicationScreen.kt:314-320`). | UX choice; no code constraint. |
| **Mixed reminder modes** | **Verified irrelevant for non-SKIPPED writes.** Tier-write path is `medicationTierStateDao.update/insert` only. Doesn't read or modify `medications.reminderMode`. | `BatchOperationsRepository.kt:493-507`, `MedicationSlotRepository.kt:192-217` |
| ⚠️ **SKIPPED-bulk side effect** | **Bulk-marking SKIPPED MUST mirror `setSkippedForSlot`'s synthetic-skip dose loop** (`MedicationViewModel.kt:194-207` calls `medicationRepository.logSyntheticSkipDose` to re-anchor interval-mode reminders). Non-SKIPPED bulk-marks must NOT invoke this — they'd scramble interval scheduling. | `MedicationViewModel.kt:194-207` |
| **Cross-device race** | **LWW preserved per-row.** Multi-target apply on Android = N independent `upsertTierState` calls, each with its own `updatedAt = now`. Each row's last-write-wins is unaffected by being part of a bulk. | `MedicationSlotRepository.kt:213` (`updatedAt = now`); `SyncService.kt:2133-2135` (pull-side comparator) |
| ⚠️ **Web partial-write** | **No atomicity today on web** (no `writeBatch` in `web/src/`). N independent `setDoc` calls; network blip mid-bulk leaves torn state. LWW converges on retry, but UI may show half-applied during the window. | `web/src/features/batch/batchApplier.ts` (no batched write); zero `writeBatch` matches in `web/src/` |

---

## Item 7 — Proposed PR shape

### Files touched (estimate)

| Layer | Files | Approx LOC |
|---|---|---|
| Android repo | `BatchOperationsRepository.kt` (no changes — `applyBatch` loop already handles N mutations), `MedicationViewModel.kt` (+ `bulkMark` method ~30 LOC), `MedicationSlotRepository.kt` (no changes) | +30 |
| Android UI | `MedicationScreen.kt` (TopAppBar icon ~5 LOC), `BulkMarkDialog.kt` (new ~250 LOC), `BulkMarkScope.kt` (new ~20 LOC enum + helper) | +275 |
| Android tests | `BatchOperationsRepositoryMedicationTest.kt` (+ 4 multi-target cases ~150 LOC), `MedicationViewModelTest.kt` (+ 3 bulk cases ~80 LOC), `BulkMarkDialogTest.kt` (new ~120 LOC) | +350 |
| Web | `BulkMarkDialog.tsx` (new ~200 LOC), `MedicationScreen.tsx` (+ button ~10 LOC), `useBulkMark.ts` hook (new ~50 LOC) | +260 |
| Web tests | `BulkMarkDialog.test.tsx` (new ~150 LOC), `useBulkMark.test.ts` (new ~80 LOC), `batchApplier.medication.test.ts` (+4 multi-target cases ~80 LOC) | +310 |
| Backend | **None** — STATE_CHANGE shape unchanged; backend allowlist already accepts the existing payload | 0 |
| CHANGELOG | One entry under `## Unreleased → ### Added` | +12 |

**Total estimate: ~1,250 net LOC across ~10 files. No backend changes. No schema changes. No new mutation verb.**

### Test scale honest assessment

The prompt asks to "match PR #772's test scale (10 androidTest, ~290+ vitest)." Verified counts:
- PR #772 actual androidTest: **11 cases** (`BatchOperationsRepositoryMedicationTest.kt`, all single-target)
- PR #772 actual vitest: **8 cases** (`batchApplier.medication.test.ts`)

The prompt's "295 vitest cases" figure refers to the broader web test suite, not PR #772 specifically. Realistic bulk-mark coverage matches scope: 7 new android cases + 7 new vitest cases = roughly 50% growth on each suite, justified by the multi-target shape and the new dialog component.

### CHANGELOG entry text

> **Bulk medication tier marking.** A new "Mark multiple" entry in `MedicationScreen` (TopAppBar on Android, header bar on web) lets the user mark all medications in a slot, all medications at a chosen tier today, or all medications today as a chosen target tier in one action. Routes through PR #772's existing `BatchEntityType.MEDICATION` + `STATE_CHANGE` infrastructure with fan-out at the apply layer — every mutation shares one `batch_id` so the existing 24h Snackbar undo and durable history undo cover the whole bulk action atomically. Slot-level long-press from PR #744 (Android) and PR #745 (web) is preserved unchanged; bulk-mark uses a separate gesture surface. The SKIPPED bulk path mirrors the synthetic-skip dose loop from `MedicationViewModel.setSkippedForSlot` so interval-mode reminder scheduling stays anchored.

---

## ⚠️ Decision points needing your input

These are the audit's wrong-premise candidates and architectural choice points. **The audit recommends each, but each is a real choice and worth your call.**

### Decision 1 — Web atomicity: introduce `writeBatch` or accept torn writes?

**Context:** Web has zero `writeBatch` usage today. A 4-medication bulk-mark on web is 4 independent `setDoc` calls. Network blip mid-bulk → user sees 2-of-4 applied until retry. LWW eventually converges.

- **A.** Introduce `writeBatch` for the bulk path only. ~20 LOC in `medicationSlots.ts`. Atomic per-bulk. Recommended for production polish.
- **B.** Accept partial-write risk; rely on LWW convergence. Zero new infra. Faster to ship. Documented limitation.

**My pick: A.** Atomicity matters for a destructive bulk action; "I marked 4 medications complete" partially failing is a bad failure mode. The 20 LOC delta is small.

### Decision 2 — Tier scope semantics: source filter vs uniform setter?

**Context:** "Mark all today at tier essential" is ambiguous. Two readings:

- **A.** *Source-tier filter:* "Mark every medication WHERE classification = essential as complete today." Reads `MedicationEntity.tier` per med, fans out to N tier-state writes for matching meds. **Android-natural** (per-med tier exists). **Web complication:** web doesn't render per-medication classification on this screen, so the UI affordance feels disconnected from the data.
- **B.** *Uniform setter:* "Set today's achieved tier to essential for all slots." Iterates active slots, writes each one. **Both platforms natural** (slot-aggregate IS what the screen renders).
- **C.** Drop the tier scope entirely; only ship slot-scope and full-day-scope. Simpler. The tier scope wasn't in the original screen idiom and may be over-thinking it.

**My pick: B if you want all three scopes to feel uniform. C if you want the cleanest first ship.** A is technically richer but the web disconnect makes it feel half-shipped.

### Decision 3 — Test scale parity vs scope-appropriate?

**Context:** Prompt asks "match PR #772's test scale (10 androidTest, ~290+ vitest, pytest as needed)." The 290+ vitest figure refers to the broader web suite, not PR #772 specifically (which has 8). Bulk-mark scope justifies 7 new android cases + 7 new vitest cases.

**My pick: ship scope-appropriate (7+7), document the gap from "295" by citing actual PR #772 counts in the PR body.** Match the spirit of "thorough multi-target coverage," not a number that doesn't reflect PR #772 reality.

### Decision 4 — Confirm the BatchPreviewScreen detour skip?

**Context:** The new bulk-mark UI is a direct user action (no AI parse), so it would naturally bypass `BatchPreviewScreen`. But the user sees a destructive bulk write — should there be a preview-style confirmation?

- **A.** In-dialog summary line ("This will update 4 medications in slot 8am for today.") + Mark button. No separate preview screen.
- **B.** Route through `BatchPreviewScreen` even for direct invocation, render N rows. Heavy for 1 click → 5 rows.
- **C.** New "BulkMarkPreview" screen styled like `BatchPreviewScreen` but compressed to a one-line summary.

**My pick: A.** In-dialog summary is the bulk-action equivalent of BatchPreview, scoped appropriately to a direct user action.

---

## Recommendations summary

| Section | Verdict |
|---|---|
| Item 1 — Schema verification | ⚠️ **PROCEED WITH NOTED ASYMMETRY.** Web doc shape ≠ Android row shape — surfaced above; doesn't block ship but is a real architectural difference worth the user's awareness. |
| Item 2 — Android infra (3a/3b/3c) | ✅ **PROCEED AS WRITTEN** with **3b (fan-out)**. Zero schema changes. |
| Item 3 — Web infra (3a/3b/3c) | ✅ **PROCEED AS WRITTEN** with **3b (fan-out)** — pending Decision 1 on `writeBatch`. |
| Item 4 — UI invocation surfaces | ✅ **PROCEED AS WRITTEN** with TopAppBar (Android) + header-bar (web) entry. PR #744/#745 long-press preserved. |
| Item 5 — UX shape | ✅ **PROCEED AS WRITTEN** with **Option A** (single menu, scope picker). |
| Item 6 — Edge cases | ✅ **PROCEED AS WRITTEN** with: write-through (already-set), disable-button (empty), SKIPPED mirrors synthetic-skip dose loop. |
| Item 7 — PR shape | ✅ **PROCEED AS WRITTEN** with ~1,250 LOC, 4 commits, no backend. |

**No STOP-WRONG-PREMISE verdicts.** The audit-first pattern surfaced 4 architectural decision points (Decisions 1–4 above) that deserve user input but are not blockers in either direction. Defaults are recommended; alternatives are documented.

**Phase 2 readiness:** waiting on user response to Decisions 1–4 (or "use your picks" / "go" if they accept the recommendations as-is).
