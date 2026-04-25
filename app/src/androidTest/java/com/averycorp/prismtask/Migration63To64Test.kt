package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_63_64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v63 → v64 — drops the orphan `medication_marks`
 * table. No production write path ever populated it (the
 * per-medication intended_time ended up on `medication_tier_states`
 * instead), so this is a pure cleanup with no data preservation.
 *
 * Per `docs/audits/PHASE_D_BUNDLE_AUDIT.md` Item 3.
 */
@RunWith(AndroidJUnit4::class)
class Migration63To64Test {

    private fun openV63(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(63) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Minimal v63 shape — only the orphan table itself plus the
                    // parents its FKs reference. The actual v63 schema includes
                    // many other tables, but the migration only touches
                    // `medication_marks`, so this is sufficient.
                    db.execSQL(
                        "CREATE TABLE `medications` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE `medication_tier_states` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`medication_id` INTEGER NOT NULL, " +
                            "`slot_id` INTEGER NOT NULL, " +
                            "`log_date` TEXT NOT NULL, " +
                            "`tier` TEXT NOT NULL, " +
                            "`tier_source` TEXT NOT NULL, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `medication_marks` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`cloud_id` TEXT, " +
                            "`medication_id` INTEGER NOT NULL, " +
                            "`medication_tier_state_id` INTEGER NOT NULL, " +
                            "`intended_time` INTEGER, " +
                            "`logged_at` INTEGER NOT NULL, " +
                            "`marked_taken` INTEGER NOT NULL DEFAULT 1, " +
                            "`updated_at` INTEGER NOT NULL, " +
                            "FOREIGN KEY(`medication_id`) REFERENCES `medications`(`id`) " +
                            "ON DELETE CASCADE, " +
                            "FOREIGN KEY(`medication_tier_state_id`) " +
                            "REFERENCES `medication_tier_states`(`id`) ON DELETE CASCADE" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX `index_medication_marks_cloud_id` " +
                            "ON `medication_marks` (`cloud_id`)"
                    )
                    db.execSQL(
                        "CREATE INDEX `index_medication_marks_medication_id` " +
                            "ON `medication_marks` (`medication_id`)"
                    )
                    // Insert a row to confirm dropping is unconditional —
                    // even if a row exists somehow (e.g. a future client
                    // had written to it), the migration removes the table.
                    db.execSQL("INSERT INTO `medications` (id, name) VALUES (1, 'Adderall')")
                    db.execSQL(
                        "INSERT INTO `medication_tier_states` " +
                            "(id, medication_id, slot_id, log_date, tier, tier_source, " +
                            " created_at, updated_at) " +
                            "VALUES (1, 1, 1, '2026-04-25', 'complete', 'user_set', 100, 200)"
                    )
                    db.execSQL(
                        "INSERT INTO `medication_marks` " +
                            "(cloud_id, medication_id, medication_tier_state_id, " +
                            " intended_time, logged_at, marked_taken, updated_at) " +
                            "VALUES ('mark-1', 1, 1, 1234567890, 1234599999, 1, 1234599999)"
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
    fun migration_dropsMedicationMarksTable() {
        val helper = openV63()
        val db = helper.writableDatabase

        // Sanity: the table exists pre-migration.
        val preTables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) preTables.add(c.getString(0))
        }
        assertTrue("medication_marks present pre-migration", "medication_marks" in preTables)

        MIGRATION_63_64.migrate(db)

        val postTables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) postTables.add(c.getString(0))
        }
        assertFalse("medication_marks dropped", "medication_marks" in postTables)

        // Indexes go with the table.
        val postIndexes = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' " +
                "AND tbl_name = 'medication_marks'"
        ).use { c ->
            while (c.moveToNext()) postIndexes.add(c.getString(0))
        }
        assertTrue("medication_marks indexes dropped", postIndexes.isEmpty())

        // Parent tables untouched.
        assertTrue("medications retained", "medications" in postTables)
        assertTrue("medication_tier_states retained", "medication_tier_states" in postTables)

        helper.close()
    }

    @Test
    fun migration_isIdempotentWhenTableAbsent() {
        // Defensive: rerunning the migration on a database where the
        // table is already gone must not raise (the DROP uses IF EXISTS).
        val helper = openV63()
        val db = helper.writableDatabase
        MIGRATION_63_64.migrate(db)
        MIGRATION_63_64.migrate(db) // second call — must be a no-op, not throw.
        helper.close()
    }
}
