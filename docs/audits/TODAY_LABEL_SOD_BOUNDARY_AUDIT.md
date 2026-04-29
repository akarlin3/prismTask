# Today/Tomorrow label SoD-boundary audit

**Trigger:** User report — "Tasks should not show as being due the same day
until the set start of the day in settings."

**Scope:** Every place a task's due-date renders or is bucketed as
"Today" / "Tomorrow" / "Overdue" — *display-side only*. The SoD-aware Today
**filter** (`TodayViewModel.dayStart`) and TaskList **grouping**
(`TaskListViewModel.dayStartFlow`) were already migrated by PR #798 / #811
/ #812 (see `UTIL_DAYBOUNDARY_SWEEP_AUDIT.md`); this sweep targets the
remaining surfaces that still compute "start of today" from raw wall-clock
midnight.

**Bug shape:** A synchronous `Calendar.getInstance()` (or
`LocalDate.now().atStartOfDay()`) computes "start of today" at render time
and ignores the user's configured Start-of-Day. Between calendar midnight
and SoD, this puts the user one logical day ahead of where they think
they are, so labels mislabel tasks.

**Concrete repro:** SoD = 04:00, wall-clock = 02:00 on Apr 29. Logical day
is still Apr 28.

| Task due date | Logical bucket (correct) | Current display |
|---------------|--------------------------|-----------------|
| Apr 28 23:00  | Today                    | "Mon, Apr 28" + overdue tint ❌ |
| Apr 29 09:00  | Tomorrow                 | "Today" (warning color) ❌ |
| Apr 29 00:00  | Tomorrow (Today filter excludes it correctly) | "Today" on the card ❌ |

The Today screen *filter* already gets this right (April 29 09:00 doesn't
appear), but if the same task is viewed on the Tasks list it lives in the
"Tomorrow" group while the card text reads "Today" — internally
inconsistent within a single screen.

---

## Triage results — summary

