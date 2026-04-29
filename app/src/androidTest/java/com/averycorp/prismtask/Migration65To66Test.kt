package com.averycorp.prismtask

import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.averycorp.prismtask.data.local.database.MIGRATION_65_66
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration test for v65 → v66 — backfills `show_streak = 1` on the
 * 6 built-in habits so existing installs see the streak badge that
 * `BuiltInHabitCard` and `SelfCareRoutineCard` already render.
 *
 * Verifies the backfill: only flips rows where `is_built_in = 1 AND
 * show_streak = 0 AND is_user_modified = 0`. User-modified rows and
 * non-built-in rows are left alone so the user's choice is preserved.
 */
@RunWith(AndroidJUnit4::class)
class Migration65To66Test {

    private fun openV65(): SupportSQLiteOpenHelper {
        val config = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        )
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(65) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE `habits` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`is_built_in` INTEGER NOT NULL DEFAULT 0, " +
                            "`show_streak` INTEGER NOT NULL DEFAULT 0, " +
                            "`is_user_modified` INTEGER NOT NULL DEFAULT 0" +
                            ")"
                    )
                    // Built-in, default — should flip.
                    db.execSQL(
                        "INSERT INTO `habits` (id, name, is_built_in, show_streak, is_user_modified) " +
                            "VALUES (1, 'School', 1, 0, 0)"
                    )
                    // Built-in, user-modified — must stay off.
                    db.execSQL(
                        "INSERT INTO `habits` (id, name, is_built_in, show_streak, is_user_modified) " +
                            "VALUES (2, 'Leisure', 1, 0, 1)"
                    )
                    // Built-in, already on — left as-is (the WHERE excludes it).
                    db.execSQL(
                        "INSERT INTO `habits` (id, name, is_built_in, show_streak, is_user_modified) " +
                            "VALUES (3, 'Morning Self-Care', 1, 1, 0)"
                    )
                    // User-created habit — never touched.
                    db.execSQL(
                        "INSERT INTO `habits` (id, name, is_built_in, show_streak, is_user_modified) " +
                            "VALUES (4, 'Drink Water', 0, 0, 0)"
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

    private fun showStreakOf(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: Long
    ): Int {
        var v = -1
        db.query("SELECT show_streak FROM habits WHERE id = $id").use { c ->
            c.moveToFirst()
            v = c.getInt(0)
        }
        return v
    }

    @Test
    fun migration_flipsShowStreakOnUnmodifiedBuiltIns() {
        val helper = openV65()
        val db = helper.writableDatabase

        MIGRATION_65_66.migrate(db)

        assertEquals("unmodified built-in flipped on", 1, showStreakOf(db, 1))
        assertEquals("user-modified built-in left untouched", 0, showStreakOf(db, 2))
        assertEquals("already-on built-in stays on", 1, showStreakOf(db, 3))
        assertEquals("user-created habit untouched", 0, showStreakOf(db, 4))

        helper.close()
    }
}
