# ChemSearch for Android

<p align="center">
  <img src="app/src/main/res/drawable/chemsearch.png" width="108" alt="ChemSearch icon"/>
</p>

<p align="center">
  <strong>Chemistry lookup, structure viewing, reference data, and calculation tools in one native Android app.</strong><br/>
  Search PubChem, inspect 2D/3D structures, save compounds, browse an offline chemical database, and use practical chemistry tools without leaving the app.
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

## What ChemSearch Does

ChemSearch is built for quickly moving from a compound name to useful chemistry:

| Area | What you get |
|---|---|
| Compound search | PubChem lookup by name, CAS, formula, or CID |
| Structures | 2D image viewer and native 3D ball-and-stick viewer |
| Reference data | Offline database of substances, ions, functional groups, and reactions |
| Calculators | Molar mass, oxidation states, reaction balancing, stoichiometry, dilution, gas law |
| Study workflow | Recent searches, favorites, copy buttons, explanations, synonyms, safety data |
| Optional AI | Compound descriptions from Gemini, Groq, OpenAI, OpenRouter, or Mistral |

The app is designed for chemistry students, teachers, lab prep, quick checks, and anyone who wants a clean chemistry reference on Android.

---

## Download

Get the latest APK from [GitHub Releases](https://github.com/FurtherSecrets24680/chemsearch-android/releases).

Debug builds can also be built from source:

```bash
git clone https://github.com/FurtherSecrets24680/chemsearch-android
cd chemsearch-android
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Screenshots

| Search | Light Mode | 3D Viewer |
|:---:|:---:|:---:|
| <img src="screenshots/home_dark.jpg" width="220"/> | <img src="screenshots/home_light.jpg" width="220"/> | <img src="screenshots/3dmolecule.jpg" width="220"/> |

| Favorites | Tools | Identifiers |
|:---:|:---:|:---:|
| <img src="screenshots/favorites.jpg" width="220"/> | <img src="screenshots/tools_page.jpg" width="220"/> | <img src="screenshots/iden_ea.jpg" width="220"/> |

| Descriptions | GHS Safety | Molar Mass |
|:---:|:---:|:---:|
| <img src="screenshots/syn_desc.jpg" width="220"/> | <img src="screenshots/ghs_safety.jpg" width="220"/> | <img src="screenshots/tool_mmc.jpg" width="220"/> |

| Oxidation States | Reaction Balancer | SMILES |
|:---:|:---:|:---:|
| <img src="screenshots/tool_osf.jpg" width="220"/> | <img src="screenshots/tool_rb.jpg" width="220"/> | <img src="screenshots/tool_sv.jpg" width="220"/> |

---

## Feature Guide

### Search

ChemSearch starts with a simple search page.

- Search by compound name, common name, IUPAC name, CAS number, formula, or PubChem CID.
- Autocomplete suggestions help with long names and spelling.
- CID lookup is supported directly when you already know the PubChem ID.
- Recent searches are saved locally and can be reopened, deleted, or cleared.
- Results are cached locally so repeat searches open faster.

Usage:

1. Open the Search tab.
2. Type a compound name, CAS number, formula, or CID.
3. Press the arrow/search action.
4. Use the result cards to inspect structures, identifiers, synonyms, safety, and descriptions.

### Compound Summary

The result page keeps the high-value data near the top.

- Compound name and formula with readable chemical subscripts.
- CID, CAS, and molecular weight in compact summary chips.
- `MW (g/mol)` labeling for clearer units.
- Favorite/bookmark button with immediate state update.
- Inline `Find isomers` action beside the molecular formula.
- Copy actions for formulas, identifiers, SMILES, summaries, and key values.

Usage:

- Tap the bookmark icon to save or remove the compound from Favorites.
- Tap formula-related actions when you want to copy or jump into the Isomer Finder.
- Tap copy-enabled rows in identifier/detail sections to copy their value.

### Structure Viewer

Every compound result includes a visual structure area.

- 2D structure image from PubChem.
- Native 3D model viewer for SDF structures.
- Drag to rotate.
- Pinch to zoom.
- Auto-spin when idle.
- CPK atom coloring.
- Ball-and-stick bonds connected to atom surfaces.
- Expand action for easier viewing.
- Download 2D PNG or 3D SDF files.

Usage:

1. Search for a compound.
2. Switch between `2D Structure` and `3D Model`.
3. Drag or pinch inside the viewer.
4. Use download/share actions from the structure card.

### Identifiers

The identifiers card collects the strings used by databases, papers, and cheminformatics tools.

- IUPAC name.
- SMILES and connectivity SMILES.
- InChI and InChIKey.
- Molecular and empirical formula.
- Atom count, bond count, and formal charge when available.
- Tap-to-copy rows.
- Small info buttons explain what the section means.

Usage:

- Tap any identifier row to copy it.
- Use SMILES/InChI values in external chemistry tools.
- Use InChIKey when you need a stable search key.

### Synonyms

PubChem synonyms can be huge, so ChemSearch loads them progressively.

- First 10 synonyms appear quickly.
- `Show 10 more` reveals the next batch.
- `Collapse` returns to the short list.
- Synonyms include alternate names, registry-style labels, and common names when PubChem provides them.

Usage:

- Use synonyms to find trade names, old names, or alternate spellings.
- Expand only when you need deeper synonym lists.

### Descriptions

ChemSearch can show descriptions from multiple sources.

- PubChem for scientific descriptions.
- Wikipedia for general summaries.
- Optional AI descriptions when an API key is configured.
- Description source can be changed per compound.
- Default source can be set in Settings.

Usage:

1. Search for a compound.
2. Open the Description section.
3. Choose PubChem, Wikipedia, or AI.
4. Use `Read more` when the description is longer than the preview.

### Safety Information

Safety data is pulled from PubChem PUG View when available.

- GHS signal word: Danger or Warning.
- GHS pictogram cards.
- Hazard H-codes.
- Clear empty/loading states when no safety data is available.

Usage:

- Use this as a quick reference only.
- Always follow SDS, institutional rules, and local lab guidance for real handling.

### Elemental Analysis

Elemental breakdown helps with composition checks.

- Element-by-element mass percentages.
- Visual bars for easier comparison.
- Useful for formulas, molar mass work, and quick composition checks.

Usage:

- Search a compound.
- Open Elemental Analysis.
- Compare mass contribution by element.

---

## Chemical Database

The Chemical Database is an offline reference browser inside the Tools tab.

Current database:

| Category | Entries |
|---|---:|
| Substances | 331 |
| Ions | 108 |
| Functional groups | 71 |
| Reactions | 149 |
| Total | 659 |

What it includes:

- Common substances with formulas, names, aliases, types, uses, notes, and source links.
- Common ions with formula, charge, type, common compounds, and notes.
- Functional groups with general formula, structure, naming cue, examples, and behavior.
- Reactions with balanced equation, type, conditions, observations, and notes.

Usage:

1. Open Tools.
2. Open Chemical Database.
3. Choose Substances, Ions, Functional Groups, or Reactions.
4. Choose a type card, or search directly.
5. Open an entry to view full details.
6. Copy formulas, equations, structures, or notes from the detail page.
7. For substances, use `Search in ChemSearch` to jump from the database into live PubChem search.

The database is stored as editable JSON files:

```text
app/src/main/assets/chemical_database/substances.json
app/src/main/assets/chemical_database/ions.json
app/src/main/assets/chemical_database/functional_groups.json
app/src/main/assets/chemical_database/reactions.json
```

These files are meant to be edited from Android Studio or a desktop editor. The app does not edit the database in-app.

---

## Tools

ChemSearch includes a full Tools tab with search, categories, and manual reordering.

### Custom 3D Molecule Viewer

Load a local `.sdf` or `.mol` file and view it with the same native 3D renderer used on compound pages.

Usage:

1. Open Tools.
2. Choose Custom 3D Molecule Viewer.
3. Pick an SDF or MOL file from your device.
4. Rotate, zoom, and inspect the structure.

### Molar Mass Calculator

Calculate molar mass from a formula.

Supports:

- Standard formulas like `C6H12O6`.
- Parentheses such as `Ca(OH)2`.
- Hydrate dot notation.
- Elemental breakdown by mass percentage.
- Quick formula helpers and cursor-aware editing.

Usage:

1. Enter a formula.
2. Read the total molar mass.
3. Use the elemental breakdown to check each element's contribution.

### Oxidation State Finder

Find oxidation states for elements in a compound or ion.

Supports:

- Neutral compounds.
- Charged ions.
- Peroxides, superoxides, ozonides, and metal hydrides.
- Interhalogen compounds and common edge cases.

Usage:

1. Enter a formula.
2. Set overall charge if needed.
3. Read the oxidation states and reasoning.

### SMILES Visualizer

Paste a SMILES string and view the compound.

Usage:

1. Open SMILES Visualizer.
2. Paste a SMILES string.
3. ChemSearch looks it up and loads structure data when available.

### Reaction Balancer

Balance chemical equations using exact rational arithmetic.

Supports:

- Reactants and products separated by `->`.
- Automatic display normalization to a chemical arrow.
- Matrix-based balancing.
- Swap-sides action.
- Quick insert helpers.

Usage:

1. Enter an equation such as `H2 + O2 -> H2O`.
2. Tap balance.
3. Copy the balanced equation.

### Isomer Finder

Search PubChem for structural isomers from a molecular formula.

Usage:

1. Enter a molecular formula, or use the `Find isomers` action from a compound page.
2. Review the returned isomer list.
3. Open an isomer in ChemSearch.

### Limiting Reagent

Calculate the limiting reagent and theoretical yield from a balanced equation.

Usage:

1. Enter a balanced equation.
2. Enter reactant amounts.
3. Pick the product.
4. Read the limiting reagent, mole ratios, and theoretical yield.

### Percent Yield

Compare actual yield to theoretical yield.

Usage:

1. Enter the theoretical yield.
2. Enter the actual yield.
3. ChemSearch calculates percent yield.

### Reaction Scaling

Scale reactants for a target product amount.

Usage:

1. Enter a balanced equation.
2. Choose the desired product amount.
3. Read the scaled reactant requirements.

### Dilution Calculator

Solve `C1V1 = C2V2` for solution dilution work.

Usage:

1. Enter three known values.
2. Choose the missing value.
3. ChemSearch calculates the result.

### Ideal Gas Law

Solve `PV = nRT`.

Usage:

1. Enter any three known values.
2. Select the missing variable.
3. Choose units where applicable.
4. Read the calculated gas-law value.

---

## Favorites and Recent Searches

Favorites and history are stored locally.

Favorites:

- Save compounds from the result page.
- Open saved compounds quickly.
- Delete favorites.
- Sort by A-Z or atom count.
- Manually reorder saved compounds.

Recent searches:

- Reopen earlier searches.
- Delete individual items.
- Clear all history.

---

## Settings

Settings are built for daily use, not only setup.

### Appearance

- Light and dark theme.
- Color schemes: Blue, Violet, Emerald, Rose, Amber.
- Compact mode for denser screens.
- Default description source.

### AI Providers

Supported providers:

| Provider | Default model |
|---|---|
| Google Gemini | `gemini-flash-latest` |
| Groq Cloud | `openai/gpt-oss-120b` |
| OpenAI | `gpt-4o-mini` |
| OpenRouter | `openrouter/auto` |
| Mistral AI | `mistral-small-latest` |

Usage:

1. Open Settings.
2. Choose an AI provider.
3. Add the provider API key.
4. Use `Refresh models` to fetch available models.
5. Pick the model you want.
6. Search a compound and choose the AI description tab.

API keys are encrypted locally using the Android Keystore.

### Backup and Restore

Settings can be exported to JSON and imported later.

The backup includes:

- Theme and color preferences.
- Search/settings preferences.
- AI provider and selected models.
- API keys.

Because exported backups can include API keys, keep exported JSON files private.

### Cache

The cache helps repeat searches open faster.

- Clear cache from Settings.
- View cache size.
- Set a custom cache directory.
- Cached compound data is labeled in the UI.

### Updates

ChemSearch can check GitHub Releases for updates.

- Manual update checks.
- Optional update notifications.
- Test notification action in developer settings.

---

## Developer Options

Developer options are hidden by default.

Unlock:

1. Open Settings.
2. Go to About.
3. Tap the build number five times.

Developer tools include:

- Live log viewer.
- Verbose logging toggle.
- Network diagnostics for PubChem, Wikipedia, GitHub, and configured AI providers.
- SharedPreferences inspector with sensitive values masked.
- Memory info.
- API endpoint copy actions.
- Test update notification.
- Wipe SharedPreferences.
- Force crash with confirmation.
- Relock developer settings.

---

## Data Sources

| Source | Used for |
|---|---|
| [PubChem PUG REST](https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest) | Compound lookup, properties, synonyms, 2D images, SDF files, autocomplete |
| [PubChem PUG View](https://pubchem.ncbi.nlm.nih.gov/docs/pug-view) | GHS safety information |
| [Wikipedia REST API](https://en.wikipedia.org/api/rest_v1/) | General compound summaries |
| [GitHub Releases API](https://docs.github.com/en/rest/releases/releases) | App update checks |
| Offline JSON database | Chemical Database entries |
| AI provider APIs | Optional generated descriptions |

---

## Privacy

- No analytics.
- No tracking.
- No app-owned server.
- Compound data is fetched directly from PubChem and Wikipedia.
- AI requests go directly to the selected provider.
- API keys are encrypted locally with the Android Keystore.
- Search history, favorites, settings, and cache data stay on the device.
- Settings exports can include API keys, so exported JSON files should be treated as sensitive.

---

## Permissions

| Permission | Why it is used |
|---|---|
| `INTERNET` | PubChem, Wikipedia, AI providers, and update checks |
| `POST_NOTIFICATIONS` | Optional update notifications on Android 13+ |
| `WRITE_EXTERNAL_STORAGE` | Saving structure downloads on Android 9 and below |

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| State | ViewModel, StateFlow, lifecycle-aware Compose collection |
| Networking | Retrofit 3, OkHttp 5 |
| JSON | Gson |
| Images | Coil |
| Async | Kotlin Coroutines |
| 3D rendering | Custom native Canvas renderer |
| Storage | SharedPreferences, local cache files |
| API key storage | Android Keystore-backed encryption |
| Build | Android Gradle Plugin, Compose Compiler, Gradle |

Android configuration:

| Setting | Value |
|---|---|
| Application ID | `com.furthersecrets.chemsearch` |
| Minimum SDK | API 26 / Android 8.0 |
| Target SDK | API 34 |
| Compile SDK | API 36 |
| Java target | 11 |

---

## Building from Source

Requirements:

- Android Studio.
- JDK 11.
- Android SDK with API 36 installed.

Debug build:

```bash
./gradlew assembleDebug
```

Install on a connected device:

```bash
./gradlew installDebug
```

Run Kotlin compile check:

```bash
./gradlew :app:compileDebugKotlin
```

Debug builds work without AI keys.

### Release Signing

Release builds use `keystore.properties` in the project root when present.

```properties
storeFile=path/to/your.keystore
storePassword=yourStorePassword
keyAlias=yourKeyAlias
keyPassword=yourKeyPassword
```

Build a release APK:

```bash
./gradlew assembleRelease
```

---

## Versioning

ChemSearch uses Git-based versioning at build time.

- If `HEAD` is exactly on a Git tag, that tag becomes `versionName`.
- If commits exist after the latest tag, the version becomes `tag+commitCountSinceTag`.
- If no tag is available, the short commit hash is used.
- `versionCode` is the total commit count from `git rev-list --count HEAD`.

Example:

```text
1.10.0
1.10.0+3
b868a57
```

---

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
