# ChemSearch for Android

<p align="center">
  <img src="app/src/main/res/drawable/chemsearch.png" width="96" alt="ChemSearch icon"/>
</p>

<p align="center">
  <strong>A native Android app for searching, visualizing, and understanding chemical compounds.</strong><br/>
  PubChem-powered lookup, native 2D/3D structure viewing, chemistry tools, favorites, and optional AI summaries.
</p>

<p align="center">
  <a href="https://github.com/FurtherSecrets24680/chemsearch-android/releases">
    <img src="https://img.shields.io/github/v/release/FurtherSecrets24680/chemsearch-android?style=flat-square" alt="Latest Release"/>
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-green?style=flat-square&logo=android" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-blue?style=flat-square&logo=kotlin" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/License-MIT-lightgrey?style=flat-square" alt="MIT License"/>
</p>

<p align="center">
<a href="https://www.producthunt.com/products/chemsearch?embed=true&amp;utm_source=badge-featured&amp;utm_medium=badge&amp;utm_campaign=badge-chemsearch" target="_blank" rel="noopener noreferrer"><img alt="ChemSearch - Chemistry Simplified. | Product Hunt" width="250" height="54" src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=1104309&amp;theme=light&amp;t=1774183145053"></a>
</p>

## Download
- Grab the latest APK from [GitHub Releases](https://github.com/FurtherSecrets24680/chemsearch-android/releases).
- Or build from source (see below).

---
## Features

### Search
- Search by common name, IUPAC name, CAS number, or PubChem CID via PubChem PUG REST
- One-line search field with real-time autocomplete suggestions and a dedicated CID shortcut
- Recent searches with saved count, quick clear, and fast repeat lookup
- Favorites with filtering, A-Z and atom-count sorting, manual reordering, and immediate bookmark state updates
- Formula-first workflow with an inline **Find isomers** action beside the molecular formula

### Compound Data
- Redesigned compound summary showing name, smart-rendered molecular formula, CID, CAS, and molecular weight in compact copyable chips
- Molecular weight is labeled with `MW (g/mol)` while keeping the value compact for small screens
- Full identifiers card: IUPAC name, SMILES (full and connectivity), InChI, InChIKey, empirical formula, atom count, bond count, and formal charge, with tap-to-copy values
- **Info tooltips** on each card explaining what each identifier or data type means
- Expanded structural metadata sourced from PubChem, with older cached entries automatically backfilled when needed
- **Synonyms** load progressively: the first 10 show immediately, then **Show 10 more** continues through long PubChem synonym lists, with a collapse control
- **Elemental analysis** with mass percentage bars for each element
- Copy formula, CID, CAS, molecular weight, identifiers, summaries, and SMILES directly from the result UI

### Structure Viewer
- **2D structure** via PubChem PNG images with tap-to-zoom
- **3D model** using a fully native Canvas-based engine:
    - Drag to rotate, pinch to zoom, auto-spin with pause on touch
    - CPK coloring for all 118 elements
    - Ball-and-stick model with bonds connected to atom surfaces
- Loading feedback while 3D data is prepared in the background
- **Download** 2D PNG or 3D SDF files directly to Downloads
- Share compound details from the structure card with a compact icon action

### Safety Information
- GHS classification fetched from PubChem PUG View:
    - Signal word badge (Danger / Warning) with color coding
    - GHS pictograms (GHS01 through GHS09) with icons and labels
    - Hazard H-codes

### Descriptions
Three switchable sources per compound:
- **PubChem** for scientific descriptions
- **Wikipedia** for general summaries
- **AI** via Google Gemini, Groq Cloud, OpenAI, OpenRouter, or Mistral AI
- AI provider settings include API-key management, provider dropdown, model dropdown, default models, and a **Refresh models** action after adding a key

### Tools
Eleven chemistry tools accessible from the Tools tab:

- **Custom 3D Molecule Viewer** : Load any `.sdf` or `.mol` file from your device and view it in the native 3D engine
- **Molar Mass Calculator** : Enter any molecular formula (including parentheses groups and hydrate dot notation) to get the molar mass and a full elemental breakdown by mass percentage, with cursor-aware editing and quick-insert helpers
- **Oxidation State Finder** : Determine oxidation states for each element in a compound, with support for peroxides, superoxides, ozonides, metal hydrides, and interhalogen compounds. Enter the overall charge for polyatomic ions
- **SMILES Visualizer** : Paste any SMILES string to look it up on PubChem and view its 2D structure and 3D model
- **Reaction Balancer** : Balance any chemical equation using matrix-based Gaussian elimination with exact rational arithmetic. Includes quick-insert buttons, live `->` to `→` normalization, and swap-sides control
- **Isomer Finder** : Find up to 20 structural isomers by searching with a molecular formula
- **Limiting Reagent** : Identify limiting reagent, ratios, and theoretical yield for a balanced equation
- **Percent Yield** : Compare actual yield against theoretical yield for a target product
- **Reaction Scaling** : Scale reactants for a desired product amount
- **Dilution Calculator** : Solve C1V1 = C2V2 for solutions
- **Ideal Gas Law** : Solve PV = nRT for gases

Tool categories include scrollable filter pills and manual tool reordering.

### Customization
- **Theme mode** dropdown (Light / Dark) in Settings
- **Color schemes**: Blue, Violet, Emerald, Rose, and Amber
- **Compact mode** toggle for denser layouts on smaller screens
- Configurable default description source
- AI provider selection with encrypted per-provider API key management
- Per-provider model selection with fetched model lists and built-in defaults
- Autosuggestions toggle (scrollable dropdown, toggleable)
- **Settings backup and restore** using JSON export/import, including API keys and recent AI model/provider settings

### Updates and Cache
- Built-in update checks against GitHub releases, with optional notifications
- Local compound cache to speed repeat searches, with clear + custom location controls
- Missing structural metadata for older cached entries is refreshed automatically in the background
- Cached results are labeled in the UI

### Developer Options
A hidden debug menu can be unlocked by tapping the build number in the 'About' card five times. It includes:
- **Live log viewer** : Real-time in-app log buffer (up to 200 lines) capturing search events, API calls, errors, SDF loads, and more, with timestamps. Errors always captured; verbose logs toggle-gated
- **Verbose logging toggle** : Writes detailed `D/ChemSearch` lines to Logcat and the live buffer
- **Network diagnostics** : Tests core data/update endpoints and configured AI provider reachability with status and latency reporting
- **SharedPreferences inspector** : Dumps stored keys and values with sensitive values masked
- **Memory info** : JVM heap usage and system RAM from `ActivityManager`
- **API endpoints** : Copies configured base URLs to clipboard for manual testing
- **Test update notification** : Triggers a sample update alert for verification
- **Wipe SharedPreferences** : Completely reset all the stored data
- **Force crash** : Throws a deliberate unhandled exception to verify crash reporting (confirmation required)
- **Hide debug settings** : Relock the developer menu until the next 5-tap unlock

---

## Screenshots
|                                                    |                                                     |                                                     |
|:--------------------------------------------------:|:---------------------------------------------------:|:---------------------------------------------------:|
| <img src="screenshots/home_dark.jpg" width="220"/> | <img src="screenshots/home_light.jpg" width="220"/> | <img src="screenshots/3dmolecule.jpg" width="220"/> |
|                 Search (Dark mode)                 |                 Search (Light mode)                 |                 3D Molecule Viewer                  |
| <img src="screenshots/favorites.jpg" width="220"/> | <img src="screenshots/tools_page.jpg" width="220"/> |  <img src="screenshots/iden_ea.jpg" width="220"/>   |
|                     Favorites                      |                     Tools Page                      |           Identifiers & Element Analysis            |
| <img src="screenshots/syn_desc.jpg" width="220"/>  | <img src="screenshots/ghs_safety.jpg" width="220"/> |  <img src="screenshots/tool_mmc.jpg" width="220"/>  |
|               Synonyms & Description               |               GHS Safety Information                |                Molar Mass Calculator                |
| <img src="screenshots/tool_osf.jpg" width="220"/>  |  <img src="screenshots/tool_rb.jpg" width="220"/>   |  <img src="screenshots/tool_sv.jpg" width="220"/>   |
|               Oxidation State Finder               |                  Reaction Balancer                  |                  SMILES Visualizer                  |

---

## Tech Stack

| Component     | Technology                                             |
|---------------|--------------------------------------------------------|
| Language      | Kotlin                                                 |
| UI            | Jetpack Compose + Material 3                           |
| Networking    | Retrofit 2 + OkHttp                                    |
| Image loading | Coil                                                   |
| Async         | Kotlin Coroutines + StateFlow + lifecycle-aware Compose state collection |
| JSON          | Gson                                                   |
| 3D rendering  | Custom native Canvas engine                            |
| Storage       | SharedPreferences + Android Keystore-backed encrypted API keys |
| Build         | Android Gradle Plugin 9.1.0, Kotlin 2.3.20, Compose BOM 2026.03.00 |
| Versioning    | Git tag-based version name + commit count version code |

---

## Data Sources

| Source                                                             | Used for                                                               |
|--------------------------------------------------------------------|------------------------------------------------------------------------|
| [PubChem PUG REST](https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest) | Compound lookup, properties, structural metadata, synonyms, descriptions, SDF, autocomplete |
| [PubChem PUG View](https://pubchem.ncbi.nlm.nih.gov/docs/pug-view) | GHS safety classifications                                             |
| [Wikipedia REST API](https://en.wikipedia.org/api/rest_v1/)        | Compound summaries                                                     |
| [Google Gemini](https://ai.google.dev/)                            | AI descriptions                                                        |
| [Groq](https://groq.com/)                                          | AI descriptions                                                        |
| [OpenAI](https://platform.openai.com/)                              | AI descriptions                                                        |
| [OpenRouter](https://openrouter.ai/)                                | AI descriptions                                                        |
| [Mistral AI](https://mistral.ai/)                                   | AI descriptions                                                        |
| [GitHub Releases API](https://docs.github.com/en/rest/releases/releases) | Update checks                                                     |

---

## Requirements

### For Users
- Android 8.0 (Oreo) or higher (API 26+)
- Internet connection for compound data and AI descriptions

### For Developers
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11
- Android SDK API 36

---

## Permissions

- `INTERNET` for compound data, suggestions, descriptions, and update checks
- `POST_NOTIFICATIONS` (Android 13+) for optional update notifications
- `WRITE_EXTERNAL_STORAGE` (Android 9 and below) to save 2D PNG and 3D SDF downloads

---

## Building from Source

```bash
git clone https://github.com/FurtherSecrets24680/chemsearch-android
cd chemsearch-android
./gradlew assembleDebug
```

Open in Android Studio, sync Gradle, and run on a device or emulator (API 26+). Debug builds work without any API keys configured.

To install a debug build on a connected device:

```bash
./gradlew installDebug
```

### Release Signing

Release builds require a `keystore.properties` file in the project root:

```properties
storeFile=path/to/your.keystore
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

Then build via `./gradlew assembleRelease` or **Build → Generate Signed APK**.

---

## AI Descriptions (Optional)

AI descriptions are optional. Add a provider API key in Settings, choose a model, then regenerate the AI description for a compound.

| Provider       | Default model             | Get an API key                                                       |
|----------------|---------------------------|----------------------------------------------------------------------|
| Google Gemini  | `gemini-flash-latest`     | [aistudio.google.com/api-keys](https://aistudio.google.com/api-keys) |
| Groq Cloud     | `openai/gpt-oss-120b`     | [console.groq.com/keys](https://console.groq.com/keys)               |
| OpenAI         | `gpt-4o-mini`             | [platform.openai.com/api-keys](https://platform.openai.com/api-keys) |
| OpenRouter     | `openrouter/auto`         | [openrouter.ai/keys](https://openrouter.ai/keys)                     |
| Mistral AI     | `mistral-small-latest`    | [console.mistral.ai/api-keys](https://console.mistral.ai/api-keys)   |

Each provider includes built-in default models. After adding a key, use **Refresh models** in Settings to fetch the live model list and choose a preferred model. Keys are encrypted locally with the Android Keystore and only sent directly to the selected provider.

## Settings Backup

Settings can be exported to a JSON file and imported later. The export includes general preferences, color scheme, AI provider/model choices, and saved API keys.

Because exported backups include usable API keys, treat exported JSON files as sensitive. Imported keys are re-encrypted on the device when restored.

## Versioning

The app version is derived from Git at build time:

- If `HEAD` points at a Git tag, that tag becomes `versionName`.
- If there are commits after the latest tag, the name becomes `tag+commitCountSinceTag`.
- If no tag is available, the short commit hash is used.
- `versionCode` is the total commit count from `git rev-list --count HEAD`.

---

## Privacy

- Data is fetched directly from PubChem, Wikipedia, selected AI providers, and GitHub Releases. No intermediary servers are used.
- API keys are encrypted locally using the Android Keystore and stored in app preferences. Search history, favorites, settings, and cache metadata are stored locally.
- Settings exports can include API keys, so exported JSON backups should be kept private.
- Cached compound data is stored locally on device.
- No analytics, tracking or telemetry of any kind.

---
<a href="https://www.star-history.com/?repos=furthersecrets24680%2Fchemsearch-android&type=timeline&logscale=&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=furthersecrets24680/chemsearch-android&type=timeline&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=furthersecrets24680/chemsearch-android&type=timeline&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/image?repos=furthersecrets24680/chemsearch-android&type=timeline&legend=top-left" />
 </picture>
</a>
---

## License

MIT License. See [LICENSE](LICENSE) for details.

---
