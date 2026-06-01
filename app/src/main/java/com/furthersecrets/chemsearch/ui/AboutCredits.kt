package com.furthersecrets.chemsearch.ui

internal data class AboutCreditEntry(
    val title: String,
    val detail: String,
    val url: String
)

internal val aboutAppLinks = listOf(
    AboutCreditEntry(
        title = "GitHub repository",
        detail = "Source, releases, issues, and project notes",
        url = "https://github.com/FurtherSecrets24680/chemsearch-android"
    ),
    AboutCreditEntry(
        title = "Latest release",
        detail = "Download the newest APK",
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/releases/latest"
    ),
    AboutCreditEntry(
        title = "Wiki",
        detail = "Feature guide and setup notes",
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/wiki"
    ),
    AboutCreditEntry(
        title = "Issue tracker",
        detail = "Bug reports and feature requests",
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/issues"
    ),
    AboutCreditEntry(
        title = "Product Hunt",
        detail = "ChemSearch project page",
        url = "https://www.producthunt.com/products/chemsearch"
    ),
    AboutCreditEntry(
        title = "License",
        detail = "Open-source license",
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/blob/main/LICENSE"
    )
)

internal val aboutDataCredits = listOf(
    AboutCreditEntry(
        title = "PubChem PUG REST",
        detail = "Compound lookup, identifiers, properties, images, synonyms, isomers, and SDF structures",
        url = "https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest"
    ),
    AboutCreditEntry(
        title = "PubChem PUG View",
        detail = "Safety sections, GHS data, descriptions, and source records",
        url = "https://pubchem.ncbi.nlm.nih.gov/docs/pug-view"
    ),
    AboutCreditEntry(
        title = "PubChem Periodic Table",
        detail = "Element properties for the periodic table",
        url = "https://pubchem.ncbi.nlm.nih.gov/periodic-table/"
    ),
    AboutCreditEntry(
        title = "Wikipedia and Wikimedia Commons",
        detail = "Description summaries, element images, and spectral-line images",
        url = "https://en.wikipedia.org/api/rest_v1/"
    ),
    AboutCreditEntry(
        title = "Bowserinator/Periodic-Table-JSON",
        detail = "Extra element data, electron shells, summaries, and image links",
        url = "https://github.com/Bowserinator/Periodic-Table-JSON/"
    ),
    AboutCreditEntry(
        title = "NCI/CADD Resolver",
        detail = "Fallback SDF generation when PubChem 3D is unavailable",
        url = "https://cactus.nci.nih.gov/chemical/structure_documentation"
    ),
    AboutCreditEntry(
        title = "IUPAC Gold Book",
        detail = "Chemistry terms and definitions",
        url = "https://goldbook.iupac.org/"
    ),
    AboutCreditEntry(
        title = "IUPAC Red Book",
        detail = "Inorganic naming rules",
        url = "https://iupac.org/what-we-do/books/redbook/"
    ),
    AboutCreditEntry(
        title = "UNECE GHS",
        detail = "GHS pictograms and hazard labels",
        url = "https://unece.org/transport/dangerous-goods/ghs-pictograms"
    )
)

internal val aboutAiProviderCredits = listOf(
    AboutCreditEntry(
        title = "Google Gemini",
        detail = "Optional AI descriptions",
        url = "https://ai.google.dev/gemini-api/docs"
    ),
    AboutCreditEntry(
        title = "Groq Cloud",
        detail = "Optional AI descriptions",
        url = "https://console.groq.com/docs/overview"
    ),
    AboutCreditEntry(
        title = "OpenAI",
        detail = "Optional AI descriptions",
        url = "https://platform.openai.com/docs"
    ),
    AboutCreditEntry(
        title = "OpenRouter",
        detail = "Optional AI descriptions",
        url = "https://openrouter.ai/docs"
    ),
    AboutCreditEntry(
        title = "Mistral AI",
        detail = "Optional AI descriptions",
        url = "https://docs.mistral.ai/"
    )
)

internal val aboutTechnologyCredits = listOf(
    AboutCreditEntry(
        title = "Jetpack Compose",
        detail = "Native Android UI",
        url = "https://developer.android.com/compose"
    ),
    AboutCreditEntry(
        title = "Material 3",
        detail = "UI components and theming",
        url = "https://developer.android.com/develop/ui/compose/designsystems/material3"
    ),
    AboutCreditEntry(
        title = "AndroidX Navigation Compose",
        detail = "Screen navigation",
        url = "https://developer.android.com/develop/ui/compose/navigation"
    ),
    AboutCreditEntry(
        title = "AndroidX Room",
        detail = "Offline compound library storage",
        url = "https://developer.android.com/training/data-storage/room"
    ),
    AboutCreditEntry(
        title = "AndroidX DataStore",
        detail = "App settings storage",
        url = "https://developer.android.com/topic/libraries/architecture/datastore"
    ),
    AboutCreditEntry(
        title = "AndroidX WorkManager",
        detail = "Background update checks",
        url = "https://developer.android.com/topic/libraries/architecture/workmanager"
    ),
    AboutCreditEntry(
        title = "Retrofit",
        detail = "HTTP API client",
        url = "https://square.github.io/retrofit/"
    ),
    AboutCreditEntry(
        title = "OkHttp",
        detail = "Network transport",
        url = "https://square.github.io/okhttp/"
    ),
    AboutCreditEntry(
        title = "Coil",
        detail = "Structure thumbnails and remote images",
        url = "https://coil-kt.github.io/coil/"
    ),
    AboutCreditEntry(
        title = "Gson",
        detail = "JSON conversion",
        url = "https://github.com/google/gson"
    ),
    AboutCreditEntry(
        title = "Kotlin Coroutines",
        detail = "Async work",
        url = "https://kotlinlang.org/docs/coroutines-overview.html"
    ),
    AboutCreditEntry(
        title = "Phosphor Icons",
        detail = "App icon set",
        url = "https://github.com/adamglin0/compose-phosphor-icon"
    ),
    AboutCreditEntry(
        title = "Icons8",
        detail = "Wikipedia logo icon",
        url = "https://icons8.com/icon/OMH4vF9f6nQg/wikipedia-logo"
    )
)
