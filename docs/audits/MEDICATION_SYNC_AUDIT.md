# Medication Sync — Phase 1 Audit

**Date:** 2026-04-29
**Scope sentence:** the user reports "meds aren't syncing fully between devices." Sweep every medication-family entity (`medication`, `medication_dose`, `medication_slot`, `medication_slot_override`, `medication_tier_state`, `medication_refill`, plus the `medication_medication_slots` junction) for push-side, pull-side, and reconciliation gaps that would drop user data on the floor.

## TL;DR

Three RED items and one YELLOW. All four describe **silent** failure modes — Room throws `SQLiteConstraintException` inside `pullCollection`'s `try/catch`, the row is dropped, and the next pull cycle dutifully re-fails on the same constraint. The doc-id never gets a `lastSyncedAt` stamp so the device keeps trying forever, but the user sees nothing arrive.

## Items

### 1. Tier-state pull has no natural-key dedup *(RED — PROCEED)*

`MedicationTierStateEntity` indices: `Index(value = ["medication_id", "log_date", "slot_id"], unique = true)` (`MedicationTierStateEntity.kt:43`).

`SyncService.pullRemoteChanges` for `medication_tier_states` (`SyncService.kt:2256-2300`) only checks `syncMetadataDao.getLocalId(cloudId, "medication_tier_state")`. When `localId == null` it inserts naïvely:

```kotlin
val newId = medicationTierStateDao.insert(state)
```

This is the **exact shape** of the medication-by-name bug already pinned by `medicationByName_dedupAcrossDevices` (`MedicationCrossDeviceConvergenceTest.kt:272-341`) — that test's docstring even calls out the silent-drop pattern: *"naive INSERT throws SQLiteConstraintException and the medication is silently dropped (P0 surfaced in Test 3 of Session 1 manual testing, 2026-04-27)."*

Repro shape: Device A and Device B independently log `(med=Lexapro, slot=Morning, date=2026-04-29)`. Both push their tier-state docs. On pull, the second device's row collides on the `(medication_id, log_date, slot_id)` UNIQUE index and is dropped — the achievement tier difference (Device A says `essential`, Device B says `prescription`) never converges, and last-write-wins doesn't apply because the row never makes it past `insert`.

Impact for a daily-pill-tracker user: **every cross-device tier change risks silent loss**. This matches the user's "not syncing fully" symptom.

