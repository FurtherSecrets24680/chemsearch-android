# ChemSearch App Audit

This audit maps the current app to the source matrix and identifies where the next improvements should land first.

## Current Architecture Snapshot

ChemSearch is a Kotlin Android app built with Jetpack Compose. It uses Retrofit/OkHttp for network calls, Room for downloaded compounds, DataStore for app settings, SharedPreferences for some legacy/local state, and Android Keystore-backed secure preference helpers for API keys.

Important files:

- `app/src/main/java/com/furthersecrets/chemsearch/ChemViewModel.kt` - main search/data orchestration, favorites, downloads, PubChem/Wikipedia/AI fetching, GHS parsing, notifications.
- `app/src/main/java/com/furthersecrets/chemsearch/data/ApiService.kt` - Retrofit APIs for PubChem, Wikipedia, GitHub releases, Gemini, and OpenAI-compatible providers.
- `app/src/main/java/com/furthersecrets/chemsearch/data/Models.kt` - API response models and main UI state.
- `app/src/main/java/com/furthersecrets/chemsearch/data/local/` - Room download database and mappers.
- `app/src/main/java/com/furthersecrets/chemsearch/data/ChemicalDatabase.kt` - bundled local reference database loader and summary helpers.
- `app/src/main/java/com/furthersecrets/chemsearch/data/OxidationStateCalculator.kt` - extracted oxidation-state logic for testing.
- `app/src/main/java/com/furthersecrets/chemsearch/data/PhPohCalculator.kt` - pH/pOH domain logic.
- `app/src/main/java/com/furthersecrets/chemsearch/data/SdfFallback.kt` - generated SDF fallback from identifiers.
- `app/src/main/java/com/furthersecrets/chemsearch/ui/SearchUi.kt` - compound result screen, descriptions, structures, safety, identifiers.
- `app/src/main/java/com/furthersecrets/chemsearch/ui/ToolsScreen.kt` - tools page and several tool implementations.
- `app/src/main/java/com/furthersecrets/chemsearch/ui/SettingsUi.kt` - settings, library, developer options, FAQ, AI provider UI.
- `app/src/main/java/com/furthersecrets/chemsearch/ui/ChemicalDatabaseUi.kt` - local chemical database browser.

## Strengths

- Search already combines PubChem lookup, properties, images, SDF, descriptions, synonyms, GHS, and history.
- Offline downloads use Room and store richer data than simple cache records.
- AI providers are configurable and separated by provider/model.
- Developer diagnostics already test PubChem, structures, fallback SDF, Wikipedia, GitHub, and AI endpoints.
- Newer domain logic has started moving out of UI files, especially pH/pOH and oxidation states.
- Unit tests exist for settings snapshots, recent searches, pH/pOH, downloads, chemical database summary, network diagnostics, main navigation icon state, and oxidation states.

## Main Risks

### 1. Chemistry correctness is uneven

Some tools still live inside `ToolsScreen.kt`, and formula/chemistry parsing is duplicated. Oxidation states are now testable, but reaction balancing, molar mass parsing, stoichiometry, dilution, gas law, and compare-compound calculations need the same treatment.

Priority:

- Extract formula parsing into one domain module.
- Add fixture tests from IUPAC/OpenStax-style examples.
- Add known edge cases: hydrates, charged ions, nested groups, oxyhalogens, peroxides, hydrides, mixed-valence compounds, and ambiguous formulas.

### 2. PubChem data handling needs provenance

PubChem is the main source, but app state does not consistently preserve source metadata for each field. PUG REST and PUG View return different types of data, and PUG View headings vary by compound.

Priority:

- Add a `CompoundDataRepository`.
- Return typed results with `source`, `retrievedAt`, `status`, and `confidence`.
- Store provenance in downloads.
- Add specific missing-data messages instead of generic fallbacks.

### 3. 3D structures need clearer labels

The app uses PubChem 3D SDF first and generated fallback data second. This is good, but the UI should make the source distinction obvious.

Priority:

- Label models as `PubChem conformer`, `Generated estimate`, or `Unavailable`.
- Explain ionic/metallic/crystal limitations near the 3D viewer.
- Add tests for fallback candidate selection and SDF usability.

