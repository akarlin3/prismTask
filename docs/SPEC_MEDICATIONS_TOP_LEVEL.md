# SPEC — Medications as a Standalone Top-Level Entity

**Status:** Proposal — not yet scheduled
**Author:** Scoped via audit on 2026-04-22
**Target Room version:** 53 → 54 (one bump)
**Related docs:** `docs/PHASE_3_FIX_D_PLAN.md` (quarantine-table precedent),
`docs/sync-architecture.md`, `docs/FIREBASE_EMULATOR.md`

---

## 0. Reader's note — prompt premise correction

The brief that triggered this spec framed Medications as a "habit category" and
implied doses live in `habits` + `habit_completions`. The audit found
otherwise. Medication data today is scattered across **four** storage surfaces:

1. **`habits` table** — one built-in row named `"Medication"`
   (`templateKey="builtin_medication"`, `category="Medication"`). Its
   `reminderIntervalMillis` powers the `INTERVAL_HABIT` dose source, and its
   `habit_completions` rows are the increment target for `SPECIFIC_TIMES`
   alarms. No user-created habits with `category="Medication"` are supported.
2. **`self_care_steps` table, `routine_type="medication"`** — the primary,
   user-visible store of named doses (`Lipitor`, `Adderall`, …) with
   `medication_name`, `time_of_day`, `tier`.
3. **`self_care_logs` table, `routine_type="medication"`** — daily completion
   records with a JSON `completed_steps` array carrying
   `{id, note, at, timeOfDay}` per taken dose.
4. **`medication_prefs` DataStore** (`MedicationPreferences`) — interval
   minutes, schedule mode (`INTERVAL` / `SPECIFIC_TIMES`), set of `HH:mm`
   strings.

Plus `medication_refills` (Room table added at v43→v44) for pharmacy / refill
metadata, linked to (2) by `medication_name`.

`MedicationStatusUseCase.observeDueDosesToday()` is the single read-time
aggregator that joins (1) + (2) + (4) into a `List<MedicationDose>`; its
output feeds `DailyEssentialsUseCase` + `MedicationSlotGrouper`.

This spec's migration must cover all four surfaces and fold `medication_refills`
into the new shape. The `habits` row is the smallest piece of the move.

---

## 1. Summary

Medications become a top-level entity with their own screen, nav tile, settings
toggle, data model, reminder scheduler, and Firestore collection — on par with
Tasks and Habits rather than as a sub-routine inside Self-Care. The user-facing
outcome is a dedicated **Medication** tab in the bottom nav (gated by the
existing `HabitListPreferences.MEDICATION_ENABLED` toggle, which today only
hides a tile inside the Habits list), a single authoritative per-dose data
model that replaces the current three-way scatter, and a clean split between
"medication reminders" (user-declared dose times) and "habit reminders"
(generic daily / interval alarms). Internally this collapses the current
`MedicationReminderScheduler` — which is today a dual-purpose app-wide habit
reminder scheduler — into two sharper-scoped classes.

The motivation is clinical separation from general habits, better UX framing
for a surface that users experience as "health-critical, not habit-ish," and
an unlock for future refill / adherence / pharmacy features that are awkward
to hang off a self-care-step row. It is not a feature launch in itself — the
user-visible behavior is nearly identical to today's Medication screen on
first open — it is the prerequisite architecture for those features. A
meaningful side benefit: `medication_refills` becomes syncable (today it is
registered in Room but has zero references in the sync layer).

**Out of scope for v1 of this change.** Pharmacy-API integrations, drug-drug
interaction warnings, prescription photo OCR, insurance refill authorization,
dose-skip analytics beyond what `DailyEssentialsSlotCompletionDao` already
provides, and any changes to the Daily Essentials medication slot UX. These
defer to post-launch work once the entity model is stable. The migration
preserves the current Medication screen, the Today-screen Daily Essentials
card, and the existing medication reminder alarms end-to-end — users should
notice only the new tab.

---

## 2. Data model

### 2.1 New Room entities

#### `MedicationEntity` (new table `medications`)

```kotlin
@Entity(
    tableName = "medications",
    indices = [
        Index(value = ["cloud_id"], unique = true),
        Index(value = ["name"], unique = true)  // enforce unique name per user
    ]
)
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "cloud_id") val cloudId: String? = null,
    @ColumnInfo(name = "name") val name: String,              // "Lipitor"
    @ColumnInfo(name = "display_label") val displayLabel: String? = null,  // "Lipitor 20mg"
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "tier", defaultValue = "'essential'") val tier: String = "essential",
    @ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order", defaultValue = "0") val sortOrder: Int = 0,

    // Schedule — exactly one of (timesOfDay, specificTimes, intervalMillis)
    // is populated; the mode column disambiguates.
    @ColumnInfo(name = "schedule_mode") val scheduleMode: String = "TIMES_OF_DAY",
        // "TIMES_OF_DAY" | "SPECIFIC_TIMES" | "INTERVAL" | "AS_NEEDED"
    @ColumnInfo(name = "times_of_day") val timesOfDay: String? = null,
        // comma-separated subset of "morning,afternoon,evening,night"
    @ColumnInfo(name = "specific_times") val specificTimes: String? = null,
        // comma-separated "HH:mm" strings, e.g. "08:00,14:30,21:00"
    @ColumnInfo(name = "interval_millis") val intervalMillis: Long? = null,
    @ColumnInfo(name = "doses_per_day", defaultValue = "1") val dosesPerDay: Int = 1,

    // Refill data — subsumes `medication_refills`. Nullable because not every
    // medication tracks pills.
    @ColumnInfo(name = "pill_count") val pillCount: Int? = null,
    @ColumnInfo(name = "pills_per_dose", defaultValue = "1") val pillsPerDose: Int = 1,
    @ColumnInfo(name = "last_refill_date") val lastRefillDate: Long? = null,
    @ColumnInfo(name = "pharmacy_name") val pharmacyName: String? = null,
    @ColumnInfo(name = "pharmacy_phone") val pharmacyPhone: String? = null,
    @ColumnInfo(name = "reminder_days_before", defaultValue = "3") val reminderDaysBefore: Int = 3,

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
```

Design decisions:

- **Single `medications` table, not `medications` + `medication_schedules`.**
  In practice every dose today either maps to times-of-day buckets or to
  specific clock times or to a single global interval; splitting the schedule
  into a child table adds a JOIN for no user-visible payoff. If future schedule
  variations need repeating rules, add a `medication_schedules` table then,
  not preemptively now.
- **Refill data lives inline.** The existing `medication_refills` table has
  exactly the right fields but is linked by the brittle `medication_name`
  string. Folding it inline drops one table, removes the name-FK risk, and
  unifies updated_at semantics.
- **`schedule_mode` is a string, not a Room `@TypeConverter` enum.** Matches
  existing patterns (`HabitEntity.frequencyPeriod`, `routine_type`). Parsing
  happens in the domain layer via `MedicationScheduleMode.fromStorage(…)`,
  returning a safe default for unknown values.
- **`cloud_id` is present from day one** per `MIGRATION_51_52` pattern —
  unique-indexed, nullable; new rows insert `NULL` until synced.
