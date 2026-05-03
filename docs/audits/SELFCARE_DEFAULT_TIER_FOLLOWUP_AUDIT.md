# Self-Care Default Tier Settings — Follow-up

## Scope

User report: **"Self-care default tier settings don't work."**

The setting being referenced is the "Self-Care Default Tier" group in
**Settings → Advanced Tuning** (per-routine: Morning / Bedtime /
Medication / Housework). It was introduced by
`docs/audits/SELFCARE_DEFAULT_TIER_AUDIT.md` and shipped in PR #999.

The original audit's Item 4 verdict claimed there was a single consumer
site (`SelfCareViewModel.getSelectedTier`). This follow-up sweeps every
consumer of the "starting tier when no log exists today" decision and
confirms two additional consumers were missed, plus a writer that
silently overrides the user's preference. The Open Questions section of
the original audit flagged the writer issue as latent — it is now
user-visible.

## Phase 1 — Audit

### Item 1 — `SelfCareViewModel.getSelectedTier` honors the preference (GREEN)

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/ui/screens/selfcare/SelfCareViewModel.kt:152-165`
  reads `tierDefaults` from `AdvancedTuningPreferences.getSelfCareTierDefaults()`,
  validates against `SelfCareRoutines.getTierOrder(routineType)`, and
  falls back to penultimate-of-order. Identical shape to what
  `SELFCARE_DEFAULT_TIER_AUDIT.md` shipped.
- Round-trip persistence verified by
  `app/src/test/java/com/averycorp/prismtask/data/preferences/AdvancedTuningSelfCareTierDefaultsTest.kt`.

**Recommendation.** STOP-no-work-needed — this site already works.

### Item 2 — `DailyEssentialsUseCase.resolveSelectedTier` ignores user preference (RED)

**Premise verification.** The Today screen's "Daily Essentials" section
shows a Morning Routine card / Bedtime Routine card / Housework card.
Each filters its visible steps by the routine's "selected tier". When
no log exists for today the card has no `selectedTier` to read.

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/domain/usecase/DailyEssentialsUseCase.kt:323-327`:
  ```kotlin
  internal fun resolveSelectedTier(logTier: String?, tierOrder: List<String>): String? {
      if (!logTier.isNullOrBlank() && logTier in tierOrder) return logTier
      if (tierOrder.isEmpty()) return null
      return tierOrder.getOrNull(tierOrder.size - 2) ?: tierOrder.last()
  }
  ```
  Hardcoded penultimate-of-order — never consults
  `AdvancedTuningPreferences.getSelfCareTierDefaults()`.
- The companion-doc comment at line 314-321 even claims "matching
  `SelfCareViewModel`'s default" — but `SelfCareViewModel` was rewired
  in PR #999 to read the user pref; this site was missed during that
  PR's sweep.
- Caller at line 217 in `observeRoutineCard()` uses the fallback when
  `log?.selectedTier` is null, which is the user's "morning of a fresh
  day" path — exactly when the user expects their default tier to take
  effect on the Today screen.
- Existing tests in
  `app/src/test/java/com/averycorp/prismtask/domain/usecase/DailyEssentialsUseCaseTest.kt:106-115`
  pin the broken behavior:
  > `resolveSelectedTier falls back to the second-to-last tier for null or blank`

**Risk classification.** RED — the user's most-visible path (Today
screen Daily Essentials cards) shows the wrong tier of steps.

**Recommendation.** PROCEED — accept a `defaultTier: String?` argument
in `resolveSelectedTier`, plumb the preference flow into the
`combine()` in `observeRoutineCard()`.

### Item 3 — `HabitListViewModel.computeCardData` ignores user preference (RED)

**Premise verification.** The Habits screen renders Self-Care /
Bedtime / Housework / Medication cards. Each card's progress counter
is computed by `computeCardData()` against a "tier" filter that comes
from `log?.selectedTier ?: <fallback>`.

**Findings.**

- `app/src/main/java/com/averycorp/prismtask/ui/screens/habits/HabitListViewModel.kt:273-279`:
  ```kotlin
  val tier = log?.selectedTier ?: if (routineType == "medication") {
      "prescription"
  } else {
      SelfCareRoutines.getTierOrder(routineType).let {
          if (it.size >= 2) it[it.size - 2] else it.first()
      }
  }
  ```
  Same hardcoded penultimate-of-order; same blind-spot to the user
  preference. Plus the `"medication" → "prescription"` literal is now
  duplicated state — the canonical default lives in
  `SelfCareTierDefaults.medication`.
