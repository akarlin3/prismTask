# P0 sync constraint failures — fix-now audit

**Status:** Phase 1 complete — items 1–5 diagnosed across all three
checkpoints in a single pass per user direction ("continue through
checkpoints"). PR-E (CI infra: APK + AAB on every merge) added per user
request. Phase 2 fan-out pending PR-E AAB-destination confirmation.

**Trigger:** Phase F P0 from Test 3 of Session 1 manual testing
(2026‑04‑27, Apple Silicon AVDs, Mac). Two distinct constraint violations on
cross-device pull are silently masked as `pull.summary | status=warning` while
the parent operation reports `sync.completed | status=success`.

**Branch:** `audit/p0-sync-constraints` (off `origin/main` @ `2d56bb4f`).

---

## Premise refinements (read first)

The original prompt frames PR #840 as "shipped merge-on-write for tasks.ts and
habits.ts" with the fix shape being to "extend that template to medications."
Verification (`git show --stat d7aa1a7b`) shows that framing is incorrect:

- **PR #840 is web-only.** It touches `web/src/**/habits.ts` (and only that
  module), fixing two parity gaps named **H‑S2** and **H‑S4**:
  - **H‑S2:** web's `createHabit` (`habitCreateToDoc`) was hardcoding
    Android-only fields like `isBookable: false`, `trackBooking: false`, etc.
    on every web-side habit create, which silently flipped them back to false
    on the next Android edit. Fix: omit those fields entirely so web emits
    only what it owns.
  - **H‑S4:** web's `toggleCompletion` was not writing `completedDateLocal`
    on the Firestore doc. Fix: write the YYYY‑MM‑DD string the caller already
    computed via `useLogicalToday(startOfDayHour)` so completions stay on the
    correct logical day across DST/midnight.
- **PR #840 has no Android changes and does not address Room UPSERT vs
  INSERT** semantics on pull. The "merge-on-write template" the prompt
  references for medications is therefore not a portable template — both fixes
  needed for this P0 are net-new Android Kotlin work.
- **Item 2's correct fix shape** is *natural-key dedup before INSERT in the
  Android pull handler* (the same shape `habit_completions` already uses via
  `getByHabitAndDateLocal` at `SyncService.kt:1683`), not a port from PR #840.

This refinement does not invalidate the P0 — it just changes what the fix
looks like and rules out cross-platform contamination as a hypothesis.

---

## Item 1 — `habit_completions` FK constraint failure

### Premise verification

- **Entity FK spec** — `app/src/main/java/com/averycorp/prismtask/data/local/entity/HabitCompletionEntity.kt:9–18`:
  ```kotlin
  foreignKeys = [
      ForeignKey(
          entity = HabitEntity::class,
          parentColumns = ["id"],
          childColumns = ["habit_id"],
          onDelete = ForeignKey.CASCADE
      )
  ]
  ```
  Standard FK, `ON DELETE CASCADE`, **not deferrable** (Room/SQLite enforces
  immediately). Indexes on `habit_id`, `completed_date`, `completed_date_local`,
  `cloud_id` (unique).

- **Pull path** — `SyncService.kt:1669–1707` (the `pullCollection("habit_completions")`
  handler):
  ```kotlin
  val localId = syncMetadataDao.getLocalId(cloudId, "habit_completion")
  val habitCloudId = data["habitCloudId"] as? String
      ?: return@pullCollection false
  val habitLocalId = syncMetadataDao.getLocalId(habitCloudId, "habit")
      ?: return@pullCollection false
  if (localId == null) {
      val completion = SyncMapper.mapToHabitCompletion(...)
      val existingByNaturalKey = completion.completedDateLocal?.let {
          habitCompletionDao.getByHabitAndDateLocal(habitLocalId, it)
      }
      if (existingByNaturalKey != null) {
          syncMetadataDao.upsert(SyncMetadataEntity(localId = existingByNaturalKey.id, ...))
      } else {
          val newId = habitCompletionDao.insert(completion)   // ← throws SQLiteConstraintException
          ...
      }
  }
  ```
  The handler **does** guard against missing-parent-FK by returning `false`
  when `habitLocalId` is null. The exception in Test 3 logs is **not** that
  guard firing — it is the `habitCompletionDao.insert(completion)` line
  throwing.

- **Pull ordering** — `SyncService.kt:1516–1518` documents and enforces the
  pull order: `projects → tags → habits → tasks → task_completions →
  habit_completions → habit_logs → milestones → task_templates`. So habits
  are pulled **before** habit_completions, and the dependency-first ordering
  rules out hypothesis (a) ("unordered pull, child arrives before parent")
  from the prompt.

- **`pullCollection` exception envelope** — `SyncService.kt:2486–2504`. When
  the handler throws, `skipped++`, the error is logged via
  `logger.error(operation = "pull.apply", entity = name, id = doc.id,
  throwable = e)`, and the exception is recorded to Crashlytics. The whole
  pull then continues for the next doc. **No retry, no FK promotion to
  deferrable, no second-pass replay.**

### Findings

The Test 3 logcat shape (`pull.apply | habit_completions=… | status=failed`
with `FOREIGN KEY constraint failed`) means `habitLocalId` was **non-null**
at the lookup, but no row with that local id existed in the `habits` table at
INSERT time. That is a **`sync_metadata` ↔ `habits` divergence** — metadata
says "cloud_id C maps to local_id N" while `habits.id = N` is gone.

Smoking gun — `HabitRepository.kt:102–108`:

```kotlin
suspend fun deleteHabit(id: Long) {
    medicationReminderScheduler.cancelAll(id)
    medicationReminderScheduler.cancelFollowUp(id)
    syncTracker.trackDelete(id, "habit")     // queues delete for push
    habitDao.deleteById(id)                   // deletes the habit row NOW
    widgetUpdateManager.updateHabitWidgets()
}
```

