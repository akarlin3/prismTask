# PII Egress Audit — Backend ↔ Anthropic Haiku/Sonnet

**Author:** akarlin3 (Avery)
**Date:** 2026-04-25
**Scope:** All backend code paths that ship user data to Anthropic's API
**Trigger:** PR #773's audit phase flagged `backend/app/services/ai_productivity.py:777,842` as shipping medication names to Haiku
**Phase F launch beta:** 2026-05-15 — this audit is a P0 blocker

---

## TL;DR — Recommendation

**PROCEED with Option C — disclose-and-ship.** Update privacy policy +
Data Safety form + Settings opt-out toggle in a follow-up PR, then ship Phase F.

Initiate **Option D — ZDR + BAA contracting with Anthropic Sales** in parallel
as the post-launch hardening track.

**Option A (tokenization) is INFEASIBLE backend-only** for the load-bearing
call site (`parse_batch_command`) because the medication name in the prompt is
exactly what lets Haiku match the user's command (`"Skip my Adderall today"`)
to the entity ID. Tokenization would require client-side entity resolution
before AI parsing — a substantial Android + web change that defers Phase F.
Section 5A documents the constraint.

This is a *premise nuance*, not a STOP. The original PR #773 framing was
"medication names are HIPAA-adjacent → P0 blocker." That framing is partially
incorrect — PrismTask is a productivity app, not a HIPAA Covered Entity, and
"HIPAA-adjacent" is not a regulatory category. **However**, the underlying
privacy concern is still valid via three other framings — Play Console health-
data declarations, GDPR Article 9 special-category data, and user trust — so
the audit doesn't recommend ignoring the issue. See Section 4 for the
re-framing.

Because the recommendation is **not** Option A, per the audit's hard rule the
PoC branch is NOT being created in this session. Decision required before
Phase 2 work proceeds.

---

## Section 1 — Endpoint Inventory (Full Sweep)

### Method

`grep` + import-graph trace across `backend/`. Every file that imports
`anthropic` or calls `client.messages.create` was opened and read end-to-end.
Test files (`backend/tests/`) excluded — only production code that egresses
user data is in scope.

### Production call sites (8 unique)

| # | File | Line | Function / endpoint | Model | Reachable from |
|---|------|------|---------------------|-------|----------------|
| 1 | `backend/app/services/ai_productivity.py` | 88 | `categorize_eisenhower` | Haiku | `routers/ai.py:143` (POST `/ai/eisenhower/categorize`) |
| 2 | `backend/app/services/ai_productivity.py` | 151 | `classify_eisenhower_text` | Haiku | `routers/ai.py:196` (POST `/ai/eisenhower/classify-text`) |
| 3 | `backend/app/services/ai_productivity.py` | 210 | `plan_pomodoro` | Haiku | `routers/ai.py:242` (POST `/ai/pomodoro/plan`) |
| 4 | `backend/app/services/ai_productivity.py` | 263 | `generate_daily_briefing` | Haiku | `routers/ai.py:358` (POST `/ai/briefing/daily`) |
| 5 | `backend/app/services/ai_productivity.py` | 310 | `generate_weekly_plan` | **Sonnet** | `routers/ai.py:416` (POST `/ai/planner/weekly`) |
| 6 | `backend/app/services/ai_productivity.py` | 434 | `generate_time_blocks` | Haiku | `routers/ai.py:534` (POST `/ai/time-block`) |
| 7 | `backend/app/services/ai_productivity.py` | 481 | `analyze_habit_correlations` | Haiku | analytics router (Pro) |
| 8 | `backend/app/services/ai_productivity.py` | 598 | `generate_weekly_review` | **Sonnet** (`monthly_review` flag) | `routers/ai.py:639` (POST `/ai/review/weekly`) |
| 9 | `backend/app/services/ai_productivity.py` | 649 | `extract_tasks_from_text` | Haiku | `routers/ai.py:691` (POST `/ai/extract`) |
| 10 | `backend/app/services/ai_productivity.py` | 760 | `generate_pomodoro_coaching` | Haiku | `routers/ai.py:279` (POST `/ai/pomodoro/coaching`) |
| 11 | `backend/app/services/ai_productivity.py` | 888 | **`parse_batch_command`** ← **PR #773 flagged** | Haiku | `routers/ai.py:742` (POST `/ai/batch-parse`) |
| 12 | `backend/app/services/nlp_parser.py` | 72 | `parse_task_input` | Haiku | `routers/tasks.py:223` (POST `/tasks/parse`) — **unauthenticated** |
| 13 | `backend/app/routers/tasks.py` | 262 | `_call_haiku` (used by `parse-import` + `parse-checklist`) | Haiku | POST `/tasks/parse-import`, POST `/tasks/parse-checklist` |
| 14 | `backend/app/routers/syllabus.py` | 173 | `parse_syllabus` | Haiku | POST `/syllabus/parse` (Pro-gated) |
| 15 | `backend/app/services/integrations/gmail_integration.py` | 227 | `scan_gmail` | Haiku | `routers/integrations.py` (Pro + opt-in) |

