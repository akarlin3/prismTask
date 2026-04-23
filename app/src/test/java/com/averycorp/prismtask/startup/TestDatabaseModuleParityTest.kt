package com.averycorp.prismtask.startup

import com.averycorp.prismtask.data.local.database.PrismTaskDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against a recurring drift where a new DAO lands on
 * [PrismTaskDatabase] (via a feature PR adding a Room entity) without a
 * matching `@Provides` in
 * `app/src/androidTest/.../smoke/TestDatabaseModule.kt`.
 *
 * `TestDatabaseModule` replaces the production `DatabaseModule` under
 * `@HiltAndroidTest` via `@TestInstallIn`, so every missing provider breaks
 * the Hilt component graph for every instrumented `@HiltAndroidTest` — a
 * failure that only surfaces inside the expensive Android Integration CI
 * job. This JVM-only test fails fast at `./gradlew testDebugUnitTest` time
 * instead.
 *
 * The test uses reflection for the DB side (robust to refactors) and
 * regex text-parsing for the test module (androidTest sources aren't on
 * the unit-test classpath).
 */
class TestDatabaseModuleParityTest {

    @Test
    fun `every PrismTaskDatabase DAO has a matching TestDatabaseModule provider`() {
        val dbDaoAccessors: Set<String> = PrismTaskDatabase::class.java.methods
            .filter { it.name.endsWith("Dao") && it.parameterCount == 0 }
            .filter { it.returnType.simpleName.endsWith("Dao") }
            .map { it.name } // e.g. "taskDao", "batchUndoLogDao"
            .toSet()

        val testModuleFile = resolveTestModuleFile()
        val testModuleSrc = testModuleFile.readText()

        // Match lines like:
        //   fun provideFooDao(database: PrismTaskDatabase) = database.fooDao()
        // Extract the accessor on the right-hand side (canonical source of truth).
        val accessorPattern = Regex("""database\.([a-zA-Z0-9_]+Dao)\(\)""")
        val providedAccessors: Set<String> = accessorPattern
            .findAll(testModuleSrc)
            .map { it.groupValues[1] }
            .toSet()

        val missing = dbDaoAccessors - providedAccessors
        val extraneous = providedAccessors - dbDaoAccessors

        assertTrue(
            "TestDatabaseModule is missing @Provides for: $missing. " +
                "Every new DAO on PrismTaskDatabase must be mirrored there or " +
                "@HiltAndroidTest instrumented tests fail to compile their " +
                "Hilt component graph. Add:\n" +
                missing.joinToString("\n") { dao ->
                    "    @Provides\n" +
                        "    fun provide${dao.replaceFirstChar { it.uppercaseChar() }}" +
                        "(database: PrismTaskDatabase) = database.$dao()"
                },
            missing.isEmpty()
        )

        assertEquals(
            "TestDatabaseModule has extraneous providers for accessors that " +
                "no longer exist on PrismTaskDatabase: $extraneous",
            emptySet<String>(),
            extraneous
        )
    }

    /**
     * Unit tests run from the `app/` module dir, so
     * `src/androidTest/...` is the normal path. Fall back to `app/src/...`
     * in case some harness runs from the repo root.
     */
    private fun resolveTestModuleFile(): File {
        val candidates = listOf(
            "src/androidTest/java/com/averycorp/prismtask/smoke/TestDatabaseModule.kt",
            "app/src/androidTest/java/com/averycorp/prismtask/smoke/TestDatabaseModule.kt"
        )
        for (path in candidates) {
            val f = File(path)
            if (f.exists()) return f
        }
        error(
            "Could not locate TestDatabaseModule.kt. Tried: $candidates — " +
                "working dir is ${File(".").absolutePath}"
        )
    }
}
