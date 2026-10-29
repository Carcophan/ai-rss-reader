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
        assertTrue("Text should have reasonable length", text.length > 1000)

        // Verify that consent wall boilerplate is NOT present
        assertFalse("Should not contain consent hint", text.contains("Skript wurde nicht geladen", ignoreCase = true))
        assertFalse("Should not contain Pur Abo hint", text.contains("Golem pur", ignoreCase = true))
        assertFalse("Should not contain Cookies zustimmen", text.contains("Cookies zustimmen", ignoreCase = true))

        // Verify that actual article content is present
        assertTrue("Should contain article content about scam / bank", text.contains("Bank", ignoreCase = true))
    }

    @Test
    fun extractFullArticle_golemMultiPageArticle_extractsAllPages() = runBlocking {
        val multiPageGolemUrl = "https://www.golem.de/news/quartalsergebnis-samsungs-rekordgewinn-laesst-investoren-kalt-2610-214640.html"
        val result = ArticleWebExtractor.extractFullArticle(multiPageGolemUrl)

        assertNotNull("Result should not be null", result)
        val text = result!!.fullText
        assertTrue("Multi-page text should be substantial", text.length > 2500)

        // Verify content from Page 1
        assertTrue("Should contain content from page 1", text.contains("SK Hynix", ignoreCase = true))
        assertTrue("Should contain subheadings from page 1", text.contains("Speicher bleibt knapp", ignoreCase = true))

        // Verify content from Page 2
        assertTrue("Should contain content from page 2", text.contains("TSMC", ignoreCase = true))
        assertTrue("Should contain page 2 section title", text.contains("Engpässe bei Logik und steigende Kosten", ignoreCase = true))

        // Verify that boilerplate is excluded
        assertFalse("Should not contain consent hint", text.contains("Skript wurde nicht geladen", ignoreCase = true))
        assertFalse("Should not contain Pur Abo hint", text.contains("Golem pur", ignoreCase = true))
        assertFalse("Should not contain Career / Seminar teaser", text.contains("Karrierewelt", ignoreCase = true))
    }
}
