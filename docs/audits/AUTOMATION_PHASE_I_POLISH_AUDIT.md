# Automation Phase I Polish — Auto-Enable Migration + Same-Template Dedup

**Date:** 2026-05-03
**Operator framing:** Two low-effort Phase I follow-ups bundled into one
audit per memory `feedback_audit_drive_by_migration_fixes.md` and the
fan-out bundling rule. Source items:

1. **AI-action template auto-enable migration** — premise: AI templates
   shipped pre-#1071 could not fire because the backend wasn't wired;
   now that #1071 has landed, retroactively enable them.
2. **Same-template-imported-on-two-devices dedup sweeper** — premise:
   importing the same template on Device A and Device B produces two
   rules with the same `templateKey` after sync (#1070 routing landed
   2026-05-03 03:18 UTC). Operator picked Option (b) "post-sync sweeper"
   per forgiveness-first UX framing.

**Verdict preview:** Item 1 = STOP (premise invalidated by recon —
marker doesn't exist, all template imports default to `enabled=false`
per #1056 UX policy, no signal distinguishes user-disable from
"system-disable"). Item 2 = PROCEED with PIVOT — sweeper is the wrong
solution shape; the canonical `naturalKeyLookup` pattern already used by
6 sibling entities solves the same problem at-insert with strictly
better tradeoffs. Net Phase 2 ship: 1 PR, ~15 LOC + tests.

---

## 1 — AI-Action Template Auto-Enable Migration (RED, STOP)

### Premise verification

The operator scope describes a "pending backend" marker as the
load-bearing signal — a discrete field, AutomationLog string, or
convention-based combination that distinguishes "AI template currently
disabled awaiting backend" from "user explicitly disabled this rule."
Recon evidence:

- **No discrete field.** `AutomationRuleEntity`
  (app/src/main/java/com/averycorp/prismtask/data/local/entity/AutomationRuleEntity.kt:30-61)
  carries `enabled: Boolean`, `templateKey: String?`, `isBuiltIn: Boolean`,
  and the three structural JSON blobs. No `pendingBackend`,
  `pendingBackendAction`, or equivalent.
- **No log string.** `grep -rn "Skipped: backend pending\|backend pending"`
  across the entire automation tree returns zero hits. The only related
  string is `"AI features disabled (server-side 451)"` in
  `AiActionHandlers.kt:115`, which is emitted at *firing time* if the
  rule is enabled — not at import time.
- **All seeded sample rules ship `enabled = false`.**
  `AutomationSampleRulesSeeder.seedIfNeeded()`
  (app/src/main/java/com/averycorp/prismtask/data/seed/AutomationSampleRulesSeeder.kt:24-41)
  uses `enabled = false` for ALL 5 first-install templates — the AI rule
  (`focusAiSummarizeCompletions`) AND the 4 non-AI rules
  (`notifyOverdueUrgent`, `morningKickoff`, `streak7`,
  `frictionAutotagToday`). The disabled state is the universal default,
  not an AI-specific signal.
- **`AutomationTemplateRepository.importTemplate()` also ships
  `enabled = false`.**
  (app/src/main/java/com/averycorp/prismtask/data/repository/AutomationTemplateRepository.kt:58-71)
  The KDoc explicitly states *"Persists the template as a user rule
  with `enabled = false` (per PR #1056 UX choice — never auto-fire on
  update install)."* This is by design across all 27 starter templates,
  not a backend-readiness compromise.

### Findings

The premise that "AI templates are stuck in a 'pending backend' state
that needs auto-enable" is **factually incorrect**. The mechanism the
migration would key on does not exist:

1. **No retroactive marker is recoverable.** Even if we added a discrete
   field today via Room migration, we cannot backfill it correctly. The
   only signal we'd have to set it is "current `enabled = false` AND
   action is `ai.complete`/`ai.summarize`" — which is identical to the
   "user explicitly disabled this rule" state. The signal needed to be
   written at the time of disabling; it wasn't, and history can't
   recover it.

2. **The only AI rule users could have hit is `focusAiSummarizeCompletions`
   from FIRST_INSTALL_SEED_IDS.** The other 4 AI templates
   (`starter.med.weekly_ai_summary`, `starter.power.manual_ai_briefing`,
   `starter.power.daily_ai_eod_summary`,
   `starter.power.weekly_ai_reflection`) only land in the user's rule
   list if they explicitly import via the Template Library UI — and
   that import already sets `enabled = false`. There is no "stuck
   disabled" cohort to migrate.

