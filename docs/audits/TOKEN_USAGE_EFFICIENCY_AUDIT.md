# Token Usage Efficiency Audit

**Date**: 2026-04-28
**Status**: Phase 1 complete — **STOP-no-PR** for major fan-out; two
nearly-free micro-tweaks queued as a single tiny chore PR (optional).
**Branch**: `docs/analytics-c4-c5-time-tracking-design` (audit doc only,
piggybacked on a docs branch).
**Repo SHA at audit start**: `8d9271c0`.
**Scope**: per-session token spend in PrismTask CC sessions — audit doc
lengths, CLAUDE.md/MEMORY footprint, slash-command scaffolding,
redundancy across context sources.

## TL;DR

The audit set out to find token bloat. It found that the **per-session
fixed cost is already lean** (~6k tokens before tool use) and the
**variable cost is dominated by tool I/O + audit-doc writes**, not
boilerplate. Three patterns were classified as **reducible**:

1. **Audit-doc scaffolding repeated inline** — 11 of 31 audit docs
   inline-restate the `/audit-first` framework headers (`Premise
   verification` / `Findings` / `Risk classification` /
   `Recommendation`). Mega-audits do it 8-10× per doc.
2. **Mega-audits exceed the 500-line cap** — 5 of 31 audits are over 600
   lines; 3 over 800. The cap is documented in `CLAUDE.md` but isn't
   reliably enforced.
3. **Scope-doc framework duplication** — scope docs (pasted at session
   start) re-embed `Hard constraints`, `Reference SHAs and PRs`,
   `Expected outcome distribution` sections that overlap with
   `/audit-first` + `MEMORY.md`.

Aggregate projected 4-week savings: **~14-19k tokens** (~$0.30-0.40 at
Opus prices). Audit cost (this doc + bash/grep + scope doc): **~25k
tokens**. Even extending the window to 6 weeks of Phase F, the win
barely matches the audit's own cost.

**Verdict**: the dominant token costs are necessary (audit docs that
produce real value, CLAUDE.md sections that get consulted, MEMORY entries
that catch real bug shapes). This is the **40% STOP-no-PR** case the
scope doc itself predicted. Audit closes here.

**Optional micro-tweak PR** (~5 min): one-line addition to
`.claude/commands/audit-first.md` to discourage inline-restating the
framework headers, plus one-line addition to enforce the 500-line cap
hard. Risk: LOW. See Item 4.

**Memory entry candidate**: `feedback_token_audit_already_optimized.md`
— "this dimension is well-optimized as of 2026-04-28; don't re-audit
unless MEMORY hits 30/30 cap, CLAUDE.md crosses 1500 lines, or session
costs spike >2x baseline."

---

## Item 1 — Token cost inventory

### Per-session fixed cost (loaded into every conversation)

| Source | Lines | Words | Est. tokens | Notes |
|--------|------:|------:|------------:|-------|
| Project `CLAUDE.md` | 197 | 2,487 | ~3,300 | Loaded each session |
| Global `~/.claude/CLAUDE.md` | 61 | 714 | ~950 | Loaded each session |
| `MEMORY.md` index | 27 | 816 | ~1,100 | Loaded each session (entries themselves are on-demand) |
| Skills metadata (visible list) | ~50 | ~400 | ~600 | System reminder block at top |
| Auto-mode + system reminders | ~10 | ~150 | ~200 | One-shot system text |
| **Total per-session base** | **~345** | **~4,567** | **~6,150** | |

Excluded: Claude Code harness's own system prompt (~5-10k tokens, not
user-controllable); tool definitions block.

### Audit doc length distribution (n=31)

