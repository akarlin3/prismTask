package com.averycorp.prismtask.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Compile-time-ish sentinel: greps the source files of the migration
 * telemetry surface and fails the build if a forbidden PII-shaped
 * key/value is referenced. Catches accidents like
 * `setCustomKey("medication_name", med.name)` long before they
 * land in Crashlytics.
 *
 * This test is deliberately a JVM unit test — it reads source from
 * the working tree, so it runs every time `testDebugUnitTest`
 * fires in CI.
 */
class MigrationTelemetryPiiTest {
    private val forbiddenSubstrings = listOf(
        "medication_name",
        "med_name",
        "user_id",
        "user_email",
        "email",
        "title",
        "notes",
        "label",
        "dose"
    )

    private val targetFiles = listOf(
        "app/src/main/java/com/averycorp/prismtask/data/diagnostics/MigrationInstrumentor.kt",
        "app/src/main/java/com/averycorp/prismtask/data/local/database/InstrumentedMigrations.kt",
        "app/src/main/java/com/averycorp/prismtask/domain/model/telemetry/MigrationTelemetryEvent.kt"
    )

    @Test
    fun `migration telemetry source files contain no PII-shaped tokens`() {
        val violations = mutableListOf<String>()
        for (path in targetFiles) {
            val file = resolveSourceFile(path)
            assertTrue(
                "Migration telemetry source file should exist: $path",
                file.exists()
            )
            val source = file.readText()
            // Scan only setCustomKey / putString / putLong / putInt /
            // logEvent argument neighbourhoods. A blanket grep would
            // false-positive on every comment that reasonably
            // mentions "medication". Limit to call sites.
            val callSiteRegex = Regex(
                "(setCustomKey|putString|putLong|putInt|logEvent)\\s*\\(([^)]*)\\)"
            )
            for (match in callSiteRegex.findAll(source)) {
                val args = match.groupValues[2].lowercase()
                for (forbidden in forbiddenSubstrings) {
                    if (args.contains(forbidden)) {
                        violations.add(
                            "$path: forbidden token '$forbidden' inside call site `${match.value}`"
                        )
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            fail(
                "PII sentinel tripped — migration telemetry must never include " +
                    "row content in event arguments:\n" +
                    violations.joinToString("\n")
            )
        }
    }

    @Test
    fun `MigrationTelemetryEvent declares only primitive-typed fields`() {
        val file = resolveSourceFile(
            "app/src/main/java/com/averycorp/prismtask/domain/model/telemetry/MigrationTelemetryEvent.kt"
        )
        assertTrue("MigrationTelemetryEvent.kt must exist", file.exists())
        val source = file.readText()
        // Allowlist: data class properties whose types are Int, Long,
        // String, or String?. Anything else (Map, list, custom type)
        // is rejected so the surface stays primitive-only.
        val propertyRegex = Regex("""val\s+\w+\s*:\s*([A-Za-z?]+)""")
        val allowed = setOf("Int", "Long", "String", "String?")
        val violations = propertyRegex
            .findAll(source)
            .map { it.groupValues[1] }
            .filterNot { it in allowed }
            .toList()
        assertEquals(
            "Non-primitive property types found in MigrationTelemetryEvent: $violations",
            emptyList<String>(),
            violations
        )
    }

    private fun resolveSourceFile(relativePath: String): File {
        // Unit tests run with CWD = `app/`; walk up to repo root if
        // the literal path doesn't resolve.
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val viaApp = File("../$relativePath")
        if (viaApp.exists()) return viaApp
        return direct // let the caller's existence assertion fail
    }
}
