package com.averycorp.prismtask.ui.screens.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.screens.templates.components.TaskBlueprintSection
import com.averycorp.prismtask.ui.screens.templates.components.TemplateInfoSection
import kotlinx.coroutines.launch

/**
 * Emoji set used for the template icon picker. Curated to cover the common
 * recurring-task categories people reach for first (personal, work, home,
 * errands, study, fitness) rather than an exhaustive emoji grid.
 */
private val TEMPLATE_ICONS = listOf(
    "\uD83D\uDCCB", // 📋
    "\uD83D\uDCDD", // 📝
    "\uD83D\uDCCC", // 📌
    "\uD83C\uDFE0", // 🏠
    "\uD83D\uDCBC", // 💼
    "\uD83D\uDED2", // 🛒
    "\uD83D\uDCDA", // 📚
    "\uD83C\uDFC3", // 🏃
    "\uD83E\uDDF9", // 🧹
    "\uD83D\uDC68\u200D\uD83D\uDCBB", // 👨‍💻
    "\uD83D\uDCE7", // 📧
    "\uD83D\uDCDE", // 📞
    "\u2708\uFE0F", // ✈️
    "\uD83C\uDFAF", // 🎯
    "\uD83D\uDCA1", // 💡
    "\u2B50" // ⭐
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTemplateScreen(
    navController: NavController,
    viewModel: AddEditTemplateViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dismiss: () -> Unit = {
        scope
            .launch {
                sheetState.hide()
            }.invokeOnCompletion {
                if (!sheetState.isVisible) {
                    navController.popBackStack()
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = { navController.popBackStack() },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.92f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sticky header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = dismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                    Text(
                        text = if (viewModel.isEdit) "Edit Template" else "New Template",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    if (viewModel.isEdit) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (viewModel.saveTemplate()) {
                                    dismiss()
                                }
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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TemplateInfoSection(viewModel)
                    HorizontalDivider()
                    TaskBlueprintSection(viewModel)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    BackHandler { dismiss() }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Template") },
            text = { Text("Delete \"${viewModel.name.ifBlank { "this template" }}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            viewModel.deleteTemplate()
                            dismiss()
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
