package de.carcophan.ai_rss.data.model

import java.util.UUID

data class Keyword(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
