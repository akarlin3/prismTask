---
description: Standard audit-first workflow boilerplate — Phase 1 audit doc, Phase 2 PR fan-out, optional Phase 3 summary
---

# Audit-first workflow

Use this when starting a multi-step audit/investigation. The user provides
**scope** (a markdown file path, an inline scope description, or a list of
items). This command sets up the standard structure so the user doesn't
have to rewrite the boilerplate every time.

## Hard rules — read first

- **Audit-first.** Phase 1 produces NO config or code changes. Audit doc only.
- **Self-investigate first, ask second.** Read the artifacts before asking the
  user questions. Specific examples > general principles.
- **STOP and report on wrong premises.** If a premise turns out wrong, stop
  and report rather than rationalizing scope.
- **Per CLAUDE.md "Audit doc length"**, cap each Phase at ~500 lines. Above
  that, split into batches with separate Phase 1 sweeps. The validated
  single-pass shape is `docs/audits/CONNECTED_TESTS_STABILIZATION_AUDIT.md`
  (390 lines, PR #859).
- **Skip checkpoint stops by default** (memory `feedback_skip_audit_checkpoints.md`)
  unless the user explicitly asks for them.

## Phase 1 — Audit (single doc)

Create `docs/audits/<SCOPE_SLUG>.md`. For each scoped item use:

1. **Premise verification.** Does the item describe real codebase reality?
2. **Findings.** What did the sweep surface? Cite files / line numbers / PR
   numbers / commit SHAs.
3. **Risk classification.** RED / YELLOW / GREEN / DEFERRED.
4. **Recommendation.** PROCEED, STOP-no-work-needed, or DEFER.

End the audit doc with a ranked improvement table sorted by
**wall-clock-savings ÷ implementation-cost**, plus an anti-pattern list
for things worth flagging but not necessarily fixing.

## Phase 2 — Implementation (after Phase 1 approval)

Per-improvement shape:

- Branch: `<type>/<scope-slug>` (e.g. `chore/...`, `fix/...`, `ci/...`).
- Squash-merge auto-merge via `gh pr merge <num> --auto --squash`.
- Required CI green.
- **No `[skip ci]`** in commit messages — applies regardless of quoting
  (`feedback_skip_ci_in_commit_message.md`).
- Trailing newline on `CHANGELOG.md` if touched.
- Use a worktree per feature (memory `feedback_use_worktrees_for_features.md`);
  remove the worktree + delete the branch the same session it merges
  (memory: worktree teardown paired with merge).

Bundle multiple small fixes into one PR only when they're a single coherent
scope; otherwise prefer N small PRs (fan-out bundling rule).

## Phase 3 — Bundle summary (optional)

After Phase 2 PRs merge, append to the audit doc:

- Per-improvement: PR number(s), measured impact (if measurable post-merge).
- Re-baselined wall-clock-per-PR estimate (if relevant).
- Memory entry candidates (only if surprising / non-obvious).
- Schedule for next audit.

## Args

The user may pass `<scope>` as:

- A path to a markdown file describing scoped items — read it first.
- An inline scope description — use it directly.
- Nothing — ask the user for the scope (one sentence each: domain,
  optimization target, suspected-failure-modes).

## Reference: relevant memory entries

- `feedback_use_worktrees_for_features.md` — worktree teardown paired with merge.
- `feedback_skip_ci_in_commit_message.md` — `[skip ci]` blocks all workflows.
- `feedback_skip_audit_checkpoints.md` — skip checkpoint stops by default.
- `feedback_audit_drive_by_migration_fixes.md` — `git log -p -S` before recommending fixes.
- `feedback_repro_first_for_time_boundary_bugs.md` — write the structural repro test first.
