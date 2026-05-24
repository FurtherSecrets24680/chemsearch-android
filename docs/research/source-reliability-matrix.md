# ChemSearch Source Reliability Matrix

This matrix ranks sources by how much ChemSearch should trust them, where they fit in the app, and what caveats must be shown to users or handled in code.

## Reliability Levels

- **A - Primary standard:** official standards, definitions, or platform docs.
- **B - Primary data/service:** authoritative database or API, but data may be aggregated or incomplete.
- **C - Implementation reference:** reliable library/tool docs or papers for algorithms and formats.
- **D - Learning/reference copy:** good for explanations, examples, and FAQ, not for silent data authority.
- **E - Future research:** useful for future features, not needed for current correctness.

## Chemistry And Data Sources

| Source | Level | Best Use In ChemSearch | Caveats | First Actions |
| --- | --- | --- | --- | --- |
| [IUPAC Gold Book](https://goldbook.iupac.org/) | A | Definitions for terms such as oxidation state, empirical formula, InChI, SMILES, acid/base, isomer, and concentration. | Definitions are precise, not always beginner-friendly. Summarize in app language and cite in docs/wiki. | Build an internal terminology table for FAQ/tool info dialogs. |
| [IUPAC oxidation state recommendation](https://iupac.org/recommendation/comprehensive-definition-of-oxidation-state/) | A | Fix oxidation-state rules and explain limits. | Full IUPAC treatment depends on Lewis structures or bond graphs; formula-only results are limited. | Build a golden fixture set for oxyhalogens, hydrides, peroxides, mixed-valence compounds, ions, and transition-metal edge cases. |
| [IUPAC Red Book](https://iupac.org/what-we-do/books/redbook/) | A | Inorganic nomenclature, ions, charges, oxidation-state naming, coordination compound limits. | Some content is book/PDF-based and not API-friendly. | Use for rules and docs; do not copy large text into the app. |
| [IUPAC Blue Book](https://iupac.org/what-we-do/books/bluebook/) | A | Organic nomenclature, IUPAC name expectations, name parsing limitations. | Not an implementation API. | Use to label app limits around IUPAC naming and AI-generated explanations. |
| [PubChem PUG REST](https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest) | B | Search, CID lookup, properties, synonyms, PNG structures, SDF, identifiers. | PubChem aggregates depositor data. Some records lack 3D, safety, or complete identifiers. | Centralize PubChem calls into a repository with source metadata and error classes. |
| [PubChem PUG View](https://pubchem.ncbi.nlm.nih.gov/docs/pug-view) | B | Record sections, GHS, descriptions, experimental properties, source attribution. | Headings differ per compound; code must check index/availability. | Fetch section indexes before relying on headings; store source provenance in downloads. |
| [PubChem3D](https://pubchem.ncbi.nlm.nih.gov/docs/pubchem3d) | B | Explain why 3D is missing and decide when to show PubChem conformers. | PubChem conformers are molecule conformers, not crystal/ionic/metallic lattice models. | Add explicit "PubChem conformer" vs "generated estimate" labels. |
| [PubChem LCSS](https://pubchem.ncbi.nlm.nih.gov/lcss/) | B | Safety summaries and lab hazard panels. | PubChem says LCSS augments safe lab practice and does not replace SDS/lab procedures. | Add LCSS-derived cards only with source and safety disclaimer. |
| [PubChem GHS Summary](https://pubchem.ncbi.nlm.nih.gov/ghs/) | B | Hazard/precaution code meanings and pictogram labels. | Aggregated classification; not a full SDS. | Add H/P code lookup table and better missing-data states. |
| [NCI/CADD Chemical Identifier Resolver](https://cactus.nci.nih.gov/chemical/structure_documentation) | B | Fallback conversion for SMILES, InChI, InChIKey, SDF, names. | Name-to-structure can lose stereochemistry; errors are HTTP-level; service has timeouts. | Keep as fallback only; label generated SDF as estimated. |
| [NIST Chemistry WebBook](https://webbook.nist.gov/index.html.en-us.en) | B | Future physical property data, spectra, thermochemistry references. | Not currently a simple drop-in mobile API; licensing/usage should be checked before bulk use. | Start with external links or manual reference, not background scraping. |
| [EPA CompTox Dashboard](https://www.epa.gov/comptox-tools/comptox-chemicals-dashboard) | B | Future toxicity/exposure enrichment and regulatory context. | API access may require a key and support contact. | Treat as optional advanced provider, not a core dependency. |
| [UNECE GHS pictograms](https://unece.org/transportdangerous-goods/ghs-pictograms) | A | Official pictogram meanings and hazard communication language. | Use exact official meanings carefully; transport labels differ from GHS pictograms. | Align pictogram names and help text with UNECE/PubChem. |

## Cheminformatics And Algorithm Sources

| Source | Level | Best Use In ChemSearch | Caveats | First Actions |
| --- | --- | --- | --- | --- |
| [RDKit docs](https://www.rdkit.org/docs/) | C | Reference for conformer generation, descriptors, canonicalization, validation, and future backend/helper tooling. | Native Android integration is heavy; a server/helper route may be more realistic. | Use RDKit behavior as reference for generated-structure labels and future descriptor tools. |
| [Open Babel docs](https://openbabel.org/docs/) | C | File format conversion and SMILES/InChI/SDF behavior references. | Native/mobile integration can add build complexity. | Use docs to validate format assumptions and import/export wording. |
| [Open Babel paper](https://jcheminf.biomedcentral.com/articles/10.1186/1758-2946-3-33) | C | Background for format conversion and open chemistry tooling. | Paper is not app API documentation. | Cite in wiki/research notes when describing conversion limitations. |
| [CDK docs](https://cdk.github.io/cdk/) | C | Java/Kotlin-friendly cheminformatics reference for future local parsing. | Adding a full toolkit may increase app size and API complexity. | Evaluate only after domain extraction and fixture tests are in place. |
| [OPSIN](https://www.ebi.ac.uk/opsin/) | C | Future IUPAC name-to-structure feature. | Covers systematic names; not every trivial/common name. | Add as a candidate provider after source abstraction exists. |
| [MoleculeNet paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC5868307/) | E | Future ML/property prediction ideas. | Not needed for current app correctness; predictions need strong warnings. | Keep out of core app until all deterministic chemistry tools are solid. |
| [ChEMBL web services](https://chembl.gitbook.io/chembl-interface-documentation/web-services) | E | Future bioactivity/drug-context panels. | Not useful for general inorganic/general chemistry; can confuse casual users. | Consider only as an optional advanced source later. |

## Learning And Explanation Sources

| Source | Level | Best Use In ChemSearch | Caveats | First Actions |
| --- | --- | --- | --- | --- |
| [OpenStax Chemistry 2e](https://openstax.org/details/books/chemistry-2e) | D | User-friendly explanations, tool examples, and FAQ wording. | License is not the same as app code; do not copy large sections. | Use as inspiration for examples and short explanations. |
| [Chem LibreTexts](https://chem.libretexts.org/) | D | Supplementary explanations across general/organic/inorganic topics. | Content varies by page/source; cite in docs if reused. | Use for help-dialog examples after checking each page. |

## Android App Quality Sources

| Source | Level | Best Use In ChemSearch | Caveats | First Actions |
| --- | --- | --- | --- | --- |
| [Android offline-first guide](https://developer.android.com/topic/architecture/data-layer/offline-first) | A | Downloads/library architecture, refresh states, stale-data handling, sync behavior. | Needs adapting to a single-user chemistry app. | Add stale/refresh metadata to downloaded compounds. |
| [Android security checklist](https://developer.android.com/guide/practices/security) | A | API key storage, network security, secure local data. | API keys still belong to users; avoid logging them. | Audit debug logs, clipboard output, and SharedPreferences wipe behavior. |
| [Compose semantics](https://developer.android.com/jetpack/compose/semantics) | A | Accessibility labels, headings, state descriptions, error announcements. | Requires UI-by-UI review. | Add semantics to cards, tabs, tool results, hazard alerts, and loading states. |
| [Compose performance](https://developer.android.com/develop/ui/compose/performance) | A | Recomposition, lazy layouts, stable models, large-screen responsiveness. | Needs measurement before broad refactors. | Add a performance checklist and profile top screens after major UI changes. |

## Working Rule

ChemSearch should prefer:

1. IUPAC for definitions/rules.
2. PubChem for compound facts and identifiers.
3. PubChem/PUG View plus UNECE for safety presentation.
4. NCI/CADD only as a fallback converter.
5. RDKit/Open Babel/CDK as algorithm references or future tooling, not hidden authorities inside the UI.
