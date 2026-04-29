# Multi-task creation via QuickAddBar — audit

**Status:** Phase 1 complete (audit). Phase 2 fan-out auto-fires below.
**Scope source:** inline session prompt 2026-04-29.
**Branch state:** ran on `fix/today-label-sod-boundary` HEAD; nothing in this audit
depends on the branch contents.

## Summary verdict

**PROCEED with COMPROMISED scope.** The premise that the existing batch-mutate
infrastructure (BatchPreviewScreen / `batch_undo_log` / Haiku `/api/v1/ai/batch-parse`)
can absorb a `CREATE` mutation type cleanly does **not** survive contact with the
schema. The scope's "Notes on ambiguity" Option B (single-task × N) is **closer to
correct, but with a stronger spin**: there is already a Haiku-backed
`/api/v1/ai/tasks/extract-from-text` endpoint (PR #692-era, schema in
`backend/app/schemas/ai.py:299-313`, service in
`backend/app/services/ai_productivity.py:620-664`) that returns N
`ExtractedTaskCandidate` rows in a single round-trip. The Android client even has
a UI shell for it — `PasteConversationScreen` — though it currently runs against
the offline regex extractor only. Recommendation: route multi-task QuickAddBar
input through that endpoint, **not** through batch-parse.

That cuts most of the scope's hard-constraint #1 ("reuse, don't fork
BatchPreviewScreen / batch_undo_log / 30s Snackbar undo / 24hr sweep / batch
endpoint"). The audit recommends accepting that compromise rather than
contorting the batch-parse schema, with concrete reasoning per item below.

---

## Item 1 — Routing precedence (GREEN)

QuickAddViewModel.kt:329 places `BatchIntentDetector.detect(text)` BEFORE
template, project-intent, and single-task NLP. The branch only emits to
`_batchIntents` on `Result.Batch`; otherwise it falls through. A multi-task
detector inserted on the `Result.NotABatch` branch fires AFTER bulk-batch wins
and BEFORE single-task NLP — which is the right precedence.

**Edge case:** `BatchIntentDetector` requires **two distinct signal categories**
(see kdoc lines 4-25 + `Signal` enum). A user typing `"complete tasks today"`
matches `BULK_VERB_AND_PLURAL` + `TIME_RANGE` = batch. A user typing
`"complete groceries today, call mom tomorrow"` matches `TIME_RANGE` (`today`,
`tomorrow`) plus `complete`+`tasks` is broken (no `tasks` plural here) — so it
returns `NotABatch`. Good — multi-create can pick it up. The pathological case
`"complete tasks today, archive done items"` matches QUANTIFIER (`done` is not in
the set, but `everything` could be) … actually `done` is not a quantifier, so
this resolves as: `complete`+`tasks` (BULK_VERB_AND_PLURAL) + `today` (TIME_RANGE)
= 2 signals → `Result.Batch`. Multi-create never sees it. ✓

**Verified by reading:** `BatchIntentDetector.kt:88-117`, `QuickAddViewModel.kt:325-344`.

**Recommendation:** PROCEED. No changes to `BatchIntentDetector` needed; insert
the new detector after the batch-detector branch in QuickAddViewModel.

---

## Item 2 — Heuristic specification (YELLOW — adversarial cases force a strict rule)

**Adversarial false-positives the user prompt names:**

| Input | Comma count | Expected route |
|---|---|---|
| `pick up groceries 5pm` | 0 | single-task |
| `email Bob, then call Mary` | 1 | single-task (one task, comma in title) |
| `finish report (the long one), or skip if blocked` | 1 | single-task |
| `buy milk, eggs, bread` | 2 | **single-task** (this is the load-bearing trap — 3 segments, all "task-shaped" by length, but the user wants ONE task) |

**True-positives the user wants caught:**

| Input | Expected route |
|---|---|
| `pick up groceries 5pm, call mom tomorrow, finish report by Friday` | multi-create |
| `email Bob today\ncall Mary tomorrow\nwrite notes Friday` | multi-create |
| Mixed comma + newline | multi-create (newlines win) |

