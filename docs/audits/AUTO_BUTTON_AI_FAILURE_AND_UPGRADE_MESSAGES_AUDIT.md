# Auto Button — AI Failure + Free-Tier Upgrade Messaging Audit

**Date:** 2026-05-05
**Branch:** `claude/auto-button-messages-oZ0gS`
**Operator scope:** "Auto buttons should show a failure message if they are
unable to connect to AI and an upgrade message for Free users."
**User-reported symptom:** "Right now, when I click it, nothing at all happens."

The premise is real for *some* Auto buttons (silent fire-and-forget paths) and
already handled for others (sealed `*UiState` with Loading/Empty/Error +
upgrade `AlertDialog`). This audit splits the inventory into wired vs. broken
so Phase 2 only touches the broken paths.

## Inventory

Cross-referenced from `ui/screens/`. Each button's verdict is the result of
following the click handler all the way to where state is rendered.

### 1. Timeline > Auto-Block My Day (GREEN)

`TimelineScreen.kt:142` → `TimelineViewModel.showAutoBlockMyDaySheet()`
(`TimelineViewModel.kt:424`) gates Pro and toggles `_showHorizonSheet`. Error
path: `runAutoBlockMyDay()` (line 449) wraps in try/catch, sets
`AiScheduleUiState.Error(msg)`, rendered as a dismissible banner
(`TimelineScreen.kt:213-217`). Upgrade dialog rendered at line 617-635.
Fully wired.

### 2. Eisenhower > Categorize / Re-Categorize (GREEN)

`EisenhowerScreen.kt:151` → `EisenhowerViewModel.categorize()` (line 108)
checks Pro, sets `_showUpgradePrompt`, otherwise sets `EisenhowerUiState.Error`
on failure. Both states rendered (banner + AlertDialog). Fully wired.

### 3. Eisenhower task overflow > "Reclassify With AI" (RED)

`EisenhowerScreen.kt:505-518` → `EisenhowerViewModel.reclassify(taskId)`
(`EisenhowerViewModel.kt:173`) → `taskRepository.reclassify(taskId)`
(`TaskRepository.kt:628`) → `classifyInBackground(taskId)`
(`TaskRepository.kt:91`).

Three failure modes, all silent:
- **No Pro gate.** Free users hit the same path as Pro and get nothing.
- **`autoClassifyEnabled` short-circuit.** If the user has the auto-classify
  preference off, `classifyInBackground` returns at line 94 with no UI
  feedback. The user explicitly tapped "Reclassify With AI" — that intent
  should override the global preference, or at minimum surface a message.
- **AI failure swallowed.** `eisenhowerClassifier.classify(task)` returns
  `Result.failure` on no-token, network 5xx, or malformed response
  (`EisenhowerClassifier.kt:39`). `classifyInBackground` discards it via
  `result.getOrNull() ?: return@launch` (line 98) — no Snackbar, no banner,
  no log surfaced to the user.

This is the canonical "nothing happens" case.

**Recommendation:** PROCEED. Route the menu item through a new VM method
that (a) checks Pro and shows the existing upgrade prompt for Free, (b)
ignores `autoClassifyEnabled` for an explicit user request, (c) surfaces
failure via the existing `EisenhowerUiState.Error` banner the user already
sees for the bulk Categorize button. Re-use the existing infrastructure —
no new sealed states.

### 4. DetailsTab > "Help Me Start" (YELLOW)

`DetailsTab.kt:177-193` → `CoachingViewModel.getStuckHelp(taskId)`
(`CoachingViewModel.kt:181`).

VM logic is correct: `CoachingResult.UpgradeRequired` sets
`_showUpgradePrompt`, `CoachingResult.Error` logs and sets `_errorMessage`.
The UI renders the upgrade prompt (`DetailsTab.kt:236-243`), but never reads
`coachingViewModel.errorMessage`. On AI/backend failure:

1. `_stuckLoading` flips true → `CoachingCard` (line 204) shows a spinner.
2. Backend errors → VM sets `_errorMessage`, clears `_stuckLoading`,
   leaves `_stuckMessage` null.
3. `CoachingCard(message=null, isLoading=false)` renders nothing.

End state: button looked tappable, then the card vanishes with no
explanation. From the user's perspective: nothing happened.

**Recommendation:** PROCEED. Wire `coachingViewModel.errorMessage` to a
SnackbarHost in the AddEditTask sheet (or a small inline error row inside
the existing CoachingCard). Use the same Snackbar host the
AddEditTaskViewModel already uses for `errorMessages` so the user sees one
consistent error surface.

