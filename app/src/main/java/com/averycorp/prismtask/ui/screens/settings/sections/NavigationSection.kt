package com.averycorp.prismtask.ui.screens.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.ui.components.CircularCheckbox
import com.averycorp.prismtask.ui.components.settings.ReorderableRow
import com.averycorp.prismtask.ui.components.settings.SectionHeader
import com.averycorp.prismtask.ui.navigation.ALL_BOTTOM_NAV_ITEMS

@Composable
fun NavigationSection(
    tabOrder: List<String>,
    hiddenTabs: Set<String>,
    onHiddenTabsChange: (Set<String>) -> Unit,
    onTabOrderChange: (List<String>) -> Unit,
    onResetTabDefaults: () -> Unit
) {
    SectionHeader("Navigation")

    Text(
        text = "Visible Tabs",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    val visibleTabCount = ALL_BOTTOM_NAV_ITEMS.count { it.route !in hiddenTabs }
    ALL_BOTTOM_NAV_ITEMS.forEach { item ->
        val isHidden = item.route in hiddenTabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (!isHidden && visibleTabCount <= 2) return@clickable
                    val newHidden = if (isHidden) hiddenTabs - item.route else hiddenTabs + item.route
                    onHiddenTabsChange(newHidden)
                }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularCheckbox(
                checked = !isHidden,
                onCheckedChange = {
                    if (!isHidden && visibleTabCount <= 2) return@CircularCheckbox
                    val newHidden = if (isHidden) hiddenTabs - item.route else hiddenTabs + item.route
                    onHiddenTabsChange(newHidden)
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(item.label, style = MaterialTheme.typography.bodyLarge)
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Tab Order",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    tabOrder.forEachIndexed { index, route ->
        val item = ALL_BOTTOM_NAV_ITEMS.find { it.route == route }
        ReorderableRow(
            label = item?.label ?: route,
            canMoveUp = index > 0,
            canMoveDown = index < tabOrder.size - 1,
            onMoveUp = {
                val mutable = tabOrder.toMutableList()
                mutable[index] = mutable[index - 1].also { mutable[index - 1] = mutable[index] }
                onTabOrderChange(mutable)
            },
            onMoveDown = {
                val mutable = tabOrder.toMutableList()
                mutable[index] = mutable[index + 1].also { mutable[index + 1] = mutable[index] }
                onTabOrderChange(mutable)
            }
        )
    }
    TextButton(onClick = onResetTabDefaults) {
        Text("Reset Navigation", color = MaterialTheme.colorScheme.error)
    }

    HorizontalDivider()
}
