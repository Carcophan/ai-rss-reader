package de.carcophan.ai_rss.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.carcophan.ai_rss.data.model.RssItem
import de.carcophan.ai_rss.data.model.SummaryLevel
import de.carcophan.ai_rss.data.repository.ArticleWebExtractor
import de.carcophan.ai_rss.ui.ArticleSummaryState
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailSheet(
    articles: List<RssItem>,
    initialArticle: RssItem,
    summaryStates: Map<String, ArticleSummaryState>,
    getSummaryState: (articleId: String, level: SummaryLevel) -> ArticleSummaryState,
    defaultSummaryLevel: SummaryLevel = SummaryLevel.DEFAULT,
    geminiModelName: String,
    onSummarize: (article: RssItem, forceRefresh: Boolean, currentFullText: String, level: SummaryLevel) -> Unit,
    onOpenGeminiSettings: () -> Unit,
    onDismiss: () -> Unit,
    onArticleChanged: ((RssItem) -> Unit)? = null
) {
    if (articles.isEmpty()) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val initialIndex = remember(initialArticle.id) {
        val idx = articles.indexOfFirst { it.id == initialArticle.id }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { articles.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage in articles.indices) {
            onArticleChanged?.invoke(articles[pagerState.currentPage])
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header: Navigation controls (arrows & counter) + Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (articles.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (pagerState.currentPage > 0) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            pagerState.currentPage - 1,
                                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            },
                            enabled = pagerState.currentPage > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Vorheriger Artikel",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "${pagerState.currentPage + 1} / ${articles.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                if (pagerState.currentPage < articles.size - 1) {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            pagerState.currentPage + 1,
                                            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            },
                            enabled = pagerState.currentPage < articles.size - 1,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Nächster Artikel",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Schließen")
                }
            }

            // Swipeable article pager with animated transitions
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1,
                key = { page -> articles.getOrNull(page)?.id ?: page }
            ) { page ->
                val article = articles[page]
                val summaryState = summaryStates[article.id] ?: getSummaryState(article.id, defaultSummaryLevel)

                // Page offset relative to current scroll position for smooth animated transitions
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                ArticlePageContent(
                    article = article,
                    summaryState = summaryState,
                    defaultSummaryLevel = defaultSummaryLevel,
                    geminiModelName = geminiModelName,
                    onSummarize = { forceRefresh, currentFullText, level ->
                        onSummarize(article, forceRefresh, currentFullText, level)
                    },
                    onOpenGeminiSettings = onOpenGeminiSettings,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val clampedOffset = pageOffset.coerceIn(0f, 1f)
                            val scale = 1f - (clampedOffset * 0.06f)
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (clampedOffset * 0.5f)
                        }
                )
            }
        }
    }
}

// Single-article convenience overload for backwards compatibility
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailSheet(
    article: RssItem,
    summaryState: ArticleSummaryState,
    geminiModelName: String,
    onSummarize: (forceRefresh: Boolean, currentFullText: String) -> Unit,
    onOpenGeminiSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ArticleDetailSheet(
        articles = listOf(article),
        initialArticle = article,
        summaryStates = mapOf(article.id to summaryState),
        getSummaryState = { _, _ -> summaryState },
        defaultSummaryLevel = summaryState.level,
        geminiModelName = geminiModelName,
        onSummarize = { _, forceRefresh, currentFullText, _ ->
            onSummarize(forceRefresh, currentFullText)
        },
        onOpenGeminiSettings = onOpenGeminiSettings,
        onDismiss = onDismiss
    )
}