3. **The migration would clobber explicit user intent.** A user who
   imported `starter.power.manual_ai_briefing`, decided they don't want
   it, and left it disabled is indistinguishable from a hypothetical
   user who imported it pre-#1071. Auto-enable would silently flip a
   user-deliberated decision to `enabled = true` — and Manual triggers
   are RUN-ON-DEMAND, but TimeOfDay AI rules
   (`starter.med.weekly_ai_summary`, `starter.power.daily_ai_eod_summary`,
   `starter.power.weekly_ai_reflection`) would start firing scheduled
   AI calls without consent.

4. **Cross-device LWW makes this worse.** Per
   `SyncMapper.automationRuleToMap` (line 1258) full-doc LWW syncs
   `enabled` as a regular field. If the migration fires on Device A
   (sets `enabled = true`), and Device B has an explicit user-disable
   with newer `editedAt`, the user's choice survives — but the reverse
   case (migration runs on Device A AFTER any edit on Device B) clobbers
   Device B's user state on next sync. There is no field-level merge
   for `enabled`.

5. **Users have a working surface to enable AI rules themselves.**
   PR #1069 + #1074 shipped the rule list + edit screen + FAB. A user
   who wants `starter.med.weekly_ai_summary` to fire can toggle it from
   the rule list in two taps. This is the right entry point — explicit,
   visible, and reversible. Migration adds nothing the rule list
   doesn't already provide.

### Risk classification

**RED.** The migration premise is invalidated by recon. Implementing it
under any of the operator's three pre-approved fallback shapes leads to
a worse outcome than not shipping:

- **(a) Add discrete marker inline.** Cannot backfill the marker
  correctly — see Finding 1. Going forward it would work for new rules,
  but new rules don't have the problem (they're imported post-#1071
  with the backend ready). Net value: zero.
- **(b) TemplateKey allowlist + date filter.** Date filter
  `createdAt < #1071-ship-time` (2026-05-03 03:33 UTC) is a sensible
  bound, but inside that bound it cannot distinguish user-deliberated
  disable from un-deliberated disable. Clobbers explicit intent — see
  Finding 3.
- **(c) STOP.** Per operator scope: *"If 'pending backend' marker
  doesn't exist as discrete field AND can't be reliably approximated
  via templateKey allowlist + date filter, STOP."* This is the
  operator's own pre-approved fallback for this exact recon outcome.

### Recommendation

**STOP — close timeline item with rationale, do not ship migration.**

