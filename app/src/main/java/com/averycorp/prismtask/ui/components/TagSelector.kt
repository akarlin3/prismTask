package com.averycorp.prismtask.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.averycorp.prismtask.data.local.entity.TagEntity

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagSelector(
    availableTags: List<TagEntity>,
    selectedTagIds: Set<Long>,
    onSelectionChanged: (Set<Long>) -> Unit
) {
    if (availableTags.isEmpty()) {
        Text(
            text = "No tags available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        availableTags.forEach { tag ->
            val isSelected = tag.id in selectedTagIds
            val tagColor = try {
                Color(android.graphics.Color.parseColor(tag.color))
            } catch (_: Exception) {
                Color.Gray
            }

            FilterChip(
                selected = isSelected,
                onClick = {
                    val newSet = if (isSelected) {
                        selectedTagIds - tag.id
                    } else {
                        selectedTagIds + tag.id
                    }
                    onSelectionChanged(newSet)
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tagColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tag.name)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tagColor.copy(alpha = 0.15f),
                    selectedLabelColor = tagColor
                )
            )
        }
    }
}
