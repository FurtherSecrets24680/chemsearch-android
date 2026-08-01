package com.furthersecrets.chemsearch.ui

import com.furthersecrets.chemsearch.R

internal data class AboutCreditEntry(
    val titleRes: Int,
    val detailRes: Int,
    val url: String
)

internal val aboutAppLinks = listOf(
    AboutCreditEntry(
        titleRes = R.string.ui_credit_github_repository,
        detailRes = R.string.ui_credit_github_repository_detail,
        url = "https://github.com/FurtherSecrets24680/chemsearch-android"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_latest_release,
        detailRes = R.string.ui_credit_latest_release_detail,
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/releases/latest"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_wiki,
        detailRes = R.string.ui_credit_wiki_detail,
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/wiki"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_issue_tracker,
        detailRes = R.string.ui_credit_issue_tracker_detail,
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/issues"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_product_hunt,
        detailRes = R.string.ui_credit_product_hunt_detail,
        url = "https://www.producthunt.com/products/chemsearch"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_license,
        detailRes = R.string.ui_credit_license_detail,
        url = "https://github.com/FurtherSecrets24680/chemsearch-android/blob/main/LICENSE"
    )
)

internal val aboutDataCredits = listOf(
    AboutCreditEntry(
        titleRes = R.string.ui_source_pubchem_pug_rest,
        detailRes = R.string.ui_credit_pubchem_pug_rest_detail,
        url = "https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_source_pubchem_pug_view,
        detailRes = R.string.ui_credit_pubchem_pug_view_detail,
        url = "https://pubchem.ncbi.nlm.nih.gov/docs/pug-view"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_pubchem_periodic_table,
        detailRes = R.string.ui_credit_pubchem_periodic_table_detail,
        url = "https://pubchem.ncbi.nlm.nih.gov/periodic-table/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_wikipedia_wikimedia,
        detailRes = R.string.ui_credit_wikipedia_wikimedia_detail,
        url = "https://en.wikipedia.org/api/rest_v1/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_pt_bowserinator,
        detailRes = R.string.ui_credit_bowserinator_detail,
        url = "https://github.com/Bowserinator/Periodic-Table-JSON/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_source_nci_cadd_resolver,
        detailRes = R.string.ui_credit_nci_cadd_detail,
        url = "https://cactus.nci.nih.gov/chemical/structure_documentation"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_source_iupac_gold_book,
        detailRes = R.string.ui_credit_iupac_gold_book_detail,
        url = "https://goldbook.iupac.org/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_source_iupac_red_book,
        detailRes = R.string.ui_credit_iupac_red_book_detail,
        url = "https://iupac.org/what-we-do/books/redbook/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_source_unece_ghs,
        detailRes = R.string.ui_credit_unece_ghs_detail,
        url = "https://unece.org/transport/dangerous-goods/ghs-pictograms"
    )
)

internal val aboutAiProviderCredits = listOf(
    AboutCreditEntry(
        titleRes = R.string.ui_credit_google_gemini,
        detailRes = R.string.ui_credit_ai_descriptions_detail,
        url = "https://ai.google.dev/gemini-api/docs"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_groq_cloud,
        detailRes = R.string.ui_credit_ai_descriptions_detail,
        url = "https://console.groq.com/docs/overview"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_openai,
        detailRes = R.string.ui_credit_ai_descriptions_detail,
        url = "https://platform.openai.com/docs"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_openrouter,
        detailRes = R.string.ui_credit_ai_descriptions_detail,
        url = "https://openrouter.ai/docs"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_mistral_ai,
        detailRes = R.string.ui_credit_ai_descriptions_detail,
        url = "https://docs.mistral.ai/"
    )
)

internal val aboutTechnologyCredits = listOf(
    AboutCreditEntry(
        titleRes = R.string.ui_credit_jetpack_compose,
        detailRes = R.string.ui_credit_jetpack_compose_detail,
        url = "https://developer.android.com/compose"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_material_3,
        detailRes = R.string.ui_credit_material_3_detail,
        url = "https://developer.android.com/develop/ui/compose/designsystems/material3"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_androidx_navigation,
        detailRes = R.string.ui_credit_androidx_navigation_detail,
        url = "https://developer.android.com/develop/ui/compose/navigation"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_androidx_room,
        detailRes = R.string.ui_credit_androidx_room_detail,
        url = "https://developer.android.com/training/data-storage/room"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_androidx_datastore,
        detailRes = R.string.ui_credit_androidx_datastore_detail,
        url = "https://developer.android.com/topic/libraries/architecture/datastore"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_androidx_workmanager,
        detailRes = R.string.ui_credit_androidx_workmanager_detail,
        url = "https://developer.android.com/topic/libraries/architecture/workmanager"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_retrofit,
        detailRes = R.string.ui_credit_retrofit_detail,
        url = "https://square.github.io/retrofit/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_okhttp,
        detailRes = R.string.ui_credit_okhttp_detail,
        url = "https://square.github.io/okhttp/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_coil,
        detailRes = R.string.ui_credit_coil_detail,
        url = "https://coil-kt.github.io/coil/"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_gson,
        detailRes = R.string.ui_credit_gson_detail,
        url = "https://github.com/google/gson"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_coroutines,
        detailRes = R.string.ui_credit_coroutines_detail,
        url = "https://kotlinlang.org/docs/coroutines-overview.html"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_phosphor_icons,
        detailRes = R.string.ui_credit_phosphor_icons_detail,
        url = "https://github.com/adamglin0/compose-phosphor-icon"
    ),
    AboutCreditEntry(
        titleRes = R.string.ui_credit_icons8,
        detailRes = R.string.ui_credit_icons8_detail,
        url = "https://icons8.com/icon/OMH4vF9f6nQg/wikipedia-logo"
    )
)
