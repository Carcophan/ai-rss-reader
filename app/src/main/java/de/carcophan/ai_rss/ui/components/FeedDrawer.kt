package de.carcophan.ai_rss.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.carcophan.ai_rss.data.model.Feed

@Composable
fun FeedDrawer(
    feeds: List<Feed>,
    selectedFeed: Feed?,
    onSelectFeed: (Feed?) -> Unit,
    onDeleteFeed: (Feed) -> Unit,
    onAddFeedClick: () -> Unit
) {
    var feedToDelete by remember { mutableStateOf<Feed?>(null) }

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RssFeed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Meine RSS Feeds",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // "All Feeds" item
            NavigationDrawerItem(
                label = { Text("Alle Feeds") },
                icon = { Icon(Icons.Default.DynamicFeed, contentDescription = null) },
                selected = selectedFeed == null,
                onClick = { onSelectFeed(null) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ABONNIERTE QUELLEN (${feeds.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Feed List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(feeds, key = { it.id }) { feed ->
                    val isSelected = selectedFeed?.id == feed.id

                    NavigationDrawerItem(
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = feed.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                )
                                Text(
                                    text = feed.url,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        },
                        icon = {
                            Icon(
                                Icons.Default.RssFeed,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        },
                        badge = {
                            IconButton(
                                onClick = { feedToDelete = feed },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Feed löschen",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        selected = isSelected,
                        onClick = { onSelectFeed(feed) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Add Feed Button
            Button(
                onClick = onAddFeedClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Feed-URL hinzufügen")
            }
        }
    }

    // Delete confirmation dialog
    feedToDelete?.let { feed ->
        AlertDialog(
            onDismissRequest = { feedToDelete = null },
            title = { Text("Feed entfernen") },
            text = { Text("Möchtest du '${feed.title}' wirklich aus deinen Abonnements entfernen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFeed(feed)
                        feedToDelete = null
                    }
                ) {
                    Text("Entfernen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { feedToDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