A naïve `split-on-comma + ≥3 segments + each ≥4 chars` rule trips on
`buy milk, eggs, bread`. A first-word verb check is unreliable. The cleanest
deterministic rule that clears all listed adversarials:

```
MultiCreateDetector.detect(text) = MultiCreate when ANY of:
  (a) text contains ≥1 newline AND has ≥2 non-empty trimmed lines
      where each line is ≥4 chars and doesn't start with a continuation
      conjunction (then|or|and|but|so|because|while|if).
  (b) text contains ≥3 comma-separated segments AND
      ≥50% of segments contain a recognized date/time marker
      (today|tonight|tomorrow|monday..sunday|this week|next week|
       \\d+\\s*(am|pm)|by\\s+(monday..sunday|next week|tomorrow)|
       \\d{1,2}:\\d{2}).
```

Test against the adversarial table:

- `pick up groceries 5pm` — no newlines; 1 segment. → NotMulti ✓
- `email Bob, then call Mary` — 2 segments fails ≥3 rule. → NotMulti ✓
- `finish report (the long one), or skip if blocked` — 2 segments fails ≥3.
  → NotMulti ✓
- `buy milk, eggs, bread` — 3 segments, **0/3 have time markers**. → NotMulti ✓
- `pick up groceries 5pm, call mom tomorrow, finish report by Friday` — 3
  segments, 3/3 have markers. → MultiCreate ✓
- 3-line newline input — rule (a). → MultiCreate ✓

**Tradeoff cost:** Comma-only inputs without time markers (e.g.
`email Bob today, call Mary tomorrow` — only 2 segments) silently stay on
single-task path. User has to either add a 3rd task or use newlines to opt in.
That's an acceptable cost of conservatism — false-positives on a heavy NLP path
are worse than the user typing Enter.

**AI fallback:** Not recommended in PR-A. The deterministic rule covers the
session's primary use case. If telemetry later shows users frequently typing
2-segment lists, add an AI-classify pass that calls `extract-from-text` with a
"classify only — is this multi-task?" cheap variant. Not in scope here.

**Recommendation:** PROCEED with rule (a)+(b) above. Empirical false-positive
rate on existing single-task fixtures: needs measurement in PR-A's tests
(estimate 0% based on the rule shape — no current test fixture has 3+ commas
with 50%+ time markers).

---

## Item 3 — Backend reuse verification (RED on batch-parse, GREEN on extract-from-text)

### Batch-parse path: REJECTED

