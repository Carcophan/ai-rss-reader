package de.carcophan.ai_rss.data.repository

import android.content.Context
import de.carcophan.ai_rss.data.model.KeywordMatch
import de.carcophan.ai_rss.data.model.RssItem
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

        fun parseKeywordMatchesJson(
            jsonString: String,
            articles: List<RssItem> = emptyList()
        ): List<KeywordMatch> {
            val cleanJson = jsonString.trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonArray = try {
                if (cleanJson.startsWith("[")) {
                    JSONArray(cleanJson)
                } else {
                    val start = cleanJson.indexOf('[')
                    val end = cleanJson.lastIndexOf(']')
                    if (start != -1 && end != -1 && end > start) {
                        JSONArray(cleanJson.substring(start, end + 1))
                    } else {
                        JSONArray()
                    }
                }
            } catch (_: Exception) {
                JSONArray()
            }

            val idToArticle = articles.associateBy { it.id }
            val list = mutableListOf<KeywordMatch>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val reason = obj.optString("reason", "")
                val score = obj.optInt("relevanceScore", obj.optInt("score", 7))
                val relevant = obj.optBoolean("relevant", true)

                // Resolve target article
                var targetArticle: RssItem? = null

                // 1. Try numeric index
                val rawIndex = when {
                    obj.has("index") -> obj.optInt("index", -1)
                    obj.has("articleNumber") -> obj.optInt("articleNumber", -1)
                    obj.optInt("id", -1) > 0 -> obj.optInt("id", -1)
                    else -> -1
                }

                if (rawIndex in 1..articles.size) {
                    targetArticle = articles[rawIndex - 1]
                }

                // 2. Try string id
                if (targetArticle == null && obj.has("id")) {
                    val idStr = obj.optString("id", "").trim()
                    if (idStr.isNotBlank()) {
                        targetArticle = idToArticle[idStr]
                        if (targetArticle == null) {
                            val digits = idStr.filter { it.isDigit() }.toIntOrNull()
                            if (digits != null && digits in 1..articles.size) {
                                targetArticle = articles[digits - 1]
                            }
                        }
                    }
                }

                if (targetArticle != null) {
                    list.add(
                        KeywordMatch(
                            articleId = targetArticle.id,
                            isRelevant = relevant,
                            reason = if (relevant) reason.ifBlank { "Passend zum Thema" } else reason,
                            relevanceScore = score
                        )
                    )
                } else if (articles.isEmpty()) {
                    val fallbackId = obj.optString("id", "").ifBlank { "item-${i + 1}" }
                    list.add(
                        KeywordMatch(
                            articleId = fallbackId,
                            isRelevant = relevant,
                            reason = if (relevant) reason.ifBlank { "Passend zum Thema" } else reason,
                            relevanceScore = score
                        )
                    )
                }
            }
            return list
        }
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

    fun getCachedBriefing(key: String): String? {
        return cachePrefs.getString("briefing_$key", null)
    }

    fun saveCachedBriefing(key: String, briefing: String) {
        cachePrefs.edit().putString("briefing_$key", briefing).apply()
    }

    fun getCachedKeywordMatches(cacheKey: String): Map<String, KeywordMatch>? {
        val jsonString = cachePrefs.getString("kw_matches_$cacheKey", null) ?: return null
        return try {
            val array = JSONArray(jsonString)
            val map = mutableMapOf<String, KeywordMatch>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                map[id] = KeywordMatch(
                    articleId = id,
                    isRelevant = obj.getBoolean("relevant"),
                    reason = obj.optString("reason", ""),
                    relevanceScore = obj.optInt("score", 0)
                )
            }
            map
        } catch (_: Exception) {
            null
        }
    }

    fun saveCachedKeywordMatches(cacheKey: String, matches: List<KeywordMatch>) {
        val existing = getCachedKeywordMatches(cacheKey)?.toMutableMap() ?: mutableMapOf()
        matches.forEach { existing[it.articleId] = it }
        val array = JSONArray()
        for ((_, match) in existing) {
            val obj = JSONObject().apply {
                put("id", match.articleId)
                put("relevant", match.isRelevant)
                put("reason", match.reason)
                put("score", match.relevanceScore)
            }
            array.put(obj)
        }
        cachePrefs.edit().putString("kw_matches_$cacheKey", array.toString()).apply()
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

    /**
     * Generate a structured Daily Briefing from a list of recent articles using Gemini.
     */
    suspend fun generateDailyBriefing(
        feedTitle: String,
        articles: List<RssItem>
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Kein Gemini API-Key hinterlegt. Bitte trage deinen API-Key in den Einstellungen ein."))
        }

        if (articles.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Keine Artikel vorhanden, um ein Briefing zu erstellen."))
        }

        val model = getModel()
        val topArticles = articles.take(20)

        val systemPrompt = """
            Du bist ein professioneller Nachrichten-Redakteur und Moderator. Deine Aufgabe ist es, aus den wichtigsten aktuellen Meldungen ein flüssiges, hochinformatives und klar strukturiertes "Daily Briefing" (Tagesüberblick) auf Deutsch zu verfassen.

            Formatierungsrichtlinien (nutze sauberes Markdown):
            - Starte mit einer freundlichen, motivierenden Begrüßung ("☀️ Guten Tag" o. Ä.) und einem prägnanten 1- bis 2-Satz-Überblick über das heutige Tagesgeschehen.
            - Strukturiere das Briefing thematisch (z. B. 🌍 **Politik & Weltgeschehen**, 💻 **Technologie & Digitales**, 📈 **Wirtschaft & Finanzen**, 🔬 **Wissen & Umwelt** – passe die Kategorien dynamisch an die Meldungen an).
            - Führe pro Kategorie 2 bis 4 der relevantesten Punkte als gut lesbare Aufzählungspunkte (Bullet Points) mit den wichtigsten Fakten auf.
            - Nenne bei relevanten Punkten kurz die Quelle in eckigen Klammern (z. B. "[Tagesschau]", "[Heise Online]").
            - Schließe mit einem inspirierenden "💡 **Gedanke des Tages / Ausblick**" ab.
            - Vermeide Meta-Floskeln wie "In diesem Briefing fassen wir zusammen". Schreibe lebendig, lesefreundlich und auf den Punkt.
        """.trimIndent()

        val articlesText = buildString {
            topArticles.forEachIndexed { index, item ->
                appendLine("${index + 1}. [${item.feedTitle}] ${item.title}")
                val snippet = (item.description.ifBlank { item.content }).take(300).replace("\n", " ").trim()
                if (snippet.isNotBlank()) {
                    appendLine("   Auszug: $snippet")
                }
                if (item.pubDate.isNotBlank()) {
                    appendLine("   Datum: ${item.pubDate}")
                }
                appendLine()
            }
        }

        val userPrompt = buildString {
            appendLine("Feed-Kontext: $feedTitle")
            appendLine("Anzahl der aktuellen Meldungen: ${topArticles.size}")
            appendLine("\nAktuelle Schlagzeilen & Inhalte:\n")
            appendLine(articlesText)
            appendLine("\nBitte erstelle das strukturierte deutsche Daily Briefing für den heutigen Tag basierend auf diesen Meldungen.")
        }

        try {
            val response = executeGeminiRequest(apiKey, model, systemPrompt, userPrompt)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Categorize and filter articles for a given keyword using Gemini API.
     */
    suspend fun classifyArticlesByKeyword(
        keyword: String,
        articles: List<RssItem>,
        forceRefresh: Boolean = false
    ): Result<List<KeywordMatch>> = withContext(Dispatchers.IO) {
        val cleanKeyword = keyword.trim()
        if (cleanKeyword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Schlagwort darf nicht leer sein."))
        }

        if (articles.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val cacheKey = cleanKeyword.lowercase()
        if (!forceRefresh) {
            val cachedMatches = getCachedKeywordMatches(cacheKey)
            if (cachedMatches != null && cachedMatches.isNotEmpty()) {
                val cachedList = articles.mapNotNull { cachedMatches[it.id] }
                if (cachedList.size == articles.size) {
                    return@withContext Result.success(cachedList)
                }
            }
        }

        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("MISSING_API_KEY"))
        }

        val model = getModel()
        // Send up to 50 recent articles to classify
        val articlesToSend = articles.take(50)

        val systemPrompt = """
            Du bist ein präziser semantischer Nachrichten-Klassifizierer. Deine Aufgabe ist es, aus den übergebenen nummerierten Nachrichtenartikeln diejenigen herauszufiltern, die inhaltlich oder thematisch zum gesuchten Schlagwort passen.
            
            Kriterien für Relevanz:
            - Der Artikel behandelt das Thema, ein direkt verwandtes Teilgebiet, Akteure, Technologien oder wichtige Ereignisse (z. B. gehören Artikel über "ChatGPT", "Nvidia-Chips", "Machine Learning", "LLMs" zum Thema "Künstliche Intelligenz" oder "KI"; Artikel über "Photovoltaik", "Wärmepumpen", "CO2-Ziele" zu "Klimawandel").
            - Beachte Synonyme, Abkürzungen und englische Fachbegriffe.
            - Nimm nur Artikel auf, die einen echten thematischen Bezug haben.
            
            Antworte STRENG als valides JSON-Array, das NUR die passenden Artikel enthält. Verwende für 'index' die Nummer in den eckigen Klammern [1], [2], ...:
            [
              {
                "index": 1,
                "reason": "1 kurzer prägnanter Satz auf Deutsch, warum dieser Artikel zum Thema passt",
                "relevanceScore": 8
              }
            ]
            Falls kein Artikel zum Schlagwort passt, antworte mit: []
        """.trimIndent()

        val userPrompt = buildString {
            appendLine("Gesuchtes Schlagwort: \"$cleanKeyword\"")
            appendLine("Anzahl der Artikel: ${articlesToSend.size}")
            appendLine("\nArtikelliste:\n")
            articlesToSend.forEachIndexed { index, item ->
                val num = index + 1
                appendLine("[$num] [${item.feedTitle}] ${item.title}")
                val snippet = (item.description.ifBlank { item.content }).take(200).replace("\n", " ").trim()
                if (snippet.isNotBlank()) {
                    appendLine("Auszug: $snippet")
                }
                appendLine()
            }
            appendLine("Bewerte die Artikel und gib das JSON-Array der relevanten Treffer zurück.")
        }

        try {
            val response = executeGeminiRequest(apiKey, model, systemPrompt, userPrompt)
            val matches = parseKeywordMatchesJson(response, articlesToSend)

            // Cache matches
            saveCachedKeywordMatches(cacheKey, matches)

            Result.success(matches)
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
                put("temperature", 0.2)
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