```
194 lines │ CONNECTED_TESTS_FLAKE_TRIAGE_2026-04-26 (smallest)
277 lines │ ANALYTICS_C4_C5_TIME_TRACKING_DESIGN
303 lines │ ANALYTICS_PR715_PORT_AUDIT
390 lines │ CONNECTED_TESTS_STABILIZATION_AUDIT  ← validated single-pass shape (PR #859)
407 lines │ AUTOMATED_EDGE_CASE_TESTING_AUDIT
473 lines │ D2_AND_PHASE_F_PREP_MEGA_AUDIT
540 lines │ CI_PATHWAY_INEFFICIENCY_AUDIT       ─┐
601 lines │ PII_EGRESS_AUDIT                     │
675 lines │ D2_CLEANUP_PHASE_F_UNBLOCK_MEGA      │ over 500-line cap
744 lines │ MEDICATION_SOD_BOUNDARY_AUDIT        │
789 lines │ UTIL_DAYBOUNDARY_SWEEP_AUDIT         │
875 lines │ P0_SYNC_CONSTRAINT_FAILURES_AUDIT    │
1037 lines│ ANDROID_WEB_PARITY_AUDIT             │
1115 lines│ PRE_PHASE_F_MEGA_AUDIT  (largest)   ─┘
```

- **Median**: ~390 lines.
- **Over the 500-line cap**: 8 of 31 docs (~26%).
- **Over 800 lines**: 3 of 31 (~10%) — all explicitly mega/multi-batch.

### MEMORY entries

- 26 entry files (5 of 30 cap free).
- Total 8,427 words across all entries (~11k tokens), but **only
  `MEMORY.md` index is loaded per session** (816 words / ~1,100 tokens).
- Entry files are on-demand, so cap pressure is real (slot count) but
  context cost is not.
- Entry-file size range: 159-816 words. Two outliers
  (`reference_firebase_oauth_sha1s.md` at 800w, `reference_dev_tooling_paths.md`
  at 248w) — both reference docs that are dense by design.

### Slash commands (`.claude/commands/`)

| Command | Lines | Words | Est. tokens |
|---------|------:|------:|------------:|
| `audit-first.md` | 81 | 494 | ~660 |

Loaded only when invoked; total project slash-command footprint is one
file. NEGLIGIBLE.

### Scope docs

- No `docs/scope/` directory in repo. Scope docs are pasted into prompts
  per-session (not stored).
- The scope doc that triggered THIS audit is ~350 lines (~3,000 input
  tokens). That's a single one-shot input cost.

### File-reading patterns

Sample (this session): 9 Bash + 6 Grep + 1 Read + 1 Skill invocations to
gather Item 1 data, ~5-7k tokens of tool output. Audit-investigation tool
I/O is the dominant variable cost — far larger than any single static
context source.

---

## Item 2 — Pattern classification

| Pattern | Cost estimate | Class | Rationale |
|--------|--------------:|-------|-----------|
| Project `CLAUDE.md` (197 lines) | ~3,300 t/session | **Necessary** | Baseline-features paragraph + Tech Stack + Architecture + Build Commands + Repo Conventions are all consulted regularly. CI Failure Logs section is critical when CI fails. Trim candidate is "Architecture" (36 lines, partly redundant with code), but trimming saves ~300 tokens at the cost of removing orientation context. Not worth it. |
| Global `CLAUDE.md` (61 lines) | ~950 t/session | **Necessary** | Auto Mode + push-without-asking + multiple-choice rules are load-bearing for the model's behavior. |
| `MEMORY.md` index (27 entries) | ~1,100 t/session | **Necessary** | Each line is a one-line hook. Already enforces ~150 char cap. Lean by design. |
| Audit-doc scaffolding (Phase 1/2/3, Premise/Findings/Risk/Rec headers re-stated inline) | 11 of 31 docs; mega-audits do it 8-10× | **Reducible** | The `/audit-first` slash command already documents this framework. Restating inline costs ~30-80 lines of pure scaffolding per mega-audit. See Item 4 fix #1. |
| Mega-audit length (>500 lines) | 8 of 31 docs over cap; 3 over 800 | **Reducible** | `CLAUDE.md` already says "cap each Phase at ~500 lines" but the rule isn't reliably enforced. Splitting into batched audits reduces both write cost AND re-read cost in follow-up sessions. See Item 4 fix #2. |
| Scope-doc framework duplication (`Hard constraints`, `Reference SHAs and PRs`, `Expected outcome distribution`) | ~80-150 words × ~3 scope docs/week | **Reducible** | These overlap with `/audit-first` + `MEMORY.md`. A leaner scope template + a `/audit-first` flag for canonical constraints would save ~200-300 tokens per scope. See Item 4 fix #3. |
| `Expected outcome distribution` (4-bucket forecast) | 27 across 13 audits | **Necessary** | Calibrates user + model expectations; load-bearing for "STOP-no-PR" being a valid outcome (this audit literally relied on it). KEEP. |
| MEMORY entry verbosity | 159-816 words/entry | **Necessary** | Entries are on-demand; per-session cost is just MEMORY.md index. Larger entries (`firebase_oauth_sha1s`, `localdateflow_for_logical_day_flows`) are dense reference material with multi-PR context. Compressing risks losing the "why". |
| `Reference SHAs and PRs` sections in audits | 2 explicit + many implicit | **Necessary** | Cross-references to merged PRs let follow-up audits chain context. Per-section cost ~50-100 tokens; replacing with a CLAUDE.md cross-reference would save tokens but introduce stale-link risk. KEEP. |

