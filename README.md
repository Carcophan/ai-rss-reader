# AI RSS Reader (ai-rss) 📰✨

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.02.01-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Latest-795548?logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![Google Gemini API](https://img.shields.io/badge/Google%20Gemini-Integrated-orange?logo=googlegemini&logoColor=white)](https://ai.google.dev/)
[![Android API](https://img.shields.io/badge/Android-minSdk%2037%20|%20targetSdk%2037-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)

A modern, fast, and intelligent Android RSS reader built entirely with **Jetpack Compose** and **Material 3**, powered by **Google Gemini AI**.

**AI RSS Reader** not only lets you follow your favorite news feeds and blogs in a clean, ad-free interface—it also extracts full article text directly from the web and leverages Gemini to generate concise article summaries and comprehensive **Daily Executive Briefings** (*Tagesüberblick*).

---

## 🌟 Features

### 📡 RSS & Atom Feed Management
- **Universal Feed Support**: Seamlessly parses both **RSS 2.0** and **Atom** feeds with robust XML pull parsing and date handling across various timezones and formats.
- **Default Suggestions**: Pre-configured with top German and international news sources (Tagesschau, Heise Online, SPIEGEL Online, Golem.de, Hacker News) with one-click additions.
- **Custom Feeds**: Add any RSS or Atom URL with custom naming and simple feed management (delete / switch feeds via navigation drawer).
- **Search & Filter**: Real-time article search filtering across titles, summaries, and content.
- **Pull-to-Refresh**: Easily refresh single feeds or all feeds simultaneously with swipe gestures.

### 🤖 Gemini AI Integration
- **Full Article Extraction**: Built-in web scraper (`ArticleWebExtractor`) strips boilerplate, scripts, and navigation to retrieve clean, readable article content (with intelligent cookie/consent wall handling, e.g., Golem.de).
- **Semantische Schlagwort-Filterung & Kategorisierung**: Filtert und kategorisiert Meldungen über alle RSS-Feeds hinweg anhand flexibler Schlagwörter. Gemini erkennt thematische Zusammenhänge über reine Textsuche hinaus und blendet prägnante Relevanz-Begründungen direkt auf den Artikelkarten ein. Schlagwörter können beliebig angelegt, aktiviert und gelöscht werden.
- **Executive Summaries**: One-tap AI summarization generating:
  - 📌 **Key Takeaway** (*Kernbotschaft*): 1–2 punchy sentences summarizing the core message.
  - 🔍 **Key Points** (*Wichtigste Punkte*): 3–6 structured bullet points detailing background and facts.
  - 💡 **Conclusion** (*Fazit & Einordnung*): Contextual perspective on the broader impact.
- **Daily Briefing**: Synthesizes the top 20 recent news items into an executive daily briefing grouped into thematic categories (Politics, Tech, Economy, Science) with source citations and a "Thought of the Day".
- **Flexible Model Selection**: Supports `gemini-3.8-flash` (default) or any custom Gemini model identifier.
- **Offline / Local Caching**: Summaries and daily briefings are cached locally (`SharedPreferences`) to reduce API costs and provide instant reload times.
- **In-App API Key Setup & Live Test**: Easily configure your Google Gemini API key inside the app with immediate connection testing.

### 🎨 Modern Android Architecture & UI
- **100% Jetpack Compose**: Pure Compose UI adopting the latest Material 3 guidelines and dynamic color schemes.
- **Interactive Keyword Filter Chips**: Horizontal scrollable FilterChip bar with one-tap selection, instant deletion, and full keyword management modal.
- **Rich Markdown Rendering**: Custom Compose-native markdown parser for article summaries, supporting bold, italic, code blocks, bullet points, and headers.
- **Coil Image Loading & Caching**: Efficient asynchronous image loading with dedicated 25% RAM memory caching and a 150 MB disk cache for thumbnails and lead images.
- **Edge-to-Edge Design**: Full edge-to-edge support with immersive system bars.

---

## 🏗️ Architecture & Tech Stack

The application follows Google's recommended **MVVM (Model-View-ViewModel)** architectural pattern with unidirectional data flow (UDF) powered by Kotlin Coroutines and StateFlow.

```
de.carcophan.ai_rss
├── AirssApplication.kt         # Application class configuring custom Coil ImageLoader
├── MainActivity.kt              # Entry point Activity with edge-to-edge Compose setup
├── data
│   ├── model
│   │   ├── Feed.kt             # Data model for RSS/Atom feed sources
│   │   ├── Keyword.kt          # Data model for user-defined filter keywords
│   │   ├── KeywordMatch.kt     # Data model for Gemini classification results
│   │   └── RssItem.kt          # Data model for individual articles
│   ├── parser
│   │   └── RssParser.kt        # Custom XML Pull Parser for RSS 2.0 & Atom feeds
│   └── repository
│       ├── ArticleWebExtractor.kt  # Web scraper for extracting clean full-text articles
│       ├── FeedRepository.kt   # Feed persistence & concurrent network feed fetching
│       ├── GeminiRepository.kt # Google Gemini API client, caching & prompts
│       └── KeywordRepository.kt# Keyword persistence and default suggestions
└── ui
    ├── RssViewModel.kt         # UI state management, coroutine orchestration
    ├── RssScreen.kt            # Main screen combining top bar, list, drawer, and sheets
    ├── components
    │   ├── AddFeedDialog.kt        # Dialog for adding custom feeds and presets
    │   ├── ArticleCard.kt          # Feed list item card with Coil image & AI match badge
    │   ├── ArticleDetailSheet.kt   # Modal bottom sheet for article reading & AI summary
    │   ├── DailyBriefingCard.kt    # Header banner for the Daily Briefing
    │   ├── DailyBriefingSheet.kt   # Fullscreen/sheet daily overview modal
    │   ├── FeedDrawer.kt           # Side navigation drawer for feed selection
    │   ├── GeminiSettingsDialog.kt # Settings dialog for API Key & model configuration
    │   ├── KeywordChipRow.kt       # Scrollable filter chip bar for active keywords
    │   ├── ManageKeywordsDialog.kt # Modal dialog for managing/deleting keywords
    │   └── MarkdownText.kt         # Custom Jetpack Compose Markdown renderer
    └── theme
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

### Dependencies
- **UI**: AndroidX Compose (BOM `2026.02.01`), Material 3, Material Icons Extended
- **Lifecycle**: `lifecycle-runtime-ktx`, `lifecycle-viewmodel-compose`
- **Images**: `io.coil-kt:coil-compose:2.7.0`
- **Concurrency**: Kotlin Coroutines (`kotlinx-coroutines-android`)
- **Testing**: JUnit 4, Espresso, AndroidX Test Runner, Compose UI Test

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Android Studio Meerkat or Ladybug (or newer recommended)
- **JDK**: Java 11 or higher
- **Android SDK**: Compile SDK `37`, Min SDK `37`
- **Google Gemini API Key**: Obtain a free API key from [Google AI Studio](https://aistudio.google.com/)

### Clone & Build

1. Clone the repository:
   ```bash
   git clone https://github.com/Carcophan/ai-rss-reader.git
   cd ai-rss-reader
   ```

2. Open the project in Android Studio or build via Gradle wrapper:
   ```bash
   # Build debug APK
   ./gradlew assembleDebug

   # Run unit tests
   ./gradlew test
   ```

3. Install on a connected Android device or emulator (API 37+):
   ```bash
   ./gradlew installDebug
   ```

---

## ⚙️ Configuration (Gemini API)

To use the AI-powered summary and Daily Briefing features:

1. Launch **ai-rss** on your device.
2. Tap the menu icon (☰) in the top-left corner to open the feed drawer.
3. Tap **Gemini Einstellungen** (or the AI icon ⚡ / ✨).
4. Enter your **Gemini API Key** from [Google AI Studio](https://aistudio.google.com/).
5. (Optional) Choose your preferred model (e.g. `gemini-3.8-flash` or custom model).
6. Tap **Verbindung testen** to verify that your key is working, then save.

> [!TIP]
> The default model `gemini-3.8-flash` provides fast response times and generous free-tier quotas suitable for continuous daily reading.

---

## 🧪 Testing

Unit tests cover the core parsing and extraction logic:
- `ArticleWebExtractorTest`: Tests web extraction and ensures consent walls (e.g. Golem.de) are bypassed cleanly.
- `MarkdownTextTest`: Validates the custom inline markdown parsing engine (bold, italic, code, mixed formatting).

Run unit tests from the command line:
```bash
./gradlew testDebugUnitTest
```

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE) (or your preferred open-source license).
