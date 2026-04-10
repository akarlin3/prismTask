package com.averycorp.prismtask.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.averycorp.prismtask.data.remote.AuthManager
import com.averycorp.prismtask.data.remote.SyncService
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
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val syncService: SyncService
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
                // Initial upload on first sign-in
                try { syncService.initialUpload() } catch (_: Exception) { }
                syncService.startRealtimeListeners()
            } else {
                // Firebase rejected the token (commonly a stale/revoked
                // credential from Credential Manager auto-select). Clear the
                // cached credential so the next attempt shows the account
                // picker instead of silently reusing the bad one.
                authManager.clearCredentialState()
                _authState.value = AuthState.Error(
                    result.exceptionOrNull()?.message ?: "Sign-in failed"
                )
            }
        }
    }

    fun onSignInError(message: String) {
        _authState.value = AuthState.Error(message)
    }

    fun onSignOut() {
        viewModelScope.launch {
            syncService.stopRealtimeListeners()
            authManager.signOut()
            _authState.value = AuthState.SignedOut
        }
    }

    fun onSkipSignIn() {
        _skippedSignIn.value = true
    }
}
