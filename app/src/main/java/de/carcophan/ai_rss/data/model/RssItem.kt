package de.carcophan.ai_rss.data.model

import java.util.UUID

data class RssItem(
    val id: String = UUID.randomUUID().toString(),
    val feedId: String,
    val feedTitle: String,
    val title: String,
    val link: String,
    val description: String,
    val content: String = "",
    val pubDate: String,
    val pubDateMillis: Long = 0L,
    val author: String? = null,
    val imageUrl: String? = null
)
