package de.carcophan.ai_rss.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.carcophan.ai_rss.data.model.Keyword
import de.carcophan.ai_rss.data.repository.KeywordRepository

@Composable
fun ManageKeywordsDialog(
    keywords: List<Keyword>,
    activeKeyword: Keyword?,
    onAddKeyword: (String) -> Unit,
    onDeleteKeyword: (String) -> Unit,
    onSelectKeyword: (Keyword) -> Unit,
    onDismiss: () -> Unit
) {
    var newKeywordText by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    val unusedSuggestions = remember(keywords) {
        val existingSet = keywords.map { it.text.lowercase().trim() }.toSet()
        KeywordRepository.DEFAULT_KEYWORDS.filter { it.lowercase().trim() !in existingSet }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sell,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Schlagwörter verwalten",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Gib Schlagwörter ein, nach denen Gemini Meldungen aus allen Feeds thematisch filtert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeywordText,
                        onValueChange = {
                            newKeywordText = it
                            inputError = null
                        },
                        label = { Text("Neues Schlagwort") },
                        placeholder = { Text("z. B. Raumfahrt") },
                        singleLine = true,
                        isError = inputError != null,
                        supportingText = inputError?.let { { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val clean = newKeywordText.trim()
                            if (clean.isBlank()) {
                                inputError = "Bitte Text eingeben"
                            } else if (keywords.any { it.text.equals(clean, ignoreCase = true) }) {
                                inputError = "Bereits vorhanden"
                            } else {
                                onAddKeyword(clean)
                                newKeywordText = ""
                                inputError = null
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                    }
                }

                // Quick suggestions
                if (unusedSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Vorschläge zum Hinzufügen:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        unusedSuggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { onAddKeyword(suggestion) },
                                label = { Text(suggestion) },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Gespeicherte Schlagwörter (${keywords.size}):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (keywords.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Noch keine Schlagwörter gespeichert.\nFüge oben dein erstes Schlagwort hinzu!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(keywords, key = { it.id }) { keyword ->
                            val isActive = activeKeyword?.id == keyword.id
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isActive) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Aktiv",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = keyword.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isActive) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = {
                                                onSelectKeyword(keyword)
                                                onDismiss()
                                            }
                                        ) {
                                            Text(if (isActive) "Aktiv" else "Verwenden")
                                        }

                                        IconButton(
                                            onClick = { onDeleteKeyword(keyword.id) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Löschen",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Fertig")
            }
        }
    )
}
