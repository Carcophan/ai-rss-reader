package de.carcophan.ai_rss.data.repository

import android.content.Context
import de.carcophan.ai_rss.data.model.Keyword
import org.json.JSONArray
import org.json.JSONObject

class KeywordRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("ai_rss_keywords_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SAVED_KEYWORDS = "saved_keywords"

        val DEFAULT_KEYWORDS = listOf(
            "Künstliche Intelligenz",
            "Klimawandel",
            "Wirtschaft",
            "Cybersicherheit",
            "Raumfahrt"
        )
    }

    fun getKeywords(): List<Keyword> {
        val jsonString = prefs.getString(KEY_SAVED_KEYWORDS, null)
        if (jsonString.isNullOrBlank()) {
            val defaults = DEFAULT_KEYWORDS.take(3).map { Keyword(text = it) }
            saveKeywords(defaults)
            return defaults
        }

        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<Keyword>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    Keyword(
                        id = obj.optString("id"),
                        text = obj.optString("text"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveKeywords(keywords: List<Keyword>) {
        val jsonArray = JSONArray()
        for (kw in keywords) {
            val obj = JSONObject().apply {
                put("id", kw.id)
                put("text", kw.text)
                put("createdAt", kw.createdAt)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_KEYWORDS, jsonArray.toString()).apply()
    }

    fun addKeyword(rawText: String): Result<Keyword> {
        val cleanText = rawText.trim()
        if (cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("Das Schlagwort darf nicht leer sein."))
        }

        val current = getKeywords().toMutableList()
        if (current.any { it.text.equals(cleanText, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Dieses Schlagwort existiert bereits."))
        }

        val newKeyword = Keyword(text = cleanText)
        current.add(newKeyword)
        saveKeywords(current)
        return Result.success(newKeyword)
    }

    fun deleteKeyword(keywordId: String): List<Keyword> {
        val updated = getKeywords().filterNot { it.id == keywordId }
        saveKeywords(updated)
        return updated
    }
}
