# Built-In Habit Streaks Audit

**Scope.** "Add streaks to the built-in habits" — ensure the 6 built-in
habits (school, leisure, morning self-care, bedtime self-care, medication,
housework) display their streak count in the same way user-created habits
do, both for new installs and for existing users.

**Non-goals.** Forgiveness-streak surfacing on the badge itself, widget UX,
analytics-screen changes, AddEditHabit reachability for built-ins.

---

## Findings

### Streak data is already computed correctly for built-in habits (GREEN)

`HabitRepository.getHabitsWithFullStatus()` (`HabitRepository.kt:335`)
calls `StreakCalculator.calculateCurrentStreak(...)` for *every* habit
returned by `habitDao.getActiveHabits()` — built-in or not. There is no
`isBuiltIn` filter on the streak calculation path. `HabitListViewModel`
(`HabitListViewModel.kt:77`) consumes this flow and pipes
`habitWithStatus.currentStreak` into the `BuiltInHabitItem` shown on the
habit list.

**Recommendation:** STOP-no-work-needed on the streak engine. The streak
*number* is already correct; the gap is purely whether the badge is
allowed to render.

---

### `BuiltInHabitCard` already has a streak-rendering branch — gated on `showStreak` (RED)

`BuiltInHabitCard.kt:106` already renders `StreakBadge(streak = ...)` when
`habitWithStatus.habit.showStreak && habitWithStatus.currentStreak > 0`.
The branch never lights up because:

- `HabitEntity.showStreak` defaults to `false`
  (`HabitEntity.kt:54-55`, column default `0`).
- The three repositories that seed built-in habits all rely on the entity
  default and never override:
  - `SchoolworkRepository.kt:181-182` — `isBuiltIn = true, templateKey = "builtin_school"`, no `showStreak`.
  - `LeisureRepository.kt:244-245` — same shape, no `showStreak`.
  - `SelfCareRepository.kt:991-992` — covers morning / bedtime /
    medication / housework; same shape, no `showStreak`.

So every built-in habit ships with `showStreak = false`, and the `school`
and `leisure` cards never display the badge — even though the streak
number sitting in `HabitWithStatus.currentStreak` is correct.

**Recommendation:** PROCEED — flip `showStreak = true` on the seeded
`HabitEntity` for all six built-in habits, and add a one-time backfill
migration that sets `show_streak = 1` for existing rows where
`is_built_in = 1 AND show_streak = 0 AND is_user_modified = 0`. The
`is_user_modified` guard preserves the user's choice if they explicitly
turned the streak off after install.

---

### `SelfCareRoutineCard` has no streak slot (YELLOW)

The morning / bedtime / medication / housework rows on the habit list
render via `SelfCareRoutineCard` (`SelfCareRoutineCard.kt:40-150`), not
`BuiltInHabitCard`. `SelfCareRoutineCard` accepts only `SelfCareCardData`
(tier label + completion ratio) — it has no `habitWithStatus` reference
and no `StreakBadge` call. Even after the migration in the previous item,
those four cards will silently keep streak-less.

`HabitListViewModel.computeCardData()` (`HabitListViewModel.kt:261`) builds
`SelfCareCardData` from the routine log + steps, with no plumbing back to
the underlying `HabitEntity` / `HabitWithStatus`. The list does have the
underlying habit available though — the ViewModel already filters out
built-in habit names from the regular list (`HabitListViewModel.kt:206-214`).

**Recommendation:** PROCEED — add a `currentStreak: Int` field to
`SelfCareCardData` (sourced from the matching habit's
`HabitWithStatus.currentStreak`), and render `StreakBadge` in
`SelfCareRoutineCard` under the same gate
(`habit.showStreak && currentStreak > 0`).

---

### Today-screen `dailyessentials/cards/HabitCard.kt` has no streak rendering (DEFERRED)

`today/dailyessentials/cards/HabitCard.kt:23-72` takes a `HabitCardState`
(name / icon / color / completedToday) and has no `StreakBadge`. Adding it
requires extending `HabitCardState` with a streak field and a
`showStreak` flag, and threading both through `DailyEssentialsUseCase`.

**Recommendation:** DEFER — this is a separate, more invasive UX change to
the Daily Essentials surface (housework + schoolwork on Today), and the
user's request is naturally satisfied by the habit list. Track for a
follow-up audit if Daily Essentials gets a streak ask.

---

### `getHabitsWithTodayStatus()` hardcodes `currentStreak = 0` (DEFERRED — anti-pattern)

`HabitRepository.kt:325` always returns `currentStreak = 0` from the
"today-only" status flow. `TodayViewModel.allTodayHabits`
(`TodayViewModel.kt:561`) consumes this. If the today-screen surfaces ever
want streak, the data path lies. Today's chips don't show streak today,
so this is benign for the current scope but worth flagging.

