# ChemSearch for Android

<p align="center">
  <img src="app/src/main/res/drawable/chemsearch.png" width="112" alt="ChemSearch icon"/>
</p>

<p align="center">
  <strong>Chemistry simplified for Android.</strong><br/>
  Search compounds, view structures, save offline data, compare compounds, browse references, and use chemistry tools in one app.
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

<p align="center">
  <a href="https://github.com/FurtherSecrets24680/chemsearch-android/releases"><strong>Download APK</strong></a>
  -
  <a href="https://github.com/FurtherSecrets24680/chemsearch-android/wiki"><strong>Wiki</strong></a>
  -
  <a href="https://github.com/FurtherSecrets24680/chemsearch-android/issues"><strong>Issues</strong></a>
</p>

---

## About

ChemSearch is a native Android app for chemistry lookup, study work, and quick reference. It combines compound search, PubChem data, 2D and 3D structures, GHS safety information, offline saving, compound comparison, and practical calculators in a clean Jetpack Compose interface.

It is useful for students, teachers, lab prep, quick compound checks, and anyone who needs chemistry data on a phone without opening several sites.

## Highlights

- Search by compound name, CAS number, formula, or PubChem CID.
- View 2D structures and interactive 3D molecular models.
- Save full compound data for offline use, including structures, identifiers, descriptions, synonyms, safety data, and source metadata.
- Compare several compounds side by side.
- Browse a built-in chemical database with substances, ions, functional groups, and reactions.
- Use chemistry tools for molar mass, pH/pOH, oxidation states, reaction balancing, stoichiometry, dilution, gas laws, isomers, and SMILES lookup.
- Choose PubChem, Wikipedia, or a configured AI provider for descriptions.
- Use light mode, dark mode, AMOLED Mode, compact mode, and multiple color schemes.

## Screenshots

| Search | Light Mode | 3D Viewer |
|:---:|:---:|:---:|
| <img src="screenshots/home_dark.jpg" width="220" alt="ChemSearch search screen in dark mode"/> | <img src="screenshots/home_light.jpg" width="220" alt="ChemSearch search screen in light mode"/> | <img src="screenshots/3dmolecule.jpg" width="220" alt="3D molecule viewer"/> |

| Library / Favorites | Tools | Identifiers |
|:---:|:---:|:---:|
| <img src="screenshots/favorites.jpg" width="220" alt="Library and favorites screen"/> | <img src="screenshots/tools_page.jpg" width="220" alt="ChemSearch tools screen"/> | <img src="screenshots/iden_ea.jpg" width="220" alt="Compound identifiers section"/> |

| Descriptions | GHS Safety | Molar Mass |
|:---:|:---:|:---:|
| <img src="screenshots/syn_desc.jpg" width="220" alt="Compound synonyms and description"/> | <img src="screenshots/ghs_safety.jpg" width="220" alt="GHS safety information"/> | <img src="screenshots/tool_mmc.jpg" width="220" alt="Molar mass calculator"/> |

| Oxidation States | Reaction Balancer | SMILES Viewer |
|:---:|:---:|:---:|
| <img src="screenshots/tool_osf.jpg" width="220" alt="Oxidation state finder"/> | <img src="screenshots/tool_rb.jpg" width="220" alt="Reaction balancer"/> | <img src="screenshots/tool_sv.jpg" width="220" alt="SMILES visualizer"/> |

## Compound Search

ChemSearch can look up compounds using several common identifiers:

- Name, such as `glucose`, `sodium chloride`, or `sulfuric acid`.
- CAS number.
- Molecular formula.
- PubChem CID.

The result page focuses on the details people usually need first:

- Compound name and formula.
- CID, CAS number, and molecular weight.
- IUPAC name, InChI, InChIKey, SMILES, and synonyms.
- 2D structure image.
- 3D structure viewer.
- GHS safety information when available.
- Descriptions from the selected source.
- Isomer search from the formula.
- Favorite and offline download actions.

Formula display supports subscripts, charge superscripts, wrapping for large formulas, and familiar formula ordering for common inorganic compounds. For example, sodium chloride appears as `NaCl`, and sulfuric acid appears as `H2SO4`.

## Structures

ChemSearch loads structure data from PubChem when it is available.

- 2D structure images are shown directly in the compound page.
- 3D SDF models open in the built-in viewer.
- If PubChem has no 3D model, ChemSearch can try fallback structure loading from identifiers such as SMILES, InChI, and InChIKey.
- Generated or fallback structures are labeled so the source is clear.
- Structure pills include visible outlines in light, dark, and AMOLED themes.

## Descriptions

Descriptions can come from:

- PubChem.
- Wikipedia.
- A configured AI provider.

AI descriptions are optional. When enabled, ChemSearch uses available compound details such as formula, identifiers, safety data, and source context to keep the generated text grounded. API keys are stored locally with Android Keystore.

Long descriptions can be expanded and collapsed, including inside the compound comparison tool.

## Library

The Library screen groups saved and offline content into three areas:

