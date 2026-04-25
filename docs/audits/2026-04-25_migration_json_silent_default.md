# Audit: Room migrations for malformed-JSON silent-default risk

**Date:** 2026-04-25 · **Auditor:** Claude (TODO #4 of Phase B cleanup batch) · **Scope:** every migration in `app/src/main/java/com/averycorp/prismtask/data/local/database/Migrations.kt` (v1→v2 through v62→v63).

## Audit pattern

Looking for migrations where:

1. A backfill `INSERT...SELECT` or `UPDATE` parses a TEXT column whose values are intended to be JSON (or otherwise structured), AND
2. Uses `CASE WHEN ... LIKE '%token%' THEN ... ELSE <fallback> END` to extract a discriminator, AND
3. The `ELSE` branch produces a meaningful, non-error value that the application then treats as user intent.

When all three are present, malformed input (truncated JSON, non-JSON text, empty container) silently lands in the ELSE bucket and produces rows that misrepresent user intent — the user didn't choose the ELSE branch; the parser just couldn't extract anything.

This pattern was discovered when `Migration59To60Test::malformedTiersByTimeJson_backfillsNothing` regressed in PR #774; the migration's WHERE clause filtered empty/`{}` JSON but let strings like `{"morning"` through, where `CASE`'s `ELSE 'skipped'` produced bogus 'skipped' rows.

## Inventory

`Migrations.kt` is 1908 lines, covers `MIGRATION_1_2` through `MIGRATION_62_63` (62 migrations total). The grep for `CASE`/`LIKE '%`/`json_extract`/`json_valid` returned 3 CASE expressions in total:

| Location | Migration | Pattern | Verdict |
|---|---|---|---|
| Line 658 | v37→v38 (`task_completions` backfill) | `CASE WHEN due_date IS NOT NULL AND due_date < completed_at THEN 1 ELSE 0 END` (was_overdue) | **Safe.** ELSE 0 is correct: a task with no due_date or one not past its due_date wasn't overdue. Boolean — no malformed-input space. |
| Line 659–661 | v37→v38 (same backfill) | `CASE WHEN created_at > 0 AND completed_at > created_at THEN CAST(... AS INTEGER) ELSE NULL END` (days_to_complete) | **Safe.** ELSE NULL is honest "we couldn't compute it"; column is nullable; readers handle NULL explicitly. |
| Line 1664–1669 | v59→v60 (`medication_tier_states` backfill from `self_care_logs.tiers_by_time` JSON) | `CASE WHEN tiers_by_time LIKE '%"complete"%' THEN ... ELSE 'skipped' END` | **Was vulnerable, fixed by PR #774.** WHERE clause now requires at least one recognized token to appear in the JSON via LIKE; the ELSE 'skipped' branch becomes dead code but is left in place as defensive backstop. |

Other backfill operations inventoried (no CASE, no JSON parse, but worth confirming):

| Location | Migration | Pattern | Verdict |
|---|---|---|---|
| Line 803–854 | v44→v45 (data-integrity hardening) | `UPDATE study_logs SET course_pick = NULL WHERE …` + table-rebuild INSERT | **Safe.** SET NULL is the documented intent for orphan FK rows; not a default-on-malformed pattern. |
| Line 943–948 | v48→v49 (built-in habit identity) | `UPDATE habits SET is_built_in=1, template_key='builtin_X' WHERE name='X'` (six built-ins) | **Safe.** Hard-coded names; non-matching rows keep the safer default (`is_built_in=0`). The risk shape is the inverse of the audit pattern. |
| Line 1245–1284 | v53→v54 (medications backfill) | `COALESCE(NULLIF(TRIM(medication_name), ''), label)` to normalize names; `REPLACE(GROUP_CONCAT(DISTINCT label), ',', ' / ')` to merge labels | **Safe.** COALESCE/NULLIF are well-defined fallback chains, not silent defaults. The "labels assume no comma" is documented inline; a comma would only mangle a display string, not produce semantically wrong rows. The duplicate-name collapse is a documented data-quality decision. |
| Line 1554–1559 | v53→v54 (DEFAULT slot seed) | `INSERT...SELECT 'Default', '09:00', ... WHERE NOT EXISTS (SELECT 1 FROM medication_slots)` | **Safe.** Hardcoded literals; NOT EXISTS guard makes it idempotent. |
| Line 1564–1572 | v53→v54 (junction backfill) | `INSERT OR IGNORE INTO medication_medication_slots ...` | **Safe.** OR IGNORE skips conflicts; junction has composite PK, no silent default. |
| Line 1710–1716 | v60→v61 (reminder mode) | Pure ALTER TABLE additions (no backfill SELECT). | **Safe.** |
| Line 1742–1759 | v61→v62 (template versioning) | `ALTER TABLE habits ADD COLUMN ...` + `UPDATE habits SET source_version=1 WHERE is_built_in=1 AND template_key IS NOT NULL` + `UPDATE self_care_steps SET source_version=1` | **Safe.** Simple WHERE; rows that don't match keep the column default (0), which is documented as "linked but pre-versioning, treat as v1." Explicit semantic, not silent. |
| Line 1779–end | v62→v63 (medication time-logging) | ALTER TABLE additions + `UPDATE medication_tier_states SET logged_at = updated_at WHERE logged_at = 0` | **Safe.** Simple WHERE; no JSON parse, no CASE. |

## Verdict

**One vulnerable migration found across all 62: v59→v60. It is already fixed in `main` via PR #774.**

No further code changes are required from this audit. The audit doc itself is the deliverable so the pattern stays on record for future PRs that add JSON-parsing migrations.

## Recommended fix pattern (for future migrations)

When backfilling from a JSON-shaped TEXT column with CASE-discriminator extraction:

1. **Tighten the WHERE clause** to require at least one of the recognized tokens to appear (the v59→v60 fix shape):
   ```sql
   WHERE col IS NOT NULL
     AND col != ''
     AND col != '{}'
     AND (
       col LIKE '%"token1"%' OR
       col LIKE '%"token2"%' OR
       col LIKE '%"tokenN"%'
     )
   ```
   Malformed input (truncated, non-JSON, missing-token) is filtered out at the row level instead of falling into the ELSE bucket.

2. **Keep the CASE's ELSE branch** as a defensive backstop — if a future migration adds a token to LIKE but forgets the matching CASE arm, the unreachable ELSE provides a sane crash boundary instead of silently producing wrong rows.

3. **Test with malformed input.** Every migration that parses JSON should ship with a `Migration{X}To{Y}Test::malformedJSON_backfillsNothing` test asserting that bad input produces zero rows, not a misleading default.

4. **Or: skip SQL-side parsing entirely.** v53→v54's `medication_doses` backfill explicitly punted JSON parsing to a Kotlin-side `MedicationMigrationRunner` ("safer in Kotlin than SQLite's JSON1"). For complex JSON, this is the lower-risk path. SQL backfills are best when the discriminator is a single token and the malformed-input set is bounded.

5. **Use the migration instrumentation safety net (PR #773).** `MigrationInstrumentor` surfaces unexpected migration outcomes to Crashlytics — instrument the CASE so unexpected ELSE-bucket hits are visible in production.

## Out of scope

- **Cleanup migrations** (v53→v54's quarantine tables, the planned Phase B cleanup that drops `self_care_steps`/`self_care_logs` after the convergence window): these are deletions, not backfills, and don't have the audit pattern.
- **Backend Postgres migrations** (`backend/migrations/`): different SQL dialect, different review path. Out of scope for this audit.
- **Web client localStorage migrations**: not Room.
