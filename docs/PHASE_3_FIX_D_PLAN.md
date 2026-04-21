# Phase 3 Fix D — Local Room Data Collapse Plan

> **Status:** Draft (read-only plan document).
> **Authoritative source:** this file. Phase 3 executes from here.
> **Author guardrail:** Do NOT run any SQL in this document against a live DB without first taking the backup described in [Prerequisites](#prerequisites).

---

## ⚠️ Column-name correction (must read before executing any SQL)

The original Fix D spec referred to a `remoteId` column. **The column Phase 2 actually created is named `cloud_id`.** This is not a typo drift — it is an intentional rename decided during Phase 2 implementation (commit `f7c92e44`) because `TaskTemplateEntity` already carried a `remoteId: Int?` field holding the backend-API numeric ID, which is orthogonal to the Firestore document ID.

- ✅ Use `cloud_id` (SQL column) / `cloudId` (Kotlin field) for the Firestore doc-ID column on all 14 syncable entities.
- ❌ `remoteId` on `task_templates` is the backend-API numeric ID. It is NOT the Firestore doc ID and must NOT be used as the canonical identity column for collapse.

Every SQL block in this document is written against `cloud_id`. If you see `remoteId` in a prior session transcript or in the audit output, mentally translate to `cloud_id` before executing.

---

## Summary

Fix D collapses the Room database in place so every natural-key group of duplicates reduces to a single canonical row, and every local foreign-key reference points at that canonical row. It runs entirely against Room: it does not touch Firestore, does not mutate `sync_metadata`, and does not change any application code. After Fix D runs, Room becomes the authoritative reference for the subsequent Firestore cleanup step.

**Expected row-count reductions.** Pre-execution counts come from a read-only snapshot of the production device pulled 2026-04-21 at 01:42 UTC (see [Pre-execution snapshot](#pre-execution-snapshot--2026-04-21)). **Winner target** = the row count Fix D produces after collapsing on the natural key stated in the winner rule — NOT `sync_metadata.cloud_id` distinctness, which is a separate and higher number (cloud_id-distinct counts are recorded in the snapshot section for sanity). The earlier Firestore audit numbers are kept in the third column for reference but are stale; the Room-side corruption has continued growing since the audit was taken.

| Table              | Live rows | Winner target | Audit reference | Notes                                         |
|--------------------|----------:|--------------:|----------------:|-----------------------------------------------|
| tasks              |     5,144 |          ~12† |         4,843→12 | Canonical by `(lower(trim(title)), createdAt)`. `†` Could drift up if new cloud pulls introduced distinct titles since audit — verify post-run via `GROUP BY lower(trim(title)) HAVING n > 1` |
| tags               |     9,656 |           ~3† |         2,165→3 | Lex-smallest cloud_id wins within each `lower(trim(name))` group |
| projects           |        93 |           ~2† |            16→2 | Lex-smallest cloud_id wins within each `trim(name)` group |
| habits             |     1,248 |           ~3† |            14→3 | Prefer `is_built_in=1`, then lex-smallest cloud_id within each `COALESCE(template_key, lower(trim(name)))` group |
| task_completions   |     6,966 |          ~77† |        1,170→77 | After quarantining 2,353 null-taskId rows, remaining 4,613 dedupe by `(task_id, completed_date)` with winner = smallest `completed_at_time`. Winner target scales with surviving task count (~12), so ~77 still plausible |
| task_templates     |       317 |           ~8† |            34→8 | Prefer `is_built_in=1`, then lex-smallest cloud_id within each `lower(trim(name))` group |
| habit_completions  |         8 |             0 |               7 | All dangling → drop                           |
| habit_logs         |       835 |             0 |             137 | All dangling → drop                           |
| courses            |        44 |       ~small† |             n/a | Collapse by `trim(name)`. Snapshot has 4 distinct cloud_ids — target is ≤ 4 (may be fewer if names collide) |
| course_completions |        66 |       ~small† |             n/a | Repoint `course_id`, then dedupe by `(date, course_id)`; snapshot has 6 distinct cloud_ids |
| leisure_logs       |         3 |             3 |             n/a | **Verified zero duplicate groups on snapshot — Step 5 executes as verify-only** |
| self_care_logs     |        38 |            38 |             n/a | **Verified zero duplicate groups on snapshot — Step 6 executes as verify-only** |
| self_care_steps    |        36 |            36 |             n/a | **Verified zero duplicate groups on snapshot — Step 7 executes as verify-only** |
| milestones         |         0 |             0 |             n/a | Empty; no work                                |
| task_tags          |         0 |             0 |               0 | Empty per Room audit — nothing to do          |

`†` = natural-key collapse target carried forward from the original audit, with current-snapshot context annotated. These are the authoritative post-Fix-D row counts the verify queries should match.

**What Fix D does:**
- Reduce duplicates in Room to canonical rows per the winner rules below.
- Repoint every local-ID foreign-key reference from a loser row to the winner.
- Quarantine the **2,353** `task_completions` rows with `task_id = NULL` (up from 467 in the earlier audit — corruption continued).
- Produce a Room database that, when uploaded, would push a clean canonical dataset to Firestore.

**What Fix D does NOT do:**
- Touch Firestore (done by a separate Phase 3 step after Fix D verifies clean).
- Touch `sync_metadata` (deprecated; removal scheduled for a later phase).
- Modify source code, run code migrations, or change Room version (already 52).
- Fix the deferred bugs (`lifeCategory` nulls, `templateKey` nulls).

---

## Pre-execution snapshot — 2026-04-21

Counts below came from a read-only snapshot of the physical device's DB (`adb exec-out run-as ... cat`), pulled into `.fixd-workspace/averytask.db` on the host. The device is at **DB version 51 (no `cloud_id` column yet)** — Phase 2 has not run on it. These counts are therefore "pre-Phase-2" numbers and represent the maximal work Fix D will face. Once Phase 2 + 2.5 install, the migration will backfill `cloud_id` (collision resolution may NULL some losers) but will not reduce row counts; Fix D still processes the same row population.

**Live Room row counts:**

```
tasks                        5,144
projects                        93
tags                         9,656
habits                       1,248
task_completions             6,966
  └─ of which task_id NULL   2,353  (→ quarantine)
task_templates                 317
habit_completions                8    (dangling → drop)
habit_logs                     835    (dangling → drop)
milestones                       0
courses                         44
course_completions              66
leisure_logs                     3    (zero dup groups)
self_care_logs                  38    (zero dup groups)
self_care_steps                 36    (zero dup groups)
task_tags                        0
sync_metadata               24,885
```

**`sync_metadata` — distinct cloud_id counts per entity type** (the target row counts after Fix D collapse should approach these for entities where the natural-key winner rule aligns with one-cloud-id-per-row):

| entity_type        | live_rows | sm_rows | distinct_cloud_ids | sm_collisions (sm_rows − distinct) |
|--------------------|----------:|--------:|-------------------:|-----------------------------------:|
| tag                |     9,656 |   9,656 |              2,165 |                              7,491 |
| task_completion    |     6,966 |   6,966 |              4,589 |                              2,377 |
| task               |     5,144 |   5,155 |              4,850 |                                305 |
| habit              |     1,248 |   1,248 |                 26 |                              1,222 |
| habit_log          |       835 |     835 |                 81 |                                754 |
| self_care_log      |        38 |     365 |                 38 |                                327 |
| task_template      |       317 |     317 |                 34 |                                283 |
| project            |        93 |     135 |                 16 |                                119 |
| course_completion  |        66 |      66 |                  6 |                                 60 |
| leisure_log        |         3 |      54 |                  6 |                                 48 |
| course             |        44 |      44 |                  4 |                                 40 |
| self_care_step     |        36 |      36 |                 36 |                                  0 |
| habit_completion   |         8 |       8 |                  3 |                                  5 |

**Orphan `sync_metadata` rows** (mapping pointed at a `local_id` that no longer exists in the entity table): `task=11`, `project=42`, `tag=0` (others unchecked — same LEFT-JOIN pattern if needed). These are harmless for migration 51→52's backfill (LEFT JOIN produces no match) and harmless for Fix D.

**Duplicate-group queries run on the snapshot:**
- `leisure_logs GROUP BY date HAVING COUNT(*) > 1` → **0 groups** (UNIQUE index on `date` held).
- `self_care_logs GROUP BY routine_type, date HAVING COUNT(*) > 1` → **0 groups** (UNIQUE index on `(routine_type, date)` held).
- `self_care_steps GROUP BY step_id, routine_type HAVING COUNT(*) > 1` → **0 groups** (no UNIQUE index but also no duplicates).

→ Steps 5, 6, 7 below are documented but execute only as pre-check assertions; no deletes will happen on this snapshot's data. Re-verify at execution time against a fresh snapshot in case post-Phase-2 pulls introduce any.

**Audit deltas** — the earlier Firestore audit numbers (shown in Summary table col 3) are all smaller than the live Room counts, consistent with continued client-side duplication between audit time and 2026-04-21. The biggest deltas:
- tasks: 4,843 → 5,144 (+301)
- tags: 2,165 → 9,656 (+7,491)
- habits: 14 → 1,248 (+1,234)
- task_completions: 1,170 → 6,966 (+5,796); null-taskId 467 → 2,353 (+1,886)
- task_templates: 34 → 317 (+283)

Two different denominators are in play and must not be conflated:

- **Cloud-doc count** (`COUNT(DISTINCT cloud_id)` in `sync_metadata`) — how many unique Firestore documents were ever created for this entity type. These match the distinct cloud_id counts in the cloud audit: tags 2,165, projects 16, tasks ~4,850, task_templates 34, etc. Fix D does NOT collapse down to this count — that is the *Firestore-cleanup-step* target, not the Fix-D target.
- **Natural-key winner count** (the "Winner target" column in the Summary table above) — how many rows survive after Fix D collapses on the entity's natural key (`lower(trim(title))`, `trim(name)`, etc.). These are much smaller: tasks ~12, tags ~3, projects ~2, habits ~3, task_templates ~8.

The gap between the two is the measure of the user's intent mismatch with the corrupted upload: the cloud has 2,165 tag docs, but the user ever only authored 3 distinct tag names. Fix D restores Room to that 3-row intent; the subsequent Firestore cleanup then prunes the 2,162 zombie docs.

---

## Prerequisites

1. **Phase 2 (Fix E + A + B + C) AND Phase 2.5 are deployed and verified clean.** Phase 2 created the `cloud_id` column; Phase 2.5 (commit `15e387b8`) actually populates it from the pull path and adds a one-shot restore from `sync_metadata`. Both must land in a build the device runs, and the app must be launched once post-install so migration 51→52 AND `SyncService.restoreCloudIdFromMetadata()` both fire.
   - **As of 2026-04-21 the production device is running v1.4.18 (build 662)**, which predates Phase 2. DB is at version 51 with no `cloud_id` column. Fix D cannot execute until ≥ build 663 is installed. Verify installed build before proceeding:
     ```powershell
     adb shell "dumpsys package com.averycorp.prismtask | grep -E 'versionCode|versionName'"
     # Expected: versionCode=663 (or higher), versionName=1.4.19 (or higher)
     ```
2. **Firestore is NOT yet cleaned up** — Fix D runs before Firestore cleanup. Rationale: after Fix D, the post-collapse Room DB becomes the authoritative reference used to decide which Firestore documents to retain.
3. **Room DB version is 52 AND `cloud_id` is populated.** Two separate checks:
   ```powershell
   # (a) Migration 51→52 ran — PRAGMA version is 52.
   # sqlite3 is not in the stock Android shell; push it to /data/local/tmp or pull the DB via
   # `adb exec-out run-as com.averycorp.prismtask cat .../averytask.db > local-copy.db` and query host-side.
   sqlite3 local-copy.db "PRAGMA user_version;"
   # Expected: 52

   # (b) cloud_id column exists and has non-null values on at least one
   # large table (Phase 2.5 restore has run):
   sqlite3 local-copy.db "
     SELECT 'tasks', SUM(cloud_id IS NOT NULL), COUNT(*) FROM tasks
     UNION ALL SELECT 'tags', SUM(cloud_id IS NOT NULL), COUNT(*) FROM tags;
   "
   # Expected: non-zero non-null counts on both. Per-row 1:1 mapping is
   # NOT required (collisions may have NULLed some). Zero non-nulls on
   # either table means Phase 2.5's restore has not run — launch the
   # app, sign in, wait ~10s, re-pull, re-check.
   ```
4. **App is not running.** Force-stop before any SQL executes:
   ```powershell
   adb shell am force-stop com.averycorp.prismtask
   ```
5. **Full DB backup pulled via adb.** Three files (wal + shm matter — skipping them will lose uncommitted pages):
   ```powershell
   $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
   $backupDir = "C:\Projects\averyTask\backups\pre-fixd-$stamp"
   New-Item -ItemType Directory -Force $backupDir | Out-Null
   adb shell "run-as com.averycorp.prismtask cp /data/data/com.averycorp.prismtask/databases/averytask.db     /sdcard/averytask.db"
   adb shell "run-as com.averycorp.prismtask cp /data/data/com.averycorp.prismtask/databases/averytask.db-wal /sdcard/averytask.db-wal"
   adb shell "run-as com.averycorp.prismtask cp /data/data/com.averycorp.prismtask/databases/averytask.db-shm /sdcard/averytask.db-shm"
   adb pull /sdcard/averytask.db     "$backupDir\averytask.db"
   adb pull /sdcard/averytask.db-wal "$backupDir\averytask.db-wal"
   adb pull /sdcard/averytask.db-shm "$backupDir\averytask.db-shm"
   adb shell "rm /sdcard/averytask.db /sdcard/averytask.db-wal /sdcard/averytask.db-shm"
   ```
6. **Sanity-check the backup** locally before touching the live DB:
   ```powershell
   sqlite3 "$backupDir\averytask.db" "PRAGMA integrity_check;"
   sqlite3 "$backupDir\averytask.db" "SELECT COUNT(*) FROM tasks;"          # expect ~5,144 (or current snapshot count)
   sqlite3 "$backupDir\averytask.db" "SELECT COUNT(*) FROM task_completions WHERE task_id IS NULL;"  # expect ~2,353
   ```
7. **`sync_metadata` remains intact** throughout Fix D. No row in `sync_metadata` is read or written. It is carried forward as-is for the deprecation phase.

---

## Collapse Order (dependency-aware)

Because every collapse step (a) repoints children to the winner, then (b) deletes losers, we must collapse parents **before** children would be affected, and we must NOT delete a parent whose children still reference it by local ID. `ON DELETE CASCADE` and `ON DELETE SET NULL` amplify the blast radius of any premature delete, so the order below is tuned to make every delete step safe.

**Principle:** for each parent table, *first repoint children, then delete losers*. Child tables can be collapsed on their own terms once their parent column is stable.

```
 1. quarantine task_completions with task_id IS NULL   (pre-step — isolate ~2,353 orphans per 2026-04-21 snapshot)
 2. habit_completions                                  (all dangling → drop; no dependents)
 3. habit_logs                                         (all dangling → drop; no dependents)
 4. tags                                               (leaf — no dependents except empty task_tags)
 5. leisure_logs                                       (leaf)
 6. self_care_logs                                     (leaf; already has (routine_type,date) unique index)
 7. self_care_steps                                    (leaf)
 8. courses                                            (before course_completions because its FK CASCADEs)
 9. course_completions                                 (depends on courses)
10. projects                                           (before tasks/milestones/task_completions — they reference projects.id)
11. habits                                             (before tasks — tasks.source_habit_id references habits.id)
12. milestones                                         (depends on projects; after projects are collapsed)
13. task_templates                                     (depends on projects via templateProjectId)
14. tasks                                              (depends on projects, habits; subtasks depend on parent task)
15. task_completions                                   (depends on tasks and projects; last)
16. (task_tags — skipped; table is empty per prior audit; verify then skip)
```

**Why this order works:**

- `task_completions` must be collapsed after `tasks` because its winner rule is `(task_id, completed_date)`; grouping cannot be meaningful until `task_id` values themselves have been consolidated onto winners.
- `tasks` must be collapsed after `projects` and `habits` because the tasks step repoints `project_id` and `source_habit_id` to winners — that mapping must exist in those parent tables first.
- `milestones` must come after `projects` because we repoint `project_id`; the `ON DELETE CASCADE` FK would otherwise delete milestones when loser projects are removed.
- `habit_completions` / `habit_logs` are handled **first** (dropped as dangling) rather than during/after habits because doing it first eliminates the need to repoint them — saves one full pass per table.
- `tags` is a leaf because the `task_tags` join is empty; if it were non-empty, `tags` would come after `tasks` with a repoint step on `task_tags.tagId` before delete.
- `courses` must come before `course_completions` because `course_completions` has an `ON DELETE CASCADE` FK on `course_id`; unlike `tasks→task_completions` (which is `SET NULL`), deleting a loser course would silently wipe its completions.
- `self_care_logs` has an existing `(routine_type, date)` UNIQUE index that will block any collapse INSERT if we weren't careful; we handle duplicates by picking winners and deleting losers rather than trying to merge into a new row.

---

## Per-Entity Collapse SQL

**Every block must run inside a single transaction** (`BEGIN IMMEDIATE;` / `COMMIT;` / `ROLLBACK;`). If any step inside a block fails a row-count check, the entire block rolls back and execution halts for human review.

**Every block assumes PRAGMA foreign_keys is ON.** Verify up-front:

```sql
PRAGMA foreign_keys = ON;  -- must be ON; SET_NULL / CASCADE behavior depends on it
PRAGMA foreign_keys;        -- expect 1
```

Each block below has four sub-sections:

1. **Identify** — dry-runnable `SELECT` that enumerates duplicate groups and flags the winner. Run and inspect before committing.
2. **Repoint** — `UPDATE` children of this entity to point at winners.
3. **Delete** — `DELETE` loser rows.
4. **Verify** — `SELECT COUNT(*)` with an expected value.

---

### Pre-step: quarantine `task_completions` rows with NULL task_id (~2,353 on 2026-04-21 snapshot)

These rows lost their `task_id` link during a race in pre-Phase-2 upload code. We isolate them before any other collapse step because the main `task_completions` collapse step groups by `(task_id, completed_date)` and would otherwise treat NULL-taskId rows as one giant duplicate group.

```sql
BEGIN IMMEDIATE;

-- Create quarantine table (off-Room; not a Room-managed table, so Room ignores it at startup).
CREATE TABLE IF NOT EXISTS quarantine_task_completions_null_taskid (
    original_id         INTEGER PRIMARY KEY,
    cloud_id            TEXT,
    project_id          INTEGER,
    completed_date      INTEGER NOT NULL,
    completed_at_time   INTEGER NOT NULL,
    priority            INTEGER NOT NULL,
    was_overdue         INTEGER NOT NULL,
    days_to_complete    INTEGER,
    tags                TEXT,
    quarantined_at      INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
);

-- Identify (expect ≈ 2,353 on the 2026-04-21 snapshot; original audit
-- said 467, corruption continued growing — re-check against a fresh
-- snapshot at execution time):
SELECT COUNT(*) AS null_taskid_count FROM task_completions WHERE task_id IS NULL;

-- Copy to quarantine:
INSERT INTO quarantine_task_completions_null_taskid
    (original_id, cloud_id, project_id, completed_date, completed_at_time,
     priority, was_overdue, days_to_complete, tags)
SELECT id, cloud_id, project_id, completed_date, completed_at_time,
       priority, was_overdue, days_to_complete, tags
FROM task_completions
WHERE task_id IS NULL;

-- Delete from live table:
DELETE FROM task_completions WHERE task_id IS NULL;

-- Verify:
SELECT COUNT(*) FROM task_completions WHERE task_id IS NULL;  -- expect 0
SELECT COUNT(*) FROM quarantine_task_completions_null_taskid; -- expect ≈ 2,353

COMMIT;
```

See [§ Handling the null-taskId task_completions](#handling-the-null-taskid-task_completions) for the tradeoff and recommendation on what to do with the quarantine afterward.

---

### Step 2 — `habit_completions` (drop all, dangling)

Per audit: all rows are dangling (their `habit_id` does not match any surviving habit after Phase-2 dedup, and none are salvageable). 2026-04-21 snapshot: 8 rows. Simplest to drop outright.

```sql
BEGIN IMMEDIATE;

-- Identify:
SELECT COUNT(*) AS before_count FROM habit_completions;  -- expect 8 (snapshot); 7 per audit

-- Delete:
DELETE FROM habit_completions;

-- Verify:
SELECT COUNT(*) FROM habit_completions;  -- expect 0

COMMIT;
```

---

### Step 3 — `habit_logs` (drop all, dangling)

Per audit: all rows are dangling. 2026-04-21 snapshot: 835 rows (up from 137 at audit time). Drop.

```sql
BEGIN IMMEDIATE;

-- Identify:
SELECT COUNT(*) AS before_count FROM habit_logs;  -- expect 835 (snapshot); 137 per audit

-- Delete:
DELETE FROM habit_logs;

-- Verify:
SELECT COUNT(*) FROM habit_logs;  -- expect 0

COMMIT;
```

---

### Step 4 — `tags` (group by `lower(trim(name))`, winner = lex-smallest cloud_id)

The `task_tags` join is empty per prior Room audit, so no repoint step is needed. Verify before skipping.

```sql
BEGIN IMMEDIATE;

-- Confirm task_tags is empty (spec assumption):
SELECT COUNT(*) AS task_tags_rows FROM task_tags;  -- expect 0; halt if nonzero

-- Identify duplicate groups:
SELECT lower(trim(name))                                     AS norm_name,
       COUNT(*)                                              AS n,
       MIN(CASE WHEN cloud_id IS NOT NULL THEN cloud_id END) AS winner_cloud_id,
       GROUP_CONCAT(id)                                      AS local_ids,
       GROUP_CONCAT(cloud_id)                                AS cloud_ids
FROM tags
GROUP BY lower(trim(name))
HAVING n > 1
ORDER BY n DESC;

-- Stage winner per group into a temp table.
-- Winner: the row whose cloud_id is lex-smallest (NULLs sort first in ASC; exclude NULL cloud_id from min)
DROP TABLE IF EXISTS _tag_winners;
CREATE TEMP TABLE _tag_winners AS
SELECT lower(trim(t.name)) AS norm_name,
       (SELECT id FROM tags t2
         WHERE lower(trim(t2.name)) = lower(trim(t.name))
         ORDER BY (CASE WHEN t2.cloud_id IS NULL THEN 1 ELSE 0 END), t2.cloud_id, t2.id
         LIMIT 1) AS winner_id
FROM tags t
GROUP BY lower(trim(t.name));

-- (If task_tags were non-empty, we would repoint here:)
-- UPDATE task_tags SET tagId = (SELECT winner_id FROM _tag_winners w WHERE w.norm_name = (SELECT lower(trim(name)) FROM tags WHERE id = task_tags.tagId))
-- WHERE tagId NOT IN (SELECT winner_id FROM _tag_winners);

-- Delete losers:
DELETE FROM tags WHERE id NOT IN (SELECT winner_id FROM _tag_winners);

-- Verify (expect ~3 per audit; see rollback-plan halt thresholds for drift tolerance):
SELECT COUNT(*) AS after_count FROM tags;

DROP TABLE _tag_winners;
COMMIT;
```

---

### Step 5 — `leisure_logs` (verify-only; snapshot had zero duplicates)

The table has `UNIQUE(date)` per `LeisureLogEntity`, which held on the 2026-04-21 snapshot — zero duplicate-`date` groups. **Run the identification query only; if it returns zero rows, skip the collapse and proceed to Step 6.** The SQL below is kept for completeness in case a later snapshot shows drift.

```sql
BEGIN IMMEDIATE;

-- Identify duplicate groups:
SELECT date, COUNT(*) AS n, GROUP_CONCAT(id) AS local_ids
FROM leisure_logs
GROUP BY date
HAVING n > 1;

DROP TABLE IF EXISTS _leisure_winners;
CREATE TEMP TABLE _leisure_winners AS
SELECT date, MIN(id) AS winner_id FROM leisure_logs GROUP BY date;

-- No dependents — delete losers directly:
DELETE FROM leisure_logs
 WHERE id NOT IN (SELECT winner_id FROM _leisure_winners);

-- Verify: COUNT(*) == COUNT(DISTINCT date)
SELECT COUNT(*) AS n_rows, COUNT(DISTINCT date) AS n_dates FROM leisure_logs;

DROP TABLE _leisure_winners;
COMMIT;
```

---

### Step 6 — `self_care_logs` (verify-only; snapshot had zero duplicates)

Has `UNIQUE(routine_type, date)` index per `SelfCareLogEntity`, which held on the 2026-04-21 snapshot — zero duplicate-group rows. **Run the identification query only; if it returns zero, skip the collapse.** SQL kept for completeness.

```sql
BEGIN IMMEDIATE;

-- Identify:
SELECT routine_type, date, COUNT(*) AS n, GROUP_CONCAT(id) AS local_ids
FROM self_care_logs
GROUP BY routine_type, date
HAVING n > 1;

DROP TABLE IF EXISTS _scl_winners;
CREATE TEMP TABLE _scl_winners AS
SELECT routine_type, date, MIN(id) AS winner_id
FROM self_care_logs
GROUP BY routine_type, date;

DELETE FROM self_care_logs
 WHERE id NOT IN (SELECT winner_id FROM _scl_winners);

-- Verify:
SELECT COUNT(*) AS n_rows,
       COUNT(DISTINCT routine_type || '|' || date) AS n_pairs
FROM self_care_logs;

DROP TABLE _scl_winners;
COMMIT;
```

---

### Step 7 — `self_care_steps` (verify-only; snapshot had zero duplicates)

Group key is `(step_id, routine_type)`. No UNIQUE index on this pair, but the 2026-04-21 snapshot returned zero duplicate groups anyway. **Run the identification query only; if it returns zero, skip the collapse.** SQL kept for completeness in case a later snapshot shows drift.

```sql
BEGIN IMMEDIATE;

-- Identify:
SELECT step_id, routine_type, COUNT(*) AS n, GROUP_CONCAT(id) AS local_ids
FROM self_care_steps
GROUP BY step_id, routine_type
HAVING n > 1;

DROP TABLE IF EXISTS _scs_winners;
CREATE TEMP TABLE _scs_winners AS
SELECT step_id, routine_type, MIN(id) AS winner_id
FROM self_care_steps
GROUP BY step_id, routine_type;

DELETE FROM self_care_steps
 WHERE id NOT IN (SELECT winner_id FROM _scs_winners);

-- Verify:
SELECT COUNT(*) AS n_rows,
       COUNT(DISTINCT step_id || '|' || routine_type) AS n_pairs
FROM self_care_steps;

DROP TABLE _scs_winners;
COMMIT;
```

---

### Step 8 — `courses` (group by `trim(name)`, winner = smallest cloud_id)

Must repoint `course_completions.course_id` before delete because the FK is `ON DELETE CASCADE`.

```sql
BEGIN IMMEDIATE;

-- Identify:
SELECT trim(name) AS norm_name, COUNT(*) AS n,
       GROUP_CONCAT(id) AS local_ids,
       GROUP_CONCAT(cloud_id) AS cloud_ids
FROM courses
GROUP BY trim(name)
HAVING n > 1;

DROP TABLE IF EXISTS _course_winners;
CREATE TEMP TABLE _course_winners AS
SELECT trim(c.name) AS norm_name,
       (SELECT id FROM courses c2
         WHERE trim(c2.name) = trim(c.name)
         ORDER BY (CASE WHEN c2.cloud_id IS NULL THEN 1 ELSE 0 END), c2.cloud_id, c2.id
         LIMIT 1) AS winner_id
FROM courses c
GROUP BY trim(c.name);

-- Repoint course_completions BEFORE deleting loser courses (FK is CASCADE):
UPDATE course_completions
   SET course_id = (
       SELECT winner_id FROM _course_winners w
        WHERE w.norm_name = (SELECT trim(name) FROM courses WHERE id = course_completions.course_id)
   )
 WHERE course_id NOT IN (SELECT winner_id FROM _course_winners);

-- Delete loser courses:
DELETE FROM courses WHERE id NOT IN (SELECT winner_id FROM _course_winners);

-- Verify no orphaned course_completions (should be 0; FK would have prevented but sanity check):
SELECT COUNT(*) FROM course_completions cc
 LEFT JOIN courses c ON c.id = cc.course_id
 WHERE c.id IS NULL;  -- expect 0

DROP TABLE _course_winners;
COMMIT;
```

---

### Step 9 — `course_completions`

After Step 8, `course_completions.course_id` points at winners. The existing `UNIQUE(date, course_id)` index will have collapsed any duplicates implicitly if the repoint produced collisions — SQLite will raise a constraint violation, not silently dedupe. Handle defensively by pre-deduping the repoint target.

```sql
BEGIN IMMEDIATE;

-- Identify potential duplicates after Step 8's repoint:
SELECT date, course_id, COUNT(*) AS n, GROUP_CONCAT(id) AS local_ids
FROM course_completions
GROUP BY date, course_id
HAVING n > 1;

DROP TABLE IF EXISTS _cc_winners;
CREATE TEMP TABLE _cc_winners AS
SELECT date, course_id, MIN(id) AS winner_id
FROM course_completions
GROUP BY date, course_id;

DELETE FROM course_completions
 WHERE id NOT IN (SELECT winner_id FROM _cc_winners);

-- Verify:
SELECT COUNT(*) AS n_rows,
       COUNT(DISTINCT date || '|' || course_id) AS n_pairs
FROM course_completions;

DROP TABLE _cc_winners;
COMMIT;
```

> **Note:** If Step 8's repoint would have triggered a constraint violation mid-update (two losers with same `date` but different `course_id` pointing at the same winner), it will have aborted Step 8's transaction. In that case, run Step 9's dedupe first on the raw pre-repoint state, then retry Step 8. Flagged in [Unresolved Questions](#unresolved-questions).

---

### Step 10 — `projects` (group by `trim(name)`, winner = lex-smallest cloud_id)

Repoint `tasks.project_id`, `milestones.project_id`, `task_completions.project_id`, and `task_templates.templateProjectId` before delete.

```sql
BEGIN IMMEDIATE;

-- Identify (expect 16 → 2):
SELECT trim(name) AS norm_name, COUNT(*) AS n,
       GROUP_CONCAT(id) AS local_ids,
       GROUP_CONCAT(cloud_id) AS cloud_ids
FROM projects
GROUP BY trim(name)
HAVING n > 1;

DROP TABLE IF EXISTS _proj_winners;
CREATE TEMP TABLE _proj_winners AS
SELECT trim(p.name) AS norm_name,
       (SELECT id FROM projects p2
         WHERE trim(p2.name) = trim(p.name)
         ORDER BY (CASE WHEN p2.cloud_id IS NULL THEN 1 ELSE 0 END), p2.cloud_id, p2.id
         LIMIT 1) AS winner_id
FROM projects p
GROUP BY trim(p.name);

-- Repoint children (order doesn't matter among these — none share FKs with each other that would cascade):
UPDATE tasks
   SET project_id = (SELECT winner_id FROM _proj_winners w
                      WHERE w.norm_name = (SELECT trim(name) FROM projects WHERE id = tasks.project_id))
 WHERE project_id IS NOT NULL
   AND project_id NOT IN (SELECT winner_id FROM _proj_winners);

UPDATE milestones
   SET project_id = (SELECT winner_id FROM _proj_winners w
                      WHERE w.norm_name = (SELECT trim(name) FROM projects WHERE id = milestones.project_id))
 WHERE project_id NOT IN (SELECT winner_id FROM _proj_winners);

UPDATE task_completions
   SET project_id = (SELECT winner_id FROM _proj_winners w
                      WHERE w.norm_name = (SELECT trim(name) FROM projects WHERE id = task_completions.project_id))
 WHERE project_id IS NOT NULL
   AND project_id NOT IN (SELECT winner_id FROM _proj_winners);

UPDATE task_templates
   SET templateProjectId = (SELECT winner_id FROM _proj_winners w
                             WHERE w.norm_name = (SELECT trim(name) FROM projects WHERE id = task_templates.templateProjectId))
 WHERE templateProjectId IS NOT NULL
   AND templateProjectId NOT IN (SELECT winner_id FROM _proj_winners);

-- Delete loser projects:
DELETE FROM projects WHERE id NOT IN (SELECT winner_id FROM _proj_winners);

-- Verify (expect ~2 per audit; halt threshold: >20 or <1):
SELECT COUNT(*) AS after_count FROM projects;

-- Verify no orphan references:
SELECT COUNT(*) FROM tasks             WHERE project_id IS NOT NULL AND project_id NOT IN (SELECT id FROM projects);  -- expect 0
SELECT COUNT(*) FROM milestones        WHERE project_id NOT IN (SELECT id FROM projects);                             -- expect 0
SELECT COUNT(*) FROM task_completions  WHERE project_id IS NOT NULL AND project_id NOT IN (SELECT id FROM projects);  -- expect 0
SELECT COUNT(*) FROM task_templates    WHERE templateProjectId IS NOT NULL AND templateProjectId NOT IN (SELECT id FROM projects);  -- expect 0

DROP TABLE _proj_winners;
COMMIT;
```

---

### Step 11 — `habits` (group by `COALESCE(templateKey, lower(trim(name)))`, winner prefers `is_built_in=1` then lex-smallest cloud_id)

`habit_completions` and `habit_logs` have already been dropped. `tasks.source_habit_id` is NOT a formal FK — it is a plain nullable column — so we must repoint manually.

```sql
BEGIN IMMEDIATE;

-- Identify (expect 14 → 3):
SELECT COALESCE(template_key, lower(trim(name))) AS norm_key,
       COUNT(*) AS n,
       GROUP_CONCAT(id) AS local_ids,
       GROUP_CONCAT(is_built_in) AS built_in_flags,
       GROUP_CONCAT(cloud_id) AS cloud_ids
FROM habits
GROUP BY COALESCE(template_key, lower(trim(name)))
HAVING n > 1;

DROP TABLE IF EXISTS _habit_winners;
CREATE TEMP TABLE _habit_winners AS
SELECT COALESCE(h.template_key, lower(trim(h.name))) AS norm_key,
       (SELECT id FROM habits h2
         WHERE COALESCE(h2.template_key, lower(trim(h2.name))) = COALESCE(h.template_key, lower(trim(h.name)))
         ORDER BY h2.is_built_in DESC,                                        -- prefer built-in
                  (CASE WHEN h2.cloud_id IS NULL THEN 1 ELSE 0 END),          -- then non-null cloud_id
                  h2.cloud_id,                                                -- then lex-smallest
                  h2.id                                                       -- final tiebreak
         LIMIT 1) AS winner_id
FROM habits h
GROUP BY COALESCE(h.template_key, lower(trim(h.name)));

-- Repoint tasks.source_habit_id (NOT a formal FK, so no cascade — manual update is the only way):
UPDATE tasks
   SET source_habit_id = (
       SELECT winner_id FROM _habit_winners w
        WHERE w.norm_key = (SELECT COALESCE(template_key, lower(trim(name))) FROM habits WHERE id = tasks.source_habit_id)
   )
 WHERE source_habit_id IS NOT NULL
   AND source_habit_id NOT IN (SELECT winner_id FROM _habit_winners);

-- Delete loser habits. habit_completions & habit_logs are already empty (Steps 2–3).
DELETE FROM habits WHERE id NOT IN (SELECT winner_id FROM _habit_winners);

-- Verify (expect ~3 per audit; snapshot's sync_metadata shows 26 distinct cloud_ids, so natural-key collapse could land anywhere in ~3–26 depending on name dedup):
SELECT COUNT(*) AS after_count FROM habits;

-- Verify no orphan source_habit_id:
SELECT COUNT(*) FROM tasks
 WHERE source_habit_id IS NOT NULL
   AND source_habit_id NOT IN (SELECT id FROM habits);  -- expect 0

DROP TABLE _habit_winners;
COMMIT;
```

---

### Step 12 — `milestones`

After Step 10's repoint, `milestones.project_id` points at winners. If the Firestore audit surfaced duplicate milestones within a project (same title, same project after project collapse), dedupe by `(project_id, title)`. The audit did not supply a target number — identify and report.

```sql
BEGIN IMMEDIATE;

-- Identify:
SELECT project_id, title, COUNT(*) AS n, GROUP_CONCAT(id) AS local_ids
FROM milestones
GROUP BY project_id, title
HAVING n > 1;

DROP TABLE IF EXISTS _ms_winners;
CREATE TEMP TABLE _ms_winners AS
SELECT project_id, title, MIN(id) AS winner_id
FROM milestones
GROUP BY project_id, title;

DELETE FROM milestones
 WHERE id NOT IN (SELECT winner_id FROM _ms_winners);

-- Verify:
SELECT COUNT(*) AS n_rows,
       COUNT(DISTINCT project_id || '|' || title) AS n_pairs
FROM milestones;

DROP TABLE _ms_winners;
COMMIT;
```

---

### Step 13 — `task_templates` (group by `lower(trim(name))`, winner prefers `is_built_in=1` then lex-smallest cloud_id)

`templateProjectId` has already been repointed in Step 10.

```sql
BEGIN IMMEDIATE;

-- Identify (snapshot has 317 rows; expect collapse to ~8 per audit; max ceiling is 34 distinct cloud_ids):
SELECT lower(trim(name)) AS norm_name, COUNT(*) AS n,
       GROUP_CONCAT(id) AS local_ids,
       GROUP_CONCAT(is_built_in) AS built_in_flags,
       GROUP_CONCAT(cloud_id) AS cloud_ids
FROM task_templates
GROUP BY lower(trim(name))
HAVING n > 1;

DROP TABLE IF EXISTS _tt_winners;
CREATE TEMP TABLE _tt_winners AS
SELECT lower(trim(t.name)) AS norm_name,
       (SELECT id FROM task_templates t2
         WHERE lower(trim(t2.name)) = lower(trim(t.name))
         ORDER BY t2.is_built_in DESC,
                  (CASE WHEN t2.cloud_id IS NULL THEN 1 ELSE 0 END),
                  t2.cloud_id,
                  t2.id
         LIMIT 1) AS winner_id
FROM task_templates t
GROUP BY lower(trim(t.name));

-- Delete losers (no children to repoint — TaskTemplate is a terminal entity in this graph):
DELETE FROM task_templates WHERE id NOT IN (SELECT winner_id FROM _tt_winners);

-- Verify (expect ~8 per audit; halt threshold: >50 or <1):
SELECT COUNT(*) AS after_count FROM task_templates;

DROP TABLE _tt_winners;
COMMIT;
```

---

### Step 14 — `tasks` (group by `(lower(trim(title)), createdAt)`, winner = most recent `updatedAt`)

This is the largest step by far — 5,144 rows on the 2026-04-21 snapshot (4,843 at audit time). Repoint `task_tags.taskId` (empty — should be no-op), `task_completions.task_id`, and subtask `parent_task_id` before delete. **Order inside this transaction matters:** repoint subtasks BEFORE deleting losers or ON DELETE CASCADE will wipe subtask subtrees.

```sql
BEGIN IMMEDIATE;

-- Identify — sanity check duplicate group sizes:
SELECT lower(trim(title))   AS norm_title,
       created_at           AS created_at,
       COUNT(*)             AS n
FROM tasks
GROUP BY lower(trim(title)), created_at
HAVING n > 1
ORDER BY n DESC
LIMIT 20;  -- spot-check top offenders

-- Winner rule: most recent updated_at; tiebreak on smallest id.
DROP TABLE IF EXISTS _task_winners;
CREATE TEMP TABLE _task_winners AS
SELECT lower(trim(t.title)) AS norm_title,
       t.created_at          AS created_at,
       (SELECT id FROM tasks t2
         WHERE lower(trim(t2.title)) = lower(trim(t.title))
           AND t2.created_at = t.created_at
         ORDER BY t2.updated_at DESC, t2.id ASC
         LIMIT 1) AS winner_id
FROM tasks t
GROUP BY lower(trim(t.title)), t.created_at;

-- Index _task_winners lookup keys for speed on a ~5,144-row join:
CREATE INDEX idx_task_winners_lookup ON _task_winners(norm_title, created_at);

-- Repoint task_completions.task_id:
UPDATE task_completions
   SET task_id = (
       SELECT winner_id FROM _task_winners w
        WHERE w.norm_title = (SELECT lower(trim(title)) FROM tasks WHERE id = task_completions.task_id)
          AND w.created_at = (SELECT created_at         FROM tasks WHERE id = task_completions.task_id)
   )
 WHERE task_id IS NOT NULL
   AND task_id NOT IN (SELECT winner_id FROM _task_winners);

-- Repoint parent_task_id on subtasks:
UPDATE tasks
   SET parent_task_id = (
       SELECT winner_id FROM _task_winners w
        WHERE w.norm_title = (SELECT lower(trim(title)) FROM tasks inner_t WHERE inner_t.id = tasks.parent_task_id)
          AND w.created_at = (SELECT created_at         FROM tasks inner_t WHERE inner_t.id = tasks.parent_task_id)
   )
 WHERE parent_task_id IS NOT NULL
   AND parent_task_id NOT IN (SELECT winner_id FROM _task_winners);

-- task_tags is expected empty; guard anyway:
UPDATE task_tags
   SET taskId = (
       SELECT winner_id FROM _task_winners w
        WHERE w.norm_title = (SELECT lower(trim(title)) FROM tasks WHERE id = task_tags.taskId)
          AND w.created_at = (SELECT created_at         FROM tasks WHERE id = task_tags.taskId)
   )
 WHERE taskId NOT IN (SELECT winner_id FROM _task_winners);

-- Delete losers:
DELETE FROM tasks WHERE id NOT IN (SELECT winner_id FROM _task_winners);

-- Verify (expect ~12 per audit target; allow for drift if post-audit pulls introduced new distinct titles):
SELECT COUNT(*) AS after_count FROM tasks;

-- Orphan checks:
SELECT COUNT(*) FROM task_completions WHERE task_id IS NOT NULL AND task_id NOT IN (SELECT id FROM tasks);  -- expect 0
SELECT COUNT(*) FROM tasks            WHERE parent_task_id IS NOT NULL AND parent_task_id NOT IN (SELECT id FROM tasks);  -- expect 0
SELECT COUNT(*) FROM task_tags        WHERE taskId NOT IN (SELECT id FROM tasks);  -- expect 0

DROP INDEX idx_task_winners_lookup;
DROP TABLE _task_winners;
COMMIT;
```

---

### Step 15 — `task_completions` (group by `(task_id, completed_date)`, winner = smallest `completed_at_time`)

After Step 14, every surviving `task_completions.task_id` points at a winning task (or is NULL — but NULLs were quarantined in the pre-step). Dedupe now.

```sql
BEGIN IMMEDIATE;

-- Identify:
SELECT task_id, completed_date, COUNT(*) AS n, GROUP_CONCAT(id) AS local_ids
FROM task_completions
GROUP BY task_id, completed_date
HAVING n > 1
ORDER BY n DESC
LIMIT 20;

DROP TABLE IF EXISTS _tc_winners;
CREATE TEMP TABLE _tc_winners AS
SELECT task_id,
       completed_date,
       (SELECT id FROM task_completions tc2
         WHERE tc2.task_id = tc.task_id
           AND tc2.completed_date = tc.completed_date
         ORDER BY tc2.completed_at_time ASC, tc2.id ASC
         LIMIT 1) AS winner_id
FROM task_completions tc
GROUP BY task_id, completed_date;

DELETE FROM task_completions
 WHERE id NOT IN (SELECT winner_id FROM _tc_winners);

-- Verify (expect ~77 surviving; quarantined ~2,353 already held aside in the pre-step table):
SELECT COUNT(*) AS after_count FROM task_completions;

DROP TABLE _tc_winners;
COMMIT;
```

---

### Step 16 — `task_tags` (expected no-op; verify only)

```sql
-- Should be 0 both before and after Fix D per prior audit.
SELECT COUNT(*) AS task_tags_count FROM task_tags;  -- expect 0
```

---

## Handling the null-taskId `task_completions`

Snapshot count: **2,353 rows** on 2026-04-21 (up from 467 at original audit time). These rows lost their `task_id` pointer during a pre-Phase-2 upload race: when a `task_completion` was pushed to Firestore, the code looked up the completing task's cloudId, got NULL (because the task hadn't finished uploading yet), and persisted NULL as the taskId on the completion. The completions' `completed_date`, `project_id`, `completed_at_time`, `priority`, and `tags` columns are intact, but the taskId link is unrecoverable via a direct mapping.

### Option A — Drop them

- **Pro:** Simplest. Leaves Room clean with zero unknown linkages.
- **Con:** Permanent loss of completion history for ~2,353 completion events (~34% of all completions on the 2026-04-21 snapshot). The productivity dashboard / analytics features will show lower lifetime completion counts.
- **Recommended for:** If the user's priority is correctness over completeness.

### Option B — Fuzzy-match by `(completed_date, project_id)` to surviving tasks

- **Pro:** Preserves completion history where we can reasonably guess.
- **Con:** Misattribution is very likely: many days have multiple tasks completed in the same project, and a completion with no recorded taskId cannot be distinguished from another. Bad matches pollute analytics silently.
- **Concrete risk:** The snapshot shows 2,353 null-taskId rows against ~12 canonical post-collapse tasks — roughly a 196:1 ratio (and it was 6:1 even at audit time). Fuzzy-matching would pile hundreds of historical completions onto a single task and misrepresent its completion frequency. On the date dimension alone there is not enough information to disambiguate.
- **Recommended for:** Never — the signal-to-noise ratio is too low.

### Option C — Move to a quarantine table for manual review (RECOMMENDED)

- **Pro:** Zero data loss; the completion history still exists, just outside the active set. A future migration or manual triage can reattach rows if ever needed. Analytics computed from `task_completions` reflect only data we can stand behind.
- **Pro:** Keeps the live table clean, so the post-Fix-D Firestore cleanup step doesn't see any orphaned completions.
- **Con:** The quarantine table (`quarantine_task_completions_null_taskid`) persists in Room but is not declared to Room's schema — it is invisible to Room's `@Database` registry and unaffected by future migrations.
  - This is safe because Room ignores tables it does not know about at startup.
  - It is marginal cost (~2,353 rows of narrow data on current snapshot; narrow columns; no indexes to maintain).
- **Recommended.**

**Recommendation:** Option C. Wire into the pre-step defined above. Leave the quarantine table in place indefinitely; revisit if a user-facing feature ever requires reconstructing the missing linkage.

---

## Rollback Plan

### Detecting mid-execution failure

Every block above ends in a verification `SELECT COUNT(*)`. A discrepancy between the expected and actual count means we halt — the current block has already rolled back (every block is wrapped in `BEGIN IMMEDIATE` / `COMMIT`; a transaction failure inside reverts the block's writes automatically).

**Trigger rollback if any of these fires:**

Targets below are *approximate* — exact values depend on the natural-key distribution at execution time, which may drift from the audit if post-audit pulls introduced genuinely-new distinct natural keys. Treat large deviations (>2×) as hard halts; small deviations (say, tasks landing at 14 instead of 12 because two new distinct titles were authored) should be investigated but are not automatic rollback triggers.

| Stage          | Verification query                                                | Expected                            | Halt if                                         |
|----------------|-------------------------------------------------------------------|-------------------------------------|-------------------------------------------------|
| Pre-step       | `SELECT COUNT(*) FROM task_completions WHERE task_id IS NULL;`    | 0                                   | ≠ 0 (quarantine incomplete)                     |
| Step 2         | `SELECT COUNT(*) FROM habit_completions;`                         | 0                                   | ≠ 0                                             |
| Step 3         | `SELECT COUNT(*) FROM habit_logs;`                                | 0                                   | ≠ 0                                             |
| Step 4         | `SELECT COUNT(*) FROM tags;`                                      | ~3 (audit target)                   | > 50 or < 1                                     |
| Step 10        | `SELECT COUNT(*) FROM projects;`                                  | ~2 (audit target)                   | > 20 or < 1                                     |
| Step 10 orphan | `SELECT COUNT(*) FROM tasks WHERE project_id IS NOT NULL AND project_id NOT IN (SELECT id FROM projects);` | 0 | ≠ 0 |
| Step 11        | `SELECT COUNT(*) FROM habits;`                                    | ~3 (audit target; ≤ 26 cap)         | > 50 or < 1                                     |
| Step 13        | `SELECT COUNT(*) FROM task_templates;`                            | ~8 (audit target; ≤ 34 cap)         | > 50 or < 1                                     |
| Step 14        | `SELECT COUNT(*) FROM tasks;`                                     | ~12 (audit target)                  | > 100 or < 1                                    |
| Step 15        | `SELECT COUNT(*) FROM task_completions;`                          | ~77 (audit target; scales with Step 14) | > 10× Step-14 row count |

On halt: do **not** run the next block. Decide whether to investigate live (with the in-block transaction already rolled back, the DB is only as far along as the prior block's successful commit) or restore from backup.

### Full restore from backup

```powershell
# 1. Force-stop the app so no process holds a db handle.
adb shell am force-stop com.averycorp.prismtask

# 2. Push the backup files back onto the device's private storage.
$backupDir = "C:\Projects\averyTask\backups\pre-fixd-<TIMESTAMP>"   # <-- the dir you created in Prerequisites step 5
adb push "$backupDir\averytask.db"     /sdcard/averytask.db
adb push "$backupDir\averytask.db-wal" /sdcard/averytask.db-wal
adb push "$backupDir\averytask.db-shm" /sdcard/averytask.db-shm

# 3. run-as into the app's sandbox and copy into place, overwriting the live files.
adb shell "run-as com.averycorp.prismtask cp /sdcard/averytask.db     /data/data/com.averycorp.prismtask/databases/averytask.db"
adb shell "run-as com.averycorp.prismtask cp /sdcard/averytask.db-wal /data/data/com.averycorp.prismtask/databases/averytask.db-wal"
adb shell "run-as com.averycorp.prismtask cp /sdcard/averytask.db-shm /data/data/com.averycorp.prismtask/databases/averytask.db-shm"

# 4. Clean up sdcard.
adb shell "rm /sdcard/averytask.db /sdcard/averytask.db-wal /sdcard/averytask.db-shm"

# 5. Verify restore integrity:
adb shell "run-as com.averycorp.prismtask sqlite3 /data/data/com.averycorp.prismtask/databases/averytask.db 'PRAGMA integrity_check;'"
adb shell "run-as com.averycorp.prismtask sqlite3 /data/data/com.averycorp.prismtask/databases/averytask.db 'SELECT COUNT(*) FROM tasks;'"   # expect ~5,144 (or current snapshot count)
```

Once restored, the app can be relaunched: Room will read the version (52) and skip migrations. No app-side invalidation is required.

---

## Execution Plan

### Host-side vs on-device execution

Recommended: execute on-device via `adb shell` — this keeps the DB file in place and avoids the risk of pulling, mutating, and pushing back a file whose WAL state is out of sync.

```powershell
adb shell "run-as com.averycorp.prismtask sqlite3 /data/data/com.averycorp.prismtask/databases/averytask.db"
# (Interactive prompt; paste each block one at a time, observing output.)
```

Alternative: save each block above to a file on the device (`adb push block_10_projects.sql /sdcard/`), then:

```powershell
adb shell "run-as com.averycorp.prismtask sh -c 'cat /sdcard/block_10_projects.sql | sqlite3 /data/data/com.averycorp.prismtask/databases/averytask.db'"
```

### Logs to watch

- `adb logcat -s PrismSync:*` — Phase 2 migration logs `PrismSync.Migration_51_52` on collision resolution. If new errors appear here during Fix D execution, the app process was not actually stopped. Halt and force-stop again.
- SQLite error output at the `sqlite3` prompt itself: any `Runtime error:`, `Parse error:`, or `UNIQUE constraint failed` must halt.

### Estimated duration

| Step                       | Rows touched           | Estimate |
|----------------------------|-----------------------:|---------:|
Estimates below are rescaled to the 2026-04-21 snapshot (snapshot counts, not audit counts):

| Pre-step (quarantine ~2,353) |                  2,353 |     ~1 s |
| Step 2 (habit_completions)   |                      8 |     <1 s |
| Step 3 (habit_logs)          |                    835 |     <1 s |
| Step 4 (tags)                |                  9,656 |     ~3 s |
| Steps 5–7 (verify-only, zero dup groups) | ~80 rows scanned | <1 s |
| Step 8 (courses)             |                     44 |     <1 s |
| Step 9 (course_completions)  |                     66 |     <1 s |
| Step 10 (projects + repoints across 5144 tasks, ~4613 completions, 317 templates) | ~10 k update cells | ~4 s |
| Step 11 (habits + repoint 5144 tasks)  |      ~6 k update cells | ~3 s |
| Step 12 (milestones)         |                      0 |     <1 s |
| Step 13 (task_templates)     |                    317 |     <1 s |
| Step 14 (tasks, the big one) | 5,144 grouped, ~4,613 + 5,144 + 0 repoints | ~30–60 s |
| Step 15 (task_completions)   |                  4,613 |     ~3 s |

**Total estimate: 1–2 minutes on the device.** A run that takes over 5 minutes is almost certainly stuck on a missing index — halt and investigate.

### Stop-and-report triggers

| Condition                                                       | Action                                             |
|-----------------------------------------------------------------|----------------------------------------------------|
| Any verification count differs from expected                    | Halt. Do not run next block. Inspect live or restore. |
| `UNIQUE constraint failed` from the `index_X_cloud_id` indexes  | A loser row with a non-null cloud_id was not captured as a duplicate by the winner temp table. Halt, rebuild the temp table using `cloud_id`-based grouping instead of name-based, and retry. |
| `FOREIGN KEY constraint failed`                                 | A repoint was missed. Halt, examine the orphan-check queries in the verify section of the preceding step. |
| Any `PRAGMA foreign_keys` check returns 0                       | Halt. Re-enable FKs and retry; Fix D's `ON DELETE CASCADE` safety depends on them. |
| Process exits / `sqlite3` closes mid-block                      | The enclosing transaction has rolled back. Re-run the block idempotently (every block recomputes winners from scratch so a retry is safe). |

---

## Post-Execution State

### Expected row counts

| Table              |  After |
|--------------------|-------:|
| tasks              |    ~12 |
| tags               |     ~3 |
| projects           |     ~2 |
| habits             |     ~3 |
| task_completions   |    ~77 |
| task_templates     |     ~8 |
| habit_completions  |      0 |
| habit_logs         |      0 |
| milestones         | deduped per `(project_id, title)` |
| courses            | deduped per `trim(name)` |
| course_completions | deduped per `(date, course_id)` |
| leisure_logs       | deduped per `date` |
| self_care_logs     | deduped per `(routine_type, date)` |
| self_care_steps    | deduped per `(step_id, routine_type)` |
| task_tags          |      0 |
| quarantine_task_completions_null_taskid | ~2,353 (snapshot) |

### `sync_metadata` state

- **Untouched.** Rows that pointed at deleted local IDs are now orphans within `sync_metadata`. That is intentional: the Phase 2 per-row cloud-mapping guard reads from `sync_metadata` and, on a stale entry, happens to point at no surviving row — but that guard's semantic is "skip if mapping exists", so at worst a stale mapping suppresses a future reupload for a no-longer-existing row. This is a harmless steady state.
- Pruning `sync_metadata` is deferred to a later phase, after Firestore cleanup has authoritatively confirmed which cloud docs remain.

### UI changes the user should see

- Task list: ~5,140 duplicates collapse to ~12 canonical tasks.
- Projects screen: 93 duplicates collapse to ~2.
- Tags editor: 9,656 collapse to ~3.
- Habits: 1,248 collapse to ~3. Previously-orphaned `habit_completions` / `habit_logs` that were driving phantom streaks are gone, so habit streak counts may drop to 0 until the user records new completions.
- Today screen: the balance bar, overdue count, and "planned" count will reflect the collapsed, authoritative task set.
- Analytics / dashboard: completion history drops from ~6,966 visible rows to ~77 (with ~2,353 held aside in quarantine). Expect a sharp drop in the contribution grid and completion-rate metrics. This is correct — the prior inflation was the bug.

---

## Known Risks

1. **`tasks.source_habit_id` is NOT a formal foreign key.** It is declared in `TaskEntity` as a plain `@ColumnInfo(name = "source_habit_id") val sourceHabitId: Long? = null` without any `ForeignKey` constraint. `ON DELETE CASCADE`/`SET NULL` will not fire. Step 11 repoints this column manually. If any future collapse work is done, treat `source_habit_id` as a soft-reference that requires explicit handling.

2. **Rows with `cloud_id = NULL` (exact count unknown pre-Phase-2.5 deploy).** After migration 51→52 runs, every row with a `sync_metadata` mapping gets its `cloud_id` populated; rows without a mapping, plus losers nulled by collision resolution (~1,829 at Phase 2 authoring time, likely more post-Phase-2.5 given the snapshot shows ~13k collisions across all entity types), will have `cloud_id = NULL`. These rows participate in grouping **by natural key**, not by `cloud_id`, so they are captured correctly. The winner-selection tiebreaker on `cloud_id` uses `ORDER BY (CASE WHEN cloud_id IS NULL THEN 1 ELSE 0 END), cloud_id` so NULLs sort last — a NULL cloud_id can only win if every row in its group has NULL cloud_id, in which case the final tiebreak on `id ASC` decides. This is consistent and deterministic, but does mean a row with a real cloud mapping will always beat a local-only row in the same group.

3. **`cloud_id` uniqueness constraints fire during delete, not update.** The Phase 2 migration created `UNIQUE INDEX index_<table>_cloud_id`. Deleting a duplicate row is fine; but if any step accidentally reassigned a non-null cloud_id onto another row, the unique index would fire. No step in Fix D assigns `cloud_id`, so this should not trigger — verify by running each block and watching for `UNIQUE constraint failed`.

4. **String-ID references in JSON blobs.** Fields like `TaskTemplateEntity.templateTagsJson` and `LeisureLogEntity.customSectionsState` contain JSON arrays of IDs. These are **local IDs** of tags/sections and will become stale if any referenced tag loses its local ID during tag collapse. Scope: tags go 2165→3, so the risk is real. **Fix D does not rewrite JSON blobs.** Result: a task_template referencing a deleted tag ID will, at apply time, silently drop that tag — which is arguably the correct behavior (the duplicate tag is conceptually the same as the winner) but is not the most precise mapping. Out of scope for Fix D; flagged in [Out of Scope](#out-of-scope).

5. **Room `InvalidationTracker` observers fire on any table change.** If the app process is not force-stopped before Fix D runs, Room's observers inside the app will see every UPDATE/DELETE and re-emit `Flow` values to the UI at whatever rate they're being consumed. In the worst case this can trigger ViewModel-driven writes (urgency scoring, smart-defaults, reminder rescheduling) that interleave with Fix D's SQL. **Mandatory:** `adb shell am force-stop com.averycorp.prismtask` before step 1. No Fix D step re-checks liveness; the caller is responsible.

6. **Reactive push observes `sync_metadata.pending_action`.** Fix D does not touch `sync_metadata`, so no `pending_action` is set, so reactive push stays quiet. Verified by Phase 2 commit message explicitly (same mechanism as Fix E migration).

7. **Room schema validation on next startup.** Room runs a schema hash check against the compiled schema at every startup. Our SQL creates a single non-Room table (`quarantine_task_completions_null_taskid`). SQLite unknown-table behavior: Room `RoomOpenHelper.checkIdentity()` only inspects tables declared in `@Database(entities=...)`, so extra tables are invisible and do not break startup. Extra indexes and temp tables (all temp tables are cleaned up at COMMIT) are also invisible.

8. **`course_completions` repoint/UNIQUE ordering.** Step 8 repoints `course_completions.course_id` before Step 9 dedupes. If two losers in the same course-group have completions for the same `date`, Step 8's `UPDATE` would hit the `UNIQUE(date, course_id)` index and abort. Mitigation documented inline in Step 9. In practice the audit did not report duplicate course completions, so this is a latent edge case.

9. **Partial completion of a block due to power loss / device restart.** Every block is a single transaction; a crash mid-block rolls back automatically at the next DB open. The caller should simply retry the block. Room migrations do not run mid-Fix-D because the DB is already at version 52.

---

## Out of Scope

- **Firestore cleanup.** Runs as a separate Phase 3 step after Fix D completes and verifies clean.
- **`sync_metadata` pruning.** Deprecated; removal scheduled for a later phase. Leaving it intact during Fix D preserves the per-row cloud-mapping guard that Phase 2's Fix C depends on.
- **UI / ViewModel changes.** None required; reactive flows re-read after the app restarts.
- **Deferred bugs** — `lifeCategory` null-backfill, `templateKey` null-backfill. Do NOT attempt in this document.
- **Rewriting JSON-encoded ID blobs.** (`templateTagsJson`, `customSectionsState`, etc.) Out of scope — see Risk #4.
- **Source-code changes.** Fix D is a pure data migration. Any code change belongs in a different commit.
- **Reindexing / VACUUM.** Would reclaim disk space after ~4800-row delete; cosmetic. Room does not rely on page layout, and the next `ANALYZE` run (or next app startup) refreshes stats automatically.
- **`sync_metadata` retry_count reset.** Unaffected.

---

## Unresolved Questions

These require confirmation from the prior audit output or a read-only `SELECT` on the live DB before execution:

1. **Does `leisure_logs` actually contain duplicates?** The table carries a `UNIQUE(date)` index declared in `LeisureLogEntity`. If the unique index was created in the original entity definition (not retroactively in a later migration), the table cannot contain date-level duplicates by construction. Before running Step 5, check:
   ```sql
   SELECT date, COUNT(*) AS n FROM leisure_logs GROUP BY date HAVING n > 1;
   ```
   If no rows, Step 5 is a no-op (skip).

2. **Same question for `self_care_logs`** and the `UNIQUE(routine_type, date)` index — verify Step 6 is needed before running it.

3. **Are there duplicate milestones post project-collapse?** The audit did not report milestone counts. Run the Step 12 identification query before executing its delete.

4. **Exact `course_completions` FK and existing unique-index status.** Confirmed in `CourseCompletionEntity.kt`: `UNIQUE(date, course_id)` + `ON DELETE CASCADE` on `course_id`. No further verification needed, but flagged here so future readers don't redo the investigation.

5. **What is the exact count of rows with `cloud_id = NULL` per entity post-Phase-2.5?** The 2026-04-21 snapshot shows the device has not yet installed Phase 2 (DB version 51, no `cloud_id` column). Once Phase 2.5 installs and both the migration backfill + `restoreCloudIdFromMetadata` run, the per-table null-count distribution can be read directly:
   ```sql
   SELECT 'tasks' AS t, COUNT(*) FROM tasks WHERE cloud_id IS NULL
   UNION ALL SELECT 'projects', COUNT(*) FROM projects WHERE cloud_id IS NULL
   UNION ALL SELECT 'tags', COUNT(*) FROM tags WHERE cloud_id IS NULL
   UNION ALL SELECT 'habits', COUNT(*) FROM habits WHERE cloud_id IS NULL
   UNION ALL SELECT 'task_completions', COUNT(*) FROM task_completions WHERE cloud_id IS NULL
   UNION ALL SELECT 'task_templates', COUNT(*) FROM task_templates WHERE cloud_id IS NULL
   UNION ALL SELECT 'habit_completions', COUNT(*) FROM habit_completions WHERE cloud_id IS NULL
   UNION ALL SELECT 'habit_logs', COUNT(*) FROM habit_logs WHERE cloud_id IS NULL
   UNION ALL SELECT 'milestones', COUNT(*) FROM milestones WHERE cloud_id IS NULL
   UNION ALL SELECT 'courses', COUNT(*) FROM courses WHERE cloud_id IS NULL
   UNION ALL SELECT 'course_completions', COUNT(*) FROM course_completions WHERE cloud_id IS NULL
   UNION ALL SELECT 'leisure_logs', COUNT(*) FROM leisure_logs WHERE cloud_id IS NULL
   UNION ALL SELECT 'self_care_steps', COUNT(*) FROM self_care_steps WHERE cloud_id IS NULL
   UNION ALL SELECT 'self_care_logs', COUNT(*) FROM self_care_logs WHERE cloud_id IS NULL;
   ```
   Informational only — does not change Fix D's logic, but useful for the post-Fix-D Firestore reconciliation that follows.

6. **Is the tasks natural key `(lower(trim(title)), createdAt)` or `(lower(trim(title)))` alone?** The spec says both. The stricter version (includes `createdAt`) will under-collapse if the same task was uploaded twice with slightly different timestamps from different devices. The looser version (title only) may over-collapse distinct tasks that happen to share titles across months. The audit reports 4843 → 12, which is extreme-enough reduction that either rule would likely produce the same outcome. **Recommendation:** run Step 14 with `(lower(trim(title)), createdAt)` as written, then run a post-check:
   ```sql
   SELECT lower(trim(title)) AS t, COUNT(*) AS n FROM tasks GROUP BY lower(trim(title)) HAVING n > 1;
   ```
   If more than one row survives with the same normalized title, escalate before Firestore cleanup — the extra rows will each mint separate cloud docs on the next upload.

7. **Is the app's foreign-key enforcement definitely on at the `sqlite3` CLI?** SQLite defaults `foreign_keys = OFF` on every new connection. Without it, `ON DELETE CASCADE` and `ON DELETE SET NULL` are silently ignored and we'd end up with orphan rows. The Execution Plan includes `PRAGMA foreign_keys = ON;` as the first line of every session; do not skip.

8. **Does the Phase 2 migration's `cloud_id` column survive an app uninstall-reinstall cycle during the Fix D flow?** Not a blocker — the backup captures state before Fix D, and restore would put it back. But if a reinstall is needed mid-Fix-D, Room would rerun migration 51→52 against any backed-up pre-52 DB, which would re-emit the migration's duplicate-resolution logic. Not expected in a normal flow; documenting in case.