- The card's `completedCount` and `isComplete` flags both feed off
  `visibleSteps`, which is filtered by this `tier`. So if the user
  picks "Survival" as their default morning tier, the Habits screen
  will keep showing them "of N steps" against the "Solid" tier set
  until they explicitly tap a tier chip on the Self-Care screen for
  the day.

**Risk classification.** RED — wrong card progress on the Habits
screen, same root cause as Item 2.

**Recommendation.** PROCEED — inject `AdvancedTuningPreferences`,
combine the `getSelfCareTierDefaults()` flow into the existing 17-arg
`combine`, look up the per-routine default in `computeCardData`.

### Item 4 — Writers leak `"solid"` into fresh logs (RED)

**Premise verification.** Even with the read-side fixes (Items 2/3),
the user's preference can still get silently overridden by a write
path that creates a `SelfCareLogEntity` without an explicit
`selectedTier`. The entity column default is `"solid"` —
`app/src/main/java/com/averycorp/prismtask/data/local/entity/SelfCareLogEntity.kt:24`.

**Findings.**

- Two writers create logs without passing `selectedTier`:
  1. `SelfCareRepository.toggleStep()`
     (`app/src/main/java/com/averycorp/prismtask/data/repository/SelfCareRepository.kt:562-580`):
     ```kotlin
     if (existing == null) {
         selfCareDao.insertLog(
             SelfCareLogEntity(
                 routineType = routineType,
                 date = today,
                 startedAt = System.currentTimeMillis()
             )
         )
         existing = selfCareDao.getLogForDateOnce(routineType, today) ?: return
     }
     ```
     No `selectedTier` argument → column default `"solid"` is used.
  2. `SelfCareRepository.setTierForTime()`
     (`SelfCareRepository.kt:791-805`): same shape — inserts a fresh
     log with no `selectedTier`.
- This is exactly the bug the original audit's Open Questions section
  flagged (`SELFCARE_DEFAULT_TIER_AUDIT.md:262-266`):
  > Should `SelfCareRepository.toggleStep`'s "no log yet" insertLog
  > also read the new pref to seed `selectedTier`? Today it falls back
  > to the entity literal `"solid"`. Out of scope here; the
  > `getSelectedTier` coercion makes this latent rather than user-visible.