### 4. GHS/safety is useful but incomplete

The app parses PubChem GHS data, but safety should be treated carefully. PubChem LCSS explicitly augments, not replaces, safe lab procedures.

Priority:

- Add hazard and precaution code lookup.
- Add clear source and timestamp.
- Add missing-data states.
- Add a short persistent SDS/lab-rules disclaimer in the safety panel.
- Consider LCSS sections as a future expansion.

### 5. Offline downloads need versioning and refresh status

Downloads already store compound data and structures. The next step is making each record explicit about what it contains and whether it is stale.

Priority:

- Add schema/data version to downloaded records.
- Store source metadata and retrieval timestamps.
- Add `Refresh downloaded compound`.
- Show saved asset status: 2D image, 3D model, descriptions, safety, synonyms, identifiers.

### 6. AI descriptions need grounding

AI descriptions should use real fetched data as input and show what sources were used. Provider/model selection works, but descriptions need source discipline.

Priority:

- Build prompt context from PubChem fields, synonyms, identifiers, GHS, and selected source mode.
- Add instruction to avoid claims not present in source context.
- Cache by compound, provider, model, and prompt version.
- Add a small "Based on" source list under AI output.

### 7. UI files are large

`SearchUi.kt`, `SettingsUi.kt`, and `ToolsScreen.kt` are very large. This increases risk when making small changes.

Priority:

- Split only where it directly serves a feature.
- Move tool logic into data/domain files before splitting UI.
- Move repeated card/pill/list components into focused UI files.

### 8. Accessibility needs a pass

Many Compose elements are visually clear but need semantic state and descriptions.

Priority:

- Add state descriptions for selected source, selected tabs, favorite/download buttons, and result expansion.
- Mark section headings.
- Add live regions for error/loading result changes where appropriate.
- Verify compact/non-compact text does not truncate critical labels.

## Feature-Specific Audit

| Area | Current State | Source To Use | Next Improvement |
| --- | --- | --- | --- |
| Search | PubChem name/CID lookup and autocomplete. | PubChem PUG REST. | Add repository result types and source metadata. |
| Compound properties | Formula, weight, IUPAC, SMILES, InChI, InChIKey, charge, covalent units. | PubChem PUG REST. | Preserve per-field source/retrieval time in downloads. |
| Descriptions | PubChem, Wikipedia, AI. | PubChem PUG View/PUG REST, Wikipedia, AI provider docs. | Ground AI with fetched source context. |
| 2D structure | PubChem PNG. | PubChem PUG REST. | Cache image metadata and source label. |
| 3D structure | PubChem SDF plus NCI/CADD fallback. | PubChem3D, NCI/CADD. | Add visible model source and fallback reason. |
| GHS safety | PubChem GHS section parse. | PubChem GHS, PubChem LCSS, UNECE. | Add H/P code explanations and stronger missing-data states. |
| Library/downloads | Favorites and Room downloads. | Android offline-first. | Add version, freshness, refresh, and asset completeness. |
| Chemical database | Local JSON browser. | IUPAC/OpenStax/LibreTexts, source labels per entry. | Add citation/source metadata for bundled entries. |
| Oxidation state | Extracted domain module with tests. | IUPAC oxidation recommendation. | Expand fixture set and remove legacy private implementation once confidence is high. |
| pH/pOH | Extracted domain module with tests. | OpenStax/general chemistry. | Add temperature note: assumes 25 C / pKw 14. |
| Reaction tools | Mostly UI-local logic. | OpenStax/IUPAC examples. | Extract balancing/stoichiometry modules and tests. |
| Compare compounds | Multi-compound compare tool. | PubChem PUG REST/PUG View. | Add source-aware descriptions and structured diff groups. |
| Developer options | Diagnostics and debug info. | Android docs, API docs. | Add repository health and download/storage integrity checks. |

## First Implementation Target

The most valuable first code target is a **chemistry fixtures and domain extraction pass**:

1. Create a shared formula parser module.
2. Move molar mass and reaction/stoichiometry logic out of UI.
3. Add fixture tests for common and edge compounds.
4. Use the same parser across molar mass, oxidation states, compare compounds, and formula display helpers.

This reduces wrong-answer risk before adding more data sources.
