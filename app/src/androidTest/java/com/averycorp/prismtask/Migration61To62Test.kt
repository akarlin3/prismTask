package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_61_62
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Direct-SQL migration test for v61 → v62: adds versioning columns
 * (`source_version`, `is_user_modified`, `is_detached_from_template`)
 * to `habits` and `source_version` to `self_care_steps`. Built-in habit
 * rows are backfilled to `source_version = 1`; non-built-in rows stay
 * at the column default of 0. All `self_care_steps` rows are backfilled
 * to 1.
 */
@RunWith(AndroidJUnit4::class)
class Migration61To62Test {

    private fun openV61(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(61) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    // Minimal v60 shape — only the columns the migration touches.
                    db.execSQL(
                        "CREATE TABLE `habits` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`is_built_in` INTEGER NOT NULL DEFAULT 0, " +
                            "`template_key` TEXT" +
                            ")"
                    )
                    db.execSQL(
                        "CREATE TABLE `self_care_steps` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`step_id` TEXT NOT NULL" +
                            ")"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) {
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun habits_gainsThreeVersioningColumns() {
        val helper = openV61()
        val db = helper.writableDatabase

        MIGRATION_61_62.migrate(db)

        db.query("PRAGMA table_info(`habits`)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(1))
            assertTrue("missing source_version, found $names", "source_version" in names)
            assertTrue("missing is_user_modified, found $names", "is_user_modified" in names)
            assertTrue(
                "missing is_detached_from_template, found $names",
                "is_detached_from_template" in names
            )
        }
        helper.close()
    }

    @Test
    fun selfCareSteps_gainsSourceVersionColumn() {
        val helper = openV61()
        val db = helper.writableDatabase

        MIGRATION_61_62.migrate(db)

        db.query("PRAGMA table_info(`self_care_steps`)").use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(1))
            assertTrue("missing source_version, found $names", "source_version" in names)
        }
        helper.close()
    }

    @Test
    fun builtInHabits_backfillToVersionOne() {
        val helper = openV61()
        val db = helper.writableDatabase

        // Three rows: one built-in, one built-in with NULL template_key
        // (defensive — migration only touches non-null), one user habit.
        db.execSQL(
            "INSERT INTO habits (name, is_built_in, template_key) " +
                "VALUES ('School', 1, 'builtin_school')"
        )
        db.execSQL(
            "INSERT INTO habits (name, is_built_in, template_key) " +
                "VALUES ('Stale Built-in', 1, NULL)"
        )
        db.execSQL(
            "INSERT INTO habits (name, is_built_in, template_key) " +
                "VALUES ('My Custom', 0, NULL)"
        )

        MIGRATION_61_62.migrate(db)

        db.query("SELECT name, source_version FROM habits ORDER BY name").use { c ->
            assertTrue(c.moveToFirst())
            // Asc: My Custom (0), School (1), Stale Built-in (0 — no template_key)
            assertEquals("My Custom", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.moveToNext())
            assertEquals("School", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertTrue(c.moveToNext())
            assertEquals("Stale Built-in", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
        helper.close()
    }

    @Test
    fun selfCareSteps_backfillEverythingToVersionOne() {
        val helper = openV61()
        val db = helper.writableDatabase

        db.execSQL("INSERT INTO self_care_steps (step_id) VALUES ('sc_water')")
        db.execSQL("INSERT INTO self_care_steps (step_id) VALUES ('sc_walk')")

        MIGRATION_61_62.migrate(db)

        db.query("SELECT step_id, source_version FROM self_care_steps ORDER BY step_id").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("sc_walk", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertTrue(c.moveToNext())
            assertEquals("sc_water", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
        helper.close()
    }
}
