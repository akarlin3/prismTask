package com.averycorp.prismtask.data.remote

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.e("AuthManager", "FirebaseAuth init failed — Firebase may not be configured", e)
        null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val currentUser: StateFlow<FirebaseUser?> = if (auth != null) {
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
            auth.addAuthStateListener(listener)
            awaitClose { auth.removeAuthStateListener(listener) }
        }.stateIn(scope, SharingStarted.Eagerly, auth.currentUser)
    } else {
        kotlinx.coroutines.flow.MutableStateFlow<FirebaseUser?>(null)
    }

    val isSignedIn: StateFlow<Boolean> = currentUser
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, auth?.currentUser != null)

    val userId: String? get() = auth?.currentUser?.uid

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = try {
        val firebaseAuth = auth
            ?: return Result.failure(IllegalStateException("Firebase Auth not available"))
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        val user = result.user
            ?: return Result.failure(IllegalStateException("Sign-in succeeded but user is null"))
        Result.success(user)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signOut() {
        auth?.signOut()
        clearCredentialState()
    }

    /**
     * Clears any cached Google credential held by Credential Manager so the
     * next sign-in won't auto-select a stale/revoked account (which causes
     * reauth failures). Safe to call even when nothing is cached.
     */
    suspend fun clearCredentialState() {
        try {
            CredentialManager
                .create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (e: ClearCredentialException) {
            Log.w("AuthManager", "Failed to clear credential state", e)
        }
    }

    suspend fun deleteAccount() {
        auth?.currentUser?.delete()?.await()
        clearCredentialState()
    }
}
