package com.averycorp.prismtask.testing

import com.averycorp.prismtask.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Debug-only helper for signing into the Firebase Auth emulator.
 *
 * The Auth emulator does not validate passwords — any email+password pair
 * creates or signs into a test account. Use this to bootstrap two-device
 * sync tests without running through the real Google Sign-In flow.
 *
 * This file lives in the `debug` source set and is never compiled into a
 * release APK. Do NOT wire it into the production sign-in path.
 */
object EmulatorAuthHelper {
    const val DEFAULT_EMAIL = "test@prismtask.local"
    const val DEFAULT_PASSWORD = "testpass"

    /**
     * Signs into the Auth emulator as [email]/[password], creating the test
     * account on first use. Safe to call repeatedly. Requires the
     * compile-time [BuildConfig.USE_FIREBASE_EMULATOR] flag to be on so it
     * fails loudly if someone mistakenly points it at production auth.
     */
    suspend fun signInAsTestUser(
        email: String = DEFAULT_EMAIL,
        password: String = DEFAULT_PASSWORD
    ) {
        require(BuildConfig.USE_FIREBASE_EMULATOR) {
            "EmulatorAuthHelper requires USE_FIREBASE_EMULATOR = true"
        }
        val auth = FirebaseAuth.getInstance()
        try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (_: Exception) {
            // Account already exists on this emulator instance — fall
            // through and sign in with the same credentials.
            auth.signInWithEmailAndPassword(email, password).await()
        }
    }
}
