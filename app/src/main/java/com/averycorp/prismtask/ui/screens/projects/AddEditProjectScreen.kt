package com.averycorp.prismtask.ui.screens.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val presetColors = listOf(
    "#E86F3C",
    "#D4534A",
    "#4A90D9",
    "#7B61C2",
    "#2E9E6E",
    "#E8B84A",
    "#5B8C5A",
    "#8B5CF6",
    "#EC4899",
    "#06B6D4",
    "#F59E0B",
    "#6B7280"
)

private val presetIcons = listOf(
    "\uD83D\uDCC1",
    "\uD83D\uDCBC",
    "\uD83C\uDFE0",
    "\uD83C\uDFAF",
    "\uD83D\uDCA1",
    "\uD83D\uDD27",
    "\uD83D\uDCDA",
    "\uD83C\uDFA8",
    "\uD83C\uDFCB\uFE0F",
    "\uD83D\uDED2",
    "\u2708\uFE0F",
    "\u2764\uFE0F"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditProjectScreen(
    navController: NavController,
    viewModel: AddEditProjectViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val prismColors = LocalPrismColors.current

    Scaffold(
        containerColor = prismColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isEditMode) "Edit Project" else "New Project",
                        fontWeight = FontWeight.Bold,
                        color = prismColors.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = prismColors.onSurface
                        )
                    }
                },
                actions = {
                    if (viewModel.isEditMode) {
                        IconButton(onClick = {
                            scope.launch {
                                viewModel.deleteProject()
                                navController.popBackStack()
                            }
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = prismColors.urgentAccent
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = prismColors.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Project Name") },
                isError = viewModel.nameError,
                supportingText = if (viewModel.nameError) {
                    { Text("Name is required") }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = viewModel.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description (Optional)") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel("Dates (Optional)")
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DateField(
                    label = "Start",
                    dateMillis = viewModel.startDate,
                    onChange = viewModel::onStartDateChange,
                    modifier = Modifier.weight(1f)
                )
                DateField(
                    label = "End",
                    dateMillis = viewModel.endDate,
                    onChange = viewModel::onEndDateChange,
                    modifier = Modifier.weight(1f)
                )
            }

            SectionLabel("Color")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                presetColors.forEach { hex ->
                    ColorCircle(
                        hex = hex,
                        selected = viewModel.color.equals(hex, ignoreCase = true),
                        onClick = { viewModel.onColorChange(hex) }
                    )
                }
            }

            SectionLabel("Icon")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetIcons.forEach { emoji ->
                    IconOption(
                        emoji = emoji,
                        selected = viewModel.icon == emoji,
                        onClick = { viewModel.onIconChange(emoji) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        if (viewModel.saveProject()) navController.popBackStack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (viewModel.isEditMode) "Update Project" else "Save Project",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val prismColors = LocalPrismColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = prismColors.muted,
        fontWeight = FontWeight.SemiBold
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    dateMillis: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val prismColors = LocalPrismColors.current
    var showPicker by remember { mutableStateOf(false) }
    val formatted = remember(dateMillis) {
        dateMillis?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
    }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = modifier,
        shape = MaterialTheme.shapes.small
    ) {
        Icon(
            Icons.Default.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = prismColors.primary
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = formatted ?: label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (formatted != null) prismColors.onSurface else prismColors.muted,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (dateMillis != null) {
            IconButton(
                onClick = { onChange(null) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear $label date",
                    modifier = Modifier.size(14.dp),
                    tint = prismColors.muted
                )
            }
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        com.averycorp.prismtask.ui.components
                            .datePickerToLocalMillis(state.selectedDateMillis)
                    )
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun ColorCircle(hex: String, selected: Boolean, onClick: () -> Unit) {
    val prismColors = LocalPrismColors.current
    val color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        prismColors.muted
    }

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, prismColors.onSurface, CircleShape)
                } else {
                    Modifier
                }
            ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = prismColors.onBackground,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun IconOption(emoji: String, selected: Boolean, onClick: () -> Unit) {
    val prismColors = LocalPrismColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) {
                    Modifier
                        .background(prismColors.tagSurface)
                        .border(2.dp, prismColors.primary, RoundedCornerShape(10.dp))
                } else {
                    Modifier.background(prismColors.surfaceVariant)
                }
            ).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp,
            textAlign = TextAlign.Center
        )
    }
}
