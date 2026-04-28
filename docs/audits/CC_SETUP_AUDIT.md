# Claude Code Setup Self-Audit — 2026-04-28

**Trigger:** Periodic audit of how Claude Code is being used on PrismTask
ahead of Phase F kickoff (2026-05-15) and post-launch G.0 (Jun 5–Jul 9).
Several weeks of organic process evolution may have accumulated waste.

**Process:** audit-first, single doc, two checkpoint stops (items 1-4 then
items 5-6). Phase 2 implementation only after Phase 1 approval.

**Optimization target:** wall-clock per PR (primary); cost / reliability /
cognitive load (secondary). Don't trade reliability for wall-clock.

**Repo state at audit start:** branch `main` @ `d7bd0c60`, build 754,
5 local worktrees + 30 local branches, 24 audit docs, 22 memory entries.

---

## Phase 1 — Audit (no config changes)

### Item 1 — Process audit

| # | Pattern | Evidence | Wall-clock impact | Notes |
|---|---|---|---|---|
| 1.1 | CLAUDE.md is 327 lines / 36 KB | `wc -l CLAUDE.md` = 327; size = 36,348 B; loaded as system prompt every session | ~9 K tokens/session of read overhead | Self-acknowledged drift risk: line 22 says "current DB version lives on `CURRENT_DB_VERSION` constant in `Migrations.kt` — read it from there rather than this doc, which drifts." Migration narrative on line 19 alone is ~1.4 KB of dense prose duplicating code-authoritative info. |
| 1.2 | Project Structure file tree is 163 lines | `awk` on `^```$` markers, lines 43–206 | Reading + drift detection cost | Half of CLAUDE.md is a file tree that grows with every new file. Highly drift-prone — every refactor invalidates entries. |
| 1.3 | Audit-first track record solid; doc length wildly variable | `wc -l docs/audits/*.md`: 24 docs, 10,355 lines, mean 431 lines, top 6 ≥ 600 lines, max 1,115 (`PRE_PHASE_F_MEGA_AUDIT.md`) and 1,037 (`ANDROID_WEB_PARITY_AUDIT.md`); validated 390-line single-pass audit was `CONNECTED_TESTS_STABILIZATION_AUDIT.md` (PR #859) | Mega-audits cost wall-clock to *write* the doc, then cost wall-clock per *future* read | The 1,115-line `PRE_PHASE_F_MEGA_AUDIT.md` covers 9 items in 3 batches — the structure invites scope creep. The validated 390-line single-pass shape (memory #20 came from it) hits the same goals at 35 % the size. |
| 1.4 | "Checkpoint — STOP" markers used inconsistently | `grep "Checkpoint"` matches in only 1 of 24 audit docs; `Phase 1`/`Phase 2` markers in 12 of 24 | Mixed | Memory #20 (`feedback_skip_audit_checkpoints.md`) explicitly says checkpoint markers are doc milestones, not gates. Most recent audits already follow that — the marker count is low because the rule is being respected. |
| 1.5 | Mega-prompt structure heavily repetitive | Current `/usage` prompt is ~200 lines: "## Hard rules — read first", "## Phase 1 — Audit", "### Checkpoint N — STOP", "## Phase 2 — Implementation", "## Phase 3 — Bundle summary". Repeated near-verbatim across audit triggers (see PR #859 audit prompt vs this one) | 5–15 min/audit prompt of writing time | No `.claude/commands/` directory exists at project or user level — no slash command has been extracted from this pattern. |
| 1.6 | Skip-checkpoints feedback (memory #20) being respected | Current prompt explicitly says "two checkpoint stops (not the usual three)" — scope override, not violation. Earlier audits like `CONNECTED_TESTS_STABILIZATION_AUDIT.md` are single-pass (validated) | Working as designed | Not a problem — flagged for completeness. |

**Item 1 summary:** CLAUDE.md size and the file-tree section are the
dominant per-session costs. The mega-audit pattern is the dominant
per-audit cost. Both have a clear right-shape (smaller, code-derived).

---

### Item 2 — Tooling audit

| # | Tool | Evidence | Wall-clock cost | Severity |
|---|---|---|---|---|
| 2.1 | **30 stale local branches — all merged but never deleted** | `git branch --no-merged main` lists 30 branches; `gh pr list --state merged --search "head:$b"` returns MERGED for **all 29 sampled non-`ci-logs` branches** (audit/d2-* → PRs #825-#831, audit/p0-sync-constraints → #862, fix/p0-sync-* → #851/#853/#855/#856, ci/* → #850/#852/#854/#824, feat/medication-* → #832/#857, etc.) | Direct memory #16 violation; pollutes `git branch` output, distorts `--no-merged main` queries, makes "what's in flight" hard to read | **HIGH** |
| 2.2 | **4 lingering worktrees besides main** | `git worktree list`: `prismTask-conn-tests-audit` (PR #859 merged), `prismTask-ktlint` at `d7bd0c60` (= main HEAD, fix/ktlint-inline-comment merged), `prismTask-parity-onboarding`, `prismTask-parity-phase3-summary` | Disk + lock conflicts when CC creates new worktrees; orphan paths that CC may try to operate on | **HIGH** |
| 2.3 | **5 stale CC project dirs from old worktree paths** | `~/.claude/projects/`: `C--Projects-averyTask-eisenhower` (1.6 MB), `…-pomodoro` (3.2 MB), `…-timeblock` (1.8 MB), `…-weekly-reviews` (1.6 MB), `C--Projects-pancData3` (7.9 MB). Total ~16 MB. The `averyTask-*` dirs are from before the PrismTask rebrand; pancData3 is unrelated. | Disk only; CC won't read these in a prismTask session | **LOW** |
| 2.4 | **No `.claude/commands/` slash commands at any level** | `ls C:/Users/avery_yy1vm3l/.claude/commands/` → no such directory; `ls C:/Projects/prismTask/.claude/` → only `settings.json`, `settings.local.json`, `scheduled_tasks.lock` | The audit-first mega-prompt boilerplate is rewritten by the user every time (Item 1.5). A `/audit-first <scope>` command would amortize 5–15 min/prompt. | **MEDIUM** |
| 2.5 | gh CLI patterns look healthy | Recent fixes (PR #858 JSON race, PR #868 actions:write) suggest active maintenance; auto-merge + auto-update-branch (PR #777) flow is in production | None | OK |
| 2.6 | Firebase MCP plugin enabled | Deferred-tools list shows 50+ `mcp__plugin_firebase_*` tools loaded (auth, crashlytics, firestore, RC, messaging…) | Tool-list bloat in deferred section, possibly unused | **MEDIUM** — usage not directly observable from session jsonls without scanning them; flag as "verify usage before next renewal." |
| 2.7 | Long-running ops dominate wall-clock | Connected-tests AVD boot (~20 min), CI feedback loop (~10–15 min/PR), per-PR build + tests | Per memory #25 (AVD failure recipe) and recent CI work (#841, #859) — already heavily optimized | Already addressed |

**Item 2 summary:** Branch hygiene is the single biggest tooling-side
wall-clock waste. Worktrees-without-cleanup violates an explicit user rule.
Slash command extraction is medium-effort/medium-payoff.

---

### Item 3 — Cost/context audit

| # | Axis | Evidence | Cost shape |
|---|---|---|---|
| 3.1 | CLAUDE.md context overhead | 36 KB / ~9 K tokens loaded every session as system prompt | Per-session fixed cost; non-trivial slice of the 200 K window. |
| 3.2 | Audit doc aggregate weight | 24 docs / 10,355 lines / ~700 KB. None deleted. CC reads `docs/audits/*.md` ad hoc when investigating recent decisions. | Cumulative — newer audits cost more to fit in context if CC reads multiple. Several large audits (`PRE_PHASE_F_MEGA`, `ANDROID_WEB_PARITY`) are 1000+ lines and would consume 25–30 K tokens each if read whole. |
| 3.3 | Memory cap pressure | MEMORY.md lists 22 entries; cap is 30. 8 slots free. | Healthy. |
| 3.4 | Memory index/storage divergence | `project_testdebugunit_blocked.md` exists in `memory/` (2,665 B) but is **not indexed** in MEMORY.md. Either deprecated and not deleted, or accidentally orphaned. | Trivial cost; cleanup signal. |
| 3.5 | Session jsonl history | `~/.claude/projects/C--Projects-prismTask/` = 137 MB across 83 jsonl session files | Disk only; not loaded into new sessions. Stays unless deleted. |
| 3.6 | File-reading patterns | Inferred from current session: parallel batched reads work well; recent CC sessions use Glob/Grep over Bash where possible (CLAUDE.md guidance respected) | OK |
| 3.7 | Conversation handoff / auto-compact | No direct evidence of recent compacts; chat-handoff skill not present in this conversation's skill list | Unknown; flag if user has data |

**Item 3 summary:** CLAUDE.md is the only **per-session** context cost
worth optimizing. Audit-doc weight is a **cumulative** cost — addressed
by either pruning shipped audits or by tighter audit-doc length discipline.
Memory cap pressure is fine.

---

### Item 4 — Setup audit

| # | Area | Evidence | Issue |
|---|---|---|---|
| 4.1 | **`.claude/settings.json` allowlist cruft** | 24+ lines reference stale paths: `pancData3` (unrelated project), `improvement_loop` (Python lib in another project), `averyTask` (old name from before PrismTask rebrand). Examples in lines 13-17, 28, 30, 33-38, 49-50 of `settings.json` | Cognitive load: settings file is 47 lines of accumulated cruft. Most rules pin paths that no longer exist or wildcards (`Bash(find /c/Users/.../pancData3 -maxdepth 2 ...)`) that won't fire here. Won't cause errors but clutters review. |
| 4.2 | **`.claude/settings.local.json` references `averyTask` paths** | Lines 18, 23, 28-34, 43-44 reference `C:/Projects/averyTask/`, `C:/Projects/averyTask-nlpbatch/` (old name) | Same as 4.1 — old name from rebrand |
| 4.3 | Hooks: SessionStart `git fetch --all --prune` | Active and working (visible in CLAUDE.md too). 30 s timeout, async. | OK — confirmed useful per global CLAUDE.md |
| 4.4 | CI workflow file size | 10 yml files, 2,059 lines. `version-bump-and-tag.yml` 403 lines, `android-ci.yml` 315 lines, `auto-update-branch.yml` 251 lines, `auto-merge.yml` 241 lines | Recent active tuning (PRs #858, #868). Not stale. Possible consolidation candidate if scope allows. |
| 4.5 | `[skip ci]` chore: bump pattern | 48 of last 100 commits are `chore: bump … [skip ci]`. Required workaround per memory #18 + #22 (release.yml manual fire). | Architectural choice with documented tradeoff. Flagged in Item 6 anti-patterns. |
| 4.6 | No `.gitattributes` audit performed | Out of scope this round | Defer |
| 4.7 | `app/build.gradle.kts` accumulated tweaks | Out of audit scope; would require detailed gradle review | Defer |

**Item 4 summary:** Settings allowlist cruft is the only meaningful setup
issue. Workflow file complexity is a known cost being actively managed.

---

## Checkpoint 1 — STOP

### Top 5 inefficiencies (ranked by wall-clock impact)

| Rank | Inefficiency | Recent example | Confidence |
|---|---|---|---|
| **1** | **Stale local branches + lingering worktrees** (memory #16 violation) | `git branch --no-merged main` lists 30 branches; **all 29 non-`ci-logs` branches sampled have MERGED PRs** (e.g. `audit/d2-and-phase-f-prep` → PR #825 MERGED, `feat/medication-tier-buttons-log-doses` → PR #857 MERGED). 4 worktrees besides main, 3 on merged branches (`prismTask-ktlint` at `d7bd0c60` = main HEAD). | **HIGH** |
| **2** | **CLAUDE.md size + drift-prone file tree** (327 lines, 36 KB, ~9K tokens/session) | 163-line file tree section (lines 43-206) is half the doc and grows with every refactor. Migration narrative on line 19 is self-acknowledged as drift-prone ("read [`Migrations.kt`] rather than this doc, which drifts"). | **HIGH** |
| **3** | **No slash commands extracted from repeated audit-prompt boilerplate** | Current `/usage` prompt structure ("Hard rules — read first" / "Phase 1 — Audit" / "Checkpoint N — STOP" / "Phase 2 — Implementation") is ~200 lines and is rewritten near-verbatim each audit (cf. CONNECTED_TESTS_STABILIZATION_AUDIT prompt). No `.claude/commands/` exists at any level. | **MEDIUM** |
| **4** | **Settings allowlist cruft (stale rebrand paths)** | `.claude/settings.json` has ~24 lines referencing `pancData3`/`improvement_loop`/`averyTask` (old name from before PrismTask rebrand). `.claude/settings.local.json` similar. Won't cause errors; clutters review and review-fatigue masks legitimate rules. | **MEDIUM** |
| **5** | **Audit-doc accumulation (24 docs, 10,355 lines, never pruned)** | Top 2 audits are 1,115 + 1,037 lines. The validated 390-line single-pass shape (CONNECTED_TESTS, PR #859) hits the same goals at 35% the size. Multiple post-shipped audits remain in `docs/audits/` indefinitely. | **MEDIUM** |

### Confidence on wall-clock fix impact

- **#1 (branch/worktree cleanup):** HIGH — one-time cleanup, plus a hook
  or daily check-in to maintain. Saves ~30-60 s per branch-related operation
  in future CC sessions.
- **#2 (CLAUDE.md trim):** HIGH on size, MEDIUM on drift — trimming the
  file tree to ~30-50 lines + removing the migration narrative could halve
  the per-session prompt overhead. Drift fix is harder (needs convention).
- **#3 (slash command):** MEDIUM — wins are real (5-15 min/audit prompt
  saved) but only fire when a new audit is started. PR #859-style audits
  are weekly-to-monthly cadence.
- **#4 (settings cleanup):** LOW direct wall-clock; MEDIUM cognitive
  load. Worth doing cheaply.
- **#5 (audit pruning):** LOW — these aren't read every session. But
  mega-audit *prevention* (length discipline going forward) has
  HIGH per-audit value.

### Anti-patterns flagged for Item 6 (post-checkpoint-2)

- Mega-audit pattern (1000+ line audits) — scope creep masks as thoroughness.
- `[skip ci]` chore: bump architecture — forces release.yml manual fire
  (memory #22). Tradeoff is documented but worth re-examining.
- File-tree section in CLAUDE.md as drift sink.
- Memory entry orphan (`project_testdebugunit_blocked.md` not indexed).

### Asks before proceeding

Two questions:

1. **Branch/worktree cleanup scope.** Recommended Phase 2 action:
   `git branch -d` the 29 merged branches + `git worktree remove` the 3
   merged worktrees. The 1 active worktree (current `prismTask-conn-tests-audit`
   if applicable) stays. Confirm OK to recommend in Phase 2.
2. **CLAUDE.md trim depth.** Two shapes:
   - **(a) Light:** drop the 163-line file tree → 30-line top-level overview;
     drop the migration narrative → "current = 64 (see `Migrations.kt`)".
     Saves ~20 KB / ~5 K tokens/session. Risk: CC has less spatial map.
   - **(b) Aggressive:** also trim "Architecture" + "Important Files" lists
     to top-5 each. Saves ~28 KB / ~7 K tokens. Risk: more lookups needed
     during sessions.
   - **Recommendation: (a)**. Architecture and Important Files lists are
     read-rarely but high-signal when read; the file tree is read-often
     and drift-prone.

**Waiting for approval before proceeding to Items 5-6.**

---

## Phase 1 (continued) — Items 5-6

**User picks at Checkpoint 1:** A1 (delete merged branches/worktrees, verify
parity/* first) + B1 (light CLAUDE.md trim).

**Verification of Checkpoint-1 assumptions:**

- `fix/ktlint-inline-comment` is **PR #871 OPEN** — active work. The
  `prismTask-ktlint` worktree must be preserved. Top-5 list said 3 of 4
  worktrees were stale; corrected count is **3 stale + 1 active** anyway,
  but the active one is `ktlint`, not what I'd assumed. (`prismTask-ktlint`
  HEAD is `7a11a438`, ahead of main by one commit — the actual PR commit.)
- Firebase MCP plugin is in **active use** (26 of 83 prismTask session
  jsonls reference `mcp__plugin_firebase`, ~31%). Item 2.6 downgraded
  from "verify usage" to "in production, no action."
- All 3 stale worktrees confirmed pointing at MERGED PRs (#859, #844, #847).
- Final cleanup count: **28 merged branches** (excluding `ci-logs` + open
  `fix/ktlint-inline-comment`) and **3 stale worktrees**.

---

### Item 5 — Improvement proposal (sorted by wall-clock-savings ÷ implementation-cost)

| Rank | Change | Impl cost | Wall-clock savings | Risk | Verification |
|---|---|---|---|---|---|
| **1** | **Branch + worktree cleanup.** `git worktree remove` the 3 merged worktrees (`prismTask-conn-tests-audit`, `prismTask-parity-onboarding`, `prismTask-parity-phase3-summary`); `git branch -d` the 28 merged branches (preserve `ci-logs` and `fix/ktlint-inline-comment`). | 5 min one-time | ~15-30 s saved per branch-related CC operation in future sessions. Cumulative across many sessions. | LOW — all underlying PRs are MERGED; recovery is `gh pr checkout <num>` if ever needed. | `git branch --no-merged main` returns ≤2 entries (`ci-logs`, `fix/ktlint-inline-comment`). |
| **2** | **Stale `~/.claude/projects/` dirs cleanup.** `rm -rf` the 5 dirs from old worktree paths: `C--Projects-averyTask-{eisenhower,pomodoro,timeblock,weekly-reviews}` + `C--Projects-pancData3`. Total ~16 MB. | 2 min one-time | Disk only; no per-session wall-clock. | LOW — dirs are inactive. | `du -sh ~/.claude/projects/` drops by ~16 MB. |
| **3** | **CLAUDE.md light trim (B1).** Drop the 163-line Project Structure file tree (lines 43-206) → 30-line top-level overview (`app/src/main/.../{data,domain,ui,notifications,widget}` + 1 sentence each). Drop the migration narrative (line 19 in current doc) → "Current Room version: read `CURRENT_DB_VERSION` from `Migrations.kt` (currently 64); see Phase D bundle audit for design rationale." | 30-45 min one-time | ~5K tokens/session × every session. ~22 KB / 60 % size reduction. | LOW — trimmed sections are read-rarely + drift-prone; replaced with code pointers. | `wc -l CLAUDE.md` < 180 lines. Spot-check next 3 sessions don't hallucinate paths. |
| **4** | **Settings allowlist cleanup.** Strip `pancData3` / `improvement_loop` / `averyTask` references from `.claude/settings.json` and `.claude/settings.local.json`. Keep PrismTask-relevant rules. Net-add a few broader Bash patterns if the cleanup reveals legitimate gaps. | 15 min one-time | Cognitive load reduction; ~5-10 s per future allow-list review. Negligible direct. | LOW — strictly removing rules pinned to nonexistent paths. Test: spot-check that no cleaned rule fires in this repo. | Settings files contain only PrismTask-relevant rules. |
| **5** | **`/audit-first` slash command.** Create `.claude/commands/audit-first.md` extracting the recurring boilerplate ("Hard rules — read first" / "Phase 1 — Audit (single doc, N checkpoint stops)" / "Phase 2 — Implementation" / "Phase 3 — Bundle summary"). User-invokable as `/audit-first <scope-doc-path>` with the user supplying only the scope-specific items. | 30 min one-time | 5-15 min/audit × ~weekly cadence = ~30-60 min/month. | LOW — purely additive; user can keep writing custom prompts when needed. | Next audit started via `/audit-first` produces the standard skeleton. |
| **6** | **Mega-audit length convention.** Add to CLAUDE.md "Repo conventions": *Audit docs cap at ~500 lines per Phase. If a single phase exceeds 500, split into N batches with separate Phase 1 sweeps.* The validated 390-line single-pass shape (PR #859) sets the bar. | 5 min | 5-30 min/audit (prevention) × monthly+. | LOW. | Future audits ≤500 lines/phase. |
| **7** | **Memory orphan + index reconcile.** `project_testdebugunit_blocked.md` exists in `memory/` but is not indexed in MEMORY.md. Either add the index line (if still relevant) or delete the file (if obsolete). Read the file first to decide. | 5 min | None direct. Hygiene. | LOW. | `ls memory/*.md \| wc -l == MEMORY.md entry count` reconciled. |

**Sort by ROI (savings ÷ cost):**
- **Tier 1 (cheap + recurring savings):** #1, #2, #6
- **Tier 2 (real per-session win):** #3
- **Tier 3 (one-time setup, recurring savings):** #5
- **Tier 4 (cleanup hygiene):** #4, #7

---

### Item 6 — Anti-patterns flagged

| # | Anti-pattern | Recent example | Should-fix? |
|---|---|---|---|
| AP-1 | **Mega-audit length** — 1000+ line single-Phase audits invite scope creep that costs wall-clock to write *and* to re-read. | `PRE_PHASE_F_MEGA_AUDIT.md` (1,115 lines), `ANDROID_WEB_PARITY_AUDIT.md` (1,037 lines). The validated single-pass shape (`CONNECTED_TESTS_STABILIZATION_AUDIT.md`, 390 lines, PR #859) hit the same goals at 35 % size. | Yes — handled by improvement #6 (length cap convention). |
| AP-2 | **`[skip ci]` chore-bump architecture forces manual release.yml fire.** Memory #22 documents the workaround (`gh workflow run release.yml --ref vX.Y.Z` after each merge) but the underlying cost — release pipeline doesn't auto-fire on the version-bump tag — recurs every merge. 48 of last 100 commits are `chore: bump … [skip ci]`. | PRs #865 (retire Firebase App Distribution; release every merge), #868 (grant actions:write for release dispatch) suggest this is being actively re-architected. | **Defer** — already in flight via PR #865/#868; let new architecture settle (~1 week) before re-auditing. |
| AP-3 | **CLAUDE.md file tree as drift sink.** Self-acknowledged in CLAUDE.md itself ("read it from there rather than this doc, which drifts"). Every new file in `app/src/main/...` invalidates the tree. | Lines 43-206 of CLAUDE.md. | Yes — handled by improvement #3. |
| AP-4 | **Memory orphan files.** Files exist in `memory/` without an index entry in MEMORY.md, so they don't load into context but consume cap-budget consideration. | `project_testdebugunit_blocked.md` (2.6 KB). | Yes — handled by improvement #7. |
| AP-5 | **Branch deletion never automated.** Memory #16 says "after merge, remove the worktree and delete the branch in the same session" — but it requires the user/CC to remember. The squash-merge model means `git branch -d` fails with "not fully merged" forcing `-D`, which is friction that delays the cleanup. | 28 stale merged branches accumulated over the audit window. | **Optional** — memory rule already covers this; could add a post-merge hook but adds complexity. Recommend: include branch cleanup in `commit-push-pr` slash command if it isn't there already, OR add a daily-tick cleanup script. |
| AP-6 | **Stale settings paths from rebrand.** PrismTask was renamed from AveryTask but `.claude/settings.{json,local.json}` still has 24+ rebrand-era path entries. The stale-path drift will keep recurring as PrismTask is renamed/refactored unless a cleanup convention exists. | Item 4.1, 4.2 in this audit. | Yes — handled by improvement #4. |

**Anti-pattern severity assessment:** AP-1, AP-3, AP-4, AP-6 all map to
specific improvements above. AP-2 is in flight. AP-5 is borderline — the
memory rule is correct but compliance depends on session discipline.

---

## Checkpoint 2 — STOP

### Improvement proposal table (re-sorted by ROI)

| ROI tier | # | Change | Cost | Recurring? |
|---|---|---|---|---|
| Tier 1 | 1 | Branch + worktree cleanup | 5 min | Yes (per future op) |
| Tier 1 | 2 | `~/.claude/projects/` stale dir delete | 2 min | One-time |
| Tier 1 | 6 | Mega-audit length cap convention | 5 min | Yes (per audit) |
| Tier 2 | 3 | CLAUDE.md light trim | 30-45 min | Yes (per session) |
| Tier 3 | 5 | `/audit-first` slash command | 30 min | Yes (per audit) |
| Tier 4 | 4 | Settings allowlist cleanup | 15 min | One-time |
| Tier 4 | 7 | Memory orphan reconcile | 5 min | One-time |

### Anti-patterns flagged (recap)

- AP-1, AP-3, AP-4, AP-6: addressed by improvements #6, #3, #7, #4 respectively.
- AP-2: defer (PR #865/#868 in flight).
- AP-5: optional automation gap.

### Recommended Phase 2 sequencing

Three small bundled PRs + one optional follow-up:

1. **PR-A: Hygiene bundle (~10 min total).** Improvements #1 + #2 + #4 + #7.
   Branch: `chore/cc-audit-hygiene-bundle`. Squash-merge, no `[skip ci]`,
   trailing newline on CHANGELOG. Most of the work is `git` + `rm` operations
   you (the user) run locally; the PR carries the settings-file diffs +
   memory-index reconcile.
2. **PR-B: CLAUDE.md trim (~30-45 min).** Improvement #3 alone. Branch:
   `chore/cc-audit-claude-md-trim`. Light-trim per pick B1.
3. **PR-C: Length-cap convention (~5 min).** Improvement #6. Branch:
   `chore/cc-audit-length-cap-convention`. One-line addition to CLAUDE.md
   "Repo conventions" section.
4. **PR-D (optional): `/audit-first` slash command.** Improvement #5.
   Branch: `chore/cc-audit-slash-command`. Defer if you want to live with
   manual prompts a while longer; ship if next 1-2 audits feel boilerplate-heavy.

### Memory candidates

None recommended this round. The audit reduces memory pressure (frees
slot for `project_testdebugunit_blocked.md` decision) rather than adding
to it. Memory cap is 22 of 30 — healthy.

### Re-audit cadence

Quarterly, or after another major process change (Phase F kickoff +
post-launch G.0 are likely triggers). Next: ~2026-07-28 unless prompted
sooner.

---

**Phase 1 complete. Waiting for Phase 2 approval.**

**Asks:**

- **C. Phase 2 PR scope.** Three options:
  1. Ship PRs A + B + C now; defer D. **(My pick — best ROI/risk balance.)**
  2. Ship PR A now (cheap wins); defer B/C/D until separate session.
  3. Ship all four (A + B + C + D) in this session.

Reply with your pick (e.g. "C1") and I'll begin Phase 2.

