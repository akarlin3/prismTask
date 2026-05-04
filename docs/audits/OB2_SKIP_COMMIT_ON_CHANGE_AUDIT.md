# OB-2 Skip = Commit-On-Change Audit

**Scope.** Implement OB-2 Skip semantic = commit-on-change in the
onboarding Skip handler so partial state already entered into a step
is preserved when Skip is tapped. Deferred from PR #1123 (D-series UX
P2-extension) pending operator design decision; operator picked
commit-on-change on May 4.

## Operator decision (locked)

Skip = **commit-on-change**. Estimated 10–50 LOC depending on current
Skip-handler shape.

## Premise verification (D.1–D.5)

- **D.1** PR #1123 deferred OB-2 with the framing "needs explicit
  operator design decision (commit-on-change vs commit-on-Next)" —
  confirmed via `D_SERIES_UX_AUDIT.md` Phase 2-extension table row.
- **D.2** Onboarding flow exists — confirmed at
  `app/src/main/java/com/averycorp/prismtask/ui/screens/onboarding/
  OnboardingScreen.kt` (15 pages, fresh installs).
- **D.3** Skip semantic — **premise wrong**, see § "Skip handler
  current state" below. The prompt asserted Skip currently has
  commit-on-Next semantic; the codebase already implements
  commit-on-change throughout.
- **D.4** No prior PR shipped OB-2 fix — confirmed by
  `git log -p -S "skipStep|onSkip|onboarding.*skip|commitOnChange"
  origin/main` (empty) and `git branch -r | grep -iE
  "ob2|onboarding.*skip|skip.*commit"` (empty).
- **D.5** Operator decision = commit-on-change — recorded in this
  audit's prompt.

## Recon findings

### A.1 Drive-by (memory #18)

`git log -p -S "skipStep|onSkip|onboarding.*skip|commitOnChange"` →
no recent commits touching the Skip handler. The most recent
onboarding-related work is `3ce3a13` (POST_NOTIFICATIONS timing on
NotificationsPage), which adjusted permission flow but didn't touch
state-commit semantics.

### A.2 Parked-branch sweep

No parked branches matching `ob2|onboarding.*skip|skip.*commit`.

### A.3 Skip handler current state

The Skip button is a **single global TextButton** at the top-end of
`OnboardingScreen.kt:152–164`:

```kotlin
if (pagerState.currentPage < LAST_PAGE_INDEX) {
    TextButton(
        onClick = {
            coroutineScope.launch {
                pagerState.animateScrollToPage(LAST_PAGE_INDEX)
            }
        },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 48.dp, end = 16.dp)
    ) { Text("Skip") }
}
```

Skip's *only* effect is to scroll the pager forward to the final page
(`SetupPage`). It does NOT mutate any ViewModel or DataStore state.
**State preservation is therefore decided entirely by the per-page
input handlers.**

### A.3 — per-page input-handler audit

Every interactive page with Skip exposure was inspected. Verdict
column = "is the user input committed-on-change to the ViewModel /
DataStore independently of Skip?"

