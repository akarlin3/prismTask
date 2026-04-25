package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_59_60
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v59 → v60 — `medication_tier_states` schema +
 * backfill from legacy `self_care_logs.tiers_by_time` JSON.
 *
 * Pre-migration setup mirrors the v59 shape: `medications`,
 * `medication_slots` (with the DEFAULT slot from `MIGRATION_58_59`), and
 * `self_care_logs` carrying the legacy `tiers_by_time` JSON column.
 */
@RunWith(AndroidJUnit4::class)
class Migration59To60Test {

    private fun openV59(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(59) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `medications` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE `medication_slots` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`ideal_time` TEXT NOT NULL, " +
                            "`drift_minutes` INTEGER NOT NULL DEFAULT 180, " +
                            "`is_active` INTEGER NOT NULL DEFAULT 1" +
                            ")"
                    )
                    // self_care_logs shape — only columns the migration reads.
                    db.execSQL(
                        "CREATE TABLE `self_care_logs` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`routine_type` TEXT NOT NULL, " +
                            "`log_date` TEXT NOT NULL, " +
                            "`tiers_by_time` TEXT NOT NULL DEFAULT '{}'" +
                            ")"
                    )
                    db.execSQL(
                        "INSERT INTO `medication_slots` (name, ideal_time) " +
                            "VALUES ('Default', '09:00')"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun migration_createsTierStatesTable() {
        val helper = openV59()
        val db = helper.writableDatabase

        MIGRATION_59_60.migrate(db)

        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        assertTrue("medication_tier_states created", "medication_tier_states" in tables)
        helper.close()
    }

    @Test
    fun migration_backfillsTierStatesFromLegacyTiersByTime() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO `medications` (name) VALUES ('Lamotrigine'), ('Vitamin D')"
        )
        // Legacy log: morning marked at "complete" tier.
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) " +
                "VALUES ('medication', '2026-04-22', '{\"morning\":\"complete\"}')"
        )

        MIGRATION_59_60.migrate(db)

        // Two meds × one slot × one log day = 2 tier-state rows, all
        // 'complete' since the JSON contains "complete".
        db.query("SELECT COUNT(*), tier FROM medication_tier_states GROUP BY tier").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
            assertEquals("complete", c.getString(1))
            assertFalse(c.moveToNext())
        }
        helper.close()
    }

    @Test
    fun migration_picksHighestTierWhenMultiplePresent() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('M1')")
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) " +
                "VALUES ('medication', '2026-04-22', " +
                "'{\"morning\":\"essential\",\"evening\":\"prescription\"}')"
        )

        MIGRATION_59_60.migrate(db)

        db.query("SELECT tier FROM medication_tier_states").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("prescription beats essential", "prescription", c.getString(0))
        }
        helper.close()
    }

    @Test
    fun migration_skipsEmptyTiersByTime() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('M1')")
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) " +
                "VALUES ('medication', '2026-04-22', '{}'), " +
                "('medication', '2026-04-23', '')"
        )

        MIGRATION_59_60.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_tier_states").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "empty tiers_by_time entries should not produce tier-state rows",
                0,
                c.getInt(0)
            )
        }
        helper.close()
    }

    @Test
    fun migration_skipsNonMedicationRoutineTypes() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('M1')")
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) " +
                "VALUES ('morning', '2026-04-22', '{\"morning\":\"complete\"}'), " +
                "('housework', '2026-04-22', '{\"morning\":\"complete\"}')"
        )

        MIGRATION_59_60.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_tier_states").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "only routine_type='medication' rows participate in backfill",
                0,
                c.getInt(0)
            )
        }
        helper.close()
    }

    @Test
    fun migration_isIdempotentOnReRun() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('M1')")
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) " +
                "VALUES ('medication', '2026-04-22', '{\"morning\":\"essential\"}')"
        )

        MIGRATION_59_60.migrate(db)
        MIGRATION_59_60.migrate(db) // re-run

        db.query("SELECT COUNT(*) FROM medication_tier_states").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "INSERT OR IGNORE keeps the unique (med, log_date, slot) row count stable",
                1,
                c.getInt(0)
            )
        }
        helper.close()
    }

    /**
     * The SQL-LIKE-based JSON parser in MIGRATION_59_60 only matches
     * the `"morning":"essential"` shape via prefix scanning. A truncated
     * or malformed `tiers_by_time` payload should backfill nothing
     * rather than throw — the migration must keep going on dirty data.
     */
    @Test
    fun malformedTiersByTimeJson_backfillsNothing() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('M1')")
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) VALUES " +
                "('medication', '2026-04-22', '{\"morning\"'), " +
                "('medication', '2026-04-23', 'not even json'), " +
                "('medication', '2026-04-24', '{}')"
        )

        MIGRATION_59_60.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_tier_states").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("malformed JSON skipped without throwing", 0, c.getInt(0))
        }
        helper.close()
    }

    /**
     * Cross-join cardinality: the backfill fans the same achieved
     * tier across every active medication on each log date. With N
     * medications and M logs containing tiers, we get N×M tier_state
     * rows. Pin this so a future "smarter" backfill that breaks the
     * cardinality contract surfaces as a failing test.
     */
    @Test
    fun crossJoinCardinality_isMedicationsTimesLogsForPopulatedTiers() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL(
            "INSERT INTO `medications` (name) VALUES ('M1'), ('M2'), ('M3')"
        )
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) VALUES " +
                "('medication', '2026-04-22', '{\"morning\":\"essential\"}'), " +
                "('medication', '2026-04-23', '{\"morning\":\"prescription\"}')"
        )

        MIGRATION_59_60.migrate(db)

        db.query("SELECT COUNT(*) FROM medication_tier_states").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("3 medications × 2 logs = 6 tier_state rows", 6, c.getInt(0))
        }
        helper.close()
    }

    @Test
    fun migration_preservesLegacyTiersByTimeColumn() {
        val helper = openV59()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO `medications` (name) VALUES ('M1')")
        db.execSQL(
            "INSERT INTO `self_care_logs` (routine_type, log_date, tiers_by_time) " +
                "VALUES ('medication', '2026-04-22', '{\"morning\":\"prescription\"}')"
        )

        MIGRATION_59_60.migrate(db)

        // Legacy column + data must remain — quarantine pattern lets PR3
        // dual-write through one release before the cleanup migration.
        db.query("SELECT tiers_by_time FROM self_care_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("{\"morning\":\"prescription\"}", c.getString(0))
        }
        helper.close()
    }
}
