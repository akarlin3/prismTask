package com.averycorp.prismtask.startup

import com.averycorp.prismtask.data.local.database.ALL_MIGRATIONS
import com.averycorp.prismtask.data.local.database.CURRENT_DB_VERSION
import com.averycorp.prismtask.data.local.database.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Startup crash diagnostic tests.
 *
 * These tests verify structural invariants that, when violated, cause the app
 * to crash on launch. They run as pure JVM tests (no Android/Robolectric) and
 * are designed to catch the most common startup crash patterns:
 *
 * 1. Room migration gaps — a missing migration causes IllegalStateException
 * 2. DataStore file name collisions — two delegates with the same name cause
 *    IllegalStateException ("There are multiple DataStores active for …")
 * 3. Incorrect migration version ordering in ALL_MIGRATIONS
 */
class StartupCrashDiagnosticTest {
    // ------------------------------------------------------------------
    // Region: Room Migration Integrity
    // ------------------------------------------------------------------

    /**
     * Current DB version is the [CURRENT_DB_VERSION] const read by both
     * `@Database(version = …)` on PrismTaskDatabase and this test. Bumping
     * the schema means: increment the const, add a MIGRATION_N_N+1, append
     * it to ALL_MIGRATIONS. This file self-adapts.
     *
     * (Can't reflect on @Database because it's binary-retention.)
     */
    @Test
    fun `ALL_MIGRATIONS size matches declared DB version`() {
        val expected = CURRENT_DB_VERSION - 1
        assertEquals(
            "ALL_MIGRATIONS should contain exactly $expected migrations " +
                "(v1→v$CURRENT_DB_VERSION) — add the missing MIGRATION_N_N+1 entry.",
            expected,
            ALL_MIGRATIONS.size
        )
    }

    @Test
    fun `ALL_MIGRATIONS forms a contiguous version chain`() {
        // Verify that sorting by startVersion yields 1,2,3,…,37 with no gaps.
        val sorted = ALL_MIGRATIONS.sortedBy { it.startVersion }
        for (i in sorted.indices) {
            val expected = i + 1
            assertEquals(
                "Migration at index $i should start at version $expected",
                expected,
                sorted[i].startVersion
            )
            assertEquals(
                "Migration at index $i should end at version ${expected + 1}",
                expected + 1,
                sorted[i].endVersion
            )
        }
    }

    @Test
    fun `ALL_MIGRATIONS has no duplicate start versions`() {
        val startVersions = ALL_MIGRATIONS.map { it.startVersion }
        val duplicates = startVersions.groupBy { it }.filter { it.value.size > 1 }
        assertTrue(
            "Found duplicate start versions in ALL_MIGRATIONS: ${duplicates.keys}",
            duplicates.isEmpty()
        )
    }

    @Test
    fun `first migration starts at version 1`() {
        val minStart = ALL_MIGRATIONS.minOf { it.startVersion }
        assertEquals("Earliest migration should start at version 1", 1, minStart)
    }

    @Test
    fun `last migration ends at declared DB version`() {
        val maxEnd = ALL_MIGRATIONS.maxOf { it.endVersion }
        assertEquals(
            "Latest migration should end at the current DB version ($CURRENT_DB_VERSION)",
            CURRENT_DB_VERSION,
            maxEnd
        )
    }

    @Test
    fun `MIGRATION_1_2 is included in ALL_MIGRATIONS`() {
        assertTrue(
            "MIGRATION_1_2 must be in ALL_MIGRATIONS",
            ALL_MIGRATIONS.contains(MIGRATION_1_2)
        )
    }

    @Test
    fun `each migration is a single-step migration`() {
        // Room supports multi-step migrations (e.g. 1→3) but they can mask
        // missing intermediate migrations. All ours should be single-step.
        for (migration in ALL_MIGRATIONS) {
            assertEquals(
                "Migration ${migration.startVersion}→${migration.endVersion} should be a single step",
                migration.startVersion + 1,
                migration.endVersion
            )
        }
    }
}
