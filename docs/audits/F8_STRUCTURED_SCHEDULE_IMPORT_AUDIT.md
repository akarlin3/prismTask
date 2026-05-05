# F8_STRUCTURED_SCHEDULE_IMPORT_AUDIT.md

**Phase 1 audit — F.8 "Import Project from Schedule File" feature.**
**Disposition: BLOCKED. STOP-A + STOP-D + premise D.3 broken.** Phase 2 does
NOT auto-fire. Operator decision needed before implementation.

## Operator decision + risk acknowledgment (recap from prompt)

- F.8 placement: post-launch power-user feature (NOT launch-gate).
- Formats supported: JSX + JSON + YAML + Markdown + CSV (5 formats).
- Parsing: LLM-only (no AST / sandbox).
- Phase 2 fan-out: TBD — Phase 1 was supposed to verdict single-PR vs multi-PR.
  Audit found the question is moot until scope re-decision (see Verdicts).

---

## Recon findings (A.1 – A.5)

### A.1 — Drive-by detection (RED — STOP-A FIRES)

`git log --all -S "projectImport"` / `"/projects/import"` / `"ProjectImportService"` —
all empty for the literal strings the prompt suggested. **But** broader sweeps for
"import / parse / schedule" surface load-bearing existing infrastructure that the
prompt's hypothesis duplicates wholesale:

| Endpoint | File | Shape |
| --- | --- | --- |
| `POST /api/v1/tasks/parse-import` | `backend/app/routers/tasks.py:319-384` | JSX/text content (string) → `{name, items[]}` with subtasks |
| `POST /api/v1/tasks/parse-checklist` | `backend/app/routers/tasks.py:387-489` | JSX/text/syllabus → `{course, project, tags[], tasks[]}` with subtasks |
| `POST /api/v1/syllabus/parse` | `backend/app/routers/syllabus.py:119-257` | PDF UploadFile → `{course_name, tasks, events, recurring_schedule}` |
| `POST /api/v1/syllabus/confirm` | `backend/app/routers/syllabus.py:260-397` | extracted JSON → creates `Goal → Project → Task` server-side |

