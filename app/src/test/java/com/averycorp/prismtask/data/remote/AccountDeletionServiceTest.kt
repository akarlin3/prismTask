package com.averycorp.prismtask.data.remote

import com.averycorp.prismtask.data.remote.AccountDeletionService.Companion.ALL_PREFERENCE_DATASTORE_NAMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Static-analysis tests for [AccountDeletionService].
 *
 * Behavioral tests of the full Firestore-mark + signOut + Room-wipe chain
 * live in androidTest because mocking [com.google.firebase.firestore.FirebaseFirestore]
 * cleanly is a yak shave we don't want to take on for a single class. These
 * tests instead protect the structural invariants that would make
 * AccountDeletionService unsafe if violated:
 *
 *   - Firestore mark failure must abort before any local state changes
 *   - The DataStore-file enumeration must cover every actual ``preferencesDataStore``
 *     declaration in the codebase (otherwise a future-added preference quietly
 *     leaks across deleted accounts)
 *   - The broken hard-delete ``AuthManager.deleteAccount`` must not have been
 *     resurrected (the soft-delete model relies on signOut, not delete)
 */
class AccountDeletionServiceTest {

    @Test
    fun `Firestore mark failure aborts before any local state changes`() {
        val source = readService()

        // The Firestore mark is wrapped in a try/catch that returns
        // Result.failure on exception. Any local-state mutation (sign out,
        // clearAllTables, file deletion) MUST come after the markFirestorePending
        // call returns successfully. The simplest invariant we can assert
        // statically is: ``cleanLocalState()`` is invoked only after the
        // markFirestorePending try/catch returns successfully.
        val markIdx = source.indexOf("markFirestorePending(uid, initiatedFrom)")
        val cleanIdx = source.indexOf("cleanLocalState()")
        assertTrue(
            "markFirestorePending call site not found",
            markIdx > 0
        )
        assertTrue(
            "cleanLocalState call site not found",
            cleanIdx > 0
        )
        assertTrue(
            "cleanLocalState() must be called AFTER markFirestorePending — " +
                "otherwise local state changes happen even on Firestore failure, " +
                "leaving the user with no soft-delete record but a wiped device.",
            cleanIdx > markIdx
        )

        // The catch around markFirestorePending must call return Result.failure.
        val firestoreCatch = source.substringAfter("markFirestorePending(uid, initiatedFrom)")
            .substringBefore("// Best-effort:")
        assertTrue(
            "markFirestorePending failure path must return Result.failure(e) so the " +
                "user sees an error rather than a silent half-deletion",
            firestoreCatch.contains("return Result.failure(e)")
        )
    }

    @Test
    fun `every preferencesDataStore name in the codebase is covered (or explicitly excluded)`() {
        val srcDir = listOf(File("src/main/java"), File("app/src/main/java"))
            .firstOrNull { it.isDirectory }
            ?: error("Couldn't find src/main/java under any candidate root")

        // Names we deliberately exclude. Document why in the AccountDeletionService
        // KDoc — these are widget UI state with no PII and the widget itself is
        // expected to keep working across sign-outs (showing an empty/sign-in
        // prompt when the underlying data is gone).
        val intentionallyExcluded = setOf("widget_config", "timer_widget_state")

        // Pull every name = "X" from preferencesDataStore declarations in source.
        // The declaration may span multiple lines (we have at least three of those),
        // so read each .kt file and regex over the whole content with DOTALL.
        val pattern = Regex(
            """preferencesDataStore\([^)]*?name\s*=\s*"([^"]+)"""",
            RegexOption.DOT_MATCHES_ALL
        )

        val foundNames = sortedSetOf<String>()
        srcDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name != "AccountDeletionService.kt" } // self-reference
            .filter { it.name != "AccountDeletionServiceTest.kt" }
            .forEach { file ->
                pattern.findAll(file.readText()).forEach { match ->
                    foundNames.add(match.groupValues[1])
                }
            }

        val expected = ALL_PREFERENCE_DATASTORE_NAMES.toSet() + intentionallyExcluded

        val missing = foundNames - expected
        assertTrue(
            "AccountDeletionService.ALL_PREFERENCE_DATASTORE_NAMES is missing entries: " +
                "$missing. Either add them to the list (so account-delete wipes them) " +
                "or add them to the intentionallyExcluded set in this test (with a " +
                "documented reason). Otherwise a deleted user's data leaks across to " +
                "the next person who signs in on this device.",
            missing.isEmpty()
        )

