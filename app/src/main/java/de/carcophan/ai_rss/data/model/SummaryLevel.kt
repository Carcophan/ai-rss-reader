package de.carcophan.ai_rss.data.model

enum class SummaryLevel(
    val id: String,
    val title: String,
    val shortLabel: String,
    val description: String
) {
    COMPACT(
        id = "compact",
        title = "Kompakt (TL;DR)",
        shortLabel = "Kompakt",
        description = "Schnellster Überblick: 1 Kernsatz und 2–3 kurze Stichpunkte."
    ),
    BALANCED(
        id = "balanced",
        title = "Ausgewogen (Standard)",
        shortLabel = "Ausgewogen",
        description = "Kernbotschaft, wichtigste Fakten und abschließendes Fazit."
    ),
    DETAILED(
        id = "detailed",
        title = "Ausführlich (Deep Dive)",
        shortLabel = "Ausführlich",
        description = "Tiefgehende Analyse mit Hintergründen, Akteuren und Ausblick."
    );

    companion object {
        val DEFAULT = BALANCED

        fun fromId(id: String?): SummaryLevel {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
