package de.carcophan.ai_rss.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("ai_rss_gemini_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model_name"
        private const val PREFS_CACHE = "ai_rss_summary_cache"

        const val DEFAULT_MODEL = "gemini-3.8-flash"
        val POPULAR_MODELS = listOf(
            "gemini-3.8-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-2.5-flash"
        )
    }

    private val cachePrefs = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getModel(): String {
        return prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }

    fun setModel(model: String) {
        prefs.edit().putString(KEY_MODEL, model.trim()).apply()
    }

    fun hasApiKey(): Boolean {
        return getApiKey().isNotBlank()
    }

    fun getCachedSummary(articleId: String): String? {
        return cachePrefs.getString(articleId, null)
    }

    fun saveCachedSummary(articleId: String, summary: String) {
        cachePrefs.edit().putString(articleId, summary).apply()
    }

    fun clearCache() {
        cachePrefs.edit().clear().apply()
    }

    /**
     * Test connection to Gemini API with current or provided API key and model.
     */
    suspend fun testConnection(testApiKey: String? = null, testModel: String? = null): Result<String> = withContext(Dispatchers.IO) {
        val key = (testApiKey ?: getApiKey()).trim()
        val model = (testModel ?: getModel()).trim()

        if (key.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Bitte gib einen Gemini API-Key ein."))
        }

        try {
            val response = executeGeminiRequest(
                apiKey = key,
                model = model,
                systemInstruction = "Antworte mit genau einem Wort: 'OK'.",
                userPrompt = "Testverbindung"
            )
            Result.success(response.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Summarize full article text using Gemini API.
     */
    suspend fun summarizeArticle(
        title: String,
        fullArticleText: String,
        sourceName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Kein Gemini API-Key hinterlegt. Bitte trage deinen API-Key in den Einstellungen ein."))
        }

        val model = getModel()

        // Truncate if article is excessively large (e.g. over 80k characters) to keep within reasonable limits
        val contentToSend = if (fullArticleText.length > 80000) {
            fullArticleText.take(80000) + "\n\n[... Text gekürzt ...]"
        } else {
            fullArticleText
        }

        val systemPrompt = """
            Du bist ein hochqualifizierter journalistischer Assistent. Deine Aufgabe ist es, den vollständigen Text des bereitgestellten Artikels gründlich zu analysieren und eine strukturierte, prägnante Zusammenfassung auf Deutsch zu erstellen.
            
            Formatierungsrichtlinien:
            - Verwende klare Markdown-Abschnitte.
            - 📌 **Kernbotschaft**: 1 bis 2 prägnante Sätze, die das Wesentliche auf den Punkt bringen.
            - 🔍 **Wichtigste Punkte**: 3 bis 6 gut lesbare Aufzählungspunkte (Bullet Points) mit den zentralen Fakten, Hintergründen und Entwicklungen.
            - 💡 **Fazit & Einordnung**: Ein kurzer abschließender Satz zur Bedeutung oder Tragweite des Themas.
            - Verwende keine unnötigen Floskeln wie "In diesem Artikel geht es um...".
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Titel: $title")
            if (sourceName.isNotBlank()) appendLine("Quelle: $sourceName")
            appendLine("\nVollständiger Artikeltext:")
            appendLine(contentToSend)
            appendLine("\nBitte erstelle die strukturierte deutsche Zusammenfassung des vollständigen Artikels.")
        }

        try {
            val summary = executeGeminiRequest(apiKey, model, systemPrompt, userPrompt)
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGeminiRequest(
        apiKey: String,
        model: String,
        systemInstruction: String?,
        userPrompt: String
    ): String {
        val cleanModel = model.trim().removePrefix("models/")
        val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        val rootJson = JSONObject().apply {
            // Optional system instruction
            if (!systemInstruction.isNullOrBlank()) {
                val sysPart = JSONObject().put("text", systemInstruction)
                val sysParts = JSONArray().put(sysPart)
                put("systemInstruction", JSONObject().put("parts", sysParts))
            }

            // User content
            val contentsArray = JSONArray()
            val userContent = JSONObject().apply {
                put("role", "user")
                val partsArray = JSONArray().apply {
                    put(JSONObject().put("text", userPrompt))
                }
                put("parts", partsArray)
            }
            contentsArray.put(userContent)
            put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 2048)
            }
            put("generationConfig", genConfig)
        }

        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(rootJson.toString())
                writer.flush()
            }

            val statusCode = conn.responseCode
            val responseStream = if (statusCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream
            }

            val responseText = responseStream?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            } ?: ""

            if (statusCode !in 200..299) {
                val errorMessage = parseErrorMessage(responseText, statusCode, model)
                throw java.io.IOException(errorMessage)
            }

            return parseSuccessResponse(responseText)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseSuccessResponse(jsonString: String): String {
        val obj = JSONObject(jsonString)
        val candidates = obj.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw java.io.IOException("Keine Antwort von Gemini erhalten.")
        }

        val firstCandidate = candidates.getJSONObject(0)
        val content = firstCandidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")

        if (parts == null || parts.length() == 0) {
            throw java.io.IOException("Leere Antwort von Gemini erhalten.")
        }

        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val partObj = parts.getJSONObject(i)
            val text = partObj.optString("text", "")
            sb.append(text)
        }

        return sb.toString().trim()
    }

    private fun parseErrorMessage(jsonString: String, statusCode: Int, model: String): String {
        return try {
            val obj = JSONObject(jsonString)
            val errorObj = obj.optJSONObject("error")
            val msg = errorObj?.optString("message") ?: "HTTP-Status $statusCode"

            when (statusCode) {
                400 -> {
                    if (msg.contains("API key not valid", ignoreCase = true)) {
                        "Der eingegebene Gemini API-Key ist ungültig. Bitte überprüfe deinen Key."
                    } else {
                        "Fehlerhafte Anfrage an Gemini: $msg"
                    }
                }
                404 -> {
                    "Das Modell '$model' wurde nicht gefunden. Versuche z.B. 'gemini-2.0-flash' oder 'gemini-1.5-flash'."
                }
                429 -> {
                    "Gemini API-Kontingent überschritten (Rate Limit / Quota). Bitte versuche es in Kürze erneut."
                }
                403 -> {
                    "Zugriff verweigert (403): Prüfe die Berechtigungen deines API-Keys oder das ausgewählte Modell."
                }
                else -> {
                    "Gemini-Fehler ($statusCode): $msg"
                }
            }
        } catch (_: Exception) {
            "Fehler bei Verbindung mit Gemini (HTTP $statusCode)."
        }
    }
}
