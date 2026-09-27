package de.carcophan.ai_rss.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.regex.Pattern

data class ExtractedArticleWeb(
    val fullText: String,
    val imageUrl: String?
)

object ArticleWebExtractor {

    private val SCRIPT_STYLE_PATTERN = Pattern.compile("(?is)<(script|style|nav|footer|aside|noscript|svg|button|form)[^>]*>.*?</\\1>")
    private val OG_IMAGE_PATTERN = Pattern.compile("(?i)<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"']")
    private val OG_IMAGE_PATTERN_ALT = Pattern.compile("(?i)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"']")
    private val PARAGRAPH_PATTERN = Pattern.compile("(?is)<p(?:\\s+[^>]*)?>(.*?)</p>")
    private val INTRO_PATTERN = Pattern.compile("(?is)<(?:div|p)[^>]+class=[\"'][^\"']*(?:go-article-header__intro|article-intro|article__intro|intro|lead)[^\"']*[\"'][^>]*>(.*?)</(?:div|p)>")
    private val ARTICLE_TAG_PATTERN = Pattern.compile("(?is)<article[^>]*>(.*?)</article>")
    private val HTML_TAG_PATTERN = Pattern.compile("<[^>]*>")

    private const val GOLEM_DEFAULT_COOKIE = "golem_consent20=cmp|250101; golem_consent20=simple|250101; golem_consent=simple"

    suspend fun extractFullArticle(urlStr: String): ExtractedArticleWeb? = withContext(Dispatchers.IO) {
        try {
            var targetUrl = urlStr
            var cookie = if (targetUrl.contains("golem.de", ignoreCase = true)) {
                GOLEM_DEFAULT_COOKIE
            } else {
                null
            }

            var (finalUrl, html) = fetchHtml(targetUrl, cookie) ?: return@withContext null

            // Check if redirected to Golem consent wall (auswahl.html)
            if (finalUrl.contains("auswahl.html", ignoreCase = true) || isGolemConsentPage(html)) {
                val extractedCookie = extractGolemCookieFromConsentPage(html)
                val originalArticleUrl = extractOriginalUrlFromConsent(finalUrl, html) ?: targetUrl

                val retryCookie = extractedCookie ?: GOLEM_DEFAULT_COOKIE
                val retryResult = fetchHtml(originalArticleUrl, retryCookie)
                if (retryResult != null && !retryResult.first.contains("auswahl.html")) {
                    html = retryResult.second
                }
            }

            // Extract OG image
            var ogImage: String? = null
            var matcher = OG_IMAGE_PATTERN.matcher(html)
            if (matcher.find()) {
                ogImage = matcher.group(1)
            } else {
                matcher = OG_IMAGE_PATTERN_ALT.matcher(html)
                if (matcher.find()) {
                    ogImage = matcher.group(1)
                }
            }

            // Remove scripts, styles, navs, svgs, buttons
            val sanitized = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll("")

            // Find all <article> tags and choose the one with the most content (avoids teaser/ad articles)
            val targetHtml = selectBestArticleSection(sanitized)

            val paragraphs = mutableListOf<String>()

            // Extract lead/intro paragraph if present
            val introMatcher = INTRO_PATTERN.matcher(targetHtml)
            if (introMatcher.find()) {
                val introContent = cleanHtmlSnippet(introMatcher.group(1) ?: "")
                if (introContent.length > 25 && !isBoilerplate(introContent)) {
                    paragraphs.add(introContent)
                }
            }

            // Extract standard paragraphs
            val pMatcher = PARAGRAPH_PATTERN.matcher(targetHtml)
            while (pMatcher.find()) {
                val pContent = pMatcher.group(1) ?: ""
                val clean = cleanHtmlSnippet(pContent)
                // Filter out duplicates (e.g. if intro was also in <p>), short fragments, or boilerplate
                if (clean.length > 25 && !isBoilerplate(clean) && !paragraphs.contains(clean)) {
                    paragraphs.add(clean)
                }
            }

            val fullText = if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n\n")
            } else {
                ""
            }

            if (fullText.isNotBlank()) {
                ExtractedArticleWeb(fullText = fullText, imageUrl = ogImage)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchHtml(urlStr: String, cookie: String?): Pair<String, String>? {
        return try {
            val url = URL(urlStr)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.8")
                if (!cookie.isNullOrBlank()) {
                    setRequestProperty("Cookie", cookie)
                }
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) return null

            val finalUrl = connection.url.toString()
            val html = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }
            Pair(finalUrl, html)
        } catch (_: Exception) {
            null
        }
    }

    private fun selectBestArticleSection(sanitizedHtml: String): String {
        val articleMatcher = ARTICLE_TAG_PATTERN.matcher(sanitizedHtml)
        var bestSection: String? = null
        var maxParagraphCount = 0

        while (articleMatcher.find()) {
            val section = articleMatcher.group(1) ?: continue
            val pCount = countParagraphs(section)
            if (pCount > maxParagraphCount) {
                maxParagraphCount = pCount
                bestSection = section
            }
        }

        return if (bestSection != null && maxParagraphCount > 0) {
            bestSection
        } else {
            sanitizedHtml
        }
    }

    private fun countParagraphs(html: String): Int {
        val matcher = PARAGRAPH_PATTERN.matcher(html)
        var count = 0
        while (matcher.find()) {
            count++
        }
        return count
    }

    private fun isGolemConsentPage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("c2_wrapper") ||
                lower.contains("skript wurde nicht geladen") ||
                (lower.contains("willkommen auf golem") && lower.contains("cookies zustimmen"))
    }

    private fun extractGolemCookieFromConsentPage(html: String): String? {
        val nameMatcher = Pattern.compile("[\"']cookieName[\"']\\s*:\\s*[\"']([^\"']+)[\"']").matcher(html)
        val verMatcher = Pattern.compile("[\"']cookieVersion[\"']\\s*:\\s*[\"']([^\"']+)[\"']").matcher(html)

        val cookieName = if (nameMatcher.find()) nameMatcher.group(1) else "golem_consent20"
        val cookieVersion = if (verMatcher.find()) verMatcher.group(1) else "|250101"

        return "$cookieName=cmp$cookieVersion; $cookieName=simple$cookieVersion; golem_consent=simple"
    }

    private fun extractOriginalUrlFromConsent(currentUrl: String, html: String): String? {
        // Extract 'from=' parameter in URL
        if (currentUrl.contains("from=")) {
            try {
                val fromPart = currentUrl.substringAfter("from=").substringBefore("&")
                val decoded = URLDecoder.decode(fromPart, "UTF-8")
                if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                    return decoded
                }
            } catch (_: Exception) {}
        }

        // Extract destination from JS config in HTML
        val destMatcher = Pattern.compile("[\"']destination[\"']\\s*:\\s*[\"']([^\"']+)[\"']").matcher(html)
        if (destMatcher.find()) {
            val raw = destMatcher.group(1)
            return raw?.replace("\\/", "/")
        }

        return null
    }

    private fun cleanHtmlSnippet(html: String): String {
        val withoutTags = HTML_TAG_PATTERN.matcher(html).replaceAll(" ")
        return withoutTags
            .replace("(öffnet im neuen Fenster)", "")
            .replace("(öffnet in neuem Tab)", "")
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
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun isBoilerplate(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("cookie") ||
                lower.contains("datenschutz") ||
                lower.contains("impressum") ||
                lower.contains("abonnieren") ||
                lower.contains("newsletter") ||
                lower.contains("alle rechte vorbehalten") ||
                lower.contains("all rights reserved") ||
                lower.contains("skript wurde nicht geladen") ||
                lower.contains("informationen zur problembehandlung finden sie") ||
                lower.contains("referenz-link zur seite") ||
                lower.contains("golem pur") ||
                lower.contains("cookies zustimmen") ||
                lower.contains("zustimmungs-dialog") ||
                lower.contains("artikel merken")
    }
}
