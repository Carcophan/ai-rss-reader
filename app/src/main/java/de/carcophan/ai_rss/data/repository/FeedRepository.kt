package de.carcophan.ai_rss.data.repository

import android.content.Context
import de.carcophan.ai_rss.data.model.Feed
import de.carcophan.ai_rss.data.model.RssItem
import de.carcophan.ai_rss.data.parser.RssParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

class FeedRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("ai_rss_prefs", Context.MODE_PRIVATE)
    private val FEEDS_KEY = "saved_feeds"

    companion object {
        val SUGGESTIONS = listOf(
            Feed(
                title = "Tagesschau",
                url = "https://www.tagesschau.de/infoservices/alle-meldungen-100~rss2.xml",
                description = "Aktuelle Nachrichten der Tagesschau"
            ),
            Feed(
                title = "Heise Online",
                url = "https://www.heise.de/rss/heise-atom.xml",
                description = "IT-Nachrichten und News aus der Tech-Welt"
            ),
            Feed(
                title = "Spiegel Online",
                url = "https://www.spiegel.de/schlagzeilen/index.rss",
                description = "Schlagzeilen von SPIEGEL ONLINE"
            ),
            Feed(
                title = "Hacker News",
                url = "https://news.ycombinator.com/rss",
                description = "Tech and startup news"
            ),
            Feed(
                title = "Golem.de",
                url = "https://rss.golem.de/rss.php?feed=RSS2.0",
                description = "IT-News für Profis"
            )
        )
    }

    fun getFeeds(): List<Feed> {
        val jsonString = prefs.getString(FEEDS_KEY, null)
        if (jsonString.isNullOrBlank()) {
            // Default initial feeds
            val defaultFeeds = listOf(
                SUGGESTIONS[0], // Tagesschau
                SUGGESTIONS[1]  // Heise
            )
            saveFeeds(defaultFeeds)
            return defaultFeeds
        }

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<Feed>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Feed(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        url = obj.optString("url"),
                        description = obj.optString("description", ""),
                        link = obj.optString("link", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveFeeds(feeds: List<Feed>) {
        val jsonArray = JSONArray()
        for (feed in feeds) {
            val obj = JSONObject().apply {
                put("id", feed.id)
                put("title", feed.title)
                put("url", feed.url)
                put("description", feed.description)
                put("link", feed.link)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(FEEDS_KEY, jsonArray.toString()).apply()
    }

    suspend fun addFeed(rawUrl: String, customTitle: String? = null): Result<Feed> = withContext(Dispatchers.IO) {
        val normalizedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
            "https://$rawUrl"
        } else {
            rawUrl
        }

        try {
            val tempFeed = Feed(
                title = customTitle?.trim() ?: "",
                url = normalizedUrl
            )
            val parsedResult = fetchAndParse(normalizedUrl, tempFeed)
            val finalTitle = when {
                !customTitle.isNullOrBlank() -> customTitle.trim()
                parsedResult.title.isNotBlank() -> parsedResult.title
                else -> normalizedUrl
            }

            val newFeed = tempFeed.copy(
                title = finalTitle,
                description = parsedResult.description,
                link = parsedResult.link
            )

            val current = getFeeds().toMutableList()
            // Avoid duplicates by URL
            if (current.any { it.url.equals(normalizedUrl, ignoreCase = true) }) {
                return@withContext Result.failure(IllegalArgumentException("Dieser Feed wurde bereits hinzugefügt."))
            }

            current.add(newFeed)
            saveFeeds(current)
            Result.success(newFeed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteFeed(feedId: String): List<Feed> {
        val updated = getFeeds().filterNot { it.id == feedId }
        saveFeeds(updated)
        return updated
    }

    suspend fun fetchFeedArticles(feed: Feed): List<RssItem> = withContext(Dispatchers.IO) {
        try {
            val result = fetchAndParse(feed.url, feed)
            result.items
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchAllArticles(feeds: List<Feed>): List<RssItem> = coroutineScope {
        val deferreds = feeds.map { feed ->
            async(Dispatchers.IO) {
                fetchFeedArticles(feed)
            }
        }
        val allItems = deferreds.awaitAll().flatten()
        allItems.sortedWith(
            compareByDescending<RssItem> { it.pubDateMillis }
                .thenByDescending { it.pubDate }
        )
    }

    private fun fetchAndParse(urlStr: String, feed: Feed): de.carcophan.ai_rss.data.parser.ParsedFeedResult {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android; AI-RSS-Reader)")
            setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw java.io.IOException("HTTP-Fehler $responseCode beim Abrufen des Feeds")
        }

        connection.inputStream.use { input ->
            val bufferedInput = BufferedInputStream(input)
            return RssParser.parse(bufferedInput, feed)
        }
    }
}
