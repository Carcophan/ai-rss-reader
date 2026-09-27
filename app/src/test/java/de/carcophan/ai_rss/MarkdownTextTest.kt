package de.carcophan.ai_rss

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import de.carcophan.ai_rss.ui.components.parseInlineMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MarkdownTextTest {

    @Test
    fun parseInlineMarkdown_handlesBoldText() {
        val input = "Dies ist ein **wichtiger** Punkt."
        val annotated = parseInlineMarkdown(input, Color.LightGray)

        assertEquals("Dies ist ein wichtiger Punkt.", annotated.text)
        val styles = annotated.spanStyles
        assertEquals(1, styles.size)
        assertEquals(FontWeight.Bold, styles[0].item.fontWeight)
        assertEquals(13, styles[0].start)
        assertEquals(22, styles[0].end)
    }

    @Test
    fun parseInlineMarkdown_handlesItalicText() {
        val input = "Dies ist *kursiver* Text."
        val annotated = parseInlineMarkdown(input, Color.LightGray)

        assertEquals("Dies ist kursiver Text.", annotated.text)
        val styles = annotated.spanStyles
        assertEquals(1, styles.size)
        assertEquals(FontStyle.Italic, styles[0].item.fontStyle)
    }

    @Test
    fun parseInlineMarkdown_handlesMultipleElements() {
        val input = "📌 **Kernbotschaft:** Das ist `Code` und *Hervorhebung*."
        val annotated = parseInlineMarkdown(input, Color.LightGray)

        assertNotNull(annotated)
        assertEquals(3, annotated.spanStyles.size)
    }
}
