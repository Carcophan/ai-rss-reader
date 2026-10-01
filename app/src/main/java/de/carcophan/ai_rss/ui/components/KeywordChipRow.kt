package de.carcophan.ai_rss.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.carcophan.ai_rss.data.model.Keyword

@Composable
fun KeywordChipRow(
    keywords: List<Keyword>,
    activeKeyword: Keyword?,
    onSelectKeyword: (Keyword) -> Unit,
    onClearFilter: () -> Unit,
    onDeleteKeyword: (Keyword) -> Unit,
    onAddKeywordClick: () -> Unit,
    onManageKeywordsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Alle" Chip to reset filter
        FilterChip(
            selected = activeKeyword == null,
            onClick = onClearFilter,
            label = { Text("Alle") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.DynamicFeed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )

        // Saved Keyword Chips
        keywords.forEach { keyword ->
            val isSelected = activeKeyword?.id == keyword.id
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (isSelected) {
                        onClearFilter()
                    } else {
                        onSelectKeyword(keyword)
                    }
                },
                label = { Text(keyword.text) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null,
                trailingIcon = {
                    IconButton(
                        onClick = { onDeleteKeyword(keyword) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schlagwort löschen",
                            modifier = Modifier.size(14.dp),
                            tint = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            }
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        // Add Keyword Chip
        AssistChip(
            onClick = onAddKeywordClick,
            label = { Text("Schlagwort") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Schlagwort hinzufügen",
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )

        // Manage Keywords Button
        IconButton(
            onClick = onManageKeywordsClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Schlagwörter verwalten",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