- **`name` is UNIQUE.** Duplicate source rows sharing the same normalized
  `medication_name` collapse to one row at migration time. Disambiguating
  detail (e.g. `"Lipitor 20mg"` + `"Lipitor 40mg"` from two self-care steps
  both named `"Lipitor"`) is preserved in `display_label` via
  `GROUP_CONCAT(DISTINCT label, ' / ')`. Users retain a visible disambiguator
  on the list and can edit per-med post-migration if they want separate rows.

#### `MedicationDoseEntity` (new table `medication_doses`)

```kotlin
@Entity(
    tableName = "medication_doses",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medication_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cloud_id"], unique = true),
        Index(value = ["medication_id", "taken_date_local"]),
        Index(value = ["taken_date_local"])
    ]
)
data class MedicationDoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "cloud_id") val cloudId: String? = null,
    @ColumnInfo(name = "medication_id") val medicationId: Long,

    // Slot identity — either "HH:mm" wall-clock or a time-of-day bucket id.
    @ColumnInfo(name = "slot_key") val slotKey: String,
        // "08:00" | "morning" | "afternoon" | "evening" | "night" | "anytime"

    @ColumnInfo(name = "taken_at") val takenAt: Long,
    @ColumnInfo(name = "taken_date_local") val takenDateLocal: String,
        // ISO LocalDate in device timezone — matches habit_completions convention
    @ColumnInfo(name = "note") val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
```

Design decisions:

- **Parity with `habit_completions`** (`completed_date_local` column, single
  log-per-dose-per-slot-per-day pattern). Reuses the same timezone-neutrality
  fix landed in `MIGRATION_49_50`.
- **`slot_key` is a string, not an enum.** Day-boundary math happens on
  `taken_date_local`, not on `slot_key`.
- **`CASCADE` on delete.** Deleting a medication removes its history. This
  matches `milestones` cascade-from-projects. For regulated-data use cases the
  user would archive, not delete; the UI should surface "archive" as the
  primary affordance.
- **No FK from `daily_essential_slot_completions`** to this table. That table
  already uses synthetic `source:name` dose keys and survives medication
  renames; it keeps its current shape unchanged.

### 2.2 New DAO: `MedicationDao`

Follows the `HabitDao` pattern — `Flow<List<…>>` for reactive reads,
`suspend` for writes, explicit `…Once()` variants where the sync layer
needs a snapshot. Minimum surface:

```kotlin
@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications WHERE is_archived = 0 ORDER BY sort_order, name")
    fun getActive(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY sort_order, name")
    fun getAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getByIdOnce(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE is_archived = 0")
    suspend fun getActiveOnce(): List<MedicationEntity>

    @Insert suspend fun insert(med: MedicationEntity): Long
    @Update suspend fun update(med: MedicationEntity)
    @Query("UPDATE medications SET is_archived = 1, updated_at = :now WHERE id = :id")
    suspend fun archive(id: Long, now: Long)
    @Delete suspend fun delete(med: MedicationEntity)

    // Sync helpers — mirror HabitDao.getByCloudId / upsertFromCloud
    @Query("SELECT * FROM medications WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): MedicationEntity?
}

@Dao
interface MedicationDoseDao {
    @Query("SELECT * FROM medication_doses WHERE taken_date_local = :date")
    fun getForDate(date: String): Flow<List<MedicationDoseEntity>>

    @Query("SELECT * FROM medication_doses WHERE medication_id = :medicationId AND taken_date_local = :date")
    fun getForMedOnDate(medicationId: Long, date: String): Flow<List<MedicationDoseEntity>>

    @Query("SELECT COUNT(*) FROM medication_doses WHERE medication_id = :medicationId AND taken_date_local = :date")
    suspend fun countForMedOnDateOnce(medicationId: Long, date: String): Int

    @Insert suspend fun insert(dose: MedicationDoseEntity): Long
    @Delete suspend fun delete(dose: MedicationDoseEntity)

    // Sync helpers
    @Query("SELECT * FROM medication_doses WHERE cloud_id = :cloudId LIMIT 1")
    suspend fun getByCloudIdOnce(cloudId: String): MedicationDoseEntity?
}
```

### 2.3 Firestore collection structure

Mirror the Room shape verbatim under the existing per-user subcollection scheme.
`SyncService.collectionNameFor` must gain two new cases:

```kotlin
"medication"        -> "medications"
"medication_dose"   -> "medication_doses"
```

Doc path: `users/{uid}/medications/{cloudId}`,
`users/{uid}/medication_doses/{cloudId}`. Schema fields 1-to-1 with Room
columns except for the usual `server_updated_at` / `local_updated_at` pair
used by `BackendSyncMappers` for last-write-wins.

### 2.4 Firestore security rules

**Resolved (2026-04-22).** Prod uses a recursive wildcard under
`/users/{userId}/`:

```
match /users/{userId}/{document=**} {
  allow read, write: if request.auth != null && request.auth.uid == userId;
}
```

The `{document=**}` glob covers every subcollection at every depth, so
`medications` + `medication_doses` are allowed without any rule change
when those collections first appear. No console edit required for this
feature.

**Note for future specs:** PrismTask's rule model is "per-user sandbox,
wildcard collections." If a future feature needs per-collection
hardening (rate limits, field validation, size caps), the wildcard must
be un-wildcarded first. That's a repo-wide refactor, not a one-feature
change.

#### Client-side verification after v54 first-launch

- Trigger a full sync (Settings → Sync → Force Push).
- Watch `PrismSyncLogger` output. Any `PERMISSION_DENIED` on
  `medications` or `medication_doses` means the wildcard was accidentally
  removed or narrowed between now and ship.
- `sync_metadata.retry_count` incrementing for `entity_type='medication'`
  is the other signal.

---

## 3. Migration strategy

### 3.1 The migration is the highest-risk part of this spec

Unlike every other migration in `Migrations.kt`, this one **moves row data
across tables and then deletes the source rows**. No precedent exists in the
codebase. The closest inspiration is `PHASE_3_FIX_D_PLAN.md`'s
`quarantine_task_completions_null_taskid` pattern (currently documented but
not yet landed), which preserves source rows in a staging table before
deletion so rollback is possible.

### 3.2 `MIGRATION_53_54`

