package com.averycorp.prismtask.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cross-platform canonical store for "user has completed onboarding".
 *
 * Both Android and web read/write the same Firestore field so that
 * completing onboarding on one client satisfies the gate on the other.
 * The canonical location is the top-level field
 * `users/{uid}.onboardingCompletedAt` (a millisecond epoch).
 *
 * Web reference: `web/src/stores/onboardingStore.ts`. Android continues
 * to mirror onboarding state into the local `onboarding_prefs` DataStore
 * (which still syncs through `GenericPreferenceSyncService`) so offline
 * reads stay instant; this class augments that mirror with a
 * cross-device source of truth that doesn't depend on the per-account
 * `users/{uid}/prefs/onboarding_prefs` doc that the web client doesn't
 * touch.
 *
 * All operations are best-effort: if Firestore is unreachable, missing,
 * or denied by rules, this class swallows the error and returns `null`
 * (read) or no-op completes (write). Onboarding gates degrade to
 * "device-local DataStore decides" rather than crashing — the same
 * graceful-degradation contract the web store uses.
 */
@Singleton
class CanonicalOnboardingSync
@Inject
constructor() {
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseFirestore init failed; canonical onboarding sync disabled", e)
        null
    }

    /**
     * Reads `users/{uid}.onboardingCompletedAt`. Returns the timestamp if
     * present and finite, `null` otherwise (including on any failure —
     * callers should treat `null` as "unknown", not "definitely not
     * completed").
     */
    suspend fun readCompletedAt(uid: String): Long? {
        val db = firestore ?: return null
        return try {
            val snap = db.collection(USERS).document(uid).get().await()
            if (!snap.exists()) return null
            when (val raw = snap.get(FIELD_COMPLETED_AT)) {
                is Number -> raw.toLong().takeIf { it > 0L }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read canonical onboarding flag for uid=$uid", e)
            null
        }
    }

    /**
     * Writes `users/{uid}.onboardingCompletedAt = timestampMs` with
     * `merge: true` so we don't clobber other fields the user doc may
     * accumulate. Best-effort; logs and returns on failure.
     */
    suspend fun writeCompletedAt(uid: String, timestampMs: Long) {
        val db = firestore ?: return
        if (timestampMs <= 0L) return
        try {
            db.collection(USERS)
                .document(uid)
                .set(mapOf(FIELD_COMPLETED_AT to timestampMs), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write canonical onboarding flag for uid=$uid", e)
        }
    }

    companion object {
        private const val TAG = "CanonicalOnboardingSync"
        private const val USERS = "users"
        private const val FIELD_COMPLETED_AT = "onboardingCompletedAt"
    }
}
