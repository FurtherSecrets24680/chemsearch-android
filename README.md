# ChemSearch for Android

<p align="center">
  <img src="app/src/main/res/drawable/chemsearch.png" width="108" alt="ChemSearch icon"/>
</p>

<p align="center">
  <strong>Chemistry simplified for Android.</strong><br/>
  Search compounds, view structures, compare results, save offline data, browse references, and use practical chemistry tools in one app.
</p>

<p align="center">
  <a href="https://github.com/FurtherSecrets24680/chemsearch-android/releases">
    <img src="https://img.shields.io/github/v/release/FurtherSecrets24680/chemsearch-android?style=for-the-badge" alt="Latest Release"/>
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin and Compose"/>
  <img src="https://img.shields.io/badge/License-MIT-111827?style=for-the-badge" alt="MIT License"/>
</p>

<p align="center">
  <a href="https://www.producthunt.com/products/chemsearch?embed=true&amp;utm_source=badge-featured&amp;utm_medium=badge&amp;utm_campaign=badge-chemsearch" target="_blank" rel="noopener noreferrer">
    <img alt="ChemSearch - Chemistry Simplified. | Product Hunt" width="250" height="54" src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=1104309&amp;theme=light&amp;t=1774183145053">
  </a>
</p>

---

## About

ChemSearch is a native Android app for quick chemistry lookup and study work. It combines PubChem search, 2D and 3D structure viewing, safety data, descriptions, references, offline saving, compound comparison, and calculators in a clean Compose interface.

The app is useful for students, teachers, lab prep, and quick compound checks.

## Features

- Compound search by name, CAS number, formula, or PubChem CID.
- 2D structure images and an interactive 3D molecule viewer.
- Backup 3D model loading from SMILES, InChI, or InChIKey when PubChem has no 3D model.
- Formula display with subscripts, charge superscripts, wrapping, and isomer search.
- Descriptions from PubChem, Wikipedia, or a configured AI provider.
- GHS safety information when PubChem provides it.
- Library with Favorites, offline Downloads, and a searchable Chemical Database.
- Full offline compound copies with structures, identifiers, descriptions, safety data, and synonyms.
- Chemical Database cards for substances, ions, functional groups, and reactions.
- Tools for molar mass, oxidation states, pH/pOH, reaction balancing, isomers, stoichiometry, dilution, gas law, SMILES lookup, and compound comparison.
- Tool and Library screens with list/grid layouts.
- Compound comparison for formulas, descriptions, identifiers, atom counts, bond counts, and key properties.
- Search history with pinning and time groups.
- Light/dark themes, color schemes, compact mode, and onboarding setup.

## Screenshots

| Search | Light Mode | 3D Viewer |
|:---:|:---:|:---:|
| <img src="screenshots/home_dark.jpg" width="220"/> | <img src="screenshots/home_light.jpg" width="220"/> | <img src="screenshots/3dmolecule.jpg" width="220"/> |

| Library / Favorites | Tools | Identifiers |
|:---:|:---:|:---:|
| <img src="screenshots/favorites.jpg" width="220"/> | <img src="screenshots/tools_page.jpg" width="220"/> | <img src="screenshots/iden_ea.jpg" width="220"/> |

| Descriptions | GHS Safety | Molar Mass |
|:---:|:---:|:---:|
| <img src="screenshots/syn_desc.jpg" width="220"/> | <img src="screenshots/ghs_safety.jpg" width="220"/> | <img src="screenshots/tool_mmc.jpg" width="220"/> |

## Documentation

The full feature guide belongs in the [GitHub Wiki](https://github.com/FurtherSecrets24680/chemsearch-android/wiki). It covers search, Library, downloads, tools, AI descriptions, signing, cross-platform setup, and developer notes.

## Built With

- [Kotlin](https://kotlinlang.org/)
- [Jetpack Compose](https://developer.android.com/compose)
- [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [AndroidX Room](https://developer.android.com/training/data-storage/room)
- [AndroidX DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Retrofit](https://square.github.io/retrofit/)
- [OkHttp](https://square.github.io/okhttp/)
- [Gson](https://github.com/google/gson)
- [Coil](https://coil-kt.github.io/coil/compose/)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## Data Sources

- [PubChem PUG REST](https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest) for lookup, properties, synonyms, images, and SDF files.
- [PubChem PUG View](https://pubchem.ncbi.nlm.nih.gov/docs/pug-view) for GHS safety data.
- [Wikipedia REST API](https://en.wikipedia.org/api/rest_v1/) for general summaries.
- [NCI/CADD Chemical Identifier Resolver](https://cactus.nci.nih.gov/chemical/structure) for fallback SDF models.
- Local JSON files in `app/src/main/assets/chemical_database/` for offline reference data.
- Optional AI provider APIs for generated descriptions.

## Download

Download the latest APK from [GitHub Releases](https://github.com/FurtherSecrets24680/chemsearch-android/releases).

## Build From Source

Requirements:

- Android Studio
- JDK 17+
- Android SDK API 36

```bash
git clone https://github.com/FurtherSecrets24680/chemsearch-android
cd chemsearch-android
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For signed release builds, copy `keystore.properties.example` to `keystore.properties` and keep the keystore in the project-relative path shown in the example file. `local.properties`, `keystore.properties`, and keystore files are intentionally ignored.

## Privacy

- No analytics.
- No tracking.
- No app-owned server.
- Compound data is fetched directly from the selected data source.
- AI requests go directly to the selected AI provider.
- API keys are encrypted locally with Android Keystore.
- Search history, favorites, downloads, settings, and cache data stay on the device.

## License

MIT License. See [LICENSE](LICENSE) for details.

---

<a href="https://www.star-history.com/?repos=furthersecrets24680%2Fchemsearch-android&type=timeline&logscale=&legend=top-left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=furthersecrets24680/chemsearch-android&type=timeline&theme=dark&legend=top-left" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=furthersecrets24680/chemsearch-android&type=timeline&legend=top-left" />
    <img alt="Star History Chart" src="https://api.star-history.com/image?repos=furthersecrets24680/chemsearch-android&type=timeline&legend=top-left" />
  </picture>
</a>
