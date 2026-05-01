# Leisure Screen — Back Button & Toolbar Audit

**Scope.** User report: "I want a back button and the toolbar present on the
leisure screen." Verify both premises against the current `main` codebase
(commit baseline below) and recommend the smallest concrete fix.

**Reference commit.** Audit performed against `origin/main` at
`42f80c16` (docs(audits): add onboarding coverage audit, #1007). The
relevant file `LeisureScreen.kt` is unchanged from `7be7c74c` (Split
AddEditTemplateScreen and LeisureScreen) plus `785c7c8e` (Allow the
addition of custom leisure sections).

---

## Phase 1 — Audit

### Item 1 — Back button on Leisure screen `(YELLOW)`

**Premise.** "There is no back button on the Leisure screen."

**Findings.**
- `LeisureScreen` renders a `TopAppBar` (`LeisureScreen.kt:71-109`) with
  `title`, `actions` (Settings + Refresh), and `colors` — but **no
  `navigationIcon` slot is set**. No `popBackStack()` call exists in the file.
- The screen is a feature route (`ModeRoutes.kt:24`,
  `simpleSlideComposable(PrismTaskRoute.Leisure.route)`) reached via
  `navController.navigate(...)` from Today (`TodayScreen.kt:559, 596, 600`)
  and from the Daily-essentials habit dispatcher
  (`HabitListScreen.kt:279`). The route therefore sits on the back stack —
  `popBackStack()` is the right escape hatch, the affordance is just missing
  on screen.
- Sibling life-mode detail screens already render a back-arrow
  `navigationIcon` with `popBackStack()`:
  `SelfCareScreen.kt:106-110`,
  `LeisureSettingsScreen.kt:71`,
  `MedicationLogScreen.kt:66`,
  `MedicationRefillScreen.kt:64`,
  `SyllabusReviewScreen.kt:112`,
  `AddEditCourseScreen.kt:105`.
  59 screens in the app define `navigationIcon`; Leisure is the conspicuous
  outlier inside its own peer group.
- System back gesture and `Escape` (NavGraph keyboard shortcut at
  `NavGraph.kt:415-418`) still pop the stack today, so this is a
  discoverability gap, not a trap-state.

**Risk classification.** YELLOW — UX inconsistency, navigable via gesture but
no on-screen affordance. The fix is mechanical and matches an established
local pattern (`Icons.AutoMirrored.Filled.ArrowBack` + `popBackStack()`).

**Recommendation.** PROCEED. Add a `navigationIcon` to the `LeisureScreen`
`TopAppBar` that calls `navController.popBackStack()`, mirroring
`SelfCareScreen.kt:106-110` exactly so the look-and-feel matches the rest of
the life-mode cluster.

### Item 2 — "Toolbar present on the leisure screen" `(GREEN)`

**Premise verification.** Premise that "the toolbar is missing" is **wrong**
when read against the existing code. `LeisureScreen.kt:71-109` already wraps
the screen in a `Scaffold` whose `topBar` slot renders a `TopAppBar` with the
"DAILY / Leisure Mode" title block, the Settings action, and the Refresh
action. The toolbar IS present today.

A second possible reading — "I want the bottom navigation toolbar to remain
visible on Leisure" — also doesn't hold up:

- `NavGraph.kt:361` gates the bottom bar on
  `currentRoute == null || currentRoute == PrismTaskRoute.MainTabs.route`,
  so the bottom bar is hidden on **every** feature route by design (Self-Care,
  Schoolwork, Medication-Log, Medication-Refill, Leisure-Settings,
  Syllabus-Review, Add-Edit-Course all behave the same way). Special-casing
  Leisure here would break a uniform convention with no signal that the user
  is asking for a global UX change.

**Risk classification.** GREEN — top toolbar already shipped; bottom-bar
hiding is intentional and consistent across the feature-route cluster.

**Recommendation.** STOP-no-work-needed for the "toolbar present" piece.
Document the verification so the conversation thread doesn't loop back to it.
If the user actually wants the bottom bar to leak into feature routes, that's
a global navigation refactor and warrants its own audit (called out as an
open question for handoff).

---

## Ranked improvement table

| # | Item | Wall-clock saved | Implementation cost | Ratio | Verdict |
|---|------|------------------|---------------------|-------|---------|
| 1 | Add `navigationIcon` back arrow to `LeisureScreen` `TopAppBar` | ~5s per Leisure visit (no need to swipe gesture / search for back) | ~5 min (one composable edit) | High | PROCEED |
| 2 | Make bottom nav bar visible on Leisure feature route | n/a — would be inconsistent with 6+ peer screens | Cross-cutting nav refactor | Low | STOP-no-work-needed (out of scope) |

## Anti-patterns flagged (no fix bundled)

- `SchoolworkScreen.kt:213-244` shares the exact same pattern as Leisure —
  feature route reached via `navController.navigate(...)`, `TopAppBar` with
  no `navigationIcon`. Worth flagging for a follow-up sweep, but **out of
  this audit's scope** since the user only named Leisure. Don't drive-by it.
- `MedicationScreen` (which also has no back button) is intentionally tab-
  hosted from `MainTabs` (`NavGraph.kt:566-567`) — back button would be
  wrong there. Confirms the per-screen judgment matters.

---

## Phase 3 — Bundle summary

- **PR #1011** — `fix(leisure): add back button to Leisure screen toolbar`
  (branch `fix/leisure-back-button`, worktree
  `/Users/averykarlin/prismTask-leisure-back-btn`).
  - Touches: `app/.../ui/screens/leisure/LeisureScreen.kt` (one
    `navigationIcon` slot + one import for `ArrowBack`) plus this audit
    doc. Net: 2 files, 110 insertions, 0 deletions.
  - Mirrors `SelfCareScreen.kt:106-110` exactly so the affordance matches
    the rest of the life-mode cluster.
- **Auto-merge intentionally NOT enabled.** `origin/main` is currently red
  on Android CI: `TaskListItemScopes.kt` has unresolved references
  (`drawRoundRect`, `toPx`, `detectTapGestures`, `startTransfer`)
  introduced by `7d928cb0` (refactor partial — Card/Button/Chip callsite
  migration). Same failure reproduces locally on a fresh worktree off
  `origin/main`. The required Android CI check therefore can't pass
  until main is fixed — auto-merge would just sit blocked. Re-evaluate
  once main is green.
- **No measured-impact follow-ups bundled here.** Schoolwork has the same
  back-button gap (flagged in anti-patterns above) but is **explicitly
  out of scope** since the user named only Leisure. Worth its own
  one-paragraph audit if the user wants the sweep — call out as an open
  question for handoff.
- **Memory entry candidates.** Nothing surprising. The "feature routes
  hide the bottom nav" rule is documented in this audit and visible in
  `NavGraph.kt:361`; not worth a memory entry.
- **Schedule for next audit.** No follow-up needed. If the user later
  asks for a sweep across all life-mode screens, that warrants a fresh
  audit — keep this one scoped to its single deliverable.
