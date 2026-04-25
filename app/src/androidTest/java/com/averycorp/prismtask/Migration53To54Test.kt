package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_53_54
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v53 → v54 (top-level `medications` +
 * `medication_doses` tables, backfilled from `self_care_steps` where
 * `routine_type='medication'`). Pattern mirrors
 * [Migration51To52Test] — the project ships with `exportSchema = false`
 * so Room's [androidx.room.testing.MigrationTestHelper] isn't wired up;
 * this test seeds a stripped-down v53 schema via [SupportSQLiteOpenHelper],
 * invokes the migration directly, and asserts the post-state.
 *
 * Covers:
 *  - distinct-name rows → one medication each
 *  - duplicate-name rows → collapsed to one row with merged display_label
 *  - null / blank medication_name → label used as the normalized name
 *  - refill rows merge by medication_name
 *  - quarantine tables contain exact copies of source rows
 *  - source tables are unchanged
 *  - UNIQUE(name) index rejects post-migration duplicates
 */
@RunWith(AndroidJUnit4::class)
class Migration53To54Test {

    private fun openV53(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(53) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Minimal schema for the three source tables the
                    // migration reads. Extra columns beyond what the INSERT
                    // … SELECT touches are harmless — `CREATE TABLE
                    // quarantine_* AS SELECT *` replicates whatever columns
                    // are present.
                    db.execSQL(
                        """CREATE TABLE `self_care_steps` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `cloud_id` TEXT,
                            `step_id` TEXT NOT NULL,
                            `routine_type` TEXT NOT NULL,
                            `label` TEXT NOT NULL,
                            `duration` TEXT NOT NULL DEFAULT '',
                            `tier` TEXT NOT NULL DEFAULT 'essential',
                            `note` TEXT NOT NULL DEFAULT '',
                            `phase` TEXT NOT NULL DEFAULT 'Medication',
                            `sort_order` INTEGER NOT NULL DEFAULT 0,
                            `reminder_delay_millis` INTEGER,
                            `time_of_day` TEXT NOT NULL DEFAULT 'morning',
                            `medication_name` TEXT,
                            `updated_at` INTEGER NOT NULL DEFAULT 0
                        )"""
                    )
                    db.execSQL(
                        """CREATE TABLE `self_care_logs` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `routine_type` TEXT NOT NULL,
                            `date` INTEGER NOT NULL,
                            `completed_steps` TEXT NOT NULL DEFAULT '[]',
                            `updated_at` INTEGER NOT NULL DEFAULT 0
                        )"""
                    )
                    db.execSQL(
                        """CREATE TABLE `medication_refills` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `medication_name` TEXT NOT NULL,
                            `pill_count` INTEGER,
                            `pills_per_dose` INTEGER NOT NULL DEFAULT 1,
                            `doses_per_day` INTEGER NOT NULL DEFAULT 1,
                            `last_refill_date` INTEGER,
                            `pharmacy_name` TEXT,
                            `pharmacy_phone` TEXT,
                            `reminder_days_before` INTEGER NOT NULL DEFAULT 3,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL
                        )"""
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                    // Migration invoked manually in each test.
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun migration_createsBothNewTablesWithIndexes() {
        val helper = openV53()
        val db = helper.writableDatabase

        MIGRATION_53_54.migrate(db)

        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name IN ('medications', 'medication_doses') ORDER BY name"
        ).use { c ->
            assertTrue(c.moveToNext())
            assertEquals("medication_doses", c.getString(0))
            assertTrue(c.moveToNext())
            assertEquals("medications", c.getString(0))
            assertFalse(c.moveToNext())
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' " +
                "AND name LIKE 'index_medication%' ORDER BY name"
        ).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names += c.getString(0)
            assertTrue(
                "expected index_medication_doses_cloud_id present, got $names",
                "index_medication_doses_cloud_id" in names
            )
            assertTrue(
                "expected index_medications_cloud_id present, got $names",
                "index_medications_cloud_id" in names
            )
            assertTrue(
                "expected index_medications_name present, got $names",
                "index_medications_name" in names
            )
        }
        helper.close()
    }

