package com.averycorp.prismtask.ui.screens.templates

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.averycorp.prismtask.data.local.entity.TaskTemplateEntity
import com.averycorp.prismtask.ui.components.RichEmptyState
import com.averycorp.prismtask.ui.navigation.PrismTaskRoute
import com.averycorp.prismtask.ui.screens.addedittask.AddEditTaskSheetHost
import com.averycorp.prismtask.ui.theme.LocalPrismColors
import com.averycorp.prismtask.ui.theme.LocalPrismShapes
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(
    navController: NavController,
    viewModel: TemplateListViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val quickUseBanner by viewModel.quickUseBanner.collectAsStateWithLifecycle()

    var showSearch by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<TaskTemplateEntity?>(null) }
    var editorSheetTaskId by remember { mutableStateOf<Long?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showManageCategoriesDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss the quick-use banner after a short window so it behaves
    // like a Snackbar despite being a bespoke composable. The delay is
    // reset whenever a new quick-use lands.
    LaunchedEffect(quickUseBanner?.newTaskId) {
        if (quickUseBanner != null) {
            delay(5_000)
            viewModel.dismissQuickUseBanner()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Templates", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            if (!showSearch) viewModel.setSearchQuery("")
                        }) {
                            Icon(
                                if (showSearch) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (showSearch) "Close Search" else "Search"
                            )
                        }
                        Box {
                            IconButton(onClick = { overflowMenuExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More Options"
                                )
                            }
                            DropdownMenu(
                                expanded = overflowMenuExpanded,
                                onDismissRequest = { overflowMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Manage Categories") },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        showManageCategoriesDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Built-in Updates") },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        navController.navigate(
                                            PrismTaskRoute.BuiltInUpdates.route
                                        )
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text("Search Templates\u2026") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        } else {
                            null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (categories.isNotEmpty()) {
                    CategoryFilterRow(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelect = viewModel::setCategory
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    navController.navigate(PrismTaskRoute.AddEditTemplate.createRoute())
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "New Template",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (templates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    TemplatesEmptyState(
                        hasAnyFilter = selectedCategory != null || searchQuery.isNotEmpty(),
                        onCreate = {
                            navController.navigate(PrismTaskRoute.AddEditTemplate.createRoute())
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }
                    items(templates, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onQuickUse = { viewModel.quickUseTemplate(template.id) },
                            onEdit = {
                                navController.navigate(
                                    PrismTaskRoute.AddEditTemplate.createRoute(template.id)
                                )
                            },
                            onDelete = { templateToDelete = template }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            // Quick-use banner — overlays the FAB at the bottom of the
            // screen with View and Undo actions. Dismissed automatically
            // after 5s via a LaunchedEffect above.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = quickUseBanner != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    quickUseBanner?.let { banner ->
                        QuickUseSnackbar(
                            taskTitle = banner.taskTitle,
                            onView = {
                                editorSheetTaskId = banner.newTaskId
                                showEditorSheet = true
                                viewModel.dismissQuickUseBanner()
                            },
                            onUndo = { viewModel.undoQuickUse(banner.newTaskId) },
                            onDismiss = { viewModel.dismissQuickUseBanner() }
                        )
                    }
                }
            }
        }
    }

    // Editor sheet — shown when user taps "View" on the quick-use banner.
    if (showEditorSheet) {
        AddEditTaskSheetHost(
            taskId = editorSheetTaskId,
            projectId = null,
            initialDate = null,
            onDismiss = { showEditorSheet = false }
        )
    }

    templateToDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templateToDelete = null },
            title = { Text("Delete Template") },
            text = { Text("Delete \"${template.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(template.id)
                    templateToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { templateToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showManageCategoriesDialog) {
        ManageCategoriesDialog(
            categories = categories,
            onDelete = { categoryToDelete = it },
            onDismiss = { showManageCategoriesDialog = false }
        )
    }

    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category") },
            text = {
                Text(
                    "Remove the \"$category\" category? Templates in this " +
                        "category will stay, but they'll no longer be grouped."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(category)
                    categoryToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Simple list-of-categories dialog invoked from the "Manage Categories"
 * overflow menu item. Each row has a trash icon that asks for a confirmation
 * via [onDelete]; the confirmation itself is an [AlertDialog] hoisted into the
 * parent so the two dialogs can be stacked.
 */
@Composable
private fun ManageCategoriesDialog(
    categories: List<String>,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Categories") },
        text = {
            if (categories.isEmpty()) {
                Text(
                    "No Categories Yet. Add a category when creating or editing a template.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onDelete(category) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete $category",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        items(categories, key = { it }) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onSelect(category) },
                label = { Text(category) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: TaskTemplateEntity,
    onQuickUse: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val categoryColor = colorForCategory(template.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onQuickUse),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.icon ?: "\uD83D\uDCCB",
                    fontSize = 24.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name + preview + usage analytics
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = template.templateTitle?.takeIf { it.isNotBlank() }
                if (preview != null) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                template.category?.let { category ->
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Usage analytics footer — different copy depending on whether
                // the template has ever been used.
                TemplateUsageLine(template = template)
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Usage badge — "Used Nx" at a glance.
            Box(
                modifier = Modifier
                    .clip(LocalPrismShapes.current.chip)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Used ${template.usageCount}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            // Overflow menu
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplatesEmptyState(
    hasAnyFilter: Boolean,
    onCreate: () -> Unit
) {
    if (hasAnyFilter) {
        RichEmptyState(
            icon = "\uD83D\uDD0D",
            title = "No Matching Templates",
            description = "Try a different category or search term.",
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        )
    } else {
        RichEmptyState(
            icon = "\uD83D\uDCCB",
            title = "No Templates",
            description = "Save reusable task blueprints for things you do often.",
            actionLabel = "Create Template",
            onAction = onCreate,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        )
    }
}

/**
 * Bespoke Material3-styled snackbar that hosts both the "View" and "Undo"
 * actions for the quick-use flow. Material's default Snackbar only supports
 * a single action slot, so we render our own surface with a Row of
 * TextButtons instead.
 */
@Composable
private fun QuickUseSnackbar(
    taskTitle: String,
    onView: () -> Unit,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Created '$taskTitle' from template",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onView) {
                Text(
                    text = "VIEW",
                    color = MaterialTheme.colorScheme.inversePrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = {
                onUndo()
                onDismiss()
            }) {
                Text(
                    text = "UNDO",
                    color = MaterialTheme.colorScheme.inversePrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Tiny footer shown under the template name + preview that summarizes usage:
 *
 *  - 0 uses ever → "Not Used Yet" in muted text.
 *  - Used, last used within 30 days → "Used N Times · Last Used: <date>".
 *  - Used, but untouched for 30+ days → adds a subtle "Haven't Used This In a
 *    While" tail so cold templates are visually distinct without being shouty.
 */
@Composable
private fun TemplateUsageLine(template: TaskTemplateEntity) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    if (template.usageCount == 0) {
        Text(
            text = "Not Used Yet",
            style = MaterialTheme.typography.labelSmall,
            color = muted
        )
        return
    }
    val lastUsedAt = template.lastUsedAt
    val text = buildString {
        append("Used ")
        append(template.usageCount)
        append(" time")
        if (template.usageCount != 1) append("s")
        if (lastUsedAt != null) {
            append(" · Last Used: ")
            append(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(lastUsedAt)))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    if (isStaleTemplate(template, System.currentTimeMillis())) {
        Text(
            text = "Haven't Used This In a While",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * Returns true when a template has been used at least once but not in the
 * last 30 days. Exposed as a top-level function (rather than inlined) so it
 * can be unit-tested with a deterministic `now`.
 */
internal fun isStaleTemplate(template: TaskTemplateEntity, now: Long): Boolean {
    if (template.usageCount == 0) return false
    val lastUsed = template.lastUsedAt ?: return false
    val thirtyDaysMs = TimeUnit.DAYS.toMillis(30)
    return (now - lastUsed) >= thirtyDaysMs
}

/**
 * Maps a category string to a stable accent color. Used to tint the icon
 * background and category label on template cards so categories are
 * visually distinguishable at a glance.
 */
@Composable
private fun colorForCategory(category: String?): Color {
    val palette = LocalPrismColors.current.dataVisualizationPalette
    if (category.isNullOrBlank()) return palette[0]
    val index = (category.hashCode() and Int.MAX_VALUE) % palette.size
    return palette[index]
}
