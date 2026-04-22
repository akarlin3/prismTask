# Sync Architecture — Firebase Firestore

This document describes how PrismTask pushes and pulls data between the local
Room database and Firebase Firestore. See `data/remote/SyncService.kt` for
the canonical implementation.

---

## 1. Entity and Sync Map

Each synced entity has a Firestore collection path under `users/{uid}/` and a
`sync_metadata` row that maps the local Room `id` to the Firestore document ID
(`cloudId`).

| Entity type | Room table | Firestore path | FK note |
|---|---|---|---|
| `task` | `tasks` | `users/{uid}/tasks/{cloudId}` | `projectCloudId`, `parentCloudId`, `tagCloudIds[]` resolved on push/pull |
| `project` | `projects` | `users/{uid}/projects/{cloudId}` | None |
| `milestone` | `milestones` | `users/{uid}/milestones/{cloudId}` | `projectCloudId` → projects |
| `tag` | `tags` | `users/{uid}/tags/{cloudId}` | None |
| `habit` | `habits` | `users/{uid}/habits/{cloudId}` | None |
| `habit_completion` | `habit_completions` | `users/{uid}/habit_completions/{cloudId}` | `habitCloudId` → habits |
| `habit_log` | `habit_logs` | `users/{uid}/habit_logs/{cloudId}` | `habitCloudId` → habits |
| `task_completion` | `task_completions` | `users/{uid}/task_completions/{cloudId}` | `taskCloudId` → tasks |
| `task_template` | `task_templates` | `users/{uid}/task_templates/{cloudId}` | `projectCloudId` (nullable) → projects |

**FK resolution semantics**: On push, all foreign-key references are serialized
as their Firestore `cloudId` strings (e.g., `projectCloudId`), never as local
`Long` IDs. On pull, the reverse lookup happens via `SyncMetadataDao` — if a
referenced `cloudId` is not yet in `sync_metadata`, the row is skipped and
queued for retry on the next pull cycle.

---

## 2. Push Path

```
User action (create/update/delete)
    │
    ▼
Repository (e.g. HabitRepository, TaskRepository)
    │  writes entity to Room
    │  calls SyncTracker.trackCreate / trackUpdate / trackDelete
    │
    ▼
sync_metadata table (local_id, entity_type, cloud_id, status=PENDING)
    │
    ▼
SyncService.observePending()          ← reactive Flow on sync_metadata writes
    │  500 ms debounce
    ▼
SyncService.pushLocalChanges()
    ├── CREATE: serialize entity → Firestore doc, store returned cloudId in sync_metadata
    ├── UPDATE: serialize entity → Firestore doc (merge), bump updatedAt
    └── DELETE: Firestore doc.delete(), remove sync_metadata row
```

The `observePending()` observer runs on a `CoroutineScope(SupervisorJob() + Dispatchers.IO)`,
not `viewModelScope`, so navigation does not interrupt in-flight pushes.

---

## 3. Pull Path

```
SyncService.pullRemoteChanges()  (called on fullSync or startAutoSync listener)
    │
    ├── pullCollection("projects")  → upsert via ProjectDao
    ├── pullCollection("tags")      → upsert via TagDao
    ├── pullCollection("habits")    → upsert via HabitDao
    ├── pullCollection("tasks")     → upsert via TaskDao (resolves tag cloud IDs)
    ├── inline pull: task_completions (embedded in tasks collection response)
    ├── inline pull: habit_completions
    └── inline pull: habit_logs
```

Conflict resolution is **last-write-wins** by `updatedAt`. If the incoming
document's `updatedAt` ≤ the local row's `updated_at`, the pull is skipped
and counted as a duplicate.

---

## 4. Real-Time Listener

`SyncService.startAutoSync()` attaches a Firestore snapshot listener per
collection. Each snapshot event is dispatched by type:

- **MODIFIED** → calls `pullRemoteChanges()` for the affected collection.
- **REMOVED** → calls `processRemoteDeletions(entityType, localId)`.

`processRemoteDeletions` resolves the local Room ID via `sync_metadata`,
then dispatches to the appropriate DAO:

| Entity type | Delete call |
|---|---|
| `task` | `taskDao.deleteById(localId)` |
| `project` | `projectDao.deleteById(localId)` |
| `tag` | `tagDao.deleteById(localId)` |
| `habit` | `habitDao.deleteById(localId)` |
| `habit_completion` | `habitCompletionDao.deleteById(localId)` |
| `habit_log` | `habitLogDao.deleteById(localId)` |
| `task_completion` | `taskCompletionDao.deleteById(localId)` |
| `task_template` | `taskTemplateDao.deleteById(localId)` |
| `milestone` | `milestoneDao.deleteById(localId)` |

---

## 5. Built-In Habit Reconciliation

Built-in habits (School, Leisure, Morning Self-Care, Bedtime Self-Care,
Medication, Housework) carry `is_built_in = 1` and a stable `template_key`
in the `habits` table (migration 48→49). This allows `BuiltInHabitReconciler`
to identify them reliably regardless of display name.

`BuiltInHabitReconciler.reconcileAfterSyncIfNeeded()` runs once after a
successful `fullSync()`. It:

1. Deduplicates cloud-pulled built-in habits that arrived as separate
   documents (one per prior install).
2. Backfills `is_built_in` / `template_key` on any row where these fields
   are absent.
3. Calls `habitDao.backfillAllBuiltIns()` to apply name-based inference for
   rows that pre-date migration 48→49.

Three boolean flags in `BuiltInSyncPreferences` guard each repair so it runs
at most once per install: `builtInsReconciled`, `driftCleanupDone`,
`builtInBackfillDone`.

`SyncMapper.mapToHabit()` also infers `isBuiltIn = true` when `templateKey`
is present in the Firestore document but `isBuiltIn` field is absent (forward-
compatible read of pre-migration 48→49 cloud data).

---

## 6. Timezone-Neutral Completion Dates (Migration 49→50)

`habit_completions.completed_date_local` (TEXT, ISO `YYYY-MM-DD`) stores the
calendar date of a completion in the device's local timezone. It is backfilled
on upgrade via:

```sql
UPDATE habit_completions
SET completed_date_local =
    strftime('%Y-%m-%d', completed_date / 1000, 'unixepoch', 'localtime')
WHERE completed_date_local IS NULL
```

All `HabitRepository` day-boundary queries use `completed_date_local` instead
of epoch-millis arithmetic. This eliminates a class of multi-timezone bugs
where a completion recorded just after midnight UTC could land on the wrong
calendar day on devices with non-UTC clocks.

On pull, Firestore-sourced `completedDate` (epoch millis) is re-normalized
through the device's configured `dayStartHour` (`DayBoundary`) before
insertion, keeping `completed_date_local` consistent with the Start-of-Day
user preference.

---

## 7. Per-Entity One-Time Backfill Flags

| Flag | DataStore | Meaning |
|---|---|---|
| `builtInsReconciled` | `BuiltInSyncPreferences` | Full built-in dedup + `template_key` backfill run |
| `driftCleanupDone` | `BuiltInSyncPreferences` | Stale duplicate cloud documents removed |
| `builtInBackfillDone` | `BuiltInSyncPreferences` | Name-based `is_built_in` backfill applied |
| `startOfDay` | `UserPreferencesDataStore` | First-launch SoD picker shown; hour stored (0–23) |

These flags are intentionally excluded from the JSON export/import cycle
because restoring a backup to a new device should re-run the backfill against
the freshly-pulled cloud data, not skip it.
