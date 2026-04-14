package com.averycorp.prismtask.startup

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Verifies that all `FirebaseCrashlytics.getInstance()` and
 * `FirebaseAuth.getInstance()` calls are wrapped in try-catch blocks.
 *
 * Firebase SDK calls throw [IllegalStateException] when the default
 * [com.google.firebase.FirebaseApp] has not been initialized (e.g.,
 * invalid or placeholder google-services.json). If such a call happens
 * during Application.onCreate() or Activity.onCreate() without a
 * catch, it kills the process immediately.
 *
 * Previous startup crashes (PRs #331, #332) were caused by this pattern.
 * This test ensures no unguarded Firebase calls exist in startup-critical
 * code paths.
 */
class FirebaseSafetyTest {
    private val srcMainDir = File("app/src/main/java/com/averycorp/prismtask")

    /**
     * Files that are on the startup critical path — they execute during
     * Application.onCreate() or Activity.onCreate() and must guard all
     * Firebase calls with try-catch.
     */
    private val startupCriticalFiles = listOf(
        "PrismTaskApplication.kt",
        "MainActivity.kt",
        "data/remote/AuthManager.kt"
    )

    @Test
    fun `startup files guard FirebaseCrashlytics calls with try-catch`() {
        if (!srcMainDir.exists()) return // CI may run from a different root

        for (relativePath in startupCriticalFiles) {
            val file = File(srcMainDir, relativePath)
            if (!file.exists()) continue

            val content = file.readText()
            val lines = content.lines()

            // Find all lines with FirebaseCrashlytics.getInstance()
            lines.forEachIndexed { index, line ->
                if (line.contains("FirebaseCrashlytics.getInstance()")) {
                    // Check that this line is inside a try block.
                    // We look backward through the preceding lines for a
                    // `try {` that hasn't been closed yet.
                    val isSafe = isInsideTryCatch(lines, index)
                    assertTrue(
                        "FirebaseCrashlytics.getInstance() at $relativePath:${index + 1} " +
                            "is not inside a try-catch block. This will crash at startup " +
                            "if Firebase is not initialized. Wrap it in try { ... } catch (e: Exception) { }.",
                        isSafe
                    )
                }
            }
        }
    }

    @Test
    fun `startup files guard FirebaseAuth calls with try-catch`() {
        if (!srcMainDir.exists()) return

        for (relativePath in startupCriticalFiles) {
            val file = File(srcMainDir, relativePath)
            if (!file.exists()) continue

            val content = file.readText()
            val lines = content.lines()

            lines.forEachIndexed { index, line ->
                if (line.contains("FirebaseAuth.getInstance()")) {
                    val isSafe = isInsideTryCatch(lines, index)
                    assertTrue(
                        "FirebaseAuth.getInstance() at $relativePath:${index + 1} " +
                            "is not inside a try-catch block. This will crash at startup " +
                            "if Firebase is not initialized.",
                        isSafe
                    )
                }
            }
        }
    }

    @Test
    fun `Application onCreate does not call Firebase before try-catch`() {
        if (!srcMainDir.exists()) return
        val file = File(srcMainDir, "PrismTaskApplication.kt")
        if (!file.exists()) return

        val content = file.readText()

        // Verify that configureCrashlytics wraps its Firebase call
        val configureMethod = extractMethodBody(content, "configureCrashlytics")
        if (configureMethod != null && configureMethod.contains("FirebaseCrashlytics.getInstance()")) {
            assertTrue(
                "configureCrashlytics() must wrap FirebaseCrashlytics.getInstance() " +
                    "in try-catch to survive Firebase init failure",
                configureMethod.contains("try")
            )
        }
    }

    @Test
    fun `MainActivity setCrashlyticsUserId is called inside try-catch`() {
        if (!srcMainDir.exists()) return
        val file = File(srcMainDir, "MainActivity.kt")
        if (!file.exists()) return

        val content = file.readText()
        val lines = content.lines()

        // Find where setCrashlyticsUserId() is called in onCreate
        val onCreateStart = lines.indexOfFirst { it.contains("override fun onCreate") }
        if (onCreateStart < 0) return

        val callLine = lines.indexOfFirst {
            it.contains("setCrashlyticsUserId()")
        }
        if (callLine < 0) return

        val isSafe = isInsideTryCatch(lines, callLine)
        assertTrue(
            "setCrashlyticsUserId() call at MainActivity.kt:${callLine + 1} must " +
                "be inside a try-catch block to survive Firebase init failure",
            isSafe
        )
    }

    @Test
    fun `AuthManager handles FirebaseAuth init failure gracefully`() {
        if (!srcMainDir.exists()) return
        val file = File(srcMainDir, "data/remote/AuthManager.kt")
        if (!file.exists()) return

        val content = file.readText()

        // AuthManager should either lazy-init FirebaseAuth or wrap it in try-catch.
        // It's on the DI critical path, so an unprotected call will crash during
        // Hilt graph construction.
        val hasProtection = content.contains("try") &&
            (content.contains("FirebaseAuth.getInstance()") || content.contains("by lazy"))

        assertTrue(
            "AuthManager must protect FirebaseAuth.getInstance() with try-catch " +
                "or lazy initialization. An unguarded call crashes during Hilt " +
                "graph construction if Firebase is not initialized.",
            hasProtection
        )
    }

    /**
     * Rough heuristic: checks whether the line at [targetIndex] is nested
     * inside a `try` block by scanning backwards for unmatched `try {`.
     */
    private fun isInsideTryCatch(lines: List<String>, targetIndex: Int): Boolean {
        var braceDepth = 0
        for (i in targetIndex downTo 0) {
            val line = lines[i].trim()
            // Count closing braces going backwards (they become opens)
            braceDepth += line.count { it == '}' }
            braceDepth -= line.count { it == '{' }
            if (line.startsWith("try") || line.contains("} catch") || line == "try {") {
                if (braceDepth <= 0) return true
            }
            // If we hit a function or class declaration, stop searching
            if (line.startsWith("fun ") ||
                line.startsWith("override fun ") ||
                line.startsWith("class ") ||
                line.startsWith("object ")
            ) {
                return false
            }
        }
        return false
    }

    /**
     * Extracts the body of a named private function from source code.
     */
    private fun extractMethodBody(source: String, methodName: String): String? {
        val pattern = Regex("""private fun $methodName\(\)[^{]*\{""")
        val match = pattern.find(source) ?: return null
        val start = match.range.last + 1
        var depth = 1
        var end = start
        while (end < source.length && depth > 0) {
            when (source[end]) {
                '{' -> depth++
                '}' -> depth--
            }
            end++
        }
        return source.substring(start, end)
    }
}
