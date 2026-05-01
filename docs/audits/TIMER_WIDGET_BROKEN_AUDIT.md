# Timer Widget — "Doesn't Work" Audit

**Scope.** User reports that the home-screen Timer widget "doesn't work."
Verify the end-to-end widget path against the codebase, classify each
failure mode, and identify ship-ready fixes.

**Source files in scope.**
- `app/src/main/java/com/averycorp/prismtask/widget/TimerWidget.kt`
- `app/src/main/java/com/averycorp/prismtask/widget/TimerStateDataStore.kt`
- `app/src/main/java/com/averycorp/prismtask/widget/WidgetActions.kt`
- `app/src/main/java/com/averycorp/prismtask/widget/WidgetUpdateManager.kt`
- `app/src/main/java/com/averycorp/prismtask/widget/FocusWidget.kt`
- `app/src/main/java/com/averycorp/prismtask/ui/screens/timer/TimerViewModel.kt`
- `app/src/main/java/com/averycorp/prismtask/MainActivity.kt`
- `app/src/main/java/com/averycorp/prismtask/ui/navigation/NavGraph.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/timer_widget_info.xml`
- `app/src/test/java/com/averycorp/prismtask/widget/TimerStateDataStoreTest.kt`

## Item 1 — Start button is a dead deep-link (RED)

`TimerWidget.kt:99-108` builds an intent with
`EXTRA_LAUNCH_ACTION = "open_timer"` for the idle-state "▶ Start" button.
`FocusWidget.kt:85-88` does the same for its "Start timer" affordance.

`MainActivity.kt:128-133` declares constants for `quick_add`, `open_templates`,
`voice_input`, and `open_habits` — but **no `ACTION_OPEN_TIMER` constant
exists**. `NavGraph.kt:318-358` only routes `ACTION_OPEN_TEMPLATES`,
`ACTION_VOICE_INPUT`, and `ACTION_OPEN_HABITS`. The string `"open_timer"`
appears only in the two widget files (verified via
`grep -rn "open_timer" app/src/`); zero handlers consume it.

Net effect: tapping "▶ Start" on the Timer widget (or "Start timer" on the
Focus widget) launches MainActivity but **lands on whatever the default
landing tab is — never the Timer screen**. The idle widget therefore
appears non-functional.

**Recommendation:** PROCEED. Add `ACTION_OPEN_TIMER` constant + NavGraph
handler that scrolls the pager to the Timer tab (PrismTaskRoute.Timer is
already a registered bottom-nav route at `NavGraph.kt:113,294,550`).

## Item 2 — Pause / Resume / Stop / Skip don't drive the running timer (RED)

`WidgetActions.kt:76-131` defines `PauseTimerAction`, `ResumeTimerAction`,
`StopTimerAction`, and `SkipBreakAction`. Each one only mutates the
`TimerStateDataStore` preference store (`isRunning` / `isPaused`) and
calls `TimerWidget().updateAll(context)`.

The actual countdown lives in `TimerViewModel.kt:158-198`, where
`tickJob` is a `viewModelScope.launch { ... }` coroutine that reads
**only** `_uiState.value.isRunning` per tick. The flow is one-way:
`TimerViewModel → syncWidgetState() → TimerStateDataStore`. There is
no observer on the DataStore from the ViewModel, and the widget actions
do not bind to the ViewModel.

Behavioral consequences:

1. **Widget Pause does nothing in practice.** Tapping "⏸" writes
   `isRunning=false, isPaused=true` to DataStore. The next regular tick
   in the live ViewModel calls `syncWidgetState()` and overwrites that
   write with whatever the in-app countdown thinks. Worst case: widget
   blinks "paused" then snaps back to running.
2. **Widget Resume is similarly defeated** if the timer is actually
   running in-app, and is a no-op if the ViewModel has been destroyed
   (its coroutine was cancelled in `onCleared`, see Item 3).
3. **Stop only clears DataStore.** The in-app `tickJob` keeps ticking
   — the ViewModel will re-write the cleared state on the next tick.
4. **SkipBreak only mutates DataStore** — the ViewModel's
   `mode`/`completedSessions` are unaffected; pomodoro state desyncs.

Worth flagging: the `TimerStateDataStoreTest` suite
(`app/src/test/.../TimerStateDataStoreTest.kt`) verifies the data-class
shape but **never exercises the round-trip between widget action and
ViewModel** — which is exactly the broken path. The unit tests are
green and the bug is invisible to CI.

**Recommendation:** PROCEED, but scoped — full bidirectional control
requires migrating the countdown to a foreground service (already
flagged as a TODO at `TimerViewModel.kt:177-180`). For this audit, the
minimum-viable fix is:

- Document the limitation clearly in `WidgetActions.kt` for now.
- When the Timer screen is **not** active (DataStore says
  `isRunning=false` or no live ViewModel), widget actions should be
  no-ops or should hide the buttons.
- When the Timer screen **is** active, the widget control buttons
  cannot reliably override the live ViewModel, so they should be
  hidden or replaced with a "Open timer" tap target.

The least-risk fix that restores user trust is **Option B**: replace
the Pause/Resume/Stop/Skip buttons in the widget with a single tap
target that opens the Timer screen, and rely on the in-app controls.
Keep the live readout (time remaining / session label / progress bar)
as today. This avoids the race condition entirely.

## Item 3 — Stale "running" state when ViewModel is destroyed (YELLOW)