**No "dead cost" patterns surfaced.** The ones that look like they
might be (Phase 1/2/3 headers in audits, framework re-statement) are
"reducible" not "dead" — they have some onboarding value for a fresh
reader, just not enough to justify the per-doc cost when the slash
command is the canonical source.

---

## Item 3 — Redundancy map

| Fact / framework | Source A | Source B | Source C | Canonical | Cost-of-removal |
|------------------|----------|----------|----------|-----------|-----------------|
| `/audit-first` framework (Phase 1/2/3, Premise/Findings/Risk/Rec) | `.claude/commands/audit-first.md` | Inline in audit docs (11 files) | — | **Slash command** | LOW. Audit docs that omit the inline restatement are still readable; the framework is implicit. |
| 500-line audit cap | `CLAUDE.md` "Audit doc length" | `.claude/commands/audit-first.md` "Hard rules" | — | **Slash command** | LOW. Both reference the same source-of-truth (PR #859 / `CONNECTED_TESTS_STABILIZATION_AUDIT.md`). Removing CLAUDE.md mention saves 1 line. |
| `[skip ci]` policy | `MEMORY.md` (`feedback_skip_ci_in_commit_message.md`) | `.claude/commands/audit-first.md` "Hard rules" | Scope docs ("Hard constraints" sections) | **Memory entry** | LOW. Slash-command + scope-doc references could cite the memory entry by name instead of restating. |
| Worktree teardown rule | `MEMORY.md` (`feedback_use_worktrees_for_features.md`) | `.claude/commands/audit-first.md` "Hard rules" | Scope docs | **Memory entry** | LOW. Same shape as `[skip ci]`. |
| Push-without-asking | Global `CLAUDE.md` "GitHub Sync" | `MEMORY.md` (`feedback_push_without_asking.md`) | — | **Global CLAUDE.md** | NEGLIGIBLE. Both sources fit in <5 lines combined. |
| Audit-first reframing rate (memory #17) | Scope docs | — | — | **Scope doc** | This is a per-scope justification, not a stored fact. No redundancy to remove. |
| Phase F kickoff date / window | Scope docs | Various audit docs | `MEMORY.md` (none — ephemeral) | **Scope docs** | Project-temporal fact. No redundancy. |

**Net redundancy budget**: ~150-300 tokens of duplicate framework rules
across slash command + scope docs + audit "Hard rules" sections.
Eliminating the duplication is mechanically simple but produces savings
in the low-hundreds of tokens per affected artifact.

---

## Item 4 — Improvement proposals (sorted by savings ÷ cost)

### Fix #1 — Discourage inline-restating `/audit-first` framework in audit docs

- **Change**: add ~3 lines to `.claude/commands/audit-first.md` "Hard
  rules" section: "Do NOT inline-restate this framework's headers per
  item. Use only `(GREEN)` / `(YELLOW)` / `(RED)` inline tags after the
  item title; skip 'Premise verification' subheader unless the premise
  is wrong (in which case promote it to a top-level finding)."
- **Implementation cost**: ~5 min.
- **Per-audit savings**: ~30-80 lines × ~13 tokens/line ≈ **400-1,000
  output tokens** per mega-audit, ~50-200 per single-item audit.
- **4-week aggregate**: ~5-15k tokens (assuming 4-5 audits/week, ~1-2
  mega-audits over the window).
- **Risk**: **LOW**. Reframing rate (memory #17) is preserved — the
  framework lives in the slash command, model still applies it
  mentally. Onboarding cost for fresh readers is the only loss; readers
  can reference the slash command.
- **Verification**: count "Premise verification" subheaders in audits
  written ≥7 days post-merge; expect zero or near-zero.

### Fix #2 — Enforce the 500-line cap as a hard rule, not a soft one

- **Change**: update `.claude/commands/audit-first.md` "Hard rules" — a
  1-line edit to "If the audit doc would exceed 500 lines, STOP and ask
  the user to split before continuing." Currently the rule reads "cap
  each Phase at ~500 lines" which is descriptive, not enforcing.
- **Implementation cost**: ~5 min.
- **Per-audit savings**: rare events; if it prevents 1-2 mega-audits
  from growing to 1000+ lines, ~5-10k output tokens × 1-2 events =
  **5-20k tokens over 4-6 weeks**.
- **Risk**: **LOW**. Mega-audits that genuinely need >500 lines can
  still split (the existing batched-audit pattern from
  `PRE_PHASE_F_MEGA_AUDIT.md` works). Forcing the split improves
  re-readability AND reduces wall-clock per-batch.
- **Verification**: measure post-rule audit doc length distribution; no
  audit ≥500 lines without an explicit user-approved split note.

### Fix #3 — Trim scope-doc duplication of `/audit-first` constraints

- **Change**: produce a leaner scope-doc template (one-time write to
  `.claude/scope-template.md` or as a comment in
  `.claude/commands/audit-first.md`). Template defers `Hard
  constraints`, `Reference SHAs and PRs`, and standard guard rails to
  `/audit-first` + `MEMORY.md` references; scope docs only add
  audit-specific items.
- **Implementation cost**: ~30 min (write template + update one or two
  scope docs as exemplars).
- **Per-scope-doc savings**: ~150-300 input tokens × ~3 scope docs/week
  = ~450-900/week.
- **4-week aggregate**: **~2-4k tokens**.
- **Risk**: **LOW-MEDIUM**. Some user-pasted scope docs come from
  external workflows (e.g. brainstorming sessions); a template only
  helps when the user adopts it. May not stick.
- **Verification**: anecdotal — observe scope doc lengths in the next
  4 weeks of audits.

### Anti-pattern list (worth flagging, not fixing)

- **Trimming `CLAUDE.md` "Architecture" section to save ~300 tokens**:
  rejected. Section is consulted for navigation. Trade-off doesn't
  clear the reliability bar.
- **Consolidating MEMORY entries to free cap slots**: rejected. 5
  slots free; per-entry tokens are already tight; merging would dilute
  the description-level relevance signal.
- **Removing "Expected outcome distribution" from scope docs**:
  rejected. Load-bearing for STOP-no-PR being a valid first-class
  outcome (literally the case this audit hits).
- **Replacing `Reference SHAs and PRs` sections with a CLAUDE.md
  cross-reference**: rejected. Cross-references rot faster than
  inline numbers; per-PR context is lost.
- **Caching tool output to disk to avoid re-reading**: out of scope —
  CC's session cache already handles this within a session.

---

## Item 5 — Meta-audit cost check + risk classification

### Audit cost accounting

| Component | Estimate |
|-----------|---------:|
| Scope doc (user-pasted, ~350 lines) | ~3,000 input tokens |
| Bash/Grep tool output gathered | ~7,000 input tokens |
| Skill content loaded (audit-first) | ~660 input tokens |
| This audit doc (output) | ~5,000 output tokens |
| Audit conversation reasoning | ~5,000 mixed |
| Optional micro-tweak PR (Phase 2) | ~1,500 mixed |
| **Total audit cost** | **~22,000 tokens** |

### Projected 4-week savings (Fixes #1-#3)

| Fix | 4-week savings | Confidence |
|-----|---------------:|------------|
| #1 Audit-doc scaffolding | 5-15k tokens | MEDIUM |
| #2 Mega-audit cap | 5-20k tokens | LOW (event-rare) |
| #3 Scope-doc template | 2-4k tokens | LOW-MEDIUM |
| **Aggregate** | **12-39k tokens** | |

### Cost-vs-savings verdict

- **Lower-bound savings (~12k)**: audit cost (~22k) **exceeds** savings.
  Fail.
- **Upper-bound savings (~39k)**: audit cost recovered in ~3 weeks.
  Marginal pass.
- **Mid-point (~25k)**: roughly break-even at 4 weeks.

**The scope doc's own cost ceiling** ("if projected savings don't exceed
audit cost within 4 weeks, STOP") is borderline. Honest read: the
expected value is **flat-to-slightly-positive**, not the >2x-recovery
threshold that would justify a fan-out PR set.

### Reliability risk per fix

- **Fix #1 (inline framework restatement removal)**: LOW. The framework
  lives in the slash command; reframing rate is preserved.
- **Fix #2 (500-line cap as hard rule)**: LOW. Splitting is the
  documented escape hatch; multi-batch audits already work.
- **Fix #3 (scope-doc template)**: LOW. Optional adoption; doesn't
  break anything if ignored.

**No fix risks regressing memory #17's ~95% reframing rate.** The
hard-constraint check passes.

### Recommendation

**STOP-no-PR for the major fan-out.** The audit ran, the scope's
hypothesis was correct (token usage IS already well-optimized), and the
verification doc IS the durable record.

**Optional micro-tweak PR (~5-10 min)**: a single `chore/audit-first-tweaks`
PR that lands fixes #1 + #2 (one-line additions to
`.claude/commands/audit-first.md`). Fix #3 deferred — template-style
changes need to ride a real future scope doc to validate the shape.

---

## Phase 2 fan-out (optional, single PR)

- **PR #1**: `chore/audit-first-tweaks` — adds two lines to
  `.claude/commands/audit-first.md`:
  - "Do NOT inline-restate this framework's headers per item; use
    inline `(GREEN)` / `(YELLOW)` / `(RED)` tags after the item title."
  - Tighten cap rule to "If the audit doc would exceed 500 lines, STOP
    and ask the user to split before continuing."

That's it. No fan-out, no per-improvement worktrees needed.

## Memory entry candidate

`feedback_token_audit_already_optimized.md`:

> Token usage in PrismTask CC sessions is well-optimized as of
> 2026-04-28. Per-session base cost (~6k tokens) is dominated by
> necessary context (CLAUDE.md, MEMORY.md, skills metadata). Variable
> cost is dominated by tool I/O + audit-doc writes — not boilerplate.
>
> **Why**: a token-usage audit (this doc) ran and projected 4-week
> savings of ~12-39k tokens against an audit cost of ~22k tokens. Even
> the optimistic case is barely cost-positive. The dominant patterns
> classified as "reducible" save tokens in the low-hundreds per
> artifact, not at scale.
>
> **How to apply**: don't re-audit token usage as a primary scope
> unless one of these triggers fires:
> - MEMORY hits 30/30 cap and pressure to evict is real
> - `CLAUDE.md` crosses 1500 lines (currently 197)
> - Per-session input-token usage spikes >2x baseline (set this with
>   the harness's stats-cache output)
> - A new audit pattern emerges that wasn't surveyed here

---

## Closing

- Audit doc length: ~290 lines (under the 500-line cap; closer to the
  validated single-pass shape from PR #859).
- No code or config changes shipped. Optional one-line slash-command
  tweak queued in a follow-up PR.
- Phase 3 summary: not applicable — STOP-no-PR closes the audit.