Fix shape (mirrors lines 2099-2143's medication-by-name adoption): before insert, look up the existing row by `(medicationLocalId, logDate, slotLocalId)`; if found, last-write-wins update + adopt the `cloudId` into `sync_metadata`; otherwise insert fresh.

### 2. Slot-override pull has no natural-key dedup *(RED — PROCEED)*

`MedicationSlotOverrideEntity` indices: `Index(value = ["medication_id", "slot_id"], unique = true)` (`MedicationSlotOverrideEntity.kt:38`).

`SyncService.pullRemoteChanges` for `medication_slot_overrides` (`SyncService.kt:2210-2253`) — same naïve `insert` path, no `(medicationLocalId, slotLocalId)` lookup.

Repro: both devices set a per-slot drift override on the same `(med, slot)` pair before the first sync cycle. Pull drops the second one. User-visible: the override they set on the secondary device "disappears."

Lower frequency than tier-state (overrides are sticky configuration vs. daily activity logs) but **same code shape** — bundle the fix.

### 3. `BuiltInMedicationReconciler` makes silent local-only mutations *(RED — PROCEED)*

`BuiltInMedicationReconciler.mergeDuplicatesByName` (`BuiltInMedicationReconciler.kt:37-65`) runs **once after first cloud sync** to collapse duplicates that arose from independent v53→v54 migration on each device. The merge does:

```kotlin
medicationDoseDao.reassignMedicationId(oldId = loser.id, newId = keeper.id)  // raw UPDATE
medicationDao.deleteById(loser.id)                                            // raw DELETE
```

Neither call touches `SyncTracker`. `MedicationDoseDao.reassignMedicationId` (`MedicationDoseDao.kt:92-93`) is `@Query("UPDATE medication_doses SET medication_id = :newId WHERE medication_id = :oldId")` — pure SQL. Same for `deleteById`.

Result, after reconciliation lands locally:
- Local `medication_doses` rows are reattached to keeper's local id, but their `medicationCloudId` field on the cloud doc still points at loser's cloud doc. Push side won't re-emit because no `pending_action` row was written.
- Loser's `medications/<cloudId>` cloud doc is never deleted. It just sits there. Any third device joining later pulls both keeper and loser, and either:
  - re-creates the loser locally (since natural-key dedup exists on `medications.name`, line 2114 — so it adopts keeper, but binds keeper's local id to loser's cloud id, scrambling sync metadata), or
  - hits the same UNIQUE-by-name collision twice (one from each cloud doc) and partially fails on the second.

Fix shape: replace the raw DAO calls with `medicationRepository.delete(loser)` (which already calls `syncTracker.trackDelete`). For dose reassignment, after the reassign emit `syncTracker.trackUpdate(doseId, "medication_dose")` for each affected dose so the cloud doc's `medicationCloudId` gets rewritten on the next push.

### 4. Slot pull doesn't dedup by name *(YELLOW — PROCEED)*

`MedicationSlotEntity` indices: only `Index(value = ["cloud_id"], unique = true)` (`MedicationSlotEntity.kt:32-34`). `name` is **not** UNIQUE.

`SyncService.pullRemoteChanges` for `medication_slots` (`SyncService.kt:2068-2096`) inserts without checking for an existing same-named slot.

Symptom: Device A and Device B both create a "Morning" slot pre-sync. After first pull, each device has **two** "Morning" slots. The slot list UI shows the duplicate and the user has to manually merge.

Less severe than 1+2 (no exception, no data loss — both rows persist) but visible enough to count as "meds aren't syncing fully" from the user's perspective. Same fix shape: in `pullCollection("medication_slots")`, when `localId == null`, look up by `name` first; if a same-named local slot exists without a `cloud_id`, adopt it.

### 5. Junction-membership change propagation *(GREEN — STOP-no-work-needed)*

The `medication_medication_slots` junction is **not** a first-class synced entity — it's rebuilt on pull from the parent medication doc's `slotCloudIds` field (`MedicationSyncMapper.kt:21`, `SyncService.kt:2099-2173`). The contract: any change to a medication's slot membership must bump the parent medication's `updated_at` so the embedded list re-pushes.

Audit: every UI flow that mutates the junction wraps the change in a `medicationRepository.update()`:
- `MedicationViewModel.addMedication` — `medicationRepository.update(inserted)` after `replaceLinksForMedication` (`MedicationViewModel.kt:318-320`).
- `MedicationViewModel.updateMedication` — `medicationRepository.update(...)` before `replaceLinksForMedication` (`MedicationViewModel.kt:335`). Order doesn't matter; push reads current junction at push time (`SyncService.kt:1247`).

`MedicationSlotRepository.{addLink,removeLink,replaceLinksForMedication}` themselves do **not** bump the parent medication (`MedicationSlotRepository.kt:104-117`) — but `addLink` and `removeLink` have **no callers** in `app/src/main`. The standalone APIs are unused dead-code footguns. Worth tightening but no current sync bug.

### 6. `medication_refills` push/pull *(GREEN — STOP-no-work-needed)*

Refills go through `uploadRoomConfigFamily` (`SyncService.kt:546-552`) on push and `pullRoomConfigFamily` (`SyncService.kt:2464`) on pull — same Tier-2-config path that habit_template, project_template, etc. use. Per-entity push paths at `SyncService.kt:1313` and `:1475` cover incremental updates. No gaps.

### 7. Backfill coverage for v53→v54 migration *(GREEN — STOP-no-work-needed)*

`runMedicationsBackfillIfNeeded` and `runMedicationDosesBackfillIfNeeded` (`SyncService.kt:961-1034`) push existing rows that pre-date the cloud-id system. Slots/overrides/tier-states/refills don't need backfill: the migration runner (`MedicationMigrationRunner.kt`) doesn't create slot-system rows, and any post-migration write goes through `MedicationSlotRepository`'s `syncTracker`-instrumented methods.

## Improvement table — sorted by wall-clock-savings ÷ implementation-cost

| # | Item | Severity | Effort | Wall-clock savings | PR shape |
|---|---|---|---|---|---|
| 1 | Tier-state pull dedup by `(med, log_date, slot)` | RED | ~2h impl + repro test | High — daily logging on multi-device users | One PR: `fix/medication-tier-state-dedup` |
| 2 | Slot-override pull dedup by `(med, slot)` | RED | ~1h (mirror #1) | Medium — power-user feature | Bundle with #1: `fix/medication-natural-key-dedup` |
| 3 | Reconciler routes deletes/updates through `SyncTracker` | RED | ~2h + reconciler-then-push integration test | Medium-high — affects every multi-device first-sync user | Separate PR: `fix/builtin-medication-reconciler-sync` |
| 4 | Slot pull dedup by name | YELLOW | ~1h | Low-medium — visible doubling, no data loss | Bundle with #1+2 OR separate `fix/medication-slot-name-dedup` |

Recommended bundling: PR-A (#1 + #2 + #4 — all are "pull-side natural-key dedup" same code shape), PR-B (#3 — reconciler is a different file + different test pattern).

## Anti-patterns flagged but not fixed

- `MedicationSlotRepository.addLink` / `removeLink` are public APIs with **no callers** that don't bump the parent medication. Not a current bug, but a footgun for future code. Either delete them or add `@Deprecated("use replaceLinksForMedication via MedicationRepository.update")`. Not worth a PR until someone tries to call them.
- The medication doc's `slotCloudIds` field rebuilds the junction destructively on every pull (`SyncService.kt:2161-2168`). If a future feature lets a user link a slot from a flow that *doesn't* bump the medication, the link will be silently wiped on next pull. Document this contract in `MedicationSyncMapper.medicationToMap`'s KDoc — it's a load-bearing invariant.
- `medication_refills` and `medication_tier_states` have no cross-device convergence tests in `MedicationCrossDeviceConvergenceTest`. Adding `medicationTierState_dedupAcrossDevices` and `medicationSlotOverride_dedupAcrossDevices` would catch any regression of items #1+#2 going forward — these are the load-bearing repro tests for the fix PR (per the repro-first memory).
