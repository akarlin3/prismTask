package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_62_63
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v62 → v63 — `medication_tier_states` gains
 * `intended_time` (nullable) + `logged_at` (NOT NULL, backfilled from
 * `updated_at`); new `medication_marks` table created.
 *
 * Pre-migration setup mirrors the relevant subset of the v62 shape for
 * `medication_tier_states` (unchanged since v59).
 */
@RunWith(AndroidJUnit4::class)
class Migration62To63Test {

    private fun openV62(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(62) {
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
                            "`ideal_time` TEXT NOT NULL" +
                            ")"
                    )
                    // medication_tier_states v62 shape — unchanged since v59,
                    // since neither MIGRATION_60_61 nor MIGRATION_61_62 touched it.
                    db.execSQL(
                        "CREATE TABLE `medication_tier_states` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`cloud_id` TEXT, " +
                            "`medication_id` INTEGER NOT NULL, " +
                            "`slot_id` INTEGER NOT NULL, " +
                            "`log_date` TEXT NOT NULL, " +
                            "`tier` TEXT NOT NULL, " +
                            "`tier_source` TEXT NOT NULL DEFAULT 'computed', " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL" +
                            ")"
                    )
                    db.execSQL(
                        "INSERT INTO `medications` (id, name) VALUES (1, 'Adderall')"
                    )
                    db.execSQL(
                        "INSERT INTO `medication_slots` (id, name, ideal_time) " +
                            "VALUES (1, 'Morning', '08:00')"
                    )
                    // Two legacy tier-state rows with distinct updated_at values
                    // so we can verify the backfill copies updated_at into logged_at.
                    db.execSQL(
                        "INSERT INTO `medication_tier_states` " +
                            "(id, cloud_id, medication_id, slot_id, log_date, tier, " +
                            " tier_source, created_at, updated_at) " +
                            "VALUES (1, 'ts-cloud-1', 1, 1, '2026-04-22', 'complete', " +
                            "'user_set', 100, 200)"
                    )
                    db.execSQL(
                        "INSERT INTO `medication_tier_states` " +
                            "(id, cloud_id, medication_id, slot_id, log_date, tier, " +
                            " tier_source, created_at, updated_at) " +
                            "VALUES (2, 'ts-cloud-2', 1, 1, '2026-04-23', 'essential', " +
                            "'computed', 300, 400)"
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
    fun migration_addsTierStateColumnsAndBackfillsLoggedAt() {
        val helper = openV62()
        val db = helper.writableDatabase

        MIGRATION_62_63.migrate(db)

        // Verify columns exist via PRAGMA.
        val tierStateColumns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`medication_tier_states`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) tierStateColumns.add(c.getString(nameIdx))
        }
        assertTrue("intended_time added", "intended_time" in tierStateColumns)
        assertTrue("logged_at added", "logged_at" in tierStateColumns)

        // Verify legacy rows: intended_time stays NULL, logged_at = updated_at.
        db.query(
            "SELECT id, intended_time, logged_at, updated_at " +
                "FROM `medication_tier_states` ORDER BY id"
        ).use { c ->
            assertTrue("row 1 present", c.moveToNext())
            assertEquals(1L, c.getLong(0))
            assertTrue("intended_time stays null on legacy row", c.isNull(1))
            assertEquals(200L, c.getLong(2))
            assertEquals(200L, c.getLong(3))

            assertTrue("row 2 present", c.moveToNext())
            assertEquals(2L, c.getLong(0))
            assertTrue("intended_time stays null on legacy row", c.isNull(1))
            assertEquals(400L, c.getLong(2))
            assertEquals(400L, c.getLong(3))
        }
        helper.close()
    }

    @Test
    fun migration_createsMedicationMarksTable() {
        val helper = openV62()
        val db = helper.writableDatabase

        MIGRATION_62_63.migrate(db)

        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) tables.add(c.getString(0))
        }
        assertTrue("medication_marks created", "medication_marks" in tables)

        val markColumns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`medication_marks`)").use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) markColumns.add(c.getString(nameIdx))
        }
        // Spot-check the columns the feature depends on.
        for (col in listOf(
            "id",
            "cloud_id",
            "medication_id",
            "medication_tier_state_id",
            "intended_time",
            "logged_at",
            "marked_taken",
            "updated_at"
        )) {
            assertTrue("$col column present", col in markColumns)
        }

        // Indexes — spot-check the unique pair index.
        val indexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND tbl_name = 'medication_marks'"
        ).use { c ->
            while (c.moveToNext()) indexes.add(c.getString(0))
        }
        assertTrue(
            "unique pair index created",
            "index_medication_marks_medication_id_medication_tier_state_id" in indexes
        )
        helper.close()
    }

    @Test
    fun migration_canInsertMarkAfterMigrate() {
        val helper = openV62()
        val db = helper.writableDatabase

        MIGRATION_62_63.migrate(db)

        // Insert a mark referencing the seeded medication + tier state.
        db.execSQL(
            "INSERT INTO `medication_marks` " +
                "(cloud_id, medication_id, medication_tier_state_id, " +
                " intended_time, logged_at, marked_taken, updated_at) " +
                "VALUES ('mark-1', 1, 1, 1234567890, 1234599999, 1, 1234599999)"
        )
        db.query(
            "SELECT cloud_id, intended_time, logged_at, marked_taken " +
                "FROM `medication_marks`"
        ).use { c ->
            assertTrue(c.moveToNext())
            assertEquals("mark-1", c.getString(0))
            assertEquals(1234567890L, c.getLong(1))
            assertEquals(1234599999L, c.getLong(2))
            assertEquals(1, c.getInt(3))
        }
        helper.close()
    }

    @Test
    fun migration_intendedTimeNullableForNewRows() {
        val helper = openV62()
        val db = helper.writableDatabase

        MIGRATION_62_63.migrate(db)

        // Inserting without intended_time should succeed and leave it NULL.
        db.execSQL(
            "INSERT INTO `medication_tier_states` " +
                "(cloud_id, medication_id, slot_id, log_date, tier, " +
                " tier_source, logged_at, created_at, updated_at) " +
                "VALUES ('ts-new', 1, 1, '2026-04-24', 'skipped', " +
                "'computed', 5000, 5000, 5000)"
        )
        db.query(
            "SELECT intended_time FROM `medication_tier_states` " +
                "WHERE cloud_id = 'ts-new'"
        ).use { c ->
            assertTrue(c.moveToNext())
            assertTrue("new row's intended_time is NULL by default", c.isNull(0))
        }
        helper.close()
    }
}
