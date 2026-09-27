package de.carcophan.ai_rss.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.carcophan.ai_rss.data.model.Feed
import de.carcophan.ai_rss.data.model.RssItem
import de.carcophan.ai_rss.data.repository.ArticleWebExtractor
import de.carcophan.ai_rss.data.repository.FeedRepository
import de.carcophan.ai_rss.data.repository.GeminiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArticleSummaryState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val summary: String? = null,
    val error: String? = null
)

data class DailyBriefingState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val briefing: String? = null,
    val error: String? = null,
    val generatedDate: String? = null
)

data class RssUiState(
    val feeds: List<Feed> = emptyList(),
    val selectedFeed: Feed? = null,
    val articles: List<RssItem> = emptyList(),
    val isLoading: Boolean = false,
    val isAddingFeed: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val summaryStates: Map<String, ArticleSummaryState> = emptyMap(),
    val dailyBriefingState: DailyBriefingState = DailyBriefingState()
)

class RssViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FeedRepository(application.applicationContext)
    val geminiRepository = GeminiRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(RssUiState())
    val uiState: StateFlow<RssUiState> = _uiState.asStateFlow()

    init {
        loadFeedsAndArticles()
    }

    fun loadFeedsAndArticles() {
        val feeds = repository.getFeeds()
        _uiState.update { it.copy(feeds = feeds, isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val articles = if (_uiState.value.selectedFeed != null) {
                    repository.fetchFeedArticles(_uiState.value.selectedFeed!!)
                } else {
                    repository.fetchAllArticles(feeds)
                }
                _uiState.update { it.copy(articles = articles, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Fehler beim Laden der Artikel: ${e.localizedMessage ?: "Unbekannter Fehler"}"
                    )
                }
            }
        }
    }

    fun selectFeed(feed: Feed?) {
        _uiState.update { it.copy(selectedFeed = feed, isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val articles = if (feed != null) {
                    repository.fetchFeedArticles(feed)
                } else {
                    repository.fetchAllArticles(_uiState.value.feeds)
                }
                _uiState.update { it.copy(articles = articles, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Fehler beim Laden: ${e.localizedMessage ?: "Unbekannter Fehler"}"
                    )
                }
            }
        }
    }

    fun addFeed(url: String, customTitle: String? = null, onSuccess: () -> Unit = {}) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Bitte gib eine gültige Feed-URL ein.") }
            return
        }

        _uiState.update { it.copy(isAddingFeed = true, errorMessage = null) }
        viewModelScope.launch {
            val result = repository.addFeed(trimmedUrl, customTitle)
            result.onSuccess { newFeed ->
                val updatedFeeds = repository.getFeeds()
                _uiState.update {
                    it.copy(
                        feeds = updatedFeeds,
                        isAddingFeed = false,
                        selectedFeed = newFeed
                    )
                }
                selectFeed(newFeed)
                onSuccess()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAddingFeed = false,
                        errorMessage = "Konnte Feed nicht laden: ${error.localizedMessage ?: "Ungültige Feed-Adresse oder Verbindungsproblem"}"
                    )
                }
            }
        }
    }

    fun removeFeed(feedId: String) {
        val updatedFeeds = repository.deleteFeed(feedId)
        val wasSelected = _uiState.value.selectedFeed?.id == feedId
        val newSelected = if (wasSelected) null else _uiState.value.selectedFeed
        _uiState.update { it.copy(feeds = updatedFeeds, selectedFeed = newSelected) }
        selectFeed(newSelected)
    }

    fun refresh() {
        selectFeed(_uiState.value.selectedFeed)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun getSummaryState(articleId: String): ArticleSummaryState {
        val inMemory = _uiState.value.summaryStates[articleId]
        if (inMemory != null) return inMemory

        val cached = geminiRepository.getCachedSummary(articleId)
        return if (cached != null) {
            ArticleSummaryState(summary = cached)
        } else {
            ArticleSummaryState()
        }
    }

    fun summarizeArticle(
        article: RssItem,
        fullTextOverride: String? = null,
        forceRefresh: Boolean = false
    ) {
        if (!geminiRepository.hasApiKey()) {
            updateArticleSummaryState(
                article.id,
                ArticleSummaryState(error = "MISSING_API_KEY")
            )
            return
        }

        if (!forceRefresh) {
            val cached = geminiRepository.getCachedSummary(article.id)
            if (cached != null) {
                updateArticleSummaryState(article.id, ArticleSummaryState(summary = cached))
                return
            }
        }

        viewModelScope.launch {
            try {
                // Step 1: Ensure full article text is obtained
                val textToSummarize = if (!fullTextOverride.isNullOrBlank() && fullTextOverride.length > 300) {
                    fullTextOverride
                } else if (article.link.isNotBlank()) {
                    updateArticleSummaryState(
                        article.id,
                        ArticleSummaryState(
                            isLoading = true,
                            statusMessage = "Lade vollständigen Artikeltext von Webseite..."
                        )
                    )
                    val extracted = ArticleWebExtractor.extractFullArticle(article.link)
                    extracted?.fullText?.ifBlank { null }
                        ?: article.content.ifBlank { article.description }
                } else {
                    article.content.ifBlank { article.description }
                }

                if (textToSummarize.isBlank()) {
                    updateArticleSummaryState(
                        article.id,
                        ArticleSummaryState(error = "Kein Text zum Zusammenfassen verfügbar.")
                    )
                    return@launch
                }

                // Step 2: Request summary from Gemini
                val modelName = geminiRepository.getModel()
                updateArticleSummaryState(
                    article.id,
                    ArticleSummaryState(
                        isLoading = true,
                        statusMessage = "Gemini ($modelName) analysiert den vollständigen Artikel..."
                    )
                )

                val result = geminiRepository.summarizeArticle(
                    title = article.title,
                    fullArticleText = textToSummarize,
                    sourceName = article.feedTitle
                )

                result.onSuccess { summaryText ->
                    geminiRepository.saveCachedSummary(article.id, summaryText)
                    updateArticleSummaryState(
                        article.id,
                        ArticleSummaryState(
                            isLoading = false,
                            summary = summaryText
                        )
                    )
                }.onFailure { error ->
                    updateArticleSummaryState(
                        article.id,
                        ArticleSummaryState(
                            isLoading = false,
                            error = error.localizedMessage ?: "Fehler bei der Zusammenfassung."
                        )
                    )
                }
            } catch (e: Exception) {
                updateArticleSummaryState(
                    article.id,
                    ArticleSummaryState(
                        isLoading = false,
                        error = e.localizedMessage ?: "Unerwarteter Fehler aufgetreten."
                    )
                )
            }
        }
    }

    private fun updateArticleSummaryState(articleId: String, state: ArticleSummaryState) {
        _uiState.update { current ->
            val updatedMap = current.summaryStates.toMutableMap()
            updatedMap[articleId] = state
            current.copy(summaryStates = updatedMap)
        }
    }

    fun loadOrGenerateDailyBriefing(forceRefresh: Boolean = false) {
        val feedName = _uiState.value.selectedFeed?.title ?: "Alle Feeds"
        val feedId = _uiState.value.selectedFeed?.id ?: "all"
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val cacheKey = "${feedId}_$todayStr"

        if (!geminiRepository.hasApiKey()) {
            _uiState.update {
                it.copy(
                    dailyBriefingState = DailyBriefingState(error = "MISSING_API_KEY")
                )
            }
            return
        }

        if (!forceRefresh) {
            val current = _uiState.value.dailyBriefingState
            if (!current.briefing.isNullOrBlank() && current.generatedDate == todayStr) {
                return
            }
            val cached = geminiRepository.getCachedBriefing(cacheKey)
            if (!cached.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        dailyBriefingState = DailyBriefingState(
                            briefing = cached,
                            generatedDate = todayStr
                        )
                    )
                }
                return
            }
        }

        val articlesToBrief = _uiState.value.articles
        if (articlesToBrief.isEmpty()) {
            _uiState.update {
                it.copy(
                    dailyBriefingState = DailyBriefingState(
                        error = "Keine Artikel zum Erstellen eines Briefings verfügbar."
                    )
                )
            }
            return
        }

        val modelName = geminiRepository.getModel()
        val count = articlesToBrief.take(20).size
        _uiState.update {
            it.copy(
                dailyBriefingState = DailyBriefingState(
                    isLoading = true,
                    statusMessage = "Gemini ($modelName) analysiert die $count wichtigsten Meldungen..."
                )
            )
        }

        viewModelScope.launch {
            try {
                val result = geminiRepository.generateDailyBriefing(feedName, articlesToBrief)
                result.onSuccess { briefingText ->
                    geminiRepository.saveCachedBriefing(cacheKey, briefingText)
                    _uiState.update {
                        it.copy(
                            dailyBriefingState = DailyBriefingState(
                                isLoading = false,
                                briefing = briefingText,
                                generatedDate = todayStr
                            )
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            dailyBriefingState = DailyBriefingState(
                                isLoading = false,
                                error = error.localizedMessage ?: "Fehler beim Erstellen des Daily Briefings."
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        dailyBriefingState = DailyBriefingState(
                            isLoading = false,
                            error = e.localizedMessage ?: "Unerwarteter Fehler beim Erstellen des Briefings."
                        )
                    )
                }
            }
        }
    }
}
