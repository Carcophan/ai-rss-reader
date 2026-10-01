package de.carcophan.ai_rss

import de.carcophan.ai_rss.data.repository.GeminiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiKeywordClassificationTest {

    @Test
    fun parseKeywordMatchesJson_validJson_parsesCorrectly() {
        val json = """
            [
              {
                "id": "item-1",
                "relevant": true,
                "reason": "Behandelt Fortschritte bei generativer KI und LLMs",
                "relevanceScore": 9
              },
              {
                "id": "item-2",
                "relevant": false,
                "reason": "",
                "relevanceScore": 1
              }
            ]
        """.trimIndent()

        val matches = GeminiRepository.parseKeywordMatchesJson(json)
        assertEquals(2, matches.size)

        val match1 = matches[0]
        assertEquals("item-1", match1.articleId)
        assertTrue(match1.isRelevant)
        assertEquals("Behandelt Fortschritte bei generativer KI und LLMs", match1.reason)
        assertEquals(9, match1.relevanceScore)

        val match2 = matches[1]
        assertEquals("item-2", match2.articleId)
        assertFalse(match2.isRelevant)
        assertEquals(1, match2.relevanceScore)
    }

    @Test
    fun parseKeywordMatchesJson_markdownWrappedJson_parsesCorrectly() {
        val markdownJson = """
            ```json
            [
              {
                "id": "item-ai-3",
                "relevant": true,
                "reason": "Berichtet über Nvidia AI Chips",
                "relevanceScore": 8
              }
            ]
            ```
        """.trimIndent()

        val matches = GeminiRepository.parseKeywordMatchesJson(markdownJson)
        assertEquals(1, matches.size)
        assertEquals("item-ai-3", matches[0].articleId)
        assertTrue(matches[0].isRelevant)
        assertEquals("Berichtet über Nvidia AI Chips", matches[0].reason)
        assertEquals(8, matches[0].relevanceScore)
    }

    @Test
    fun parseKeywordMatchesJson_withSurroundingText_extractsArraySafely() {
        val textWithJson = """
            Hier ist die Klassifizierung der Artikel:
            [
              {
                "id": "item-4",
                "relevant": true,
                "reason": "Neue EU-Regulierung zu KI-Systemen",
                "relevanceScore": 10
              }
            ]
            Hoffe das hilft!
        """.trimIndent()

        val matches = GeminiRepository.parseKeywordMatchesJson(textWithJson)
        assertEquals(1, matches.size)
        assertEquals("item-4", matches[0].articleId)
        assertTrue(matches[0].isRelevant)
    }

    @Test
    fun parseKeywordMatchesJson_indexBased_resolvesExactArticleId() {
        val articles = listOf(
            de.carcophan.ai_rss.data.model.RssItem(
                id = "feed1_uuid_hash123",
                feedId = "feed1",
                feedTitle = "Tagesschau",
                title = "OpenAI stellt neues Modell vor",
                link = "https://tagesschau.de/ki",
                description = "Text",
                pubDate = "Heute"
            ),
            de.carcophan.ai_rss.data.model.RssItem(
                id = "feed2_uuid_hash456",
                feedId = "feed2",
                feedTitle = "Spiegel",
                title = "Wetterbericht für das Wochenende",
                link = "https://spiegel.de/wetter",
                description = "Sonne",
                pubDate = "Heute"
            ),
            de.carcophan.ai_rss.data.model.RssItem(
                id = "feed3_uuid_hash789",
                feedId = "feed3",
                feedTitle = "Heise",
                title = "Nvidia KI-Beschleuniger auf der Messe",
                link = "https://heise.de/nvidia",
                description = "Hardware",
                pubDate = "Heute"
            )
        )

        // Gemini returns relevant items with index 1 and 3
        val json = """
            [
              {
                "index": 1,
                "reason": "Behandelt OpenAI und neue KI-Modelle",
                "relevanceScore": 10
              },
              {
                "index": 3,
                "reason": "Berichtet über Nvidia Hardware für KI",
                "relevanceScore": 9
              }
            ]
        """.trimIndent()

        val matches = GeminiRepository.parseKeywordMatchesJson(json, articles)
        assertEquals(2, matches.size)

        assertEquals("feed1_uuid_hash123", matches[0].articleId)
        assertTrue(matches[0].isRelevant)
        assertEquals("Behandelt OpenAI und neue KI-Modelle", matches[0].reason)

        assertEquals("feed3_uuid_hash789", matches[1].articleId)
        assertTrue(matches[1].isRelevant)
        assertEquals("Berichtet über Nvidia Hardware für KI", matches[1].reason)
    }

    @Test
    fun parseKeywordMatchesJson_invalidOrEmptyJson_returnsEmptyList() {
        val emptyMatches = GeminiRepository.parseKeywordMatchesJson("")
        assertTrue(emptyMatches.isEmpty())

        val invalidMatches = GeminiRepository.parseKeywordMatchesJson("Das ist kein JSON.")
        assertTrue(invalidMatches.isEmpty())
    }

    @Test
    fun parseKeywordMatchesJson_largeArticleList_mapsCorrectlyAcrossEntireList() {
        val articles = (1..60).map { i ->
            de.carcophan.ai_rss.data.model.RssItem(
                id = "item_id_$i",
                feedId = "feed_$i",
                feedTitle = "Feed $i",
                title = "Titel $i",
                link = "https://example.com/$i",
                description = "Volle RSS-Beschreibung für Artikel $i mit ausführlichem Kontext.",
                pubDate = "Heute"
            )
        }

        val json = """
            [
              {
                "index": 55,
                "reason": "Passt perfekt zu Artikel 55",
                "relevanceScore": 9
              }
            ]
        """.trimIndent()

        val matches = GeminiRepository.parseKeywordMatchesJson(json, articles)
        assertEquals(1, matches.size)
        assertEquals("item_id_55", matches[0].articleId)
        assertTrue(matches[0].isRelevant)
        assertEquals("Passt perfekt zu Artikel 55", matches[0].reason)
    }
}