**Recommendation:** DEFER — note as anti-pattern. Either rename the field
to something like `dailyTargetMet` to reflect the actual contract, or
compute the streak. Not core to "add streaks to built-in habits".

---

### AddEditHabit reachability for built-ins (DEFERRED)

`HabitListScreen.kt:272-287` renders `BuiltInHabitItem` without an
`onEdit` button (the built-in card has no overflow), so users can't
toggle `showStreak` after install via the UI for school / leisure.
`SelfCareRoutineCard` similarly has no edit path. After the migration in
item 1 the streak shows by default, so this is moot for the immediate
ask, but the absence of a per-built-in toggle is a real UX gap separate
from the streak question.

**Recommendation:** DEFER — track in the broader "built-in habit edit
parity" backlog (out of scope here).

---

## Ranked improvements (savings ÷ cost)

| # | Item | Cost | Savings / Impact | Verdict |
|---|------|------|------------------|---------|
| 1 | Seed `showStreak = true` for built-ins + migration backfill | Small (1 migration, 3 seeders, ~5 tests) | High — lights up the existing `BuiltInHabitCard` streak branch for both new + existing users | **PROCEED — PR 1** |
| 2 | Streak in `SelfCareRoutineCard` | Medium (data class field + 4 cards' worth of plumbing) | Medium — covers the 4 remaining built-in habits' rows | **PROCEED — PR 2** |
| 3 | Streak in Today `dailyessentials/cards/HabitCard` | Medium-High (HabitCardState + use case) | Lower — a separate Today surface | DEFER |
| 4 | Fix `getHabitsWithTodayStatus()` hardcoded streak=0 | Low | Anti-pattern flag | DEFER |
| 5 | AddEditHabit reachability for built-ins | Medium | Adjacent UX | DEFER |

## Anti-patterns flagged (no fix this round)

- `getHabitsWithTodayStatus()` returning `currentStreak = 0` is a silent
  contract — the field is "honest" only on the `getHabitsWithFullStatus`
  path. Document or rename if/when item 4 is picked up.
- `SelfCareCardData` losing the underlying `HabitEntity` reference forces
  workarounds whenever a habit-level field needs to surface in the
  routine card. Item 2 plumbs the streak through; consider passing the
  whole `HabitWithStatus` next time the card grows another habit-driven
  field.

---

## Phase 3 — Bundle summary

Both PROCEED items shipped in 2 PRs, plus the audit doc itself. All
merged 2026-04-29 within ~31 minutes of the audit landing on main.

| # | Item | PR | Merge SHA | Notes |
|---|------|----|-----------|-------|
| — | Audit doc (Phase 1) | [#930](https://github.com/Akarlin3/PrismTask/pull/930) | `0c9e1e88` | Phase 1 deliverable. |
| 1 | Seed `showStreak = true` for built-ins + `MIGRATION_65_66` backfill + `Migration65To66Test` | [#931](https://github.com/Akarlin3/PrismTask/pull/931) | `f2446a6c` | Touches `SchoolworkRepository`, `LeisureRepository`, `SelfCareRepository`, `Migrations.kt` (`CURRENT_DB_VERSION = 66`). Backfill is gated on `is_user_modified = 0` so user opt-outs are preserved. PR needed a rebase after #932 landed (auto-merge stalled on `BEHIND` mergeState — known shape). |
| 2 | `SelfCareCardData.{currentStreak, showStreak}` + `StreakBadge` in `SelfCareRoutineCard` | [#932](https://github.com/Akarlin3/PrismTask/pull/932) | `8b5bb38b` | Wires `HabitWithStatus.currentStreak` from the matching habit (looked up by `name`) through `computeCardData()`. New fields default to `0`/`false` so existing call sites are unchanged. |

**Measured impact (post-merge).** All 6 built-in habits now render the
🔥 N badge under the same gate as user habits (`habit.showStreak &&
currentStreak > 0`). New installs see it via the seed flip; existing
installs see it after the next launch (one-shot UPDATE during the v65→v66
upgrade). Users who explicitly disabled the streak are preserved by the
`is_user_modified = 0` guard on the backfill UPDATE.

**Re-baselined wall-clock estimate.** The audit doc came in at 144 lines
(well under the 500-line cap). Phase 1 took ~10 min wall-clock; Phase 2
PR 1 ~12 min (incl. test); Phase 2 PR 2 ~8 min. Auto-merge stall on
PR #931 cost ~15 min waiting for CI to re-fire on the rebased HEAD.

**Memory entry candidates.** None. The auto-merge stall is already
captured in `feedback_auto_merge_branch_update_deadlock.md`; the rest is
straightforward seeder + migration work that the existing codebase
patterns cover.

**Schedule for next audit.** No follow-up. The 3 DEFERRED items
(Today `dailyessentials/cards/HabitCard` streak, `getHabitsWithTodayStatus`
hardcoded streak, AddEditHabit reachability for built-ins) are tracked
in this doc and can be picked up individually if/when a user request
naturally surfaces them.