        val unknownInList = ALL_PREFERENCE_DATASTORE_NAMES.toSet() - foundNames
        assertTrue(
            "ALL_PREFERENCE_DATASTORE_NAMES contains entries that don't match any " +
                "preferencesDataStore declaration in source: $unknownInList. " +
                "These are typos or stale entries — remove them.",
            unknownInList.isEmpty()
        )
    }

    @Test
    fun `AuthManager_deleteAccount is removed (broken hard-delete primitive)`() {
        val authManager = sourceFile("com/averycorp/prismtask/data/remote/AuthManager.kt")
        val content = authManager.readText().replace("\r\n", "\n")

        assertFalse(
            "AuthManager.deleteAccount() called Firebase Auth currentUser.delete() at delete-time, " +
                "which is a hard-delete that doesn't fit the new soft-delete model and triggers " +
                "FirebaseAuthRecentLoginRequiredException for sessions older than ~5 minutes. " +
                "The new flow uses AccountDeletionService.requestAccountDeletion() (signs out, " +
                "marks Firestore deletion-pending) and the BACKEND deletes the Firebase Auth " +
                "record at /me/purge time via Firebase Admin SDK after the grace window expires.",
            content.contains("fun deleteAccount")
        )

        // currentUser.delete() should not appear anywhere in AuthManager — the
        // backend handles permanent Firebase Auth deletion via Firebase Admin SDK.
        assertFalse(
            "AuthManager must not call currentUser.delete() — that's the backend's " +
                "responsibility (via firebase_admin.auth.delete_user) at permanent-cleanup time.",
            content.contains("currentUser?.delete()") || content.contains("currentUser.delete()")
        )
    }

    @Test
    fun `cleanLocalState wraps every step in runCatching for partial-failure resilience`() {
        val source = readService()
        val cleanLocalStateBody = source.substringAfter("private suspend fun cleanLocalState() {")
            .substringBefore("    /**")

        // "cancelAll" matches both NotificationWorkerScheduler.cancelAllForAccountDeletion()
        // and NotificationManager.cancelAll() — the latter is the one we're guarding here.
        val expectedStepKeywords = listOf(
            "stopRealtimeListeners",
            "signOut",
            "clearAllTables",
            "wipeAllPreferenceFiles",
            "cancelAllForAccountDeletion",
            "cancelAll",
            "clearCredentialState"
        )
        expectedStepKeywords.forEach { keyword ->
            assertTrue(
                "cleanLocalState should call $keyword. Missing — see AccountDeletionService.cleanLocalState.",
                cleanLocalStateBody.contains(keyword)
            )
        }

        // Each call should be inside a runCatching block so one step's failure
        // doesn't abort the rest. We assert the count of runCatching blocks
        // matches the number of distinct steps.
        val runCatchingCount = Regex("""runCatching\s*\{""")
            .findAll(cleanLocalStateBody)
            .count()
        assertEquals(
            "cleanLocalState should wrap each cleanup step in its own runCatching " +
                "so one failing step doesn't abort the others. Expected one runCatching " +
                "per step (see expectedStepKeywords).",
            expectedStepKeywords.size,
            runCatchingCount
        )
    }

    @Test
    fun `grace window is exactly 30 days (matches privacy policy and backend constant)`() {
        assertEquals(30, AccountDeletionService.GRACE_DAYS)
    }

    @Test
    fun `AuthViewModel sign-in handler routes through deletion guard before runPostSignInSync`() {
        val viewModel = sourceFile("com/averycorp/prismtask/ui/screens/auth/AuthViewModel.kt").readText()

        // The guard must be called from onGoogleSignIn AFTER auth succeeds —
        // otherwise sync starts pulling data into a Room DB that may be
        // about to be wiped (RestorePending) or that belongs to an account
        // we're about to permanently purge (Expired). Normalize CRLF -> LF
        // so the body-bound regex works on both Windows and Unix checkouts.
        val normalized = viewModel.replace("\r\n", "\n")
        val onSignInBody = normalized
            .substringAfter("fun onGoogleSignIn(idToken: String) {")
            .substringBefore("\n    }\n")
        assertTrue(
            "onGoogleSignIn must call handlePostAuthDeletionGuard() — otherwise sign-in " +
                "skips the deletion-pending check and bypasses the soft-delete grace window.",
            onSignInBody.contains("handlePostAuthDeletionGuard()")
        )
        // It must NOT call runPostSignInSync directly — sync is gated on the
        // guard's outcome. The guard itself calls runPostSignInSync from the
        // NotPending branch, but onGoogleSignIn must not bypass the guard.
        assertFalse(
            "onGoogleSignIn must NOT call runPostSignInSync() directly. The guard " +
                "decides whether sync runs (NotPending) or is suppressed (RestorePending / Expired).",
            onSignInBody.contains("runPostSignInSync()")
        )

        // The guard must call accountDeletionService.checkDeletionStatus()
        // and route on each of the three sealed sub-states.
        val guardBody = normalized
            .substringAfter("private suspend fun handlePostAuthDeletionGuard()")
            .substringBefore("/**")
        assertTrue(
            "Guard must call accountDeletionService.checkDeletionStatus()",
            guardBody.contains("accountDeletionService.checkDeletionStatus()")
        )
        listOf("NotPending", "Pending", "Expired").forEach { branch ->
            assertTrue(
                "Guard must route on the $branch sub-state",
                guardBody.contains(branch)
            )
        }
        assertTrue(
            "Guard must call executePermanentPurge() when grace window has expired",
            guardBody.contains("executePermanentPurge()")
        )
    }

    /** Read source with CRLF normalized to LF so substring patterns work
     *  on both Windows and Unix checkouts. */
    private fun readService(): String =
        sourceFile("com/averycorp/prismtask/data/remote/AccountDeletionService.kt")
            .readText()
            .replace("\r\n", "\n")

    /** Resolves a source file across both project-root and module-root cwd cases.
     *
     * Gradle runs unit tests with the cwd at the module root (``app/``); IntelliJ
     * sometimes runs from the project root. Existing static-analysis tests in this
     * codebase silently skip on missing files — we'd rather fail loudly here. */
    private fun sourceFile(relativeFromJava: String): File {
        val candidates = listOf(
            File("src/main/java/$relativeFromJava"),
            File("app/src/main/java/$relativeFromJava")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error(
                "Couldn't find source file $relativeFromJava under any candidate root. " +
                    "Tried: ${candidates.map { it.absolutePath }}"
            )
    }
}
