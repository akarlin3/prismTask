# Automation: cannot create new automations — audit

**Scope (operator):** "Automation doesn't allow for the making of new
automations."

**Domain:** the v1.7+ Automation feature (IFTTT-style rules engine
shipped in PR #1056, starter library shipped in PR #1057).

**Optimization target:** restore "user can create a new automation rule
from scratch" — currently the only authoring path is importing a
template, then living with whatever fields the template encoded. Even
edit-after-import is not supported.

**Suspected failure modes going in:** copy/discoverability gap; the
edit screen was a known v1.1 defer; the deferred work may have been
silently completed but never shipped.

**Verdict at a glance.** All four scoped items are real. The one with
asymmetric leverage is **A1+A2 (RED)** — the v1.1 follow-up has
already been written and pushed to a feature branch (SHA `4aa9ad96`,
~787 LOC, includes FAB + Edit overflow + new edit screen + sync mapper)
but no PR was ever opened, so the work has been sitting orphan on
`origin/claude/automation-rules-engine-j32o8` since 2026-05-02. The fix
shape is "rebase that commit on current main, resolve the three
overlapping files from PR #1057, open a PR." Wall-clock-savings ÷ cost
is the highest in the timeline-bundle below.

## A1 — No "create from scratch" UI on the rule list (RED)

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/ui/screens/automation/AutomationRuleListScreen.kt:51-100`
  — the `Scaffold` has no `floatingActionButton`. The top-bar
  `actions` slot has exactly two `IconButton`s: `LibraryBooks` (browse
  templates, line 61-68) and `History` (run history, line 69-73).
  There is no third entry point for "new rule."
- The empty-state composable
  (`AutomationRuleListScreen.kt:103-120`) explicitly steers users
  toward the seed/library path: *"Sample rules are seeded on first
  launch and appear here disabled. Toggle one on, or tap the library
  icon above to browse the full starter catalog."* No mention of
  "create your own."
- Route inventory in
  `app/src/main/java/com/averycorp/prismtask/ui/navigation/NavGraph.kt:271-281`:
  the only three automation routes are `Automation` (list),
  `AutomationLog`, and `AutomationTemplateLibrary`. There is no
  `AutomationEdit` / `AutomationCreate` route.
- `app/src/main/java/com/averycorp/prismtask/ui/navigation/routes/SettingsRoutes.kt:66-96`
  registers the same three routes; no edit/create destination.
- The architecture spec acknowledged this gap up front:
  `docs/audits/AUTOMATION_ENGINE_ARCHITECTURE.md:29` (§ A7) says the
  edit screen is a v1.1 follow-up, and `:352` calls it out as "the
  one explicitly scoped-out item per the LOC budget."

**Recommendation: PROCEED.** This is the user's exact complaint and
the gap is real, not a misread of the UI.

## A2 — No "edit existing rule" UI either (RED, same root cause)

**Findings.**

- `AutomationRuleListScreen.kt:184-209` — the `OverflowMenu` for each
  rule card offers exactly two items: "View Run History" and (if
  not built-in) "Delete Rule." There is no "Edit Rule" entry.
- The `RuleCard` body itself has no tap-to-edit behaviour
  (`:122-181`). Tapping the card surface does nothing; only the
  Switch, Run-Now, and overflow icons are interactive.
- `docs/automation/STARTER_LIBRARY.md:109` documents the user-facing
  consequence: *"The v1.7 series ships the rule list + library; the
  in-app rule editor [is the next step]"*. The user-facing FAQ at
  `:126` further confirms: even an imported template's settings are
  effectively immutable post-import — the workaround is delete + re-
  import.
- `docs/audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md:182`
  spelled out the v1 limitation explicitly: *"v1 ships read-only
  (rule edit screen is v1.1 follow-up per engine doc § A9). User can
  delete + re-import a non-builtin rule to 'reset'."*

**Recommendation: PROCEED.** Same root cause as A1 — both gaps close
with the same edit-screen ship. Bundling A1 + A2 is the natural shape
because the orphan commit already implements both (FAB → create flow,
overflow item → edit flow, single screen handles both via
SavedStateHandle ruleId).

## A3 — Empty-state copy hides that user-defined rules are even possible (YELLOW)

**Findings.**

- `AutomationRuleListScreen.kt:113-118` empty-state copy mentions only
  seeded rules and the library. A user who lands on the empty state
  has no signal that "create your own" is even on the roadmap, let
  alone shippable.
- This is downstream of A1 — once the FAB lands, the empty state must
  be reworded to mention the new path. The orphan commit `4aa9ad96`
  does **not** appear to update this copy (its diffstat lists
  `AutomationRuleListScreen.kt | 24 ++` which fits FAB + Edit
  overflow + minor; copy-rewording is a separate, ~3-line edit).

**Recommendation: PROCEED — bundle into the same PR.** Trivial copy
fix that becomes wrong the moment A1+A2 ship.

## A4 — Imported rules ship disabled with no edit-before-enable (YELLOW)

**Findings.**

- `docs/audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md:180`:
  imported rules land with `enabled=false`. Without an edit screen,
  the user can only flip the switch on — they cannot adjust the
  template's fields (notification body, hour for a daily trigger,
  priority threshold) before activating. Effectively forces "trust
  the template authors' defaults."
- This becomes a non-issue once A1+A2 ship: the user can tap the
  imported rule → Edit → adjust → enable.
- Without the edit screen this is acutely felt for the time-keyed
  templates (e.g., the "Daily 7:00" library entries documented in
  `docs/audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md:109`
  table) — the user has no way to change the time.

**Recommendation: STOP-no-work-needed (resolves automatically when A1+A2 land).**
List in Phase 4 handoff so the next session knows it was scoped and
intentionally not addressed separately.

## A5 — The v1.1 follow-up has been written but not shipped (RED — process)

**Findings.**

- `git log origin/claude/automation-rules-engine-j32o8 --not origin/main`
  shows three commits ahead of main:
  - `d6f7e061` — the v1.0 work that landed as PR #1056
  - `6a44e39d` — auto-format
  - **`4aa9ad96` — `feat(automation): land deferred handlers + rule edit screen + sync mapper`**
- `gh pr list --state all --search "automation rule edit"` returns
  zero PRs for `4aa9ad96`. PR #1056 closed against `d6f7e061`; no
  separate PR was opened for the follow-up commit.
- `git show --stat 4aa9ad96` confirms the commit lands:
  - `ui/screens/automation/AutomationRuleEditScreen.kt` (+339 LOC) — block-based editor
  - `ui/screens/automation/AutomationRuleEditViewModel.kt` (+233 LOC)
  - `ui/screens/automation/AutomationRuleListScreen.kt` (+24/-?) — adds FAB + "Edit Rule" overflow
  - `ui/navigation/NavGraph.kt` (+6) — `PrismTaskRoute.AutomationEdit`
  - `ui/navigation/routes/SettingsRoutes.kt` (+16) — registers the new route
  - `data/remote/mapper/SyncMapper.kt` (+53) — `automationRuleToMap` / `mapToAutomationRule`
  - `domain/automation/handlers/SimpleActionHandlers.kt` (+89/-?)
  - `domain/automation/handlers/AiActionHandlers.kt` (+61/-?)
  - Total: 787 insertions across 8 files.
- The commit message body explicitly says the handlers it
  un-stubs are `schedule.timer`, `apply.batch`, and
  `mutate.medication`. Note: `schedule.timer` and
  `mutate.medication` were *also* un-stubbed independently in PR
  #1057 (commit `a14baaca` on origin/main per `git log -- handlers`).
  → The orphan commit's handler hunks will conflict.
- The rule-edit Compose surface (the 339+233 LOC) is all net-new and
  cannot conflict with main.
- The `AutomationRuleListScreen.kt` hunks WILL conflict with PR
  #1057's commit `23e82c60` which added the LibraryBooks icon to the
  same `actions` slot the FAB sibling lives near.

**Recommendation: PROCEED.** Land the orphan commit. This is the
single highest-leverage fix in the audit because:
- The implementation work is done (~787 LOC).
- The conflicts are *known* and bounded (3 files: `AutomationRuleListScreen.kt`,
  `SimpleActionHandlers.kt`, `AiActionHandlers.kt`).
- The unshipped scope avoids the same risk surface PR #1056 deferred
  (no SyncService routing, no AI endpoint wiring — see commit body).

## A6 — SyncService routing for `automation_rule` (RED, PROCEED)

**Findings.**

- The orphan commit `4aa9ad96` ships the mapper layer
  (`data/remote/mapper/SyncMapper.kt` +53 — `automationRuleToMap` and
  `mapToAutomationRule` round-trip the three structural blobs as
  opaque strings). The commit explicitly leaves SyncService
  consumption out: *"SyncService routing through this mapper is a
  follow-up… the mapper exists so that work is mechanical."*
- `AutomationRuleRepository.create()` already calls
  `syncTracker.trackCreate(id, "automation_rule")` (confirmed in
  `docs/audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md:18`). The
  push-pending sweep won't act on those entries until the routing
  arm exists — every imported rule today gets stranded in
  `SyncTracker` with no consumer.
- `app/src/main/java/com/averycorp/prismtask/data/remote/SyncService.kt`
  is ~1500 LOC of consistent push/pull/listen patterns per entity
  (`task`, `habit`, `task_template`, `habit_template`, etc.). Adding
  `automation_rule` is mechanical: inject `AutomationRuleDao`, add a
  push arm in `pushPending` that calls
  `docRef.set(SyncMapper.automationRuleToMap(rule))`, add a pull arm
  in `pullAll`, register a real-time listener.

**Recommendation: PROCEED.** Rule of thumb from the existing entity
arms: ~120-200 LOC matching e.g. `task_template`. Single PR after the
orphan-commit rebase lands so it builds on the merged mapper.

## A7 — Backend `/ai/automation/*` endpoints + on-device handler wiring (RED, PROCEED)

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/domain/automation/handlers/AiActionHandlers.kt:11-26`
  — the doc-comment on `AiCompleteActionHandler` already specifies
  the exact contract: *"routed through the backend
  `/api/v1/ai/automation/{action}` endpoints, which inherit the
  existing `/ai/` prefix entry in
  `AiFeatureGateInterceptor.AI_PATH_PREFIXES`."*
- `AiCompleteActionHandler.execute()` (`:33-46`) and
  `AiSummarizeActionHandler.execute()` (`:55-68`) currently both
  return `ActionResult.Skipped` with a "backend endpoint pending"
  reason. Local feature-gate check is in place; only the network
  round-trip is missing.
- `backend/app/routers/ai.py:57-60` — the `/ai/*` router already
  ships with PII egress audit + Anthropic key wiring + AI feature
  gate. Existing siblings include `/ai/eisenhower`,
  `/ai/pomodoro-plan`, `/ai/daily-briefing`, `/ai/weekly-review`,
  `/ai/tasks/extract-from-text`, `/ai/batch-parse`, `/ai/chat`.
  Adding `/ai/automation/complete` and `/ai/automation/summarize` is
  the same pattern as any other endpoint on this router.

**Recommendation: PROCEED.** Single coordinated PR (backend + app):
- Backend: two new endpoints with Pydantic schemas + tests in
  `backend/tests/`. Reuse the existing rate-limit decorator and
  feature gate.
- App: replace the two `Skipped` returns with real OkHttp calls
  through the existing AI path (the interceptor already 451-gates
  via the `/ai/` prefix).

The orphan commit body's "compliance review" note appears to be
generic, not blocking — the existing `/ai/*` router already passed
the same review.

## A8 — Full AND/OR/NOT condition tree editor (RED, PROCEED)

**Findings.**

- The orphan commit `4aa9ad96`'s `AutomationRuleEditScreen.kt` (+339
  LOC) ships a *single Compare leaf* editor — confirmed in commit
  body: *"single Compare leaf with field path / operator / value
  pickers — full AND/OR/NOT tree remains supported on disk."*
- `app/src/main/java/com/averycorp/prismtask/domain/automation/AutomationCondition.kt`
  defines `Compare`, `And`, `Or`, `Not` as a sealed hierarchy and
  round-trips the full tree through `AutomationJsonAdapter`. The
  storage shape is already capable; only the editor UI is missing.
- Without this, a user creating a rule from scratch can't express
  multi-clause conditions ("priority ≥ High **and** has tag #urgent")
  even though the engine evaluates them correctly when imported via
  template.

**Recommendation: PROCEED.** Lands as a follow-up PR after A1+A2
ship. Implementation shape: a recursive Composable that renders each
node (`Compare` → leaf editor; `And`/`Or` → vertical group with add-
clause + boolean toggle; `Not` → wrapper). Modelled on the recursive
project-tree editor pattern already used in
`ProjectDetailScreen.kt`. Estimate ~300-500 LOC.

## A9 — Composed-trigger picker (RED, PROCEED)

**Findings.**

- `AutomationTrigger.kt` defines a `Composed` variant (parent rule id +
  composition kind) used by IFTTT-style chains. The orphan commit's
  edit screen exposes the four primitive triggers (entity event /
  time-of-day / manual / day-of-week-time) but the composed picker
  remains library-import-only.
- Without the picker, users can't author "when rule X fires, then
  also do Y" chains from scratch — they have to find or build a
  template that already encodes the chain.

**Recommendation: PROCEED.** Bundle into the same PR as A8 — both
expand the orphan commit's edit screen along the same axis (richer
node pickers). Implementation shape: a dropdown of `AutomationRule`s
filtered to `enabled=true` excluding the rule being edited (avoid
self-referential chains). Estimate ~100-200 LOC including the cycle-
detection guard.

## A10 — Un-stub `apply.batch` action handler (RED, PROCEED)

**Findings.**

- `AiActionHandlers.kt:80-91` — `ApplyBatchActionHandler.execute()`
  returns `Skipped` with reason: *"apply.batch deferred to v1.1 —
  needs `BatchOperationsRepository.applyBatchSynthetic` extraction."*
- The orphan commit `4aa9ad96`'s body claims this handler is
  un-stubbed (*"converts each action map into a
  `ProposedMutationResponse` and delegates to
  `BatchOperationsRepository.applyBatch`"*). Spot-check needed
  during rebase to confirm the orphan implementation actually lands.
  If the orphan implementation works, A10 closes "for free" with the
  A1+A5 rebase. If not (or the conflict resolution drops it), this
  becomes a small standalone follow-up.
- Several library templates encode batch mutations (per
  `docs/audits/AUTOMATION_STARTER_LIBRARY_ARCHITECTURE.md:81`'s
  "no timer-starting rules" caveat — `apply.batch` is the workaround
  for several scenarios). They currently log "Skipped" on every fire.

**Recommendation: PROCEED — verify during A1+A5 rebase first.** If
the orphan implementation survives the rebase, mark complete in
Phase 3. If not, file a small standalone PR.

## Anti-patterns (note, do not necessarily fix)

- **Follow-up commits pushed to an already-merged feature branch
  without opening a new PR.** Commit `4aa9ad96` was pushed to
  `claude/automation-rules-engine-j32o8` on 2026-05-02 06:00 UTC,
  ~1h after PR #1056 merged. The branch had served its purpose; the
  follow-up commit went orphan because no one opened a new PR for it.
  Lesson: when a feature merges as one PR, subsequent work belongs on
  a fresh branch with its own PR — pushing to the now-archived
  branch is a quiet way to lose the work.
- **Documenting "v1.1 follow-up" in a design doc without an issue or
  schedule.** `AUTOMATION_ENGINE_ARCHITECTURE.md:352` and
  `STARTER_LIBRARY.md:109` both refer to the edit screen as a v1.1
  follow-up, but there's no GitHub issue, no schedule entry, and no
  reminder anywhere that points back at SHA `4aa9ad96`. The work was
  one `gh pr create` away from shipping for the entire interval.
  Lesson: for explicit "v1.1 follow-up" defers, either land the
  work in the same fan-out OR open an issue with the orphan SHA so
  the path back in is one click.

## Improvement timeline

Sorted by wall-clock-savings ÷ implementation-cost. Each row is one
PR's worth of work. Phase 2 fan-out fires automatically — single
worktree per PR per the workflow's worktree rule.

| Rank | Item | Wall-clock savings | Implementation cost | PR shape |
|------|------|--------------------|---------------------|----------|
| 1 | **A1+A2+A3+A5+A10 — land orphan rule-edit screen + FAB + edit overflow + empty-state reword + verify `apply.batch` un-stub** | High — closes the operator's exact complaint; A4 also resolves; A10 likely closes for free | Low — ~787 LOC already written on `origin/claude/automation-rules-engine-j32o8`; rebase, resolve 3 known conflicts (`AutomationRuleListScreen.kt`, `SimpleActionHandlers.kt`, `AiActionHandlers.kt`), add ~3-line copy edit, verify tests | Branch `feat/automation-rule-edit-screen`, cherry-pick SHA `4aa9ad96` onto current main |
| 2 | **A6 — SyncService routing for `automation_rule`** | Medium — imported rules currently strand in `SyncTracker` with no consumer; cross-device sync silently no-ops | Low — mapper ships in PR #1; ~120-200 LOC matching `task_template`'s push/pull/listener arms | Branch `feat/automation-sync-routing`, depends on PR #1 merging first |
| 3 | **A7 — Backend `/ai/automation/{complete,summarize}` + on-device handler wiring** | Medium — every AI-action library template logs "Skipped: backend pending" today; users can import them but they never fire | Medium — two new endpoints on existing `/ai/*` router (Pydantic + tests + handler doc-comment contract is exact); two on-device handler call sites swap from `Skipped` to real calls | Backend + app paired PR on `feat/automation-ai-endpoints` |
| 4 | **A8+A9 — Full AND/OR/NOT condition tree + composed-trigger picker** | Medium — completes the "create from scratch" promise for power-user shapes the engine already supports | Medium — recursive Composable for the tree (~300-500 LOC) + dropdown for composed parent rule id (~100-200 LOC) | Branch `feat/automation-rich-editor`, depends on PR #1 |

**Sequencing note.** PR #1 is the atomic unblocker — every other PR
either depends on its merged surface (A6, A8, A9) or is most
efficiently verified after it lands (A10). PR #2 and PR #3 are
independent of each other once PR #1 is in. Plan: ship PR #1
first, then PRs #2 and #3 in parallel worktrees, then PR #4 last.

## Verification before claiming Phase 1 done

- [x] Premise restated in operator's own framing.
- [x] Every scoped item has a citation by file:line or PR/SHA.
- [x] At least one finding goes against the prevailing assumption
      (here: "this is a missing feature" → reality: "the feature is
      written but unshipped").
- [x] Improvement table sorted by savings ÷ cost; row 1 has the
      strongest ratio.
- [x] DEFERRED items listed so they're not re-litigated.
- [x] Anti-patterns called out separately from PROCEED items.