| # | Page | Interactive? | Commit pattern | Verdict |
|---|------|---|---|---|
| 0 | WelcomePage | Sign-In only | OAuth fires its own callback (`viewModel.onGoogleSignIn` at `:1091`) | ✓ commit-on-change |
| 1 | ThemePickerPage | Theme card tap | `viewModel.setTheme(entry.theme)` on every `onSelect` (`:1915`) | ✓ commit-on-change |
| 2 | SmartTasksPage | informational | n/a | n/a |
| 3 | NaturalLanguagePage | demo, animated `typedText` | not user-editable | n/a |
| 4 | HabitsPage | Switch + Slider | `viewModel::setForgivenessStreaksEnabled` (`:603`), `viewModel.setStreakMaxMissedDays` (`:616`) | ✓ commit-on-change |
| 5 | LifeModesPage | 5 LifeModeRow toggles | each `onCheckedChange = viewModel::setXEnabled` (`:1218–1248`) | ✓ commit-on-change |
| 6 | TemplatesPage | TemplatePickerContent `onChange` | `viewModel::updateTemplateSelections` (`:705`); buffered in `_templateSelections: MutableStateFlow`; **applied on `completeOnboarding` (`:371`), which Skip → SetupPage → "Start Using PrismTask" still triggers** | ✓ commit-on-change (deferred-but-guaranteed) |
| 7 | ViewsPage | informational | n/a | n/a |
| 8 | BrainModePage | 3 BrainModeCard toggles | local `adhdSelected/calmSelected/focusReleaseSelected` toggled then `viewModel.setX` fired in same `onToggle` handler (`:820–822`, `:840–842`, `:861–863`) | ✓ commit-on-change (with adjacent stale-init bug — see § Adjacent findings) |
| 9 | AccessibilityPage | 3 LifeModeRow toggles | each `onCheckedChange = viewModel::setX` (`:1370–1385`) | ✓ commit-on-change |
| 10 | PrivacyPage | 2 LifeModeRow toggles | `viewModel::setVoiceInputEnabled`, `viewModel::setAiFeaturesEnabled` (`:1456–1468`) | ✓ commit-on-change |
| 11 | NotificationsPage | 6 LifeModeRow toggles | `viewModel::setX` for each stream (`:1553–1597`); POST_NOTIFICATIONS request fires on first composition per `3ce3a13` | ✓ commit-on-change |
| 12 | DaySetupPage | 2 hour/minute Sliders | `viewModel.setStartOfDay(...)` on every `onValueChange` (`:1667`, `:1675`); `setStartOfDay` atomically writes hour, minute, AND `hasSetStartOfDay = true` (`TaskBehaviorPreferences.kt:162–168`) | ✓ commit-on-change |
| 13 | ConnectIntegrationsPage | informational | no toggles, no input | n/a |
| 14 | SetupPage (LAST_PAGE_INDEX) | Skip is hidden here | n/a (Skip button is gated by `pagerState.currentPage < LAST_PAGE_INDEX`) | n/a |

**No page exists where Skip would discard partial state.** The
`collectAsLocalState` helper at `:1392–1399` collects from each
ViewModel `StateFlow` so prior values are reflected on re-entry. Every
interactive control commits its change to the ViewModel in the same
event handler that updates local Compose state — there is no
"draft-vs-committed" distinction anywhere in the onboarding flow.

### A.4 Sibling-primitive (e) axis

Other multi-step flows surveyed:

- **Routine creation (`SelfCareScreen` morning/bedtime, `SchoolworkScreen`
  course)**: each step writes to its repo on field change; no Skip
  button concept (Save or Cancel).
- **Habit creation (`AddEditHabitScreen`)**: single-screen form, not
  multi-step.
- **Project creation (`AddEditProjectScreen`)**: single-screen form.
- **Notification profile editor (`NotificationsHubScreen` flows)**: each
  detail screen uses an explicit "Save" Button at the bottom that
  commits a profile edit via `commitProfileEdit`. Different idiom — Save
  is required, no Skip. Out of scope.
- **Settings wizards**: none (settings are flat hubs).

No sibling flow has the same Skip-discards-state defect class — the
defect class doesn't exist anywhere in this codebase.

## Skip handler current state — paper-closed

**Verdict: STOP-E (paper-closure).** Per the prompt: "Existing Next
handler already does Pattern A eager-commit. In that case Skip
semantic is already commit-on-change by accident — verify the OB-2
defect by reproducing on AVD. If unreproducible, paper-closure."

The codebase implements commit-on-change throughout. Operator's chosen
semantic is satisfied. Implementation hypothesis "Pattern A: Eager
commit per field change" matches the existing convention exactly — no
code changes are needed to ship the operator's decision.

