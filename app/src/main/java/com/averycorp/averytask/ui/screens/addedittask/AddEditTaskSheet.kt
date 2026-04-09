package com.averycorp.averytask.ui.screens.addedittask

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * Entry point for presenting the task editor as a modal bottom sheet from any
 * screen. Creates its own [AddEditTaskViewModel] scoped to the caller's
 * ViewModelStoreOwner (typically the containing NavBackStackEntry) and seeds
 * it with the supplied taskId / create-mode defaults.
 *
 * @param taskId existing task to edit, or null for create mode.
 * @param projectId pre-selected project for create mode (ignored in edit mode).
 * @param initialDate pre-set due date for create mode (ignored in edit mode).
 * @param onDismiss invoked after the sheet has finished closing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskSheetHost(
    taskId: Long?,
    projectId: Long?,
    initialDate: Long?,
    onDismiss: () -> Unit,
) {
    val viewModel: AddEditTaskViewModel = hiltViewModel(key = "addedit_task_sheet")

    LaunchedEffect(taskId, projectId, initialDate) {
        viewModel.initialize(taskId = taskId, projectId = projectId, initialDate = initialDate)
    }

    AddEditTaskSheet(
        viewModel = viewModel,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTaskSheet(
    viewModel: AddEditTaskViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDiscardConfirm by remember { mutableStateOf(false) }

    fun attemptDismiss() {
        if (viewModel.hasUnsavedChanges) {
            showDiscardConfirm = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { attemptDismiss() },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            // Sticky header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { attemptDismiss() }) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                }
                Text(
                    text = if (viewModel.isEditMode) "Edit Task" else "New Task",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            if (viewModel.saveTask()) onDismiss()
                        }
                    }
                ) {
                    Text(
                        text = "Save",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            HorizontalDivider()

            // Scrollable form body
            AddEditTaskFormFields(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                trailingContent = {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            )
        }
    }

    // Back button / gesture: route through attemptDismiss so unsaved changes
    // prompt for confirmation before closing.
    BackHandler { attemptDismiss() }

    // Discard confirmation dialog
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard Changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    }
                ) {
                    Text(
                        text = "Discard",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }
}
