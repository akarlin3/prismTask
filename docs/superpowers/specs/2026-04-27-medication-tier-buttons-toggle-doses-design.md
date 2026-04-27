# Medication tier buttons toggle the dose log

**Date:** 2026-04-27
**Touches:** `MedicationViewModel`, `MedicationScreen` (tier-button handler), `BulkMarkDialog` flow

## Problem

Each slot card on the Medication screen surfaces four tier buttons — `Skip · Ess · Rx · Done`. Today, clicking one writes a `medication_tier_states` row marked `USER_SET` but **leaves the dose log untouched**. The slot can read "Done" at the top while every individual med checkbox below stays empty, and the achieved-tier display becomes a fiction laid over an out-of-date dose log.

The same disconnect applies to `BulkMarkDialog`'s `STATE_CHANGE` mutations.

## Goal

Clicking a tier button should make the dose log match what the tier claims. The achieved-tier display then falls out of auto-compute (`MedicationTierComputer`), and the `USER_SET` tier-state row stops being a way to "lie" about the slot.

## Behavior

For a slot with medications partitioned into tiers `ESSENTIAL` / `PRESCRIPTION` / `COMPLETE`:

| User clicks | Dose log effect | Tier-state row |
|---|---|---|
| **Ess**  | Insert dose for every `ESSENTIAL` med not yet taken. Higher tiers untouched. | Auto-computed (`COMPUTED`) |
| **Rx**   | Insert dose for every `ESSENTIAL` + `PRESCRIPTION` med not yet taken. `COMPLETE` untouched. | Auto-computed |
| **Done** | Insert dose for every med in the slot not yet taken. | Auto-computed |
| **Skip** | Delete every real dose for the slot today. Then write a synthetic-skip dose per med so interval-mode reminders re-anchor. | `USER_SET = SKIPPED` (unchanged path) |

Already-taken meds at higher tiers than the clicked tier are **not** unchecked — the user may have manually ticked them; respect that.

### Click the currently-active tier

The active tier is now whatever auto-compute says. Clicking it is **idempotent** — re-runs the same insert pass, which is a no-op if everything is already logged. This replaces the old "tap active to clear `USER_SET`" affordance, which no longer has anything to clear.

Long-press on the active tier still opens the intended-time edit sheet (existing behavior).

### `BulkMarkDialog`

`MedicationViewModel.bulkMark(scope, slotId, tier)` follows the same rules: for `ESSENTIAL` / `PRESCRIPTION` / `COMPLETE` it logs doses across the scope (one slot or full day); for `SKIPPED` it deletes real doses and writes synthetic skips. The shared `batch_id` is preserved so 24h durable history undo still reverses the action atomically.

## Architecture

The inline tier buttons and `BulkMarkDialog` already share `MedicationViewModel.bulkMark(scope, slotId, tier)` for non-skip tiers (Q3 = apply everywhere). That stays the unified entry point; its body changes:

```
bulkMark(scope, slotId, tier)
   → for each (med, slot) target in scope:
        tier == SKIPPED → delete real doses + logSyntheticSkipDose(...)   // existing skip path
        tier != SKIPPED → if med.tier <= clickedTier && not taken → logDose(...)
   → refreshTierState(slot) for each touched slot   // writes COMPUTED row(s)
```

The inline `Skip` button still routes through `setSkippedForSlot`, which absorbs the new "delete real doses first" step. The `USER_SET=SKIPPED` upsert stays — auto-compute would also return `SKIPPED` once the doses are gone, but keeping the explicit `USER_SET` row preserves the user's intent if they later add a med to the slot mid-day.

The existing "click active `USER_SET` tier → clear override" branch in `MedicationScreen.onSelectTier` goes away. The active tier is auto-computed now; re-clicking it is idempotent.

### Batch infrastructure

The current `bulkMark` produces one `ProposedMutationResponse` per `(med, slot)` pair with `mutationType = STATE_CHANGE`. To make undo continue to work cleanly:

- Continue producing one mutation per `(med, slot)` pair under one `batch_id`.
- For non-skip tiers, switch the mutation to a dose-write shape (e.g. `BatchMutationType.STATE_CHANGE` with `proposed_new_values` describing the dose insert, OR a new `MARK_TAKEN` type if `STATE_CHANGE`'s reverse-handler can't unwind a dose insert).
- For skip tiers, the existing `SKIP` type already does the synthetic-skip + dose-clearing dance.

The exact mutation type chosen for "log doses up to tier T" depends on what `BatchOperationsRepository`'s reverse-handler can already undo. The implementation plan investigates this and picks the lightest-weight option (extend an existing type's payload vs. add a new type).

## Edge cases

- **Empty slot.** No meds linked → click is a no-op (matches today's UI which already disables the bulk-mark icon when zero targets exist; the inline tier buttons currently still respond, so this becomes a no-op path).
- **Slot has no `ESSENTIAL` meds and user clicks `Ess`.** Logs nothing, recomputes tier-state. Auto-compute may yield `SKIPPED` or — if all higher-tier meds were already manually taken — a higher tier. That's correct.
- **Manual checkbox afterward.** Toggling an individual med dose post-tier-click works exactly like today — `toggleDose` flips the row and `refreshTierState` recomputes.
- **Synthetic-skip rows from a previous skip.** Clicking `Done` after a skip needs to delete the synthetic skips before logging real doses (or the auto-compute walk would still see `SKIPPED` doses and ignore them — actually the auto-compute reads `markedTaken: Set<Long>` derived from real doses, so synthetic skips are filtered out at the dose-load layer; verify this in the implementation plan).
- **Cross-device.** `medication_doses` already syncs (post-PR #774). Bulk dose inserts ride that path.

## Testing

- Unit tests on `MedicationViewModel` for `applyTierToSlot` covering each tier × {empty slot, partial doses, all doses already taken, mixed-tier slot}.
- Update `MedicationViewModelTest` cases that asserted `STATE_CHANGE`-only mutations.
- Update `BulkMarkDialog` tests that asserted the dose log was unchanged after a non-skip bulk mark.
- A regression test: click `Done`, then toggle one checkbox off — slot tier should drop one rung and reflect the dose log.

## Out of scope

- The achieved-tier display logic itself (no change).
- Synthetic-skip / interval-mode reminder mechanics (unchanged for skip path).
- Migration: this is purely a write-path behavior change. Existing `USER_SET` rows in the wild are still respected by `slotTodayStates` and decay naturally as users interact with their slots tomorrow.