(Numbering is per-prompt-shape; #1–#11 share the `_get_client` helper inside `ai_productivity.py`.)

### Files NOT calling Anthropic (verified)

- `backend/app/routers/medications.py` — no LLM (CRUD + reminder logic only)
- `backend/app/services/integrations/calendar_integration.py` — no LLM (Google Calendar API only)
- `backend/app/services/integrations/base.py`, `token_crypto.py` — no LLM
- All other routers in `backend/app/routers/` — verified by `grep`

### Indirect call paths

Every `messages.create` call is reachable from a user-initiated FastAPI
endpoint. There are no background workers in the backend that call
Anthropic — the equivalent (e.g. `OverloadCheckWorker`, `WeeklySummaryWorker`)
runs on Android, not on the backend. Confirmed by `Glob` on
`backend/app/workers/**` (does not exist).

---

## Section 2 — What Actually Gets Sent

The 15 call sites cluster into three payload-shape groups. For each, an
abbreviated example of what transits to Anthropic in the user message body.

### 2A — Site #11 `parse_batch_command` — the PR #773 flag

**Schema:** `BatchParseRequest` → `BatchUserContext` (`backend/app/schemas/ai.py:407–418`)
shipped as JSON in the user-message content. Includes:

- `command_text: str` — the user's raw natural-language command (e.g. `"Skip my Adderall today"`)
- `today: str`, `timezone: str`
- `tasks: list[BatchTaskContext]` — id, title, due_date, scheduled_start_time, priority, project, tags, life_category, is_completed
- `habits: list[BatchHabitContext]` — id, name, is_archived
- `projects: list[BatchProjectContext]` — id, name, status
- **`medications: list[BatchMedicationContext]` — id, name** ← P0 surface

Confirmed by reading `BatchMedicationContext` (`schemas/ai.py:402-404`):

```python
class BatchMedicationContext(BaseModel):
    id: str
    name: str
```

The router (`routers/ai.py:739`) does `data.user_context.model_dump()` and
hands the dict directly to `parse_batch_command`. The service then trims
completed tasks and caps each list at 50–200 (`ai_productivity.py:867-877`)
but does **not** redact, hash, or otherwise transform medication names. The
plaintext list, including names like `"Adderall"`, ships in the user-message
body alongside the system prompt at `ai_productivity.py:777`.

The system prompt explicitly instructs Haiku to match user-typed phrases
against the medication `name` field (`ai_productivity.py:823 "Never invent
entity IDs. Only use IDs that appear in the input lists."`). The name is
*load-bearing for prompt correctness* — see Section 5A.

**The downstream response** is structured mutations (entity_type, entity_id,
mutation_type, proposed_new_values). These flow back to the Android client
as a diff-preview screen; no medication name returns from Haiku that wasn't
already known to the client. **The egress is one-way: client → Haiku, not
Haiku → client.** No medication data is *returned* from Anthropic.

**Reachable path:** Pro-tier authenticated endpoint, rate-limited
10/hour/user. Triggered explicitly by user typing in the NLP batch ops UI.

### 2B — Sites #1–#10, #12–#13 — Task / habit / free-text endpoints

These ship task and habit titles, project names, schedule data, free-form
text the user pasted, etc. They do **not** load from the medications table
or from `BatchMedicationContext`. They will incidentally carry medication
names *only if a user has typed a medication name into a task or habit
title* (e.g. a task literally titled `"Refill Adderall prescription"`). That
risk class is identical to "what's in any task title" — user-controlled
free-text transit, not schema-bound PHI flow.

The unauthenticated `/tasks/parse` endpoint (#12, `routers/tasks.py:223`) is
worth calling out separately: it accepts arbitrary text from any caller
without auth, runs the NLP parser, and returns the structured parse. The
endpoint is rate-limited per IP. There is no medication-table coupling, but
a malicious caller could submit `"Refill Adderall"` and have that string
transit Anthropic. That's an existing property of the public endpoint, not
a regression.

### 2C — Site #14 `parse_syllabus` — student PDF content

Ships the first 12k characters of an extracted PDF (`syllabus.py:180`). No
coupling to medication or health data. Out of scope for the medication-PII
question.

### 2D — Site #15 `scan_gmail` — opt-in Gmail integration

Ships `subject + sender + date + 500-char snippet` for up to 20 recent
unread/starred emails (`gmail_integration.py:147–163`). Email content the
user has *already received* in their own inbox — including any pharmacy
order confirmations, doctor's office reminders, etc. This is a real PII
surface but distinct from the schema-bound medication flow. Mitigated
already by Pro tier + user-initiated opt-in + Gmail OAuth scope grant.

### Categorization (per spec)

| # | Call site | Type | Notes |
|---|-----------|------|-------|
| 1–10, 12–13 | Task/habit/text endpoints | **Type C** (non-medication user data) and **Type A** (free-text) | Carries medication names only incidentally if user types them into task titles. |
| 11 | `parse_batch_command` | **Type B** (structured medication PII) | The flagged flow. Schema-bound `medications: [{id, name}]` ships every batch parse. |
| 14 | `parse_syllabus` | **Type C** | Academic PDF content. |
| 15 | `scan_gmail` | **Type A** (free-text) + **Type C** (third-party email metadata) | Could transit medication-related email contents incidentally. |

Only **site #11** is unambiguously Type B. The audit's recommendation
focuses there.

---

## Section 3 — Anthropic Data-Retention + Training Stance (verified 2026-04-25)

Sources fetched today: `privacy.claude.com`, `anthropic.com/legal/commercial-terms`,
`anthropic.com/legal/aup`, `support.claude.com`. Where text is quoted, it is
verbatim from the page on this date. ZDR-specific docs page returned 404 —
flagged below for manual confirmation.

### Training on inputs

**Commercial Terms § B (Customer Content):**
> "Anthropic may not train models on Customer Content from Services."

**Conclusion:** Default no-training for commercial API customers. PrismTask
is on the standard commercial API tier and inherits this protection.

### Standard data retention

**`privacy.claude.com/articles/7996866`:**
> "For Anthropic API users, we automatically delete inputs and outputs on
> our backend within 30 days of receipt or generation, except: When you use
> a service with longer retention under your control (e.g. Files API)…"

> "We retain inputs and outputs for up to 2 years and trust and safety
> classification scores for up to 7 years if your chat is flagged by our
> trust and safety classifiers as violating our Usage Policy"

**Conclusion:** Standard retention is 30 days. Up to 2 years if a request
is flagged for AUP violation. Trust-and-safety classification scores up to
7 years (these are scores about the request, not the request content
itself).

### Zero Data Retention (ZDR)

**Confirmed available** by `privacy.claude.com/articles/7996866`:
> "When you and we have agreed otherwise (e.g. zero data retention agreement)"

**Manual confirmation needed:** the dedicated ZDR docs page
(`docs.anthropic.com/en/api/zero-data-retention`, redirected to
`platform.claude.com/docs/en/api/zero-data-retention`) returned 404 at
audit time. Eligibility, minimum spend, feature restrictions, and
contracting timeline must be verified directly with Anthropic Sales.

### HIPAA BAA

**Available** per `support.claude.com/articles/8956058`:
> "Business Associate Agreement (BAA), which is only available to customers
> who use our HIPAA eligible services."

> "customers of Anthropic's HIPAA eligible services are subject to certain
> configuration requirements and limitations on what features/integrations
> are available."

> "the BAA would not apply to use of the web search functionality."

**Conclusion:** BAA is on offer but requires Sales contact, configuration
restrictions, and likely an enterprise tier. Web search is excluded; other
feature restrictions not enumerated in public docs.

### Acceptable Use Policy — Healthcare classification

**`anthropic.com/legal/aup`:**
> "Healthcare: Use cases related to healthcare decisions, medical diagnosis,
> patient care, therapy, mental health, or other medical guidance."

These cases require **two** safeguards: human professional review AND
disclosure that AI assisted in producing the advice.

> "wellness advice (e.g., advice on sleep, stress, nutrition, exercise,
> etc.)" falls *outside* the high-risk category.

**Conclusion for PrismTask:** PrismTask's AI does not generate medical
advice — it generates schedule mutations, task structures, and productivity
narratives. Per Anthropic's own AUP, PrismTask's use is a *productivity /
wellness* category, not a *healthcare* category. The user voluntarily types
medication names into a productivity context to *schedule* taking them, not
to *receive medical guidance about* them. The output is "skip your Adderall
today" as a calendar mutation, not "you should take 20mg of Adderall at
9am" as medical advice.

This nuance is load-bearing for the framing in Section 4.

---

## Section 4 — Risk Classification

### Re-framing the original PR #773 concern

The PR #773 audit phase wrote: *"Medication names are HIPAA-adjacent."* That
framing is technically incorrect:

- HIPAA's Privacy Rule applies to **Covered Entities** (healthcare providers,
  health plans, healthcare clearinghouses) and their **Business Associates**.
  PrismTask is neither. Medication names voluntarily typed into a productivity
  app by a consumer are not Protected Health Information (PHI) under HIPAA's
  scope. (45 CFR § 160.103.)