Defer-minimization (memory #30) is satisfied by closing the item rather
than punting it. There is no follow-up "this was deferred for X
reason" — there is "this was evaluated and determined to add no value."
The audit doc itself is the durable record of why.

If a future signal emerges (e.g. user reports of AI rules sitting
disabled and causing confusion), revisit by adding a one-time UI nudge
("we noticed you have N disabled AI rules — enable them now?") rather
than a silent migration. UX surface beats data-layer mutation for this
class of "user might want this" decision.

---

## 2 — Same-Template Dedup (GREEN — PROCEED with PIVOT)

### Premise verification

Operator's premise: importing the same template on Device A and Device B
produces two rows with the same `templateKey` after sync. Recon evidence:

- **`importTemplate` does not dedup at insert.**
  `AutomationTemplateRepository.importTemplate(templateId)` (line 58)
  unconditionally calls `ruleRepository.create(...)`. There's no
  `getByTemplateKeyOnce` check before insert. So a user importing the
  same template twice on one device already produces two local rows —
  cross-device sync makes the same problem visible after one round-trip.
- **`SyncService.pullRoomConfigFamily` (line ~2860) already supports a
  `naturalKeyLookup` parameter for this exact case.** The
  `automation_rules` call site at line 2526 does NOT pass it. Six
  sibling entities DO:
  - `nlpShortcutsResult` (line 2459, key: phrase)
  - `checkInLogsResult` (line 2542, key: date) — comment cites
    "P0 sync audit PR-C: same-day logs created on both devices offline
    must dedup on pull"
  - `moodEnergyLogsResult` (line 2563, key: date+timeOfDay)
  - `medicationRefillsResult` (line 2590, key: ...)
  - `weeklyReviewsResult` (line 2613)
  - `dailyEssentialSlotResult` (line 2635)
- **`AutomationRuleDao.getByTemplateKeyOnce(templateKey)` already
  exists** (app/src/main/java/com/averycorp/prismtask/data/local/dao/AutomationRuleDao.kt:31-32)
  — the lookup the canonical pattern needs is already on the DAO surface.
- **`templateKey` is nullable.** User-authored rules carry
  `templateKey = null`; the natural-key path returns `null` for them
  (no false dedup of unrelated user rules).

The duplicate scenario IS real — pull-side adoption is missing.

### Findings

Operator's proposed fix shape (Option b: post-sync sweeper) and the
canonical pattern (at-insert `naturalKeyLookup`) solve the same problem
with very different tradeoffs:

| Concern | Sweeper (Option b) | naturalKeyLookup (canonical) |
|---|---|---|
| Duplicates ever exist locally | Yes, briefly | No |
| User-visible dup window | < 5s typical | Zero |
| Race protection needed | Yes (5s threshold per B.4) | No (handled in pull tx) |
| Lost user customization risk | Real (B.3 picks "newest wins" but adds complexity) | Zero (LWW on `updatedAt` already correct) |
| Background work cost | Per-sync DB scan | Zero |
| Pattern consistency | Net-new shape | Matches 6 sibling entities |
| LOC | ~120-180 (per scope estimate) | ~5 + a few-line helper |
| Reversibility | Complex (sweeper deletes rows; "undo" is repository-level) | Trivial (rebuild metadata mapping) |

The canonical path also resolves operator's concern B.6 ("duplicates
visible briefly") — they're never visible at all.

`pullRoomConfigFamily` LWW logic
(SyncService.kt:~2860) on natural-key adoption:

```
if (remoteUpdatedAt > localUpdatedAt) update(data, existingLocalId, cloudId)
syncMetadataDao.upsert(SyncMetadataEntity(localId = existingLocalId, ...))
```

This is the forgiveness-first UX shape the operator wanted from B.3
("most-recently-edited wins"). It's already implemented; we just need
to wire it up.

### One open subquestion: pre-existing duplicates from the #1070 → fix window

Sync routing landed 2026-05-03 03:18 UTC. Current time 2026-05-03 (per
env). Window for users to have produced cross-device duplicates ranges
from hours to a single day. Two options:

- **(a) Ship at-insert fix only.** Anyone with pre-existing duplicates
  manually deletes them via the rule list. Simplest scope.
- **(b) At-insert fix + one-time backfill sweep on app start.** Runs
  once gated by SharedPreferences flag, groups by templateKey, keeps
  the row with the newest `updatedAt`, deletes the rest.

Recommend (b). The window is small, but the additional code (~30 LOC)
is bounded, the backfill matches the same LWW semantics as the new
at-insert path, and shipping (a) alone leaves a small but real "user
silently has duplicates from a 24h window of vulnerability" cohort.

### Risk classification

**GREEN.** Pivoting from sweeper to canonical pattern is lower risk on
all axes:
- No new pattern to maintain — matches existing entities.
- No race window to reason about.
- No background scan cost.
- LWW correctness already proven by sibling entities.
- One-time backfill sweep is bounded, idempotent, and gated.

### Recommendation

**PROCEED.** Single PR shipping:

1. `naturalKeyLookup = { data -> (data["templateKey"] as? String)?.let
   { key -> automationRuleDao.getByTemplateKeyOnce(key)?.id } }` on the
   `automationRulesResult = pullRoomConfigFamily(...)` call site
   (SyncService.kt:2526). ~5 LOC.
2. `AutomationDuplicateBackfill` — one-shot backfill in
   `PrismTaskApplication.onCreate()` guarded by SharedPreferences flag
   (`automation_dup_backfill_v1_done`). Groups
   `automationRuleDao.getAllOnce()` by `templateKey` (skip null),
   keeps `maxByOrNull { it.updatedAt }`, deletes the rest via
   `automationRuleRepository.delete(id)` (so SyncTracker records the
   deletes and they propagate to peers). ~30 LOC.
3. Logging: each consolidation logs `templateKey + deletedRuleIds +
   keptRuleId + reason`. Backfill writes a single `Log.i` summary
   ("dedup backfill: N templates consolidated, M rows deleted").
4. Tests:
   - `pullRoomConfigFamily` adoption path: incoming cloud doc with
     templateKey matching a local row → no new local row, cloud_id
     bound to existing.
   - Backfill no-op when no duplicates.
   - Backfill consolidates 2-rule group (newer `updatedAt` wins).
   - Backfill consolidates 3+ rule group.
   - Backfill skips templateKey=null rules (user-authored survive).
   - Backfill idempotent (second run is no-op).

### LOC estimate + scope guard

~35 LOC source + ~80 LOC tests. Well under the 200-LOC PR ceiling.

---

## 3 — Cross-Bundle Architecture Risks

With Item 1 STOPped, no cross-bundle interaction surface exists. Item 2
is a self-contained sync-path fix + one-time backfill. The risks
catalogued in operator scope Part C all stem from migration ⇄ sweeper
interaction; none apply.

The single residual risk is the at-insert `naturalKeyLookup` adopting a
cloud_id onto a *user-edited* duplicate row whose `templateKey` matches
an incoming cloud doc that was created from a different rule edit
chain. LWW on `updatedAt` resolves this correctly: whichever side is
newer wins the data merge; the cloud_id binding is to the surviving
local id. This is the same correctness shape as the 6 sibling entities
already using natural-key adoption.

---

## 4 — Phase 2 Ordering

One PR. Order is moot.

---

## 5 — Verdict + Anti-Pattern Notes

| Item | Verdict | One-line rationale |
|---|---|---|
| AI-action template auto-enable migration | **STOP — close** | "Pending backend" marker doesn't exist; no signal distinguishes user-disable from system-disable; users have a working rule-list toggle. |
| Same-template dedup (operator's "Option b sweeper") | **PROCEED with PIVOT** | Pivot to canonical `naturalKeyLookup` pattern (already used by 6 sibling entities); ship one-time backfill alongside. ~35 LOC source. |

### Anti-patterns surfaced (worth flagging, not necessarily fixing here)

1. **`importTemplate` lacks an idempotency guard.** Tapping "import" on
   the same template twice on the same device produces two rows. The
   `naturalKeyLookup` fix in this PR makes cross-device sync converge
   on a single row, but on a single device the user can still create
   duplicates by double-tapping. Fix: `importTemplate` could call
   `getByTemplateKeyOnce(templateId)` first and either no-op, or
   surface a Snackbar ("already imported"). Out of scope for this PR;
   file as a separate small UX polish.
2. **Full-doc LWW for `automation_rules` `enabled` field.** This means
   any cross-device toggle race is resolved by `updatedAt`. For
   `enabled` specifically, that's usually fine, but if both users
   toggle in opposite directions within a few seconds, the loser sees
   their toggle silently reverted. Same shape as the medication-rule
   LWW deferred work. Not actionable here.
3. **`AutomationStarterLibrary` `requiresAi` is computed but
   `AutomationTemplate.requiresAi` is not surfaced in `importTemplate`.**
   If we ever want to nudge "this rule needs AI features" at import
   time, the data is already there. No action needed now; just noting
   that the affordance exists.

### Ranked improvement table (wall-clock-savings ÷ implementation-cost)

| Rank | Improvement | Wall-clock saved | Impl cost | Ratio |
|---|---|---|---|---|
| 1 | At-insert dedup via `naturalKeyLookup` | High (eliminates duplicate-rule support burden as Phase I rolls out) | ~5 LOC + tests | **Very high** |
| 2 | One-time backfill sweep for the #1070→fix window | Moderate (covers small cohort cleanly) | ~30 LOC + tests | High |
| 3 | (Anti-pattern 1) `importTemplate` idempotency guard | Low — masked once dedup ships | ~10 LOC | Medium — file separately |
| 4 | (STOP) Auto-enable migration | Negative (would clobber explicit user intent) | Any | **Negative — do not ship** |

---

## STOP-condition compliance check

Per operator scope:

- ✅ "If marker doesn't exist AND can't be reliably approximated…STOP"
  → STOP fired for Item 1.
- ✅ "If sweep edge case (B.3) reveals more than 4 user-customization
  scenarios, STOP" → moot, sweep pivoted away.
- ✅ "If audit recon reveals either feature has already been shipped
  via drive-by or parked branch, STOP and remove from scope" → no
  drive-by ships found; recon clean.
- ✅ "If audit cap exceeds 500 lines, STOP" → this doc ~270 lines.
- ✅ "If migration would clobber explicit-disable across devices via
  full-doc LWW, STOP" → STOP fired.
- ✅ Defer-minimization (memory #30): both items reach a closing
  decision. Item 1 closes via STOP-with-rationale; Item 2 ships via
  PROCEED. No DEFERRED items.

---

*Phase 2 implementation auto-fires after this commit; no checkpoint.*
