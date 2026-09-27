package de.carcophan.ai_rss.data.model

import java.util.UUID

data class Feed(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val description: String = "",
    val link: String = ""
)