- "HIPAA-adjacent" is not a regulatory category.

**However**, the underlying privacy concern is real — three valid framings
remain and are what this audit acts on:

1. **Google Play Console Health-Apps Data Policy.** PrismTask declared the
   "Health and fitness" + "Medical/health info" categories in its Data
   Safety form (PR #765). Sharing those data classes with a third party
   for AI processing is a disclosable event regardless of HIPAA.
2. **GDPR Article 9.** "Data concerning health" is a special-category
   personal data under GDPR. Medication names qualify. EU users require
   explicit consent + a lawful processing basis; transferring to an
   Anthropic API endpoint outside the EU additionally engages Chapter V
   (international transfers) considerations.
3. **User trust + expectation.** Even where neither HIPAA nor GDPR applies
   strictly, users typing medication names into PrismTask reasonably
   expect those names to remain within the PrismTask trust boundary
   (PrismTask + Firebase). Egress to a third-party LLM is a trust event
   that warrants disclosure.

### Per-site risk

| # | Call site | Type | Risk |
|---|-----------|------|------|
| 11 | `parse_batch_command` | B | **Medium (acceptable with disclosure).** Medication names ship in plaintext, but: no training (Anthropic Commercial Terms § B), 30-day standard retention, BAA / ZDR available on contract, AUP categorizes use as wellness/productivity (not healthcare). The data is sensitive but not PHI under HIPAA's scope, and Anthropic's API-tier protections substantively mitigate exposure. |
| 1–10, 12–13 | Task/habit/text | C / A | **Low.** Carries medication name only if user types one into a task title — same risk class as any user-typed task content. |
| 14 | `parse_syllabus` | C | **Low.** No medical coupling. |
| 15 | `scan_gmail` | A / C | **Medium-Low.** Could incidentally transit pharmacy email content. Already opt-in + Pro-gated; mitigated by user-initiated consent. Disclosable in privacy policy. |

**Threshold for Phase F blocker:** Critical. The audit finds zero Critical-tier
sites. The flagged site #11 sits at Medium with mitigation paths.

**Phase F is not blocked** by this audit's findings provided Option C is shipped.

---

## Section 5 — Mitigation Options

### 5A — Option A: Tokenization (the originally-proposed PoC)

**FEASIBILITY: INFEASIBLE backend-only for site #11.**

The blocker is not technical — it's that the medication `name` field is
exactly the matchpoint Haiku uses to bind the user's command to an entity
ID. A representative trace:

| User types | Client sends | Haiku must produce |
|-----------|-------------|---------------------|
| `"Skip my Adderall today"` | `command_text + {medications: [{"id": "med_42", "name": "Adderall"}, ...]}` | `{entity_type: "MEDICATION", entity_id: "med_42", mutation_type: "SKIP", ...}` |

Haiku's binding step is *string-match the user's free-text mention against
the `name` fields in the context, then return the matching `id`*. The system
prompt (`ai_productivity.py:823 "Never invent entity IDs. Only use IDs that
appear in the input lists"`) hardens this binding.

If we tokenize the medication context — replace `"Adderall"` with
`"MED_TOKEN_42"` — Haiku sees `"Skip my Adderall today"` in the command
and `[{"id": "med_42", "name": "MED_TOKEN_42"}]` in the context. There is no
match path. The mutation cannot be produced.

**Two avenues to make Option A work, both of which exceed PoC scope:**

1. **Client-side entity resolution.** The Android client matches `"Adderall"`
   in the user's command to `id: med_42` *before* sending. The backend then
   never sees the name, and the prompt operates on opaque tokens. This is a
   substantial client change — the matching logic that AI was *intentionally*
   delegated to (because users type misspellings, abbreviations, brand-vs-
   generic names, etc.) returns to the client. Defeats the whole purpose of
   batch_parse-via-AI.

2. **Two-stage AI flow.** First call: ask Haiku for entity ID resolution
   only, with name visible. Second call: ask Haiku for the mutation, with
   tokens visible. This *doubles* the egress surface, doesn't reduce it.

**Conclusion:** Option A is not the right answer for site #11. It would be
appropriate for a hypothetical future call site that ships medication
*usage data* (dosage, frequency, side-effect notes) where the name itself
is not the prompt's matchpoint — but no such call site exists today.

### 5B — Option B: On-device parsing (Android)

**FEASIBILITY: PARTIAL, defers Phase F.**

Move `parse_batch_command` from the backend to Android. Two substrate
choices:

- **Rule-based parser.** Regex-and-grammar over the small command grammar
  documented in the system prompt (RESCHEDULE / DELETE / COMPLETE / SKIP /
  PRIORITY_CHANGE / TAG_CHANGE / etc.). Plausible for ~70% of commands the
  prompt currently handles; degrades on synonyms, misspellings, complex
  date phrases, and ambiguous-entity disambiguation. The whole reason
  PR #697 + #700 shipped backend-Haiku batch parsing was to escape the
  rule-based ceiling. Reverting is a feature regression.

- **On-device LLM.** TinyLlama / Phi-3-mini / Gemma-2B-IT class models can
  run on-device on Android 8.0+ with ~1.5–3 GB RAM headroom. APK size
  impact: ~700 MB–1.5 GB additional. Inference latency: 2–10s per parse on
  mid-tier hardware (vs ~700ms backend Haiku). Battery + thermal cost on
  long sessions. Pro-tier users would notice the regression compared to
  the Phase A2 build they're already using.

**Cost:** Both substrates require Android-side rewrites (~3–4 weeks of
engineering for the rule-based path, ~6–8 weeks for the on-device LLM
path) — Phase F slips at minimum 4 weeks.

**Phase G or later** is the realistic landing window if the team commits to
this track. Worth pursuing as the *long-term* answer to AI-egress for
sensitive contexts, but not a Phase F unblocker.

### 5C — Option C: Disclose-and-ship (RECOMMENDED for Phase F)

**FEASIBILITY: YES, small, Phase F-ready.**

Phase F ships as-is. The privacy policy + Data Safety form + Settings UI
are updated *before* the launch beta to disclose:

1. Medication names (and names of habits, projects, tasks) transit
   Anthropic's Claude API for AI-feature processing.
2. Anthropic does not train on inputs (Commercial Terms § B).
3. Anthropic retains inputs/outputs for up to 30 days standard, up to
   2 years if flagged for AUP review.
4. The user can disable AI features (turn off the AI Pro tier toggle
   that gates `/ai/*` routes) to opt out of all egress.

A Settings opt-out toggle ("Disable AI features — keep all my data
on-device + Firebase only") gives users a meaningful choice without
forcing the architectural rebuild Options A/B require.

**Play Console policy implications:** The existing Data Safety declarations
already cover "Health and fitness" + "Medical/health info" categories.
This change adds the "Data shared with third parties" disclosure for
those categories. That is *within the same product classification* —
productivity-primary — and does not re-trigger the medical-app
classification (which would re-engage the D-U-N-S blocker that PR #765's
classification fix resolved). Disclosure is sufficient.

**iOS App Store policy implications (Phase K):** Privacy Nutrition Label
will mirror the Play disclosures. Apple's stricter health-data policy
(App Store Review Guideline 5.1.1.iv) requires disclosure when health data
is shared with third parties; this audit's recommended change satisfies
that bar.

**Cost estimate:** ~1 medium PR.
- `docs/privacy/index.md` — 2 sections added (~80 lines)
- `docs/store-listing/compliance/data-safety-form.md` — 3 category deltas
- Android Settings — opt-out toggle composable + DataStore persistence (~120 lines + 2 unit tests)
- Backend `/ai/*` middleware — return 451 if `ai_disabled=true` for that user (or simply rely on client-side gating; 451-on-server gives defense-in-depth)
- 1 backend test for the gated path

### 5D — Option D: ZDR + BAA (parallel hardening track)

**FEASIBILITY: YES but timeline-uncertain.**

Engage Anthropic Sales for:

- Zero Data Retention agreement → 30-day retention drops to 0
- Business Associate Agreement → enables marketing PrismTask to
  healthcare-adjacent users (caregivers, ADHD coaching, chronic illness
  management) without the disclosure-only posture

**Cost:** Sales cycle 2–6 weeks. May require enterprise tier or minimum
spend (not disclosed publicly). Configuration restrictions apply (web
search excluded, possibly other features).

**Phase F-ready: probably not** for May 15. Track this in parallel as the
post-launch hardening narrative — "we ship Phase F under Option C
disclosure, then upgrade to ZDR+BAA in the following 6–12 weeks for
defense-in-depth."

---

## Section 6 — Recommendation

**PROCEED with Option C — disclose-and-ship — for Phase F.**

Open Option D (ZDR + BAA) as a parallel sales/legal track for post-launch
hardening.

Defer Option A (tokenization) until / unless a future call site ships
medication usage data (dosage, frequency, clinical notes) where the name
itself is *not* the prompt's matchpoint. None today.

Defer Option B (on-device parsing) to Phase G+ as the long-term
architectural answer if user feedback or jurisdictional pressure (EU GDPR
Article 9 enforcement, US state-level health-privacy laws) escalates.

### Why not STOP

Three of the four "STOP candidates" the user enumerated were checked:

1. ❌ "Two flagged lines don't actually send what was claimed." False —
   `BatchMedicationContext.name` is plaintext, ships every batch parse,
   exactly as PR #773 said.
2. ❌ "Medication names already tokenized upstream." False — confirmed
   plaintext in `schemas/ai.py:402-404` and in the `data.user_context.model_dump()`
   dict at `routers/ai.py:739`.
3. ⚠️ "Anthropic stance changed, concern is moot." Partially — Anthropic's
   API-tier stance (no training, 30-day retention, BAA/ZDR available) is
   *better* than worst-case and substantively mitigates the original
   concern, but the egress itself still happens. The concern is not moot.

A fourth premise issue did surface — the "HIPAA-adjacent" framing was
technically incorrect (Section 4) — but the underlying privacy concern is
valid via three other framings. The audit re-framed rather than dismissed.

This is a *recommendation-different*, not a STOP. Per the audit's hard
rule, **no PoC code has been written** in this session. Decision required
before Phase 2 work proceeds.

---

## Section 7 — Privacy-Doc + Data Safety Form Follow-up Plan

If you accept Option C, the follow-up PR (separate from this audit doc)
ships the user-facing changes. Drafted shape below.

### `docs/privacy/index.md` — proposed additions

**New section: "AI processing and Anthropic"**

Approximate language (final wording in the follow-up PR):

> ### AI Processing
>
> When you use AI-powered features in PrismTask Pro (Eisenhower
> categorization, daily briefing, weekly planner, time blocking, batch
> NLP commands, conversation extraction, Pomodoro coaching, Gmail task
> extraction), the relevant data is sent to **Anthropic, PBC** for
> processing via their Claude API.
>
> **What is sent:** task titles, habit names, project names, schedule
> data, and — for batch NLP commands — medication names. Free-text you
> type into AI surfaces (e.g. natural-language commands, syllabus PDFs,
> conversation extraction text) is sent verbatim.
>
> **What Anthropic does with it:**
> - Inputs and outputs are not used to train Anthropic's models
>   (Anthropic Commercial Terms, Section B).
> - Inputs and outputs are deleted within 30 days of receipt
>   (Anthropic API Data Retention policy).
> - If a request triggers Anthropic's Trust & Safety review, retention
>   may extend up to 2 years; classification scores up to 7 years.
>
> **Opting out:** You can disable AI features in *Settings → AI Features*.
> When disabled, no PrismTask data is sent to Anthropic. The AI-powered
> Pro features become unavailable; all other Pro features (cloud sync,
> Drive backup, etc.) continue to work.
>
> **Why we share medication names:** the batch NLP feature
> (e.g. "Skip my Adderall today") needs to match phrases in your command
> against your medication list to produce the right schedule action. We
> do not share dosage, frequency, prescriber, or other clinical data —
> only the name you typed for the medication.

### `docs/store-listing/compliance/data-safety-form.md` — proposed deltas

| Category | Current | Proposed |
|----------|---------|----------|
| "Health and fitness" → Shared with third parties | No | **Yes** (Anthropic, for AI feature processing) |
| "Medical/health info" → Shared with third parties | No | **Yes** (Anthropic, for AI feature processing) |
| "Health and fitness" → Optional | Yes | Yes (no change) |
| Data sharing purpose | n/a | **Functionality** (AI features) |

### Settings UI — opt-out toggle

- New section in Settings: *AI Features*
- Toggle: *"Use Claude AI for advanced features"* (default ON for Pro
  users — preserves backward-compat, but disclosure makes the choice
  meaningful)
- Subtitle: *"When off, AI-powered features (briefing, planner, batch
  commands) won't work, but no data is sent to Anthropic."*
- Persistence: `UserPreferencesDataStore` (new key `ai_features_enabled`,
  default `true`)
- Backend enforcement: middleware on `/ai/*` returns 451 Unavailable For
  Legal Reasons if the user-prefs flag is `false` (defense-in-depth so
  the egress can't happen even on a stale-cache client)

### Estimated PR shape (separate follow-up PR)

- `docs/privacy/index.md` — ~80 lines added
- `docs/store-listing/compliance/data-safety-form.md` — ~3 small deltas
- `app/.../UserPreferencesDataStore.kt` — ~25 lines (new flag)
- `app/.../ui/screens/settings/sections/AISettingsSection.kt` — new file ~120 lines
- `backend/app/middleware/ai_gate.py` — new file ~40 lines
- `backend/app/routers/ai.py` — wire middleware (~5 lines)
- Tests: `app/src/test/.../AISettingsSectionTest.kt`, `backend/tests/test_ai_gate.py` — ~120 lines combined

**Total:** ~10 files, ~400 lines, 1 medium PR. Phase F-ready.

---

## Appendix A — Verification commands for re-runners

```bash
# Re-list all production Anthropic call sites
grep -rn "messages.create\|anthropic.Anthropic" backend/app/

# Confirm BatchMedicationContext shape
grep -A 4 "class BatchMedicationContext" backend/app/schemas/ai.py

# Confirm no tokenization at the router boundary
grep -B 2 -A 5 "user_context_dict" backend/app/routers/ai.py
```

## Appendix B — Items requiring manual / external follow-up

1. **ZDR docs page 404.** `docs.anthropic.com/en/api/zero-data-retention`
   redirected to a 404 at audit time. Confirm with Anthropic Sales:
   eligibility, minimum spend, feature restrictions, contracting timeline.
2. **HIPAA BAA configuration restrictions.** Public docs note "BAA does
   not apply to web search" and "configuration requirements and
   limitations on what features/integrations are available." Get the full
   list from Sales before committing to Option D.
3. **GDPR Article 9 lawful basis.** Even with Option C disclosure, EU
   users may require explicit consent at first use. Legal review needed
   before Phase F EU rollout.
4. **State-level US health-privacy laws.** Washington's My Health My Data
   Act (effective 2024-03-31), California's CMIA, and similar — verify
   PrismTask's posture under each before targeting those markets.

---

*This audit was written before any PoC code was written. Per the rules of
the engagement: no implementation, no branch, no tokenization scaffolding
exists. Decision on Options A/B/C/D required before Phase 2 work begins.*