| # | Caller | Verdict | Severity | Migration |
|---|--------|---------|----------|-----------|
| 1 | `TaskCardDueDate.formatDueDate` | RED | HIGH | PROCEED |
| 2 | `TaskCardDueDate.isTaskOverdue` | RED | HIGH | PROCEED |
| 3 | `DateShortcuts.today/tomorrow` | RED (shared dep of #4) | HIGH | PROCEED |
| 4 | `QuickRescheduleFormatter.describe` | RED (transitive on #3) | MEDIUM | PROCEED (auto-fixed by #3) |
| 5 | `QuickReschedulePopup` "Today"/"Tomorrow" chips | YELLOW | MEDIUM | DEFER — design call |
| 6 | `NaturalLanguageParser` regex `today` keyword | YELLOW | MEDIUM | DEFER — separate scope |
| 7 | `WeekViewScreen` / `MonthViewScreen` "today" highlight | GREEN | n/a | STOP |
| 8 | `Today` screen filter (`TodayViewModel.dayStart`) | GREEN | n/a | STOP — already fixed |

**4 PROCEED (bundled into 1 PR — they're a single coherent fix), 2 DEFER, 2 STOP.**

---

## 1. `TaskCardDueDate.formatDueDate` (RED)

**File:** `app/src/main/java/com/averycorp/prismtask/ui/screens/tasklist/components/TaskCardDueDate.kt:28-55`

```kotlin
@Composable
internal fun formatDueDate(epochMillis: Long): DueDateLabel {
    val cal = Calendar.getInstance()           // ← wall-clock now
    cal.set(Calendar.HOUR_OF_DAY, 0)           // ← calendar midnight
    ...
    val startOfToday = cal.timeInMillis
    cal.add(Calendar.DAY_OF_YEAR, 1)
    val startOfTomorrow = cal.timeInMillis
    ...
    return when {
        epochMillis < startOfToday -> formatted-date
        epochMillis < startOfTomorrow -> "Today"
        epochMillis < startOfDayAfter -> "Tomorrow"
        ...
```

`startOfToday` is calendar 00:00 of the wall-clock date, not the SoD
boundary of the user's logical date. Render-time computation, no SoD
input.

**Findings:** Sole caller is `TaskCard.TaskItem` at line 165 in the same
package. Card lives in TaskList, Today, Week-view, Month-view, Timeline,
Eisenhower, Search, Filter results — the bug shows up everywhere a card
is drawn with a due date.

**Severity reasoning:** This is the most user-visible label on a task.
Wrong "Today"/"Tomorrow" between midnight–SoD undermines the whole point
of the SoD setting.

**Recommendation: PROCEED.** Pass SoD-anchored `startOfToday` /
`startOfTomorrow` into the formatter from a parent that observes
`LocalDateFlow`. Mirror the shape used in `TaskListViewModel.dayStartFlow`.

## 2. `TaskCardDueDate.isTaskOverdue` (RED)

**File:** `TaskCardDueDate.kt:61-72`

Same shape as #1 (`Calendar.getInstance()` → calendar midnight). Returns
true for tasks due before raw calendar midnight, so a task due "Apr 28
23:00" is marked overdue from Apr 29 00:00 — but the user with SoD=04:00
is logically still on Apr 28 and would not call that task overdue until
04:00.

**Caller scan:** `Grep formatDueDate|isTaskOverdue` returned only the
declarations; `isTaskOverdue` may be currently unused at the callsite
level (declared `internal`, no callers in `app/src/main`). Verify before
gutting — if it's dead, delete; if a follow-up PR is about to wire it
into TaskCard, fix the same way as #1.

**Recommendation: PROCEED.** Either delete-as-dead or fix in the same PR
as #1.

## 3. `DateShortcuts.today` / `tomorrow` (RED, shared dep)

**File:** `app/src/main/java/com/averycorp/prismtask/domain/usecase/DateShortcuts.kt:22-29`

```kotlin
fun today(now: Long = System.currentTimeMillis()): Long = startOfDay(now)
fun tomorrow(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
    cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
}
```

`startOfDay(now)` snaps to calendar 00:00. No SoD parameter, no logical-day
awareness.

**Callers** (`Grep DateShortcuts.`):

| Caller | Bug? | Notes |
|--------|------|-------|
| `QuickRescheduleFormatter.describe` (#4) | YES | Passed-through `today`/`tomorrow` constants used to label snackbars. |
| `QuickReschedulePopup` (#5) | DESIGN | Sets due dates on tasks; deferred separately. |
| `TaskTemplateRepository:111` | NO — point-of-use snapshot | Sets `effectiveDueDate` at template-instantiation time. Snapshot semantics are correct here (the `[skip ci]`-style "snapshot is the contract" rule from `UTIL_DAYBOUNDARY_SWEEP_AUDIT.md`). User tapping "instantiate template" at 2 AM with SoD=4 implicitly accepts that "now" means "now". |

**Recommendation: PROCEED.** Add SoD-aware overloads
(`today(now, sodHour, sodMinute, zone)` and `tomorrow(...)`) that snap to
the SoD boundary of the logical day, and route `QuickRescheduleFormatter`
through the new overload. Keep the no-arg helper for the
`TaskTemplateRepository` point-of-use snapshot caller (the legacy shape
is still correct there). Alternatively, route `today`/`tomorrow` through
`core.time.DayBoundary.logicalDate(...)` helpers.

## 4. `QuickRescheduleFormatter.describe` (RED, transitive)

**File:** `app/src/main/java/com/averycorp/prismtask/ui/components/QuickRescheduleFormatter.kt:19-29`

Compares the rescheduled `millis` against `DateShortcuts.today(now)` /
`tomorrow(now)`. Inherits the bug from #3.

**Surface:** Snackbar copy after every Quick-Reschedule action ("Rescheduled
to Today", "Rescheduled to Tomorrow", "Rescheduled to Apr 30"). Used by
`TodayViewModel`, `TaskListViewModel`, `TaskListViewModelBulk`,
`WeekViewModel`, `TimelineViewModel`, `MonthViewModel`.

**Recommendation: PROCEED — auto-fixed when #3 lands.** Add a SoD overload
to `describe(millis, now, sodHour, sodMinute)` and update the 6 callers to
pass SoD. Or: have callers compute the bucket label themselves using
`DayBoundary.logicalDate`.

## 5. `QuickReschedulePopup` chips (YELLOW — design call)

**File:** `app/src/main/java/com/averycorp/prismtask/ui/components/QuickReschedulePopup.kt:58-61`

```kotlin
val today = remember(now) { DateShortcuts.today(now) }       // calendar
val tomorrow = remember(now) { DateShortcuts.tomorrow(now) } // calendar
```

These set the *task's due-date timestamp* when the user taps "Today" or
"Tomorrow". At 2 AM Apr 29 with SoD=04:00, tapping "Today" sets dueDate =
Apr 29 00:00 — which the SoD-aware filter then puts in **tomorrow's**
window.

**Why this is a design call, not a clear bug:** The `Today`/`Tomorrow`
chips have always meant "calendar today/tomorrow" historically. Changing
them to "logical today/tomorrow" would also subtly change reminder
scheduling, recurrence anchors, and what end-of-day rollover does. The
user's complaint is about *display*, not *what the chips do when tapped*.

**Recommendation: DEFER.** Note in Phase 4 handoff. Worth a
brainstorming pass before silently changing semantics.

## 6. `NaturalLanguageParser` regex `today` keyword (YELLOW)

**File:** `NaturalLanguageParser.kt:190` + `:323-336`

```kotlin
val today = timeProvider.now().atZone(zone).toLocalDate()  // calendar
...
if (todayRegex.containsMatchIn(text)) parsedDate = today
```

The previous sweep (`UTIL_DAYBOUNDARY_SWEEP_AUDIT.md` § 7) marked NLP
"already on canonical" — but that was specifically about
`resolveAmbiguousTime` for "remind me at 2 AM" inputs. The plain
`today`/`tomorrow` regex still resolves to calendar today, not logical
today.

**Why DEFER:** Same shape as #5 — changing what "today" means at NLP
parse time changes write-side semantics, not just display. User's
complaint is display-focused. Worth its own audit pass.

**Recommendation: DEFER.** Flag for a follow-up audit
(`NLP_LOGICAL_TODAY_AUDIT.md`).

## 7. `WeekViewScreen` / `MonthViewScreen` "today" highlight (GREEN)

`WeekViewScreen.kt:87`, `MonthViewScreen.kt:96`, `MonthViewModel.kt:95`,
`WeekViewModel.kt:68/76/146` — all use `LocalDate.now()` to highlight
"today" on a calendar grid. **Correct as-is**: a calendar grid shows
calendar dates, and "today's box" should be the calendar-today box, not
the logical-today box (which would jump cells at SoD).

**Recommendation: STOP.** Note here so future audits don't re-flag.

## 8. Today screen filter (`TodayViewModel.dayStart`) — GREEN

Already on `LocalDateFlow` per PR #811. Calendar-midnight projection of
the logical date is the documented contract — see comments at
`TodayViewModel.kt:363-394`. **No work needed.**

---

## Ranked improvement table

Sorted by wall-clock-savings ÷ implementation-cost.

| Rank | Item | Savings | Cost | Ratio |
|------|------|---------|------|-------|
| 1 | #1 + #3 + #4 bundled (TaskCard label + DateShortcuts SoD overload + QuickRescheduleFormatter) | HIGH (every task card on every screen during midnight–SoD window) | ~80 LOC + tests | high |
| 2 | #2 (`isTaskOverdue` fix or delete-as-dead) | LOW (only matters if it gets re-wired) | ~10 LOC | medium |
| 3 | #5 (chip semantics) — DEFER | n/a (design call) | ~20 LOC | n/a |
| 4 | #6 (NLP `today`) — DEFER to follow-up audit | MEDIUM | ~30 LOC + tests | n/a |

**Phase 2 plan:** One PR bundles #1 + #2 + #3 + #4. They share the same
fix (plumb SoD-aware `startOfToday`/`startOfTomorrow` into the label
formatters), so splitting them adds churn without adding clarity. Per
the fan-out rule, "single coherent scope" justifies bundling.

## Anti-pattern list — flag, don't fix

- **`Calendar.getInstance()` + manual midnight** at render time —
  every occurrence in `ui/` should be reviewed for SoD relevance. Most
  are wrong. Migrate to `core.time.DayBoundary.logicalDayStart(...)` or
  pass SoD-anchored bounds in from a `LocalDateFlow`-observing parent.
- **`LocalDate.now()` in a Composable or render path** — fine for
  calendar-grid highlight (#7), wrong for "is this task today?" labels.
  Discriminator: does the answer depend on what the user thinks "today"
  means, or on the literal calendar grid?
- **`DateShortcuts.today/tomorrow` no-arg form** — keep the no-arg form
  for legitimate point-of-use snapshots (template instantiation), but
  add SoD-aware overloads for label/comparison use.

---

## Reproduction test (write this first)

Per memory `feedback_repro_first_for_time_boundary_bugs.md`, structural
repro before fix:

```kotlin
@Test
fun `formatDueDate marks Apr 29 09:00 as Tomorrow at 02:00 Apr 29 with SoD 04:00`() {
    // wall-clock = 2026-04-29 02:00
    // SoD = 04:00 → logical day = 2026-04-28
    // task due 2026-04-29 09:00 → expected label = "Tomorrow"
    // current bug: returns "Today"
}

@Test
fun `formatDueDate marks Apr 28 23:00 as Today at 02:00 Apr 29 with SoD 04:00`() {
    // wall-clock = 2026-04-29 02:00
    // task due 2026-04-28 23:00 → expected label = "Today"
    // current bug: returns "Mon, Apr 28" + overdue tint
}
```

Both should fail before the fix and pass after.

---

## Phase 3 — Bundle summary

PROCEED items #1, #2, #3, #4 shipped together in **PR #935**
(`fix/today-label-sod-boundary`).

| # | Item | Outcome |
|---|------|---------|
| 1 | `TaskCardDueDate.formatDueDate` | Reads `LocalDayBounds.current` (SoD-anchored) instead of `Calendar.getInstance()`. |
| 2 | `TaskCardDueDate.isTaskOverdue` | Deleted — confirmed dead (no callers in `app/src/main`). |
| 3 | `DateShortcuts.today/tomorrow` | Untouched. The buggy caller (#4) was switched off them and onto `core.time.DayBoundary.logicalDate(...)`; the legitimate point-of-use snapshot caller (`TaskTemplateRepository`) keeps the no-arg form. |
| 4 | `QuickRescheduleFormatter.describe` | New `sodHour`/`sodMinute`/`zone` overload using `DayBoundary.logicalDate`; 6 ViewModel callers (Today, TaskList, TaskListBulk, Week, Timeline, Month) plumb SoD via `taskBehaviorPreferences.getStartOfDay().first()`. MonthVM + TimelineVM gained a `TaskBehaviorPreferences` injection. |

**Wiring:** `MainActivity` now installs `LocalDayBounds` from
`localDateFlow.observe(taskBehaviorPreferences.getStartOfDay())` so card
labels re-key at every Start-of-Day boundary crossing — same source-of-truth
as `TodayViewModel.dayStart`/`dayEnd`.

**Tests added:**

- `TaskCardDueDateBoundsTest` — pure-logic repro on `classifyDueDate(...)`,
  with a "calendar-bounds" branch that documents the pre-fix shape.
- `QuickRescheduleFormatterSoDTest` — covers Today/Tomorrow under SoD=04:00
  at wall-clock 02:00 + back-compat path with SoD=00:00.
- `TimelineViewModelTest` — adjusted constructor call for the new
  `TaskBehaviorPreferences` parameter.

**Re-baselined Phase 2 estimate:** ~95 LOC of net main-source change
(plus tests + audit doc), single coherent PR, ~1 hour wall-clock from
Phase 1 commit to PR open. Bundling #1–#4 into one PR was the right call:
they share the `LocalDayBounds`/`DayBoundary.logicalDate` plumbing and
splitting would have been pure churn.

**Memory entry candidates (only if a future Claude would benefit):**

- None new. The bug class is already covered by
  `feedback_localdateflow_for_logical_day_flows.md` and
  `feedback_repro_first_for_time_boundary_bugs.md`.

**Schedule for follow-up audit:** queue `NLP_LOGICAL_TODAY_AUDIT.md` to
cover deferred items #5 (QuickReschedulePopup chips) and #6
(`NaturalLanguageParser` regex `today` keyword). Both are write-side
semantic changes that deserve their own brainstorming pass — they decide
*what dueDate value gets persisted* when the user types "today" or taps
the chip, not just what the label says.

---

## Phase 4 — Claude Chat handoff

```markdown
# PrismTask Today/Tomorrow label SoD-boundary fix — handoff

**Repo:** github.com/averycorp/prismTask (Android, Kotlin/Compose)
**Branch:** `fix/today-label-sod-boundary` → PR #935 (auto-merge SQUASH armed; CI gating)

## Scope
Audited every place a task's due date renders or is bucketed as "Today" /
"Tomorrow" / "Overdue". A user reported "tasks should not show as being
due the same day until the set Start of Day" — this is the *display side*
of the SoD-boundary work that PRs #798/#811/#812 already shipped on the
filter/grouping side.

## Verdicts

| Item | Risk | Finding |
|------|------|---------|
| `TaskCardDueDate.formatDueDate` | RED | Used `Calendar.getInstance()` midnight at render time, not SoD. |
| `TaskCardDueDate.isTaskOverdue` | RED | Same bug shape AND dead (no callers in `app/src/main`). |
| `DateShortcuts.today/tomorrow` | RED | No SoD parameter — buggy for display callers; legit for snapshot callers. |
| `QuickRescheduleFormatter.describe` | RED | Transitive on DateShortcuts; 6 ViewModel callers. |
| `QuickReschedulePopup` chips | YELLOW | Sets `dueDate = calendar today/tomorrow` on tap — write-side semantic, DEFERRED. |
| `NaturalLanguageParser` `today` regex | YELLOW | Resolves "today" to calendar date, not logical — DEFERRED to NLP_LOGICAL_TODAY_AUDIT. |
| Week/Month-view `today` highlight | GREEN | Calendar grid should highlight calendar-today, not logical-today. |
| `TodayViewModel.dayStart` filter | GREEN | Already on `LocalDateFlow` (PR #811). |

## Shipped
- **PR #935** — `fix(ui): Today/Tomorrow card labels track Start of Day, not calendar midnight`
  - New `LocalDayBounds` CompositionLocal installed at `MainActivity` from `LocalDateFlow.observe(...)`.
  - `formatDueDate` reads `LocalDayBounds.current` instead of `Calendar.getInstance()`.
  - SoD-aware overload on `QuickRescheduleFormatter.describe`; 6 ViewModel callers plumbed.
  - Dead `isTaskOverdue` deleted.
  - Repro tests: `TaskCardDueDateBoundsTest`, `QuickRescheduleFormatterSoDTest`.

## Deferred / stopped
- **YELLOW #5** (chip semantics) — DEFERRED: changing what `Today` chip *writes* changes reminder timing & recurrence anchors; the user's complaint is display, not write-side.
- **YELLOW #6** (NLP `today` keyword) — DEFERRED to a separate `NLP_LOGICAL_TODAY_AUDIT.md`; same write-vs-display split as #5.
- **GREEN #7, #8** — STOP, no work needed.

## Non-obvious findings
- The SoD-aware Today *filter* and TaskList *grouping* moved to `LocalDateFlow` in PRs #798/#811/#812 but the per-card *label* stayed on calendar midnight. Symptom: at 02:00 wall-clock with SoD=04:00, a task dated calendar-today afternoon would (correctly) sit in the "Tomorrow" group of the Tasks list, while the card text inside it (incorrectly) read "Today" — internally inconsistent within a single screen.
- `formatDueDate` had no callers other than `TaskCard.TaskItem`, but `isTaskOverdue` (same buggy shape) had **no callers at all** — drive-by deletion in the same PR.
- `DateShortcuts.today/tomorrow` is correctly used by `TaskTemplateRepository` (point-of-use snapshot when instantiating a template at user-tap time). Not every calendar-midnight call site is a bug — discriminator is "display label" vs "snapshot at user action".

## Open questions
- Should the `Today`/`Tomorrow` chips in `QuickReschedulePopup` write **logical** today/tomorrow or **calendar** today/tomorrow? Affects reminder timing, recurrence anchors. Worth a brainstorming pass before silently changing.
- Same question for NLP "today"/"tomorrow" keyword resolution.
```