### 5. DetailsTab > "Break It Down" (YELLOW)

Same shape as Help Me Start — `getTaskBreakdown()` sets `_errorMessage` on
`CoachingResult.Error` (`CoachingViewModel.kt:336`), and the UI never reads
it. `BreakdownResultCard` (line 222) renders only when `breakdownSubtasks`
is non-empty, so an AI failure leaves the user with a button click and no
visible response. `FreeLimitReached` and `UpgradeRequired` *are* wired
(line 329-334) and render the upgrade dialog correctly.

**Recommendation:** PROCEED — fix is identical to #4. One Snackbar wire-up
covers both Coaching buttons.

### 6. Chat > AI Coach screen (GREEN)

`ChatScreen.kt:84,109-114` collects `error` and shows it in the
SnackbarHost. Upgrade prompt rendered at line 116-133. Fully wired.

### 7. Weekly Planner > Generate Plan (GREEN)

`WeeklyPlannerScreen.kt:162` → `WeeklyPlannerViewModel.generatePlan()`
(line 139). Pro gate at line 140; error captured in `_error` and surfaced
via Snackbar (line 88-93). Fully wired.

### 8. Smart Pomodoro > Generate Plan (GREEN)

`SmartPomodoroScreen.kt:159` → `SmartPomodoroViewModel.generatePlan()`
(line 484). Pro gate, sealed `PomodoroPlanUiState.Error(msg)`, upgrade
dialog rendered. Fully wired.

### 9. Daily Briefing (GREEN)

Auto-loads on entry; both Pro gate (line 118-134) and error state are
handled by the existing sealed `DailyBriefingUiState`.

### 10. Habit Correlations > Analyze (GREEN)

`HabitCorrelationsSection.kt:70` — exemplary. Explicit
`HabitCorrelationsOutcome` sealed type with NotPro, BackendUnavailable,
RateLimited, AiFeaturesDisabled, Success states each rendering their own
copy (lines 88-152). The pattern other Auto buttons should emulate.

### 11. Weekly Review (GREEN)

Auto-loads, falls back to a local narrative on AI failure with a banner
explaining "AI review unavailable — showing local summary"
(`WeeklyReviewViewModel.kt:154-162`).

## Verdict table

| # | Button | Verdict | Reason |
|---|---|---|---|
| 1 | Timeline > Auto-Block My Day | GREEN | Wired (banner + dialog) |
| 2 | Eisenhower > Categorize | GREEN | Wired (banner + dialog) |
| 3 | Eisenhower > Reclassify With AI (menu) | **RED** | No Pro gate; swallowed errors |
| 4 | DetailsTab > Help Me Start | **YELLOW** | `errorMessage` set in VM but never rendered |
| 5 | DetailsTab > Break It Down | **YELLOW** | Same as #4 |
| 6 | Chat > AI Coach | GREEN | Wired (snackbar + dialog) |
| 7 | Weekly Planner > Generate Plan | GREEN | Wired (snackbar + dialog) |
| 8 | Smart Pomodoro > Generate Plan | GREEN | Wired (banner + dialog) |
| 9 | Daily Briefing | GREEN | Auto-load, both states wired |
| 10 | Habit Correlations > Analyze | GREEN | Exemplary outcome enum |
| 11 | Weekly Review | GREEN | Local-narrative fallback |

## Wall-clock-savings ÷ implementation-cost ranking

| Rank | Item | Why |
|---|---|---|
| 1 | #3 Reclassify With AI | The clearest "nothing happens" case, blocked on a fire-and-forget path with no infrastructure to leverage — but the fix is small (route through the VM, add Pro gate, set `EisenhowerUiState.Error` on failure). |
| 2 | #4 + #5 Coaching buttons | Single Snackbar wire-up covers both. ViewModel already exposes `errorMessage`; we just need `LaunchedEffect(errorMessage) { ... }` and a SnackbarHost (or reuse AddEditTaskViewModel's). |

## Anti-patterns flagged (no fix in this audit)

- **`reclassify()` repository method is fire-and-forget** but the only
  consumer that needs synchronous error feedback is the manual menu item.
  Background classification on insert/update *should* stay silent. Solution
  is a separate explicit-user-request path, not retrofitting
  `classifyInBackground` to surface errors.
- **`autoClassifyEnabled` preference applies to background classification
  only.** Explicit user actions should bypass it. Worth a comment on the
  flag's docstring during the fix.
