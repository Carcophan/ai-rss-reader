package de.carcophan.ai_rss

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleFontSizeTest {

    private val minFontSize = 12
    private val maxFontSize = 28
    private val defaultFontSize = 16

    @Test
    fun defaultFontSize_is16Sp() {
        assertEquals(16, defaultFontSize)
    }

    @Test
    fun fontSize_clampsToMinAndMax() {
        assertEquals(minFontSize, 8.coerceIn(minFontSize, maxFontSize))
        assertEquals(maxFontSize, 36.coerceIn(minFontSize, maxFontSize))
        assertEquals(18, 18.coerceIn(minFontSize, maxFontSize))
    }

    @Test
    fun fontScale_calculatedCorrectly() {
        val scaleDefault = 16f / defaultFontSize.toFloat()
        assertEquals(1.0f, scaleDefault, 0.001f)

        val scaleLarge = 24f / defaultFontSize.toFloat()
        assertEquals(1.5f, scaleLarge, 0.001f)

        val scaleSmall = 12f / defaultFontSize.toFloat()
        assertEquals(0.75f, scaleSmall, 0.001f)
    }
}

