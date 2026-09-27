package de.carcophan.ai_rss.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val primaryColor = MaterialTheme.colorScheme.primary

    val lines = remember(markdown) {
        markdown.lines()
    }

    Column(modifier = modifier) {
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()

        lines.forEachIndexed { _, rawLine ->
            val trimmed = rawLine.trim()

            // Handle code block fences (```)
            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End of code block
                    CodeBlockCard(code = codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                return@forEachIndexed
            }

            if (inCodeBlock) {
                codeBlockLines.add(rawLine)
                return@forEachIndexed
            }

            // Empty line -> spacing
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                return@forEachIndexed
            }

            // Horizontal divider (--- or ***)
            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                return@forEachIndexed
            }

            // Headings (#, ##, ###, ####)
            val headingLevel = countHeadingLevel(trimmed)
            if (headingLevel > 0) {
                val headingContent = trimmed.drop(headingLevel).trim()
                val annotatedHeading = parseInlineMarkdown(headingContent, codeBackgroundColor)

                val headingStyle = when (headingLevel) {
                    1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                }

                Text(
                    text = annotatedHeading,
                    style = headingStyle,
                    color = primaryColor,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
                return@forEachIndexed
            }

            // Blockquote (> Quote)
            if (trimmed.startsWith(">")) {
                val quoteContent = trimmed.removePrefix(">").trim()
                val annotatedQuote = parseInlineMarkdown(quoteContent, codeBackgroundColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(primaryColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = annotatedQuote,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = textColor.copy(alpha = 0.85f),
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3
                    )
                }
                return@forEachIndexed
            }

            // Bullet list item (*, -, +, •)
            val isBullet = trimmed.startsWith("- ") ||
                    trimmed.startsWith("* ") ||
                    trimmed.startsWith("+ ") ||
                    trimmed.startsWith("• ")

            if (isBullet) {
                val bulletContent = trimmed.drop(2).trim()
                val annotatedBullet = parseInlineMarkdown(bulletContent, codeBackgroundColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor,
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                    )
                    Text(
                        text = annotatedBullet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3,
                        modifier = Modifier.weight(1f)
                    )
                }
                return@forEachIndexed
            }

            // Numbered list item (e.g. "1. Item")
            val numberMatcher = NUMBER_LIST_PATTERN.matcher(trimmed)
            if (numberMatcher.matches()) {
                val number = numberMatcher.group(1) ?: "1"
                val itemContent = numberMatcher.group(2) ?: ""
                val annotatedItem = parseInlineMarkdown(itemContent, codeBackgroundColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "$number.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor,
                        modifier = Modifier
                            .width(24.dp)
                            .padding(end = 4.dp)
                    )
                    Text(
                        text = annotatedItem,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor,
                        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.3,
                        modifier = Modifier.weight(1f)
                    )
                }
                return@forEachIndexed
            }

            // Regular paragraph line
            val annotatedParagraph = parseInlineMarkdown(trimmed, codeBackgroundColor)
            Text(
                text = annotatedParagraph,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.35,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // Catch unclosed code block if present
        if (inCodeBlock && codeBlockLines.isNotEmpty()) {
            CodeBlockCard(code = codeBlockLines.joinToString("\n"))
        }
    }
}

@Composable
private fun CodeBlockCard(code: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp)
        )
    }
}

private fun countHeadingLevel(line: String): Int {
    var count = 0
    while (count < line.length && line[count] == '#') {
        count++
    }
    return if (count in 1..4 && count < line.length && line[count] == ' ') {
        count
    } else {
        0
    }
}

private val NUMBER_LIST_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.+)$")

// Pattern matches:
// Group 1,2: ***BoldItalic***
// Group 3,4: **Bold**
// Group 5,6: *Italic*
// Group 7,8: `InlineCode`
// Group 9,10: ~~Strikethrough~~
private val INLINE_PATTERN = Pattern.compile(
    "(\\*{3}(.+?)\\*{3})|(\\*{2}(.+?)\\*{2})|(\\*(.+?)\\*)|(`([^`]+)`)|(~~(.+?)~~)"
)

fun parseInlineMarkdown(text: String, codeBackgroundColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val matcher = INLINE_PATTERN.matcher(text)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Append plain text before match
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }

            when {
                // ***BoldItalic***
                matcher.group(1) != null -> {
                    val content = matcher.group(2) ?: ""
                    val span = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    val sStart = length
                    append(content)
                    addStyle(span, sStart, length)
                }

                // **Bold**
                matcher.group(3) != null -> {
                    val content = matcher.group(4) ?: ""
                    val span = SpanStyle(fontWeight = FontWeight.Bold)
                    val sStart = length
                    append(content)
                    addStyle(span, sStart, length)
                }

                // *Italic*
                matcher.group(5) != null -> {
                    val content = matcher.group(6) ?: ""
                    val span = SpanStyle(fontStyle = FontStyle.Italic)
                    val sStart = length
                    append(content)
                    addStyle(span, sStart, length)
                }

                // `Code`
                matcher.group(7) != null -> {
                    val content = matcher.group(8) ?: ""
                    val span = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackgroundColor
                    )
                    val sStart = length
                    append(" $content ")
                    addStyle(span, sStart, length)
                }

                // ~~Strikethrough~~
                matcher.group(9) != null -> {
                    val content = matcher.group(10) ?: ""
                    val span = SpanStyle(textDecoration = TextDecoration.LineThrough)
                    val sStart = length
                    append(content)
                    addStyle(span, sStart, length)
                }
            }

            lastIndex = end
        }

        // Append remaining plain text
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}