`TimerViewModel.kt:382-393` (`onCleared`) only clears the DataStore
when `!s.isRunning`. If the timer is actively running and the user
navigates away from the Timer tab (which destroys the ViewModel under
default navigation-compose scoping), the `tickJob` coroutine is
cancelled by `viewModelScope`, but the DataStore is left untouched
with `isRunning=true` and a stale `remainingSeconds`.

The widget will continue to display "Session N of M" with a frozen
clock, indefinitely, until the user re-opens the Timer screen.

This is the same root cause as the `TODO` at `TimerViewModel.kt:177-180`:
the timer should be a foreground service, not a `viewModelScope`
coroutine.

**Recommendation:** DEFER full foreground-service migration (large,
out of scope), but PROCEED with a defensive cleanup: on `onCleared`,
if `s.isRunning`, clear the DataStore *and* trigger
`updateTimerWidget()` so the widget snaps back to the idle "Ready
to focus" state instead of pretending to run.

This trades one bug (frozen clock) for a different but more honest
bug (timer "stops" when you leave the screen) — which is what the
in-app timer *actually does today*. The widget then matches reality.

## Item 4 — Widget update period is 30s but tick is per-second (YELLOW)

`timer_widget_info.xml` declares `android:updatePeriodMillis="30000"`
(30 seconds). `TimerViewModel.kt:189-194` calls `syncWidgetState()`
every 30 ticks — so the widget refreshes its readout once every 30s
even while the live screen ticks every 1s. This is intentional
(battery), but it means the widget's "remaining time" lags by up to
30 seconds.

Combined with the fact that pause/resume don't work (Item 2), users
will tap pause, see no UI change for up to 30 seconds, then watch the
widget snap back to its old (running) value when the next sync fires
— reinforcing the perception that the widget is broken.

**Recommendation:** STOP-no-work-needed for the update interval
itself (it's the right tradeoff). Item 2's fix (hide pause/resume,
keep readout only) makes the lag harmless.

## Item 5 — `MainActivity` does not override `onNewIntent` (YELLOW)

If the app is already running and the user taps the Timer widget,
`MainActivity` is brought back to the foreground without re-entering
`onCreate`, and the new intent's `EXTRA_LAUNCH_ACTION` is **not
read** because no `onNewIntent(intent)` override exists
(`grep -n onNewIntent MainActivity.kt` → 0 matches).

This means even after Item 1 is fixed, the deep-link will only work
on cold start. Hot-launches will silently no-op.

**Recommendation:** PROCEED. Override `onNewIntent`, call
`setIntent(intent)`, and re-emit `launchAction` into the existing
NavGraph (e.g. via a `mutableStateOf` snapshot or a one-shot
`SharedFlow`).

This is a foundational fix that benefits **every** widget deep-link
(QuickAdd, Templates, Voice, Habits, and the new Timer one) — not
just the Timer widget.

## Item 6 — Widget tests don't cover end-to-end action handlers (GREEN)

`TimerStateDataStoreTest.kt` validates the `TimerWidgetState` data
class. There are no tests for `PauseTimerAction`, `ResumeTimerAction`,
`StopTimerAction`, or `SkipBreakAction`, and no tests verifying that
the Timer widget Start button targets `PrismTaskRoute.Timer`.

**Recommendation:** PROCEED. Add lightweight unit tests verifying
that:
- The Start button's intent carries `ACTION_OPEN_TIMER`.
- The NavGraph actually consumes `ACTION_OPEN_TIMER` and lands on
  the Timer route.

The action callbacks themselves are harder to unit-test (they need a
Context and DataStore), so leave those as-is for this batch.

---

## Ranked improvements

Sorted by `wall-clock-savings ÷ implementation-cost`. "Cost" is
small/medium/large, "savings" is the user-visible bug-fix gain.

| Rank | Item                                     | Risk   | Cost   | Action |
|------|------------------------------------------|--------|--------|--------|
| 1    | Item 1 — Add `open_timer` deep-link      | RED    | small  | PROCEED |
| 2    | Item 5 — Override `onNewIntent`          | YELLOW | small  | PROCEED |
| 3    | Item 2 — Hide widget pause/resume/skip   | RED    | small  | PROCEED |
| 4    | Item 3 — Clear DataStore on `onCleared`  | YELLOW | small  | PROCEED |
| 5    | Item 6 — Add deep-link nav test          | GREEN  | small  | PROCEED |
| 6    | Foreground service for timer (TODO)      | n/a    | large  | DEFER  |

Items 1, 2, 3, 5, 6 fit cleanly in a single PR — they're a coherent
"Timer widget actually works" scope. Item 5 is technically a wider
fix (helps all widget deep-links) but is small and shipped together
because Item 1 is a no-op without it on hot-launches.

## Anti-patterns flagged (no fix scheduled)

- **One-way state syncing without an explicit owner.** The widget's
  pause/resume buttons writing to a DataStore that the live ViewModel
  doesn't observe is a classic "two writers, no reader" pattern. Any
  future widget that needs to drive in-app behavior should go via a
  foreground service or a shared singleton with a back-channel.
- **`grep`-only deep-link wiring.** `EXTRA_LAUNCH_ACTION` strings are
  scattered across widgets and consumers as raw string literals. A
  sealed enum + exhaustive `when` would catch unhandled actions at
  compile time. Out of scope for this audit but worth a follow-up.
- **`updatePeriodMillis` doesn't actually drive Glance widgets.**
  Glance refreshes via `updateAll()`. The 30000ms manifest value is
  an OS hint that's largely ignored on modern Android; left as-is.