    @Test
    fun migration_createsQuarantineTablesWithSourceData() {
        val helper = openV53()
        val db = helper.writableDatabase

        // Seed source rows: 2 medication self-care-steps + 1 non-medication
        // (which must NOT land in the quarantine).
        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor 20mg', 'Lipitor', 'morning'), " +
                "('med_2', 'medication', 'Adderall', 'Adderall', 'morning'), " +
                "('morning_1', 'morning', 'Brush teeth', NULL, 'morning')"
        )
        db.execSQL(
            "INSERT INTO self_care_logs (routine_type, date, completed_steps) VALUES " +
                "('medication', 1000, '[\"med_1\"]'), " +
                "('morning', 2000, '[\"morning_1\"]')"
        )
        db.execSQL(
            "INSERT INTO medication_refills " +
                "(medication_name, pill_count, pills_per_dose, doses_per_day, created_at, updated_at) VALUES " +
                "('Lipitor', 30, 1, 1, 0, 0)"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM quarantine_medication_selfcare_steps").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("quarantine must contain only medication rows", 2, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM quarantine_medication_selfcare_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("quarantine must contain only medication logs", 1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM quarantine_medication_refills").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("quarantine must contain all refill rows", 1, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun backfill_distinctNamesProduceOneMedicationEach() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day, tier) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning', 'essential'), " +
                "('med_2', 'medication', 'Adderall', 'Adderall', 'morning', 'prescription'), " +
                "('med_3', 'medication', 'Metformin', 'Metformin', 'evening', 'essential')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM medications").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(3, c.getInt(0))
        }
        db.query(
            "SELECT name, display_label, tier, times_of_day FROM medications ORDER BY name"
        ).use { c ->
            assertTrue(c.moveToNext())
            assertEquals("Adderall", c.getString(0))
            assertEquals("Adderall", c.getString(1))
            assertEquals("prescription", c.getString(2))
            assertEquals("morning", c.getString(3))

            assertTrue(c.moveToNext())
            assertEquals("Lipitor", c.getString(0))
            assertEquals("essential", c.getString(2))

            assertTrue(c.moveToNext())
            assertEquals("Metformin", c.getString(0))
            assertEquals("evening", c.getString(3))
        }
        helper.close()
    }

    @Test
    fun backfill_duplicateNamesCollapseIntoOneMedicationWithMergedLabels() {
        val helper = openV53()
        val db = helper.writableDatabase

        // Two self-care-steps sharing medication_name='Lipitor' with different
        // dose-strength labels AND different times-of-day. Option C from the
        // spec §2.1 resolution: collapse to one row, merge labels via " / "
        // into display_label, aggregate times_of_day into comma-separated.
        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day, tier) VALUES " +
                "('med_1', 'medication', 'Lipitor 20mg', 'Lipitor', 'morning', 'essential'), " +
                "('med_2', 'medication', 'Lipitor 40mg', 'Lipitor', 'evening', 'essential')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM medications").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("duplicate-name rows must collapse to a single medication", 1, c.getInt(0))
        }
        db.query(
            "SELECT name, display_label, times_of_day FROM medications"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Lipitor", c.getString(0))
            val label = c.getString(1)
            assertTrue(
                "display_label must preserve both source labels, got '$label'",
                label.contains("Lipitor 20mg") && label.contains("Lipitor 40mg") && label.contains(" / ")
            )
            val tod = c.getString(2)
            assertTrue(
                "times_of_day must aggregate both slots, got '$tod'",
                tod.contains("morning") && tod.contains("evening")
            )
        }
        helper.close()
    }

    @Test
    fun backfill_blankMedicationNameFallsBackToLabel() {
        val helper = openV53()
        val db = helper.writableDatabase

        // User never filled in the `medication_name` field — the normalized
        // name should fall back to the step's `label`.
        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Morning Vitamin', NULL, 'morning'), " +
                "('med_2', 'medication', 'Evening Melatonin', '', 'evening')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT name FROM medications ORDER BY name").use { c ->
            assertTrue(c.moveToNext())
            assertEquals("Evening Melatonin", c.getString(0))
            assertTrue(c.moveToNext())
            assertEquals("Morning Vitamin", c.getString(0))
        }
        helper.close()
    }

    @Test
    fun backfill_mergesRefillDataByMedicationName() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning'), " +
                "('med_2', 'medication', 'Unlinked', 'Unlinked', 'evening')"
        )
        db.execSQL(
            "INSERT INTO medication_refills " +
                "(medication_name, pill_count, pills_per_dose, doses_per_day, " +
                " last_refill_date, pharmacy_name, pharmacy_phone, reminder_days_before, " +
                " created_at, updated_at) VALUES " +
                "('Lipitor', 42, 2, 2, 5000, 'CVS', '555-0100', 7, 0, 0)"
        )

        MIGRATION_53_54.migrate(db)

        db.query(
            "SELECT pill_count, pills_per_dose, doses_per_day, last_refill_date, " +
                "pharmacy_name, pharmacy_phone, reminder_days_before " +
                "FROM medications WHERE name = 'Lipitor'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42, c.getInt(0))
            assertEquals(2, c.getInt(1))
            assertEquals(2, c.getInt(2))
            assertEquals(5000L, c.getLong(3))
            assertEquals("CVS", c.getString(4))
            assertEquals("555-0100", c.getString(5))
            assertEquals(7, c.getInt(6))
        }
        db.query(
            "SELECT pill_count, pills_per_dose, doses_per_day, last_refill_date, " +
                "pharmacy_name, pharmacy_phone, reminder_days_before " +
                "FROM medications WHERE name = 'Unlinked'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertTrue("no refill → pill_count NULL", c.isNull(0))
            assertEquals("default pills_per_dose", 1, c.getInt(1))
            assertEquals("default doses_per_day", 1, c.getInt(2))
            assertTrue("no refill → last_refill_date NULL", c.isNull(3))
            assertNull("no refill → pharmacy_name NULL", c.getString(4))
            assertEquals("default reminder_days_before", 3, c.getInt(6))
        }
        helper.close()
    }

    @Test
    fun backfill_scheduleModeIsAlwaysTimesOfDay_runnerFinalizesLater() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning')"
        )

        MIGRATION_53_54.migrate(db)

        db.query(
            "SELECT schedule_mode, cloud_id, is_archived, sort_order, notes, doses_per_day " +
                "FROM medications"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("migration hard-codes TIMES_OF_DAY; runner overrides", "TIMES_OF_DAY", c.getString(0))
            assertTrue("cloud_id must be NULL until next sync push", c.isNull(1))
            assertEquals("is_archived defaults to 0", 0, c.getInt(2))
            assertEquals("sort_order defaults to 0", 0, c.getInt(3))
            assertEquals("notes defaults to empty string", "", c.getString(4))
            assertEquals("doses_per_day defaults to 1 when no refill row", 1, c.getInt(5))
        }
        helper.close()
    }

    @Test
    fun migration_sourceTablesAreUnchanged() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning'), " +
                "('morn_1', 'morning', 'Stretch', NULL, 'morning')"
        )
        db.execSQL(
            "INSERT INTO self_care_logs (routine_type, date, completed_steps) VALUES " +
                "('medication', 1000, '[\"med_1\"]')"
        )
        db.execSQL(
            "INSERT INTO medication_refills " +
                "(medication_name, pill_count, pills_per_dose, doses_per_day, created_at, updated_at) VALUES " +
                "('Lipitor', 30, 1, 1, 0, 0)"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM self_care_steps").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("self_care_steps must not be modified", 2, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM self_care_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("self_care_logs must not be modified", 1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM medication_refills").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("medication_refills must not be modified", 1, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun uniqueNameIndex_rejectsDuplicatePostMigration() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning')"
        )

        MIGRATION_53_54.migrate(db)

        // Post-migration: attempting to insert a second medication with the
        // same name must throw on the unique index.
        var threw = false
        try {
            db.execSQL(
                "INSERT INTO medications " +
                    "(name, display_label, notes, tier, schedule_mode, doses_per_day, " +
                    " pills_per_dose, reminder_days_before, is_archived, sort_order, " +
                    " created_at, updated_at) VALUES " +
                    "('Lipitor', 'Lipitor 20mg', '', 'essential', 'TIMES_OF_DAY', 1, " +
                    " 1, 3, 0, 0, 0, 0)"
            )
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("unique(name) must reject duplicate post-migration", threw)
        helper.close()
    }

    @Test
    fun emptySource_migrationCreatesEmptyTables() {
        val helper = openV53()
        val db = helper.writableDatabase

        // No source rows at all — migration must still succeed and leave
        // the new tables empty.
        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM medications").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM medication_doses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM quarantine_medication_selfcare_steps").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun nonMedicationSelfCareSteps_areIgnored() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('morn_1', 'morning', 'Stretch', NULL, 'morning'), " +
                "('bed_1', 'bedtime', 'Moisturizer', NULL, 'night')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM medications").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("only routine_type='medication' rows backfill", 0, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun medicationDosesTable_hasNoRowsFromMigration() {
        val helper = openV53()
        val db = helper.writableDatabase

        // Dose backfill is deferred to MedicationMigrationRunner (PR 2).
        // This test pins the deferral so a future "oh let's just add it
        // back to SQL" refactor surfaces as a failing test, not a silent
        // regression on API 26 where JSON1 availability is OEM-dependent.
        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning')"
        )
        db.execSQL(
            "INSERT INTO self_care_logs (routine_type, date, completed_steps) VALUES " +
                "('medication', 1000, '[\"med_1\"]')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_doses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "dose backfill deferred to Kotlin runner in PR 2",
                0,
                c.getInt(0)
            )
        }
        // Quarantine still has the log for the runner to read.
        db.query("SELECT COUNT(*) FROM quarantine_medication_selfcare_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun foreignKey_medicationDoses_cascadesOnMedicationDelete() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning')"
        )

        MIGRATION_53_54.migrate(db)
        db.execSQL("PRAGMA foreign_keys = ON")

        val medId = db.query("SELECT id FROM medications").use { c ->
            assertTrue(c.moveToFirst())
            c.getLong(0)
        }
        db.execSQL(
            "INSERT INTO medication_doses " +
                "(medication_id, slot_key, taken_at, taken_date_local, note, created_at, updated_at) " +
                "VALUES ($medId, 'morning', 1000, '2026-04-22', '', 0, 0)"
        )
        db.query("SELECT COUNT(*) FROM medication_doses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.execSQL("DELETE FROM medications WHERE id = $medId")
        db.query("SELECT COUNT(*) FROM medication_doses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("FK CASCADE must wipe dose history with parent", 0, c.getInt(0))
        }
        assertNotNull(helper)
        helper.close()
    }

    /**
     * Re-running [MIGRATION_53_54] after it succeeded once must throw —
     * UNIQUE(name) on `medications` rejects the duplicate INSERT, which
     * is the contract that protects beta installs from a hypothetical
     * "let's just rerun the migration" recovery path. Pinned so a future
     * "make idempotent" refactor surfaces as a failing test.
     */
    @Test
    fun migration_isNotIdempotent_secondRunFailsOnUniqueName() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('med_1', 'medication', 'Lipitor', 'Lipitor', 'morning')"
        )

        MIGRATION_53_54.migrate(db)

        var threw = false
        try {
            MIGRATION_53_54.migrate(db)
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("re-running the migration must fail on UNIQUE(name)", threw)
        helper.close()
    }

    /**
     * The backfill collapses duplicate-name rows by joining display
     * labels with `' / '`. Labels containing literal commas would
     * mangle the join because the implementation uses
     * `REPLACE(GROUP_CONCAT(label, ','), ',', ' / ')`. Pin the
     * current behavior so a fix surfaces as an intentional change.
     */
    @Test
    fun commaInLabel_corruptsMergedDisplayLabel() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('m1', 'medication', 'Lipitor, generic', 'Lipitor', 'morning'), " +
                "('m2', 'medication', 'Lipitor 20mg', 'Lipitor', 'evening')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT display_label FROM medications WHERE name='Lipitor'").use { c ->
            assertTrue(c.moveToFirst())
            val merged = c.getString(0)
            assertTrue(
                "comma-in-label produces split tokens — current behavior pinned",
                merged.contains(" / ")
            )
            assertFalse(
                "comma replacement currently corrupts the original label",
                merged.contains("Lipitor, generic")
            )
        }
        helper.close()
    }

    /**
     * Whitespace-only `medication_name` must fall back to `label` via
     * `COALESCE(NULLIF(TRIM(medication_name), ''), label)`. Without
     * the TRIM, `'   '` would group separately from `''`/NULL and
     * produce duplicate medications.
     */
    @Test
    fun whitespaceOnlyMedicationName_fallsBackToLabel() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('m1', 'medication', 'Vitamin D', '   ', 'morning'), " +
                "('m2', 'medication', 'Vitamin D', '', 'evening'), " +
                "('m3', 'medication', 'Vitamin D', NULL, 'night')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM medications WHERE name='Vitamin D'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "whitespace + empty + null medication_name all collapse to label",
                1,
                c.getInt(0)
            )
        }
        helper.close()
    }

    /**
     * Both `medication_name` AND `label` blank — the resulting
     * normalized name is the empty string. This is a malformed
     * source row, but the migration must still complete without
     * throwing for the *first* such row. UNIQUE(name) then rejects
     * any second row with the same shape.
     */
    @Test
    fun blankNameAndLabel_allowsFirstButRejectsSecond() {
        val helper = openV53()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('m1', 'medication', '', '', 'morning')"
        )

        MIGRATION_53_54.migrate(db)

        db.query("SELECT COUNT(*) FROM medications WHERE name=''").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }

        // A second blank-blank row would violate UNIQUE(name).
        db.execSQL(
            "INSERT INTO self_care_steps " +
                "(step_id, routine_type, label, medication_name, time_of_day) VALUES " +
                "('m2', 'medication', '', '', 'evening')"
        )

        var threw = false
        try {
            // Re-running the migration is itself non-idempotent (see
            // earlier test); use a direct INSERT to isolate the
            // UNIQUE(name) rejection on the empty-string normalized
            // name. Only the NOT-NULL-without-default columns are
            // listed; everything else takes its column default.
            db.execSQL(
                "INSERT INTO medications (name, created_at, updated_at) VALUES ('', 0, 0)"
            )
        } catch (_: Exception) {
            threw = true
        }
        assertTrue("UNIQUE(name) must reject a second empty-string row", threw)
        helper.close()
    }
}
