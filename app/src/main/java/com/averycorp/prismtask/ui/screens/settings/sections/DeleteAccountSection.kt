package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.settings.SectionHeader

/**
 * "Danger zone" section for the Account & Sync screen — exposes the
 * irreversible "Delete Account" action.
 *
 * Only renders when the user is signed in. The action takes them through
 * a two-step typed-confirmation dialog matching the existing
 * [com.averycorp.prismtask.ui.components.dialogs.ResetAppDataDialog]
 * idiom (with ``DELETE`` instead of ``RESET``). On confirm, the deletion
 * runs through [com.averycorp.prismtask.data.remote.AccountDeletionService]:
 * marks Firestore deletion-pending, signs out, wipes all local state.
 * The 30-day grace window means the user can still restore by signing
 * back in any time before the scheduled date.
 */
@Composable
fun DeleteAccountSection(
    isSignedIn: Boolean,
    isDeleting: Boolean,
    onRequestDeletion: () -> Unit
) {
    if (!isSignedIn) return

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        DeleteAccountDialog(
            isDeleting = isDeleting,
            onConfirm = {
                showDialog = false
                onRequestDeletion()
            },
            onDismiss = { showDialog = false }
        )
    }

    SectionHeader("Danger Zone")

    Text(
        text = "Deletes your account and all synced data. You can restore for 30 days by signing in again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { showDialog = true },
        enabled = !isDeleting,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        if (isDeleting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Deleting…", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Delete Account…", color = MaterialTheme.colorScheme.error)
        }
    }

    HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
}

/**
 * Two-step typed-confirmation dialog. First step explains what gets
 * deleted; second step requires the user to type DELETE before the
 * destructive button enables. Mirrors [ResetAppDataDialog]'s shape
 * (.trim()-tolerant, error-styled confirm button) so the visual
 * grammar of destructive Settings actions stays consistent.
 */
@Composable
private fun DeleteAccountDialog(
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(DeleteDialogStep.EXPLAIN) }
    var confirmText by remember { mutableStateOf("") }

    when (step) {
        DeleteDialogStep.EXPLAIN -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Account?")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "This will:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        BulletLine("Sign you out of this device.")
                        BulletLine(
                            "Schedule your account for permanent deletion in 30 days. " +
                                "All synced data is preserved during the grace window."
                        )
                        BulletLine(
                            "Wipe all local data on this device — tasks, habits, projects, " +
                                "and preferences."
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Sign in again within 30 days to restore your account. " +
                                "After 30 days the deletion is permanent and cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmText = ""
                            step = DeleteDialogStep.CONFIRM
                        }
                    ) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            )
        }
        DeleteDialogStep.CONFIRM -> {
            val confirmEnabled = confirmText.trim() == "DELETE" && !isDeleting

            AlertDialog(
                onDismissRequest = { if (!isDeleting) onDismiss() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Are You Sure?")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Type DELETE to confirm:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { confirmText = it },
                            singleLine = true,
                            placeholder = { Text("DELETE") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isDeleting
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onConfirm,
                        enabled = confirmEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (isDeleting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onError
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Deleting…")
                            }
                        } else {
                            Text("Delete Account")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { step = DeleteDialogStep.EXPLAIN },
                        enabled = !isDeleting
                    ) { Text("Back") }
                }
            )
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Text("• ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private enum class DeleteDialogStep { EXPLAIN, CONFIRM }
