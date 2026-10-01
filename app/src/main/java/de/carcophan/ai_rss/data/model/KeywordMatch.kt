package de.carcophan.ai_rss.data.model

data class KeywordMatch(
    val articleId: String,
    val isRelevant: Boolean,
    val reason: String = "",
    val relevanceScore: Int = 0
)
