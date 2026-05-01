# Widget ↔ Tab Parity Audit (Timer + Eisenhower)

**Date**: 2026-05-01
**Branch**: `claude/sync-alarm-widget-yOrsK`
**Scope**: User asks (1) the Timer widget to mirror the Timer tab, (2) the
Eisenhower widget to mirror the Eisenhower screen, (3) reports a
"Couldn't update task" error when checking off a task. The first two
re-open territory the codebase has audited recently — see
`TIMER_WIDGET_BROKEN_AUDIT.md` (PR #1042) and
`WIDGET_FUNCTIONALITY_FIDELITY_AUDIT.md` (PR #1025) — so this audit
classifies the remaining surface area against those landed decisions
rather than re-deriving them.

## TL;DR

| # | Item | Risk | Action |
|---|------|------|--------|
| 1.1 | Timer countdown lives in `viewModelScope`, not a service | RED | DEFER — same blocker `TIMER_WIDGET_BROKEN_AUDIT.md` § Item 6 already flagged; needs its own PR |
| 1.2 | Inline Pause/Resume/Skip controls on Timer widget | RED | DEFER — deliberately removed by PR #1042; cannot return until 1.1 lands |
| 1.3 | Pomodoro session indicator (dots) on Timer widget when running | GREEN | PROCEED — display-only, reads existing `TimerStateDataStore.currentSession/totalSessions` |
| 1.4 | Mode label parity (Long Break vs Short Break) | GREEN | PROCEED — `sessionType` field exists; widget currently shows generic "Break Time" |
| 1.5 | Inline Pomodoro / auto-start / custom-duration toggles | YELLOW | DEFER — Glance has no dialog/picker; settings deep-link is the right shape |
| 1.6 | Themed circular timer ring | YELLOW | DEFER — Glance can't render arbitrary canvas; linear progress is the right shape |
| 2.1 | Per-quadrant tap deep-links to expanded view | GREEN | PROCEED — widget currently only opens the matrix root |
| 2.2 | Per-quadrant complete-top-task action | YELLOW | DEFER — adds density to a 2×2 widget; user value unclear |
| 2.3 | Categorize / Re-Categorize button on widget | YELLOW | DEFER — Pro-gated AI call; foreground-execution + auth UX out of scope |
| 2.4 | Move-task / reclassify dropdown on widget | RED | STOP-no-work-needed — Glance has no popup menus |
| 2.5 | Priority dot + due-date hint per top task | GREEN | PROCEED — both fields already on `TaskEntity`; small display polish |
| 3.1 | "Couldn't update task" snackbar on checkoff | YELLOW | DEFER-pending-repro — exact failure path identified, root cause requires runtime stack trace |

Phase 2 ships the four GREEN items (1.3, 1.4, 2.1, 2.5) on
`claude/sync-alarm-widget-yOrsK`. The two RED items and the bug are
held until the user supplies (a) approval to land the foreground-service
migration as a separate large PR and (b) a stack trace for the
checkoff failure.

---

## Surface 1: Timer widget ↔ Timer tab

### 1.1 Timer countdown lives in `viewModelScope`, not a service (RED — blocker)

**Premise verification.** The Timer tab (`TimerScreen.kt` +
`TimerViewModel.kt`) runs its countdown in
`viewModelScope.launch { while(true) { delay(1000L); … } }` at
`TimerViewModel.kt:165-198`. The widget snapshot DataStore is written
from that same coroutine via `syncWidgetState()` every 30 ticks
(`TimerViewModel.kt:189-194`). The TODO at `TimerViewModel.kt:177-180`
explicitly calls out the migration:

> // TODO: migrate countdown to PomodoroTimerService-style
> //  foreground service so the timer survives backgrounding

`PomodoroTimerService` already exists and is used by the *Smart
Pomodoro* tab (`SmartPomodoroViewModel`); its companion exposes
`ACTION_START` / `ACTION_STOP` / `ACTION_TICK` / `ACTION_COMPLETE` and
the running countdown survives backgrounding.

**Findings.** Until the Timer tab uses a foreground service:

- Tapping a widget control button can only mutate
  `TimerStateDataStore`. The live ViewModel doesn't observe that store
  (verified — there are zero `timerStateDataStore.data.collect` call
  sites outside `TimerWidget.provideGlance` itself), so widget
  mutations are overwritten on the next ViewModel `syncWidgetState()`
  call.
- When the Timer tab is destroyed, the countdown is gone. The widget
  snapshot is cleared by PR #1042's
  `WidgetUpdateManager.clearTimerStateAndUpdate()` from `onCleared`.

**Risk classification.** RED — the foreground-service migration is the
foundation any inline-control work depends on. Skipping it
re-introduces the exact "two writers, no reader" anti-pattern that
`TIMER_WIDGET_BROKEN_AUDIT.md` § Item 2 caught.

**Recommendation.** DEFER for this session. The migration touches a
critical user flow, has its own test surface, and should ship as a
dedicated PR once the user approves. Out-of-scope for the
parity-fan-out.

### 1.2 Inline Pause/Resume/Skip controls on Timer widget (RED)

**Premise verification.** PR #1042 deliberately removed the four
`PauseTimerAction` / `ResumeTimerAction` / `StopTimerAction` /
`SkipBreakAction` callbacks. `WidgetActions.kt` no longer references
them. The current widget body is "live readout + tap to open app."

**Findings.** The user is asking to put those controls back. Doing so
without 1.1 returns to the broken state PR #1042 fixed — controls
appear functional but get overwritten on the next ViewModel sync.

**Recommendation.** DEFER. Bundles with 1.1. When 1.1 lands, the
widget can drive the service directly via service-targeted intents and
ignore `TimerStateDataStore` for control intent — at that point this
becomes a small mechanical add-back.

### 1.3 Pomodoro session indicator (dots) on Timer widget (GREEN — PROCEED)

**Premise verification.** The Timer tab renders four to N dots
showing current cycle position when pomodoro mode is enabled
(`TimerScreen.kt:506-526`, `PomodoroSessionIndicator`). The widget has
the data — `TimerWidgetState.currentSession` and `totalSessions` are
already in the DataStore (`TimerStateDataStore.kt:28-29`) and written
on every sync (`TimerViewModel.kt:375-376`). The widget displays them
only as the text "Session N of M".

**Findings.** Adding the dot row when the widget is in the LARGE size
bucket is a pure display change — no service work, no new actions,
fits in the existing layout above the linear progress bar.

**Recommendation.** PROCEED. Renders only in `isLarge` mode (≥200dp
wide); SMALL keeps the text-only readout.

### 1.4 Mode label parity (Long Break vs Short Break) (GREEN — PROCEED)

**Premise verification.** The Timer tab distinguishes "Long Break"
from "Short Break" using `uiState.isLongBreak` (`TimerScreen.kt:191`).
The widget always shows literal "Break Time" for any non-work session
(`TimerWidget.kt:121`).

**Findings.** `TimerWidgetState` carries `sessionType` ("work" /
"break") but no `isLongBreak`. The ViewModel knows whether the current
break is long. Adding a single boolean (or distinguishing a third
sessionType `"long_break"`) plus a label switch in the widget is a
two-line change.

**Recommendation.** PROCEED. Add `isLongBreak: Boolean = false` to
`TimerWidgetState`, populate from `TimerViewModel.syncWidgetState()`,
and switch the widget label.

### 1.5 Inline toggles for Pomodoro / auto-start / custom duration (YELLOW)

**Premise verification.** The Timer tab exposes `Pomodoro Mode`,
`Auto-Start Breaks`, `Auto-Start Focus`, and `Custom Duration` as
inline settings rows (`TimerScreen.kt:528-588`). Custom Duration opens
a `DurationPickerDialog`.

**Findings.** Glance widgets cannot host dialogs or `Switch`
composables that toggle without a round trip through an ActionCallback;
toggle-on-tap is feasible (it's the same pattern as
`ToggleTaskFromWidgetAction`), but the result lives in
`TimerPreferences` (DataStore) which the widget would have to re-read
on next refresh. For a 2×2 widget with already-cramped real estate,
adding 3 toggles plus a duration picker is a poor density tradeoff.

**Recommendation.** DEFER. Better shape: small "Settings" button on
the widget that deep-links to `PrismTaskRoute.Timer` (already routed
via PR #1042's `ACTION_OPEN_TIMER`) where the existing dialog UX is
correct. No widget-side change needed today.

### 1.6 Themed circular timer ring (YELLOW)

**Premise verification.** The Timer tab renders a 260dp circular
progress ring with theme-specific tick marks, dashed tracks, sweep
gradients (`TimerScreen.kt:288-411`, `ThemedTimerRing`), drawn via
Compose `Canvas`.

**Findings.** Glance has no `Canvas` equivalent. The widget already
uses `LinearProgressIndicator` (`TimerWidget.kt:163-168`). Faking a
ring would require a static drawable per progress bucket — high cost,
low value, dimensions vary per theme.

**Recommendation.** DEFER. Linear progress is the right shape for a
Glance widget; the user's "mirror functionality" should not literally
mean "render the same vector graphics."

---

## Surface 2: Eisenhower widget ↔ Eisenhower screen

### 2.1 Per-quadrant tap deep-links to expanded view (GREEN — PROCEED)

**Premise verification.** The Eisenhower screen lets the user tap a
quadrant header to enter a full-screen list of just that quadrant's
tasks (`EisenhowerScreen.kt:166-181`, driven by
`EisenhowerViewModel.expandQuadrant(key)`). The widget currently only
opens the matrix root via `EXTRA_LAUNCH_ACTION = "open_matrix"`
(`EisenhowerWidget.kt:96`). Tapping any quadrant cell deep-links to
the same generic screen.

**Findings.** Adding a per-quadrant deep-link is a surface change in
two files: a new `EXTRA_EISENHOWER_QUADRANT` string extra in
`MainActivity` + `NavGraph` consumption in the existing
`open_matrix` handler that calls `expandQuadrant(quadrantKey)` after
navigating. The widget passes the quadrant key alongside the launch
action.

**Recommendation.** PROCEED. Small, mechanical, no service or DB
change.

### 2.2 Per-quadrant complete-top-task action (YELLOW)

**Premise verification.** The Eisenhower screen exposes
`onCompleteTask(taskId)` per row in each quadrant
(`EisenhowerScreen.kt:224, 237, 261, 274`). The widget currently only
displays the top task title — no checkbox.

**Findings.** Adding a complete affordance is technically feasible
(reuse `ToggleTaskFromWidgetAction` pattern) but a 2×2 grid widget at
the SMALL size has ~50dp of vertical real estate per cell after the
header — enough for the count + title text, not enough for a
checkbox column without sacrificing the title. Also: completing the
top task on the widget removes the task from view, surfacing the
*next* top task on the next refresh; this can confuse users into
thinking the action did nothing.

**Recommendation.** DEFER. Better path: per-quadrant tap → expanded
view (covered by 2.1) where the existing `CompactTaskCard` row UI
already has the complete affordance. User clicks once to enter the
quadrant view, then completes from there.

### 2.3 Categorize / Re-Categorize button on widget (YELLOW)

**Premise verification.** The Eisenhower screen's top-right action is
`Categorize` (or `Re-Categorize` after first run); it calls a
Pro-gated AI endpoint (`EisenhowerViewModel.categorize()`,
`EisenhowerScreen.kt:151-160`).

**Findings.** Putting an AI call behind a widget tap creates several
new failure modes:

- The action runs in a background ActionCallback context — without a
  visible loading spinner, the user has no feedback for the 5–30s
  network call.
- Pro-gating: free users would get the upgrade prompt, but Glance
  can't render an `AlertDialog`. The widget would need to deep-link
  into the screen on Pro-failure, which is what the existing tap-
  to-open behavior already does.
- Auth/network failures need a retry surface that doesn't fit on a
  widget.

**Recommendation.** DEFER. The widget already opens the matrix
screen on tap; users can hit Categorize from there. Putting the
button on the widget gates a long-running AI call behind a UI that
can't show its progress.

### 2.4 Move-task / reclassify dropdown on widget (STOP-no-work-needed)

**Premise verification.** The Eisenhower screen's `CompactTaskCard`
has an overflow `MoreVert` menu with "Move to Q1/Q2/Q3/Q4" + "Reclassify
With AI" dropdown items (`EisenhowerScreen.kt:469-520`).

**Findings.** Glance does not support `DropdownMenu` or any popup
menu. The closest analog is launching a separate Activity from the
widget action, which is heavier than the existing tap-to-open.

**Recommendation.** STOP-no-work-needed. The widget opening the
expanded quadrant view (2.1) gets the user to the same dropdown in
one extra tap. No widget-side primitive supports this directly.

### 2.5 Priority dot + due-date hint per top task (GREEN — PROCEED)

**Premise verification.** The Eisenhower screen's `CompactTaskCard`
renders a 6dp priority dot (`EisenhowerScreen.kt:415-420`, color via
`LocalPriorityColors.forLevel(task.priority)`) and a "Tmrw"/"Today"/
"Nd ago" due-date label (`EisenhowerScreen.kt:432-451`). The widget's
top-task line is plain text.

**Findings.** Both fields already live on `TaskEntity` and are
fetched by `WidgetDataProvider.getEisenhowerData`. The
`EisenhowerQuadrantSummary` data class only exposes `topTaskTitle`;
extending it to also carry `topTaskPriority: Int?` and
`topTaskDueDate: Long?` is a localised change.

**Recommendation.** PROCEED. Small display polish that brings the
widget's top-task line in line with the screen's row UI without
changing layout density much.

---

## Surface 3: "Couldn't update task" on checkoff (3.1)

### 3.1 Checkoff failure (YELLOW — DEFER-pending-repro)

**Premise verification.** The exact string "Couldn't update task" is
emitted from a single call site:
`TaskListViewModel.kt:679` —

```kotlin
fun onToggleComplete(taskId: Long, isCurrentlyCompleted: Boolean) {
    viewModelScope.launch {
        try {
            if (isCurrentlyCompleted) taskRepository.uncompleteTask(taskId)
            else taskRepository.completeTask(taskId)
        } catch (e: Exception) {
            Log.e("TaskListVM", "Failed to toggle complete", e)
            snackbarHostState.showSnackbar("Couldn't update task")
        }
    }
}
```

So the user is hitting this on the **Tasks tab** specifically (not
Today, not Eisenhower — those have separate paths and different
snackbars). `taskRepository.completeTask` (`TaskRepository.kt:314`)
runs reminder cancel → DB transaction → sync tracker → calendar push
→ widget refresh; any of the five steps could throw.

**Findings.** Without a runtime stack trace I can't pin down which of
those five throws. Suspects worth checking once a repro exists:

- `reminderScheduler.cancelReminder` → `AlarmManager` access on
  Android 14+ requiring `SCHEDULE_EXACT_ALARM` revocation handling.
- `transactionRunner.withTransaction` → constraint violation if a
  recurrence rule references a missing column post-migration.
- `taskCompletionRepository.recordCompletion` → added by recent
  analytics work (PR #1041); FK or column type mismatch would surface
  here.
- `widgetUpdateManager.updateTaskWidgets` → if any widget receiver
  throws, the bubbled exception aborts the whole completion.
- `calendarPushDispatcher.enqueueDeleteTaskEvent` → calendar
  permission revocation post-grant.

**Risk classification.** YELLOW — user-blocking but I can't reach the
root cause from static inspection alone. Speculative fixes risk
papering over a real Room/Hilt issue.

**Recommendation.** DEFER-pending-repro. Need from the user:

- The full Logcat / Crashlytics stack trace under the
  `"TaskListVM: Failed to toggle complete"` log line, OR
- Reproduction steps + Android version + whether sync/calendar
  permissions were recently revoked.

In the meantime, look at `b8257e5` (`fix(ci): provide Clock binding`)
and `7a56ac0` (`fix(recurrence): make completeTask idempotent + roll
back spawn on Undo`) as the two recent changes that could plausibly
have regressed completion. Neither is an obvious smoking gun on
inspection — `Clock` isn't injected into `TaskRepository`, and
`7a56ac0` strengthens idempotency rather than weakening it.

---

## Improvement table (sorted by parity-impact ÷ implementation-cost)

This audit's metric is parity-impact × confidence ÷ cost — for a
feature audit, "wall-clock-savings" doesn't apply.

| Rank | Item | Risk   | Cost | Action | PR |
|------|------|--------|------|--------|----|
| 1    | 2.1 — Per-quadrant deep-link             | GREEN  | XS  | PROCEED | A |
| 2    | 1.4 — Long-Break / Short-Break label     | GREEN  | XS  | PROCEED | A |
| 3    | 2.5 — Priority dot + due-date hint       | GREEN  | S   | PROCEED | A |
| 4    | 1.3 — Pomodoro session indicator dots    | GREEN  | S   | PROCEED | A |
| 5    | 3.1 — Checkoff failure                   | YELLOW | ?   | DEFER-pending-repro | — |
| 6    | 1.1 — Timer foreground-service migration | RED    | L   | DEFER (own PR) | — |
| 7    | 1.2 — Inline Pause/Resume/Skip           | RED    | M   | DEFER (depends on 1.1) | — |
| 8    | 2.2 — Complete-top-task widget action    | YELLOW | M   | DEFER (covered by 2.1) | — |
| 9    | 2.3 — Categorize button on widget        | YELLOW | M   | DEFER | — |
| 10   | 1.5 — Inline timer settings toggles      | YELLOW | M   | DEFER (deep-link is right shape) | — |
| 11   | 1.6 — Themed circular ring               | YELLOW | L   | DEFER (Glance limitation) | — |
| 12   | 2.4 — Move-task dropdown on widget       | RED    | —   | STOP-no-work-needed | — |

PR A bundles items 1–4 into a single coherent "widget readout fidelity
+ deep-link granularity" scope on `claude/sync-alarm-widget-yOrsK`,
following the fan-out bundling rule (one coherent scope, not four
small PRs).

## Anti-patterns flagged (no fix scheduled)

- **"Mirror the tab" is a leaky abstraction for widgets.** Glance
  widgets are a genuinely different rendering target — no canvas, no
  dialogs, no popups, no live state, only DataStore + ActionCallback
  round-trips. A literal "mirror" is impossible; the right framing is
  "ship the subset of the tab's affordances that survive the
  Glance/launcher contract." This audit covers six items (1.5, 1.6,
  2.2, 2.3, 2.4) where a literal mirror would be wrong.
- **Pro-gated AI calls behind widget taps.** A widget action
  callback runs without a visible loading state and without an
  upgrade-prompt surface. Any future "Pro feature on a widget"
  proposal should default to deep-linking into the screen rather
  than running the network call from the widget process.
- **Widget snapshot DataStore as a bidirectional control channel.**
  Already memorised in `TIMER_WIDGET_BROKEN_AUDIT.md` Phase 3 memory
  candidates; reasserted here to keep the lesson visible the next
  time someone reaches for the same shape.

## Schedule for next audit

After the foreground-service migration (1.1) ships, re-audit Surface
1 to bring back the inline controls (1.2) and re-evaluate 1.5
(toggles can probably ship safely once the service owns state).
Surface 2's deferred items (2.2, 2.3) likely stay deferred — they're
shape mismatches, not blocked work.
