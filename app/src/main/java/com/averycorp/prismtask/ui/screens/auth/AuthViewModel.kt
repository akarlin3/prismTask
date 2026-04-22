package com.averycorp.prismtask.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.BuildConfig
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.GenericPreferenceSyncService
import com.averycorp.prismtask.data.remote.SortPreferencesSyncService
import com.averycorp.prismtask.data.remote.SyncService
import com.averycorp.prismtask.data.remote.ThemePreferencesSyncService
import com.averycorp.prismtask.testing.EmulatorAuthHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    data object SignedOut : AuthState()

    data object Loading : AuthState()

    data object SignedIn : AuthState()

    data class Error(
        val message: String
    ) : AuthState()
}

@HiltViewModel
class AuthViewModel
@Inject
constructor(
    private val authManager: AuthManager,
    private val syncService: SyncService,
    private val sortPreferencesSyncService: SortPreferencesSyncService,
    private val themePreferencesSyncService: ThemePreferencesSyncService,
    private val genericPreferenceSyncService: GenericPreferenceSyncService
) : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(
        if (authManager.isSignedIn.value) AuthState.SignedIn else AuthState.SignedOut
    )
    val authState: StateFlow<AuthState> = _authState

    val isSignedIn = authManager.isSignedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, authManager.isSignedIn.value)

    val userEmail: String? get() = authManager.currentUser.value?.email

    private val _skippedSignIn = MutableStateFlow(false)
    val skippedSignIn: StateFlow<Boolean> = _skippedSignIn

    fun onGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authManager.signInWithGoogle(idToken)
            if (result.isSuccess) {
                _authState.value = AuthState.SignedIn
                runPostSignInSync()
            } else {
                // Firebase rejected the token (commonly a stale/revoked
                // credential from Credential Manager auto-select). Clear the
                // cached credential so the next attempt shows the account
                // picker instead of silently reusing the bad one.
                authManager.clearCredentialState()
                _authState.value = AuthState.Error("Sign-in failed")
            }
        }
    }

    fun onSignInError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    /**
     * Debug-only: sign in against the Firebase Auth emulator as the default
     * test user so two-device sync can be exercised without a real Google
     * account. The UI gates this behind the same compile-time flags, but the
     * early return here guards against accidental calls from release code.
     */
    fun signInAsEmulatorTestUser() {
        if (!BuildConfig.DEBUG || !BuildConfig.USE_FIREBASE_EMULATOR) return
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                EmulatorAuthHelper.signInAsTestUser()
                _authState.value = AuthState.SignedIn
                runPostSignInSync()
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    "Emulator sign-in failed: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun onSignOut() {
        viewModelScope.launch {
            syncService.stopRealtimeListeners()
            sortPreferencesSyncService.stopAfterSignOut()
            themePreferencesSyncService.stopAfterSignOut()
            genericPreferenceSyncService.stopAfterSignOut()
            authManager.signOut()
            _authState.value = AuthState.SignedOut
        }
    }

    /**
     * Fix B — pull-before-upload post-sign-in sequence.
     *
     * The former flow launched initialUpload and startRealtimeListeners
     * back-to-back on the same scope. Firestore's initial snapshot for the
     * just-registered listener then raced the upload loop: the upload's
     * `tagDao.getAllTagsOnce()` / `taskDao.getAllTasksOnce()` picked up
     * rows the concurrent pull had just inserted, and re-uploaded them as
     * brand-new cloud docs. That produced the 403× tasks / 722× tags /
     * 15× task_completions Firestore corruption.
     *
     * New order:
     *   1. fullSync — pushes nothing on first sign-in (no pending actions)
     *      and pulls the canonical cloud state into Room. Holds isSyncing,
     *      so any in-flight listener pull (if one somehow arrives) defers.
     *   2. initialUpload — guarded by
     *      [com.averycorp.prismtask.data.preferences.BuiltInSyncPreferences.isInitialUploadDone]
     *      via Fix A. Repeat sign-ins become no-ops here. On first sign-in,
     *      uploads local-only rows to the cloud and sets the flag.
     *   3. startRealtimeListeners last — at this point, local and cloud
     *      are already in agreement, so the listener's initial snapshot
     *      produces at most a benign duplicate-detection pull.
     *
     * Each step swallows exceptions locally (they're already logged inside
     * the sync service). Continuing past a failure lets the user operate
     * locally-only while a retry happens on the next sign-in or app boot.
     */
    private suspend fun runPostSignInSync() {
        try {
            syncService.fullSync(trigger = "signIn")
        } catch (_: Exception) {
            // Error already logged by fullSync/markSyncCompleted.
        }
        try {
            syncService.initialUpload()
        } catch (_: Exception) {
            // Error already logged by initialUpload.
        }
        syncService.startRealtimeListeners()
        sortPreferencesSyncService.startAfterSignIn()
        themePreferencesSyncService.startAfterSignIn()
        genericPreferenceSyncService.startAfterSignIn()
    }

    fun onSkipSignIn() {
        _skippedSignIn.value = true
    }
}