- **Favorites**: saved compounds for quick access.
- **Downloads**: offline compound copies.
- **Chemical Database**: built-in reference data for substances, ions, functional groups, and reactions.

Library supports list and grid layouts. The Chemical Database card keeps the grid layout clean, while the list layout can show database counts from an info button beside the description.

Offline downloads save more than a cached result. A downloaded compound can include:

- Basic compound details.
- 2D structure image.
- 3D structure data when available.
- Identifiers.
- Descriptions.
- Synonyms.
- GHS safety information.
- Source metadata.

The offline save button shows download progress while the compound data is being saved.

## Recent Searches

The Recent screen keeps search history organized by time.

- Pin important searches.
- Sort by newest or oldest from the filter menu.
- Remove single entries.
- Clear all recent searches.

Pinned entries use a pin icon so they are easier to understand at a glance.

## Tools

ChemSearch includes practical tools for common chemistry work:

- **Chemical Database**: offline reference data for quick lookup.
- **Molar Mass Calculator**: parse a formula and calculate molar mass.
- **Oxidation State Finder**: estimate oxidation states for compounds, including oxyhalogens, peroxides, superoxides, ozonides, hydrides, and mixed-valence cases.
- **pH / pOH Calculator**: convert between pH, pOH, hydrogen ion concentration, and hydroxide ion concentration.
- **Reaction Balancer**: balance chemical equations.
- **Isomer Search**: search isomers from a formula.
- **Stoichiometry**: convert reaction amounts.
- **Dilution Calculator**: calculate concentration and volume changes.
- **Gas Law Calculator**: work with pressure, volume, amount, and temperature.
- **SMILES Visualizer**: open structure data from SMILES input.
- **Custom 3D Molecule Viewer**: load local `.sdf` or `.mol` files.
- **Compare Compounds**: compare formulas, descriptions, identifiers, atom counts, bond counts, molecular weight, and other properties across several compounds.

The Tools page supports list and grid layouts, compact mode, and a reset action for returning tools to the default order.

## Display And Settings

ChemSearch includes display settings for different phones and reading styles:

- Light and dark theme.
- AMOLED Mode for true-black backgrounds in dark mode.
- Color schemes.
- Compact mode.
- Welcome screen reset for checking onboarding again.
- Internal app update download with progress in Settings.
- Update install prompt after the APK finishes downloading.

## Developer Options

Developer options include checks for the app's main data paths:

- PubChem lookup.
- PubChem structures.
- PubChem safety data.
- Wikipedia descriptions.
- NCI/CADD fallback structures.
- GitHub release checks.
- Configured AI providers.

These checks are useful when a data source changes, a network request fails, or a provider key needs testing.

## Built With

- [Kotlin](https://kotlinlang.org/)
- [Jetpack Compose](https://developer.android.com/compose)
- [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Phosphor Icons](https://phosphoricons.com/)
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

Safety data is shown as reference information only. Always follow the official SDS, lab rules, and local safety requirements for real handling decisions.

## Project Structure

```text
.
|-- app/
|   `-- src/main/
|       |-- assets/chemical_database/
|       |-- java/com/furthersecrets/chemsearch/
|       `-- res/
|-- docs/
|-- gradle/
|-- screenshots/
|-- keystore.properties.example
|-- local.properties.example
|-- build.gradle.kts
|-- settings.gradle.kts
`-- README.md
```

Useful areas:

- `app/src/main/java/com/furthersecrets/chemsearch/` contains the Android app code.
- `app/src/main/assets/chemical_database/` contains local reference JSON files.
- `app/src/main/res/` contains icons, images, and Android resources.
- `screenshots/` contains README screenshots.
- `docs/` contains planning and technical notes.

## Download

Download the latest APK from [GitHub Releases](https://github.com/FurtherSecrets24680/chemsearch-android/releases).

## Build From Source

Requirements:

- Android Studio
- JDK 17+
- Android SDK API 36

Clone the repo:

```bash
git clone https://github.com/FurtherSecrets24680/chemsearch-android
cd chemsearch-android
```

Build a debug APK on macOS or Linux:

```bash
./gradlew assembleDebug
```

Build a debug APK on Windows:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Signed Release Builds

For signed release builds, copy `keystore.properties.example` to `keystore.properties` and keep the keystore in the project-relative path shown in the example file.

The local signing files are ignored by Git:

```text
local.properties
keystore.properties
*.jks
*.keystore
```

Build the release APK:

```powershell
.\gradlew.bat assembleRelease
```

The release APK is generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Windows And macOS Notes

The project is meant to build on both Windows and macOS.

- Use `gradlew.bat` on Windows.
- Use `./gradlew` on macOS and Linux.
- Keep Android SDK paths in `local.properties`, not in Git.
- Keep signing secrets in `keystore.properties`, not in Git.
- Keep the keystore path project-relative so both operating systems can resolve it.
- Avoid committing generated build folders.

## Documentation

The full feature guide belongs in the [GitHub Wiki](https://github.com/FurtherSecrets24680/chemsearch-android/wiki). It covers search, Library, downloads, tools, AI descriptions, signing, cross-platform setup, and developer notes.

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