- It is no longer latent. Once a step is toggled (or, for medication, a
  per-block tier is picked), the log row carries `selectedTier = "solid"`,
  and every other consumer that **does** check `log.selectedTier` first
  (Items 2 and 3, plus `HabitListViewModel`'s own card data) sees the
  override and ignores the user pref forever.
- For medication, this is worse: `"solid"` is not in
  `medicationTierOrder = listOf("essential", "prescription", "complete")`,
  so the value is structurally invalid. `HabitListViewModel` only
  reads `log?.selectedTier` for non-medication routines, so the
  medication tile is shielded today, but `SelfCareViewModel.getSelectedTier`
  does coerce it via `takeIf { it in order }` — so the bug is masked
  on the dedicated Self-Care screen but **not** on Today / Habits
  cards (Items 2 and 3 above), where the user-pref isn't even consulted.

**Risk classification.** RED — every write path that creates a fresh
log silently invalidates the user's preference for the rest of the
day's reads.

**Recommendation.** PROCEED — both insert sites should seed
`selectedTier` from `AdvancedTuningPreferences.getSelfCareTierDefaults()`
keyed on `routineType`, falling back to penultimate-of-order if the
preference value is somehow not in the order. This brings writer
behavior in line with `SelfCareViewModel.getSelectedTier`.

### Item 5 — Original audit's "single consumer" claim was incomplete (YELLOW)

**Premise verification.** `SELFCARE_DEFAULT_TIER_AUDIT.md` Item 4
claimed:

> Only `SelfCareViewModel.getSelectedTier` (line 145) needs the new
> default fed in.

**Findings.**

- The sweep that audit performed missed `DailyEssentialsUseCase.resolveSelectedTier`
  (Item 2) and `HabitListViewModel.computeCardData` (Item 3). Both
  contain near-identical hardcoded penultimate-of-order fallbacks that
  predate `SelfCareViewModel.getSelectedTier` and are not reachable
  from a `grep "getSelectedTier"`.
- A grep for `tierOrder.size - 2` would have surfaced both. So would a
  grep for the literal `"prescription"` (HabitListViewModel) or any
  systematic sweep of "tier" + "fallback" sites.

**Risk classification.** YELLOW — process learning, not load-bearing
code. The fix shipped under PR #999 was correct as far as it went; it
just stopped one site short.

**Recommendation.** Memory candidate after Phase 2 lands: when
shipping a new "user can override hardcoded fallback X" preference,
grep for the *fallback shape* (e.g. `tierOrder.size - 2`,
`tierOrder.lastIndex`, `getOrNull(... - 2)`, hardcoded tier ids)
across the entire `main/` tree, not just the symbol that the new
preference reader is added to.

---

## Ranked improvements

Sorted by wall-clock-savings ÷ implementation-cost:

| # | Improvement | Verdict | Notes |
|---|---|---|---|
| 1 | Wire `SelfCareTierDefaults` into `DailyEssentialsUseCase.resolveSelectedTier`. | PROCEED | Highest user impact (Today screen) / smallest diff. |
| 2 | Wire `SelfCareTierDefaults` into `HabitListViewModel.computeCardData`. | PROCEED | Same shape as #1; touches Habits screen. |
| 3 | Seed `selectedTier` from the preference in `SelfCareRepository.toggleStep` + `setTierForTime`. | PROCEED | Plugs the silent-override leak; without this, #1 and #2 work only until the user touches a step. |
| — | Promote the "user-pref else penultimate-of-order" rule to a single helper on `SelfCareRoutines`. | DEFER | Not load-bearing; would churn three callsites for marginal DRYness. Skip. |

These three improvements are the same coherent scope (one user-visible
bug, one feature, one preference) — bundle into a single PR per the
fan-out bundling rule.

## Anti-patterns flagged (not fixing here)

- **`SelfCareLogEntity.selectedTier = "solid"` schema-level default
  (`SelfCareLogEntity.kt:24`).** Same flag as the original audit. The
  Phase 2 fixes here close the *insert paths* that previously relied
  on it; the literal default itself is then truly dead and could be
  changed to `""` or made non-default in a future cleanup. Not load-bearing
  after Phase 2, so not fixing in this scope.
- **`HabitListViewModel.computeCardData` hardcodes `"prescription"`
  for medication.** The literal duplicates the canonical default in
  `SelfCareTierDefaults.medication`. Phase 2 #2 removes this.
- **Doc-comment drift on `DailyEssentialsUseCase.resolveSelectedTier`
  companion** ("matching SelfCareViewModel's default") — was true
  pre-#999, false now. Phase 2 #1 will rewrite the comment.

---

## Phase 3 — Bundle summary

**Shipped (single PR per the fan-out bundling rule).**

- **PR [#1067](https://github.com/averycorp/prismTask/pull/1067)** —
  `fix(selfcare): default-tier preference now applies on Today,
  Habits, and after step toggles`. Branch
  `claude/fix-self-care-defaults-0TxnC`.
  - `AdvancedTuningPreferences.SelfCareTierDefaults` gains
    `forRoutine(routineType)` so the four call sites look up the
    same way regardless of consumer.
  - `DailyEssentialsUseCase`: injects `AdvancedTuningPreferences`,
    folds `getSelfCareTierDefaults()` into `observeRoutineCard()`'s
    `combine()`, and passes the per-routine default to
    `resolveSelectedTier(...)`. `resolveSelectedTier` gains an
    optional `defaultTier: String?` arg with the precedence:
    stored log → user pref → penultimate-of-order. Doc comment
    rewritten.
  - `HabitListViewModel`: injects `AdvancedTuningPreferences`, adds
    a `tierDefaults` `StateFlow`, threads it through the (now
    18-arg) `combine()` and into `computeCardData()`. Removes the
    duplicated `"prescription"` literal — uses the canonical
    default from `SelfCareTierDefaults.medication` instead.
  - `SelfCareRepository`: injects `AdvancedTuningPreferences`. New
    `resolveStartingTier(routineType)` helper mirrors the
    read-side precedence; `toggleStep` and `setTierForTime` now
    seed `selectedTier` from it instead of relying on the
    schema-level `"solid"` entity default.
  - Tests: three new cases on `DailyEssentialsUseCaseTest` (user
    pref precedence / stale-default coercion / log-precedence-over-
    default), three new cases on `SelfCareRepositoryConflictTest`
    (toggleStep + setTierForTime seed from pref, plus stale-
    default coercion). Existing `HabitListViewModelTest` and
    `SelfCareRepositorySeedingTest` updated for the new
    constructor argument.

**Verification.**

- Local Gradle is unavailable on this Linux sandbox (Android SDK
  not installed; CLAUDE.md notes the toolchain lives on the user's
  Windows env). CI on PR #1067 is the verification gate — `test`,
  `lint-and-test`, `web-lint-and-test`, and `e2e` were green-or-
  pending at PR-open time; `connected-tests` / `cross-device-tests`
  / `capture-failure-log` skipped per their usual gating.
- Manual smoke (Settings → Advanced Tuning → Self-Care Default
  Tier → cycle Morning to Survival → confirm Today screen Daily
  Essentials morning card and Habits screen morning row both pick
  up the survival-tier step set on a fresh day) is queued for the
  user — not run from this audit session.

**Re-baseline.**

- Original `SELFCARE_DEFAULT_TIER_AUDIT.md` shipped under PR #999
  with Item 4 verdict "single consumer site". This follow-up
  surfaces three additional sites — re-baselined estimate for
  "user-overridable hardcoded fallback X" features should assume
  N consumer sites where N ≥ 4 unless the sweep specifically
  greps for fallback-shape tokens.

**Memory candidate.**

- One memory entry candidate (logged inline in Item 5 of Phase 1):
  when shipping a "user can override hardcoded fallback X"
  preference, grep for the fallback *shape*
  (`tierOrder.size - 2`, `getOrNull(... - 2)`, hardcoded enum
  literals matching the canonical default) across the entire
  `main/` tree, not just the symbol the new pref reader is added
  to. Worth promoting if the same shape recurs on the next
  similar PR.

**Schedule for next audit.** No follow-up audit needed for this
scope. The remaining anti-pattern note —
`SelfCareLogEntity.selectedTier = "solid"` schema default — is
fully dead after this PR's writer fix and can be cleaned up
opportunistically; not load-bearing.

## Phase 4 — Claude Chat handoff

```markdown
**Scope.** Audit-first follow-up sweep on PrismTask: user reported
"Self-care default tier settings don't work." Audit doc lives at
`docs/audits/SELFCARE_DEFAULT_TIER_FOLLOWUP_AUDIT.md`. Original
feature shipped under PR #999 wired only one of the four affected
sites; this run swept the rest.

**Verdicts.**

| # | Item | Verdict | Finding |
|---|------|---------|---------|
| 1 | `SelfCareViewModel.getSelectedTier` honours pref | GREEN | Already wired in PR #999; no work. |
| 2 | `DailyEssentialsUseCase.resolveSelectedTier` ignores pref | RED | Hardcoded penultimate-of-order — Today screen Daily Essentials cards show wrong tier. |
| 3 | `HabitListViewModel.computeCardData` ignores pref | RED | Same hardcode, plus duplicate `"prescription"` literal — Habits screen rows show wrong card data. |
| 4 | Writers (`toggleStep`, `setTierForTime`) leak `"solid"` into fresh logs | RED | Schema-level `"solid"` default leaks in via insertLog without explicit `selectedTier`; silently overrides the user's preference for the rest of the day. Structurally invalid for medication. |
| 5 | Original audit's "single consumer" claim was incomplete | YELLOW | Process learning, not a load-bearing bug. |

**Shipped.**

- PR #1067 — `fix(selfcare): default-tier preference now applies
  on Today, Habits, and after step toggles`. Single coherent scope
  per the fan-out bundling rule; bundles the three RED fixes with
  a `forRoutine` helper on `SelfCareTierDefaults` so the four
  routines look up the same way everywhere.

**Deferred / stopped.**

- Item 1 (STOP-no-work-needed) — already fixed in PR #999.
- "Promote user-pref-else-penultimate rule to a single helper on
  `SelfCareRoutines`" (DEFER) — cross-call-site refactor, not
  load-bearing, skipped.
- `SelfCareLogEntity.selectedTier = "solid"` schema default —
  fully dead after #1067 lands; can be deleted opportunistically.

**Non-obvious findings.**

- The original audit explicitly flagged the writer bug in its
  Open Questions section ("Should `toggleStep`'s no-log-yet
  insertLog also read the new pref?") and dismissed it as latent.
  It was not latent — once writers leak `"solid"` into the log,
  every other consumer that does check `log.selectedTier` first
  (Items 2 and 3) sees the override and ignores the user pref.
- For medication, `"solid"` is **not** in `medicationTierOrder`,
  so a `setTierForTime` write before this PR would persist a
  structurally invalid tier id. `SelfCareViewModel.getSelectedTier`
  coerced it via `takeIf { it in order }`, but Items 2 and 3 did
  not — so the bug was masked on the dedicated screen but visible
  on Today / Habits.
- Process learning: a grep for `getSelectedTier` from PR #999's
  branch wouldn't surface Items 2 or 3. A grep for the *fallback
  shape* (`tierOrder.size - 2`, hardcoded `"prescription"`) would
  have. Memory candidate filed.

**Open questions.** None — the four-routine interpretation from the
original audit still holds, no architectural ambiguity surfaced.
```
