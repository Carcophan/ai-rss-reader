package de.carcophan.ai_rss

import de.carcophan.ai_rss.data.repository.ArticleWebExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleWebExtractorTest {

    @Test
    fun extractFullArticle_golemArticle_extractsActualContentNotConsentWall() = runBlocking {
        val golemUrl = "https://www.golem.de/news/gefaelschte-stimme-bank-chef-ueberwies-wegen-ki-scam-95-millionen-euro-2609-213473.html"
        val result = ArticleWebExtractor.extractFullArticle(golemUrl)

        assertNotNull("Result should not be null", result)
        val text = result!!.fullText
        assertTrue("Text should have reasonable length", text.length > 300)

        // Verify that consent wall boilerplate is NOT present
        assertFalse("Should not contain consent hint", text.contains("Skript wurde nicht geladen", ignoreCase = true))
        assertFalse("Should not contain Pur Abo hint", text.contains("Golem pur", ignoreCase = true))
        assertFalse("Should not contain Cookies zustimmen", text.contains("Cookies zustimmen", ignoreCase = true))

        // Verify that actual article content is present
        assertTrue("Should contain article content about scam / bank", text.contains("Bank", ignoreCase = true))
    }
}