`SyncTracker.trackDelete` (`SyncTracker.kt:56–78`) only sets
`pending_action='delete'` on the existing `sync_metadata` row when the row
already has a non-empty `cloud_id`; **the metadata row is left in place**
until the push-delete actually lands at `SyncService.kt:1505–1510`
(`pushDelete`, which calls `syncMetadataDao.delete(meta.localId,
meta.entityType)` only after the Firestore delete completes).

Between `habitDao.deleteById(id)` and the eventual successful `pushDelete`:

1. `habits.id = N` is gone.
2. `sync_metadata` still has `(cloud_id = C, local_id = N, entity_type =
   "habit", pending_action = "delete")`.
3. Any pull that arrives in this window (Firebase pull, BackendSync apply,
   real-time listener) for a `habit_completion` whose `habitCloudId = C`
   will resolve `habitLocalId = N` from sync_metadata, then attempt
   `habitCompletionDao.insert(completion)` with `habit_id = N`, hitting
   the FK CASCADE constraint and throwing.

**The same shape exists on the backend-sync delete path** —
`BackendSyncService.kt:509–517`:

```kotlin
private suspend fun applyHabitChanges(changes: List<SyncChange>): Int {
    var applied = 0
    for (change in changes) {
        val clientId = change.entityId
        if (change.operation == "delete") {
            habitDao.deleteById(clientId)   // no syncMetadata cleanup
            applied++
            continue
        }
        ...
```

Backend's apply-delete deletes the habit row **without touching
sync_metadata at all**, even when the delete was originated by another device.
The metadata row will only be reaped by the next `pushDelete` cycle, *if*
this device also deletes the same habit, which it never does on the receive
side.

`BuiltInHabitReconciler.kt:120–121` is the **only** code path that gets the
order right (`syncMetadataDao.delete(loser.id, "habit")` *before*
`habitDao.deleteById(loser.id)`). Every other habit-deletion path leaks
metadata.

### Root cause (single sentence)

Habit deletion paths in `HabitRepository.deleteHabit` and
`BackendSyncService.applyHabitChanges` delete the `habits` row without
removing the corresponding `sync_metadata` row, leaving stale `cloud_id →
local_id` mappings that subsequent `habit_completions` pulls trust and
INSERT against — triggering `FOREIGN KEY constraint failed`.

### Risk classification: **RED**

- High frequency: triggered by any habit delete on any device followed by a
  pull-side completion arrival before the push-delete drains.
- Silent: hidden inside `pull.summary | status=warning` (item 4).
- Data loss: the completion is **not retried** (`pullCollection` does not
  enqueue a retry). It is permanently dropped on this device unless the
  Firestore doc is touched again to bump its `updatedAt` and trigger another
  pull.
- Affects flagship feature (habit streaks): a missing completion silently
  corrupts streak math.

### Recommendation: **PROCEED**

Fix shape (no code in Phase 1):

1. **Pair `habitDao.deleteById` with `syncMetadataDao.delete` everywhere.**
   At minimum: `HabitRepository.deleteHabit`,
   `BackendSyncService.applyHabitChanges` (delete branch), and any
   `processRemoteDeletions` Firebase apply-delete path. The right primitive
   is "delete the entity row + the metadata row in the same transaction"
   (extract a `HabitRepository.deleteHabitAndMetadata` or push the
   metadata-delete into a Room `@Transaction` on the DAO).
