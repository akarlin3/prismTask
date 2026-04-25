package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_60_61
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v60 → v61 — adds `reminder_mode` +
 * `reminder_interval_minutes` to `medication_slots` and `medications`,
 * and `is_synthetic_skip` to `medication_doses`. No backfill: existing
 * rows inherit the global default reminder mode (CLOCK).
 */
@RunWith(AndroidJUnit4::class)
class Migration60To61Test {

    private fun openV60(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(60) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `medication_slots` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`ideal_time` TEXT NOT NULL, " +
                            "`drift_minutes` INTEGER NOT NULL DEFAULT 180, " +
                            "`is_active` INTEGER NOT NULL DEFAULT 1" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE `medications` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE `medication_doses` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`medication_id` INTEGER NOT NULL, " +
                            "`slot_key` TEXT NOT NULL, " +
                            "`taken_at` INTEGER NOT NULL, " +
                            "`taken_date_local` TEXT NOT NULL, " +
                            "`note` TEXT NOT NULL DEFAULT ''" +
                            ")"
                    )
                    db.execSQL(
                        "INSERT INTO `medication_slots` (name, ideal_time) " +
                            "VALUES ('Morning', '08:00')"
                    )
                    db.execSQL(
                        "INSERT INTO `medications` (name) VALUES ('Lamotrigine')"
                    )
                    db.execSQL(
                        "INSERT INTO `medication_doses` " +
                            "(medication_id, slot_key, taken_at, taken_date_local) " +
                            "VALUES (1, '08:00', 1700000000000, '2026-04-22')"
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
    fun migration_addsReminderModeColumnsToSlots() {
        val helper = openV60()
        val db = helper.writableDatabase
        MIGRATION_60_61.migrate(db)

        db.query("SELECT reminder_mode, reminder_interval_minutes FROM medication_slots").use { c ->
            assertTrue(c.moveToFirst())
            assertNull("existing slot's reminder_mode is NULL (inherit)", c.getString(0))
            assertTrue("existing slot's reminder_interval_minutes is NULL", c.isNull(1))
        }
        helper.close()
    }

    @Test
    fun migration_addsReminderModeColumnsToMedications() {
        val helper = openV60()
        val db = helper.writableDatabase
        MIGRATION_60_61.migrate(db)

        db.query("SELECT reminder_mode, reminder_interval_minutes FROM medications").use { c ->
            assertTrue(c.moveToFirst())
            assertNull(c.getString(0))
            assertTrue(c.isNull(1))
        }
        helper.close()
    }

    @Test
    fun migration_addsIsSyntheticSkipDefaultZeroToDoses() {
        val helper = openV60()
        val db = helper.writableDatabase
        MIGRATION_60_61.migrate(db)

        db.query("SELECT is_synthetic_skip FROM medication_doses").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(
                "existing doses default to is_synthetic_skip=0 (real)",
                0,
                c.getInt(0)
            )
        }
        helper.close()
    }

    @Test
    fun migration_allowsWritingNewColumns() {
        val helper = openV60()
        val db = helper.writableDatabase
        MIGRATION_60_61.migrate(db)

        db.execSQL(
            "UPDATE medication_slots SET reminder_mode = 'INTERVAL', " +
                "reminder_interval_minutes = 240 WHERE id = 1"
        )
        db.execSQL("UPDATE medications SET reminder_mode = 'CLOCK' WHERE id = 1")
        db.execSQL(
            "INSERT INTO medication_doses " +
                "(medication_id, slot_key, taken_at, taken_date_local, note, is_synthetic_skip) " +
                "VALUES (1, '08:00', 1700000001000, '2026-04-22', '', 1)"
        )

        db.query(
            "SELECT reminder_mode, reminder_interval_minutes FROM medication_slots WHERE id = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("INTERVAL", c.getString(0))
            assertEquals(240, c.getInt(1))
        }
        db.query(
            "SELECT COUNT(*) FROM medication_doses WHERE is_synthetic_skip = 1"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        helper.close()
    }
}