The `parse-import` system prompt explicitly says **"JSX/TSX file or a text list"** —
the prompt's "JSX support" requirement is shipped today. The `parse-checklist`
prompt at `tasks.py:414` is similarly broad ("JSX/TSX file, a text list,
schedule, syllabus, or other content"). Claude Haiku is fundamentally
format-agnostic: a JSON / YAML / Markdown / CSV paste already parses through
either endpoint without code changes. **Re-recon test: `parse-import` is
likely already a working multi-format extractor today, undocumented.**

Android wires the result via:
- `ClaudeParserService.kt:25-50` — calls `parse-import`, returns
  `ParsedTodoList` for the regex-fallback `TodoListParser`.
- `ChecklistParser.kt` — calls `parse-checklist`, returns the richer course
  bundle (used by `SchoolworkViewModel`).

Web wires:
- `web/src/utils/import.ts` — JSON-file → backend `exportApi.importJson` (this
  is the **full PrismTask JSON export/restore path**, not the AI-extraction
  path; orthogonal feature).

**Verdict: STOP-A — at least 70% of the F.8 prompt's stated scope is already
shipped under different naming.** The prompt's PIS schema is a superset of
`ParseChecklistResponse`. Re-scope before any code change.

### A.2 — Parked-branch sweep (GREEN)

`git branch -r | grep -iE "import|upload|extract|schedule"` returns only
`origin/claude/schedule-import-audit-4TeUy` (this branch). No parked
implementation work to coordinate.

### A.3 — Existing AI endpoint shape (load-bearing)

**SDK / client.** `anthropic` Python SDK is in use. Two call sites of note:

- Sync client: `backend/app/services/ai_productivity.py:36-44` (`_get_client`
  returns `anthropic.Anthropic(api_key=...)`). Used by Eisenhower / Pomodoro /
  briefing / planner / etc.
- Async client: `backend/app/routers/syllabus.py:178-190`
  (`anthropic.AsyncAnthropic(api_key=...)`). Used by parse-syllabus.
- Sync inline client: `backend/app/routers/tasks.py:296-309` (`_call_haiku`,
  inline single-shot helper). Used by parse-import / parse-checklist.

Both Haiku and Sonnet are wired (`MODEL_HAIKU = "claude-haiku-4-5-20251001"`,
`MODEL_SONNET = "claude-sonnet-4-20250514"`); selection is per-feature with
`SONNET_FEATURES = {"weekly_planner", "monthly_review"}`. Everything else
(including all existing import/extract endpoints) is Haiku.

**Request shape.** Authenticated JSON body `{content: str}` with a 50,000-char
cap per `app/schemas/import_parse.py:15` (`MAX_PARSE_CONTENT_LENGTH`). The
syllabus endpoint is the file-upload analogue (`UploadFile = File(...)`,
10 MB cap, PDF-only).

**Response shape.** Strict JSON via Pydantic models (`ParseImportResponse`,
`ParseChecklistResponse`, `SyllabusParseResponse`). Markdown-fence stripping
happens in `_call_haiku` / `_parse_ai_json` helpers — same logic in
`tasks.py:312-316`, `syllabus.py:86-94`, `ai_productivity.py:48-58`.
**Drive-by note**: this helper is duplicated three times. Out of audit scope
but worth remembering for future consolidation.

**Error handling.** 451 (opt-out), 403 (not Pro), 429 (rate-limited),
503 (no API key), 502 (Claude call failed), 422 (invalid input).

**Retry policy.** Eisenhower retries once on JSONDecodeError; import /
syllabus do not. F.8 prompt's "max 1 retry" is consistent with either.

**Quota / rate-limit infra.**

| Limiter | Class | Limit | Source file |
| --- | --- | --- | --- |
| `parse_rate_limiter` | `RateLimiter` | 20 / IP / minute | `tasks.py:30` (unauthenticated demo only) |
| `import_parse_rate_limiter` | `UserRateLimiter` | 10 / user / hour | `rate_limit.py:148` |
| `daily_ai_rate_limiter` | `DailyAIRateLimiter` | 100 / Pro / day, 0 / Free | `rate_limit.py:107` |

The 100/day cap is system-wide for Pro AI calls, so any new endpoint inherits
it. The hourly per-user limit is endpoint-specific and is the primary lever
for adjusting cost ceiling.

### A.4 — File upload infrastructure (YELLOW — partial)

**Backend (GREEN).** Established multipart pattern. `UploadFile = File(...)`
appears in `syllabus.py`, `export.py`, `app_update.py`. No new infra needed.

**Android (RED).** `ActivityResultContracts.GetContent()` is used in exactly
**one** place: `ui/screens/feedback/BugReportScreen.kt:102` (image-only,
single file, no MIME filtering for project-import). No reusable file-picker
composable. F.8 would need to write one — small scope (~50 LOC) but real.

**Web (GREEN-ish).** `web/src/utils/import.ts:30-49` has a JSON file picker
with `FileReader` + `JSON.parse` for the export-restore path. Trivially
adaptable for new formats since the upload happens via `<input type="file">`.

### A.5 — Sibling extraction primitives (RED — STOP-D FIRES)

The (e)-axis sweep surfaces a triplet of sibling extractors that already
implement the F.8 prompt's hypothesis:

1. **`parse-import`** — flat todo + subtasks (Android: `TodoListParser`'s
   AI fallback). Closest to the F.8 prompt's hypothesised PIS for the
   "small file" case.
2. **`parse-checklist`** — course + project + tags + nested tasks
   (Android: `ChecklistParser` → `SchoolworkViewModel`'s syllabus import).
   Closest to the F.8 prompt's hypothesised PIS for the "rich file" case.
3. **`syllabus/parse` + `syllabus/confirm`** — the *only* existing path
   that **creates server-side entities** from extracted output. Two-step
   (parse, then confirm) flow; second step takes JSON not file content.
4. **`ConversationTaskExtractor`** (Android, `domain/usecase/`) — pulls
   task list out of chat transcripts. Local LLM-free regex extractor;
   mentioned in CLAUDE.md but does not hit Claude.

**Verdict: STOP-D — F.8 should EXTEND `parse-checklist`, not duplicate
it.** The remaining gap (server-side entity creation, phases / risks /
anchors / dependencies in the response) is much smaller than building
a new endpoint from scratch.

---

## Existing AI endpoint pattern — paste-ready summary

If F.8 ships, the canonical shape is:

```python
@router.post(
    "/projects/import",
    response_model=ProjectImportResponse,
    dependencies=[Depends(require_ai_features_enabled)],  # PII opt-out
)
async def import_project(
    file: UploadFile = File(...),  # OR data: ProjectImportRequest
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    if await resolve_effective_tier(current_user, db) != "PRO":
        raise HTTPException(403, "Pro required")
    import_parse_rate_limiter.check(current_user.id)  # 10/user/hour
    # ... read+validate content (cap 50_000 chars or 10 MB binary)
    # ... call Claude Haiku via _call_haiku helper
    # ... parse JSON, validate via Pydantic, create entities
```

This is the operator-locked pattern. Any deviation (e.g. introducing a new
SDK, skipping the AI gate, a new tier system) is a separate audit.

---

## File upload infrastructure summary

| Surface | Status | Notes |
| --- | --- | --- |
| Backend multipart | ✅ shipped | `syllabus.py` template; reuse |
| Android file picker | ⚠ ~50 LOC stub | only `BugReport` uses GetContent today |
| Web file picker | ✅ shipped | `web/src/utils/import.ts` |
| File-format detection | ❌ require explicit `format` field per prompt anti-pattern |

---

## Implementation hypothesis verdicts

### B.1 — Project Import Schema (PIS) shape (RED — premise broken)

**Premise D.3 broken.** The prompt asserts "Project + Phase + Risk +
ExternalAnchor + TaskDependency entities all exist server-side and are
creatable via existing service layer". They do not.

`backend/app/models.py` exhaustive class list (line 24-781): Goal, Project,
Tag, Task, TaskTag, Attachment, Habit, HabitCompletion, TaskTemplate,
ProjectMember, ProjectInvite, ActivityLog, TaskComment, AppRelease,
SuggestedTask, IntegrationConfig, CalendarSyncSettings, BugReportModel,
NdPreferencesModel, Medication, MedicationSlot, MedicationTierState,
MedicationLogEvent, DailyEssentialSlotCompletion, RtdnEvent, BetaCode,
BetaCodeRedemption, User. **No Phase, no Risk, no ExternalAnchor, no
TaskDependency, no Milestone.**

These primitives exist **client-side only** (`ProjectPhaseEntity`,
`ProjectRiskEntity`, `ExternalAnchorEntity`, `TaskDependencyEntity`,
`MilestoneEntity` in `app/src/main/java/.../data/local/entity/`, registered
in `PrismTaskDatabase.kt:131-145`). Per CLAUDE.md the web "has full
Project Roadmap including phases/risks/anchors/dependencies post-PR-#1120"
— but that lives in client-side storage / per-device sync, not in a
server-validated entity tree.

**Two paths forward, both have real cost:**

- **Path A — Client-side richer extraction.** Extend `parse-checklist`
  response to include phases / risks / anchors / dependencies, and have
  the Android client materialise them into Room entities locally. Requires
  no backend schema change. ~400 LOC. *But*: cross-device parity through
  whatever sync layer those Android entities use today (verify before
  Phase 2 — they may already be Firestore-synced).
- **Path B — Server-side entity creation.** Add Phase / Risk / Anchor /
  Dependency / Milestone SQLAlchemy models, migrations, schemas, repo
  methods, sync wiring on Android + web. Then a richer `parse-project`
  endpoint that returns the PIS *and* persists the tree. ~1500–2500 LOC
  across backend + Android + web. **Exceeds STOP-F's 1500 ceiling.**

**Recommendation: STOP and operator-decide path before Phase 2.**

Sub-verdicts (deferred until path is locked):
- Pydantic vs JSON Schema: Pydantic, matches existing ParseChecklistResponse.
- Title-refs vs ID-refs: title-refs in payload, server resolves to IDs.
- Unresolvable refs: warning, not error (prompt anti-pattern: surface
  gracefully, do not fail extraction wholesale).
- Partial imports: allow.

### B.2 — Backend Claude API extraction (GREEN — pattern is clear)

If Path A: extend `parse-checklist` system prompt + `ParseChecklistResponse`
schema. ~150 LOC.

If Path B: new `POST /projects/import-from-file` endpoint. Reuse
`_call_haiku` helper (extract to a shared module first — currently
duplicated in tasks.py, syllabus.py, ai_productivity.py). ~400 LOC backend.

**Model: Haiku.** Sonnet is reserved for `weekly_planner` / `monthly_review`
per `ai_productivity.py:21`. Do not promote without operator approval —
~12× cost increase.

**Prompt strategy:** single-shot, system-prompt-with-PIS-schema-embedded,
matching the `parse-checklist` shape. Multi-pass extract-then-validate is
not justified for this scope (cost ~2×, marginal accuracy gain on JSX which
is the high-confidence format).

**Retry: zero** — match the `parse-import` pattern (502 on first failure).
The Eisenhower-style retry-once is overkill; users can retry from UI.

### B.3 — Tier-gating + rate limits (RED — prompt's tier model is wrong)

**Prompt premise wrong.** Prompt assumes 3-tier system ("Free", "Pro",
"Premium+"). Actual system is binary `FREE | PRO` per
`rate_limit.py:62-65` (`TIER_LIMITS = {"PRO": 100, "FREE": 0}`) and
`beta_codes.py:58` (`resolve_effective_tier` returns `"PRO"` or `"FREE"`).

**Recommendation:** mirror existing `parse-import` exactly:
- Pro-only (Free → 403 "Project import requires Pro").
- Reuse `import_parse_rate_limiter` (10/user/hour). Adding a separate
  limiter for project-import would split the budget without justification.
- Inherit `daily_ai_rate_limiter` 100/day Pro cap.
- `MAX_PARSE_CONTENT_LENGTH = 50_000` chars (string body) OR 10 MB
  (multipart). One or the other, not both.

Cost ceiling check (STOP-E): 100 Pro imports/day × ~$0.013/import (50k
chars in Haiku ≈ ~$0.003 input + ~$0.01 output = ~$0.013) ≈ **$1.30/Pro/day
worst case**, ~$40/Pro/month worst case. At $7.99/mo MRR per Pro this
torches the margin if any user actually maxes the cap. **Recommend:
re-baseline the daily Pro cap to 25/day for project-import specifically**
(via a new dedicated limiter, NOT shared) to keep the worst-case
margin sane. Operator decision.

### B.4 — Android UI (YELLOW — small but real)

Per Path A (client-side extraction): no new screen needed. Reuse
`SchoolworkScreen` → `SyllabusReviewScreen` flow as the template; bind
to a new "Import Project" button on `ProjectListScreen`.

Per Path B (server-side): same UI, plus a sync trigger after server-side
project tree creation.

File picker scope: ~50 LOC. ~150 LOC of ViewModel state machine
(`Idle → Picking → Uploading → Reviewing → Confirming → Done | Failed`).
Reuse `SyllabusViewModel` shape.

### B.5 — Web UI (DEFERRED)

Default per prompt: Android-only first, web as follow-up. Web file picker
infra is trivial (~30 LOC) — but creating a pickin / preview / confirm
flow on the web Project page is ~300 LOC. Defer; re-trigger when operator
asks for parity.

### B.6 — Tests (GREEN — enumerated)

Backend unit:
- `MAX_PARSE_CONTENT_LENGTH` enforcement (over-cap → 422)
- Pydantic PIS validation (each constraint violation)
- Each format fixture (JSX / JSON / YAML / Markdown / CSV) round-trips through
  a mocked Claude response → Pydantic → entity create

Backend integration: real Claude call against minimal JSX fixture
(gated by `RUN_AI_INTEGRATION_TESTS=1` env, optional in CI to keep cost
controllable).

Android: ViewModel state machine; file picker integration tests; reuse
`SyllabusViewModel`'s test shape.

---

## Cost estimate (STOP-E review)

Per-import on Haiku: ~$0.003 input (50k chars ≈ 12.5k tokens × $0.25/1M)
+ ~$0.010 output (8k tokens × $1.25/1M) = **~$0.013/import**.

At the existing shared 100/day Pro AI cap: $1.30/Pro/day = ~$40/Pro/mo
worst case (shared with Eisenhower / Pomodoro / briefing / planner —
maxing import displaces them). Dedicated 25/day cap = ~$10/Pro/mo.
STOP-E threshold ($0.10/import) — **GREEN.** Cost concern is volume,
not per-call.

---

## STOP-conditions evaluated

| ID | Condition | Status | Action |
| --- | --- | --- | --- |
| STOP-A | Existing import / extraction work | **FIRES** | Re-scope; paper-close 70% of prompt scope |
| STOP-B | AI endpoint pattern incompatible | GREEN | Pattern is highly compatible; reuse |
| STOP-C | PIS schema unresolvable ambiguity | YELLOW | Manageable once Path A/B locked |
| STOP-D | Sibling extraction primitive exists | **FIRES** | Extend `parse-checklist`, do not duplicate |
| STOP-E | Per-import cost > $0.10 | GREEN | $0.013/import, well under |
| STOP-F | Aggregate Phase 2 LOC > 1500 | **FIRES** under Path B | Path A keeps under; Path B exceeds |
| STOP-G | Active PRs touching surfaces | GREEN | No open PRs |
| STOP-D.3 | Server-side entities exist | **FIRES (premise broken)** | Operator decides Path A vs B |

---

## Phase 2 scope (CONDITIONAL)

**Phase 2 cannot auto-fire.** Two STOPs (A, D) plus broken D.3 premise plus
wrong tier-tier hypothesis (B.3) all require operator input.

If operator picks **Path A** (client-side richer extraction):

| File | Δ LOC est. |
| --- | --- |
| `backend/app/schemas/import_parse.py` (extend `ParseChecklistResponse` with `phases / risks / anchors / dependencies`) | +60 |
| `backend/app/routers/tasks.py` (extend `parse-checklist` system prompt) | +30 |
| `backend/tests/test_parse_checklist.py` (new) | +200 |
| `app/.../data/remote/api/ApiModels.kt` (extend response DTOs) | +40 |
| `app/.../domain/usecase/ChecklistParser.kt` (extend mapper) | +60 |
| `app/.../ui/screens/projects/ImportProjectScreen.kt` (new) | +250 |
| `app/.../ui/screens/projects/ImportProjectViewModel.kt` (new) | +200 |
| `app/.../ui/navigation/NavGraph.kt` (route) | +10 |
| Android tests | +150 |
| **Total** | **~1000 LOC** — single-PR feasible |

If operator picks **Path B** (server-side entities):

| Domain | Δ LOC est. |
| --- | --- |
| `models.py` + Alembic migration (5 entities) | +300 |
| Schemas | +200 |
| New `routers/projects/import.py` | +400 |
| Server-side service layer + repo | +500 |
| Android sync wiring | +500 |
| Web parity (mandatory if backend is canonical) | +500 |
| Tests | +400 |
| **Total** | **~2800 LOC** — exceeds STOP-F; mandates fan-out |

---

## Memory #15 conventions check

Project import doesn't add a new task-dim column. Memory #15 spirit applies
to creation pathway:
- Path A: Android client materialises Room entities → existing sync layer
  handles cross-device. **Verify before Phase 2:** are
  `ProjectPhaseEntity` / `ProjectRiskEntity` / etc. actually
  Firestore-synced today? If not, Path A creates phantom local-only data.
- Path B: server creates entities; Android pulls via existing sync. Cleaner
  conceptually, but the missing primitives are exactly what Path B has to
  build first.

---

## Premise verification (D.1 – D.5)

| Premise | Status | Evidence |
| --- | --- | --- |
| D.1 — FastAPI + Postgres + AI endpoint pattern | ✅ | `tasks.py`, `syllabus.py`, `ai_productivity.py` |
| D.2 — `effective_tier` supports per-feature gating | ✅ | `beta_codes.py:58`, used in `syllabus.py:130` |
| D.3 — Project + Phase + Risk + Anchor + Dependency exist server-side | **❌ BROKEN** | Only `Project` server-side; rest are Android-only Room entities |
| D.4 — No prior import / extraction work exists | **❌ BROKEN** | `parse-import`, `parse-checklist`, `syllabus/parse` all shipped |
| D.5 — `anthropic` Python SDK is the client | ✅ | sync + async usage in 3 modules |

---

## Deferred (NOT auto-filed per memory #30)

- **Web file-picker UI for project import** — re-trigger when operator
  asks for parity OR after Path A/B Android ships.
- **`_call_haiku` / `_parse_ai_json` consolidation** — duplicated in
  3 modules. Pure refactor, no behaviour change. Surfaces during this
  audit but is orthogonal scope. Re-trigger as a chore PR.
- **`ConversationTaskExtractor` Claude upgrade** — currently regex-only on
  Android. (e)-axis sibling. Out of F.8 scope; capture if operator wants
  parity later.
- **Multi-format detection** — prompt anti-pattern says "require explicit
  format field". If user research shows manual format-pick is friction,
  re-scope.

---

## Open questions for operator

1. **Path A or Path B?** (Critical — gates everything else.)
2. If **Path A**: are the Android Phase / Risk / Anchor / Dependency
   entities Firestore-synced today, or local-only? Audit doc could not
   verify in scope-time. Determines whether Path A actually delivers the
   "user sees the new project on next sync" UX the prompt describes.
3. **Tier model**: prompt assumed 3-tier (Free / Pro / Premium+); reality
   is binary FREE / PRO. Use existing 10/hr + 100/day Pro caps, or
   spin up a dedicated project-import limiter at 25/day?
4. **Cost ceiling concern**: at $40/Pro/mo worst case (shared cap) is
   project-import allowed to displace other Pro AI features (briefing,
   planner, etc.) when a user maxes it? If no → dedicated 25/day cap.
5. **Multi-format scope**: existing `parse-checklist` handles JSX / text
   today and Claude is fundamentally format-agnostic — recon strongly
   suggests JSON / YAML / Markdown / CSV already work without code
   changes. Should Phase 2 include test-fixture coverage for all 5
   formats but NO prompt changes? (Operator: this is the cheapest
   delivery of "5 format support".)
6. **Two-step preview/confirm UX**: existing syllabus uses parse →
   confirm. Existing parse-checklist returns extraction without
   creating entities (Android creates locally). F.8 prompt forbids
   "second API call" — clarification: only second *Claude* call
   forbidden, second *DB* call (confirm step) is fine?

---

## Ranked improvement table (Phase 1 outputs)

Sorted by **wall-clock-savings ÷ implementation-cost**.

| Rank | Item | Effort | Value | Verdict |
| --- | --- | --- | --- | --- |
| 1 | **Operator decision A vs B** | 0 (decision) | Unblocks everything | NEEDED NOW |
| 2 | Path A test fixtures for 5 formats against existing `parse-checklist` (verify multi-format hypothesis WITHOUT code changes) | XS (~50 LOC test) | Confirms or refutes "70% shipped" claim | RECOMMENDED PRE-DECISION |
| 3 | Confirm Phase/Risk/Anchor/Dependency Firestore sync status | XS (recon) | Critical for Path A viability | NEEDED PRE-DECISION |
| 4 | Path A implementation (if chosen) | M (~1000 LOC, single PR) | Closes F.8 | STAGED |
| 5 | Path B implementation (if chosen) | XL (~2800 LOC, multi-PR) | Closes F.8 + canonicalises server schema | STAGED |
| 6 | `_call_haiku` / `_parse_ai_json` consolidation | S (~150 LOC) | Removes 3-way duplication | DEFERRED |
| 7 | Web import-project parity | M (~700 LOC) | Cross-platform UX | DEFERRED |

---

## Anti-patterns surfaced (worth flagging, not necessarily fixing)

1. **Three-way duplication of `_parse_ai_json` / Claude call helper.**
   `tasks.py:296-316`, `syllabus.py:86-94`, `ai_productivity.py:36-58`.
   Each is slightly different (sync vs async, different fence-stripping
   tolerance). Out of F.8 scope; surface for later refactor.
2. **AI gate is named like a feature gate but is a PII opt-out gate.**
   `require_ai_features_enabled` returns 451 only when the *client* sets
   `X-PrismTask-AI-Features: disabled` — it does NOT block based on
   user setting persisted server-side. The naming invites confusion. Out
   of F.8 scope; documentation tweak in the docstring is the right
   minimal fix (already comprehensive in `ai_gate.py:1-19`).
3. **Tier system is binary but rate-limit comment uses "PRO" as if there
   were tiers above.** `rate_limit.py:55` mentions "covers both monthly
   and annual billing" — fine. The prompt's invented "Premium+" tier
   does not exist; future audits should premise-verify against
   `TIER_LIMITS` directly.
4. **Schoolwork import lives in two places.** `parse-checklist`
   (synchronous-style, returns extraction, client creates entities) and
   `syllabus/parse + /confirm` (two-step, server creates entities).
   Both ship and both work — but a future contributor will have to pick
   one. Document the chosen pattern when F.8 lands.

---

## Phase 2 status

**Originally BLOCKED.** Operator unblocked mid-session by picking Path A
and asking that nothing be deferred. Phase 2 work shipped in the same PR
as the audit doc; see Phase 3 below for what landed.

Operator decisions taken on the 6 open questions:

1. Path A (extend `parse-checklist`, client-side, ~1000 LOC).
2. Firestore sync verified GREEN — `SyncService.kt` + `SyncMapper.kt`
   already push/pull/delta-sync `ProjectPhaseEntity`, `ProjectRiskEntity`,
   `MilestoneEntity`, `TaskDependencyEntity`, `ExternalAnchorEntity`. No
   sync-layer change needed.
3. Tier-gating: keep existing 10/hr + 100/Pro/day shared cap (parse-checklist
   is the existing surface; reusing its limiter avoids splitting budget).
4. Cost displacement: moot since (3) keeps existing cap.
5. Multi-format: ship test fixtures + integration test gate. Implemented.
6. "No second API call" interpreted as "no second Claude call" — the
   parse + materialise flow uses one Claude call followed by DB inserts.

---

## Phase 3 — Bundle summary

| PR | Status | Description |
| --- | --- | --- |
| #1135 | Open (this audit + Phase 2) | Audit doc + Path A implementation: backend schema + prompt + tests + fixtures + Android DTO + ChecklistParser + ProjectListViewModel materialisation |

**Re-baselined wall-clock estimate:** Path A ~1 working day single PR;
Path B ~3–5 working days fan-out (5+ PRs). Includes server migrations,
Android sync wiring, and web parity.

**Memory candidates (pending second data-point):**
- "F.8 prompt assumed multi-tier Pro/Premium+; actual is binary FREE/PRO
  per `rate_limit.py:62`." → Worth memorising **only if a second audit
  premises 3-tier**. Single occurrence, defer.
- "AI / extraction premise verification must enumerate
  `backend/app/models.py` directly — CLAUDE.md describes Android-side
  Room entities that are not server-mirrored." → Specific instance of
  the existing memory #22 (premise-verify bidirectionally). No new
  entry needed.

**Schedule for next audit:** triggered by operator answering Path A vs B.
This audit's findings should be re-checked at that time (codebase moves
fast; any of the existing endpoints or rate limits could shift).
