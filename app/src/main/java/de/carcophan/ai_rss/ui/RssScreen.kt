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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import de.carcophan.ai_rss.ui.components.KeywordChipRow
import de.carcophan.ai_rss.ui.components.ManageKeywordsDialog
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
    var showManageKeywordsDialog by remember { mutableStateOf(false) }
    var selectedArticle by remember { mutableStateOf<RssItem?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Show error snackbar if error occurs
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
            viewModel.clearError()
        }
    }

    // Filter articles based on active keyword and search query
    val filteredArticles = remember(uiState.articles, uiState.searchQuery, uiState.keywordFilterState) {
        var list = uiState.articles
        // 1. Keyword filter (Gemini)
        if (uiState.keywordFilterState.activeKeyword != null && !uiState.keywordFilterState.isLoading) {
            val matchedIds = uiState.keywordFilterState.matchedArticleIds
            list = list.filter { it.id in matchedIds }
        }
        // 2. Search query filter
        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) ||
                        it.description.lowercase().contains(q) ||
                        it.feedTitle.lowercase().contains(q)
            }
        }
        list
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

                    // Keyword Chip Row for filtering
                    KeywordChipRow(
                        keywords = uiState.keywords,
                        activeKeyword = uiState.keywordFilterState.activeKeyword,
                        onSelectKeyword = { keyword ->
                            viewModel.selectKeyword(keyword)
                        },
                        onClearFilter = {
                            viewModel.clearActiveKeyword()
                        },
                        onDeleteKeyword = { keyword ->
                            viewModel.deleteKeyword(keyword.id)
                        },
                        onAddKeywordClick = {
                            showManageKeywordsDialog = true
                        },
                        onManageKeywordsClick = {
                            showManageKeywordsDialog = true
                        }
                    )

                    // Keyword AI Analysis Progress Bar
                    if (uiState.keywordFilterState.isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        uiState.keywordFilterState.statusMessage?.let { status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
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
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = { viewModel.refresh() },
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
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val kwState = uiState.keywordFilterState
                        when {
                            kwState.errorMessage == "MISSING_API_KEY" -> {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Gemini API-Key erforderlich",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Für die intelligente KI-Kategorisierung und Filterung nach \"${kwState.activeKeyword?.text}\" wird ein Gemini API-Key benötigt.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = { showGeminiSettings = true }) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("API-Key einrichten")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { kwState.activeKeyword?.let { viewModel.applyFallbackTextFilter(it) } }) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Einfache Stichwortsuche nutzen")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.clearActiveKeyword() }) {
                                    Text("Filter aufheben")
                                }
                            }
                            kwState.errorMessage != null -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Fehler bei der KI-Analyse",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = kwState.errorMessage ?: "Unbekannter Fehler",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = { viewModel.refreshActiveKeywordFilter() }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Erneut versuchen")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(onClick = { kwState.activeKeyword?.let { viewModel.applyFallbackTextFilter(it) } }) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Einfache Stichwortsuche nutzen")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { viewModel.clearActiveKeyword() }) {
                                    Text("Filter aufheben")
                                }
                            }
                            kwState.activeKeyword != null -> {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Keine Treffer für \"${kwState.activeKeyword?.text}\"",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "In den aktuellen Meldungen aller Feeds wurden keine passenden Artikel zu diesem Thema gefunden.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                OutlinedButton(onClick = { kwState.activeKeyword?.let { viewModel.applyFallbackTextFilter(it) } }) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Mit Volltextsuche versuchen")
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(onClick = { viewModel.clearActiveKeyword() }) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Text("Filter aufheben")
                                }
                            }
                            else -> {
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
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Keyword Filter Banner
                        if (uiState.keywordFilterState.activeKeyword != null && !uiState.keywordFilterState.isLoading) {
                            item(key = "keyword_filter_banner", contentType = "filter_banner") {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "KI-Filter: \"${uiState.keywordFilterState.activeKeyword?.text}\"",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "${filteredArticles.size} passende Meldungen aus allen Feeds",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.refreshActiveKeywordFilter() },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Erneut analysieren",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.clearActiveKeyword() },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Filter entfernen",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.searchQuery.isBlank() && uiState.keywordFilterState.activeKeyword == null && filteredArticles.isNotEmpty()) {
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
                                onClick = { selectedArticle = article },
                                geminiMatchReason = uiState.keywordFilterState.reasonsByArticleId[article.id]
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

    // Manage Keywords Dialog
    if (showManageKeywordsDialog) {
        ManageKeywordsDialog(
            keywords = uiState.keywords,
            activeKeyword = uiState.keywordFilterState.activeKeyword,
            onAddKeyword = { text ->
                viewModel.addKeyword(text)
            },
            onDeleteKeyword = { id ->
                viewModel.deleteKeyword(id)
            },
            onSelectKeyword = { keyword ->
                viewModel.selectKeyword(keyword)
            },
            onDismiss = { showManageKeywordsDialog = false }
        )
    }
}