Why the prompt's framing was off: the original D-series UX audit's
OB-2 defect (lines 1639–1652 of the Onboarding sub-agent's report) was
itself ambiguous about which direction was "the bug" and which was
"the fix" — the audit explicitly noted Skip currently *preserves*
state and listed the alternatives (a) lift to draft buffer (b)
document as feature. Operator picked (b)-equivalent: commit-on-change
is the desired semantic, and that's what the code does today.

## Implementation hypothesis verdicts (B.1–B.3)

- **B.1 Pattern A vs B.** **Pattern A (eager commit per field change)**
  matches existing convention. Skip is a no-op for state because every
  control already commits on its own onChange. No code change needed
  to align with operator decision.
- **B.2 State boundary.** Per-page interactive state lives in two
  forms: (i) local `var X by remember { mutableStateOf(...) }` shadow
  of a `viewModel.X: StateFlow` collected via `collectAsLocalState`,
  with the source of truth in the ViewModel + DataStore; (ii)
  `viewModel._templateSelections: MutableStateFlow` in-memory until
  `completeOnboarding` triggers `applyTemplateSelections`. Both
  preserve through Skip → SetupPage → "Start Using PrismTask"
  navigation.
- **B.3 Test coverage.** No new tests are added because no code
  changes ship. Existing onboarding ViewModel tests at
  `app/src/test/java/com/averycorp/prismtask/ui/screens/onboarding/`
  already cover per-setter commit semantics. AVD verification of the
  STOP-E premise is operator-driven (any interactive page → tap a
  toggle → tap Skip → re-enter onboarding via dev-reset → verify
  toggle is preserved).

## STOP-conditions evaluated

- **STOP-A** (OB-2 fix already shipped post-PR-#1123): cleared.
  No prior commits.
- **STOP-B** (Skip handler materially different from prompt
  assumption): borderline — Skip exists, but its semantic
  (already commit-on-change) is the opposite of what the prompt
  framed. STOP-E (more specific) fires instead.
- **STOP-C** (state lift >100 LOC): n/a, no lift needed.
- **STOP-D** (sibling flows with same defect): cleared, no siblings.
- **STOP-E** ✅ **fires.** Existing per-page handlers already do
  Pattern A eager commit. Paper-closure recommended.
- **STOP-F** (form-state primitive change required): n/a.

## Phase 2 scope

Per STOP-E, Phase 2 ships **zero code changes**. Deliverables:

1. This audit doc.
2. Update `docs/audits/D_SERIES_UX_AUDIT.md` to mark OB-2 as
   paper-closed with rationale (the existing entry was
   "operator-decision-required" — promote to ✅ paper-closed).

## Adjacent findings (NOT auto-filed per memory #30)

**BrainModePage stale-init bug** (`OnboardingScreen.kt:767–769`).
`adhdSelected`, `calmSelected`, `focusReleaseSelected` are initialized
to `false` via `remember { mutableStateOf(false) }` regardless of the
ViewModel's current value. If a returning user lands back on
BrainModePage (e.g. dev-reset, or future deep-link scenarios) after
having previously set ADHD Mode = true, the cards visually show
"unselected" while the ViewModel says true. Tapping a card flips local
to true and writes ViewModel.setAdhdMode(true) (no-op), so the
behavior is *user-visible-but-not-data-corrupting* — but it's the only
onboarding page that doesn't use `collectAsLocalState`.

**Re-trigger criterion:** if BrainModePage gets touched in a future
PR, swap the three local `mutableStateOf(false)` to `collectAsLocalState`
shadows of `viewModel.adhdMode`, `viewModel.calmMode`,
`viewModel.focusReleaseMode` (5–10 LOC). Out of scope for OB-2; not
auto-filed.

## Open questions for operator

None. STOP-E is unambiguous. AVD verification is operator-discretion
post-merge.

## Anti-patterns observed (worth noting, not fixing)

- **Per-page local state shadows of ViewModel flows** are a verbose
  idiom (`val x by collectAsLocalState(...)` + `var x by remember
  { mutableStateOf(...) }` in BrainModePage). A shared `useFlow {}`
  helper would tighten the syntax but qualifies as "new infrastructure"
  per the D-series UX prompt's guardrail.
- **The Skip button at `:152–164` carries no analytics / telemetry
  — operator may eventually want to know how often Skip is tapped per
  page** (post-Phase E feedback signal). Out of scope; instrumentation
  is separate.

---

## Phase 3 — Bundle summary (pre-merge per CLAUDE.md)

**Bundle PR:** #1123 (`claude/audit-d-series-ux-KznZ8` → `main`)
extended with this audit doc commit per session-init's "develop on the
designated branch" constraint. Zero code changes ship for OB-2 —
STOP-E paper-closure.

| Deliverable | Commit | Files | Net LOC |
|---|---|---|---|
| Phase 1 audit doc | (this commit) | `docs/audits/OB2_SKIP_COMMIT_ON_CHANGE_AUDIT.md` (new) | +220 doc |
| D-series audit table update | (this commit) | `docs/audits/D_SERIES_UX_AUDIT.md` | OB-2 row promoted to ✅ paper-closed |

**Verification path.**
- Static gates: doc-only commit; no ktlint/detekt impact.
- Runtime: AVD smoke optional — operator-driven, since no code change.

**Memory entry candidates.** One pattern worth capturing if it lands a
second data point:

> "Audit-first STOP-E (paper-closure when codebase already implements
> the operator-chosen semantic) is more common than expected. The
> mechanism: an audit defers a P2 item with two semantic options;
> operator picks the option codebase already implements; the right
> outcome is paper-closure with rationale, not implementation."

This is the second data point this week — the D-series UX
P2-extension found 5 paper-closures (Custom Sort, Timeline, Smart
Suggestions, Today medication chip, Clinical Report) using a similar
mechanism. Worth filing as a memory if a third instance lands within a
month.

## Phase 4 — Claude Chat handoff

```markdown
## OB-2 Skip = commit-on-change — pre-merge handoff

**Scope.** Implement OB-2 Skip semantic = commit-on-change in the
PrismTask onboarding flow. Deferred from PR #1123 (D-series UX
P2-extension); operator picked commit-on-change on May 4.

**Verdict.** STOP-E paper-closure. The codebase already implements
commit-on-change throughout the onboarding flow — every interactive
page commits to the ViewModel / DataStore on each onChange handler,
independent of Skip. Skip itself is a global TextButton at
`OnboardingScreen.kt:152–164` whose only effect is
`pagerState.animateScrollToPage(LAST_PAGE_INDEX)` — it does not mutate
state. The TemplatesPage subtlety (in-memory MutableStateFlow buffer)
also resolves cleanly because Skip → SetupPage → "Start Using
PrismTask" → `completeOnboarding` → `applyTemplateSelections` still
fires.

**Shipped.** Zero code changes. Deliverables on PR #1123:
- `docs/audits/OB2_SKIP_COMMIT_ON_CHANGE_AUDIT.md` (new, 220 lines)
- `docs/audits/D_SERIES_UX_AUDIT.md` OB-2 row updated from
  "operator-decision-required" → "paper-closed (already
  commit-on-change)"

**Why STOP-E fires.** Audit walked all 14 Skip-eligible pages.
Verdict per page in audit doc § "A.3 — per-page input-handler audit".
No defect exists — the prompt's framing assumed Skip currently has
commit-on-Next semantic; the actual codebase has commit-on-change
semantic, which matches the operator's chosen direction.

**Adjacent finding (NOT auto-filed per memory #30).** BrainModePage at
`OnboardingScreen.kt:767–769` is the only interactive onboarding page
not using the `collectAsLocalState` shadow idiom — its three
`var X by remember { mutableStateOf(false) }` cards initialize to
`false` regardless of ViewModel state. Re-entry after a prior set
shows stale "unselected" cards. Re-trigger: if BrainModePage gets
touched in a future PR, swap to `collectAsLocalState` (5–10 LOC).

**Open questions for operator.** None. AVD verification of STOP-E
premise is at operator's discretion post-merge.

**Final state.** D-series UX OB-2 closure: 0 → 1.0 (paper-close).
F-series follow-on items filed: 0. Phase F GREEN-GO impact: neutral.
```
