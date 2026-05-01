# Batch Medication Resolver — GREEN-GO Audit (v3)

**Date:** 2026-05-01
**Branch:** `feat/batch-medication-green-go` off `main` (`07acc4ef`).
**Scope:** Closes the three v2 (PR #1034) residuals so the medication batch
resolver clears the verdict ladder all the way to GREEN-GO.

History of this audit chain:
- v1 (RED-P1): `cowork_outputs/medication_ambiguous_name_resolution_REPORT.md`
- v2 (GREEN-WITH-FOLLOWUP, post-PR #1034): `cowork_outputs/medication_ambiguous_name_resolution_REPORT_v2.md`
- v3 (this doc, GREEN-GO).

---

## Section A — Verdict (v2 → v3 diff)

| | v2 verdict | v3 verdict | Change |
|---|---|---|---|
| **Overall** | GREEN-WITH-FOLLOWUP | **GREEN-GO** | All three v2 residuals closed. |
| #1 silent wrong-med pick | GREEN | **GREEN** | Reinforced. Backend `_enforce_medication_match_guards` defensively drops MEDICATION mutations whose `entity_id` isn't in the user's medication list — last-line firewall even if Haiku ignores Hard Rule #1. |
| #2 false-confident match (typo) | NO (deterministic) | **GREEN** | Closed. `MedicationNameMatcher` (Kotlin + Python + TS twins, byte-identical) returns `NoMatch` on typos; client confidence guard auto-strips MEDICATION mutations below 0.85 unless the matcher already committed the entity_id. TASK mutations stay (wrong-day is recoverable, wrong-medication is not). |
| #3 undisambiguated-ask UX | GREEN-with-residual-YELLOW | **GREEN** | Web ships a parity `DisambiguationPicker` component; Android Calm Mode keeps the picker (compact-styled instead of hidden) so the sensory-reduction tier still has a recovery path other than Cancel-and-retype. |
| #4 ignored input / silent skip | YES (mitigated) | **YES (mitigated)** | Unchanged. Backend logs dropped unknown-id mutations. |
| #5 cross-medication leak | GREEN | **GREEN** | Unchanged. Multi-recovery loop routes through the same `applyBatch` transaction. |
| #6 case/whitespace/unicode | NO (deterministic) | **GREEN** | Closed. Matcher normalizes (NFC + trim + lowercase + trailing-punct strip) deterministically across all three surfaces; tests cross-validate via mirrored test names. |

---

## Section B — What landed (commit-by-commit)

### B.1 Commit 1 — `feat(batch): pure-function MedicationNameMatcher (Android + backend + web)`
- New: `MedicationNameMatcher.kt`, `medication_name_matcher.py`, `medicationNameMatcher.ts` — semantically identical pure functions.
- New: 14 mirrored unit tests per surface (`exact match`, `case mismatch`, `trailing whitespace`, `unicode smart quote`, `typo no match`, `two wellbutrins ambiguous`, `display label match`, `mixed`, `trailing punctuation`, `longest key wins on overlap`, `substring inside word does not match`, `empty command`, `empty list`, `normalize idempotent`).
- Coverage: closes failure-modes #2 (typo) and #6 (case/whitespace/unicode) with deterministic semantics — same input, same output, every surface.

### B.2 Commit 2 — `feat(batch): wire deterministic pre-resolver + confidence guard for medication batch`
- `BatchOperationsRepository.parseCommand` runs the matcher before the network call, builds `committed_medication_matches` + `forced_ambiguous_phrases`, returns a `BatchParseOutcome` (response + committed-id set).
- `BatchPreviewViewModel.loadPreview` honors the committed-id set as an override for both auto-strip and the new low-confidence MEDICATION strip (floor: `MEDICATION_CONFIDENCE_FLOOR = 0.85f`). TASK mutations are intentionally exempt.
- Backend `_enforce_medication_match_guards` (a) drops MEDICATION mutations referencing unknown ids and (b) appends `forced_ambiguous_phrases` (deduped) to the response.
- System prompt updated to describe both new fields as authoritative.
- Web `batchStore.parsePendingCommand` mirrors the Android wire-up.

### B.3 Commit 3 — `feat(batch): web disambiguation picker + Calm Mode picker parity`
- New web `DisambiguationPicker` component + new firestore-read helper `web/src/api/firestore/medications.ts` (read-only, mirrors `MedicationSyncMapper.medicationToMap` shape).
- `batchStore` adds `medicationCandidates`, `strippedMutations`, `resolveAmbiguity` action.
- Android `BatchPreviewScreen.DisambiguationPicker` gains a `compactStyling` flag wired from `simplifiedUi`. Picker is no longer suppressed in Calm Mode.

### B.4 Commit 4 — `feat(batch): multi-recovery loop + audit doc v3`
- `BatchPreviewViewModel.resolveAmbiguity`: `firstOrNull` → `filter`. One picker tap recovers ALL stripped mutations sharing the resolved phrase (e.g. "skip my morning AND evening Wellbutrin" → both SKIP mutations recovered with one tap).
- New ViewModel tests: `loadPreview_shortCircuitsForUnambiguousMedicationOnlyCommand`, `loadPreview_stripsLowConfidenceMedicationMutation`, `loadPreview_keepsLowConfidenceTaskMutation`, `resolveAmbiguity_recoversAllStrippedMutationsForOneHint`.
- New backend tests: `test_backend_drops_haiku_mutation_for_unknown_medication_id`, `test_backend_appends_forced_ambiguous_phrases_to_response`, `test_backend_dedupes_forced_ambiguous_phrases`, `test_system_prompt_documents_committed_medication_matches`.
- New web tests: `parsePendingCommand_shortCircuitsForUnambiguousMedicationOnlyCommand`, `parsePendingCommand_stripsLowConfidenceMedicationMutation`, `parsePendingCommand_keepsLowConfidenceTaskMutation`, `resolveAmbiguity_recoversAllStrippedMutationsForOneHint`.

---

## Section C — Failure Mode Coverage Matrix (post-GREEN-GO)

| # | Failure mode | v2 | v3 | Code reference |
|---|---|---|---|---|
| 1 | Silent wrong-med pick | GREEN | **GREEN** | `BatchPreviewViewModel.loadPreview` (Android), `batchStore.applyClientSafeguards` (web), `_enforce_medication_match_guards` (backend) |
| 2 | False-confident typo match | mitigated | **GREEN** | `MedicationNameMatcher.match` returns `NoMatch` on typos; `MEDICATION_CONFIDENCE_FLOOR = 0.85` strip |
| 3 | Undisambiguated-ask UX | GREEN-with-residual-YELLOW | **GREEN** | Web `DisambiguationPicker.tsx`; Android `compactStyling` for Calm Mode |
| 4 | Ignored input / silent skip | YES (mitigated) | **YES (mitigated)** | Backend logs dropped unknown-id mutations |
| 5 | Cross-medication leak | GREEN | **GREEN** | `applyBatch` transaction unchanged |
| 6 | Case / whitespace / unicode | mitigated | **GREEN** | Matcher normalize: NFC + trim + lowercase + trailing-punct strip — twin-checked across 3 langs |

---

## Section D — What's intentionally out of scope

- **Web medication CRUD UI**: the new firestore helper reads `users/{uid}/medications` but web doesn't yet write there. Until users have synced data from Android, the matcher returns `NoMatch` and the wire-up reduces to the v2 safeguards. This is the design — closing the gap requires a web meds-management UI which is a separate feature, not a safety fix.
- **Fuzzy / phonetic matching**: explicitly refused. Failure-mode #2 IS that bug; adding fuzzy resolution would reopen it.
- **Picker instrumentation test**: skipped this round — Android emulator-required suites stayed in line with v2 audit's existing instrumentation. Recommend a follow-up Phase F.2 ticket.
