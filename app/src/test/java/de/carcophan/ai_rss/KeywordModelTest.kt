package de.carcophan.ai_rss

import de.carcophan.ai_rss.data.model.Keyword
import de.carcophan.ai_rss.data.model.KeywordMatch
import de.carcophan.ai_rss.data.repository.KeywordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordModelTest {

    @Test
    fun keywordCreation_generatesIdAndPreservesText() {
        val keyword = Keyword(text = "Künstliche Intelligenz")
        assertNotNull(keyword.id)
        assertTrue(keyword.id.isNotBlank())
        assertEquals("Künstliche Intelligenz", keyword.text)
        assertTrue(keyword.createdAt > 0)
    }

    @Test
    fun keywordMatch_storesRelevanceAndReason() {
        val match = KeywordMatch(
            articleId = "art-123",
            isRelevant = true,
            reason = "Relevanter KI-Artikel",
            relevanceScore = 9
        )
        assertEquals("art-123", match.articleId)
        assertTrue(match.isRelevant)
        assertEquals("Relevanter KI-Artikel", match.reason)
        assertEquals(9, match.relevanceScore)
    }

    @Test
    fun defaultKeywords_containsCuratedTopics() {
        val defaults = KeywordRepository.DEFAULT_KEYWORDS
        assertTrue(defaults.contains("Künstliche Intelligenz"))
        assertTrue(defaults.contains("Klimawandel"))
        assertTrue(defaults.contains("Wirtschaft"))
        assertFalse(defaults.isEmpty())
    }
}
