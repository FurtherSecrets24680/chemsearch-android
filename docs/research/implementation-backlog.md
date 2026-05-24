# ChemSearch Source-Backed Implementation Backlog

This backlog turns the source matrix and app audit into work packages. Each package should be implemented with tests before UI changes.

## Phase 0: Research Pack And Fixtures

Goal: make the source material actionable.

- Create `app/src/test/resources/chemistry-fixtures/`.
- Add JSON fixtures for:
  - oxidation states,
  - molar masses,
  - formula parsing,
  - reaction balancing,
  - pH/pOH,
  - PubChem source responses,
  - SDF fallback cases.
- Record for each fixture:
  - input,
  - expected output,
  - source/reference,
  - notes/limits.
- Use IUPAC for definitions/rules, PubChem for compound facts, and OpenStax for beginner examples.

Exit criteria:

- At least 50 chemistry correctness fixtures exist.
- Every fixture names its source.
- Tests fail if a fixture output changes unexpectedly.

## Phase 1: Shared Chemistry Domain

Goal: stop duplicating chemistry logic across UI files.

Create or expand:

- `app/src/main/java/com/furthersecrets/chemsearch/data/FormulaParser.kt`
- `app/src/main/java/com/furthersecrets/chemsearch/data/MolarMassCalculator.kt`
- `app/src/main/java/com/furthersecrets/chemsearch/data/ReactionBalancer.kt`
- `app/src/main/java/com/furthersecrets/chemsearch/data/StoichiometryCalculator.kt`

Work:

- Extract parser logic from `ToolsScreen.kt` and `ChemViewModel.kt`.
- Support hydrates, dot notation, nested parentheses, brackets, and simple charge notation.
- Return structured parse errors instead of generic strings.
- Make UI call domain modules instead of private functions.

Exit criteria:

- Molar mass, oxidation states, stoichiometry, compare compounds, and formula display share one parser.
- All chemistry fixtures pass.
- `ToolsScreen.kt` has less domain logic.

## Phase 2: PubChem Repository And Provenance

Goal: make data source and missing-data states explicit.

Create:

- `app/src/main/java/com/furthersecrets/chemsearch/data/CompoundDataRepository.kt`
- `app/src/main/java/com/furthersecrets/chemsearch/data/SourceMetadata.kt`

Work:

- Wrap PUG REST and PUG View calls in repository methods.
- Return typed states: success, unavailable, network error, parse error, rate/timeout error.
- Attach source metadata to properties, descriptions, synonyms, safety, 2D images, and 3D models.
- Store provenance in downloaded compound records.

Exit criteria:

- UI no longer guesses why data is missing.
- Downloads know where each major field came from.
- Developer diagnostics map to repository probes.

## Phase 3: Structure Pipeline

Goal: make 2D/3D structure behavior more reliable and transparent.

Work:

- Keep PubChem 2D PNG as the primary 2D source.
- Keep PubChem 3D SDF as the primary 3D source.
- Use NCI/CADD SDF as fallback from SMILES, InChI, and InChIKey.
- Add labels:
  - `PubChem 3D conformer`,
  - `Generated fallback`,
  - `Structure unavailable`,
  - `Not a crystal/ionic lattice model`.
- Add test cases for salts, ionic compounds, metals, large compounds, and missing SDF.

Exit criteria:

- The 3D panel always states source and confidence.
- Fallback SDF is never silently presented as PubChem 3D.
- Downloaded compounds preserve structure source.

## Phase 4: Safety/GHS Expansion

Goal: make safety cards more useful without pretending to replace SDS.

Work:

- Parse and display GHS pictograms, hazard statements, precautionary statements, and signal word.
- Add H/P code lookup from PubChem GHS data.
- Align pictogram names with UNECE.
- Add LCSS sections if available through PUG View.
- Add a compact disclaimer in the safety panel.

Exit criteria:

- Safety panel has source, timestamp, and missing-data state.
- Downloads save safety data and source metadata.
- The app never implies safety info is complete.

## Phase 5: Offline Library Upgrade

Goal: make downloads feel like a dependable local compound library.

Work:

- Add data version and schema version to downloads.
- Store retrieval timestamps for properties, descriptions, safety, 2D, and 3D.
- Add `Refresh downloaded compound`.
- Add asset completeness chips: `2D saved`, `3D saved`, `Safety saved`, `Synonyms saved`.
- Add stale-data messaging.

Exit criteria:

- A downloaded compound can explain what is saved and when.
- Refresh updates fields without losing the offline record.
- Offline mode gracefully shows saved data even without network.

## Phase 6: AI Grounding

Goal: make AI descriptions useful but bounded by real data.

Work:

- Build AI prompt context from fetched PubChem fields, identifiers, formula, GHS, and source descriptions.
- Add prompt versioning.
- Cache by CID/name, provider, model, prompt version, and source data fingerprint.
- Show `Based on: PubChem properties, GHS, synonyms...` below AI text.
- Add a warning when source context is thin.

Exit criteria:

- AI text is generated from explicit source context.
- Cached AI descriptions invalidate when prompt/source version changes.
- UI makes AI source limits visible.

## Phase 7: UI, Accessibility, And Performance

Goal: reduce visual inconsistency and improve reliability on phones.

Work:

- Keep Lucide icons for app feature icons and Material icons for platform actions.
- Add selected-state consistency tests for icon swaps.
- Add Compose semantics to result cards, tool outputs, expandable descriptions, safety alerts, and loading states.
- Split large UI areas only when already touched by a feature.
- Profile Search, Tools, Library, and Settings after major UI changes.

Exit criteria:

- Important controls have content descriptions/state descriptions.
- Compact and non-compact layouts avoid title truncation.
- Main screens build with stable layout dimensions.

## Phase 8: Developer Diagnostics

Goal: make failures obvious while testing builds.

Work:

- Group diagnostics by source: PubChem, structure fallback, AI, GitHub, storage.
- Add repository-level health checks after the repository abstraction exists.
- Add local storage summary for Room downloads, cache files, and settings.
- Add exportable diagnostic report with secrets redacted.

Exit criteria:

- Developer options can distinguish network, source, parse, and storage failures.
- Secret values are never copied into reports.

## Phase 9: Documentation And Release

Goal: ship changes with clear user-facing notes.

Work:

- README remains short and user-facing.
- Wiki contains source policy, data limits, offline behavior, AI limits, and safety disclaimers.
- Changelog lists features and fixes without implementation jargon.
- Release builds are made only after full tests and debug build pass.

Exit criteria:

- README is simple.
- Wiki contains the source/correctness details.
- Release APK is signed and version-tagged.

## Suggested First Sprint

Sprint 1 should include:

1. Add chemistry fixture resources.
2. Create shared formula parser.
3. Move molar mass into a domain module.
4. Expand oxidation-state fixtures.
5. Add pH/pOH assumption text and tests for edge values.
6. Run full unit tests and debug build.

This sprint improves correctness without expanding the UI surface.