@Composable
private fun ArticlePageContent(
    article: RssItem,
    summaryState: ArticleSummaryState,
    defaultSummaryLevel: SummaryLevel,
    geminiModelName: String,
    onSummarize: (forceRefresh: Boolean, currentFullText: String, level: SummaryLevel) -> Unit,
    onOpenGeminiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fullText by remember(article.id) {
        mutableStateOf(article.content.ifBlank { article.description })
    }
    var imageUrl by remember(article.id) {
        mutableStateOf(article.imageUrl)
    }
    var isLoadingWebText by remember(article.id) { mutableStateOf(false) }
    var webTextLoaded by remember(article.id) { mutableStateOf(false) }
    var isSummaryDismissed by remember(article.id) { mutableStateOf(false) }
    var selectedLevel by remember(article.id) {
        mutableStateOf(summaryState.level)
    }

    LaunchedEffect(summaryState.level) {
        selectedLevel = summaryState.level
    }

    fun loadWebArticle() {
        if (article.link.isBlank() || isLoadingWebText) return
        isLoadingWebText = true
        scope.launch {
            val result = ArticleWebExtractor.extractFullArticle(article.link)
            if (result != null && result.fullText.isNotBlank()) {
                fullText = result.fullText
                if (imageUrl.isNullOrBlank() && !result.imageUrl.isNullOrBlank()) {
                    imageUrl = result.imageUrl
                }
                webTextLoaded = true
            }
            isLoadingWebText = false
        }
    }

    // Auto-fetch full text if the RSS text is short and article link exists
    LaunchedEffect(article.id) {
        if (fullText.length < 400 && article.link.isNotBlank()) {
            loadWebArticle()
        }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Feed Name badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = article.feedTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }

            Spacer(modifier = Modifier.height(12.dp))

            // Article Image (Hero)
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Article Title
            Text(
                text = article.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata: Date & Author
            if (article.pubDate.isNotBlank() || !article.author.isNullOrBlank()) {
                val metaText = buildString {
                    if (article.pubDate.isNotBlank()) append(article.pubDate)
                    if (article.pubDate.isNotBlank() && !article.author.isNullOrBlank()) append(" • ")
                    if (!article.author.isNullOrBlank()) append("Von ${article.author}")
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Actions: Small AI summary button + Webtext chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Small AI summary button with icon
                if (summaryState.summary == null || isSummaryDismissed) {
                    FilledTonalButton(
                        onClick = {
                            isSummaryDismissed = false
                            if (summaryState.summary == null) {
                                onSummarize(false, fullText, selectedLevel)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (summaryState.summary != null) "KI-Zusammenfassung anzeigen" else "KI-Zusammenfassung",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Web article reload chip
                if (!webTextLoaded && article.link.isNotBlank() && !isLoadingWebText) {
                    AssistChip(
                        onClick = { loadWebArticle() },
                        label = { Text("Webtext nachladen") },
                        leadingIcon = {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                } else if (webTextLoaded) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Webtext geladen") },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }

            // Web article loading indicator
            if (isLoadingWebText) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vollständigen Artikeltext von Webseite laden...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // AI Summary Card: only shown when generating, completed or error
            val showSummaryCard = (summaryState.isLoading || summaryState.summary != null || summaryState.error != null) && !isSummaryDismissed
            if (showSummaryCard) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "KI-Zusammenfassung",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = geminiModelName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                if (summaryState.summary != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { isSummaryDismissed = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Zusammenfassung ausblenden",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Summary Level Selector Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SummaryLevel.entries.forEach { level ->
                                FilterChip(
                                    selected = (selectedLevel == level),
                                    onClick = {
                                        if (selectedLevel != level) {
                                            selectedLevel = level
                                            onSummarize(false, fullText, level)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = level.shortLabel,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                    when {
                        summaryState.isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = summaryState.statusMessage
                                        ?: "Gemini analysiert den vollständigen Artikel...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        summaryState.summary != null -> {
                            Column {
                                MarkdownText(
                                    markdown = summaryState.summary,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Artikel-Zusammenfassung", summaryState.summary)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Zusammenfassung kopiert", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Kopieren",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { onSummarize(true, fullText, selectedLevel) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Neu generieren",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        summaryState.error == "MISSING_API_KEY" -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Um den vollständigen Artikel mit Gemini zusammenzufassen, wird ein API-Key benötigt.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onOpenGeminiSettings,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Gemini API-Key hinterlegen")
                                }
                            }
                        }

                        summaryState.error != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = summaryState.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onSummarize(true, fullText, selectedLevel) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Erneut versuchen")
                                    }
                                    IconButton(onClick = onOpenGeminiSettings) {
                                        Icon(Icons.Default.Key, contentDescription = "Einstellungen")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Section Header: Full Article Text
            Text(
                text = "Vollständiger Artikeltext",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Full Article Text with clean paragraph formatting (visual paragraph after each line break)
            if (fullText.isNotBlank()) {
                val paragraphs = remember(fullText) {
                    fullText.lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
                paragraphs.forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.35
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }
            } else {
                Text(
                    text = "Kein Vorschautext verfügbar. Öffne den vollständigen Artikel im Browser.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (article.link.isNotBlank()) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Im Browser öffnen")
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Teilen", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
