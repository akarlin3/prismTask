package com.averycorp.prismtask.ui.screens.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.averycorp.prismtask.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

private val WEB_CLIENT_ID = BuildConfig.WEB_CLIENT_ID

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onContinue: () -> Unit
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Navigate away once sign-in succeeds
    if (authState is AuthState.SignedIn) {
        LaunchedEffect(Unit) {
            onContinue()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PrismTask",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sync your tasks across devices",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        when (authState) {
            is AuthState.Loading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Signing in...", style = MaterialTheme.typography.bodyMedium)
            }
            is AuthState.Error -> {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            else -> {}
        }

        if (authState !is AuthState.Loading) {
            Button(
                onClick = {
                    scope.launch {
                        val credentialManager = CredentialManager.create(context)
                        val activity = context as Activity

                        suspend fun requestAuthorized(): GetCredentialResponse {
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setServerClientId(WEB_CLIENT_ID)
                                .setFilterByAuthorizedAccounts(true)
                                .setAutoSelectEnabled(true)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            return credentialManager.getCredential(activity, request)
                        }

                        suspend fun requestSignInButton(): GetCredentialResponse {
                            val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(signInOption)
                                .build()
                            return credentialManager.getCredential(activity, request)
                        }

                        try {
                            // First try returning users (authorized accounts).
                            // Only fall back on NoCredentialException — other
                            // failures (user cancelled, network, etc.) should
                            // surface as-is rather than silently retrying.
                            val result = try {
                                requestAuthorized()
                            } catch (_: NoCredentialException) {
                                requestSignInButton()
                            }

                            val credential = result.credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                try {
                                    val googleIdTokenCredential =
                                        GoogleIdTokenCredential.createFrom(credential.data)
                                    viewModel.onGoogleSignIn(googleIdTokenCredential.idToken)
                                } catch (e: GoogleIdTokenParsingException) {
                                    Log.e("AuthScreen", "Failed to parse Google ID token", e)
                                    // Clear cached credential — a malformed
                                    // token usually means the cached account
                                    // needs reauth.
                                    runCatching {
                                        credentialManager.clearCredentialState(
                                            androidx.credentials.ClearCredentialStateRequest()
                                        )
                                    }
                                    viewModel.onSignInError(
                                        "Google account needs to be re-authenticated. Please try again."
                                    )
                                }
                            } else {
                                Log.e("AuthScreen", "Unexpected credential type: ${credential.type}")
                                viewModel.onSignInError("Unexpected credential type")
                            }
                        } catch (_: GetCredentialCancellationException) {
                            // User dismissed the sheet — return to idle.
                            viewModel.onSignInError("Sign-in cancelled")
                        } catch (e: GetCredentialException) {
                            Log.e("AuthScreen", "Sign-in failed", e)
                            viewModel.onSignInError(e.message ?: "Google Sign-In failed")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In with Google")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = {
                viewModel.onSkipSignIn()
                onContinue()
            }) {
                Text("Continue Without Account")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Your data stays on this device until you sign in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