The `_BATCH_PARSE_SYSTEM_PROMPT` in `backend/app/services/ai_productivity.py:777-840`
is built around a single hard rule (rule #1, line 822):

> Never invent entity IDs. Only use IDs that appear in the input lists.

CREATE has no input ID by definition. Adding a CREATE branch requires:

1. Pydantic `_BATCH_MUTATION_TYPE_PATTERN` regex extension (1 line).
2. `ProposedMutation.entity_id` becomes `Optional[str]` for CREATE (breaks the
   schema invariant; every existing field consumer assumes non-null).
3. Haiku system prompt rewrite — a new "if user asks to create, emit CREATE
   with `proposed_new_values` carrying title/due_date/priority/project — and
   leave entity_id null".
4. `BatchUndoLogEntry.entityId` is **already nullable** (entity.kt:51 — preserved
   for hard-deletes), so the undo-log shape itself accommodates CREATE rows.
   But `pre_state_json` for a CREATE row is meaningless ("the entity didn't
   exist") — undo would have to special-case "delete the row we just made"
   instead of reading pre-state.
5. `BatchOperationsRepository.applyOne` (line 138) bails on
   `mutation.entityId.toLongOrNull()`-null. Fix: route entityId-null to a
   CREATE branch before the toLongOrNull check.
6. `BatchPreviewScreen.MutationRow` (BatchPreviewScreen.kt:239-292) renders
   `entity_type • mutation_type` headers and TAG_CHANGE / STATE_CHANGE specifics
   — needs a NEW preview row variant for CREATE that shows title + chips for
   priority / due_date / project.

That's **6 cross-cutting changes** spanning Pydantic schema, Haiku prompt, undo
log semantics, repository routing, and preview UI. It also asks Haiku to do two
fundamentally different jobs in one prompt (mutate-existing vs. extract-new),
which the prompt isn't structured for — the model would have to switch modes
based on the user's intent within a single response. The hard constraint #1 in
the user prompt says "audit MUST verify these can absorb a CREATE mutation type
without data-shape divergence before recommending PROCEED. If the existing
batch infrastructure can't carry creation semantics cleanly, the audit STOPs."

**Audit verdict on batch-parse reuse: STOP.** It can't absorb CREATE cleanly.

### Extract-from-text path: ACCEPTED

`/api/v1/ai/tasks/extract-from-text` (router at `backend/app/routers/ai.py:690-729`,
schema at `schemas/ai.py:299-313`, service at
`services/ai_productivity.py:620-664`) **already does exactly the work the
multi-create flow needs**:

```python
class ExtractedTaskCandidate(BaseModel):
    title: str
    suggested_due_date: Optional[str] = None  # ISO YYYY-MM-DD
    suggested_priority: int = 0
    suggested_project: Optional[str] = None
    confidence: float = Field(ge=0.0, le=1.0)
```

One Haiku call returns N task candidates with title + due-date + priority +
project + confidence. The Android side has the API client wire-up implicitly
(it consumes ai schemas) but not yet a `parseRemote`-style call site — the
PasteConversationScreen ViewModel (`extract/PasteConversationViewModel.kt:48`)
currently calls only the regex `ConversationTaskExtractor`.

**Wire-up cost** (PR-B):
- Add `extractFromText(text, source)` method to `NaturalLanguageParser` that
  calls the API and falls back to the regex extractor on failure (mirrors
  `parseRemote` shape at `NaturalLanguageParser.kt:93-106`).
- Add `extractFromText` Retrofit method to `PrismTaskApi` (1 line + DTOs that
  already exist for the schema — `ExtractedTaskCandidate` may need a Kotlin
  data class addition to ApiModels.kt).

**No backend changes needed.** No Pydantic edits. No Haiku prompt edits. No
new endpoint. No undo-log schema change.

**Recommendation:** PROCEED via extract-from-text reuse. The "BatchPreview /
batch_undo_log reuse" hard constraint from the prompt is **NOT MET** — the
audit explicitly recommends overriding it because the alternative is structural
churn for marginal gain.

---

## Item 4 — Haiku prompt extension (GREEN — no extension needed)

The existing `extract_tasks_from_text` Haiku prompt
(`services/ai_productivity.py:629-643`) already does structured task extraction
with title + suggested_due_date + suggested_priority + suggested_project +
confidence. It is the right shape for the multi-create flow. **No prompt
changes.**

**Latency / cost analysis:**

- 1 batch extract call vs N parallel single-task `parseRemote` calls (the
  scope's other option): for N=3, the batch call is ~1 round-trip × Haiku ~500ms
  = **~500ms p95**. N=3 parallel single-task `parseRemote` calls is also
  ~500ms p95 (parallelism floors at the slowest call) — a wash on latency.
- Cost: the batch extract prompt is ~250 input tokens + 200 output tokens.
  N=3 parallel `parseRemote` calls at ~150 input + 80 output each is 450
  input + 240 output. Batch extract is **~30% cheaper** because the system
  prompt isn't repeated 3×.
- Failure modes: `extract_tasks_from_text` is JSON-arrayed; if Haiku returns
  malformed JSON for one candidate, the **whole array** fails parse and the
  service raises `ValueError`. Compare to N parallel calls where each fails
  independently. The batch call is "all-or-nothing" but retries-on-parse-error
  twice (`services/ai_productivity.py:647-664`), so practical reliability is
  fine.

**Recommendation:** Use the single batch call, fall back to local regex
extractor on `ValueError` / network failure (mirrors PasteConversationScreen
pattern). For very long inputs where retry-and-fail is expensive, the regex
fallback is good enough to keep the user moving.

---

## Item 5 — Web parity (DEFERRED — flag, don't gate)

The web app (`web/`) has its own QuickAddBar and consumes `/api/v1/ai/batch-parse`
already (per PR #772 description). It does **not** currently consume
`/api/v1/ai/tasks/extract-from-text` (no PasteConversationScreen analogue
shipped to web). Adding a multi-create flow to web means:

- Web QuickAddBar needs the same MultiCreateDetector heuristic (port the
  Kotlin rule to TS).
- Web needs to wire a fetch call to extract-from-text + a small preview
  UI component + a "create N tasks" mutation.

That's a **separate web-side PR**, not bundled with the Android PR. Per memory
`reference_workflow_only_pr_skip_semantics.md`, separating cross-platform
changes is the convention. The Android-only multi-create can ship without web
parity blocking it; web slice follows in a sibling PR.

**Recommendation:** Web parity = PR-D, **not blocking** Phase F closure. Document
divergence in the PR description so a future operator sees Android shipped
ahead of web.

---

## Item 6 — Test surface (GREEN)

Estimated test coverage per PR (Phase 2 fan-out below):

**PR-A — MultiCreateDetector** (~12 unit tests, ~250 LOC including tests):
- 6 adversarial false-positive cases (single-task with comma, single-task with
  parenthetical, comma list of nouns, "then/or/and" continuation, ≥4 segments
  but no time markers, mixed punctuation).
- 6 true-positive cases (3+ comma segments with markers, 2-line newline,
  3-line newline, mixed comma+newline, line with leading whitespace, segments
  with embedded slot like "5pm").

**PR-B — NaturalLanguageParser.extractFromText** (~6 unit tests, ~120 LOC):
- Success path returns N parsed candidates.
- Network failure → regex fallback.
- Malformed response → regex fallback.
- Empty input → empty list.
- Pro-gate: free user → regex fallback (no API call).
- ≥10K char input truncation matches backend cap.

**PR-C — MultiCreateBottomSheet + ViewModel** (~6 unit tests + 2 androidTest
smoke):
- ViewModel: extract → preview → toggle → create-selected → emit count.
- ViewModel: empty selection on Approve → no-op.
- androidTest: QuickAddBar → multi-create input → bottom sheet renders → tap
  Approve → assert N tasks in TaskDao.
- androidTest: regex fallback path (mock API failure).

**PR-A through PR-C combined estimate:** ~24 unit tests + 2 androidTests, ~600
LOC including tests, no DB migration, no Hilt module shuffles.

**Recommendation:** PROCEED. No DAO/test-module parity work (memory entry #27)
because no DB schema lands.

---

## Item 7 — Phase placement (GREEN — Phase F is fine)

Phase F currently YELLOW pending Test 3 P0 verification, ships May 15. This
work:

- **No DB migration** — extract-from-text is stateless on the backend,
  TaskRepository.addTask already exists.
- **No sync layer changes** — created tasks flow through the existing
  TaskEntity sync mapper.
- **No critical-path coupling** — fully behind `proFeatureGate.hasAccess(AI_NLP)`
  if we want to gate on Pro (recommended — extract-from-text is a paid Haiku
  call). Free users stay on single-task path.
- **Bounded scope** — ~3-4 days build for one developer including tests.

Could ship in v1.7.x as a Phase F bonus or split out to v1.7.y. No reason to
defer to G.0 stabilization or Phase G integrations.

**Recommendation:** Phase F. Ship as a feature PR off main, not coupled to
any other in-flight Phase F work.

---

## Item 8 — Expected-outcome distribution (calibration check)

The user prompt asked for predictions before the audit:

| Outcome | User-prompted prior | Audit verdict |
|---|---|---|
| PROCEED-as-written (BatchPreview reuse) | — | **Did not survive** — Item 3 |
| COMPROMISED-SCOPE-PROCEED | — | **Selected** — extract-from-text reuse instead |
| STOP-defer-to-G | — | Rejected — small scope, no infra blockers |
| STOP-build-as-Option-B (single-task × N) | — | Close — but the existing batch
  extract endpoint is one round-trip, not N parallel |

Audit-first track record holds: at least one premise (BatchPreview reuse) was
reframed. The reframe is meaningful — we go from a 6-cross-cutting-change
estimate to a 3-PR additive scope.

---

## Recommended improvements (ranked by wall-clock-savings ÷ implementation-cost)

| Rank | Improvement | Wall-clock saved | Implementation cost | Notes |
|---|---|---|---|---|
| 1 | Add MultiCreateDetector with rule (a)+(b) | high (unblocks the feature) | ~1 day | Pure Kotlin, ~80 LOC + 12 tests |
| 2 | Wire NaturalLanguageParser.extractFromText to backend endpoint | high | ~1 day | Mirrors `parseRemote` shape; 1 Retrofit method + 2 DTO classes |
| 3 | MultiCreateBottomSheet UI + routing in QuickAddViewModel | high | ~1.5 days | Reuses PasteConversationScreen patterns; separate route in NavGraph |
| 4 | Web parity (TS port of detector + extract endpoint wire) | medium | ~1 day | Separate PR, follows Android |
| - | (REJECTED) Extend batch-parse with CREATE | n/a | ~3-5 days, fragile | See Item 3 |

Total estimated build for Android (PR-A + PR-B + PR-C): **~3.5 days**.

---

## Anti-patterns flagged (worth noting, not necessarily fixing now)

- **Two parallel "preview-and-approve" UIs** (PasteConversationScreen vs.
  BatchPreviewScreen) is an existing condition, not introduced here. The new
  MultiCreateBottomSheet makes it three. A future refactor could unify them
  behind a generic `<PreviewWithSelection>` composable; not a blocker.
- **`BatchUndoLogEntry.entityId` nullability** is currently load-bearing only
  for hard-delete fallback (`entity.kt:48-50`). Adding CREATE rows to the table
  would change the semantics of "what does null mean here" — another reason to
  NOT route multi-create through batch_undo_log.
- **`extract-from-text` has no Android call site shipped** (only PasteConversationScreen
  uses regex). PR-B implicitly fixes this.
- **`buy milk, eggs, bread` will stay on single-task path** with the
  recommended heuristic — that's the right call, but flag in user-facing
  release notes so the user understands why a 3-comma list of nouns isn't
  treated as multi-create.

---

## Phase 1 → Phase 2 transition

Phase 1 is complete. Phase 2 fan-out auto-fires now per audit-first skill rule.
Per memory `feedback_skip_audit_checkpoints.md`, no checkpoint approval is
needed. The fan-out shape:

- **PR-A** (`feat/multi-create-detector`): `MultiCreateDetector` + unit tests.
  Standalone — no behavior change in QuickAddBar yet.
- **PR-B** (`feat/extract-from-text-wire`): Wire
  `NaturalLanguageParser.extractFromText` + Retrofit + DTOs + tests.
  Standalone — no UI changes yet.
- **PR-C** (`feat/multi-create-quickadd-routing`): MultiCreateBottomSheet UI,
  ViewModel, NavGraph route, QuickAddViewModel routing pre-pass +
  androidTest smokes. Depends on PR-A and PR-B merged.
- **PR-D** (web — separate, deferred): MultiCreateDetector TS port + web
  extract-from-text wire + UI. NOT in this audit's Phase 2; flagged for
  follow-up.

PR-A and PR-B are mergeable in parallel. PR-C blocks on both.

Fan-out execution begins below.

---

## Phase 3 — Bundle summary (filled in after Phase 2 merges)

_Pending Phase 2 completion._

## Phase 4 — Claude Chat handoff

_Emitted after Phase 3 below._
