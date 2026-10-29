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

    private val SCRIPT_STYLE_PATTERN = Pattern.compile("(?is)<(script|style|nav|footer|aside|noscript|svg|button|form|figure|details)[^>]*>.*?</\\1>")
    private val OG_IMAGE_PATTERN = Pattern.compile("(?i)<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"']")
    private val OG_IMAGE_PATTERN_ALT = Pattern.compile("(?i)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"']")
    private val PARAGRAPH_PATTERN = Pattern.compile("(?is)<p(?:\\s+[^>]*)?>(.*?)</p>")
    private val CONTENT_TAG_PATTERN = Pattern.compile("(?is)<(h1|h2|h3|p)(?:\\s+[^>]*)?>(.*?)</\\1>")
    private val INTRO_PATTERN = Pattern.compile("(?is)<(?:div|p)[^>]+class=[\"'][^\"']*(?:go-article-header__intro|article-intro|article__intro|intro|lead)[^\"']*[\"'][^>]*>(.*?)</(?:div|p)>")
    private val INNER_ARTICLE_PATTERN = Pattern.compile("(?is)<article\\b[^>]*>.*?</article>")
    private val TEASER_BLOCK_PATTERN = Pattern.compile("(?is)<div[^>]+class=[\"'][^\"']*(?:go-teaser|teaser-block|ad-slot|go-tag-list)[^\"']*[\"'][^>]*>.*?</div>")
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
                cookie = retryCookie
                val retryResult = fetchHtml(originalArticleUrl, retryCookie)
                if (retryResult != null && !retryResult.first.contains("auswahl.html")) {
                    finalUrl = retryResult.first
                    html = retryResult.second
                }
            }

            val ogImage = extractOgImage(html)
            val isGolem = finalUrl.contains("golem.de", ignoreCase = true)
            val allPages = if (isGolem) {
                extractGolemPageUrls(finalUrl, html)
            } else {
                emptyList()
            }

            val paragraphs = mutableListOf<String>()

            if (allPages.size > 1) {
                for ((index, pageUrl) in allPages.take(10).withIndex()) {
                    val pageHtml = if (pageUrl == finalUrl) {
                        html
                    } else {
                        fetchHtml(pageUrl, cookie)?.second
                    } ?: continue

                    extractContentElements(
                        html = pageHtml,
                        isFirstPage = (index == 0),
                        into = paragraphs
                    )
                }
            } else {
                extractContentElements(
                    html = html,
                    isFirstPage = true,
                    into = paragraphs
                )
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

    private fun extractOgImage(html: String): String? {
        var matcher = OG_IMAGE_PATTERN.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        matcher = OG_IMAGE_PATTERN_ALT.matcher(html)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return null
    }

    private fun extractContentElements(
        html: String,
        isFirstPage: Boolean,
        into: MutableList<String>
    ) {
        val sanitized = SCRIPT_STYLE_PATTERN.matcher(html).replaceAll("")
        val section = selectBestArticleSection(sanitized)

        // Strip embedded teasers, ads and related course blocks
        val cleaned = section
            .let { INNER_ARTICLE_PATTERN.matcher(it).replaceAll("") }
            .let { TEASER_BLOCK_PATTERN.matcher(it).replaceAll("") }

        // Extract lead/intro paragraph if present on first page
        if (isFirstPage) {
            val introMatcher = INTRO_PATTERN.matcher(cleaned)
            if (introMatcher.find()) {
                val introContent = cleanHtmlSnippet(introMatcher.group(1) ?: "")
                if (introContent.length > 25 && !isBoilerplate(introContent) && !into.contains(introContent)) {
                    into.add(introContent)
                }
            }
        }

        // Extract headings and paragraphs in order
        val contentMatcher = CONTENT_TAG_PATTERN.matcher(cleaned)
        while (contentMatcher.find()) {
            val tag = (contentMatcher.group(1) ?: "").lowercase()
            val raw = contentMatcher.group(2) ?: ""
            val clean = cleanHtmlSnippet(raw)

            if (clean.length < 4 || isBoilerplate(clean)) continue

            val formatted = when (tag) {
                "h1" -> if (!isFirstPage) "## $clean" else null
                "h2" -> "## $clean"
                "h3" -> "### $clean"
                "p" -> if (clean.length >= 20) clean else null
                else -> null
            } ?: continue

            if (!into.contains(formatted) && !into.contains(clean)) {
                into.add(formatted)
            }
        }
    }

    private fun extractGolemPageUrls(currentUrl: String, html: String): List<String> {
        val pageUrls = mutableListOf<String>()

        // 1. Check for <ol class="go-index__list"> or links with class="go-index-link"
        val indexLinkMatcher = Pattern.compile("(?i)(?:class=[\"'][^\"']*go-index-link[^\"']*[\"'][^>]*href=[\"']([^\"']+)[\"']|href=[\"']([^\"']+)[\"'][^>]*class=[\"'][^\"']*go-index-link[^\"']*[\"'])")
        val indexMatcher = indexLinkMatcher.matcher(html)
        while (indexMatcher.find()) {
            val href = indexMatcher.group(1) ?: indexMatcher.group(2) ?: continue
            val fullUrl = resolveUrl(currentUrl, href)
            if (fullUrl.startsWith("http") && !pageUrls.contains(fullUrl)) {
                pageUrls.add(fullUrl)
            }
        }

        if (pageUrls.isNotEmpty()) {
            return pageUrls
        }

        // 2. Fallback: check <nav class="go-pagination...">
        val navMatcher = Pattern.compile("(?is)<nav[^>]+class=[\"'][^\"']*go-pagination[^\"']*[\"'][^>]*>(.*?)</nav>").matcher(html)
        if (navMatcher.find()) {
            val navContent = navMatcher.group(1) ?: ""
            val hrefMatcher = Pattern.compile("(?i)href=[\"']([^\"']+)[\"']").matcher(navContent)
            while (hrefMatcher.find()) {
                val href = hrefMatcher.group(1) ?: continue
                val fullUrl = resolveUrl(currentUrl, href)
                if (fullUrl.startsWith("http") && !pageUrls.contains(fullUrl)) {
                    pageUrls.add(fullUrl)
                }
            }
        }

        return pageUrls
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        return try {
            URL(URL(baseUrl), relativeUrl).toString()
        } catch (_: Exception) {
            relativeUrl
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
        // Priority 1: Check for explicit article container with class "go-article" (Golem)
        val golemArticle = extractTagWithClass(sanitizedHtml, "article", "go-article")
        if (golemArticle != null && countParagraphs(golemArticle) > 0) {
            return golemArticle
        }

        // Priority 2: Check top-level <article> tags using depth matching
        val topArticles = extractTopLevelTags(sanitizedHtml, "article")
        var bestArticle: String? = null
        var maxPCount = 0
        for (art in topArticles) {
            val pCount = countParagraphs(art)
            if (pCount > maxPCount) {
                maxPCount = pCount
                bestArticle = art
            }
        }
        if (bestArticle != null && maxPCount > 0) {
            return bestArticle
        }

        // Priority 3: Check <main> tags
        val mainTags = extractTopLevelTags(sanitizedHtml, "main")
        for (m in mainTags) {
            val pCount = countParagraphs(m)
            if (pCount > maxPCount) {
                maxPCount = pCount
                bestArticle = m
            }
        }
        if (bestArticle != null && maxPCount > 0) {
            return bestArticle
        }

        return sanitizedHtml
    }

    private fun extractTopLevelTags(html: String, tagName: String): List<String> {
        val results = mutableListOf<String>()
        val openPattern = Pattern.compile("(?i)<$tagName\\b[^>]*>")
        val closeTag = "</$tagName>"
        val matcher = openPattern.matcher(html)
        var searchPos = 0

        while (matcher.find(searchPos)) {
            val openTagEnd = matcher.end()
            var depth = 1
            var curr = openTagEnd

            while (curr < html.length && depth > 0) {
                val nextOpen = indexOfTag(html, "<$tagName", curr)
                val nextClose = html.indexOf(closeTag, curr, ignoreCase = true)
                if (nextClose == -1) break
                if (nextOpen != -1 && nextOpen < nextClose) {
                    depth++
                    curr = nextOpen + tagName.length + 1
                } else {
                    depth--
                    curr = nextClose + closeTag.length
                }
            }

            if (depth == 0) {
                val innerContent = html.substring(openTagEnd, curr - closeTag.length)
                results.add(innerContent)
                searchPos = curr
            } else {
                searchPos = openTagEnd
            }
        }
        return results
    }

    private fun extractTagWithClass(html: String, tagName: String, className: String): String? {
        val pattern = Pattern.compile("(?i)<$tagName\\b[^>]*class=[\"'][^\"']*$className[^\"']*[\"'][^>]*>")
        val matcher = pattern.matcher(html)
        if (!matcher.find()) return null

        val openTagEnd = matcher.end()
        var depth = 1
        var curr = openTagEnd
        val closeTag = "</$tagName>"

        while (curr < html.length && depth > 0) {
            val nextOpen = indexOfTag(html, "<$tagName", curr)
            val nextClose = html.indexOf(closeTag, curr, ignoreCase = true)
            if (nextClose == -1) break
            if (nextOpen != -1 && nextOpen < nextClose) {
                depth++
                curr = nextOpen + tagName.length + 1
            } else {
                depth--
                curr = nextClose + closeTag.length
            }
        }

        return if (depth == 0) {
            html.substring(openTagEnd, curr - closeTag.length)
        } else {
            null
        }
    }

    private fun indexOfTag(html: String, prefix: String, fromIndex: Int): Int {
        var idx = fromIndex
        val len = html.length
        val prefixLen = prefix.length
        while (idx < len) {
            val found = html.indexOf(prefix, idx, ignoreCase = true)
            if (found == -1) return -1
            val after = found + prefixLen
            if (after >= len || html[after] == ' ' || html[after] == '>' || html[after] == '\t' || html[after] == '\n' || html[after] == '\r') {
                return found
            }
            idx = found + 1
        }
        return -1
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
                lower.contains("artikel merken") ||
                lower.contains("relevante themen") ||
                lower.contains("passend zum thema") ||
                lower.contains("mehr zum thema") ||
                lower.contains("quellenangabe")
    }
}
