package de.carcophan.ai_rss.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.filled.WbSunny
import de.carcophan.ai_rss.data.model.RssItem
import de.carcophan.ai_rss.ui.components.AddFeedDialog
import de.carcophan.ai_rss.ui.components.ArticleCard
import de.carcophan.ai_rss.ui.components.ArticleDetailSheet
import de.carcophan.ai_rss.ui.components.DailyBriefingCard
import de.carcophan.ai_rss.ui.components.DailyBriefingSheet
import de.carcophan.ai_rss.ui.components.FeedDrawer
import de.carcophan.ai_rss.ui.components.GeminiSettingsDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssScreen(
    viewModel: RssViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showGeminiSettings by remember { mutableStateOf(false) }
    var showDailyBriefing by remember { mutableStateOf(false) }
    var selectedArticle by remember { mutableStateOf<RssItem?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Show error snackbar if error occurs
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    // Filter articles based on search query
    val filteredArticles = remember(uiState.articles, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.articles
        } else {
            val q = uiState.searchQuery.trim().lowercase()
            uiState.articles.filter {
                it.title.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.feedTitle.lowercase().contains(q)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            FeedDrawer(
                feeds = uiState.feeds,
                selectedFeed = uiState.selectedFeed,
                onSelectFeed = { feed ->
                    viewModel.selectFeed(feed)
                    scope.launch { drawerState.close() }
                },
                onDeleteFeed = { feed ->
                    viewModel.removeFeed(feed.id)
                },
                onAddFeedClick = {
                    scope.launch { drawerState.close() }
                    showAddDialog = true
                }
            )
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = uiState.selectedFeed?.title ?: "Alle Feeds",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${filteredArticles.size} Artikel",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menü")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) viewModel.setSearchQuery("")
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Suche"
                                )
                            }

                            IconButton(
                                onClick = { viewModel.refresh() },
                                enabled = !uiState.isLoading
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                            }

                            IconButton(
                                onClick = {
                                    showDailyBriefing = true
                                    viewModel.loadOrGenerateDailyBriefing(forceRefresh = false)
                                },
                                enabled = !uiState.isLoading && uiState.articles.isNotEmpty()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WbSunny,
                                    contentDescription = "Daily Briefing",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { showGeminiSettings = true }) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Gemini KI-Einstellungen",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Feed hinzufügen")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Search input field
                    AnimatedVisibility(visible = isSearchActive) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Artikel durchsuchen...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            trailingIcon = {
                                if (uiState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Close, contentDescription = "Löschen")
                                    }
                                }
                            }
                        )
                    }

                    // Progress bar when updating
                    if (uiState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Feed hinzufügen") }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (uiState.isLoading && uiState.articles.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Feeds werden abgerufen...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (filteredArticles.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty()) {
                                "Keine passenden Artikel für \"${uiState.searchQuery}\" gefunden."
                            } else if (uiState.feeds.isEmpty()) {
                                "Keine Feeds vorhanden.\nFüge deine gewünschten RSS-Feed-Adressen hinzu!"
                            } else {
                                "Keine Artikel für diesen Feed gefunden."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Feed-URL hinzufügen")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.searchQuery.isBlank() && filteredArticles.isNotEmpty()) {
                            item(key = "daily_briefing_card", contentType = "daily_briefing") {
                                DailyBriefingCard(
                                    feedTitle = uiState.selectedFeed?.title ?: "Alle Feeds",
                                    onClick = {
                                        showDailyBriefing = true
                                        viewModel.loadOrGenerateDailyBriefing(forceRefresh = false)
                                    }
                                )
                            }
                        }

                        items(
                            items = filteredArticles,
                            key = { it.id },
                            contentType = { if (!it.imageUrl.isNullOrBlank()) "article_with_image" else "article_text_only" }
                        ) { article ->
                            ArticleCard(
                                article = article,
                                onClick = { selectedArticle = article }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Feed Dialog
    if (showAddDialog) {
        AddFeedDialog(
            isLoading = uiState.isAddingFeed,
            onDismiss = { showAddDialog = false },
            onAddFeed = { url, title ->
                viewModel.addFeed(url, title) {
                    showAddDialog = false
                }
            }
        )
    }

    // Gemini Settings Dialog
    if (showGeminiSettings) {
        GeminiSettingsDialog(
            geminiRepository = viewModel.geminiRepository,
            onDismiss = { showGeminiSettings = false }
        )
    }

    // Article Detail Sheet
    selectedArticle?.let { article ->
        ArticleDetailSheet(
            articles = filteredArticles,
            initialArticle = article,
            summaryStates = uiState.summaryStates,
            getSummaryState = { articleId -> viewModel.getSummaryState(articleId) },
            geminiModelName = viewModel.geminiRepository.getModel(),
            onSummarize = { targetArticle, forceRefresh, currentFullText ->
                viewModel.summarizeArticle(
                    article = targetArticle,
                    fullTextOverride = currentFullText,
                    forceRefresh = forceRefresh
                )
            },
            onOpenGeminiSettings = { showGeminiSettings = true },
            onDismiss = { selectedArticle = null }
        )
    }

    // Daily Briefing Sheet
    if (showDailyBriefing) {
        DailyBriefingSheet(
            feedTitle = uiState.selectedFeed?.title ?: "Alle Feeds",
            briefingState = uiState.dailyBriefingState,
            geminiModelName = viewModel.geminiRepository.getModel(),
            onRefresh = {
                viewModel.loadOrGenerateDailyBriefing(forceRefresh = true)
            },
            onOpenGeminiSettings = { showGeminiSettings = true },
            onDismiss = { showDailyBriefing = false }
        )
    }
}