```kotlin
val MIGRATION_53_54 = object : Migration(53, 54) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // A. CREATE medications + medication_doses with cloud_id indexes.
        //    Schema text lives inline here per the repo convention (no
        //    external DDL files). Column order matches MedicationEntity.kt.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `medications` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `name` TEXT NOT NULL,
              `display_label` TEXT,
              `notes` TEXT NOT NULL DEFAULT '',
              `tier` TEXT NOT NULL DEFAULT 'essential',
              `is_archived` INTEGER NOT NULL DEFAULT 0,
              `sort_order` INTEGER NOT NULL DEFAULT 0,
              `schedule_mode` TEXT NOT NULL DEFAULT 'TIMES_OF_DAY',
              `times_of_day` TEXT,
              `specific_times` TEXT,
              `interval_millis` INTEGER,
              `doses_per_day` INTEGER NOT NULL DEFAULT 1,
              `pill_count` INTEGER,
              `pills_per_dose` INTEGER NOT NULL DEFAULT 1,
              `last_refill_date` INTEGER,
              `pharmacy_name` TEXT,
              `pharmacy_phone` TEXT,
              `reminder_days_before` INTEGER NOT NULL DEFAULT 3,
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX `index_medications_cloud_id` ON `medications` (`cloud_id`)")
        db.execSQL("CREATE UNIQUE INDEX `index_medications_name` ON `medications` (`name`)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `medication_doses` (
              `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
              `cloud_id` TEXT,
              `medication_id` INTEGER NOT NULL,
              `slot_key` TEXT NOT NULL,
              `taken_at` INTEGER NOT NULL,
              `taken_date_local` TEXT NOT NULL,
              `note` TEXT NOT NULL DEFAULT '',
              `created_at` INTEGER NOT NULL,
              `updated_at` INTEGER NOT NULL,
              FOREIGN KEY(`medication_id`) REFERENCES `medications`(`id`) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX `index_medication_doses_cloud_id` ON `medication_doses` (`cloud_id`)")
        db.execSQL("CREATE INDEX `index_medication_doses_med_id_date` ON `medication_doses` (`medication_id`, `taken_date_local`)")
        db.execSQL("CREATE INDEX `index_medication_doses_date` ON `medication_doses` (`taken_date_local`)")

        // B. CREATE staging tables (quarantine) — preserve source rows
        //    before they're removed. Rollback script in 3.5 restores from
        //    these. Same not-in-Room-schema caveat as Fix D's quarantine
        //    table: Room ignores these on identity check.
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `quarantine_medication_selfcare_steps` AS
            SELECT * FROM `self_care_steps` WHERE `routine_type` = 'medication'
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `quarantine_medication_selfcare_logs` AS
            SELECT * FROM `self_care_logs` WHERE `routine_type` = 'medication'
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `quarantine_medication_refills` AS
            SELECT * FROM `medication_refills`
        """.trimIndent())
        // NOTE: the built-in 'Medication' habit is NOT deleted by this
        // migration — that's a follow-up cleanup once sync convergence is
        // confirmed (see §3.4). Same for its habit_completions rows.

        // C. BACKFILL medications from self_care_steps — GROUPED by
        //    normalized name so duplicate source rows collapse per §2.1
        //    Option C resolution.
        //
        //    - One row per distinct normalized name
        //      (COALESCE(NULLIF(TRIM(medication_name), ''), label)).
        //    - display_label: GROUP_CONCAT(DISTINCT label, ' / ') preserves
        //      disambiguating detail (e.g. "Lipitor 20mg / Lipitor 40mg").
        //    - times_of_day: GROUP_CONCAT(DISTINCT time_of_day) aggregates
        //      across all source rows sharing the name.
        //    - tier: picks the MIN-id source row's tier (stable tiebreaker).
        //    - Refill row with matching medication_name merges in inline.
        //    - schedule_mode: hard-coded 'TIMES_OF_DAY' at migration time.
        //      The post-migration MedicationMigrationRunner (§4.4) overrides
        //      this based on MedicationPreferences.scheduleMode +
        //      the built-in Medication habit's reminderIntervalMillis so
        //      the user's current global schedule is preserved.
        //    - cloud_id: left NULL. The next sync push creates fresh cloud
        //      docs for every medication. Source self_care_steps cloud_ids
        //      stay in sync_metadata for rollback; Phase 2 cleanup removes
        //      them.
        db.execSQL("""
            INSERT INTO medications (
              name, display_label, notes, tier,
              schedule_mode, times_of_day,
              pill_count, pills_per_dose, doses_per_day, last_refill_date,
              pharmacy_name, pharmacy_phone, reminder_days_before,
              created_at, updated_at
            )
            SELECT
              normalized_name AS name,
              merged_labels AS display_label,
              '' AS notes,
              picked_tier AS tier,
              'TIMES_OF_DAY' AS schedule_mode,
              merged_times_of_day AS times_of_day,
              r.pill_count,
              COALESCE(r.pills_per_dose, 1),
              COALESCE(r.doses_per_day, 1),
              r.last_refill_date,
              r.pharmacy_name,
              r.pharmacy_phone,
              COALESCE(r.reminder_days_before, 3),
              strftime('%s','now') * 1000,
              strftime('%s','now') * 1000
            FROM (
              SELECT
                COALESCE(NULLIF(TRIM(medication_name), ''), label) AS normalized_name,
                GROUP_CONCAT(DISTINCT label) AS merged_labels_raw,
                GROUP_CONCAT(DISTINCT time_of_day) AS merged_times_of_day,
                -- pick the tier from the MIN-id row (stable tiebreaker)
                (SELECT tier FROM self_care_steps s2
                 WHERE s2.routine_type='medication'
                   AND COALESCE(NULLIF(TRIM(s2.medication_name), ''), s2.label) =
                       COALESCE(NULLIF(TRIM(self_care_steps.medication_name), ''), self_care_steps.label)
                 ORDER BY s2.id ASC LIMIT 1) AS picked_tier,
                -- SQLite GROUP_CONCAT defaults to comma separator; rewrite
                -- to ' / ' for display by REPLACE'ing. The spec assumes no
                -- literal ',' in labels (current data has none); add an
                -- escape pass if that ever changes.
                REPLACE(GROUP_CONCAT(DISTINCT label), ',', ' / ') AS merged_labels
              FROM self_care_steps
              WHERE routine_type = 'medication'
              GROUP BY normalized_name
            ) grouped
            LEFT JOIN medication_refills r
              ON r.medication_name = grouped.normalized_name
        """.trimIndent())

        // D. INSERT medication_doses from self_care_logs.
        //    completed_steps is a JSON array — SQLite json_each() is
        //    required for this. Older SQLite on Android <= API 26 may not
        //    have JSON1 compiled in; the spec assumes JSON1 is available
        //    (Room 2.8.4 targets Android SQLite ≥ 3.32 which has it), but
        //    the implementer MUST verify against min SDK 26 before
        //    shipping. If JSON1 is not guaranteed, fall back to a
        //    one-shot Kotlin migration pass via `RoomDatabase.Callback
        //    .onOpen` that parses in-process and writes dose rows.
        //
        //    Each JSON entry can be either a bare string stepId or an
        //    object {id, note, at, timeOfDay}. The query handles both.
        //
        //    slot_key resolution:
        //      - if entry.timeOfDay ∈ {morning, afternoon, evening, night} → use it
        //      - else default to 'anytime'
        //      - 'HH:mm' specific-time slots don't exist at this layer —
        //        they come from MedicationPreferences and aren't tracked
        //        per-dose in self_care_logs.
        db.execSQL("""
            INSERT INTO medication_doses (
              medication_id, slot_key, taken_at, taken_date_local, note,
              created_at, updated_at
            )
            SELECT
              m.id AS medication_id,
              COALESCE(je.value ->> '$.timeOfDay', 'anytime') AS slot_key,
              COALESCE(je.value ->> '$.at', l.log_date) AS taken_at,
              strftime('%Y-%m-%d', l.log_date / 1000, 'unixepoch', 'localtime') AS taken_date_local,
              COALESCE(je.value ->> '$.note', '') AS note,
              strftime('%s','now') * 1000,
              strftime('%s','now') * 1000
            FROM self_care_logs l
            JOIN json_each(l.completed_steps) je
            JOIN self_care_steps s
              ON s.routine_type = 'medication'
              AND s.step_id = COALESCE(je.value ->> '$.id', je.value)
            JOIN medications m
              ON m.name = COALESCE(NULLIF(TRIM(s.medication_name), ''), s.label)
            WHERE l.routine_type = 'medication'
        """.trimIndent())

        // E. DO NOT touch self_care_steps/self_care_logs/habits/
        //    medication_refills yet. Keep them in place so:
        //      - the pre-migration UI keeps rendering during the sync
        //        convergence window
        //      - cross-device race (see §3.4) has a fallback read path
        //      - rollback is possible if the post-migration verification
        //        fails
        //    The next migration (55→?) after a release + telemetry
        //    window drops the source rows. Staged cleanup.

        // F. One-shot preference flag to trigger the sync-side steps
        //    (collection rules change, initial upload of medications +
        //    medication_doses) on next app start. Owned by a new
        //    MedicationMigrationPreferences (§4.4).
        db.execSQL("""
            INSERT INTO sync_metadata (local_id, entity_type, cloud_id, last_synced_at)
            SELECT 0, '__medication_migration_v1_pending', '', 0
            WHERE NOT EXISTS (
                SELECT 1 FROM sync_metadata
                WHERE entity_type = '__medication_migration_v1_pending'
            )
        """.trimIndent())
    }
}
```

### 3.3 What this migration does NOT do

- **Does not delete the built-in `"Medication"` habit row.** It stays wired
  to its current `reminderIntervalMillis` so existing `INTERVAL_HABIT` alarms
  keep firing during the rollout. Follow-up PR #2 (after sync convergence)
  repoints those alarms at the new `MedicationEntity.intervalMillis` and
  deletes the habit row with a second migration.
- **Does not delete `self_care_steps` / `self_care_logs` rows.** Same
  reason — these stay as a read-fallback and as quarantine for rollback.
  Cleanup is the Phase 2 follow-up.
- **Does not rename `medication_refills`.** It stays in place as quarantine
  data; the cleanup migration drops it.

### 3.4 The cross-device race — the real risk

Two devices, both on v53, both signed into the same account. Device A
upgrades to v54, runs the migration, pushes new `medications` +
`medication_doses` docs. Device B is still on v53 and receives Firestore
updates on collections it doesn't know about (harmless) — but also continues
to **push updates to the old `self_care_steps` + `self_care_logs` docs**,
which Device A is still reading as a fallback. If Device B deletes a
medication (a `self_care_step` delete) after Device A migrated, the source
table on Device A still reflects the delete but the new `medications` table
doesn't — until Device B also upgrades.

Mitigations:

1. **Additive-only during the convergence window.** Device A's migration
   reads source tables one time, inserts into new tables, and does NOT
   modify the source tables. Ongoing edits on Device A write to the new
   tables AND mirror back to the source tables via a dual-write shim in
   `SelfCareRepository` (deprecated path) + `MedicationRepository` (new
   path) until the Phase 2 cleanup migration lands.
2. **Staleness TTL.** A setting like 72 hours after cutover, Phase 2
   migration checks `self_care_steps (routine_type='medication')` for any
   rows with `updated_at` newer than the corresponding `medications.updated_at`
   and refuses to drop source rows until the user re-opens the app on every
   signed-in device. `BuiltInMedicationReconciler` (§7.2) enforces this.
3. **Sync collection isolation.** Device B on v53 never sees
   `medications` / `medication_doses` docs because its `SyncService` doesn't
   subscribe to those collections (no `collectionNameFor` mapping). Adding
   new docs to Firestore doesn't leak into B.
4. **Explicit telemetry.** `PrismSyncLogger` logs a
   `medication.migration.device_mismatch` warning when the reconciler detects
   divergence; users can be nudged to update.

### 3.5 Rollback

Room schema is not down-migratable — `fallbackToDestructiveMigrationOnDowngrade()`
is the default, which would wipe the DB. A user rollback via v54 → v53 means
restoring the source tables from quarantine and dropping the new tables.
Rollback script (manual, via ADB or a debug-build menu item):

```sql
BEGIN;
DELETE FROM medications;
DELETE FROM medication_doses;
-- source rows were never deleted, so nothing to restore from quarantine
-- (the quarantine tables were a belt-and-braces copy for read-only audit)
DROP TABLE quarantine_medication_selfcare_steps;
DROP TABLE quarantine_medication_selfcare_logs;
DROP TABLE quarantine_medication_refills;
UPDATE room_master_table SET identity_hash = '<pre-v54 hash>';
COMMIT;
```

In practice rollback from the field is impossible — this script is for
emergency QA only. The release must ship with enough test coverage (§11)
that rollback is not a realistic option.

---

## 4. Sync architecture

### 4.1 `MedicationSyncMapper`

New file at `data/remote/mapper/MedicationSyncMapper.kt` following the
`SelfCareStepSyncMapper` shape:

```kotlin
object MedicationSyncMapper {
    fun toFirestoreMap(med: MedicationEntity): Map<String, Any?> = mapOf(
        "name" to med.name,
        "displayLabel" to med.displayLabel,
        // … full field list
        "updatedAt" to med.updatedAt
    )

    fun fromFirestore(data: Map<String, Any?>, cloudId: String): MedicationEntity = …

    fun toFirestoreMap(dose: MedicationDoseEntity): Map<String, Any?> = …
    fun doseFromFirestore(…): MedicationDoseEntity = …
}
```

### 4.2 `MedicationRepository` (new)

```kotlin
@Singleton
class MedicationRepository @Inject constructor(
    private val medicationDao: MedicationDao,
    private val doseDao: MedicationDoseDao,
    private val syncTracker: SyncTracker,
    private val dayBoundary: DayBoundary,
    // ...
) { … }
```

Public surface: `observeActive()`, `observeDosesForDate()`, `insert()`,
`update()`, `archive()`, `logDose()`, `unlogDose()`. Every write calls
`syncTracker.trackUpdate(id, "medication")` or `"medication_dose"` so the
push path picks it up.

### 4.3 `SyncService` changes

Minimum diff:

```kotlin
// SyncService.collectionNameFor(...)
"medication"      -> "medications"
"medication_dose" -> "medication_doses"

// SyncService.pullRemoteChanges(...) — add two new calls parallel to
// habits / habit_completions, with ORDERING: pull medications BEFORE
// medication_doses so the FK resolves. Same ordering that habits /
// habit_completions already use.

// SyncService.initialUpload(...) — same two new calls after habits /
// habit_completions. One-shot guard through BackendSyncPreferences.
```

`BackendSyncService` (the newer backend-first sync path) needs parallel
updates at `data/remote/sync/BackendSyncService.kt:275` where the Daily
Essentials comment already lives.

### 4.4 One-shot migration guards + schedule preservation runner

New `MedicationMigrationPreferences` DataStore, mirror of
`BuiltInSyncPreferences`:

```kotlin
class MedicationMigrationPreferences {
    val isSchedulePreserved: Flow<Boolean>
    suspend fun setSchedulePreserved(done: Boolean)

    val isMigrationPushedToCloud: Flow<Boolean>
    suspend fun setMigrationPushedToCloud(done: Boolean)

    val isReconciliationDone: Flow<Boolean>
    suspend fun setReconciliationDone(done: Boolean)

    val isSourceDataPurgedPhase2: Flow<Boolean>    // Phase 2 cleanup flag
    suspend fun setSourceDataPurgedPhase2(done: Boolean)
}
```

#### `MedicationMigrationRunner` — resolves §1.1 (schedule preservation)

Room migrations can't read DataStore, so the schedule preservation can't fit
in `MIGRATION_53_54` itself. A post-migration Kotlin runner fires from
`PrismTaskApplication.onCreate` on next app start and overrides the
hard-coded `schedule_mode='TIMES_OF_DAY'` set by the migration with whatever
the user's current global schedule is.

```kotlin
@Singleton
class MedicationMigrationRunner @Inject constructor(
    private val medicationDao: MedicationDao,
    private val habitDao: HabitDao,
    private val medicationPreferences: MedicationPreferences,
    private val migrationPrefs: MedicationMigrationPreferences,
    private val logger: PrismSyncLogger
) {
    suspend fun preserveScheduleIfNeeded() {
        if (migrationPrefs.isSchedulePreserved().first()) return
        try {
            val builtIn = habitDao.getHabitByName(SelfCareRepository.MEDICATION_HABIT_NAME)
            val intervalFromHabit = builtIn?.reminderIntervalMillis
            val mode = medicationPreferences.getScheduleModeOnce()
            val specificTimes = medicationPreferences.getSpecificTimesOnce()
            val intervalMinutesFromPrefs = medicationPreferences.getReminderIntervalMinutesOnce()

            // Telemetry — confirms whether preservation logic is
            // load-bearing in the wild. If nobody hits the `interval !=
            // null` branch after a release cycle, Phase 2 can simplify.
            logger.info(
                operation = "medication.migration.preserve_schedule",
                detail = "habit_interval=$intervalFromHabit pref_mode=$mode " +
                    "pref_interval_min=$intervalMinutesFromPrefs " +
                    "specific_times=${specificTimes.size}"
            )

            val meds = medicationDao.getAllOnce()
            val now = System.currentTimeMillis()
            for (med in meds) {
                val updated = when (mode) {
                    MedicationScheduleMode.SPECIFIC_TIMES -> med.copy(
                        scheduleMode = "SPECIFIC_TIMES",
                        specificTimes = specificTimes.sorted().joinToString(","),
                        intervalMillis = null,
                        updatedAt = now
                    )
                    MedicationScheduleMode.INTERVAL -> med.copy(
                        scheduleMode = "INTERVAL",
                        // Prefer the habit row's millis (what the scheduler
                        // actually reads today); fall back to the
                        // DataStore minutes value converted to millis.
                        intervalMillis = intervalFromHabit
                            ?: intervalMinutesFromPrefs.takeIf { it > 0 }?.let { it * 60_000L },
                        specificTimes = null,
                        updatedAt = now
                    )
                }
                medicationDao.update(updated)
            }

            migrationPrefs.setSchedulePreserved(true)
        } catch (e: Exception) {
            logger.error("medication.migration.preserve_schedule", "failed: ${e.message}")
            // Flag intentionally NOT set — retry on next app start.
        }
    }
}
```

Call order at app startup:

1. Room opens DB, runs `MIGRATION_53_54` if needed.
2. `MedicationMigrationRunner.preserveScheduleIfNeeded()` — preserves schedule.
3. First sync pull/push cycle — uploads all new medications + doses.
4. `BuiltInMedicationReconciler.reconcileAfterSyncIfNeeded()` — dedup pass.
5. Each of 2/3/4 has its own one-shot flag and is idempotent on subsequent
   launches.

First call after migration-push runs: push every `medications` +
`medication_doses` row to Firestore with `trackUpdate`, then flip
`isMigrationPushedToCloud=true`.

### 4.5 Conflict resolution

Last-write-wins by `updated_at`. Same policy as `self_care_logs` /
`habits`. The cross-device race (§3.4) means one device's migration can
produce `medications` rows with the same `cloud_id` as another device's
migration; `BuiltInMedicationReconciler` dedups by `(name)` after pull,
same rule as `BuiltInHabitReconciler` (winner = row with most
`medication_doses`; losers' doses reassigned via
`MedicationDoseDao.reassignMedicationId` + source row `delete`).

---

## 5. Reminder scheduling — three options

### 5.1 Option A (recommended) — Split into HabitReminderScheduler + MedicationReminderScheduler

Rename `MedicationReminderScheduler` → `HabitReminderScheduler` and extract
only the `MedicationPreferences`-driven `SPECIFIC_TIMES` path into a new,
smaller `MedicationReminderScheduler`.

The current class is misnamed: `rescheduleAll()` already handles every
habit's daily-time and interval alarm app-wide, plus a habit follow-up
suppressor that has nothing to do with meds. The rename is overdue and
independent of this spec; medication extraction is the forcing function.

Post-split:

- `HabitReminderScheduler` — `scheduleDailyTime(habit)`, `scheduleNext(…)`,
  `rescheduleAll()`, `rescheduleAllDailyTime()`, `getFollowUpTimeIfSuppressed`,
  `scheduleDelayedHabitFollowUp`. Request-code offsets stay the same
  (`+200_000` for interval, `+900_000` for daily-time).
- `MedicationReminderScheduler` (new) — takes `MedicationDao`, reads the
  `medications` table directly. Per-medication alarms for:
  - `TIMES_OF_DAY` mode → `scheduleForTimeOfDay(med, todId)`, one alarm per
    `(med.id, tod)` bucket.
  - `SPECIFIC_TIMES` mode → `scheduleSpecificTime(med, HH:mm)`, request-code
    offset `+400_000 + (med.id % 1000) * 10 + slotIndex` (new namespace;
    deliberately distinct from the existing `+300_000` offset, which will
    be cancelled during the migration).
  - `INTERVAL` mode → `scheduleNextInterval(med, completedAt)` after the
    last dose was logged.
- `BootReceiver` is updated to call BOTH schedulers' `rescheduleAll()`.
- `MedicationReminderReceiver` is rewritten to resolve by `medicationId`
  (new `"medicationId"` extra) instead of by `habitId`. Existing alarms
  scheduled by the old scheduler with `habitId` fire once after upgrade
  and dispatch through the legacy path (kept around for ~2 weeks) until
  they re-register themselves under the new scheduler.

**Pros:** cleanest separation, fixes naming debt, new code is easy to
test in isolation.
**Cons:** largest surface area to change, two-class split means two
`@Inject` sites to update across repositories / VMs / tests. Realistically
~45–75 min of Claude Code runtime, medium PR size.

### 5.2 Option B — Keep dual-purpose via a sealed discriminator

Keep the class named `MedicationReminderScheduler`, add a
`sealed interface AlarmTarget { data class Habit(…); data class Medication(…) }`
and route by target. No split, no rename.

**Pros:** least code churn.
**Cons:** entrenches the misnaming further. Every future reader still has
to understand that a class named "Medication*" schedules most of the app's
habit alarms. Fails the spec goal of "clean separation."

### 5.3 Option C — Translation layer during migration

Keep the existing class mostly intact. Add a
`habitId → medicationId` map in `BackendSyncPreferences` populated by the
migration (built-in-"Medication"-habit-id → null; other interval habits →
their corresponding new medication id). Existing scheduled alarms with
`habitId` keep firing; receiver translates `habitId` → `medicationId`
before dispatching the notification.

**Pros:** zero alarm gap during migration. Existing `PendingIntent`s keep
working.
**Cons:** the translation layer is a permanent piece of debt — or requires
its own follow-up removal migration. Naming is unchanged.

### 5.4 Recommendation: Option A

The naming debt is real, the split surfaces a cleaner mental model, and the
alarm-gap risk (see §5.1 fallback: legacy receiver path for ~2 weeks) is
manageable. Worst case a user misses one dose reminder in the upgrade
window — flagged in the release notes.

---

## 6. UI surfaces

### 6.1 New screens

Most code already exists; this spec mostly re-wires the data source.

- **`MedicationsScreen`** (formerly `MedicationScreen`) — the list of
  medications, edit mode, reminder schedule settings. Rewrite the
  `MedicationViewModel` to consume `MedicationRepository` instead of
  `SelfCareRepository`. The existing Compose tree stays ~95% intact —
  same tier chips, same time-of-day grouping, same empty state. The
  `SelfCareStepEntity` parameter types become `MedicationEntity`.
- **`MedicationEditorScreen`** (new, replaces the `MedDialog` bottom sheet)
  — dedicated full-screen editor with schedule mode picker, pill/refill
  fields, tier, notes. Reuses `MedDialog`'s body as a starting shape.
- **`MedicationLogScreen`** — stays, re-source to `MedicationDoseDao`.
- **`MedicationRefillScreen`** — stays, now reads directly from
  `MedicationEntity` (refill fields are inline).

### 6.2 Nav registration

In `NavGraph.kt`, add:

```kotlin
BottomNavItem(
    PrismTaskRoute.Medication.route,
    "Meds",
    Icons.Filled.Medication,          // Material Symbol exists at AGP 9.1
    Icons.Outlined.Medication
)
```

Place between `HabitsRecurring` and `Timer` in `ALL_BOTTOM_NAV_ITEMS`. The
route itself already exists (`PrismTaskRoute.Medication = "medication"`),
just needs to become a nav-tab destination in the `HorizontalPager` `when`
at `NavGraph.kt:515`.

### 6.3 Settings toggle wiring

`HabitListPreferences.isMedicationEnabled()` already exists and already
gates the `SelfCareItem("medication")` tile in `HabitListViewModel.items`.
The new wiring:

1. `MainActivity` (or wherever `PrismTaskNavGraph` is composed) reads
   `isMedicationEnabled` and computes `hiddenTabs`:
   `if (!medicationEnabled) hiddenTabs += PrismTaskRoute.Medication.route`.
2. Existing `ModesSection.kt:24` `ModeToggleRow("Medication", …)` stays;
   no section change.
3. `HabitListViewModel.items` REMOVES the `SelfCareItem("medication")`
   branch (lines 206-208) — medications no longer appear on the Habits
   tab at all. Existing built-in "Medication" habit row (deleted by
   Phase 2 migration) is also no longer rendered. This is a visible
   UX change.

### 6.4 Widget updates — NONE required

Confirmed by grep: no widget currently references medication data.

### 6.5 Daily Essentials card

`MedicationStatusUseCase.observeDueDosesToday()` rewrite:

- Remove `intervalDoses(…)` (reads from habits).
- Remove `selfCareStepDoses(…)` (reads from self_care_steps).
- Remove `specificTimeDoses(…)` (reads from MedicationPreferences).
- Replace with `combine(medicationRepo.observeActive(),
  medicationDoseRepo.observeForDate(todayLocal)) { meds, doses → … }` that
  expands each `MedicationEntity`'s schedule into `MedicationDose` entries
  and marks `takenToday = doses.any { it.medicationId == med.id && it.slotKey == slot }`.
- Slot-grouping via `MedicationSlotGrouper` is unchanged — the
  `MedicationDose` data class stays compatible.

---

## 7. Built-in seeder changes

### 7.1 Stop seeding Medication from `SelfCareRepository.ensureHabitsExist`

`SelfCareRepository.ensureHabitsExist()` currently ensures Morning, Bedtime,
Medication, Housework habits. Remove the `"medication"` branch:

```kotlin
suspend fun ensureHabitsExist() {
    getOrCreateHabit("morning")
    getOrCreateHabit("bedtime")
    // getOrCreateHabit("medication")  <- removed
    getOrCreateHabit("housework")
}
```

Similarly, `getOrCreateHabit`'s `"medication"` cases in the `when`
expressions (lines 898, 907, 913, 918, 924, 931) are deleted. The
`MEDICATION_HABIT_NAME` constant becomes unused and is also removed.

### 7.2 New `BuiltInMedicationReconciler`

New file mirroring `BuiltInHabitReconciler.kt` shape:

```kotlin
@Singleton
class BuiltInMedicationReconciler @Inject constructor(
    private val medicationDao: MedicationDao,
    private val medicationDoseDao: MedicationDoseDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val migrationPrefs: MedicationMigrationPreferences,
    private val syncTracker: SyncTracker,
    private val logger: PrismSyncLogger
) {
    suspend fun reconcileAfterSyncIfNeeded() {
        if (migrationPrefs.isReconciliationDone()) return
        try {
            mergeDuplicatesByName()
        } finally {
            migrationPrefs.setReconciliationDone(true)
        }
    }

    private suspend fun mergeDuplicatesByName() {
        val all = medicationDao.getAllOnce()
        val groups = all.groupBy { it.name.trim().lowercase() }
        for ((_, meds) in groups) {
            if (meds.size <= 1) continue
            val withCounts = meds.map { m -> m to medicationDoseDao.countForMedOnDateLocalOnce(m.id) }
            val keeper = withCounts.maxByOrNull { it.second }!!.first
            val losers = meds.filter { it.id != keeper.id }
            for (loser in losers) {
                medicationDoseDao.reassignMedicationId(loser.id, keeper.id)
                // transfer cloud_id to keeper if keeper has none
                // …
                medicationDao.delete(loser)
            }
        }
    }
}
```

Called from the same app-start coordinator that already calls
`BuiltInHabitReconciler.reconcileAfterSyncIfNeeded()`.

### 7.3 Template seeder (optional, Phase 2)

If we want to ship with a handful of default meds for new users, add a
`BuiltInMedicationSeeder` (parallel to `TemplateSeeder`) that seeds nothing
by default (empty list) but reserves the pattern. Not required for v1.

---

## 8. Effort estimate

| Area | PR count | Claude Code runtime (estimate per my standing preference) |
|---|---|---|
| Room + migration + entities + DAOs | 1 | 45–75 min (large multi-file) |
| `MedicationRepository` + `MedicationSyncMapper` + `SyncService` changes | 1 | 20–45 min |
| Reminder scheduler split (Option A) | 1 | 45–75 min |
| UI: new nav tile + screen rewiring + settings toggle coupling | 1 | 20–45 min |
| `BuiltInMedicationReconciler` + `MedicationMigrationPreferences` | (folded into 1) | +10 min |
| Widgets | 0 | — |
| Tests — unit (migration, reconciler, scheduler) | (folded into above) | +30 min across PRs |
| Tests — instrumentation (migration correctness, two-device convergence) | 1 | 45–75 min |
| **Phase 2 cleanup migration** (drops source tables after convergence window) | 1 (deferred) | 20–45 min |

**Total for the "landable v1" slice (PRs 1–5): ~3–5 hours of Claude Code runtime,
5 PRs.** Plus a Phase 2 cleanup PR 2–4 weeks later.

---

## 9. Risks and rollback

| Risk | Severity | Mitigation |
|---|---|---|
| Migration JSON1 availability on min SDK 26 | High | Verify with a device test on API 26 before landing; fall back to Kotlin-side migration pass via `RoomDatabase.Callback.onOpen` if needed |
| Cross-device race: device A migrates, B doesn't, dose edits diverge | High | Dual-write shim during convergence window; 2-week `isSourceDataPurgedPhase2=false` guard; `BuiltInMedicationReconciler` runs on every post-sync pull |
| Reminder continuity gap at upgrade | Medium | Legacy `MedicationReminderReceiver` path kept alive for 2 weeks; new alarms registered at first launch post-upgrade; release note tells users to open the app once after update |
| Firestore rules missed at deploy | ~~High~~ Low | Rules use a `/users/{uid}/{document=**}` wildcard — new collections are allowed without any rule change. Risk flips to "someone un-wildcards before we ship" — covered by post-release client-side sync check (§2.4) |
| FK cascade wipes history on archive misclick | Medium | UI primary affordance is "Archive", not "Delete"; Delete confirmation dialog warns about dose history loss |
| `medication_refills` data loss during migration | Low | Quarantine table preserves every refill row; inline backfill into `medications` validates nothing was dropped |
| Phase 2 cleanup deletes source data that user had re-edited | High | Phase 2 migration REFUSES to drop rows if `self_care_steps.updated_at > medications.updated_at`; requires manual re-sync first |
| Room schema-hash mismatch after adding quarantine tables | Low | Pattern from Fix D (§3.2) — Room's identity check ignores unregistered tables |
| v54 → v53 rollback | N/A | Not supported. `fallbackToDestructiveMigrationOnDowngrade()` is the default. Release must not need rollback. |

---

## 10. Phase placement

**Resolved (2026-04-22): Phase B, gated on Fix D execution completing.**

Context from git history at the time of this spec:

- Phase A closeout shipped (PRs #616 / #617, 2026-04-22 / 04-21).
- Phase 2 sync hardening shipped (CHANGELOG entry via #616).
- Phase 3 Fix D is actively in flight — plan + pre-execution snapshot
  merged (PR #605, 2026-04-21), scripts merged (PR #608), dry-run findings
  amended into the plan (commit `7520b213`, 2026-04-21). Execution against
  prod has NOT yet run. `quarantine_task_completions_null_taskid` is
  documented but not yet in `Migrations.kt`.
- Phase 3 Firestore cleanup runbook merged (PR #609, 2026-04-22).

Implementation gating:

1. **Do not start this spec's implementation until Fix D executes
   end-to-end against prod.** Two reasons. (a) Fix D's quarantine pattern
   becomes the in-code reference for §3.2 step B — easier to copy from a
   landed migration than from a doc. (b) Phase 3's active Firestore dedup
   scripts would flag `medications` / `medication_doses` as "unknown
   collections" if they appear during Phase 3; queue behind the cleanup
   completion to avoid script false-positives.
2. **Phase B scope placement.** This spec is architectural prep, not
   launch-path. Phase B is the natural home for refactors that unlock
   future features (refill adherence analytics, clinical export, pharmacy
   integrations) without being user-facing launches themselves.

Considered-and-rejected phases:

| Phase | Why not |
|---|---|
| Phase A2 | Reads as closeout. Migration-heavy cross-device work doesn't belong in a closeout slice. |
| Phase 3 (concurrent with Fix D) | See gating reason #1. |
| Phase F.0 | Fits the scheduler rename (§5) as a standalone small PR, but not the migration. Could cherry-pick just the scheduler rename to F.0 if timing demands, though I'd keep them together for coherence. |
| Phase F | Would work if Phase F is pre-launch architecture lock-in, but then rollback-impossibility + 2-week convergence window would bleed into launch risk. |

Escape hatches:

- If v2.0 launch is < 4 weeks out when Fix D finishes, **defer to
  post-v2.0**. Phase 2 cleanup of this spec needs 2+ weeks of convergence
  window per device before source tables can be dropped, and that window
  can't overlap a Play Store release.
- If Fix D slips >2 weeks, the scheduler rename (§5) could land standalone
  as a Phase F.0 candidate and the migration waits.

---

## 11. Test plan

### 11.1 Unit tests

- `MIGRATION_53_54_Test` — Room `MigrationTestHelper`, seeds v53 schema
  with:
  - 3 `self_care_steps` rows (2 with `medication_name`, 1 with only
    `label`)
  - 2 `self_care_logs` rows with mixed-format `completed_steps` JSON
    (bare strings + objects)
  - 1 `medication_refills` row linked by name
  - 1 built-in "Medication" habit row
  - Asserts post-migration:
    - 3 `medications` rows with correct `times_of_day` aggregation
    - ~5 `medication_doses` rows (matches the JSON entries)
    - Refill fields merged inline into the matching `medications` row
    - Source rows untouched
- `BuiltInMedicationReconcilerTest` — dedup by name, winner by dose count,
  dose reassignment, sync_metadata cloud_id transfer. Mirrors
  `BuiltInHabitReconcilerTest` line-for-line.
- `MedicationReminderSchedulerTest` (new smaller scheduler) — request-code
  uniqueness across medications, cancel-by-medication-id, scheduleForTimeOfDay
  honors day-start-hour rollover.
- `MedicationStatusUseCaseTest` (rewrite existing) — due doses computed
  from new `MedicationEntity` + `MedicationDoseEntity`, dedup-by-name
  invariant preserved.
- `MedicationRepositoryTest` — CRUD, `syncTracker` is called on every
  write, `logDose` + `unlogDose` round-trip correctly.

### 11.2 Instrumentation tests

- `MedicationMigrationIntegrationTest` — opens a Room DB with v53 seed data
  (same fixture as unit test), performs the migration, verifies via
  `MedicationDao.getAllOnce()` that the expected rows land. Runs on API 26
  emulator (JSON1 check).
- `TwoDeviceMedicationMigrationTest` (new, hardest) — two Room DB instances
  pointing at different file paths, both seeded at v53, both sharing a
  Firestore emulator-backed sync layer. Device A migrates, pushes. Device
  B edits a `self_care_step`. Device A's reconciler runs. Assert both
  devices converge with no dose loss and no duplicate `medications` rows.
- `MedicationReminderContinuityTest` — Robolectric. Seed a v53 DB with
  a scheduled alarm via the old scheduler, run migration, assert the
  alarm still fires AND re-registers against the new scheduler on first
  receive.

### 11.3 Manual test cases (runbook)

- On a real device running v53 with existing medication data:
  - Upgrade to v54, open app, verify the Medication tab appears (toggle on)
  - Verify all previous medications visible under the new tab
  - Verify today's already-logged doses are still marked taken
  - Verify the Medication card still appears on the Today → Daily Essentials
  - Trigger a scheduled alarm (set a time 2 min in the future pre-upgrade),
    upgrade, wait for fire
- On a fresh install of v54:
  - Verify no built-in medications (unless §7.3 seeder ships)
  - Add a medication, log a dose, verify Daily Essentials card updates
- Settings toggle — turn "Medication" off, verify tab disappears from
  bottom nav AND medication card disappears from Today

### 11.4 CI gates

Migration test MUST be marked `@SmallTest` and run in the standard
`testDebugUnitTest` target so it gates every PR. Two-device test is
`@LargeTest` and runs nightly.

---

## Appendix A — Files touched (estimated)

| File | Change type |
|---|---|
| `app/src/main/java/com/averycorp/prismtask/data/local/entity/MedicationEntity.kt` | NEW |
| `app/src/main/java/com/averycorp/prismtask/data/local/entity/MedicationDoseEntity.kt` | NEW |
| `app/src/main/java/com/averycorp/prismtask/data/local/entity/MedicationRefillEntity.kt` | DELETE (after Phase 2) |
| `app/src/main/java/com/averycorp/prismtask/data/local/dao/MedicationDao.kt` | MODIFY (expand) |
| `app/src/main/java/com/averycorp/prismtask/data/local/dao/MedicationDoseDao.kt` | NEW |
| `app/src/main/java/com/averycorp/prismtask/data/local/database/Migrations.kt` | ADD `MIGRATION_53_54` |
| `app/src/main/java/com/averycorp/prismtask/data/local/database/PrismTaskDatabase.kt` | Update entities list, version, DAO accessor |
| `app/src/main/java/com/averycorp/prismtask/data/repository/MedicationRepository.kt` | NEW |
| `app/src/main/java/com/averycorp/prismtask/data/repository/SelfCareRepository.kt` | Remove `"medication"` branches |
| `app/src/main/java/com/averycorp/prismtask/data/remote/mapper/MedicationSyncMapper.kt` | NEW |
| `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt` | Add collection cases + pull/upload |
| `app/src/main/java/com/averycorp/prismtask/data/remote/sync/BackendSyncService.kt` | Parallel update |
| `app/src/main/java/com/averycorp/prismtask/data/remote/BuiltInMedicationReconciler.kt` | NEW |
| `app/src/main/java/com/averycorp/prismtask/data/preferences/MedicationMigrationPreferences.kt` | NEW |
| `app/src/main/java/com/averycorp/prismtask/data/preferences/MedicationPreferences.kt` | DELETE (after Phase 2) |
| `app/src/main/java/com/averycorp/prismtask/notifications/MedicationReminderScheduler.kt` | SPLIT → rename → `HabitReminderScheduler` + new smaller `MedicationReminderScheduler` |
| `app/src/main/java/com/averycorp/prismtask/notifications/MedicationReminderReceiver.kt` | MODIFY (resolve by medicationId) |
| `app/src/main/java/com/averycorp/prismtask/notifications/BootReceiver.kt` | Add new scheduler wire |
| `app/src/main/java/com/averycorp/prismtask/domain/usecase/MedicationStatusUseCase.kt` | Rewrite data sources |
| `app/src/main/java/com/averycorp/prismtask/domain/usecase/DailyEssentialsUseCase.kt` | Keep (consumer unchanged) |
| `app/src/main/java/com/averycorp/prismtask/ui/navigation/NavGraph.kt` | Add bottom nav item + pager case |
| `app/src/main/java/com/averycorp/prismtask/ui/screens/medication/*` | Rewire VMs to new repo |
| `app/src/main/java/com/averycorp/prismtask/ui/screens/habits/HabitListViewModel.kt` | Remove `SelfCareItem("medication")` |
| `app/src/main/java/com/averycorp/prismtask/data/export/DataExporter.kt` | Add `medications` + `medication_doses` sections |
| `app/src/main/java/com/averycorp/prismtask/data/export/DataImporter.kt` | Add import paths |
| `app/src/test/java/…/MIGRATION_53_54_Test.kt` | NEW |
| `app/src/test/java/…/BuiltInMedicationReconcilerTest.kt` | NEW |
| `app/src/test/java/…/MedicationRepositoryTest.kt` | NEW |
| `app/src/test/java/…/MedicationReminderSchedulerTest.kt` | MODIFY |
| `app/src/test/java/…/MedicationStatusUseCaseTest.kt` | MODIFY |
| `app/src/androidTest/java/…/MedicationMigrationIntegrationTest.kt` | NEW |
| `app/src/androidTest/java/…/TwoDeviceMedicationMigrationTest.kt` | NEW |
| `CHANGELOG.md` | Entry per PR |
| `CLAUDE.md` | Update DB version + nav count |
| **Firestore prod rules (console)** | OUT-OF-REPO step |

---

## Appendix B — Open questions for the implementer

1. ~~`"Medication"` built-in habit interval preservation.~~ **Resolved.**
   §4.4 `MedicationMigrationRunner` reads the habit row's
   `reminderIntervalMillis` + `MedicationPreferences` on first app-start
   post-migration and writes the preserved schedule onto every migrated
   medication. Telemetry in the runner surfaces whether the preservation
   path is load-bearing.
2. `MedicationRefillEntity` was added at DB v44 per CLAUDE.md. The
   migration at §3.2 assumes `medication_refills` exists at v53. Verify
   no intermediate migration has renamed it.
3. ~~Fix D quarantine pattern status.~~ **Resolved (2026-04-22).** Fix D
   is planned + dry-run + scripts-merged but not yet executed against
   prod. The `quarantine_*` tables do not yet exist in `Migrations.kt`.
   §10 gates this spec on Fix D execution completing — copy the pattern
   from landed code, not from the doc.
4. The §3.2 step C `REPLACE(GROUP_CONCAT(…), ',', ' / ')` assumes no
   literal `,` appears in any existing `self_care_steps.label` string.
   Run `SELECT COUNT(*) FROM self_care_steps WHERE routine_type='medication'
   AND label LIKE '%,%'` against a representative production DB before
   landing to confirm this is safe; if not, switch to a Kotlin migration
   pass that handles escaping.