2. **Defensive guard in pull handlers.** Change the
   `habitCompletionDao.insert(completion)` site at `SyncService.kt:1695` to
   verify the parent row actually exists (`habitDao.getHabitByIdOnce(habitLocalId)
   != null`) before inserting; on absent parent, treat the metadata row as
   stale, delete it, and skip the completion (or push-delete the orphan
   completion from Firestore so it doesn't keep trying).
3. **Item 3** (sweep) determines whether (1) needs to be repeated for every
   parent-of-FK entity, or whether the Phase 2 PR can ship a single
   "delete-with-metadata" wrapper used everywhere.

---

## Item 2 — `medications` UNIQUE constraint failure

### Premise verification

- **Entity UNIQUE constraints** — `MedicationEntity.kt:24–30`:
  ```kotlin
  @Entity(
      tableName = "medications",
      indices = [
          Index(value = ["cloud_id"], unique = true),
          Index(value = ["name"], unique = true)
      ]
  )
  ```
  `cloud_id` unique (sync-managed, expected) **and `name` unique** (the
  business-key constraint that's blowing up). `name` is the only natural-key
  UNIQUE column — the rest of the entity has no other UNIQUE constraints.

- **Pull path** — `SyncService.kt:1984–2028` (the `pullCollection("medications")`
  handler):
  ```kotlin
  val localId = syncMetadataDao.getLocalId(cloudId, "medication")
  val resolvedLocalId = if (localId == null) {
      val med = MedicationSyncMapper.mapToMedication(data, cloudId = cloudId)
      val newId = medicationDao.insert(med)   // ← throws on UNIQUE(name) collision
      syncMetadataDao.upsert(...)
      newId
  } else {
      // last-write-wins update against existing local row
      ...
      localId
  }
  ```
  When `localId == null` (cloud_id is new to this device), the path is a
  **plain INSERT with no natural-key dedup**. Compare with the
  habit_completions handler at `SyncService.kt:1682–1693`, which checks
  `getByHabitAndDateLocal` first and adopts the existing row if found.
  Medications has no equivalent check.

- **PR #840 scope** — `git show --stat d7aa1a7b` shows changes only under
  `web/src/.../habits.ts`. Confirmed unrelated to this Android pull path.

### Findings

`pullCollection("medications")`'s `localId == null` branch unconditionally
calls `medicationDao.insert(med)` with `cloud_id` set but `name` taken from
the Firestore doc verbatim. If the local `medications` table already has a
row with the same `name` (regardless of `cloud_id`), `INSERT` violates
`Index(value = ["name"], unique = true)` and throws
`SQLiteConstraintException: UNIQUE constraint failed: medications.name`.

This is reachable in two routine scenarios:

1. **Both devices ran v53→v54 backfill before signing in.** Per CLAUDE.md
   v53→v54 description: "backfilled from `self_care_steps WHERE
   routine_type='medication'` with duplicate-name collapse." Both devices'
   migrations produce locally-rooted medications (`cloud_id = null`) with
   the same names. When sync starts, whichever device pushes first stamps
   its rows with cloud_ids; the other device pulls those cloud_ids,
   `getLocalId(cloudId, "medication") == null`, and the INSERT collides on
   `name` against its own locally-migrated row.
2. **Manual cross-device duplicate.** User creates "Vitamin D" on each
   device while offline / not signed in; same shape on first sync.

The existing `MedicationCrossDeviceConvergenceTest` covers three cases —
`medicationLastWriteWins_remoteUpdateOverwritesLocal`,
`medicationDoseFkResolvesAcrossDevices`,
`medicationSlotJunctionRebuildAfterRemoteSlotAdd`. **None of them assert the
"same name, no shared cloud_id" natural-key collision case.** That's the
test gap that allowed this P0 to ship.

### Root cause (single sentence)

`pullCollection("medications")` does an `INSERT` on the `localId == null`
branch without first checking for a same-name local row, so any pull whose
`cloud_id` is new but whose `name` collides with an existing local
medication throws `UNIQUE constraint failed: medications.name`.

### Risk classification: **RED**

- High frequency: triggered for every user who upgraded to ≥v53→v54 on
  ≥2 devices and then signed in, or who manually created same-name meds
  cross-device. Beta testers signing in across multiple devices will hit
  this on first sync.
- Silent: hidden inside `pull.summary | status=warning` (item 4).
- Data loss: the medication is permanently dropped on the receiving device
  unless the Firestore doc is updated again. Doses pulled later that
  reference this medication's cloud_id will then **also** fail FK
  (`SyncService.kt:2032–2056`), cascading silent failure into
  `medication_doses`.
- Affects flagship feature (medication tracking).

### Recommendation: **PROCEED**

Fix shape (no code in Phase 1):

1. **Add natural-key dedup in the `localId == null` branch** of
   `pullCollection("medications")` at `SyncService.kt:1984`. Pseudocode:
   ```
   if (localId == null) {
       val incoming = MedicationSyncMapper.mapToMedication(data, cloudId)
       val existingByName = medicationDao.getByNameOnce(incoming.name)
       if (existingByName != null) {
           // Adopt: bind incoming cloudId to existing local row;
           // apply last-write-wins against incoming.updatedAt.
           syncMetadataDao.upsert(SyncMetadataEntity(localId = existingByName.id,
               entityType = "medication", cloudId = cloudId, ...))
           if (incoming.updatedAt > existingByName.updatedAt) {
               medicationDao.update(incoming.copy(id = existingByName.id))
           }
           resolvedLocalId = existingByName.id
       } else {
           val newId = medicationDao.insert(incoming)
           ...
       }
   }
   ```
2. **Ensure `MedicationDao` exposes `getByNameOnce(name): MedicationEntity?`**
   (likely already exists; verify in Phase 2).
3. **Add regression-gate test** to `MedicationCrossDeviceConvergenceTest`
   following the PR #798 / convergence-test pattern: `medicationByName_dedupAcrossDevices`,
   asserting that after `pullCollection` runs with a same-name remote doc
   the local table has exactly one row per name and `sync_metadata` points
   to it. Per memory `feedback_firestore_doc_iteration_order.md`, assert
   *convergence shape* (row counts, name uniqueness, sync_metadata mapping
   exists), **not** which `cloud_id` "wins" the dedup.
4. **Item 3** (sweep) determines whether other entities have the same
   shape (UNIQUE business-key column with no pull-time natural-key dedup).

---

## Item 3 — Sweep all Room entities for similar shape

### Methodology

- Listed every entity file under `data/local/entity/` (42 entities).
- Greped for `unique = true` (UNIQUE indexes other than the sync-managed
  `cloud_id`) and `ForeignKey(` (Room FK relations).
- Cross-referenced each pull-side handler in
  `SyncService.pullRemoteChanges()` and the unified
  `pullRoomConfigFamily` helper at `SyncService.kt:2516–2543`.
- For each parent-of-FK entity, traced the deletion paths in
  `data/repository/*Repository.kt` and the receive side in
  `BackendSyncService.applyXxxChanges`.

### 3a — Natural-key UNIQUE collision shape (item 2 generalized)

Entities with at least one non-`cloud_id` UNIQUE index. These are at risk of
the same `SQLiteConstraintException: UNIQUE constraint failed: <table>.<col>`
shape as medications when both devices independently created a row with the
same business-key value before sync.

| Entity | UNIQUE column(s) | Pull path | Has natural-key dedup? | Risk |
|---|---|---|---|---|
| `medications` | `name` | `pullCollection("medications")` (`SyncService.kt:1984`) | ❌ plain INSERT | **RED** — known P0 |
| `medication_refills` | `medication_name` | `pullRoomConfigFamily("medication_refills", …)` (`SyncService.kt:2281`) | ❌ helper does plain INSERT | **RED** — same migration-backfill shape (each device runs v53→v54 locally and creates its own refill rows) |
| `nlp_shortcuts` | `trigger` | `pullRoomConfigFamily("nlp_shortcuts", …)` (`SyncService.kt:2196`) | ❌ helper does plain INSERT | **YELLOW** — only built-in shortcuts collide; user-created triggers are unique by user intent |
| `check_in_logs` | `date` | `pullRoomConfigFamily("check_in_logs", …)` (`SyncService.kt:2253`) | ❌ helper does plain INSERT | **YELLOW** — only collides if the same calendar day is logged on both devices offline |
| `mood_energy_logs` | `(date, time_of_day)` | `pullRoomConfigFamily("mood_energy_logs", …)` (`SyncService.kt:2267`) | ❌ helper does plain INSERT | **YELLOW** — same shape, narrower (must match both date and time_of_day) |
| `weekly_reviews` | `week_start_date` | `pullRoomConfigFamily("weekly_reviews", …)` (`SyncService.kt:2295`) | ❌ helper does plain INSERT | **YELLOW** — weekly cadence, low frequency |
| `daily_essential_slot_completions` | `(date, slot_key)` | `pullRoomConfigFamily("daily_essential_slot_completions", …)` (`SyncService.kt:2309`) | ❌ helper does plain INSERT | **YELLOW** — same shape, daily cadence |
| `leisure_logs` | `date` | `pullCollection("leisure_logs")` (`SyncService.kt:1850`) | ❌ plain INSERT | **YELLOW** |
| `study_logs` | `date` | `pullCollection("study_logs")` (`SyncService.kt:2410`) | ❌ plain INSERT | **YELLOW** |
| `course_completions` | `(date, course_id)` | `pullCollection("course_completions")` (`SyncService.kt:1820`) | ❌ plain INSERT | **YELLOW** |
| `self_care_logs` | `(routine_type, date)` | `pullCollection("self_care_logs")` (`SyncService.kt:1924`) | ❌ plain INSERT | **YELLOW** |
| `medication_slot_overrides` | `(medication_id, slot_id)` | `pullCollection("medication_slot_overrides")` (`SyncService.kt:2063`) | ❌ plain INSERT | **YELLOW** — each device's overrides usually point to its own local slot ids; unlikely collision unless same slot synced first |
| `medication_tier_states` | `(medication_id, log_date, slot_id)` | `pullCollection("medication_tier_states")` (`SyncService.kt:2108`) | ❌ plain INSERT | **YELLOW** — same shape, narrow composite key |

**13 entities at risk.** `medications` and `medication_refills` are the two
RED entries because the v53→v54 migration creates locally-rooted rows on
every upgrading device, guaranteeing a same-name collision when the second
device pulls.

`habit_completions` is the only entity with a working natural-key dedup
(`getByHabitAndDateLocal` at `SyncService.kt:1683`) — that's the model.
Every YELLOW/RED entry should adopt the same pattern at its pull-handler
level.

### 3b — Foreign-key stale-sync_metadata shape (item 1 generalized)

Entities that are children in a `ForeignKey` declaration. The risk on each
child entity is governed by whether the **parent** entity has a deletion
path that fails to clean up `sync_metadata` — because that is what causes
the lookup to return a stale local id and the child INSERT to violate FK.

Parent-side audit (deletion paths that mark `sync_metadata.pending_action='delete'`
without removing the row):

| Parent entity | Deletion path | Cleans `sync_metadata`? | Children that pull and resolve via `sync_metadata` |
|---|---|---|---|
| `habits` | `HabitRepository.deleteHabit` (`HabitRepository.kt:102–108`) | ❌ — only `syncTracker.trackDelete` (sets `pending_action`, leaves row) | `habit_completions` (**known P0**), `habit_logs` |
| `habits` | `BackendSyncService.applyHabitChanges` (`BackendSyncService.kt:514`) | ❌ — `habitDao.deleteById` only, no metadata touch at all | same |
| `tasks` | `TaskRepository.deleteTask` (need to verify same shape — see note) | likely ❌ same shape | `attachments`, `focus_release_logs`, `calendar_sync`, `task_tag_cross_ref`, `task_completions`, child `tasks` (subtasks) |
| `projects` | `ProjectRepository.deleteProject` (`ProjectRepository.kt:68–71`) | ❌ — only `syncTracker.trackDelete` then `projectDao.delete` | `tasks`, `milestones`, `task_templates`, `task_completions.project_id` |
| `courses` | `SchoolworkRepository.deleteCourse` (`SchoolworkRepository.kt:57–60`) | ❌ — `syncTracker.trackDelete` then `dao.deleteCourse` | `assignments`, `course_completions`, `study_logs.course_pick` |
| `assignments` | `SchoolworkRepository.deleteAssignment` (`SchoolworkRepository.kt:84–87`) | ❌ same | `study_logs.assignment_pick` |
| `medications` | (no explicit `MedicationRepository.deleteMedication` found in this pass — verify in Phase 2; archive-only deletions reduce risk) | unknown — Phase 2 verifies | `medication_doses`, `medication_slot_overrides`, `medication_tier_states`, `medication_medication_slots` |
| `medication_slots` | `MedicationSlotRepository.deleteSlot` (`MedicationSlotRepository.kt:77–80`) | ❌ — `slotDao.delete` then `syncTracker.trackDelete` | `medication_slot_overrides`, `medication_tier_states`, `medication_medication_slots` |
| `tags` | (verify) | likely ❌ same shape | `task_tag_cross_ref` |

**Only correct deletion path** in the codebase:
`BuiltInHabitReconciler.kt:120–121` (deletes `sync_metadata` *before* the
entity row).

**Risk classification per parent:**

- **RED** — `habits` (Test 3 confirmed) and `projects`/`tasks` (large
  fan-in: many child entities pulled cross-device, deletions are routine).
- **YELLOW** — `courses`/`assignments`/`medication_slots`/`tags` —
  same shape, lower frequency.
- **GREEN** — none. Every parent-of-FK entity that deletes through
  `syncTracker.trackDelete` carries the bug.

### 3c — `pullCollection` exception envelope (item 4 prep)

`SyncService.kt:2479–2509`: every handler runs inside a try/catch that
counts exceptions as `skipped++` and logs `pull.apply | status=failed` with
the throwable. Exceptions and "clean skips" (handler returned `false` for
documented reasons like missing-parent-FK) are **indistinguishable in the
return value**. `pull.summary`'s `if (skipped > 0)` branch then lumps both
into a single `status=warning`. Item 4 covers the recommendation.

### Item 3 — Risk summary

- **2 RED entities for UNIQUE-collision shape** (`medications`, `medication_refills`).
- **2 RED + 4 YELLOW parent entities for FK-staleness shape**
  (`habits`/`projects`/`tasks` RED; `courses`/`assignments`/
  `medication_slots`/`tags` YELLOW).
- **All 12 entities pulled via `pullRoomConfigFamily` share the same
  no-natural-key-dedup behavior**; 7 of them have non-`cloud_id` UNIQUE
  indexes that can collide. The fix shape can be one helper change applied
  to the helper rather than 7 individual call-site fixes.
- The pull-side defensive guard for FK lookups (item 1's recommendation #2)
  applies to every child handler — also a single-shape fix that can be
  rolled out across multiple call sites in one pass.

### Item 3 — Recommendation: **PROCEED**

Two systemic fix patterns emerge:

1. **`pullRoomConfigFamily.naturalKeyLookup` parameter** — the helper at
   `SyncService.kt:2516–2543` can take an optional
   `naturalKeyLookup: suspend (Map<String, Any?>) -> Long?` callback.
   When supplied, the `localId == null` branch first runs the lookup to
   adopt an existing local row by business key. Same shape as the inline
   habit_completions natural-key dedup at `SyncService.kt:1682–1693`.
   This single change fixes 7 of the 13 UNIQUE-collision entities at once.
2. **Delete-with-metadata helper** — extract a
   `SyncTracker.deleteEntityAndMetadata(localId, entityType, deleter:
   suspend (Long) -> Unit)` (or an equivalent Room `@Transaction` helper)
   that pairs `syncMetadataDao.delete` with the entity-level delete in
   one transaction. Migrate all 8 parent-entity deletion paths in
   repositories AND the `BackendSyncService.applyXxxChanges` delete
   branches to use it. Same shape as `BuiltInHabitReconciler.kt:120–121`.

Both patterns generalize the diagnoses in items 1 and 2 — Phase 2 doesn't
need 13 separate PRs, it needs ~3 systemic-fix PRs.

---

## Item 4 — Silent-failure rule violation

### Premise verification

- **`pull.summary` status decision** — `SyncService.kt:2456–2470`:
  ```kotlin
  if (skipped > 0) {
      logger.warn(operation = "pull.summary", entity = "all",
          status = "warning",
          detail = "applied=$applied skipped=$skipped — check pull.apply status=failed logs for details")
  } else {
      logger.info(operation = "pull.summary", entity = "all",
          status = "success", detail = "applied=$applied skipped=0")
  }
  ```
- **`sync.completed` status decision** — `SyncStateRepository.kt:97–122`
  (called from `SyncService.fullSync` at `SyncService.kt:2591`):
  ```kotlin
  syncStateRepository.markSyncCompleted(
      source = SOURCE_FIREBASE,
      success = true,                      // ← unconditionally true
      durationMs = ...,
      pushed = pushed, pulled = pulled
  )
  ```
  `success = true` is hardcoded as long as `pullRemoteChanges()` returned
  normally. The fact that `pullRemoteChanges` returned a non-zero `skipped`
  count is **not propagated up** — it's not part of `markSyncCompleted`'s
  signature.
- **`pullCollection` exception envelope** — `SyncService.kt:2479–2507` lumps
  exception-skips (the `catch` branch) and clean-skips (handler returned
  `false`) into a single `skipped++`. They are visible separately only via
  the per-doc `pull.apply | status=failed` lines in logcat — never
  aggregated.

### Findings

The user-visible state from the Test 3 trace is fully aligned with the
silent-failure-rule violation flagged in the prompt:

1. `pullCollection` catches `SQLiteConstraintException` (permanent data
   loss for that doc) and clean-skips (transient, will retry next pull) —
   both increment `skipped`.
2. `pull.summary` emits `status=warning` for any non-zero `skipped`.
   Because clean-skips are routine (e.g. completion arriving before its
   parent habit on first-ever pull is a normal transient state), tooling
   that watches for `status=warning` is implicitly tuned to ignore it —
   masking real exceptions.
3. `sync.completed` is unconditionally `status=success`. The user sees
   "Sync complete" UI feedback while the data they expected isn't there.

The `applyXxxChanges` paths in `BackendSyncService` have the same shape: the
method counts `applied` but does not surface FK or constraint exceptions to
the caller; the high-level sync result still reports success.

### Root cause

`pullCollection` collapses two semantically different outcomes ("transient
clean-skip, will retry" vs "exception, permanent loss") into one
`skipped++` counter. `markSyncCompleted` is called with an unconditional
`success = true` regardless of the constraint-exception count. There is no
in-app surface for the user that constraint-violation skips happened.

### Risk classification: **RED**

- All RED issues from items 1–3 are *invisible to the user* because of this
  collapse. Item 4 is the multiplier that turned a fixable cross-device sync
  bug into a silent-data-loss tester-confidence killer.
- Tooling and dashboards that filter on `sync.completed | status=failed`
  miss every constraint-violation event.

### Recommendation: **PROCEED with option (b)** (with (a) as a fallback)

The prompt offered two options; item 3's findings show option (a) (promote
all skips to error) would burn the receiver: cross-device pulls routinely
have transient clean-skips for legitimate reasons (parent not yet pulled in
this pass, will retry), and forcing every cross-device sync to flag
`status=error` would be both noisy and wrong. Option (b) is the right
call.

Fix shape (no code in Phase 1):

1. **Differentiate skip kinds in `PullResult`.** Replace
   `data class PullResult(applied: Int, skipped: Int)` at
   `SyncService.kt:2509` with `(applied: Int, skippedTransient: Int,
   skippedPermanent: Int)`, or equivalent. The catch branch in
   `pullCollection` (`SyncService.kt:2490–2504`) sets the permanent counter;
   the `false` return from the handler sets the transient counter.
2. **Promote `pull.summary` status to `error` only when
   `skippedPermanent > 0`.** `skippedTransient > 0` stays at `warning`.
3. **Plumb `skippedPermanent` into `markSyncCompleted`.** Add an optional
   `permanentlyFailed: Int = 0` to its signature; when non-zero, log
   `sync.completed | status=success_with_data_loss` (or similar) — and
   bump a user-visible counter the in-app sync indicator (`SyncIndicatorViewModel`,
   already present in `ui/components/sync/`) reads from. Alternatively
   set `success = false` if the team prefers the existing two-state UI.
4. **Crashlytics-record permanent skips** (already done at
   `SyncService.kt:2499–2503`) — keep as-is; this PR adds the in-app
   surface that the user sees, not the dev-side surface that already
   exists.
5. **Drop the `recordException` call's `try/catch` around it** —
   Crashlytics already handles its own failures and the silent catch
   here masks debugging signals. (Optional polish.)

The fallback (option (a) — promote everything to error) should be reserved
for the case where the team decides item 3's full sweep is unwanted and
this PR ships standalone — in that case, every skip is a red flag worth
escalating because the underlying handlers haven't been fixed yet.

---

## Item 5 — Phase 2 fan-out PR proposal

Items 1–4's diagnoses converge on **three systemic-fix PRs and one targeted
test backfill**, not 13–17 micro-PRs. Each PR scoped, regression-gated, and
verified against Test 3.

### PR-B (ships first) — `fix/p0-sync-medications-name-dedup`

**Smallest scope; fixes the highest-impact Test 3 symptom.**

- **Branch:** `fix/p0-sync-medications-name-dedup`
- **Files touched** (~80 LOC delta):
  - `data/local/dao/MedicationDao.kt` — add `getByNameOnce(name: String): MedicationEntity?` if not already present.
  - `data/remote/SyncService.kt` — modify the `localId == null` branch of
    `pullCollection("medications")` at lines 1984–2008 to call
    `getByNameOnce` before INSERT; on hit, adopt local row + bind cloud_id.
  - `androidTest/.../sync/scenarios/MedicationCrossDeviceConvergenceTest.kt`
    — add `medicationByName_dedupAcrossDevices` regression test.
- **Bundles with:** none (standalone). Per memory
  `feedback_audit_drive_by_migration_fixes` (drive-by fix shape), keep this
  PR's diff tight to make the regression-gate test the visible artifact.
- **Verification:** re-run Test 3 from Session 1 after merge. Same Mac
  Apple-Silicon AVD pair; pre-populate medication on Emu-A; sign in on
  Emu-B which already has a same-name local medication; assert no
  `pull.apply | medications=… | status=failed` line in logcat and assert
  exactly one local medication row by name on each device.
- **Test pattern:** convergence-shape only (per memory
  `feedback_firestore_doc_iteration_order` — never assert which `cloud_id`
  "wins", because Firestore doc iteration order flips between runs).

### PR-A (ships second) — `fix/p0-sync-delete-metadata-cleanup`

**Generalizes item 1; fixes habit_completions FK + 7 other parent entities
in one shape change.**

- **Branch:** `fix/p0-sync-delete-metadata-cleanup`
- **Files touched** (~250 LOC delta):
  - `data/remote/SyncTracker.kt` — add
    `suspend fun deleteEntityAndMetadata(localId: Long, entityType: String, deleter: suspend (Long) -> Unit)`
    that wraps the entity-delete and the metadata-delete in a single
    `withTransaction` block. Or push this responsibility into the DAOs
    behind `@Transaction`.
  - `data/repository/HabitRepository.kt:102–108` — `deleteHabit` migrates
    to the new helper.
  - `data/repository/ProjectRepository.kt:68–71` — same.
  - `data/repository/SchoolworkRepository.kt:57–87` — `deleteCourse`,
    `deleteAssignment` migrate.
  - `data/repository/MedicationSlotRepository.kt:77–80` —
    `deleteSlot` migrates.
  - `data/repository/AttachmentRepository.kt:64–70` — migrate.
  - `data/repository/HabitRepository.kt:299–302` — `deleteLog` migrates.
  - `data/repository/TagRepository.kt`, `TaskRepository.kt` — migrate
    after Phase 2 verification of their current shape.
  - `data/repository/MedicationRepository.kt` — verify whether a delete
    path exists; migrate if so.
  - `data/remote/sync/BackendSyncService.kt:509–517` (`applyHabitChanges`
    delete branch) — call `syncMetadataDao.delete` before
    `habitDao.deleteById`. Repeat for every other `applyXxxChanges`
    delete branch in the same file (`applyTaskChanges`,
    `applyProjectChanges`, etc.).
  - `data/remote/SyncService.kt:1695` — defensive guard at the
    `habitCompletionDao.insert` site:
    `habitDao.getHabitByIdOnce(habitLocalId) ?: return false` — treats
    missing parent as transient skip rather than letting Room throw.
  - **Test:** add `androidTest`
    `Test_DeleteParentRow_DoesNotLeakSyncMetadata` covering at minimum
    habits + projects + medication_slots; assert that after `deleteHabit`,
    `syncMetadataDao.getLocalId(cloudId, "habit")` returns null
    immediately, and a subsequent inbound habit_completion for the same
    cloud_id is treated as transient skip rather than thrown.
- **Bundles with:** none — distinct file scope from PR-B; per memory
  `feedback_use_worktrees_for_features` (worktree per feature) and the
  bundling rule, the helper-extraction shape doesn't overlap with the
  medication dedup shape.
- **Verification:** re-run Test 3 (habit_completions arm). Assert no
  `pull.apply | habit_completions=… | status=failed | FOREIGN KEY`. Add a
  manual stress-case: delete a habit on Emu-A while offline, log a
  completion on Emu-B for the same habit, sync both → Emu-A's pull does
  not throw FK; Emu-B's push observes the delete and tombstones.

### PR-C (ships third, optional same-cycle) — `fix/p0-sync-room-config-natural-key-dedup`

**Generalizes item 2's shape to the 7 config-family entities pulled via
`pullRoomConfigFamily`.**

- **Branch:** `fix/p0-sync-room-config-natural-key-dedup`
- **Files touched** (~180 LOC delta):
  - `data/remote/SyncService.kt:2516–2543` — extend `pullRoomConfigFamily`
    with an optional
    `naturalKeyLookup: (suspend (Map<String, Any?>) -> Long?)? = null`
    parameter. When supplied, the `localId == null` branch runs the
    lookup before INSERT and adopts the existing row.
  - `data/remote/SyncService.kt:2196` (`pullRoomConfigFamily("nlp_shortcuts", …)`)
    — pass `naturalKeyLookup = { data -> nlpShortcutDao.getByTriggerOnce(data["trigger"]) }`.
  - Repeat for the 6 remaining call sites with non-`cloud_id` UNIQUE
    indexes: `check_in_logs`, `mood_energy_logs`, `medication_refills`,
    `weekly_reviews`, `daily_essential_slot_completions`, plus any new
    config family added since the audit was written.
  - DAO additions: each affected DAO needs a `getByNaturalKeyOnce`
    counterpart matching its UNIQUE composite (e.g.
    `MoodEnergyLogDao.getByDateAndTimeOfDayOnce(date: Long, timeOfDay:
    String)`).
  - Test: parametrized androidTest in `sync/scenarios/` per affected
    family, asserting same-natural-key rows converge to one row.
- **Bundles with:** PR-B ships its medications-only dedup; PR-C is the
  generalization. Two ways to play it:
  - **Option C1 (recommended):** Ship PR-C as a separate PR after PR-B
    soak (≥1 day). Smaller diffs reviewed individually. Per memory
    `feedback_audit_drive_by_migration_fixes`, keeping the medications
    fix and the helper-generalization fix in distinct PRs makes future
    `git log -p -S` grep work correctly.
  - **Option C2:** Bundle into PR-B if review bandwidth allows. Diff
    grows by ~180 LOC.
  - **Recommendation: C1.**
- **Verification:** for each of the 7 entities, add a manual cross-device
  smoke. The 4 RED-leaning ones (`medication_refills` especially) get
  androidTest coverage; the YELLOW-leaning daily-log entities can ship
  with manual smoke only and a follow-up regression-gate test in G.0 if
  bandwidth's tight.

### PR-D (ships last) — `fix/p0-sync-pull-summary-error-promotion`

**Item 4 fix — differentiates transient vs permanent skips and surfaces
permanent skips at sync.completed and in-app.**

- **Branch:** `fix/p0-sync-pull-summary-error-promotion`
- **Files touched** (~120 LOC delta):
  - `data/remote/SyncService.kt:2479–2509` — split `PullResult` into
    `(applied, skippedTransient, skippedPermanent)`; route the catch
    branch to `skippedPermanent`, `false`-return to `skippedTransient`.
  - `data/remote/SyncService.kt:2456–2470` (the `pull.summary` block) —
    promote to `status=error` only when `skippedPermanent > 0`.
  - `data/remote/sync/SyncStateRepository.kt:86–122` — add
    `permanentlyFailed: Int = 0` to `markSyncCompleted`. When non-zero,
    flip log status to `success_with_data_loss` (or `success = false`
    per team preference). Plumb a `lastDataLossAt: StateFlow<Long?>` for
    the indicator UI.
  - `ui/components/sync/SyncIndicatorViewModel.kt` — surface a
    user-visible "Sync complete with errors — tap for details" state.
  - `data/remote/sync/BackendSyncService.kt` — same shape applied to its
    `applyXxxChanges` aggregator.
- **Bundles with:** none — distinct from A/B/C (this one is a
  cross-cutting plumbing change, not a per-entity fix).
- **Order constraint:** PR-D **must** ship after PR-A, PR-B, PR-C (or
  whichever subset is approved). If PR-D ships first, every cross-device
  sync from a tester running today's main would surface as
  `status=success_with_data_loss` until A/B/C land. Bad signal-to-noise.
- **Verification:** unit test `pullCollection` in isolation; verify
  exception path increments only `skippedPermanent`, `false`-return
  increments only `skippedTransient`. Smoke: trigger a deliberate
  constraint exception in a debug build (e.g. via the existing debug
  panel) and confirm the in-app indicator surfaces.

### Phase 2 ordering & bundling decision

| PR | Scope LOC | Order | Standalone vs bundled |
|---|---|---|---|
| **E** — CI workflow: build APK + AAB on every PR merge | ~30 | 0th (lands first) | Standalone CI infra |
| **B** — medications natural-key dedup | ~80 | 1st | Standalone |
| **A** — delete-with-metadata helper + FK guard | ~250 | 2nd | Standalone |
| **C** — config-family natural-key dedup | ~180 | 3rd | Standalone (Option C1) — bundles with B optional (C2) |
| **D** — error promotion | ~120 | 4th (after A, B, C) | Standalone — depends on prior fixes |

**Total LOC delta: ~660.** All five PRs combined are smaller than the
single Test 3 audit log. Each ships behind auto-merge with squash, all
required CI green per memory `feedback_auto_merge_branch_update_deadlock`
(empty commit re-fire if `gh pr update-branch` runs).

### PR-E (lands first) — `ci/build-apk-and-aab-on-merge`

**User-requested CI infra change so every Phase 2 merge produces both a
release APK and a release AAB for inspection / sideloading / Play Store
upload.**

Current state — verified `git show :.github/workflows/`:

- `version-bump-and-distribute.yml:387` runs `./gradlew assembleRelease`
  on every PR merge (triggered via `workflow_run` on auto-merge.yml). Then
  uploads the resulting APK to Firebase App Distribution at line 398.
  **No AAB is built or retained on the per-merge path.**
- `release.yml:87` runs both `assembleRelease` and `bundleRelease`, but
  only fires on tags matching `v[0-9]+.[0-9]+.0` (minor/major only — see
  `release.yml:7`). Per-merge patch tags like `v1.7.30` do **not** trigger
  it, so the AAB never builds on a regular PR merge today.

Fix shape:

- **Files touched** (~30 LOC delta):
  - `.github/workflows/version-bump-and-distribute.yml:387` — change
    `./gradlew assembleRelease ...` to
    `./gradlew assembleRelease bundleRelease ...`. Same flags
    (`--no-configuration-cache --build-cache --parallel -x lint -x
    lintVitalRelease`).
  - Add a new `actions/upload-artifact@v4` step after the build that
    uploads `app/build/outputs/apk/release/*.apk` and
    `app/build/outputs/bundle/release/*.aab` as workflow artifacts named
    `prismtask-release-${versionName}-${versionCode}` (retain ~30 days).
    Both artifacts downloadable from the workflow run page.
  - Optional: gate the AAB-upload step on `if: success()` so a failed
    bundle doesn't block the existing Firebase APK distribution path.
- **Bundles with:** none — pure CI change, ships first as Phase 2
  infrastructure.
- **Order constraint:** lands **before** PR-B/A/C/D so each of those
  merges produces both artifacts as a side effect.

**Open question (one-line confirmation needed before I open PR-E):** AAB
destination — pick one:

  1. **Workflow artifact only** (downloadable from the GH run page; nothing else changes). Simplest. *My pick* — matches the literal request "build APK and AAB."
  2. **Workflow artifact + Play Store internal testing track upload** (uses `r0adkll/upload-google-play@v1` or equivalent; needs the Play service-account JSON already configured in CI per the upload-key memory).
  3. **Workflow artifact + GitHub Release attachment** (mirror what `release.yml` does for v*.*.0 tags, but for every patch tag too).

Verification:

- After PR-E merges, the next merge (PR-B in the planned order) should
  show the workflow artifact bundle on its Actions run with both files
  present. If only the APK is present, the AAB step regressed — fix
  before PR-A/C/D ship.
- `bundleRelease` adds ~90s to the per-merge CI run. Acceptable for the
  P0 cadence; revisit if the budget is tight after Phase F.

### Per-PR regression-gate template (mirrors PR #798 / #840 shape)

Every PR adds a parametrized androidTest under
`androidTest/.../sync/scenarios/` named for the failure mode it gates:

- PR-B: `medicationByName_dedupAcrossDevices`
- PR-A: `deleteParentRow_doesNotLeakSyncMetadata`
- PR-C: per family — `<family>ByNaturalKey_dedupAcrossDevices`
- PR-D: `pullCollection_distinguishesTransientVsPermanentSkips` (unit) +
  `permanentSkip_promotesSyncCompletedToError` (instrumented)

Per memory `feedback_localdateflow_for_logical_day_flows` and
`feedback_repro_first_for_time_boundary_bugs`: tests use the SoD-aware
helpers (`useLogicalToday`, `LocalDateFlow`) when constructing log dates
to keep the fix surface consistent with the broader codebase.

### Auto-bump pipeline note

Each merge fires the v1.7.X auto-bump per memory
`feedback_auto_merge_branch_update_deadlock` and the noted Phase E
behavior. Four merges in sequence will produce four version bumps; that's
expected and not a regression.

### Verification gate

P0 closes when:

1. PR-B + PR-A merged. Test 3 re-run on the original 2-AVD setup is clean
   (no `pull.apply | … | status=failed | FOREIGN KEY` and no `UNIQUE
   constraint failed: medications.name`).
2. PR-D merged. A deliberately-induced constraint failure surfaces in the
   in-app sync indicator AND in `sync.completed | status=…`.
3. PR-C is **not** a P0 close requirement — it's the generalization.
   Defer to G.0 if review bandwidth is tight.

Phase F GREEN-GO can revert from YELLOW to UNCONDITIONAL after item (1) +
(2) above are verified.

---

## Per-item audit framework recap

| Item | Premise verified | Risk | Recommendation |
|---|---|---|---|
| 1 — habit_completions FK | ✅ stale `sync_metadata` after habit delete | RED | PROCEED — pair `deleteById` with `syncMetadataDao.delete` everywhere (PR-A) |
| 2 — medications UNIQUE | ✅ no natural-key dedup in pull | RED | PROCEED — add `getByNameOnce` dedup before INSERT (PR-B) |
| 3 — Sweep | ✅ 13 entities risky for UNIQUE; 8 parents risky for FK | RED (2 entities) + YELLOW (11 entities) | PROCEED — generalize via `pullRoomConfigFamily.naturalKeyLookup` (PR-C) + `deleteEntityAndMetadata` helper (PR-A) |
| 4 — Silent failure | ✅ skip kinds collapsed; `success=true` hardcoded | RED — multiplier on items 1–3 | PROCEED with **option (b)** (PR-D), only after A/B/C land |
| 5 — Phase 2 fan-out | ✅ 5 PRs total, ~660 LOC (4 fix + 1 CI infra) | — | PROCEED — order **E → B → A → C → D** |

## Phase 3 bundle summary (placeholder)

After all approved PRs merged + Test 3 re-runs clean, append:

- Per-PR: PR number(s), SHA(s), line delta, scope-vs-audit deviation
- Test 3 re-run result (pass/fail per scenario)
- Phase F GREEN-GO status: revert to UNCONDITIONAL or remain YELLOW with
  explicit gating
- Memory entry candidates (all subject to the user-confirmed cap; current
  index is full so any new entry needs displacement):
  - **"Sync-layer silent-failure rule"** — data-layer parallel to the
    existing CD silent-failure rule. Likely candidate to *consolidate
    with* the existing CD entry rather than add a new line.
  - **"Pair `dao.deleteById` with `syncMetadataDao.delete` in one
    transaction"** — if it survives review.
  - **"Apply `naturalKeyLookup` to every `pullRoomConfigFamily` caller
    with a non-`cloud_id` UNIQUE index"** — design rule going forward.
- Whether item 3's full sweep (other YELLOW entities) needs follow-up PRs
  or is closed by PR-C.

