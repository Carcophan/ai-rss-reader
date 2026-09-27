package de.carcophan.ai_rss.data.parser

import android.util.Xml
import de.carcophan.ai_rss.data.model.Feed
import de.carcophan.ai_rss.data.model.RssItem
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

data class ParsedFeedResult(
    val title: String,
    val description: String,
    val link: String,
    val items: List<RssItem>
)

object RssParser {

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    )

    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]*>")

    fun parse(inputStream: InputStream, feed: Feed): ParsedFeedResult {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(inputStream, null)
        }

        var eventType = parser.eventType
        var isAtom = false
        var channelTitle = ""
        var channelDescription = ""
        var channelLink = ""
        val items = mutableListOf<RssItem>()

        var currentItemTitle = ""
        var currentItemLink = ""
        var currentItemDesc = ""
        var currentItemContent = ""
        var currentItemPubDate = ""
        var currentItemAuthor: String? = null
        var currentItemImage: String? = null
        var inItem = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val rawName = parser.name ?: ""
            val tagName = rawName.lowercase(Locale.ROOT)

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (tagName == "feed") {
                        isAtom = true
                    } else if (tagName == "item" || (isAtom && tagName == "entry")) {
                        inItem = true
                        currentItemTitle = ""
                        currentItemLink = ""
                        currentItemDesc = ""
                        currentItemContent = ""
                        currentItemPubDate = ""
                        currentItemAuthor = null
                        currentItemImage = null
                    } else if (inItem) {
                        when {
                            tagName == "title" -> currentItemTitle = readText(parser)
                            tagName == "link" -> {
                                if (isAtom) {
                                    val rel = parser.getAttributeValue(null, "rel")
                                    val href = parser.getAttributeValue(null, "href")
                                    if (rel == null || rel == "alternate" || currentItemLink.isEmpty()) {
                                        if (!href.isNullOrBlank()) {
                                            currentItemLink = href
                                        }
                                    }
                                    if (currentItemLink.isEmpty()) {
                                        val text = readText(parser)
                                        if (text.isNotBlank()) currentItemLink = text
                                    }
                                } else {
                                    currentItemLink = readText(parser)
                                }
                            }
                            tagName == "encoded" || tagName.endsWith(":encoded") -> {
                                val text = readText(parser)
                                if (text.isNotBlank()) {
                                    currentItemContent = text
                                }
                            }
                            tagName == "description" || tagName == "summary" -> {
                                val text = readText(parser)
                                if (currentItemDesc.isEmpty()) {
                                    currentItemDesc = text
                                }
                            }
                            tagName == "content" -> {
                                val text = readText(parser)
                                if (text.isNotBlank()) {
                                    currentItemContent = text
                                }
                            }
                            tagName == "pubdate" || tagName == "published" || tagName == "updated" || tagName.endsWith(":date") -> {
                                currentItemPubDate = readText(parser)
                            }
                            tagName == "author" || tagName.endsWith(":creator") -> {
                                currentItemAuthor = readText(parser)
                            }
                            tagName == "enclosure" -> {
                                val type = parser.getAttributeValue(null, "type")
                                val url = parser.getAttributeValue(null, "url")
                                val isImg = (type != null && type.startsWith("image/")) ||
                                        (url != null && isImageUrl(url))
                                if (isImg && !url.isNullOrBlank() && currentItemImage == null) {
                                    currentItemImage = url
                                }
                            }
                            tagName == "media:content" || tagName == "content" && parser.prefix == "media" -> {
                                val medium = parser.getAttributeValue(null, "medium")
                                val type = parser.getAttributeValue(null, "type")
                                val url = parser.getAttributeValue(null, "url")
                                val isImg = medium == "image" ||
                                        (type != null && type.startsWith("image/")) ||
                                        (url != null && isImageUrl(url)) ||
                                        medium == null
                                if (isImg && !url.isNullOrBlank() && currentItemImage == null) {
                                    currentItemImage = url
                                }
                            }
                            tagName == "media:thumbnail" || tagName == "thumbnail" && parser.prefix == "media" -> {
                                val url = parser.getAttributeValue(null, "url")
                                if (!url.isNullOrBlank() && currentItemImage == null) {
                                    currentItemImage = url
                                }
                            }
                            tagName == "itunes:image" || (tagName == "image" && parser.prefix == "itunes") -> {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank() && currentItemImage == null) {
                                    currentItemImage = href
                                }
                            }
                        }
                    } else {
                        // Channel level
                        when {
                            tagName == "title" -> if (channelTitle.isEmpty()) channelTitle = readText(parser)
                            tagName == "description" || tagName == "subtitle" -> if (channelDescription.isEmpty()) channelDescription = readText(parser)
                            tagName == "link" -> {
                                if (isAtom) {
                                    val href = parser.getAttributeValue(null, "href")
                                    if (!href.isNullOrBlank()) channelLink = href
                                    else {
                                        val text = readText(parser)
                                        if (text.isNotBlank()) channelLink = text
                                    }
                                } else {
                                    if (channelLink.isEmpty()) channelLink = readText(parser)
                                }
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (tagName == "item" || (isAtom && tagName == "entry")) {
                        inItem = false

                        // Extract image from HTML if not yet found
                        if (currentItemImage == null) {
                            currentItemImage = extractImageFromHtml(currentItemContent)
                                ?: extractImageFromHtml(currentItemDesc)
                        }

                        val cleanTitle = cleanHtml(currentItemTitle).trim()
                        val cleanDesc = cleanHtml(currentItemDesc).trim()
                        val cleanContent = if (currentItemContent.isNotBlank()) {
                            cleanHtml(currentItemContent).trim()
                        } else {
                            cleanDesc
                        }
                        val pubDateMillis = parseDateToMillis(currentItemPubDate)

                        val stableId = if (currentItemLink.isNotBlank()) {
                            "${feed.id}_${currentItemLink.trim().hashCode()}"
                        } else {
                            "${feed.id}_${cleanTitle.hashCode()}"
                        }

                        if (cleanTitle.isNotEmpty() || currentItemLink.isNotEmpty()) {
                            items.add(
                                RssItem(
                                    id = stableId,
                                    feedId = feed.id,
                                    feedTitle = feed.title.ifBlank { channelTitle.ifBlank { "Unbenannter Feed" } },
                                    title = cleanTitle.ifBlank { "Ohne Titel" },
                                    link = currentItemLink.trim(),
                                    description = cleanDesc,
                                    content = cleanContent,
                                    pubDate = currentItemPubDate.trim(),
                                    pubDateMillis = pubDateMillis,
                                    author = currentItemAuthor?.trim(),
                                    imageUrl = currentItemImage?.trim()
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeedResult(
            title = channelTitle.trim(),
            description = channelDescription.trim(),
            link = channelLink.trim(),
            items = items
        )
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.contains("image")
    }

    private val IMG_REGEX = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    private fun extractImageFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val matcher = IMG_REGEX.matcher(html)
        if (matcher.find()) {
            val url = matcher.group(1)
            if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                return url
            }
        }
        return null
    }

    private fun cleanHtml(html: String): String {
        if (html.isEmpty()) return ""
        // Replace paragraph/break tags with newlines first so paragraphs don't merge into one giant block
        val withLineBreaks = html
            .replace("(?i)<br\\s*/?>".toRegex(), "\n")
            .replace("(?i)</p>".toRegex(), "\n\n")
            .replace("(?i)</li>".toRegex(), "\n")
            .replace("(?i)</div>".toRegex(), "\n")

        val withoutTags = HTML_TAG_PATTERN.matcher(withLineBreaks).replaceAll(" ")
        return withoutTags
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&euro;", "€")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace("&hellip;", "…")
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8216;", "‘")
            .replace("&#8217;", "’")
            .replace("&#8220;", "“")
            .replace("&#8221;", "”")
            .replace("[ \t]+".toRegex(), " ")
            .replace("\n[ \t]+".toRegex(), "\n")
            .replace("\n{3,}".toRegex(), "\n\n")
            .trim()
    }

    private fun parseDateToMillis(dateStr: String): Long {
        if (dateStr.isBlank()) return 0L
        for (format in DATE_FORMATS) {
            try {
                val parsed = format.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return 0L
    }
}
